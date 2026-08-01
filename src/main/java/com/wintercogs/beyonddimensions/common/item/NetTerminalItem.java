package com.wintercogs.beyonddimensions.common.item;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import com.wintercogs.beyonddimensions.BeyondDimensions;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.common.gui.BDGuiHandler;

public class NetTerminalItem extends NetedItem {

    public NetTerminalItem() {
        super();
        setMaxStackSize(1);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (player.isSneaking()) {
            return super.onItemRightClick(stack, world, player);
        }

        if (world.isRemote) {
            return stack;
        }

        int netId = NetedItem.getNetId(stack);
        if (netId >= 0) {
            DimensionsNet net = DimensionsNet.getNetFromId(netId);
            if (net != null) {
                // 对齐源项目：便携终端右键打开 DimensionsCraftMenuTerminal（终端版合成菜单），
                // 而非 DimensionsCraftMenu。BDGuiHandler 会从玩家背包查找 terminalStack 并读取绑定的网络。
                player.openGui(
                    BeyondDimensions.instance,
                    BDGuiHandler.DIMENSIONS_CRAFT_MENU_TERMINAL,
                    world,
                    (int) player.posX,
                    (int) player.posY,
                    (int) player.posZ);
            } else {
                player.addChatMessage(new ChatComponentTranslation("msg.beyonddimensions.item_need_bound"));
            }
        } else {
            player.addChatMessage(new ChatComponentTranslation("msg.beyonddimensions.item_need_bound"));
        }

        return stack;
    }
}
