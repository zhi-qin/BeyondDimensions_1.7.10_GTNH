package com.wintercogs.beyonddimensions.common.menu.widget.slot;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.wintercogs.beyonddimensions.api.storage.handler.IStackHandler;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.menu.BDBaseMenu;
import com.wintercogs.beyonddimensions.util.BDMath;

/**
 * 专用于IStackKey泛型类的slot组件，内部自带click、quick-click以及数据的网络同步处理
 * 请确保其只被添加到BDBaseMenu及其子类
 */
public abstract class AbstractStackTypedSlot extends Slot {

    private static final IInventory EMPTY_INV = new InventoryBasic("", false, 0);

    protected final IStackHandler storage;
    protected final int quickMoveSlotStartIndex;
    protected final int quickMoveSlotEndIndex;
    protected int theSlot;
    protected boolean fake;
    protected boolean active = true;
    protected final BDBaseMenu menu;

    public AbstractStackTypedSlot(BDBaseMenu menu, IStackHandler storage, int slotIndex, int xPosition, int yPosition) {
        super(EMPTY_INV, slotIndex, xPosition, yPosition);
        this.theSlot = slotIndex;
        this.storage = storage;
        this.menu = menu;
        this.quickMoveSlotStartIndex = -1;
        this.quickMoveSlotEndIndex = -1;
    }

    public AbstractStackTypedSlot(BDBaseMenu menu, IStackHandler storage, int slotIndex, int quickMoveSlotStartIndex,
        int quickMoveSlotEndIndex, int xPosition, int yPosition) {
        super(EMPTY_INV, slotIndex, xPosition, yPosition);
        this.theSlot = slotIndex;
        this.storage = storage;
        this.menu = menu;
        this.quickMoveSlotStartIndex = quickMoveSlotStartIndex;
        this.quickMoveSlotEndIndex = quickMoveSlotEndIndex;
    }

    public IStackHandler getStorage() {
        return storage;
    }

    public abstract boolean isOrdered();

    public abstract void click(KeyAmount clickStack, int button, EntityPlayer player);

    public abstract void quickMove(KeyAmount clickStack, int button, EntityPlayer player);

    public abstract void updateChange();

    public abstract void loadChange(int where, IStackKey<?> newKey, long newAmount);

    public long getSlotCap() {
        return storage.getSlotCapacity(theSlot);
    }

    public KeyAmount getTypedStackFromUnifiedStorage() {
        return storage.getStackBySlot(getSlotIndex());
    }

    public ItemStack getItemStackFromUnifiedStorage() {
        KeyAmount stackType = storage.getStackBySlot(getSlotIndex());
        if (stackType.key() instanceof ItemStackKey) {
            ItemStackKey itemStackType = (ItemStackKey) stackType.key();
            ItemStack readOnlyStack = itemStackType.getReadOnlyStack();
            readOnlyStack.stackSize = BDMath.clampLongToInt(stackType.amount());
            return readOnlyStack;
        }
        return null;
    }

    public KeyAmount getVanillaActualStack() {
        KeyAmount stack = getTypedStackFromUnifiedStorage();
        if (stack.isEmpty()) return stack;
        if (stack.amount() > stack.key()
            .getVanillaMaxStackSize()) {
            return new KeyAmount(
                stack.key(),
                stack.key()
                    .getVanillaMaxStackSize());
        }
        return stack;
    }

    public KeyAmount getVanillaMaxSizeStack() {
        KeyAmount stack = getTypedStackFromUnifiedStorage();
        if (stack.isEmpty()) return stack;
        return new KeyAmount(
            stack.key(),
            stack.key()
                .getVanillaMaxStackSize());
    }

    public void setStackDirectly(IStackKey<?> key, long amount) {
        // 空实现，子类按需重写
    }

    public abstract KeyAmount safeInsert(IStackKey<?> key, long amount);

    public abstract KeyAmount safeExtract(IStackKey<?> key, long amount);

    /**
     * 1.7.10 兼容：将物品插入原版 Slot，返回无法插入的剩余物（null 表示全部插入）。
     */
    protected static ItemStack safeInsertIntoVanillaSlot(Slot slot, ItemStack stack) {
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

    @Override
    public ItemStack getStack() {
        if (getSlotIndex() < 0) return null;
        ItemStack itemStack = getItemStackFromUnifiedStorage();
        if (itemStack == null) return null;
        return itemStack.copy();
    }

    @Override
    public boolean getHasStack() {
        return !storage.getStackBySlot(getSlotIndex())
            .isEmpty();
    }

    @Override
    public void onSlotChanged() {
        // IStackHandler系列均应当在实际变化后自行调用onchange
    }

    @Override
    public int getSlotIndex() {
        return this.theSlot;
    }

    @Override
    public boolean isSlotInInventory(IInventory inventory, int slotIndex) {
        if (inventory instanceof com.wintercogs.beyonddimensions.api.storage.handler.IStackHandler) {
            return this.storage == inventory;
        }
        return false;
    }

    public int getContainerSlot() {
        return this.theSlot;
    }

    public void setTheSlotIndex(int index) {
        this.theSlot = index;
    }

    public long getItemCount() {
        if (getSlotIndex() < 0) return -1;
        KeyAmount stack = storage.getStackBySlot(getSlotIndex());
        if (!stack.isEmpty()) return stack.amount();
        return -1;
    }

    public boolean isFake() {
        return fake;
    }

    public void setFake(boolean fake) {
        this.fake = fake;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public void putStack(ItemStack stack) {
        // 不处理
    }

    @Override
    public void onPickupFromSlot(EntityPlayer player, ItemStack stack) {
        // 点击事件交由其他函数处理
    }

    @Override
    public int getSlotStackLimit() {
        return Integer.MAX_VALUE;
    }

    @Override
    public ItemStack decrStackSize(int amount) {
        return null;
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return true;
    }
}
