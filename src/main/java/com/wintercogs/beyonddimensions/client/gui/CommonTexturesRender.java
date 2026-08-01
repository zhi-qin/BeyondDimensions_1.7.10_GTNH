package com.wintercogs.beyonddimensions.client.gui;

import net.minecraft.util.ResourceLocation;

import com.wintercogs.beyonddimensions.util.GuiRenderHelper;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 通用纹理渲染辅助方法（1.7.10 移植版）。
 * <p>
 * 移植自 1.20.1 的 CommonTexturesRender，将 {@code GuiGraphics#blit} 替换为
 * {@link GuiRenderHelper#blit}，以支持任意尺寸纹理。
 * 1.7.10 的 {@code drawTexturedModalRect} 假设纹理为 256×256，无法直接使用。
 */
@SideOnly(Side.CLIENT)
public final class CommonTexturesRender {

    private CommonTexturesRender() {}

    /** 渲染 TOP_BASE_COMMON 并将 yPosRef[0] 下移 height */
    public static void renderTopBaseCommon(int leftPos, int[] yPosRef) {
        renderTopBaseCommon(
            leftPos,
            yPosRef,
            CommonTextures.TOP_BASE_COMMON_WIDTH,
            CommonTextures.TOP_BASE_COMMON_HEIGHT);
    }

    public static void renderTopBaseCommon(int leftPos, int[] yPosRef, int width, int height) {
        GuiRenderHelper.blit(
            CommonTextures.TOP_BASE_COMMON,
            leftPos,
            yPosRef[0],
            width,
            height,
            0,
            0,
            CommonTextures.TOP_BASE_COMMON_WIDTH,
            CommonTextures.TOP_BASE_COMMON_HEIGHT,
            CommonTextures.TOP_BASE_COMMON_WIDTH,
            CommonTextures.TOP_BASE_COMMON_HEIGHT);
        yPosRef[0] += height;
    }

    /** 渲染 COMMON_CONNECTION 并将 yPosRef[0] 下移 height */
    public static void renderCommonConnection(int leftPos, int[] yPosRef) {
        renderCommonConnection(
            leftPos,
            yPosRef,
            CommonTextures.COMMON_CONNECTION_WIDTH,
            CommonTextures.COMMON_CONNECTION_HEIGHT);
    }

    public static void renderCommonConnection(int leftPos, int[] yPosRef, int width, int height) {
        GuiRenderHelper.blit(
            CommonTextures.COMMON_CONNECTION,
            leftPos,
            yPosRef[0],
            width,
            height,
            0,
            0,
            CommonTextures.COMMON_CONNECTION_WIDTH,
            CommonTextures.COMMON_CONNECTION_HEIGHT,
            CommonTextures.COMMON_CONNECTION_WIDTH,
            CommonTextures.COMMON_CONNECTION_HEIGHT);
        yPosRef[0] += height;
    }

    /** 渲染 BOTTOM_BASE_COMMON 并将 yPosRef[0] 下移 height */
    public static void renderBottomBaseCommon(int leftPos, int[] yPosRef) {
        renderBottomBaseCommon(
            leftPos,
            yPosRef,
            CommonTextures.BOTTOM_BASE_COMMON_WIDTH,
            CommonTextures.BOTTOM_BASE_COMMON_HEIGHT);
    }

    public static void renderBottomBaseCommon(int leftPos, int[] yPosRef, int width, int height) {
        GuiRenderHelper.blit(
            CommonTextures.BOTTOM_BASE_COMMON,
            leftPos,
            yPosRef[0],
            width,
            height,
            0,
            0,
            CommonTextures.BOTTOM_BASE_COMMON_WIDTH,
            CommonTextures.BOTTOM_BASE_COMMON_HEIGHT,
            CommonTextures.BOTTOM_BASE_COMMON_WIDTH,
            CommonTextures.BOTTOM_BASE_COMMON_HEIGHT);
        yPosRef[0] += height;
    }

    /** 渲染 COMMON_SLOTS 并将 yPosRef[0] 下移 height */
    public static void renderCommonSlots(int leftPos, int[] yPosRef) {
        renderCommonSlots(leftPos, yPosRef, CommonTextures.COMMON_SLOTS_WIDTH, CommonTextures.COMMON_SLOTS_HEIGHT);
    }

    public static void renderCommonSlots(int leftPos, int[] yPosRef, int width, int height) {
        GuiRenderHelper.blit(
            CommonTextures.COMMON_SLOTS,
            leftPos,
            yPosRef[0],
            width,
            height,
            0,
            0,
            CommonTextures.COMMON_SLOTS_WIDTH,
            CommonTextures.COMMON_SLOTS_HEIGHT,
            CommonTextures.COMMON_SLOTS_WIDTH,
            CommonTextures.COMMON_SLOTS_HEIGHT);
        yPosRef[0] += height;
    }

    /** 渲染 FILTER_SLOTS 并将 yPosRef[0] 下移 height */
    public static void renderFilterSlots(int leftPos, int[] yPosRef) {
        renderFilterSlots(leftPos, yPosRef, CommonTextures.FILTER_SLOTS_WIDTH, CommonTextures.FILTER_SLOTS_HEIGHT);
    }

    public static void renderFilterSlots(int leftPos, int[] yPosRef, int width, int height) {
        GuiRenderHelper.blit(
            CommonTextures.FILTER_SLOTS,
            leftPos,
            yPosRef[0],
            width,
            height,
            0,
            0,
            CommonTextures.FILTER_SLOTS_WIDTH,
            CommonTextures.FILTER_SLOTS_HEIGHT,
            CommonTextures.FILTER_SLOTS_WIDTH,
            CommonTextures.FILTER_SLOTS_HEIGHT);
        yPosRef[0] += height;
    }

    /** 渲染 PLAYER_INV 并将 yPosRef[0] 下移 height */
    public static void renderPlayerInv(int leftPos, int[] yPosRef) {
        renderPlayerInv(leftPos, yPosRef, CommonTextures.PLAYER_INV_WIDTH, CommonTextures.PLAYER_INV_HEIGHT);
    }

    public static void renderPlayerInv(int leftPos, int[] yPosRef, int width, int height) {
        GuiRenderHelper.blit(
            CommonTextures.GUI_TEXTURE_PLAYER_INV,
            leftPos,
            yPosRef[0],
            width,
            height,
            0,
            0,
            CommonTextures.PLAYER_INV_WIDTH,
            CommonTextures.PLAYER_INV_HEIGHT,
            CommonTextures.PLAYER_INV_WIDTH,
            CommonTextures.PLAYER_INV_HEIGHT);
        yPosRef[0] += height;
    }

    /** 渲染整张指定纹理（缩放到 width×height） */
    public static void renderFullTexture(ResourceLocation texture, int leftPos, int yPos, int width, int height,
        int origWidth, int origHeight) {
        GuiRenderHelper.blit(texture, leftPos, yPos, width, height, 0, 0, origWidth, origHeight, origWidth, origHeight);
    }

    /**
     * 渲染熔炼进度（从上往下填充）。
     * 移植自 1.20.1 renderWorkDoneV_AsProgress，使用 GuiRenderHelper.blit。
     */
    public static void renderWorkDoneV_AsProgress(int leftPos, int yPos, int width, int height, float progress) {
        progress = clamp(progress, 0f, 1f);
        if (progress <= 0f) return;

        int vHeight = (int) (CommonTextures.WORK_DONE_V_HEIGHT * progress);
        GuiRenderHelper.blit(
            CommonTextures.WORK_DONE_V,
            leftPos,
            yPos,
            width,
            (int) (height * progress),
            0,
            0,
            CommonTextures.WORK_DONE_V_WIDTH,
            vHeight,
            CommonTextures.WORK_DONE_V_WIDTH,
            CommonTextures.WORK_DONE_V_HEIGHT);
    }

    /**
     * 渲染燃料进度（从下往上填充）。
     * 移植自 1.20.1 renderFurnaceWorkV_AsProgress，使用 GuiRenderHelper.blit。
     */
    public static void renderFurnaceWorkV_AsProgress(int leftPos, int yPos, int width, int height, float progress) {
        progress = clamp(progress, 0f, 1f);
        if (progress <= 0f) return;

        int vHeight = (int) (CommonTextures.FURNACE_WORK_V_HEIGHT * progress);
        int vOffset = CommonTextures.FURNACE_WORK_V_HEIGHT - vHeight;
        int drawY = yPos + vOffset;

        GuiRenderHelper.blit(
            CommonTextures.FURNACE_WORK_V,
            leftPos,
            drawY,
            width,
            (int) (height * progress),
            0,
            vOffset,
            CommonTextures.FURNACE_WORK_V_WIDTH,
            vHeight,
            CommonTextures.FURNACE_WORK_V_WIDTH,
            CommonTextures.FURNACE_WORK_V_HEIGHT);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
