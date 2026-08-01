package com.wintercogs.beyonddimensions.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * GUI 渲染辅助工具（1.7.10 适配版）。
 * <p>
 * 1.7.10 没有 {@code GuiGraphics}、{@code RenderSystem}、{@code GameRenderer}、
 * {@code Matrix4f}、{@code BufferBuilder}、{@code DefaultVertexFormat} 等。
 * 本文件基于 1.7.10 的 Tessellator/GL11 实现全部渲染方法。
 */
@SideOnly(Side.CLIENT)
public final class GuiRenderHelper {

    private GuiRenderHelper() {}

    // ============================================================
    // 颜色常量
    // ============================================================

    public static final int COLOR_WHITE = 0xFFFFFFFF;
    public static final int COLOR_GRAY = 0xFF808080;
    public static final int COLOR_DARK_GRAY = 0xFF404040;
    public static final int COLOR_BLACK = 0xFF000000;
    public static final int COLOR_RED = 0xFFFF0000;
    public static final int COLOR_GREEN = 0xFF00FF00;
    public static final int COLOR_BLUE = 0xFF0000FF;
    public static final int COLOR_YELLOW = 0xFFFFFF00;

    // ============================================================
    // 基本绘制
    // ============================================================

    /**
     * 填充矩形（使用 Tessellator + GL_BLEND）。
     */
    public static void fillRect(int x, int y, int width, int height, int color) {
        float a = (float) (color >> 24 & 255) / 255.0F;
        float r = (float) (color >> 16 & 255) / 255.0F;
        float g = (float) (color >> 8 & 255) / 255.0F;
        float b = (float) (color & 255) / 255.0F;

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(r, g, b, a);

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertex(x, y + height, 0);
        tessellator.addVertex(x + width, y + height, 0);
        tessellator.addVertex(x + width, y, 0);
        tessellator.addVertex(x, y, 0);
        tessellator.draw();

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
    }

    /**
     * 绘制带边框的矩形。
     */
    public static void drawRectBorder(int x, int y, int width, int height, int fillColor, int borderColor) {
        fillRect(x, y, width, height, fillColor);
        fillRect(x, y, width, 1, borderColor);
        fillRect(x, y + height - 1, width, 1, borderColor);
        fillRect(x, y, 1, height, borderColor);
        fillRect(x + width - 1, y, 1, height, borderColor);
    }

    /**
     * 绑定并绘制纹理（假设纹理为 256x256，与 1.7.10 drawTexturedModalRect 一致）。
     */
    public static void drawTexture(ResourceLocation texture, int x, int y, int u, int v, int width, int height) {
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(texture);
        float f = 1.0F / 256.0F;
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(x, y + height, 0.0, u * f, (v + height) * f);
        tessellator.addVertexWithUV(x + width, y + height, 0.0, (u + width) * f, (v + height) * f);
        tessellator.addVertexWithUV(x + width, y, 0.0, (u + width) * f, v * f);
        tessellator.addVertexWithUV(x, y, 0.0, u * f, v * f);
        tessellator.draw();
    }

    /**
     * 绘制字符串。
     */
    public static void drawString(FontRenderer fontRenderer, String text, int x, int y, int color) {
        if (fontRenderer != null) {
            fontRenderer.drawString(text, x, y, color);
        }
    }

    /**
     * 绘制带阴影的字符串。
     */
    public static void drawStringWithShadow(FontRenderer fontRenderer, String text, int x, int y, int color) {
        if (fontRenderer != null) {
            fontRenderer.drawStringWithShadow(text, x, y, color);
        }
    }

    /**
     * 绘制居中的字符串。
     */
    public static void drawCenteredString(FontRenderer fontRenderer, String text, int x, int y, int color) {
        if (fontRenderer != null) {
            fontRenderer.drawString(text, x - fontRenderer.getStringWidth(text) / 2, y, color);
        }
    }

    /**
     * 绘制 IChatComponent 文本。
     */
    public static void drawChatComponent(FontRenderer fontRenderer, IChatComponent component, int x, int y, int color) {
        if (fontRenderer != null && component != null) {
            fontRenderer.drawString(component.getFormattedText(), x, y, color);
        }
    }

    /**
     * 启用 scissor 裁剪（将 GUI 坐标转换为 OpenGL 窗口坐标）。
     * <p>
     * 1.7.10 的 OpenGL 坐标原点在左下角，Y 轴向上；
     * GUI 坐标原点在左上角，Y 轴向下，需要翻转 Y。
     * <p>
     * 使用 {@link ScaledResolution} 获取 scaleFactor，避免手动计算与游戏实际缩放不一致。
     */
    public static void enableScissor(int x, int y, int width, int height) {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution scaledResolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int scaleFactor = scaledResolution.getScaleFactor();

        int scissorX = x * scaleFactor;
        int scissorY = mc.displayHeight - (y + height) * scaleFactor;
        int scissorW = width * scaleFactor;
        int scissorH = height * scaleFactor;

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(scissorX, scissorY, scissorW, scissorH);
    }

    /**
     * 禁用 scissor 裁剪。
     */
    public static void disableScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    /**
     * 设置颜色。
     */
    public static void setColor(float r, float g, float b, float a) {
        GL11.glColor4f(r, g, b, a);
    }

    /**
     * 重置颜色为白色。
     */
    public static void resetColor() {
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * 绘制渐变矩形（从上到下颜色渐变，对齐 1.7.10 Gui.drawGradientRect）。
     */
    public static void drawGradientRect(int x, int y, int width, int height, int topColor, int bottomColor) {
        float a1 = (float) (topColor >> 24 & 255) / 255.0F;
        float r1 = (float) (topColor >> 16 & 255) / 255.0F;
        float g1 = (float) (topColor >> 8 & 255) / 255.0F;
        float b1 = (float) (topColor & 255) / 255.0F;
        float a2 = (float) (bottomColor >> 24 & 255) / 255.0F;
        float r2 = (float) (bottomColor >> 16 & 255) / 255.0F;
        float g2 = (float) (bottomColor >> 8 & 255) / 255.0F;
        float b2 = (float) (bottomColor & 255) / 255.0F;

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glShadeModel(GL11.GL_SMOOTH);

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_F(r1, g1, b1, a1);
        tessellator.addVertex(x, y, 0);
        tessellator.addVertex(x, y + height, 0);
        tessellator.setColorRGBA_F(r2, g2, b2, a2);
        tessellator.addVertex(x + width, y + height, 0);
        tessellator.addVertex(x + width, y, 0);
        tessellator.draw();

        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    /**
     * 绘制水平线。
     */
    public static void drawHorizontalLine(int x, int y, int width, int color) {
        fillRect(x, y, width, 1, color);
    }

    /**
     * 绘制垂直线。
     */
    public static void drawVerticalLine(int x, int y, int height, int color) {
        fillRect(x, y, 1, height, color);
    }

    // ============================================================
    // 纹理绘制（移植自 1.20.1 GuiRenderHelper）
    // ============================================================

    /**
     * 绑定并绘制纹理（支持任意纹理尺寸，等价于 1.20.1 的 10 参 GuiGraphics#blit）。
     * <p>
     * 1.7.10 的 {@code drawTexturedModalRect} 假设纹理为 256×256，无法直接使用，
     * 这里通过 Tessellator 自行计算 UV 比例以支持任意尺寸纹理。
     *
     * @param texture       纹理资源位置
     * @param x             目标位置 X
     * @param y             目标位置 Y
     * @param width         目标绘制宽度
     * @param height        目标绘制高度
     * @param u             纹理起始 U
     * @param v             纹理起始 V
     * @param uWidth        纹理区域宽度
     * @param vHeight       纹理区域高度
     * @param textureWidth  整张纹理宽度
     * @param textureHeight 整张纹理高度
     */
    public static void blit(ResourceLocation texture, int x, int y, int width, int height, int u, int v, int uWidth,
        int vHeight, int textureWidth, int textureHeight) {
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(texture);
        float f = 1.0F / textureWidth;
        float f1 = 1.0F / textureHeight;
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(x, y + height, 0.0, u * f, (v + vHeight) * f1);
        tessellator.addVertexWithUV(x + width, y + height, 0.0, (u + uWidth) * f, (v + vHeight) * f1);
        tessellator.addVertexWithUV(x + width, y, 0.0, (u + uWidth) * f, v * f1);
        tessellator.addVertexWithUV(x, y, 0.0, u * f, v * f1);
        tessellator.draw();
    }

    /**
     * 绘制具有边框的九宫格纹理，并自动处理拉伸。
     * <p>
     * 移植自 1.20.1 GuiRenderHelper#renderBorderedPanel，将 GuiGraphics#blit 替换为
     * 本类的 {@link #blit} 实现。
     *
     * @param texture      纹理资源位置
     * @param x            目标位置 X
     * @param y            目标位置 Y
     * @param width        目标总宽度
     * @param height       目标总高度
     * @param borderTop    上边框大小 (像素)
     * @param borderBottom 下边框大小 (像素)
     * @param borderLeft   左边框大小 (像素)
     * @param borderRight  右边框大小 (像素)
     * @param origWidth    原始纹理宽度
     * @param origHeight   原始纹理高度
     */
    public static void renderBorderedPanel(ResourceLocation texture, int x, int y, int width, int height, int borderTop,
        int borderBottom, int borderLeft, int borderRight, int origWidth, int origHeight) {

        // === 四个角（不拉伸） ===
        // 左上
        blit(texture, x, y, borderLeft, borderTop, 0, 0, borderLeft, borderTop, origWidth, origHeight);

        // 右上
        blit(
            texture,
            x + width - borderRight,
            y,
            borderRight,
            borderTop,
            origWidth - borderRight,
            0,
            borderRight,
            borderTop,
            origWidth,
            origHeight);

        // 左下
        blit(
            texture,
            x,
            y + height - borderBottom,
            borderLeft,
            borderBottom,
            0,
            origHeight - borderBottom,
            borderLeft,
            borderBottom,
            origWidth,
            origHeight);

        // 右下
        blit(
            texture,
            x + width - borderRight,
            y + height - borderBottom,
            borderRight,
            borderBottom,
            origWidth - borderRight,
            origHeight - borderBottom,
            borderRight,
            borderBottom,
            origWidth,
            origHeight);

        // === 四条边（单向拉伸） ===
        int dstEdgeW = width - borderLeft - borderRight;
        int dstEdgeH = height - borderTop - borderBottom;
        int srcEdgeW = origWidth - borderLeft - borderRight;
        int srcEdgeH = origHeight - borderTop - borderBottom;

        // 上边
        if (borderTop > 0) {
            blit(
                texture,
                x + borderLeft,
                y,
                dstEdgeW,
                borderTop,
                borderLeft,
                0,
                srcEdgeW,
                borderTop,
                origWidth,
                origHeight);
        }

        // 下边
        if (borderBottom > 0) {
            blit(
                texture,
                x + borderLeft,
                y + height - borderBottom,
                dstEdgeW,
                borderBottom,
                borderLeft,
                origHeight - borderBottom,
                srcEdgeW,
                borderBottom,
                origWidth,
                origHeight);
        }

        // 左边
        if (borderLeft > 0) {
            blit(
                texture,
                x,
                y + borderTop,
                borderLeft,
                dstEdgeH,
                0,
                borderTop,
                borderLeft,
                srcEdgeH,
                origWidth,
                origHeight);
        }

        // 右边
        if (borderRight > 0) {
            blit(
                texture,
                x + width - borderRight,
                y + borderTop,
                borderRight,
                dstEdgeH,
                origWidth - borderRight,
                borderTop,
                borderRight,
                srcEdgeH,
                origWidth,
                origHeight);
        }

        // === 中心（双向拉伸） ===
        blit(
            texture,
            x + borderLeft,
            y + borderTop,
            dstEdgeW,
            dstEdgeH,
            borderLeft,
            borderTop,
            srcEdgeW,
            srcEdgeH,
            origWidth,
            origHeight);
    }

    /**
     * 绘制整张纹理并缩放到指定宽高。
     * <p>
     * 移植自 1.20.1 GuiRenderHelper#renderFullTexture。
     * 1.7.10 无 RenderSystem/GameRenderer shader API，直接绑定纹理后绘制即可。
     *
     * @param texture        纹理资源路径（不需要是在图集里的）
     * @param x              目标左上角 X
     * @param y              目标左上角 Y
     * @param width          希望绘制出的宽度
     * @param height         希望绘制出的高度
     * @param originalWidth  原始纹理宽度
     * @param originalHeight 原始纹理高度
     */
    public static void renderFullTexture(ResourceLocation texture, int x, int y, int width, int height,
        int originalWidth, int originalHeight) {
        blit(texture, x, y, width, height, 0, 0, originalWidth, originalHeight, originalWidth, originalHeight);
    }

    // ============================================================
    // 文本绘制（移植自 1.20.1 GuiRenderHelper）
    // ============================================================

    /**
     * 右对齐绘制文本（IChatComponent 版本）。
     * <p>
     * 移植自 1.20.1 GuiRenderHelper#drawRightAnchoredText。
     * 1.7.10 用 {@link FontRenderer#getStringWidth(String)} 替代 {@code Font.width(Component)}，
     * 用 {@link FontRenderer#drawString(String, int, int, int, boolean)} 替代
     * {@code GuiGraphics.drawString(Font, Component, int, int, int, boolean)}。
     *
     * @param fontRenderer 字体渲染器
     * @param text         绘制文本
     * @param xRight       右对齐情况下的 x 坐标
     * @param y            y 坐标
     * @param color        字体颜色
     * @param dropShadow   是否绘制字体阴影
     */
    public static void drawRightAnchoredText(FontRenderer fontRenderer, IChatComponent text, int xRight, int y,
        int color, boolean dropShadow) {
        if (fontRenderer == null || text == null) return;
        String formatted = text.getFormattedText();
        int width = fontRenderer.getStringWidth(formatted);
        int xStart = xRight - width;
        fontRenderer.drawString(formatted, xStart, y, color, dropShadow);
    }

    /**
     * 右对齐绘制文本（String 版本）。
     *
     * @param fontRenderer 字体渲染器
     * @param text         绘制文本
     * @param xRight       右对齐情况下的 x 坐标
     * @param y            y 坐标
     * @param color        字体颜色
     * @param dropShadow   是否绘制字体阴影
     */
    public static void drawRightAnchoredText(FontRenderer fontRenderer, String text, int xRight, int y, int color,
        boolean dropShadow) {
        if (fontRenderer == null || text == null) return;
        int width = fontRenderer.getStringWidth(text);
        int xStart = xRight - width;
        fontRenderer.drawString(text, xStart, y, color, dropShadow);
    }
}
