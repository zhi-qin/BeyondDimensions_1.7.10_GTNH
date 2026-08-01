package com.wintercogs.beyonddimensions.common.block.entity;

import com.wintercogs.beyonddimensions.common.machine.FurnaceRecipeType;

public class NetFurnaceBlockEntity extends BaseNetFurnaceBlockEntity {

    public NetFurnaceBlockEntity() {
        super(FurnaceRecipeType.SMELTING, "menu.title.beyonddimensions.furnace_menu");
    }
}
