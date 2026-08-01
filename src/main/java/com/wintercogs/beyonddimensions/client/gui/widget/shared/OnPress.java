package com.wintercogs.beyonddimensions.client.gui.widget.shared;

import net.minecraft.client.gui.GuiButton;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 按钮按下回调接口（1.7.10 移植版）。
 * <p>
 * 1.20.1 的 {@code net.minecraft.client.gui.components.Button.OnPress} 在 1.7.10 中不存在，
 * 这里提供一个等价的函数式接口，由 {@link IconButton} 在被点击时触发。
 */
@SideOnly(Side.CLIENT)
@FunctionalInterface
public interface OnPress {

    void onPress(GuiButton button);
}
