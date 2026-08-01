package com.wintercogs.beyonddimensions.common.block.entity;

import net.minecraft.nbt.NBTTagCompound;

import com.wintercogs.beyonddimensions.common.machine.BaseMachine;
import com.wintercogs.beyonddimensions.common.machine.RedStoneControlMode;

public class BaseMachineBlockEntity extends NetedBlockEntity implements BaseMachine {

    public RedStoneControlMode controlMode = RedStoneControlMode.IGNORE;
    public int stepTick = 0;

    @Override
    public void updateEntity() {
        super.updateEntity();
        if (!worldObj.isRemote) {
            BaseMachine.super.working();
        }
    }

    @Override
    public RedStoneControlMode getControlMode() {
        return controlMode;
    }

    @Override
    public boolean hasRedStoneSignal() {
        return worldObj.isBlockIndirectlyGettingPowered(xCoord, yCoord, zCoord);
    }

    @Override
    public int getStepTick() {
        return stepTick;
    }

    @Override
    public void setStepTick(int newTick) {
        this.stepTick = newTick;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        String controlModeStr = nbt.getString("control_mode");
        if (controlModeStr != null && !controlModeStr.isEmpty()) {
            try {
                controlMode = RedStoneControlMode.valueOf(controlModeStr);
            } catch (IllegalArgumentException ignored) {
                controlMode = RedStoneControlMode.IGNORE;
            }
        }
        stepTick = nbt.getInteger("step_tick");
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setString("control_mode", controlMode.name());
        nbt.setInteger("step_tick", stepTick);
    }
}
