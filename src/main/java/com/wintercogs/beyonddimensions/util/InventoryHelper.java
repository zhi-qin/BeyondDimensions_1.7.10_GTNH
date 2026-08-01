package com.wintercogs.beyonddimensions.util;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.Loader;

/**
 * 玩家背包操作工具（1.7.10 适配版）。
 * <p>
 * 1.7.10 没有副手（off-hand），也没有 Curios API，相关功能已移除或 stub。
 */
public final class InventoryHelper {

    private InventoryHelper() {}

    /**
     * 获取玩家主手物品。
     */
    @Nullable
    public static ItemStack getMainHandItem(EntityPlayer player) {
        if (player == null || player.inventory == null) return null;
        return player.inventory.getCurrentItem();
    }

    /**
     * 设置玩家主手物品。
     */
    public static void setMainHandItem(EntityPlayer player, @Nullable ItemStack stack) {
        if (player == null || player.inventory == null) return;
        player.inventory.setInventorySlotContents(player.inventory.currentItem, stack);
    }

    /**
     * 在玩家背包中查找匹配的物品（忽略 NBT）。
     *
     * @param player 玩家
     * @param target 目标物品
     * @return 找到的物品槽位索引，未找到返回 -1
     */
    public static int findItemInInventory(EntityPlayer player, ItemStack target) {
        if (player == null || target == null) return -1;
        for (int i = 0; i < player.inventory.mainInventory.length; i++) {
            ItemStack stack = player.inventory.mainInventory[i];
            if (stack != null && stack.getItem() == target.getItem()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 在玩家背包中查找精确匹配的物品（比较 NBT）。
     */
    public static int findItemExact(EntityPlayer player, ItemStack target) {
        if (player == null || target == null) return -1;
        for (int i = 0; i < player.inventory.mainInventory.length; i++) {
            ItemStack stack = player.inventory.mainInventory[i];
            if (stack != null && ItemStack.areItemStacksEqual(stack, target)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 统计玩家背包中某种物品的总数量。
     */
    public static int countItemInInventory(EntityPlayer player, ItemStack target) {
        if (player == null || target == null) return 0;
        int count = 0;
        for (int i = 0; i < player.inventory.mainInventory.length; i++) {
            ItemStack stack = player.inventory.mainInventory[i];
            if (stack != null && stack.getItem() == target.getItem()
                && ItemStack.areItemStackTagsEqual(stack, target)) {
                count += stack.stackSize;
            }
        }
        return count;
    }

    /**
     * 从玩家背包中提取指定数量的物品。
     *
     * @param player 玩家
     * @param target 目标物品模板
     * @param amount 要提取的数量
     * @return 实际提取的物品堆叠，如果不足以提取则返回 null
     */
    @Nullable
    public static ItemStack extractFromInventory(EntityPlayer player, ItemStack target, int amount) {
        if (player == null || target == null || amount <= 0) return null;

        // 先验证背包总量是否足够，不足直接返回 null（不产生任何副作用，
        // 避免调用方误以为"失败 = 未改动背包"（审计 M5-8 死代码陷阱））
        int available = 0;
        for (int i = 0; i < player.inventory.mainInventory.length && available < amount; i++) {
            ItemStack stack = player.inventory.mainInventory[i];
            if (stack != null && stack.getItem() == target.getItem()
                && ItemStack.areItemStackTagsEqual(stack, target)) {
                available += stack.stackSize;
            }
        }
        if (available < amount) return null;

        int remaining = amount;
        ItemStack result = null;

        for (int i = 0; i < player.inventory.mainInventory.length && remaining > 0; i++) {
            ItemStack stack = player.inventory.mainInventory[i];
            if (stack != null && stack.getItem() == target.getItem()
                && ItemStack.areItemStackTagsEqual(stack, target)) {
                int toExtract = Math.min(remaining, stack.stackSize);
                if (result == null) {
                    result = stack.copy();
                    result.stackSize = toExtract;
                } else {
                    result.stackSize += toExtract;
                }

                stack.stackSize -= toExtract;
                if (stack.stackSize <= 0) {
                    player.inventory.mainInventory[i] = null;
                }
                remaining -= toExtract;
            }
        }

        if (remaining > 0) return null;
        return result;
    }

    /**
     * 将物品插入玩家背包。
     *
     * @param player 玩家
     * @param stack  要插入的物品
     * @return 无法插入的剩余物品，如果全部插入则返回 null
     */
    @Nullable
    public static ItemStack insertIntoInventory(EntityPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.stackSize <= 0) return stack;

        int maxStackSize = Math.min(stack.getMaxStackSize(), 64);
        ItemStack remaining = stack.copy();

        // 先尝试合并到已有堆叠
        for (int i = 0; i < player.inventory.mainInventory.length && remaining.stackSize > 0; i++) {
            ItemStack slot = player.inventory.mainInventory[i];
            if (slot != null && slot.stackSize < maxStackSize
                && slot.isItemEqual(remaining)
                && ItemStack.areItemStackTagsEqual(slot, remaining)) {
                int canAdd = Math.min(remaining.stackSize, maxStackSize - slot.stackSize);
                slot.stackSize += canAdd;
                remaining.stackSize -= canAdd;
            }
        }

        // 再放入空槽位
        for (int i = 0; i < player.inventory.mainInventory.length && remaining.stackSize > 0; i++) {
            if (player.inventory.mainInventory[i] == null) {
                int canAdd = Math.min(remaining.stackSize, maxStackSize);
                ItemStack newStack = remaining.copy();
                newStack.stackSize = canAdd;
                player.inventory.mainInventory[i] = newStack;
                remaining.stackSize -= canAdd;
            }
        }

        if (remaining.stackSize <= 0) return null;
        return remaining;
    }

    /**
     * 检查玩家背包是否有足够空间容纳指定物品。
     */
    public static boolean hasSpaceFor(EntityPlayer player, ItemStack stack, int amount) {
        if (player == null || stack == null || amount <= 0) return false;

        int maxStackSize = Math.min(stack.getMaxStackSize(), 64);
        int canFit = 0;

        for (int i = 0; i < player.inventory.mainInventory.length; i++) {
            ItemStack slot = player.inventory.mainInventory[i];
            if (slot == null) {
                canFit += maxStackSize;
            } else if (slot.isItemEqual(stack) && ItemStack.areItemStackTagsEqual(slot, stack)) {
                canFit += maxStackSize - slot.stackSize;
            }
            if (canFit >= amount) return true;
        }
        return false;
    }

    /**
     * 复制物品堆叠并设置数量。
     * 1.7.10 没有 copyWithCount，需要手动实现。
     */
    public static ItemStack copyWithCount(ItemStack stack, int count) {
        if (stack == null || count <= 0) return null;
        ItemStack copy = stack.copy();
        copy.stackSize = count;
        return copy;
    }

    /**
     * [Stub] 获取 Curios/饰品栏中的物品。
     * 1.7.10 没有 Curios API，始终返回 null。
     * 如后续需要 Baubles 支持，可在此处扩展。
     */
    @Nullable
    public static ItemStack getCuriosItem(EntityPlayer player, String slotType) {
        // TODO: Phase 11 - 集成 Baubles API
        return null;
    }

    /**
     * [Stub] 检查 Curios/饰品栏中是否有物品。
     */
    public static boolean hasCuriosItem(EntityPlayer player, String slotType) {
        return false;
    }

    /**
     * 检查某个模组是否已加载。
     */
    public static boolean isModLoaded(String modId) {
        return Loader.isModLoaded(modId);
    }
}
