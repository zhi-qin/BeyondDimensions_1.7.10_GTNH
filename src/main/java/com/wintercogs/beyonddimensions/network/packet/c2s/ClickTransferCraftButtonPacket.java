package com.wintercogs.beyonddimensions.network.packet.c2s;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;

import com.wintercogs.beyonddimensions.common.menu.DimensionsCraftMenu;
import com.wintercogs.beyonddimensions.util.BDMainThreadScheduler;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * 点击合成转移按钮的网络包（1.7.10 移植版）。
 * 1.7.10 适配：record → 普通类；FriendlyByteBuf → ByteBuf；
 * NetworkEvent.Context → MessageContext；player.containerMenu → player.openContainer。
 */
public class ClickTransferCraftButtonPacket implements IMessage {

    private boolean toStorage;

    public ClickTransferCraftButtonPacket() {}

    public ClickTransferCraftButtonPacket(boolean toStorage) {
        this.toStorage = toStorage;
    }

    public boolean isToStorage() {
        return toStorage;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.toStorage = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(this.toStorage);
    }

    public static class Handler implements IMessageHandler<ClickTransferCraftButtonPacket, IMessage> {

        @Override
        public IMessage onMessage(ClickTransferCraftButtonPacket message, MessageContext ctx) {
            // 包 handler 在 Netty IO 线程执行，cleanCraftSlots 会清空工艺槽并归还材料，
            // 必须切到服务端主线程
            BDMainThreadScheduler.scheduleServer(() -> handle(message, ctx));
            return null;
        }

        private void handle(ClickTransferCraftButtonPacket message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return;
            Container menu = player.openContainer;
            if (menu instanceof DimensionsCraftMenu) {
                DimensionsCraftMenu craftMenu = (DimensionsCraftMenu) menu;
                craftMenu.cleanCraftSlots(message.isToStorage());
            }
        }
    }
}
