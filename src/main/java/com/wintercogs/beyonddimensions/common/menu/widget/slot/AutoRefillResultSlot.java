package com.wintercogs.beyonddimensions.common.menu.widget.slot;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCraftResult;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.SlotCrafting;
import net.minecraft.item.ItemStack;

import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.menu.DimensionsCraftMenu;

import cpw.mods.fml.common.FMLCommonHandler;

/**
 * 合成结果槽（1.7.10 移植版），对应源项目 {@code AutoRefillResultSlot}。
 * <p>
 * 连续/批量合成的核心机制完全对齐源项目 {@code AutoRefillResultSlot.onTake}：
 * 每次取走成品时，若某个合成格材料将因本次合成耗尽（{@code stackSize <= 1}），
 * <b>不消耗合成格本身</b>，而是优先从维度网络存储、其次从玩家背包"借料"一份满足本次消耗，
 * 使合成格保持原样，从而支持一次 Shift 连续合成多次，直至材料真正耗尽（如 20 桶水
 * 一次合成 20 个，每次借 1 桶水、返还 1 个空桶）。
 * 返回物按 源项目 顺序分发：合成格空则填回 1 个 → 存储 → 背包 → 掉落。
 * <p>
 * 1.7.10 特有约束（本地预测 + 服务端反同步校验）：客户端 {@code PlayerControllerMP.windowClick}
 * 会先在镜像容器上执行一次 {@code slotClick} 预测，本方法因此在客户端镜像上也可能执行。
 * 若借料在客户端预测时真实扣减镜像（存储或背包），快速连点时镜像比服务端真实提前耗尽，
 * 预测结果槽为空而服务端有成品，触发 {@code NetHandlerPlayServer.processClickWindow} 反同步
 * （{@code setPlayerIsPresent(false)}），后续点击包被忽略直到 C0F 确认往返恢复——
 * 表现为"快速点击合成被限速约 0.5~1 秒一次"。因此客户端预测的存储提取用 simulate 模拟、
 * 背包借料只查询可借量，均不实际扣减镜像（见 Bug #56 与后续背包补充修复）。
 */
public class AutoRefillResultSlot extends SlotCrafting {

    private final DimensionsCraftMenu menu;
    private final EntityPlayer player;
    private final IInventory craftMatrix;

    public AutoRefillResultSlot(DimensionsCraftMenu menu, EntityPlayer player, IInventory craftMatrix,
        IInventory resultInventory, int slotIndex, int xPosition, int yPosition) {
        super(player, craftMatrix, resultInventory, slotIndex, xPosition, yPosition);
        this.menu = menu;
        this.player = player;
        this.craftMatrix = craftMatrix;
    }

    @Override
    public void onPickupFromSlot(EntityPlayer player, ItemStack stack) {
        // 1.7.10 原版 SlotCrafting 会在取走成品时 fire PlayerEvent.ItemCraftedEvent
        // （FMLCommonHandler.firePlayerCraftingEvent），BetterQuesting 合成任务依赖该事件。
        // 自定义借料逻辑不能调用 super（会重复消耗材料），因此按原版顺序显式补齐（Bug #76）。
        FMLCommonHandler.instance()
            .firePlayerCraftingEvent(player, stack, this.craftMatrix);

        // 保留原版 SlotCrafting 的成就逻辑（对齐源项目 checkTakeAchievements）。
        this.onCrafting(stack);

        // 借料 + 消耗 + 返回物分发（连续/批量合成的核心）
        consumeCraftMaterials();

        // 刷新结果槽
        DimensionsCraftMenu.slotChangedCraftingGrid(
            menu,
            player.worldObj,
            player,
            (InventoryCrafting) this.craftMatrix,
            (InventoryCraftResult) this.inventory,
            menu.resultSlotIndex);
    }

    /**
     * 借料 + 消耗 + 返回物分发。
     * <p>
     * 拆分为独立方法的原因：{@link #onPickupFromSlot} 末尾的结果槽刷新依赖 1.7.10 原版
     * {@link net.minecraft.item.crafting.CraftingManager}，而其在裸 JVM 单测中无法初始化
     * （静态初始化依赖完整物品注册表，会抛 {@code NoClassDefFoundError}）；本方法不依赖它，
     * 使回归测试能直接验证借料机制。
     */
    private void consumeCraftMaterials() {
        final int craftTimes = 1; // 一次取走完成一次合成

        for (int i = 0; i < this.craftMatrix.getSizeInventory(); i++) {
            ItemStack slotStack = this.craftMatrix.getStackInSlot(i);
            if (slotStack == null) {
                continue;
            }

            // 基于"消耗前"的合成格计算返回物（对齐源项目 getRemainingItemsFor 在 onTake 开头计算）
            ItemStack recipeRemainder = slotStack.getItem()
                .hasContainerItem(slotStack)
                    ? slotStack.getItem()
                        .getContainerItem(slotStack)
                    : null;

            // 自磨损工具（如 GT 研钵）：容器物品是自身同 meta 的磨损副本，借料会把磨损写回存储，
            // 导致网络中所有研钵被整体改写耐久。此类工具必须在合成格内按原版语义持续磨损。
            boolean selfWearTool = isSelfWearingTool(slotStack, recipeRemainder);

            int itemsToRemove = craftTimes;

            // 借料：本格材料将因本次合成耗尽时，优先从网络存储、其次玩家背包借一份满足本次消耗，
            // 使合成格保持原样（源项目 onTake 的核心机制，支持一次 shift 连续合成多次）。
            // Bug #56：客户端预测仅 simulate 提取（不实际扣减镜像），服务端真实扣减。
            if (!selfWearTool && slotStack.stackSize <= itemsToRemove) {
                ItemStackKey toRemoveKey = new ItemStackKey(slotStack);
                boolean simulate = this.player.worldObj.isRemote;
                long extracted = menu.storage.extract(toRemoveKey, itemsToRemove, simulate, false)
                    .amount();
                itemsToRemove -= (int) extracted;

                if (itemsToRemove > 0) {
                    itemsToRemove -= shrinkFromInventory(
                        this.player.inventory.mainInventory,
                        toRemoveKey,
                        itemsToRemove,
                        simulate);
                }
            }

            // 借料不足以满足本次消耗时，才消耗合成格本身
            if (itemsToRemove > 0) {
                this.craftMatrix.decrStackSize(i, itemsToRemove);
            }

            // 返回物分发（对齐源项目：合成格空则填回 1 个 → 存储 → 背包 → 掉落）
            if (recipeRemainder != null && recipeRemainder.stackSize > 0) {
                ItemStackKey remainderKey = new ItemStackKey(recipeRemainder);
                int remainderCount = craftTimes;
                // 客户端预测：存储写入仅模拟，避免镜像存储被真实改写导致与服务端分叉
                // （与借料路径 Bug #56 的 simulate 语义一致，审计 M5-2）
                boolean simulate = this.player.worldObj.isRemote;

                ItemStack afterStack = this.craftMatrix.getStackInSlot(i);
                if (afterStack == null && remainderCount > 0) {
                    this.craftMatrix.setInventorySlotContents(i, remainderKey.copyStackWithCount(1));
                    remainderCount--;
                }
                if (remainderCount > 0) {
                    remainderCount = (int) menu.storage.insert(remainderKey, remainderCount, simulate)
                        .amount();
                }
                if (remainderCount > 0) {
                    if (simulate) {
                        // 客户端预测不真实写入镜像背包，等待服务端窗口同步（审计 M5-2）
                        remainderCount = 0;
                    } else {
                        ItemStack insertStack = remainderKey.copyStackWithCount(remainderCount);
                        boolean allInserted = this.player.inventory.addItemStackToInventory(insertStack);
                        remainderCount = allInserted ? 0 : insertStack.stackSize;
                    }
                }
                if (remainderCount > 0) {
                    this.player.dropPlayerItemWithRandomChoice(remainderKey.copyStackWithCount(remainderCount), false);
                }
            }

            // 自磨损工具耐久耗尽（容器物品不再返回自身）时，从存储/背包补充下一把同类型工具，
            // 使一次 shift 连续合成可以继续，直到材料真正耗尽。
            if (selfWearTool && this.craftMatrix.getStackInSlot(i) == null) {
                refillWornTool(i, slotStack);
            }
        }
    }

    /**
     * 判断合成格物品是否为"自磨损工具"：容器物品是自身同 meta 的磨损副本（如 GT 研钵），
     * 或已进入最后一次使用（GT5U {@code MetaGeneratedTool.doDamage} 在本次使用后达到
     * MaxDamage 即损坏消失，getContainerItem 返回 null；耐久字段见
     * {@code MetaGeneratedTool.getToolMaxDamage} 的 GT.ToolStats.Damage/MaxDamage）。
     */
    private boolean isSelfWearingTool(ItemStack slotStack, ItemStack recipeRemainder) {
        if (slotStack == null) return false;
        if (recipeRemainder != null && recipeRemainder.isItemEqual(slotStack)) return true;
        if (slotStack.getTagCompound() != null) {
            net.minecraft.nbt.NBTTagCompound stats = slotStack.getTagCompound()
                .getCompoundTag("GT.ToolStats");
            return stats.getLong("MaxDamage") > 0L;
        }
        return false;
    }

    /**
     * 自磨损工具损坏消失后，从存储（item+meta 模糊匹配，忽略 NBT）或玩家背包补充一把到合成格。
     * 客户端预测（simulate）仅查询可用数量，不实际扣减镜像，与服务端借料语义一致。
     */
    private void refillWornTool(int slotIndex, ItemStack wornTool) {
        boolean simulate = this.player.worldObj.isRemote;
        ItemStackKey typeKey = new ItemStackKey(wornTool);

        KeyAmount extracted = menu.storage.extract(typeKey, 1, simulate, true);
        if (extracted.amount() > 0 && extracted.key() instanceof ItemStackKey) {
            this.craftMatrix
                .setInventorySlotContents(slotIndex, ((ItemStackKey) extracted.key()).copyStackWithCount(1));
            return;
        }

        ItemStack readOnly = typeKey.getReadOnlyStack();
        for (int j = 0; j < this.player.inventory.mainInventory.length; j++) {
            ItemStack invStack = this.player.inventory.mainInventory[j];
            if (invStack != null && invStack.isItemEqual(readOnly)) {
                ItemStack fill = invStack.copy();
                fill.stackSize = 1;
                this.craftMatrix.setInventorySlotContents(slotIndex, fill);
                if (!simulate) {
                    invStack.stackSize--;
                    if (invStack.stackSize <= 0) {
                        this.player.inventory.mainInventory[j] = null;
                    }
                }
                return;
            }
        }
    }

    /**
     * 从玩家背包借料：返回实际借到的数量（对齐源项目 onTake 的背包提取循环）。
     * 客户端预测（simulate=true）仅查询可借量，不实际扣减镜像。
     */
    private int shrinkFromInventory(ItemStack[] slots, ItemStackKey key, int amount, boolean simulate) {
        ItemStack readOnly = key.getReadOnlyStack();
        int remaining = amount;
        for (int j = 0; j < slots.length && remaining > 0; j++) {
            ItemStack invStack = slots[j];
            if (invStack != null && invStack.isItemEqual(readOnly)
                && ItemStack.areItemStackTagsEqual(invStack, readOnly)) {
                int take = Math.min(remaining, invStack.stackSize);
                if (!simulate) {
                    invStack.stackSize -= take;
                    if (invStack.stackSize <= 0) {
                        slots[j] = null;
                    }
                }
                remaining -= take;
            }
        }
        return amount - remaining;
    }
}
