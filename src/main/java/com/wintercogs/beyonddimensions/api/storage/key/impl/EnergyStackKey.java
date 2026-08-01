package com.wintercogs.beyonddimensions.api.storage.key.impl;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import com.wintercogs.beyonddimensions.api.ids.BDConstants;
import com.wintercogs.beyonddimensions.api.longtype.EnergyType;
import com.wintercogs.beyonddimensions.api.storage.key.IStackRender;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.render.EnergyStackKeyRender;

import io.netty.buffer.ByteBuf;

public class EnergyStackKey extends LongStackKey<EnergyType> {

    public static final ResourceLocation ID = new ResourceLocation(BDConstants.MODID, "stack_type/energy");

    public static final EnergyStackKey INSTANCE = new EnergyStackKey();

    private EnergyStackKey() {
        this.stack = new EnergyType(0);
    }

    @Override
    @Nullable
    public KeyAmount fromStackObject(Object stack) {
        if (stack instanceof EnergyType) {
            EnergyType energyType = (EnergyType) stack;
            return new KeyAmount(EnergyStackKey.INSTANCE, energyType.getStackCount());
        }
        return null;
    }

    @Override
    public ResourceLocation getTypeID() {
        return ID;
    }

    @Override
    public long getVanillaMaxStackSize() {
        return 1000000;
    }

    @Override
    @Nullable
    public EnergyStackKey fromSourceObject(Object key, NBTTagCompound ignored) {
        if (key instanceof EnergyType || key instanceof Number) {
            return INSTANCE;
        }
        return null;
    }

    @Override
    @Nonnull
    public EnergyType getSource() {
        return this.stack;
    }

    @Override
    public String getModId() {
        return "Forge";
    }

    @Override
    public EnergyStackKey getEmpty() {
        return EnergyStackKey.INSTANCE;
    }

    @Override
    public EnergyType getEmptyStack() {
        return new EnergyType(0);
    }

    @Override
    public void serialize(ByteBuf buf) {}

    @Override
    @Nonnull
    public EnergyStackKey deserialize(ByteBuf buf) {
        return INSTANCE;
    }

    @Override
    @Nonnull
    public NBTTagCompound serializeNBT() {
        return new NBTTagCompound();
    }

    @Override
    @Nonnull
    public EnergyStackKey deserializeNBT(NBTTagCompound nbt) {
        return INSTANCE;
    }

    @Override
    @Nonnull
    public IStackRender getRender() {
        return EnergyStackKeyRender.INSTANCE;
    }
}
