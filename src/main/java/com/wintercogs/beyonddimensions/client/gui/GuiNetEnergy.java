package com.wintercogs.beyonddimensions.client.gui;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import com.wintercogs.beyonddimensions.api.ButtonState;
import com.wintercogs.beyonddimensions.api.ids.BDConstants;
import com.wintercogs.beyonddimensions.api.storage.eu.NetEuStorage;
import com.wintercogs.beyonddimensions.client.gui.widget.shared.RightTabButton;
import com.wintercogs.beyonddimensions.common.block.entity.NetEnergyPathwayBlockEntity;
import com.wintercogs.beyonddimensions.common.machine.PopMode;
import com.wintercogs.beyonddimensions.common.machine.RedStoneControlMode;
import com.wintercogs.beyonddimensions.common.menu.NetEnergyMenu;
import com.wintercogs.beyonddimensions.util.GuiRenderHelper;
import com.wintercogs.beyonddimensions.util.StringFormat;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 维度网络能量通道 GUI（1.7.10 移植版）。
 * <p>
 * 对应源项目 1.20.1 的 {@code NetEnergyGUI}。
 * 显示能量存储量、传输速度及能量条。状态按钮：弹出模式、红石控制模式。
 * <p>
 * 能量条使用 {@link Gui#drawRect(int, int, int, int, int)} 绘制，
 * 避免自定义 {@link GuiRenderHelper#fillRect(int, int, int, int, int)} 的
 * 坐标系偏移问题。Y 位置使用 {@code ENERGY_BAR_Y_OFFSET = 35}
 * （对齐源项目 {@code topPos + 35}），移除此前 Y=19 的魔法常数补偿。
 * 渲染前通过 {@link GuiRenderHelper#resetColor()} 重置 OpenGL 颜色状态，
 * 避免前序操作残留的 Alpha 影响纹理和矩形渲染。
 * <p>
 * <b>纹理尺寸修正</b>：背景纹理 {@code net_energy_storage.png} 实际尺寸为
 * 256×256（1.7.10 的 2 的幂填充），代码中通过 {@code TEXTURE_FILE_WIDTH/HEIGHT = 256}
 * 作为 {@link GuiRenderHelper#blit} 的 UV 归一化分母，确保仅采样内容区域
 * （左上角 176×175 像素），避免内容被压缩至约 69% 线性尺寸。
 */
@SideOnly(Side.CLIENT)
public class GuiNetEnergy extends GuiBase {

    private static final int ID_POP_MODE = 100;
    private static final int ID_CONTROL_MODE = 101;
    private static final int ID_ACTIVE_PULL = 102;

    private static final ResourceLocation GUI_TEXTURE = new ResourceLocation(
        BDConstants.MODID,
        "textures/gui/net_energy_storage.png");
    /** 能量条在 GUI 背景纹理中的位置（距顶边像素），对齐源项目 topPos + 35 */
    private static final int ENERGY_BAR_Y_OFFSET = 35;
    private static final int ENERGY_BAR_X_OFFSET = 8;
    private static final int ENERGY_BAR_WIDTH = 160;
    private static final int ENERGY_BAR_HEIGHT = 16;

    /** EU 存量文字 Y 偏移（RF 速度文字 y=56 与物品栏标题 y=82 之间的空档） */
    private static final int EU_TEXT_Y_OFFSET = 68;

    /** EU 池能量条（移植新增，对齐终端 EU 条配色）：右对齐于 x=168，画在 EU 文字下方 y=77 */
    private static final int EU_BAR_RIGHT_X = 168;
    private static final int EU_BAR_WIDTH = 100;
    private static final int EU_BAR_HEIGHT = 4;
    private static final int EU_BAR_Y_OFFSET = 77;
    /** 底槽颜色（深灰，半透明，与终端 EU 条一致） */
    private static final int EU_BAR_BG_COLOR = 0xAA555555;
    /** 填充颜色（琥珀金，与终端 EU 条一致） */
    private static final int EU_BAR_FILL_COLOR = 0xFFE0B400;

    /**
     * 纹理实际尺寸。源 PNG 文件为 256x256（Minecraft 1.7.10 使用 2 的幂纹理），
     * 内容区域仅占左上角 176x175 像素，其余为透明填充。
     * <p>
     * {@link GuiRenderHelper#blit} 使用这些值做 UV 归一化分母，
     * 必须等于 PNG 真实尺寸（256），而非内容区域尺寸（176x175），
     * 否则 UV 映射到 1.0 时会采样到填充区域，导致内容被压缩至约 69% 线性尺寸
     * （176/256 ≈ 0.6875），配合 GL 颜色状态异常（透明背景）会呈现
     * "缩放到 25%、物品悬空透明" 的视觉效果。
     */
    private static final int TEXTURE_FILE_WIDTH = 256;
    private static final int TEXTURE_FILE_HEIGHT = 256;

    protected final NetEnergyMenu menu;

    protected RightTabButton popModeButton;
    protected RightTabButton controlModeButton;
    protected RightTabButton activePullButton;

    public GuiNetEnergy(InventoryPlayer inventory) {
        this(inventory, null);
    }

    public GuiNetEnergy(InventoryPlayer inventory, NetEnergyPathwayBlockEntity te) {
        super(new NetEnergyMenu(inventory, te));
        this.menu = (NetEnergyMenu) this.inventorySlots;
        this.xSize = 176;
        this.ySize = 175;
    }

    // ==================== 初始化 ====================

    @Override
    public void initGui() {
        super.initGui();
        initWidgets();
    }

    /** 初始化状态按钮，点击后通过 QuickDataTagPacket 同步到服务端 */
    protected void initWidgets() {
        int baseX = this.guiLeft + 176;
        int topY = this.guiTop + 6;
        int step = 30;

        popModeButton = new RightTabButton(ID_POP_MODE, baseX, topY, 23, 26, baseX + 3, topY + 4, 16, 16, button -> {
            popModeButton.toggleState();
            if (menu.be != null) {
                menu.be.setPopMode((PopMode) popModeButton.currentState);
                menu.writeAndSendQuickData();
            }
        }) {

            @Override
            protected void initButton() {
                iconMap.put(
                    PopMode.OPEN,
                    new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/popmode_up.png"));
                iconMap.put(
                    PopMode.STOP,
                    new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/popmode_down.png"));
                tooltipMap.put(PopMode.OPEN, "tooltip.button.beyonddimensions.popmode_on");
                tooltipMap.put(PopMode.STOP, "tooltip.button.beyonddimensions.popmode_off");
                for (Enum<?> state : iconMap.keySet()) {
                    this.states.add(state);
                }
                setState(menu.be != null ? menu.be.getPopMode() : PopMode.STOP);
            }
        };
        this.buttonList.add(popModeButton);

        controlModeButton = new RightTabButton(
            ID_CONTROL_MODE,
            baseX,
            topY + step,
            23,
            26,
            baseX + 3,
            topY + step + 4,
            16,
            16,
            button -> {
                controlModeButton.toggleState();
                if (menu.be != null) {
                    menu.be.controlMode = (RedStoneControlMode) controlModeButton.currentState;
                    menu.writeAndSendQuickData();
                }
            }) {

            @Override
            protected void initButton() {
                iconMap.put(
                    RedStoneControlMode.IGNORE,
                    new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/control_mode_ignore.png"));
                iconMap.put(
                    RedStoneControlMode.NOT_WORKING,
                    new ResourceLocation(
                        BDConstants.MODID,
                        "textures/gui/sprites/widget/control_mode_not_working.png"));
                iconMap.put(
                    RedStoneControlMode.POWERED,
                    new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/control_mode_powered.png"));
                iconMap.put(
                    RedStoneControlMode.UNPOWERED,
                    new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/control_mode_unpowered.png"));
                tooltipMap.put(RedStoneControlMode.IGNORE, "tooltip.button.beyonddimensions.control_mode_ignore");
                tooltipMap
                    .put(RedStoneControlMode.NOT_WORKING, "tooltip.button.beyonddimensions.control_mode_not_working");
                tooltipMap.put(RedStoneControlMode.POWERED, "tooltip.button.beyonddimensions.control_mode_powered");
                tooltipMap.put(RedStoneControlMode.UNPOWERED, "tooltip.button.beyonddimensions.control_mode_unpowered");
                for (Enum<?> state : iconMap.keySet()) {
                    this.states.add(state);
                }
                setState(menu.be != null ? menu.be.controlMode : RedStoneControlMode.IGNORE);
            }
        };
        this.buttonList.add(controlModeButton);

        activePullButton = new RightTabButton(
            ID_ACTIVE_PULL,
            baseX,
            topY + step * 2,
            23,
            26,
            baseX + 3,
            topY + step * 2 + 4,
            16,
            16,
            button -> {
                activePullButton.toggleState();
                if (menu.be != null) {
                    menu.be.setActivePull(activePullButton.currentState == ButtonState.ENABLED);
                    menu.writeAndSendQuickData();
                }
            }) {

            @Override
            protected void initButton() {
                iconMap.put(
                    ButtonState.ENABLED,
                    new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/net_absorb.png"));
                iconMap.put(
                    ButtonState.DISABLED,
                    new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/net_disable.png"));
                tooltipMap.put(ButtonState.ENABLED, "tooltip.button.beyonddimensions.active_pull_on");
                tooltipMap.put(ButtonState.DISABLED, "tooltip.button.beyonddimensions.active_pull_off");
                for (Enum<?> state : iconMap.keySet()) {
                    this.states.add(state);
                }
                setState(menu.be != null && menu.be.getActivePull() ? ButtonState.ENABLED : ButtonState.DISABLED);
            }
        };
        this.buttonList.add(activePullButton);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        syncButtonStates();
    }

    /** 从 menu.be 同步按钮状态（客户端菜单持有效 be 引用） */
    protected void syncButtonStates() {
        if (menu.be == null) return;
        if (popModeButton.currentState != menu.be.getPopMode()) popModeButton.setState(menu.be.getPopMode());
        if (controlModeButton.currentState != menu.be.controlMode) controlModeButton.setState(menu.be.controlMode);
        ButtonState activePullTarget = menu.be.getActivePull() ? ButtonState.ENABLED : ButtonState.DISABLED;
        if (activePullButton.currentState != activePullTarget) activePullButton.setState(activePullTarget);
    }

    // ==================== 渲染 ====================

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        // 重置 OpenGL 颜色为白色，避免前序渲染操作（如 drawDefaultBackground）残留的
        // 非白色/非不透明颜色状态导致纹理或矩形绘制异常（如透明背景）。
        GuiRenderHelper.resetColor();

        // 绘制背景纹理（能量条灰色槽位于纹理内）
        GuiRenderHelper.blit(
            GUI_TEXTURE,
            this.guiLeft,
            this.guiTop,
            this.xSize,
            this.ySize,
            0,
            0,
            this.xSize,
            this.ySize,
            TEXTURE_FILE_WIDTH,
            TEXTURE_FILE_HEIGHT);

        // 在背景纹理的灰色槽上方绘制能量填充条。
        // 使用 Gui.drawRect（标准 1.7.10 GUI 矩形绘制方法）替代
        // GuiRenderHelper.fillRect（自定义 Tessellator 实现，存在坐标系偏移），
        // 消除此前 ~16px 的渲染位置偏移。
        // Y 位置使用 ENERGY_BAR_Y_OFFSET = 35（对齐源项目 topPos + 35），
        // 移除此前 Y=19 的魔法常数补偿。
        renderEnergyBar(this.guiLeft + ENERGY_BAR_X_OFFSET, this.guiTop + ENERGY_BAR_Y_OFFSET);

        // EU 池能量条（移植新增）：画在 EU 文字下方，右对齐于 x=168，配色对齐终端 EU 条。
        // 10^40 容量下现实存量占比 < 1e-28，填充宽度恒为 0，条仅作视觉槽位，数值以文字为准。
        renderEuBar(this.guiLeft + EU_BAR_RIGHT_X - EU_BAR_WIDTH, this.guiTop + EU_BAR_Y_OFFSET);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        // 标题：对齐源项目 titleLabelY = 6
        this.fontRendererObj
            .drawString(StatCollector.translateToLocal("tile.beyonddimensions.net_energy_pathway.name"), 8, 6, 4210752);

        // 能量存储量文字（"0/0"）：在能量条上方约 12px
        // 能量条灰色槽位于纹理 Y=35，渲染在 GUI 相对坐标 Y=35 处
        this.fontRendererObj.drawString(
            StringFormat.formatCount(menu.lastEnergyStored) + "/" + StringFormat.formatCount(menu.lastEnergyCapacity),
            8,
            23,
            4210752);

        // 能量传输速度文字（"0 FE/t"）：在能量条下方约 5px
        // 能量条高度 16px，槽底位于 Y=35+16=51
        this.fontRendererObj.drawString(StringFormat.formatChange(menu.lastEnergySpeedState) + " FE/t", 8, 56, 4210752);

        // EU 池存量（移植新增）：右对齐于内容区右缘(x=168)，下方画 EU 能量条（见 renderEuBar）。
        // 10^40 容量下比例条填充恒 0（现实存量占比 < 1e-28），条仅作视觉槽位，数值以文字为准。
        String euLine = "EU " + StringFormat.formatCount(getEuAmount())
            + " / "
            + StringFormat.formatCount(NetEuStorage.DEFAULT_CAPACITY);
        this.fontRendererObj
            .drawString(euLine, 168 - this.fontRendererObj.getStringWidth(euLine), EU_TEXT_Y_OFFSET, 4210752);

        // 物品栏标题：对齐源项目 inventoryLabelY = imageHeight - 94，
        // 玩家背包槽首行位于 Y=93，标签在 Y=82（11px 间距）
        // 1.7.10 无 inventoryLabelY 基类字段，直接计算
        this.fontRendererObj.drawString(StatCollector.translateToLocal("container.inventory"), 8, 82, 4210752);
    }

    // ==================== 能量条 ====================

    /**
     * 渲染能量填充条。
     * <p>
     * 在背景纹理的灰色槽（(8,35)-(168,51)，160×16）上方绘制能量条。
     * 始终绘制深红色背景条（即使能量为 0），再以亮红色填充已存储比例。
     * <p>
     * 对齐源项目 {@code NetEnergyGUI.renderEnergyBar} 始终绘制背景条纹的语义，
     * 确保能量条槽位不会显示为空框。
     *
     * @param xStart 能量条左上角屏幕 X 坐标
     * @param yStart 能量条左上角屏幕 Y 坐标
     */
    private void renderEnergyBar(int xStart, int yStart) {
        // 始终绘制能量条背景（深红色），覆盖整个灰色槽区域
        // 即使网络能量数据为 0（如未同步或空网络），槽位也不显空
        Gui.drawRect(xStart, yStart, xStart + ENERGY_BAR_WIDTH, yStart + ENERGY_BAR_HEIGHT, 0x40FF0000);

        // 计算填充比例并严格限制在 [0, 1]
        float energyRatio = 0f;
        if (menu.lastEnergyCapacity > 0) {
            energyRatio = (float) menu.lastEnergyStored / (float) menu.lastEnergyCapacity;
        }
        if (energyRatio < 0f) {
            energyRatio = 0f;
        } else if (energyRatio > 1f) {
            energyRatio = 1f;
        }

        int filledWidth = (int) (energyRatio * ENERGY_BAR_WIDTH);
        if (filledWidth < 0) {
            filledWidth = 0;
        } else if (filledWidth > ENERGY_BAR_WIDTH) {
            filledWidth = ENERGY_BAR_WIDTH;
        }

        // 在背景条上方绘制亮红色填充（仅当有能量时）
        if (filledWidth > 0) {
            Gui.drawRect(xStart, yStart, xStart + filledWidth, yStart + ENERGY_BAR_HEIGHT, 0xFFFF0000);
        }
    }

    /**
     * 渲染 EU 池能量条（移植新增）。
     * <p>
     * 对齐终端 EU 条配色（深灰底槽 + 琥珀金填充），右对齐于 x=168，画在 EU 文字下方。
     * 10^40 容量下现实存量占比 < 1e-28，填充宽度恒为 0，条仅作视觉槽位，数值以文字为准。
     * 始终绘制底槽，即使 EU 为 0 槽位也不显空。
     *
     * @param xStart 能量条左上角屏幕 X 坐标
     * @param yStart 能量条左上角屏幕 Y 坐标
     */
    private void renderEuBar(int xStart, int yStart) {
        // 底槽（深灰，半透明）
        Gui.drawRect(xStart, yStart, xStart + EU_BAR_WIDTH, yStart + EU_BAR_HEIGHT, EU_BAR_BG_COLOR);

        BigInteger amount = getEuAmount();
        double ratio = 0.0;
        if (amount.signum() > 0) {
            ratio = amount.doubleValue() / NetEuStorage.DEFAULT_CAPACITY.doubleValue();
        }
        if (ratio < 0.0) {
            ratio = 0.0;
        } else if (ratio > 1.0) {
            ratio = 1.0;
        }

        int filledWidth = (int) (ratio * EU_BAR_WIDTH);
        if (filledWidth > 0) {
            Gui.drawRect(xStart, yStart, xStart + filledWidth, yStart + EU_BAR_HEIGHT, EU_BAR_FILL_COLOR);
        }
    }

    // ==================== EU 池存量显示（移植新增） ====================

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawEuStorageTooltip(mouseX, mouseY);
    }

    /**
     * 解析 menu.euAmount（十进制字符串）为 BigInteger，非法输入视为 0。
     */
    private BigInteger getEuAmount() {
        try {
            return new BigInteger(menu.euAmount);
        } catch (NumberFormatException e) {
            return BigInteger.ZERO;
        }
    }

    /**
     * 悬停在 EU 存量文字或下方能量条上时显示完整十进制 tooltip（含容量）。
     */
    private void drawEuStorageTooltip(int mouseX, int mouseY) {
        BigInteger amount = getEuAmount();
        String line = "EU " + StringFormat.formatCount(amount)
            + " / "
            + StringFormat.formatCount(NetEuStorage.DEFAULT_CAPACITY);
        int textWidth = this.fontRendererObj.getStringWidth(line);
        int textX = this.guiLeft + 168 - textWidth;
        int textY = this.guiTop + EU_TEXT_Y_OFFSET;
        boolean hoverText = mouseX >= textX && mouseX < textX + textWidth && mouseY >= textY && mouseY < textY + 9;
        int barX = this.guiLeft + EU_BAR_RIGHT_X - EU_BAR_WIDTH;
        int barY = this.guiTop + EU_BAR_Y_OFFSET;
        boolean hoverBar = mouseX >= barX && mouseX < barX + EU_BAR_WIDTH
            && mouseY >= barY
            && mouseY < barY + EU_BAR_HEIGHT;
        if (!hoverText && !hoverBar) {
            return;
        }
        List<String> lines = new ArrayList<>();
        lines.add(StatCollector.translateToLocal("gui.beyonddimensions.eu_storage"));
        lines.add("EU " + amount + " / " + NetEuStorage.DEFAULT_CAPACITY);
        this.drawHoveringText(lines, mouseX, mouseY, this.fontRendererObj);
        // 复位 OpenGL 状态，避免污染后续渲染
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
