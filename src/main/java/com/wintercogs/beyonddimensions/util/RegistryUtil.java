package com.wintercogs.beyonddimensions.util;

import net.minecraft.item.Item;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

/**
 * 1.7.10 没有 Holder API，提供兼容封装。
 */
public final class RegistryUtil {

    private RegistryUtil() {}

    public static String getItemId(Item item) {
        if (item == null) return "minecraft:air";
        return Item.itemRegistry.getNameForObject(item);
    }

    public static Item getItemById(String id) {
        if (id == null || id.isEmpty() || "minecraft:air".equals(id)) return null;
        return (Item) Item.itemRegistry.getObject(id);
    }

    public static String getFluidName(net.minecraftforge.fluids.Fluid fluid) {
        if (fluid == null) return "minecraft:empty";
        return FluidRegistry.getFluidName(fluid);
    }

    public static net.minecraftforge.fluids.Fluid getFluidByName(String name) {
        if (name == null || name.isEmpty()) return null;
        return FluidRegistry.getFluid(name);
    }

    public static boolean isEmptyFluidStack(FluidStack stack) {
        return stack == null || stack.getFluid() == null || stack.amount <= 0;
    }
}
