package com.wintercogs.beyonddimensions.api.storage.key.render;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.FluidRegistry;

import org.lwjgl.opengl.GL11;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.IStackRender;
import com.wintercogs.beyonddimensions.util.StringFormat;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 能量条目渲染器（对齐源项目 EnergyStackKeyRender）。
 * <p>
 * 维度网络终端的存储网格中，能量以"绿色水滴图标 + 缩写数量"出现，
 * 与物品/流体条目混排；悬停提示显示名称与精确储量。
 * <p>
 * 图标采用源项目同款占位方案：水静态贴图 + 能量绿（0x50F18E）着色。
 */
public final class EnergyStackKeyRender implements IStackRender {

    public static final EnergyStackKeyRender INSTANCE = new EnergyStackKeyRender();

    /** 能量绿（源项目占位图标配色） */
    private static final int ENERGY_TINT = 0x50F18E;

    private EnergyStackKeyRender() {}

    @Override
    @SideOnly(Side.CLIENT)
    public void render(IStackKey<?> key, int x, int y) {
        IIcon icon = FluidRegistry.WATER.getStillIcon();
        if (icon == null) return;

        Minecraft mc = Minecraft.getMinecraft();
        mc.getTextureManager()
            .bindTexture(TextureMap.locationBlocksTexture);

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        float r = ((ENERGY_TINT >> 16) & 0xFF) / 255.0F;
        float g = ((ENERGY_TINT >> 8) & 0xFF) / 255.0F;
        float b = (ENERGY_TINT & 0xFF) / 255.0F;
        GL11.glColor4f(r, g, b, 1.0F);

        drawTexturedModelRectFromIcon(x, y, icon, 16, 16);

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderAmount(long amount, int x, int y) {
        String text = getCountText(amount);
        if (text.isEmpty()) return;

        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        // 对齐源项目：0.666 缩放，右下角对齐
        float scale = 0.666F;
        GL11.glPushMatrix();
        GL11.glScalef(scale, scale, scale);
        int w = font.getStringWidth(text);
        int drawX = (int) ((x - 1 + 16.0F + 2.0F - w * scale) / scale);
        int drawY = (int) ((y - 1 + 16.0F - 5.0F * scale) / scale);
        font.drawStringWithShadow(text, drawX, drawY, 0xFFFFFF);
        GL11.glPopMatrix();
    }

    @Override
    public String getCountText(long count) {
        // 与能量通道 GUI（GuiNetEnergy）同一格式化器：k/M/G/T/P/E 单位，截断取整
        return StringFormat.formatCount(count);
    }

    @Override
    public String getDisplayName(IStackKey<?> key) {
        // 对齐源项目：getRenderStack().getName() → types.beyonddimensions.energytype.name（FE/FE能量）
        return StatCollector.translateToLocal("types.beyonddimensions.energytype.name");
    }

    @Override
    public List<String> getTooltipLines(IStackKey<?> key, long amount, EntityPlayer player, boolean advanced) {
        List<String> lines = new ArrayList<>(2);
        lines.add(getDisplayName(key));
        lines.add(
            StatCollector
                .translateToLocalFormatted("istack.beyonddimensions.storage_num.long_type", String.valueOf(amount)));
        return lines;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderTooltip(IStackKey<?> key, long amount, int mouseX, int mouseY) {
        // 提示框由 GUI 层绘制（GuiDimensionsNet 经 getTooltipLines 调用 drawHoveringText，
        // 1.7.10 的 drawHoveringText 为 protected，渲染器内无法直接访问）
    }

    /**
     * 从 IIcon 绘制纹理矩形（1.7.10 兼容方法）
     */
    @SideOnly(Side.CLIENT)
    private static void drawTexturedModelRectFromIcon(int x, int y, IIcon icon, int width, int height) {
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(x, y + height, 0, icon.getMinU(), icon.getMaxV());
        tessellator.addVertexWithUV(x + width, y + height, 0, icon.getMaxU(), icon.getMaxV());
        tessellator.addVertexWithUV(x + width, y, 0, icon.getMaxU(), icon.getMinV());
        tessellator.addVertexWithUV(x, y, 0, icon.getMinU(), icon.getMinV());
        tessellator.draw();
    }
}
