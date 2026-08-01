package com.wintercogs.beyonddimensions.common.menu;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler;
import com.wintercogs.beyonddimensions.common.block.entity.NetTerminalBlockEntity;
import com.wintercogs.beyonddimensions.common.item.NetTerminalItem;
import com.wintercogs.beyonddimensions.util.InventoryHelper;

/**
 * 终端版合成菜单（1.7.10 移植版）。
 * 与 DimensionsCraftMenu 的区别：
 * - 关联一个 NetTerminalItem 物品栈，关闭时将工艺槽内容写入物品 NBT
 * - 方块终端模式（blockEntity 非空）时，关闭时将工艺槽内容写回方块 TE（物品常驻方块）
 * - canInteractWith 检查关联物品是否仍然有效
 * <p>
 * 1.7.10 适配：BlockPos → 移除（仅保留物品模式）；
 * CompoundTag → NBTTagCompound；ListTag → NBTTagList；
 * ServerPlayer → EntityPlayerMP；
 * player.level().isClientSide → player.worldObj.isRemote；
 * player.getInventory().placeItemBackInInventory → InventoryHelper.insertIntoInventory；
 * player.drop → player.dropPlayerItemWithRandomChoice；
 * stack.save → stack.writeToNBT；terminalStack.getOrCreateTag → 手动创建 stackTagCompound；
 * ItemStack.EMPTY → null；stack.isEmpty() → stack == null / stack.stackSize <= 0。
 */
public class DimensionsCraftMenuTerminal extends DimensionsCraftMenu {

    @Nullable
    private ItemStack terminalStack = null;

    /** 方块终端模式：持有方块 TE，关闭时将合成格内容写回（非空时优先于 terminalStack） */
    @Nullable
    private NetTerminalBlockEntity blockEntity = null;

    /**
     * 客户端构造函数
     */
    public DimensionsCraftMenuTerminal(InventoryPlayer playerInventory) {
        this(playerInventory, null, null);
    }

    /**
     * 服务端构造函数（便携终端物品模式）
     *
     * @param playerInventory 玩家背包
     * @param data            维度网络存储，null 时使用临时存储
     * @param craftItems      工艺槽初始物品，null 时为空工艺槽
     */
    public DimensionsCraftMenuTerminal(InventoryPlayer playerInventory, @Nullable AbstractUnorderedStackHandler data,
        @Nullable ItemStack[] craftItems) {
        this(playerInventory, data, craftItems, null);
    }

    /**
     * 服务端构造函数（方块终端模式：blockEntity 非空）。
     * 方块模式下不查找手持便携终端（避免玩家背包同时持有便携终端时误把方块配方写入手持物品 NBT）。
     */
    public DimensionsCraftMenuTerminal(InventoryPlayer playerInventory, @Nullable AbstractUnorderedStackHandler data,
        @Nullable ItemStack[] craftItems, @Nullable NetTerminalBlockEntity blockEntity) {
        super(playerInventory, data, craftItems);
        this.blockEntity = blockEntity;
        if (!player.worldObj.isRemote && blockEntity == null) {
            // 1.7.10: GUI 系统无法直接传递触发物品，从玩家背包查找 NetTerminalItem
            this.terminalStack = findTerminalStack(playerInventory);
        }
    }

    /**
     * 从玩家背包中查找 NetTerminalItem（优先主手）
     */
    @Nullable
    private static ItemStack findTerminalStack(InventoryPlayer inventory) {
        // 优先检查主手
        ItemStack mainHand = inventory.getCurrentItem();
        if (mainHand != null && mainHand.getItem() instanceof NetTerminalItem) {
            return mainHand;
        }
        // 回退：搜索整个背包
        for (int i = 0; i < inventory.mainInventory.length; i++) {
            ItemStack stack = inventory.mainInventory[i];
            if (stack != null && stack.getItem() instanceof NetTerminalItem) {
                return stack;
            }
        }
        return null;
    }

    @Override
    protected void initCraftSlots(InventoryPlayer playerInventory, ItemStack[] craftItems) {
        super.initCraftSlots(playerInventory, craftItems);
        // 父函数处理完毕后更新一次结果槽
        slotChangedCraftingGrid(this, player.worldObj, player, craftSlots, resultSlots, resultSlotIndex);
    }

    @Override
    public void onContainerClosed(EntityPlayer player) {
        // 处理光标物品
        if (player instanceof EntityPlayerMP) {
            ItemStack itemstack = this.getCarried();
            if (itemstack != null) {
                if (player.isEntityAlive()) {
                    // TODO: 1.7.10 无 ServerPlayer.hasDisconnected()，使用 isEntityAlive 近似判断
                    ItemStack leftover = InventoryHelper.insertIntoInventory(player, itemstack);
                    if (leftover != null && leftover.stackSize > 0) {
                        player.dropPlayerItemWithRandomChoice(leftover, false);
                    }
                } else {
                    player.dropPlayerItemWithRandomChoice(itemstack, false);
                }
                this.setCarried(null);
            }
        }

        // 处理合成槽物品
        if (player instanceof EntityPlayerMP) {
            if (blockEntity != null) {
                // 方块终端模式：将合成槽内容写回方块 TE（物品常驻方块，随方块 NBT 持久化，
                // 对齐源项目 TransientCraftingContainer 共享数组语义）。若不写回，TE 数组保持
                // 陈旧引用，重开 GUI 会再次载入已回收物品造成复制。
                ItemStack[] slots = new ItemStack[craftSlots.getSizeInventory()];
                for (int i = 0; i < slots.length; i++) {
                    ItemStack stack = craftSlots.getStackInSlot(i);
                    slots[i] = stack != null ? stack.copy() : null;
                }
                blockEntity.setCraftItems(slots);
                // 清空工艺槽，避免父类 cleanCraftSlots 重复处理导致物品复制
                for (int i = 0; i < craftSlots.getSizeInventory(); i++) {
                    craftSlots.setInventorySlotContents(i, null);
                }
                resultSlots.setInventorySlotContents(0, null);
            } else if (terminalStack != null && terminalStack.getItem() instanceof NetTerminalItem) {
                // 终端物品模式：将合成槽物品写入终端物品的 NBT
                NBTTagCompound tag = terminalStack.stackTagCompound;
                if (tag == null) {
                    tag = new NBTTagCompound();
                    terminalStack.stackTagCompound = tag;
                }
                NBTTagList slotsTag = new NBTTagList();
                for (int i = 0; i < craftSlots.getSizeInventory(); i++) {
                    ItemStack stack = craftSlots.getStackInSlot(i);
                    NBTTagCompound itemTag = new NBTTagCompound();
                    if (stack != null) {
                        stack.writeToNBT(itemTag);
                    }
                    slotsTag.appendTag(itemTag);
                }
                tag.setTag("craft_slots", slotsTag);

                // 清空工艺槽，避免父类 cleanCraftSlots 重复处理导致物品复制
                for (int i = 0; i < craftSlots.getSizeInventory(); i++) {
                    craftSlots.setInventorySlotContents(i, null);
                }
                resultSlots.setInventorySlotContents(0, null);
            }
        }

        // 调用父类以释放资源（slotGroupSyncs 等）
        // 若已清空工艺槽，cleanCraftSlots 不会做任何事
        super.onContainerClosed(player);
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        if (terminalStack != null) {
            // 终端物品模式：检查物品是否仍有效
            // TODO: 1.7.10 适配 - 源码检查 !terminalStack.isEmpty()
            // 更严格的检查应验证物品仍在玩家背包中
            return terminalStack.stackSize > 0;
        }
        // 无终端物品（方块模式）时，始终允许
        return true;
    }
}
