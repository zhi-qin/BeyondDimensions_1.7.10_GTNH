package com.wintercogs.beyonddimensions.common.block;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.wintercogs.beyonddimensions.BeyondDimensions;
import com.wintercogs.beyonddimensions.api.ids.BDBlockIds;
import com.wintercogs.beyonddimensions.common.block.entity.NetSmokerBlockEntity;
import com.wintercogs.beyonddimensions.common.gui.BDGuiHandler;

public class NetSmokerBlock extends BaseNetFurnaceBlock {

    public NetSmokerBlock() {
        super();
        setBlockName(BDBlockIds.NET_SMOKER_BLOCK);
    }

    @Override
    protected String getFrontTextureName() {
        return "net_smoker_front";
    }

    @Override
    protected String getFrontOnTextureName() {
        return "net_smoker_front_on";
    }

    @Override
    public TileEntity createNewTileEntity(World world, int metadata) {
        return new NetSmokerBlockEntity();
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
