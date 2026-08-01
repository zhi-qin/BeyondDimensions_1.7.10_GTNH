package com.wintercogs.beyonddimensions.common.init;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * 配方注册类，移植自 1.20.1 的 ModRecipeProvider（datagen）。
 * <p>
 * 1.7.10 中使用 GameRegistry.addRecipe() / addSmelting() 手动注册。
 * <p>
 * 适配说明（1.20.1 → 1.7.10）：
 * <ul>
 * <li>NETHERITE_INGOT → Items.diamond（1.7.10 无下界合金）</li>
 * <li>AMETHYST_SHARD → Items.quartz（1.7.10 无紫水晶碎片）</li>
 * <li>COPPER_INGOT → Items.iron_ingot（1.7.10 原版无铜锭）</li>
 * <li>BLAST_FURNACE → Blocks.furnace（1.7.10 原版无高炉方块）</li>
 * <li>SMOKER → Blocks.furnace（1.7.10 原版无烟熏炉方块）</li>
 * </ul>
 */
public class BDRecipes {

    private BDRecipes() {}

    public static void registerRecipes() {
        registerShapedRecipes();
        registerSmeltingRecipes();
    }

    private static void registerShapedRecipes() {
        // ==================== 基础材料 ====================

        // 不稳定时空碎片
        // 原版: A=Diamond, B=TNT, C=NetherStar
        GameRegistry.addRecipe(
            new ItemStack(BDItems.UNSTABLE_SPACE_TIME_FRAGMENT, 1),
            "ABA",
            "BCB",
            "ABA",
            'A',
            Items.diamond,
            'B',
            Blocks.tnt,
            'C',
            Items.nether_star);

        // 时空稳定框架
        // 原版: A=SPACE_TIME_BAR, B=Redstone, C=EnderEye
        GameRegistry.addRecipe(
            new ItemStack(BDItems.SPACE_TIME_STABLE_FRAME, 1),
            "ABA",
            "BCB",
            "ABA",
            'A',
            BDItems.SPACE_TIME_BAR,
            'B',
            Items.redstone,
            'C',
            Items.ender_eye);

        // ==================== 网络创建/管理物品 ====================

        // 网络创建器
        // 原版: A=NETHERITE_INGOT(→Diamond), B=EnderEye, C=EnderPearl, D=STABLE_SPACE_TIME_FRAGMENT
        GameRegistry.addRecipe(
            new ItemStack(BDItems.NET_CREATER, 1),
            "ABA",
            "CDC",
            "ACA",
            'A',
            Items.diamond, // 1.20.1 为 NETHERITE_INGOT
            'B',
            Items.ender_eye,
            'C',
            Items.ender_pearl,
            'D',
            BDItems.STABLE_SPACE_TIME_FRAGMENT);

        // 网络成员邀请器
        // 原版: A=AMETHYST_SHARD(→Quartz), B=IronIngot, C=SPACE_TIME_STABLE_FRAME
        GameRegistry.addRecipe(
            new ItemStack(BDItems.NET_MEMBER_INVITER, 1),
            " A ",
            "BCB",
            " B ",
            'A',
            Items.quartz, // 1.20.1 为 AMETHYST_SHARD
            'B',
            Items.iron_ingot,
            'C',
            BDItems.SPACE_TIME_STABLE_FRAME);

        // 网络管理员邀请器
        // 原版: A=AMETHYST_SHARD(→Quartz), B=GoldIngot, C=SPACE_TIME_STABLE_FRAME
        GameRegistry.addRecipe(
            new ItemStack(BDItems.NET_MANAGER_INVITER, 1),
            " A ",
            "BCB",
            " B ",
            'A',
            Items.quartz, // 1.20.1 为 AMETHYST_SHARD
            'B',
            Items.gold_ingot,
            'C',
            BDItems.SPACE_TIME_STABLE_FRAME);

        // ==================== 网络通路方块 ====================

        // 网络通路
        // 原版: A=SPACE_TIME_BAR, B=SPACE_TIME_STABLE_FRAME, C=EnderPearl, D=EnderEye
        GameRegistry.addRecipe(
            new ItemStack(BDBlocks.NET_PATHWAY, 1),
            "ABA",
            "CDC",
            "ABA",
            'A',
            BDItems.SPACE_TIME_BAR,
            'B',
            BDItems.SPACE_TIME_STABLE_FRAME,
            'C',
            Items.ender_pearl,
            'D',
            Items.ender_eye);

        // 网络能量通路
        // 原版: A=SPACE_TIME_BAR, B=SPACE_TIME_STABLE_FRAME, C=COPPER_INGOT(→Iron), D=EnderEye
        GameRegistry.addRecipe(
            new ItemStack(BDBlocks.NET_ENERGY_PATHWAY, 1),
            "ABA",
            "CDC",
            "ABA",
            'A',
            BDItems.SPACE_TIME_BAR,
            'B',
            BDItems.SPACE_TIME_STABLE_FRAME,
            'C',
            Items.iron_ingot, // 1.20.1 为 COPPER_INGOT
            'D',
            Items.ender_eye);

        // 维度连接方块
        // 原版: A=SPACE_TIME_BAR, B=IronIngot, C=SPACE_TIME_STABLE_FRAME
        GameRegistry.addRecipe(
            new ItemStack(BDBlocks.DIMENSIONAL_CONNECT_BLOCK, 1),
            "ABA",
            "BCB",
            "ABA",
            'A',
            BDItems.SPACE_TIME_BAR,
            'B',
            Items.iron_ingot,
            'C',
            BDItems.SPACE_TIME_STABLE_FRAME);

        // 网络接口
        // 原版: A=IronIngot, B=SPACE_TIME_STABLE_FRAME, C=Piston, D=RedstoneTorch, E=StickyPiston
        GameRegistry.addRecipe(
            new ItemStack(BDBlocks.NET_INTERFACE, 1),
            "ABA",
            "CDE",
            "ABA",
            'A',
            Items.iron_ingot,
            'B',
            BDItems.SPACE_TIME_STABLE_FRAME,
            'C',
            Blocks.piston,
            'D',
            Blocks.redstone_torch,
            'E',
            Blocks.sticky_piston);

        // 网络控制器
        // 原版: A=IronIngot, B=Comparator, C=Repeater, D=SPACE_TIME_STABLE_FRAME
        GameRegistry.addRecipe(
            new ItemStack(BDBlocks.NET_CONTROL, 1),
            "ABA",
            "CDC",
            "ABA",
            'A',
            Items.iron_ingot,
            'B',
            Items.comparator,
            'C',
            Items.repeater,
            'D',
            BDItems.SPACE_TIME_STABLE_FRAME);

        // ==================== 终端 ====================

        // 网络终端（物品）
        // 原版: A=IronIngot, B=GoldIngot, D=NET_MEMBER_INVITER
        GameRegistry.addRecipe(
            new ItemStack(BDItems.NET_TERMINAL_ITEM, 1),
            "ABA",
            "BDB",
            "ABA",
            'A',
            Items.iron_ingot,
            'B',
            Items.gold_ingot,
            'D',
            BDItems.NET_MEMBER_INVITER);

        // 网络终端（方块）
        // 原版: A=IronIngot, B=GoldIngot, C=CraftingTable, D=NET_MEMBER_INVITER
        GameRegistry.addRecipe(
            new ItemStack(BDBlocks.NET_TERMINAL_BLOCK, 1),
            "ACA",
            "BDB",
            "ABA",
            'A',
            Items.iron_ingot,
            'B',
            Items.gold_ingot,
            'C',
            Blocks.crafting_table,
            'D',
            BDItems.NET_MEMBER_INVITER);

        // ==================== 功能物品 ====================

        // 网络赠礼器
        // 原版: A=Diamond, B=GoldIngot, C=SPACE_TIME_STABLE_FRAME
        GameRegistry.addRecipe(
            new ItemStack(BDItems.NET_GIFTER, 1),
            " A ",
            "BCB",
            " B ",
            'A',
            Items.diamond,
            'B',
            Items.gold_ingot,
            'C',
            BDItems.SPACE_TIME_STABLE_FRAME);

        // 网络销毁器
        // 原版: A=TNT, B=GoldIngot, C=SPACE_TIME_STABLE_FRAME
        GameRegistry.addRecipe(
            new ItemStack(BDItems.NET_DESTROYER, 1),
            " A ",
            "BCB",
            " B ",
            'A',
            Blocks.tnt,
            'B',
            Items.gold_ingot,
            'C',
            BDItems.SPACE_TIME_STABLE_FRAME);

        // 网络磁铁
        // 原版: A=SPACE_TIME_BAR, B=IronIngot, C=SHATTERED_SPACE_TIME_CRYSTALLIZATION
        GameRegistry.addRecipe(
            new ItemStack(BDItems.NET_MAGNET_ITEM, 1),
            " AB",
            "A C",
            " AB",
            'A',
            BDItems.SPACE_TIME_BAR,
            'B',
            Items.iron_ingot,
            'C',
            BDItems.SHATTERED_SPACE_TIME_CRYSTALLIZATION);

        // 网络喂食器
        // 原版: A=SPACE_TIME_BAR, B=Apple, C=SPACE_TIME_STABLE_FRAME
        GameRegistry.addRecipe(
            new ItemStack(BDItems.NET_FEEDER_ITEM, 1),
            " AA",
            "ABC",
            " AA",
            'A',
            BDItems.SPACE_TIME_BAR,
            'B',
            Items.apple,
            'C',
            BDItems.SPACE_TIME_STABLE_FRAME);

        // 网络补货器
        // 原版: A=SPACE_TIME_BAR, B=Chest, C=SPACE_TIME_STABLE_FRAME
        GameRegistry.addRecipe(
            new ItemStack(BDItems.NET_RESTOCKER_ITEM, 1),
            " AA",
            "ABC",
            " AA",
            'A',
            BDItems.SPACE_TIME_BAR,
            'B',
            Blocks.chest,
            'C',
            BDItems.SPACE_TIME_STABLE_FRAME);

        // 经验交换物品
        // 原版: A=AMETHYST_SHARD(→Quartz), B=Stick, C=SPACE_TIME_BAR
        GameRegistry.addRecipe(
            new ItemStack(BDItems.XP_EXCHANGE_ITEM, 1),
            "  A",
            " B ",
            "C  ",
            'A',
            Items.quartz, // 1.20.1 为 AMETHYST_SHARD
            'B',
            Items.stick,
            'C',
            BDItems.SPACE_TIME_BAR);

        // ==================== 机器方块 ====================

        // 网络熔炉
        // 原版: A=Cobblestone, B=Piston, C=DIMENSIONAL_CONNECT_BLOCK, D=RedstoneTorch
        GameRegistry.addRecipe(
            new ItemStack(BDBlocks.NET_FURNACE_BLOCK, 1),
            "AAA",
            "BCB",
            "ADA",
            'A',
            Blocks.cobblestone,
            'B',
            Blocks.piston,
            'C',
            BDBlocks.DIMENSIONAL_CONNECT_BLOCK,
            'D',
            Blocks.redstone_torch);

        // 网络高炉
        // 原版: A=Cobblestone, B=Piston, C=DIMENSIONAL_CONNECT_BLOCK, D=BLAST_FURNACE(→Furnace)
        GameRegistry.addRecipe(
            new ItemStack(BDBlocks.NET_BLAST_FURNACE_BLOCK, 1),
            "AAA",
            "BCB",
            "ADA",
            'A',
            Blocks.cobblestone,
            'B',
            Blocks.piston,
            'C',
            BDBlocks.DIMENSIONAL_CONNECT_BLOCK,
            'D',
            Blocks.furnace); // 1.20.1 为 BLAST_FURNACE

        // 网络烟熏炉
        // 原版: A=Cobblestone, B=Piston, C=DIMENSIONAL_CONNECT_BLOCK, D=SMOKER(→Furnace)
        GameRegistry.addRecipe(
            new ItemStack(BDBlocks.NET_SMOKER_BLOCK, 1),
            "AAA",
            "BCB",
            "ADA",
            'A',
            Blocks.cobblestone,
            'B',
            Blocks.piston,
            'C',
            BDBlocks.DIMENSIONAL_CONNECT_BLOCK,
            'D',
            Blocks.furnace); // 1.20.1 为 SMOKER

        // 网络泵
        // 原版: A=Cobblestone, B=StickyPiston, C=DIMENSIONAL_CONNECT_BLOCK
        GameRegistry.addRecipe(
            new ItemStack(BDBlocks.NET_PUMP_BLOCK, 1),
            "ABA",
            "BCB",
            "ABA",
            'A',
            Blocks.cobblestone,
            'B',
            Blocks.sticky_piston,
            'C',
            BDBlocks.DIMENSIONAL_CONNECT_BLOCK);

        // 网络漏斗
        // 原版: A=Bucket, B=DIMENSIONAL_CONNECT_BLOCK, C=Hopper, D=Cobblestone
        GameRegistry.addRecipe(
            new ItemStack(BDBlocks.NET_HOPPER_BLOCK, 1),
            "   ",
            "ABC",
            "DDD",
            'A',
            Items.bucket,
            'B',
            BDBlocks.DIMENSIONAL_CONNECT_BLOCK,
            'C',
            Blocks.hopper,
            'D',
            Blocks.cobblestone);
    }

    private static void registerSmeltingRecipes() {
        // 时空锭：烧炼破碎的时空结晶
        // 原版: 输入=SHATTERED_SPACE_TIME_CRYSTALLIZATION, 输出=SPACE_TIME_BAR, 经验=1.0, 时间=600
        GameRegistry.addSmelting(
            new ItemStack(BDItems.SHATTERED_SPACE_TIME_CRYSTALLIZATION, 1),
            new ItemStack(BDItems.SPACE_TIME_BAR, 1),
            1.0f);
    }
}
