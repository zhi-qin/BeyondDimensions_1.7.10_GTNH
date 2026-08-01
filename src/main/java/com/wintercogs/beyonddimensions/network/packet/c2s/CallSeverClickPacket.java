package com.wintercogs.beyonddimensions.network.packet.c2s;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;

import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.common.menu.BDBaseMenu;
import com.wintercogs.beyonddimensions.util.BDMainThreadScheduler;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * 服务端点击事件网络包（1.7.10 移植版）。
 * 1.7.10 适配：record → 普通类；FriendlyByteBuf → ByteBuf；
 * NetworkEvent.Context → MessageContext；broadcastChanges → detectAndSendChanges。
 */
public class CallSeverClickPacket implements IMessage {

    private int slotIndex;
    private KeyAmount clickItem;
    private int button;
    private boolean shiftDown;

    public CallSeverClickPacket() {}

    public CallSeverClickPacket(int slotIndex, KeyAmount clickItem, int button, boolean shiftDown) {
        this.slotIndex = slotIndex;
        this.clickItem = clickItem;
        this.button = button;
        this.shiftDown = shiftDown;
    }

    public int getSlotIndex() {
        return slotIndex;
    }

    public KeyAmount getClickItem() {
        return clickItem;
    }

    public int getButton() {
        return button;
    }

    public boolean isShiftDown() {
        return shiftDown;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.slotIndex = buf.readInt();
        this.clickItem = KeyAmount.deserialize(buf);
        this.button = buf.readInt();
        this.shiftDown = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.slotIndex);
        KeyAmount.serialize(buf, this.clickItem);
        buf.writeInt(this.button);
        buf.writeBoolean(this.shiftDown);
    }

    public static class Handler implements IMessageHandler<CallSeverClickPacket, IMessage> {

        @Override
        public IMessage onMessage(CallSeverClickPacket message, MessageContext ctx) {
            // 包 handler 在 Netty IO 线程执行，customClickHandler 会修改容器/背包/网络存储，
            // 必须切到服务端主线程
            BDMainThreadScheduler.scheduleServer(() -> handle(message, ctx));
            return null;
        }

        private void handle(CallSeverClickPacket message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) {
                return;
            }
            Container menu = player.openContainer;
            if (menu instanceof BDBaseMenu) {
                BDBaseMenu bdMenu = (BDBaseMenu) menu;
                bdMenu.customClickHandler(
                    message.getSlotIndex(),
                    message.getClickItem(),
                    message.getButton(),
                    message.isShiftDown());
                bdMenu.detectAndSendChanges();
            }
        }
    }
}
