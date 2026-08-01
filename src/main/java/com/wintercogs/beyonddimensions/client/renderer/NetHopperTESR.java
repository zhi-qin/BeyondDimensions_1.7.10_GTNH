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
 * 网络漏斗方块 TESR
 * <p>
 * 对齐 1.20.1 源项目 Blockbench JSON 模型（net_hopper_block.json）：
 * - 绑定贴图 PNG 为 64x64，但模型 UV 处于 Blockbench/vanilla 的 16 坐标空间（见 {@link #UV_DENOM}）
 * - 6 个元素：底座 + 内体 + 4 条带 22.5° 旋转的斜腿
 * - 使用原始 3D 模型贴图（blocks_original_3d/net_hopper_block.png）
 */
@SideOnly(Side.CLIENT)
public class NetHopperTESR extends TileEntitySpecialRenderer {

    private static final ResourceLocation TEXTURE = new ResourceLocation(
        "beyonddimensions",
        "textures/blocks_original_3d/net_hopper_block.png");
    // UV 坐标空间分母：Blockbench 导出 / vanilla 加载的模型 UV 处于 16 坐标空间，renderBox 以该值
    // 作分母把 JSON 的 UV 数值归一化为 0..1（与绑定贴图 PNG 的像素尺寸 64 无关）。
    // 此前误用 PNG 像素宽 64 作分母，使各面仅采到目标区域的左上 1/4（纹理整体被放大 4 倍、采样错位）；
    // 正确分母 16 与源项目一致，使底座/内体/斜腿各面采样到完整目标区域。
    private static final double UV_DENOM = 16.0;
    private static final double SCALE = 1.0 / 16.0;

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
     * 渲染漏斗完整 3D 模型（含旋转斜腿），自管理 Tessellator 批次与每条腿的 pushMatrix/popMatrix。
     * <p>
     * 世界 TESR 与物品渲染器共用此方法，保证背包/手持/掉落物外观与方块完全一致（斜腿带 22.5° 旋转）。
     */
    public static void renderFullModel(Tessellator t, int brightness) {
        // 不旋转部分（底座 + 内体）
        t.startDrawingQuads();
        t.setBrightness(brightness);
        t.setColorOpaque_F(1f, 1f, 1f);
        renderStaticParts(t);
        t.draw();

        // 旋转部分（4 条斜腿），每条腿单独 pushMatrix/popMatrix
        renderRotatedLegs(t, brightness);
    }

    /** 渲染底座（元素 0）和内体（元素 1） */
    private static void renderStaticParts(Tessellator tessellator) {
        // 元素 0：底座 from(2.5,0,2.5) to(13.5,4,13.5)
        BlockModelRenderer.renderBox(
            tessellator,
            2.5,
            0,
            2.5,
            13.5,
            4,
            13.5,
            UV_DENOM,
            UV_DENOM,
            new double[] { 2.75, 2.75, 0, 5.5 }, // down
            new double[] { 2.75, 2.75, 0, 0 }, // up
            new double[] { 2.75, 2, 5.5, 3 }, // north
            new double[] { 2.75, 4, 5.5, 5 }, // south
            new double[] { 4.75, 0, 7.5, 1 }, // west
            new double[] { 2.75, 3, 5.5, 4 }); // east

        // 元素 1：内体 from(4,6,4) to(12,8,12)
        BlockModelRenderer.renderBox(
            tessellator,
            4,
            6,
            4,
            12,
            8,
            12,
            UV_DENOM,
            UV_DENOM,
            null, // down
            new double[] { 4.75, 2, 2.75, 0 }, // up
            new double[] { 4, 6, 6, 6.5 }, // north
            new double[] { 0, 6.5, 2, 7 }, // south
            new double[] { 4, 6.5, 6, 7 }, // west
            new double[] { 6, 6, 8, 6.5 }); // east
    }

    /** 渲染 4 条旋转斜腿 */
    private static void renderRotatedLegs(Tessellator tessellator, int brightness) {
        // 腿 1: axis=z, angle=22.5, origin=(10.3,6,10)
        renderRotatedLeg(tessellator, brightness, 10.3, 6, 10, 0, 0, 1, 22.5f, () -> {
            BlockModelRenderer.renderBox(
                tessellator,
                10.3,
                2.73631,
                4,
                12.63596,
                7.1972,
                12,
                UV_DENOM,
                UV_DENOM,
                new double[] { 7, 6.5, 6.5, 8.5 }, // down
                new double[] { 6.5, 8.5, 6, 6.5 }, // up
                new double[] { 1, 7, 1.5, 8 }, // north
                new double[] { 1.5, 7, 2, 8 }, // south
                new double[] { 2.75, 5, 4.75, 6 }, // west
                new double[] { 4.75, 1, 6.75, 2 }); // east
        });

        // 腿 2: axis=x, angle=-22.5, origin=(6,6,10.3)
        renderRotatedLeg(tessellator, brightness, 6, 6, 10.3, 1, 0, 0, -22.5f, () -> {
            BlockModelRenderer.renderBox(
                tessellator,
                4,
                2.73631,
                10.3,
                12,
                7.1972,
                12.63596,
                UV_DENOM,
                UV_DENOM,
                new double[] { 8.75, 1.5, 6.75, 2 }, // down
                new double[] { 8.75, 1.5, 6.75, 1 }, // up
                new double[] { 5.5, 2, 7.5, 3 }, // north
                new double[] { 5.5, 3, 7.5, 4 }, // south
                new double[] { 3.5, 7, 4, 8 }, // west
                new double[] { 3, 7, 3.5, 8 }); // east
        });

        // 腿 3: axis=x, angle=22.5, origin=(6,6,5.7)
        renderRotatedLeg(tessellator, brightness, 6, 6, 5.7, 1, 0, 0, 22.5f, () -> {
            BlockModelRenderer.renderBox(
                tessellator,
                4,
                2.73631,
                3.36404,
                12,
                7.1972,
                5.7,
                UV_DENOM,
                UV_DENOM,
                new double[] { 8.75, 5.5, 6.75, 6 }, // down
                new double[] { 8.75, 5.5, 6.75, 5 }, // up
                new double[] { 5.5, 4, 7.5, 5 }, // north
                new double[] { 2, 6, 4, 7 }, // south
                new double[] { 4.5, 7, 5, 8 }, // west
                new double[] { 4, 7, 4.5, 8 }); // east
        });

        // 腿 4: axis=z, angle=-22.5, origin=(5.7,6,10)
        renderRotatedLeg(tessellator, brightness, 5.7, 6, 10, 0, 0, 1, -22.5f, () -> {
            BlockModelRenderer.renderBox(
                tessellator,
                3.36404,
                2.73631,
                4,
                5.7,
                7.1972,
                12,
                UV_DENOM,
                UV_DENOM,
                new double[] { 1, 7, 0.5, 9 }, // down
                new double[] { 0.5, 9, 0, 7 }, // up
                new double[] { 2, 7, 2.5, 8 }, // north
                new double[] { 2.5, 7, 3, 8 }, // south
                new double[] { 0, 5.5, 2, 6.5 }, // west
                new double[] { 4.75, 5, 6.75, 6 }); // east
        });
    }

    /** 渲染一条旋转腿 */
    private static void renderRotatedLeg(Tessellator tessellator, int brightness, double originX, double originY,
        double originZ, float axisX, float axisY, float axisZ, float angle, Runnable renderAction) {
        GL11.glPushMatrix();
        // 平移到旋转原点
        GL11.glTranslated(originX * SCALE, originY * SCALE, originZ * SCALE);
        // 应用旋转
        GL11.glRotatef(angle, axisX, axisY, axisZ);
        // 平移回去
        GL11.glTranslated(-originX * SCALE, -originY * SCALE, -originZ * SCALE);

        tessellator.startDrawingQuads();
        tessellator.setBrightness(brightness);
        tessellator.setColorOpaque_F(1f, 1f, 1f);
        renderAction.run();
        tessellator.draw();

        GL11.glPopMatrix();
    }

    public static ResourceLocation getTexture() {
        return TEXTURE;
    }

    /**
     * @deprecated 该旧版本为「不旋转斜腿」的简化几何，物品渲染已改用 {@link #renderFullModel} 以对齐方块/新版本外观。 保留仅为兼容，勿再调用。
     */
    @Deprecated
    public static void renderModel(Tessellator tessellator) {
        renderStaticParts(tessellator);
    }
}
