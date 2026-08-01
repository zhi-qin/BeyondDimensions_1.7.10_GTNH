package com.wintercogs.beyonddimensions.integration.module.botania.storage;

import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.IStackRender;
import com.wintercogs.beyonddimensions.util.StringFormat;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Mana StackKey 渲染器（1.7.10 适配版）。
 * <p>
 * 使用 Botania 的 Mana 颜色（蓝绿色 #00C6FF）绘制一个 16x16 实心方块作为图标。
 * 渲染风格参照 FluidStackKeyRender，使用 GL11 + Tessellator。
 */
public final class ManaStackKeyRender implements IStackRender {

    public static final ManaStackKeyRender INSTANCE = new ManaStackKeyRender();

    // Botania Mana 颜色：蓝绿色 #00C6FF
    private static final int MANA_COLOR = 0x00C6FF;
    private static final float MANA_R = ((MANA_COLOR >> 16) & 0xFF) / 255.0F;
    private static final float MANA_G = ((MANA_COLOR >> 8) & 0xFF) / 255.0F;
    private static final float MANA_B = (MANA_COLOR & 0xFF) / 255.0F;

    private ManaStackKeyRender() {}

    @Override
    @SideOnly(Side.CLIENT)
    public void render(IStackKey<?> key, int x, int y) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(MANA_R, MANA_G, MANA_B, 1.0F);

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertex(x, y + 16, 0);
        tessellator.addVertex(x + 16, y + 16, 0);
        tessellator.addVertex(x + 16, y, 0);
        tessellator.addVertex(x, y, 0);
        tessellator.draw();

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
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
        // 对齐 1.20.1 源项目 ManaStackKeyRender：StringFormat.formatCount（k/M/G/T/P/E 单位）
        if (count < 0) return "";
        return StringFormat.formatCount(count);
    }

    @Override
    public String getDisplayName(IStackKey<?> key) {
        return StatCollector.translateToLocal("types.beyonddimensions.mana_type.name");
    }

    @Override
    public List<String> getTooltipLines(IStackKey<?> key, long amount, EntityPlayer player, boolean advanced) {
        return Collections.singletonList(getDisplayName(key) + " (" + amount + ")");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderTooltip(IStackKey<?> key, long amount, int mouseX, int mouseY) {
        // 1.7.10 GuiScreen#drawHoveringText 为 protected，需要 GuiScreen 子类或反射调用。
        // 在 GUI 阶段实现具体的提示框绘制。
    }
}
