package com.wintercogs.beyonddimensions.integration;

/**
 * 联动模块规格：modId + 实现类全名。
 * <p>
 * 1.7.10 适配版：源项目使用 record，这里使用不可变 final 类（与项目其他 1.7.10 类风格一致）。
 */
public final class ModuleSpec {

    private final String modId;
    private final String implClassName;

    public ModuleSpec(String modId, String implClassName) {
        this.modId = modId;
        this.implClassName = implClassName;
    }

    public String modId() {
        return modId;
    }

    public String implClassName() {
        return implClassName;
    }
}
