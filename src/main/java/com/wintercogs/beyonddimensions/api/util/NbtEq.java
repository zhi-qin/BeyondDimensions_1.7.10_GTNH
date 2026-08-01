package com.wintercogs.beyonddimensions.api.util;

import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagList;

/**
 * 防止某些情况下把 NaN 塞进 NBT 中，导致哈希相等、NBT 打印完全一致，但是 equals 时不相等
 */
public final class NbtEq {

    private NbtEq() {}

    public static boolean equalsRelaxed(@Nullable NBTBase a, @Nullable NBTBase b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.getId() != b.getId()) return false;

        switch (a.getId()) {
            case 10: // TAG_COMPOUND
            {
                NBTTagCompound ca = (NBTTagCompound) a, cb = (NBTTagCompound) b;
                Set<String> ak = ca.func_150296_c();
                Set<String> bk = cb.func_150296_c();
                if (!ak.equals(bk)) return false;
                for (String k : ak) {
                    if (!equalsRelaxed(ca.getTag(k), cb.getTag(k))) return false;
                }
                return true;
            }
            case 9: // TAG_LIST
            {
                NBTTagList la = (NBTTagList) a, lb = (NBTTagList) b;
                if (la.tagCount() != lb.tagCount() || la.func_150303_d() != lb.func_150303_d()) return false;
                int listType = la.func_150303_d();
                if (listType == 10) {
                    // Compound 列表：getCompoundTagAt 可正确返回元素
                    for (int i = 0; i < la.tagCount(); i++) {
                        if (!equalsRelaxed(la.getCompoundTagAt(i), lb.getCompoundTagAt(i))) return false;
                    }
                    return true;
                } else {
                    // 非 Compound 列表：getCompoundTagAt 会返回空 Compound，不能使用。
                    // 回退到 NBTBase.equals，该方法对简单类型（含 NaN 的 double/float）行为正确。
                    return a.equals(b);
                }
            }
            case 6: // TAG_DOUBLE
            {
                double x = ((NBTTagDouble) a).func_150286_g();
                double y = ((NBTTagDouble) b).func_150286_g();
                return (x == y) || (Double.isNaN(x) && Double.isNaN(y));
            }
            case 5: // TAG_FLOAT
            {
                float x = ((NBTTagFloat) a).func_150288_h();
                float y = ((NBTTagFloat) b).func_150288_h();
                return (x == y) || (Float.isNaN(x) && Float.isNaN(y));
            }
            default:
                return a.equals(b);
        }
    }

    public static int hashRelaxed(@Nullable NBTBase t) {
        if (t == null) return 0;

        switch (t.getId()) {
            case 10: // TAG_COMPOUND
            {
                NBTTagCompound c = (NBTTagCompound) t;
                int sum = 0;
                for (String k : c.func_150296_c()) {
                    int kh = k.hashCode();
                    int vh = hashRelaxed(c.getTag(k));
                    int eh = kh ^ vh;
                    sum += eh;
                }
                return avalanche32(sum);
            }
            case 9: // TAG_LIST
            {
                NBTTagList l = (NBTTagList) t;
                int h = 1;
                h = 31 * h + l.func_150303_d();
                int listType = l.func_150303_d();
                if (listType == 10) {
                    // Compound 列表：getCompoundTagAt 可正确返回元素
                    for (int i = 0; i < l.tagCount(); i++) {
                        h = 31 * h + hashRelaxed(l.getCompoundTagAt(i));
                    }
                } else {
                    // 非 Compound 列表：回退到 hashCode（简单类型不受 NaN 影响）
                    h = 31 * h + t.hashCode();
                }
                return h;
            }
            case 6: // TAG_DOUBLE
            {
                double v = ((NBTTagDouble) t).func_150286_g();
                if (Double.isNaN(v)) v = Double.NaN;
                if (v == 0.0d) v = 0.0d;
                long bits = Double.doubleToRawLongBits(v);
                return (int) (bits ^ (bits >>> 32));
            }
            case 5: // TAG_FLOAT
            {
                float v = ((NBTTagFloat) t).func_150288_h();
                if (Float.isNaN(v)) v = Float.NaN;
                if (v == 0.0f) v = 0.0f;
                return Float.floatToRawIntBits(v);
            }
            default:
                return t.hashCode();
        }
    }

    private static int avalanche32(int x) {
        x ^= (x >>> 16);
        x *= 0x7feb352d;
        x ^= (x >>> 15);
        x *= 0x846ca68b;
        x ^= (x >>> 16);
        return x;
    }
}
