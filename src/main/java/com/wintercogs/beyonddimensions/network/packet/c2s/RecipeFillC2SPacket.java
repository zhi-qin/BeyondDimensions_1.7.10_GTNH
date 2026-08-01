package com.wintercogs.beyonddimensions.network.packet.c2s;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.common.menu.DimensionsCraftMenu;
import com.wintercogs.beyonddimensions.util.BDMainThreadScheduler;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * 配方填充 C2S 网络包（1.7.10 移植版）。
 * 1.7.10 适配：record → 普通类；FriendlyByteBuf → ByteBuf；writeVarInt → writeInt；
 * NetworkEvent.Context → MessageContext；player.containerMenu → player.openContainer。
 */
public class RecipeFillC2SPacket implements IMessage {

    private List<IStackKey<?>> keys;
    private List<Long> amount;

    public RecipeFillC2SPacket() {
        this.keys = new ArrayList<>();
        this.amount = new ArrayList<>();
    }

    public RecipeFillC2SPacket(List<IStackKey<?>> keys, List<Long> amount) {
        this.keys = keys;
        this.amount = amount;
    }

    public List<IStackKey<?>> getKeys() {
        return keys;
    }

    public List<Long> getAmount() {
        return amount;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int keysSize = buf.readInt();
        // 读取前校验数量上限（配方槽最多 9 格，64 已留足余量），防恶意客户端
        // new ArrayList<>(2^30) 触发超大分配 OOM
        if (keysSize < 0 || keysSize > 64) {
            this.keys = new ArrayList<>();
            return;
        }
        this.keys = new ArrayList<>(keysSize);
        for (int i = 0; i < keysSize; i++) {
            this.keys.add(IStackKey.deserializeCommon(buf));
        }

        int amtSize = buf.readInt();
        if (amtSize < 0 || amtSize > 64) {
            this.amount = new ArrayList<>();
            return;
        }
        this.amount = new ArrayList<>(amtSize);
        for (int i = 0; i < amtSize; i++) {
            this.amount.add(buf.readLong());
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.keys.size());
        for (IStackKey<?> key : this.keys) {
            IStackKey.serializeCommon(buf, key);
        }

        buf.writeInt(this.amount.size());
        for (long v : this.amount) {
            buf.writeLong(v);
        }
    }

    public static class Handler implements IMessageHandler<RecipeFillC2SPacket, IMessage> {

        @Override
        public IMessage onMessage(RecipeFillC2SPacket message, MessageContext ctx) {
            // 包 handler 在 Netty IO 线程执行，transferRecipe 会操作工艺槽/背包/网络存储，
            // 必须切到服务端主线程
            BDMainThreadScheduler.scheduleServer(() -> handle(message, ctx));
            return null;
        }

        private void handle(RecipeFillC2SPacket message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return;
            List<IStackKey<?>> keys = message.getKeys();
            List<Long> amounts = message.getAmount();
            // 解码校验失败（fromBytes 超限被截断为空）或非法请求时直接拒绝，
            // 避免空列表误触发 cleanCraftSlots 清空工艺槽
            if (keys == null || amounts == null || keys.isEmpty() || keys.size() != amounts.size()) {
                return;
            }
            Container menu = player.openContainer;
            if (menu instanceof DimensionsCraftMenu) {
                DimensionsCraftMenu craftMenu = (DimensionsCraftMenu) menu;
                craftMenu.transferRecipe(keys, amounts);
            }
        }
    }
}
