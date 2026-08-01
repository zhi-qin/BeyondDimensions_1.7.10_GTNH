package com.wintercogs.beyonddimensions.common.menu.widget.slot;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.wintercogs.beyonddimensions.api.storage.handler.IStackHandler;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.menu.BDBaseMenu;
import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;
import com.wintercogs.beyonddimensions.util.BDMath;

/**
 * 无序存储槽位 - 简化版（移除能力系统交互）
 */
public class DisorderedStackTypedSlot extends AbstractStackTypedSlot {

    /** 槽位在存储槽中的绝对位置（addStorageSlots 时记录），供 NEI 合成链精准暴露使用（方案 A） */
    private int storageSlotPosition = -1;

    public void setStorageSlotPosition(int pos) {
        this.storageSlotPosition = pos;
    }

    public DisorderedStackTypedSlot(BDBaseMenu menu, IStackHandler stackTypedHandler, int slotIndex, int xPosition,
        int yPosition) {
        super(menu, stackTypedHandler, slotIndex, xPosition, yPosition);
    }

    public DisorderedStackTypedSlot(BDBaseMenu menu, IStackHandler stackTypedHandler, int slotIndex,
        int quickMoveSlotStartIndex, int quickMoveSlotEndIndex, int xPosition, int yPosition) {
        super(menu, stackTypedHandler, slotIndex, quickMoveSlotStartIndex, quickMoveSlotEndIndex, xPosition, yPosition);
    }

    @Override
    public boolean isOrdered() {
        return false;
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
                int actualInsert = (int) (changedCount
                    - storage.insert(new ItemStackKey(carriedCopy), changedCount, false)
                        .amount());
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
                    KeyAmount extracted = storage.extract(clickKey, actualChangeNum, false, false);
                    if (!extracted.isEmpty() && extracted.toStack() instanceof ItemStack) {
                        menu.setCarried((ItemStack) extracted.toStack());
                    }
                }
            } else if (mayPlace(carriedCopy)) {
                // 槽位有物品，光标有物品：先检查桶交互（空桶右键流体槽 → 盛出；满桶右键 → 回填）
                if (button == 1 && tryBucketInteraction(clickStack, carriedCopy)) {
                    return;
                }
                // 槽位有物品，光标有物品，尝试插入
                int changedCount = button == 0 ? carriedCopy.stackSize : 1;
                int actualInsert = (int) (changedCount
                    - storage.insert(new ItemStackKey(carriedCopy), changedCount, false)
                        .amount());
                int newCount = carriedCopy.stackSize - actualInsert;
                if (newCount <= 0) {
                    menu.setCarried(null);
                } else {
                    ItemStack newCarried = carriedCopy.copy();
                    newCarried.stackSize = newCount;
                    menu.setCarried(newCarried);
                }
            }
        }
    }

    /**
     * 桶交互（对齐 1.20.1 源项目 DisorderedStackTypedSlot.click 的 BucketItem 分支）：
     * <ol>
     * <li>空桶右键流体槽 → 从网络盛出 1000mB，光标变满桶；</li>
     * <li>满桶右键 → 回填网络（桶必须完全清空），光标变空桶。</li>
     * </ol>
     *
     * @return true 表示已处理，调用方应跳过普通插入逻辑
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
                if (filledBucket != null && storage.getStackByKey(fluidKey)
                    .amount() >= 1000) {
                    storage.extract(fluidKey, 1000, false, false);
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

        KeyAmount trueStack = new KeyAmount(clickStack.key(), clickStack.amount());

        for (int targetSlotIndex = quickMoveSlotStartIndex; targetSlotIndex < quickMoveSlotEndIndex
            && !trueStack.isEmpty(); targetSlotIndex++) {
            Slot slot = (Slot) menu.inventorySlots.get(targetSlotIndex);
            if (slot instanceof AbstractStackTypedSlot) {
                AbstractStackTypedSlot aSlot = (AbstractStackTypedSlot) slot;
                KeyAmount extract = storage.extract(trueStack.key(), trueStack.amount(), false, false);
                KeyAmount remaining = aSlot.safeInsert(extract.key(), extract.amount());
                if (!remaining.isEmpty()) storage.insert(remaining.key(), remaining.amount(), false);
                trueStack = remaining;
            } else {
                IStackKey<?> key = trueStack.key();
                if (key instanceof ItemStackKey) {
                    ItemStackKey trueItemTypedKey = (ItemStackKey) key;
                    KeyAmount extractKA = storage.extract(trueItemTypedKey, trueStack.amount(), false, false);
                    if (!extractKA.isEmpty() && extractKA.toStack() instanceof ItemStack) {
                        ItemStack extract = (ItemStack) extractKA.toStack();
                        ItemStack remaining = safeInsertIntoVanillaSlot(slot, extract);
                        if (remaining != null && remaining.stackSize > 0) {
                            storage.insert(new ItemStackKey(remaining), remaining.stackSize, false);
                        }
                        trueStack = remaining != null && remaining.stackSize > 0
                            ? new KeyAmount(new ItemStackKey(remaining), remaining.stackSize)
                            : new KeyAmount(ItemStackKey.EMPTY, 0);
                    }
                } else if (key instanceof FluidStackKey) {
                    // 对齐 1.20.1 源项目：shift 点击流体槽 → 自动装桶（从网络取空桶 + 盛 1000mB → 满桶入槽）
                    // 1.7.10 中 Fluid 无可靠 getBucket()，经 FluidContainerRegistry 查找满桶（同 Ordered quickMove 惯例）
                    FluidStackKey trueFluidTypedKey = (FluidStackKey) key;
                    Fluid fluid = trueFluidTypedKey.getSource();
                    if (fluid != null) {
                        ItemStack filledBucket = FluidContainerRegistry
                            .fillFluidContainer(new FluidStack(fluid, 1000), new ItemStack(Items.bucket));
                        if (filledBucket != null) {
                            KeyAmount extract = storage.extract(trueFluidTypedKey, 1000, false, false);
                            if (extract.amount() != 1000) {
                                storage.insert(extract.key(), extract.amount(), false);
                                break;
                            }
                            KeyAmount bucket = storage
                                .extract(new ItemStackKey(new ItemStack(Items.bucket)), 1, false, false);
                            if (bucket.isEmpty()) {
                                storage.insert(extract.key(), extract.amount(), false);
                                break;
                            }
                            ItemStack remaining = safeInsertIntoVanillaSlot(slot, filledBucket);
                            if (remaining != null && remaining.stackSize > 0) {
                                storage.insert(extract.key(), extract.amount(), false);
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
    public KeyAmount safeInsert(IStackKey<?> stack, long amount) {
        if (stack != null) {
            return storage.insert(stack, amount, false);
        }
        return new KeyAmount(ItemStackKey.EMPTY, 0);
    }

    @Override
    public KeyAmount safeExtract(IStackKey<?> stack, long amount) {
        if (stack != null) {
            return storage.extract(stack, amount, false, false);
        }
        return new KeyAmount(ItemStackKey.EMPTY, 0);
    }

    @Override
    public void updateChange() {
        // 无序槽位由 DisorderedSlotGroupSync 负责同步
    }

    @Override
    public void loadChange(int where, IStackKey<?> newStack, long newAmount) {
        // 无序槽位由 DisorderedSlotGroupSync 负责加载
    }

    @Override
    public ItemStack getStack() {
        int slotIdx = getSlotIndex();
        if (slotIdx < 0) {
            // 非活跃槽位：屏幕外（yDisplayPosition=-9999）GUI 不可见/不可点，
            // 仅供 NEI 合成链检查读取。方案 A：按槽位位置读菜单的链条目（无全量索引、无槽位池扩展）。
            // 注意：getNeiExposureEntry 是 @SideOnly(CLIENT) 方法，专用服务器上会被 FML 剥离，
            // 必须先以 worldObj.isRemote 守卫，否则服务端（恶意/第三方点击包、shift 转移等）
            // 触发本分支即 NoSuchMethodError 崩服；服务端直接返回空。
            if (menu.player.worldObj.isRemote && menu instanceof DimensionsNetMenu) {
                KeyAmount ka = ((DimensionsNetMenu) menu).getNeiExposureEntry(storageSlotPosition);
                if (ka != null && !ka.isEmpty() && ka.key() instanceof ItemStackKey) {
                    ItemStack s = ((ItemStackKey) ka.key()).getReadOnlyStack();
                    s.stackSize = BDMath.clampLongToInt(ka.amount());
                    return s.copy();
                }
            }
            return null;
        }
        ItemStack itemStack = getItemStackFromUnifiedStorage();
        if (itemStack == null) return null;
        return itemStack.copy();
    }

    @Override
    public boolean getHasStack() {
        if (getSlotIndex() < 0) {
            // 与 getStack 同理：先守卫客户端，服务端不触碰 @SideOnly(CLIENT) 方法
            if (menu.player.worldObj.isRemote && menu instanceof DimensionsNetMenu) {
                KeyAmount ka = ((DimensionsNetMenu) menu).getNeiExposureEntry(storageSlotPosition);
                return ka != null && !ka.isEmpty();
            }
            return false;
        }
        return !storage.getStackBySlot(getSlotIndex())
            .isEmpty();
    }
}
