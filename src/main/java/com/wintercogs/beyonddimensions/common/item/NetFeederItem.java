package com.wintercogs.beyonddimensions.common.item;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.util.FoodStats;
import net.minecraft.world.World;

import com.wintercogs.beyonddimensions.BeyondDimensions;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.gui.BDGuiHandler;
import com.wintercogs.beyonddimensions.common.machine.FeederMode;

public class NetFeederItem extends BaseMachineItem {

    public static final int CAPACITY = 36;

    public NetFeederItem() {
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
            BDGuiHandler.NET_FEEDER_MENU,
            world,
            (int) player.posX,
            (int) player.posY,
            (int) player.posZ);
        return stack;
    }

    @Override
    public void checkComponents(ItemStack stack) {
        super.checkComponents(stack);
        if (!hasFilterSlots(stack)) {
            setFilterSlots(stack, emptyFilterSlots(CAPACITY));
        }
        if (!hasFeederMode(stack)) setFeederMode(stack, FeederMode.NORMAL);
    }

    @Override
    public boolean shouldWork(ItemStack stack, World world, Entity holder, int slotId, boolean isSelected) {
        return super.shouldWork(stack, world, holder, slotId, isSelected) && NetedItem.getNet(stack) != null;
    }

    @Override
    public void workContent(ItemStack stack, World world, Entity holder, int slotId, boolean isSelected) {
        super.workContent(stack, world, holder, slotId, isSelected);

        if (!(holder instanceof EntityPlayer player)) return;

        FeederMode feederMode = getFeederModeOrDefault(stack, FeederMode.NORMAL);
        List<KeyAmount> filterSlots = getFilterSlotsOrDefault(stack, new ArrayList<KeyAmount>());

        FoodStats foodStats = player.getFoodStats();
        if (!feederModeMatch(foodStats, feederMode)) return;

        UnifiedStorage storage = NetedItem.getNet(stack)
            .getUnifiedStorage();

        KeyAmount foodCache = findEdibleFood(storage, filterSlots);
        if (foodCache == null) return;

        KeyAmount foodToFeed = storage.extract(foodCache.key(), foodCache.amount(), false, false);
        if (foodToFeed.isEmpty() || !(foodToFeed.key() instanceof ItemStackKey foodKey)) {
            if (!foodToFeed.isEmpty()) {
                storage.insert(foodToFeed.key(), foodToFeed.amount(), false);
            }
            return;
        }

        ItemStack foodStack = foodKey.copyStackWithCount(foodCache.amount());
        Item foodItem = foodStack.getItem();

        if (foodItem instanceof ItemFood itemFood) {
            // ItemFood：用已知食物值做模式判断（对齐源项目 FoodProperties 判断）
            int heal = itemFood.func_150905_g(foodStack);
            float saturation = itemFood.func_150906_h(foodStack);
            if ((feederMode == FeederMode.SATURATION_KEEP && saturation > 0)
                || (feederMode != FeederMode.SATURATION_KEEP && heal > 0)) {
                ItemStack remaining = itemFood.onEaten(foodStack.copy(), world, player);
                insertLeftover(storage, player, remaining);
            } else {
                storage.insert(foodToFeed.key(), foodToFeed.amount(), false);
            }
        } else if (isEdibleFood(foodStack) && feederMode != FeederMode.SATURATION_KEEP) {
            // 非 ItemFood 可食用物品：1.7.10 无统一食物值 API，交由物品自身 onEaten 实现
            // （自定义可食用物品会覆盖 onEaten 自行恢复饥饿）。SATURATION_KEEP 无法校验饱和度，跳过。
            ItemStack remaining = foodItem.onEaten(foodStack.copy(), world, player);
            insertLeftover(storage, player, remaining);
        } else {
            storage.insert(foodToFeed.key(), foodToFeed.amount(), false);
        }
    }

    /**
     * 将食用后的剩余物插回网络存储，放不下则丢给玩家。
     * 对齐源项目 workContent 中 remaining 的剩余堆叠处理逻辑。
     */
    private static void insertLeftover(UnifiedStorage storage, EntityPlayer player, ItemStack remaining) {
        if (remaining == null || remaining.stackSize <= 0) return;
        KeyAmount leftover = storage.insert(new ItemStackKey(remaining), remaining.stackSize, false);
        if (!leftover.isEmpty()) {
            player.dropPlayerItemWithRandomChoice((ItemStack) leftover.toStack(), false);
        }
    }

    private KeyAmount findEdibleFood(UnifiedStorage storage, List<KeyAmount> filterSlots) {
        for (KeyAmount filter : filterSlots) {
            for (KeyAmount stored : storage.getStorage()) {
                if (stored.key() instanceof ItemStackKey itemKey && itemKey.isSame(filter.key())
                    && itemKey.getReadOnlyStack() != null
                    && isEdibleFood(itemKey.getReadOnlyStack())) {
                    return new KeyAmount(stored.key(), 1);
                }
            }
        }
        return null;
    }

    /**
     * 1.7.10 判断物品是否可食用，对齐 1.20.1 源项目 {@code getFoodProperties(player) != null} 的语义。
     * <p>
     * 原生食物（{@link ItemFood}）以及覆盖 {@code getItemUseAction} 返回 eat 的自定义可食用物品均视为食物。
     */
    public static boolean isEdibleFood(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        return stack.getItem()
            .getItemUseAction(stack) == EnumAction.eat;
    }

    private boolean feederModeMatch(FoodStats foodStats, FeederMode feederMode) {
        switch (feederMode) {
            case HUNGER_TO_EAT:
                return foodStats.getFoodLevel() <= 2;
            case NORMAL:
                return foodStats.getFoodLevel() <= 10;
            case SATURATION_KEEP:
                return foodStats.getSaturationLevel() <= 0;
            case CRAZY:
                return foodStats.getFoodLevel() < 20;
            default:
                return false;
        }
    }

    @Override
    public int getTicksPerWork(ItemStack stack, World world, Entity holder, int slotId, boolean isSelected) {
        return 10;
    }
}
