package com.wintercogs.beyonddimensions.common.item;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;

public class NetMemberInviter extends NetedItem implements IAddNetMemberHandler {

    public NetMemberInviter() {
        super();
        setMaxStackSize(1);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        super.onItemRightClick(stack, world, player);

        if (player.isSneaking()) {
            return stack;
        }

        if (world.isRemote) {
            return stack;
        }

        if (DimensionsNet.getNetFromPlayer(player) == null) {
            int netId = NetedItem.getNetId(stack);
            if (netId >= 0) {
                boolean flag = onAddNetMember(stack, player, world, netId);
                if (flag) {
                    stack.stackSize--;
                }
            }
        }

        return stack;
    }

    @Override
    public boolean onAddNetMember(ItemStack stack, EntityPlayer player, World world, int netId) {
        DimensionsNet net = DimensionsNet.getNetFromId(netId);
        if (net != null) {
            net.addPlayer(player.getUniqueID());
            return true;
        }
        return false;
    }
}
