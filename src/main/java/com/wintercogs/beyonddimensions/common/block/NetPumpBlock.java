package com.wintercogs.beyonddimensions.common.block;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import com.wintercogs.beyonddimensions.BeyondDimensions;
import com.wintercogs.beyonddimensions.api.ids.BDBlockIds;
import com.wintercogs.beyonddimensions.api.ids.BDConstants;
import com.wintercogs.beyonddimensions.common.block.entity.NetPumpBlockEntity;
import com.wintercogs.beyonddimensions.common.gui.BDGuiHandler;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 网络泵方块。
 * 1.7.10 移植：使用 TESR 渲染源项目 3D 模型（32x32 贴图 + Blockbench JSON，13 个元素）。
 * getRenderType=-1 禁用标准方块渲染，由 NetPumpTESR 负责全部 3D 渲染。
 * 16x16 裁剪贴图仅用于破坏粒子效果。
 */
public class NetPumpBlock extends BaseMachineBlock {

    public NetPumpBlock() {
        super();
        setBlockName(BDBlockIds.NET_PUMP_BLOCK);
        setBlockTextureName(BDConstants.MODID + ":" + BDBlockIds.NET_PUMP_BLOCK);
    }

    @Override
    public int getRenderType() {
        return -1;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void registerBlockIcons(IIconRegister iconRegister) {
        this.blockIcon = iconRegister.registerIcon(BDConstants.MODID + ":" + BDBlockIds.NET_PUMP_BLOCK);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public IIcon getIcon(int side, int meta) {
        return this.blockIcon;
    }

    @Override
    public TileEntity createNewTileEntity(World world, int metadata) {
        return new NetPumpBlockEntity();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        super.onBlockActivated(world, x, y, z, player, side, hitX, hitY, hitZ);
        if (!world.isRemote && !player.isSneaking()) {
            player.openGui(BeyondDimensions.instance, BDGuiHandler.NET_PUMP_MENU, world, x, y, z);
        }
        return true;
    }
}
