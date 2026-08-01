package com.wintercogs.beyonddimensions.client.gui;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.wintercogs.beyonddimensions.Config;
import com.wintercogs.beyonddimensions.api.ButtonState;
import com.wintercogs.beyonddimensions.api.ids.BDConstants;
import com.wintercogs.beyonddimensions.api.storage.eu.NetEuStorage;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.client.event.listener.NeiExposureBridge;
import com.wintercogs.beyonddimensions.client.event.listener.NeiSearchBridge;
import com.wintercogs.beyonddimensions.client.gui.widget.button.ReverseButton;
import com.wintercogs.beyonddimensions.client.gui.widget.button.SearchToggleButton;
import com.wintercogs.beyonddimensions.client.gui.widget.button.SortMethodButton;
import com.wintercogs.beyonddimensions.client.gui.widget.scroller.BigScroller;
import com.wintercogs.beyonddimensions.client.gui.widget.shared.IconButton;
import com.wintercogs.beyonddimensions.client.init.BDShortKeys;
import com.wintercogs.beyonddimensions.common.init.BDPackets;
import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;
import com.wintercogs.beyonddimensions.common.menu.widget.slot.AbstractStackTypedSlot;
import com.wintercogs.beyonddimensions.config.CommonConfigRuntime;
import com.wintercogs.beyonddimensions.network.packet.c2s.OpenNetGuiPacket;
import com.wintercogs.beyonddimensions.network.packet.c2s.OpenPrimaryNetSwitcherPacket;
import com.wintercogs.beyonddimensions.util.GuiRenderHelper;
import com.wintercogs.beyonddimensions.util.StringFormat;
import com.wintercogs.beyonddimensions.util.UIDataHelper;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 维度网络主界面 GUI（1.7.10 移植版）。
 * <p>
 * 1.7.10 适配：AbstractContainerScreen → GuiContainer（经 GuiBase）；
 * GuiGraphics#blit → GuiRenderHelper#blit；font → fontRendererObj；
 * imageWidth/imageHeight → xSize/ySize；leftPos/topPos → guiLeft/guiTop；
 * EditBox → GuiTextField；AbstractWidget → 自定义 ScrollBar。
 */
@SideOnly(Side.CLIENT)
public class GuiDimensionsNet extends GuiBase {

    // 按钮ID（避开槽位ID）
    private static final int ID_SORT_BUTTON = 100;
    private static final int ID_SECOND_SORT_BUTTON = 101;
    private static final int ID_REVERSE_BUTTON = 102;
    private static final int ID_SEARCH_TOGGLE_BUTTON = 103;
    private static final int ID_ADD_PAGE_BUTTON = 104;
    private static final int ID_REMOVE_PAGE_BUTTON = 105;
    private static final int ID_CRAFT_BUTTON = 106;
    private static final int ID_PRIMARY_NET_SWITCHER_BUTTON = 107;

    // ===== EU 池能量条（移植新增，终端独有）=====
    // 标题画在 (8,8)（高约 9px，止于 y≈17）；搜索框占 x 72..170。空闲区域 = TOP_BASE 内
    // y≈18..22 一条细条，从 x=8 到 x≈68（右接搜索框左缘 72，留 4px 间隙）。
    private static final int EU_BAR_X = 8;
    private static final int EU_BAR_Y = 18;
    private static final int EU_BAR_WIDTH = 60;
    private static final int EU_BAR_HEIGHT = 4;

    /** 底槽颜色（深灰，半透明） */
    private static final int EU_BAR_BG_COLOR = 0xAA555555;
    /** 填充颜色（琥珀金，区别于 RF 绿滴 0x50F18E） */
    private static final int EU_BAR_FILL_COLOR = 0xFFE0B400;

    protected final DimensionsNetMenu menu;

    protected GuiTextField searchField;
    protected String lastSearchText = "";

    // 搜索框边界（1.7.10 的 GuiTextField 不暴露坐标 getter，命中测试需自行记录，
    // 复用 GuiPrimaryNetSwitcher.renameFieldX/Y/W/H 的既有模式）
    protected int searchFieldX;
    protected int searchFieldY;
    protected int searchFieldW;
    protected int searchFieldH;
    protected ReverseButton reverseButton;
    protected SortMethodButton sortButton;
    protected SortMethodButton secondSortButton;
    protected SearchToggleButton searchToggleButton;
    protected IconButton addPageButton;
    protected IconButton removePageButton;
    protected IconButton craftButton;
    protected IconButton primaryNetSwitcherButton;
    protected BigScroller scroller;

    /** 标记需要在下一帧重新初始化 GUI（避免在按钮回调中直接调用 initGui 导致列表并发修改） */
    protected boolean needsReinit = false;

    /**
     * NEI 合成链精准暴露桥（方案 A）。由 NeiClientModule 在 NEI 存在时注册；
     * 为 null 表示无 NEI 或未注册，updateScreen 直接跳过，不影响其他功能。
     */
    private static NeiExposureBridge neiExposureBridge;

    /**
     * 注册 NEI 合成链精准暴露桥（仅在 NEI 存在时由 NEI 联动模块调用）。
     */
    public static void registerNeiExposureBridge(NeiExposureBridge bridge) {
        neiExposureBridge = bridge;
    }

    /**
     * NEI 搜索文本双向同步桥。由 NeiClientModule 在 NEI 存在时注册；
     * 为 null 表示无 NEI 或未注册，updateScreen 直接跳过，不影响其他功能。
     */
    private static NeiSearchBridge neiSearchBridge;

    /**
     * 注册 NEI 搜索文本同步桥（仅在 NEI 存在时由 NEI 联动模块调用）。
     */
    public static void registerNeiSearchBridge(NeiSearchBridge bridge) {
        neiSearchBridge = bridge;
    }

    public GuiDimensionsNet(InventoryPlayer inventory) {
        this(new DimensionsNetMenu(inventory));
    }

    public GuiDimensionsNet(InventoryPlayer inventory,
        com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler data) {
        this(new DimensionsNetMenu(inventory, data));
    }

    /**
     * 子类专用构造函数：允许传入 {@link DimensionsNetMenu} 的子类实例
     * （如 {@link com.wintercogs.beyonddimensions.common.menu.DimensionsCraftMenu}）。
     */
    protected GuiDimensionsNet(DimensionsNetMenu menu) {
        super(menu);
        this.menu = menu;
        this.xSize = 194;
        this.ySize = rebuildImageHeight();
    }

    /** 计算并设置界面尺寸与位置 */
    @Override
    public void initGui() {
        // 对齐源项目 init 中的转移上下文恢复：从合成/主网络切换器菜单切回时
        // 恢复页码与鼠标位置（审计 M3-4）
        if (UIDataHelper.isTransfer) {
            UIDataHelper.isTransfer = false;
            if (menu.lineData != UIDataHelper.currentPage) {
                menu.lineData = UIDataHelper.currentPage;
                menu.buildIndexList();
            }
            Mouse.setCursorPosition(UIDataHelper.lastMouseX, UIDataHelper.lastMouseY);
        }
        // 根据窗口高度限制最大行数
        int maxLines = calMaxLines();
        if (maxLines < menu.getLines()) {
            if (maxLines < 2) maxLines = 2;
            menu.setLines(maxLines);
            menu.rebuildSlots();
        }
        this.xSize = 194;
        this.ySize = rebuildImageHeight();
        super.initGui();
        // 源码以 176 宽度居中以获得更好视觉效果
        this.guiLeft = (this.width - 176) / 2;
        this.guiTop = (this.height - this.ySize) / 2;

        initWidgets();
    }

    /**
     * 初始化搜索框、排序按钮、翻页按钮、滚动条等控件。
     * 子类可覆写 {@link #addCraftButton()} 与 {@link #addPrimaryNetSwitcherButton()} 扩展。
     */
    protected void initWidgets() {
        // 排序按钮
        sortButton = new SortMethodButton(ID_SORT_BUTTON, this.guiLeft - 18, this.guiTop + 6, button -> {
            sortButton.toggleState();
            CommonConfigRuntime.uiSortButton = (ButtonState) sortButton.currentState;
            Config.setUiString(
                "ui_sort_button",
                CommonConfigRuntime.uiSortButton.name(),
                ButtonState.SORT_NAME.name(),
                "存储UI搜索按钮值 (除非你知道你在做什么，否则不要手动修改)");
            menu.buildIndexList();
        });
        this.buttonList.add(sortButton);

        // 第二排序策略按钮（对齐源项目：匿名子类重写 initButton 以使用 uiSecondSortButton
        // 初始状态与 _second tooltip；若用默认 initButton 会误用 uiSortButton，导致两按钮图标相同）
        secondSortButton = new SortMethodButton(
            ID_SECOND_SORT_BUTTON,
            this.guiLeft - 18,
            this.guiTop + 6 + 18,
            button -> {
                secondSortButton.toggleState();
                CommonConfigRuntime.uiSecondSortButton = (ButtonState) secondSortButton.currentState;
                Config.setUiString(
                    "ui_second_sort_button",
                    CommonConfigRuntime.uiSecondSortButton.name(),
                    ButtonState.SORT_INSERTED_TIME.name(),
                    "存储UI搜索按钮值 (除非你知道你在做什么，否则不要手动修改)");
                menu.buildIndexList();
            }) {

            @Override
            protected void initButton() {
                iconMap.put(
                    ButtonState.SORT_CREATIVE_TAB,
                    new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/sort_creative_tab.png"));
                iconMap.put(
                    ButtonState.SORT_MAX_STACK,
                    new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/sort_max_stack.png"));
                iconMap.put(
                    ButtonState.SORT_QUANTITY,
                    new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/sort_quantity.png"));
                iconMap.put(
                    ButtonState.SORT_NAME,
                    new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/sort_name.png"));
                iconMap.put(
                    ButtonState.SORT_MODID,
                    new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/sort_modid.png"));
                iconMap.put(
                    ButtonState.SORT_INSERTED_TIME,
                    new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/sort_inserted_time.png"));
                iconMap.put(
                    ButtonState.SORT_MODIFIED_TIME,
                    new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/sort_modified_time.png"));

                tooltipMap
                    .put(ButtonState.SORT_CREATIVE_TAB, "tooltip.button.beyonddimensions.sort_creative_tab_second");
                tooltipMap.put(ButtonState.SORT_MAX_STACK, "tooltip.button.beyonddimensions.sort_max_stack_second");
                tooltipMap.put(ButtonState.SORT_QUANTITY, "tooltip.button.beyonddimensions.sort_quantity_second");
                tooltipMap.put(ButtonState.SORT_NAME, "tooltip.button.beyonddimensions.sort_name_second");
                tooltipMap.put(ButtonState.SORT_MODID, "tooltip.button.beyonddimensions.sort_modid_second");
                tooltipMap
                    .put(ButtonState.SORT_INSERTED_TIME, "tooltip.button.beyonddimensions.sort_inserted_time_second");
                tooltipMap
                    .put(ButtonState.SORT_MODIFIED_TIME, "tooltip.button.beyonddimensions.sort_modified_time_second");

                for (Enum<?> state : iconMap.keySet()) {
                    this.states.add(state);
                }
                setState(CommonConfigRuntime.uiSecondSortButton);
            }
        };
        this.buttonList.add(secondSortButton);

        // 倒序切换按钮
        reverseButton = new ReverseButton(ID_REVERSE_BUTTON, this.guiLeft - 18, this.guiTop + 6 + 18 * 2, button -> {
            reverseButton.toggleState();
            CommonConfigRuntime.uiReverseButton = (ButtonState) reverseButton.currentState;
            Config.setUiString(
                "ui_reverse_button",
                CommonConfigRuntime.uiReverseButton.name(),
                ButtonState.DISABLED.name(),
                "存储UI倒序按钮值 (除非你知道你在做什么，否则不要手动修改)");
            menu.buildIndexList();
        });
        this.buttonList.add(reverseButton);

        // 搜索切换按钮
        searchToggleButton = new SearchToggleButton(
            ID_SEARCH_TOGGLE_BUTTON,
            this.guiLeft - 18,
            this.guiTop + 6 + 18 * 3,
            button -> {
                searchToggleButton.toggleState();
                CommonConfigRuntime.uiSearchButton = (ButtonState) searchToggleButton.currentState;
                Config.setUiString(
                    "ui_search_button",
                    CommonConfigRuntime.uiSearchButton.name(),
                    ButtonState.DISABLED.name(),
                    "存储UI搜索按钮值 (除非你知道你在做什么，否则不要手动修改)");
            });
        this.buttonList.add(searchToggleButton);

        // 页面增加按钮
        addPageButton = new IconButton(
            ID_ADD_PAGE_BUTTON,
            this.guiLeft - 18,
            this.guiTop + 6 + 18 * 4,
            16,
            16,
            new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/up_arrow.png"),
            button -> {
                if (this.height - 36 <= (rebuildImageHeight() + CommonTextures.MID_SLOTS_HEIGHT)
                    || menu.getLines() >= 99) {
                    return;
                }
                menu.addLines();
                menu.rebuildSlots();
                CommonConfigRuntime.uiPageNum = menu.getLines();
                CommonConfigRuntime.uiSearch = searchField.getText();
                Config.setUiString(
                    "ui_page_num",
                    String.valueOf(menu.getLines()),
                    "5",
                    "存储UI当前显示的总页数 (除非你知道你在做什么，否则不要手动修改)");
                needsReinit = true;
            });
        this.buttonList.add(addPageButton);

        // 页面减少按钮
        removePageButton = new IconButton(
            ID_REMOVE_PAGE_BUTTON,
            this.guiLeft - 18,
            this.guiTop + 6 + 18 * 5,
            16,
            16,
            new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/down_arrow.png"),
            button -> {
                if (menu.getLines() <= 2) return;
                menu.reduceLines();
                menu.rebuildSlots();
                CommonConfigRuntime.uiPageNum = menu.getLines();
                CommonConfigRuntime.uiSearch = searchField.getText();
                Config.setUiString(
                    "ui_page_num",
                    String.valueOf(menu.getLines()),
                    "5",
                    "存储UI当前显示的总页数 (除非你知道你在做什么，否则不要手动修改)");
                needsReinit = true;
            });
        this.buttonList.add(removePageButton);

        addCraftButton();
        addPrimaryNetSwitcherButton();

        // 搜索框：左边缘右移至 +72，避免与较长标题（如"维度网络合成"6 字≈54px，
        // 止于 x≈62）的末字/右边框碰撞；宽 98 保证右边缘 72+98=170 不越界（滚动条在 +174）。
        // 注：源项目为 +60/宽120，但 1.7.10 字体度量下 6 字标题会与 +60 搜索框边框重叠露出灰块，故右移。
        this.searchField = new GuiTextField(
            this.fontRendererObj,
            this.guiLeft + 72,
            this.guiTop + 7,
            98,
            this.fontRendererObj.FONT_HEIGHT + 5);
        this.searchFieldX = this.guiLeft + 72;
        this.searchFieldY = this.guiTop + 7;
        this.searchFieldW = 98;
        this.searchFieldH = this.fontRendererObj.FONT_HEIGHT + 5;
        this.searchField.setMaxStringLength(200);
        this.searchField.setEnableBackgroundDrawing(true);
        this.searchField.setVisible(true);
        this.searchField.setTextColor(16777215);
        this.searchField.setCanLoseFocus(true);
        this.searchField.setText(CommonConfigRuntime.uiSearch);
        this.textFields.add(searchField);
        this.lastSearchText = searchField.getText();
        // 1.7.10 的 GuiTextField.setText() 不会触发任何回调（1.20.1 的 EditBox.setValue()
        // 会立即触发 responder），故重开终端恢复搜索文本后需主动重新应用过滤，
        // 否则列表不会重新过滤（Bug 1 根因）。
        if (!this.lastSearchText.isEmpty()) {
            menu.loadSearchText(this.lastSearchText);
            CommonConfigRuntime.uiSearch = this.lastSearchText;
            menu.markForceAllUpdateClientView();
            menu.updateViewerStorage(false);
        }

        // 滚动条
        int trackLength = 18 * menu.getLines() - CommonTextures.SCROLLER_HEIGHT - 2;
        this.scroller = new BigScroller(
            this.guiLeft + 174,
            this.guiTop + CommonTextures.TOP_BASE_HEIGHT + 1,
            trackLength,
            menu.lineData,
            menu.maxLineData,
            pos -> {
                if (menu.lineData != pos) {
                    menu.lineData = pos;
                    menu.buildIndexList();
                }
            });
        this.scroller.setStep(1);
        // 滚轮生效区域 = 存储槽网格区（8..8+9*18 × 25..25+lines*18），加滚动条轨道本身。
        // 鼠标在 GUI 外（如 NEI 收藏栏）滚轮不再被吞，NEI Shift+滚轮切配方可正常收到事件（BUGFIX_RECORD #100）。
        this.scroller.setWheelRegion(this.guiLeft + 8, this.guiTop + 25, 9 * 18, menu.getLines() * 18);
        this.scrollBars.add(scroller);
    }

    /** 用于让子类重写工艺槽位按钮的函数 */
    protected void addCraftButton() {
        craftButton = new IconButton(
            ID_CRAFT_BUTTON,
            this.guiLeft - 18,
            this.guiTop + 6 + 18 * 6,
            16,
            16,
            new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/craft_button.png"),
            button -> {
                saveTransferContext();
                if (menu instanceof com.wintercogs.beyonddimensions.common.menu.DimensionsCraftMenu) {
                    CommonConfigRuntime.uiCraftButton = ButtonState.DISABLED;
                    Config.setUiString(
                        "ui_craft_button",
                        ButtonState.DISABLED.name(),
                        ButtonState.DISABLED.name(),
                        "决定打开菜单时是否显示合成槽");
                    BDPackets.INSTANCE.sendToServer(
                        new OpenNetGuiPacket(
                            menu.player.getUniqueID()
                                .toString(),
                            OpenNetGuiPacket.NET_MENU));
                } else {
                    CommonConfigRuntime.uiCraftButton = ButtonState.ENABLED;
                    Config.setUiString(
                        "ui_craft_button",
                        ButtonState.ENABLED.name(),
                        ButtonState.DISABLED.name(),
                        "决定打开菜单时是否显示合成槽");
                    BDPackets.INSTANCE.sendToServer(
                        new OpenNetGuiPacket(
                            menu.player.getUniqueID()
                                .toString(),
                            OpenNetGuiPacket.NET_CRAFT_MENU));
                }
            });
        this.buttonList.add(craftButton);
    }

    protected void addPrimaryNetSwitcherButton() {
        primaryNetSwitcherButton = new IconButton(
            ID_PRIMARY_NET_SWITCHER_BUTTON,
            this.guiLeft - 18,
            this.guiTop + 6 + 18 * 7,
            16,
            16,
            new ResourceLocation(BDConstants.MODID, "textures/gui/sprites/widget/opposite_arrow.png"),
            button -> {
                saveTransferContext();
                BDPackets.INSTANCE.sendToServer(new OpenPrimaryNetSwitcherPacket());
            });
        this.buttonList.add(primaryNetSwitcherButton);
    }

    /**
     * 保存转移上下文（对齐源项目 {@code DimensionsNetGUI.saveTransferContext}，审计 M3-4）：
     * 切到合成/主网络切换器菜单时记录当前页码与鼠标位置，切回后由 {@link #initGui()} 恢复。
     */
    private void saveTransferContext() {
        UIDataHelper.currentPage = menu.lineData;
        UIDataHelper.lastMouseX = Mouse.getX();
        UIDataHelper.lastMouseY = Mouse.getY();
        UIDataHelper.isTransfer = true;
    }

    protected int rebuildImageHeight() {
        return CommonTextures.TOP_BASE_HEIGHT + CommonTextures.TOP_SLOTS_HEIGHT
            + (menu.getLines() - 2) * CommonTextures.MID_SLOTS_HEIGHT
            + CommonTextures.BOTTOM_SLOTS_HEIGHT
            + CommonTextures.PLAYER_INV_HEIGHT;
    }

    protected int calMaxLines() {
        return (int) ((this.height - 36
            - (CommonTextures.TOP_BASE_HEIGHT + CommonTextures.TOP_SLOTS_HEIGHT
                + CommonTextures.BOTTOM_SLOTS_HEIGHT
                + CommonTextures.PLAYER_INV_HEIGHT))
            / (float) CommonTextures.MID_SLOTS_HEIGHT + 2);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        // 对齐源项目 keyPressed/keyReleased：每 tick 同步 Shift 状态到菜单（1.7.10 无
        // keyReleased 事件，逐 tick 轮询 isShiftKeyDown 是等效且更准确的替代，审计 M3-2）。
        // hasShiftDown 控制 updateViewerStorage 的更新模式（按住 Shift 仅更新数量）。
        menu.hasShiftDown = isShiftKeyDown();
        if (needsReinit) {
            needsReinit = false;
            // 先重建索引列表，更新 lineData/maxLineData 与槽位映射，
            // 这样 initGui() 中创建 scroller 时读到的是最新值（对齐 1.20.1 顺序）
            menu.buildIndexList();
            initGui();
            return;
        }
        // 搜索框文本变化同步
        if (searchField != null) {
            // 先拉取 NEI 搜索文本（NEI 侧优先），再检测 BD 自身文本变化并推送 NEI。
            // 对齐源项目 DimensionsNetGUI：responder 推送（BD 变化即同步到 JEI/EMI）+ containerTick
            // 拉取（每 tick 从 JEI/EMI 读回）；1.7.10 的 GuiTextField.setText() 不触发任何回调
            // （同 initGui 中的移植注释），故拉取后需手动同步过滤与持久化状态。
            if (CommonConfigRuntime.searchTextWithJEIEMI && neiSearchBridge != null) {
                String neiText = neiSearchBridge.readSearchText();
                if (neiText != null && !neiText.equals(lastSearchText)) {
                    searchField.setText(neiText);
                    lastSearchText = neiText;
                    menu.loadSearchText(neiText);
                    CommonConfigRuntime.uiSearch = neiText;
                    menu.markForceAllUpdateClientView();
                    menu.updateViewerStorage(false);
                }
            }
            String currentSearch = searchField.getText();
            if (!currentSearch.equals(lastSearchText)) {
                lastSearchText = currentSearch;
                menu.loadSearchText(currentSearch);
                CommonConfigRuntime.uiSearch = currentSearch;
                menu.markForceAllUpdateClientView();
                menu.updateViewerStorage(false);
                // 文本同步到 NEI 搜索栏（对齐源项目 responder 的 JEI/EMI 推送分支）
                if (CommonConfigRuntime.searchTextWithJEIEMI && neiSearchBridge != null) {
                    neiSearchBridge.pushSearchText(currentSearch);
                }
            }
        }
        // 同步滚动条位置
        if (scroller != null) {
            scroller.updateScrollPosition(menu.lineData, menu.maxLineData);
        }
        // NEI 合成链精准暴露（方案 A）：每 tick 把当前悬停收藏组的链条目同步到非活跃槽位。
        // 在 drawScreen（NEI 链 tooltip 渲染）之前执行，保证 NEI 读到的映射是本帧的。
        if (neiExposureBridge != null) {
            int mx = Mouse.getX() * this.width / this.mc.displayWidth;
            int my = this.height - Mouse.getY() * this.height / this.mc.displayHeight - 1;
            neiExposureBridge.updateNeiExposure(menu, mx, my);
        }
    }

    /**
     * 关闭 GUI 时持久化搜索框内容（对齐源项目 DimensionsNetGUI.removed()）：
     * 仅当搜索开关启用时保存文本，否则清空；写入配置文件以跨重启保留。
     */
    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        if (searchField != null) {
            if (searchField.getText()
                .length() > 0 && CommonConfigRuntime.uiSearchButton == ButtonState.ENABLED) {
                CommonConfigRuntime.uiSearch = searchField.getText();
                Config.setUiString("ui_search", CommonConfigRuntime.uiSearch, "", "存储UI搜索框内容 (除非你知道你在做什么，否则不要手动修改)");
            } else {
                CommonConfigRuntime.uiSearch = "";
                Config.setUiString("ui_search", "", "", "存储UI搜索框内容 (除非你知道你在做什么，否则不要手动修改)");
            }
        }
    }

    /**
     * 鼠标点击处理。对齐源项目 {@code DimensionsNetGUI.mouseClicked()} 的搜索框右键清空行为：
     * 右键点击搜索框则清空搜索内容。焦点取消（点击外部失焦）由 GuiBase 转发的
     * {@link GuiTextField#mouseClicked}（setCanLoseFocus(true)）天然处理，无需移植源项目的
     * getFocused() 分支。
     * <p>
     * 1.7.10 的 GuiTextField.setText() 不触发任何回调（同 initGui 中的移植注释），
     * 故清空后需手动同步过滤与持久化状态，否则列表保持旧过滤条件。
     */
    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);

        // 右键（button==1）点击搜索框 → 清空搜索内容
        if (button == 1 && searchField != null
            && searchField.getVisible()
            && mouseX >= searchFieldX
            && mouseY >= searchFieldY
            && mouseX < searchFieldX + searchFieldW
            && mouseY < searchFieldY + searchFieldH) {
            clearSearch();
        }
    }

    /**
     * 清空搜索框内容并同步过滤状态（右键清空入口；同步链路对齐 {@link #updateScreen}）。
     */
    protected void clearSearch() {
        searchField.setText("");
        if (!lastSearchText.isEmpty() || !CommonConfigRuntime.uiSearch.isEmpty()) {
            lastSearchText = "";
            menu.loadSearchText("");
            CommonConfigRuntime.uiSearch = "";
            menu.markForceAllUpdateClientView();
            menu.updateViewerStorage(false);
            // 清空同样推送 NEI（对齐源项目 responder 文本变化即同步）
            if (CommonConfigRuntime.searchTextWithJEIEMI && neiSearchBridge != null) {
                neiSearchBridge.pushSearchText("");
            }
        }
    }

    /**
     * 判断给定屏幕坐标是否命中搜索框区域（供 NEI 拖拽填充物品名使用）。
     * 命中判定复用 {@link #mouseClicked} 右键清空时的边界常量。
     */
    public boolean isSearchFieldHit(int mouseX, int mouseY) {
        return searchField != null && searchField.getVisible()
            && mouseX >= searchFieldX
            && mouseY >= searchFieldY
            && mouseX < searchFieldX + searchFieldW
            && mouseY < searchFieldY + searchFieldH;
    }

    /**
     * 用物品显示名填充搜索框并立即重新过滤。
     * <p>
     * 对齐 NEI {@code SearchInputDropHandler} 的"拖拽物品到搜索栏 → 填充名称"UX：
     * - 名称准确性：由调用方传入显示名（流体容器取流体本地化名），此处直接落框；
     * - 填充即过滤：1.7.10 的 GuiTextField.setText() 不触发任何回调（同 initGui 移植注释），
     * 故手动执行与 {@link #updateScreen} 一致的过滤链路；
     * - 若开启了与NEI搜索同步（search_text_with_jei_emi），同步推送到 NEI 搜索栏。
     */
    public void fillSearchFromItemName(String name) {
        if (searchField == null || name == null) {
            return;
        }
        searchField.setText(name);
        lastSearchText = name;
        menu.loadSearchText(name);
        CommonConfigRuntime.uiSearch = name;
        menu.markForceAllUpdateClientView();
        menu.updateViewerStorage(false);
        if (CommonConfigRuntime.searchTextWithJEIEMI && neiSearchBridge != null) {
            neiSearchBridge.pushSearchText(name);
        }
    }

    /**
     * 键盘输入处理。对齐源项目 {@code DimensionsNetGUI.keyPressed()} 的 Shift+Z 切换
     * 「与NEI同步搜索」（配置 search_text_with_jei_emi）。搜索框聚焦时不拦截，
     * 让文本输入优先（与源项目 searchField.canConsumeInput 前置判断一致）。
     */
    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        boolean searchFieldFocused = false;
        for (GuiTextField textField : textFields) {
            if (textField.isFocused()) {
                searchFieldFocused = true;
                break;
            }
        }
        if (!searchFieldFocused && isShiftKeyDown() && keyCode == Keyboard.KEY_Z) {
            boolean current = CommonConfigRuntime.searchTextWithJEIEMI;
            CommonConfigRuntime.searchTextWithJEIEMI = !current;
            Config.setUiBoolean("search_text_with_jei_emi", !current);
            return;
        }
        // 对齐源项目 keyPressed：背包键(E)或主网络快捷键(O)关闭 GUI（审计 M3-3）。
        // 搜索框聚焦时不拦截，让输入优先。
        if (!searchFieldFocused && (keyCode == this.mc.gameSettings.keyBindInventory.getKeyCode()
            || keyCode == BDShortKeys.OPEN_GUI_KEY.getKeyCode())) {
            this.mc.displayGuiScreen(null);
            return;
        }
        super.keyTyped(typedChar, keyCode);
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
            CommonTextures.GUI_TEXTURE_PLAYER_INV,
            this.guiLeft,
            drawY,
            CommonTextures.PLAYER_INV_WIDTH,
            CommonTextures.PLAYER_INV_HEIGHT,
            CommonTextures.PLAYER_INV_WIDTH,
            CommonTextures.PLAYER_INV_HEIGHT);

        // 终端 EU 池能量条（合成界面子类覆写 shouldDrawEuBar 为 false，不绘制）
        drawEuStorageBar();

        // 选中状态提示：GL 颜色复位
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);

        // 文本输入框（搜索框）置于背景层，避免遮挡标题与 NEI 提示
        drawTextFields();
        // 滚动条置于背景层，避免滑块遮挡 NEI 提示
        drawScrollBars();
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        int titleLabelY = 8;
        int inventoryLabelY = CommonTextures.TOP_BASE_HEIGHT + menu.getLines() * 18 + 5;
        this.fontRendererObj
            .drawString(StatCollector.translateToLocal("gui.beyonddimensions.dimensions_net"), 8, titleLabelY, 4210752);
        this.fontRendererObj
            .drawString(StatCollector.translateToLocal("container.inventory"), 8, inventoryLabelY, 4210752);

        // 存储网格中的非物品条目（能量/流体）覆盖层
        drawTypedSlotOverlays();
    }

    /**
     * 是否绘制终端 EU 池能量条。合成界面（GuiDimensionsCraft 及子类）返回 false。
     */
    protected boolean shouldDrawEuBar() {
        return true;
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
     * 绘制终端 EU 池能量条（背景层，绝对屏幕坐标）。
     * 底槽深灰 + 按 已用/10^40 比例填充的琥珀金进度条（独立配色，区别于 RF 绿滴 0x50F18E）。
     * 悬停完整十进制 tooltip 由 {@link #drawScreen} 处理。
     */
    protected void drawEuStorageBar() {
        if (!shouldDrawEuBar()) return;
        int x = this.guiLeft + EU_BAR_X;
        int y = this.guiTop + EU_BAR_Y;
        Gui.drawRect(x, y, x + EU_BAR_WIDTH, y + EU_BAR_HEIGHT, EU_BAR_BG_COLOR);

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
            Gui.drawRect(x, y, x + filledWidth, y + EU_BAR_HEIGHT, EU_BAR_FILL_COLOR);
        }
    }

    /**
     * 绘制存储网格中非物品条目的图标与数量文字（对齐源项目 BDBaseGUI.renderSlot 的
     * IStackRender 通用渲染管线）。
     * <p>
     * 1.7.10 原版 drawSlot 只能渲染 ItemStack（{@code AbstractStackTypedSlot#getStack()}
     * 对非物品条目返回 null），能量/流体等通用条目必须由本层经各 key 的
     * {@link com.wintercogs.beyonddimensions.api.storage.key.IStackRender} 叠加绘制。
     * 前景层在原版槽位渲染之后、拖拽物品之前绘制，层序正确。
     * <p>
     * protected：子类（GuiDimensionsCraft）覆写了前景层且不调 super，
     * 需在子类前景层末尾显式调用本方法，否则终端/合成界面不显示非物品条目。
     */
    protected void drawTypedSlotOverlays() {
        for (Object slotObj : this.inventorySlots.inventorySlots) {
            if (!(slotObj instanceof AbstractStackTypedSlot)) continue;
            AbstractStackTypedSlot slot = (AbstractStackTypedSlot) slotObj;
            KeyAmount stack = slot.getTypedStackFromUnifiedStorage();
            if (stack == null || stack.isEmpty()) continue;
            if (stack.key() instanceof ItemStackKey) continue; // 物品走原版渲染管线

            int x = slot.xDisplayPosition;
            int y = slot.yDisplayPosition;
            stack.key()
                .getRender()
                .render(stack.key(), x, y);
            stack.key()
                .getRender()
                .renderAmount(stack.amount(), x, y);
        }
        // 图标绘制可能残留混合/颜色状态，复位避免污染后续绘制
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawEuStorageBarTooltip(mouseX, mouseY);
        drawSearchFieldTooltip(mouseX, mouseY);
        // 非物品条目（能量/流体）槽位悬停 tooltip 已上移至 GuiBase.drawTypedSlotHoverTooltip
        // （对齐源项目 BDBaseGUI.renderTooltip），本类经 super.drawScreen 生效，不再重复绘制
    }

    /**
     * 悬停在 EU 能量条上时显示完整十进制 tooltip。
     */
    private void drawEuStorageBarTooltip(int mouseX, int mouseY) {
        if (!shouldDrawEuBar()) return;
        int barX = this.guiLeft + EU_BAR_X;
        int barY = this.guiTop + EU_BAR_Y;
        if (mouseX < barX || mouseX >= barX + EU_BAR_WIDTH || mouseY < barY || mouseY >= barY + EU_BAR_HEIGHT) {
            return;
        }
        BigInteger amount = getEuAmount();
        List<String> lines = new ArrayList<>();
        lines.add(StatCollector.translateToLocal("gui.beyonddimensions.eu_storage"));
        lines.add(
            StringFormat.formatCount(amount) + " / " + StringFormat.formatCount(NetEuStorage.DEFAULT_CAPACITY) + " EU");
        lines.add(amount.toString() + " EU");
        drawHoveringText(lines, mouseX, mouseY, fontRendererObj);
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * 悬停在搜索框上时显示搜索语法 + Shift+Z 切换与 NEI 同步的提示。
     * <p>
     * 对齐源项目 1.20.1 {@code EditBox#setTooltip} 的搜索框提示行为（含
     * "Shift+Z 开关 JEI/EMI 搜索框联动"文案）；1.7.10 GuiTextField 无 setTooltip，
     * 故在 drawScreen 中自行检测悬停并绘制。lang 值以字面 {@code \n} 存多行，
     * 按既有惯例（XpExchangeItem）以 {@code split("\\\\n")} 拆分。
     */
    private void drawSearchFieldTooltip(int mouseX, int mouseY) {
        if (!isSearchFieldHit(mouseX, mouseY)) {
            return;
        }
        String[] lines = StatCollector.translateToLocal("tooltip.editbox.beyonddimensions.search")
            .split("\\\\n");
        drawHoveringText(Arrays.asList(lines), mouseX, mouseY, fontRendererObj);
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
