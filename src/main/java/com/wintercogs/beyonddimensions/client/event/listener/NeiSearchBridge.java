package com.wintercogs.beyonddimensions.client.event.listener;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * NEI 搜索文本双向同步桥（核心包接口，无任何 NEI 类依赖）。
 * <p>
 * 对齐源项目 1.20.1 {@code DimensionsNetGUI} 中 searchTextWithJEIEMI 的 JEI/EMI 搜索文本同步：
 * 1.7.10 无 JEI/EMI，等价物为 NEI 搜索栏。由
 * {@link com.wintercogs.beyonddimensions.integration.module.nei.NeiClientModule}
 * 在 NEI 存在时注册实现（经
 * {@link com.wintercogs.beyonddimensions.client.gui.GuiDimensionsNet#registerNeiSearchBridge}），
 * 使核心 GUI 无需引用 NEI 类即可在配置 search_text_with_jei_emi 开启时双向同步搜索文本。
 */
@SideOnly(Side.CLIENT)
public interface NeiSearchBridge {

    /**
     * 将维度网络 GUI 的搜索文本推送到 NEI 搜索栏（触发 NEI 物品面板过滤）。
     */
    void pushSearchText(String text);

    /**
     * 读取 NEI 搜索栏当前文本；无 NEI 或未注册时由 GUI 端判空跳过。
     */
    String readSearchText();
}
