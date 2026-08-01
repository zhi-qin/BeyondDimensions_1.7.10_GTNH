package com.wintercogs.beyonddimensions.common.block;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.wintercogs.beyonddimensions.api.ids.BDBlockIds;
import com.wintercogs.beyonddimensions.api.ids.BDConstants;
import com.wintercogs.beyonddimensions.common.block.entity.NetPathwayBlockEntity;

public class NetPathwayBlock extends NetedBlock {

    public NetPathwayBlock() {
        super();
        setBlockName(BDBlockIds.NET_PATHWAY);
        setBlockTextureName(BDConstants.MODID + ":" + BDBlockIds.NET_PATHWAY);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int metadata) {
        return new NetPathwayBlockEntity();
    }
}
