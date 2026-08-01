package com.wintercogs.beyonddimensions;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.wintercogs.beyonddimensions.api.ids.BDConstants;
import com.wintercogs.beyonddimensions.api.storage.key.StackKeyRegistry;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EmptyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.init.BDBlockEntities;
import com.wintercogs.beyonddimensions.common.init.BDBlocks;
import com.wintercogs.beyonddimensions.common.init.BDCreativeModeTabs;
import com.wintercogs.beyonddimensions.common.init.BDFluids;
import com.wintercogs.beyonddimensions.common.init.BDItems;
import com.wintercogs.beyonddimensions.common.init.BDMenus;
import com.wintercogs.beyonddimensions.common.init.BDPackets;
import com.wintercogs.beyonddimensions.integration.IntegrationManager;
import com.wintercogs.beyonddimensions.util.BDMainThreadScheduler;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

@Mod(modid = BDConstants.MODID, name = "Beyond Dimensions", version = Tags.VERSION)
public class BeyondDimensions {

    public static final Logger LOGGER = LogManager.getLogger(BDConstants.MODID);

    @SidedProxy(
        clientSide = "com.wintercogs.beyonddimensions.ClientProxy",
        serverSide = "com.wintercogs.beyonddimensions.CommonProxy")
    public static CommonProxy proxy;

    @Mod.Instance(BDConstants.MODID)
    public static BeyondDimensions instance;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);

        // 注册 StackKey 类型
        StackKeyRegistry.registerType(EmptyStackKey.INSTANCE);
        StackKeyRegistry.registerType(ItemStackKey.EMPTY);
        StackKeyRegistry.registerType(FluidStackKey.EMPTY);
        StackKeyRegistry.registerType(EnergyStackKey.INSTANCE);

        // 注册创造模式标签
        BDCreativeModeTabs.register();
        // 注册物品
        BDItems.register();
        // 注册方块
        BDBlocks.register();
        // 注册流体
        BDFluids.register();
        // 注册TileEntity
        BDBlockEntities.register();

        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // 注册网络包主线程调度器（服务端 tick 排空队列，见 util.BDMainThreadScheduler）
        BDMainThreadScheduler.init();
        // 注册网络包
        BDPackets.register();
        // 注册菜单/GUI
        BDMenus.register();

        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        // 引导联动模块（NEI/AE2/Botania/Mekanism），在所有模组加载完成后执行
        IntegrationManager.bootstrap(event);
        // 客户端联动模块（如 NEI GUI 处理器）仅应在客户端侧引导：
        // 服务端引导会引用客户端类抛 NoClassDefFoundError（被 try-catch 吞掉但产生错误日志噪音）
        if (event.getSide()
            .isClient()) {
            IntegrationManager.bootstrapClient(event);
        }
        proxy.postInit(event);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
        LOGGER.info("维度网络初始化完成(服务端)");
    }

    public static ResourceLocation makeId(String path) {
        return new ResourceLocation(BDConstants.MODID, path);
    }
}
