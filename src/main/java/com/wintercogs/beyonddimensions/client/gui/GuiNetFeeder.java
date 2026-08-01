package com.wintercogs.beyonddimensions.client.gui;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import com.wintercogs.beyonddimensions.api.ids.BDConstants;
import com.wintercogs.beyonddimensions.client.gui.widget.shared.RightTabButton;
import com.wintercogs.beyonddimensions.common.item.BaseMachineItem;
import com.wintercogs.beyonddimensions.common.machine.FeederMode;
import com.wintercogs.beyonddimensions.common.machine.RedStoneControlMode;
import com.wintercogs.beyonddimensions.common.menu.NetFeederMenu;
import com.wintercogs.beyonddimensions.util.GuiRenderHelper;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 网络喂食器界面 GUI（1.7.10 移植版）。
 * <p>
 * 布局：TOP_BASE_COMMON + FILTER_SLOTS*4 + COMMON_CONNECTION + PLAYER_INV。
 * <p>
 * 状态按钮：喂食模式、红石控制模式。
 * 对齐 1.20.1 源项目 NetFeederGUI：按钮点击后切换本地状态，同时把新状态写入
 * menu.menuStack 并调用 writeAndSendQuickData 同步到服务端；initButton 从
 * menu.menuStack 读取初始状态。
 */
@SideOnly(Side.CLIENT)
public class GuiNetFeeder extends GuiBase {

    private static final int ID_FEEDER_MODE = 100;
    private static final int ID_CONTROL_MODE = 101;

    protected final NetFeederMenu menu;

    protected RightTabButton feederModeButton;
    protected RightTabButton controlModeButton;

    public GuiNetFeeder(InventoryPlayer inventory) {
        this(inventory, null);
    }

    public GuiNetFeeder(InventoryPlayer inventory, ItemStack menuStack) {
        super(new NetFeederMenu(inventory, menuStack));
        this.menu = (NetFeederMenu) this.inventorySlots;
        this.xSize = 176;
        this.ySize = rebuildImageHeight();
    }

    protected int rebuildImageHeight() {
        return CommonTextures.TOP_BASE_COMMON_HEIGHT + CommonTextures.FILTER_SLOTS_HEIGHT * 4
            + CommonTextures.COMMON_CONNECTION_HEIGHT
            + CommonTextures.PLAYER_INV_HEIGHT;
    }

    @Override
    public void initGui() {
        super.initGui();
        initWidgets();
    }

    /** 初始化状态按钮（对齐 1.20.1 源项目：初始状态读取 menu.menuStack） */
    protected void initWidgets() {
        int baseX = this.guiLeft + 176;
        int topY = this.guiTop + 6;
        int step = 30;

        feederModeButton = new RightTabButton(
            ID_FEEDER_MODE,
            baseX,
            topY,
            23,
            26,
            baseX + 3,
            topY + 4,
            16,
            16,
            button -> {
                feederModeButton.toggleState();
                BaseMachineItem.setFeederMode(menu.menuStack, (FeederMode) feederModeButton.currentState);
                menu.writeAndSendQuickData();
            }) {

            @Override
            protected void initButton() {
                iconMap.put(
                    FeederMode.HUNGER_TO_EAT,
                    new ResourceLocation(
                        BDConstants.MODID,
                        "textures/gui/sprites/widget/feeder_mode_hunger_to_eat.png"));
                iconMap.put(
                    FeederMode.NORMAL,
                    new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/feeder_mode_normal.png"));
                iconMap.put(
                    FeederMode.SATURATION_KEEP,
                    new ResourceLocation(
                        BDConstants.MODID,
                        "textures/gui/sprites/widget/feeder_mode_saturation_keep.png"));
                iconMap.put(
                    FeederMode.CRAZY,
                    new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/feeder_mode_crazy.png"));
                tooltipMap.put(FeederMode.HUNGER_TO_EAT, "tooltip.button.beyonddimensions.feeder_mode_hunger_to_eat");
                tooltipMap.put(FeederMode.NORMAL, "tooltip.button.beyonddimensions.feeder_mode_normal");
                tooltipMap
                    .put(FeederMode.SATURATION_KEEP, "tooltip.button.beyonddimensions.feeder_mode_saturation_keep");
                tooltipMap.put(FeederMode.CRAZY, "tooltip.button.beyonddimensions.feeder_mode_crazy");
                for (Enum<?> state : iconMap.keySet()) {
                    this.states.add(state);
                }
                setState(BaseMachineItem.getFeederModeOrDefault(menu.menuStack, FeederMode.NORMAL));
            }
        };
        this.buttonList.add(feederModeButton);

        controlModeButton = new RightTabButton(
            ID_CONTROL_MODE,
            baseX,
            topY + step,
            23,
            26,
            baseX + 2,
            topY + step + 5,
            16,
            16,
            button -> {
                controlModeButton.toggleState();
                BaseMachineItem.setControlMode(menu.menuStack, (RedStoneControlMode) controlModeButton.currentState);
                menu.writeAndSendQuickData();
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
                tooltipMap.put(RedStoneControlMode.IGNORE, "tooltip.button.beyonddimensions.control_mode_ignore");
                tooltipMap
                    .put(RedStoneControlMode.NOT_WORKING, "tooltip.button.beyonddimensions.control_mode_not_working");
                for (Enum<?> state : iconMap.keySet()) {
                    this.states.add(state);
                }
                setState(BaseMachineItem.getControlModeOrDefault(menu.menuStack, RedStoneControlMode.IGNORE));
            }
        };
        this.buttonList.add(controlModeButton);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        syncButtonStates();
    }

    /** 从 menu.menuStack 同步按钮状态（客户端菜单经 findCurrentFeederStack 持有效引用） */
    protected void syncButtonStates() {
        // 刷新 menuStack 引用（1.7.10 中背包 ItemStack 对象可能被替换）
        menu.findCurrentFeederStack();
        if (menu.menuStack == null) return;
        FeederMode feederMode = BaseMachineItem.getFeederModeOrDefault(menu.menuStack, FeederMode.NORMAL);
        RedStoneControlMode controlMode = BaseMachineItem
            .getControlModeOrDefault(menu.menuStack, RedStoneControlMode.IGNORE);
        if (feederModeButton.currentState != feederMode) feederModeButton.setState(feederMode);
        if (controlModeButton.currentState != controlMode) controlModeButton.setState(controlMode);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int[] drawY = new int[] { this.guiTop };
        GuiRenderHelper.resetColor();

        CommonTexturesRender.renderTopBaseCommon(this.guiLeft, drawY);
        CommonTexturesRender.renderFilterSlots(this.guiLeft, drawY);
        CommonTexturesRender.renderFilterSlots(this.guiLeft, drawY);
        CommonTexturesRender.renderFilterSlots(this.guiLeft, drawY);
        CommonTexturesRender.renderFilterSlots(this.guiLeft, drawY);
        CommonTexturesRender.renderCommonConnection(this.guiLeft, drawY);
        CommonTexturesRender.renderPlayerInv(this.guiLeft, drawY);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        int titleLabelY = 8;
        int inventoryLabelY = CommonTextures.TOP_BASE_COMMON_HEIGHT + CommonTextures.FILTER_SLOTS_HEIGHT * 4 + 4;
        this.fontRendererObj.drawString(
            StatCollector.translateToLocal("item.beyonddimensions.net_feeder_item.name"),
            8,
            titleLabelY,
            4210752);
        GuiRenderHelper.drawRightAnchoredText(
            this.fontRendererObj,
            StatCollector.translateToLocal("menu.label.beyonddimensions.filter_slots"),
            this.xSize - 6,
            titleLabelY + 3,
            4210752,
            false);
        this.fontRendererObj
            .drawString(StatCollector.translateToLocal("container.inventory"), 8, inventoryLabelY, 4210752);
        // 标记槽/存储槽中的非物品条目（流体/能量）覆盖层（对齐 GuiNetFurnace）：
        // 无此层时流体/能量标记渲染为空槽（原版 drawSlot 只能渲染 ItemStack）
        drawTypedSlotOverlays();
    }
}
