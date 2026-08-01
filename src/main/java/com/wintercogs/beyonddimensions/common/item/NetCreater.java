package com.wintercogs.beyonddimensions.common.item;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.init.BDItems;

public class NetCreater extends Item {

    public NetCreater() {
        super();
        setMaxStackSize(1);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote) {
            return stack;
        }

        DimensionsNet net = DimensionsNet.getNetFromPlayer(player);
        if (net != null) {
            return stack;
        }

        DimensionsNet newNet = DimensionsNet.createNewNetForPlayer(player, Long.MAX_VALUE, Integer.MAX_VALUE);

        stack.stackSize--;

        world.playSoundEffect(player.posX, player.posY, player.posZ, "random.levelup", 0.8F, 1.0F);

        player.addChatMessage(new ChatComponentTranslation("msg.beyonddimensions.network_created"));

        if (newNet != null) {
            ItemStack timeCrystal = new ItemStack(BDItems.SHATTERED_SPACE_TIME_CRYSTALLIZATION);
            newNet.getUnifiedStorage()
                .insert(new ItemStackKey(timeCrystal), 64, false);
        }

        return stack;
    }
}
