package mekanism.api.energy;

import net.minecraftforge.common.util.ForgeDirection;

/**
 * Mekanism IStrictEnergyAcceptor 接口存根（1.7.10 编译时占位）。
 * <p>
 * 此接口为 Mekanism API 的编译时存根，仅用于让本模组的 Mekanism 能量联动在
 * 缺少 Mekanism 依赖时也能编译通过。运行时若 Mekanism 已加载，
 * 真实的 {@code IStrictEnergyAcceptor} 类会由 Mekanism 的 classloader 提供，覆盖此存根。
 * <p>
 * 原始版权归 Mekanism 作者 aidancbrady 所有，此处仅保留公共方法签名。
 */
public interface IStrictEnergyAcceptor extends IStrictEnergyStorage {

    double transferEnergyToAcceptor(ForgeDirection side, double amount);

    boolean canReceiveEnergy(ForgeDirection side);
}
