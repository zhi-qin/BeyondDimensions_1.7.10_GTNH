package com.wintercogs.beyonddimensions;

import net.minecraft.item.Item;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;

import com.wintercogs.beyonddimensions.client.event.listener.ShortKeysListener;
import com.wintercogs.beyonddimensions.client.init.BDShortKeys;
import com.wintercogs.beyonddimensions.client.renderer.BDItemRenderer;
import com.wintercogs.beyonddimensions.client.renderer.NetHopperTESR;
import com.wintercogs.beyonddimensions.client.renderer.NetPumpTESR;
import com.wintercogs.beyonddimensions.client.renderer.NetTerminalTESR;
import com.wintercogs.beyonddimensions.common.block.entity.NetHopperBlockEntity;
import com.wintercogs.beyonddimensions.common.block.entity.NetPumpBlockEntity;
import com.wintercogs.beyonddimensions.common.block.entity.NetTerminalBlockEntity;
import com.wintercogs.beyonddimensions.common.init.BDBlocks;
import com.wintercogs.beyonddimensions.common.init.BDFluids;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        // 客户端预初始化：键位、渲染注册等
        BDShortKeys.registerKeys();
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        MinecraftForge.EVENT_BUS.register(this);
        // 1.7.10 的 InputEvent.KeyInputEvent 在 FML bus 上触发，而非 MinecraftForge.EVENT_BUS
        FMLCommonHandler.instance()
            .bus()
            .register(new ShortKeysListener());

        // 注册 3D 方块 TESR
        ClientRegistry.bindTileEntitySpecialRenderer(NetPumpBlockEntity.class, new NetPumpTESR());
        ClientRegistry.bindTileEntitySpecialRenderer(NetHopperBlockEntity.class, new NetHopperTESR());
        ClientRegistry.bindTileEntitySpecialRenderer(NetTerminalBlockEntity.class, new NetTerminalTESR());

        // 注册 3D 方块物品渲染器
        MinecraftForgeClient.registerItemRenderer(
            Item.getItemFromBlock(BDBlocks.NET_PUMP_BLOCK),
            new BDItemRenderer(BDItemRenderer.ModelType.PUMP));
        MinecraftForgeClient.registerItemRenderer(
            Item.getItemFromBlock(BDBlocks.NET_HOPPER_BLOCK),
            new BDItemRenderer(BDItemRenderer.ModelType.HOPPER));
        MinecraftForgeClient.registerItemRenderer(
            Item.getItemFromBlock(BDBlocks.NET_TERMINAL_BLOCK),
            new BDItemRenderer(BDItemRenderer.ModelType.TERMINAL));
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
        // 客户端后初始化
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onTextureStitch(TextureStitchEvent.Pre event) {
        if (event.map.getTextureType() == 0) {
            BDFluids.XP_FLUID.setIcons(
                event.map.registerIcon("beyonddimensions:xp_fluid_still"),
                event.map.registerIcon("beyonddimensions:xp_fluid_flow"));
        }
    }
}
