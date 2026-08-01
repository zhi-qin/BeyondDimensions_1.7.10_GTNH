package com.wintercogs.beyonddimensions.integration;

import cpw.mods.fml.common.Loader;

/**
 * 模组存在性检测（1.7.10 适配版）。
 * <p>
 * 1.20.1 使用 ModList.get().isLoaded(modId)，
 * 1.7.10 使用 cpw.mods.fml.common.Loader.isModLoaded(modId)。
 */
public final class ModPresence {

    private ModPresence() {}

    public static boolean isLoaded(String modId) {
        return Loader.isModLoaded(modId);
    }
}
