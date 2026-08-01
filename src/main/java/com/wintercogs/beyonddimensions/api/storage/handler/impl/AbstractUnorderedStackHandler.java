package com.wintercogs.beyonddimensions.api.storage.handler.impl;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EmptyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.init.BDItems;
import com.wintercogs.beyonddimensions.common.item.MatterCompressionBall;

/**
 * 无序存储处理器的抽象基类（1.7.10 简化版）。
 * 使用 HashMap 存储 key -> amount，并提供基础的 insert/extract 逻辑。
 * <p>
 * 继承自 {@link StackHandler}，复用其公共 API 基础，但使用自己的无序 HashMap 存储。
 */
public abstract class AbstractUnorderedStackHandler extends StackHandler {

    /* ---------- 是否保留 amount==0 的键 ---------- */
    public enum ZeroPolicy {
        KEEP_ZERO,
        REMOVE_ON_ZERO
    }

    /* ---------- 订阅：强/弱 + 增量上下文 ---------- */
    @FunctionalInterface
    public interface DeltaListener {

        void onDelta(IStackKey<?> key, long size, boolean insert);
    }

    @FunctionalInterface
    public interface AnyChangeListener {

        void onAnyChange();
    }

    @FunctionalInterface
    public interface QuadConsumer<A, B, C, D> {

        void accept(A a, B b, C c, D d);
    }

    private static final class OwnerRef extends WeakReference<Object> {

        OwnerRef(Object owner, ReferenceQueue<Object> q) {
            super(owner, q);
        }
    }

    private static final class AnyEntry {

        final OwnerRef ownerRef;
        final AnyChangeListener listener;

        AnyEntry(OwnerRef ref, AnyChangeListener l) {
            this.ownerRef = ref;
            this.listener = l;
        }
    }

    private static final class DeltaEntry {

        final OwnerRef ownerRef;
        final DeltaListener listener;

        DeltaEntry(OwnerRef ref, DeltaListener l) {
            this.ownerRef = ref;
            this.listener = l;
        }
    }

    private final CopyOnWriteArrayList<AnyEntry> anyListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<DeltaEntry> deltaListeners = new CopyOnWriteArrayList<>();
    private int deltaContextDepth = 0;

    private void beginDeltaContext() {
        deltaContextDepth++;
    }

    private void endDeltaContext() {
        deltaContextDepth = Math.max(0, deltaContextDepth - 1);
    }

    private boolean inDeltaContext() {
        return deltaContextDepth > 0;
    }

    private final ReferenceQueue<Object> refQueue = new ReferenceQueue<>();

    private void drainRefQueue() {
        OwnerRef ref;
        while ((ref = (OwnerRef) refQueue.poll()) != null) {
            final OwnerRef deadRef = ref;
            // CopyOnWriteArrayList 的迭代器不支持 remove()，必须使用 removeIf
            anyListeners.removeIf(e -> e.ownerRef == deadRef);
            deltaListeners.removeIf(e -> e.ownerRef == deadRef);
        }
    }

    private ZeroPolicy zeroPolicy;

    protected AbstractUnorderedStackHandler(ZeroPolicy policy, UiTimestampPolicy uiTimestampPolicy) {
        super(0, uiTimestampPolicy);
        this.zeroPolicy = Objects.requireNonNull(policy);
        // 对齐 1.20.1 行为：无序存储不依赖父类 StackHandler 的 slotMaxSize 限制容量
        // （父类构造时 size=0 会让 slotMaxSize=0，导致 setAmountByKey/insert 误拒新 key）
        this.slotMaxSize = Integer.MAX_VALUE;
    }

    /* ---------- 内部存储 ---------- */
    protected final Map<IStackKey<?>, Long> storageMap = new HashMap<>();
    protected final ArrayList<IStackKey<?>> slotIndex = new ArrayList<>();
    protected final Map<IStackKey<?>, Integer> posMap = new HashMap<>();

    /* ---------- 仅供 UI 使用的时间表 ---------- */
    /**
     * 记录该 Key 最近一次「从无到有建槽位」的时间（毫秒时间戳）。仅供 UI 展示，无其他语义。
     */
    protected final Map<IStackKey<?>, Long> creationTimeMap = new HashMap<>();
    /**
     * 记录该 Key 最近一次「数量被修改」的时间（毫秒时间戳）。仅供 UI 展示，无其他语义。
     */
    protected final Map<IStackKey<?>, Long> lastModifiedTimeMap = new HashMap<>();

    /* ---------- 只读、动态的 KeyAmount 视图 ---------- */
    private final List<KeyAmount> entriesView = Collections.unmodifiableList(new AbstractList<KeyAmount>() {

        @Override
        public KeyAmount get(int index) {
            IStackKey<?> key = slotIndex.get(index);
            long amt = storageMap.getOrDefault(key, 0L);
            return new KeyAmount(key, amt);
        }

        @Override
        public int size() {
            return slotIndex.size();
        }
    });

    /* =================== UI 时间策略：工具 =================== */

    /**
     * 覆写父类 StackHandler.getSlots()。
     * 父类返回内部 storage（ArrayList）的 size，但无序存储不使用该 storage（构造时 size=0），
     * 导致 getSlots() 恒为 0。这里返回 slotIndex.size() 对齐 1.20.1 的 IStackHandler 默认行为。
     */
    @Override
    public int getSlots() {
        return slotIndex.size();
    }

    /**
     * 主动覆写：设定某个 key 的「建槽位时间」
     */
    public void setCreationTime(IStackKey<?> key, long timeMillis) {
        if (key != null) creationTimeMap.put(key, timeMillis);
    }

    /**
     * 主动覆写：设定某个 key 的「最后修改时间」
     */
    public void setLastModifiedTime(IStackKey<?> key, long timeMillis) {
        if (key != null) lastModifiedTimeMap.put(key, timeMillis);
    }

    /**
     * 获取「建槽位时间表」的引用
     */
    public Map<IStackKey<?>, Long> getCreationTimeMap() {
        return creationTimeMap;
    }

    /**
     * 获取「最后修改时间表」的引用
     */
    public Map<IStackKey<?>, Long> getLastModifiedTimeMap() {
        return lastModifiedTimeMap;
    }

    /* ================= 公共订阅 API ================= */

    public AutoCloseable subscribeAny(Object owner, AnyChangeListener onAny) {
        if (owner == null || onAny == null) throw new IllegalArgumentException();
        drainRefQueue();
        AnyEntry e = new AnyEntry(new OwnerRef(owner, refQueue), onAny);
        anyListeners.add(e);
        return () -> anyListeners.remove(e);
    }

    public AutoCloseable subscribeDelta(Object owner, DeltaListener onDelta) {
        if (owner == null || onDelta == null) throw new IllegalArgumentException();
        drainRefQueue();
        DeltaEntry e = new DeltaEntry(new OwnerRef(owner, refQueue), onDelta);
        deltaListeners.add(e);
        return () -> deltaListeners.remove(e);
    }

    public <T> AutoCloseable subscribeAnyWeak(T owner, java.util.function.Consumer<T> onAny) {
        if (owner == null || onAny == null) throw new IllegalArgumentException();
        drainRefQueue();
        OwnerRef ref = new OwnerRef(owner, refQueue);
        AnyEntry e = new AnyEntry(ref, () -> {
            @SuppressWarnings("unchecked")
            T o = (T) ref.get();
            if (o != null) onAny.accept(o);
            else drainRefQueue();
        });
        anyListeners.add(e);
        return () -> anyListeners.remove(e);
    }

    public <T> AutoCloseable subscribeDeltaWeak(T owner, QuadConsumer<T, IStackKey<?>, Long, Boolean> onDelta) {
        if (owner == null || onDelta == null) throw new IllegalArgumentException();
        drainRefQueue();
        OwnerRef ref = new OwnerRef(owner, refQueue);
        DeltaEntry e = new DeltaEntry(ref, (key, size, insert) -> {
            @SuppressWarnings("unchecked")
            T o = (T) ref.get();
            if (o != null) onDelta.accept(o, key, size, insert);
            else drainRefQueue();
        });
        deltaListeners.add(e);
        return () -> deltaListeners.remove(e);
    }

    protected void fireChange() {
        if (inDeltaContext()) return;
        drainRefQueue();
        for (AnyEntry e : anyListeners) {
            try {
                e.listener.onAnyChange();
            } catch (Throwable ignored) {}
        }
    }

    protected void fireDelta(IStackKey<?> key, long size, boolean insert) {
        if (inDeltaContext()) return;
        drainRefQueue();
        for (DeltaEntry e : deltaListeners) {
            try {
                e.listener.onDelta(key, size, insert);
            } catch (Throwable ignored) {}
        }
    }

    /**
     * 对齐 1.20.1 源项目 onContentChanged 的通知契约：
     * 在 delta 上下文中调用 onChange()（触发 {@link UnifiedStorage#onChange} 的
     * net.markDirty() 持久化脏标记，但经 {@link #fireChange()} 的 inDeltaContext
     * 检查抑制 anyChange 全量通知），随后发送 fireDelta 增量通知。
     * 使全部存储突变路径一致：既标记网络持久化，又不触发多余的全量刷新。
     */
    protected final void onContentChanged(IStackKey<?> key, long size, boolean insert) {
        beginDeltaContext();
        try {
            onChange();
        } finally {
            endDeltaContext();
        }
        fireDelta(key, size, insert);
    }

    @Override
    public void onChange() {
        fireChange();
    }

    public void setZeroPolicy(ZeroPolicy policy) {
        Objects.requireNonNull(policy);
        if (this.zeroPolicy == policy) return;

        this.zeroPolicy = policy;
        reconcileAfterZeroPolicyChange();
    }

    public ZeroPolicy getZeroPolicy() {
        return this.zeroPolicy;
    }

    /**
     * 在状态切换到 remove zero 时，做一个零键清理
     */
    private void reconcileAfterZeroPolicyChange() {
        if (this.zeroPolicy != ZeroPolicy.REMOVE_ON_ZERO) return;

        boolean anyChange = false;
        for (Iterator<Map.Entry<IStackKey<?>, Long>> it = storageMap.entrySet()
            .iterator(); it.hasNext();) {
            Map.Entry<IStackKey<?>, Long> e = it.next();
            if (e.getValue() <= 0L) {
                anyChange = true;
                IStackKey<?> key = e.getKey();
                it.remove();
                removeFromIndex(key);
            }
        }

        if (anyChange) {
            onChange();
        }
    }

    /* ================= IStackHandler 实现（通用） ================= */

    @Override
    public List<KeyAmount> getStorage() {
        return entriesView;
    }

    @Override
    public void clearStorage() {
        storageMap.clear();
        slotIndex.clear();
        posMap.clear();
        creationTimeMap.clear();
        lastModifiedTimeMap.clear();
        onChange();
    }

    @Override
    @Nonnull
    public KeyAmount getStackBySlot(int slot) {
        if (slot < 0 || slot >= slotIndex.size()) return new KeyAmount(EmptyStackKey.INSTANCE, 0L);
        IStackKey<?> key = slotIndex.get(slot);
        return new KeyAmount(key, storageMap.getOrDefault(key, 0L));
    }

    @Override
    @Nonnull
    public KeyAmount getStackByKey(IStackKey<?> key) {
        if (key == null) return new KeyAmount(EmptyStackKey.INSTANCE, 0L);
        return new KeyAmount(key, storageMap.getOrDefault(key, 0L));
    }

    @Override
    public boolean hasStack(IStackKey<?> key) {
        if (key == null) return false;
        if (this.zeroPolicy == ZeroPolicy.KEEP_ZERO) {
            return storageMap.containsKey(key);
        }
        return storageMap.getOrDefault(key, 0L) > 0L;
    }

    /**
     * 按 key 直接设置数量，并处理索引维护
     */
    public long setAmountByKey(IStackKey<?> key, long amount) {
        if (key == null) return 0L;

        long current = storageMap.getOrDefault(key, 0L);
        long target = Math.max(0L, Math.min(amount, slotCapacity));
        if (target == current) return current;

        if (target == 0L) {
            if (zeroPolicy == ZeroPolicy.REMOVE_ON_ZERO) {
                if (current > 0L || posMap.containsKey(key)) {
                    storageMap.remove(key);
                    removeFromIndex(key);
                    if (uiTimestampPolicy == UiTimestampPolicy.AUTO) {
                        lastModifiedTimeMap.put(key, nowMillis());
                    }
                    // 对齐源项目 setAmountByKey(target==0)：移除 key 时经 onContentChanged 通知
                    // （触发 markDirty 持久化 + delta 增量；否则依赖 delta 订阅的客户端视图
                    // ClientNetStorage 无法感知 key 被移除，残留幽灵条目）
                    onContentChanged(key, current, false);
                }
            } else {
                // KEEP_ZERO
                storageMap.put(key, 0L);
                ensureInIndex(key);
                if (uiTimestampPolicy == UiTimestampPolicy.AUTO) {
                    lastModifiedTimeMap.put(key, nowMillis());
                }
                if (current > 0L) {
                    onContentChanged(key, current, false);
                }
            }
            return 0L;
        }

        // target > 0
        boolean isNew = (current == 0L) && !posMap.containsKey(key);
        if (isNew && slotIndex.size() >= slotMaxSize) {
            return current;
        }
        storageMap.put(key, target);
        ensureInIndex(key);
        if (uiTimestampPolicy == UiTimestampPolicy.AUTO) {
            lastModifiedTimeMap.put(key, nowMillis());
        }
        long delta = Math.abs(target - current);
        if (delta > 0L) {
            onContentChanged(key, delta, target > current);
        }
        return target;
    }

    @Override
    public void setStackDirectly(int slot, IStackKey<?> newKey, long amount) {
        if (slot < 0 || slot >= slotIndex.size()) return;

        IStackKey<?> oldKey = slotIndex.get(slot);
        long target = Math.max(0L, amount);

        if (Objects.equals(oldKey, newKey)) {
            setAmountByKey(oldKey, target);
            return;
        }

        long oldAmt = storageMap.getOrDefault(oldKey, 0L);
        if (zeroPolicy == ZeroPolicy.REMOVE_ON_ZERO) {
            storageMap.remove(oldKey);
            removeFromIndex(oldKey);
        } else {
            storageMap.put(oldKey, 0L);
        }
        if (uiTimestampPolicy == UiTimestampPolicy.AUTO) {
            lastModifiedTimeMap.put(oldKey, nowMillis());
        }
        // 对齐源项目 setStackDirectly：移除旧 key 时经 onContentChanged 通知（markDirty + delta）
        if (oldAmt > 0L) {
            onContentChanged(oldKey, oldAmt, false);
        }

        if (newKey != null) {
            setAmountByKey(newKey, target);
            if (uiTimestampPolicy == UiTimestampPolicy.AUTO) {
                lastModifiedTimeMap.put(newKey, nowMillis());
            }
        }
    }

    @Override
    public void addStackDirectly(IStackKey<?> key, long amount) {
        insert(key, amount, false);
    }

    @Override
    @Nonnull
    public KeyAmount insert(int slot, IStackKey<?> key, long amount, boolean simulate) {
        return insert(key, amount, simulate);
    }

    @Override
    @Nonnull
    public KeyAmount insert(IStackKey<?> key, long amount, boolean simulate) {
        if (key == null) return new KeyAmount(EmptyStackKey.INSTANCE, Math.max(0L, amount));
        long add = Math.max(0L, amount);
        if (add == 0L) return new KeyAmount(key, 0L);

        // 对齐 1.20.1 源项目：物质压缩球插入时自动解压为内含物品
        if (key instanceof ItemStackKey itemKey && itemKey.getSource() == BDItems.MATTER_COMPRESS_BALL) {
            return unzipMatterBall(itemKey, add, simulate);
        }

        long current = storageMap.getOrDefault(key, 0L);
        boolean needNewSlot = (current == 0L) && !posMap.containsKey(key);
        if (needNewSlot && slotIndex.size() >= slotMaxSize) {
            return new KeyAmount(key, add);
        }

        long cap = slotCapacity;
        long room = cap <= current ? 0L : (cap - current);
        if (room <= 0L) return new KeyAmount(key, add);

        long actual = Math.min(room, add);
        long leftover = add - actual;

        if (!simulate) {
            storageMap.put(key, current + actual);
            ensureInIndex(key);
            if (uiTimestampPolicy == UiTimestampPolicy.AUTO) {
                lastModifiedTimeMap.put(key, nowMillis());
            }
            onContentChanged(key, actual, true);
        }
        return new KeyAmount(key, leftover);
    }

    /**
     * 物质压缩球解压逻辑（对齐 1.20.1 源项目 unzipMatterBall）。
     * <p>
     * 当物质压缩球被插入无序存储时，不存储球本身，而是读取其 NBT 中的 stack_list，
     * 将内含的 KeyAmount 按 ballCount 倍数展开后逐个插入存储。
     * <p>
     * 1.7.10 适配要点：
     * - BDItems.MATTER_COMPRESS_BALL 是直接 Item 引用（非 Supplier）
     * - ItemStack 空判断使用 == null（1.7.10 无 isEmpty()）
     * - 存储字段名为 storageMap（移植版重命名）
     */
    protected KeyAmount unzipMatterBall(ItemStackKey ballKey, long ballCount, boolean simulate) {
        ItemStack ballStack = ballKey.copyStackWithCount(ballCount);
        if (ballStack == null || !(ballStack.getItem() instanceof MatterCompressionBall)) {
            return new KeyAmount(ballKey, ballCount);
        }

        // 从 NBT stack_list 读取内含物品列表
        List<KeyAmount> contents;
        if (!MatterCompressionBall.hasIStackList(ballStack)) {
            contents = Collections.emptyList();
        } else {
            try {
                contents = MatterCompressionBall.getIStackList(ballStack);
            } catch (Throwable t) {
                contents = Collections.emptyList();
            }
        }

        // 空球视为已解压完成（返回 0 表示全部接收）
        if (contents.isEmpty()) return new KeyAmount(ballKey, 0L);

        // 计算所需插入总量（按 ballCount 倍数缩放）
        final Map<IStackKey<?>, Long> needMap = new HashMap<>();
        try {
            for (KeyAmount entry : contents) {
                if (entry.isEmpty()) continue;
                long scaled = Math.multiplyExact(entry.amount(), ballCount);
                needMap.merge(entry.key(), scaled, Math::addExact);
            }
        } catch (ArithmeticException e) {
            // 溢出：无法解压，原样返回球
            return new KeyAmount(ballKey, ballCount);
        }

        // 预检查：槽位与容量是否足够
        int freeSlots = Math.max(0, slotMaxSize - slotIndex.size());
        int newKeysNeeded = 0;
        for (Map.Entry<IStackKey<?>, Long> e : needMap.entrySet()) {
            IStackKey<?> k = e.getKey();
            long need = e.getValue();
            long current = storageMap.getOrDefault(k, 0L);
            boolean isNew = (current == 0L) && !posMap.containsKey(k);
            if (isNew && ++newKeysNeeded > freeSlots) return new KeyAmount(ballKey, ballCount);
            long room = (slotCapacity <= current) ? 0L : (slotCapacity - current);
            if (need > room) return new KeyAmount(ballKey, ballCount);
        }

        if (simulate) return new KeyAmount(ballKey, 0L);

        // 实际插入：记录已应用的数量，失败时回滚
        final ArrayList<KeyAmount> applied = new ArrayList<>();
        for (KeyAmount entry : contents) {
            if (entry.isEmpty()) continue;
            long scaled;
            try {
                scaled = Math.multiplyExact(entry.amount(), ballCount);
            } catch (ArithmeticException e) {
                // 回滚已插入的内容
                for (int i = applied.size() - 1; i >= 0; i--) {
                    KeyAmount a = applied.get(i);
                    extract(a.key(), a.amount(), false, false);
                }
                return new KeyAmount(ballKey, ballCount);
            }
            KeyAmount leftover = insert(entry.key(), scaled, false);
            long ok = scaled - leftover.amount();
            if (ok > 0L) applied.add(new KeyAmount(entry.key(), ok));
            if (leftover.amount() > 0L) {
                // 容量不足：回滚已插入的内容
                for (int i = applied.size() - 1; i >= 0; i--) {
                    KeyAmount a = applied.get(i);
                    extract(a.key(), a.amount(), false, false);
                }
                return new KeyAmount(ballKey, ballCount);
            }
        }
        return new KeyAmount(ballKey, 0L);
    }

    private KeyAmount extractByKey(IStackKey<?> key, long count, boolean simulate) {
        long current = storageMap.getOrDefault(key, 0L);
        if (current <= 0L) return new KeyAmount(key, 0L);

        long take = Math.min(count, current);
        if (!simulate) {
            long left = current - take;
            if (left == 0L) {
                if (uiTimestampPolicy == UiTimestampPolicy.AUTO) {
                    lastModifiedTimeMap.put(key, nowMillis());
                }
                if (zeroPolicy == ZeroPolicy.REMOVE_ON_ZERO) {
                    storageMap.remove(key);
                    removeFromIndex(key);
                } else {
                    storageMap.put(key, 0L);
                    ensureInIndex(key);
                }
            } else {
                storageMap.put(key, left);
                if (uiTimestampPolicy == UiTimestampPolicy.AUTO) {
                    lastModifiedTimeMap.put(key, nowMillis());
                }
            }
            onContentChanged(key, take, false);
        }
        return new KeyAmount(key, take);
    }

    @Override
    @Nonnull
    public KeyAmount extract(int slot, long count, boolean simulate) {
        if (slot < 0 || slot >= slotIndex.size() || count <= 0L) {
            return new KeyAmount(EmptyStackKey.INSTANCE, 0L);
        }
        IStackKey<?> key = slotIndex.get(slot);
        return extractByKey(key, count, simulate);
    }

    @Override
    @Nonnull
    public KeyAmount extract(IStackKey<?> key, long amount, boolean simulate, boolean fuzzy) {
        if (fuzzy && key != null) {
            final IStackKey<?> fuzzyKey = key;
            key = slotIndex.stream()
                .filter(x -> x.isSame(fuzzyKey))
                .findFirst()
                .orElse(null);
        }
        if (key == null || amount <= 0L) return new KeyAmount(EmptyStackKey.INSTANCE, 0L);
        return extractByKey(key, amount, simulate);
    }

    @Override
    public boolean isEmpty() {
        return slotIndex.isEmpty();
    }

    /* ---------------- 索引维护：O(1) 换尾 ---------------- */

    protected void ensureInIndex(IStackKey<?> key) {
        if (key == EmptyStackKey.INSTANCE) return;
        if (posMap.containsKey(key)) return;
        int idx = slotIndex.size();
        slotIndex.add(key);
        posMap.put(key, idx);
        // 新建槽位时间
        if (uiTimestampPolicy == UiTimestampPolicy.AUTO) {
            creationTimeMap.put(key, nowMillis());
        }
    }

    protected void removeFromIndex(IStackKey<?> key) {
        Integer pos = posMap.remove(key);
        if (pos == null) return;
        int last = slotIndex.size() - 1;
        if (pos.intValue() != last) {
            IStackKey<?> tail = slotIndex.get(last);
            slotIndex.set(pos.intValue(), tail);
            posMap.put(tail, pos.intValue());
        }
        slotIndex.remove(last);
        // 为避免无上限增长，这里选择在移除槽位时一并清理时间记录
        creationTimeMap.remove(key);
        lastModifiedTimeMap.remove(key);
    }

    /* ---------------- 便捷设置 ---------------- */

    @Override
    public void setSlotCapacity(long capacity) {
        this.slotCapacity = capacity;
        onChange();
    }

    @Override
    public void setSlotMaxSize(int maxSize) {
        this.slotMaxSize = maxSize;
        onChange();
    }

    public boolean isFullSlotsSize() {
        return slotIndex.size() >= slotMaxSize;
    }

    /* ---------------- NBT 序列化 ---------------- */

    /**
     * 序列化为 NBT（无序格式）
     */
    @Override
    public NBTTagCompound serializeNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong("slotCapacity", this.slotCapacity);
        tag.setInteger("slotMaxSize", this.slotMaxSize);

        NBTTagList stacksTag = new NBTTagList();
        final boolean writeZero = (zeroPolicy == ZeroPolicy.KEEP_ZERO);

        for (Map.Entry<IStackKey<?>, Long> e : storageMap.entrySet()) {
            IStackKey<?> key = e.getKey();
            long amount = e.getValue() == null ? 0L : e.getValue();
            if (key == null || key.isEmpty()) continue;
            if (!writeZero && amount <= 0L) continue;

            NBTTagCompound one = new NBTTagCompound();
            one.setTag("key", IStackKey.serializeNBTCommon(key));
            one.setLong("amount", amount);
            stacksTag.appendTag(one);
        }

        tag.setTag("stacks", stacksTag);
        return tag;
    }

    /**
     * 从 NBT 反序列化
     * <p>
     * 优先新格式 "stacks"；否则回退旧格式 "Stacks"
     */
    @Override
    public void deserializeNBT(NBTTagCompound tag) {
        clearStorage();
        if (tag == null) return;

        if (tag.hasKey("slotCapacity", 4)) {
            slotCapacity = tag.getLong("slotCapacity");
        }
        if (tag.hasKey("slotMaxSize", 3)) {
            slotMaxSize = tag.getInteger("slotMaxSize");
        }

        // 1) 新格式
        if (tag.hasKey("stacks", 9)) {
            NBTTagList list = tag.getTagList("stacks", 10);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound one = (NBTTagCompound) list.getCompoundTagAt(i);

                if (!one.hasKey("key", 10)) continue;
                IStackKey<?> key;
                try {
                    key = IStackKey.deserializeNBTCommon(one.getCompoundTag("key"));
                } catch (Throwable t) {
                    continue;
                }

                long amount = one.hasKey("amount", 4) ? one.getLong("amount") : one.getLong("Amount");
                acceptEntry(key, amount);
            }
            return;
        }

        // 2) 旧格式回退：Stacks
        if (tag.hasKey("Stacks", 9)) {
            NBTTagList list = tag.getTagList("Stacks", 10);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound entry = (NBTTagCompound) list.getCompoundTagAt(i);
                String typeStr = entry.getString("Type");
                if (typeStr.isEmpty()) continue;

                NBTBase typedNode = entry.getTag("TypedStack");
                if (!(typedNode instanceof NBTTagCompound)) continue;

                KeyAmount ka;
                try {
                    ka = KeyAmount.deserializeNBT((NBTTagCompound) typedNode);
                } catch (Throwable t) {
                    continue;
                }
                acceptEntry(ka.key(), ka.amount());
            }
        }
    }

    /**
     * 将解码得到的 (key, amount) 写入当前结构（遵守 zeroPolicy 与 UI 时间策略）
     */
    protected void acceptEntry(IStackKey<?> key, long amount) {
        if (key == null || key.isEmpty()) return;

        if (uiTimestampPolicy == UiTimestampPolicy.AUTO) {
            long now = nowMillis();
            creationTimeMap.put(key, now);
            lastModifiedTimeMap.put(key, now);
        }

        if (amount <= 0L) {
            if (zeroPolicy == ZeroPolicy.KEEP_ZERO) {
                storageMap.put(key, 0L);
                ensureInIndex(key);
            }
            return;
        }

        insert(key, amount, false);
    }
}
