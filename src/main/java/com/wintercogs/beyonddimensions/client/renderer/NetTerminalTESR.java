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
 * 网络终端方块 TESR
 * <p>
 * 对齐 1.20.1 源项目 OBJ 模型（net_terminal_block.obj）：
 * - 贴图尺寸 64x64
 * - 1 个薄板立方体：from(0,0,0) to(16,16,3)（OBJ 坐标 0-1 映射为 0-16 像素）
 * - UV 映射 (0,0)-(0.25,0.25) 即 64x64 贴图的左上角 16x16 区域
 * - 使用原始 3D 模型贴图（blocks_original_3d/net_terminal_block_texture.png）
 * - 根据 metadata 控制朝向
 */
@SideOnly(Side.CLIENT)
public class NetTerminalTESR extends TileEntitySpecialRenderer {

    private static final ResourceLocation TEXTURE = new ResourceLocation(
        "beyonddimensions",
        "textures/blocks_original_3d/net_terminal_block_texture.png");
    private static final double TEX_W = 64.0;
    private static final double TEX_H = 64.0;

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
        int meta = te.getBlockMetadata();

        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);

        // 根据朝向旋转（对齐 1.20.1 blockstate 中的 facing 变体）
        // 0=north(默认), 1=east, 2=south, 3=west
        // 源项目终端为薄面板，朝向决定面板面向的方向
        applyFacingRotation(meta);

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
     * 渲染终端完整 3D 模型，自管理 Tessellator 批次（startDrawing/draw）。
     * <p>
     * 世界 TESR 与物品渲染器共用此方法，保证背包/手持/掉落物外观与方块一致。
     */
    public static void renderFullModel(Tessellator t, int brightness) {
        t.startDrawingQuads();
        t.setBrightness(brightness);
        t.setColorOpaque_F(1f, 1f, 1f);
        renderModel(t);
        t.draw();
    }

    /** 根据朝向旋转模型 */
    private static void applyFacingRotation(int meta) {
        // 绕方块中心旋转
        GL11.glTranslated(0.5, 0.5, 0.5);
        switch (meta) {
            case 1: // east
                GL11.glRotatef(-90, 0, 1, 0);
                break;
            case 2: // south
                GL11.glRotatef(180, 0, 1, 0);
                break;
            case 3: // west
                GL11.glRotatef(90, 0, 1, 0);
                break;
            default: // 0 = north, 无旋转
                break;
        }
        GL11.glTranslated(-0.5, -0.5, -0.5);
    }

    /**
     * 渲染终端薄板模型。 可被物品渲染器复用。
     * <p>
     * 薄板尺寸：16x16x3 像素（满 X-Y 面，Z 方向 3 像素厚）
     * UV 映射：所有面使用 (0,0)-(16,16) 区域（64x64 贴图左上角 16x16）
     */
    public static void renderModel(Tessellator tessellator) {
        double[] uv = { 0, 0, 16, 16 };
        BlockModelRenderer.renderBox(tessellator, 0, 0, 0, 16, 16, 3, TEX_W, TEX_H, uv, uv, uv, uv, uv, uv);
    }

    public static ResourceLocation getTexture() {
        return TEXTURE;
    }
}
