package com.wintercogs.beyonddimensions.common.init;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.BlockFluidClassic;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.wintercogs.beyonddimensions.api.ids.BDConstants;
import com.wintercogs.beyonddimensions.common.fluid.XpFluid;
import com.wintercogs.beyonddimensions.common.item.XpFluidBucket;

import cpw.mods.fml.common.registry.GameRegistry;

public class BDFluids {

    public static Fluid XP_FLUID;
    public static Block XP_FLUID_BLOCK;
    public static Item XP_FLUID_BUCKET;

    public static void register() {
        XP_FLUID = new XpFluid().setDensity(800)
            .setViscosity(1500)
            .setLuminosity(10);
        FluidRegistry.registerFluid(XP_FLUID);

        XP_FLUID_BLOCK = new BlockFluidClassic(XP_FLUID, Material.water).setBlockName("xp_fluid")
            .setBlockTextureName("beyonddimensions:xp_fluid");
        GameRegistry.registerBlock(XP_FLUID_BLOCK, "xp_fluid");
        XP_FLUID.setBlock(XP_FLUID_BLOCK);

        // 注册 XP 流体桶物品（对齐源项目的 BucketItem 注册）
        // 1.7.10 适配：XpFluidBucket（自定义 onItemRightClick 倒出行为，审计 M5-7）
        // 替代 Item.Properties.craftRemainder；FluidContainerRegistry.registerFluidContainer
        // 替代 ForgeFlowingFluid.Properties.bucket。
        XP_FLUID_BUCKET = new XpFluidBucket().setUnlocalizedName(BDConstants.MODID + "." + "xp_fluid_bucket")
            .setTextureName(BDConstants.MODID + ":" + "xp_fluid_bucket")
            .setCreativeTab(BDCreativeModeTabs.BEYOND_DIMENSIONS_ITEMS_TAB);
        GameRegistry.registerItem(XP_FLUID_BUCKET, "xp_fluid_bucket", BDConstants.MODID);

        // 注册流体-桶容器映射，使空桶可以舀取 XP 流体
        FluidContainerRegistry.registerFluidContainer(
            new FluidStack(XP_FLUID, FluidContainerRegistry.BUCKET_VOLUME),
            new ItemStack(XP_FLUID_BUCKET),
            new ItemStack(Items.bucket));
    }
}
