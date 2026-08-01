package com.wintercogs.beyonddimensions.network.packet.s2c;

import java.util.HashMap;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

import com.wintercogs.beyonddimensions.api.dimensionnet.PlayerPermissionInfo;
import com.wintercogs.beyonddimensions.common.menu.NetControlMenu;
import com.wintercogs.beyonddimensions.util.BDMainThreadScheduler;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * 玩家权限信息同步的网络包（1.7.10 移植版）。
 * 1.7.10 适配：record → 普通类；FriendlyByteBuf → ByteBuf；
 * writeUUID/readUUID → 自行序列化；NetworkEvent.Context → MessageContext；
 * player.containerMenu → player.openContainer。
 */
public class PlayerPermissionInfoPacket implements IMessage {

    private HashMap<UUID, PlayerPermissionInfo> infoMap;

    public PlayerPermissionInfoPacket() {
        this.infoMap = new HashMap<>();
    }

    public PlayerPermissionInfoPacket(HashMap<UUID, PlayerPermissionInfo> infoMap) {
        this.infoMap = infoMap;
    }

    public HashMap<UUID, PlayerPermissionInfo> getInfoMap() {
        return infoMap;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int entryCount = buf.readInt();
        // 读取前校验上限（网络成员数有自然上限，1024 远超合理值），防 new HashMap<>(2^30) OOM
        if (entryCount < 0 || entryCount > 1024) {
            this.infoMap = new HashMap<>();
            return;
        }
        this.infoMap = new HashMap<>(entryCount);
        for (int i = 0; i < entryCount; i++) {
            UUID uuid = new UUID(buf.readLong(), buf.readLong());
            PlayerPermissionInfo info = PlayerPermissionInfo.decode(buf);
            this.infoMap.put(uuid, info);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.infoMap.size());
        for (java.util.Map.Entry<UUID, PlayerPermissionInfo> entry : this.infoMap.entrySet()) {
            UUID uuid = entry.getKey();
            buf.writeLong(uuid.getMostSignificantBits());
            buf.writeLong(uuid.getLeastSignificantBits());
            PlayerPermissionInfo.encode(entry.getValue(), buf);
        }
    }

    public static class Handler implements IMessageHandler<PlayerPermissionInfoPacket, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PlayerPermissionInfoPacket message, MessageContext ctx) {
            // 包 handler 在 Netty IO 线程执行，loadPlayerInfo 替换菜单权限表，
            // 切到客户端主线程保持与渲染一致
            BDMainThreadScheduler.scheduleClient(() -> handle(message));
            return null;
        }

        @SideOnly(Side.CLIENT)
        private static void handle(PlayerPermissionInfoPacket message) {
            EntityPlayer player = Minecraft.getMinecraft().thePlayer;
            if (player == null) return;
            Container menu = player.openContainer;
            if (!(menu instanceof NetControlMenu)) return;
            NetControlMenu netControlMenu = (NetControlMenu) menu;
            netControlMenu.loadPlayerInfo(message.getInfoMap());
        }
    }
}
