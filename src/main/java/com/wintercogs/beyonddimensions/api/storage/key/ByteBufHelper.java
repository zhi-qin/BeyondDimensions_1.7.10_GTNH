package com.wintercogs.beyonddimensions.api.storage.key;

import net.minecraft.nbt.NBTTagCompound;

import com.wintercogs.beyonddimensions.api.util.BytebufHelper;

import io.netty.buffer.ByteBuf;

/**
 * 兼容层：将原本散落在 api.util 的 BytebufHelper 暴露到 api.storage.key 包下，
 * 供 storage key 实现类使用。
 */
public final class ByteBufHelper {

    private ByteBufHelper() {}

    public static void writeUtf8(ByteBuf buf, String s) {
        BytebufHelper.writeUtf8(buf, s);
    }

    public static String readUtf8(ByteBuf buf) {
        return BytebufHelper.readUtf8(buf);
    }

    public static void writeNbt(ByteBuf buf, NBTTagCompound tag) {
        BytebufHelper.writeNbt(buf, tag);
    }

    public static NBTTagCompound readNbt(ByteBuf buf) {
        return BytebufHelper.readNbt(buf);
    }
}
