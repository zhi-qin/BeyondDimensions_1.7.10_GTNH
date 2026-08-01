package com.wintercogs.beyonddimensions.integration;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;

/**
 * 客户端联动模块接口（1.7.10 适配版）。
 * <p>
 * 1.7.10 用 @SideOnly(Side.CLIENT) 替代 1.20.1 的 IIntegrationClientModule 分离机制，
 * 但保留此接口用于客户端专属初始化逻辑（如渲染器注册）。
 */
public interface IIntegrationClientModule {

    /**
     * 返回此模块依赖的目标模组 modId
     */
    String modId();

    /**
     * 客户端引导：在 postInit 阶段调用
     */
    void onBootstrapClient(FMLPostInitializationEvent event);

    /**
     * 客户端初始化
     */
    default void onInitClient(FMLInitializationEvent event) {}
}
