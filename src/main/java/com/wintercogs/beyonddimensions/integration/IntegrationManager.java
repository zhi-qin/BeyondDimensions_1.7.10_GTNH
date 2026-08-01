package com.wintercogs.beyonddimensions.integration;

import java.util.ArrayList;
import java.util.List;

import com.wintercogs.beyonddimensions.BeyondDimensions;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

/**
 * 联动模块管理器（1.7.10 适配版）。
 * <p>
 * 源项目（1.20.1）使用 ASM 注解扫描（ModuleRegistry）自动发现模块，
 * 1.7.10 不支持 ModFileScanData，改为手动注册（方案 A，见 INTEGRATION_PLAN.md 2.1）。
 * <p>
 * 生命周期（实际生效）：
 * - postInit: bootstrap / bootstrapClient —— 实例化模块 + onBootstrap / onBootstrapClient。
 * 模块必须等目标模组（NEI/AE2/Botania/Mekanism）初始化完成后再实例化，故统一在 postInit。
 * <p>
 * 注意：{@link #onPreInit} / {@link #onInit} 为接口兼容保留的钩子，但模块在 postInit
 * 才实例化，preInit/init 阶段模块列表为空，这两个钩子当前不会实际触发任何逻辑——
 * 模块的全部初始化必须写在 onBootstrap 内，勿将注册逻辑放入 onPreInit/onInit。
 * <p>
 * 模块仅在目标模组已加载时才会被实例化（通过 ModPresence.isLoaded 判断），
 * 实例化使用 OptionalClassLoader 反射加载，避免硬依赖导致 ClassNotFoundException。
 */
public final class IntegrationManager {

    private static final List<IIntegrationModule> ACTIVE_COMMON_MODULES = new ArrayList<>();
    private static final List<IIntegrationClientModule> ACTIVE_CLIENT_MODULES = new ArrayList<>();

    private static boolean bootstrapped = false;
    private static boolean bootstrappedClient = false;

    private IntegrationManager() {}

    /**
     * 注册所有联动模块（在 postInit 阶段调用）。
     * 手动注册方案：在此添加新的联动模块。
     */
    public static void bootstrap(FMLPostInitializationEvent event) {
        if (bootstrapped) {
            return;
        }
        bootstrapped = true;

        ACTIVE_COMMON_MODULES.clear();
        ACTIVE_CLIENT_MODULES.clear();

        // 手动注册联动模块（仅当目标模组已加载时才会实例化）
        // P1: NEI（JEI 在 1.7.10 的替代）
        registerModule(OtherModIds.NEI, "com.wintercogs.beyonddimensions.integration.module.nei.NeiModule");
        // P1: AE2（GTNH 版）
        registerModule(OtherModIds.AE2, "com.wintercogs.beyonddimensions.integration.module.ae2.AE2Module");
        // P2: Botania（v1.8 Mana 系统）
        registerModule(OtherModIds.BOTANIA, "com.wintercogs.beyonddimensions.integration.module.botania.BotaniaModule");
        // P2: Mekanism（仅 Gas，跳过 Infusion/Pigment/Slurry）
        registerModule(OtherModIds.MEKANISM, "com.wintercogs.beyonddimensions.integration.module.mekanism.MekModule");

        // 引导所有已激活模块
        for (IIntegrationModule module : ACTIVE_COMMON_MODULES) {
            try {
                module.onBootstrap(event);
            } catch (Throwable t) {
                BeyondDimensions.LOGGER.error("Error during bootstrap of integration module {}", module.modId(), t);
            }
        }

        // P0: CoFH RF API（CoFHCore/TE/EnderIO 等 RF 机器通用）——独立于联动模块直接注册，
        // 使能量通道 OPEN 模式可向纯 CoFH RF 机器推送能量（审计 M2-1）。
        // CoFH 缺席时 matches 恒不命中，无副作用；存根类由运行时真实 CoFH API 覆盖。
        try {
            IntegrationHandlerRegistry
                .registerProvider(new com.wintercogs.beyonddimensions.integration.rf.CoFHEnergyHandlerProvider());
        } catch (Throwable t) {
            BeyondDimensions.LOGGER.warn("CoFH RF 能量提供者注册失败（不影响运行）", t);
        }

        BeyondDimensions.LOGGER
            .info("BeyondDimensions integration: {} common modules activated", ACTIVE_COMMON_MODULES.size());
    }

    /**
     * 注册客户端联动模块（在 postInit 阶段调用，客户端侧）。
     */
    public static void bootstrapClient(FMLPostInitializationEvent event) {
        // 与 bootstrap 同款幂等守卫：防止重入导致客户端模块重复注册（如 GuiOverlayHandler 重复注册覆盖）
        if (bootstrappedClient) {
            return;
        }
        bootstrappedClient = true;

        // 与 bootstrap 对齐：先清空再注册，保证重复引导不残留（审计 M2-6）
        ACTIVE_CLIENT_MODULES.clear();

        // 客户端模块注册
        registerClientModule(OtherModIds.NEI, "com.wintercogs.beyonddimensions.integration.module.nei.NeiClientModule");

        for (IIntegrationClientModule module : ACTIVE_CLIENT_MODULES) {
            try {
                module.onBootstrapClient(event);
            } catch (Throwable t) {
                BeyondDimensions.LOGGER
                    .error("Error during client bootstrap of integration module {}", module.modId(), t);
            }
        }
    }

    public static void onPreInit(FMLPreInitializationEvent event) {
        for (IIntegrationModule module : ACTIVE_COMMON_MODULES) {
            try {
                module.onPreInit(event);
            } catch (Throwable t) {
                BeyondDimensions.LOGGER.error("Error during preInit of integration module {}", module.modId(), t);
            }
        }
    }

    public static void onInit(FMLInitializationEvent event) {
        for (IIntegrationModule module : ACTIVE_COMMON_MODULES) {
            try {
                module.onInit(event);
            } catch (Throwable t) {
                BeyondDimensions.LOGGER.error("Error during init of integration module {}", module.modId(), t);
            }
        }
    }

    /**
     * 注册一个联动模块（仅当目标模组已加载时才会实例化）
     */
    private static void registerModule(String modId, String implClassName) {
        if (!ModPresence.isLoaded(modId)) {
            return;
        }
        IIntegrationModule module = OptionalClassLoader.instantiate(implClassName, IIntegrationModule.class);
        if (module != null) {
            ACTIVE_COMMON_MODULES.add(module);
            BeyondDimensions.LOGGER.info("Loaded integration module: {}", modId);
        }
    }

    private static void registerClientModule(String modId, String implClassName) {
        if (!ModPresence.isLoaded(modId)) {
            return;
        }
        IIntegrationClientModule module = OptionalClassLoader
            .instantiate(implClassName, IIntegrationClientModule.class);
        if (module != null) {
            ACTIVE_CLIENT_MODULES.add(module);
        }
    }

    public static List<IIntegrationModule> getActiveModules() {
        return ACTIVE_COMMON_MODULES;
    }
}
