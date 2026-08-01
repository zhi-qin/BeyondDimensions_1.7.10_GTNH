package com.wintercogs.beyonddimensions.integration.module.ae2.item;

import net.minecraft.item.Item;

/**
 * AE2 能量滴物品（1.7.10 适配版）。
 * <p>
 * 1.7.10 AE2 的 {@code StorageChannel} 只有 ITEMS/FLUIDS，没有能量通道，也没有
 * 源项目（1.20.1）使用的 AppFlux {@code FluxKey} 自定义键类型。为使维度网络能量
 * 能在 ME 终端可见，以"能量滴"物品形式暴露进 ITEMS 通道：1 FE = 1 个能量滴，
 * AE 堆叠计数即 FE 数（参照 AE2FC 离散器 {@code ItemFluidDrop} 的 1 mB = 1 滴模式）。
 * <p>
 * 该物品本身不导入 AE2 类，可在无 AE2 环境下安全加载；物品↔能量的转换逻辑由
 * {@link com.wintercogs.beyonddimensions.integration.module.ae2.me.NetStorageCell}
 * 特判处理。
 */
public class NetEnergyDrop extends Item {

    public NetEnergyDrop() {
        super();
        setMaxStackSize(64);
    }
}
