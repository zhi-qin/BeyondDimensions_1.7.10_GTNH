package com.wintercogs.beyonddimensions.common.init;

import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;

import com.wintercogs.beyonddimensions.api.ids.BDBlockIds;
import com.wintercogs.beyonddimensions.api.ids.BDConstants;
import com.wintercogs.beyonddimensions.common.block.*;

import cpw.mods.fml.common.registry.GameRegistry;

public class BDBlocks {

    public static Block NET_CONTROL;
    public static Block NET_INTERFACE;
    public static Block NET_PATHWAY;
    public static Block NET_ENERGY_PATHWAY;
    public static Block NET_TERMINAL_BLOCK;
    public static Block NET_PUMP_BLOCK;
    public static Block NET_HOPPER_BLOCK;
    public static Block NET_FURNACE_BLOCK;
    public static Block NET_BLAST_FURNACE_BLOCK;
    public static Block NET_SMOKER_BLOCK;
    public static Block DIMENSIONAL_CONNECT_BLOCK;

    public static void register() {
        NET_CONTROL = new NetControlBlock().setBlockName(BDConstants.MODID + "." + BDBlockIds.NET_CONTROL)
            .setBlockTextureName(BDConstants.MODID + ":" + BDBlockIds.NET_CONTROL)
            .setHardness(4f)
            .setCreativeTab(BDCreativeModeTabs.BEYOND_DIMENSIONS_BLOCKS_TAB);
        GameRegistry.registerBlock(NET_CONTROL, ItemBlock.class, BDBlockIds.NET_CONTROL);

        NET_INTERFACE = new NetInterfaceBlock().setBlockName(BDConstants.MODID + "." + BDBlockIds.NET_INTERFACE)
            .setBlockTextureName(BDConstants.MODID + ":" + BDBlockIds.NET_INTERFACE)
            .setHardness(2f)
            .setCreativeTab(BDCreativeModeTabs.BEYOND_DIMENSIONS_BLOCKS_TAB);
        GameRegistry.registerBlock(NET_INTERFACE, ItemBlock.class, BDBlockIds.NET_INTERFACE);

        NET_PATHWAY = new NetPathwayBlock().setBlockName(BDConstants.MODID + "." + BDBlockIds.NET_PATHWAY)
            .setBlockTextureName(BDConstants.MODID + ":" + BDBlockIds.NET_PATHWAY)
            .setHardness(2f)
            .setCreativeTab(BDCreativeModeTabs.BEYOND_DIMENSIONS_BLOCKS_TAB);
        GameRegistry.registerBlock(NET_PATHWAY, ItemBlock.class, BDBlockIds.NET_PATHWAY);

        NET_ENERGY_PATHWAY = new NetEnergyPathwayBlock()
            .setBlockName(BDConstants.MODID + "." + BDBlockIds.NET_ENERGY_PATHWAY)
            .setBlockTextureName(BDConstants.MODID + ":" + BDBlockIds.NET_ENERGY_PATHWAY)
            .setHardness(2f)
            .setCreativeTab(BDCreativeModeTabs.BEYOND_DIMENSIONS_BLOCKS_TAB);
        GameRegistry.registerBlock(NET_ENERGY_PATHWAY, ItemBlock.class, BDBlockIds.NET_ENERGY_PATHWAY);

        NET_TERMINAL_BLOCK = new NetTerminalBlock()
            .setBlockName(BDConstants.MODID + "." + BDBlockIds.NET_TERMINAL_BLOCK)
            .setBlockTextureName(BDConstants.MODID + ":net_terminal_block_texture")
            .setHardness(2f)
            .setCreativeTab(BDCreativeModeTabs.BEYOND_DIMENSIONS_BLOCKS_TAB);
        GameRegistry.registerBlock(NET_TERMINAL_BLOCK, ItemBlock.class, BDBlockIds.NET_TERMINAL_BLOCK);

        NET_PUMP_BLOCK = new NetPumpBlock().setBlockName(BDConstants.MODID + "." + BDBlockIds.NET_PUMP_BLOCK)
            .setBlockTextureName(BDConstants.MODID + ":" + BDBlockIds.NET_PUMP_BLOCK)
            .setHardness(2f)
            .setCreativeTab(BDCreativeModeTabs.BEYOND_DIMENSIONS_BLOCKS_TAB);
        GameRegistry.registerBlock(NET_PUMP_BLOCK, ItemBlock.class, BDBlockIds.NET_PUMP_BLOCK);

        NET_HOPPER_BLOCK = new NetHopperBlock().setBlockName(BDConstants.MODID + "." + BDBlockIds.NET_HOPPER_BLOCK)
            .setBlockTextureName(BDConstants.MODID + ":" + BDBlockIds.NET_HOPPER_BLOCK)
            .setHardness(2f)
            .setCreativeTab(BDCreativeModeTabs.BEYOND_DIMENSIONS_BLOCKS_TAB);
        GameRegistry.registerBlock(NET_HOPPER_BLOCK, ItemBlock.class, BDBlockIds.NET_HOPPER_BLOCK);

        NET_FURNACE_BLOCK = new NetFurnaceBlock().setBlockName(BDConstants.MODID + "." + BDBlockIds.NET_FURNACE_BLOCK)
            .setBlockTextureName(BDConstants.MODID + ":net_furnace_front")
            .setHardness(2f)
            .setCreativeTab(BDCreativeModeTabs.BEYOND_DIMENSIONS_BLOCKS_TAB);
        GameRegistry.registerBlock(NET_FURNACE_BLOCK, ItemBlock.class, BDBlockIds.NET_FURNACE_BLOCK);

        NET_BLAST_FURNACE_BLOCK = new NetBlastFurnaceBlock()
            .setBlockName(BDConstants.MODID + "." + BDBlockIds.NET_BLAST_FURNACE_BLOCK)
            .setBlockTextureName(BDConstants.MODID + ":net_blast_furnace_front")
            .setHardness(2f)
            .setCreativeTab(BDCreativeModeTabs.BEYOND_DIMENSIONS_BLOCKS_TAB);
        GameRegistry.registerBlock(NET_BLAST_FURNACE_BLOCK, ItemBlock.class, BDBlockIds.NET_BLAST_FURNACE_BLOCK);

        NET_SMOKER_BLOCK = new NetSmokerBlock().setBlockName(BDConstants.MODID + "." + BDBlockIds.NET_SMOKER_BLOCK)
            .setBlockTextureName(BDConstants.MODID + ":net_smoker_front")
            .setHardness(2f)
            .setCreativeTab(BDCreativeModeTabs.BEYOND_DIMENSIONS_BLOCKS_TAB);
        GameRegistry.registerBlock(NET_SMOKER_BLOCK, ItemBlock.class, BDBlockIds.NET_SMOKER_BLOCK);

        DIMENSIONAL_CONNECT_BLOCK = new DimensionalConnectBlock()
            .setBlockName(BDConstants.MODID + "." + BDBlockIds.DIMENSIONAL_CONNECT_BLOCK)
            .setBlockTextureName(BDConstants.MODID + ":" + BDBlockIds.DIMENSIONAL_CONNECT_BLOCK)
            .setHardness(2f)
            .setCreativeTab(BDCreativeModeTabs.BEYOND_DIMENSIONS_BLOCKS_TAB);
        GameRegistry.registerBlock(DIMENSIONAL_CONNECT_BLOCK, ItemBlock.class, BDBlockIds.DIMENSIONAL_CONNECT_BLOCK);
    }
}
