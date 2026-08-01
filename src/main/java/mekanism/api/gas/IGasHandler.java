package mekanism.api.gas;

import net.minecraftforge.common.util.ForgeDirection;

/**
 * Mekanism IGasHandler 接口存根（1.7.10 编译时占位）。
 * <p>
 * 此接口为 Mekanism API 的编译时存根，仅用于让本模组的 Mekanism 联动模块在
 * 缺少 Mekanism 依赖时也能编译通过。运行时若 Mekanism 已加载，
 * 真实的 {@code IGasHandler} 类会由 Mekanism 的 classloader 提供，覆盖此存根。
 * <p>
 * 原始版权归 Mekanism 作者 aidancbrary 所有，此处仅保留公共方法签名。
 */
public interface IGasHandler {

    int receiveGas(ForgeDirection side, GasStack stack, boolean doTransfer);

    @Deprecated
    int receiveGas(ForgeDirection side, GasStack stack);

    GasStack drawGas(ForgeDirection side, int amount, boolean doTransfer);

    @Deprecated
    GasStack drawGas(ForgeDirection side, int amount);

    boolean canReceiveGas(ForgeDirection side, Gas type);

    boolean canDrawGas(ForgeDirection side, Gas type);
}
