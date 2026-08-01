package com.wintercogs.beyonddimensions.util;

/**
 * UI 数据持有者，用于在 GUI 渲染时传递上下文信息。
 * <p>
 * 1.7.10 没有 Vec2，使用简单内部类替代。
 */
public class UIDataHelper {

    /**
     * 跨界面转移上下文（对齐源项目 UIDataHelper 静态字段，审计 M3-4）：
     * 终端/网络主界面 ↔ 合成/主网络切换器菜单切换时保留页码与鼠标位置，切回后由
     * {@code GuiDimensionsNet.initGui()} 恢复。
     */
    public static int currentPage;
    public static int lastMouseX;
    public static int lastMouseY;
    public static boolean isTransfer;

    private int mouseX;
    private int mouseY;
    private float partialTicks;
    private int screenWidth;
    private int screenHeight;

    public UIDataHelper() {}

    public UIDataHelper(int mouseX, int mouseY, float partialTicks, int screenWidth, int screenHeight) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.partialTicks = partialTicks;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    public int getMouseX() {
        return mouseX;
    }

    public void setMouseX(int mouseX) {
        this.mouseX = mouseX;
    }

    public int getMouseY() {
        return mouseY;
    }

    public void setMouseY(int mouseY) {
        this.mouseY = mouseY;
    }

    public float getPartialTicks() {
        return partialTicks;
    }

    public void setPartialTicks(float partialTicks) {
        this.partialTicks = partialTicks;
    }

    public int getScreenWidth() {
        return screenWidth;
    }

    public void setScreenWidth(int screenWidth) {
        this.screenWidth = screenWidth;
    }

    public int getScreenHeight() {
        return screenHeight;
    }

    public void setScreenHeight(int screenHeight) {
        this.screenHeight = screenHeight;
    }

    /**
     * 简单的二维向量（替代 1.20.1 的 Vec2）。
     */
    public static class Vec2i {

        public int x;
        public int y;

        public Vec2i() {}

        public Vec2i(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    /**
     * 简单的浮点二维向量。
     */
    public static class Vec2f {

        public float x;
        public float y;

        public Vec2f() {}

        public Vec2f(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }
}
