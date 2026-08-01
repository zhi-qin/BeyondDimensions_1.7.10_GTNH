package com.wintercogs.beyonddimensions.common.machine;

/**
 * 1.7.10 中原版只有 FurnaceRecipes.smelting()，没有 Smoking/Blasting 配方类型。
 * 此枚举用于区分三种熔炉的工作参数（烹饪时间、速度倍率）。
 * <ul>
 * <li>SMELTING：标准熔炉，200 tick</li>
 * <li>SMOKING：烟熏炉，100 tick（2 倍速）</li>
 * <li>BLASTING：高炉，100 tick（2 倍速）</li>
 * </ul>
 */
public enum FurnaceRecipeType {

    SMELTING(200),
    SMOKING(100),
    BLASTING(100);

    private final int cookTime;

    FurnaceRecipeType(int cookTime) {
        this.cookTime = cookTime;
    }

    public int getCookTime() {
        return cookTime;
    }
}
