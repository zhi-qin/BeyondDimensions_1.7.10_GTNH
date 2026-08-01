package com.wintercogs.beyonddimensions.client.gui;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import com.wintercogs.beyonddimensions.Config;
import com.wintercogs.beyonddimensions.api.ButtonState;
import com.wintercogs.beyonddimensions.api.ids.BDConstants;
import com.wintercogs.beyonddimensions.client.gui.widget.shared.IconButton;
import com.wintercogs.beyonddimensions.client.gui.widget.shared.StatusButton;
import com.wintercogs.beyonddimensions.common.init.BDPackets;
import com.wintercogs.beyonddimensions.common.menu.DimensionsCraftMenu;
import com.wintercogs.beyonddimensions.config.CommonConfigRuntime;
import com.wintercogs.beyonddimensions.network.packet.c2s.ClickTransferCraftButtonPacket;
import com.wintercogs.beyonddimensions.util.GuiRenderHelper;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 维度网络合成界面 GUI（1.7.10 移植版）。
 * <p>
 * 在 DimensionsNet 基础上增加工艺槽（craft_slots）区域与对应的转移按钮、优先转移切换按钮。
 */
@SideOnly(Side.CLIENT)
public class GuiDimensionsCraft extends GuiDimensionsNet {

    private static final int ID_TRANSFER_CRAFT_TO_INV_BUTTON = 110;
    private static final int ID_TRANSFER_CRAFT_TO_STORAGE_BUTTON = 111;
    private static final int ID_CRAFT_RETURN_BUTTON = 112;

    protected IconButton transferCraftToInvButton;
    protected IconButton transferCraftToStorageButton;
    protected StatusButton craftReturnButton;

    public GuiDimensionsCraft(InventoryPlayer inventory) {
        this(new DimensionsCraftMenu(inventory));
    }

    public GuiDimensionsCraft(InventoryPlayer inventory,
        com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler data,
        net.minecraft.item.ItemStack[] craftItems) {
        this(new DimensionsCraftMenu(inventory, data, craftItems));
    }

    /**
     * 子类专用构造函数：允许传入 {@link DimensionsCraftMenu} 的子类实例
     * （如 {@link com.wintercogs.beyonddimensions.common.menu.DimensionsCraftMenuTerminal}）。
     */
    protected GuiDimensionsCraft(DimensionsCraftMenu menu) {
        super(menu);
        this.xSize = 194;
        this.ySize = rebuildImageHeight();
    }

    @Override
    protected int rebuildImageHeight() {
        return CommonTextures.TOP_BASE_HEIGHT + CommonTextures.TOP_SLOTS_HEIGHT
            + (menu.getLines() - 2) * CommonTextures.MID_SLOTS_HEIGHT
            + CommonTextures.BOTTOM_SLOTS_HEIGHT
            + CommonTextures.CRAFT_SLOTS_HEIGHT
            + CommonTextures.PLAYER_INV_HEIGHT;
    }

    /** 合成界面不绘制终端 EU 池能量条（菜单未持有网络引用，无同步数据）。 */
    @Override
    protected boolean shouldDrawEuBar() {
        return false;
    }

    @Override
    protected int calMaxLines() {
        return (int) ((this.height - 36
            - (CommonTextures.TOP_BASE_HEIGHT + CommonTextures.TOP_SLOTS_HEIGHT
                + CommonTextures.BOTTOM_SLOTS_HEIGHT
                + CommonTextures.CRAFT_SLOTS_HEIGHT
                + CommonTextures.PLAYER_INV_HEIGHT))
            / (float) CommonTextures.MID_SLOTS_HEIGHT + 2);
    }

    @Override
    protected void initWidgets() {
        super.initWidgets();

        int buttonY = this.guiTop + CommonTextures.TOP_BASE_HEIGHT + menu.getLines() * 18 + 10;

        // 工艺槽 → 背包 转移按钮
        transferCraftToInvButton = new IconButton(
            ID_TRANSFER_CRAFT_TO_INV_BUTTON,
            this.guiLeft + 90,
            buttonY,
            8,
            8,
            new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/down_arrow.png"),
            button -> BDPackets.INSTANCE.sendToServer(new ClickTransferCraftButtonPacket(false)));
        this.buttonList.add(transferCraftToInvButton);

        // 工艺槽 → 仓库 转移按钮
        transferCraftToStorageButton = new IconButton(
            ID_TRANSFER_CRAFT_TO_STORAGE_BUTTON,
            this.guiLeft + 81,
            buttonY,
            8,
            8,
            new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/up_arrow.png"),
            button -> BDPackets.INSTANCE.sendToServer(new ClickTransferCraftButtonPacket(true)));
        this.buttonList.add(transferCraftToStorageButton);

        // 优先转移切换按钮
        menu.writeAndSendQuickData();
        craftReturnButton = new StatusButton(ID_CRAFT_RETURN_BUTTON, this.guiLeft + 99, buttonY, 8, 8, button -> {
            craftReturnButton.toggleState();
            CommonConfigRuntime.uiCraftReturnButton = (ButtonState) craftReturnButton.currentState;
            Config.setUiString(
                "ui_craft_return_button",
                CommonConfigRuntime.uiCraftReturnButton.name(),
                ButtonState.DISABLED.name(),
                "决定工艺菜单关闭时，物品优先转移的方向；启用则优先向存储，关闭则优先向背包");
            menu.writeAndSendQuickData();
        }) {

            @Override
            protected void initButton() {
                iconMap.put(
                    ButtonState.ENABLED,
                    new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/sort_asc.png"));
                iconMap.put(
                    ButtonState.DISABLED,
                    new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/sort_desc.png"));

                tooltipMap.put(ButtonState.ENABLED, "tooltip.button.beyonddimensions.first_storage");
                tooltipMap.put(ButtonState.DISABLED, "tooltip.button.beyonddimensions.first_inv");

                for (Enum<?> state : iconMap.keySet()) {
                    this.states.add(state);
                }
                setState(CommonConfigRuntime.uiCraftReturnButton);
            }
        };
        this.buttonList.add(craftReturnButton);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int drawY = this.guiTop;
        GuiRenderHelper.resetColor();

        GuiRenderHelper.renderFullTexture(
            CommonTextures.TOP_BASE,
            this.guiLeft,
            drawY,
            CommonTextures.TOP_BASE_WIDTH,
            CommonTextures.TOP_BASE_HEIGHT,
            CommonTextures.TOP_BASE_WIDTH,
            CommonTextures.TOP_BASE_HEIGHT);
        drawY += CommonTextures.TOP_BASE_HEIGHT;

        GuiRenderHelper.renderFullTexture(
            CommonTextures.TOP_SLOTS,
            this.guiLeft,
            drawY,
            CommonTextures.TOP_SLOTS_WIDTH,
            CommonTextures.TOP_SLOTS_HEIGHT,
            CommonTextures.TOP_SLOTS_WIDTH,
            CommonTextures.TOP_SLOTS_HEIGHT);
        drawY += CommonTextures.TOP_SLOTS_HEIGHT;

        for (int i = 0; i < menu.getLines() - 2; i++) {
            GuiRenderHelper.renderFullTexture(
                CommonTextures.MID_SLOTS,
                this.guiLeft,
                drawY,
                CommonTextures.MID_SLOTS_WIDTH,
                CommonTextures.MID_SLOTS_HEIGHT,
                CommonTextures.MID_SLOTS_WIDTH,
                CommonTextures.MID_SLOTS_HEIGHT);
            drawY += CommonTextures.MID_SLOTS_HEIGHT;
        }

        GuiRenderHelper.renderFullTexture(
            CommonTextures.BOTTOM_SLOTS,
            this.guiLeft,
            drawY,
            CommonTextures.BOTTOM_SLOTS_WIDTH,
            CommonTextures.BOTTOM_SLOTS_HEIGHT,
            CommonTextures.BOTTOM_SLOTS_WIDTH,
            CommonTextures.BOTTOM_SLOTS_HEIGHT);
        drawY += CommonTextures.BOTTOM_SLOTS_HEIGHT;

        GuiRenderHelper.renderFullTexture(
            CommonTextures.CRAFT_SLOTS,
            this.guiLeft,
            drawY,
            CommonTextures.CRAFT_SLOTS_WIDTH,
            CommonTextures.CRAFT_SLOTS_HEIGHT,
            CommonTextures.CRAFT_SLOTS_WIDTH,
            CommonTextures.CRAFT_SLOTS_HEIGHT);
        drawY += CommonTextures.CRAFT_SLOTS_HEIGHT;

        GuiRenderHelper.renderFullTexture(
            CommonTextures.GUI_TEXTURE_PLAYER_INV,
            this.guiLeft,
            drawY,
            CommonTextures.PLAYER_INV_WIDTH,
            CommonTextures.PLAYER_INV_HEIGHT,
            CommonTextures.PLAYER_INV_WIDTH,
            CommonTextures.PLAYER_INV_HEIGHT);

        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);

        // 文本输入框（搜索框）置于背景层，避免遮挡标题与 NEI 提示
        drawTextFields();
        // 滚动条置于背景层，避免滑块遮挡 NEI 提示
        drawScrollBars();
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        int titleLabelY = 8;
        int inventoryLabelY = CommonTextures.TOP_BASE_HEIGHT + menu.getLines() * 18
            + 5
            + CommonTextures.CRAFT_SLOTS_HEIGHT;
        this.fontRendererObj.drawString(
            StatCollector.translateToLocal("gui.beyonddimensions.dimensions_craft"),
            8,
            titleLabelY,
            4210752);
        this.fontRendererObj
            .drawString(StatCollector.translateToLocal("container.inventory"), 8, inventoryLabelY, 4210752);

        // 本类覆写前景层且不调 super（标题布局与父类不同），
        // 必须显式补调非物品条目（能量/流体）覆盖层，否则终端界面看不到网络能量
        drawTypedSlotOverlays();
    }
}
