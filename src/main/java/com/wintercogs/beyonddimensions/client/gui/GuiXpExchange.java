package com.wintercogs.beyonddimensions.client.gui;

import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import com.wintercogs.beyonddimensions.api.ids.BDConstants;
import com.wintercogs.beyonddimensions.client.gui.widget.shared.RightTabButton;
import com.wintercogs.beyonddimensions.common.item.XpExchangeItem;
import com.wintercogs.beyonddimensions.common.item.XpExchangeSettings;
import com.wintercogs.beyonddimensions.common.menu.XpExchangeMenu;
import com.wintercogs.beyonddimensions.util.GuiRenderHelper;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 经验交换界面 GUI（1.7.10 移植版）。
 * <p>
 * 布局：TOP_BASE_COMMON + COMMON_CONNECTION*5 + PLAYER_INV。
 * <p>
 * 控件：目标等级输入框（GuiTextField）、保持模式按钮（RightTabButton）。
 * 交互直接写回 menu.menuStack 的 NBT 字段并调用 writeAndSendQuickData() 同步服务端
 * （客户端菜单持有效 menuStack 引用，由 BDGuiHandler 传入）。
 */
@SideOnly(Side.CLIENT)
public class GuiXpExchange extends GuiBase {

    private static final int ID_KEEP_MODE = 100;

    /** 保持模式按钮状态枚举（移植自 1.20.1 XpExchangeGUI.KeepModeState） */
    private enum KeepModeState {
        WORKING,
        NOT_WORKING
    }

    protected final XpExchangeMenu menu;

    protected GuiTextField targetLevelField;
    protected RightTabButton keepModeButton;
    private boolean syncingField;
    private String lastFieldText = "";

    public GuiXpExchange(InventoryPlayer inventory) {
        this(inventory, null);
    }

    public GuiXpExchange(InventoryPlayer inventory, ItemStack menuStack) {
        super(new XpExchangeMenu(inventory, menuStack));
        this.menu = (XpExchangeMenu) this.inventorySlots;
        this.xSize = 176;
        this.ySize = rebuildImageHeight();
    }

    protected int rebuildImageHeight() {
        return CommonTextures.TOP_BASE_COMMON_HEIGHT + CommonTextures.COMMON_CONNECTION_HEIGHT * 5
            + CommonTextures.PLAYER_INV_HEIGHT;
    }

    @Override
    public void initGui() {
        super.initGui();
        initWidgets();
    }

    /** 初始化目标等级输入框与保持模式按钮 */
    protected void initWidgets() {
        // 目标等级输入框
        int fieldX = this.guiLeft + 50;
        int fieldY = this.guiTop + 24;
        int fieldWidth = 82;
        int fieldHeight = this.fontRendererObj.FONT_HEIGHT + 6;
        targetLevelField = new GuiTextField(this.fontRendererObj, fieldX, fieldY, fieldWidth, fieldHeight);
        targetLevelField.setMaxStringLength(6);
        targetLevelField.setEnableBackgroundDrawing(true);
        targetLevelField.setVisible(true);
        targetLevelField.setTextColor(0xFFFFFFFF);
        int initialTargetLevel = menu.menuStack != null ? XpExchangeSettings.getTargetLevel(menu.menuStack) : 0;
        targetLevelField.setText(String.valueOf(initialTargetLevel));
        lastFieldText = targetLevelField.getText();
        this.textFields.add(targetLevelField);

        // 保持模式按钮
        int baseX = this.guiLeft + 176;
        int topY = this.guiTop + 6;
        keepModeButton = new RightTabButton(ID_KEEP_MODE, baseX, topY, 23, 26, baseX + 3, topY + 4, 16, 16, button -> {
            KeepModeState nextState = keepModeButton.currentState == KeepModeState.WORKING ? KeepModeState.NOT_WORKING
                : KeepModeState.WORKING;
            keepModeButton.setState(nextState);
            // 三段式：写入数据源 + 同步服务端（对齐源项目 XpExchangeGUI）
            XpExchangeItem.setXpNetKeepMode(menu.menuStack, nextState == KeepModeState.WORKING);
            menu.writeAndSendQuickData();
        }) {

            @Override
            protected void initButton() {
                iconMap.put(
                    KeepModeState.WORKING,
                    new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/control_mode_ignore.png"));
                iconMap.put(
                    KeepModeState.NOT_WORKING,
                    new ResourceLocation(
                        BDConstants.MODID,
                        "textures/gui/sprites/widget/control_mode_not_working.png"));
                tooltipMap.put(KeepModeState.WORKING, "tooltip.button.beyonddimensions.xp_exchange.keep_mode_working");
                tooltipMap.put(
                    KeepModeState.NOT_WORKING,
                    "tooltip.button.beyonddimensions.xp_exchange.keep_mode_not_working");
                this.states.add(KeepModeState.WORKING);
                this.states.add(KeepModeState.NOT_WORKING);
                setState(resolveKeepModeState());
            }
        };
        this.buttonList.add(keepModeButton);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        syncWidgetStates();
    }

    /** 从 menu.menuStack 同步控件状态（客户端 menuStack 为 null 时保持默认状态） */
    protected void syncWidgetStates() {
        // 同步保持模式按钮
        KeepModeState keepModeState = resolveKeepModeState();
        if (keepModeButton.currentState != keepModeState) keepModeButton.setState(keepModeState);

        // 同步目标等级输入框（仅在未聚焦时从数据源同步）
        if (menu.menuStack != null && !targetLevelField.isFocused()) {
            String currentTargetLevel = Integer.toString(XpExchangeSettings.getTargetLevel(menu.menuStack));
            if (!targetLevelField.getText()
                .equals(currentTargetLevel)) {
                syncingField = true;
                targetLevelField.setText(currentTargetLevel);
                lastFieldText = currentTargetLevel;
                syncingField = false;
            }
        }

        // 检测输入框文本变化（聚焦时由用户编辑触发）
        if (!syncingField && targetLevelField.isFocused()
            && !targetLevelField.getText()
                .equals(lastFieldText)) {
            lastFieldText = targetLevelField.getText();
            handleTargetLevelChanged(lastFieldText);
        }
    }

    /** 处理目标等级输入框文本变化 */
    private void handleTargetLevelChanged(String text) {
        if (menu.menuStack == null) return;
        int targetLevel = 0;
        if (!text.isEmpty()) {
            try {
                targetLevel = Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return;
            }
        }
        int sanitized = XpExchangeSettings.sanitizeTargetLevel(targetLevel);
        XpExchangeSettings.setTargetLevel(menu.menuStack, sanitized);
        // 同步服务端（对齐源项目 XpExchangeGUI responder 的 writeAndSendQuickData）
        menu.writeAndSendQuickData();
    }

    private KeepModeState resolveKeepModeState() {
        boolean keepMode = menu.menuStack != null ? XpExchangeItem.getOrDefaultXpNetKeepMode(menu.menuStack, false)
            : false;
        return keepMode ? KeepModeState.WORKING : KeepModeState.NOT_WORKING;
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int[] drawY = new int[] { this.guiTop };
        GuiRenderHelper.resetColor();

        CommonTexturesRender.renderTopBaseCommon(this.guiLeft, drawY);
        CommonTexturesRender.renderCommonConnection(this.guiLeft, drawY);
        CommonTexturesRender.renderCommonConnection(this.guiLeft, drawY);
        CommonTexturesRender.renderCommonConnection(this.guiLeft, drawY);
        CommonTexturesRender.renderCommonConnection(this.guiLeft, drawY);
        CommonTexturesRender.renderCommonConnection(this.guiLeft, drawY);
        CommonTexturesRender.renderPlayerInv(this.guiLeft, drawY);

        // 文本输入框（等级输入）置于背景层，避免遮挡标题与 NEI 提示
        drawTextFields();
        // 滚动条置于背景层，避免滑块遮挡 NEI 提示
        drawScrollBars();
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        int titleLabelY = 8;
        int inventoryLabelY = CommonTextures.TOP_BASE_COMMON_HEIGHT + CommonTextures.COMMON_CONNECTION_HEIGHT * 4 + 4;
        this.fontRendererObj.drawString(
            StatCollector.translateToLocal("item.beyonddimensions.xp_exchange_item.name"),
            8,
            titleLabelY,
            4210752);
        this.fontRendererObj.drawString(
            StatCollector.translateToLocal("menu.label.beyonddimensions.xp_exchange.target_level"),
            8,
            27,
            4210752);
        this.fontRendererObj.drawString(
            StatCollector.translateToLocal("menu.label.beyonddimensions.xp_exchange.max_level") + " "
                + XpExchangeSettings.MAX_TARGET_LEVEL,
            8,
            41,
            4210752);
        this.fontRendererObj
            .drawString(StatCollector.translateToLocal("container.inventory"), 8, inventoryLabelY, 4210752);
    }
}
