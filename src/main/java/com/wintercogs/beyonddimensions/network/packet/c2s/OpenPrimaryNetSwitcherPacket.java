package com.wintercogs.beyonddimensions.network.packet.c2s;

import net.minecraft.entity.player.EntityPlayerMP;

import com.wintercogs.beyonddimensions.BeyondDimensions;
import com.wintercogs.beyonddimensions.common.gui.BDGuiHandler;
import com.wintercogs.beyonddimensions.util.BDMainThreadScheduler;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * 打开主网络切换器 GUI 的网络包（1.7.10 移植版）。
 * 1.7.10 适配：record → 普通类；FriendlyByteBuf → ByteBuf；
 * NetworkEvent.Context → MessageContext；player.openMenu(SimpleMenuProvider) → player.openGui(...)。
 */
public class OpenPrimaryNetSwitcherPacket implements IMessage {

    public OpenPrimaryNetSwitcherPacket() {}

    @Override
    public void fromBytes(ByteBuf buf) {}

    @Override
    public void toBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<OpenPrimaryNetSwitcherPacket, IMessage> {

        @Override
        public IMessage onMessage(OpenPrimaryNetSwitcherPacket message, MessageContext ctx) {
            // 包 handler 在 Netty IO 线程执行，openGui 立即构造服务端容器，必须切到服务端主线程
            BDMainThreadScheduler.scheduleServer(() -> handle(ctx));
            return null;
        }

        private static void handle(MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return;
            player.openGui(
                BeyondDimensions.instance,
                BDGuiHandler.PRIMARY_NET_SWITCHER_MENU,
                player.worldObj,
                (int) player.posX,
                (int) player.posY,
                (int) player.posZ);
        }
    }
}
