package com.wintercogs.beyonddimensions.client.gui;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import com.wintercogs.beyonddimensions.api.ids.BDConstants;
import com.wintercogs.beyonddimensions.client.gui.widget.shared.RightTabButton;
import com.wintercogs.beyonddimensions.common.block.entity.NetPumpBlockEntity;
import com.wintercogs.beyonddimensions.common.machine.FilterMode;
import com.wintercogs.beyonddimensions.common.machine.RedStoneControlMode;
import com.wintercogs.beyonddimensions.common.menu.NetPumpMenu;
import com.wintercogs.beyonddimensions.util.GuiRenderHelper;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 网络泵界面 GUI（1.7.10 移植版）。
 * <p>
 * 布局：TOP_BASE_COMMON + FILTER_SLOTS*4 + COMMON_CONNECTION + PLAYER_INV。
 * <p>
 * 状态按钮：过滤模式、红石控制模式。按钮点击直接写回 menu.be 字段并调用
 * writeAndSendQuickData() 同步服务端（客户端菜单持有效 be 引用，由 BDGuiHandler 传入）。
 */
@SideOnly(Side.CLIENT)
public class GuiNetPump extends GuiBase {

    private static final int ID_FILTER_MODE = 100;
    private static final int ID_CONTROL_MODE = 101;

    protected final NetPumpMenu menu;

    protected RightTabButton filterModeButton;
    protected RightTabButton controlModeButton;

    public GuiNetPump(InventoryPlayer inventory) {
        this(inventory, null);
    }

    public GuiNetPump(InventoryPlayer inventory, NetPumpBlockEntity te) {
        super(new NetPumpMenu(inventory, te));
        this.menu = (NetPumpMenu) this.inventorySlots;
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

    /** 初始化状态按钮（客户端菜单持有效 be 引用） */
    protected void initWidgets() {
        int baseX = this.guiLeft + 176;
        int topY = this.guiTop + 6;
        int step = 30;

        filterModeButton = new RightTabButton(
            ID_FILTER_MODE,
            baseX,
            topY,
            23,
            26,
            baseX + 3,
            topY + 4,
            16,
            16,
            button -> {
                filterModeButton.toggleState();
                if (menu.be != null) {
                    menu.be.filterMode = (FilterMode) filterModeButton.currentState;
                    menu.writeAndSendQuickData();
                }
            }) {

            @Override
            protected void initButton() {
                iconMap.put(
                    FilterMode.IGNORE,
                    new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/ignore_filter.png"));
                iconMap.put(
                    FilterMode.WHITE,
                    new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/white_filter.png"));
                iconMap.put(
                    FilterMode.BLACK,
                    new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/black_filter.png"));
                tooltipMap.put(FilterMode.IGNORE, "tooltip.button.beyonddimensions.filter_mode_ignore");
                tooltipMap.put(FilterMode.WHITE, "tooltip.button.beyonddimensions.filter_mode_white");
                tooltipMap.put(FilterMode.BLACK, "tooltip.button.beyonddimensions.filter_mode_black");
                for (Enum<?> state : iconMap.keySet()) {
                    this.states.add(state);
                }
                setState(menu.be != null ? menu.be.filterMode : FilterMode.IGNORE);
            }
        };
        this.buttonList.add(filterModeButton);

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
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        syncButtonStates();
    }

    /** 从 menu.be 同步按钮状态（客户端菜单持有效 be 引用） */
    protected void syncButtonStates() {
        if (menu.be == null) return;
        if (filterModeButton.currentState != menu.be.filterMode) filterModeButton.setState(menu.be.filterMode);
        if (controlModeButton.currentState != menu.be.controlMode) controlModeButton.setState(menu.be.controlMode);
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
            StatCollector.translateToLocal("tile.beyonddimensions.net_pump_block.name"),
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
