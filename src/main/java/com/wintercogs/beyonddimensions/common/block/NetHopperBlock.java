package com.wintercogs.beyonddimensions.common.block;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import com.wintercogs.beyonddimensions.BeyondDimensions;
import com.wintercogs.beyonddimensions.api.ids.BDBlockIds;
import com.wintercogs.beyonddimensions.api.ids.BDConstants;
import com.wintercogs.beyonddimensions.common.block.entity.NetHopperBlockEntity;
import com.wintercogs.beyonddimensions.common.gui.BDGuiHandler;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 网络漏斗方块。
 * 1.7.10 移植：使用 TESR 渲染源项目 3D 模型（64x64 贴图 + Blockbench JSON，6 个元素含旋转斜腿）。
 * getRenderType=-1 禁用标准方块渲染，由 NetHopperTESR 负责全部 3D 渲染。
 * 16x16 裁剪贴图仅用于破坏粒子效果。
 * 碰撞箱对齐源项目 VoxelShape SHAPE = Block.box(2, 0, 2, 14, 8, 14)，
 * 即方块下半部分（2/16, 0, 2/16）-（14/16, 8/16, 14/16）。
 */
public class NetHopperBlock extends BaseMachineBlock {

    public NetHopperBlock() {
        super();
        setBlockName(BDBlockIds.NET_HOPPER_BLOCK);
        setBlockTextureName(BDConstants.MODID + ":" + BDBlockIds.NET_HOPPER_BLOCK);
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

    /**
     * 对齐源项目碰撞箱：Block.box(2, 0, 2, 14, 8, 14)。
     * 漏斗主体仅占据方块下半部分（Y=0..8），四条斜腿在 X/Z 方向收缩到 2..14。
     */
    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        this.setBlockBounds(2f / 16f, 0f, 2f / 16f, 14f / 16f, 8f / 16f, 14f / 16f);
    }

    @Override
    public void setBlockBoundsForItemRender() {
        this.setBlockBounds(2f / 16f, 0f, 2f / 16f, 14f / 16f, 8f / 16f, 14f / 16f);
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
        this.setBlockBoundsBasedOnState(world, x, y, z);
        return super.getCollisionBoundingBoxFromPool(world, x, y, z);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void registerBlockIcons(IIconRegister iconRegister) {
        this.blockIcon = iconRegister.registerIcon(BDConstants.MODID + ":" + BDBlockIds.NET_HOPPER_BLOCK);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public IIcon getIcon(int side, int meta) {
        return this.blockIcon;
    }

    @Override
    public TileEntity createNewTileEntity(World world, int metadata) {
        return new NetHopperBlockEntity();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        super.onBlockActivated(world, x, y, z, player, side, hitX, hitY, hitZ);
        if (!world.isRemote && !player.isSneaking()) {
            player.openGui(BeyondDimensions.instance, BDGuiHandler.NET_HOPPER_MENU, world, x, y, z);
        }
        return true;
    }
}
