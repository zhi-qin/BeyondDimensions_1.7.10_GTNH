package com.wintercogs.beyonddimensions.common.item;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;

public class NetedItem extends Item {

    public NetedItem() {
        super();
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote || !player.isSneaking()) {
            return stack;
        }

        if (setNet(stack, world, player)) {
            return stack;
        }
        return stack;
    }

    @Override
    public void onCreated(ItemStack stack, World world, EntityPlayer player) {
        super.onCreated(stack, world, player);
        if (world.isRemote) return;
        DimensionsNet net = DimensionsNet.getNetFromPlayer(player);
        if (net != null) {
            setNet(stack, world, player);
        }
    }

    public static boolean setNet(ItemStack stack, World world, EntityPlayer player) {
        if (stack.getItem() instanceof NetedItem) {
            NetedItem item = (NetedItem) stack.getItem();
            int netId = getNetId(stack);
            if (netId >= 0) {
                DimensionsNet itemNet = DimensionsNet.getNetFromId(netId);
                if (itemNet != null && !item.validToReWrite(itemNet, player)) {
                    player.addChatMessage(new ChatComponentTranslation("msg.beyonddimensions.no_right_to_bound_item"));
                    return false;
                }

                setNetId(stack, -1);
                world.playSoundEffect(player.posX, player.posY, player.posZ, "random.orb", 0.8F, 1.0F);
                player.addChatMessage(new ChatComponentTranslation("msg.beyonddimensions.item_net_unbound", netId));
                return true;
            }

            DimensionsNet playerNet = DimensionsNet.getNetFromPlayer(player);
            if (playerNet != null && item.validToReWrite(playerNet, player)) {
                setNetId(stack, playerNet.getId());
                world.playSoundEffect(player.posX, player.posY, player.posZ, "random.orb", 0.8F, 1.0F);
                player.addChatMessage(
                    new ChatComponentTranslation("msg.beyonddimensions.item_net_bound", playerNet.getId()));
                return true;
            }

            player.addChatMessage(new ChatComponentTranslation("msg.beyonddimensions.no_right_to_bound_item"));
            return false;
        }
        return false;
    }

    public static DimensionsNet getNet(ItemStack stack) {
        int netId = getNetId(stack);
        if (netId >= 0) {
            return DimensionsNet.getNetFromId(netId);
        }
        return null;
    }

    public static int getNetId(ItemStack stack) {
        if (stack != null && stack.hasTagCompound()) {
            NBTTagCompound tag = stack.getTagCompound();
            if (tag.hasKey("NetId")) {
                return tag.getInteger("NetId");
            }
        }
        return -1;
    }

    public static void setNetId(ItemStack stack, int netId) {
        if (stack != null) {
            if (!stack.hasTagCompound()) {
                stack.setTagCompound(new NBTTagCompound());
            }
            stack.getTagCompound()
                .setInteger("NetId", netId);
        }
    }

    protected boolean validToReWrite(DimensionsNet net, EntityPlayer player) {
        return net.isManager(player);
    }
}
