package com.wintercogs.beyonddimensions.client.event.listener;

import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * NEI 合成链精准暴露桥（核心包接口，无任何 NEI 类依赖）。
 * <p>
 * 由 {@link com.wintercogs.beyonddimensions.integration.module.nei.NeiClientModule}
 * 在 NEI 存在时注册实现（经 {@link com.wintercogs.beyonddimensions.client.gui.GuiDimensionsNet#registerNeiExposureBridge}），
 * 使核心 GUI 无需引用 NEI 类即可在每 tick 将"当前悬停收藏组的链条目"同步到非活跃槽位，
 * 供 NEI 合成链材料检查读取（方案 A 精准暴露）。
 */
@SideOnly(Side.CLIENT)
public interface NeiExposureBridge {

    /**
     * 更新 NEI 合成链暴露（每 tick 由终端 GUI 的 updateScreen 调用）。
     *
     * @param menu   当前终端菜单（含存储槽位与网络存储）
     * @param mouseX 屏幕坐标（鼠标）
     * @param mouseY 屏幕坐标（鼠标）
     */
    void updateNeiExposure(DimensionsNetMenu menu, int mouseX, int mouseY);
}
