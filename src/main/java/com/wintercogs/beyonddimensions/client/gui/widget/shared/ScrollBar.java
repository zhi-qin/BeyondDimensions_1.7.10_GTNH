package com.wintercogs.beyonddimensions.client.gui.widget.shared;

import java.util.function.IntConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.wintercogs.beyonddimensions.util.GuiRenderHelper;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 滚动条控件（1.7.10 移植版）。
 * <p>
 * 移植自 1.20.1 的 {@code ScrollBar}（继承 {@code AbstractWidget}）。
 * 1.7.10 没有 {@code AbstractWidget}，这里改为独立的客户端类，由宿主 GUI 在
 * {@code drawScreen}/{@code mouseClicked}/{@code mouseClickMove}/{@code mouseMovedOrUp}/
 * {@code handleMouseInput} 中显式转发事件。
 * <p>
 * 控件 x/y 表示轨道起点（滑块顶部初始位置）；maxScrollLength 表示滑块顶部可移动的像素长度。
 */
@SideOnly(Side.CLIENT)
public class ScrollBar implements GuiElementAccess {

    /** 滑块贴图 */
    protected final ResourceLocation SPRITE;

    /** 轨道可滑动像素长度（滑块“顶部”从 0 到末端的位移量） */
    protected int maxScrollLength;

    /** 当前位置（0..maxPosition） */
    protected int currentPosition;

    /** 最大位置（总数据“起始行”或总索引；包含可见 + 不可见） */
    protected int maxPosition;

    /** 步长（滚轮/量化的最小单位） */
    protected int step = 1;

    /** 当前滑块像素偏移（相对组件 y；只用于渲染/命中，不改变组件 y） */
    protected int scrollerOffset = 0;

    /** 是否按“滑块中心对齐鼠标”（true 更自然；false 为顶部对齐） */
    protected boolean alignCenterToMouse = true;

    /** 拖拽状态 */
    protected boolean isDragging = false;

    /** 位置变化回调（在 {@link #setCurrentPosition} 时触发） */
    protected IntConsumer onScroll;

    /** 组件左上角 X（固定不动） */
    protected final int x;
    /** 组件左上角 Y（固定不动：轨道起点 Y） */
    protected final int y;
    /** 滑块宽度 */
    protected final int width;
    /** 滑块高度 */
    protected final int height;

    /** 是否启用 */
    protected boolean enabled = true;
    /** 是否可见 */
    protected boolean visible = true;

    /**
     * 滚轮生效的附加区域（绝对坐标 {x, y, w, h}；null=无）。
     * 鼠标落在此区域或轨道区域上时，滚轮才滚动本滚动条；其余位置滚轮放行给上层
     * （如 NEI 收藏栏 Shift+滚轮切换配方）。1.20.1 由框架只把滚轮派发给悬停组件把关，
     * 1.7.10 手动转发需在此补齐（BUGFIX_RECORD #100）。
     */
    protected int[] wheelRegion = null;

    /**
     * @param x            组件左上角 X（固定不动）
     * @param y            组件左上角 Y（固定不动：轨道起点 Y）
     * @param width        滑块宽度（不是轨道宽）
     * @param height       滑块高度（不是轨道高）
     * @param sprite       滑块贴图
     * @param maxScrollLen 轨道可滑动像素长度（滑块顶部 0..此值）
     * @param currentPos   初始当前位置（0..maxPosition）
     * @param maxPos       最大位置
     * @param onScroll     位置变化回调，可为 null
     */
    public ScrollBar(int x, int y, int width, int height, ResourceLocation sprite, int maxScrollLen, int currentPos,
        int maxPos, IntConsumer onScroll) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.SPRITE = sprite;
        this.maxScrollLength = Math.max(0, maxScrollLen);
        this.maxPosition = Math.max(0, maxPos);
        this.onScroll = onScroll;
        setCurrentPosition(currentPos);
    }

    /* ---------------------------- 外部 API ---------------------------- */

    public void setOnScroll(IntConsumer cb) {
        this.onScroll = cb;
    }

    public void setAlignCenterToMouse(boolean center) {
        this.alignCenterToMouse = center;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    /** 设置滚轮生效的附加区域（绝对坐标）；传 null 表示仅轨道区域响应滚轮 */
    public void setWheelRegion(int x, int y, int w, int h) {
        this.wheelRegion = new int[] { x, y, w, h };
    }

    public boolean isVisible() {
        return visible;
    }

    /** 动态更新“当前位置/最大位置”（会触发量化 + 回调） */
    public void updateScrollPosition(int currentPosition, int maxPosition) {
        this.maxPosition = Math.max(0, maxPosition);
        setCurrentPosition(currentPosition);
    }

    /** 动态更新轨道长度（像素） */
    public void setMaxScrollLength(int maxScrollLength) {
        this.maxScrollLength = Math.max(0, maxScrollLength);
    }

    /** 设置步长（&gt;=1） */
    public void setStep(int step) {
        this.step = Math.max(1, step);
        setCurrentPosition(this.currentPosition);
    }

    public int getStep() {
        return this.step;
    }

    public int getCurrentPosition() {
        return currentPosition;
    }

    public int getMaxPosition() {
        return maxPosition;
    }

    /** 相对滚动“步数”（&gt;0 向下，&lt;0 向上） */
    public void scrollBySteps(int steps) {
        if (maxPosition <= 0) return;
        int unit = Math.max(1, this.step);
        long target = (long) this.currentPosition + (long) steps * unit;
        setCurrentPosition((int) Math.max(0, Math.min(target, this.maxPosition)));
    }

    /** 把当前位置锚到“鼠标在轨道上的比例” */
    public void scrollToMouse(int mouseY) {
        if (maxPosition <= 0 || maxScrollLength <= 0) return;

        double anchorOffset = alignCenterToMouse ? (this.height / 2.0) : 0.0;
        double relative = (mouseY - this.y - anchorOffset) / (double) this.maxScrollLength;
        double clamped = Math.max(0.0, Math.min(1.0, relative));
        int pos = (int) Math.round(clamped * this.maxPosition);
        setCurrentPosition(pos);
    }

    /** 设置当前位置（统一出口：clamp + 步长量化 + 回调） */
    public void setCurrentPosition(int pos) {
        int clamped = Math.max(0, Math.min(pos, Math.max(0, this.maxPosition)));
        int quantized = quantizeToStep(clamped);
        if (quantized != this.currentPosition) {
            this.currentPosition = quantized;
            if (this.onScroll != null) this.onScroll.accept(this.currentPosition);
        } else {
            this.currentPosition = quantized;
        }
    }

    /* ---------------------------- 内部工具 ---------------------------- */

    /** 四舍五入到最近步长 */
    protected int quantizeToStep(int value) {
        if (step <= 1) return value;
        int q = Math.round(value / (float) step) * step;
        return Math.max(0, Math.min(q, Math.max(0, this.maxPosition)));
    }

    /** 根据 current/max → 计算像素偏移（滑块“顶部”） */
    protected int computeOffset() {
        if (maxPosition > 0 && maxScrollLength > 0) {
            return (int) Math.round(maxScrollLength * (this.currentPosition / (double) this.maxPosition));
        }
        return 0;
    }

    /* ---------------------------- 事件处理 ---------------------------- */

    /**
     * 整个轨道（y .. y + maxScrollLength + knobHeight）都算 hover，可点击/拖拽
     */
    public boolean isMouseOver(int mouseX, int mouseY) {
        int left = this.x;
        int right = left + this.width;
        int top = this.y;
        int bottom = top + this.maxScrollLength + this.height;
        return mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
    }

    /** 是否落在滚轮生效的附加区域（wheelRegion）内 */
    protected boolean inWheelRegion(int mouseX, int mouseY) {
        if (wheelRegion == null) return false;
        return mouseX >= wheelRegion[0] && mouseX < wheelRegion[0] + wheelRegion[2]
            && mouseY >= wheelRegion[1]
            && mouseY < wheelRegion[1] + wheelRegion[3];
    }

    /**
     * 处理鼠标按下事件。宿主 GUI 应在 {@code mouseClicked} 中调用。
     */
    public boolean mousePressed(int mouseX, int mouseY, int button) {
        if (!this.enabled || !this.visible) return false;
        if (button != 0) return false;

        if (this.isMouseOver(mouseX, mouseY)) {
            this.isDragging = true;
            scrollToMouse(mouseY);
            return true;
        }
        return false;
    }

    /**
     * 处理鼠标拖拽事件。宿主 GUI 应在 {@code mouseClickMove} 中调用。
     */
    public boolean mouseDragged(int mouseX, int mouseY, int button) {
        if (!isDragging) return false;
        if (button != 0) return false;
        scrollToMouse(mouseY);
        return true;
    }

    /**
     * 处理鼠标释放事件。宿主 GUI 应在 {@code mouseMovedOrUp} 中调用。
     */
    public boolean mouseReleased(int mouseX, int mouseY, int button) {
        if (button != 0) return false;
        boolean wasDragging = isDragging;
        isDragging = false;
        return wasDragging;
    }

    /**
     * 悬停轨道区域或滚轮生效区域（wheelRegion）时滚轮生效。
     * 宿主 GUI 应在 {@code handleMouseInput} 中调用。
     * <p>
     * 注意：必须校验鼠标位置，否则会吃掉 GUI 窗口内任意位置的滚轮事件，
     * 导致 NEI（经 GuiContainer.handleMouseInput 注入）收不到事件，
     * 收藏栏 Shift+滚轮切配方失效（BUGFIX_RECORD #100）。
     *
     * @param wheel 滚轮方向（正数向上，负数向下，与 1.7.10 {@code Mouse.getEventDWheel()} 一致）
     */
    public boolean mouseScrolled(int mouseX, int mouseY, int wheel) {
        if (!this.enabled || !this.visible) return false;
        if (maxPosition <= 0) return false;
        if (!isMouseOver(mouseX, mouseY) && !inWheelRegion(mouseX, mouseY)) return false;

        int dir = Integer.signum(wheel); // +1 上滚，-1 下滚
        if (dir != 0) {
            // 上滚 → 位置减小；下滚 → 位置增大
            scrollBySteps(-dir);
            return true;
        }
        return false;
    }

    /* ---------------------------- 渲染 ---------------------------- */

    /**
     * 渲染滚动条。宿主 GUI 应在 {@code drawScreen} 中（绘制完背景之后）调用。
     */
    public void drawScrollBar(Minecraft mc, int mouseX, int mouseY) {
        if (!this.visible) return;
        this.scrollerOffset = computeOffset();

        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GuiRenderHelper.blit(
            SPRITE,
            this.x,
            this.y + this.scrollerOffset,
            this.width,
            this.height,
            0,
            0,
            this.width,
            this.height,
            this.width,
            this.height);

        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GL11.glDisable(GL11.GL_BLEND);
    }

    @Override
    public int[] getElementArea() {
        return new int[] { this.x, this.y, this.width, this.maxScrollLength + this.height };
    }
}
