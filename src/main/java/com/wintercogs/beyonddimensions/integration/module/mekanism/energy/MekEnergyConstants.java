package com.wintercogs.beyonddimensions.integration.module.mekanism.energy;

import mekanism.api.MekanismConfig;

/**
 * 焦耳↔RF 能量换算常量（1.7.10 移植新增）。
 * <p>
 * Mekanism 9.x 使用 double 焦耳作为能量单位，本模组网络能量（{@code EnergyStackKey}）
 * 以 RF 语义的 long 计数（与自定义 {@code IEnergyHandler} 一致）。桥接两套接口时
 * 必须换算单位。换算系数直接读取 {@code MekanismConfig.general.FROM_TE/TO_TE}——
 * 与 Mekanism 自身 CoFH 兼容层（{@code CableUtils.emit_do_do}）使用同一换算源，
 * 保证网络计数与 Mekanism 侧结算一致：
 * <ul>
 * <li>{@code FROM_TE}（Mekanism.cfg JoulesToRF，默认 2.5）：1 RF = 2.5 J</li>
 * <li>{@code TO_TE}（Mekanism.cfg RFToJoules，默认 0.4）：1 J = 0.4 RF</li>
 * </ul>
 * 换算值非法（NaN / Infinity / 非正数）时回退默认值；不足 1 RF 的焦耳量按
 * {@code floor} 截断，保留在 Mekanism 侧，杜绝向网络虚报能量。
 */
public final class MekEnergyConstants {

    /** 默认每 RF 对应的焦耳数（Mekanism.cfg JoulesToRF 默认值） */
    private static final double DEFAULT_J_PER_RF = 2.5D;

    /** 默认每焦耳对应的 RF 数（Mekanism.cfg RFToJoules 默认值） */
    private static final double DEFAULT_RF_PER_J = 0.4D;

    private MekEnergyConstants() {}

    /**
     * 每 RF 对应的焦耳数。读取 {@code MekanismConfig.general.FROM_TE}，
     * 非法值回退默认 2.5。
     */
    public static double joulesPerRf() {
        double v = MekanismConfig.general.FROM_TE;
        return isValid(v) ? v : DEFAULT_J_PER_RF;
    }

    /**
     * 每焦耳对应的 RF 数。读取 {@code MekanismConfig.general.TO_TE}，
     * 非法值回退默认 0.4。
     */
    public static double rfPerJoule() {
        double v = MekanismConfig.general.TO_TE;
        return isValid(v) ? v : DEFAULT_RF_PER_J;
    }

    /**
     * 焦耳量折算为 RF 量（向下取整，不虚报）。
     *
     * @param joules Mekanism 侧焦耳数
     * @return 折算后的 RF 数；非正数或折算结果不足 1 RF 时返回 0
     */
    public static long joulesToRF(double joules) {
        if (!(joules > 0)) return 0; // 同时排除 NaN
        double rf = joules * rfPerJoule();
        if (!(rf > 0)) return 0;
        return (long) Math.floor(rf);
    }

    /**
     * RF 量折算为焦耳量。
     *
     * @param rf 网络侧 RF 数
     * @return 折算后的焦耳数；非正数时返回 0
     */
    public static double rfToJoules(long rf) {
        if (rf <= 0) return 0;
        return rf * joulesPerRf();
    }

    private static boolean isValid(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v) && v > 0;
    }
}
