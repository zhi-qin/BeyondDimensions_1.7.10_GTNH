package com.wintercogs.beyonddimensions.client.gui.widget.shared;

import com.wintercogs.beyonddimensions.client.gui.CommonTextures;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 右侧标签按钮（1.7.10 移植版）。
 * <p>
 * 移植自 1.20.1 的 {@code RightTabButton}。背景使用 {@link CommonTextures#RIGHT_TAB}。
 */
@SideOnly(Side.CLIENT)
public abstract class RightTabButton extends StatusButton {

    protected RightTabButton(int id, int x, int y, int width, int height, int iconX, int iconY, int iconWidth,
        int iconHeight, OnPress onPress) {
        super(id, x, y, width, height, iconX, iconY, iconWidth, iconHeight, onPress);
    }

    protected RightTabButton(int id, int x, int y, int width, int height, OnPress onPress) {
        this(id, x, y, width, height, x, y, width, height, onPress);
    }

    @Override
    public void initBackground() {
        setBackgroundSprites(new WidgetSprites(CommonTextures.RIGHT_TAB, CommonTextures.RIGHT_TAB));
    }
}
