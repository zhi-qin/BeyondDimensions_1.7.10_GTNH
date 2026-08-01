package com.wintercogs.beyonddimensions.util;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import org.lwjgl.opengl.GL11;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 物品/流体图标的渲染器（1.7.10 适配版）。
 * <p>
 * 1.7.10 没有 {@code GuiGraphics}、{@code TextureAtlasSprite}、{@code Material}、
 * {@code InventoryMenu.BLOCK_ATLAS}、{@code RenderSystem}、{@code BufferBuilder}、
 * {@code DefaultVertexFormat}、{@code Matrix4f}。
 * <p>
 * 1.7.10 使用 {@code IIcon} 替代 {@code TextureAtlasSprite}，
 * 使用 {@code Tessellator} 直接绘制。
 */
@SideOnly(Side.CLIENT)
public final class IngredientRenderer {

    private static final int TEXTURE_SIZE = 16;
    private static final RenderItem renderItem = new RenderItem();

    private IngredientRenderer() {}

    /**
     * 渲染物品图标（使用 RenderItem 渲染物品及其特效）。
     *
     * @param stack 物品堆叠
     * @param x     屏幕 X 坐标
     * @param y     屏幕 Y 坐标
     */
    public static void renderItemStack(ItemStack stack, int x, int y) {
        if (stack == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
        renderItem.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, x, y);
        net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
    }

    /**
     * 渲染物品图标（带数量文字）。
     */
    public static void renderItemStackWithCount(ItemStack stack, int x, int y, String countText) {
        if (stack == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
        renderItem.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, x, y);
        renderItem.renderItemOverlayIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, x, y, countText);
        net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
    }

    /**
     * 渲染流体图标。
     *
     * @param icon   流体 IIcon（1.7.10 替代 TextureAtlasSprite）
     * @param color  流体颜色
     * @param x      屏幕 X 坐标
     * @param y      屏幕 Y 坐标
     * @param width  绘制宽度
     * @param height 绘制高度
     */
    public static void renderFluidIcon(IIcon icon, int color, int x, int y, int width, int height) {
        if (icon == null) return;
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(TextureMap.locationBlocksTexture);

        float a = (float) (color >> 24 & 255) / 255.0F;
        float r = (float) (color >> 16 & 255) / 255.0F;
        float g = (float) (color >> 8 & 255) / 255.0F;
        float b = (float) (color & 255) / 255.0F;

        GL11.glColor4f(r, g, b, a);

        double uMin = icon.getMinU();
        double uMax = icon.getMaxU();
        double vMin = icon.getMinV();
        double vMax = icon.getMaxV();

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(x, y + height, 0, uMin, vMax);
        tessellator.addVertexWithUV(x + width, y + height, 0, uMax, vMax);
        tessellator.addVertexWithUV(x + width, y, 0, uMax, vMin);
        tessellator.addVertexWithUV(x, y, 0, uMin, vMin);
        tessellator.draw();

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * 渲染 StackKey 的图标（委托给 IStackRender.render）。
     */
    public static void renderStackKey(IStackKey<?> key, int x, int y) {
        if (key == null) return;
        key.getRender()
            .render(key, x, y);
    }

    /**
     * 渲染 StackKey 的图标（带数量文字，委托给 IStackRender）。
     */
    public static void renderStackKeyWithAmount(IStackKey<?> key, long amount, int x, int y) {
        if (key == null) return;
        key.getRender()
            .render(key, x, y);
        key.getRender()
            .renderAmount(amount, x, y);
    }

    /**
     * 获取工具提示行（委托给 IStackRender.getTooltipLines）。
     */
    public static List<String> getTooltip(IStackKey<?> key, long amount, boolean advanced) {
        if (key == null) return java.util.Collections.emptyList();
        return key.getRender()
            .getTooltipLines(key, amount, Minecraft.getMinecraft().thePlayer, advanced);
    }

    // ============================================================
    // 平铺 Sprite 绘制（移植自 1.20.1 IngredientRenderer）
    // ============================================================

    // TODO: 1.20.1 中存在以下两个 Material 常量用于 mod 集成渲染：
    // public static final Material ARS_SOURCE = new Material(InventoryMenu.BLOCK_ATLAS, new
    // ResourceLocation("ars_nouveau", "block/mana_still"));
    // public static final Material BOTANIA_MANA = new Material(InventoryMenu.BLOCK_ATLAS, new
    // ResourceLocation("botania", "block/mana_water"));
    // 1.7.10 没有 Material 类与 InventoryMenu.BLOCK_ATLAS，且对应 mod 的集成方式不同，
    // 如需在 1.7.10 中实现魔源/魔力渲染，应直接使用对应 mod 的 IIcon/纹理 API。

    /**
     * 平铺绘制 IIcon（用于流体等图标的垂直填充渲染）。
     * <p>
     * 移植自 1.20.1 IngredientRenderer#drawTiledSprite，主要适配点：
     * <ul>
     * <li>{@code TextureAtlasSprite} → {@link IIcon}</li>
     * <li>{@code sprite.atlasLocation()} → {@link TextureMap#locationBlocksTexture}</li>
     * <li>{@code RenderSystem.enableBlend()} → {@code GL11.glEnable(GL11.GL_BLEND)}</li>
     * <li>{@code RenderSystem.setShaderTexture/setShaderColor} → 绑定纹理 + {@code GL11.glColor4f}</li>
     * <li>{@code Matrix4f}（来自 GuiGraphics#pose）→ 直接使用屏幕坐标</li>
     * </ul>
     *
     * @param tiledWidth   平铺总宽度
     * @param tiledHeight  平铺总高度
     * @param color        颜色（RGB int，与 1.20.1 一致不含 alpha）
     * @param scaledAmount 实际填充量（从底部往上填充）
     * @param icon         流体 IIcon（1.7.10 替代 TextureAtlasSprite）
     * @param posX         屏幕 X 坐标
     * @param posY         屏幕 Y 坐标（顶部）
     */
    public static void drawTiledSprite(int tiledWidth, int tiledHeight, int color, long scaledAmount, IIcon icon,
        int posX, int posY) {
        if (icon == null) return;

        GL11.glEnable(GL11.GL_BLEND);
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(TextureMap.locationBlocksTexture);
        setGLColorFromInt(color);

        final int xTileCount = tiledWidth / TEXTURE_SIZE;
        final int xRemainder = tiledWidth - (xTileCount * TEXTURE_SIZE);
        final long yTileCount = scaledAmount / TEXTURE_SIZE;
        final long yRemainder = scaledAmount - (yTileCount * TEXTURE_SIZE);

        final int yStart = tiledHeight + posY;

        for (int xTile = 0; xTile <= xTileCount; xTile++) {
            for (int yTile = 0; yTile <= yTileCount; yTile++) {
                int width = (xTile == xTileCount) ? xRemainder : TEXTURE_SIZE;
                long height = (yTile == yTileCount) ? yRemainder : TEXTURE_SIZE;
                int x = posX + (xTile * TEXTURE_SIZE);
                int y = yStart - ((yTile + 1) * TEXTURE_SIZE);
                if (width > 0 && height > 0) {
                    long maskTop = TEXTURE_SIZE - height;
                    int maskRight = TEXTURE_SIZE - width;

                    drawTextureWithMasking(x, y, icon, maskTop, maskRight, 100);
                }
            }
        }

        GL11.glColor4f(1, 1, 1, 1);
        GL11.glDisable(GL11.GL_BLEND);
    }

    /**
     * 从 int 颜色值设置 GL 颜色（移植自 1.20.1 IngredientRenderer#setGLColorFromInt）。
     * <p>
     * 1.7.10 用 {@code GL11.glColor4f} 替代 {@code RenderSystem.setShaderColor}。
     * 注意：与源项目保持一致，使用 /256f 而非 /255f，alpha 固定为 1。
     */
    private static void setGLColorFromInt(int color) {
        float red = ((color >> 16) & 255) / 256f;
        float green = ((color >> 8) & 255) / 256f;
        float blue = (color & 255) / 256f;
        float alpha = 1;

        GL11.glColor4f(red, green, blue, alpha);
    }

    /**
     * 带遮罩的纹理绘制（移植自 1.20.1 IngredientRenderer#drawTextureWithMasking）。
     * <p>
     * 主要适配点：
     * <ul>
     * <li>{@code TextureAtlasSprite} → {@link IIcon}，UV 通过 {@code getMinU/MaxU/MinV/MaxV} 获取</li>
     * <li>{@code Matrix4f} 参数移除，直接使用屏幕坐标</li>
     * <li>{@code Tesselator.getInstance().getBuilder()} → {@link Tessellator#instance}</li>
     * <li>{@code BufferBuilder.begin(QUADS, POSITION_TEX)} → {@code startDrawingQuads()}</li>
     * <li>{@code vertex(matrix,x,y,z).uv(u,v).endVertex()} → {@code addVertexWithUV(x,y,z,u,v)}</li>
     * </ul>
     *
     * @param xCoord    屏幕 X 坐标
     * @param yCoord    屏幕 Y 坐标
     * @param icon      IIcon（1.7.10 替代 TextureAtlasSprite）
     * @param maskTop   顶部遮罩像素数
     * @param maskRight 右侧遮罩像素数
     * @param zLevel    Z 层级
     */
    private static void drawTextureWithMasking(float xCoord, float yCoord, IIcon icon, long maskTop, long maskRight,
        float zLevel) {
        double uMin = icon.getMinU();
        double uMax = icon.getMaxU();
        double vMin = icon.getMinV();
        double vMax = icon.getMaxV();
        uMax = uMax - (maskRight / 16.0 * (uMax - uMin));
        vMax = vMax - (maskTop / 16.0 * (vMax - vMin));

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(xCoord, yCoord + 16, zLevel, uMin, vMax);
        tessellator.addVertexWithUV(xCoord + 16 - maskRight, yCoord + 16, zLevel, uMax, vMax);
        tessellator.addVertexWithUV(xCoord + 16 - maskRight, yCoord + maskTop, zLevel, uMax, vMin);
        tessellator.addVertexWithUV(xCoord, yCoord + maskTop, zLevel, uMin, vMin);
        tessellator.draw();
    }
}
