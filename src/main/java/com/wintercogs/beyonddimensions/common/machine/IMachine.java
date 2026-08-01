package com.wintercogs.beyonddimensions.common.machine;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public interface IMachine {

    // 用于方块
    public void working();

    // 用于物品
    public void working(ItemStack stack, World world, Entity entity, int slotId, boolean isSelected);

    // 用于方块
    public boolean shouldWork();

    // 用于物品
    public boolean shouldWork(ItemStack stack, World world, Entity entity, int slotId, boolean isSelected);

}
