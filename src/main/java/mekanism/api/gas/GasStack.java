package mekanism.api.gas;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Mekanism GasStack 类存根（1.7.10 编译时占位）。
 * <p>
 * 此类为 Mekanism API 的编译时存根，仅保留公共方法签名与最小实现，
 * 用于让本模组的 Mekanism 联动模块在缺少 Mekanism 依赖时也能编译通过。
 * 运行时若 Mekanism 已加载，真实的 {@code GasStack} 类会覆盖此存根。
 * <p>
 * 原始版权归 Mekanism 作者 aidancbrary 所有。
 */
public class GasStack {

    private Gas type;
    public int amount;

    public GasStack(int id, int quantity) {
        type = GasRegistry.getGas(id);
        amount = quantity;
    }

    public GasStack(Gas gas, int quantity) {
        type = gas;
        amount = quantity;
    }

    private GasStack() {}

    public Gas getGas() {
        return type;
    }

    public GasStack withAmount(int newAmount) {
        amount = newAmount;
        return this;
    }

    public NBTTagCompound write(NBTTagCompound nbtTags) {
        if (type != null) {
            nbtTags.setString("gasName", type.getName());
        }
        nbtTags.setInteger("amount", amount);
        return nbtTags;
    }

    public void read(NBTTagCompound nbtTags) {
        type = Gas.readFromNBT(nbtTags);
        amount = nbtTags.getInteger("amount");
    }

    public static GasStack readFromNBT(NBTTagCompound nbtTags) {
        if (nbtTags == null || nbtTags.hasNoTags()) {
            return null;
        }
        GasStack stack = new GasStack();
        stack.read(nbtTags);
        if (stack.getGas() == null || stack.amount <= 0) {
            return null;
        }
        return stack;
    }

    public GasStack copy() {
        return new GasStack(type, amount);
    }

    public boolean isGasEqual(GasStack stack) {
        return stack != null && getGas() == stack.getGas();
    }

    @Override
    public int hashCode() {
        return type == null ? 0 : type.getID();
    }
}
