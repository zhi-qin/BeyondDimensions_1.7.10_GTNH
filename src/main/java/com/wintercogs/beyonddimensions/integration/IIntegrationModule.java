package com.wintercogs.beyonddimensions.integration;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

/**
 * 联动模块接口（1.7.10 适配版）。
 * <p>
 * 源项目（1.20.1）使用 IEventBus + FMLCommonSetupEvent 等事件类型，
 * 1.7.10 改用 FML 生命周期事件（FMLPreInitializationEvent 等）。
 * <p>
 * 模块实例化由 IntegrationManager 在 postInit 阶段通过反射完成（OptionalClassLoader），
 * 确保目标模组未加载时不会触发 ClassNotFoundException。
 */
public interface IIntegrationModule {

    /**
     * 返回此模块依赖的目标模组 modId
     */
    String modId();

    /**
     * 模块引导：在 postInit 阶段调用，用于注册方块/物品/TE 等
     */
    void onBootstrap(FMLPostInitializationEvent event);

    /**
     * 模块初始化：接口兼容保留的钩子。模块实际在 postInit 才被实例化，
     * 此方法当前不会被 IntegrationManager 调用——模块初始化逻辑必须写在
     * {@link #onBootstrap} 内，勿将注册逻辑放入 onInit。
     */
    void onInit(FMLInitializationEvent event);

    /**
     * 模块预初始化：接口兼容保留的钩子，当前不会被 IntegrationManager 调用。
     */
    default void onPreInit(FMLPreInitializationEvent event) {}
}
