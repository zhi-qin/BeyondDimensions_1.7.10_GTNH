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
 * 自动合成 C2S 网络包（NEI Shift+C 自动合成用）。
 * <p>
 * 携带配方单次填充方案（9 槽 key + 单次配方量）与目标合成次数；
 * 服务端在 {@link DimensionsCraftMenu#autoCraft} 中逐次执行
 * "填充工艺槽 → 取走结果（复用 AutoRefillResultSlot 的借料/工具磨损/返还逻辑）→ 产出到背包"，
 * 使 10 铁锭 = 5 次锻造锤合成 = 5 铁板（BUGFIX_RECORD #103）。
 * <p>
 * 客户端不再像 #102 那样把倍数交给 AutoCraftingManager 循环重试（会因网络存储量
 * 不即时下降而死循环），而是把倍数一次发给服务端完成全部合成。
 */
public class AutoCraftC2SPacket implements IMessage {

    private List<IStackKey<?>> keys;
    private List<Long> amount;
    private int multiplier;

    public AutoCraftC2SPacket() {
        this.keys = new ArrayList<>();
        this.amount = new ArrayList<>();
    }

    public AutoCraftC2SPacket(List<IStackKey<?>> keys, List<Long> amount, int multiplier) {
        this.keys = keys;
        this.amount = amount;
        this.multiplier = multiplier;
    }

    public List<IStackKey<?>> getKeys() {
        return keys;
    }

    public List<Long> getAmount() {
        return amount;
    }

    public int getMultiplier() {
        return multiplier;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int keysSize = buf.readInt();
        // 读取前校验数量上限（配方槽最多 9 格，64 已留足余量），防恶意客户端大分配
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
        this.multiplier = buf.readInt();
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
        buf.writeInt(this.multiplier);
    }

    public static class Handler implements IMessageHandler<AutoCraftC2SPacket, IMessage> {

        @Override
        public IMessage onMessage(AutoCraftC2SPacket message, MessageContext ctx) {
            // 包 handler 在 Netty IO 线程执行，autoCraft 会操作玩家背包/工艺槽/网络存储，
            // 必须切到服务端主线程（1.7.10 无 MinecraftServer.addScheduledTask，经 tick 队列调度）
            BDMainThreadScheduler.scheduleServer(() -> handle(message, ctx));
            return null;
        }

        private void handle(AutoCraftC2SPacket message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return;
            List<IStackKey<?>> keys = message.getKeys();
            List<Long> amounts = message.getAmount();
            // 解码校验失败（fromBytes 超限被截断为空）或非法请求（空配方/负倍数）时直接拒绝，
            // 避免空列表误触发 cleanCraftSlots 清空工艺槽
            if (keys == null || amounts == null
                || keys.isEmpty()
                || keys.size() != amounts.size()
                || message.getMultiplier() <= 0) {
                return;
            }
            Container menu = player.openContainer;
            if (menu instanceof DimensionsCraftMenu) {
                DimensionsCraftMenu craftMenu = (DimensionsCraftMenu) menu;
                final int count = Math.max(1, Math.min(64, message.getMultiplier()));
                craftMenu.autoCraft(keys, amounts, count);
            }
        }
    }
}
