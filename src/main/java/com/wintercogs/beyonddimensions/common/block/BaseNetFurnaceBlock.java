package com.wintercogs.beyonddimensions.common.block;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import com.wintercogs.beyonddimensions.api.ids.BDConstants;
import com.wintercogs.beyonddimensions.common.block.entity.BaseNetFurnaceBlockEntity;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 网络熔炉类机器的通用方块逻辑。
 * 1.7.10 适配：实现 breakBlock 调用 dropContent，防止破坏时物品丢失。
 * 原版 1.20.1 使用 onRemove + BlockState 属性系统，1.7.10 简化为 metadata 朝向。
 */
public abstract class BaseNetFurnaceBlock extends BaseMachineBlock {

    protected IIcon frontIcon;
    protected IIcon frontOnIcon;
    protected IIcon topIcon;
    protected IIcon sideIcon;

    public BaseNetFurnaceBlock() {
        super();
    }

    /**
     * 熔炉点亮时发出亮度 13 的光（与原版熔炉一致）。
     * 1.7.10 通过 metadata 高位（值为 8）表示点亮状态。
     */
    @Override
    public int getLightValue(net.minecraft.world.IBlockAccess world, int x, int y, int z) {
        return (world.getBlockMetadata(x, y, z) & 8) != 0 ? 13 : 0;
    }

    /** 正面纹理名（如 net_furnace_front） */
    protected abstract String getFrontTextureName();

    /** 点亮正面纹理名（如 net_furnace_front_on） */
    protected abstract String getFrontOnTextureName();

    /**
     * 方块被破坏时调用，掉落熔炉内部存储的所有物品。
     * 1.7.10 的 breakBlock 在方块被移除前调用，此时 TE 仍然存在。
     */
    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof BaseNetFurnaceBlockEntity) {
            BaseNetFurnaceBlockEntity blockEntity = (BaseNetFurnaceBlockEntity) te;
            blockEntity.dropContent();
        }
        super.breakBlock(world, x, y, z, block, meta);
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, x, y, z, placer, stack);
        int facing = MathHelper.floor_double((placer.rotationYaw * 4.0F / 360.0F) + 0.5D) & 3;
        // 1.7.10 熔炉朝向：2=南, 3=北, 4=东, 5=西（与原版熔炉一致）
        int meta;
        switch (facing) {
            case 0:
                meta = 2;
                break;
            case 1:
                meta = 5;
                break;
            case 2:
                meta = 3;
                break;
            case 3:
                meta = 4;
                break;
            default:
                meta = 2;
        }
        world.setBlockMetadataWithNotify(x, y, z, meta, 2);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void registerBlockIcons(IIconRegister iconRegister) {
        String base = BDConstants.MODID + ":";
        this.blockIcon = iconRegister.registerIcon(base + getFrontTextureName());
        this.frontIcon = iconRegister.registerIcon(base + getFrontTextureName());
        this.frontOnIcon = iconRegister.registerIcon(base + getFrontOnTextureName());
        this.topIcon = iconRegister.registerIcon(base + "mana_pool_top");
        this.sideIcon = iconRegister.registerIcon(base + "mana_pool_top");
    }

    @SideOnly(Side.CLIENT)
    @Override
    public IIcon getIcon(int side, int meta) {
        // 物品栏中 meta 为 0，此时让侧面显示正面纹理
        if (meta == 0) {
            if (side == 0 || side == 1) {
                return this.topIcon;
            }
            return this.frontIcon;
        }

        // metadata 高位（8）表示点亮状态，低 3 位表示朝向
        boolean lit = (meta & 8) != 0;
        int facing = meta & 7;

        // top / bottom
        if (side == 0 || side == 1) {
            return this.topIcon;
        }
        // front face matches metadata value in vanilla furnace convention
        if (side == facing) {
            return lit ? this.frontOnIcon : this.frontIcon;
        }
        return this.sideIcon;
    }
}
