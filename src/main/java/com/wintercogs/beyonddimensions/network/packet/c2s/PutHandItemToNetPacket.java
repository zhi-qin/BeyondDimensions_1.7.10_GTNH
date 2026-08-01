package com.wintercogs.beyonddimensions.network.packet.c2s;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.util.BDMainThreadScheduler;
import com.wintercogs.beyonddimensions.util.BDMath;
import com.wintercogs.beyonddimensions.util.InventoryHelper;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * 将主手物品存入网络的网络包（1.7.10 移植版）。
 * 1.7.10 适配：record → 普通类；FriendlyByteBuf → ByteBuf；
 * NetworkEvent.Context → MessageContext；player.getMainHandItem() → InventoryHelper.getMainHandItem()；
 * InteractionHand → 仅主手（1.7.10 无副手）；
 * stack.setCount(int) → stack.stackSize = int。
 */
public class PutHandItemToNetPacket implements IMessage {

    // 1.7.10 无副手，仅保留主手语义，hand 字段不再有意义但保留以兼容序列化
    private int handOrdinal;

    public PutHandItemToNetPacket() {}

    public PutHandItemToNetPacket(int handOrdinal) {
        this.handOrdinal = handOrdinal;
    }

    public int getHandOrdinal() {
        return handOrdinal;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.handOrdinal = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.handOrdinal);
    }

    public static class Handler implements IMessageHandler<PutHandItemToNetPacket, IMessage> {

        @Override
        public IMessage onMessage(PutHandItemToNetPacket message, MessageContext ctx) {
            // 包 handler 在 Netty IO 线程执行，storage.insert 增删网络存储、改写主手物品，
            // 必须切到服务端主线程
            BDMainThreadScheduler.scheduleServer(() -> handle(ctx));
            return null;
        }

        private static void handle(MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return;
            ItemStack mainHand = InventoryHelper.getMainHandItem(player);
            if (mainHand == null) return;

            DimensionsNet net = DimensionsNet.getNetFromPlayer(player);
            if (net == null) return;
            UnifiedStorage storage = net.getUnifiedStorage();

            KeyAmount remaining = storage.insert(new ItemStackKey(mainHand), mainHand.stackSize, false);
            // 将未插入的剩余物品数量回写到主手
            int leftover = BDMath.clampLongToInt(remaining.amount());
            if (leftover <= 0) {
                InventoryHelper.setMainHandItem(player, null);
            } else {
                mainHand.stackSize = leftover;
            }
        }
    }
}
