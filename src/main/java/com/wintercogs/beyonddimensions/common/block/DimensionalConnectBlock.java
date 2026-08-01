package com.wintercogs.beyonddimensions.common.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

public class DimensionalConnectBlock extends Block {

    public DimensionalConnectBlock(Material material) {
        super(material);
    }

    public DimensionalConnectBlock() {
        this(Material.rock);
    }
}
