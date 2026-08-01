package com.wintercogs.beyonddimensions.client.renderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 3D 方块物品渲染器
 * <p>
 * 在背包/手中/地上渲染 3D 模型，而非 2D 平面图标。
 * 使用与 TESR 相同的模型数据和贴图。
 */
@SideOnly(Side.CLIENT)
public class BDItemRenderer implements IItemRenderer {

    public enum ModelType {
        PUMP,
        HOPPER,
        TERMINAL
    }

    private final ModelType type;
    private final ResourceLocation texture;

    // 背包视图归一化（把模型包围盒映射到 0..1 立方体，使其与普通方块同尺寸居中）。懒计算并缓存。
    private boolean normComputed = false;
    private boolean normEnabled = false;
    private float normTx;
    private float normTy;
    private float normTz;
    private float normScale = 1f;

    // 背包视图朝向：直接取自源项目 1.20.1 各模型 display.gui 的 rotation，使背包外观与源一致
    // （尤其终端为薄面板，源 [30,45,0] 使其正面朝向镜头；若用 1.7.10 默认 INVENTORY_BLOCK 角度会侧成一条线）。
    private final float guiRotX;
    private final float guiRotY;

    public BDItemRenderer(ModelType type) {
        this.type = type;
        switch (type) {
            case PUMP:
                this.texture = NetPumpTESR.getTexture();
                this.guiRotX = 30f;
                this.guiRotY = -135f;
                break;
            case HOPPER:
                this.texture = NetHopperTESR.getTexture();
                this.guiRotX = 30f;
                this.guiRotY = -135f;
                break;
            case TERMINAL:
                this.texture = NetTerminalTESR.getTexture();
                this.guiRotX = 30f;
                this.guiRotY = 45f;
                break;
            default:
                this.texture = null;
                this.guiRotX = 30f;
                this.guiRotY = -135f;
        }
    }

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        return true;
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        // 背包视图自行构建取景变换（复刻源 display.gui 朝向），故不让 Forge 套用默认 3D 方块变换；
        // 其余（手持 EQUIPPED_BLOCK、掉落 BLOCK_3D/ENTITY_ROTATION 等）仍交由 Forge 处理。
        if (type == ItemRenderType.INVENTORY && helper == ItemRendererHelper.INVENTORY_BLOCK) {
            return false;
        }
        return true;
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
        GL11.glPushMatrix();

        // Forge 在调用前已绑定方块/物品图集，这里改绑本方块的 3D 模型贴图
        Minecraft.getMinecraft().renderEngine.bindTexture(texture);

        // 禁用背面剔除，确保所有面都可见
        GL11.glDisable(GL11.GL_CULL_FACE);

        // 面光照已烘焙为逐面顶点色（BlockModelRenderer），故绘制期间关闭固定管线光照，
        // 避免与 GUI/世界光照叠加导致发白或过暗；绘制后恢复原状态。
        boolean wasLit = GL11.glIsEnabled(GL11.GL_LIGHTING);
        if (wasLit) {
            GL11.glDisable(GL11.GL_LIGHTING);
        }

        // 模型贴图为「小图集 + 大面积透明」，必须启用 alpha test 丢弃透明像素，否则 GUI 残留的
        // 混合状态会让透明 texel 参与混合/写深度 → 模型发白（泵）或整体不可见（漏斗/终端）。
        // 对齐 vanilla 3D 方块物品路径：alpha test GREATER 0.1、关闭 blend。绘制后恢复。
        boolean wasAlpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        int prevAlphaFunc = GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
        float prevAlphaRef = GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);
        boolean wasBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        GL11.glDisable(GL11.GL_BLEND);

        Tessellator tessellator = Tessellator.instance;

        switch (type) {
            case INVENTORY:
                // 背包格子：自行构建取景变换，复刻源 1.20.1 display.gui 朝向（shouldUseRenderHelper 已对
                // INVENTORY_BLOCK 返回 false，故此处于 GUI 像素空间：原点=槽左上、1 单位=1 像素、+Y 向下）。
                // 顺序（对顶点由内向外）：归一化 → 居中到原点 → 源朝向旋转 → scale(10,-10,10)（Y 取负把
                // GUI 的 +Y 向下翻成模型 +Y 向上，使源旋转角可直接套用）→ 平移到槽中心 (8,8)。
                ensureNormalization();
                GL11.glTranslatef(8f, 8f, 0f);
                GL11.glScalef(10f, -10f, 10f);
                GL11.glRotatef(guiRotY, 0f, 1f, 0f);
                GL11.glRotatef(guiRotX, 1f, 0f, 0f);
                GL11.glTranslatef(-0.5f, -0.5f, -0.5f);
                if (normEnabled) {
                    GL11.glTranslatef(normTx, normTy, normTz);
                    GL11.glScalef(normScale, normScale, normScale);
                }
                renderModel(tessellator, 0x00F000F0, false);
                break;
            case EQUIPPED:
            case EQUIPPED_FIRST_PERSON:
                // 手持：shouldUseRenderHelper(EQUIPPED_BLOCK)=true 时 Forge 已 translate(-0.5,-0.5,-0.5)，
                // 按 (0,0,0)-(1,1,1) 原样绘制即可居中（保持真实方块尺寸，不归一化）。
                renderModel(tessellator, 0x00F000F0, false);
                break;
            case ENTITY:
                // 掉落物：Forge 已做旋转/缩放，且原点为 EntityItem 中心，故将 0..1 模型平移到中心。
                GL11.glTranslatef(-0.5f, -0.5f, -0.5f);
                renderModel(tessellator, 0x00F000F0, false);
                break;
            default:
                GL11.glTranslatef(-0.5f, -0.5f, -0.5f);
                renderModel(tessellator, 0x00F000F0, false);
                break;
        }

        GL11.glEnable(GL11.GL_CULL_FACE);
        if (wasLit) {
            GL11.glEnable(GL11.GL_LIGHTING);
        }
        // 恢复 alpha test / blend 状态，避免污染后续槽位/控件渲染。
        if (wasAlpha) {
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(prevAlphaFunc, prevAlphaRef);
        } else {
            GL11.glDisable(GL11.GL_ALPHA_TEST);
        }
        if (wasBlend) {
            GL11.glEnable(GL11.GL_BLEND);
        }
        GL11.glPopMatrix();
    }

    /**
     * 分发到对应 TESR 的 renderFullModel（自管理 startDrawing/draw 批次，漏斗含旋转斜腿）。
     * <p>
     * 本方法不再自行 startDrawing/draw，以免与漏斗旋转腿内部的批次嵌套冲突。
     */
    private void renderModel(Tessellator tessellator, int brightness, boolean normalize) {
        if (normalize) {
            ensureNormalization();
            if (normEnabled) {
                GL11.glTranslatef(normTx, normTy, normTz);
                GL11.glScalef(normScale, normScale, normScale);
            }
        }
        switch (type) {
            case PUMP:
                NetPumpTESR.renderFullModel(tessellator, brightness);
                break;
            case HOPPER:
                NetHopperTESR.renderFullModel(tessellator, brightness);
                break;
            case TERMINAL:
                NetTerminalTESR.renderFullModel(tessellator, brightness);
                break;
        }
    }

    /**
     * 懒计算背包视图归一化变换：测量模型包围盒，求等比缩放与居中平移，使包围盒映射到 0..1 立方体。
     * <p>
     * 测量通过 {@link BlockModelRenderer#measure} 在 capture 模式下复用 renderFullModel 完成，
     * 旋转斜腿等 GL 变换后的顶点亦被正确统计；不产生 GL 输出。结果缓存，仅首次调用计算。
     */
    private void ensureNormalization() {
        if (normComputed) {
            return;
        }
        normComputed = true;
        BlockModelRenderer.Bounds b = BlockModelRenderer.measure(() -> renderModel(Tessellator.instance, 0, false));
        double sx = b.sizeX();
        double sy = b.sizeY();
        double sz = b.sizeZ();
        if (!(sx > 0) || !(sy > 0) || !(sz > 0)) {
            normEnabled = false;
            return;
        }
        double max = Math.max(sx, Math.max(sy, sz));
        if (max <= 0) {
            normEnabled = false;
            return;
        }
        double s = 1.0 / max;
        double cx = b.centerX();
        double cy = b.centerY();
        double cz = b.centerZ();
        // 先 scale 再 translate：v' = s*v + t，令包围盒中心映射到 0.5 → t = 0.5 - s*center。
        normScale = (float) s;
        normTx = (float) (0.5 - s * cx);
        normTy = (float) (0.5 - s * cy);
        normTz = (float) (0.5 - s * cz);
        // 已是满立方体且居中时无需变换。
        normEnabled = !(Math.abs(s - 1.0) < 1e-4 && Math.abs(cx - 0.5) < 1e-4
            && Math.abs(cy - 0.5) < 1e-4
            && Math.abs(cz - 0.5) < 1e-4);
    }
}
