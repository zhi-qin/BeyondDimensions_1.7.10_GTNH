package com.wintercogs.beyonddimensions.common.item;

import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;

import com.wintercogs.beyonddimensions.common.machine.XpTransferSpeedMode;

public final class XpExchangeSettings {

    public static final String XP_TARGET_LEVEL_TAG = "xp_target_level";
    public static final int DEFAULT_TARGET_LEVEL = 1;
    public static final int MAX_TARGET_LEVEL = 9999;

    private XpExchangeSettings() {}

    public static int sanitizeTargetLevel(int targetLevel) {
        return MathHelper.clamp_int(targetLevel, 0, MAX_TARGET_LEVEL);
    }

    public static int targetLevelFromLegacyMode(XpTransferSpeedMode legacyMode) {
        return switch (legacyMode) {
            case SLOW -> 1;
            case MID -> 10;
            case HIGH -> 30;
            case HIGHEST -> 100;
            case OVER_HIGHEST -> 150;
        };
    }

    public static boolean hasTargetLevel(ItemStack stack) {
        return stack != null && stack.hasTagCompound()
            && stack.getTagCompound()
                .hasKey(XP_TARGET_LEVEL_TAG);
    }

    public static int getTargetLevel(ItemStack stack) {
        if (hasTargetLevel(stack)) {
            return sanitizeTargetLevel(
                stack.getTagCompound()
                    .getInteger(XP_TARGET_LEVEL_TAG));
        }
        return targetLevelFromLegacyMode(
            XpExchangeItem.getOrDefaultXpTransferSpeedMode(stack, XpTransferSpeedMode.SLOW));
    }

    public static void setTargetLevel(ItemStack stack, int targetLevel) {
        if (stack == null) return;
        if (!stack.hasTagCompound()) stack.setTagCompound(new net.minecraft.nbt.NBTTagCompound());
        stack.getTagCompound()
            .setInteger(XP_TARGET_LEVEL_TAG, sanitizeTargetLevel(targetLevel));
    }

    public static void ensureComponents(ItemStack stack) {
        if (!XpExchangeItem.hasXpTransferSpeedMode(stack))
            XpExchangeItem.setXpTransferSpeedMode(stack, XpTransferSpeedMode.SLOW);

        if (!XpExchangeItem.hasXpNetKeepMode(stack)) XpExchangeItem.setXpNetKeepMode(stack, false);

        int targetLevel = getTargetLevel(stack);
        if (!hasTargetLevel(stack) || stack.getTagCompound()
            .getInteger(XP_TARGET_LEVEL_TAG) != targetLevel) {
            setTargetLevel(stack, targetLevel);
        }
    }
}
