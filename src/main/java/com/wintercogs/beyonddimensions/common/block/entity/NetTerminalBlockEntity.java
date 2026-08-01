package com.wintercogs.beyonddimensions.common.block.entity;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.common.menu.DimensionsCraftMenuTerminal;

public class NetTerminalBlockEntity extends NetedBlockEntity {

    private final ItemStack[] craftItems = new ItemStack[9];

    public NetTerminalBlockEntity() {
        for (int i = 0; i < 9; i++) {
            craftItems[i] = null;
        }
    }

    public Container createMenu(InventoryPlayer inventory, EntityPlayer player) {
        DimensionsNet net = getNet();
        if (net != null) {
            // 传入 this：菜单关闭时将合成格内容写回方块（物品常驻方块，对齐源项目
            // TransientCraftingContainer 共享数组语义；否则 TE 数组保持陈旧引用，
            // 重开 GUI 会再次载入已回收物品造成复制）
            return new DimensionsCraftMenuTerminal(inventory, net.getUnifiedStorage(), craftItems, this);
        }
        return null;
    }

    /**
     * 方块终端专用：从菜单写回合成格内容（copy 后存储并标记持久化）。
     * 对齐源项目"方块终端合成格内容随方块 NBT 持久化"的语义。
     */
    public void setCraftItems(ItemStack[] items) {
        for (int i = 0; i < 9; i++) {
            craftItems[i] = (items != null && i < items.length && items[i] != null) ? items[i].copy() : null;
        }
        if (worldObj != null && !worldObj.isRemote) {
            markDirty();
        }
    }

    public String getInventoryName() {
        return "container.beyonddimensions.net_terminal";
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        NBTTagList itemsList = tag.getTagList("CraftItems", 10);
        for (int i = 0; i < 9; i++) {
            if (i < itemsList.tagCount()) {
                NBTTagCompound itemTag = itemsList.getCompoundTagAt(i);
                craftItems[i] = ItemStack.loadItemStackFromNBT(itemTag);
            } else {
                craftItems[i] = null;
            }
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        NBTTagList itemsList = new NBTTagList();
        for (int i = 0; i < 9; i++) {
            NBTTagCompound itemTag = new NBTTagCompound();
            if (craftItems[i] != null) {
                craftItems[i].writeToNBT(itemTag);
            }
            itemsList.appendTag(itemTag);
        }
        tag.setTag("CraftItems", itemsList);
    }

    public void dropContent() {
        if (worldObj == null || worldObj.isRemote) return;
        for (ItemStack stack : craftItems) {
            if (stack != null) {
                float fx = worldObj.rand.nextFloat() * 0.8F + 0.1F;
                float fy = worldObj.rand.nextFloat() * 0.8F + 0.1F;
                float fz = worldObj.rand.nextFloat() * 0.8F + 0.1F;
                net.minecraft.entity.item.EntityItem entityItem = new net.minecraft.entity.item.EntityItem(
                    worldObj,
                    xCoord + fx,
                    yCoord + fy,
                    zCoord + fz,
                    stack.copy());
                entityItem.motionX = worldObj.rand.nextGaussian() * 0.05D;
                entityItem.motionY = worldObj.rand.nextGaussian() * 0.05D + 0.2D;
                entityItem.motionZ = worldObj.rand.nextGaussian() * 0.05D;
                worldObj.spawnEntityInWorld(entityItem);
            }
        }
    }
}
