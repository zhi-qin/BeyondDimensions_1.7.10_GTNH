package com.wintercogs.beyonddimensions.api.longtype;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

public final class EnergyType extends LongType<EnergyType> {

    public EnergyType(long amount) {
        this.stackCount = amount;
    }

    @Override
    public IChatComponent getName() {
        return new ChatComponentTranslation("types.beyonddimensions.energytype.name");
    }

    @Override
    public EnergyType getEmpty() {
        return new EnergyType(0);
    }

    @Override
    public EnergyType copy() {
        return new EnergyType(stackCount);
    }

    @Override
    public EnergyType copyWithAmount(long amount) {
        return new EnergyType(amount);
    }
}
