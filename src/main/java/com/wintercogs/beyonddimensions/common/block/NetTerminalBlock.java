package com.wintercogs.beyonddimensions.common.block;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import com.wintercogs.beyonddimensions.BeyondDimensions;
import com.wintercogs.beyonddimensions.api.ids.BDBlockIds;
import com.wintercogs.beyonddimensions.api.ids.BDConstants;
import com.wintercogs.beyonddimensions.common.block.entity.NetTerminalBlockEntity;
import com.wintercogs.beyonddimensions.common.gui.BDGuiHandler;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 网络终端方块。
 * 1.7.10 移植：使用 TESR 渲染源项目 OBJ 3D 模型（64x64 贴图，薄板面板）。
 * getRenderType=-1 禁用标准方块渲染，由 NetTerminalTESR 负责全部 3D 渲染。
 * 16x16 裁剪贴图仅用于破坏粒子效果。
 * 根据 metadata 控制面板朝向，碰撞箱随朝向调整。
 */
public class NetTerminalBlock extends NetedBlock {

    public NetTerminalBlock() {
        super();
        setBlockName(BDBlockIds.NET_TERMINAL_BLOCK);
        setBlockTextureName(BDConstants.MODID + ":net_terminal_block_texture");
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

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        int meta = world.getBlockMetadata(x, y, z);
        setBlockBoundsForMeta(meta);
    }

    @Override
    public void setBlockBoundsForItemRender() {
        setBlockBoundsForMeta(0);
    }

    /**
     * 根据朝向设置碰撞箱（薄板3像素厚）。对齐 NetTerminalTESR.applyFacingRotation 的旋转结果：
     * meta1 绕 Y -90° 后面板位于东侧 x[0.8125,1]，meta3 绕 Y +90° 后面板位于西侧 x[0,0.1875]。
     * 注意：meta 语义为"面板所在方位"（0=北/1=东/2=南/3=西），case 1 与 case 3 不可互换。
     */
    private void setBlockBoundsForMeta(int meta) {
        float thickness = 3f / 16f;
        switch (meta) {
            case 1: // east
                this.setBlockBounds(1f - thickness, 0f, 0f, 1f, 1f, 1f);
                break;
            case 2: // south
                this.setBlockBounds(0f, 0f, 1f - thickness, 1f, 1f, 1f);
                break;
            case 3: // west
                this.setBlockBounds(0f, 0f, 0f, thickness, 1f, 1f);
                break;
            default: // 0 = north
                this.setBlockBounds(0f, 0f, 0f, 1f, 1f, thickness);
                break;
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void registerBlockIcons(IIconRegister iconRegister) {
        this.blockIcon = iconRegister.registerIcon(BDConstants.MODID + ":net_terminal_block_texture");
    }

    @SideOnly(Side.CLIENT)
    @Override
    public IIcon getIcon(int side, int meta) {
        return this.blockIcon;
    }

    @Override
    public TileEntity createNewTileEntity(World world, int metadata) {
        return new NetTerminalBlockEntity();
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        // 设置朝向
        int facing = determineOrientation(world, x, y, z, placer);
        world.setBlockMetadataWithNotify(x, y, z, facing, 2);
        super.onBlockPlacedBy(world, x, y, z, placer, stack);
    }

    // 根据放置方向确定metadata
    public static int determineOrientation(World world, int x, int y, int z, EntityLivingBase placer) {
        int l = net.minecraft.util.MathHelper.floor_double((double) (placer.rotationYaw * 4.0F / 360.0F) + 0.5D) & 3;
        // 简化：使用水平朝向
        return l;
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        super.onBlockActivated(world, x, y, z, player, side, hitX, hitY, hitZ);
        if (!world.isRemote && !player.isSneaking()) {
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof NetTerminalBlockEntity) {
                NetTerminalBlockEntity blockEntity = (NetTerminalBlockEntity) te;
                if (blockEntity.getNet() != null) {
                    player.openGui(
                        BeyondDimensions.instance,
                        BDGuiHandler.DIMENSIONS_CRAFT_MENU_TERMINAL,
                        world,
                        x,
                        y,
                        z);
                } else {
                    player.addChatMessage(new ChatComponentTranslation("msg.beyonddimensions.item_need_bound"));
                }
            }
        }
        return true;
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, net.minecraft.block.Block block, int metadata) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof NetTerminalBlockEntity) {
            ((NetTerminalBlockEntity) te).dropContent();
        }
        super.breakBlock(world, x, y, z, block, metadata);
    }
}
