package com.wintercogs.beyonddimensions.common.menu;

import java.util.*;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.inventory.SlotCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.S2FPacketSetSlot;

import com.wintercogs.beyonddimensions.BeyondDimensions;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.init.BDPackets;
import com.wintercogs.beyonddimensions.common.menu.widget.slot.AbstractStackTypedSlot;
import com.wintercogs.beyonddimensions.common.menu.widget.slot.DisorderedSlotGroupSync;
import com.wintercogs.beyonddimensions.common.menu.widget.slot.SlotGroupSync;
import com.wintercogs.beyonddimensions.network.packet.both.QuickDataTagPacket;

/**
 * 超越维度模组UI界面的基类Container（1.7.10 移植版）。
 * 重写网络同步和点击事件，确保父类机制不处理AbstractStackTypedSlot的相关内容。
 * <p>
 * 1.7.10 适配：AbstractContainerMenu → Container；Inventory → InventoryPlayer；
 * Player → EntityPlayer；addSlot() → addSlotToContainer()；
 * broadcastChanges → detectAndSendChanges + 自定义同步。
 */
public abstract class BDBaseMenu extends Container {

    public final EntityPlayer player;
    /** 玩家背包的槽位索引范围 [inventoryStartIndex, inventoryEndIndex) */
    public int inventoryStartIndex = -1;
    public int inventoryEndIndex = -1;

    /** 原版槽位想要进行快速转移时的目标槽位范围 */
    protected int vanillaQuickMoveStartIndex = -1;
    protected int vanillaQuickMoveEndIndex = -1;

    private boolean init = false;
    protected List<AbstractStackTypedSlot> updatedSlots = new ArrayList<>();
    public List<SlotGroupSync> slotGroupSyncs = new ArrayList<>();

    /** 用于检测光标物品变化并向客户端同步的上一次缓存 */
    private ItemStack lastCarried;

    protected BDBaseMenu(InventoryPlayer inventory) {
        this.player = inventory.player;
        this.lastCarried = inventory.player.inventory.getItemStack();
    }

    protected void addSlotGroupSync(SlotGroupSync slotGroupSync) {
        slotGroupSyncs.add(slotGroupSync);
    }

    @Override
    protected Slot addSlotToContainer(Slot slot) {
        if (slot instanceof AbstractStackTypedSlot) {
            updatedSlots.add((AbstractStackTypedSlot) slot);
        }
        return super.addSlotToContainer(slot);
    }

    /**
     * 1.7.10 兼容：获取玩家光标上的物品
     */
    public ItemStack getCarried() {
        return player.inventory.getItemStack();
    }

    /**
     * 1.7.10 兼容：设置玩家光标上的物品
     */
    public void setCarried(ItemStack stack) {
        player.inventory.setItemStack(stack);
    }

    @Override
    public void detectAndSendChanges() {
        // 在原版方法上剔除了对AbstractStackTypedSlot的处理
        for (int i = 0; i < this.inventorySlots.size(); ++i) {
            Slot slot = (Slot) this.inventorySlots.get(i);
            if (slot instanceof AbstractStackTypedSlot) continue;

            ItemStack itemstack = slot.getStack();
            ItemStack itemstack1 = (ItemStack) this.inventoryItemStacks.get(i);

            if (!ItemStack.areItemStacksEqual(itemstack1, itemstack)) {
                itemstack1 = itemstack == null ? null : itemstack.copy();
                this.inventoryItemStacks.set(i, itemstack1);

                for (int j = 0; j < this.crafters.size(); ++j) {
                    ((ICrafting) this.crafters.get(j)).sendSlotContents(this, i, itemstack1);
                }
            }
        }

        // 自定义同步
        if (!player.worldObj.isRemote) {
            if (!init) {
                initUpdate();
                init = true;
            }

            if (shouldSendQuickData()) {
                NBTTagCompound updateTag = new NBTTagCompound();
                writeQuickDataTag(updateTag);
                BDPackets.INSTANCE.sendTo(new QuickDataTagPacket(updateTag), (EntityPlayerMP) player);
            }

            setSlotGroupSyncsUpdate();
            abstractSlotsUpdate();
            updateChange();

            // 同步光标物品（自定义点击路径不会触发原版的光标同步）
            ItemStack currentCarried = player.inventory.getItemStack();
            if (!ItemStack.areItemStacksEqual(this.lastCarried, currentCarried)) {
                this.lastCarried = currentCarried == null ? null : currentCarried.copy();
                ((EntityPlayerMP) player).playerNetServerHandler
                    .sendPacket(new S2FPacketSetSlot(-1, -1, currentCarried));
            }
        }
    }

    /** 是否应该发送快速更新 */
    protected boolean shouldSendQuickData() {
        return false;
    }

    /** 双端可用的快速更新（写入） */
    protected void writeQuickDataTag(NBTTagCompound tag) {}

    /** 双端可用的快速读取 */
    public void readQuickDataTag(NBTTagCompound tag) {}

    public void writeAndSendQuickData() {
        NBTTagCompound updateTag = new NBTTagCompound();
        writeQuickDataTag(updateTag);
        if (player.worldObj.isRemote) {
            BDPackets.INSTANCE.sendToServer(new QuickDataTagPacket(updateTag));
        } else {
            BDPackets.INSTANCE.sendTo(new QuickDataTagPacket(updateTag), (EntityPlayerMP) player);
        }
    }

    /** 槽位更新 */
    protected void abstractSlotsUpdate() {
        for (AbstractStackTypedSlot slot : updatedSlots) {
            slot.updateChange();
        }
    }

    /** 槽位组更新 */
    protected void setSlotGroupSyncsUpdate() {
        for (SlotGroupSync slotGroupSync : slotGroupSyncs) {
            slotGroupSync.updateChange();
        }
    }

    /** 仅由服务端发送的更新 */
    protected void updateChange() {}

    /** 仅由服务端发送一次的更新 */
    protected void initUpdate() {}

    /**
     * 双击收集防护（Bug #72 附带）：原版 slotClick mode 6（双击收集同类物品）会遍历容器
     * 全部槽位，但含类型槽（AbstractStackTypedSlot）的容器中，类型槽 getStack() 对
     * 能量/流体等非物品条目返回 null、decrStackSize() 返回 null，原版收集循环会触发空指针
     * （快速连点生成的双击事件即崩）。此处对 mode 6 直接作为空操作返回 null——双端返回值
     * 恒等，反同步比对不触发，双击收集在本类容器中停用（与 AE2 对自定义槽位的处理一致）。
     * 不含类型槽的菜单不受影响。
     */
    @Override
    public ItemStack slotClick(int slotId, int clickedButton, int mode, EntityPlayer player) {
        if (mode == 6 && !updatedSlots.isEmpty()) {
            return null;
        }
        return super.slotClick(slotId, clickedButton, mode, player);
    }

    /**
     * 自定义点击操作
     */
    public void customClickHandler(int slotIndex, KeyAmount clickedStack, int button, boolean shiftDown) {
        if (inventoryStartIndex < 0 || inventoryEndIndex < 0) {
            BeyondDimensions.LOGGER.info("警告:背包索引设置错误！！！");
        }

        if (slotIndex >= 0 && slotIndex < inventorySlots.size()) {
            Slot slot = (Slot) inventorySlots.get(slotIndex);
            if (slot instanceof AbstractStackTypedSlot) {
                AbstractStackTypedSlot typedSlot = (AbstractStackTypedSlot) slot;
                if (shiftDown) {
                    typedSlot.quickMove(clickedStack, button, player);
                } else {
                    typedSlot.click(clickedStack, button, player);
                }
            } else {
                if (shiftDown && vanillaQuickMoveEndIndex >= 0
                    && vanillaQuickMoveStartIndex >= 0
                    && vanillaQuickMoveStartIndex < vanillaQuickMoveEndIndex) {
                    quickMoveHandle(
                        player,
                        slotIndex,
                        clickedStack,
                        vanillaQuickMoveStartIndex,
                        vanillaQuickMoveEndIndex);
                } else if (!shiftDown && slot instanceof SlotCrafting) {
                    // 结果槽普通点击：服务端权威执行原版拾取/消耗/补料逻辑。
                    // 客户端不再预测结果槽点击（GuiBase 改走 CallSeverClickPacket），
                    // 此处直接执行 slotClick 即可，无 1.7.10 processClickWindow 反同步比对。
                    this.slotClick(slotIndex, button, 0, player);
                }
            }
        }
    }

    /**
     * 处理非AbstractStackTypedSlot槽位的快速转移（1.7.10 简化版）
     */
    protected ItemStack quickMoveHandle(EntityPlayer player, int slotIndex, KeyAmount clickStack, int targetStartIndex,
        int targetEndIndex) {
        Slot slot = (Slot) this.inventorySlots.get(slotIndex);
        if (slot != null && !clickStack.isEmpty()) {
            ItemStack slotStack = slot.getStack();
            if (slotStack == null) return null;

            // 快速合成处理
            if (slot instanceof SlotCrafting) {
                ItemStack cacheStack = slotStack.copy();
                for (int i = 0; slot.getHasStack() && slot.getStack()
                    .getItem() == cacheStack.getItem()
                    && i < slot.getStack()
                        .getMaxStackSize() / Math.max(1, slot.getStack().stackSize); i++) {

                    ItemStack remaining = cacheStack.copy();
                    Map<Integer, Integer> insertedToInv = new HashMap<>();
                    Map<Integer, Integer> insertedToSlots = new HashMap<>();

                    // 先尝试插入背包
                    for (int invSlot = inventoryStartIndex; invSlot < inventoryEndIndex
                        && remaining.stackSize > 0; invSlot++) {
                        Slot targetSlot = (Slot) inventorySlots.get(invSlot);
                        if (!targetSlot.isItemValid(remaining)) continue;
                        ItemStack targetStack = targetSlot.getStack();
                        int canInsert;
                        if (targetStack == null) {
                            canInsert = Math.min(remaining.stackSize, targetSlot.getSlotStackLimit());
                            targetSlot.putStack(remaining.splitStack(canInsert));
                        } else if (targetStack.isItemEqual(remaining)
                            && ItemStack.areItemStackTagsEqual(targetStack, remaining)) {
                                canInsert = Math
                                    .min(remaining.stackSize, targetSlot.getSlotStackLimit() - targetStack.stackSize);
                                if (canInsert > 0) {
                                    targetStack.stackSize += canInsert;
                                    remaining.stackSize -= canInsert;
                                }
                            } else {
                                canInsert = 0;
                            }
                        if (canInsert > 0) {
                            insertedToInv.put(invSlot, canInsert);
                        }
                    }

                    // 再尝试插入目标槽位
                    for (int targetSlotIndex = targetStartIndex; targetSlotIndex < targetEndIndex
                        && remaining.stackSize > 0; targetSlotIndex++) {
                        Slot targetSlot = (Slot) inventorySlots.get(targetSlotIndex);
                        int newSize;
                        if (targetSlot instanceof AbstractStackTypedSlot) {
                            AbstractStackTypedSlot aTargetSlot = (AbstractStackTypedSlot) targetSlot;
                            KeyAmount remainingKA = aTargetSlot
                                .safeInsert(new ItemStackKey(remaining), remaining.stackSize);
                            newSize = (int) remainingKA.amount();
                        } else {
                            ItemStack targetStack = targetSlot.getStack();
                            if (targetStack == null) {
                                int insert = Math.min(remaining.stackSize, targetSlot.getSlotStackLimit());
                                remaining.stackSize -= insert;
                                targetSlot.putStack(remaining.splitStack(insert));
                                newSize = remaining.stackSize;
                            } else if (targetStack.isItemEqual(remaining)
                                && ItemStack.areItemStackTagsEqual(targetStack, remaining)) {
                                    int insert = Math.min(
                                        remaining.stackSize,
                                        targetSlot.getSlotStackLimit() - targetStack.stackSize);
                                    targetStack.stackSize += insert;
                                    remaining.stackSize -= insert;
                                    newSize = remaining.stackSize;
                                } else {
                                    newSize = remaining.stackSize;
                                }
                        }
                        int inserted = remaining.stackSize - newSize;
                        if (inserted > 0) {
                            insertedToSlots.put(targetSlotIndex, inserted);
                        }
                        remaining.stackSize = newSize;
                    }

                    if (remaining.stackSize <= 0) {
                        // 成功取出
                        slot.onPickupFromSlot(player, cacheStack);
                    } else {
                        // 回滚
                        for (Map.Entry<Integer, Integer> entry : insertedToInv.entrySet()) {
                            Slot afterSlot = (Slot) inventorySlots.get(entry.getKey());
                            afterSlot.decrStackSize(entry.getValue());
                        }
                        for (Map.Entry<Integer, Integer> entry : insertedToSlots.entrySet()) {
                            Slot afterSlot = (Slot) inventorySlots.get(entry.getKey());
                            if (afterSlot instanceof AbstractStackTypedSlot) {
                                ((AbstractStackTypedSlot) afterSlot)
                                    .safeExtract(new ItemStackKey(cacheStack), entry.getValue());
                            } else {
                                afterSlot.decrStackSize(entry.getValue());
                            }
                        }
                        break;
                    }
                }
            } else {
                // 普通快速移动处理
                ItemStack cacheStack = slotStack.copy();
                ItemStack remaining = cacheStack.copy();
                for (int targetSlotIndex = targetStartIndex; targetSlotIndex < targetEndIndex
                    && remaining.stackSize > 0; targetSlotIndex++) {
                    Slot targetSlot = (Slot) inventorySlots.get(targetSlotIndex);
                    int newSize;
                    if (targetSlot instanceof AbstractStackTypedSlot) {
                        AbstractStackTypedSlot aTargetSlot = (AbstractStackTypedSlot) targetSlot;
                        KeyAmount remainingKA = aTargetSlot
                            .safeInsert(new ItemStackKey(remaining), remaining.stackSize);
                        newSize = (int) remainingKA.amount();
                    } else {
                        ItemStack targetStack = targetSlot.getStack();
                        if (targetStack == null) {
                            int insert = Math.min(remaining.stackSize, targetSlot.getSlotStackLimit());
                            targetSlot.putStack(remaining.splitStack(insert));
                            newSize = remaining.stackSize;
                        } else if (targetStack.isItemEqual(remaining)
                            && ItemStack.areItemStackTagsEqual(targetStack, remaining)) {
                                int insert = Math
                                    .min(remaining.stackSize, targetSlot.getSlotStackLimit() - targetStack.stackSize);
                                targetStack.stackSize += insert;
                                remaining.stackSize -= insert;
                                newSize = remaining.stackSize;
                            } else {
                                newSize = remaining.stackSize;
                            }
                    }
                    remaining.stackSize = newSize;
                }
                int consumed = cacheStack.stackSize - remaining.stackSize;
                if (consumed > 0) {
                    slot.decrStackSize(consumed);
                }
            }

            slot.onSlotChanged();
        }
        return null;
    }

    /** 完全重写快速移动方案 */
    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= this.inventorySlots.size()) {
            return null;
        }
        Slot slot = (Slot) this.inventorySlots.get(slotIndex);
        if (slot == null || !slot.getHasStack() || slot instanceof AbstractStackTypedSlot) {
            return null;
        }

        ItemStack stack = slot.getStack();
        boolean changed = false;

        boolean fromPlayerInventory = inventoryStartIndex >= 0 && inventoryEndIndex > inventoryStartIndex
            && slotIndex >= inventoryStartIndex
            && slotIndex < inventoryEndIndex;

        if (fromPlayerInventory) {
            // 先尝试移动到容器槽位，再尝试在玩家背包内部移动
            changed = mergeIntoVanillaSlots(stack, 0, inventoryStartIndex, false);
            if (!changed) {
                changed = mergeIntoVanillaSlots(stack, inventoryStartIndex, inventoryEndIndex, true);
            }
        } else {
            // 从容器槽位移回玩家背包
            if (inventoryStartIndex >= 0 && inventoryEndIndex > inventoryStartIndex) {
                changed = mergeIntoVanillaSlots(stack, inventoryStartIndex, inventoryEndIndex, false);
            }
        }

        if (!changed) {
            return null;
        }

        if (stack.stackSize <= 0) {
            slot.putStack(null);
            return null;
        } else {
            slot.onSlotChanged();
        }
        // 对齐原版 Container.transferStackInSlot 契约：返回剩余量，移空返回 null（已在上面返回）。
        return stack.copy();
    }

    /** 在指定的普通槽位范围内尝试合并物品，跳过 AbstractStackTypedSlot */
    private boolean mergeIntoVanillaSlots(ItemStack stack, int start, int end, boolean reverse) {
        if (stack == null || stack.stackSize <= 0 || start < 0 || end > this.inventorySlots.size()) {
            return false;
        }
        boolean changed = false;
        if (reverse) {
            for (int i = end - 1; i >= start && stack.stackSize > 0; i--) {
                Slot target = (Slot) this.inventorySlots.get(i);
                if (target instanceof AbstractStackTypedSlot) continue;
                if (mergeIntoSlot(target, stack)) changed = true;
            }
        } else {
            for (int i = start; i < end && stack.stackSize > 0; i++) {
                Slot target = (Slot) this.inventorySlots.get(i);
                if (target instanceof AbstractStackTypedSlot) continue;
                if (mergeIntoSlot(target, stack)) changed = true;
            }
        }
        return changed;
    }

    /** 尝试将物品合并到单个目标槽位 */
    private boolean mergeIntoSlot(Slot target, ItemStack source) {
        if (source == null || source.stackSize <= 0 || !target.isItemValid(source)) {
            return false;
        }
        int limit = Math.min(target.getSlotStackLimit(), source.getMaxStackSize());
        ItemStack targetStack = target.getStack();

        if (targetStack == null) {
            int move = Math.min(source.stackSize, limit);
            if (move <= 0) return false;
            target.putStack(source.splitStack(move));
            target.onSlotChanged();
            return true;
        } else if (targetStack.isItemEqual(source) && ItemStack.areItemStackTagsEqual(targetStack, source)) {
            int canAdd = Math.min(limit - targetStack.stackSize, source.stackSize);
            if (canAdd <= 0) return false;
            targetStack.stackSize += canAdd;
            source.stackSize -= canAdd;
            target.onSlotChanged();
            return true;
        }
        return false;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return true;
    }

    @Override
    public void onContainerClosed(EntityPlayer player) {
        super.onContainerClosed(player);
        for (SlotGroupSync slotGroupSync : slotGroupSyncs) {
            if (slotGroupSync instanceof DisorderedSlotGroupSync) {
                ((DisorderedSlotGroupSync) slotGroupSync).dispose();
            }
        }
    }
}
