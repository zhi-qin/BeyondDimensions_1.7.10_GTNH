package com.wintercogs.beyonddimensions.client.gui;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import com.wintercogs.beyonddimensions.api.ids.BDConstants;
import com.wintercogs.beyonddimensions.client.gui.widget.shared.RightTabButton;
import com.wintercogs.beyonddimensions.common.item.BaseMachineItem;
import com.wintercogs.beyonddimensions.common.machine.FuzzyMode;
import com.wintercogs.beyonddimensions.common.machine.ReceiveMode;
import com.wintercogs.beyonddimensions.common.machine.RedStoneControlMode;
import com.wintercogs.beyonddimensions.common.menu.NetRestockerMenu;
import com.wintercogs.beyonddimensions.util.GuiRenderHelper;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 网络补货器界面 GUI（1.7.10 移植版）。
 * <p>
 * 布局：TOP_BASE_COMMON + COMMON_CONNECTION*2 + FILTER_SLOTS*4 + COMMON_CONNECTION + PLAYER_INV。
 * <p>
 * 状态按钮：模糊匹配模式、接收模式、红石控制模式。
 * 按钮点击直接写回 menu.menuStack 的 NBT 模式字段并调用 writeAndSendQuickData() 同步服务端
 * （客户端菜单持有效 menuStack 引用，由 BDGuiHandler 传入）。
 * TODO: 装备槽位背景图标（1.20.1 使用 InventoryMenu.EMPTY_ARMOR_SLOT_*）暂未实现，
 * 1.7.10 缺少对应资源。
 */
@SideOnly(Side.CLIENT)
public class GuiNetRestocker extends GuiBase {

    private static final int ID_FUZZY_MODE = 100;
    private static final int ID_RECEIVE_MODE = 101;
    private static final int ID_CONTROL_MODE = 102;

    protected final NetRestockerMenu menu;

    protected RightTabButton fuzzyModeButton;
    protected RightTabButton receiveModeButton;
    protected RightTabButton controlModeButton;

    public GuiNetRestocker(InventoryPlayer inventory) {
        this(inventory, null);
    }

    public GuiNetRestocker(InventoryPlayer inventory, ItemStack menuStack) {
        super(new NetRestockerMenu(inventory, menuStack));
        this.menu = (NetRestockerMenu) this.inventorySlots;
        this.xSize = 176;
        this.ySize = rebuildImageHeight();
    }

    protected int rebuildImageHeight() {
        return CommonTextures.TOP_BASE_COMMON_HEIGHT + CommonTextures.COMMON_CONNECTION_HEIGHT * 2
            + CommonTextures.FILTER_SLOTS_HEIGHT * 4
            + CommonTextures.COMMON_CONNECTION_HEIGHT
            + CommonTextures.PLAYER_INV_HEIGHT;
    }

    @Override
    public void initGui() {
        super.initGui();
        initWidgets();
    }

    /** 初始化状态按钮（客户端菜单持有效 menuStack 引用） */
    protected void initWidgets() {
        int baseX = this.guiLeft + 176;
        int topY = this.guiTop + 6;
        int step = 30;

        fuzzyModeButton = new RightTabButton(
            ID_FUZZY_MODE,
            baseX,
            topY,
            23,
            26,
            baseX + 3,
            topY + 4,
            16,
            16,
            button -> {
                fuzzyModeButton.toggleState();
                BaseMachineItem.setFuzzyMode(menu.menuStack, (FuzzyMode) fuzzyModeButton.currentState);
                menu.writeAndSendQuickData();
            }) {

            @Override
            protected void initButton() {
                iconMap.put(
                    FuzzyMode.DISABLE,
                    new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/hopper_nbt_mode_allow.png"));
                iconMap.put(
                    FuzzyMode.ENABLE,
                    new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/hopper_nbt_mode_deny.png"));
                tooltipMap.put(FuzzyMode.DISABLE, "tooltip.button.beyonddimensions.fuzzy_mode_disable");
                tooltipMap.put(FuzzyMode.ENABLE, "tooltip.button.beyonddimensions.fuzzy_mode_enable");
                for (Enum<?> state : iconMap.keySet()) {
                    this.states.add(state);
                }
                setState(BaseMachineItem.getFuzzyModeOrDefault(menu.menuStack, FuzzyMode.DISABLE));
            }
        };
        this.buttonList.add(fuzzyModeButton);

        receiveModeButton = new RightTabButton(
            ID_RECEIVE_MODE,
            baseX,
            topY + step,
            23,
            26,
            baseX + 3,
            topY + step + 4,
            16,
            16,
            button -> {
                receiveModeButton.toggleState();
                BaseMachineItem.setReceiveMode(menu.menuStack, (ReceiveMode) receiveModeButton.currentState);
                menu.writeAndSendQuickData();
            }) {

            @Override
            protected void initButton() {
                iconMap.put(
                    ReceiveMode.STOP,
                    new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/net_disable.png"));
                iconMap.put(
                    ReceiveMode.OPEN,
                    new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/net_absorb.png"));
                tooltipMap.put(ReceiveMode.STOP, "tooltip.button.beyonddimensions.receive_mode_stop");
                tooltipMap.put(ReceiveMode.OPEN, "tooltip.button.beyonddimensions.receive_mode_open");
                for (Enum<?> state : iconMap.keySet()) {
                    this.states.add(state);
                }
                setState(BaseMachineItem.getReceiveModeOrDefault(menu.menuStack, ReceiveMode.STOP));
            }
        };
        this.buttonList.add(receiveModeButton);

        controlModeButton = new RightTabButton(
            ID_CONTROL_MODE,
            baseX,
            topY + step * 2,
            23,
            26,
            baseX + 3,
            topY + step * 2 + 4,
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

    /** 从 menu.menuStack 同步按钮状态（客户端菜单持有效 menuStack 引用） */
    protected void syncButtonStates() {
        if (menu.menuStack == null) return;
        FuzzyMode fuzzyMode = BaseMachineItem.getFuzzyModeOrDefault(menu.menuStack, FuzzyMode.DISABLE);
        ReceiveMode receiveMode = BaseMachineItem.getReceiveModeOrDefault(menu.menuStack, ReceiveMode.STOP);
        RedStoneControlMode controlMode = BaseMachineItem
            .getControlModeOrDefault(menu.menuStack, RedStoneControlMode.IGNORE);
        if (fuzzyModeButton.currentState != fuzzyMode) fuzzyModeButton.setState(fuzzyMode);
        if (receiveModeButton.currentState != receiveMode) receiveModeButton.setState(receiveMode);
        if (controlModeButton.currentState != controlMode) controlModeButton.setState(controlMode);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int[] drawY = new int[] { this.guiTop };
        GuiRenderHelper.resetColor();

        CommonTexturesRender.renderTopBaseCommon(this.guiLeft, drawY);
        CommonTexturesRender.renderCommonConnection(this.guiLeft, drawY);
        CommonTexturesRender.renderCommonConnection(this.guiLeft, drawY);
        CommonTexturesRender.renderFilterSlots(this.guiLeft, drawY);
        CommonTexturesRender.renderFilterSlots(this.guiLeft, drawY);
        CommonTexturesRender.renderFilterSlots(this.guiLeft, drawY);
        CommonTexturesRender.renderFilterSlots(this.guiLeft, drawY);
        CommonTexturesRender.renderCommonConnection(this.guiLeft, drawY);
        CommonTexturesRender.renderPlayerInv(this.guiLeft, drawY);

        // TODO: 渲染 5 个装备槽背景图标（EXTRA_SLOT_START_X + i*18 - 1, EXTRA_SLOT_Y - 1）
        // 1.20.1 使用 minecraft:textures/gui/container/stats_icons.png 中的 EMPTY_ARMOR_SLOT_*，
        // 1.7.10 没有这些资源，暂未实现。
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        int titleLabelY = 8;
        int inventoryLabelY = CommonTextures.TOP_BASE_COMMON_HEIGHT + CommonTextures.COMMON_CONNECTION_HEIGHT * 2
            + CommonTextures.FILTER_SLOTS_HEIGHT * 4
            + 4;
        this.fontRendererObj.drawString(
            StatCollector.translateToLocal("item.beyonddimensions.net_restocker_item.name"),
            8,
            titleLabelY,
            4210752);
        GuiRenderHelper.drawRightAnchoredText(
            this.fontRendererObj,
            StatCollector.translateToLocal("menu.label.beyonddimensions.restock_slots"),
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
