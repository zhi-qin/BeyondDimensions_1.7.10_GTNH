package com.wintercogs.beyonddimensions.api.storage.handler;

import java.util.List;

import javax.annotation.Nonnull;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;

/**
 * 堆叠存储处理器接口
 * <p>
 * 1.7.10 移植版：@NotNull → javax.annotation.Nonnull，default 方法保留（Java 8 支持）
 */
public interface IStackHandler {

    /**
     * 获取只读存储视图
     */
    List<KeyAmount> getStorage();

    /**
     * 当存储内容改变后，调用此方法
     */
    void onChange();

    /**
     * 获取当前容器的槽位数量
     */
    default int getSlots() {
        return getStorage().size();
    }

    /**
     * 清空容器
     */
    void clearStorage();

    /**
     * 获取指定槽位的堆叠，不要直接修改
     */
    @Nonnull
    KeyAmount getStackBySlot(int slot);

    /**
     * 根据传入的堆叠种类精确匹配（包括类型、内容、NBT，但不包括堆叠的当前数量），并返回找到的堆叠。
     */
    @Nonnull
    KeyAmount getStackByKey(IStackKey<?> key);

    /**
     * 当前存储是否存在此堆叠，精确匹配
     */
    boolean hasStack(IStackKey<?> key);

    /**
     * 直接在指定槽位设置堆叠，仅在你确定你需要的时候再使用
     */
    void setStackDirectly(int slot, IStackKey<?> key, long amount);

    /**
     * 在存储末尾添加一个堆叠，仅在你确定你需要的时候再使用
     */
    void addStackDirectly(IStackKey<?> key, long amount);

    /**
     * 尝试将指定的堆叠插入指定的槽位，并返回余量。
     */
    @Nonnull
    KeyAmount insert(int slot, IStackKey<?> key, long amount, boolean simulate);

    /**
     * 尝试插入指定的堆叠，直到容器所有位置被填满，然后返回剩余堆叠。
     */
    @Nonnull
    KeyAmount insert(IStackKey<?> key, long amount, boolean simulate);

    /**
     * 尝试从指定的槽位提取出指定数量的堆叠，并返回提取的堆叠。
     */
    @Nonnull
    KeyAmount extract(int slot, long amount, boolean simulate);

    /**
     * 按类型导出堆叠，并返回提取的堆叠
     */
    @Nonnull
    KeyAmount extract(IStackKey<?> key, long amount, boolean simulate, boolean fuzzy);

    /**
     * 按类型导出堆叠（精确匹配）
     */
    @Nonnull
    default KeyAmount extract(IStackKey<?> key, long amount, boolean simulate) {
        return extract(key, amount, simulate, false);
    }

    /**
     * 指定的槽位最大容量是多少？
     */
    long getSlotCapacity(int slot);

    /**
     * 指定的堆叠是否能插入指定的槽位？
     */
    boolean isStackValid(int slot, IStackKey<?> key);

    /**
     * 当前容器内是否存有物品
     */
    boolean isEmpty();
}
