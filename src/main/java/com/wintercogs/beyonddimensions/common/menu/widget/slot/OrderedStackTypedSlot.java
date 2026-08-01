package com.wintercogs.beyonddimensions.common.menu.widget.slot;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.wintercogs.beyonddimensions.api.storage.handler.IStackHandler;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EmptyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.init.BDPackets;
import com.wintercogs.beyonddimensions.common.menu.BDBaseMenu;
import com.wintercogs.beyonddimensions.network.packet.s2c.OrderedStackTypedSlotPacket;
import com.wintercogs.beyonddimensions.util.BDMath;

/**
 * 有序存储槽位 - 简化版
 */
public class OrderedStackTypedSlot extends AbstractStackTypedSlot {

    private KeyAmount lastStack = new KeyAmount(EmptyStackKey.INSTANCE, 0);

    public OrderedStackTypedSlot(BDBaseMenu menu, IStackHandler stackTypedHandler, int slotIndex, int xPosition,
        int yPosition) {
        super(menu, stackTypedHandler, slotIndex, xPosition, yPosition);
    }

    public OrderedStackTypedSlot(BDBaseMenu menu, IStackHandler stackTypedHandler, int slotIndex,
        int quickMoveSlotStartIndex, int quickMoveSlotEndIndex, int xPosition, int yPosition) {
        super(menu, stackTypedHandler, slotIndex, quickMoveSlotStartIndex, quickMoveSlotEndIndex, xPosition, yPosition);
    }

    @Override
    public boolean isOrdered() {
        return true;
    }

    @Override
    public void click(KeyAmount clickStack, int button, EntityPlayer player) {
        ItemStack carriedItem = menu.getCarried();
        ItemStack carriedCopy = carriedItem != null ? carriedItem.copy() : null;

        if (clickStack.isEmpty()) {
            if (carriedCopy != null) {
                // 槽位为空，光标有物品：先检查桶交互（满桶右键 → 回填网络，光标变空桶；
                // 对齐 1.20.1 源项目 click 的 capability 桶分支）
                if (button == 1 && tryDrainBucketIntoStorage(carriedCopy)) {
                    return;
                }
                // 槽位为空，将携带物品插入
                int changedCount = button == 0 ? carriedCopy.stackSize : 1;
                int remaining = (int) storage.insert(getSlotIndex(), new ItemStackKey(carriedCopy), changedCount, false)
                    .amount();
                int actualInsert = changedCount - remaining;
                int newCount = carriedCopy.stackSize - actualInsert;
                if (newCount <= 0) {
                    menu.setCarried(null);
                } else {
                    ItemStack newCarried = carriedCopy.copy();
                    newCarried.stackSize = newCount;
                    menu.setCarried(newCarried);
                }
            }
        } else if (mayPickup(player)) {
            if (carriedCopy == null) {
                // 槽位有物品，光标为空，取出
                if (clickStack.key() instanceof ItemStackKey) {
                    ItemStackKey clickKey = (ItemStackKey) clickStack.key();
                    int woundChangeNum = BDMath
                        .clampLongToInt(Math.min(clickStack.amount(), clickKey.getVanillaMaxStackSize()));
                    int actualChangeNum = button == 0 ? woundChangeNum : (woundChangeNum + 1) / 2;
                    KeyAmount extracted = storage.extract(getSlotIndex(), actualChangeNum, false);
                    if (!extracted.isEmpty() && extracted.toStack() instanceof ItemStack) {
                        menu.setCarried((ItemStack) extracted.toStack());
                    }
                }
            } else if (mayPlace(carriedCopy)) {
                // 槽位有物品，光标有物品：先检查桶交互（空桶右键流体槽 → 盛出；满桶右键 → 回填）
                if (button == 1 && tryBucketInteraction(clickStack, carriedCopy)) {
                    return;
                }
                if (clickStack.key()
                    .isSameTypeSameComponents(new ItemStackKey(carriedCopy))) {
                    // 相同类型，尝试插入
                    int changedCount = button == 0 ? carriedCopy.stackSize : 1;
                    int remaining = (int) storage
                        .insert(getSlotIndex(), new ItemStackKey(carriedCopy), changedCount, false)
                        .amount();
                    int actualInsert = changedCount - remaining;
                    int newCount = carriedCopy.stackSize - actualInsert;
                    if (newCount <= 0) {
                        menu.setCarried(null);
                    } else {
                        ItemStack newCarried = carriedCopy.copy();
                        newCarried.stackSize = newCount;
                        menu.setCarried(newCarried);
                    }
                } else {
                    // 不同类型，交换物品
                    if (button == 0) {
                        KeyAmount actualStack = getTypedStackFromUnifiedStorage();
                        if (actualStack.key() instanceof ItemStackKey && carriedCopy.stackSize <= getSlotCap()
                            && actualStack.amount() <= actualStack.key()
                                .getVanillaMaxStackSize()) {
                            KeyAmount extract = storage.extract(getSlotIndex(), actualStack.amount(), false);
                            KeyAmount remaining = storage
                                .insert(getSlotIndex(), new ItemStackKey(carriedCopy), carriedCopy.stackSize, true);
                            if (remaining.isEmpty() && extract.key() instanceof ItemStackKey) {
                                ItemStackKey extractedItemKey = (ItemStackKey) extract.key();
                                storage.insert(
                                    getSlotIndex(),
                                    new ItemStackKey(carriedCopy),
                                    carriedCopy.stackSize,
                                    false);
                                menu.setCarried(extractedItemKey.copyStackWithCount(extract.amount()));
                            } else {
                                storage.insert(getSlotIndex(), extract.key(), extract.amount(), false);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 桶交互（对齐 1.20.1 源项目 OrderedStackTypedSlot.click 的 BucketItem 分支）：
     * <ol>
     * <li>空桶右键流体槽 → 从网络盛出 1000mB，光标变满桶；</li>
     * <li>满桶右键 → 回填网络（桶必须完全清空），光标变空桶。</li>
     * </ol>
     *
     * @return true 表示已处理，调用方应跳过普通插入/交换逻辑
     */
    private boolean tryBucketInteraction(KeyAmount clickStack, ItemStack carriedCopy) {
        // 空桶盛出：仅处理原版空桶 + 已注册桶容器（1.7.10 Fluid 无可靠 getBucket()，
        // 经 FluidContainerRegistry 查找满桶，同 quickMove 惯例）
        if (carriedCopy.getItem() == Items.bucket && clickStack.key() instanceof FluidStackKey) {
            FluidStackKey fluidKey = (FluidStackKey) clickStack.key();
            Fluid fluid = fluidKey.getSource();
            if (fluid != null) {
                ItemStack filledBucket = FluidContainerRegistry
                    .fillFluidContainer(new FluidStack(fluid, 1000), new ItemStack(Items.bucket));
                if (filledBucket != null && clickStack.amount() >= 1000) {
                    storage.extract(getSlotIndex(), 1000, false);
                    menu.setCarried(filledBucket);
                    return true;
                }
            }
        }
        return tryDrainBucketIntoStorage(carriedCopy);
    }

    /**
     * 满桶回填网络（对齐源项目 capability 桶分支：桶必须完全清空才允许操作，
     * 防止把半桶混入网络）。
     */
    private boolean tryDrainBucketIntoStorage(ItemStack carriedCopy) {
        FluidStack fluidInBucket = FluidContainerRegistry.getFluidForFilledItem(carriedCopy);
        if (fluidInBucket == null || carriedCopy.stackSize != 1) {
            return false;
        }
        FluidStackKey bucketKey = new FluidStackKey(fluidInBucket);
        long changedCount = Math.min((long) fluidInBucket.amount, bucketKey.getVanillaMaxStackSize());
        // 模拟插入，必须完全清空才允许操作
        long remaining = storage.insert(bucketKey, changedCount, true)
            .amount();
        if (remaining > 0) {
            return false;
        }
        storage.insert(bucketKey, changedCount, false);
        // 还原正确的空容器（粘土桶倒空应得空粘土桶而非铁桶）；兜底保留原铁桶行为
        ItemStack emptyContainer = FluidContainerRegistry.drainFluidContainer(carriedCopy);
        menu.setCarried(emptyContainer != null ? emptyContainer : new ItemStack(Items.bucket));
        return true;
    }

    private boolean mayPickup(EntityPlayer player) {
        return !fake;
    }

    private boolean mayPlace(ItemStack stack) {
        return !fake;
    }

    @Override
    public void quickMove(KeyAmount clickStack, int button, EntityPlayer player) {
        if (!(quickMoveSlotStartIndex >= 0 && quickMoveSlotEndIndex >= 0
            && quickMoveSlotStartIndex < quickMoveSlotEndIndex)) return;
        if (clickStack.isEmpty()) return;

        KeyAmount trueStack = new KeyAmount(
            storage.getStackBySlot(theSlot)
                .key(),
            clickStack.amount());

        for (int targetSlotIndex = quickMoveSlotStartIndex; targetSlotIndex < quickMoveSlotEndIndex
            && !trueStack.isEmpty(); targetSlotIndex++) {
            Slot slot = (Slot) menu.inventorySlots.get(targetSlotIndex);
            if (slot instanceof AbstractStackTypedSlot) {
                AbstractStackTypedSlot aSlot = (AbstractStackTypedSlot) slot;
                KeyAmount extract = safeExtract(trueStack.key(), trueStack.amount());
                KeyAmount remaining = aSlot.safeInsert(extract.key(), extract.amount());
                if (!remaining.isEmpty()) safeInsert(remaining.key(), remaining.amount());
                trueStack = remaining;
            } else {
                if (trueStack.key() instanceof ItemStackKey) {
                    ItemStackKey trueItemTypedKey = (ItemStackKey) trueStack.key();
                    KeyAmount extractKA = safeExtract(trueItemTypedKey, trueStack.amount());
                    if (!extractKA.isEmpty() && extractKA.toStack() instanceof ItemStack) {
                        ItemStack remaining = safeInsertIntoVanillaSlot(slot, (ItemStack) extractKA.toStack());
                        if (remaining != null && remaining.stackSize > 0) {
                            safeInsert(new ItemStackKey(remaining), remaining.stackSize);
                        }
                        trueStack = remaining != null && remaining.stackSize > 0
                            ? new KeyAmount(new ItemStackKey(remaining), remaining.stackSize)
                            : new KeyAmount(ItemStackKey.EMPTY, 0);
                    }
                } else if (trueStack.key() instanceof FluidStackKey) {
                    // 对齐 1.20.1 源项目：移动流体并装桶
                    // 1.7.10 中 Fluid 无 getBucket()，改用 FluidContainerRegistry 查找满桶
                    FluidStackKey trueFluidTypedKey = (FluidStackKey) trueStack.key();
                    Fluid fluid = trueFluidTypedKey.getSource();
                    if (fluid != null) {
                        ItemStack filledBucket = FluidContainerRegistry
                            .fillFluidContainer(new FluidStack(fluid, 1000), new ItemStack(Items.bucket));
                        if (filledBucket != null) {
                            KeyAmount extract = safeExtract(trueFluidTypedKey, 1000);
                            if (extract.amount() != 1000) {
                                safeInsert(extract.key(), extract.amount());
                                break;
                            }

                            KeyAmount bucket = storage
                                .extract(new ItemStackKey(new ItemStack(Items.bucket)), 1, false, false);
                            if (bucket.isEmpty()) {
                                safeInsert(extract.key(), extract.amount());
                                break;
                            }

                            ItemStack remaining = safeInsertIntoVanillaSlot(slot, filledBucket);
                            if (remaining != null && remaining.stackSize > 0) {
                                safeInsert(extract.key(), extract.amount());
                                storage.insert(bucket.key(), bucket.amount(), false);
                                continue;
                            }
                            trueStack = new KeyAmount(trueFluidTypedKey, trueStack.amount() - 1000);
                            break; // 一次点击最多只成功装桶一次
                        }
                    }
                }
            }
        }
        onSlotChanged();
    }

    @Override
    public KeyAmount safeInsert(IStackKey<?> key, long amount) {
        if (key != null) {
            return storage.insert(theSlot, key, amount, false);
        }
        return new KeyAmount(ItemStackKey.EMPTY, 0);
    }

    @Override
    public KeyAmount safeExtract(IStackKey<?> key, long amount) {
        KeyAmount actual = getTypedStackFromUnifiedStorage();
        if (key != null && key.getTypeId()
            .equals(
                actual.key()
                    .getTypeId())
            && key.isSameTypeSameComponents(actual.key())) {
            return storage.extract(theSlot, amount, false);
        }
        return new KeyAmount(EmptyStackKey.INSTANCE, amount);
    }

    @Override
    public void updateChange() {
        // 对齐 1.20.1 源项目：检测有序槽位变化并发送同步包到客户端
        KeyAmount currentStack = storage.getStackBySlot(this.getSlotIndex());
        boolean changed = lastStack.amount() != currentStack.amount() || !lastStack.key()
            .getTypeId()
            .equals(
                currentStack.key()
                    .getTypeId())
            || !lastStack.key()
                .isSameTypeSameComponents(currentStack.key());
        if (changed) {
            lastStack = currentStack;
            if (menu.player instanceof EntityPlayerMP) {
                BDPackets.INSTANCE.sendTo(
                    new OrderedStackTypedSlotPacket(this.slotNumber, theSlot, lastStack.key(), lastStack.amount()),
                    (EntityPlayerMP) menu.player);
            }
        }
    }

    @Override
    public void loadChange(int where, IStackKey<?> newStack, long newAmount) {
        storage.setStackDirectly(where, newStack, newAmount);
    }
}
