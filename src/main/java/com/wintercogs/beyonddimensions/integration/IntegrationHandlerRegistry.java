package com.wintercogs.beyonddimensions.integration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;

/**
 * 外部处理器提供者注册表（1.7.10 适配版）。
 * <p>
 * 源项目（1.20.1）使用 Forge Capability 系统（CapabilityHelper.BlockCapabilityMap）
 * 让 NetPump/NetInterface 等机器遍历相邻方块的所有能力。
 * 1.7.10 无 Capability 系统，改用 instanceof 直接检查（见 NetPumpBlockEntity）。
 * <p>
 * 本注册表为联动模组提供扩展点：联动模块可注册 {@link IExternalHandlerProvider}，
 * 让 NetPump/NetInterface 能识别并抽取目标模组的特殊资源（如 Botania Mana、Mekanism Gas）。
 * <p>
 * 设计原则：
 * - 联动模块在 onBootstrap 中调用 registerProvider() 注册提供者
 * - NetPump/NetInterface 在遍历相邻方块时额外查询本注册表
 * - 提供者内部通过 instanceof 检查目标模组的接口（如 IManaReceiver、IGasHandler）
 * - 若联动模组未加载，注册表为空，不影响原有功能
 * <p>
 * 线程约束：仅允许在初始化期（postInit 引导）写入 {@link #registerProvider}，
 * 运行期只读遍历 {@link #getProviders}；底层 ArrayList 无同步，不要在运行期动态注册。
 */
public final class IntegrationHandlerRegistry {

    private static final List<IExternalHandlerProvider> PROVIDERS = new ArrayList<>();

    private IntegrationHandlerRegistry() {}

    /**
     * 注册一个外部处理器提供者
     */
    public static void registerProvider(IExternalHandlerProvider provider) {
        if (provider != null) {
            PROVIDERS.add(provider);
        }
    }

    /**
     * 获取所有已注册的提供者（不可变视图）
     */
    public static List<IExternalHandlerProvider> getProviders() {
        return Collections.unmodifiableList(PROVIDERS);
    }

    /**
     * 外部处理器提供者接口。
     * <p>
     * 联动模块实现此接口，将目标模组的方块/物品能力桥接到维度网络的统一存储系统。
     */
    public interface IExternalHandlerProvider {

        /**
         * 返回此提供者处理的 StackKey 类型 ID（如 ManaStackKey.ID、GasStackKey.ID）
         */
        net.minecraft.util.ResourceLocation getStackTypeId();

        /**
         * 检查给定 TileEntity 是否被此提供者支持
         */
        boolean matches(TileEntity te);

        /**
         * 获取可从该 TileEntity 抽取的内容列表（用于泵的扫描抽取）
         *
         * @param te   目标方块
         * @param side 从本方块看向目标方块的方向
         * @return 可抽取的内容列表，每个 KeyAmount 代表一种可抽取资源及其可用数量
         */
        List<KeyAmount> getExtractableContents(TileEntity te, ForgeDirection side);

        /**
         * 从该 TileEntity 抽取资源
         *
         * @param te       目标方块
         * @param key      要抽取的资源键
         * @param amount   要抽取的数量
         * @param simulate 是否仅模拟
         * @param side     从本方块看向目标方块的方向
         * @return 实际抽取的 KeyAmount（数量可能小于请求量）
         */
        KeyAmount extract(TileEntity te, IStackKey<?> key, long amount, boolean simulate, ForgeDirection side);

        /**
         * 向该 TileEntity 插入资源
         *
         * @param te       目标方块
         * @param key      要插入的资源键
         * @param amount   要插入的数量
         * @param simulate 是否仅模拟
         * @param side     从本方块看向目标方块的方向
         * @return 未能插入的剩余数量
         */
        long insert(TileEntity te, IStackKey<?> key, long amount, boolean simulate, ForgeDirection side);
    }
}
