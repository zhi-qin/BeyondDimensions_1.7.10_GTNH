package com.wintercogs.beyonddimensions.common.item;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.wintercogs.beyonddimensions.BeyondDimensions;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.gui.BDGuiHandler;
import com.wintercogs.beyonddimensions.common.machine.FuzzyMode;
import com.wintercogs.beyonddimensions.common.machine.ReceiveMode;
import com.wintercogs.beyonddimensions.util.BDMath;

public class NetRestockerItem extends BaseMachineItem {

    public static final int CAPACITY = 40;

    public NetRestockerItem() {
        super();
        setMaxStackSize(1);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (player.isSneaking()) {
            return super.onItemRightClick(stack, world, player);
        }

        if (world.isRemote) {
            return stack;
        }

        player.openGui(
            BeyondDimensions.instance,
            BDGuiHandler.NET_RESTOCKER_MENU,
            world,
            (int) player.posX,
            (int) player.posY,
            (int) player.posZ);
        return stack;
    }

    @Override
    public void checkComponents(ItemStack stack) {
        super.checkComponents(stack);
        if (!hasFilterSlots(stack)) setFilterSlots(stack, emptyFilterSlots(CAPACITY));
        if (!hasFuzzyMode(stack)) setFuzzyMode(stack, FuzzyMode.DISABLE);
        if (!hasReceiveMode(stack)) setReceiveMode(stack, ReceiveMode.STOP);
    }

    @Override
    public boolean shouldWork(ItemStack stack, World world, Entity holder, int slotId, boolean isSelected) {
        return super.shouldWork(stack, world, holder, slotId, isSelected) && NetedItem.getNet(stack) != null;
    }

    @Override
    public void workContent(ItemStack stack, World world, Entity holder, int slotId, boolean isSelected) {
        super.workContent(stack, world, holder, slotId, isSelected);

        if (!(holder instanceof EntityPlayer player)) return;

        UnifiedStorage storage = NetedItem.getNet(stack)
            .getUnifiedStorage();
        List<KeyAmount> templates = getFilterSlotsOrDefault(stack, new ArrayList<KeyAmount>());
        FuzzyMode fuzzyMode = getFuzzyModeOrDefault(stack, FuzzyMode.DISABLE);
        ReceiveMode receiveMode = getReceiveModeOrDefault(stack, ReceiveMode.STOP);

        boolean inventoryChanged = false;

        for (int templateSlot = 0; templateSlot < CAPACITY && templateSlot < templates.size(); templateSlot++) {
            KeyAmount template = templates.get(templateSlot);
            ItemStack currentStack = getPlayerSlotStack(player, templateSlot);

            if (receiveMode == ReceiveMode.OPEN && currentStack != null
                && currentStack.stackSize > 0
                && canRecycle(currentStack)
                && !slotMatchesTemplate(currentStack, template, fuzzyMode)) {
                ItemStackKey currentKey = new ItemStackKey(currentStack);
                KeyAmount remainder = storage.insert(currentKey, currentStack.stackSize, false);
                int accepted = currentStack.stackSize - BDMath.clampLongToInt(remainder.amount());
                if (accepted > 0) {
                    currentStack.stackSize -= accepted;
                    if (currentStack.stackSize <= 0) currentStack = null;
                    setPlayerSlotStack(player, templateSlot, currentStack);
                    inventoryChanged = true;
                    currentStack = getPlayerSlotStack(player, templateSlot);
                }
            }

            if (!(template.key() instanceof ItemStackKey targetKey) || template.isEmpty()) continue;
            if (currentStack == null
                && !canPlaceInPlayerTemplateSlot(player, templateSlot, targetKey.getReadOnlyStack())) continue;

            int targetCount = BDMath.clampLongToInt(targetKey.getVanillaMaxStackSize());
            if (targetCount <= 0) continue;

            int missing;
            if (currentStack == null) {
                missing = targetCount;
            } else if (isSameItemSameTags(currentStack, targetKey.getReadOnlyStack())) {
                missing = targetCount - currentStack.stackSize;
            } else {
                continue;
            }

            if (missing <= 0) continue;

            KeyAmount extracted = storage.extract(targetKey, missing, false, fuzzyMode == FuzzyMode.ENABLE);
            if (extracted.isEmpty()) continue;

            if (!(extracted.key() instanceof ItemStackKey extractedItemKey)) {
                storage.insert(extracted.key(), extracted.amount(), false);
                continue;
            }

            int refillCount = BDMath.clampLongToInt(extracted.amount());
            if (refillCount <= 0) continue;

            ItemStack refill = extractedItemKey.copyStackWithCount(refillCount);

            if (currentStack == null) {
                if (setPlayerSlotStack(player, templateSlot, refill)) {
                    inventoryChanged = true;
                } else {
                    storage.insert(extracted.key(), extracted.amount(), false);
                }
            } else {
                if (!isSameItemSameTags(currentStack, refill)) {
                    storage.insert(extracted.key(), extracted.amount(), false);
                    continue;
                }
                currentStack.stackSize += refillCount;
                if (setPlayerSlotStack(player, templateSlot, currentStack)) {
                    inventoryChanged = true;
                } else {
                    storage.insert(extracted.key(), extracted.amount(), false);
                }
            }
        }

        if (inventoryChanged) {
            player.inventory.markDirty();
        }
    }

    private boolean slotMatchesTemplate(ItemStack stackInSlot, KeyAmount template, FuzzyMode fuzzyMode) {
        if (stackInSlot == null || stackInSlot.stackSize <= 0
            || template.isEmpty()
            || !(template.key() instanceof ItemStackKey templateKey)) return false;

        if (fuzzyMode == FuzzyMode.ENABLE) return templateKey.isSame(new ItemStackKey(stackInSlot));

        return isSameItemSameTags(stackInSlot, templateKey.getReadOnlyStack());
    }

    private boolean canRecycle(ItemStack stack) {
        return stack != null && stack.getItem() != null && !(stack.getItem() instanceof NetRestockerItem);
    }

    private ItemStack getPlayerSlotStack(EntityPlayer player, int templateSlot) {
        if (templateSlot < 27) return player.inventory.getStackInSlot(templateSlot + 9);
        if (templateSlot < 36) return player.inventory.getStackInSlot(templateSlot - 27);

        int armorIndex = armorIndexForTemplateSlot(templateSlot);
        if (armorIndex < 0) return null;
        return player.inventory.armorInventory[armorIndex];
    }

    private boolean setPlayerSlotStack(EntityPlayer player, int templateSlot, ItemStack stack) {
        if (templateSlot < 27) {
            player.inventory.setInventorySlotContents(templateSlot + 9, stack);
            return true;
        }
        if (templateSlot < 36) {
            player.inventory.setInventorySlotContents(templateSlot - 27, stack);
            return true;
        }

        int armorIndex = armorIndexForTemplateSlot(templateSlot);
        if (armorIndex < 0) return false;
        if (!canPlaceInPlayerTemplateSlot(player, templateSlot, stack)) return false;

        player.inventory.armorInventory[armorIndex] = stack;
        return true;
    }

    private int armorIndexForTemplateSlot(int templateSlot) {
        return switch (templateSlot) {
            case 36 -> 3; // 头盔
            case 37 -> 2; // 胸甲
            case 38 -> 1; // 护腿
            case 39 -> 0; // 靴子
            default -> -1;
        };
    }

    private boolean canPlaceInPlayerTemplateSlot(EntityPlayer player, int templateSlot, ItemStack stack) {
        if (templateSlot < 36) return true;
        if (stack == null || stack.stackSize <= 0) return true;

        int armorIndex = armorIndexForTemplateSlot(templateSlot);
        if (armorIndex < 0) return false;
        Item item = stack.getItem();
        // armorInventory 索引（0=靴子,1=护腿,2=胸甲,3=头盔）与 ItemArmor.armorType（0=头盔,1=胸甲,2=护腿,3=靴子）方向相反
        int armorType = 3 - armorIndex;
        return item != null && item.isValidArmor(stack, armorType, player);
    }

    private boolean isSameItemSameTags(ItemStack a, ItemStack b) {
        if (a == null || b == null) return a == b;
        return a.getItem() == b.getItem() && a.getItemDamage() == b.getItemDamage()
            && ItemStack.areItemStackTagsEqual(a, b);
    }

    @Override
    public int getTicksPerWork(ItemStack stack, World world, Entity holder, int slotId, boolean isSelected) {
        return 10;
    }
}
