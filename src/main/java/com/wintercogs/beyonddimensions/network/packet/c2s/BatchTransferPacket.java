package com.wintercogs.beyonddimensions.network.packet.c2s;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.menu.BDBaseMenu;
import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;
import com.wintercogs.beyonddimensions.common.menu.widget.slot.AbstractStackTypedSlot;
import com.wintercogs.beyonddimensions.util.BDMainThreadScheduler;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * 批量转移物品的网络包（1.7.10 移植版）。
 * 仅记载需要转移的物品本身和转移方向。
 * 1.7.10 适配：record → 普通类；FriendlyByteBuf → ByteBuf；
 * slot.getItem() → slot.getStack()；slot.index → slot.slotNumber；
 * stack.isEmpty() → stack == null；slot.safeInsert() → AbstractStackTypedSlot.safeInsertIntoVanillaSlot()。
 */
public class BatchTransferPacket implements IMessage {

    private KeyAmount clickStack;
    private boolean dirToStorage;

    public BatchTransferPacket() {}

    public BatchTransferPacket(KeyAmount clickStack, boolean dirToStorage) {
        this.clickStack = clickStack;
        this.dirToStorage = dirToStorage;
    }

    public KeyAmount getClickStack() {
        return clickStack;
    }

    public boolean isDirToStorage() {
        return dirToStorage;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.clickStack = KeyAmount.deserialize(buf);
        this.dirToStorage = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        KeyAmount.serialize(buf, this.clickStack);
        buf.writeBoolean(this.dirToStorage);
    }

    public static class Handler implements IMessageHandler<BatchTransferPacket, IMessage> {

        @Override
        public IMessage onMessage(BatchTransferPacket message, MessageContext ctx) {
            // 包 handler 在 Netty IO 线程执行，批量转移会遍历容器槽位并增删网络存储，
            // 必须切到服务端主线程
            BDMainThreadScheduler.scheduleServer(() -> handle(message, ctx));
            return null;
        }

        private void handle(BatchTransferPacket message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return;
            if (message.getClickStack() == null) return;
            if (!(message.getClickStack()
                .key() instanceof ItemStackKey)) return;
            ItemStackKey clickItem = (ItemStackKey) message.getClickStack()
                .key();

            Container container = player.openContainer;
            if (!(container instanceof BDBaseMenu)) return;
            BDBaseMenu menu = (BDBaseMenu) container;

            if (message.isDirToStorage()) {
                // 背包 → 存储
                for (int i = 0; i < menu.inventorySlots.size(); i++) {
                    Slot invSlot = (Slot) menu.inventorySlots.get(i);
                    int listIndex = i;
                    if (listIndex < menu.inventoryStartIndex || listIndex >= menu.inventoryEndIndex) {
                        continue;
                    }
                    ItemStack invStack = invSlot.getStack();
                    if (invStack != null && clickItem.equals(new ItemStackKey(invStack))) {
                        menu.customClickHandler(
                            listIndex,
                            new KeyAmount(new ItemStackKey(invStack), invStack.stackSize),
                            0,
                            true);
                    }
                }
            } else if (menu instanceof DimensionsNetMenu) {
                // 存储 → 背包
                DimensionsNetMenu netMenu = (DimensionsNetMenu) menu;
                if (!message.getClickStack()
                    .isEmpty()) {
                    AbstractUnorderedStackHandler storage = netMenu.storage;

                    for (int targetSlotIndex = menu.inventoryStartIndex; targetSlotIndex < menu.inventoryEndIndex
                        && storage.hasStack(clickItem); targetSlotIndex++) {
                        Slot slot = (Slot) menu.inventorySlots.get(targetSlotIndex);

                        KeyAmount extract = storage.extract(clickItem, Integer.MAX_VALUE, false, false);
                        ItemStack extractedStack = (ItemStack) extract.toStack();
                        if (extractedStack != null) {
                            ItemStack remaining;
                            if (slot instanceof AbstractStackTypedSlot) {
                                AbstractStackTypedSlot typedSlot = (AbstractStackTypedSlot) slot;
                                KeyAmount leftover = typedSlot
                                    .safeInsert(new ItemStackKey(extractedStack), extractedStack.stackSize);
                                remaining = leftover.amount() > 0
                                    ? new ItemStackKey(extractedStack).copyStackWithCount(leftover.amount())
                                    : null;
                            } else {
                                remaining = safeInsertIntoVanillaSlot(slot, extractedStack);
                            }
                            if (remaining != null) {
                                storage.insert(new ItemStackKey(remaining), remaining.stackSize, false);
                            }
                        } else {
                            // 防御性回插：若 toStack() 返回 null（非物品类型或异常），
                            // 将已抽取的数量回插存储，避免物品丢失（对齐 1.20.1 源项目行为）
                            storage.insert(extract.key(), extract.amount(), false);
                        }
                    }
                }
            }

            menu.detectAndSendChanges();
        }

        /**
         * 将物品插入原版 Slot，返回无法插入的剩余物（null 表示全部插入）。
         * 与 AbstractStackTypedSlot.safeInsertIntoVanillaSlot 逻辑一致，
         * 因后者为 protected 访问，此处内联以避免跨包访问限制。
         */
        private static ItemStack safeInsertIntoVanillaSlot(Slot slot, ItemStack stack) {
            if (stack == null || stack.stackSize <= 0) return null;
            if (!slot.isItemValid(stack)) return stack;

            ItemStack current = slot.getStack();
            int limit = Math.min(slot.getSlotStackLimit(), stack.getMaxStackSize());
            if (current == null) {
                int insert = Math.min(stack.stackSize, limit);
                ItemStack placed = stack.copy();
                placed.stackSize = insert;
                slot.putStack(placed);
                int remaining = stack.stackSize - insert;
                if (remaining <= 0) return null;
                ItemStack rest = stack.copy();
                rest.stackSize = remaining;
                return rest;
            } else if (current.isItemEqual(stack) && ItemStack.areItemStackTagsEqual(current, stack)) {
                int available = limit - current.stackSize;
                if (available <= 0) return stack;
                int insert = Math.min(available, stack.stackSize);
                current.stackSize += insert;
                slot.onSlotChanged();
                int remaining = stack.stackSize - insert;
                if (remaining <= 0) return null;
                ItemStack rest = stack.copy();
                rest.stackSize = remaining;
                return rest;
            }
            return stack;
        }
    }
}
