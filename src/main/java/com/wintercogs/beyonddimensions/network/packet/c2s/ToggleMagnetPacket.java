package com.wintercogs.beyonddimensions.network.packet.c2s;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;

import com.wintercogs.beyonddimensions.common.item.BaseMachineItem;
import com.wintercogs.beyonddimensions.common.item.NetMagnetItem;
import com.wintercogs.beyonddimensions.common.machine.HopperFluidMode;
import com.wintercogs.beyonddimensions.common.machine.HopperItemMode;
import com.wintercogs.beyonddimensions.common.machine.RedStoneControlMode;
import com.wintercogs.beyonddimensions.util.BDMainThreadScheduler;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * 切换磁铁模式的网络包（1.7.10 移植版）。
 * 1.7.10 适配：record → 普通类；FriendlyByteBuf → ByteBuf；writeEnum/readEnum → writeInt/读取；
 * NetworkEvent.Context → MessageContext；player.getInventory().items → player.inventory.mainInventory；
 * player.sendSystemMessage(Component) → player.addChatMessage(new ChatComponentTranslation(...))。
 * MagnetToggleType 不存在于 1.7.10 移植版，使用本类内嵌的枚举替代，序号与源一致。
 */
public class ToggleMagnetPacket implements IMessage {

    // 与 1.20.1 MagnetToggleType 枚举序号保持一致
    public static final int ALL = 0;
    public static final int ITEM = 1;
    public static final int FLUID = 2;

    private int toggleType;

    public ToggleMagnetPacket() {}

    public ToggleMagnetPacket(int toggleType) {
        this.toggleType = toggleType;
    }

    public int getToggleType() {
        return toggleType;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.toggleType = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.toggleType);
    }

    public static class Handler implements IMessageHandler<ToggleMagnetPacket, IMessage> {

        @Override
        public IMessage onMessage(ToggleMagnetPacket message, MessageContext ctx) {
            // 包 handler 在 Netty IO 线程执行，toggleMagnet 改写物品 NBT 模式字段并广播聊天，
            // 切到服务端主线程（引用替换虽原子，但 addChatMessage 涉及主线程状态）
            BDMainThreadScheduler.scheduleServer(() -> handle(message, ctx));
            return null;
        }

        private static void handle(ToggleMagnetPacket message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return;

            List<ItemStack> itemStackList = new ArrayList<>();
            for (int i = 0; i < player.inventory.mainInventory.length; i++) {
                ItemStack stack = player.inventory.mainInventory[i];
                if (stack != null) {
                    itemStackList.add(stack);
                }
            }
            toggleMagnet(player, itemStackList, message.getToggleType());

            // TODO: 集成 Baubles API 时在此处扩展饰品栏扫描
        }

        private static void toggleMagnet(EntityPlayerMP player, List<ItemStack> itemStackList, int toggleType) {
            for (ItemStack stack : itemStackList) {
                if (stack.getItem() instanceof NetMagnetItem) {
                    switch (toggleType) {
                        case ALL:
                            if (BaseMachineItem.hasControlMode(stack)) {
                                RedStoneControlMode current = BaseMachineItem
                                    .getControlModeOrDefault(stack, RedStoneControlMode.IGNORE);
                                if (current == RedStoneControlMode.IGNORE) {
                                    BaseMachineItem.setControlMode(stack, RedStoneControlMode.NOT_WORKING);
                                    player.addChatMessage(
                                        new ChatComponentTranslation("msg.beyonddimensions.magnet.close"));
                                } else if (current == RedStoneControlMode.NOT_WORKING) {
                                    BaseMachineItem.setControlMode(stack, RedStoneControlMode.IGNORE);
                                    player.addChatMessage(
                                        new ChatComponentTranslation("msg.beyonddimensions.magnet.open"));
                                }
                            }
                            break;
                        case ITEM:
                            if (BaseMachineItem.hasHopperItemMode(stack)) {
                                HopperItemMode current = BaseMachineItem
                                    .getHopperItemModeOrDefault(stack, HopperItemMode.ALLOW);
                                if (current == HopperItemMode.ALLOW) {
                                    BaseMachineItem.setHopperItemMode(stack, HopperItemMode.DENY);
                                    player.addChatMessage(
                                        new ChatComponentTranslation("msg.beyonddimensions.magnet.itemclose"));
                                } else if (current == HopperItemMode.DENY) {
                                    BaseMachineItem.setHopperItemMode(stack, HopperItemMode.ALLOW);
                                    player.addChatMessage(
                                        new ChatComponentTranslation("msg.beyonddimensions.magnet.itemopen"));
                                }
                            }
                            break;
                        case FLUID:
                            if (BaseMachineItem.hasHopperFluidMode(stack)) {
                                HopperFluidMode current = BaseMachineItem
                                    .getHopperFluidModeOrDefault(stack, HopperFluidMode.ALLOW);
                                if (current == HopperFluidMode.ALLOW) {
                                    BaseMachineItem.setHopperFluidMode(stack, HopperFluidMode.DENY);
                                    player.addChatMessage(
                                        new ChatComponentTranslation("msg.beyonddimensions.magnet.fluidclose"));
                                } else if (current == HopperFluidMode.DENY) {
                                    BaseMachineItem.setHopperFluidMode(stack, HopperFluidMode.ALLOW);
                                    player.addChatMessage(
                                        new ChatComponentTranslation("msg.beyonddimensions.magnet.fluidopen"));
                                }
                            }
                            break;
                        default:
                            break;
                    }
                }
            }
        }
    }
}
