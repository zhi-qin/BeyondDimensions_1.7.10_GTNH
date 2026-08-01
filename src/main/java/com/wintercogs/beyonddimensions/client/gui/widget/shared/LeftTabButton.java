package com.wintercogs.beyonddimensions.client.gui.widget.shared;

import com.wintercogs.beyonddimensions.client.gui.CommonTextures;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 左侧标签按钮（1.7.10 移植版）。
 * <p>
 * 移植自 1.20.1 的 {@code LeftTabButton}。背景使用 {@link CommonTextures#LEFT_TAB}。
 * 1.7.10 的 {@code CommonTextures#LEFT_TAB_WIDTH/HEIGHT} 在目标项目常量类中暂未定义，
 * 这里直接使用与贴图尺寸一致的固定值（26×12），与源项目保持一致。
 */
@SideOnly(Side.CLIENT)
public abstract class LeftTabButton extends StatusButton {

    protected LeftTabButton(int id, int x, int y, int width, int height, int iconX, int iconY, int iconWidth,
        int iconHeight, OnPress onPress) {
        super(id, x, y, width, height, iconX, iconY, iconWidth, iconHeight, onPress);
    }

    protected LeftTabButton(int id, int x, int y, int width, int height, OnPress onPress) {
        this(id, x, y, width, height, x, y, width, height, onPress);
    }

    @Override
    public void initBackground() {
        setBackgroundSprites(new WidgetSprites(CommonTextures.LEFT_TAB, CommonTextures.LEFT_TAB));
    }
}
