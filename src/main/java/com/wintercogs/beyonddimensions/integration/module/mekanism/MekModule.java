package com.wintercogs.beyonddimensions.integration.module.mekanism;

import com.wintercogs.beyonddimensions.api.storage.key.StackKeyRegistry;
import com.wintercogs.beyonddimensions.integration.IIntegrationModule;
import com.wintercogs.beyonddimensions.integration.IntegrationHandlerRegistry;
import com.wintercogs.beyonddimensions.integration.OtherModIds;
import com.wintercogs.beyonddimensions.integration.module.mekanism.energy.MekEnergyHandlerProvider;
import com.wintercogs.beyonddimensions.integration.module.mekanism.storage.GasHandlerProvider;
import com.wintercogs.beyonddimensions.integration.module.mekanism.storage.GasStackKey;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;

/**
 * Mekanism 联动模块（1.7.10 适配版）。
 * <p>
 * 源项目（1.20.1）通过 Forge Capability 系统桥接 Gas/Infusion/Pigment/Slurry 四种化学品。
 * 1.7.10 Mekanism 仅有 Gas（其余化学品类型不存在），且无 Capability 系统，
 * 改用 {@link IntegrationHandlerRegistry#registerProvider} 注册 IGasHandler 桥接提供者，
 * 让 NetPump/NetInterface 通过 instanceof 检查识别 Mekanism 气体方块。
 * <p>
 * 模块实例化由 IntegrationManager 在 postInit 阶段通过 OptionalClassLoader 反射完成，
 * 确保目标模组未加载时不会触发 ClassNotFoundException。
 */
public class MekModule implements IIntegrationModule {

    public MekModule() {}

    @Override
    public String modId() {
        return OtherModIds.MEKANISM;
    }

    @Override
    public void onBootstrap(FMLPostInitializationEvent event) {
        // 注册 Gas 堆叠类型（1.7.10 Mekanism 仅有 Gas，跳过 Infusion/Pigment/Slurry）
        StackKeyRegistry.registerType(GasStackKey.EMPTY);
        // 注册 IGasHandler 桥接提供者，让 NetPump/NetInterface 能识别并抽取 Mekanism 气体方块
        IntegrationHandlerRegistry.registerProvider(new GasHandlerProvider());
        // 注册 IStrictEnergyStorage/IStrictEnergyAcceptor 能量桥接提供者，
        // 让 NetPump/NetInterface 能从 Mekanism 储能方块（能量方块、创造能量方块等）抽取能量入网，
        // 并让能量通道 OPEN 模式能向 Mekanism 受能方块推送网络能量
        IntegrationHandlerRegistry.registerProvider(new MekEnergyHandlerProvider());
    }

    @Override
    public void onInit(FMLInitializationEvent event) {
        // 无需额外初始化
    }
}
