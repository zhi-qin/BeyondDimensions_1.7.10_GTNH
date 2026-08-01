package com.wintercogs.beyonddimensions.common.init;

import com.wintercogs.beyonddimensions.api.ids.BDConstants;
import com.wintercogs.beyonddimensions.network.packet.both.QuickDataTagPacket;
import com.wintercogs.beyonddimensions.network.packet.both.SetSlotDirectlyPacket;
import com.wintercogs.beyonddimensions.network.packet.c2s.*;
import com.wintercogs.beyonddimensions.network.packet.s2c.DisorderedSlotGroupSyncPacket;
import com.wintercogs.beyonddimensions.network.packet.s2c.OrderedStackTypedSlotPacket;
import com.wintercogs.beyonddimensions.network.packet.s2c.PlayerPermissionInfoPacket;
import com.wintercogs.beyonddimensions.network.packet.s2c.SyncEuStoragePacket;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

public class BDPackets {

    public static final SimpleNetworkWrapper INSTANCE = NetworkRegistry.INSTANCE.newSimpleChannel(BDConstants.MODID);
    private static int packetId = 0;

    public static void register() {
        // C2S
        INSTANCE.registerMessage(OpenNetGuiPacket.Handler.class, OpenNetGuiPacket.class, packetId++, Side.SERVER);
        INSTANCE
            .registerMessage(CallSeverClickPacket.Handler.class, CallSeverClickPacket.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(
            NetControlActionPacket.Handler.class,
            NetControlActionPacket.class,
            packetId++,
            Side.SERVER);
        INSTANCE.registerMessage(RecipeFillC2SPacket.Handler.class, RecipeFillC2SPacket.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(AutoCraftC2SPacket.Handler.class, AutoCraftC2SPacket.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(
            ClickTransferCraftButtonPacket.Handler.class,
            ClickTransferCraftButtonPacket.class,
            packetId++,
            Side.SERVER);
        INSTANCE.registerMessage(BatchTransferPacket.Handler.class, BatchTransferPacket.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(
            PickBlockFromNetPacket.Handler.class,
            PickBlockFromNetPacket.class,
            packetId++,
            Side.SERVER);
        INSTANCE.registerMessage(
            PutHandItemToNetPacket.Handler.class,
            PutHandItemToNetPacket.class,
            packetId++,
            Side.SERVER);
        INSTANCE.registerMessage(ToggleMagnetPacket.Handler.class, ToggleMagnetPacket.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(OpenMagnetGuiPacket.Handler.class, OpenMagnetGuiPacket.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(
            OpenPrimaryNetSwitcherPacket.Handler.class,
            OpenPrimaryNetSwitcherPacket.class,
            packetId++,
            Side.SERVER);
        INSTANCE.registerMessage(
            PrimaryNetSwitchActionPacket.Handler.class,
            PrimaryNetSwitchActionPacket.class,
            packetId++,
            Side.SERVER);
        INSTANCE.registerMessage(RenameNetPacket.Handler.class, RenameNetPacket.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(FlagTranslatePacket.Handler.class, FlagTranslatePacket.class, packetId++, Side.SERVER);

        // S2C
        INSTANCE.registerMessage(
            PlayerPermissionInfoPacket.Handler.class,
            PlayerPermissionInfoPacket.class,
            packetId++,
            Side.CLIENT);
        INSTANCE.registerMessage(
            DisorderedSlotGroupSyncPacket.Handler.class,
            DisorderedSlotGroupSyncPacket.class,
            packetId++,
            Side.CLIENT);
        INSTANCE.registerMessage(
            OrderedStackTypedSlotPacket.Handler.class,
            OrderedStackTypedSlotPacket.class,
            packetId++,
            Side.CLIENT);
        INSTANCE.registerMessage(SyncEuStoragePacket.Handler.class, SyncEuStoragePacket.class, packetId++, Side.CLIENT);

        // 双向包：1.7.10 的 SimpleNetworkWrapper 无 registerBoth，需按 side 分别注册
        // （客户端接收 + 服务端接收各一次，共两个判别器）。Handler.onMessage 内已按 ctx.side
        // 分流。Trove 的 types 映射后写覆盖使发送统一用后注册的判别器，两端 discriminators
        // 同时保留两个判别器→同类，双向收发均正常——此双注册为 1.7.10 双向包的既定惯用法。
        INSTANCE.registerMessage(QuickDataTagPacket.Handler.class, QuickDataTagPacket.class, packetId++, Side.CLIENT);
        INSTANCE.registerMessage(QuickDataTagPacket.Handler.class, QuickDataTagPacket.class, packetId++, Side.SERVER);
        INSTANCE
            .registerMessage(SetSlotDirectlyPacket.Handler.class, SetSlotDirectlyPacket.class, packetId++, Side.CLIENT);
        INSTANCE
            .registerMessage(SetSlotDirectlyPacket.Handler.class, SetSlotDirectlyPacket.class, packetId++, Side.SERVER);
    }
}
