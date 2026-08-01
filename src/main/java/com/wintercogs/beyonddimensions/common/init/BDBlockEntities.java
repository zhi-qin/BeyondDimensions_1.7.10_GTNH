package com.wintercogs.beyonddimensions.common.init;

import net.minecraft.tileentity.TileEntity;

import com.wintercogs.beyonddimensions.api.ids.BDBlockIds;
import com.wintercogs.beyonddimensions.api.ids.BDConstants;
import com.wintercogs.beyonddimensions.common.block.entity.*;
import com.wintercogs.beyonddimensions.integration.ModPresence;
import com.wintercogs.beyonddimensions.integration.OtherModIds;

import cpw.mods.fml.common.registry.GameRegistry;

public class BDBlockEntities {

    /**
     * 通用 RF 能量通道变体全类名（实现 cofh.api.energy.IEnergyHandler）。
     * 仅以字符串引用，避免 RF API 不存在的环境加载本类时触发 NoClassDefFoundError。
     */
    private static final String RF_ENERGY_PATHWAY_CLASS = "com.wintercogs.beyonddimensions.integration.rf.RfNetEnergyPathwayBlockEntity";

    /**
     * Mekanism 能量通道变体全类名（实现 IStrictEnergyAcceptor，继承 RF 变体）。
     */
    private static final String MEK_ENERGY_PATHWAY_CLASS = "com.wintercogs.beyonddimensions.integration.module.mekanism.energy.MekNetEnergyPathwayBlockEntity";

    /**
     * GT 放电模式能量通道变体全类名（实现 IEnergyConnected，继承 RF 变体）。
     * 仅以字符串引用，避免 GT API 不存在的环境加载本类时触发 NoClassDefFoundError。
     */
    private static final String GT_ENERGY_PATHWAY_CLASS = "com.wintercogs.beyonddimensions.integration.module.gt.GtNetEnergyPathwayBlockEntity";

    /**
     * CoFH RF API 探测类名。CoFHCore 或内嵌 CoFH API 的模组（如 Mekanism 9.x）
     * 加载时该类可用。
     */
    private static final String COFH_RF_API_CLASS = "cofh.api.energy.IEnergyHandler";

    /**
     * 当前环境解析出的能量通道 TE 实现类（注册时确定）。
     * <p>
     * 方块放置时必须经 {@link #createEnergyPathway()} 实例化，保证实例类与注册映射一致：
     * 1.7.10 的 {@code TileEntity.writeToNBT} 会查 classToNameMap，实例类未注册时
     * 直接抛 {@code RuntimeException("... is missing a mapping! This is a bug!")}。
     * （源项目 1.20.1 中 BlockEntity 由注册的 BlockEntityType 统一创建，天然无此问题。）
     */
    public static Class<? extends TileEntity> ENERGY_PATHWAY_CLASS = NetEnergyPathwayBlockEntity.class;

    /**
     * 创建当前环境对应的能量通道 TE 实例（基础类 / RF 变体 / Mekanism 变体）。
     */
    public static TileEntity createEnergyPathway() {
        try {
            return ENERGY_PATHWAY_CLASS.newInstance();
        } catch (Throwable t) {
            // 兜底：变体实例化异常时退回基础类（至少保证方块可用）
            return new NetEnergyPathwayBlockEntity();
        }
    }

    public static void register() {
        GameRegistry.registerTileEntity(NetControlBlockEntity.class, BDConstants.MODID + ":" + BDBlockIds.NET_CONTROL);
        GameRegistry
            .registerTileEntity(NetInterfaceBlockEntity.class, BDConstants.MODID + ":" + BDBlockIds.NET_INTERFACE);
        GameRegistry.registerTileEntity(NetPathwayBlockEntity.class, BDConstants.MODID + ":" + BDBlockIds.NET_PATHWAY);
        Class<? extends TileEntity> energyPathwayClass = resolveEnergyPathwayClass();
        ENERGY_PATHWAY_CLASS = energyPathwayClass;
        GameRegistry.registerTileEntity(energyPathwayClass, BDConstants.MODID + ":" + BDBlockIds.NET_ENERGY_PATHWAY);
        GameRegistry
            .registerTileEntity(NetTerminalBlockEntity.class, BDConstants.MODID + ":" + BDBlockIds.NET_TERMINAL_BLOCK);
        GameRegistry.registerTileEntity(NetPumpBlockEntity.class, BDConstants.MODID + ":" + BDBlockIds.NET_PUMP_BLOCK);
        GameRegistry
            .registerTileEntity(NetHopperBlockEntity.class, BDConstants.MODID + ":" + BDBlockIds.NET_HOPPER_BLOCK);
        GameRegistry
            .registerTileEntity(NetFurnaceBlockEntity.class, BDConstants.MODID + ":" + BDBlockIds.NET_FURNACE_BLOCK);
        GameRegistry.registerTileEntity(
            NetBlastFurnaceBlockEntity.class,
            BDConstants.MODID + ":" + BDBlockIds.NET_BLAST_FURNACE_BLOCK);
        GameRegistry
            .registerTileEntity(NetSmokerBlockEntity.class, BDConstants.MODID + ":" + BDBlockIds.NET_SMOKER_BLOCK);
    }

    /**
     * 选择能量通道 TE 实现类，对齐源项目的"通用能量接收"语义：
     * <ul>
     * <li>GregTech 已加载：注册 GT 放电模式变体（GT 电包受电 + 逐机电压匹配放电）。
     * GT 变体 extends RF 变体（依赖 GT + CoFH API），GTNH 环境两者恒在；</li>
     * <li>Mekanism 已加载：注册 Mekanism 变体（Joule 推送 + 通用 RF）</li>
     * <li>仅 CoFH RF API 可用：注册 RF 变体（接受所有 RF 系模组推送）</li>
     * <li>均不可用：注册基础类（仅本模组内部能量交互）</li>
     * </ul>
     * 四种实现共用同一注册名与 NBT 格式，世界存档跨环境完全兼容
     * （NBT 只存注册名，加载时按当前环境注册的类还原）。
     */
    private static Class<? extends TileEntity> resolveEnergyPathwayClass() {
        // GT 优先：GTNH 目标环境 GT 必装。加载失败（如 GT 缺席但 class 残留）回退 Mek→RF→base 链。
        if (ModPresence.isLoaded(OtherModIds.GREGTECH)) {
            Class<? extends TileEntity> gtClass = tryLoadTileEntityClass(GT_ENERGY_PATHWAY_CLASS);
            if (gtClass != null) return gtClass;
        }
        if (ModPresence.isLoaded(OtherModIds.MEKANISM)) {
            Class<? extends TileEntity> mekClass = tryLoadTileEntityClass(MEK_ENERGY_PATHWAY_CLASS);
            if (mekClass != null) return mekClass;
            // Mekanism 变体加载失败时继续尝试 RF 变体
        }
        if (canLoadClass(COFH_RF_API_CLASS)) {
            Class<? extends TileEntity> rfClass = tryLoadTileEntityClass(RF_ENERGY_PATHWAY_CLASS);
            if (rfClass != null) return rfClass;
        }
        return NetEnergyPathwayBlockEntity.class;
    }

    private static boolean canLoadClass(String className) {
        try {
            // initialize=false：仅探测类可达性，不触发静态初始化
            Class.forName(className, false, BDBlockEntities.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static Class<? extends TileEntity> tryLoadTileEntityClass(String className) {
        try {
            return Class.forName(className)
                .asSubclass(TileEntity.class);
        } catch (Throwable t) {
            return null;
        }
    }
}
