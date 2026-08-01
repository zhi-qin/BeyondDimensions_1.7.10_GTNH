package com.wintercogs.beyonddimensions.api.storage.handler.impl;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.wintercogs.beyonddimensions.BeyondDimensions;
import com.wintercogs.beyonddimensions.api.storage.handler.IStackHandler;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EmptyStackKey;

import io.netty.buffer.ByteBuf;

/**
 * 有序、固定槽位的堆叠容器实现（箱子类）。
 * <p>
 * - 槽位数固定，允许同一 Key 占用多个槽位
 * - 采用 {@link ArrayList} 存储每个槽位的 KeyAmount
 * - 空槽位使用 {@link EmptyStackKey#INSTANCE} + amount 0 表示
 * <p>
 * 1.7.10 移植版：
 * CompoundTag → NBTTagCompound，ListTag → NBTTagList，Tag → NBTBase
 * FriendlyByteBuf → ByteBuf，@NotNull → @Nonnull
 */
public class StackHandler implements IStackHandler {

    /**
     * UI 时间戳维护策略
     */
    public enum UiTimestampPolicy {
        /** 不维护时间戳 */
        NONE,
        /** 自动维护时间戳 */
        AUTO,
        /** 手动维护时间戳 */
        MANUAL
    }

    /** 槽位存储：每个元素代表一个槽位，空槽位为 EmptyStackKey + 0 */
    protected final ArrayList<KeyAmount> storage;

    /** 每个槽位的最大容量 */
    protected long slotCapacity;

    /** 最大槽位数 */
    protected int slotMaxSize;

    /** UI 时间戳策略 */
    protected UiTimestampPolicy uiTimestampPolicy;

    /** 每个槽位的 UI 时间戳（用于排序），仅在策略非 NONE 时生效 */
    protected final long[] uiTimestamps;

    /**
     * 标记槽来源物品（仅 FlagStackTypedSlot 正向翻译时记录，反向翻译用于精确还原原标记物品）。
     * 与槽位一一对应，null 表示无来源；仅"有来源时"才写入 NBT（旧档/真实存储零开销）。
     */
    protected IStackKey<?>[] flagSources;

    /** 只读视图 */
    private final List<KeyAmount> entriesView;

    /* ================= 构造方法 ================= */

    /**
     * 创建指定大小的 StackHandler，默认 UiTimestampPolicy 为 NONE
     */
    public StackHandler(int size) {
        this(size, UiTimestampPolicy.NONE);
    }

    /**
     * 创建指定大小和 UI 时间戳策略的 StackHandler
     */
    public StackHandler(int size, UiTimestampPolicy uiTimestampPolicy) {
        this.slotMaxSize = Math.max(0, size);
        this.slotCapacity = Long.MAX_VALUE;
        this.uiTimestampPolicy = (uiTimestampPolicy != null) ? uiTimestampPolicy : UiTimestampPolicy.NONE;
        this.uiTimestamps = new long[this.slotMaxSize];
        this.flagSources = new IStackKey<?>[this.slotMaxSize];

        this.storage = new ArrayList<>(this.slotMaxSize);
        for (int i = 0; i < this.slotMaxSize; i++) {
            this.storage.add(new KeyAmount(EmptyStackKey.INSTANCE, 0L));
        }

        this.entriesView = Collections.unmodifiableList(new AbstractList<KeyAmount>() {

            @Override
            public KeyAmount get(int index) {
                if (index < 0 || index >= storage.size()) return new KeyAmount(EmptyStackKey.INSTANCE, 0L);
                return storage.get(index);
            }

            @Override
            public int size() {
                return storage.size();
            }
        });
    }

    /**
     * 从已有 KeyAmount 列表构造
     */
    public StackHandler(List<KeyAmount> stacks, UiTimestampPolicy uiTimestampPolicy) {
        this(stacks.size(), uiTimestampPolicy);
        for (int i = 0; i < this.slotMaxSize && i < stacks.size(); i++) {
            KeyAmount ka = stacks.get(i);
            if (ka != null) {
                setStackDirectly(i, ka.key(), ka.amount());
            }
        }
    }

    /* ================= 配置方法 ================= */

    public void setSlotCapacity(long capacity) {
        this.slotCapacity = capacity;
    }

    public long getSlotCapacity() {
        return this.slotCapacity;
    }

    public void setSlotMaxSize(int size) {
        this.slotMaxSize = size;
    }

    public int getSlotMaxSize() {
        return this.slotMaxSize;
    }

    public UiTimestampPolicy getUiTimestampPolicy() {
        return this.uiTimestampPolicy;
    }

    public void setUiTimestampPolicy(UiTimestampPolicy policy) {
        this.uiTimestampPolicy = (policy != null) ? policy : UiTimestampPolicy.NONE;
    }

    /* ================= 时间戳工具 ================= */

    protected long nowMillis() {
        return System.currentTimeMillis();
    }

    protected void touchTimestamp(int slot) {
        if (uiTimestampPolicy == UiTimestampPolicy.AUTO && slot >= 0 && slot < uiTimestamps.length) {
            uiTimestamps[slot] = nowMillis();
        }
    }

    public void setTimestamp(int slot, long timestamp) {
        if (slot >= 0 && slot < uiTimestamps.length) {
            uiTimestamps[slot] = timestamp;
        }
    }

    public long getTimestamp(int slot) {
        if (slot >= 0 && slot < uiTimestamps.length) {
            return uiTimestamps[slot];
        }
        return 0L;
    }

    /* ================= 标记槽来源物品 ================= */

    /**
     * 记录指定槽位标记的来源物品（仅正向翻译后写入，用于反向翻译精确还原）。
     */
    public void setFlagSource(int slot, IStackKey<?> key) {
        if (slot >= 0 && slot < flagSources.length) {
            flagSources[slot] = key;
        }
    }

    /**
     * 读取指定槽位标记的来源物品（无记录返回 null）。
     */
    public IStackKey<?> getFlagSource(int slot) {
        if (slot >= 0 && slot < flagSources.length) {
            return flagSources[slot];
        }
        return null;
    }

    /**
     * 清除指定槽位标记的来源物品。
     */
    public void clearFlagSource(int slot) {
        if (slot >= 0 && slot < flagSources.length) {
            flagSources[slot] = null;
        }
    }

    /* ================= IStackHandler 实现 ================= */

    @Override
    public List<KeyAmount> getStorage() {
        return entriesView;
    }

    @Override
    public void onChange() {
        // 子类可覆写以响应变更
    }

    @Override
    public int getSlots() {
        return storage.size();
    }

    @Override
    public void clearStorage() {
        for (int i = 0; i < storage.size(); i++) {
            storage.set(i, new KeyAmount(EmptyStackKey.INSTANCE, 0L));
        }
        if (uiTimestampPolicy != UiTimestampPolicy.NONE) {
            Arrays.fill(uiTimestamps, 0L);
        }
        Arrays.fill(flagSources, null);
        onChange();
    }

    @Override
    @Nonnull
    public KeyAmount getStackBySlot(int slot) {
        if (slot < 0 || slot >= storage.size()) return new KeyAmount(EmptyStackKey.INSTANCE, 0L);
        return storage.get(slot);
    }

    @Override
    @Nonnull
    public KeyAmount getStackByKey(IStackKey<?> key) {
        if (key == null || key == EmptyStackKey.INSTANCE) return new KeyAmount(EmptyStackKey.INSTANCE, 0L);

        for (int i = 0; i < storage.size(); i++) {
            KeyAmount ka = storage.get(i);
            if (ka.key() == EmptyStackKey.INSTANCE) continue;
            if (ka.key()
                .isSameTypeSameComponents(key)) {
                return ka;
            }
        }
        return new KeyAmount(key, 0L);
    }

    @Override
    public boolean hasStack(IStackKey<?> key) {
        if (key == null || key == EmptyStackKey.INSTANCE) return false;

        for (int i = 0; i < storage.size(); i++) {
            KeyAmount ka = storage.get(i);
            if (ka.key() == EmptyStackKey.INSTANCE) continue;
            if (ka.key()
                .isSameTypeSameComponents(key) && ka.amount() > 0L) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void setStackDirectly(int slot, IStackKey<?> key, long amount) {
        if (slot < 0 || slot >= storage.size()) return;

        IStackKey<?> actualKey = (key == null) ? EmptyStackKey.INSTANCE : key;
        long actualAmount = Math.max(0L, amount);

        if (actualKey == EmptyStackKey.INSTANCE || actualAmount <= 0L) {
            storage.set(slot, new KeyAmount(EmptyStackKey.INSTANCE, 0L));
        } else {
            long clamped = Math.min(actualAmount, getSlotCapacity(slot));
            storage.set(slot, new KeyAmount(actualKey, clamped));
        }
        touchTimestamp(slot);
        onChange();
    }

    @Override
    public void addStackDirectly(IStackKey<?> key, long amount) {
        if (key == null || key == EmptyStackKey.INSTANCE || amount <= 0L) return;

        // 找第一个空槽位
        for (int i = 0; i < storage.size(); i++) {
            KeyAmount ka = storage.get(i);
            if (ka.key() == EmptyStackKey.INSTANCE) {
                setStackDirectly(i, key, amount);
                return;
            }
        }
    }

    @Override
    @Nonnull
    public KeyAmount insert(int slot, IStackKey<?> key, long amount, boolean simulate) {
        if (key == null || key == EmptyStackKey.INSTANCE || amount <= 0L)
            return new KeyAmount(EmptyStackKey.INSTANCE, 0L);

        if (slot < 0 || slot >= storage.size()) return new KeyAmount(key, amount);
        if (!isStackValid(slot, key)) return new KeyAmount(key, amount);

        KeyAmount current = storage.get(slot);
        long left = amount;

        if (current.key() == EmptyStackKey.INSTANCE) {
            // 空槽位：直接放入
            long cap = Math.min(key.getVanillaMaxStackSize(), getSlotCapacity(slot));
            long ins = Math.min(left, cap);
            if (ins <= 0) return new KeyAmount(key, left);

            if (!simulate) {
                storage.set(slot, new KeyAmount(key, ins));
                touchTimestamp(slot);
                onChange();
            }
            left -= ins;
            return new KeyAmount(key, left);
        }

        // 非空槽位：必须匹配
        if (!current.key()
            .isSameTypeSameComponents(key)) {
            return new KeyAmount(key, left);
        }

        long cap = Math.min(key.getVanillaMaxStackSize(), getSlotCapacity(slot));
        long room = Math.max(0L, cap - current.amount());
        long ins = Math.min(left, room);
        if (ins <= 0) return new KeyAmount(key, left);

        if (!simulate) {
            storage.set(slot, new KeyAmount(current.key(), current.amount() + ins));
            touchTimestamp(slot);
            onChange();
        }
        left -= ins;
        return new KeyAmount(key, left);
    }

    @Override
    @Nonnull
    public KeyAmount insert(IStackKey<?> key, long amount, boolean simulate) {
        if (key == null || key == EmptyStackKey.INSTANCE || amount <= 0L)
            return new KeyAmount(EmptyStackKey.INSTANCE, 0L);

        long left = amount;

        // 第一阶段：尝试合并已有同 Key 的槽位
        for (int i = 0; i < storage.size() && left > 0L; i++) {
            KeyAmount ka = storage.get(i);
            if (ka.key() == EmptyStackKey.INSTANCE) continue;
            if (!ka.key()
                .isSameTypeSameComponents(key)) continue;

            long cap = Math.min(key.getVanillaMaxStackSize(), getSlotCapacity(i));
            long room = Math.max(0L, cap - ka.amount());
            if (room <= 0L) continue;

            long ins = Math.min(left, room);
            if (!simulate) {
                storage.set(i, new KeyAmount(ka.key(), ka.amount() + ins));
                touchTimestamp(i);
            }
            left -= ins;
        }

        // 第二阶段：填充空槽位
        for (int i = 0; i < storage.size() && left > 0L; i++) {
            KeyAmount ka = storage.get(i);
            if (ka.key() != EmptyStackKey.INSTANCE) continue;
            if (!isStackValid(i, key)) continue;

            long cap = Math.min(key.getVanillaMaxStackSize(), getSlotCapacity(i));
            long ins = Math.min(left, cap);
            if (ins <= 0L) continue;

            if (!simulate) {
                storage.set(i, new KeyAmount(key, ins));
                touchTimestamp(i);
            }
            left -= ins;
        }

        if (!simulate && left != amount) {
            onChange();
        }

        return new KeyAmount(key, left);
    }

    @Override
    @Nonnull
    public KeyAmount extract(int slot, long amount, boolean simulate) {
        if (slot < 0 || slot >= storage.size() || amount <= 0L) return new KeyAmount(EmptyStackKey.INSTANCE, 0L);

        KeyAmount ka = storage.get(slot);
        if (ka.key() == EmptyStackKey.INSTANCE || ka.amount() <= 0L) return new KeyAmount(EmptyStackKey.INSTANCE, 0L);

        long take = Math.min(amount, ka.amount());
        if (take <= 0L) return new KeyAmount(ka.key(), 0L);

        if (!simulate) {
            long left = ka.amount() - take;
            if (left <= 0L) {
                storage.set(slot, new KeyAmount(EmptyStackKey.INSTANCE, 0L));
            } else {
                storage.set(slot, new KeyAmount(ka.key(), left));
            }
            touchTimestamp(slot);
            onChange();
        }

        return new KeyAmount(ka.key(), take);
    }

    @Override
    @Nonnull
    public KeyAmount extract(IStackKey<?> key, long amount, boolean simulate, boolean fuzzy) {
        if (key == null || key == EmptyStackKey.INSTANCE || amount <= 0L)
            return new KeyAmount(EmptyStackKey.INSTANCE, 0L);

        long need = amount;
        long taken = 0L;
        IStackKey<?> extractedKey = key;

        // 遍历所有槽位提取
        for (int i = 0; i < storage.size() && need > 0L; i++) {
            KeyAmount ka = storage.get(i);
            if (ka.key() == EmptyStackKey.INSTANCE || ka.amount() <= 0L) continue;

            boolean matches;
            if (fuzzy) {
                matches = ka.key()
                    .isSame(key);
            } else {
                matches = ka.key()
                    .isSameTypeSameComponents(key);
            }

            if (!matches) continue;

            if (taken == 0L) extractedKey = ka.key();

            long t = Math.min(need, ka.amount());
            if (!simulate) {
                long left = ka.amount() - t;
                if (left <= 0L) {
                    storage.set(i, new KeyAmount(EmptyStackKey.INSTANCE, 0L));
                } else {
                    storage.set(i, new KeyAmount(ka.key(), left));
                }
                touchTimestamp(i);
            }
            taken += t;
            need -= t;
        }

        if (!simulate && taken > 0L) onChange();
        return new KeyAmount(extractedKey, taken);
    }

    @Override
    public long getSlotCapacity(int slot) {
        return slotCapacity;
    }

    @Override
    public boolean isStackValid(int slot, IStackKey<?> key) {
        return key != null && key != EmptyStackKey.INSTANCE;
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < storage.size(); i++) {
            KeyAmount ka = storage.get(i);
            if (ka.key() != EmptyStackKey.INSTANCE && ka.amount() > 0L) {
                return false;
            }
        }
        return true;
    }

    /* ================= NBT 序列化 ================= */

    /**
     * 序列化为 NBT
     */
    public NBTTagCompound serializeNBT() {
        NBTTagCompound root = new NBTTagCompound();
        root.setLong("slotCapacity", this.slotCapacity);
        root.setInteger("slotMaxSize", this.slotMaxSize);

        NBTTagList list = new NBTTagList();
        for (int i = 0; i < storage.size(); i++) {
            KeyAmount ka = storage.get(i);
            NBTTagCompound entry = new NBTTagCompound();
            entry.setTag("key", IStackKey.serializeNBTCommon(ka.key()));
            entry.setLong("amount", ka.amount());
            list.appendTag(entry);
        }
        root.setTag("stacks", list);

        // 标记槽来源物品：仅在有记录时才写（旧档/真实存储零开销）
        boolean hasSource = false;
        for (IStackKey<?> s : flagSources) {
            if (s != null) {
                hasSource = true;
                break;
            }
        }
        if (hasSource) {
            NBTTagList srcList = new NBTTagList();
            for (IStackKey<?> s : flagSources) {
                NBTTagCompound entry = new NBTTagCompound();
                if (s != null) {
                    entry.setTag("key", IStackKey.serializeNBTCommon(s));
                }
                srcList.appendTag(entry);
            }
            root.setTag("flagSources", srcList);
        }

        return root;
    }

    /**
     * 从 NBT 反序列化
     * <p>
     * 优先新格式 "stacks"；否则回退旧格式 "Stacks"（兼容 StackTypedHandler）
     */
    public void deserializeNBT(NBTTagCompound tag) {
        clearStorage();
        if (tag == null) return;

        if (tag.hasKey("slotCapacity", 4)) {
            this.slotCapacity = tag.getLong("slotCapacity");
        }
        if (tag.hasKey("slotMaxSize", 3)) {
            this.slotMaxSize = tag.getInteger("slotMaxSize");
        }

        // 标记槽来源物品（可选标签；旧档缺失时保持为空，反向翻译退化为无操作）
        if (tag.hasKey("flagSources", 9)) {
            NBTTagList srcList = tag.getTagList("flagSources", 10);
            for (int i = 0; i < srcList.tagCount() && i < flagSources.length; i++) {
                NBTTagCompound entry = srcList.getCompoundTagAt(i);
                if (entry.hasKey("key", 10)) {
                    flagSources[i] = IStackKey.deserializeNBTCommon(entry.getCompoundTag("key"));
                }
            }
        }

        // 1) 新格式优先
        if (tag.hasKey("stacks", 9)) {
            NBTTagList list = tag.getTagList("stacks", 10);
            int n = Math.min(storage.size(), list.tagCount());
            if (list.tagCount() > storage.size()) {
                // 槽位容量收缩导致存档数据被截断：保持既有语义（截断），但记录告警便于排查（审计 M1-10）
                BeyondDimensions.LOGGER
                    .warn("StackHandler 反序列化：存档槽位 {} > 当前容量 {}，超出部分被截断丢弃", list.tagCount(), storage.size());
            }

            for (int i = 0; i < n; i++) {
                NBTTagCompound entry = list.getCompoundTagAt(i);
                IStackKey<?> key = EmptyStackKey.INSTANCE;
                if (entry.hasKey("key", 10)) {
                    key = IStackKey.deserializeNBTCommon(entry.getCompoundTag("key"));
                }
                long amt = entry.getLong("amount");
                setStackDirectly(i, key, amt);
            }
            return;
        }

        // 2) 旧格式回退：兼容 StackTypedHandler 写出的 "Stacks"
        if (tag.hasKey("Stacks", 9)) {
            NBTTagList list = tag.getTagList("Stacks", 10);

            for (int i = 0; i < storage.size() && i < list.tagCount(); i++) {
                NBTTagCompound entry = list.getCompoundTagAt(i);
                String typeStr = entry.getString("Type");

                if ("Empty".equals(typeStr)) continue;

                NBTBase typedNode = entry.getTag("TypedStack");
                if (!(typedNode instanceof NBTTagCompound)) continue;

                KeyAmount ka = KeyAmount.deserializeNBT((NBTTagCompound) typedNode);
                if (ka.isEmpty()) continue;
                setStackDirectly(i, ka.key(), ka.amount());
            }
        }
    }

    /* ================= 网络 (ByteBuf) 序列化 ================= */

    /**
     * 写入 ByteBuf
     */
    public void serialize(ByteBuf buf) {
        buf.writeInt(storage.size());
        buf.writeLong(slotCapacity);
        buf.writeInt(slotMaxSize);

        for (int i = 0; i < storage.size(); i++) {
            KeyAmount ka = storage.get(i);
            KeyAmount.serialize(buf, ka);
        }
    }

    /**
     * 从 ByteBuf 读取
     */
    public void deserialize(ByteBuf buf) {
        int size = buf.readInt();
        this.slotCapacity = buf.readLong();
        this.slotMaxSize = buf.readInt();

        // 调整 storage 大小
        if (this.storage.size() != size) {
            this.storage.clear();
            for (int i = 0; i < size; i++) {
                this.storage.add(new KeyAmount(EmptyStackKey.INSTANCE, 0L));
            }
        }

        for (int i = 0; i < size; i++) {
            KeyAmount ka = KeyAmount.deserialize(buf);
            setStackDirectly(i, ka.key(), ka.amount());
        }
    }

    /* ================= 辅助方法 ================= */

    /**
     * 获取指定槽位中 Key 的原始堆叠对象缓存
     */
    public IStackKey<?> getKeyAtSlot(int slot) {
        if (slot < 0 || slot >= storage.size()) return EmptyStackKey.INSTANCE;
        return storage.get(slot)
            .key();
    }

    /**
     * 获取指定槽位中的数量
     */
    public long getAmountAtSlot(int slot) {
        if (slot < 0 || slot >= storage.size()) return 0L;
        return storage.get(slot)
            .amount();
    }

    /**
     * 检查指定槽位是否为空
     */
    public boolean isSlotEmpty(int slot) {
        if (slot < 0 || slot >= storage.size()) return true;
        KeyAmount ka = storage.get(slot);
        return ka.key() == EmptyStackKey.INSTANCE || ka.amount() <= 0L;
    }
}
