package com.wintercogs.beyonddimensions.integration.rf;

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

import cofh.api.energy.IEnergyProvider;
import cofh.api.energy.IEnergyReceiver;

/**
 * CoFH RF API 能量桥接提供者（1.7.10 移植新增）。
 * <p>
 * 1.7.10 无 Capability 系统，CoFH RF 机器（CoFHCore/Thermal Expansion/EnderIO 等）实现的是
 * {@code cofh.api.energy.IEnergyReceiver} / {@code IEnergyProvider}（int RF 语义），与本模组
 * 自定义 {@code IEnergyHandler}（int RF 语义）无接口交集。本类经
 * {@link IntegrationHandlerRegistry#registerProvider} 注册为能量提供者（stackType =
 * {@link EnergyStackKey#ID}），让能量通道 OPEN 模式能向纯 CoFH RF 机器推送能量
 * （审计 M2-1：此前只支持 Mekanism 提供者，OPEN 推送纯 RF 机器时静默失败）。
 * <p>
 * 运行时说明：{@code cofh.api.energy.*} 为本项目内置的编译期存根（见
 * {@code src/main/java/cofh/api/energy}），CoFHCore 或内嵌 CoFH API 的模组会在运行时覆盖
 * 同名类。CoFH 缺席时 matches 对存根类判定必然不命中，注册表多一个无副作用条目；
 * CoFH 存在时按真实接口桥接。
 */
public final class CoFHEnergyHandlerProvider implements IntegrationHandlerRegistry.IExternalHandlerProvider {

    public CoFHEnergyHandlerProvider() {}

    @Override
    public ResourceLocation getStackTypeId() {
        return EnergyStackKey.ID;
    }

    @Override
    public boolean matches(TileEntity te) {
        return te instanceof IEnergyReceiver;
    }

    @Override
    public List<KeyAmount> getExtractableContents(TileEntity te, ForgeDirection side) {
        if (!(te instanceof IEnergyProvider)) return Collections.emptyList();
        IEnergyProvider provider = (IEnergyProvider) te;
        try {
            int stored = provider.getEnergyStored(side);
            if (stored <= 0) return Collections.emptyList();
            List<KeyAmount> list = new ArrayList<>(1);
            list.add(new KeyAmount(EnergyStackKey.INSTANCE, stored));
            return list;
        } catch (Throwable t) {
            // 部分实现可能对 side 无关查询行为异常，安全降级
            return Collections.emptyList();
        }
    }

    @Override
    public KeyAmount extract(TileEntity te, IStackKey<?> key, long amount, boolean simulate, ForgeDirection side) {
        if (!(te instanceof IEnergyProvider)) return null;
        if (!(key instanceof EnergyStackKey)) return null;
        if (amount <= 0) return null;

        IEnergyProvider provider = (IEnergyProvider) te;
        try {
            int maxExtract = (int) Math.min(amount, Integer.MAX_VALUE);
            int extracted = provider.extractEnergy(side, maxExtract, simulate);
            if (extracted <= 0) return null;
            return new KeyAmount(EnergyStackKey.INSTANCE, extracted);
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public long insert(TileEntity te, IStackKey<?> key, long amount, boolean simulate, ForgeDirection side) {
        if (!(te instanceof IEnergyReceiver)) return amount;
        if (!(key instanceof EnergyStackKey)) return amount;
        if (amount <= 0) return 0;

        IEnergyReceiver receiver = (IEnergyReceiver) te;
        try {
            int maxReceive = (int) Math.min(amount, Integer.MAX_VALUE);
            int accepted = receiver.receiveEnergy(side, maxReceive, simulate);
            long leftover = amount - accepted;
            return leftover < 0 ? 0 : leftover;
        } catch (Throwable t) {
            return amount;
        }
    }
}
