package com.wintercogs.beyonddimensions.network.packet.s2c;

import java.nio.charset.StandardCharsets;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;
import com.wintercogs.beyonddimensions.util.BDMainThreadScheduler;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * 维度网络 EU 池存量同步包（1.7.10 移植新增）。
 * <p>
 * 服务端 {@link DimensionsNetMenu#updateChange()} 在网络 EU 池存量变化时发送，
 * 携带十进制字符串（如 10^40 为 41 字符），客户端写入 {@code DimensionsNetMenu.euAmount}
 * 供终端 EU 能量条渲染。容量为共享静态常量，无需同步。
 */
public class SyncEuStoragePacket implements IMessage {

    private String euAmount;

    public SyncEuStoragePacket() {
        this.euAmount = "0";
    }

    public SyncEuStoragePacket(String euAmount) {
        this.euAmount = euAmount == null ? "0" : euAmount;
    }

    public String getEuAmount() {
        return euAmount;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int len = buf.readInt();
        // 读取前校验长度，防 new byte[len] OOM（防御性，S2C 亦可能来自异常服务器）
        if (len > 0 && len <= buf.readableBytes()) {
            byte[] bytes = new byte[len];
            buf.readBytes(bytes);
            this.euAmount = new String(bytes, StandardCharsets.UTF_8);
        } else {
            this.euAmount = "0";
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        byte[] bytes = this.euAmount.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    public static class Handler implements IMessageHandler<SyncEuStoragePacket, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(SyncEuStoragePacket message, MessageContext ctx) {
            // 包 handler 在 Netty IO 线程执行，写入菜单 EU 显示字段，切到客户端主线程
            BDMainThreadScheduler.scheduleClient(() -> handle(message));
            return null;
        }

        @SideOnly(Side.CLIENT)
        private static void handle(SyncEuStoragePacket message) {
            EntityPlayer player = Minecraft.getMinecraft().thePlayer;
            if (player == null) return;
            Container menu = player.openContainer;
            if (menu instanceof DimensionsNetMenu) {
                ((DimensionsNetMenu) menu).euAmount = message.getEuAmount();
            }
        }
    }
}
