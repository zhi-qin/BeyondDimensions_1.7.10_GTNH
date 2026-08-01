package com.wintercogs.beyonddimensions.config;

public final class ServerConfigRuntime {

    private ServerConfigRuntime() {}

    // 单位 tick；与 Config.loadServerConfig 的 3600（秒）* 20 换算结果对齐（审计 M5-9）
    public static long fragmentTransferTime = 3600L * 20L;
    public static int crystalGenerateTime = 600;

    /** EU→RF 换算率（GTNH 设计：EU 是上游能量，RF 不可反向制造 EU，仅此方向）。 */
    public static int gtEuToRfRate = 4;

    /** GT 机器直接连接时是否按机器 getInputVoltage() 自动匹配电压（关闭则退回保守档位）。 */
    public static boolean gtMachineAutoVoltage = true;

    /** 维度能量通道放置时的默认主动抽取状态（默认不抽取；每方块可用 GUI 按钮覆盖并持久化）。 */
    public static boolean energyPathwayDefaultActivePull = false;
}
