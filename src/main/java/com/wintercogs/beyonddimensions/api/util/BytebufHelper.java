package com.wintercogs.beyonddimensions.api.util;

import java.nio.charset.StandardCharsets;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.wintercogs.beyonddimensions.api.storage.key.impl.EmptyStackKey;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

/**
 * 1.7.10 ByteBuf 辅助方法，包含 ItemStack、FluidStack 等序列化。
 */
public final class BytebufHelper {

    private BytebufHelper() {}

    // ==================== ResourceLocation ====================

    public static void writeResourceLocation(ByteBuf buf, ResourceLocation loc) {
        writeUtf8(buf, loc.toString());
    }

    public static ResourceLocation readResourceLocation(ByteBuf buf) {
        String s = readUtf8(buf);
        // 回退到已注册的 EmptyStackKey 类型 id（minecraft:air 未注册，getType 会抛异常断线）
        if (s == null || s.isEmpty()) return EmptyStackKey.INSTANCE.getTypeId();
        try {
            return new ResourceLocation(s);
        } catch (Exception e) {
            return EmptyStackKey.INSTANCE.getTypeId();
        }
    }

    // ==================== UTF-8 String ====================

    public static void writeUtf8(ByteBuf buf, String s) {
        if (s == null) s = "";
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    public static String readUtf8(ByteBuf buf) {
        int len = buf.readInt();
        // 长度不可能超过剩余可读字节；恶意客户端可发任意 int 触发 new byte[len] OOM（拒绝服务），
        // 读取前校验（合法包长度必然 <= readableBytes）
        if (len <= 0 || len > buf.readableBytes()) return "";
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    // ==================== VarLong ====================

    public static void writeVarLong(ByteBuf buf, long value) {
        buf.writeLong(value);
    }

    public static long readVarLong(ByteBuf buf) {
        return buf.readLong();
    }

    // ==================== NBT ====================

    public static void writeNbt(ByteBuf buf, NBTTagCompound tag) {
        ByteBufUtils.writeTag(buf, tag);
    }

    public static NBTTagCompound readNbt(ByteBuf buf) {
        return ByteBufUtils.readTag(buf);
    }

    // ==================== ItemStack ====================

    public static void writeItemStack(ByteBuf buf, ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            buf.writeBoolean(false);
            return;
        }
        buf.writeBoolean(true);
        NBTTagCompound tag = new NBTTagCompound();
        stack.writeToNBT(tag);
        writeNbt(buf, tag);
    }

    public static ItemStack readItemStack(ByteBuf buf) {
        if (!buf.readBoolean()) {
            return null;
        }
        NBTTagCompound tag = readNbt(buf);
        if (tag == null) {
            return null;
        }
        return ItemStack.loadItemStackFromNBT(tag);
    }

    // ==================== FluidStack ====================

    public static void writeFluidStack(ByteBuf buf, FluidStack stack) {
        if (stack == null || stack.getFluid() == null || stack.amount <= 0) {
            buf.writeBoolean(false);
            return;
        }
        buf.writeBoolean(true);
        NBTTagCompound tag = new NBTTagCompound();
        stack.writeToNBT(tag);
        writeNbt(buf, tag);
    }

    public static FluidStack readFluidStack(ByteBuf buf) {
        if (!buf.readBoolean()) {
            return null;
        }
        NBTTagCompound tag = readNbt(buf);
        if (tag == null) {
            return null;
        }
        return FluidStack.loadFluidStackFromNBT(tag);
    }

    // ==================== Item ID ====================

    public static void writeItemId(ByteBuf buf, Item item) {
        if (item == null) {
            writeUtf8(buf, "minecraft:air");
        } else {
            String id = Item.itemRegistry.getNameForObject(item);
            writeUtf8(buf, id != null ? id : "minecraft:air");
        }
    }

    public static Item readItemById(ByteBuf buf) {
        String id = readUtf8(buf);
        if (id == null || id.isEmpty() || "minecraft:air".equals(id)) return null;
        return (Item) Item.itemRegistry.getObject(id);
    }

    // ==================== Fluid ID ====================

    public static void writeFluidId(ByteBuf buf, Fluid fluid) {
        if (fluid == null) {
            writeUtf8(buf, "");
        } else {
            String name = FluidRegistry.getFluidName(fluid);
            writeUtf8(buf, name != null ? name : "");
        }
    }

    public static Fluid readFluidById(ByteBuf buf) {
        String name = readUtf8(buf);
        if (name == null || name.isEmpty()) return null;
        return FluidRegistry.getFluid(name);
    }
}
