package com.wintercogs.beyonddimensions.common.block.entity;

import com.wintercogs.beyonddimensions.common.machine.FurnaceRecipeType;

public class NetSmokerBlockEntity extends BaseNetFurnaceBlockEntity {

    public NetSmokerBlockEntity() {
        super(FurnaceRecipeType.SMOKING, "menu.title.beyonddimensions.smoker_menu");
    }
}
