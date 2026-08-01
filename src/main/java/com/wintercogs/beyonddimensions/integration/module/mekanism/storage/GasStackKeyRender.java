package com.wintercogs.beyonddimensions.integration.module.mekanism.storage;

import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.IIcon;

import org.lwjgl.opengl.GL11;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.IStackRender;
import com.wintercogs.beyonddimensions.util.StringFormat;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;

/**
 * GasStackKey 的渲染器（1.7.10 适配版）。
 * <p>
 * 参照 {@link com.wintercogs.beyonddimensions.api.storage.key.render.FluidStackKeyRender} 的实现风格，
 * 使用 Gas 的 IIcon（gas.getIcon()）渲染图标。若 Gas 为 null 或图标缺失，绘制灰色方块占位。
 */
public final class GasStackKeyRender implements IStackRender {

    public static final GasStackKeyRender INSTANCE = new GasStackKeyRender();

    private GasStackKeyRender() {}

    @Override
    @SideOnly(Side.CLIENT)
    public void render(IStackKey<?> key, int x, int y) {
        if (!(key instanceof GasStackKey)) {
            drawGraySquare(x, y);
            return;
        }
        GasStack stack = ((GasStackKey) key).getRenderStack();
        if (stack == null) {
            drawGraySquare(x, y);
            return;
        }

        Gas gas = stack.getGas();
        if (gas == null) {
            drawGraySquare(x, y);
            return;
        }

        IIcon icon = gas.getIcon();
        if (icon == null) {
            drawGraySquare(x, y);
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        mc.getTextureManager()
            .bindTexture(TextureMap.locationBlocksTexture);

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        drawTexturedModelRectFromIcon(x, y, icon, 16, 16);

        GL11.glDisable(GL11.GL_BLEND);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderAmount(long amount, int x, int y) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        String text = getCountText(amount);
        if (text.isEmpty()) return;
        font.drawStringWithShadow(text, x + 19 - 2 - font.getStringWidth(text), y + 6 + 3, 0xFFFFFF);
    }

    @Override
    public String getCountText(long count) {
        // 对齐 1.20.1 源项目 ChemicalStackKeyRender：StringFormat.formatBucket
        // Gas 数量以 mB 存储，显示时先转为桶（1000 mB = 1）再缩写，与流体条目一致
        if (count < 0) return "";
        return StringFormat.formatBucket(count);
    }

    @Override
    public String getDisplayName(IStackKey<?> key) {
        if (!(key instanceof GasStackKey)) return "";
        GasStack stack = ((GasStackKey) key).getRenderStack();
        if (stack == null || stack.getGas() == null) return "";
        return stack.getGas()
            .getLocalizedName();
    }

    @Override
    public List<String> getTooltipLines(IStackKey<?> key, long amount, EntityPlayer player, boolean advanced) {
        if (!(key instanceof GasStackKey)) return Collections.emptyList();
        GasStack stack = ((GasStackKey) key).getRenderStack();
        if (stack == null || stack.getGas() == null) return Collections.emptyList();
        return Collections.singletonList(
            stack.getGas()
                .getLocalizedName() + " ("
                + amount
                + ")");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderTooltip(IStackKey<?> key, long amount, int mouseX, int mouseY) {
        // 1.7.10 GuiScreen#drawHoveringText 为 protected，需要 GuiScreen 子类或具体 GUI 实现中调用。
        // 在 GUI 阶段实现具体的提示框绘制。
    }

    /**
     * 绘制灰色占位方块（Gas 为 null 或图标缺失时使用）
     */
    @SideOnly(Side.CLIENT)
    private static void drawGraySquare(int x, int y) {
        Gui.drawRect(x, y, x + 16, y + 16, 0xFF808080);
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
