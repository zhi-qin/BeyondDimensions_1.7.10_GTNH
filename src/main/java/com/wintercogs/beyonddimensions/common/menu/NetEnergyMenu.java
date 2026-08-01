package com.wintercogs.beyonddimensions.common.menu;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.nbt.NBTTagCompound;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import com.wintercogs.beyonddimensions.common.block.entity.NetEnergyPathwayBlockEntity;
import com.wintercogs.beyonddimensions.common.machine.PopMode;
import com.wintercogs.beyonddimensions.common.machine.RedStoneControlMode;

/**
 * 网络能量通路菜单（1.7.10 移植版）。
 * <p>
 * 对应源项目 1.20.1 的 {@code NetEnergyMenu}。
 * 仅显示玩家背包槽位（无自定义存储槽），通过 QuickDataTag 同步能量数据与状态按钮。
 * 1.7.10 适配：{@code be.isInvalid()} 替代 {@code be.isRemoved()}。
 */
public class NetEnergyMenu extends BDBaseMenu {

    public NetEnergyPathwayBlockEntity be;

    public long lastEnergyCapacity = 0;
    public long lastEnergyStored = 0;
    public long lastEnergySpeedState = 0;

    /** EU 池存量（十进制字符串），由 QuickDataTag 同步（移植新增，供通道 GUI 显示）。 */
    public String euAmount = "0";

    /** 服务端 EU 存量变化跟踪（上次已发送的十进制字符串；null 强制首包发送）。 */
    private String lastEuAmountSent = null;

    /**
     * 客户端构造函数
     */
    public NetEnergyMenu(InventoryPlayer playerInventory) {
        this(playerInventory, null);
    }

    /**
     * 服务端构造函数
     */
    public NetEnergyMenu(InventoryPlayer playerInventory, NetEnergyPathwayBlockEntity be) {
        super(playerInventory);

        this.be = be;

        inventoryStartIndex = this.inventorySlots.size();
        // 对齐源项目 NetEnergyMenu：仅含玩家背包槽位
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlotToContainer(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 93 + row * 18));
            }
        }

        for (int col = 0; col < 9; ++col) {
            this.addSlotToContainer(new Slot(playerInventory, col, 8 + col * 18, 151));
        }
        inventoryEndIndex = this.inventorySlots.size();
    }

    @Override
    protected boolean shouldSendQuickData() {
        if (be == null) return false;
        DimensionsNet netCache = be.getNet();
        if (netCache != null) {
            UnifiedStorage storage = netCache.getUnifiedStorage();
            if (storage == null) return false;
            long currentStored = getEnergyStored(storage);
            long currentCapacity = storage.getSlotCapacity(0);
            long currentSpeed = currentStored - lastEnergyStored;
            // EU 池存量变化检测（十进制字符串比较，仅变化时触发发包）
            String currentEu = netCache.getEuStorage()
                .getAmount()
                .toString();
            boolean euChanged = !currentEu.equals(lastEuAmountSent);
            if (lastEnergyStored != currentStored || lastEnergyCapacity != currentCapacity
                || lastEnergySpeedState != currentSpeed
                || euChanged) {
                lastEnergySpeedState = currentSpeed;
                lastEnergyStored = currentStored;
                lastEnergyCapacity = currentCapacity;
                lastEuAmountSent = currentEu;
                return true;
            }
        } else {
            String zero = "0";
            boolean euChanged = !zero.equals(lastEuAmountSent);
            if (lastEnergyStored != 0 || lastEnergyCapacity != 0 || lastEnergySpeedState != 0 || euChanged) {
                lastEnergySpeedState = 0;
                lastEnergyStored = 0;
                lastEnergyCapacity = 0;
                lastEuAmountSent = zero;
                return true;
            }
        }
        return false;
    }

    @Override
    protected void writeQuickDataTag(NBTTagCompound tag) {
        super.writeQuickDataTag(tag);
        if (be == null) {
            tag.setString("popMode", PopMode.STOP.name());
            tag.setString("controlMode", RedStoneControlMode.IGNORE.name());
            tag.setLong("lastEnergyCapacity", 0L);
            tag.setLong("lastEnergySpeedState", 0L);
            tag.setLong("lastEnergyStored", 0L);
            tag.setString("euAmount", "0");
            tag.setBoolean("activePull", false);
            return;
        }
        tag.setString(
            "popMode",
            be.getPopMode()
                .name());
        tag.setString("controlMode", be.controlMode.name());
        tag.setLong("lastEnergyCapacity", lastEnergyCapacity);
        tag.setLong("lastEnergySpeedState", lastEnergySpeedState);
        tag.setLong("lastEnergyStored", lastEnergyStored);
        tag.setString("euAmount", lastEuAmountSent == null ? "0" : lastEuAmountSent);
        tag.setBoolean("activePull", be.getActivePull());
    }

    @Override
    public void readQuickDataTag(NBTTagCompound tag) {
        super.readQuickDataTag(tag);
        if (player == null || player.worldObj == null) return;
        if (player.worldObj.isRemote) {
            // 客户端：更新显示数据
            this.lastEnergyStored = tag.getLong("lastEnergyStored");
            this.lastEnergyCapacity = tag.getLong("lastEnergyCapacity");
            this.lastEnergySpeedState = tag.getLong("lastEnergySpeedState");
            this.euAmount = tag.getString("euAmount");
        } else if (be != null) {
            // 服务端：应用状态变更并通知客户端
            String popModeStr = tag.getString("popMode");
            String controlModeStr = tag.getString("controlMode");
            boolean changed = false;
            if (popModeStr != null && !popModeStr.isEmpty()) {
                try {
                    PopMode newMode = PopMode.valueOf(popModeStr);
                    if (be.getPopMode() != newMode) {
                        be.setPopMode(newMode);
                        changed = true;
                    }
                } catch (IllegalArgumentException ignored) {
                    // 保持原状态
                }
            }
            if (controlModeStr != null && !controlModeStr.isEmpty()) {
                try {
                    RedStoneControlMode newMode = RedStoneControlMode.valueOf(controlModeStr);
                    if (be.controlMode != newMode) {
                        be.controlMode = newMode;
                        changed = true;
                    }
                } catch (IllegalArgumentException ignored) {
                    // 保持原状态
                }
            }
            boolean activePull = tag.getBoolean("activePull");
            if (be.getActivePull() != activePull) {
                be.setActivePull(activePull);
                changed = true;
            }
            if (changed) {
                be.markDirty();
                // 对齐源项目 sendBlockUpdated 的语义：通知客户端该方块状态已变化
                player.worldObj.markBlockForUpdate(be.xCoord, be.yCoord, be.zCoord);
            }
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return be != null && !be.isInvalid();
    }

    long getEnergyStored(UnifiedStorage storage) {
        return storage.getStackByKey(EnergyStackKey.INSTANCE)
            .amount();
    }
}
