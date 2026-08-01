package com.wintercogs.beyonddimensions.client.gui.widget.scroller;

import java.util.function.IntConsumer;

import com.wintercogs.beyonddimensions.client.gui.CommonTextures;
import com.wintercogs.beyonddimensions.client.gui.widget.shared.ScrollBar;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 大滚动条（1.7.10 移植版）。
 * <p>
 * 移植自 1.20.1 的 {@code BigScroller}，固定尺寸为 12×15，使用
 * {@link CommonTextures#SCROLLER} 贴图。
 */
@SideOnly(Side.CLIENT)
public class BigScroller extends ScrollBar {

    public BigScroller(int x, int y, int maxScrollLength, int currentPosition, int maxPosition, IntConsumer onScroll) {
        super(
            x,
            y,
            CommonTextures.SCROLLER_WIDTH,
            CommonTextures.SCROLLER_HEIGHT,
            CommonTextures.SCROLLER,
            maxScrollLength,
            currentPosition,
            maxPosition,
            onScroll);
    }
}
