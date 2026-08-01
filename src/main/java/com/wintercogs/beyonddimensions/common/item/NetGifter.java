package com.wintercogs.beyonddimensions.common.item;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;

public class NetGifter extends NetedItem {

    public NetGifter() {
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

        int netId = NetedItem.getNetId(stack);
        if (netId >= 0) {
            DimensionsNet itemNet = DimensionsNet.getNetFromId(netId);
            if (itemNet != null) {
                DimensionsNet playerNet = DimensionsNet.getNetFromPlayer(player);
                if (playerNet != null && playerNet.getId() != itemNet.getId() && playerNet.isOwner(player)) {
                    int id = itemNet.getId();
                    playerNet.mergeOtherNet(itemNet);
                    stack.stackSize--;
                    player.addChatMessage(new ChatComponentTranslation("msg.beyonddimensions.net_gift_done", id));
                } else {
                    player.addChatMessage(new ChatComponentTranslation("msg.beyonddimensions.cant_merge_net"));
                }
            } else {
                player.addChatMessage(new ChatComponentTranslation("msg.beyonddimensions.error_item_net"));
            }
        } else {
            player.addChatMessage(new ChatComponentTranslation("msg.beyonddimensions.item_need_bound"));
        }

        return stack;
    }

    @Override
    protected boolean validToReWrite(DimensionsNet net, EntityPlayer player) {
        return net.isOwner(player);
    }
}
