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
 * 倒序切换按钮（1.7.10 移植版）。
 * <p>
 * 移植自 1.20.1 的 {@code ReverseButton}。在两个状态间切换：升序/降序。
 */
@SideOnly(Side.CLIENT)
public class ReverseButton extends StatusButton {

    public ReverseButton(int id, int x, int y, OnPress onPress) {
        super(id, x, y, 16, 16, onPress);
    }

    @Override
    protected void initButton() {
        iconMap.put(
            ButtonState.DISABLED,
            new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/sort_asc.png"));
        iconMap.put(
            ButtonState.ENABLED,
            new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/sort_desc.png"));

        tooltipMap.put(ButtonState.DISABLED, "tooltip.button.beyonddimensions.sort_asc");
        tooltipMap.put(ButtonState.ENABLED, "tooltip.button.beyonddimensions.sort_desc");

        for (Enum<?> state : iconMap.keySet()) {
            this.states.add(state);
        }
        setState(CommonConfigRuntime.uiReverseButton);
    }
}
