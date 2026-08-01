package com.wintercogs.beyonddimensions.integration.module.botania.storage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import com.wintercogs.beyonddimensions.api.ids.BDConstants;
import com.wintercogs.beyonddimensions.api.storage.key.IStackRender;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.LongStackKey;
import com.wintercogs.beyonddimensions.integration.OtherModIds;

import io.netty.buffer.ByteBuf;

/**
 * Botania Mana 的 StackKey 实现（1.7.10 适配版）。
 * <p>
 * Mana 是单一类型（无不同种类），因此 INSTANCE 是唯一实例。
 * 继承 LongStackKey 获得 LongType 的通用逻辑，仅需实现序列化与工厂方法。
 */
public class ManaStackKey extends LongStackKey<ManaType> {

    public static final ResourceLocation ID = new ResourceLocation(BDConstants.MODID, "stack_type/mana");

    /**
     * 唯一实例
     */
    public static final ManaStackKey INSTANCE = new ManaStackKey();

    private ManaStackKey() {
        this.stack = new ManaType(0);
    }

    @Override
    @Nullable
    public KeyAmount fromStackObject(Object stack) {
        if (stack instanceof ManaType) {
            ManaType manaType = (ManaType) stack;
            return new KeyAmount(ManaStackKey.INSTANCE, manaType.getStackCount());
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
    public ManaStackKey fromSourceObject(Object key, NBTTagCompound ignored) {
        if (key instanceof ManaType || key instanceof Number) {
            return INSTANCE;
        }
        return null;
    }

    @Override
    @Nonnull
    public ManaType getSource() {
        return this.stack;
    }

    @Override
    public String getModId() {
        return OtherModIds.BOTANIA;
    }

    @Override
    public ManaStackKey getEmpty() {
        return ManaStackKey.INSTANCE;
    }

    @Override
    public ManaType getEmptyStack() {
        return new ManaType(0);
    }

    @Override
    public void serialize(ByteBuf buf) {}

    @Override
    @Nonnull
    public ManaStackKey deserialize(ByteBuf buf) {
        return INSTANCE;
    }

    @Override
    @Nonnull
    public NBTTagCompound serializeNBT() {
        return new NBTTagCompound();
    }

    @Override
    @Nonnull
    public ManaStackKey deserializeNBT(NBTTagCompound nbt) {
        return INSTANCE;
    }

    @Override
    @Nonnull
    public IStackRender getRender() {
        return ManaStackKeyRender.INSTANCE;
    }
}
