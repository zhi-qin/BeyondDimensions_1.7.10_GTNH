package com.wintercogs.beyonddimensions.client.gui;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import com.wintercogs.beyonddimensions.api.ids.BDConstants;
import com.wintercogs.beyonddimensions.client.gui.widget.shared.RightTabButton;
import com.wintercogs.beyonddimensions.common.block.entity.NetInterfaceBlockEntity;
import com.wintercogs.beyonddimensions.common.machine.FuzzyMode;
import com.wintercogs.beyonddimensions.common.machine.PopMode;
import com.wintercogs.beyonddimensions.common.machine.RedStoneControlMode;
import com.wintercogs.beyonddimensions.common.menu.NetInterfaceBaseMenu;
import com.wintercogs.beyonddimensions.util.GuiRenderHelper;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 网络接口界面 GUI（1.7.10 移植版）。
 * <p>
 * 布局：TOP_BASE_COMMON + (FILTER_SLOTS + COMMON_SLOTS)*3 + COMMON_CONNECTION + PLAYER_INV。
 * <p>
 * 状态按钮：弹出模式、红石控制模式、模糊匹配模式。按钮点击直接写回菜单/方块字段并调用
 * writeAndSendQuickData() 同步服务端（客户端菜单持有效 access 引用，由 BDGuiHandler 传入）。
 */
@SideOnly(Side.CLIENT)
public class GuiNetInterface extends GuiBase {

    private static final int ID_POP_MODE = 100;
    private static final int ID_CONTROL_MODE = 101;
    private static final int ID_FUZZY_MODE = 102;

    protected final NetInterfaceBaseMenu menu;

    protected RightTabButton popModeButton;
    protected RightTabButton controlModeButton;
    protected RightTabButton fuzzyModeButton;

    public GuiNetInterface(InventoryPlayer inventory) {
        this(inventory, null);
    }

    public GuiNetInterface(InventoryPlayer inventory, NetInterfaceBlockEntity te) {
        super(new NetInterfaceBaseMenu(inventory, te));
        this.menu = (NetInterfaceBaseMenu) this.inventorySlots;
        this.xSize = 176;
        this.ySize = rebuildImageHeight();
    }

    protected int rebuildImageHeight() {
        return CommonTextures.TOP_BASE_COMMON_HEIGHT + CommonTextures.COMMON_SLOTS_HEIGHT * 3
            + CommonTextures.FILTER_SLOTS_HEIGHT * 3
            + CommonTextures.COMMON_CONNECTION_HEIGHT
            + CommonTextures.PLAYER_INV_HEIGHT;
    }

    @Override
    public void initGui() {
        super.initGui();
        initWidgets();
    }

    /** 初始化状态按钮（占位：客户端 access 为 ClientAccess，仅本地视觉切换） */
    protected void initWidgets() {
        int baseX = this.guiLeft + 176;
        int topY = this.guiTop + 6;
        int step = 30;

        popModeButton = new RightTabButton(ID_POP_MODE, baseX, topY, 23, 26, baseX + 3, topY + 4, 16, 16, button -> {
            // 权限检查：无权限（挂载态受限）时不切换（对齐源项目 NetInterfaceBaseGUI）
            if (menu.getAccess() != null && !menu.getAccess()
                .canConfigurePopMode()) return;
            popModeButton.toggleState();
            if (menu.getAccess() != null) {
                menu.getAccess()
                    .setPopMode((PopMode) popModeButton.currentState);
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
                setState(
                    menu.getAccess() != null && menu.getAccess()
                        .isMenuValid() ? menu.getAccess()
                            .getPopMode() : PopMode.STOP);
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
                if (menu.getAccess() != null) {
                    menu.getAccess()
                        .setControlMode((RedStoneControlMode) controlModeButton.currentState);
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
                setState(
                    menu.getAccess() != null && menu.getAccess()
                        .isMenuValid() ? menu.getAccess()
                            .getControlMode() : RedStoneControlMode.IGNORE);
            }
        };
        this.buttonList.add(controlModeButton);

        fuzzyModeButton = new RightTabButton(
            ID_FUZZY_MODE,
            baseX,
            topY + step * 2,
            23,
            26,
            baseX + 3,
            topY + step * 2 + 4,
            16,
            16,
            button -> {
                fuzzyModeButton.toggleState();
                if (menu.getAccess() != null) {
                    menu.getAccess()
                        .setFuzzyMode((FuzzyMode) fuzzyModeButton.currentState);
                    menu.writeAndSendQuickData();
                }
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
                setState(
                    menu.getAccess() != null && menu.getAccess()
                        .isMenuValid() ? menu.getAccess()
                            .getFuzzyMode() : FuzzyMode.DISABLE);
            }
        };
        this.buttonList.add(fuzzyModeButton);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        syncButtonStates();
    }

    /** 从 menu.getAccess() 同步按钮状态 */
    protected void syncButtonStates() {
        if (menu.getAccess() == null || !menu.getAccess()
            .isMenuValid()) return;
        if (popModeButton.currentState != menu.getAccess()
            .getPopMode())
            popModeButton.setState(
                menu.getAccess()
                    .getPopMode());
        if (controlModeButton.currentState != menu.getAccess()
            .getControlMode())
            controlModeButton.setState(
                menu.getAccess()
                    .getControlMode());
        if (fuzzyModeButton.currentState != menu.getAccess()
            .getFuzzyMode())
            fuzzyModeButton.setState(
                menu.getAccess()
                    .getFuzzyMode());
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int[] drawY = new int[] { this.guiTop };
        GuiRenderHelper.resetColor();

        CommonTexturesRender.renderTopBaseCommon(this.guiLeft, drawY);
        CommonTexturesRender.renderFilterSlots(this.guiLeft, drawY);
        CommonTexturesRender.renderCommonSlots(this.guiLeft, drawY);
        CommonTexturesRender.renderFilterSlots(this.guiLeft, drawY);
        CommonTexturesRender.renderCommonSlots(this.guiLeft, drawY);
        CommonTexturesRender.renderFilterSlots(this.guiLeft, drawY);
        CommonTexturesRender.renderCommonSlots(this.guiLeft, drawY);
        CommonTexturesRender.renderCommonConnection(this.guiLeft, drawY);
        CommonTexturesRender.renderPlayerInv(this.guiLeft, drawY);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        int titleLabelY = 8;
        int inventoryLabelY = CommonTextures.TOP_BASE_COMMON_HEIGHT + CommonTextures.COMMON_SLOTS_HEIGHT * 3
            + CommonTextures.FILTER_SLOTS_HEIGHT * 3
            + 4;
        this.fontRendererObj.drawString(
            StatCollector.translateToLocal("tile.beyonddimensions.net_interface.name"),
            8,
            titleLabelY,
            4210752);
        GuiRenderHelper.drawRightAnchoredText(
            this.fontRendererObj,
            StatCollector.translateToLocal("menu.label.beyonddimensions.tag_and_stored_slots"),
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
