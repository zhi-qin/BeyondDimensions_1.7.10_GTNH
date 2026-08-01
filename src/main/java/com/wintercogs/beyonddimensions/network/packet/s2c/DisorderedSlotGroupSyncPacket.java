package com.wintercogs.beyonddimensions.network.packet.s2c;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.common.menu.BDBaseMenu;
import com.wintercogs.beyonddimensions.common.menu.widget.slot.SlotGroupSync;
import com.wintercogs.beyonddimensions.util.BDMainThreadScheduler;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * 无序槽位组同步包（1.7.10 移植版）。
 * 1.7.10 适配：record → 普通类；FriendlyByteBuf → ByteBuf；varint → int；
 * NetworkEvent.Context → MessageContext。
 */
public class DisorderedSlotGroupSyncPacket implements IMessage {

    private int groupId;
    private List<IStackKey<?>> keys;
    private List<Long> newCounts;
    private List<Long> newModifiedTime;
    private List<Long> newInsertedTime;

    public DisorderedSlotGroupSyncPacket() {
        this.keys = new ArrayList<>();
        this.newCounts = new ArrayList<>();
        this.newModifiedTime = new ArrayList<>();
        this.newInsertedTime = new ArrayList<>();
    }

    public DisorderedSlotGroupSyncPacket(int groupId, List<IStackKey<?>> keys, List<Long> newCounts,
        List<Long> newModifiedTime, List<Long> newInsertedTime) {
        this.groupId = groupId;
        this.keys = keys;
        this.newCounts = newCounts;
        this.newModifiedTime = newModifiedTime;
        this.newInsertedTime = newInsertedTime;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.groupId = buf.readInt();

        int keysSize = buf.readInt();
        // 读取前校验数量上限（900KB 分包内条目数有自然上限，64K 已远超合理值），
        // 防恶意包 new ArrayList<>(超大) OOM
        if (keysSize < 0 || keysSize > 65535) {
            this.keys = new ArrayList<>();
            return;
        }
        this.keys = new ArrayList<>(keysSize);
        for (int i = 0; i < keysSize; i++) {
            this.keys.add(IStackKey.deserializeCommon(buf));
        }

        int countsSize = buf.readInt();
        this.newCounts = new ArrayList<>(Math.max(0, Math.min(countsSize, 65535)));
        for (int i = 0; i < Math.max(0, Math.min(countsSize, 65535)); i++) {
            this.newCounts.add(buf.readLong());
        }

        int modifiedSize = buf.readInt();
        this.newModifiedTime = new ArrayList<>(Math.max(0, Math.min(modifiedSize, 65535)));
        for (int i = 0; i < Math.max(0, Math.min(modifiedSize, 65535)); i++) {
            this.newModifiedTime.add(buf.readLong());
        }

        int insertedSize = buf.readInt();
        this.newInsertedTime = new ArrayList<>(Math.max(0, Math.min(insertedSize, 65535)));
        for (int i = 0; i < Math.max(0, Math.min(insertedSize, 65535)); i++) {
            this.newInsertedTime.add(buf.readLong());
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.groupId);

        buf.writeInt(this.keys.size());
        for (IStackKey<?> key : this.keys) {
            IStackKey.serializeCommon(buf, key);
        }

        buf.writeInt(this.newCounts.size());
        for (long v : this.newCounts) {
            buf.writeLong(v);
        }

        buf.writeInt(this.newModifiedTime.size());
        for (long v : this.newModifiedTime) {
            buf.writeLong(v);
        }

        buf.writeInt(this.newInsertedTime.size());
        for (long v : this.newInsertedTime) {
            buf.writeLong(v);
        }
    }

    public int getGroupId() {
        return groupId;
    }

    public List<IStackKey<?>> getKeys() {
        return keys;
    }

    public List<Long> getNewCounts() {
        return newCounts;
    }

    public List<Long> getNewModifiedTime() {
        return newModifiedTime;
    }

    public List<Long> getNewInsertedTime() {
        return newInsertedTime;
    }

    public static class Handler implements IMessageHandler<DisorderedSlotGroupSyncPacket, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(DisorderedSlotGroupSyncPacket message, MessageContext ctx) {
            // 包 handler 在 Netty IO 线程执行，loadChange 会结构性修改客户端 GUI 数据集合
            // （storageMap/slotIndex），与渲染线程并发迭代会产生 CME，必须切到客户端主线程
            BDMainThreadScheduler.scheduleClient(() -> handle(message));
            return null;
        }

        @SideOnly(Side.CLIENT)
        private void handle(DisorderedSlotGroupSyncPacket message) {
            Minecraft mc = Minecraft.getMinecraft();
            EntityPlayer player = mc.thePlayer;
            if (player == null) return;
            int expected = message.getKeys()
                .size();
            if (message.getNewCounts()
                .size() != expected
                || message.getNewModifiedTime()
                    .size() != expected
                || message.getNewInsertedTime()
                    .size() != expected) {
                return;
            }
            if (player.openContainer instanceof BDBaseMenu) {
                BDBaseMenu menu = (BDBaseMenu) player.openContainer;
                // groupId 下界校验（负值会使 slotGroupSyncs.get(-1) 抛 IndexOutOfBounds 断连）
                if (message.getGroupId() >= 0 && message.getGroupId() < menu.slotGroupSyncs.size()) {
                    SlotGroupSync sync = menu.slotGroupSyncs.get(message.getGroupId());
                    if (sync != null) {
                        sync.loadChange(
                            message.getKeys(),
                            message.getNewCounts(),
                            message.getNewModifiedTime(),
                            message.getNewInsertedTime());
                        sync.afterLoadChange();
                    }
                }
            }
        }
    }
}
