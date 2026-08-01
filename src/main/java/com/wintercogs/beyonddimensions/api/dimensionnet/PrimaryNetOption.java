package com.wintercogs.beyonddimensions.api.dimensionnet;

import javax.annotation.Nonnull;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IChatComponent;

public final class PrimaryNetOption {

    private static final String NET_ID = "net_id";
    private static final String PERMISSION = "permission";
    private static final String CUSTOM_NAME = "custom_name";

    private final int netId;
    @Nonnull
    private final NetPermissionLevel permission;
    @Nonnull
    private final String customName;

    public PrimaryNetOption(int netId, @Nonnull NetPermissionLevel permission, @Nonnull String customName) {
        this.netId = netId;
        this.permission = permission;
        this.customName = customName;
    }

    public int netId() {
        return netId;
    }

    @Nonnull
    public NetPermissionLevel permission() {
        return permission;
    }

    @Nonnull
    public String customName() {
        return customName;
    }

    public NBTTagCompound save() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(NET_ID, netId);
        tag.setString(PERMISSION, permission.name());
        if (!customName.isEmpty()) {
            tag.setString(CUSTOM_NAME, customName);
        }
        return tag;
    }

    public IChatComponent getNetworkName() {
        return DimensionsNet.getNetworkName(netId, customName);
    }

    public static PrimaryNetOption load(NBTTagCompound tag) {
        NetPermissionLevel permission = NetPermissionLevel.Member;
        if (tag.hasKey(PERMISSION)) {
            try {
                permission = NetPermissionLevel.valueOf(tag.getString(PERMISSION));
            } catch (IllegalArgumentException ignored) {
                // 存档损坏/跨版本枚举改名/被篡改时回退默认值，避免 valueOf 抛异常崩服
                permission = NetPermissionLevel.Member;
            }
        }
        return new PrimaryNetOption(
            tag.getInteger(NET_ID),
            permission,
            tag.hasKey(CUSTOM_NAME) ? tag.getString(CUSTOM_NAME) : "");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PrimaryNetOption)) return false;
        PrimaryNetOption that = (PrimaryNetOption) o;
        return netId == that.netId && permission == that.permission && customName.equals(that.customName);
    }

    @Override
    public int hashCode() {
        int result = netId;
        result = 31 * result + permission.hashCode();
        result = 31 * result + customName.hashCode();
        return result;
    }
}
