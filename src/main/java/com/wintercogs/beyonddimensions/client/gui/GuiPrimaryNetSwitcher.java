package com.wintercogs.beyonddimensions.client.gui;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.NetPermissionLevel;
import com.wintercogs.beyonddimensions.api.dimensionnet.PrimaryNetOption;
import com.wintercogs.beyonddimensions.api.dimensionnet.PrimaryNetSwitchAction;
import com.wintercogs.beyonddimensions.api.ids.BDConstants;
import com.wintercogs.beyonddimensions.client.gui.widget.scroller.BigScroller;
import com.wintercogs.beyonddimensions.client.gui.widget.shared.IconButton;
import com.wintercogs.beyonddimensions.common.init.BDPackets;
import com.wintercogs.beyonddimensions.common.menu.PrimaryNetSwitcherMenu;
import com.wintercogs.beyonddimensions.network.packet.c2s.OpenNetGuiPacket;
import com.wintercogs.beyonddimensions.network.packet.c2s.PrimaryNetSwitchActionPacket;
import com.wintercogs.beyonddimensions.network.packet.c2s.RenameNetPacket;
import com.wintercogs.beyonddimensions.util.GuiRenderHelper;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 主网络切换器界面 GUI（1.7.10 移植版）。
 * <p>
 * 显示当前主网络、最近切换、所有可访问网络列表。
 * <p>
 * 控件：搜索框（GuiTextField）、最近网络按钮、选项按钮列表、滚动条（BigScroller）、重命名输入框。
 * 1.7.10 适配：EditBox → GuiTextField（无 responder，轮询文本变化）；
 * Button → GuiButton（自定义子类）；Component → StatCollector；
 * 右键重命名通过 mouseClicked button==1 处理。
 */
@SideOnly(Side.CLIENT)
public class GuiPrimaryNetSwitcher extends GuiBase {

    private static final ResourceLocation BACKGROUND = new ResourceLocation(
        BDConstants.MODID,
        "textures/gui/primary_net_switcher_gui.png");
    private static final int BACKGROUND_WIDTH = 176;
    private static final int BACKGROUND_HEIGHT = 239;
    private static final int RECENT_BUTTON_COUNT = 3;
    private static final int VISIBLE_OPTION_COUNT = 6;
    private static final int OPTION_BUTTON_WIDTH = 140;
    private static final int OPTION_BUTTON_HEIGHT = 20;
    private static final LinkedList<Integer> RECENT_PRIMARY_NET_IDS = new LinkedList<>();

    private static final int ID_CLEAR_PRIMARY = 100;
    private static final int ID_DIMENSIONS_NET = 101;
    private static final int ID_RECENT_BASE = 102;

    protected final PrimaryNetSwitcherMenu menu;

    private GuiTextField searchField;
    private GuiTextField renameField;
    private IconButton dimensionsNetButton;
    private GuiButton clearPrimaryButton;
    private final List<RecentNetButton> recentButtons = new ArrayList<>();
    private final List<PrimaryNetOptionButton> optionButtons = new ArrayList<>();
    private BigScroller scroller;

    private List<PrimaryNetOption> filteredOptions = new ArrayList<>();
    private List<PrimaryNetOption> lastObservedOptions = new ArrayList<>();
    private int topIndex;
    private int selectedIndex = -1;
    private int renamingNetId = DimensionsNet.NO_PRIMARY_NET_ID;
    private int lastObservedPrimaryNetId = DimensionsNet.NO_PRIMARY_NET_ID;
    private String lastSearchText = "";

    // 搜索框位置（GuiTextField 的 xPosition/yPosition 不可外部访问，自行跟踪）
    private int searchFieldX;
    private int searchFieldY;
    private int searchFieldWidth;
    private int searchFieldHeight;

    // 重命名输入框的边界（GuiTextField 的 xPosition/yPosition 不可外部访问，自行跟踪）
    private int renameFieldX;
    private int renameFieldY;
    private int renameFieldW;
    private int renameFieldH;

    public GuiPrimaryNetSwitcher(InventoryPlayer inventory) {
        super(new PrimaryNetSwitcherMenu(inventory));
        this.menu = (PrimaryNetSwitcherMenu) this.inventorySlots;
        this.xSize = BACKGROUND_WIDTH;
        this.ySize = BACKGROUND_HEIGHT;
    }

    @Override
    public void initGui() {
        super.initGui();
        initWidgets();
    }

    /** 初始化搜索框、按钮、选项列表、滚动条 */
    protected void initWidgets() {
        // 搜索框
        searchFieldX = this.guiLeft + 8;
        searchFieldY = this.guiTop + 20;
        searchFieldWidth = 160;
        searchFieldHeight = this.fontRendererObj.FONT_HEIGHT + 6;
        searchField = new GuiTextField(
            this.fontRendererObj,
            searchFieldX,
            searchFieldY,
            searchFieldWidth,
            searchFieldHeight);
        searchField.setMaxStringLength(100);
        searchField.setEnableBackgroundDrawing(true);
        searchField.setVisible(true);
        searchField.setTextColor(0xFFFFFFFF);
        lastSearchText = searchField.getText();
        this.textFields.add(searchField);

        // 打开网络合成菜单的图标按钮
        int dnX = this.guiLeft + 152;
        int dnY = this.guiTop + 4;
        dimensionsNetButton = new IconButton(
            ID_DIMENSIONS_NET,
            dnX,
            dnY,
            16,
            16,
            new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/opposite_arrow.png"),
            button -> {
                if (menu.currentPrimaryNetId == DimensionsNet.NO_PRIMARY_NET_ID) return;
                BDPackets.INSTANCE.sendToServer(
                    new OpenNetGuiPacket(
                        menu.player.getUniqueID()
                            .toString(),
                        OpenNetGuiPacket.NET_CRAFT_MENU));
            });
        dimensionsNetButton.enabled = menu.currentPrimaryNetId != DimensionsNet.NO_PRIMARY_NET_ID;
        this.buttonList.add(dimensionsNetButton);

        // 清除主网络按钮
        clearPrimaryButton = new GuiButton(
            ID_CLEAR_PRIMARY,
            this.guiLeft + 8,
            this.guiTop + 47,
            160,
            20,
            StatCollector.translateToLocal("menu.button.beyonddimensions.primary_net_switcher.clear"));
        this.buttonList.add(clearPrimaryButton);

        // 最近网络按钮
        int recentButtonY = this.guiTop + 78;
        for (int i = 0; i < RECENT_BUTTON_COUNT; i++) {
            RecentNetButton recentButton = new RecentNetButton(
                ID_RECENT_BASE + i,
                this.guiLeft + 8 + i * 54,
                recentButtonY,
                50,
                16);
            recentButton.visible = false;
            recentButton.enabled = false;
            recentButtons.add(recentButton);
            this.buttonList.add(recentButton);
        }

        // 网络选项按钮
        int optionStartY = this.guiTop + 107;
        for (int i = 0; i < VISIBLE_OPTION_COUNT; i++) {
            PrimaryNetOptionButton optionButton = new PrimaryNetOptionButton(
                this.guiLeft + 8,
                optionStartY + i * OPTION_BUTTON_HEIGHT,
                OPTION_BUTTON_WIDTH,
                OPTION_BUTTON_HEIGHT);
            optionButtons.add(optionButton);
            this.buttonList.add(optionButton);
        }

        // 滚动条
        scroller = new BigScroller(
            this.guiLeft + 160,
            optionStartY + 2,
            VISIBLE_OPTION_COUNT * OPTION_BUTTON_HEIGHT - 17,
            0,
            0,
            pos -> {
                if (topIndex != pos) {
                    topIndex = pos;
                    // 滚动时取消重命名（1.7.10 GuiTextField 无法重新定位）
                    cancelRename();
                    syncOptionButtons();
                }
            });
        scroller.setStep(1);
        // 滚轮生效区域 = 网络选项列表区（x=guiLeft+8, y=optionStartY, 140×120），加滚动条轨道本身。
        // 鼠标在 GUI 外（如 NEI 收藏栏）滚轮不再被吞（BUGFIX_RECORD #100）。
        scroller.setWheelRegion(
            this.guiLeft + 8,
            optionStartY,
            OPTION_BUTTON_WIDTH,
            VISIBLE_OPTION_COUNT * OPTION_BUTTON_HEIGHT);
        this.scrollBars.add(scroller);

        lastObservedPrimaryNetId = menu.currentPrimaryNetId;
        lastObservedOptions = menu.options;
        rebuildFilteredOptions();
    }

    @Override
    public void updateScreen() {
        super.updateScreen();

        // 检测搜索框文本变化（1.7.10 GuiTextField 无 responder，需轮询）
        String currentSearch = searchField.getText();
        if (!currentSearch.equals(lastSearchText)) {
            lastSearchText = currentSearch;
            rebuildFilteredOptions();
        }

        // 检测菜单数据变化
        if (menu.options != lastObservedOptions) {
            lastObservedOptions = menu.options;
            rebuildFilteredOptions();
        }

        if (menu.currentPrimaryNetId != lastObservedPrimaryNetId) {
            lastObservedPrimaryNetId = menu.currentPrimaryNetId;
            rememberRecentNet(menu.currentPrimaryNetId);
        }

        syncRecentButtons();
        syncOptionButtons();

        if (dimensionsNetButton != null) {
            dimensionsNetButton.enabled = menu.currentPrimaryNetId != DimensionsNet.NO_PRIMARY_NET_ID;
        }

        // 重命名输入框光标更新
        if (renameField != null) {
            renameField.updateCursorCounter();
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GuiRenderHelper.resetColor();
        GuiRenderHelper.blit(
            BACKGROUND,
            this.guiLeft,
            this.guiTop,
            BACKGROUND_WIDTH,
            BACKGROUND_HEIGHT,
            0,
            0,
            BACKGROUND_WIDTH,
            BACKGROUND_HEIGHT,
            BACKGROUND_WIDTH,
            BACKGROUND_HEIGHT);

        // 文本输入框（搜索框）置于背景层，避免遮挡标题与 NEI 提示
        drawTextFields();
        // 滚动条置于背景层，避免滑块遮挡 NEI 提示
        drawScrollBars();
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        int titleLabelY = 8;
        this.fontRendererObj.drawString(
            StatCollector.translateToLocal("gui.beyonddimensions.primary_net_switcher"),
            8,
            titleLabelY,
            4210752);
        this.fontRendererObj.drawString(
            StatCollector.translateToLocal("menu.text.beyonddimensions.primary_net_switcher.current") + " "
                + describeCurrentPrimary(),
            8,
            37,
            4210752);
        this.fontRendererObj.drawString(
            StatCollector.translateToLocal("menu.label.beyonddimensions.primary_net_switcher.recent"),
            8,
            69,
            4210752);
        this.fontRendererObj.drawString(
            StatCollector.translateToLocal("menu.label.beyonddimensions.primary_net_switcher.all_networks"),
            8,
            96,
            4210752);
        this.fontRendererObj
            .drawString(StatCollector.translateToLocal("container.inventory"), 8, BACKGROUND_HEIGHT - 94, 4210752);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);

        // 搜索框占位提示文本（空且未聚焦时显示）
        if (searchField.getText()
            .isEmpty() && !searchField.isFocused()) {
            String placeholder = StatCollector
                .translateToLocal("menu.label.beyonddimensions.primary_net_switcher.search");
            this.fontRendererObj.drawStringWithShadow(
                placeholder,
                searchFieldX + 4,
                searchFieldY + (searchFieldHeight - this.fontRendererObj.FONT_HEIGHT) / 2,
                0xFF808080);
        }

        // 绘制重命名输入框（不在 textFields 中，需手动绘制）
        if (renameField != null) {
            renameField.drawTextBox();
        }

        // 绘制选项按钮 tooltip
        drawOptionTooltips(mouseX, mouseY);
    }

    /** 绘制选项按钮的悬停 tooltip */
    private void drawOptionTooltips(int mouseX, int mouseY) {
        for (PrimaryNetOptionButton optionButton : optionButtons) {
            if (optionButton.visible && optionButton.option != null && optionButton.hovered) {
                String tooltip = StatCollector
                    .translateToLocal("tooltip.button.beyonddimensions.primary_net_switcher.option")
                    .replace("%s", String.valueOf(optionButton.option.netId()))
                    .replace("%s", buildPermissionLabel(optionButton.option.permission()));
                List<String> lines = new ArrayList<>();
                lines.add(tooltip);
                this.drawHoveringText(lines, mouseX, mouseY, this.fontRendererObj);
                return;
            }
        }

        for (RecentNetButton recentButton : recentButtons) {
            if (recentButton.visible && recentButton.hovered && recentButton.targetNetId >= 0) {
                String tooltip = StatCollector
                    .translateToLocal("tooltip.button.beyonddimensions.primary_net_switcher.recent")
                    .replace("%s", String.valueOf(recentButton.targetNetId));
                List<String> lines = new ArrayList<>();
                lines.add(tooltip);
                this.drawHoveringText(lines, mouseX, mouseY, this.fontRendererObj);
                return;
            }
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        // 重命名模式：仅允许点击重命名输入框，其他位置取消重命名
        if (renameField != null) {
            renameField.mouseClicked(mouseX, mouseY, button);
            if (!isClickInRenameField(mouseX, mouseY)) {
                cancelRename();
            }
            return;
        }

        // 右键点击选项按钮 → 打开重命名
        if (button == 1) {
            PrimaryNetOptionButton optionButton = getOptionButtonAt(mouseX, mouseY);
            if (optionButton != null && optionButton.option != null) {
                if (canRename(optionButton.option)) {
                    startRename(
                        optionButton.option,
                        optionButton.optionIndex,
                        optionButton.xPosition,
                        optionButton.yPosition,
                        optionButton.width,
                        optionButton.height);
                }
                return;
            }
        }

        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        // 重命名输入框：Enter 提交，ESC 取消
        if (renameField != null && renameField.isFocused()) {
            if (keyCode == 28 || keyCode == 156) { // Enter / KP_Enter
                submitRename();
                return;
            }
            if (keyCode == 1) { // ESC
                cancelRename();
                return;
            }
            renameField.textboxKeyTyped(typedChar, keyCode);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == ID_CLEAR_PRIMARY) {
            BDPackets.INSTANCE.sendToServer(new PrimaryNetSwitchActionPacket(PrimaryNetSwitchAction.CLEAR_PRIMARY, -1));
        }
        // 其他按钮（dimensionsNetButton、recentButtons、optionButtons）通过各自回调处理
    }

    /* ---------------------------- 数据同步 ---------------------------- */

    /** 根据搜索文本重建过滤后的选项列表 */
    private void rebuildFilteredOptions() {
        String searchText = searchField == null ? ""
            : searchField.getText()
                .trim()
                .toLowerCase(Locale.ROOT);
        List<PrimaryNetOption> nextFiltered = new ArrayList<>();
        for (PrimaryNetOption option : menu.options) {
            String searchable = buildSearchableText(option);
            if (searchText.isEmpty() || searchable.contains(searchText)) {
                nextFiltered.add(option);
            }
        }

        filteredOptions = nextFiltered;
        if (filteredOptions.isEmpty()) {
            selectedIndex = -1;
            topIndex = 0;
        } else {
            if (selectedIndex >= filteredOptions.size()) {
                selectedIndex = filteredOptions.size() - 1;
            }
            if (selectedIndex >= 0) {
                ensureSelectionVisible();
            }
            topIndex = Math.max(0, Math.min(topIndex, Math.max(0, filteredOptions.size() - VISIBLE_OPTION_COUNT)));
        }

        syncRecentButtons();
        syncOptionButtons();
    }

    /** 同步最近网络按钮 */
    private void syncRecentButtons() {
        List<Integer> recentNetIds = new ArrayList<>();
        for (int recentNetId : RECENT_PRIMARY_NET_IDS) {
            boolean stillExists = false;
            for (PrimaryNetOption option : menu.options) {
                if (option.netId() == recentNetId) {
                    stillExists = true;
                    break;
                }
            }
            if (stillExists) {
                recentNetIds.add(recentNetId);
            }
            if (recentNetIds.size() >= RECENT_BUTTON_COUNT) {
                break;
            }
        }

        for (int i = 0; i < recentButtons.size(); i++) {
            RecentNetButton recentButton = recentButtons.get(i);
            if (i < recentNetIds.size()) {
                recentButton.visible = true;
                recentButton.enabled = true;
                recentButton.load(recentNetIds.get(i));
            } else {
                recentButton.visible = false;
                recentButton.enabled = false;
            }
        }
    }

    /** 同步选项按钮内容与滚动条状态 */
    private void syncOptionButtons() {
        int maxTopIndex = Math.max(0, filteredOptions.size() - VISIBLE_OPTION_COUNT);
        topIndex = Math.max(0, Math.min(topIndex, maxTopIndex));
        scroller.updateScrollPosition(topIndex, maxTopIndex);

        for (int i = 0; i < optionButtons.size(); i++) {
            int optionIndex = topIndex + i;
            PrimaryNetOptionButton optionButton = optionButtons.get(i);
            if (optionIndex < filteredOptions.size()) {
                PrimaryNetOption option = filteredOptions.get(optionIndex);
                optionButton.load(option, optionIndex == selectedIndex, option.netId() == menu.currentPrimaryNetId);
            } else {
                optionButton.clear();
            }
        }
    }

    private void ensureSelectionVisible() {
        if (selectedIndex < 0) return;
        if (selectedIndex < topIndex) {
            topIndex = selectedIndex;
        } else if (selectedIndex >= topIndex + VISIBLE_OPTION_COUNT) {
            topIndex = selectedIndex - VISIBLE_OPTION_COUNT + 1;
        }
    }

    /* ---------------------------- 网络操作 ---------------------------- */

    private void sendSetPrimary(int netId) {
        BDPackets.INSTANCE.sendToServer(new PrimaryNetSwitchActionPacket(PrimaryNetSwitchAction.SET_EXPLICIT, netId));
    }

    private void selectNetAndSend(int netId) {
        if (renameField != null) return;
        for (int i = 0; i < filteredOptions.size(); i++) {
            if (filteredOptions.get(i)
                .netId() == netId) {
                selectedIndex = i;
                ensureSelectionVisible();
                syncOptionButtons();
                break;
            }
        }
        sendSetPrimary(netId);
    }

    /* ---------------------------- 重命名 ---------------------------- */

    private boolean canRename(PrimaryNetOption option) {
        return option != null
            && (option.permission() == NetPermissionLevel.Owner || option.permission() == NetPermissionLevel.Manager);
    }

    private void startRename(PrimaryNetOption option, int optionIndex, int x, int y, int width, int height) {
        cancelRename();
        selectedIndex = optionIndex;
        renamingNetId = option.netId();
        renameFieldX = x;
        renameFieldY = y + 2;
        renameFieldW = width;
        renameFieldH = height - 4;
        renameField = new GuiTextField(this.fontRendererObj, renameFieldX, renameFieldY, renameFieldW, renameFieldH);
        renameField.setMaxStringLength(DimensionsNet.MAX_NETWORK_NAME_LENGTH);
        renameField.setEnableBackgroundDrawing(true);
        renameField.setVisible(true);
        renameField.setTextColor(0xFFFFFFFF);
        renameField.setText(option.customName());
        renameField.setFocused(true);
        syncOptionButtons();
    }

    private void submitRename() {
        if (renameField == null || renamingNetId == DimensionsNet.NO_PRIMARY_NET_ID) return;
        BDPackets.INSTANCE.sendToServer(new RenameNetPacket(renamingNetId, renameField.getText()));
        cancelRename();
    }

    private void cancelRename() {
        if (renameField != null) {
            renameField.setFocused(false);
        }
        renameField = null;
        renamingNetId = DimensionsNet.NO_PRIMARY_NET_ID;
        syncOptionButtons();
    }

    private boolean isClickInRenameField(int mouseX, int mouseY) {
        return mouseX >= renameFieldX && mouseY >= renameFieldY
            && mouseX < renameFieldX + renameFieldW
            && mouseY < renameFieldY + renameFieldH;
    }

    /* ---------------------------- 辅助方法 ---------------------------- */

    private String describeCurrentPrimary() {
        return menu.currentPrimaryNetId == DimensionsNet.NO_PRIMARY_NET_ID
            ? StatCollector.translateToLocal("menu.text.beyonddimensions.primary_net_switcher.none")
            : "#" + menu.currentPrimaryNetId;
    }

    private String buildPermissionLabel(NetPermissionLevel permission) {
        return StatCollector.translateToLocal(
            "menu.text.beyonddimensions.primary_net_switcher.permission." + permission.name()
                .toLowerCase(Locale.ROOT));
    }

    private String buildSearchableText(PrimaryNetOption option) {
        return (option.getNetworkName()
            .getFormattedText() + " (#"
            + option.netId()
            + ") "
            + buildPermissionLabel(option.permission())).toLowerCase(Locale.ROOT);
    }

    private String buildOptionLabel(PrimaryNetOption option, boolean currentPrimary) {
        String name = option.getNetworkName()
            .getFormattedText();
        String permission = buildPermissionLabel(option.permission());
        String suffix = currentPrimary
            ? " " + StatCollector.translateToLocal("menu.text.beyonddimensions.primary_net_switcher.current_suffix")
            : "";
        return name + " (#" + option.netId() + ") " + permission + suffix;
    }

    private static void rememberRecentNet(int netId) {
        if (netId < 0) return;
        RECENT_PRIMARY_NET_IDS.removeIf(existing -> existing == netId);
        RECENT_PRIMARY_NET_IDS.addFirst(netId);
        while (RECENT_PRIMARY_NET_IDS.size() > 8) {
            RECENT_PRIMARY_NET_IDS.removeLast();
        }
    }

    private PrimaryNetOptionButton getOptionButtonAt(int mouseX, int mouseY) {
        for (PrimaryNetOptionButton optionButton : optionButtons) {
            if (optionButton.visible && mouseX >= optionButton.xPosition
                && mouseY >= optionButton.yPosition
                && mouseX < optionButton.xPosition + optionButton.width
                && mouseY < optionButton.yPosition + optionButton.height) {
                return optionButton;
            }
        }
        return null;
    }

    /* ---------------------------- 自定义按钮 ---------------------------- */

    /** 网络选项按钮，点击时切换主网络 */
    private class PrimaryNetOptionButton extends GuiButton {

        private PrimaryNetOption option;
        private int optionIndex = -1;
        private boolean hovered;

        private PrimaryNetOptionButton(int x, int y, int width, int height) {
            super(-1, x, y, width, height, "");
            this.visible = false;
            this.enabled = false;
        }

        private void load(PrimaryNetOption option, boolean selected, boolean currentPrimary) {
            this.option = option;
            this.optionIndex = GuiPrimaryNetSwitcher.this.topIndex + optionButtons.indexOf(this);
            // 重命名中的网络不显示对应按钮
            this.visible = option.netId() != renamingNetId;
            this.enabled = !currentPrimary;
            this.displayString = buildOptionLabel(option, currentPrimary);
        }

        private void clear() {
            this.option = null;
            this.optionIndex = -1;
            this.visible = false;
            this.enabled = false;
            this.displayString = "";
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY) {
            if (!this.visible) return;
            this.hovered = mouseX >= this.xPosition && mouseY >= this.yPosition
                && mouseX < this.xPosition + this.width
                && mouseY < this.yPosition + this.height;
            super.drawButton(mc, mouseX, mouseY);
        }

        @Override
        public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
            if (super.mousePressed(mc, mouseX, mouseY)) {
                if (this.option != null) {
                    if (this.optionIndex >= 0) {
                        selectedIndex = this.optionIndex;
                    }
                    selectNetAndSend(this.option.netId());
                }
                return true;
            }
            return false;
        }
    }

    /** 最近网络按钮，点击时切换到对应网络 */
    private class RecentNetButton extends GuiButton {

        private int targetNetId = DimensionsNet.NO_PRIMARY_NET_ID;
        private boolean hovered;

        private RecentNetButton(int id, int x, int y, int width, int height) {
            super(id, x, y, width, height, "");
        }

        private void load(int netId) {
            this.targetNetId = netId;
            this.displayString = "#" + netId;
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY) {
            if (!this.visible) return;
            this.hovered = mouseX >= this.xPosition && mouseY >= this.yPosition
                && mouseX < this.xPosition + this.width
                && mouseY < this.yPosition + this.height;
            super.drawButton(mc, mouseX, mouseY);
        }

        @Override
        public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
            if (super.mousePressed(mc, mouseX, mouseY)) {
                if (this.targetNetId >= 0) {
                    selectNetAndSend(this.targetNetId);
                }
                return true;
            }
            return false;
        }
    }
}
