package com.wintercogs.beyonddimensions.network.packet.c2s;

import java.nio.charset.StandardCharsets;

import net.minecraft.entity.player.EntityPlayerMP;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.util.BDMainThreadScheduler;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * 重命名网络的网络包（1.7.10 移植版）。
 * 1.7.10 适配：record → 普通类；FriendlyByteBuf → ByteBuf；writeVarInt/writeUtf → 手动 UTF-8 写入；
 * NetworkEvent.Context → MessageContext。
 */
public class RenameNetPacket implements IMessage {

    private int netId;
    private String customName;

    public RenameNetPacket() {}

    public RenameNetPacket(int netId, String customName) {
        this.netId = netId;
        this.customName = truncate(customName);
    }

    public int getNetId() {
        return netId;
    }

    public String getCustomName() {
        return customName;
    }

    private static final int MAX_CUSTOM_NAME_LENGTH = 64;

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= MAX_CUSTOM_NAME_LENGTH ? s : s.substring(0, MAX_CUSTOM_NAME_LENGTH);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.netId = buf.readInt();
        int len = buf.readInt();
        // 读取前校验长度，防恶意客户端 new byte[len] OOM（合法包长度必然 <= readableBytes）
        if (len > 0 && len <= buf.readableBytes()) {
            byte[] bytes = new byte[len];
            buf.readBytes(bytes);
            this.customName = new String(bytes, StandardCharsets.UTF_8);
        } else {
            this.customName = "";
        }
        this.customName = truncate(this.customName);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.netId);
        byte[] bytes = (this.customName == null ? "" : this.customName).getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    public static class Handler implements IMessageHandler<RenameNetPacket, IMessage> {

        @Override
        public IMessage onMessage(RenameNetPacket message, MessageContext ctx) {
            // 包 handler 在 Netty IO 线程执行，setCustomName 会写网络持久化数据（markDirty），
            // 必须切到服务端主线程
            BDMainThreadScheduler.scheduleServer(() -> handle(message, ctx));
            return null;
        }

        private static void handle(RenameNetPacket message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return;

            DimensionsNet net = DimensionsNet.getNetFromId(message.getNetId());
            if (net == null || !net.isManager(player)) return;

            net.setCustomName(message.getCustomName());
        }
    }
}
