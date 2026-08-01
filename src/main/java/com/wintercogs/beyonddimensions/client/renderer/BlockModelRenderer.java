package com.wintercogs.beyonddimensions.client.renderer;

import net.minecraft.client.renderer.Tessellator;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 3D 方块模型渲染工具
 * <p>
 * 提供 renderBox 方法，根据 Blockbench JSON 模型元素的 from/to/UV 数据，
 * 使用 1.7.10 Tessellator 渲染带有自定义 UV 映射的立方体面。
 * <p>
 * 坐标系：参数为 1/16 方块单位（0-16），内部转换为 0-1 OpenGL 坐标。
 * UV 映射：本类只做一件事——把 uv[] 除以 texW/texH 得到 0..1 归一化坐标（V=0 在顶部，与 Blockbench 一致；
 * 1.7.10 上传贴图不翻转 Y，故 V 直接除以 texH，无需翻转）。因此 **uv[] 与 texW/texH 必须处于同一坐标空间**：
 * <ul>
 * <li>Blockbench 导出 / vanilla 的 JSON 模型：UV 数值处于 <b>16 坐标空间</b>（与贴图 PNG 像素尺寸无关），
 * 调用方应传 texW=texH=16（直接照抄 JSON 的 uv 数值即可）。误传 PNG 像素宽会使采样区域按比例缩小、
 * 纹理错位/偏移（曾导致网络泵黑块偏心、漏斗采样 1/4）。</li>
 * <li>OBJ / 手工像素坐标模型（如网络终端）：uv[] 为贴图像素坐标，调用方传 texW/texH=PNG 像素宽。</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public class BlockModelRenderer {

    private static final double SCALE = 1.0 / 16.0;

    // 逐面方向着色（模拟 1.20.1 烘焙模型的面光照：顶面最亮、侧面次之、底面最暗）。
    // 源贴图含高饱和像素，若不按面衰减，物品/方块会呈现"霓虹/发白"观感，与源项目烘焙外观不符。
    private static final int SHADE_DOWN = 128; // 0.5
    private static final int SHADE_UP = 255; // 1.0
    private static final int SHADE_NS = 204; // 0.8
    private static final int SHADE_EW = 153; // 0.6

    /** 测量模式开关：开启时 renderBox 仅累计包围盒，不向 Tessellator 写入顶点。 */
    private static boolean capturing = false;
    private static Bounds captureBounds = null;

    /** 轴对齐包围盒（OpenGL 0..1 坐标）。 */
    public static final class Bounds {

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        void add(double x, double y, double z) {
            if (x < minX) {
                minX = x;
            }
            if (y < minY) {
                minY = y;
            }
            if (z < minZ) {
                minZ = z;
            }
            if (x > maxX) {
                maxX = x;
            }
            if (y > maxY) {
                maxY = y;
            }
            if (z > maxZ) {
                maxZ = z;
            }
        }

        public double sizeX() {
            return maxX - minX;
        }

        public double sizeY() {
            return maxY - minY;
        }

        public double sizeZ() {
            return maxZ - minZ;
        }

        public double centerX() {
            return (minX + maxX) * 0.5;
        }

        public double centerY() {
            return (minY + maxY) * 0.5;
        }

        public double centerZ() {
            return (minZ + maxZ) * 0.5;
        }
    }

    /**
     * 以「测量模式」运行 model，统计其轴对齐包围盒。
     * <p>
     * model 须通过 {@link #renderBox} 提交几何（所有 TESR 模型均如此）；旋转斜腿等 GL 变换作用于
     * renderBox 入参坐标，故测得的是变换后的真实包围盒。测量期间不产生任何 GL 顶点输出。
     */
    public static Bounds measure(Runnable model) {
        capturing = true;
        captureBounds = new Bounds();
        try {
            model.run();
        } finally {
            capturing = false;
        }
        return captureBounds;
    }

    // 注意：V 轴**不需要翻转**。1.7.10 的 TextureUtil.uploadTextureImageSubImpl 通过
    // BufferedImage.getRGB 自顶行读取并以 GL_BGRA/UNSIGNED_INT_8_8_8_8_REV 上传，不做垂直翻转，
    // 故贴图像素行 0（图像顶部）落在 GL 纹理 v=0，与 Blockbench 的 V 约定（顶部=0）一致。
    // 此前误用 1 - v/H 翻转，导致各面采样到垂直镜像区域（这些图集底部多为透明）→ 漏斗/终端不可见、泵偏色。

    /**
     * 渲染一个立方体面集合。
     *
     * @param tessellator                   Tessellator 实例
     * @param minX,minY,minZ,maxX,maxY,maxZ 立方体范围（1/16 方块单位）
     * @param texW,texH                     UV 坐标空间分母（与 uv[] 同空间：JSON 模型传 16，像素坐标模型传 PNG 像素宽）
     * @param down,up,north,south,west,east 各面 UV {u1,v1,u2,v2}，null 表示跳过该面
     */
    public static void renderBox(Tessellator tessellator, double minX, double minY, double minZ, double maxX,
        double maxY, double maxZ, double texW, double texH, double[] down, double[] up, double[] north, double[] south,
        double[] west, double[] east) {

        double x0 = minX * SCALE, y0 = minY * SCALE, z0 = minZ * SCALE;
        double x1 = maxX * SCALE, y1 = maxY * SCALE, z1 = maxZ * SCALE;

        // 测量模式：仅累计 8 个角点到包围盒，不写入 Tessellator。
        if (capturing) {
            Bounds b = captureBounds;
            b.add(x0, y0, z0);
            b.add(x0, y0, z1);
            b.add(x0, y1, z0);
            b.add(x0, y1, z1);
            b.add(x1, y0, z0);
            b.add(x1, y0, z1);
            b.add(x1, y1, z0);
            b.add(x1, y1, z1);
            return;
        }

        // Down (y=minY, normal=-Y)
        if (down != null) {
            tessellator.setNormal(0.0F, -1.0F, 0.0F);
            tessellator.setColorOpaque(SHADE_DOWN, SHADE_DOWN, SHADE_DOWN);
            double u1 = down[0] / texW, v1 = down[1] / texH, u2 = down[2] / texW, v2 = down[3] / texH;
            tessellator.addVertexWithUV(x0, y0, z0, u1, v2);
            tessellator.addVertexWithUV(x0, y0, z1, u1, v1);
            tessellator.addVertexWithUV(x1, y0, z1, u2, v1);
            tessellator.addVertexWithUV(x1, y0, z0, u2, v2);
        }

        // Up (y=maxY, normal=+Y)
        if (up != null) {
            tessellator.setNormal(0.0F, 1.0F, 0.0F);
            tessellator.setColorOpaque(SHADE_UP, SHADE_UP, SHADE_UP);
            double u1 = up[0] / texW, v1 = up[1] / texH, u2 = up[2] / texW, v2 = up[3] / texH;
            tessellator.addVertexWithUV(x0, y1, z1, u1, v2);
            tessellator.addVertexWithUV(x0, y1, z0, u1, v1);
            tessellator.addVertexWithUV(x1, y1, z0, u2, v1);
            tessellator.addVertexWithUV(x1, y1, z1, u2, v2);
        }

        // North (z=minZ, normal=-Z)
        if (north != null) {
            tessellator.setNormal(0.0F, 0.0F, -1.0F);
            tessellator.setColorOpaque(SHADE_NS, SHADE_NS, SHADE_NS);
            double u1 = north[0] / texW, v1 = north[1] / texH, u2 = north[2] / texW, v2 = north[3] / texH;
            tessellator.addVertexWithUV(x1, y0, z0, u2, v2);
            tessellator.addVertexWithUV(x1, y1, z0, u2, v1);
            tessellator.addVertexWithUV(x0, y1, z0, u1, v1);
            tessellator.addVertexWithUV(x0, y0, z0, u1, v2);
        }

        // South (z=maxZ, normal=+Z)
        if (south != null) {
            tessellator.setNormal(0.0F, 0.0F, 1.0F);
            tessellator.setColorOpaque(SHADE_NS, SHADE_NS, SHADE_NS);
            double u1 = south[0] / texW, v1 = south[1] / texH, u2 = south[2] / texW, v2 = south[3] / texH;
            tessellator.addVertexWithUV(x0, y0, z1, u1, v2);
            tessellator.addVertexWithUV(x0, y1, z1, u1, v1);
            tessellator.addVertexWithUV(x1, y1, z1, u2, v1);
            tessellator.addVertexWithUV(x1, y0, z1, u2, v2);
        }

        // West (x=minX, normal=-X)
        if (west != null) {
            tessellator.setNormal(-1.0F, 0.0F, 0.0F);
            tessellator.setColorOpaque(SHADE_EW, SHADE_EW, SHADE_EW);
            double u1 = west[0] / texW, v1 = west[1] / texH, u2 = west[2] / texW, v2 = west[3] / texH;
            tessellator.addVertexWithUV(x0, y0, z0, u1, v2);
            tessellator.addVertexWithUV(x0, y1, z0, u1, v1);
            tessellator.addVertexWithUV(x0, y1, z1, u2, v1);
            tessellator.addVertexWithUV(x0, y0, z1, u2, v2);
        }

        // East (x=maxX, normal=+X)
        if (east != null) {
            tessellator.setNormal(1.0F, 0.0F, 0.0F);
            tessellator.setColorOpaque(SHADE_EW, SHADE_EW, SHADE_EW);
            double u1 = east[0] / texW, v1 = east[1] / texH, u2 = east[2] / texW, v2 = east[3] / texH;
            tessellator.addVertexWithUV(x1, y0, z1, u2, v2);
            tessellator.addVertexWithUV(x1, y1, z1, u2, v1);
            tessellator.addVertexWithUV(x1, y1, z0, u1, v1);
            tessellator.addVertexWithUV(x1, y0, z0, u1, v2);
        }
    }

    /**
     * 渲染一个简单的全贴图立方体（所有面使用相同 UV 0,0,texW,texH）。
     */
    public static void renderSimpleBox(Tessellator tessellator, double minX, double minY, double minZ, double maxX,
        double maxY, double maxZ, double texW, double texH, double u1, double v1, double u2, double v2) {
        double[] uv = { u1, v1, u2, v2 };
        renderBox(tessellator, minX, minY, minZ, maxX, maxY, maxZ, texW, texH, uv, uv, uv, uv, uv, uv);
    }
}
