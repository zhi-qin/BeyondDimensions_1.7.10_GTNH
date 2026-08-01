package com.wintercogs.beyonddimensions.common.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.wintercogs.beyonddimensions.BeyondDimensions;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.common.gui.BDGuiHandler;
import com.wintercogs.beyonddimensions.common.init.BDFluids;
import com.wintercogs.beyonddimensions.common.machine.XpTransferSpeedMode;
import com.wintercogs.beyonddimensions.util.BDMath;
import com.wintercogs.beyonddimensions.util.XpUtil;

public class XpExchangeItem extends NetedItem {

    public static List<Fluid> xpFluids = new ArrayList<Fluid>();

    public XpExchangeItem() {
        super();
        setMaxStackSize(1);
    }

    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int slotId, boolean isSelected) {
        super.onUpdate(stack, world, entity, slotId, isSelected);
        checkComponents(stack);
        if (xpFluids.isEmpty()) xpFluids = getExperienceFluids();
        if (entity instanceof EntityPlayer player && !world.isRemote && getOrDefaultXpNetKeepMode(stack, false)) {
            keepXpLevel(stack, player, world);
        }
    }

    private void checkComponents(ItemStack stack) {
        XpExchangeSettings.ensureComponents(stack);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        String[] lines = StatCollector.translateToLocal("tooltip.beyonddimensions.item.xp_exchange")
            .split("\\\\n");
        Collections.addAll(tooltip, lines);
    }

    public static int getConversionRate() {
        return 20;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (player.isSneaking()) {
            return super.onItemRightClick(stack, world, player);
        }

        if (world.isRemote) {
            return stack;
        }

        checkComponents(stack);
        player.openGui(
            BeyondDimensions.instance,
            BDGuiHandler.XP_EXCHANGE_MENU,
            world,
            (int) player.posX,
            (int) player.posY,
            (int) player.posZ);
        return stack;
    }

    private void keepXpLevel(ItemStack stack, EntityPlayer player, World world) {
        if (world.isRemote) return;

        DimensionsNet net = NetedItem.getNet(stack);
        if (net == null) return;

        final int conversionRate = getConversionRate();
        final double currentLevel = XpUtil.levelAsDouble(player);
        final int targetLevel = getXpLevelPerAction(stack);
        final UnifiedStorage storage = net.getUnifiedStorage();

        Fluid canonicalXp = BDFluids.XP_FLUID;
        if (canonicalXp == null) return;

        if (currentLevel > targetLevel) {
            long needRemoveXp = XpUtil.xpExcessAbove(currentLevel, targetLevel);
            int toRemoveXp = BDMath.clampLongToInt(needRemoveXp);

            long toInsertUnits = (long) toRemoveXp * conversionRate;
            KeyAmount remaining = storage
                .insert(new FluidStackKey(new FluidStack(canonicalXp, 1)), toInsertUnits, false);

            if (!remaining.isEmpty()) {
                int overflowXp = BDMath.clampLongToInt(remaining.amount() / conversionRate);
                toRemoveXp -= overflowXp;
            }

            if (toRemoveXp != 0) {
                XpUtil.removePlayerXp(player, toRemoveXp);
            }
        } else if (currentLevel < targetLevel) {
            long needAddXp = XpUtil.xpToReachAtLeast(currentLevel, targetLevel);
            int remainingXp = BDMath.clampLongToInt(needAddXp);
            int gainedXpTotal = 0;

            for (Fluid f : xpFluids) {
                if (remainingXp <= 0) break;
                if (f == null) continue;

                long wantUnits = (long) remainingXp * conversionRate;
                if (wantUnits <= 0) break;

                KeyAmount extracted = storage.extract(new FluidStackKey(new FluidStack(f, 1)), wantUnits, false, false);

                if (extracted.isEmpty()) continue;

                long units = extracted.amount();
                int gainedXp = BDMath.clampLongToInt(units / conversionRate);
                if (gainedXp <= 0) {
                    storage.insert(new FluidStackKey(new FluidStack(f, 1)), units, false);
                    continue;
                }

                long consumedUnits = (long) gainedXp * conversionRate;
                long remainderUnits = units - consumedUnits;
                if (remainderUnits > 0) {
                    storage.insert(new FluidStackKey(new FluidStack(f, 1)), remainderUnits, false);
                }

                gainedXpTotal += gainedXp;
                remainingXp -= gainedXp;
            }

            if (gainedXpTotal > 0) {
                XpUtil.addPlayerXp(player, gainedXpTotal);
            }
        }
    }

    private List<Fluid> getExperienceFluids() {
        Fluid canonicalXp = BDFluids.XP_FLUID;
        if (canonicalXp != null) {
            List<Fluid> list = new ArrayList<Fluid>();
            list.add(canonicalXp);
            return list;
        }
        return new ArrayList<Fluid>();
    }

    public static int getXpLevelPerAction(ItemStack stack) {
        if (stack != null && stack.getItem() instanceof XpExchangeItem) {
            XpExchangeSettings.ensureComponents(stack);
            return XpExchangeSettings.getTargetLevel(stack);
        }
        return 0;
    }

    public static XpTransferSpeedMode getOrDefaultXpTransferSpeedMode(ItemStack stack,
        XpTransferSpeedMode defaultValue) {
        if (stack != null && stack.hasTagCompound()
            && stack.getTagCompound()
                .hasKey("xp_transfer_speed_mode")) {
            try {
                return XpTransferSpeedMode.valueOf(
                    stack.getTagCompound()
                        .getString("xp_transfer_speed_mode"));
            } catch (IllegalArgumentException ignored) {}
        }
        return defaultValue;
    }

    public static boolean hasXpTransferSpeedMode(ItemStack stack) {
        return stack != null && stack.hasTagCompound()
            && stack.getTagCompound()
                .hasKey("xp_transfer_speed_mode");
    }

    public static void setXpTransferSpeedMode(ItemStack stack, XpTransferSpeedMode newMode) {
        if (stack == null) return;
        if (!stack.hasTagCompound()) stack.setTagCompound(new net.minecraft.nbt.NBTTagCompound());
        stack.getTagCompound()
            .setString("xp_transfer_speed_mode", newMode.name());
    }

    public static boolean getOrDefaultXpNetKeepMode(ItemStack stack, boolean defaultValue) {
        if (stack != null && stack.hasTagCompound()
            && stack.getTagCompound()
                .hasKey("xp_net_keep_mode")) {
            return stack.getTagCompound()
                .getBoolean("xp_net_keep_mode");
        }
        return defaultValue;
    }

    public static boolean hasXpNetKeepMode(ItemStack stack) {
        return stack != null && stack.hasTagCompound()
            && stack.getTagCompound()
                .hasKey("xp_net_keep_mode");
    }

    public static void setXpNetKeepMode(ItemStack stack, boolean newMode) {
        if (stack == null) return;
        if (!stack.hasTagCompound()) stack.setTagCompound(new net.minecraft.nbt.NBTTagCompound());
        stack.getTagCompound()
            .setBoolean("xp_net_keep_mode", newMode);
    }
}
