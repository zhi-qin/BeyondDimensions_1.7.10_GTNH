package com.wintercogs.beyonddimensions.client.gui.widget.shared;

/**
 * 自定义 UI 元素访问接口（1.7.10 移植版）。
 * <p>
 * 1.20.1 的 {@code Rect2i} 在 1.7.10 中不存在，这里以 {@code int[]} 形式返回
 * 元素的屏幕区域：{@code [x, y, width, height]}。
 */
public interface GuiElementAccess {

    /**
     * 获取当前 UI 元素所占的屏幕空间，格式为 {@code [x, y, width, height]}。
     */
    int[] getElementArea();
}
