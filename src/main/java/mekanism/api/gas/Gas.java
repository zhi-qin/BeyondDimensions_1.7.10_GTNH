package mekanism.api.gas;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.Fluid;

/**
 * Mekanism Gas 类存根（1.7.10 编译时占位）。
 * <p>
 * 此类为 Mekanism API 的编译时存根，仅保留公共方法签名与最小实现，
 * 用于让本模组的 Mekanism 联动模块在缺少 Mekanism 依赖时也能编译通过。
 * 运行时若 Mekanism 已加载，真实的 {@code Gas} 类会覆盖此存根。
 * <p>
 * 原始版权归 Mekanism 作者 aidancbrary 所有。
 */
public class Gas {

    private String name;
    private String unlocalizedName;
    private Fluid fluid;
    private IIcon icon;
    private boolean visible = true;
    private boolean from_fluid = false;

    public Gas(String s) {
        unlocalizedName = name = s;
    }

    public Gas(Fluid f) {
        unlocalizedName = name = f.getName();
        icon = f.getStillIcon();
        fluid = f;
        from_fluid = true;
    }

    public String getName() {
        return name;
    }

    public boolean isVisible() {
        return visible;
    }

    public Gas setVisible(boolean v) {
        visible = v;
        return this;
    }

    public String getUnlocalizedName() {
        return "gas." + unlocalizedName;
    }

    public String getLocalizedName() {
        return StatCollector.translateToLocal(getUnlocalizedName());
    }

    public Gas setUnlocalizedName(String s) {
        unlocalizedName = s;
        return this;
    }

    public IIcon getIcon() {
        if (from_fluid) {
            return this.getFluid()
                .getIcon();
        }
        return icon;
    }

    public Gas setIcon(IIcon i) {
        icon = i;
        if (hasFluid()) {
            fluid.setIcons(getIcon());
        }
        from_fluid = false;
        return this;
    }

    public int getID() {
        return GasRegistry.getGasID(this);
    }

    public NBTTagCompound write(NBTTagCompound nbtTags) {
        nbtTags.setString("gasName", getName());
        return nbtTags;
    }

    public static Gas readFromNBT(NBTTagCompound nbtTags) {
        if (nbtTags == null || nbtTags.hasNoTags()) {
            return null;
        }
        return GasRegistry.getGas(nbtTags.getString("gasName"));
    }

    public boolean hasFluid() {
        return fluid != null;
    }

    public Fluid getFluid() {
        return fluid;
    }

    @Override
    public String toString() {
        return name;
    }
}
