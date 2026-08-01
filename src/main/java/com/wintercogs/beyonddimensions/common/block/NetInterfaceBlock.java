package com.wintercogs.beyonddimensions.common.block;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.wintercogs.beyonddimensions.BeyondDimensions;
import com.wintercogs.beyonddimensions.api.ids.BDBlockIds;
import com.wintercogs.beyonddimensions.api.ids.BDConstants;
import com.wintercogs.beyonddimensions.common.block.entity.NetInterfaceBlockEntity;
import com.wintercogs.beyonddimensions.common.gui.BDGuiHandler;

public class NetInterfaceBlock extends BaseMachineBlock {

    public NetInterfaceBlock() {
        super();
        setBlockName(BDBlockIds.NET_INTERFACE);
        setBlockTextureName(BDConstants.MODID + ":" + BDBlockIds.NET_INTERFACE);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int metadata) {
        return new NetInterfaceBlockEntity();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        super.onBlockActivated(world, x, y, z, player, side, hitX, hitY, hitZ);
        if (!world.isRemote && !player.isSneaking()) {
            player.openGui(BeyondDimensions.instance, BDGuiHandler.NET_INTERFACE_MENU, world, x, y, z);
        }
        return true;
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, net.minecraft.block.Block block, int metadata) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof NetInterfaceBlockEntity) {
            ((NetInterfaceBlockEntity) te).dropContent();
        }
        super.breakBlock(world, x, y, z, block, metadata);
    }

    @Override
    public boolean hasComparatorInputOverride() {
        return true;
    }

    @Override
    public int getComparatorInputOverride(World world, int x, int y, int z, int side) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof NetInterfaceBlockEntity) {
            return ((NetInterfaceBlockEntity) te).getRedstoneLevel();
        }
        return 0;
    }
}
