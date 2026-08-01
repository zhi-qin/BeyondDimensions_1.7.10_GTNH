package com.wintercogs.beyonddimensions.common.init;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;

import com.wintercogs.beyonddimensions.api.ids.BDConstants;

public class BDCreativeModeTabs {

    public static CreativeTabs BEYOND_DIMENSIONS_ITEMS_TAB;
    public static CreativeTabs BEYOND_DIMENSIONS_BLOCKS_TAB;

    public static void register() {
        BEYOND_DIMENSIONS_ITEMS_TAB = new CreativeTabs(BDConstants.MODID + ".items") {

            @Override
            public Item getTabIconItem() {
                return BDItems.NET_CREATER;
            }

            @Override
            public void displayAllReleventItems(List list) {
                super.displayAllReleventItems(list);
            }
        };

        BEYOND_DIMENSIONS_BLOCKS_TAB = new CreativeTabs(BDConstants.MODID + ".blocks") {

            @Override
            public Item getTabIconItem() {
                return Item.getItemFromBlock(BDBlocks.NET_CONTROL);
            }

            @Override
            public void displayAllReleventItems(List list) {
                super.displayAllReleventItems(list);
            }
        };
    }
}
