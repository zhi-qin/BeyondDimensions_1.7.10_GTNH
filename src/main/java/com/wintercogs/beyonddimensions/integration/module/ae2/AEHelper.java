package com.wintercogs.beyonddimensions.integration.module.ae2;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;

import appeng.api.AEApi;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;

/**
 * AE2 与 BD IStackKey 之间的类型转换工具（1.7.10 适配版）。
 * <p>
 * 源项目（1.20.1）使用 AEKey 统一抽象，1.7.10 AE2 将物品与流体分为不同通道
 * （IAEItemStack / IAEFluidStack），因此本类分别处理两种类型。
 * <p>
 * 转换映射：
 * - {@link ItemStackKey} ↔ {@link IAEItemStack}（通过 ItemStack 中转）
 * - {@link FluidStackKey} ↔ {@link IAEFluidStack}（通过 FluidStack 中转）
 */
public class AEHelper {

    /** BD IStackKey 类型 ID → 转换为 AE2 IAEStack 的函数 */
    public static final Map<ResourceLocation, Function<IStackKey<?>, IAEStack>> ISTACK_TO_AE_MAP = new HashMap<>();

    /** AE2 IAEStack → 转换为 BD IStackKey 的函数（按运行时类型分派） */
    public static final Map<Class<?>, Function<IAEStack, IStackKey<?>>> AE_TO_ISTACK_MAP = new HashMap<>();

    static {
        // ItemStackKey → IAEItemStack
        ISTACK_TO_AE_MAP.put(ItemStackKey.ID, stack -> {
            if (stack instanceof ItemStackKey) {
                ItemStack is = ((ItemStackKey) stack).copyStack();
                if (is != null) {
                    return AEApi.instance()
                        .storage()
                        .createItemStack(is);
                }
            }
            return null;
        });
        // FluidStackKey → IAEFluidStack
        ISTACK_TO_AE_MAP.put(FluidStackKey.ID, stack -> {
            if (stack instanceof FluidStackKey) {
                FluidStack fs = ((FluidStackKey) stack).copyStack();
                if (fs != null) {
                    return AEApi.instance()
                        .storage()
                        .createFluidStack(fs);
                }
            }
            return null;
        });

        // IAEItemStack → ItemStackKey
        AE_TO_ISTACK_MAP.put(IAEItemStack.class, ae -> {
            ItemStack is = ((IAEItemStack) ae).getItemStack();
            if (is != null) {
                return new ItemStackKey(is);
            }
            return null;
        });
        // IAEFluidStack → FluidStackKey
        AE_TO_ISTACK_MAP.put(IAEFluidStack.class, ae -> {
            FluidStack fs = ((IAEFluidStack) ae).getFluidStack();
            if (fs != null) {
                return new FluidStackKey(fs);
            }
            return null;
        });
    }

    /**
     * 将 AE2 的 IAEStack 转换为 BD 的 IStackKey。
     *
     * @return 对应的 IStackKey，无法转换时返回 null
     */
    @Nullable
    public static IStackKey<?> fromAEToIStack(IAEStack key) {
        if (key == null) {
            return null;
        }
        // 按类型分派
        for (Map.Entry<Class<?>, Function<IAEStack, IStackKey<?>>> entry : AE_TO_ISTACK_MAP.entrySet()) {
            if (entry.getKey()
                .isInstance(key)) {
                return entry.getValue()
                    .apply(key);
            }
        }
        return null;
    }

    /**
     * 将 BD 的 IStackKey 转换为 AE2 的 IAEStack。
     *
     * @return 对应的 IAEStack，无法转换时返回 null
     */
    @Nullable
    public static IAEStack fromIStackToAE(IStackKey<?> stack) {
        if (stack == null) {
            return null;
        }
        Function<IStackKey<?>, IAEStack> fn = ISTACK_TO_AE_MAP.get(stack.getTypeId());
        if (fn != null) {
            return fn.apply(stack);
        }
        return null;
    }
}
