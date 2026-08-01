package com.wintercogs.beyonddimensions.integration.module.nei;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.fluids.FluidStack;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.StackKeyRegistry;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.client.gui.GuiBase;
import com.wintercogs.beyonddimensions.client.gui.GuiDimensionsNet;
import com.wintercogs.beyonddimensions.common.init.BDPackets;
import com.wintercogs.beyonddimensions.common.menu.widget.slot.AbstractStackTypedSlot;
import com.wintercogs.beyonddimensions.network.packet.both.SetSlotDirectlyPacket;

import codechicken.nei.VisiblityData;
import codechicken.nei.api.INEIGuiHandler;
import codechicken.nei.api.TaggedInventoryArea;
import codechicken.nei.recipe.StackInfo;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * NEI GUI 处理器（1.7.10 适配版）。
 * <p>
 * 对应源项目（1.20.1）中的 JEI 集成：
 * - {@code NetInterfaceGhostHandler}（幽灵物品拖拽）→ {@link #handleDragNDrop}
 * - {@code JeiContainerHandler}（GUI 区域排除）→ {@link #hideItemPanelSlot}
 * <p>
 * 仅作用于 {@link GuiBase} 及其子类，将 NEI 物品面板的拖拽物品
 * 转换为 {@link KeyAmount} 并通过 {@link SetSlotDirectlyPacket} 写入 BD 自定义槽位。
 */
@SideOnly(Side.CLIENT)
public class NeiGuiHandler implements INEIGuiHandler {

    @Override
    public VisiblityData modifyVisiblity(GuiContainer gui, VisiblityData currentVisibility) {
        return currentVisibility;
    }

    @Override
    public Iterable<Integer> getItemSpawnSlots(GuiContainer gui, ItemStack item) {
        return new ArrayList<>();
    }

    @Override
    public List<TaggedInventoryArea> getInventoryAreas(GuiContainer gui) {
        return new ArrayList<>();
    }

    /**
     * 处理 NEI 物品面板拖拽至 BD GUI 的物品。
     * 仅当目标 GUI 为 {@link GuiBase} 且鼠标悬停在 {@link AbstractStackTypedSlot#isFake()}
     * 的自定义槽位时生效。
     * <p>
     * 逻辑：
     * 1. 遍历已注册的 StackKey 类型，寻找能解析 draggedStack 的类型
     * 2. 发送 SetSlotDirectlyPacket 在服务端直接设置槽位内容
     * 3. 将 draggedStack.stackSize 置 0 以让 NEI 删除光标上的幽灵物品
     *
     * @return true 表示拖拽已处理；false 表示交由其他处理器继续处理
     */
    @Override
    public boolean handleDragNDrop(GuiContainer gui, int mousex, int mousey, ItemStack draggedStack, int button) {
        if (!(gui instanceof GuiBase)) {
            return false;
        }
        GuiBase bdGui = (GuiBase) gui;

        // 拖拽物品（NEI 物品面板/收藏栏幽灵物品或光标持有物）到维度网络/合成界面搜索栏
        // → 用物品显示名填充搜索框（对齐 NEI SearchInputDropHandler 的"拖入搜索栏"UX：
        // 事件监听经 NEI handleDragNDrop 分发链，名称提取见 resolveDisplayName，
        // 命中即返回 true 消费点击，不销毁幽灵物品——与 NEI 一致可继续拖放）。
        if (bdGui instanceof GuiDimensionsNet) {
            GuiDimensionsNet netGui = (GuiDimensionsNet) bdGui;
            if (netGui.isSearchFieldHit(mousex, mousey)) {
                netGui.fillSearchFromItemName(resolveDisplayName(draggedStack));
                return true;
            }
        }

        Container container = bdGui.inventorySlots;
        if (container == null) {
            return false;
        }

        for (Object slotObj : container.inventorySlots) {
            if (!(slotObj instanceof AbstractStackTypedSlot)) {
                continue;
            }
            AbstractStackTypedSlot sSlot = (AbstractStackTypedSlot) slotObj;
            if (!sSlot.isActive() || !sSlot.isFake()) {
                continue;
            }
            // 槽位在屏幕上的实际坐标（guiLeft + slot.x，guiTop + slot.y）
            int slotX = bdGui.getGuiLeft() + sSlot.xDisplayPosition;
            int slotY = bdGui.getGuiTop() + sSlot.yDisplayPosition;
            if (mousex >= slotX && mousex < slotX + 16 && mousey >= slotY && mousey < slotY + 16) {
                // 命中槽位，尝试将 ItemStack 转换为 IStackKey
                IStackKey<?> dragging = resolveStackKey(draggedStack);
                if (dragging == null) {
                    return false;
                }
                BDPackets.INSTANCE
                    .sendToServer(new SetSlotDirectlyPacket(sSlot.slotNumber, new KeyAmount(dragging, 1)));
                draggedStack.stackSize = 0;
                return true;
            }
        }
        return false;
    }

    /**
     * 阻止 NEI 物品面板绘制在 BD 自定义槽位之上，避免视觉遮挡与误点击。
     */
    @Override
    public boolean hideItemPanelSlot(GuiContainer gui, int x, int y, int w, int h) {
        if (!(gui instanceof GuiBase)) {
            return false;
        }
        GuiBase bdGui = (GuiBase) gui;
        Container container = bdGui.inventorySlots;
        if (container == null) {
            return false;
        }
        for (Object slotObj : container.inventorySlots) {
            if (!(slotObj instanceof AbstractStackTypedSlot)) {
                continue;
            }
            AbstractStackTypedSlot sSlot = (AbstractStackTypedSlot) slotObj;
            if (!sSlot.isActive()) {
                continue;
            }
            int slotX = bdGui.getGuiLeft() + sSlot.xDisplayPosition;
            int slotY = bdGui.getGuiTop() + sSlot.yDisplayPosition;
            // 矩形相交检测
            if (x < slotX + 16 && x + w > slotX && y < slotY + 16 && y + h > slotY) {
                return true;
            }
        }
        return false;
    }

    /**
     * 提取拖拽物品的显示名（对齐 NEI {@code SearchField.getEscapedSearchText(ItemStack)} 的
     * 名称提取：流体容器经 {@link StackInfo#getFluid} 取流体本地化名，否则取物品显示名，
     * 再剥离格式码）。
     * <p>
     * 注意：不套用 NEI 的正则转义/引号包裹（patternMode / quoteDropItemName）——
     * BD 搜索为纯子串匹配（ClientNetStorageSearchHelper.matchesName → contains），
     * 转义字符会破坏匹配，故填充原始显示名。
     */
    private String resolveDisplayName(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return "";
        }
        FluidStack fluidStack = StackInfo.getFluid(stack);
        String displayName = fluidStack != null ? fluidStack.getLocalizedName() : stack.getDisplayName();
        return EnumChatFormatting.getTextWithoutFormattingCodes(displayName);
    }

    /**
     * 将 NEI 拖拽的 ItemStack 解析为对应的 IStackKey。
     * 优先匹配 ItemStackKey，再遍历其他已注册类型。
     */
    private IStackKey<?> resolveStackKey(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }
        // 优先尝试 ItemStackKey（最常见的情形）
        KeyAmount ka = ItemStackKey.EMPTY.fromStackObject(stack);
        if (ka != null) {
            return ka.key();
        }
        // 遍历其他已注册类型（如 FluidStackKey、ManaStackKey、GasStackKey 等）
        for (IStackKey<?> type : StackKeyRegistry.getAllTypes()) {
            if (type == ItemStackKey.EMPTY) {
                continue;
            }
            if (type.getStackClass()
                .isAssignableFrom(stack.getClass())) {
                KeyAmount result = type.fromStackObject(stack);
                if (result != null) {
                    return result.key();
                }
            }
        }
        return null;
    }
}
