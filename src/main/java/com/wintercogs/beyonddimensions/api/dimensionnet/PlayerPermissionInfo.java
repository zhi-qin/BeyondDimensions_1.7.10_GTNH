package com.wintercogs.beyonddimensions.api.dimensionnet;

import com.wintercogs.beyonddimensions.api.storage.key.ByteBufHelper;

import io.netty.buffer.ByteBuf;

public final class PlayerPermissionInfo {

    private final String name;
    private final NetPermissionLevel level;

    public PlayerPermissionInfo(String name, NetPermissionLevel level) {
        this.name = name;
        this.level = level;
    }

    public String name() {
        return name;
    }

    public NetPermissionLevel level() {
        return level;
    }

    public static void encode(PlayerPermissionInfo info, ByteBuf buf) {
        ByteBufHelper.writeUtf8(buf, info.name);
        buf.writeInt(info.level.ordinal());
    }

    public static PlayerPermissionInfo decode(ByteBuf buf) {
        String name = ByteBufHelper.readUtf8(buf);
        int ordinal = buf.readInt();
        // Math.floorMod 保证负序号取模结果非负，避免 values()[-1] 抛 AIOOBE（恶意/异常数据断连）
        NetPermissionLevel[] levels = NetPermissionLevel.values();
        NetPermissionLevel level = levels[Math.floorMod(ordinal, levels.length)];
        return new PlayerPermissionInfo(name, level);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlayerPermissionInfo)) return false;
        PlayerPermissionInfo that = (PlayerPermissionInfo) o;
        return name.equals(that.name) && level == that.level;
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + level.hashCode();
        return result;
    }
}
