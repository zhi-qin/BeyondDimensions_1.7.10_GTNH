package com.wintercogs.beyonddimensions.api.energy;

import net.minecraftforge.common.util.ForgeDirection;

/**
 * 自定义能量处理器接口（1.7.10 移植版）。
 * <p>
 * 对应 1.20.1 的 ForgeCapabilities.ENERGY（IEnergyStorage）以及
 * 1.7.10 CoFH 的 {@code cofh.api.energy.IEnergyHandler}。
 * <p>
 * 由于 GTNH 环境的 RF API 版本兼容性较为复杂，本模组暂未引入 CoFH 依赖，
 * 因此使用自定义接口实现模组内部及相邻方块的能量交互。
 * 当后续在 dependencies.gradle 中启用 CoFHCore 后，可直接让本接口扩展
 * {@code cofh.api.energy.IEnergyHandler}，或将本接口替换为 CoFH 的实现，
 * 调用方代码无需改动。
 */
public interface IEnergyHandler {

    /**
     * 从指定方向接收能量，返回实际接收量。
     *
     * @param from       能量输入方向（相对于本方块）
     * @param maxReceive 最多接收的能量
     * @param simulate   为 true 时仅模拟，不实际改变存储
     * @return 实际接收的能量
     */
    int receiveEnergy(ForgeDirection from, int maxReceive, boolean simulate);

    /**
     * 从指定方向提取能量，返回实际提取量。
     *
     * @param from       能量输出方向（相对于本方块）
     * @param maxExtract 最多提取的能量
     * @param simulate   为 true 时仅模拟，不实际改变存储
     * @return 实际提取的能量
     */
    int extractEnergy(ForgeDirection from, int maxExtract, boolean simulate);

    /**
     * 获取当前存储的能量。
     *
     * @param from 查询方向
     * @return 当前能量值
     */
    int getEnergyStored(ForgeDirection from);

    /**
     * 获取最大能量容量。
     *
     * @param from 查询方向
     * @return 最大能量容量
     */
    int getMaxEnergyStored(ForgeDirection from);

    /**
     * 判断指定方向是否可以连接能量管道。
     *
     * @param from 连接方向
     * @return 是否可以连接
     */
    boolean canConnectEnergy(ForgeDirection from);
}
