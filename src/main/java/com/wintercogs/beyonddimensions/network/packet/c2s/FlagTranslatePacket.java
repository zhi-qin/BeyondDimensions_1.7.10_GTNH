package com.wintercogs.beyonddimensions.network.packet.c2s;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;

import com.wintercogs.beyonddimensions.common.menu.BDBaseMenu;
import com.wintercogs.beyonddimensions.common.menu.widget.slot.FlagStackTypedSlot;
import com.wintercogs.beyonddimensions.util.BDMainThreadScheduler;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * 标记槽 SHIFT+滚轮翻译请求包（1.7.10 移植版）。
 * <p>
 * 客户端在 GUI 中按住 SHIFT 滚动滚轮并悬停在标记槽上时发送，
 * 服务端对标记槽执行一次"物品标记 ↔ 容器内流体标记"翻译（对齐 NEI 交互）。
 */
public class FlagTranslatePacket implements IMessage {

    private int slotIndex;
    private int direction;

    public FlagTranslatePacket() {}

    public FlagTranslatePacket(int slotIndex, int direction) {
        this.slotIndex = slotIndex;
        this.direction = direction;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.slotIndex = buf.readInt();
        this.direction = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.slotIndex);
        buf.writeInt(this.direction);
    }

    public static class Handler implements IMessageHandler<FlagTranslatePacket, IMessage> {

        @Override
        public IMessage onMessage(FlagTranslatePacket message, MessageContext ctx) {
            // 包 handler 在 Netty IO 线程执行，translateFlag 会修改标记槽存储，
            // 必须切到服务端主线程
            BDMainThreadScheduler.scheduleServer(() -> handle(message, ctx));
            return null;
        }

        private void handle(FlagTranslatePacket message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) {
                return;
            }
            Container menu = player.openContainer;
            if (!(menu instanceof BDBaseMenu)) {
                return;
            }
            if (message.slotIndex < 0 || message.slotIndex >= menu.inventorySlots.size()) {
                return;
            }
            Slot slot = (Slot) menu.inventorySlots.get(message.slotIndex);
            if (slot instanceof FlagStackTypedSlot) {
                ((FlagStackTypedSlot) slot).translateFlag(message.direction);
                ((BDBaseMenu) menu).detectAndSendChanges();
            }
        }
    }
}
