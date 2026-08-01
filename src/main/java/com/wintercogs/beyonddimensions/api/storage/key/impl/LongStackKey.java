package com.wintercogs.beyonddimensions.api.storage.key.impl;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.util.ResourceLocation;

import com.wintercogs.beyonddimensions.api.longtype.LongType;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;

public abstract class LongStackKey<T extends LongType<T>> implements IStackKey<T> {

    private static final long CUSTOM_MAX_STACK_SIZE = Long.MAX_VALUE;

    public abstract ResourceLocation getTypeID();

    protected T stack;

    // 惰性哈希缓存跨线程访问，置 volatile 保证可见性（审计 M1-8）
    protected volatile int hashCodeCache = 0;

    @Override
    public ResourceLocation getTypeId() {
        return getTypeID();
    }

    @Override
    public T getReadOnlyStack() {
        this.stack.setStackCount(1);
        return this.stack;
    }

    @Override
    @Nonnull
    public T getRenderStack() {
        this.stack.setStackCount(1);
        return this.stack;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<T> getStackClass() {
        return (Class<T>) stack.getClass();
    }

    @Override
    public Class<?> getSourceClass() {
        return stack.getClass();
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public T copyStack() {
        return copyStackWithCount(1L);
    }

    @Override
    @SuppressWarnings("unchecked")
    public T copyStackWithCount(long count) {
        return (T) stack.copyWithAmount(count);
    }

    @Override
    public long getVanillaMaxStackSize() {
        return Long.MAX_VALUE;
    }

    @Override
    public long getCustomMaxStackSize() {
        return CUSTOM_MAX_STACK_SIZE;
    }

    @Override
    public boolean isSame(IStackKey<?> other) {
        return other != null && Objects.equals(other.getTypeId(), this.getTypeId());
    }

    @Override
    public boolean isSameTypeSameComponents(IStackKey<?> other) {
        return isSame(other);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IStackKey)) return false;
        IStackKey<?> k = (IStackKey<?>) o;
        return Objects.equals(k.getTypeId(), this.getTypeId());
    }

    @Override
    public int hashCode() {
        if (hashCodeCache == 0) {
            hashCodeCache = 31 + Objects.hashCode(getTypeId());
        }
        return hashCodeCache;
    }
}
