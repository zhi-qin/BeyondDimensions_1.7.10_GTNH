package com.wintercogs.beyonddimensions.common.block;

import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.wintercogs.beyonddimensions.BeyondDimensions;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.ids.BDBlockIds;
import com.wintercogs.beyonddimensions.api.ids.BDConstants;
import com.wintercogs.beyonddimensions.common.block.entity.NetControlBlockEntity;
import com.wintercogs.beyonddimensions.common.gui.BDGuiHandler;

public class NetControlBlock extends NetedBlock {

    public NetControlBlock() {
        super(Material.iron);
        setBlockName(BDBlockIds.NET_CONTROL);
        setBlockTextureName(BDConstants.MODID + ":" + BDBlockIds.NET_CONTROL);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int metadata) {
        return new NetControlBlockEntity();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        super.onBlockActivated(world, x, y, z, player, side, hitX, hitY, hitZ);
        if (!world.isRemote && !player.isSneaking()) {
            if (DimensionsNet.getNetFromPlayer(player) != null) {
                player.openGui(BeyondDimensions.instance, BDGuiHandler.NET_CONTROL_MENU, world, x, y, z);
            }
        }
        return true;
    }
}
