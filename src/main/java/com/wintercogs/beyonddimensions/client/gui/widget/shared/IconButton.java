package com.wintercogs.beyonddimensions.client.gui.widget.shared;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.wintercogs.beyonddimensions.api.ids.BDConstants;
import com.wintercogs.beyonddimensions.util.GuiRenderHelper;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 图标按钮（1.7.10 移植版）。
 * <p>
 * 移植自 1.20.1 的 {@code IconButton}。1.20.1 继承 {@code Button}（{@code AbstractWidget}），
 * 1.7.10 改为继承 {@link GuiButton} 并覆写 {@link #drawButton} 与 {@link #mousePressed}。
 * <p>
 * 渲染适配：{@code GuiGraphics#blit} → {@link GuiRenderHelper#blit}；
 * {@code RenderSystem#enableBlend} → {@code GL11#glEnable}。
 */
@SideOnly(Side.CLIENT)
public class IconButton extends GuiButton implements GuiElementAccess {

    protected ResourceLocation icon;

    protected final int iconX;
    protected final int iconY;
    protected final int iconWidth;
    protected final int iconHeight;

    protected WidgetSprites backgroundSprites = new WidgetSprites(
        new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/slot_button.png"),
        new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/slot_button_disabled.png"),
        new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/slot_button_hovered.png"));

    protected final OnPress onPress;

    /** 当前帧鼠标是否悬停（由 {@link #drawButton} 更新，供 tooltip 渲染使用） */
    protected boolean hovered;

    public IconButton(int id, int x, int y, int width, int height, ResourceLocation icon, int iconX, int iconY,
        int iconWidth, int iconHeight, OnPress onPress) {
        super(id, x, y, width, height, "");
        this.icon = icon;
        this.iconX = iconX;
        this.iconY = iconY;
        this.iconWidth = iconWidth;
        this.iconHeight = iconHeight;
        this.onPress = onPress;
        initBackground();
    }

    public IconButton(int id, int x, int y, int width, int height, ResourceLocation icon, OnPress onPress) {
        this(id, x, y, width, height, icon, x, y, width, height, onPress);
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!this.visible) return;
        this.hovered = mouseX >= this.xPosition && mouseY >= this.yPosition
            && mouseX < this.xPosition + this.width
            && mouseY < this.yPosition + this.height;
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        ResourceLocation texture = backgroundSprites.get(this.enabled, this.hovered);
        GuiRenderHelper.blit(
            texture,
            this.xPosition,
            this.yPosition,
            this.width,
            this.height,
            0,
            0,
            this.width,
            this.height,
            this.width,
            this.height);
        drawIcon(mc, mouseX, mouseY);

        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GL11.glDisable(GL11.GL_BLEND);
    }

    protected void drawIcon(Minecraft mc, int mouseX, int mouseY) {
        if (this.icon != null) {
            GuiRenderHelper.blit(
                this.icon,
                this.iconX,
                this.iconY,
                this.iconWidth,
                this.iconHeight,
                0,
                0,
                this.iconWidth,
                this.iconHeight,
                this.iconWidth,
                this.iconHeight);
        }
    }

    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        if (super.mousePressed(mc, mouseX, mouseY)) {
            if (this.onPress != null) {
                this.onPress.onPress(this);
            }
            return true;
        }
        return false;
    }

    /** 用于子类覆写背景初始化 */
    public void initBackground() {}

    public void setBackgroundSprites(WidgetSprites backgroundSprites) {
        this.backgroundSprites = backgroundSprites;
    }

    public ResourceLocation getIcon() {
        return icon;
    }

    public void setIcon(ResourceLocation icon) {
        this.icon = icon;
    }

    @Override
    public int[] getElementArea() {
        return new int[] { this.xPosition, this.yPosition, this.width, this.height };
    }

    public boolean isHovered() {
        return hovered;
    }
}
