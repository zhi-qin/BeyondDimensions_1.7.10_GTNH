package com.wintercogs.beyonddimensions.common.menu.widget.slot;

import java.util.*;

import net.minecraft.entity.player.EntityPlayerMP;

import com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.common.init.BDPackets;
import com.wintercogs.beyonddimensions.common.menu.BDBaseMenu;
import com.wintercogs.beyonddimensions.network.packet.s2c.DisorderedSlotGroupSyncPacket;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * 用于无序槽位的同步器（事件驱动 + 逐 tick 合并发送）（1.7.10 移植版）。
 * - 服务端仅入队更新，不立刻发包；
 * - updateChange() 每 tick 合并一次并分包发送；
 * - 每个 key 在一次发送周期内只发送一次，且为"最近一次"的绝对状态。
 * <p>
 * 1.7.10 适配：record → 静态内部类；FriendlyByteBuf → ByteBuf；
 * ServerPlayer → EntityPlayerMP；PacketDistributor → sendTo。
 */
public class DisorderedSlotGroupSync implements SlotGroupSync {

    private static final int MAX_PACKET_SIZE = 900 * 1024; // 921,600 bytes

    public final int groupId;
    private final BDBaseMenu menu;
    private final AbstractUnorderedStackHandler storage;
    private final List<KeyAmount> lastStorage = new ArrayList<>();

    private boolean initialized = false;

    private AutoCloseable anySub;
    private AutoCloseable deltaSub;

    /**
     * 等待发送的"最新绝对状态"缓存（同一 key 多次更新仅保留最后一次）
     */
    private final Map<IStackKey<?>, PendingRecord> pending = new HashMap<>();

    /**
     * 标记：需要在下一次 tick 做一次全量对比（Any 触发）
     */
    private boolean dirtyFullRescan = false;

    /**
     * 缓存条目：绝对数量 + UI 用时间戳（1.7.10 无 record，使用静态内部类）
     */
    private static final class PendingRecord {

        final long count;
        final long modified;
        final long inserted;

        PendingRecord(long count, long modified, long inserted) {
            this.count = count;
            this.modified = modified;
            this.inserted = inserted;
        }
    }

    public DisorderedSlotGroupSync(BDBaseMenu menu, int id, AbstractUnorderedStackHandler storage) {
        this.menu = menu;
        this.groupId = id;
        this.storage = storage;

        // 仅在服务端订阅
        if (isServerSide()) {
            this.anySub = storage.subscribeAny(menu, new AbstractUnorderedStackHandler.AnyChangeListener() {

                @Override
                public void onAnyChange() {
                    onAnyChangeCallback();
                }
            });
            this.deltaSub = storage.subscribeDelta(menu, new AbstractUnorderedStackHandler.DeltaListener() {

                @Override
                public void onDelta(IStackKey<?> key, long size, boolean insert) {
                    onDeltaChangeCallback(key, size, insert);
                }
            });
        }
    }

    /**
     * 在菜单关闭时调用，主动解订阅
     */
    public void dispose() {
        try {
            if (anySub != null) anySub.close();
        } catch (Throwable ignored) {}
        try {
            if (deltaSub != null) deltaSub.close();
        } catch (Throwable ignored) {}
        anySub = null;
        deltaSub = null;
    }

    private boolean isServerSide() {
        return menu.player instanceof EntityPlayerMP;
    }

    @Override
    public int getGroupId() {
        return groupId;
    }

    /* -------------------- 事件回调（仅服务端执行，不立刻发送） -------------------- */

    private void onAnyChangeCallback() {
        if (!isServerSide()) return;
        dirtyFullRescan = true;
    }

    private void onDeltaChangeCallback(IStackKey<?> key, long size, boolean insert) {
        if (!isServerSide() || key == null) return;

        long countNow = storage.getStackByKey(key)
            .amount();
        long lastModified = getLastModifiedOrZero(key);
        long insertedTime = getCreationOrZero(key);

        pending.put(key, new PendingRecord(countNow, lastModified, insertedTime));
    }

    /* -------------------- 逐 tick 合并并发送（服务端） -------------------- */

    @Override
    public void updateChange() {
        if (!isServerSide()) return;

        if (!initialized) {
            initialized = true;
            dirtyFullRescan = true;
        }

        drainAndSend();
    }

    /**
     * 汇总待发更新 -> 分包发送 -> 推进基线
     */
    private void drainAndSend() {
        if (!dirtyFullRescan && pending.isEmpty()) return;

        Map<IStackKey<?>, PendingRecord> toSend = new LinkedHashMap<>();

        if (dirtyFullRescan) {
            Map<IStackKey<?>, Long> lastMap = new HashMap<>();
            for (KeyAmount ka : this.lastStorage) {
                lastMap.merge(ka.key(), ka.amount(), new SumLong());
            }
            Map<IStackKey<?>, Long> nowMap = new HashMap<>();
            for (KeyAmount ka : this.storage.getStorage()) {
                nowMap.merge(ka.key(), ka.amount(), new SumLong());
            }

            Set<IStackKey<?>> allKeys = new HashSet<>();
            allKeys.addAll(lastMap.keySet());
            allKeys.addAll(nowMap.keySet());

            for (IStackKey<?> key : allKeys) {
                long lastCount = lastMap.getOrDefault(key, 0L);
                long nowCount = nowMap.getOrDefault(key, 0L);
                if (nowCount != lastCount) {
                    long mtime = getLastModifiedOrZero(key);
                    long ctime = getCreationOrZero(key);
                    toSend.put(key, new PendingRecord(nowCount, mtime, ctime));
                }
            }

            pending.clear();
            dirtyFullRescan = false;
        } else {
            toSend.putAll(pending);
            pending.clear();
        }

        if (toSend.isEmpty()) return;

        List<IStackKey<?>> keys = new ArrayList<>(toSend.size());
        List<Long> counts = new ArrayList<>(toSend.size());
        List<Long> modifiedTimes = new ArrayList<>(toSend.size());
        List<Long> insertedTimes = new ArrayList<>(toSend.size());

        for (Map.Entry<IStackKey<?>, PendingRecord> e : toSend.entrySet()) {
            keys.add(e.getKey());
            counts.add(e.getValue().count);
            modifiedTimes.add(e.getValue().modified);
            insertedTimes.add(e.getValue().inserted);
        }

        List<DisorderedSlotGroupSyncPacket> packets = buildBatchedPackets(keys, counts, modifiedTimes, insertedTimes);
        for (DisorderedSlotGroupSyncPacket packet : packets) {
            BDPackets.INSTANCE.sendTo(packet, (EntityPlayerMP) menu.player);
        }

        refreshLast();
    }

    /**
     * 估算每条记录字节大小并按 MAX_PACKET_SIZE 分包
     */
    private List<DisorderedSlotGroupSyncPacket> buildBatchedPackets(List<IStackKey<?>> keys, List<Long> counts,
        List<Long> modifiedTimes, List<Long> insertedTimes) {
        final int n = keys.size();
        List<DisorderedSlotGroupSyncPacket> packets = new ArrayList<>(Math.max(1, n / 128));
        List<Integer> entrySizes = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            ByteBuf buf = Unpooled.buffer();
            IStackKey<?> k = keys.get(i);
            if (k != null) IStackKey.serializeCommon(buf, k);
            buf.writeLong(counts.get(i));
            buf.writeLong(modifiedTimes.get(i));
            buf.writeLong(insertedTimes.get(i));
            entrySizes.add(buf.readableBytes());
        }

        List<IStackKey<?>> batchKeys = new ArrayList<>();
        List<Long> batchCounts = new ArrayList<>();
        List<Long> batchModified = new ArrayList<>();
        List<Long> batchInserted = new ArrayList<>();
        int currentSize = 0;

        for (int i = 0; i < n; i++) {
            int entrySize = entrySizes.get(i);
            if (currentSize + entrySize > MAX_PACKET_SIZE && !batchKeys.isEmpty()) {
                packets.add(
                    new DisorderedSlotGroupSyncPacket(
                        groupId,
                        new ArrayList<>(batchKeys),
                        new ArrayList<>(batchCounts),
                        new ArrayList<>(batchModified),
                        new ArrayList<>(batchInserted)));
                batchKeys.clear();
                batchCounts.clear();
                batchModified.clear();
                batchInserted.clear();
                currentSize = 0;
            }
            batchKeys.add(keys.get(i));
            batchCounts.add(counts.get(i));
            batchModified.add(modifiedTimes.get(i));
            batchInserted.add(insertedTimes.get(i));
            currentSize += entrySize;
        }
        if (!batchKeys.isEmpty()) {
            packets
                .add(new DisorderedSlotGroupSyncPacket(groupId, batchKeys, batchCounts, batchModified, batchInserted));
        }
        return packets;
    }

    private long getLastModifiedOrZero(IStackKey<?> key) {
        Long v = storage.getLastModifiedTimeMap()
            .get(key);
        return v == null ? 0L : v;
    }

    private long getCreationOrZero(IStackKey<?> key) {
        Long v = storage.getCreationTimeMap()
            .get(key);
        return v == null ? 0L : v;
    }

    /* -------------------- 客户端：接收并应用 -------------------- */

    @Override
    @SideOnly(Side.CLIENT)
    public void loadChange(List<IStackKey<?>> keys, List<Long> newCounts, List<Long> newModifiedTime,
        List<Long> newInsertedTime) {
        AbstractUnorderedStackHandler clientStorage = storage;
        final int n = keys.size();

        for (int i = 0; i < n; i++) {
            IStackKey<?> key = keys.get(i);
            long count = (i < newCounts.size()) ? newCounts.get(i) : 0L;
            long mtime = (i < newModifiedTime.size()) ? newModifiedTime.get(i) : 0L;
            long ctime = (i < newInsertedTime.size()) ? newInsertedTime.get(i) : 0L;

            if (key != null) {
                clientStorage.setAmountByKey(key, count);

                storage.setLastModifiedTime(key, mtime);
                storage.setCreationTime(key, ctime);
            }
        }
    }

    @Override
    public void afterLoadChange() {}

    public void refreshLast() {
        if (!isServerSide()) return;
        this.lastStorage.clear();
        this.lastStorage.addAll(this.storage.getStorage());
    }

    /**
     * Long 求和合并函数（1.7.10 无 Long::sum 方法引用直接用于 merge）
     */
    private static final class SumLong implements java.util.function.BinaryOperator<Long> {

        @Override
        public Long apply(Long a, Long b) {
            return a + b;
        }
    }
}
