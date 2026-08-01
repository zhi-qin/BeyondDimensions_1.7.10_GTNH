package com.wintercogs.beyonddimensions.integration.module.botania.storage;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.wintercogs.beyonddimensions.api.longtype.LongType;

/**
 * Mana 类型封装（1.7.10 适配版）。
 * <p>
 * 继承 LongType 以便与 LongStackKey 协作。Botania 的 Mana 是单一类型，
 * 不区分种类，因此本类仅持有数量。
 */
public final class ManaType extends LongType<ManaType> {

    public ManaType(long amount) {
        this.stackCount = amount;
    }

    @Override
    public IChatComponent getName() {
        return new ChatComponentTranslation("types.beyonddimensions.mana_type.name");
    }

    @Override
    public ManaType getEmpty() {
        return new ManaType(0);
    }

    @Override
    public ManaType copy() {
        return new ManaType(stackCount);
    }

    @Override
    public ManaType copyWithAmount(long amount) {
        return new ManaType(amount);
    }
}
