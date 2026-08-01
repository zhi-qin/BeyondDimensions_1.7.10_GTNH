package com.wintercogs.beyonddimensions.integration.module.mekanism.energy;

import net.minecraftforge.common.util.ForgeDirection;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.integration.rf.RfNetEnergyPathwayBlockEntity;
import com.wintercogs.beyonddimensions.util.BDMath;

import mekanism.api.energy.IStrictEnergyAcceptor;

/**
 * Mekanism 能量通道变体（1.7.10 移植新增）。
 * <p>
 * 源项目（1.20.1）中 Mekanism 10.x 经 ForgeCapabilities.ENERGY 主动向能量通道
 * 推送能量（"通用能量被动接收"）。1.7.10 无 Capability，Mekanism 9.x 的能量
 * 推送（{@code CableUtils.emit}）只认 {@link IStrictEnergyAcceptor}，且仅在本方块
 * 实现该接口时调用 {@code canReceiveEnergy(side)} 与 {@code transferEnergyToAcceptor(side, amount)}。
 * 本变体实现该接口，将焦耳推流量折算为 RF 后复用基类 {@link #receiveEnergy} 入网，
 * 按返回值与 Mekanism 侧结算保证守恒。
 * <p>
 * {@code side} 语义：Mekanism 推送时传入的是从受能方指向源的方向
 * （{@code CableUtils.emit_do_do} 中 {@code side.getOpposite()}），即本方块的能量输入面，
 * 与基类 {@code IEnergyHandler.receiveEnergy(ForgeDirection from, ...)} 的 {@code from} 一致。
 * <p>
 * 由 {@code BDBlockEntities.resolveEnergyPathwayClass()} 在 Mekanism 已加载时
 * 经字符串反射注册；无 Mekanism 的环境不会加载本类，无 NoClassDefFoundError 风险。
 */
public class MekNetEnergyPathwayBlockEntity extends RfNetEnergyPathwayBlockEntity implements IStrictEnergyAcceptor {

    public MekNetEnergyPathwayBlockEntity() {
        super();
    }

    // ==================== IStrictEnergyStorage 实现 ====================
    // 网络能量以 RF 计数，对外以焦耳折算（与 Mekanism 侧单位一致）

    @Override
    public double getEnergy() {
        DimensionsNet net = getNet();
        if (net == null) return 0;
        // 与基类 getEnergyStored/extractEnergy 口径一致：RF 池 + EU 池按换算率折算的 RF 预算
        long rf = getRfBudget(net);
        return MekEnergyConstants.rfToJoules(rf);
    }

    @Override
    public void setEnergy(double energy) {
        // 网络能量池为共享存储，没有守恒的来源/去向可将池"设定"为某值；
        // 若在此主动 insert/extract 差额会凭空制造或销毁能量，故 no-op。
        // 本 TE 仅作为 IStrictEnergyAcceptor 受能端，正常 Mekanism 能量流不会调用 setEnergy。
    }

    @Override
    public double getMaxEnergy() {
        DimensionsNet net = getNet();
        if (net == null) return 0;
        // 与 getEnergyStored（getRfBudget 含 EU 折算）对齐：max 取 RF 池容量与当前总预算
        // 的较大者，避免 EU 池非空时 stored > max 的外部读数异常（进度条超限/误判可提取量，
        // 审计 M4-1）
        long capacity = net.getUnifiedStorage()
            .getSlotCapacity(0);
        long budget = getRfBudget(net);
        long maxRF = budget > capacity ? budget : capacity;
        return MekEnergyConstants.rfToJoules(maxRF);
    }

    // ==================== IStrictEnergyAcceptor 实现 ====================

    @Override
    public boolean canReceiveEnergy(ForgeDirection side) {
        return canReceive();
    }

    @Override
    public double transferEnergyToAcceptor(ForgeDirection side, double amount) {
        if (!(amount > 0)) return 0; // 同时排除 NaN
        if (!canReceive()) return 0;

        long maxRF = MekEnergyConstants.joulesToRF(amount);
        if (maxRF <= 0) return 0; // 不足 1 RF 的焦耳量保留在 Mekanism 侧

        int acceptedRF = receiveEnergy(side, BDMath.clampLongToInt(maxRF), false);
        if (acceptedRF <= 0) return 0;
        return MekEnergyConstants.rfToJoules(acceptedRF);
    }
}
