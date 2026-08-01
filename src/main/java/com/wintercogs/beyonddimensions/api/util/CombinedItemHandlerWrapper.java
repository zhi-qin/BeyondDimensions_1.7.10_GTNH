package com.wintercogs.beyonddimensions.api.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

/**
 * 组合物品栏包装器（1.7.10 适配版）。
 * 将多个 IInventory 合并为一个，对外表现为单一的物品栏。
 * 替代 1.20.1 的 IItemHandler 组合模式。
 */
public class CombinedItemHandlerWrapper implements IInventory {

    private final List<IInventory> inventories;
    private final int[] slotOffsets;
    private final int totalSlots;
    private String customName;

    /**
     * 使用多个 IInventory 创建组合包装器
     */
    public CombinedItemHandlerWrapper(IInventory... inventories) {
        this.inventories = Collections.unmodifiableList(new ArrayList<>(Arrays.asList(inventories)));
        this.slotOffsets = new int[inventories.length];
        int total = 0;
        for (int i = 0; i < inventories.length; i++) {
            slotOffsets[i] = total;
            total += inventories[i].getSizeInventory();
        }
        this.totalSlots = total;
    }

    /**
     * 使用 List 创建组合包装器
     */
    public CombinedItemHandlerWrapper(List<IInventory> inventories) {
        this.inventories = Collections.unmodifiableList(new ArrayList<>(inventories));
        this.slotOffsets = new int[inventories.size()];
        int total = 0;
        for (int i = 0; i < inventories.size(); i++) {
            slotOffsets[i] = total;
            total += inventories.get(i)
                .getSizeInventory();
        }
        this.totalSlots = total;
    }

    /**
     * 获取内部物品栏列表
     */
    public List<IInventory> getInventories() {
        return inventories;
    }

    /**
     * 将全局槽位映射到内部物品栏
     */
    private int resolveSlot(int globalSlot) {
        for (int i = inventories.size() - 1; i >= 0; i--) {
            if (globalSlot >= slotOffsets[i]) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 获取内部物品栏中的局部槽位索引
     */
    private int localSlot(int globalSlot, int inventoryIndex) {
        return globalSlot - slotOffsets[inventoryIndex];
    }

    // ==================== IInventory 实现 ====================

    @Override
    public int getSizeInventory() {
        return totalSlots;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (slot < 0 || slot >= totalSlots) return null;
        int idx = resolveSlot(slot);
        if (idx < 0) return null;
        return inventories.get(idx)
            .getStackInSlot(localSlot(slot, idx));
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        if (slot < 0 || slot >= totalSlots) return null;
        int idx = resolveSlot(slot);
        if (idx < 0) return null;
        return inventories.get(idx)
            .decrStackSize(localSlot(slot, idx), amount);
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        if (slot < 0 || slot >= totalSlots) return null;
        int idx = resolveSlot(slot);
        if (idx < 0) return null;
        return inventories.get(idx)
            .getStackInSlotOnClosing(localSlot(slot, idx));
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        if (slot < 0 || slot >= totalSlots) return;
        int idx = resolveSlot(slot);
        if (idx < 0) return;
        inventories.get(idx)
            .setInventorySlotContents(localSlot(slot, idx), stack);
    }

    @Override
    public String getInventoryName() {
        return customName != null ? customName : "container.combined";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return customName != null;
    }

    public void setCustomName(String name) {
        this.customName = name;
    }

    @Override
    public int getInventoryStackLimit() {
        int min = 64;
        for (IInventory inv : inventories) {
            int limit = inv.getInventoryStackLimit();
            if (limit < min) min = limit;
        }
        return min;
    }

    @Override
    public void markDirty() {
        for (IInventory inv : inventories) {
            inv.markDirty();
        }
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        for (IInventory inv : inventories) {
            if (!inv.isUseableByPlayer(player)) return false;
        }
        return true;
    }

    @Override
    public void openInventory() {
        for (IInventory inv : inventories) {
            inv.openInventory();
        }
    }

    @Override
    public void closeInventory() {
        for (IInventory inv : inventories) {
            inv.closeInventory();
        }
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        if (slot < 0 || slot >= totalSlots) return false;
        int idx = resolveSlot(slot);
        if (idx < 0) return false;
        return inventories.get(idx)
            .isItemValidForSlot(localSlot(slot, idx), stack);
    }

    /**
     * 创建一个空的组合包装器（替代 EmptyHandler）
     */
    public static CombinedItemHandlerWrapper empty() {
        return new CombinedItemHandlerWrapper(new IInventory[0]);
    }
}
