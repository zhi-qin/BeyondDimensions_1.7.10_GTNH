package com.wintercogs.beyonddimensions.integration.module.ae2;

import com.wintercogs.beyonddimensions.integration.IIntegrationModule;
import com.wintercogs.beyonddimensions.integration.OtherModIds;
import com.wintercogs.beyonddimensions.integration.module.ae2.me.CellHandler;

import appeng.api.AEApi;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;

/**
 * AE2 联动模块入口（1.7.10 适配版）。
 * <p>
 * 对应源项目（1.20.1）中的 AE2Module，在启动时：
 * 1. 注册 {@link CellHandler} 到 AE2 的存储元件注册表
 * 2. 初始化 {@link AEHelper} 的类型转换映射（静态初始化器已自动完成）
 * <p>
 * 物品 {@link com.wintercogs.beyonddimensions.integration.module.ae2.item.NetAEStorageCell}
 * 已在 {@link com.wintercogs.beyonddimensions.common.init.BDItems#register()} 中条件注册。
 * <p>
 * 模块实例化由 {@link com.wintercogs.beyonddimensions.integration.IntegrationManager} 在 postInit 阶段
 * 通过反射完成，确保 AE2 未加载时不会触发 ClassNotFoundException。
 */
public class AE2Module implements IIntegrationModule {

    public AE2Module() {}

    @Override
    public String modId() {
        return OtherModIds.AE2;
    }

    @Override
    public void onBootstrap(FMLPostInitializationEvent event) {
        // 注册 AE2 存储元件处理器
        // AEHelper 的类型转换映射在静态初始化器中自动构建，无需显式调用
        AEApi.instance()
            .registries()
            .cell()
            .addCellHandler(CellHandler.INSTANCE);
    }

    @Override
    public void onInit(FMLInitializationEvent event) {
        // 无需额外初始化
    }
}
