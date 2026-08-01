package com.wintercogs.beyonddimensions.client.gui;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import com.wintercogs.beyonddimensions.api.ids.BDConstants;
import com.wintercogs.beyonddimensions.client.gui.widget.shared.LeftTabButton;
import com.wintercogs.beyonddimensions.client.gui.widget.shared.RightTabButton;
import com.wintercogs.beyonddimensions.common.block.entity.NetHopperBlockEntity;
import com.wintercogs.beyonddimensions.common.machine.FilterMode;
import com.wintercogs.beyonddimensions.common.machine.HopperFluidMode;
import com.wintercogs.beyonddimensions.common.machine.HopperItemMode;
import com.wintercogs.beyonddimensions.common.machine.HopperNBTMode;
import com.wintercogs.beyonddimensions.common.machine.HopperRangeMode;
import com.wintercogs.beyonddimensions.common.machine.HopperXpMode;
import com.wintercogs.beyonddimensions.common.machine.RedStoneControlMode;
import com.wintercogs.beyonddimensions.common.menu.NetHopperMenu;
import com.wintercogs.beyonddimensions.util.GuiRenderHelper;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 网络漏斗界面 GUI（1.7.10 移植版）。
 * <p>
 * 布局：TOP_BASE_COMMON + FILTER_SLOTS*4 + COMMON_CONNECTION + PLAYER_INV。
 * <p>
 * 状态按钮：过滤模式、红石控制模式、漏斗物品/经验/NBT/流体模式、漏斗范围模式。
 * 按钮点击直接写回 menu.be 字段并调用 writeAndSendQuickData() 同步服务端
 * （客户端菜单持有效 be 引用，由 BDGuiHandler 传入）。
 */
@SideOnly(Side.CLIENT)
public class GuiNetHopper extends GuiBase {

    private static final int ID_FILTER_MODE = 100;
    private static final int ID_CONTROL_MODE = 101;
    private static final int ID_HOPPER_ITEM_MODE = 102;
    private static final int ID_HOPPER_XP_MODE = 103;
    private static final int ID_HOPPER_NBT_MODE = 104;
    private static final int ID_HOPPER_FLUID_MODE = 105;
    private static final int ID_HOPPER_RANGE_MODE = 106;

    protected final NetHopperMenu menu;

    protected RightTabButton filterModeButton;
    protected RightTabButton controlModeButton;
    protected RightTabButton hopperItemModeButton;
    protected RightTabButton hopperXpModeButton;
    protected RightTabButton hopperNBTModeButton;
    protected RightTabButton hopperFluidModeButton;
    protected LeftTabButton hopperRangeModeButton;

    public GuiNetHopper(InventoryPlayer inventory) {
        this(inventory, null);
    }

    public GuiNetHopper(InventoryPlayer inventory, NetHopperBlockEntity te) {
        super(new NetHopperMenu(inventory, te));
        this.menu = (NetHopperMenu) this.inventorySlots;
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
        int leftX = this.guiLeft - 23;
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

        hopperItemModeButton = new RightTabButton(
            ID_HOPPER_ITEM_MODE,
            baseX,
            topY + step * 2,
            23,
            26,
            baseX + 3,
            topY + step * 2 + 4,
            16,
            16,
            button -> {
                hopperItemModeButton.toggleState();
                if (menu.be != null) {
                    menu.be.hopperItemMode = (HopperItemMode) hopperItemModeButton.currentState;
                    menu.writeAndSendQuickData();
                }
            }) {

            @Override
            protected void initButton() {
                iconMap.put(
                    HopperItemMode.DENY,
                    new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/hopper_item_mode_deny.png"));
                iconMap.put(
                    HopperItemMode.ALLOW,
                    new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/hopper_item_mode_allow.png"));
                tooltipMap.put(HopperItemMode.DENY, "tooltip.button.beyonddimensions.hopper_item_mode_deny");
                tooltipMap.put(HopperItemMode.ALLOW, "tooltip.button.beyonddimensions.hopper_item_mode_allow");
                for (Enum<?> state : iconMap.keySet()) {
                    this.states.add(state);
                }
                setState(menu.be != null ? menu.be.hopperItemMode : HopperItemMode.ALLOW);
            }
        };
        this.buttonList.add(hopperItemModeButton);

        hopperXpModeButton = new RightTabButton(
            ID_HOPPER_XP_MODE,
            baseX,
            topY + step * 3,
            23,
            26,
            baseX + 3,
            topY + step * 3 + 4,
            16,
            16,
            button -> {
                hopperXpModeButton.toggleState();
                if (menu.be != null) {
                    menu.be.hopperXpMode = (HopperXpMode) hopperXpModeButton.currentState;
                    menu.writeAndSendQuickData();
                }
            }) {

            @Override
            protected void initButton() {
                iconMap.put(
                    HopperXpMode.DENY,
                    new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/hopper_xp_mode_deny.png"));
                iconMap.put(
                    HopperXpMode.ALLOW,
                    new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/hopper_xp_mode_allow.png"));
                tooltipMap.put(HopperXpMode.DENY, "tooltip.button.beyonddimensions.hopper_xp_mode_deny");
                tooltipMap.put(HopperXpMode.ALLOW, "tooltip.button.beyonddimensions.hopper_xp_mode_allow");
                for (Enum<?> state : iconMap.keySet()) {
                    this.states.add(state);
                }
                setState(menu.be != null ? menu.be.hopperXpMode : HopperXpMode.DENY);
            }
        };
        this.buttonList.add(hopperXpModeButton);

        hopperNBTModeButton = new RightTabButton(
            ID_HOPPER_NBT_MODE,
            baseX,
            topY + step * 4,
            23,
            26,
            baseX + 3,
            topY + step * 4 + 4,
            16,
            16,
            button -> {
                hopperNBTModeButton.toggleState();
                if (menu.be != null) {
                    menu.be.hopperNBTMode = (HopperNBTMode) hopperNBTModeButton.currentState;
                    menu.writeAndSendQuickData();
                }
            }) {

            @Override
            protected void initButton() {
                iconMap.put(
                    HopperNBTMode.DENY,
                    new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/hopper_nbt_mode_deny.png"));
                iconMap.put(
                    HopperNBTMode.ALLOW,
                    new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/hopper_nbt_mode_allow.png"));
                tooltipMap.put(HopperNBTMode.DENY, "tooltip.button.beyonddimensions.hopper_nbt_mode_deny");
                tooltipMap.put(HopperNBTMode.ALLOW, "tooltip.button.beyonddimensions.hopper_nbt_mode_allow");
                for (Enum<?> state : iconMap.keySet()) {
                    this.states.add(state);
                }
                setState(menu.be != null ? menu.be.hopperNBTMode : HopperNBTMode.DENY);
            }
        };
        this.buttonList.add(hopperNBTModeButton);

        hopperFluidModeButton = new RightTabButton(
            ID_HOPPER_FLUID_MODE,
            baseX,
            topY + step * 5,
            23,
            26,
            baseX + 3,
            topY + step * 5 + 4,
            16,
            16,
            button -> {
                hopperFluidModeButton.toggleState();
                if (menu.be != null) {
                    menu.be.hopperFluidMode = (HopperFluidMode) hopperFluidModeButton.currentState;
                    menu.writeAndSendQuickData();
                }
            }) {

            @Override
            protected void initButton() {
                iconMap.put(
                    HopperFluidMode.DENY,
                    new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/hopper_fluid_mode_deny.png"));
                iconMap.put(
                    HopperFluidMode.ALLOW,
                    new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/hopper_fluid_mode_allow.png"));
                tooltipMap.put(HopperFluidMode.DENY, "tooltip.button.beyonddimensions.hopper_fluid_mode_deny");
                tooltipMap.put(HopperFluidMode.ALLOW, "tooltip.button.beyonddimensions.hopper_fluid_mode_allow");
                for (Enum<?> state : iconMap.keySet()) {
                    this.states.add(state);
                }
                setState(menu.be != null ? menu.be.hopperFluidMode : HopperFluidMode.DENY);
            }
        };
        this.buttonList.add(hopperFluidModeButton);

        hopperRangeModeButton = new LeftTabButton(
            ID_HOPPER_RANGE_MODE,
            leftX,
            topY + step * 5,
            23,
            26,
            this.guiLeft - 18,
            topY + step * 5 + 4,
            16,
            16,
            button -> {
                hopperRangeModeButton.toggleState();
                if (menu.be != null) {
                    menu.be.hopperRangeMode = (HopperRangeMode) hopperRangeModeButton.currentState;
                    menu.writeAndSendQuickData();
                }
            }) {

            @Override
            protected void initButton() {
                iconMap.put(
                    HopperRangeMode.RADIUS_LOWEST,
                    new ResourceLocation(
                        BDConstants.MODID,
                        "textures/gui/sprites/widget/hopper_range_mode_lowest.png"));
                iconMap.put(
                    HopperRangeMode.RADIUS_LOW,
                    new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/hopper_range_mode_low.png"));
                iconMap.put(
                    HopperRangeMode.RADIUS_MID,
                    new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/hopper_range_mode_mid.png"));
                iconMap.put(
                    HopperRangeMode.RADIUS_HIGH,
                    new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/hopper_range_mode_high.png"));
                iconMap.put(
                    HopperRangeMode.RADIUS_HIGHEST,
                    new ResourceLocation(
                        BDConstants.MODID,
                        "textures/gui/sprites/widget/hopper_range_mode_highest.png"));
                iconMap.put(
                    HopperRangeMode.CHUNK_MODE,
                    new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/hopper_range_mode_chunk.png"));
                tooltipMap
                    .put(HopperRangeMode.RADIUS_LOWEST, "tooltip.button.beyonddimensions.hopper_range_mode_lowest");
                tooltipMap.put(HopperRangeMode.RADIUS_LOW, "tooltip.button.beyonddimensions.hopper_range_mode_low");
                tooltipMap.put(HopperRangeMode.RADIUS_MID, "tooltip.button.beyonddimensions.hopper_range_mode_mid");
                tooltipMap.put(HopperRangeMode.RADIUS_HIGH, "tooltip.button.beyonddimensions.hopper_range_mode_high");
                tooltipMap
                    .put(HopperRangeMode.RADIUS_HIGHEST, "tooltip.button.beyonddimensions.hopper_range_mode_highest");
                tooltipMap.put(HopperRangeMode.CHUNK_MODE, "tooltip.button.beyonddimensions.hopper_range_mode_chunk");
                for (Enum<?> state : iconMap.keySet()) {
                    this.states.add(state);
                }
                setState(menu.be != null ? menu.be.hopperRangeMode : HopperRangeMode.RADIUS_MID);
            }
        };
        this.buttonList.add(hopperRangeModeButton);
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
        if (hopperItemModeButton.currentState != menu.be.hopperItemMode)
            hopperItemModeButton.setState(menu.be.hopperItemMode);
        if (hopperXpModeButton.currentState != menu.be.hopperXpMode) hopperXpModeButton.setState(menu.be.hopperXpMode);
        if (hopperNBTModeButton.currentState != menu.be.hopperNBTMode)
            hopperNBTModeButton.setState(menu.be.hopperNBTMode);
        if (hopperFluidModeButton.currentState != menu.be.hopperFluidMode)
            hopperFluidModeButton.setState(menu.be.hopperFluidMode);
        if (hopperRangeModeButton.currentState != menu.be.hopperRangeMode)
            hopperRangeModeButton.setState(menu.be.hopperRangeMode);
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
            StatCollector.translateToLocal("tile.beyonddimensions.net_hopper_block.name"),
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
