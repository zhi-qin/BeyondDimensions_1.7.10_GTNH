package com.wintercogs.beyonddimensions.integration.module.nei;

import com.wintercogs.beyonddimensions.integration.IIntegrationModule;
import com.wintercogs.beyonddimensions.integration.OtherModIds;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;

/**
 * NEI 联动模块入口（1.7.10 适配版）。
 * <p>
 * 源项目（1.20.1）使用 JEI 提供配方查看、幽灵物品拖拽与配方转移功能。
 * 1.7.10 环境下使用 NEI（NotEnoughItems）作为替代，NEI 通过
 * {@link codechicken.nei.api.API#registerNEIGuiHandler(INEIGuiHandler)}
 * 注册 GUI 处理器实现等价功能。
 * <p>
 * 公共侧无额外初始化逻辑，所有 NEI 集成均在客户端侧 {@link NeiClientModule} 中完成。
 */
public class NeiModule implements IIntegrationModule {

    @Override
    public String modId() {
        return OtherModIds.NEI;
    }

    @Override
    public void onBootstrap(FMLPostInitializationEvent event) {
        // NEI 集成仅涉及客户端 GUI 处理，公共侧无需注册
    }

    @Override
    public void onInit(FMLInitializationEvent event) {
        // 无需额外初始化
    }
}
