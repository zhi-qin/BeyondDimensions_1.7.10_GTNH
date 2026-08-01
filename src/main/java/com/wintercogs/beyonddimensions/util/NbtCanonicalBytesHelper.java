package com.wintercogs.beyonddimensions.util;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagEnd;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.nbt.NBTTagString;

/**
 * 将 (tag,caps) 规范化为"稳定字节"的工具（1.7.10 适配版）。
 */
public final class NbtCanonicalBytesHelper {

    private static final byte[] EMPTY_BYTES = buildEmptyBytes();
    private static final byte[] UNAVAILABLE_BYTES = new byte[0];

    /**
     * 1.7.10 的 NBTTagList 没有公开的泛型 get(int) 方法，getCompoundTagAt 仅对 Compound 列表正确。
     * 通过反射访问内部 tagList 字段以获取泛型 NBTBase 元素，用于非 Compound 列表的规范化。
     */
    private static final Field TAG_LIST_FIELD;

    static {
        Field f = null;
        try {
            // MCP 名（开发环境 runClient 为反混淆名）
            f = NBTTagList.class.getDeclaredField("tagList");
        } catch (Throwable t) {
            try {
                // SRG 混淆名（正式发布运行时字段被混淆为 field_74747_a）
                f = NBTTagList.class.getDeclaredField("field_74747_a");
            } catch (Throwable t2) {
                f = null;
            }
        }
        if (f != null) {
            f.setAccessible(true);
        }
        TAG_LIST_FIELD = f;
    }

    /**
     * 一次性取出 NBTTagList 内部的泛型元素列表（反射，每次规范化/序列化仅取一次）。
     * 返回 null 表示反射不可用或字段缺失，调用方回退到 getCompoundTagAt。
     * <p>
     * 原实现逐元素调用 {@code TAG_LIST_FIELD.get(list)}，N 个元素即 N 次反射；
     * 改为取一次后循环内用下标访问，消除逐元素反射开销（1.7.10 特有，1.20.1 的 ListTag 有原生 get）。
     */
    private static List<NBTBase> getRawListElements(NBTTagList list) {
        if (TAG_LIST_FIELD == null) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            List<NBTBase> raw = (List<NBTBase>) TAG_LIST_FIELD.get(list);
            return raw;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private NbtCanonicalBytesHelper() {}

    private static byte[] buildEmptyBytes() {
        try {
            NBTTagCompound empty = new NBTTagCompound();
            NBTTagCompound canon = canonicalizeCompound(empty);
            return encodeDeterministic(canon);
        } catch (Throwable t) {
            return new byte[] { 0x0A, 0x00, 0x00, 0x00, 0x00 };
        }
    }

    public static byte[] toCanonicalBytes(@Nullable NBTTagCompound tag, @Nullable NBTTagCompound caps) {
        boolean tagEmpty = (tag == null || tag.hasNoTags());
        boolean capsEmpty = (caps == null || caps.hasNoTags());
        if (tagEmpty && capsEmpty) {
            return EMPTY_BYTES;
        }

        try {
            NBTTagCompound root = new NBTTagCompound();
            if (!tagEmpty) root.setTag("tag", tag);
            if (!capsEmpty) root.setTag("caps", caps);

            NBTBase canon = canonicalize(root);
            return encodeDeterministic(canon);
        } catch (Throwable t) {
            return UNAVAILABLE_BYTES;
        }
    }

    private static NBTBase canonicalize(NBTBase in) {
        if (in == null) {
            return new NBTTagEnd();
        }

        if (in instanceof NBTTagCompound) {
            return canonicalizeCompound((NBTTagCompound) in);
        }

        if (in instanceof NBTTagList) {
            NBTTagList lt = (NBTTagList) in;
            NBTTagList out = new NBTTagList();
            List<NBTBase> raw = getRawListElements(lt);
            for (int i = 0; i < lt.tagCount(); i++) {
                NBTBase element = (raw != null && i < raw.size()) ? raw.get(i) : lt.getCompoundTagAt(i);
                out.appendTag(canonicalize(element));
            }
            return out;
        }

        return in;
    }

    private static NBTTagCompound canonicalizeCompound(NBTTagCompound ct) {
        List<String> keys = new ArrayList<>(ct.func_150296_c());
        Collections.sort(keys);
        NBTTagCompound out = new NBTTagCompound();
        for (String k : keys) {
            NBTBase v = ct.getTag(k);
            out.setTag(k, canonicalize(v));
        }
        return out;
    }

    private static byte[] encodeDeterministic(NBTBase root) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(256);
        DataOutputStream out = new DataOutputStream(baos);
        writeTag(out, root);
        out.flush();
        return baos.toByteArray();
    }

    private static void writeTag(DataOutputStream out, NBTBase tag) throws Exception {
        if (tag == null) {
            out.writeByte(0);
            return;
        }

        byte type = tag.getId();
        out.writeByte(type);

        switch (type) {
            case 0:
                break;
            case 1:
                out.writeByte(((NBTTagByte) tag).func_150290_f());
                break;
            case 2:
                out.writeShort(((NBTTagShort) tag).func_150289_e());
                break;
            case 3:
                out.writeInt(((NBTTagInt) tag).func_150287_d());
                break;
            case 4:
                out.writeLong(((NBTTagLong) tag).func_150291_c());
                break;
            case 5:
                out.writeInt(canonicalFloatBits(((NBTTagFloat) tag).func_150288_h()));
                break;
            case 6:
                out.writeLong(canonicalDoubleBits(((NBTTagDouble) tag).func_150286_g()));
                break;
            case 7: {
                byte[] arr = ((NBTTagByteArray) tag).func_150292_c();
                out.writeInt(arr.length);
                out.write(arr);
                break;
            }
            case 8:
                writeUtf8(out, ((NBTTagString) tag).func_150285_a_());
                break;
            case 9:
                writeList(out, (NBTTagList) tag);
                break;
            case 10:
                writeCompound(out, (NBTTagCompound) tag);
                break;
            case 11: {
                int[] arr = ((NBTTagIntArray) tag).func_150302_c();
                out.writeInt(arr.length);
                for (int v : arr) out.writeInt(v);
                break;
            }
            default:
                throw new IllegalStateException("Unknown NBT tag id: " + type);
        }
    }

    private static final int CANONICAL_FLOAT_NAN_BITS = 0x7fc00000;
    private static final long CANONICAL_DOUBLE_NAN_BITS = 0x7ff8000000000000L;

    private static int canonicalFloatBits(float v) {
        if (v == 0.0f) return 0;
        if (Float.isNaN(v)) return CANONICAL_FLOAT_NAN_BITS;
        return Float.floatToRawIntBits(v);
    }

    private static long canonicalDoubleBits(double v) {
        if (v == 0.0d) return 0L;
        if (Double.isNaN(v)) return CANONICAL_DOUBLE_NAN_BITS;
        return Double.doubleToRawLongBits(v);
    }

    private static void writeCompound(DataOutputStream out, NBTTagCompound ct) throws Exception {
        List<String> keys = new ArrayList<>(ct.func_150296_c());
        Collections.sort(keys);

        out.writeInt(keys.size());
        for (String k : keys) {
            writeUtf8(out, k);
            writeTag(out, ct.getTag(k));
        }
    }

    private static void writeList(DataOutputStream out, NBTTagList lt) throws Exception {
        out.writeInt(lt.tagCount());
        List<NBTBase> raw = getRawListElements(lt);
        for (int i = 0; i < lt.tagCount(); i++) {
            NBTBase element = (raw != null && i < raw.size()) ? raw.get(i) : lt.getCompoundTagAt(i);
            writeTag(out, element);
        }
    }

    private static void writeUtf8(DataOutputStream out, String s) throws Exception {
        if (s == null) s = "";
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    public static byte[] emptyBytes() {
        return EMPTY_BYTES;
    }

    public static byte[] unavailableBytes() {
        return UNAVAILABLE_BYTES;
    }
}
