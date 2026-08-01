package com.wintercogs.beyonddimensions.common.item;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.wintercogs.beyonddimensions.common.init.BDItems;

public class UnstableSpaceTimeFragment extends Item {

    public UnstableSpaceTimeFragment() {
        super();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(
            StatCollector
                .translateToLocalFormatted("tooltip.item.unstable_space_time.long_data", getRemainingTime(stack) / 10));
    }

    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int slotId, boolean isSelected) {
        super.onUpdate(stack, world, entity, slotId, isSelected);

        if (world.isRemote || !(entity instanceof EntityPlayer)) {
            return;
        }

        EntityPlayer player = (EntityPlayer) entity;

        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        if (!tag.hasKey("LongData")) tag.setLong("LongData", 3600L);
        if (!tag.hasKey("TimeLine")) tag.setLong("TimeLine", 0L);

        final long currentTick = world.getTotalWorldTime();
        final long lastProcessed = tag.getLong("TimeLine");
        if (currentTick - lastProcessed <= 200L) {
            return;
        }

        long currentValue = tag.getLong("LongData");

        if (currentValue > 10) {
            tag.setLong("LongData", currentValue - 10);
            tag.setLong("TimeLine", currentTick);
            return;
        }

        // currentValue <= 10：直接转化
        int globalSlot = findGlobalSlotByReference(player.inventory, stack);
        if (globalSlot < 0) {
            tag.setLong("TimeLine", currentTick);
            return;
        }

        ItemStack stable = new ItemStack(BDItems.STABLE_SPACE_TIME_FRAGMENT, stack.stackSize);
        player.inventory.setInventorySlotContents(globalSlot, stable);
    }

    private static int findGlobalSlotByReference(InventoryPlayer inv, ItemStack target) {
        // items: 0..35
        for (int i = 0; i < inv.mainInventory.length; i++) {
            if (inv.mainInventory[i] == target) return i;
        }
        // armor: 36..39
        for (int i = 0; i < inv.armorInventory.length; i++) {
            if (inv.armorInventory[i] == target) return 36 + i;
        }
        return -1;
    }

    // 辅助方法获取剩余时间
    public static long getRemainingTime(ItemStack stack) {
        if (stack.hasTagCompound() && stack.getTagCompound()
            .hasKey("LongData")) {
            return stack.getTagCompound()
                .getLong("LongData");
        }
        return 3600L; // 默认值
    }
}
