package com.wintercogs.beyonddimensions.api.storage.key;

import javax.annotation.Nonnull;

import net.minecraft.nbt.NBTTagCompound;

import com.wintercogs.beyonddimensions.api.storage.key.impl.EmptyStackKey;

import io.netty.buffer.ByteBuf;

/**
 * 一个包含key和amount的记录类，极其轻量
 * <p>
 * 一般仅作于外部的只读视图
 * <p>
 * 1.7.10 移植版：record → 常规类（public final 字段保持兼容），
 * FriendlyByteBuf → ByteBuf，writeVarLong/readVarLong → writeLong/readLong
 */
public final class KeyAmount {

    @Nonnull
    public final IStackKey<?> key;
    public final long amount;

    public KeyAmount(@Nonnull IStackKey<?> key, long amount) {
        this.key = key;
        this.amount = amount;
    }

    /**
     * 获取 key（record 风格 getter）
     */
    @Nonnull
    public IStackKey<?> key() {
        return key;
    }

    /**
     * 获取 amount（record 风格 getter）
     */
    public long amount() {
        return amount;
    }

    public boolean isEmpty() {
        return amount <= 0L || key.isEmpty();
    }

    /**
     * 给出当前kv对所代表的实际stack副本，不支持long数量的stack可能会被内部实现自动限制到int上限
     */
    public Object toStack() {
        return key.copyStackWithCount(amount);
    }

    // ==================== 网络序列化 ====================

    public static void serialize(ByteBuf buf, KeyAmount ka) {
        IStackKey.serializeCommon(buf, ka.key);
        buf.writeLong(ka.amount);
    }

    @Nonnull
    public static KeyAmount deserialize(ByteBuf buf) {
        IStackKey<?> key = IStackKey.deserializeCommon(buf);
        long amount = buf.readLong();
        return new KeyAmount(key, amount);
    }

    // ==================== NBT 序列化 ====================

    public static NBTTagCompound serializeNBT(KeyAmount ka) {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setTag("key", IStackKey.serializeNBTCommon(ka.key));
        nbt.setLong("amount", ka.amount);
        return nbt;
    }

    @Nonnull
    public static KeyAmount deserializeNBT(NBTTagCompound nbt) {
        // 新格式：顶层有 key 标签
        if (nbt.hasKey("key", 10)) {
            NBTTagCompound keyTag = nbt.getCompoundTag("key");
            IStackKey<?> key = IStackKey.deserializeNBTCommon(keyTag);
            long amount = readAmountCompat(nbt, keyTag);
            return new KeyAmount(key, amount);
        }

        // 旧格式兜底：返回空
        return new KeyAmount(EmptyStackKey.INSTANCE, 0L);
    }

    // ==================== 兼容性读取 ====================

    /**
     * 兼容读取 amount，支持多种 key 名称
     */
    private static long readAmountCompat(NBTTagCompound root, NBTTagCompound keyTag) {
        if (root.hasKey("amount", 99)) return root.getLong("amount");
        if (root.hasKey("Amount", 99)) return root.getLong("Amount");

        long inner = readAmountFromInternalStack(keyTag);
        if (inner != 0L) return inner;

        return readAmountFromInternalStack(root);
    }

    /**
     * 从内部堆叠 NBT 中尝试读取数量
     */
    private static long readAmountFromInternalStack(NBTTagCompound tag) {
        NBTTagCompound stack = null;

        if (tag.hasKey("internal_stack", 10)) stack = tag.getCompoundTag("internal_stack");
        else if (tag.hasKey("Stack", 10)) stack = tag.getCompoundTag("Stack");

        if (stack == null) return 0L;

        if (stack.hasKey("count", 99)) return stack.getLong("count");
        if (stack.hasKey("Count", 99)) return stack.getLong("Count");
        if (stack.hasKey("amount", 99)) return stack.getLong("amount");
        if (stack.hasKey("Amount", 99)) return stack.getLong("Amount");

        return 0L;
    }

    // ==================== equals / hashCode / toString ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof KeyAmount)) return false;
        KeyAmount other = (KeyAmount) o;
        return amount == other.amount && key.equals(other.key);
    }

    @Override
    public int hashCode() {
        int result = key.hashCode();
        result = 31 * result + (int) (amount ^ (amount >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "KeyAmount[key=" + key + ", amount=" + amount + "]";
    }
}
