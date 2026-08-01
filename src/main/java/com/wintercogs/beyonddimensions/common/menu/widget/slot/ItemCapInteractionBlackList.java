package com.wintercogs.beyonddimensions.common.menu.widget.slot;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.item.Item;

public class ItemCapInteractionBlackList {

    private static final Set<Item> blacklist = new HashSet<>();

    public static boolean addToBlackList(Item item) {
        return blacklist.add(item);
    }

    public static boolean removeFromBlackList(Item item) {
        return blacklist.remove(item);
    }

    public static boolean isInBlackList(Item item) {
        return blacklist.contains(item);
    }
}
