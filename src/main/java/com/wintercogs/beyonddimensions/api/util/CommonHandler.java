package com.wintercogs.beyonddimensions.api.util;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.wintercogs.beyonddimensions.api.storage.handler.IStackHandler;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EmptyStackKey;

/**
 * 通用存储操作处理器（1.7.10 适配版）。
 * 封装 IStackHandler 的常用操作，提供便捷的批量插入/提取方法。
 */
public class CommonHandler {

    protected final IStackHandler handler;

    public CommonHandler(IStackHandler handler) {
        this.handler = handler;
    }

    public IStackHandler getHandler() {
        return handler;
    }

    /**
     * 获取所有存储内容的只读视图
     */
    @Nonnull
    public List<KeyAmount> getStacks() {
        return handler.getStorage();
    }

    /**
     * 获取非空槽位数量
     */
    public int getNonEmptySlotCount() {
        int count = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            KeyAmount ka = handler.getStackBySlot(i);
            if (ka != null && !ka.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    /**
     * 批量插入多个堆叠
     *
     * @param stacks   要插入的堆叠列表
     * @param simulate 是否仅模拟
     * @return 未能插入的余量列表
     */
    @Nonnull
    public List<KeyAmount> insertAll(@Nonnull List<KeyAmount> stacks, boolean simulate) {
        List<KeyAmount> remaining = new ArrayList<>();
        for (KeyAmount stack : stacks) {
            if (stack == null || stack.isEmpty()) continue;
            KeyAmount result = handler.insert(stack.key, stack.amount, simulate);
            if (result != null && !result.isEmpty()) {
                remaining.add(result);
            }
        }
        return remaining;
    }

    /**
     * 批量提取多个堆叠
     *
     * @param stacks   要提取的堆叠列表
     * @param simulate 是否仅模拟
     * @param fuzzy    是否模糊匹配
     * @return 实际提取到的堆叠列表
     */
    @Nonnull
    public List<KeyAmount> extractAll(@Nonnull List<KeyAmount> stacks, boolean simulate, boolean fuzzy) {
        List<KeyAmount> extracted = new ArrayList<>();
        for (KeyAmount stack : stacks) {
            if (stack == null || stack.isEmpty()) continue;
            KeyAmount result = handler.extract(stack.key, stack.amount, simulate, fuzzy);
            if (result != null && !result.isEmpty()) {
                extracted.add(result);
            }
        }
        return extracted;
    }

    /**
     * 检查是否可以完全插入所有堆叠
     */
    public boolean canInsertAll(@Nonnull List<KeyAmount> stacks) {
        List<KeyAmount> remaining = insertAll(stacks, true);
        return remaining.isEmpty();
    }

    /**
     * 检查是否可以完全提取所有堆叠
     */
    public boolean canExtractAll(@Nonnull List<KeyAmount> stacks, boolean fuzzy) {
        for (KeyAmount stack : stacks) {
            if (stack == null || stack.isEmpty()) continue;
            KeyAmount result = handler.extract(stack.key, stack.amount, true, fuzzy);
            if (result == null || result.isEmpty() || result.amount < stack.amount) {
                return false;
            }
        }
        return true;
    }

    /**
     * 获取存储中某种堆叠的总量
     */
    public long getTotalAmount(@Nullable IStackKey<?> key) {
        if (key == null || key == EmptyStackKey.INSTANCE) return 0L;
        KeyAmount found = handler.getStackByKey(key);
        return found != null ? found.amount : 0L;
    }

    /**
     * 清空存储
     */
    public void clear() {
        handler.clearStorage();
    }

    /**
     * 检查存储是否为空
     */
    public boolean isEmpty() {
        return handler.isEmpty();
    }
}
