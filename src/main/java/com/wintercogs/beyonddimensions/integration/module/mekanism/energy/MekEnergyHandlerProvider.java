package com.wintercogs.beyonddimensions.integration.module.mekanism.energy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.ForgeDirection;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import com.wintercogs.beyonddimensions.integration.IntegrationHandlerRegistry;

import mekanism.api.energy.IStrictEnergyAcceptor;
import mekanism.api.energy.IStrictEnergyStorage;

/**
 * Mekanism IStrictEnergyStorage/IStrictEnergyAcceptor 能量桥接提供者（1.7.10 移植新增）。
 * <p>
 * 源项目（1.20.1）中 Mekanism 10.x 经 ForgeCapabilities.ENERGY 与网络能量存储互操作；
 * 1.7.10 无 Capability 系统，Mekanism 9.x 的能量 API 是 {@link IStrictEnergyAcceptor} /
 * {@link IStrictEnergyStorage}（double 焦耳语义），与本模组自定义 {@code IEnergyHandler}
 * （int RF 语义）无接口交集。本类经 {@link IntegrationHandlerRegistry#registerProvider}
 * 注册为能量提供者（stackType = {@link EnergyStackKey#ID}），让
 * NetPump / NetInterface / NetEnergyPathway 通过既有注册表机制自动获得
 * Mekanism 储能方块的读写能力：
 * <ul>
 * <li>抽取：{@code getEnergy()}/{@code setEnergy()} 直接结算（对齐 Mekanism
 * {@code PartUniversalCable} 对源方块的抽取方式），折算为 RF 入网</li>
 * <li>插入：{@code canReceiveEnergy(side)} + {@code transferEnergyToAcceptor(side, ...)}
 * （对齐 {@code CableUtils.emit_do_do} 对受能方块的推送方式）</li>
 * </ul>
 * 单位换算统一走 {@link MekEnergyConstants}，与 Mekanism 自身 CoFH 兼容层同一换算源。
 */
public final class MekEnergyHandlerProvider implements IntegrationHandlerRegistry.IExternalHandlerProvider {

    public MekEnergyHandlerProvider() {}

    @Override
    public ResourceLocation getStackTypeId() {
        return EnergyStackKey.ID;
    }

    @Override
    public boolean matches(TileEntity te) {
        // IStrictEnergyAcceptor 继承 IStrictEnergyStorage，一个 instanceof 同时覆盖两类方块
        return te instanceof IStrictEnergyStorage;
    }

    @Override
    public List<KeyAmount> getExtractableContents(TileEntity te, ForgeDirection side) {
        if (!(te instanceof IStrictEnergyStorage)) return Collections.emptyList();
        IStrictEnergyStorage storage = (IStrictEnergyStorage) te;
        try {
            double stored = storage.getEnergy();
            if (!(stored > 0)) return Collections.emptyList(); // 同时排除 NaN
            long storedRF = MekEnergyConstants.joulesToRF(stored);
            if (storedRF <= 0) return Collections.emptyList();
            List<KeyAmount> list = new ArrayList<>(1);
            list.add(new KeyAmount(EnergyStackKey.INSTANCE, storedRF));
            return list;
        } catch (Throwable t) {
            // 部分实现可能对 side 无关查询行为异常，安全降级
            return Collections.emptyList();
        }
    }

    @Override
    public KeyAmount extract(TileEntity te, IStackKey<?> key, long amount, boolean simulate, ForgeDirection side) {
        if (!(te instanceof IStrictEnergyStorage)) return null;
        if (!(key instanceof EnergyStackKey)) return null;
        if (amount <= 0) return null;

        IStrictEnergyStorage storage = (IStrictEnergyStorage) te;
        try {
            double stored = storage.getEnergy();
            if (!(stored > 0)) return null;

            double wantJ = MekEnergyConstants.rfToJoules(amount);
            double toDrawJ = Math.min(stored, wantJ);
            if (!(toDrawJ > 0)) return null;

            // 先按焦耳量折算 RF 并判定，再执行扣减：
            // 避免亚 RF 量（不足 1 RF）时先扣了 Mek 侧能量、却因 drawnRF <= 0
            // 无法入网导致能量凭空消失
            long drawnRF = MekEnergyConstants.joulesToRF(toDrawJ);
            drawnRF = Math.min(drawnRF, amount);
            if (drawnRF <= 0) return null;

            if (!simulate) {
                // 按实际入网的 RF 折算回焦耳扣减，保证两侧守恒（不超扣、不虚报）
                storage.setEnergy(stored - MekEnergyConstants.rfToJoules(drawnRF));
            }
            return new KeyAmount(EnergyStackKey.INSTANCE, drawnRF);
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public long insert(TileEntity te, IStackKey<?> key, long amount, boolean simulate, ForgeDirection side) {
        if (!(te instanceof IStrictEnergyAcceptor)) return amount;
        if (!(key instanceof EnergyStackKey)) return amount;
        if (amount <= 0) return 0;

        IStrictEnergyAcceptor acceptor = (IStrictEnergyAcceptor) te;
        try {
            if (!acceptor.canReceiveEnergy(side)) return amount;

            double wantJ = MekEnergyConstants.rfToJoules(amount);
            double usedJ;
            if (simulate) {
                // IStrictEnergyAcceptor 无 simulate 参数，按容量估算可接收量
                double freeJ = acceptor.getMaxEnergy() - acceptor.getEnergy();
                if (!(freeJ > 0)) return amount;
                usedJ = Math.min(freeJ, wantJ);
            } else {
                usedJ = acceptor.transferEnergyToAcceptor(side, wantJ);
            }
            if (!(usedJ > 0)) return amount;

            long usedRF = MekEnergyConstants.joulesToRF(usedJ);
            usedRF = Math.min(usedRF, amount);
            long leftover = amount - usedRF;
            return leftover < 0 ? 0 : leftover;
        } catch (Throwable t) {
            return amount;
        }
    }
}
