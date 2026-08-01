package com.wintercogs.beyonddimensions.common.block.entity;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.energy.IEnergyHandler;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.util.BDMath;

/**
 * 网络通路方块实体（1.7.10 移植版）。
 * <p>
 * 1.20.1 原版通过 Forge Capability 系统将网络统一存储暴露给相邻方块。
 * 1.7.10 没有 Capability 系统，因此直接实现 {@link IInventory}（物品）、
 * {@link IFluidHandler}（流体）和 {@link IEnergyHandler}（能量），
 * 将调用委托给网络中的 {@link UnifiedStorage}。
 */
public class NetPathwayBlockEntity extends NetedBlockEntity implements IInventory, IFluidHandler, IEnergyHandler {

    public NetPathwayBlockEntity() {}

    // ==================== 网络存储访问 ====================

    private UnifiedStorage getStorage() {
        DimensionsNet net = getNet();
        return net != null ? net.getUnifiedStorage() : null;
    }

    /**
     * 获取统一存储中所有物品条目
     */
    private List<KeyAmount> getItemEntries() {
        List<KeyAmount> result = new ArrayList<>();
        UnifiedStorage storage = getStorage();
        if (storage == null) return result;
        for (KeyAmount ka : storage.getStorage()) {
            if (ka.key() instanceof ItemStackKey && !ka.isEmpty()) {
                result.add(ka);
            }
        }
        return result;
    }

    /**
     * 获取统一存储中所有流体条目
     */
    private List<KeyAmount> getFluidEntries() {
        List<KeyAmount> result = new ArrayList<>();
        UnifiedStorage storage = getStorage();
        if (storage == null) return result;
        for (KeyAmount ka : storage.getStorage()) {
            if (ka.key() instanceof FluidStackKey && !ka.isEmpty()) {
                result.add(ka);
            }
        }
        return result;
    }

    // ==================== IInventory（物品透传） ====================

    @Override
    public int getSizeInventory() {
        // 与 1.20.1 源码一致：未满时额外提供 1 个槽位用于插入
        UnifiedStorage storage = getStorage();
        if (storage == null) return 1;
        int count = getItemEntries().size();
        return storage.isFullSlotsSize() ? count : count + 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        List<KeyAmount> entries = getItemEntries();
        if (slot < 0 || slot >= entries.size()) return null;
        KeyAmount ka = entries.get(slot);
        ItemStack template = ((ItemStackKey) ka.key()).getReadOnlyStack();
        if (template == null) return null;
        ItemStack copy = template.copy();
        copy.stackSize = BDMath.clampLongToInt(ka.amount());
        return copy;
    }

    @Override
    public ItemStack decrStackSize(int slot, int count) {
        UnifiedStorage storage = getStorage();
        if (storage == null) return null;
        List<KeyAmount> entries = getItemEntries();
        if (slot < 0 || slot >= entries.size()) return null;
        KeyAmount ka = entries.get(slot);
        KeyAmount extracted = storage.extract(ka.key(), count, false, false);
        if (extracted.isEmpty()) return null;
        return ((ItemStackKey) extracted.key()).copyStackWithCount(extracted.amount());
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        // 通路方块没有 GUI，关闭时不取出物品
        return null;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        UnifiedStorage storage = getStorage();
        if (storage == null) return;

        // 先移除该槽位原有的物品
        List<KeyAmount> entries = getItemEntries();
        if (slot >= 0 && slot < entries.size()) {
            KeyAmount old = entries.get(slot);
            storage.extract(old.key(), old.amount(), false, false);
        }

        // 再插入新的物品（统一存储会自动合并同类）
        if (stack != null && stack.stackSize > 0) {
            storage.insert(new ItemStackKey(stack), stack.stackSize, false);
        }
        markDirty();
    }

    @Override
    public String getInventoryName() {
        return "container.beyonddimensions.net_pathway";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        UnifiedStorage storage = getStorage();
        if (storage == null) return 0;
        return BDMath.clampLongToInt(storage.getSlotCapacity(0));
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return true;
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return stack != null;
    }

    // ==================== IFluidHandler（流体透传） ====================

    @Override
    public int fill(ForgeDirection from, FluidStack resource, boolean doFill) {
        if (resource == null || resource.amount <= 0) return 0;
        UnifiedStorage storage = getStorage();
        if (storage == null) return 0;
        int amount = resource.amount;
        long remaining = storage.insert(new FluidStackKey(resource), amount, !doFill)
            .amount();
        long actual = amount - Math.min(remaining, amount);
        return BDMath.clampLongToInt(actual);
    }

    @Override
    public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain) {
        if (resource == null || resource.amount <= 0) return null;
        UnifiedStorage storage = getStorage();
        if (storage == null) return null;
        KeyAmount extracted = storage.extract(new FluidStackKey(resource), resource.amount, !doDrain, false);
        if (extracted.isEmpty()) return null;
        return ((FluidStackKey) extracted.key()).copyStackWithCount(extracted.amount());
    }

    @Override
    public FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain) {
        UnifiedStorage storage = getStorage();
        if (storage == null) return null;
        // 按数量导出时，尝试从第一个流体条目导出
        List<KeyAmount> entries = getFluidEntries();
        if (entries.isEmpty()) return null;
        KeyAmount first = entries.get(0);
        KeyAmount extracted = storage.extract(first.key(), maxDrain, !doDrain, false);
        if (extracted.isEmpty()) return null;
        return ((FluidStackKey) extracted.key()).copyStackWithCount(extracted.amount());
    }

    @Override
    public boolean canFill(ForgeDirection from, Fluid fluid) {
        return getStorage() != null;
    }

    @Override
    public boolean canDrain(ForgeDirection from, Fluid fluid) {
        return getStorage() != null;
    }

    @Override
    public FluidTankInfo[] getTankInfo(ForgeDirection from) {
        List<KeyAmount> entries = getFluidEntries();
        UnifiedStorage storage = getStorage();
        int capacity = storage != null ? BDMath.clampLongToInt(storage.getSlotCapacity(0)) : 0;
        FluidTankInfo[] infos = new FluidTankInfo[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            KeyAmount ka = entries.get(i);
            FluidStack fs = ((FluidStackKey) ka.key()).copyStackWithCount(BDMath.clampLongToInt(ka.amount()));
            infos[i] = new FluidTankInfo(fs, capacity);
        }
        return infos;
    }

    // ==================== IEnergyHandler（能量透传） ====================

    @Override
    public int receiveEnergy(ForgeDirection from, int maxReceive, boolean simulate) {
        UnifiedStorage storage = getStorage();
        if (storage == null) return 0;
        long remaining = storage.insert(EnergyStackKey.INSTANCE, maxReceive, simulate)
            .amount();
        return BDMath.clampLongToInt(maxReceive - Math.min(remaining, maxReceive));
    }

    @Override
    public int extractEnergy(ForgeDirection from, int maxExtract, boolean simulate) {
        UnifiedStorage storage = getStorage();
        if (storage == null) return 0;
        long extracted = storage.extract(EnergyStackKey.INSTANCE, maxExtract, simulate, false)
            .amount();
        return BDMath.clampLongToInt(extracted);
    }

    @Override
    public int getEnergyStored(ForgeDirection from) {
        UnifiedStorage storage = getStorage();
        if (storage == null) return 0;
        return BDMath.clampLongToInt(
            storage.getStackByKey(EnergyStackKey.INSTANCE)
                .amount());
    }

    @Override
    public int getMaxEnergyStored(ForgeDirection from) {
        UnifiedStorage storage = getStorage();
        if (storage == null) return 0;
        return BDMath.clampLongToInt(storage.getSlotCapacity(0));
    }

    @Override
    public boolean canConnectEnergy(ForgeDirection from) {
        return getStorage() != null;
    }
}
