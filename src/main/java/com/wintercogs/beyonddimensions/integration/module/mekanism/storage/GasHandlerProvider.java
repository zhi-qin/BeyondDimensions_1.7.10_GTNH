package com.wintercogs.beyonddimensions.integration.module.mekanism.storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.ForgeDirection;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.integration.IntegrationHandlerRegistry;
import com.wintercogs.beyonddimensions.util.BDMath;

import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IGasHandler;

/**
 * Mekanism IGasHandler 桥接提供者（1.7.10 适配新增）。
 * <p>
 * 1.7.10 无 Forge Capability 系统，NetPump/NetInterface 通过
 * {@link IntegrationHandlerRegistry#getProviders()} 遍历已注册的提供者，
 * 由本类将 Mekanism 的 {@link IGasHandler} 方块桥接到维度网络的统一存储系统。
 * <p>
 * 实现要点：
 * - matches: 通过 instanceof IGasHandler 识别目标方块
 * - getExtractableContents: 用 drawGas(side, MAX, false) 模拟抽取以探测当前可抽取的 Gas 类型
 * - extract: 检查 canDrawGas 后调用 drawGas(side, amount, !simulate)
 * - insert: 检查 canReceiveGas 后构造 GasStack 调用 receiveGas(side, stack, !simulate)
 * <p>
 * 注意：IGasHandler 的 doTransfer 参数 true=实际操作，false=模拟，
 * 与本接口的 simulate 语义相反，故传入 !simulate。
 */
public final class GasHandlerProvider implements IntegrationHandlerRegistry.IExternalHandlerProvider {

    public GasHandlerProvider() {}

    @Override
    public ResourceLocation getStackTypeId() {
        return GasStackKey.ID;
    }

    @Override
    public boolean matches(TileEntity te) {
        return te instanceof IGasHandler;
    }

    @Override
    public List<KeyAmount> getExtractableContents(TileEntity te, ForgeDirection side) {
        if (!(te instanceof IGasHandler)) return Collections.emptyList();
        IGasHandler handler = (IGasHandler) te;
        try {
            // 模拟抽取最大量以探测当前可抽取的 Gas（doTransfer=false 表示模拟）
            GasStack simulated = handler.drawGas(side, Integer.MAX_VALUE, false);
            if (simulated == null || simulated.getGas() == null || simulated.amount <= 0) {
                return Collections.emptyList();
            }
            List<KeyAmount> list = new ArrayList<>(1);
            list.add(new KeyAmount(new GasStackKey(new GasStack(simulated.getGas(), 1)), simulated.amount));
            return list;
        } catch (Throwable t) {
            // 部分 IGasHandler 实现可能对 Integer.MAX_VALUE 行为异常，安全降级
            return Collections.emptyList();
        }
    }

    @Override
    public KeyAmount extract(TileEntity te, IStackKey<?> key, long amount, boolean simulate, ForgeDirection side) {
        if (!(te instanceof IGasHandler)) return null;
        if (!(key instanceof GasStackKey)) return null;
        GasStackKey gk = (GasStackKey) key;
        Gas gas = (Gas) gk.getSource();
        if (gas == null) return null;
        if (amount <= 0) return null;

        IGasHandler handler = (IGasHandler) te;
        if (!handler.canDrawGas(side, gas)) return null;

        int toDraw = BDMath.clampLongToInt(amount);
        if (toDraw <= 0) return null;

        // doTransfer=true 实际抽取，false 模拟；与 simulate 语义相反
        GasStack drawn = handler.drawGas(side, toDraw, !simulate);
        if (drawn == null || drawn.getGas() == null || drawn.amount <= 0) return null;
        return new KeyAmount(new GasStackKey(new GasStack(drawn.getGas(), 1)), drawn.amount);
    }

    @Override
    public long insert(TileEntity te, IStackKey<?> key, long amount, boolean simulate, ForgeDirection side) {
        if (!(te instanceof IGasHandler)) return amount;
        if (!(key instanceof GasStackKey)) return amount;
        GasStackKey gk = (GasStackKey) key;
        Gas gas = (Gas) gk.getSource();
        if (gas == null) return amount;
        if (amount <= 0) return 0;

        IGasHandler handler = (IGasHandler) te;
        if (!handler.canReceiveGas(side, gas)) return amount;

        int toInsert = BDMath.clampLongToInt(amount);
        if (toInsert <= 0) return amount;

        GasStack stack = new GasStack(gas, toInsert);
        // receiveGas 返回实际接收量；doTransfer=true 实际插入，false 模拟
        int received = handler.receiveGas(side, stack, !simulate);
        if (received < 0) received = 0;
        long leftover = amount - received;
        return leftover < 0 ? 0 : leftover;
    }
}
