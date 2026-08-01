package com.wintercogs.beyonddimensions.client.gui.widget.button;

import net.minecraft.util.ResourceLocation;

import com.wintercogs.beyonddimensions.api.ButtonState;
import com.wintercogs.beyonddimensions.api.ids.BDConstants;
import com.wintercogs.beyonddimensions.client.gui.widget.shared.OnPress;
import com.wintercogs.beyonddimensions.client.gui.widget.shared.StatusButton;
import com.wintercogs.beyonddimensions.config.CommonConfigRuntime;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 搜索切换按钮（1.7.10 移植版）。
 * <p>
 * 移植自 1.20.1 的 {@code SearchToggleButton}。在两个状态间切换：禁用搜索/启用搜索。
 */
@SideOnly(Side.CLIENT)
public class SearchToggleButton extends StatusButton {

    public SearchToggleButton(int id, int x, int y, OnPress onPress) {
        super(id, x, y, 16, 16, onPress);
    }

    @Override
    protected void initButton() {
        iconMap.put(
            ButtonState.DISABLED,
            new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/search_disable.png"));
        iconMap.put(
            ButtonState.ENABLED,
            new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/search_enable.png"));

        tooltipMap.put(ButtonState.DISABLED, "tooltip.button.beyonddimensions.search_disable");
        tooltipMap.put(ButtonState.ENABLED, "tooltip.button.beyonddimensions.search_enable");

        for (Enum<?> state : iconMap.keySet()) {
            this.states.add(state);
        }
        setState(CommonConfigRuntime.uiSearchButton);
    }
}
