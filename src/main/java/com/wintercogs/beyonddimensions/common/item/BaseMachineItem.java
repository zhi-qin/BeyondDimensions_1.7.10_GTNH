package com.wintercogs.beyonddimensions.common.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.machine.AutoSortMode;
import com.wintercogs.beyonddimensions.common.machine.BaseMachine;
import com.wintercogs.beyonddimensions.common.machine.FeederMode;
import com.wintercogs.beyonddimensions.common.machine.FilterMode;
import com.wintercogs.beyonddimensions.common.machine.FuzzyMode;
import com.wintercogs.beyonddimensions.common.machine.HopperFluidMode;
import com.wintercogs.beyonddimensions.common.machine.HopperItemMode;
import com.wintercogs.beyonddimensions.common.machine.HopperNBTMode;
import com.wintercogs.beyonddimensions.common.machine.HopperRangeMode;
import com.wintercogs.beyonddimensions.common.machine.HopperXpMode;
import com.wintercogs.beyonddimensions.common.machine.PopMode;
import com.wintercogs.beyonddimensions.common.machine.ReceiveMode;
import com.wintercogs.beyonddimensions.common.machine.RedStoneControlMode;

public abstract class BaseMachineItem extends NetedItem implements BaseMachine {

    public BaseMachineItem() {
        super();
    }

    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int slotId, boolean isSelected) {
        super.onUpdate(stack, world, entity, slotId, isSelected);

        checkComponents(stack);

        if (world.isRemote) return;

        int ticks = getTicksPerWork(stack, world, entity, slotId, isSelected);
        if (ticks <= 0) {
            working(stack, world, entity, slotId, isSelected);
        } else if (world.getTotalWorldTime() % ticks == 0) {
            working(stack, world, entity, slotId, isSelected);
        }
    }

    public void checkComponents(ItemStack stack) {
        if (!hasControlMode(stack)) setControlMode(stack, RedStoneControlMode.IGNORE);
    }

    @Override
    public void workStart(ItemStack stack, World world, Entity holder, int slotId, boolean isSelected) {
        BaseMachine.super.workStart(stack, world, holder, slotId, isSelected);
    }

    @Override
    public RedStoneControlMode getControlMode() {
        return RedStoneControlMode.IGNORE;
    }

    @Override
    public RedStoneControlMode getControlMode(ItemStack stack) {
        return getControlModeOrDefault(stack, RedStoneControlMode.IGNORE);
    }

    @Override
    public boolean hasRedStoneSignal() {
        return false;
    }

    @Override
    public int getStepTick() {
        return 0;
    }

    @Override
    public void setStepTick(int newTick) {
        // 物品机器不依赖步进计数（由 getTicksPerWork 控制）
    }

    // ===== 红石控制模式 =====

    public static RedStoneControlMode getControlModeOrDefault(ItemStack stack,
        @Nullable RedStoneControlMode defaultValue) {
        if (stack != null && stack.hasTagCompound()
            && stack.getTagCompound()
                .hasKey("control_mode")) {
            try {
                return RedStoneControlMode.valueOf(
                    stack.getTagCompound()
                        .getString("control_mode"));
            } catch (IllegalArgumentException ignored) {}
        }
        return defaultValue;
    }

    public static void setControlMode(ItemStack stack, RedStoneControlMode newMode) {
        if (stack == null) return;
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound()
            .setString("control_mode", newMode.name());
    }

    public static boolean hasControlMode(ItemStack stack) {
        return stack != null && stack.hasTagCompound()
            && stack.getTagCompound()
                .hasKey("control_mode");
    }

    // ===== 过滤模式 =====

    public static FilterMode getFilterModeOrDefault(ItemStack stack, @Nullable FilterMode defaultValue) {
        if (stack != null && stack.hasTagCompound()
            && stack.getTagCompound()
                .hasKey("filter_mode")) {
            try {
                return FilterMode.valueOf(
                    stack.getTagCompound()
                        .getString("filter_mode"));
            } catch (IllegalArgumentException ignored) {}
        }
        return defaultValue;
    }

    public static void setFilterMode(ItemStack stack, FilterMode newMode) {
        if (stack == null) return;
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound()
            .setString("filter_mode", newMode.name());
    }

    public static boolean hasFilterMode(ItemStack stack) {
        return stack != null && stack.hasTagCompound()
            && stack.getTagCompound()
                .hasKey("filter_mode");
    }

    // ===== 弹出模式 =====

    public static PopMode getPopModeOrDefault(ItemStack stack, @Nullable PopMode defaultValue) {
        if (stack != null && stack.hasTagCompound()
            && stack.getTagCompound()
                .hasKey("pop_mode")) {
            try {
                return PopMode.valueOf(
                    stack.getTagCompound()
                        .getString("pop_mode"));
            } catch (IllegalArgumentException ignored) {}
        }
        return defaultValue;
    }

    public static void setPopMode(ItemStack stack, PopMode newMode) {
        if (stack == null) return;
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound()
            .setString("pop_mode", newMode.name());
    }

    public static boolean hasPopMode(ItemStack stack) {
        return stack != null && stack.hasTagCompound()
            && stack.getTagCompound()
                .hasKey("pop_mode");
    }

    // ===== 自动排序模式 =====

    public static AutoSortMode getAutoSortModeOrDefault(ItemStack stack, @Nullable AutoSortMode defaultValue) {
        if (stack != null && stack.hasTagCompound()
            && stack.getTagCompound()
                .hasKey("auto_sort_mode")) {
            try {
                return AutoSortMode.valueOf(
                    stack.getTagCompound()
                        .getString("auto_sort_mode"));
            } catch (IllegalArgumentException ignored) {}
        }
        return defaultValue;
    }

    public static void setAutoSortMode(ItemStack stack, AutoSortMode newMode) {
        if (stack == null) return;
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound()
            .setString("auto_sort_mode", newMode.name());
    }

    public static boolean hasAutoSortMode(ItemStack stack) {
        return stack != null && stack.hasTagCompound()
            && stack.getTagCompound()
                .hasKey("auto_sort_mode");
    }

    // ===== 漏斗：物品 =====

    public static HopperItemMode getHopperItemModeOrDefault(ItemStack stack, @Nullable HopperItemMode defaultValue) {
        if (stack != null && stack.hasTagCompound()
            && stack.getTagCompound()
                .hasKey("hopper_item_mode")) {
            try {
                return HopperItemMode.valueOf(
                    stack.getTagCompound()
                        .getString("hopper_item_mode"));
            } catch (IllegalArgumentException ignored) {}
        }
        return defaultValue;
    }

    public static void setHopperItemMode(ItemStack stack, HopperItemMode newMode) {
        if (stack == null) return;
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound()
            .setString("hopper_item_mode", newMode.name());
    }

    public static boolean hasHopperItemMode(ItemStack stack) {
        return stack != null && stack.hasTagCompound()
            && stack.getTagCompound()
                .hasKey("hopper_item_mode");
    }

    // ===== 漏斗：经验 =====

    public static HopperXpMode getHopperXpModeOrDefault(ItemStack stack, @Nullable HopperXpMode defaultValue) {
        if (stack != null && stack.hasTagCompound()
            && stack.getTagCompound()
                .hasKey("hopper_xp_mode")) {
            try {
                return HopperXpMode.valueOf(
                    stack.getTagCompound()
                        .getString("hopper_xp_mode"));
            } catch (IllegalArgumentException ignored) {}
        }
        return defaultValue;
    }

    public static void setHopperXpMode(ItemStack stack, HopperXpMode newMode) {
        if (stack == null) return;
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound()
            .setString("hopper_xp_mode", newMode.name());
    }

    public static boolean hasHopperXpMode(ItemStack stack) {
        return stack != null && stack.hasTagCompound()
            && stack.getTagCompound()
                .hasKey("hopper_xp_mode");
    }

    // ===== 漏斗：NBT =====

    public static HopperNBTMode getHopperNBTModeOrDefault(ItemStack stack, @Nullable HopperNBTMode defaultValue) {
        if (stack != null && stack.hasTagCompound()
            && stack.getTagCompound()
                .hasKey("hopper_nbt_mode")) {
            try {
                return HopperNBTMode.valueOf(
                    stack.getTagCompound()
                        .getString("hopper_nbt_mode"));
            } catch (IllegalArgumentException ignored) {}
        }
        return defaultValue;
    }

    public static void setHopperNBTMode(ItemStack stack, HopperNBTMode newMode) {
        if (stack == null) return;
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound()
            .setString("hopper_nbt_mode", newMode.name());
    }

    public static boolean hasHopperNBTMode(ItemStack stack) {
        return stack != null && stack.hasTagCompound()
            && stack.getTagCompound()
                .hasKey("hopper_nbt_mode");
    }

    // ===== 漏斗：流体 =====

    public static HopperFluidMode getHopperFluidModeOrDefault(ItemStack stack, @Nullable HopperFluidMode defaultValue) {
        if (stack != null && stack.hasTagCompound()
            && stack.getTagCompound()
                .hasKey("hopper_fluid_mode")) {
            try {
                return HopperFluidMode.valueOf(
                    stack.getTagCompound()
                        .getString("hopper_fluid_mode"));
            } catch (IllegalArgumentException ignored) {}
        }
        return defaultValue;
    }

    public static void setHopperFluidMode(ItemStack stack, HopperFluidMode newMode) {
        if (stack == null) return;
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound()
            .setString("hopper_fluid_mode", newMode.name());
    }

    public static boolean hasHopperFluidMode(ItemStack stack) {
        return stack != null && stack.hasTagCompound()
            && stack.getTagCompound()
                .hasKey("hopper_fluid_mode");
    }

    // ===== 漏斗：范围 =====

    public static HopperRangeMode getHopperRangeModeOrDefault(ItemStack stack, @Nullable HopperRangeMode defaultValue) {
        if (stack != null && stack.hasTagCompound()
            && stack.getTagCompound()
                .hasKey("hopper_range_mode")) {
            try {
                return HopperRangeMode.valueOf(
                    stack.getTagCompound()
                        .getString("hopper_range_mode"));
            } catch (IllegalArgumentException ignored) {}
        }
        return defaultValue;
    }

    public static void setHopperRangeMode(ItemStack stack, HopperRangeMode newMode) {
        if (stack == null) return;
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound()
            .setString("hopper_range_mode", newMode.name());
    }

    public static boolean hasHopperRangeMode(ItemStack stack) {
        return stack != null && stack.hasTagCompound()
            && stack.getTagCompound()
                .hasKey("hopper_range_mode");
    }

    // ===== 喂食：模式 =====

    public static FeederMode getFeederModeOrDefault(ItemStack stack, @Nullable FeederMode defaultValue) {
        if (stack != null && stack.hasTagCompound()
            && stack.getTagCompound()
                .hasKey("feeder_mode")) {
            try {
                return FeederMode.valueOf(
                    stack.getTagCompound()
                        .getString("feeder_mode"));
            } catch (IllegalArgumentException ignored) {}
        }
        return defaultValue;
    }

    public static void setFeederMode(ItemStack stack, FeederMode newMode) {
        if (stack == null) return;
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound()
            .setString("feeder_mode", newMode.name());
    }

    public static boolean hasFeederMode(ItemStack stack) {
        return stack != null && stack.hasTagCompound()
            && stack.getTagCompound()
                .hasKey("feeder_mode");
    }

    // ===== 补货：模糊模式 =====

    public static FuzzyMode getFuzzyModeOrDefault(ItemStack stack, @Nullable FuzzyMode defaultValue) {
        if (stack != null && stack.hasTagCompound()
            && stack.getTagCompound()
                .hasKey("fuzzy_mode")) {
            try {
                return FuzzyMode.valueOf(
                    stack.getTagCompound()
                        .getString("fuzzy_mode"));
            } catch (IllegalArgumentException ignored) {}
        }
        return defaultValue;
    }

    public static void setFuzzyMode(ItemStack stack, FuzzyMode newMode) {
        if (stack == null) return;
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound()
            .setString("fuzzy_mode", newMode.name());
    }

    public static boolean hasFuzzyMode(ItemStack stack) {
        return stack != null && stack.hasTagCompound()
            && stack.getTagCompound()
                .hasKey("fuzzy_mode");
    }

    // ===== 补货：回收模式 =====

    public static ReceiveMode getReceiveModeOrDefault(ItemStack stack, @Nullable ReceiveMode defaultValue) {
        if (stack != null && stack.hasTagCompound()
            && stack.getTagCompound()
                .hasKey("receive_mode")) {
            try {
                return ReceiveMode.valueOf(
                    stack.getTagCompound()
                        .getString("receive_mode"));
            } catch (IllegalArgumentException ignored) {}
        }
        return defaultValue;
    }

    public static void setReceiveMode(ItemStack stack, ReceiveMode newMode) {
        if (stack == null) return;
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound()
            .setString("receive_mode", newMode.name());
    }

    public static boolean hasReceiveMode(ItemStack stack) {
        return stack != null && stack.hasTagCompound()
            && stack.getTagCompound()
                .hasKey("receive_mode");
    }

    // ===== 标记槽位：过滤器列表 =====

    public static List<KeyAmount> getFilterSlotsOrDefault(ItemStack stack, @Nullable List<KeyAmount> defaultValue) {
        if (stack != null && stack.hasTagCompound()
            && stack.getTagCompound()
                .hasKey("filter_slots")) {
            NBTTagList tags = stack.getTagCompound()
                .getTagList("filter_slots", 10);
            List<KeyAmount> filterSlots = new ArrayList<>();
            for (int i = 0; i < tags.tagCount(); i++) {
                filterSlots.add(KeyAmount.deserializeNBT(tags.getCompoundTagAt(i)));
            }
            return filterSlots;
        }
        return defaultValue;
    }

    public static void setFilterSlots(ItemStack stack, List<KeyAmount> filterSlots) {
        if (stack == null) return;
        NBTTagList tags = new NBTTagList();
        for (KeyAmount ka : filterSlots) {
            tags.appendTag(KeyAmount.serializeNBT(ka));
        }
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound()
            .setTag("filter_slots", tags);
    }

    public static boolean hasFilterSlots(ItemStack stack) {
        return stack != null && stack.hasTagCompound()
            && stack.getTagCompound()
                .hasKey("filter_slots");
    }

    // ===== 静态机器模式字段（旧版兼容） =====

    public static int getMachineMode(ItemStack stack) {
        if (stack != null && stack.hasTagCompound()) {
            return stack.getTagCompound()
                .getInteger("MachineMode");
        }
        return 0;
    }

    public static void setMachineMode(ItemStack stack, int mode) {
        if (stack != null) {
            if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
            stack.getTagCompound()
                .setInteger("MachineMode", mode);
        }
    }

    /**
     * 供子类快速构造空过滤器槽位。
     */
    protected static List<KeyAmount> emptyFilterSlots(int capacity) {
        return new ArrayList<>(Collections.nCopies(capacity, new KeyAmount(ItemStackKey.EMPTY, 0L)));
    }
}
