package com.wintercogs.beyonddimensions.integration.rf;

import com.wintercogs.beyonddimensions.common.block.entity.NetEnergyPathwayBlockEntity;

import cofh.api.energy.IEnergyHandler;

/**
 * 通用 RF 能量通道变体（1.7.10 移植新增）。
 * <p>
 * 源项目（1.20.1）能量通道经 ForgeCapabilities.ENERGY 暴露网络存储，
 * 一切持有能量 Capability 的模组都能直接向它推送。1.7.10 无 Capability 系统，
 * 等价契约是 CoFH 的 {@code cofh.api.energy.IEnergyHandler}（RF API）。
 * 本变体通过实现该接口让 CoFH RF 系模组（Thermal Series / EnderIO 等）及
 * 内嵌 CoFH RF API 的 Mekanism 9.x 都能识别本方块的受能能力。
 * <p>
 * 本模组自定义 {@code com.wintercogs.beyonddimensions.api.energy.IEnergyHandler}
 * 与 CoFH 版方法签名完全一致（{@code receiveEnergy}/{@code extractEnergy}/
 * {@code getEnergyStored}/{@code getMaxEnergyStored}/{@code canConnectEnergy}），
 * 故基类零改动即可满足契约——本类不含任何方法体。
 * <p>
 * 由 {@code BDBlockEntities.resolveEnergyPathwayClass()} 在 CoFH RF API 可用时
 * 经字符串反射注册；无 CoFH 的环境不会加载本类，无 NoClassDefFoundError 风险。
 */
public class RfNetEnergyPathwayBlockEntity extends NetEnergyPathwayBlockEntity implements IEnergyHandler {

    public RfNetEnergyPathwayBlockEntity() {
        super();
    }
}
