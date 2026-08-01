package com.wintercogs.beyonddimensions.client.gui;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import com.wintercogs.beyonddimensions.api.ids.BDConstants;
import com.wintercogs.beyonddimensions.client.gui.widget.shared.RightTabButton;
import com.wintercogs.beyonddimensions.common.block.entity.BaseNetFurnaceBlockEntity;
import com.wintercogs.beyonddimensions.common.machine.AutoSortMode;
import com.wintercogs.beyonddimensions.common.machine.PopMode;
import com.wintercogs.beyonddimensions.common.machine.ReceiveMode;
import com.wintercogs.beyonddimensions.common.machine.RedStoneControlMode;
import com.wintercogs.beyonddimensions.common.menu.NetFurnaceMenu;
import com.wintercogs.beyonddimensions.util.GuiRenderHelper;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 网络熔炉界面 GUI（1.7.10 移植版）。
 * <p>
 * 显示熔炼进度与燃料进度。
 * <p>
 * 状态按钮：弹出模式、接收模式、红石控制模式、自动整理模式。
 * 按钮点击直接写回 menu.be 字段并调用 writeAndSendQuickData() 同步服务端
 * （客户端菜单持有效 be 引用，由 BDGuiHandler 传入）。
 */
@SideOnly(Side.CLIENT)
public class GuiNetFurnace extends GuiBase {

    private static final int ID_POP_MODE = 100;
    private static final int ID_RECEIVE_MODE = 101;
    private static final int ID_CONTROL_MODE = 102;
    private static final int ID_SORT_MODE = 103;

    protected final NetFurnaceMenu menu;

    protected RightTabButton popModeButton;
    protected RightTabButton receiveModeButton;
    protected RightTabButton controlModeButton;
    protected RightTabButton sortModeButton;

    public GuiNetFurnace(InventoryPlayer inventory) {
        this(inventory, null);
    }

    public GuiNetFurnace(InventoryPlayer inventory, BaseNetFurnaceBlockEntity te) {
        super(new NetFurnaceMenu(inventory, te));
        this.menu = (NetFurnaceMenu) this.inventorySlots;
        this.xSize = 230;
        this.ySize = 210;
    }

    @Override
    public void initGui() {
        super.initGui();
        initWidgets();
    }

    /** 初始化状态按钮（占位：客户端 be 为 null，仅本地视觉切换） */
    protected void initWidgets() {
        int baseX = this.guiLeft + this.xSize;
        int topY = this.guiTop + 6;
        int step = 30;

        popModeButton = new RightTabButton(ID_POP_MODE, baseX, topY, 23, 26, baseX + 3, topY + 4, 16, 16, button -> {
            popModeButton.toggleState();
            // 三段式：切换状态 → 写入数据源 → 同步服务端（对齐源项目 NetFurnaceGUI / #42 模式）
            if (menu.be != null) {
                menu.be.popMode = (PopMode) popModeButton.currentState;
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
                setState(menu.be != null ? menu.be.popMode : PopMode.STOP);
            }
        };
        this.buttonList.add(popModeButton);

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
                if (menu.be != null) {
                    menu.be.receiveMode = (ReceiveMode) receiveModeButton.currentState;
                    menu.writeAndSendQuickData();
                }
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
                setState(menu.be != null ? menu.be.receiveMode : ReceiveMode.STOP);
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

        sortModeButton = new RightTabButton(
            ID_SORT_MODE,
            baseX,
            topY + step * 3,
            23,
            26,
            baseX + 3,
            topY + step * 3 + 4,
            16,
            16,
            button -> {
                sortModeButton.toggleState();
                if (menu.be != null) {
                    menu.be.sortMode = (AutoSortMode) sortModeButton.currentState;
                    menu.writeAndSendQuickData();
                }
            }) {

            @Override
            protected void initButton() {
                iconMap.put(
                    AutoSortMode.OPEN,
                    new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/sort_mode_open.png"));
                iconMap.put(
                    AutoSortMode.STOP,
                    new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/sort_mode_stop.png"));
                tooltipMap.put(AutoSortMode.OPEN, "tooltip.button.beyonddimensions.sort_mode_open");
                tooltipMap.put(AutoSortMode.STOP, "tooltip.button.beyonddimensions.sort_mode_stop");
                for (Enum<?> state : iconMap.keySet()) {
                    this.states.add(state);
                }
                setState(menu.be != null ? menu.be.sortMode : AutoSortMode.OPEN);
            }
        };
        this.buttonList.add(sortModeButton);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        syncButtonStates();
    }

    /** 从 menu.be 同步按钮状态（客户端 be 为 null 时保持默认状态） */
    protected void syncButtonStates() {
        if (menu.be == null) return;
        if (popModeButton.currentState != menu.be.popMode) popModeButton.setState(menu.be.popMode);
        if (receiveModeButton.currentState != menu.be.receiveMode) receiveModeButton.setState(menu.be.receiveMode);
        if (controlModeButton.currentState != menu.be.controlMode) controlModeButton.setState(menu.be.controlMode);
        if (sortModeButton.currentState != menu.be.sortMode) sortModeButton.setState(menu.be.sortMode);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GuiRenderHelper.resetColor();
        // 绘制熔炉背景
        CommonTexturesRender.renderFullTexture(
            CommonTextures.NET_FURNACE_BACKGROUND,
            this.guiLeft,
            this.guiTop,
            CommonTextures.NET_FURNACE_BACKGROUND_WIDTH,
            CommonTextures.NET_FURNACE_BACKGROUND_HEIGHT,
            CommonTextures.NET_FURNACE_BACKGROUND_WIDTH,
            CommonTextures.NET_FURNACE_BACKGROUND_HEIGHT);

        // 绘制熔炼进度（从上往下填充）
        if (menu.be != null) {
            for (int i = 0; i < menu.be.getCapacity(); i++) {
                int cookTotal = menu.be.getCookTimeTotal()
                    .get(i);
                if (cookTotal <= 0) continue;
                float progress = (float) menu.be.getCookTime()
                    .get(i) / (float) cookTotal;
                CommonTexturesRender.renderWorkDoneV_AsProgress(
                    this.guiLeft + 32 + i * 19,
                    this.guiTop + 61,
                    CommonTextures.WORK_DONE_V_WIDTH,
                    CommonTextures.WORK_DONE_V_HEIGHT,
                    progress);
            }
            // 绘制燃料进度（从下往上填充）
            for (int i = 0; i < menu.be.getCapacity(); i++) {
                int litDuration = menu.be.getLitDuration()
                    .get(i);
                if (litDuration <= 0) continue;
                float progress = (float) menu.be.getLitTime()
                    .get(i) / (float) litDuration;
                CommonTexturesRender.renderFurnaceWorkV_AsProgress(
                    this.guiLeft + 31 + i * 19,
                    this.guiTop + 109,
                    CommonTextures.FURNACE_WORK_V_WIDTH,
                    CommonTextures.FURNACE_WORK_V_HEIGHT,
                    progress);
            }
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        this.fontRendererObj
            .drawString(StatCollector.translateToLocal("gui.beyonddimensions.net_furnace"), 8, 8, 4210752);
        this.fontRendererObj.drawString(
            StatCollector.translateToLocal("menu.label.beyonddimensions.input_filter_slots"),
            6,
            27,
            4210752);
        GuiRenderHelper.drawRightAnchoredText(
            this.fontRendererObj,
            StatCollector.translateToLocal("menu.label.beyonddimensions.fuel_filter_slots"),
            224,
            27,
            4210752,
            false);
        this.fontRendererObj.drawString(StatCollector.translateToLocal("container.inventory"), 6, 190, 4210752);
        // 标记槽/存储槽中的非物品条目（流体/能量）覆盖层：
        // 原版 drawSlot 只能渲染 ItemStack，燃料槽的杂酚油等流体标记需经 IStackRender 叠加绘制
        // （实现已上移到 GuiBase.drawTypedSlotOverlays，本类直接继承）
        drawTypedSlotOverlays();
    }
}
