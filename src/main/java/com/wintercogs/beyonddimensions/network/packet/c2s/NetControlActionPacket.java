package com.wintercogs.beyonddimensions.network.packet.c2s;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;

import com.wintercogs.beyonddimensions.api.dimensionnet.NetControlAction;
import com.wintercogs.beyonddimensions.common.menu.NetControlMenu;
import com.wintercogs.beyonddimensions.util.BDMainThreadScheduler;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * 网络控制操作的网络包（1.7.10 移植版）。
 * 1.7.10 适配：record → 普通类；FriendlyByteBuf → ByteBuf；
 * writeUUID/readUUID → 自行序列化；writeEnum/readEnum → writeInt/读取后 values()[i]；
 * NetworkEvent.Context → MessageContext；player.containerMenu → player.openContainer。
 */
public class NetControlActionPacket implements IMessage {

    private UUID receiver;
    private NetControlAction action;

    public NetControlActionPacket() {}

    public NetControlActionPacket(UUID receiver, NetControlAction action) {
        this.receiver = receiver;
        this.action = action;
    }

    public UUID getReceiver() {
        return receiver;
    }

    public NetControlAction getAction() {
        return action;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.receiver = new UUID(buf.readLong(), buf.readLong());
        int ordinal = buf.readInt();
        NetControlAction[] values = NetControlAction.values();
        this.action = (ordinal >= 0 && ordinal < values.length) ? values[ordinal] : values[0];
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(this.receiver.getMostSignificantBits());
        buf.writeLong(this.receiver.getLeastSignificantBits());
        buf.writeInt(this.action.ordinal());
    }

    public static class Handler implements IMessageHandler<NetControlActionPacket, IMessage> {

        @Override
        public IMessage onMessage(NetControlActionPacket message, MessageContext ctx) {
            // 包 handler 在 Netty IO 线程执行，handlePlayerAction 会写网络成员/所有者
            // （PlayerNetIndex/WorldSavedData），必须切到服务端主线程
            BDMainThreadScheduler.scheduleServer(() -> handle(message, ctx));
            return null;
        }

        private void handle(NetControlActionPacket message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return;
            Container menu = player.openContainer;
            if (!(menu instanceof NetControlMenu)) return;
            NetControlMenu netControlMenu = (NetControlMenu) menu;
            netControlMenu.handlePlayerAction(message.getReceiver(), message.getAction());
        }
    }
}
