package com.wintercogs.beyonddimensions.integration.module.botania;

import com.wintercogs.beyonddimensions.api.storage.key.StackKeyRegistry;
import com.wintercogs.beyonddimensions.integration.IIntegrationModule;
import com.wintercogs.beyonddimensions.integration.IntegrationHandlerRegistry;
import com.wintercogs.beyonddimensions.integration.OtherModIds;
import com.wintercogs.beyonddimensions.integration.module.botania.storage.ManaHandlerProvider;
import com.wintercogs.beyonddimensions.integration.module.botania.storage.ManaStackKey;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;

/**
 * Botania 联动模块入口（1.7.10 适配版）。
 * <p>
 * 源项目（1.20.1）使用 Forge Capability 系统桥接 Botania Mana，
 * 1.7.10 无 Capability 系统，改用 IntegrationHandlerRegistry 注册外部处理器提供者。
 * <p>
 * 本模块仅注册 ManaStackKey 和 ManaHandlerProvider，
 * ManaPoolPathway 方块/TileEntity 暂不移植（复杂度过高）。
 */
public class BotaniaModule implements IIntegrationModule {

    @Override
    public String modId() {
        return OtherModIds.BOTANIA;
    }

    @Override
    public void onBootstrap(FMLPostInitializationEvent event) {
        // 注册 ManaStackKey 到 StackKeyRegistry
        StackKeyRegistry.registerType(ManaStackKey.INSTANCE);
        // 注册外部处理器提供者，让 NetPump/NetInterface 能识别并抽取 Botania 的 IManaReceiver 方块
        IntegrationHandlerRegistry.registerProvider(new ManaHandlerProvider());
        // 注：ManaPoolPathway 方块/TileEntity 暂不移植（复杂度过高）
    }

    @Override
    public void onInit(FMLInitializationEvent event) {
        // Botania 联动模块无额外 init 逻辑
    }
}
