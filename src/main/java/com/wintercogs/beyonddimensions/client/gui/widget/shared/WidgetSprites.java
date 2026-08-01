package com.wintercogs.beyonddimensions.client.gui.widget.shared;

import net.minecraft.util.ResourceLocation;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 控件背景贴图集合（1.7.10 移植版）。
 * <p>
 * 移植自 1.20.1 的 {@code WidgetSprites} record，因 1.7.10 不支持 record，改为普通类。
 * 包含四种状态的纹理：启用/禁用、是否聚焦（鼠标悬停）。
 */
@SideOnly(Side.CLIENT)
public final class WidgetSprites {

    public final ResourceLocation enabled;
    public final ResourceLocation disabled;
    public final ResourceLocation enabledFocused;
    public final ResourceLocation disabledFocused;

    public WidgetSprites(ResourceLocation noFocused, ResourceLocation focused) {
        this(noFocused, noFocused, focused, focused);
    }

    public WidgetSprites(ResourceLocation enabled, ResourceLocation disabled, ResourceLocation enabledFocused) {
        this(enabled, disabled, enabledFocused, disabled);
    }

    public WidgetSprites(ResourceLocation enabled, ResourceLocation disabled, ResourceLocation enabledFocused,
        ResourceLocation disabledFocused) {
        this.enabled = enabled;
        this.disabled = disabled;
        this.enabledFocused = enabledFocused;
        this.disabledFocused = disabledFocused;
    }

    /**
     * 根据启用状态与聚焦状态获取对应贴图。
     *
     * @param enabled 是否启用
     * @param focused 是否聚焦（鼠标悬停）
     */
    public ResourceLocation get(boolean enabled, boolean focused) {
        if (enabled) {
            return focused ? this.enabledFocused : this.enabled;
        } else {
            return focused ? this.disabledFocused : this.disabled;
        }
    }

    public ResourceLocation enabled() {
        return this.enabled;
    }

    public ResourceLocation disabled() {
        return this.disabled;
    }

    public ResourceLocation enabledFocused() {
        return this.enabledFocused;
    }

    public ResourceLocation disabledFocused() {
        return this.disabledFocused;
    }
}
