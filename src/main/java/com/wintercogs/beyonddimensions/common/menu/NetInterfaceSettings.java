package com.wintercogs.beyonddimensions.common.menu;

import com.wintercogs.beyonddimensions.common.machine.FuzzyMode;
import com.wintercogs.beyonddimensions.common.machine.PopMode;

/**
 * 网络接口设置（1.7.10 移植版）。
 * <p>
 * 与 1.20.1 源码保持一致，封装弹出模式与模糊模式两个状态。
 */
public class NetInterfaceSettings {

    private PopMode popMode = PopMode.STOP;
    private FuzzyMode fuzzyMode = FuzzyMode.DISABLE;

    public PopMode getPopMode() {
        return popMode;
    }

    public void setPopMode(PopMode popMode) {
        this.popMode = popMode;
    }

    public FuzzyMode getFuzzyMode() {
        return fuzzyMode;
    }

    public void setFuzzyMode(FuzzyMode fuzzyMode) {
        this.fuzzyMode = fuzzyMode;
    }
}
