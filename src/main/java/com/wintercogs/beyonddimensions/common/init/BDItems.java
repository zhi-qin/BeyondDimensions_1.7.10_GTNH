package com.wintercogs.beyonddimensions.common.init;

import net.minecraft.item.Item;

import com.wintercogs.beyonddimensions.api.ids.BDConstants;
import com.wintercogs.beyonddimensions.api.ids.BDItemIds;
import com.wintercogs.beyonddimensions.common.item.*;
import com.wintercogs.beyonddimensions.integration.ModPresence;
import com.wintercogs.beyonddimensions.integration.OtherModIds;
import com.wintercogs.beyonddimensions.integration.module.ae2.item.NetAEFluidCell;
import com.wintercogs.beyonddimensions.integration.module.ae2.item.NetAEStorageCell;
import com.wintercogs.beyonddimensions.integration.module.ae2.item.NetEnergyDrop;

import cpw.mods.fml.common.registry.GameRegistry;

public class BDItems {

    public static Item NET_CREATER;
    public static Item NET_MEMBER_INVITER;
    public static Item NET_MANAGER_INVITER;
    public static Item UNSTABLE_SPACE_TIME_FRAGMENT;
    public static Item STABLE_SPACE_TIME_FRAGMENT;
    public static Item SPACE_TIME_STABLE_FRAME;
    public static Item SHATTERED_SPACE_TIME_CRYSTALLIZATION;
    public static Item SPACE_TIME_BAR;
    public static Item NET_TERMINAL_ITEM;
    public static Item NET_GIFTER;
    public static Item NET_DESTROYER;
    public static Item MATTER_COMPRESS_BALL;
    public static Item NET_MAGNET_ITEM;
    public static Item NET_FEEDER_ITEM;
    public static Item NET_RESTOCKER_ITEM;
    public static Item XP_EXCHANGE_ITEM;
    public static Item TEST_ITEM_GENERATE;
    // AE2 联动模块物品（仅 AE2 加载时注册）
    public static Item NET_AE_STORAGE_CELL;
    public static Item NET_AE_FLUID_CELL;
    public static Item NET_ENERGY_DROP;

    public static void register() {
        NET_CREATER = new NetCreater().setUnlocalizedName(BDConstants.MODID + "." + BDItemIds.NET_CREATER)
            .setTextureName(BDConstants.MODID + ":" + BDItemIds.NET_CREATER)
            .setCreativeTab(BDCreativeModeTabs.BEYOND_DIMENSIONS_ITEMS_TAB);
        GameRegistry.registerItem(NET_CREATER, BDItemIds.NET_CREATER, BDConstants.MODID);

        NET_MEMBER_INVITER = new NetMemberInviter()
            .setUnlocalizedName(BDConstants.MODID + "." + BDItemIds.NET_MEMBER_INVITER)
            .setTextureName(BDConstants.MODID + ":" + BDItemIds.NET_MEMBER_INVITER)
            .setCreativeTab(BDCreativeModeTabs.BEYOND_DIMENSIONS_ITEMS_TAB);
        GameRegistry.registerItem(NET_MEMBER_INVITER, BDItemIds.NET_MEMBER_INVITER, BDConstants.MODID);

        NET_MANAGER_INVITER = new NetManagerInviter()
            .setUnlocalizedName(BDConstants.MODID + "." + BDItemIds.NET_MANAGER_INVITER)
            .setTextureName(BDConstants.MODID + ":" + BDItemIds.NET_MANAGER_INVITER)
            .setCreativeTab(BDCreativeModeTabs.BEYOND_DIMENSIONS_ITEMS_TAB);
        GameRegistry.registerItem(NET_MANAGER_INVITER, BDItemIds.NET_MANAGER_INVITER, BDConstants.MODID);

        UNSTABLE_SPACE_TIME_FRAGMENT = new UnstableSpaceTimeFragment()
            .setUnlocalizedName(BDConstants.MODID + "." + BDItemIds.UNSTABLE_SPACE_TIME_FRAGMENT)
            .setTextureName(BDConstants.MODID + ":" + BDItemIds.UNSTABLE_SPACE_TIME_FRAGMENT)
            .setCreativeTab(BDCreativeModeTabs.BEYOND_DIMENSIONS_ITEMS_TAB);
        GameRegistry
            .registerItem(UNSTABLE_SPACE_TIME_FRAGMENT, BDItemIds.UNSTABLE_SPACE_TIME_FRAGMENT, BDConstants.MODID);

        STABLE_SPACE_TIME_FRAGMENT = new Item()
            .setUnlocalizedName(BDConstants.MODID + "." + BDItemIds.STABLE_SPACE_TIME_FRAGMENT)
            .setTextureName(BDConstants.MODID + ":" + BDItemIds.STABLE_SPACE_TIME_FRAGMENT)
            .setCreativeTab(BDCreativeModeTabs.BEYOND_DIMENSIONS_ITEMS_TAB);
        GameRegistry.registerItem(STABLE_SPACE_TIME_FRAGMENT, BDItemIds.STABLE_SPACE_TIME_FRAGMENT, BDConstants.MODID);

        SPACE_TIME_STABLE_FRAME = new Item()
            .setUnlocalizedName(BDConstants.MODID + "." + BDItemIds.SPACE_TIME_STABLE_FRAME)
            .setTextureName(BDConstants.MODID + ":" + BDItemIds.SPACE_TIME_STABLE_FRAME)
            .setCreativeTab(BDCreativeModeTabs.BEYOND_DIMENSIONS_ITEMS_TAB);
        GameRegistry.registerItem(SPACE_TIME_STABLE_FRAME, BDItemIds.SPACE_TIME_STABLE_FRAME, BDConstants.MODID);

        SHATTERED_SPACE_TIME_CRYSTALLIZATION = new Item()
            .setUnlocalizedName(BDConstants.MODID + "." + BDItemIds.SHATTERED_SPACE_TIME_CRYSTALLIZATION)
            .setTextureName(BDConstants.MODID + ":" + BDItemIds.SHATTERED_SPACE_TIME_CRYSTALLIZATION)
            .setCreativeTab(BDCreativeModeTabs.BEYOND_DIMENSIONS_ITEMS_TAB);
        GameRegistry.registerItem(
            SHATTERED_SPACE_TIME_CRYSTALLIZATION,
            BDItemIds.SHATTERED_SPACE_TIME_CRYSTALLIZATION,
            BDConstants.MODID);

        SPACE_TIME_BAR = new Item().setUnlocalizedName(BDConstants.MODID + "." + BDItemIds.SPACE_TIME_BAR)
            .setTextureName(BDConstants.MODID + ":" + BDItemIds.SPACE_TIME_BAR)
            .setCreativeTab(BDCreativeModeTabs.BEYOND_DIMENSIONS_ITEMS_TAB);
        GameRegistry.registerItem(SPACE_TIME_BAR, BDItemIds.SPACE_TIME_BAR, BDConstants.MODID);

        NET_TERMINAL_ITEM = new NetTerminalItem()
            .setUnlocalizedName(BDConstants.MODID + "." + BDItemIds.NET_TERMINAL_ITEM)
            .setTextureName(BDConstants.MODID + ":" + BDItemIds.NET_TERMINAL_ITEM)
            .setCreativeTab(BDCreativeModeTabs.BEYOND_DIMENSIONS_ITEMS_TAB);
        GameRegistry.registerItem(NET_TERMINAL_ITEM, BDItemIds.NET_TERMINAL_ITEM, BDConstants.MODID);

        NET_GIFTER = new NetGifter().setUnlocalizedName(BDConstants.MODID + "." + BDItemIds.NET_GIFTER)
            .setTextureName(BDConstants.MODID + ":" + BDItemIds.NET_GIFTER)
            .setCreativeTab(BDCreativeModeTabs.BEYOND_DIMENSIONS_ITEMS_TAB);
        GameRegistry.registerItem(NET_GIFTER, BDItemIds.NET_GIFTER, BDConstants.MODID);

        NET_DESTROYER = new NetDestroyer().setUnlocalizedName(BDConstants.MODID + "." + BDItemIds.NET_DESTROYER)
            .setTextureName(BDConstants.MODID + ":" + BDItemIds.NET_DESTROYER)
            .setCreativeTab(BDCreativeModeTabs.BEYOND_DIMENSIONS_ITEMS_TAB);
        GameRegistry.registerItem(NET_DESTROYER, BDItemIds.NET_DESTROYER, BDConstants.MODID);

        MATTER_COMPRESS_BALL = new MatterCompressionBall()
            .setUnlocalizedName(BDConstants.MODID + "." + BDItemIds.MATTER_COMPRESS_BALL)
            .setTextureName(BDConstants.MODID + ":" + BDItemIds.MATTER_COMPRESS_BALL)
            .setCreativeTab(BDCreativeModeTabs.BEYOND_DIMENSIONS_ITEMS_TAB);
        GameRegistry.registerItem(MATTER_COMPRESS_BALL, BDItemIds.MATTER_COMPRESS_BALL, BDConstants.MODID);

        NET_MAGNET_ITEM = new NetMagnetItem().setUnlocalizedName(BDConstants.MODID + "." + BDItemIds.NET_MAGNET_ITEM)
            .setTextureName(BDConstants.MODID + ":" + BDItemIds.NET_MAGNET_ITEM)
            .setCreativeTab(BDCreativeModeTabs.BEYOND_DIMENSIONS_ITEMS_TAB);
        GameRegistry.registerItem(NET_MAGNET_ITEM, BDItemIds.NET_MAGNET_ITEM, BDConstants.MODID);

        NET_FEEDER_ITEM = new NetFeederItem().setUnlocalizedName(BDConstants.MODID + "." + BDItemIds.NET_FEEDER_ITEM)
            .setTextureName(BDConstants.MODID + ":" + BDItemIds.NET_FEEDER_ITEM)
            .setCreativeTab(BDCreativeModeTabs.BEYOND_DIMENSIONS_ITEMS_TAB);
        GameRegistry.registerItem(NET_FEEDER_ITEM, BDItemIds.NET_FEEDER_ITEM, BDConstants.MODID);

        NET_RESTOCKER_ITEM = new NetRestockerItem()
            .setUnlocalizedName(BDConstants.MODID + "." + BDItemIds.NET_RESTOCKER_ITEM)
            .setTextureName(BDConstants.MODID + ":" + BDItemIds.NET_RESTOCKER_ITEM)
            .setCreativeTab(BDCreativeModeTabs.BEYOND_DIMENSIONS_ITEMS_TAB);
        GameRegistry.registerItem(NET_RESTOCKER_ITEM, BDItemIds.NET_RESTOCKER_ITEM, BDConstants.MODID);

        XP_EXCHANGE_ITEM = new XpExchangeItem().setUnlocalizedName(BDConstants.MODID + "." + BDItemIds.XP_EXCHANGE_ITEM)
            .setTextureName(BDConstants.MODID + ":" + BDItemIds.XP_EXCHANGE_ITEM)
            .setCreativeTab(BDCreativeModeTabs.BEYOND_DIMENSIONS_ITEMS_TAB);
        GameRegistry.registerItem(XP_EXCHANGE_ITEM, BDItemIds.XP_EXCHANGE_ITEM, BDConstants.MODID);

        TEST_ITEM_GENERATE = new TestItem_ItemGenerate()
            .setUnlocalizedName(BDConstants.MODID + "." + BDItemIds.TEST_ITEM_GENERATE)
            .setTextureName(BDConstants.MODID + ":" + BDItemIds.TEST_ITEM_GENERATE)
            .setCreativeTab(BDCreativeModeTabs.BEYOND_DIMENSIONS_ITEMS_TAB);
        GameRegistry.registerItem(TEST_ITEM_GENERATE, BDItemIds.TEST_ITEM_GENERATE, BDConstants.MODID);

        // AE2 联动：仅当 AE2 已加载时注册存储元件物品
        // 物品类本身不依赖 AE2 API，可安全加载；CellHandler 在 AE2Module.onBootstrap 中注册
        if (ModPresence.isLoaded(OtherModIds.AE2)) {
            NET_AE_STORAGE_CELL = new NetAEStorageCell()
                .setUnlocalizedName(BDConstants.MODID + "." + BDItemIds.NET_AE_STORAGE_CELL)
                .setTextureName(BDConstants.MODID + ":" + BDItemIds.NET_AE_STORAGE_CELL)
                .setCreativeTab(BDCreativeModeTabs.BEYOND_DIMENSIONS_ITEMS_TAB);
            GameRegistry.registerItem(NET_AE_STORAGE_CELL, BDItemIds.NET_AE_STORAGE_CELL, BDConstants.MODID);

            NET_AE_FLUID_CELL = new NetAEFluidCell()
                .setUnlocalizedName(BDConstants.MODID + "." + BDItemIds.NET_AE_FLUID_CELL)
                .setTextureName(BDConstants.MODID + ":" + BDItemIds.NET_AE_FLUID_CELL)
                .setCreativeTab(BDCreativeModeTabs.BEYOND_DIMENSIONS_ITEMS_TAB);
            GameRegistry.registerItem(NET_AE_FLUID_CELL, BDItemIds.NET_AE_FLUID_CELL, BDConstants.MODID);

            // 能量滴：维度网络能量的虚拟载体，不进创造标签页（仅经 ME 终端提取获得）
            NET_ENERGY_DROP = new NetEnergyDrop()
                .setUnlocalizedName(BDConstants.MODID + "." + BDItemIds.NET_ENERGY_DROP)
                .setTextureName(BDConstants.MODID + ":" + BDItemIds.NET_ENERGY_DROP);
            GameRegistry.registerItem(NET_ENERGY_DROP, BDItemIds.NET_ENERGY_DROP, BDConstants.MODID);
        }
    }
}
