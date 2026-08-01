package com.wintercogs.beyonddimensions.network.packet.c2s;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.wintercogs.beyonddimensions.BeyondDimensions;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.common.gui.BDGuiHandler;
import com.wintercogs.beyonddimensions.common.init.BDItems;
import com.wintercogs.beyonddimensions.util.BDMainThreadScheduler;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * 打开磁铁 GUI 的网络包（1.7.10 移植版）。
 * 1.7.10 适配：record → 普通类；FriendlyByteBuf → ByteBuf；
 * NetworkEvent.Context → MessageContext；player.openMenu(SimpleMenuProvider) → player.openGui(...)。
 */
public class OpenMagnetGuiPacket implements IMessage {

    public OpenMagnetGuiPacket() {}

    @Override
    public void fromBytes(ByteBuf buf) {}

    @Override
    public void toBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<OpenMagnetGuiPacket, IMessage> {

        @Override
        public IMessage onMessage(OpenMagnetGuiPacket message, MessageContext ctx) {
            // 包 handler 在 Netty IO 线程执行，getNetFromPlayer 读 WorldSavedData、openGui
            // 立即构造服务端容器，必须切到服务端主线程
            BDMainThreadScheduler.scheduleServer(() -> handle(ctx));
            return null;
        }

        private static void handle(MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return;

            DimensionsNet net = DimensionsNet.getNetFromPlayer(player);
            if (net == null) return;

            ItemStack magnetStack = null;
            for (int i = 0; i < player.inventory.mainInventory.length; i++) {
                ItemStack stack = player.inventory.mainInventory[i];
                if (stack != null && stack.getItem() == BDItems.NET_MAGNET_ITEM) {
                    magnetStack = stack;
                    break;
                }
            }
            if (magnetStack == null) return;

            // 1.7.10 中通过 BDGuiHandler 打开 GUI。MagnetMenu 在 BDGuiHandler 中以
            // InventoryPlayer 构造，无法直接传递 magnetStack 上下文。
            // TODO: 如需在 NetMagnetMenu 中携带 magnetStack，需要扩展 BDGuiHandler 的参数传递机制
            player.openGui(
                BeyondDimensions.instance,
                BDGuiHandler.NET_MAGNET_MENU,
                player.worldObj,
                (int) player.posX,
                (int) player.posY,
                (int) player.posZ);
        }
    }
}
