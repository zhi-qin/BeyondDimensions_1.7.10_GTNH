package com.wintercogs.beyonddimensions.common.block;

import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class BaseMachineBlock extends NetedBlock {

    public BaseMachineBlock() {
        super(Material.rock);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int metadata) {
        return null;
    }
}
