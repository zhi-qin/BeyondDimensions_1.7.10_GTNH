package com.wintercogs.beyonddimensions.client.gui;

import net.minecraft.util.ResourceLocation;

import com.wintercogs.beyonddimensions.api.ids.BDConstants;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 通用 GUI 纹理路径与尺寸常量（1.7.10 移植版）。
 * <p>
 * 移植自 1.20.1 的 CommonTextures，ResourceLocation 构造方式与 1.20.1 一致。
 */
@SideOnly(Side.CLIENT)
public final class CommonTextures {

    private CommonTextures() {}

    public static final ResourceLocation TOP_BASE_COMMON = new ResourceLocation(
        BDConstants.MODID,
        "textures/gui/top_base_common.png");
    public static final int TOP_BASE_COMMON_WIDTH = 176;
    public static final int TOP_BASE_COMMON_HEIGHT = 24;

    public static final ResourceLocation COMMON_CONNECTION = new ResourceLocation(
        BDConstants.MODID,
        "textures/gui/common_connection.png");
    public static final int COMMON_CONNECTION_WIDTH = 176;
    public static final int COMMON_CONNECTION_HEIGHT = 8;

    public static final ResourceLocation BOTTOM_BASE_COMMON = new ResourceLocation(
        BDConstants.MODID,
        "textures/gui/bottom_base_common.png");
    public static final int BOTTOM_BASE_COMMON_WIDTH = 176;
    public static final int BOTTOM_BASE_COMMON_HEIGHT = 7;

    public static final ResourceLocation COMMON_SLOTS = new ResourceLocation(
        BDConstants.MODID,
        "textures/gui/common_slots.png");
    public static final int COMMON_SLOTS_WIDTH = 176;
    public static final int COMMON_SLOTS_HEIGHT = 18;

    public static final ResourceLocation FILTER_SLOTS = new ResourceLocation(
        BDConstants.MODID,
        "textures/gui/filter_slots.png");
    public static final int FILTER_SLOTS_WIDTH = 176;
    public static final int FILTER_SLOTS_HEIGHT = 18;

    public static final ResourceLocation GUI_TEXTURE_PLAYER_INV = new ResourceLocation(
        BDConstants.MODID,
        "textures/gui/player_inv.png");
    public static final int PLAYER_INV_WIDTH = 176;
    public static final int PLAYER_INV_HEIGHT = 89;

    // 维度网络主界面分块纹理
    public static final ResourceLocation TOP_BASE = new ResourceLocation(
        BDConstants.MODID,
        "textures/gui/top_base.png");
    public static final int TOP_BASE_WIDTH = 194;
    public static final int TOP_BASE_HEIGHT = 24;

    public static final ResourceLocation TOP_SLOTS = new ResourceLocation(
        BDConstants.MODID,
        "textures/gui/top_slots.png");
    public static final int TOP_SLOTS_WIDTH = 194;
    public static final int TOP_SLOTS_HEIGHT = 18;

    public static final ResourceLocation MID_SLOTS = new ResourceLocation(
        BDConstants.MODID,
        "textures/gui/mid_slots.png");
    public static final int MID_SLOTS_WIDTH = 194;
    public static final int MID_SLOTS_HEIGHT = 18;

    public static final ResourceLocation BOTTOM_SLOTS = new ResourceLocation(
        BDConstants.MODID,
        "textures/gui/bottom_slots.png");
    public static final int BOTTOM_SLOTS_WIDTH = 194;
    public static final int BOTTOM_SLOTS_HEIGHT = 26;

    public static final ResourceLocation CRAFT_SLOTS = new ResourceLocation(
        BDConstants.MODID,
        "textures/gui/craft_slots.png");
    public static final int CRAFT_SLOTS_WIDTH = 176;
    public static final int CRAFT_SLOTS_HEIGHT = 62;

    // 网络熔炉背景图
    public static final ResourceLocation NET_FURNACE_BACKGROUND = new ResourceLocation(
        BDConstants.MODID,
        "textures/gui/net_furnace.png");
    public static final int NET_FURNACE_BACKGROUND_WIDTH = 230;
    public static final int NET_FURNACE_BACKGROUND_HEIGHT = 210;

    // 进度条箭头-完成填充
    public static final ResourceLocation WORK_DONE_V = new ResourceLocation(
        BDConstants.MODID,
        "textures/gui/sprites/widget/work_done_v.png");
    public static final int WORK_DONE_V_WIDTH = 14;
    public static final int WORK_DONE_V_HEIGHT = 19;

    // 原版火焰贴图
    public static final ResourceLocation FURNACE_WORK_V = new ResourceLocation(
        BDConstants.MODID,
        "textures/gui/sprites/widget/furnace_work_v.png");
    public static final int FURNACE_WORK_V_WIDTH = 14;
    public static final int FURNACE_WORK_V_HEIGHT = 14;

    // 右标签页贴图
    public static final ResourceLocation RIGHT_TAB = new ResourceLocation(
        BDConstants.MODID,
        "textures/gui/sprites/widget/right_tab.png");
    public static final int RIGHT_TAB_WIDTH = 26;
    public static final int RIGHT_TAB_HEIGHT = 12;

    // 左标签页贴图
    public static final ResourceLocation LEFT_TAB = new ResourceLocation(
        BDConstants.MODID,
        "textures/gui/sprites/widget/left_tab.png");
    public static final int LEFT_TAB_WIDTH = 26;
    public static final int LEFT_TAB_HEIGHT = 12;

    // 滚动条滑块贴图
    public static final ResourceLocation SCROLLER = new ResourceLocation(
        BDConstants.MODID,
        "textures/gui/sprites/widget/scroller.png");
    public static final int SCROLLER_WIDTH = 12;
    public static final int SCROLLER_HEIGHT = 15;
}
