package com.wintercogs.beyonddimensions.network.packet.both;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;

import com.wintercogs.beyonddimensions.common.menu.BDBaseMenu;
import com.wintercogs.beyonddimensions.util.BDMainThreadScheduler;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

public class QuickDataTagPacket implements IMessage {

    private NBTTagCompound tag;

    public QuickDataTagPacket() {}

    public QuickDataTagPacket(NBTTagCompound tag) {
        this.tag = tag;
    }

    public NBTTagCompound getTag() {
        return tag;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            tag = new PacketBuffer(buf).readNBTTagCompoundFromBuffer();
        } catch (Exception ignored) {
            tag = null;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        try {
            new PacketBuffer(buf).writeNBTTagCompoundToBuffer(tag != null ? tag : new NBTTagCompound());
        } catch (Exception ignored) {}
    }

    public static class Handler implements IMessageHandler<QuickDataTagPacket, IMessage> {

        @Override
        public IMessage onMessage(QuickDataTagPacket message, MessageContext ctx) {
            // 包 handler 在 Netty IO 线程执行，readQuickDataTag 会写菜单字段（搜索文本/模式等）
            // 并与 GUI 渲染线程并发，双端都必须切到各自主线程
            if (ctx.side == Side.CLIENT) {
                BDMainThreadScheduler.scheduleClient(() -> handleClient(message));
            } else {
                BDMainThreadScheduler.scheduleServer(() -> handleServer(message, ctx));
            }
            return null;
        }

        private static void handleServer(QuickDataTagPacket message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) {
                return;
            }
            if (message.getTag() == null) {
                return;
            }
            if (player.openContainer instanceof BDBaseMenu) {
                ((BDBaseMenu) player.openContainer).readQuickDataTag(message.getTag());
            }
        }

        @SideOnly(Side.CLIENT)
        private static void handleClient(QuickDataTagPacket message) {
            EntityPlayer player = Minecraft.getMinecraft().thePlayer;
            if (player == null) return;
            if (message.getTag() == null) return;
            if (player.openContainer instanceof BDBaseMenu) {
                ((BDBaseMenu) player.openContainer).readQuickDataTag(message.getTag());
            }
        }
    }
}
