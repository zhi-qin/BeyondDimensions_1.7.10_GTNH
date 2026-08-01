package com.wintercogs.beyonddimensions.common.menu;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCraftResult;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.S2FPacketSetSlot;
import net.minecraft.world.World;

import com.wintercogs.beyonddimensions.api.ButtonState;
import com.wintercogs.beyonddimensions.api.storage.handler.IStackHandler;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.UnorderedStackHandlerRemoveZero;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.menu.widget.slot.AbstractStackTypedSlot;
import com.wintercogs.beyonddimensions.common.menu.widget.slot.AutoRefillResultSlot;
import com.wintercogs.beyonddimensions.config.CommonConfigRuntime;
import com.wintercogs.beyonddimensions.util.InventoryHelper;

/**
 * 自带合成台的 DimensionsNetMenu（1.7.10 移植版）。
 * <p>
 * 1.7.10 适配：TransientCraftingContainer → InventoryCrafting；
 * ResultContainer → InventoryCraftResult；
 * AutoRefillResultSlot → AutoRefillResultSlot（补料逻辑由 onTake 落到 onPickupFromSlot）；
 * slotsChanged → onCraftMatrixChanged；
 * level.getRecipeManager().getRecipeFor → CraftingManager.getInstance().findMatchingRecipe；
 * removed → onContainerClosed；
 * canTakeItemForPickAll → 1.7.10 无对应方法，已移除；
 * slot.y → slot.yDisplayPosition；
 * ItemStack.EMPTY → null；stack.isEmpty() → stack == null。
 */
public class DimensionsCraftMenu extends DimensionsNetMenu {

    protected InventoryCrafting craftSlots;
    protected InventoryCraftResult resultSlots;
    public int resultSlotIndex;
    public int craftSlotStartIndex;
    public int craftSlotEndIndex;
    public boolean firstCraftReturnDir = false; // 决定关闭菜单时工艺槽的优先转移方向，true向存储 false背包

    /**
     * 客户端构造函数
     */
    public DimensionsCraftMenu(InventoryPlayer playerInventory) {
        this(playerInventory, null, null);
    }

    /**
     * 服务端构造函数
     *
     * @param playerInventory 玩家背包
     * @param data            维度网络存储信息，null 时使用临时存储
     * @param craftItems      工艺槽初始物品，null 时为空工艺槽
     */
    public DimensionsCraftMenu(InventoryPlayer playerInventory, AbstractUnorderedStackHandler data,
        ItemStack[] craftItems) {
        super(
            playerInventory,
            data != null ? data
                : new UnorderedStackHandlerRemoveZero(AbstractUnorderedStackHandler.UiTimestampPolicy.NONE));
        initCraftSlots(playerInventory, craftItems);
    }

    /**
     * 覆写玩家背包槽位布局，向下偏移 62 像素以容纳工艺槽区域
     */
    @Override
    protected void addPlayerInv(InventoryPlayer playerInventory) {
        inventoryStartIndex = this.inventorySlots.size();
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlotToContainer(
                    new Slot(
                        playerInventory,
                        col + row * 9 + 9,
                        8 + col * 18,
                        25 + 62 + (getLines() - 1) * 18 + 26 + 6 + row * 18));
            }
        }
        for (int col = 0; col < 9; ++col) {
            this.addSlotToContainer(
                new Slot(playerInventory, col, 8 + col * 18, 25 + 62 + (getLines() - 1) * 18 + 26 + 6 + 3 * 18 + 4));
        }
        inventoryEndIndex = this.inventorySlots.size();
    }

    /**
     * 初始化工艺槽和结果槽
     */
    protected void initCraftSlots(InventoryPlayer playerInventory, ItemStack[] craftItems) {
        this.craftSlots = new InventoryCrafting(this, 3, 3);
        if (craftItems != null) {
            for (int i = 0; i < 9 && i < craftItems.length; i++) {
                if (craftItems[i] != null) {
                    this.craftSlots.setInventorySlotContents(i, craftItems[i].copy());
                }
            }
        }
        this.resultSlots = new InventoryCraftResult();

        this.addSlotToContainer(
            new AutoRefillResultSlot(
                this,
                player,
                this.craftSlots,
                this.resultSlots,
                0,
                116 + 4,
                24 + (getLines() - 1) * 18 + 26 + 21));
        resultSlotIndex = this.inventorySlots.size() - 1;

        craftSlotStartIndex = this.inventorySlots.size();
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 3; ++j) {
                this.addSlotToContainer(
                    new Slot(this.craftSlots, j + i * 3, 26 + j * 18, 24 + (getLines() - 1) * 18 + 26 + 3 + i * 18));
            }
        }
        craftSlotEndIndex = this.inventorySlots.size();
    }

    /**
     * 工艺槽变化时更新结果槽（1.7.10 适配版）
     * 使用 CraftingManager.findMatchingRecipe 替代 1.20.1 的 RecipeManager。
     * <p>
     * 双端计算（对齐 1.7.10 原版 ContainerWorkbench.onCraftMatrixChanged 无侧检查）：
     * 1.7.10 的 PlayerControllerMP.windowClick 会在客户端本地执行 slotClick 预测，
     * 经 InventoryCrafting.setInventorySlotContents 触发本地 onCraftMatrixChanged，
     * 客户端必须本地计算结果槽才能即时渲染（1.20.1 源码的 isClientSide 门控不适用，
     * 高版本客户端不做本地预测，结果由显式包回传）。
     * 服务端额外对齐源码的显式 ClientboundContainerSetSlotPacket，立即回传结果槽。
     */
    public static void slotChangedCraftingGrid(Container menu, World world, EntityPlayer player,
        InventoryCrafting craftSlots, InventoryCraftResult resultSlots, int resultSlotIndex) {
        ItemStack itemstack = CraftingManager.getInstance()
            .findMatchingRecipe(craftSlots, world);
        resultSlots.setInventorySlotContents(0, itemstack);
        if (!world.isRemote && player instanceof EntityPlayerMP && menu.windowId > 0) {
            ((EntityPlayerMP) player).playerNetServerHandler
                .sendPacket(new S2FPacketSetSlot(menu.windowId, resultSlotIndex, itemstack));
        }
    }

    /**
     * 从背包和存储中提取物品填充工艺槽（用于自动合成）
     */
    public void transferRecipe(List<IStackKey<?>> inputKeys, List<Long> amount) {
        cleanCraftSlots(firstCraftReturnDir);

        final int limit = Math.min(craftSlots.getSizeInventory(), inputKeys.size());
        for (int i = 0; i < limit; i++) {
            long needL = (i < amount.size() ? amount.get(i) : 0L);
            IStackKey<?> key = inputKeys.get(i);

            if (!(key instanceof ItemStackKey) || needL <= 0) continue;
            ItemStackKey itemStackKey = (ItemStackKey) key;

            int need = (int) Math.min(Integer.MAX_VALUE, needL);

            int remaining = extractFromInventory(player.inventory, itemStackKey.copyStack(), need);
            if (remaining > 0) remaining = extractFromStorage(storage, itemStackKey, remaining);

            int got = need - remaining;
            if (got > 0) craftSlots.setInventorySlotContents(i, itemStackKey.copyStackWithCount(got));
        }
    }

    /**
     * 服务端自动合成（NEI Shift+C 自动合成，BUGFIX_RECORD #103）。
     * <p>
     * 按目标次数逐次执行：增量填充工艺槽（单次配方量，保留槽内已有工具/返还物）→ 检测结果
     * → 取走结果（复用 {@link AutoRefillResultSlot#onPickupFromSlot} 的借料/工具磨损/返还逻辑）
     * → 产出到玩家背包（放不下则掉落）。材料不足或配方不匹配时立即停止，
     * 已填入工艺槽的材料保留在槽内（关菜单时由 cleanCraftSlots 归还），不丢失。
     * <p>
     * 只在服务端主线程调用（{@code AutoCraftC2SPacket.Handler} 经
     * {@code BDMainThreadScheduler.scheduleServer} 切到服务端主线程后再调用本方法）。
     */
    public void autoCraft(List<IStackKey<?>> inputKeys, List<Long> amount, int multiplier) {
        if (player == null || player.worldObj.isRemote) return;
        final int maxCrafts = Math.max(1, Math.min(64, multiplier));
        // 只清空一次，之后逐次增量补料：合成格内的工具/返还物（如磨损中的锻造锤）跨轮保留，
        // 避免每轮"清空→按客户端计划 key 重新提取"——工具耐久磨损后 NBT 已与计划 key 不同，
        // 重新提取会失败导致只合成一次就中断（Bug #103 的"材料消失无产出"根因之一）。
        cleanCraftSlots(firstCraftReturnDir);
        for (int c = 0; c < maxCrafts; c++) {
            fillCraftSlotsIncremental(inputKeys, amount);
            // 显式重算结果槽：增量填充可能只改 stackSize（不触发 onCraftMatrixChanged），
            // 且 cleanCraftSlots 已把结果槽置空，不重算会导致循环第一次就误判无结果
            slotChangedCraftingGrid(this, player.worldObj, player, craftSlots, resultSlots, resultSlotIndex);
            ItemStack result = resultSlots.getStackInSlot(0);
            if (result == null) break;
            Slot resultSlot = this.inventorySlots.get(resultSlotIndex);
            if (!(resultSlot instanceof AutoRefillResultSlot)) break;
            ((AutoRefillResultSlot) resultSlot).onPickupFromSlot(player, result);
            ItemStack leftover = InventoryHelper.insertIntoInventory(player, result);
            if (leftover != null && leftover.stackSize > 0) {
                player.dropPlayerItemWithRandomChoice(leftover, false);
            }
        }
        // 立即把工艺槽/背包/结果槽的变化同步给客户端
        this.detectAndSendChanges();
    }

    /**
     * 增量填充工艺槽：仅补充"缺口"（按 item+meta 判断已在槽内的同类物品），
     * 不清空已有物品。用于服务端自动合成的逐次补料（Bug #103）。
     */
    private void fillCraftSlotsIncremental(List<IStackKey<?>> inputKeys, List<Long> amount) {
        final int limit = Math.min(craftSlots.getSizeInventory(), inputKeys.size());
        for (int i = 0; i < limit; i++) {
            long needL = (i < amount.size() ? amount.get(i) : 0L);
            IStackKey<?> key = inputKeys.get(i);
            if (!(key instanceof ItemStackKey) || needL <= 0) continue;
            ItemStackKey itemKey = (ItemStackKey) key;

            ItemStack cur = craftSlots.getStackInSlot(i);
            // 槽内物品与配方不符（如上一轮合成留下的容器返还物：水桶配方的空桶）：
            // 先归还再替换，避免被覆盖凭空消失
            if (cur != null && !cur.isItemEqual(itemKey.getReadOnlyStack())) {
                returnStackToPlayerOrStorage(cur);
                cur = null;
            }

            int already = 0;
            if (cur != null && cur.isItemEqual(itemKey.getReadOnlyStack())) {
                already = cur.stackSize;
            }
            int toAdd = (int) Math.min(Integer.MAX_VALUE, needL) - already;
            if (toAdd <= 0) continue;

            int remaining = extractFromInventory(player.inventory, itemKey.copyStack(), toAdd);
            if (remaining > 0) remaining = extractFromStorage(storage, itemKey, remaining);
            int got = toAdd - remaining;
            if (got > 0) {
                if (cur != null) {
                    cur.stackSize += got;
                } else {
                    craftSlots.setInventorySlotContents(i, itemKey.copyStackWithCount(got));
                }
            }
        }
    }

    /** 归还单格物品到背包→存储→掉落（对齐 cleanCraftSlots 的归还顺序，保证不丢失）。 */
    private void returnStackToPlayerOrStorage(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) return;
        ItemStack leftover = InventoryHelper.insertIntoInventory(player, stack.copy());
        if (leftover != null && leftover.stackSize > 0) {
            long remaining = storage.insert(new ItemStackKey(leftover), leftover.stackSize, false)
                .amount();
            if (remaining > 0) {
                player.dropPlayerItemWithRandomChoice(leftover.copy(), false);
            }
        }
    }

    // 从玩家背包提取物品
    private int extractFromInventory(InventoryPlayer inventory, ItemStack template, int amount) {
        int remaining = amount;
        for (int i = 0; i < 36 && remaining > 0; i++) {
            ItemStack stack = inventory.mainInventory[i];
            if (stack != null && stack.isItemEqual(template) && ItemStack.areItemStackTagsEqual(stack, template)) {
                int extract = Math.min(remaining, stack.stackSize);
                stack.stackSize -= extract;
                remaining -= extract;
                if (stack.stackSize <= 0) {
                    inventory.mainInventory[i] = null;
                }
            }
        }
        return remaining;
    }

    // 从网络存储提取物品
    private int extractFromStorage(IStackHandler storage, IStackKey<?> type, int amount) {
        KeyAmount extraction = storage.extract(type, amount, false, false);
        if (extraction.amount() > 0) {
            return amount - (int) extraction.amount();
        }
        return amount;
    }

    @Override
    public void onCraftMatrixChanged(IInventory inventory) {
        super.onCraftMatrixChanged(inventory);
        slotChangedCraftingGrid(this, player.worldObj, player, craftSlots, resultSlots, resultSlotIndex);
    }

    /**
     * 放大和缩小UI所使用的函数，用于重新确定槽位的激活状态以及槽位的位置
     */
    @Override
    public void rebuildSlots() {
        int sSlotNum = 0;
        for (int i = 0; i < inventorySlots.size(); i++) {
            Slot slot = (Slot) inventorySlots.get(i);
            if (slot instanceof AbstractStackTypedSlot) {
                AbstractStackTypedSlot sSlot = (AbstractStackTypedSlot) slot;
                boolean shouldBeActive = sSlotNum / 9 < getLines();
                sSlot.setActive(shouldBeActive);
                // 1.7.10 的 GuiContainer 不认识 active 概念，
                // 需将非活跃槽位移出屏幕，避免其在 GUI 背景外渲染并被点击
                if (shouldBeActive) {
                    sSlot.yDisplayPosition = 25 + (sSlotNum / 9) * 18;
                } else {
                    sSlot.yDisplayPosition = -9999;
                }
                sSlotNum++;
            }
        }

        int slotNum = 0;
        for (int i = inventoryStartIndex; i < inventoryEndIndex; ++i) {
            Slot slot = (Slot) inventorySlots.get(i);
            if (slotNum / 9 < 3) {
                slot.yDisplayPosition = 25 + 62 + (getLines() - 1) * 18 + 26 + 6 + slotNum / 9 * 18;
            } else {
                slot.yDisplayPosition = 25 + 62 + (getLines() - 1) * 18 + 26 + 6 + 3 * 18 + 4;
            }
            slotNum++;
        }

        Slot resultSlot = (Slot) inventorySlots.get(resultSlotIndex);
        resultSlot.yDisplayPosition = 24 + (getLines() - 1) * 18 + 26 + 21;

        slotNum = 0;
        for (int i = craftSlotStartIndex; i < craftSlotEndIndex; ++i) {
            Slot slot = (Slot) inventorySlots.get(i);
            slot.yDisplayPosition = 24 + (getLines() - 1) * 18 + 26 + 3 + slotNum / 3 * 18;
            slotNum++;
        }
    }

    /**
     * 清空工艺槽物品，优先放入存储或背包，剩余掉落
     */
    public void cleanCraftSlots(boolean toStorageFirst) {
        if (!player.worldObj.isRemote) {
            for (int i = 0; i < craftSlots.getSizeInventory(); i++) {
                ItemStack stack = craftSlots.getStackInSlot(i);
                if (stack != null) {
                    if (toStorageFirst) {
                        long remaining = storage.insert(new ItemStackKey(stack), stack.stackSize, false)
                            .amount();
                        if (remaining > 0) {
                            ItemStack toInsert = stack.copy();
                            toInsert.stackSize = (int) remaining;
                            ItemStack leftover = InventoryHelper.insertIntoInventory(player, toInsert);
                            if (leftover != null && leftover.stackSize > 0) {
                                player.dropPlayerItemWithRandomChoice(leftover, false);
                            }
                        }
                    } else if (player.isEntityAlive()) {
                        ItemStack leftover = InventoryHelper.insertIntoInventory(player, stack.copy());
                        if (leftover != null && leftover.stackSize > 0) {
                            long remaining = storage.insert(new ItemStackKey(leftover), leftover.stackSize, false)
                                .amount();
                            if (remaining > 0) {
                                ItemStack toDrop = leftover.copy();
                                toDrop.stackSize = (int) remaining;
                                player.dropPlayerItemWithRandomChoice(toDrop, false);
                            }
                        }
                    } else {
                        player.dropPlayerItemWithRandomChoice(stack, false);
                    }
                }
            }
            // 清空所有工艺槽和结果槽
            for (int i = 0; i < craftSlots.getSizeInventory(); i++) {
                craftSlots.setInventorySlotContents(i, null);
            }
            resultSlots.setInventorySlotContents(0, null);
        }
    }

    @Override
    protected boolean shouldSendQuickData() {
        return super.shouldSendQuickData();
    }

    @Override
    protected void writeQuickDataTag(NBTTagCompound tag) {
        super.writeQuickDataTag(tag);
        if (player.worldObj.isRemote)
            firstCraftReturnDir = CommonConfigRuntime.uiCraftReturnButton == ButtonState.ENABLED;
        tag.setBoolean("firstCraftReturnDir", firstCraftReturnDir);
    }

    @Override
    public void readQuickDataTag(NBTTagCompound tag) {
        super.readQuickDataTag(tag);
        if (!player.worldObj.isRemote) {
            if (tag.hasKey("firstCraftReturnDir")) firstCraftReturnDir = tag.getBoolean("firstCraftReturnDir");
        }
    }

    @Override
    public void onContainerClosed(EntityPlayer player) {
        super.onContainerClosed(player);
        // 将合成槽物品优先放入玩家背包或存储，否则掉落
        cleanCraftSlots(firstCraftReturnDir);
    }
}
