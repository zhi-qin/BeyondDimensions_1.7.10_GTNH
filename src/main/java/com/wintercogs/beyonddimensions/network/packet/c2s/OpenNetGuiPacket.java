package com.wintercogs.beyonddimensions.network.packet.c2s;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.wintercogs.beyonddimensions.BeyondDimensions;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.common.gui.BDGuiHandler;
import com.wintercogs.beyonddimensions.common.item.NetTerminalItem;
import com.wintercogs.beyonddimensions.util.BDMainThreadScheduler;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * 打开网络 GUI 的网络包（1.7.10 移植版）。
 * 1.7.10 适配：record → 普通类；FriendlyByteBuf → ByteBuf；
 * NetMenuType → int 常量；writeEnum/readEnum → writeInt/读取；
 * NetworkEvent.Context → MessageContext；player.openMenu(SimpleMenuProvider) → player.openGui(...)。
 */
public class OpenNetGuiPacket implements IMessage {

    // 与 1.20.1 NetMenuType 枚举序号保持一致
    public static final int NET_MENU = 0;
    public static final int NET_CRAFT_MENU = 1;
    public static final int NET_CRAFT_TERMINAL = 2;

    private String uuid;
    private int target;

    public OpenNetGuiPacket() {}

    public OpenNetGuiPacket(String uuid, int target) {
        this.uuid = uuid;
        this.target = target;
    }

    public String getUuid() {
        return uuid;
    }

    public int getTarget() {
        return target;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int len = buf.readInt();
        // 读取前校验长度，防恶意客户端 new byte[len] OOM；字符串之后还有 4 字节 int target，
        // 需预留 target 长度，否则畸形包（len 恰为剩余可读字节数）会越界读 target。
        if (len > 0 && len <= buf.readableBytes() - 4) {
            byte[] bytes = new byte[len];
            buf.readBytes(bytes);
            this.uuid = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        } else {
            this.uuid = "";
        }
        this.target = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        byte[] bytes = (this.uuid == null ? "" : this.uuid).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
        buf.writeInt(this.target);
    }

    public static class Handler implements IMessageHandler<OpenNetGuiPacket, IMessage> {

        @Override
        public IMessage onMessage(OpenNetGuiPacket message, MessageContext ctx) {
            // 包 handler 在 Netty IO 线程执行，getNetFromPlayer 读 WorldSavedData、openGui
            // 立即构造服务端容器，必须切到服务端主线程
            BDMainThreadScheduler.scheduleServer(() -> handle(message, ctx));
            return null;
        }

        private static void handle(OpenNetGuiPacket message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return;

            DimensionsNet net = DimensionsNet.getNetFromPlayer(player);
            if (net == null) return;

            int targetMenu = message.getTarget();
            switch (targetMenu) {
                case NET_CRAFT_MENU:
                    // 1.7.10 通过 BDGuiHandler 打开 DimensionsCraftMenu。
                    // TODO: 当 DimensionsCraftMenu 完整移植后，确保能拿到 net.getUnifiedStorage() 数据
                    player.openGui(
                        BeyondDimensions.instance,
                        BDGuiHandler.DIMENSIONS_CRAFT_MENU,
                        player.worldObj,
                        (int) player.posX,
                        (int) player.posY,
                        (int) player.posZ);
                    break;
                case NET_MENU:
                    // 1.7.10 通过 BDGuiHandler 打开 DimensionsNetMenu。
                    // TODO: 当 DimensionsNetMenu 完整移植后，确保能拿到 net.getUnifiedStorage() 数据
                    player.openGui(
                        BeyondDimensions.instance,
                        BDGuiHandler.DIMENSIONS_NET_MENU,
                        player.worldObj,
                        (int) player.posX,
                        (int) player.posY,
                        (int) player.posZ);
                    break;
                case NET_CRAFT_TERMINAL:
                    handleCraftTerminal(player);
                    break;
                default:
                    break;
            }
        }

        private static void handleCraftTerminal(EntityPlayerMP player) {
            // 1.7.10 没有副手；先检查主手，再扫描背包
            ItemStack terminalStack = null;
            ItemStack mainHand = player.getHeldItem();
            if (mainHand != null && mainHand.getItem() instanceof NetTerminalItem) {
                terminalStack = mainHand;
            }
            if (terminalStack == null) {
                for (int i = 0; i < player.inventory.mainInventory.length; i++) {
                    ItemStack stack = player.inventory.mainInventory[i];
                    if (stack != null && stack.getItem() instanceof NetTerminalItem) {
                        terminalStack = stack;
                        break;
                    }
                }
            }
            // TODO: 集成 Baubles API 时在此处扩展饰品栏扫描
            if (terminalStack == null) return;

            // 1.20.1 中通过 NetTerminalItem.contextMap 传递上下文并 openMenu，
            // 1.7.10 直接通过 openGui 打开合成终端 GUI。
            player.openGui(
                BeyondDimensions.instance,
                BDGuiHandler.DIMENSIONS_CRAFT_MENU_TERMINAL,
                player.worldObj,
                (int) player.posX,
                (int) player.posY,
                (int) player.posZ);
        }
    }
}
