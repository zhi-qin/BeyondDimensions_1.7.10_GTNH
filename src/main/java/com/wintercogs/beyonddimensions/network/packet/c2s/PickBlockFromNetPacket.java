package com.wintercogs.beyonddimensions.network.packet.c2s;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.api.util.BytebufHelper;
import com.wintercogs.beyonddimensions.util.BDMainThreadScheduler;
import com.wintercogs.beyonddimensions.util.InventoryHelper;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * 从网络中拾取物品的网络包（1.7.10 移植版）。
 * 1.7.10 适配：record → 普通类；FriendlyByteBuf → ByteBuf；
 * BytebufHelper.writeItemBuf/readItemBuf → BytebufHelper.writeItemStack/readItemStack；
 * player.getMainHandItem() → InventoryHelper.getMainHandItem()；
 * player.setItemInHand(InteractionHand.MAIN_HAND, ...) → InventoryHelper.setMainHandItem(...)；
 * stack.isEmpty() → stack == null。
 */
public class PickBlockFromNetPacket implements IMessage {

    private ItemStack targetStack;

    public PickBlockFromNetPacket() {}

    public PickBlockFromNetPacket(ItemStack targetStack) {
        this.targetStack = targetStack;
    }

    public ItemStack getTargetStack() {
        return targetStack;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.targetStack = BytebufHelper.readItemStack(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        BytebufHelper.writeItemStack(buf, this.targetStack);
    }

    public static class Handler implements IMessageHandler<PickBlockFromNetPacket, IMessage> {

        @Override
        public IMessage onMessage(PickBlockFromNetPacket message, MessageContext ctx) {
            // 包 handler 在 Netty IO 线程执行，storage.extract 增删网络存储、改写主手物品，
            // 必须切到服务端主线程
            BDMainThreadScheduler.scheduleServer(() -> handle(message, ctx));
            return null;
        }

        private static void handle(PickBlockFromNetPacket message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return;
            ItemStack mainHand = InventoryHelper.getMainHandItem(player);
            if (mainHand != null) return;
            if (message.getTargetStack() == null) return;

            DimensionsNet net = DimensionsNet.getNetFromPlayer(player);
            if (net == null) return;
            UnifiedStorage storage = net.getUnifiedStorage();

            ItemStackKey targetKey = new ItemStackKey(message.getTargetStack());
            IStackKey<?> target = null;
            for (KeyAmount stack : storage.getStorage()) {
                if (stack.key() instanceof ItemStackKey) {
                    ItemStackKey itemStackKey = (ItemStackKey) stack.key();
                    if (itemStackKey.equals(targetKey)) {
                        target = itemStackKey;
                        break;
                    }
                }
            }

            if (target != null) {
                ItemStack currentMain = InventoryHelper.getMainHandItem(player);
                if (currentMain == null) {
                    KeyAmount extracted = storage.extract(target, target.getVanillaMaxStackSize(), false, false);
                    ItemStack extract = (ItemStack) extracted.toStack();
                    if (extract != null) {
                        InventoryHelper.setMainHandItem(player, extract);
                    }
                }
            }
        }
    }
}
