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
 * 排序方式按钮（1.7.10 移植版）。
 * <p>
 * 移植自 1.20.1 的 {@code SortMethodButton}。在多种排序方式间循环切换。
 */
@SideOnly(Side.CLIENT)
public class SortMethodButton extends StatusButton {

    public SortMethodButton(int id, int x, int y, OnPress onPress) {
        super(id, x, y, 16, 16, onPress);
    }

    @Override
    protected void initButton() {
        iconMap.put(
            ButtonState.SORT_CREATIVE_TAB,
            new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/sort_creative_tab.png"));
        iconMap.put(
            ButtonState.SORT_MAX_STACK,
            new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/sort_max_stack.png"));
        iconMap.put(
            ButtonState.SORT_QUANTITY,
            new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/sort_quantity.png"));
        iconMap.put(
            ButtonState.SORT_NAME,
            new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/sort_name.png"));
        iconMap.put(
            ButtonState.SORT_MODID,
            new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/sort_modid.png"));
        iconMap.put(
            ButtonState.SORT_INSERTED_TIME,
            new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/sort_inserted_time.png"));
        iconMap.put(
            ButtonState.SORT_MODIFIED_TIME,
            new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/sort_modified_time.png"));

        tooltipMap.put(ButtonState.SORT_CREATIVE_TAB, "tooltip.button.beyonddimensions.sort_creative_tab");
        tooltipMap.put(ButtonState.SORT_MAX_STACK, "tooltip.button.beyonddimensions.sort_max_stack");
        tooltipMap.put(ButtonState.SORT_QUANTITY, "tooltip.button.beyonddimensions.sort_quantity");
        tooltipMap.put(ButtonState.SORT_NAME, "tooltip.button.beyonddimensions.sort_name");
        tooltipMap.put(ButtonState.SORT_MODID, "tooltip.button.beyonddimensions.sort_modid");
        tooltipMap.put(ButtonState.SORT_INSERTED_TIME, "tooltip.button.beyonddimensions.sort_inserted_time");
        tooltipMap.put(ButtonState.SORT_MODIFIED_TIME, "tooltip.button.beyonddimensions.sort_modified_time");

        for (Enum<?> state : iconMap.keySet()) {
            this.states.add(state);
        }
        setState(CommonConfigRuntime.uiSortButton);
    }
}
