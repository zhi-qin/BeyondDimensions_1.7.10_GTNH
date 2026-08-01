package com.wintercogs.beyonddimensions.common.menu;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.StackHandler;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.common.machine.FuzzyMode;
import com.wintercogs.beyonddimensions.common.machine.PopMode;
import com.wintercogs.beyonddimensions.common.machine.RedStoneControlMode;

/**
 * 网络接口访问接口（1.7.10 移植版）。
 * <p>
 * 与 1.20.1 源码保持一致：定义网络接口方块对外暴露的设置/状态访问方法，
 * 并提供 transferToNet / transferFromNet 两个静态工具方法，供 NetInterfaceBlockEntity 与
 * NetInterfaceBaseMenu 共享传输逻辑。
 */
public interface NetInterfaceAccess {

    StackHandler getStackHandler();

    StackHandler getFakeStackHandler();

    NetInterfaceSettings getNetInterfaceSettings();

    default PopMode getPopMode() {
        return getNetInterfaceSettings().getPopMode();
    }

    default void setPopMode(PopMode popMode) {
        getNetInterfaceSettings().setPopMode(popMode);
    }

    default FuzzyMode getFuzzyMode() {
        return getNetInterfaceSettings().getFuzzyMode();
    }

    default void setFuzzyMode(FuzzyMode fuzzyMode) {
        getNetInterfaceSettings().setFuzzyMode(fuzzyMode);
    }

    RedStoneControlMode getControlMode();

    void setControlMode(RedStoneControlMode controlMode);

    default boolean canConfigurePopMode() {
        return true;
    }

    boolean isMenuValid();

    void onMenuDataChanged();

    /**
     * 将接口内的物品转移到网络
     *
     * @return 是否发生了变化
     */
    static boolean transferToNet(DimensionsNet net, StackHandler stackHandler, StackHandler fakeStackHandler,
        int slotCount) {
        if (net == null) return false;
        boolean changed = false;
        for (int i = 0; i < slotCount; i++) {
            KeyAmount flag = fakeStackHandler.getStackBySlot(i);
            // 标记槽位与实际槽位类型一致时跳过（已被 transferFromNet 处理或保持现状）
            if (!flag.isEmpty() && flag.key()
                .isSameTypeSameComponents(
                    stackHandler.getStackBySlot(i)
                        .key()))
                continue;
            KeyAmount stack = stackHandler.getStackBySlot(i);
            if (!stack.isEmpty()) {
                KeyAmount extracted = stackHandler.extract(i, stack.amount(), false);
                KeyAmount remaining = net.getUnifiedStorage()
                    .insert(extracted.key(), extracted.amount(), false);
                if (!remaining.isEmpty()) {
                    stackHandler.insert(i, remaining.key(), remaining.amount(), false);
                }
                changed |= remaining.amount() != extracted.amount();
            }
        }
        return changed;
    }

    /**
     * 从网络中获取物品，转移到接口槽位
     *
     * @return 是否发生了变化
     */
    static boolean transferFromNet(DimensionsNet net, StackHandler stackHandler, StackHandler fakeStackHandler,
        int slotCount, FuzzyMode fuzzyMode) {
        if (net == null) return false;
        boolean changed = false;
        for (int i = 0; i < slotCount; i++) {
            KeyAmount flag = fakeStackHandler.getStackBySlot(i);
            if (flag.isEmpty()) continue;
            KeyAmount current = stackHandler.getStackBySlot(i);
            if (!current.isEmpty() && !current.key()
                .isSameTypeSameComponents(flag.key())) continue;
            long currentAmount = current.isEmpty() ? 0 : current.amount();
            long missing = flag.key()
                .getVanillaMaxStackSize() - currentAmount;
            if (missing <= 0) continue;
            KeyAmount stack = net.getUnifiedStorage()
                .extract(flag.key(), missing, false, fuzzyMode == FuzzyMode.ENABLE);
            if (!stack.isEmpty()) {
                KeyAmount remaining = stackHandler.insert(i, stack.key(), stack.amount(), false);
                if (!remaining.isEmpty()) {
                    net.getUnifiedStorage()
                        .insert(remaining.key(), remaining.amount(), false);
                }
                changed |= remaining.amount() != stack.amount();
            }
        }
        return changed;
    }
}
