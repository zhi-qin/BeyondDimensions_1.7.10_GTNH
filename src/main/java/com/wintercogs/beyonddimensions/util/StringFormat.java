package com.wintercogs.beyonddimensions.util;

import java.math.BigInteger;

/**
 * 字符串格式化工具，提供数字显示格式化等功能。
 * <p>
 * 1.7.10 移植版：完全对照 1.20.1 源项目实现，使用 k/M/G/T/P/E 单位缩写。
 * 移植新增 {@link #formatCount(BigInteger)} 重载，供维度网络 EU 池（10^40）显示使用。
 */
public final class StringFormat {

    private static final String[] UNITS = { "", "k", "M", "G", "T", "P", "E" };
    private static final long[] THRESHOLDS = { 1_000L, // k (10^3)
        1_000_000L, // M (10^6)
        1_000_000_000L, // G (10^9)
        1_000_000_000_000L, // T (10^12)
        1_000_000_000_000_000L, // P (10^15)
        1_000_000_000_000_000_000L // E (10^18)
    };

    /** 10^18，BigInteger 超过此值走科学计数。 */
    private static final BigInteger EXA = BigInteger.valueOf(1_000_000_000_000_000_000L);

    private StringFormat() {}

    /**
     * 将长整数格式化为带单位缩写的字符串（k/M/G/T/P/E）。
     */
    public static String formatCount(long count) {
        if (count < 1000) return String.valueOf(count);

        // 寻找最大单位
        int unitIndex = 0;
        while (unitIndex < THRESHOLDS.length - 1 && count >= THRESHOLDS[unitIndex + 1]) {
            unitIndex++;
        }

        // 计算值并格式化
        double value = count / (double) THRESHOLDS[unitIndex];
        return String.format("%d%s", (long) value, UNITS[unitIndex + 1]);
    }

    /**
     * 将大整数格式化为字符串（维度网络 EU 池专用，10^40 容量超出 long 范围）。
     * <p>
     * 小于 10^18 时转 long 走 {@link #formatCount(long)} 的单位缩写；等于或超过 10^18
     * 用科学计数（尾数 2 位小数 + e + 十进制指数），如 {@code 1.23e30}。
     * 需要完整十进制时调用 {@link BigInteger#toString()}。
     */
    public static String formatCount(BigInteger count) {
        if (count.compareTo(EXA) < 0) {
            return formatCount(count.longValue());
        }
        String s = count.toString();
        int exponent = s.length() - 1;
        String mantissa = s.charAt(0) + "." + s.substring(1, Math.min(3, s.length()));
        return mantissa + "e" + exponent;
    }

    /**
     * 与 {@link StringFormat#formatCount(long)} 一致，但是会将传入的 count 除以 1000。用于流体单位计算。
     */
    public static String formatBucket(long count) {
        if (count < 1000) return String.valueOf(count / 1000f);

        count = count / 1000;

        if (count < 1000) return String.valueOf(count);

        int unitIndex = 0;
        while (unitIndex < THRESHOLDS.length - 1 && count >= THRESHOLDS[unitIndex + 1]) {
            unitIndex++;
        }

        double value = count / (double) THRESHOLDS[unitIndex];
        return String.format("%d%s", (long) value, UNITS[unitIndex + 1]);
    }

    /**
     * 为能量增减等情况使用的格式化，自动添加 +/- 号。
     */
    public static String formatChange(long change) {
        if (change == 0) {
            return "0";
        }

        String sign = change > 0 ? "+" : "-";
        long absValue = Math.abs(change);

        // 特殊处理小于1000的值（直接显示原始值）
        if (absValue < 1000) {
            return sign + String.format("%d", absValue);
        }

        // 寻找匹配的单位
        int unitIndex = 0;
        while (unitIndex < THRESHOLDS.length - 1 && absValue >= THRESHOLDS[unitIndex + 1]) {
            unitIndex++;
        }

        // 计算带单位的值并格式化
        double value = absValue / (double) THRESHOLDS[unitIndex];
        return sign + String.format("%.2f%s", value, UNITS[unitIndex + 1]);
    }

    /**
     * 将长整数格式化为带千位分隔符的字符串（旧版兼容方法）。
     */
    public static String formatLong(long value) {
        return formatCount(value);
    }

    /**
     * 将长整数格式化为简短显示（K/M/G/T 后缀）。
     * <p>
     * 保留此方法以兼容旧调用；内部已统一使用 {@link #formatCount(long)} 的单位体系。
     */
    public static String formatLongCompact(long value) {
        return formatCount(value);
    }
}
