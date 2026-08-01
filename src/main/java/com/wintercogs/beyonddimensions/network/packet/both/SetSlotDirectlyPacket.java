package com.wintercogs.beyonddimensions.network.packet.both;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EmptyStackKey;
import com.wintercogs.beyonddimensions.common.menu.widget.slot.AbstractStackTypedSlot;
import com.wintercogs.beyonddimensions.util.BDMainThreadScheduler;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * 直接设置槽位内容的网络包（1.7.10 移植版，双向）。
 * 1.7.10 适配：record → 普通类；FriendlyByteBuf → ByteBuf；writeVarInt → writeInt；
 * NetworkEvent.Context → MessageContext；player.containerMenu → player.openContainer。
 */
public class SetSlotDirectlyPacket implements IMessage {

    private int slotId;
    private KeyAmount stack;

    public SetSlotDirectlyPacket() {}

    public SetSlotDirectlyPacket(int slotId, KeyAmount stack) {
        this.slotId = slotId;
        this.stack = stack;
    }

    public int getSlotId() {
        return slotId;
    }

    public KeyAmount getStack() {
        return stack;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.slotId = buf.readInt();
        if (buf.readBoolean()) {
            this.stack = KeyAmount.deserialize(buf);
        } else {
            this.stack = null;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.slotId);
        buf.writeBoolean(this.stack != null);
        if (this.stack != null) {
            KeyAmount.serialize(buf, this.stack);
        }
    }

    public static class Handler implements IMessageHandler<SetSlotDirectlyPacket, IMessage> {

        @Override
        public IMessage onMessage(SetSlotDirectlyPacket message, MessageContext ctx) {
            // 包 handler 在 Netty IO 线程执行，setStackDirectly 会改槽位内容，
            // 双端都必须切到各自主线程（客户端分支与渲染线程并发）
            if (ctx.side == Side.CLIENT) {
                BDMainThreadScheduler.scheduleClient(() -> handleClient(message));
            } else {
                BDMainThreadScheduler.scheduleServer(() -> handleServer(message, ctx));
            }
            return null;
        }

        private static void handleServer(SetSlotDirectlyPacket message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return;
            applyToContainer(player, message);
        }

        @SideOnly(Side.CLIENT)
        private static void handleClient(SetSlotDirectlyPacket message) {
            EntityPlayer player = Minecraft.getMinecraft().thePlayer;
            if (player == null) return;
            applyToContainer(player, message);
        }

        private static void applyToContainer(EntityPlayer player, SetSlotDirectlyPacket message) {
            Container menu = player.openContainer;
            if (menu == null) return;
            if (message.getSlotId() < 0 || message.getSlotId() >= menu.inventorySlots.size()) return;
            Slot slot = (Slot) menu.inventorySlots.get(message.getSlotId());
            if (slot instanceof AbstractStackTypedSlot) {
                AbstractStackTypedSlot typedSlot = (AbstractStackTypedSlot) slot;
                // null 表示清空槽位（StackHandler.setStackDirectly 对 EmptyStackKey/0 置空；
                // 此前直接 return 导致序列化支持的 null 分支永远无法清空槽位）
                IStackKey<?> key = message.getStack() == null ? EmptyStackKey.INSTANCE
                    : message.getStack()
                        .key();
                long amount = message.getStack() == null ? 0L
                    : message.getStack()
                        .amount();
                typedSlot.setStackDirectly(key, amount);
            }
        }
    }
}
