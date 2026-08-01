package com.wintercogs.beyonddimensions.common.item;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;

public class NetDestroyer extends NetedItem {

    public NetDestroyer() {
        super();
        // 对齐 1.20.1 源项目：未限制堆叠（默认 64，销毁时仅扣 1 个，允许携带多个）
    }

    @Override
    public int getMaxItemUseDuration(ItemStack stack) {
        return 100; // 5 秒
    }

    @Override
    public EnumAction getItemUseAction(ItemStack stack) {
        // 对齐源项目 getUseAnimation() == UseAnim.BOW：长按蓄力期间显示拉弓动画
        return EnumAction.bow;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        super.onItemRightClick(stack, world, player);

        if (player.isSneaking()) {
            return stack;
        }

        player.setItemInUse(stack, getMaxItemUseDuration(stack));
        return stack;
    }

    @Override
    public ItemStack onEaten(ItemStack stack, World world, EntityPlayer player) {
        super.onEaten(stack, world, player);

        if (world.isRemote) return stack;

        int netId = NetedItem.getNetId(stack);
        if (netId >= 0) {
            DimensionsNet itemNet = DimensionsNet.getNetFromId(netId);
            if (itemNet != null) {
                DimensionsNet playerNet = DimensionsNet.getNetFromPlayer(player);
                if (playerNet != null && playerNet.getId() == itemNet.getId() && playerNet.isOwner(player)) {
                    playerNet.destroySelf();
                    stack.stackSize--;
                    player.addChatMessage(new ChatComponentTranslation("msg.beyonddimensions.item_net_destroyed"));
                } else {
                    player.addChatMessage(new ChatComponentTranslation("msg.beyonddimensions.cant_delete_net"));
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
