package com.wintercogs.beyonddimensions.common.block;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import com.wintercogs.beyonddimensions.BeyondDimensions;
import com.wintercogs.beyonddimensions.api.ids.BDBlockIds;
import com.wintercogs.beyonddimensions.api.ids.BDConstants;
import com.wintercogs.beyonddimensions.common.gui.BDGuiHandler;
import com.wintercogs.beyonddimensions.common.init.BDBlockEntities;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 维度网络能量通道方块（1.7.10 移植版）。
 * <p>
 * 对应源项目 1.20.1 的 {@code NetEnergyPathwayBlock}。
 * 源项目使用 {@code cube_all} JSON 模型（单一纹理覆盖所有面），
 * 1.7.10 通过 {@link #setBlockTextureName} 实现等效渲染。
 * <p>
 * 所有面使用相同纹理（{@code net_energy_pathway.png}）。
 */
public class NetEnergyPathwayBlock extends BaseMachineBlock {

    public NetEnergyPathwayBlock() {
        super();
        setBlockName(BDBlockIds.NET_ENERGY_PATHWAY);
        setBlockTextureName(BDConstants.MODID + ":" + BDBlockIds.NET_ENERGY_PATHWAY);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int metadata) {
        // 必须实例化 BDBlockEntities 注册的 TE 类（按环境可能是 RF/Mekanism 变体）。
        // 直接 new 基础类会使实例与注册映射不一致，1.7.10 在 TileEntity.writeToNBT
        // （含 getDescriptionPacket 同步）时抛 "missing a mapping! This is a bug!" 崩溃。
        return BDBlockEntities.createEnergyPathway();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        super.onBlockActivated(world, x, y, z, player, side, hitX, hitY, hitZ);
        if (!world.isRemote && !player.isSneaking()) {
            player.openGui(BeyondDimensions.instance, BDGuiHandler.NET_ENERGY_MENU, world, x, y, z);
        }
        return true;
    }

    // ==================== 纹理注册 ====================
    // 显式重写以下方法，确保 blockIcon 在任何环境下都能正确初始化，
    // 对齐 NetPumpBlock / NetHopperBlock / NetTerminalBlock 的修复模式。

    @SideOnly(Side.CLIENT)
    @Override
    public void registerBlockIcons(IIconRegister register) {
        this.blockIcon = register.registerIcon(this.textureName);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public IIcon getIcon(int side, int meta) {
        return this.blockIcon;
    }
}
