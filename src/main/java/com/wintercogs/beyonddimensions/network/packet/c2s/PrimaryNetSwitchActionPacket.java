package com.wintercogs.beyonddimensions.network.packet.c2s;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.PrimaryNetSwitchAction;
import com.wintercogs.beyonddimensions.api.dimensionnet.PrimaryNetSwitchHelper;
import com.wintercogs.beyonddimensions.util.BDMainThreadScheduler;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * 主网络切换操作的网络包（1.7.10 移植版）。
 * 1.7.10 适配：record → 普通类；FriendlyByteBuf → ByteBuf；
 * writeEnum/readEnum/writeVarInt → writeInt/读取；NetworkEvent.Context → MessageContext；
 * player.sendSystemMessage(Component) → player.addChatMessage(new ChatComponentTranslation(...))。
 */
public class PrimaryNetSwitchActionPacket implements IMessage {

    private PrimaryNetSwitchAction action;
    private int targetNetId;

    public PrimaryNetSwitchActionPacket() {}

    public PrimaryNetSwitchActionPacket(PrimaryNetSwitchAction action, int targetNetId) {
        this.action = action;
        this.targetNetId = targetNetId;
    }

    public PrimaryNetSwitchAction getAction() {
        return action;
    }

    public int getTargetNetId() {
        return targetNetId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int ordinal = buf.readInt();
        PrimaryNetSwitchAction[] values = PrimaryNetSwitchAction.values();
        this.action = (ordinal >= 0 && ordinal < values.length) ? values[ordinal] : values[0];
        this.targetNetId = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.action.ordinal());
        buf.writeInt(this.targetNetId);
    }

    public static class Handler implements IMessageHandler<PrimaryNetSwitchActionPacket, IMessage> {

        @Override
        public IMessage onMessage(PrimaryNetSwitchActionPacket message, MessageContext ctx) {
            // 包 handler 在 Netty IO 线程执行，切换主网络会读写 PlayerNetIndex/WorldSavedData，
            // 必须切到服务端主线程
            BDMainThreadScheduler.scheduleServer(() -> handle(message, ctx));
            return null;
        }

        private static void handle(PrimaryNetSwitchActionPacket message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return;

            switch (message.getAction()) {
                case CYCLE_NEXT:
                    handleCycle(player);
                    break;
                case SET_EXPLICIT:
                    handleSetExplicit(player, message.getTargetNetId());
                    break;
                case CLEAR_PRIMARY:
                    handleClearPrimary(player);
                    break;
            }
        }

        private static void handleCycle(EntityPlayerMP player) {
            List<DimensionsNet> nets = DimensionsNet.getAllNetFromPlayer(player);
            if (nets.isEmpty()) {
                player.addChatMessage(
                    new ChatComponentTranslation("msg.beyonddimensions.primary_net.switch.none_available"));
                return;
            }

            DimensionsNet currentPrimaryNet = DimensionsNet.getPrimaryNetFromPlayer(player);
            int currentId = currentPrimaryNet == null ? DimensionsNet.NO_PRIMARY_NET_ID : currentPrimaryNet.getId();

            // 构造 id 列表
            java.util.List<Integer> netIds = new java.util.ArrayList<>(nets.size());
            for (DimensionsNet net : nets) {
                netIds.add(net.getId());
            }
            int nextNetId = PrimaryNetSwitchHelper.findNextPrimaryNetId(netIds, currentId);

            if (nextNetId == DimensionsNet.NO_PRIMARY_NET_ID) {
                player.addChatMessage(
                    new ChatComponentTranslation("msg.beyonddimensions.primary_net.switch.none_available"));
                return;
            }

            DimensionsNet nextNet = DimensionsNet.getNetFromId(nextNetId);
            if (nextNet == null) {
                player.addChatMessage(
                    new ChatComponentTranslation("msg.beyonddimensions.primary_net.switch.invalid_target"));
                return;
            }

            if (DimensionsNet.setPrimaryNetForPlayer(player, nextNet)) {
                player.addChatMessage(
                    new ChatComponentTranslation("msg.beyonddimensions.primary_net.switch.changed", nextNetId));
            } else {
                player.addChatMessage(new ChatComponentTranslation("msg.beyonddimensions.primary_net.switch.no_other"));
            }
        }

        private static void handleSetExplicit(EntityPlayerMP player, int targetNetId) {
            boolean stillMember = false;
            for (DimensionsNet net : DimensionsNet.getAllNetFromPlayer(player)) {
                if (net.getId() == targetNetId) {
                    stillMember = true;
                    break;
                }
            }
            if (!stillMember) {
                player.addChatMessage(
                    new ChatComponentTranslation("msg.beyonddimensions.primary_net.switch.invalid_target"));
                return;
            }

            DimensionsNet targetNet = DimensionsNet.getNetFromId(targetNetId);
            if (targetNet == null) {
                player.addChatMessage(
                    new ChatComponentTranslation("msg.beyonddimensions.primary_net.switch.invalid_target"));
                return;
            }

            if (DimensionsNet.setPrimaryNetForPlayer(player, targetNet)
                || DimensionsNet.getPrimaryNetFromPlayer(player) == targetNet) {
                player.addChatMessage(
                    new ChatComponentTranslation("msg.beyonddimensions.primary_net.switch.changed", targetNetId));
            } else {
                player.addChatMessage(
                    new ChatComponentTranslation("msg.beyonddimensions.primary_net.switch.invalid_target"));
            }
        }

        private static void handleClearPrimary(EntityPlayerMP player) {
            if (!DimensionsNet.hasAnyNet(player) && !DimensionsNet.hasPrimaryNet(player)) {
                player.addChatMessage(
                    new ChatComponentTranslation("msg.beyonddimensions.primary_net.switch.none_available"));
                return;
            }

            DimensionsNet.clearPrimaryNetForPlayer(player);
            player.addChatMessage(new ChatComponentTranslation("msg.beyonddimensions.primary_net.switch.cleared"));
        }
    }
}
