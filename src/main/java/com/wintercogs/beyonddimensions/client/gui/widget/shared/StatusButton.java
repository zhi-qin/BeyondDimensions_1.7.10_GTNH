package com.wintercogs.beyonddimensions.client.gui.widget.shared;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.util.ResourceLocation;

import com.wintercogs.beyonddimensions.api.ids.BDConstants;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 多状态按钮（1.7.10 移植版）。
 * <p>
 * 移植自 1.20.1 的 {@code StatusButton}。基于 {@link IconButton}，扩展出
 * “多状态切换”能力：每个状态对应一个图标与可选 tooltip 文本（翻译键）。
 * <p>
 * 1.20.1 的 {@code net.minecraft.client.gui.components.Tooltip} 在 1.7.10 中不存在，
 * 这里以 {@code String}（翻译键）替代，由 GUI 端在悬停时调用
 * {@code StatCollector.translateToLocal} 进行展示。
 */
@SideOnly(Side.CLIENT)
public abstract class StatusButton extends IconButton {

    protected ArrayList<Enum<?>> states = new ArrayList<>();
    /** 保证按钮切换顺序按照插入顺序 */
    protected Map<Enum<?>, ResourceLocation> iconMap = new LinkedHashMap<>();
    /** 可变工具提示（翻译键），需要固定工具提示则直接 {@link #setTooltip(String)}，此处留空 */
    protected Map<Enum<?>, String> tooltipMap = new LinkedHashMap<>();
    public Enum<?> currentState;

    /** 当前工具提示（翻译键），可由外部读取用于渲染 */
    protected String tooltip;

    protected StatusButton(int id, int x, int y, int width, int height, int iconX, int iconY, int iconWidth,
        int iconHeight, OnPress onPress) {
        // 给予一个默认图片用于构造父类
        super(
            id,
            x,
            y,
            width,
            height,
            new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/unkonw_thing.png"),
            iconX,
            iconY,
            iconWidth,
            iconHeight,
            onPress);
        initButton();
        setIcon(iconMap.get(currentState));
    }

    protected StatusButton(int id, int x, int y, int width, int height, OnPress onPress) {
        this(id, x, y, width, height, x, y, width, height, onPress);
    }

    /** 用于子类初始化状态、状态图片映射表、当前状态 */
    protected abstract void initButton();

    /** 快速切换到下一个状态（循环） */
    public void toggleState() {
        if (states == null || states.isEmpty()) {
            throw new IllegalStateException("Button states are not initialized.");
        }

        int currentIndex = states.indexOf(currentState);
        if (currentIndex == -1) {
            throw new IllegalStateException("Current state is not in the states list.");
        }

        int nextIndex = (currentIndex + 1) % states.size();
        currentState = states.get(nextIndex);
        setIcon(iconMap.get(currentState));

        if (tooltipMap != null && tooltipMap.containsKey(currentState) && tooltipMap.get(currentState) != null) {
            setTooltip(tooltipMap.get(currentState));
        }
    }

    /** 手动设置当前状态 */
    public void setState(Enum<?> state) {
        currentState = state;
        setIcon(iconMap.get(currentState));

        if (tooltipMap != null && tooltipMap.containsKey(currentState) && tooltipMap.get(currentState) != null) {
            setTooltip(tooltipMap.get(currentState));
        }
    }

    @Override
    public ResourceLocation getIcon() {
        return super.getIcon();
    }

    public String getTooltip() {
        return tooltip;
    }

    public void setTooltip(String tooltip) {
        this.tooltip = tooltip;
    }
}
