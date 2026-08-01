package com.wintercogs.beyonddimensions.common.block;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.wintercogs.beyonddimensions.BeyondDimensions;
import com.wintercogs.beyonddimensions.api.ids.BDBlockIds;
import com.wintercogs.beyonddimensions.common.block.entity.NetBlastFurnaceBlockEntity;
import com.wintercogs.beyonddimensions.common.gui.BDGuiHandler;

public class NetBlastFurnaceBlock extends BaseNetFurnaceBlock {

    public NetBlastFurnaceBlock() {
        super();
        setBlockName(BDBlockIds.NET_BLAST_FURNACE_BLOCK);
    }

    @Override
    protected String getFrontTextureName() {
        return "net_blast_furnace_front";
    }

    @Override
    protected String getFrontOnTextureName() {
        return "net_blast_furnace_front_on";
    }

    @Override
    public TileEntity createNewTileEntity(World world, int metadata) {
        return new NetBlastFurnaceBlockEntity();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        super.onBlockActivated(world, x, y, z, player, side, hitX, hitY, hitZ);
        if (!world.isRemote && !player.isSneaking()) {
            player.openGui(BeyondDimensions.instance, BDGuiHandler.NET_FURNACE_MENU, world, x, y, z);
        }
        return true;
    }
}
