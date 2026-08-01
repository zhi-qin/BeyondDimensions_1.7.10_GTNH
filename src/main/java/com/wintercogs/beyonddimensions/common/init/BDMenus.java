package com.wintercogs.beyonddimensions.common.init;

import com.wintercogs.beyonddimensions.BeyondDimensions;
import com.wintercogs.beyonddimensions.common.gui.BDGuiHandler;

import cpw.mods.fml.common.network.NetworkRegistry;

public class BDMenus {

    public static void register() {
        NetworkRegistry.INSTANCE.registerGuiHandler(BeyondDimensions.instance, new BDGuiHandler());
    }
}
