package mekanism.api.gas;

import java.util.ArrayList;
import java.util.List;

import net.minecraftforge.fluids.Fluid;

/**
 * Mekanism GasRegistry 类存根（1.7.10 编译时占位）。
 * <p>
 * 此类为 Mekanism API 的编译时存根，仅保留公共方法签名与最小实现，
 * 用于让本模组的 Mekanism 联动模块在缺少 Mekanism 依赖时也能编译通过。
 * 运行时若 Mekanism 已加载，真实的 {@code GasRegistry} 类会覆盖此存根。
 * <p>
 * 原始版权归 Mekanism 作者 aidancbrary 所有。
 */
public class GasRegistry {

    private static ArrayList<Gas> registeredGasses = new ArrayList<Gas>();

    public static Gas register(Gas gas) {
        if (gas == null) {
            return null;
        }
        registeredGasses.add(gas);
        return getGas(gas.getName());
    }

    public static Gas getGas(int id) {
        if (id == -1 || id < 0 || id >= registeredGasses.size()) {
            return null;
        }
        return registeredGasses.get(id);
    }

    public static Gas getGas(Fluid f) {
        for (Gas gas : getRegisteredGasses()) {
            if (gas.hasFluid() && gas.getFluid() == f) {
                return gas;
            }
        }
        return null;
    }

    public static boolean containsGas(String name) {
        return getGas(name) != null;
    }

    public static List<Gas> getRegisteredGasses() {
        return (List<Gas>) registeredGasses.clone();
    }

    public static Gas getGas(String name) {
        if (name == null) {
            return null;
        }
        for (Gas gas : registeredGasses) {
            if (gas.getName()
                .toLowerCase()
                .equals(name.toLowerCase())) {
                return gas;
            }
        }
        return null;
    }

    public static int getGasID(Gas gas) {
        if (gas == null || !containsGas(gas.getName())) {
            return -1;
        }
        return registeredGasses.indexOf(gas);
    }
}
