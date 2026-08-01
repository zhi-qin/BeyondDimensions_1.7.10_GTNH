package com.wintercogs.beyonddimensions;

import java.io.File;

import net.minecraft.server.MinecraftServer;

import com.wintercogs.beyonddimensions.api.dimensionnet.NetRegistryIndex;
import com.wintercogs.beyonddimensions.api.dimensionnet.PlayerNetIndex;
import com.wintercogs.beyonddimensions.common.command.CommandBDEu;
import com.wintercogs.beyonddimensions.common.command.CommandBDTools;
import com.wintercogs.beyonddimensions.common.init.BDRecipes;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        Config.init(event.getSuggestedConfigurationFile());
    }

    public void init(FMLInitializationEvent event) {
        // 注册合成配方
        BDRecipes.registerRecipes();
    }

    public void postInit(FMLPostInitializationEvent event) {
        // 集成模块初始化
    }

    public void serverStarting(FMLServerStartingEvent event) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server != null) {
            // 加载服务器/世界级配置（对应源项目 ModConfig.Type.SERVER 的 onLoaded）：
            // 无此调用时 initWorldConfig 永不执行，crystalGenerateTime 等 SERVER 配置
            // 只能停留在代码默认值、玩家无法通过配置文件调整。
            Config.initWorldConfig(new File(server.getFile("config"), "beyonddimensions_world.cfg"));
            // 服务器启动时初始化/重建维度网络索引
            NetRegistryIndex.get(server)
                .ensureInitialized(server);
            PlayerNetIndex.get(server)
                .rebuildFromServer(server);
        }
        // 注册服务端命令
        CommandBDTools.register(event);
        // 临时 OP 调试命令（Part 1 Phase A 验证用，接入真实交互后移除）
        CommandBDEu.register(event);
    }
}
