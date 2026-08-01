package mekanism.api;

/**
 * Mekanism MekanismConfig 接口存根（1.7.10 编译时占位，精简版）。
 * <p>
 * 此存根仅保留本模组能量桥接所需的换算字段
 * {@code general.FROM_TE / general.TO_TE}（对应 Mekanism.cfg 的
 * JoulesToRF / RFToJoules）。运行时若 Mekanism 已加载，真实的
 * {@code MekanismConfig} 类会覆盖此存根，字段由 Mekanism 自身在 preInit 填充。
 * <p>
 * 原始版权归 Mekanism 作者 aidancbrady 所有，此处仅保留所需字段。
 */
public class MekanismConfig {

    public static class general {

        /** 每 RF 对应的焦耳数（Mekanism.cfg JoulesToRF，默认 2.5） */
        public static double FROM_TE;

        /** 每焦耳对应的 RF 数（Mekanism.cfg RFToJoules，默认 0.4） */
        public static double TO_TE;
    }

    private MekanismConfig() {}
}
