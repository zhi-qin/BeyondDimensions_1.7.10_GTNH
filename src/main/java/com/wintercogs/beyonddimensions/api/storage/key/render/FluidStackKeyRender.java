package com.wintercogs.beyonddimensions.api.storage.key.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import org.lwjgl.opengl.GL11;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.IStackRender;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.util.StringFormat;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public final class FluidStackKeyRender implements IStackRender {

    public static final FluidStackKeyRender INSTANCE = new FluidStackKeyRender();

    private FluidStackKeyRender() {}

    @Override
    @SideOnly(Side.CLIENT)
    public void render(IStackKey<?> key, int x, int y) {
        if (!(key instanceof FluidStackKey)) return;
        FluidStack stack = ((FluidStackKey) key).getRenderStack();
        if (stack == null) return;

        Fluid fluid = stack.getFluid();
        if (fluid == null) return;

        IIcon icon = fluid.getStillIcon();
        if (icon == null) return;

        Minecraft mc = Minecraft.getMinecraft();
        mc.getTextureManager()
            .bindTexture(TextureMap.locationBlocksTexture);

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        int color = fluid.getColor(stack);
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        GL11.glColor4f(r, g, b, 1.0F);

        drawTexturedModelRectFromIcon(x, y, icon, 16, 16);

        GL11.glDisable(GL11.GL_BLEND);
    }

    @Override
    public void renderAmount(long amount, int x, int y) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        String text = getCountText(amount);
        if (text.isEmpty()) return;
        font.drawStringWithShadow(text, x + 19 - 2 - font.getStringWidth(text), y + 6 + 3, 0xFFFFFF);
    }

    @Override
    public String getCountText(long count) {
        // 对齐 1.20.1 源项目 StringFormat.formatBucket：
        // 流体数量以 mB 存储，显示时转为桶（<1000 mB 显示小数桶，如 500 mB → "0.5"）
        if (count < 0) return "";
        return StringFormat.formatBucket(count);
    }

    @Override
    public String getDisplayName(IStackKey<?> key) {
        if (!(key instanceof FluidStackKey)) return "";
        FluidStack stack = ((FluidStackKey) key).getRenderStack();
        return stack == null ? "" : stack.getLocalizedName();
    }

    @Override
    public List<String> getTooltipLines(IStackKey<?> key, long amount, EntityPlayer player, boolean advanced) {
        if (!(key instanceof FluidStackKey)) return Collections.emptyList();
        FluidStack stack = ((FluidStackKey) key).getRenderStack();
        if (stack == null) return Collections.emptyList();
        // 对齐 1.20.1 源项目：显示名 + "已存储：XmB" 两行（lang 键已存在，此前硬编码 "(X mB)" 且未用键）
        ArrayList<String> lines = new ArrayList<>();
        lines.add(stack.getLocalizedName());
        lines.add(StatCollector.translateToLocalFormatted("istack.beyonddimensions.storage_num.fluid", amount));
        // 按 Shift（advanced）悬停时显示流体注册名：区分 GT steam / ic2steam 等同名流体
        // （对齐 1.7.10 F3+H 高级提示显示注册名的惯例；GT 蒸汽机只认 Materials.Steam）
        if (advanced && stack.getFluid() != null) {
            lines.add(
                stack.getFluid()
                    .getName());
        }
        return lines;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderTooltip(IStackKey<?> key, long amount, int mouseX, int mouseY) {
        // 1.7.10 GuiScreen#drawHoveringText 为 protected，需要 GuiScreen 子类或反射调用。
        // 在 GUI 阶段实现具体的提示框绘制。
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
