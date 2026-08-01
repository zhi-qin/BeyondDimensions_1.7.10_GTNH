package com.wintercogs.beyonddimensions.integration;

/**
 * 其他模组的 modId 常量。
 * <p>
 * 1.7.10 适配版：保留源项目常量，并补充 1.7.10 替代模组的 modId。
 * 标注 "// 1.7.10 不可用" 的 modId 在 1.7.10 环境中不存在，仅保留常量供引用。
 */
public final class OtherModIds {

    // 1.7.10 实际 modId（FML Loader.isModLoaded 区分大小写，须与 @Mod 注解一致）
    public static final String MEKANISM = "Mekanism";
    public static final String AE2 = "appliedenergistics2";
    public static final String NEI = "NotEnoughItems";
    public static final String JEI = "jei"; // 1.7.10 不存在，用 NEI 替代
    public static final String EMI = "emi"; // 1.7.10 不可用
    public static final String POLYMORPH = "polymorph"; // 1.7.10 不可用
    public static final String APPMEK = "appmek"; // 1.7.10 不可用
    public static final String APPFLUX = "appflux"; // 1.7.10 不可用
    public static final String CURIOS = "curios"; // 1.7.10 用 Baubles 替代
    public static final String BAUBLES = "Baubles"; // 1.7.10 替代 Curios
    public static final String JE_CHARACTERS = "jecharacters";
    public static final String REFINED_STORAGE = "refinedstorage";
    public static final String ARS_NOUVEAU = "ars_nouveau"; // 1.7.10 不可用
    public static final String ARS_ENG = "arseng"; // 1.7.10 不可用
    public static final String BOTANIA = "Botania";
    public static final String APPBOT = "appbot"; // 1.7.10 不可用
    public static final String CREATE = "create"; // 1.7.10 不可用
    public static final String WAILA = "Waila"; // 1.7.10 替代 Jade
    public static final String THE_ONE_PROBE = "theoneprobe"; // 1.7.10 替代 Jade
    public static final String GREGTECH = "gregtech"; // GT5-Unofficial

    private OtherModIds() {}
}
