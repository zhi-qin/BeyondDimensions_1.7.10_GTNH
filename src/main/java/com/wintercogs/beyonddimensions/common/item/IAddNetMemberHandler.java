package com.wintercogs.beyonddimensions.common.item;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;

public interface IAddNetMemberHandler {

    boolean onAddNetMember(ItemStack stack, EntityPlayer player, World world, int netId);

    default boolean AddPlayerToNet(DimensionsNet net, EntityPlayer player) {
        if (net != null) {
            net.addPlayer(player.getUniqueID());
            return true;
        }

        return false;
    }
}
