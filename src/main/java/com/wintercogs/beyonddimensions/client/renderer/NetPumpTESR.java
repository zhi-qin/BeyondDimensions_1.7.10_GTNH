package com.wintercogs.beyonddimensions.client.renderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 网络泵方块 TESR
 * <p>
 * 对齐 1.20.1 源项目 Blockbench JSON 模型（net_pump_block.json）：
 * - 绑定贴图 PNG 为 32x32，但模型 UV 处于 Blockbench/vanilla 的 16 坐标空间（见 {@link #UV_DENOM}）
 * - 13 个元素：1 个主体核心立方体 + 6 组吸纳器（每组 2 个元素）
 * - 使用原始 3D 模型贴图（blocks_original_3d/net_pump_block.png）
 */
@SideOnly(Side.CLIENT)
public class NetPumpTESR extends TileEntitySpecialRenderer {

    private static final ResourceLocation TEXTURE = new ResourceLocation(
        "beyonddimensions",
        "textures/blocks_original_3d/net_pump_block.png");
    // UV 坐标空间分母：Blockbench 导出 / vanilla 加载的模型 UV 处于 16 坐标空间，renderBox 以该值
    // 作分母把 JSON 的 UV 数值归一化为 0..1（与绑定贴图 PNG 的像素尺寸 32 无关）。
    // 此前误用 PNG 像素宽 32 作分母，使核心六面 [0,0,6,6] 仅采到左上 6px（青色环的一个角：顶+左两边青、
    // 另两边无青）→ 黑色内部偏离中心；正确分母 16 使 [0,0,6,6]→0.375→采 12px = 完整青色正方形环，黑块居中。
    private static final double UV_DENOM = 16.0;

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);

        Minecraft.getMinecraft().renderEngine.bindTexture(TEXTURE);

        int brightness = te.getBlockType()
            .getMixedBrightnessForBlock(te.getWorldObj(), te.xCoord, te.yCoord, te.zCoord);
        Tessellator tessellator = Tessellator.instance;

        // 禁用背面剔除，确保所有面都可见
        GL11.glDisable(GL11.GL_CULL_FACE);

        renderFullModel(tessellator, brightness);

        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glPopMatrix();
    }

    /**
     * 渲染泵的完整 3D 模型，自管理 Tessellator 批次（startDrawing/draw）。
     * <p>
     * 世界 TESR 与物品渲染器共用此方法，保证背包/手持/掉落物外观与方块一致。
     */
    public static void renderFullModel(Tessellator t, int brightness) {
        // 泵主体核心（element 0）六面 UV [0,0,6,6]，在 16 坐标空间下归一化为 0..0.375，对应 32x32
        // 贴图左上角 12x12 区域 = 完整青色正方形环（四边各 1px 青 + 角点深青）+ alpha=0 的居中内部。
        // 源项目 1.20.1 以 solid 渲染类型渲染（忽略 alpha 通道），故主体面呈「青色方框 + 居中黑」的实心方块；
        // 1.7.10 TESR 若沿用 alpha test，alpha=0 内部会被剔除 → 方框中间镂空、可看穿到背景/吸纳器。
        // 因此绘制期间禁用 alpha test 与 blend，令 alpha=0 像素按其 RGB（黑色）不透明输出，对齐源外观。
        boolean wasAlpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        int prevAlphaFunc = GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
        float prevAlphaRef = GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);
        boolean wasBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        try {
            t.startDrawingQuads();
            t.setBrightness(brightness);
            t.setColorOpaque_F(1f, 1f, 1f);
            renderModel(t);
            t.draw();
        } finally {
            if (wasAlpha) {
                GL11.glEnable(GL11.GL_ALPHA_TEST);
                GL11.glAlphaFunc(prevAlphaFunc, prevAlphaRef);
            } else {
                GL11.glDisable(GL11.GL_ALPHA_TEST);
            }
            if (wasBlend) {
                GL11.glEnable(GL11.GL_BLEND);
            }
        }
    }

    /**
     * 渲染泵的完整 3D 模型（13 个元素）。 可被物品渲染器复用。
     */
    public static void renderModel(Tessellator tessellator) {
        // 元素 0：主体核心立方体 from(2,2,2) to(14,14,14)，六面 UV [0,0,6,6]
        double[] coreUV = { 0, 0, 6, 6 };
        BlockModelRenderer.renderBox(
            tessellator,
            2,
            2,
            2,
            14,
            14,
            14,
            UV_DENOM,
            UV_DENOM,
            coreUV,
            coreUV,
            coreUV,
            coreUV,
            coreUV,
            coreUV);

        // 元素 1：Z- 吸纳器内层 from(3,3,1) to(13,13,2)
        BlockModelRenderer.renderBox(
            tessellator,
            3,
            3,
            1,
            13,
            13,
            2,
            UV_DENOM,
            UV_DENOM,
            new double[] { 12, 1.5, 7, 2 }, // down
            new double[] { 12, 1.5, 7, 1 }, // up
            null, // north (absent)
            null, // south (absent)
            new double[] { 6.5, 6, 7, 11 }, // west
            new double[] { 6.5, 1, 7, 6 }); // east

        // 元素 2：Z- 吸纳器外层 from(2,2,0) to(14,14,1)
        BlockModelRenderer.renderBox(
            tessellator,
            2,
            2,
            0,
            14,
            14,
            1,
            UV_DENOM,
            UV_DENOM,
            new double[] { 12.5, 0.5, 6.5, 1 }, // down
            new double[] { 12.5, 0.5, 6.5, 0 }, // up
            new double[] { 0, 6, 6, 12 }, // north
            new double[] { 0, 6, 6, 12 }, // south
            new double[] { 6, 6, 6.5, 12 }, // west
            new double[] { 6, 0, 6.5, 6 }); // east

        // 元素 3：Z+ 吸纳器内层 from(3,3,14) to(13,13,15)
        BlockModelRenderer.renderBox(
            tessellator,
            3,
            3,
            14,
            13,
            13,
            15,
            UV_DENOM,
            UV_DENOM,
            new double[] { 7, 2, 12, 1.5 }, // down
            new double[] { 7, 1.5, 12, 1 }, // up
            null, // north
            null, // south
            new double[] { 7, 6, 6.5, 11 }, // west
            new double[] { 7, 1, 6.5, 6 }); // east

        // 元素 4：Z+ 吸纳器外层 from(2,2,15) to(14,14,16)
        BlockModelRenderer.renderBox(
            tessellator,
            2,
            2,
            15,
            14,
            14,
            16,
            UV_DENOM,
            UV_DENOM,
            new double[] { 6.5, 1, 12.5, 0.5 }, // down
            new double[] { 6.5, 0.5, 12.5, 0 }, // up
            new double[] { 6, 6, 0, 12 }, // north
            new double[] { 6, 6, 0, 12 }, // south
            new double[] { 6.5, 6, 6, 12 }, // west
            new double[] { 6.5, 0, 6, 6 }); // east

        // 元素 5：X- 吸纳器内层 from(1,3,3) to(2,13,13)
        BlockModelRenderer.renderBox(
            tessellator,
            1,
            3,
            3,
            2,
            13,
            13,
            UV_DENOM,
            UV_DENOM,
            new double[] { 7, 2, 12, 1.5 }, // down
            new double[] { 7, 1.5, 12, 1 }, // up
            new double[] { 7, 6, 6.5, 11 }, // north
            new double[] { 7, 1, 6.5, 6 }, // south
            null, // west
            null); // east

        // 元素 6：X- 吸纳器外层 from(0,2,2) to(1,14,14)
        BlockModelRenderer.renderBox(
            tessellator,
            0,
            2,
            2,
            1,
            14,
            14,
            UV_DENOM,
            UV_DENOM,
            new double[] { 6.5, 1, 12.5, 0.5 }, // down
            new double[] { 6.5, 0.5, 12.5, 0 }, // up
            new double[] { 6.5, 6, 6, 12 }, // north
            new double[] { 6.5, 0, 6, 6 }, // south
            new double[] { 6, 6, 0, 12 }, // west
            new double[] { 6, 6, 0, 12 }); // east

        // 元素 7：X+ 吸纳器内层 from(14,3,3) to(15,13,13)
        BlockModelRenderer.renderBox(
            tessellator,
            14,
            3,
            3,
            15,
            13,
            13,
            UV_DENOM,
            UV_DENOM,
            new double[] { 7, 1.5, 12, 2 }, // down
            new double[] { 7, 1, 12, 1.5 }, // up
            new double[] { 6.5, 6, 7, 11 }, // north
            new double[] { 6.5, 1, 7, 6 }, // south
            null, // west
            null); // east

        // 元素 8：X+ 吸纳器外层 from(15,2,2) to(16,14,14)
        BlockModelRenderer.renderBox(
            tessellator,
            15,
            2,
            2,
            16,
            14,
            14,
            UV_DENOM,
            UV_DENOM,
            new double[] { 6.5, 0.5, 12.5, 1 }, // down
            new double[] { 6.5, 0, 12.5, 0.5 }, // up
            new double[] { 6, 6, 6.5, 12 }, // north
            new double[] { 6, 0, 6.5, 6 }, // south
            new double[] { 0, 6, 6, 12 }, // west
            new double[] { 0, 6, 6, 12 }); // east

        // 元素 9：Y+ 吸纳器内层 from(3,14,3) to(13,15,13)
        BlockModelRenderer.renderBox(
            tessellator,
            3,
            14,
            3,
            13,
            15,
            13,
            UV_DENOM,
            UV_DENOM,
            null, // down
            null, // up
            new double[] { 6.5, 6, 7, 11 }, // north
            new double[] { 6.5, 1, 7, 6 }, // south
            new double[] { 12, 1.5, 7, 1 }, // west
            new double[] { 12, 1.5, 7, 2 }); // east

        // 元素 10：Y+ 吸纳器外层 from(2,15,2) to(14,16,14)
        BlockModelRenderer.renderBox(
            tessellator,
            2,
            15,
            2,
            14,
            16,
            14,
            UV_DENOM,
            UV_DENOM,
            new double[] { 6, 6, 0, 12 }, // down
            new double[] { 6, 12, 0, 6 }, // up
            new double[] { 6, 6, 6.5, 12 }, // north
            new double[] { 6, 0, 6.5, 6 }, // south
            new double[] { 12.5, 0.5, 6.5, 0 }, // west
            new double[] { 12.5, 0.5, 6.5, 1 }); // east

        // 元素 11：Y- 吸纳器内层 from(3,1,3) to(13,2,13)
        BlockModelRenderer.renderBox(
            tessellator,
            3,
            1,
            3,
            13,
            2,
            13,
            UV_DENOM,
            UV_DENOM,
            null, // down
            null, // up
            new double[] { 7, 6, 6.5, 11 }, // north
            new double[] { 7, 1, 6.5, 6 }, // south
            new double[] { 12, 1, 7, 1.5 }, // west
            new double[] { 12, 2, 7, 1.5 }); // east

        // 元素 12：Y- 吸纳器外层 from(2,0,2) to(14,1,14)
        BlockModelRenderer.renderBox(
            tessellator,
            2,
            0,
            2,
            14,
            1,
            14,
            UV_DENOM,
            UV_DENOM,
            new double[] { 0, 6, 6, 12 }, // down
            new double[] { 0, 12, 6, 6 }, // up
            new double[] { 6.5, 6, 6, 12 }, // north
            new double[] { 6.5, 0, 6, 6 }, // south
            new double[] { 12.5, 0, 6.5, 0.5 }, // west
            new double[] { 12.5, 1, 6.5, 0.5 }); // east
    }

    public static ResourceLocation getTexture() {
        return TEXTURE;
    }
}
