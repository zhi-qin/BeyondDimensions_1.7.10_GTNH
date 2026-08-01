package com.wintercogs.beyonddimensions.util;

public final class BDMath {

    private BDMath() {}

    public static int clampLongToInt(long value) {
        if (value > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        if (value < Integer.MIN_VALUE) return Integer.MIN_VALUE;
        return (int) value;
    }
}
