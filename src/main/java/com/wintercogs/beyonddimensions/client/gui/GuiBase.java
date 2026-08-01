package com.wintercogs.beyonddimensions.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.inventory.SlotCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.wintercogs.beyonddimensions.api.ids.BDConstants;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.client.gui.widget.shared.ScrollBar;
import com.wintercogs.beyonddimensions.client.gui.widget.shared.StatusButton;
import com.wintercogs.beyonddimensions.common.init.BDPackets;
import com.wintercogs.beyonddimensions.common.menu.BDBaseMenu;
import com.wintercogs.beyonddimensions.common.menu.widget.slot.AbstractStackTypedSlot;
import com.wintercogs.beyonddimensions.common.menu.widget.slot.FlagStackTypedSlot;
import com.wintercogs.beyonddimensions.network.packet.c2s.BatchTransferPacket;
import com.wintercogs.beyonddimensions.network.packet.c2s.CallSeverClickPacket;
import com.wintercogs.beyonddimensions.network.packet.c2s.FlagTranslatePacket;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 1.7.10 GUI 基类，为所有 BeyondDimensions 容器界面提供通用资源绑定与
 * 自定义控件（{@link ScrollBar}、{@link GuiTextField}）的事件转发。
 * <p>
 * 1.7.10 的 {@code GuiContainer} 不会自动渲染或转发事件给非 {@code GuiButton} 控件，
 * 因此子类只需将自定义滚动条加入 {@link #scrollBars}、文本框加入 {@link #textFields}，
 * 基类会在 {@code drawScreen}、{@code mouseClicked}、{@code mouseClickMove}、
 * {@code mouseMovedOrUp}、{@code handleMouseInput}、{@code keyTyped}、{@code updateScreen}
 * 中统一转发。
 */
@SideOnly(Side.CLIENT)
public abstract class GuiBase extends GuiContainer {

    public static final ResourceLocation GUI_TEXTURE = new ResourceLocation(BDConstants.MODID, "textures/gui/base.png");

    /** 自定义滚动条列表，由基类统一转发事件与渲染 */
    protected final List<ScrollBar> scrollBars = new ArrayList<>();
    /** 文本输入框列表，由基类统一转发事件与渲染 */
    protected final List<GuiTextField> textFields = new ArrayList<>();

    // 用于 shift 双击批量转移的检测状态（对齐 1.20.1 源项目 BDBaseGUI.slotClicked）：
    // 第一次 shift 点击记录被点击槽位/物品，窗口内再次 shift 点击同一目标即发送 BatchTransferPacket
    private ItemStack lastInvClickedStack = null;
    private ItemStackKey lastStorageClickedStack = ItemStackKey.EMPTY;
    private int lastInvClickedSlot = -1;
    private int cleanHold = 10; // 双击窗口（约 0.5s，每 tick 递减一次）

    public GuiBase(Container container) {
        super(container);
    }

    /**
     * 获取 GUI 左上角的屏幕 X 坐标（供外部模块如 NEI 集成使用）。
     */
    public int getGuiLeft() {
        return this.guiLeft;
    }

    /**
     * 获取 GUI 左上角的屏幕 Y 坐标（供外部模块如 NEI 集成使用）。
     */
    public int getGuiTop() {
        return this.guiTop;
    }

    @Override
    public void initGui() {
        super.initGui();
        // 1.7.10 的 GuiScreen.initGui() 是空方法，buttonList 仅在 setWorldAndResolution 中清空。
        // 当通过 needsReinit 在 updateScreen 中手动调用 initGui() 重建 UI 时，必须显式清空 buttonList，
        // 否则旧按钮会累积，导致视觉重影和重复触发回调（Bug 3 根因）。
        this.buttonList.clear();
        scrollBars.clear();
        textFields.clear();
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        this.mc.getTextureManager()
            .bindTexture(GUI_TEXTURE);
        int x = (this.width - this.xSize) / 2;
        int y = (this.height - this.ySize) / 2;
        this.drawTexturedModalRect(x, y, 0, 0, this.xSize, this.ySize);
        drawScrollBars();
        drawTextFields();
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        // 子类可覆盖
    }

    /**
     * 渲染槽位中的非物品条目（流体/能量）覆盖层。
     * <p>
     * 1.7.10 原版 drawSlot 只能渲染 ItemStack，流体/能量标记需经 IStackRender 叠加绘制。
     * 前景层在原版槽位渲染之后、拖拽物品之前绘制，层序正确
     * （对齐 GuiDimensionsNet.drawTypedSlotOverlays，6.31 曾为熔炉 GUI 单独复制）。
     * 含标记槽/存储槽的机器 GUI 需在其 drawGuiContainerForegroundLayer 末尾调用本方法。
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

    /**
     * 绘制自定义滚动条。
     * <p>
     * 与文本框同理，必须在<b>背景层</b>绘制而非 {@code drawScreen} 的 super 之后：
     * 否则滚动条滑块会盖住 NEI 在 super 末尾绘制的悬浮提示（用户下滚时滑块移到顶部，
     * 正好压住右上 NEI 提示）。置于背景层后 NEI 提示叠在其上。
     * 滚动条坐标为屏幕绝对坐标，背景层无平移，定位正确；滚动条位于右栏(x+174)，
     * 槽位仅到 x+170、GUI 纹理又在滚动条之前绘制，故滚动条仍清晰可见。
     * 注意：仅绘制挪到背景层，滚动条的拖拽/点击/滚轮事件转发仍在原处不变。
     */
    protected void drawScrollBars() {
        // 背景层传入的 mouseX/mouseY 是相对 guiLeft/guiTop 的坐标，而滚动条按绝对坐标判定 hover，故换算回绝对坐标
        int absX = Mouse.getX() * this.width / this.mc.displayWidth;
        int absY = this.height - Mouse.getY() * this.height / this.mc.displayHeight - 1;
        for (ScrollBar scrollBar : scrollBars) {
            scrollBar.drawScrollBar(this.mc, absX, absY);
        }
    }

    /**
     * 绘制文本输入框。
     * <p>
     * 必须在<b>背景层</b>（纹理绘制之后）调用，而非 {@code drawScreen} 的 super 之后：
     * 否则文本框的黑色背景会盖住前景层标题（如合成界面较长的标题）以及 NEI 在
     * super 末尾绘制的悬浮提示。置于背景层后，标题（前景层）与 NEI 提示均叠在其上。
     * 文本框坐标为屏幕绝对坐标，背景层无 guiLeft/guiTop 平移，定位正确；
     * 且文本框位于顶部条带，与槽位绘制层不重叠。
     */
    protected void drawTextFields() {
        for (GuiTextField textField : textFields) {
            textField.drawTextBox();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        // 滚动条、文本框均改在背景层绘制（见 drawScrollBars/drawTextFields），避免遮挡 NEI/标题
        // 绘制控件 tooltip
        drawWidgetTooltips(mouseX, mouseY);
        // 对齐源项目 BDBaseGUI.renderTooltip：原版悬停提示只对持有 ItemStack 的槽位生效，
        // 非物品条目（流体/能量）标记/存储槽由本层经 IStackRender.getTooltipLines 自行绘制
        drawTypedSlotHoverTooltip(mouseX, mouseY);
    }

    /**
     * 悬停非物品条目（流体/能量）槽位时绘制 tooltip（对齐源项目 BDBaseGUI.renderTooltip）。
     * <p>
     * 标记槽/存储槽中的流体、能量等非 ItemStack 条目无原版悬停提示，此处经
     * {@link IStackRender#getTooltipLines} 显示其名称/数量（如标记了水则显示"水"）。
     * 物品条目仍走原版渲染管线（vanilla 槽位悬停已生效）。
     */
    protected void drawTypedSlotHoverTooltip(int mouseX, int mouseY) {
        if (this.mc.thePlayer.inventory.getItemStack() != null) return;
        AbstractStackTypedSlot hovered = findHoveredTypedSlot(mouseX, mouseY);
        if (hovered == null) return;
        KeyAmount stack = hovered.getTypedStackFromUnifiedStorage();
        if (stack == null || stack.isEmpty() || stack.key() instanceof ItemStackKey) return;
        List<String> lines = stack.key()
            .getRender()
            .getTooltipLines(stack.key(), stack.amount(), this.mc.thePlayer, GuiScreen.isShiftKeyDown());
        if (lines != null && !lines.isEmpty()) {
            drawHoveringText(lines, mouseX, mouseY, fontRendererObj);
            RenderHelper.enableGUIStandardItemLighting();
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    /** 查找鼠标悬停的泛型槽位（屏幕坐标 → 槽位区域判定） */
    private AbstractStackTypedSlot findHoveredTypedSlot(int mouseX, int mouseY) {
        for (Object slotObj : this.inventorySlots.inventorySlots) {
            if (!(slotObj instanceof AbstractStackTypedSlot)) continue;
            AbstractStackTypedSlot slot = (AbstractStackTypedSlot) slotObj;
            if (mouseX >= this.guiLeft + slot.xDisplayPosition && mouseX < this.guiLeft + slot.xDisplayPosition + 16
                && mouseY >= this.guiTop + slot.yDisplayPosition
                && mouseY < this.guiTop + slot.yDisplayPosition + 16) {
                return slot;
            }
        }
        return null;
    }

    /**
     * 绘制按钮/控件的悬停 tooltip。子类可覆盖以扩展。
     */
    protected void drawWidgetTooltips(int mouseX, int mouseY) {
        for (Object button : this.buttonList) {
            if (button instanceof StatusButton) {
                StatusButton statusButton = (StatusButton) button;
                if (statusButton.isHovered() && statusButton.getTooltip() != null) {
                    String tooltip = StatCollector.translateToLocal(statusButton.getTooltip());
                    List<String> lines = new ArrayList<>();
                    lines.add(tooltip);
                    this.drawHoveringText(lines, mouseX, mouseY, this.fontRendererObj);
                    return;
                }
            }
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        // 先转发给文本框（处理焦点获取/丢失）
        for (GuiTextField textField : textFields) {
            textField.mouseClicked(mouseX, mouseY, button);
        }
        // 转发给滚动条；若处理了事件则不继续（避免命中底层槽位）
        for (ScrollBar scrollBar : scrollBars) {
            if (scrollBar.mousePressed(mouseX, mouseY, button)) {
                return;
            }
        }

        // 对齐 1.20.1 源项目 BDBaseGUI.slotClicked：光标持有物品时点击自定义槽位，
        // 绕过 GuiContainer 的拖拽模式（field_147007_t），直接分发为普通点击（type=0）。
        // 否则 GuiContainer.mouseClicked 会进入拖拽模式，松开时以 type=5（QUICK_CRAFT）
        // 分发给拖拽集合中的槽位，而自定义槽位被 mouseClickMove 拦截不在集合中，
        // 导致自定义槽位完全收不到点击事件（过滤槽无法标记物品）。
        Slot clickedSlot = getSlotAtPosition(mouseX, mouseY);
        if (clickedSlot instanceof AbstractStackTypedSlot && this.mc.thePlayer.inventory.getItemStack() != null) {
            this.handleMouseClick(clickedSlot, clickedSlot.slotNumber, button, 0);
            return;
        }

        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long time) {
        for (ScrollBar scrollBar : scrollBars) {
            scrollBar.mouseDragged(mouseX, mouseY, button);
        }
        // 对齐 1.20.1 源项目 BDBaseGUI.mouseDragged：命中自定义槽位时拦截容器 quick-craft，
        // 阻止 GuiContainer.dragSplittingSlots（field_147008_s）收集自定义槽位。
        // 1.7.10 中光标持有物品时按下即进入拖拽模式，若松开前光标扫过标记槽位，原版会把该槽位
        // 加入拖拽集合，松开时以 type=5（QUICK_CRAFT）分发点击，而 GuiBase.handleMouseClick
        // 对 type!=0/1 直接丢弃，导致「拾起食物→拖到过滤槽→松开」的标记手势永远无法到达服务端
        // （关闭重开后过滤被清空）。拦截后拖拽集合不含自定义槽位，松开退化为普通点击（type=0），
        // 由 handleMouseClick 转发 CallSeverClickPacket 完成标记/清空。
        if (field_147007_t) {
            Slot slot = getSlotAtPosition(mouseX, mouseY);
            if (slot instanceof AbstractStackTypedSlot) {
                return;
            }
        }
        super.mouseClickMove(mouseX, mouseY, button, time);
    }

    /**
     * 命中测试：获取指定屏幕坐标处的槽位。
     * <p>
     * GuiContainer.getSlotAtPosition / isMouseOverSlot 均为 private，子类无法复用，
     * 故按原版 isMouseOverSlot 的边界判定（[x-1, x+width+1)）自行实现。
     */
    private Slot getSlotAtPosition(int mouseX, int mouseY) {
        int relX = mouseX - this.guiLeft;
        int relY = mouseY - this.guiTop;
        for (Object s : this.inventorySlots.inventorySlots) {
            Slot slot = (Slot) s;
            if (relX >= slot.xDisplayPosition - 1 && relX < slot.xDisplayPosition + 16 + 1
                && relY >= slot.yDisplayPosition - 1
                && relY < slot.yDisplayPosition + 16 + 1) {
                return slot;
            }
        }
        return null;
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int button) {
        for (ScrollBar scrollBar : scrollBars) {
            scrollBar.mouseReleased(mouseX, mouseY, button);
        }
        // 对齐 mouseClicked 中的拦截：光标持有物品时松开自定义槽位不再重复分发点击，
        // 防止 mouseClicked 已分发一次后 mouseMovedOrUp 再分发一次导致「标记→立刻清空」。
        Slot slot = getSlotAtPosition(mouseX, mouseY);
        if (slot instanceof AbstractStackTypedSlot && this.mc.thePlayer.inventory.getItemStack() != null) {
            // 原版 mouseMovedOrUp 末尾会复位拖拽标志并清空拖拽槽位集合（field_147007_t/field_147008_s），
            // 此处跳过 super 必须手动复位，否则残留状态会在下一次松开时把上一次拖拽的旧槽位
            // 以 type=5（QUICK_CRAFT）误分发（含模式按钮等普通点击路径），产生异常点击。
            if (field_147007_t) {
                field_147007_t = false;
                field_147008_s.clear();
            }
            return;
        }
        super.mouseMovedOrUp(mouseX, mouseY, button);
    }

    @Override
    public void handleMouseInput() {
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
            int mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
            for (ScrollBar scrollBar : scrollBars) {
                if (scrollBar.mouseScrolled(mouseX, mouseY, wheel)) {
                    return;
                }
            }
            // 对齐 NEI 的 SHIFT+滚轮交互：悬停在标记槽上滚动时，将标记在
            // "物品 ↔ 容器内流体"之间翻译一步（滚一下翻译一次）
            if (GuiScreen.isShiftKeyDown()) {
                Slot slot = getSlotAtPosition(mouseX, mouseY);
                if (slot instanceof FlagStackTypedSlot) {
                    BDPackets.INSTANCE.sendToServer(new FlagTranslatePacket(slot.slotNumber, wheel > 0 ? 1 : -1));
                    return;
                }
            }
        }
        super.handleMouseInput();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        // 转发给获得焦点的文本框（ESC 仍允许关闭 GUI）
        for (GuiTextField textField : textFields) {
            if (textField.isFocused()) {
                if (keyCode == 1) { // ESC
                    break;
                }
                textField.textboxKeyTyped(typedChar, keyCode);
                return;
            }
        }
        super.keyTyped(typedChar, keyCode);
    }

    /**
     * 拦截自定义槽位点击，避免原版 {@link Container#slotClick} 因
     * {@link AbstractStackTypedSlot#putStack}/{@link AbstractStackTypedSlot#decrStackSize}
     * 的被动实现而误删物品。
     * <p>
     * 仅处理普通点击（type=0）与 Shift+点击（type=1），其它操作回退到原版逻辑。
     * 对于玩家背包原版的 Shift+点击，若当前界面存在自定义槽位，则转发到服务端由
     * {@link com.wintercogs.beyonddimensions.common.menu.BDBaseMenu#customClickHandler}
     * 处理，使其能正确进入维度网络存储。
     */
    @Override
    protected void handleMouseClick(Slot slot, int slotId, int mouseButton, int type) {
        if (slot == null) {
            super.handleMouseClick(slot, slotId, mouseButton, type);
            return;
        }

        // 合成结果槽：完全走自定义点击路径（对齐 AE2 SlotCraftingTerm 的服务端权威方案）。
        // 1.7.10 原版对结果槽点击会先在客户端本地预测 slotClick，再经 processClickWindow
        // 用 C0E.clickedItem 与服务端 slotClick 返回值比对，网络延迟下预测与真实状态分叉即
        // setPlayerIsPresent(false) 锁玩家（后续点击包被忽略），表现为快速合成被限速/丢点击。
        // 改走 CallSeverClickPacket 后由服务端权威执行拾取/消耗/补料，绕开原版反同步机制。
        if (slot instanceof SlotCrafting) {
            if (type == 6) {
                // 原版双击收集（slotClick mode 6）在含 900+ 类型槽的本容器上会遍历全部槽位，
                // 对能量/流体等非物品类型槽触发空指针；且它本就会吞掉快速连点的隔次点击
                // （250ms 内的第二次点击被派发成 type 6）。结果槽有成品时把双击也当作一次
                // 普通合成，使快速连点每次点击都生效；结果槽为空时无可合成，丢弃。
                ItemStack stack = slot.getStack();
                if (stack != null && stack.stackSize > 0) {
                    KeyAmount clickItem = new KeyAmount(new ItemStackKey(stack), stack.stackSize);
                    BDPackets.INSTANCE.sendToServer(new CallSeverClickPacket(slotId, clickItem, mouseButton, false));
                }
                return;
            }
            if (type == 0 || type == 1) {
                ItemStack stack = slot.getStack();
                if (stack != null && stack.stackSize > 0) {
                    KeyAmount clickItem = new KeyAmount(new ItemStackKey(stack), stack.stackSize);
                    BDPackets.INSTANCE
                        .sendToServer(new CallSeverClickPacket(slotId, clickItem, mouseButton, type == 1));
                }
                return;
            }
            // 其它类型（数字键/Q 键/拖曳/创造模式取用）不落原版：原版 slotClick 会改写结果槽
            // 或遍历类型槽，在本容器上不安全（空指针 / 反同步），直接丢弃。
            // （AE2 的 SlotCraftingTerm.canTakeStack=false 同样使这些类型在结果槽上成为空操作）
            return;
        }

        if (slot instanceof AbstractStackTypedSlot) {
            if (type != 0 && type != 1) {
                // 拖曳、数字键、丢出等暂不由自定义槽位处理，防止状态混乱
                return;
            }

            AbstractStackTypedSlot typedSlot = (AbstractStackTypedSlot) slot;
            KeyAmount clickItem = typedSlot.getVanillaActualStack();

            // 对齐 1.20.1 源项目 BDBaseGUI.slotClicked：shift 双击存储槽 → 批量转移（存储→背包）。
            // 高版本当前源码（commit fd14707e）因"相当一部分人不喜欢存储物品双击后全量进入背包"
            // 已禁用该方向，仅保留双击检测状态记录；此处注释掉触发行，保持行为与高版本一致。
            if (type == 1 && clickItem.key() instanceof ItemStackKey) {
                ItemStackKey itemKey = (ItemStackKey) clickItem.key();
                if (!lastStorageClickedStack.isEmpty() && lastStorageClickedStack.equals(itemKey)) {
                    // TODO 对齐 1.20.1 源项目：存储→背包方向批量转移已禁用，如需恢复可启用下行
                    // BDPackets.INSTANCE.sendToServer(new BatchTransferPacket(clickItem, false));
                } else if (!clickItem.isEmpty()) {
                    this.lastStorageClickedStack = itemKey;
                }
            }

            BDPackets.INSTANCE.sendToServer(new CallSeverClickPacket(slotId, clickItem, mouseButton, type == 1));
            return;
        }

        // Shift+点击玩家背包物品：当容器中存在自定义槽位时，走自定义快速移动路径
        if (type == 1 && slot.inventory instanceof InventoryPlayer && hasTypedSlot()) {
            ItemStack stack = slot.getStack();
            if (stack != null && stack.stackSize > 0) {
                KeyAmount clickItem = new KeyAmount(new ItemStackKey(stack), stack.stackSize);

                // 对齐 1.20.1 源项目 BDBaseGUI.slotClicked：shift 双击同一背包槽 → 批量转移（背包→存储）。
                // 第一次 shift 点击记录槽位与物品，窗口内再次 shift 点击同一槽位即触发批量转移，
                // 将背包中该物品全部快速移入网络存储。
                BDBaseMenu bdMenu = (BDBaseMenu) this.inventorySlots;
                if (lastInvClickedSlot == slotId && lastInvClickedStack != null) {
                    BDPackets.INSTANCE.sendToServer(
                        new BatchTransferPacket(
                            new KeyAmount(new ItemStackKey(lastInvClickedStack), lastInvClickedStack.stackSize),
                            true));
                } else if (slotId >= bdMenu.inventoryStartIndex && slotId < bdMenu.inventoryEndIndex) {
                    lastInvClickedStack = stack;
                    lastInvClickedSlot = slotId;
                }

                BDPackets.INSTANCE.sendToServer(new CallSeverClickPacket(slotId, clickItem, mouseButton, true));
                return;
            }
        }

        super.handleMouseClick(slot, slotId, mouseButton, type);
    }

    /**
     * 判断当前容器是否包含自定义槽位。
     */
    private boolean hasTypedSlot() {
        for (Object s : this.inventorySlots.inventorySlots) {
            if (s instanceof AbstractStackTypedSlot) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        // 双击窗口倒计时：归零时清空 shift 双击检测状态（对齐 1.20.1 源项目 containerTick）
        if (cleanHold > 0) {
            cleanHold--;
        } else {
            lastInvClickedStack = null;
            lastStorageClickedStack = ItemStackKey.EMPTY;
            lastInvClickedSlot = -1;
            cleanHold = 10;
        }
        for (GuiTextField textField : textFields) {
            textField.updateCursorCounter();
        }
    }
}
