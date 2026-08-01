package com.wintercogs.beyonddimensions.api.storage.key.impl;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import com.wintercogs.beyonddimensions.api.ids.BDConstants;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.IStackRender;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.render.EmptyStackKeyRender;

import io.netty.buffer.ByteBuf;

public final class EmptyStackKey implements IStackKey<EmptyStackKey.EmptyStackType> {

    public static final ResourceLocation ID = new ResourceLocation(BDConstants.MODID, "stack_type/empty");
    public static final EmptyStackKey INSTANCE = new EmptyStackKey();

    private EmptyStackKey() {}

    @Override
    public ResourceLocation getTypeId() {
        return ID;
    }

    @Override
    @Nullable
    public KeyAmount fromStackObject(Object stack) {
        if (stack instanceof EmptyStackType) return new KeyAmount(INSTANCE, 0);
        return null;
    }

    @Override
    @Nullable
    public EmptyStackKey fromSourceObject(Object key, NBTTagCompound dataComponentPatch) {
        if (key instanceof EmptyStackType) return INSTANCE;
        return null;
    }

    @Override
    public EmptyStackType getReadOnlyStack() {
        return EmptyStackType.INSTANCE;
    }

    @Override
    public Class<EmptyStackType> getStackClass() {
        return EmptyStackType.class;
    }

    @Override
    @Nonnull
    public Object getSource() {
        return EmptyStackType.INSTANCE;
    }

    @Override
    public Class<?> getSourceClass() {
        return EmptyStackType.class;
    }

    @Override
    public String getModId() {
        return BDConstants.MODID;
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public IStackKey<EmptyStackType> getEmpty() {
        return EmptyStackKey.INSTANCE;
    }

    @Override
    public EmptyStackType getEmptyStack() {
        return EmptyStackType.INSTANCE;
    }

    @Override
    public EmptyStackType copyStack() {
        return EmptyStackType.INSTANCE;
    }

    @Override
    public EmptyStackType copyStackWithCount(long count) {
        return EmptyStackType.INSTANCE;
    }

    @Override
    public long getVanillaMaxStackSize() {
        return 0;
    }

    @Override
    public long getCustomMaxStackSize() {
        return 0;
    }

    @Override
    public boolean isSame(IStackKey<?> other) {
        return other instanceof EmptyStackKey;
    }

    @Override
    public boolean isSameTypeSameComponents(IStackKey<?> other) {
        return other instanceof EmptyStackKey;
    }

    @Override
    public void serialize(ByteBuf buf) {}

    @Override
    @Nonnull
    public EmptyStackKey deserialize(ByteBuf buf) {
        return INSTANCE;
    }

    @Override
    @Nonnull
    public NBTTagCompound serializeNBT() {
        return new NBTTagCompound();
    }

    @Override
    @Nonnull
    public EmptyStackKey deserializeNBT(NBTTagCompound nbt) {
        return INSTANCE;
    }

    @Override
    @Nonnull
    public IStackRender getRender() {
        return EmptyStackKeyRender.INSTANCE;
    }

    @Override
    @Nonnull
    public EmptyStackKey.EmptyStackType getRenderStack() {
        return EmptyStackKey.EmptyStackType.INSTANCE;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof EmptyStackKey;
    }

    @Override
    public int hashCode() {
        return ID.hashCode();
    }

    public static class EmptyStackType {

        public static final EmptyStackType INSTANCE = new EmptyStackType();

        private EmptyStackType() {}
    }
}
