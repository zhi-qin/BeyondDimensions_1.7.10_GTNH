package com.wintercogs.beyonddimensions.common.menu.widget.slot;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.wintercogs.beyonddimensions.api.storage.handler.IStackHandler;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.StackHandler;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EmptyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.init.BDFluids;
import com.wintercogs.beyonddimensions.common.init.BDPackets;
import com.wintercogs.beyonddimensions.common.item.XpExchangeItem;
import com.wintercogs.beyonddimensions.common.menu.BDBaseMenu;
import com.wintercogs.beyonddimensions.network.packet.s2c.OrderedStackTypedSlotPacket;

/**
 * 标记性槽位 - 简化版
 */
public class FlagStackTypedSlot extends AbstractStackTypedSlot {

    private KeyAmount lastStack = new KeyAmount(ItemStackKey.EMPTY, 0);

    public FlagStackTypedSlot(BDBaseMenu menu, IStackHandler storage, int slotIndex, int xPosition, int yPosition) {
        super(menu, storage, slotIndex, xPosition, yPosition);
        setFake(true);
    }

    @Override
    public boolean isOrdered() {
        return true;
    }

    @Override
    public void setStackDirectly(IStackKey<?> key, long amount) {
        storage.setStackDirectly(theSlot, key, amount);
    }

    @Override
    public KeyAmount safeInsert(IStackKey<?> key, long amount) {
        if (key != null) {
            setStackDirectly(key, amount);
        }
        return new KeyAmount(EmptyStackKey.INSTANCE, amount);
    }

    @Override
    public KeyAmount safeExtract(IStackKey<?> key, long amount) {
        clearFlagSource();
        setStackDirectly(ItemStackKey.EMPTY, 0);
        return new KeyAmount(ItemStackKey.EMPTY, amount);
    }

    @Override
    public void click(KeyAmount clickStack, int button, EntityPlayer player) {
        ItemStack carriedItem = menu.getCarried();
        ItemStack carriedCopy = carriedItem != null ? carriedItem.copy() : null;

        if (clickStack.isEmpty()) {
            if (carriedCopy != null) {
                // 新标记：来源记录随之失效（重新标记后需再次正向翻译才会记录新来源）
                clearFlagSource();
                // 对齐 1.20.1 源项目：槽位为空且光标有物品时，按按钮类型决定行为
                if (button == 0) {
                    // 左键：直接将光标物品设为标记
                    setStackDirectly(new ItemStackKey(carriedCopy), 1);
                } else if (button == 1) {
                    // 右键：特殊处理经验交换物品，将其转为 XP 流体标记
                    if (carriedCopy.getItem() instanceof XpExchangeItem) {
                        setStackDirectly(new FluidStackKey(new FluidStack(BDFluids.XP_FLUID, 1)), 1);
                    } else {
                        // 非经验物品右键：对齐 1.20.1 源项目——检测流体容器（桶/GT 单元），
                        // 标记内部流体为 FluidStackKey（如杂酚油桶可标记为燃料流体）；
                        // 非流体容器退回物品标记
                        FluidStack fluidInContainer = FluidContainerRegistry.getFluidForFilledItem(carriedCopy);
                        if (fluidInContainer != null) {
                            setStackDirectly(new FluidStackKey(fluidInContainer), 1);
                        } else {
                            setStackDirectly(new ItemStackKey(carriedCopy), 1);
                        }
                    }
                }
            }
        } else {
            // 槽位已有标记：光标为空或持有任意物品时，清空标记
            clearFlagSource();
            setStackDirectly(ItemStackKey.EMPTY, 0);
        }
    }

    @Override
    public void quickMove(KeyAmount clickStack, int button, EntityPlayer player) {
        click(clickStack, button, player);
    }

    /**
     * 翻译标记（对齐 NEI 的 SHIFT+滚轮交互，滚一下翻译一步）。
     * <p>
     * 对齐 1.20.1 源项目点击语义的扩展：源项目右键可用"装好流体的容器"（桶/GT 单元）
     * 把标记翻译成流体，但左键标记的单元本身仍是物品标记；此处提供 SHIFT+滚轮在
     * "物品标记 ↔ 容器内流体标记"之间循环翻译，使"标记一个流体单元也能直接出流体"。
     * <p>
     * direction &gt; 0：物品 → 容器内流体（记录来源物品）；direction &lt; 0：流体 → 还原
     * 为标记时记录的原物品（无记录则无操作，不遍历注册表——避免还原成其它注册容器，
     * 如 GT 蒸汽会被还原成 Railcraft 蒸汽瓶）。
     * 无可翻译对象时无操作（不会清空标记）。
     */
    public void translateFlag(int direction) {
        KeyAmount current = storage.getStackBySlot(theSlot);
        if (current == null || current.isEmpty()) return;

        if (current.key() instanceof ItemStackKey) {
            // 物品标记 → 容器内流体标记（与右键点击同一检测途径，保持一致）
            ItemStack stack = ((ItemStackKey) current.key()).copyStackWithCount(1);
            if (stack == null) return;
            FluidStack fluidInContainer = FluidContainerRegistry.getFluidForFilledItem(stack);
            if (fluidInContainer != null) {
                // 记录来源物品，反向翻译时精确还原
                setFlagSource(current.key());
                setStackDirectly(new FluidStackKey(fluidInContainer), 1);
                updateChange();
            }
        } else if (direction < 0 && current.key() instanceof FluidStackKey) {
            // 流体标记 → 还原为标记时记录的原物品（无记录则无操作）
            IStackKey<?> source = getFlagSource();
            if (source instanceof ItemStackKey) {
                clearFlagSource();
                setStackDirectly(source, 1);
                updateChange();
            }
        }
    }

    // ==================== 标记来源物品（反向翻译精确还原） ====================

    private StackHandler asStackHandler() {
        return storage instanceof StackHandler sh ? sh : null;
    }

    private void setFlagSource(IStackKey<?> key) {
        StackHandler sh = asStackHandler();
        if (sh != null) sh.setFlagSource(theSlot, key);
    }

    private IStackKey<?> getFlagSource() {
        StackHandler sh = asStackHandler();
        return sh != null ? sh.getFlagSource(theSlot) : null;
    }

    private void clearFlagSource() {
        StackHandler sh = asStackHandler();
        if (sh != null) sh.clearFlagSource(theSlot);
    }

    @Override
    public void updateChange() {
        // 对齐 1.20.1 源项目：检测标记槽位变化并发送同步包到客户端
        KeyAmount currentStack = storage.getStackBySlot(this.getSlotIndex());
        if (lastStack.amount() != currentStack.amount() || !lastStack.key()
            .getTypeId()
            .equals(
                currentStack.key()
                    .getTypeId())
            || !lastStack.key()
                .isSameTypeSameComponents(currentStack.key())) {
            lastStack = currentStack;
            if (menu.player instanceof EntityPlayerMP) {
                BDPackets.INSTANCE.sendTo(
                    new OrderedStackTypedSlotPacket(this.slotNumber, theSlot, lastStack.key(), lastStack.amount()),
                    (EntityPlayerMP) menu.player);
            }
        }
    }

    @Override
    public void loadChange(int where, IStackKey<?> newKey, long newAmount) {
        storage.setStackDirectly(where, newKey, newAmount);
    }
}
