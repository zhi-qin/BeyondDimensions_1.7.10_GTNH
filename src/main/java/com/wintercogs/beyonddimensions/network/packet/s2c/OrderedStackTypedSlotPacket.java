package com.wintercogs.beyonddimensions.network.packet.s2c;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.common.menu.widget.slot.AbstractStackTypedSlot;
import com.wintercogs.beyonddimensions.util.BDMainThreadScheduler;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * 有序槽位类型化的网络包（1.7.10 移植版）。
 * 1.7.10 适配：record → 普通类；FriendlyByteBuf → ByteBuf；writeVarInt/writeVarLong → writeInt/writeLong；
 * NetworkEvent.Context → MessageContext；player.containerMenu → player.openContainer；
 * menu.slots.get(...) → menu.inventorySlots.get(...)。
 */
public class OrderedStackTypedSlotPacket implements IMessage {

    private int slotId;
    private int slotIndex;
    private IStackKey<?> stack;
    private long newAmount;

    public OrderedStackTypedSlotPacket() {}

    public OrderedStackTypedSlotPacket(int slotId, int slotIndex, IStackKey<?> stack, long newAmount) {
        this.slotId = slotId;
        this.slotIndex = slotIndex;
        this.stack = stack;
        this.newAmount = newAmount;
    }

    public int getSlotId() {
        return slotId;
    }

    public int getSlotIndex() {
        return slotIndex;
    }

    public IStackKey<?> getStack() {
        return stack;
    }

    public long getNewAmount() {
        return newAmount;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.slotId = buf.readInt();
        this.slotIndex = buf.readInt();
        this.stack = IStackKey.deserializeCommon(buf);
        this.newAmount = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.slotId);
        buf.writeInt(this.slotIndex);
        IStackKey.serializeCommon(buf, this.stack);
        buf.writeLong(this.newAmount);
    }

    public static class Handler implements IMessageHandler<OrderedStackTypedSlotPacket, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(OrderedStackTypedSlotPacket message, MessageContext ctx) {
            // 包 handler 在 Netty IO 线程执行，loadChange 会修改槽位内容，与渲染线程并发，
            // 必须切到客户端主线程
            BDMainThreadScheduler.scheduleClient(() -> handle(message));
            return null;
        }

        @SideOnly(Side.CLIENT)
        private static void handle(OrderedStackTypedSlotPacket message) {
            EntityPlayer player = Minecraft.getMinecraft().thePlayer;
            if (player == null) return;
            Container menu = player.openContainer;
            if (menu == null) return;
            if (message.getSlotId() < 0 || message.getSlotId() >= menu.inventorySlots.size()) return;
            Slot slot = (Slot) menu.inventorySlots.get(message.getSlotId());
            if (slot instanceof AbstractStackTypedSlot) {
                AbstractStackTypedSlot typedSlot = (AbstractStackTypedSlot) slot;
                typedSlot.loadChange(message.getSlotIndex(), message.getStack(), message.getNewAmount());
            }
        }
    }
}
