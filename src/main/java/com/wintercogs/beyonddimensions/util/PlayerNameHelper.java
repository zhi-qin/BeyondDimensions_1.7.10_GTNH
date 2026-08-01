package com.wintercogs.beyonddimensions.util;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import com.mojang.authlib.GameProfile;

/**
 * 根据 UUID 获取玩家名称的工具（1.7.10 适配版）。
 */
public final class PlayerNameHelper {

    private PlayerNameHelper() {}

    /**
     * 通过 UUID 获取玩家名称。
     * 优先从在线玩家查找，其次从档案缓存查找。
     *
     * @param server Minecraft 服务器实例
     * @param uuid   玩家 UUID
     * @return 玩家名称，如果无法获取则返回 "Unknown"
     */
    public static String getPlayerName(@Nullable MinecraftServer server, UUID uuid) {
        if (server == null || uuid == null) {
            return "Unknown";
        }

        // 1. 尝试从在线玩家查找
        EntityPlayerMP player = getPlayerByUUID(server, uuid);
        if (player != null) {
            return player.getCommandSenderName();
        }

        // 2. 尝试从玩家档案缓存查找
        GameProfile profile = getProfileByUUID(server, uuid);
        if (profile != null && profile.getName() != null) {
            return profile.getName();
        }

        return "Unknown";
    }

    /**
     * 通过 UUID 获取在线玩家。
     * 1.7.10 的 ServerConfigurationManager 没有按 UUID 查找玩家的方法，
     * func_152612_a(String) 实为 getPlayerByUsername，传入 UUID 字符串永远无法匹配。
     * 因此遍历在线玩家列表进行 UUID 匹配。
     */
    @Nullable
    public static EntityPlayerMP getPlayerByUUID(MinecraftServer server, UUID uuid) {
        if (server == null || server.getConfigurationManager() == null || uuid == null) {
            return null;
        }
        try {
            for (Object o : server.getConfigurationManager().playerEntityList) {
                if (o instanceof EntityPlayerMP) {
                    EntityPlayerMP player = (EntityPlayerMP) o;
                    if (uuid.equals(player.getUniqueID())) {
                        return player;
                    }
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    /**
     * 通过 UUID 获取 GameProfile。
     */
    @Nullable
    public static GameProfile getProfileByUUID(MinecraftServer server, UUID uuid) {
        if (server == null || uuid == null) {
            return null;
        }
        try {
            // 1.7.10 使用 func_152358_ax() 获取玩家档案缓存
            return server.func_152358_ax()
                .func_152652_a(uuid);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 通过玩家名称获取 UUID。
     */
    @Nullable
    public static UUID getUUIDByName(MinecraftServer server, String name) {
        if (server == null || name == null || name.isEmpty()) {
            return null;
        }

        // 1. 尝试从在线玩家查找
        EntityPlayerMP player = server.getConfigurationManager()
            .func_152612_a(name);
        if (player != null) {
            return player.getUniqueID();
        }

        // 2. 尝试从档案缓存查找
        try {
            GameProfile profile = server.func_152358_ax()
                .func_152655_a(name);
            if (profile != null) {
                return profile.getId();
            }
        } catch (Exception e) {
            // ignore
        }

        return null;
    }
}
