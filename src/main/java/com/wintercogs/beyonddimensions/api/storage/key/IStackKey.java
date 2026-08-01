package com.wintercogs.beyonddimensions.api.storage.key;

import java.nio.charset.StandardCharsets;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import com.wintercogs.beyonddimensions.BeyondDimensions;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EmptyStackKey;

import io.netty.buffer.ByteBuf;

/**
 * 资源Key，对一种资源的唯一标识，其实现必须是不可变对象
 * <p>
 * 1.7.10 移植版：FriendlyByteBuf → ByteBuf, ResourceLocation → net.minecraft.util.ResourceLocation,
 * TagKey → 存根（无操作），@NotNull/@Nullable → javax.annotation
 */
public interface IStackKey<T> {

    /**
     * 获取类型的唯一标识符 如(beyonddimension:stack_type/item) beyonddimension为本modID，提供对原版Item的支持，Item为要支持的Stack类型
     */
    ResourceLocation getTypeId();

    /**
     * 从具体堆叠转出一个KeyAmount，如：ItemStack->KeyAmount(ItemStackKey,Long)
     * <p>
     * 如当前实例的解释无法完成转换，应当返回null
     */
    @Nullable
    KeyAmount fromStackObject(Object stack);

    /**
     * 从未知源Object构建实例，如果Object不合法，则返回null
     *
     * @param key                类似Item
     * @param dataComponentPatch 数据组件（1.7.10 中对应 NBT tag）
     * @return 类似ItemStack
     */
    @Nullable
    IStackKey<T> fromSourceObject(Object key, NBTTagCompound dataComponentPatch);

    /**
     * 如ItemStackKey，返回ItemStack，应当返回一个缓存对象以提高性能
     * <p>
     * 由此输出的对象应当总是将数量设定为1，外界需要数量则应当自己再重新设置
     * <p>
     * 对于有组件的堆叠，不要修改它的组件！
     */
    T getReadOnlyStack();

    /**
     * 获取堆叠的类型，如ItemStackKey，返回ItemStack.class
     */
    Class<T> getStackClass();

    /**
     * 获取根，如：ItemStackType，返回其存储的Item
     */
    @Nonnull
    Object getSource();

    /**
     * 获取根类型，如：ItemStackType 返回Item.class
     */
    Class<?> getSourceClass();

    /**
     * 获取资源所属的模组id
     */
    String getModId();

    /**
     * 判断堆叠是否为空堆叠
     */
    boolean isEmpty();

    /**
     * 获取当前类型的空堆叠，如：ItemStackKey.getEmpty会返回 ItemStackKey.EMPTY
     */
    IStackKey<T> getEmpty();

    /**
     * 获得当前存储类型的空实例，如ItemStackKey返回空ItemStack
     */
    T getEmptyStack();

    /**
     * 复制存储的堆叠，数量固定为1
     */
    T copyStack();

    /**
     * 按数量复制存储的堆叠
     */
    T copyStackWithCount(long count);

    /**
     * 当前存储的堆叠，其在原版游戏的普通容器（如箱子）中，单个堆叠的最大容量应为多少？
     */
    long getVanillaMaxStackSize();

    /**
     * 你期望当前存储的堆叠最大容量为多少
     */
    long getCustomMaxStackSize();

    /**
     * 检查2个实例是否能模糊匹配，即：2个物品，是否为同一种物品，不管NBT等数据
     */
    boolean isSame(IStackKey<?> other);

    /**
     * 检查2个实例是否能精确匹配，即：2个物品，种类、NBT等数据是否一致
     */
    boolean isSameTypeSameComponents(IStackKey<?> other);

    /**
     * 网络序列化，只写入自己的实际负载
     */
    void serialize(ByteBuf buf);

    /**
     * 网络反序列化，只处理自己的负载
     */
    @Nonnull
    IStackKey<T> deserialize(ByteBuf buf);

    /**
     * 将key的数据以及id写入缓冲区
     */
    static void serializeCommon(ByteBuf buf, IStackKey<?> key) {
        writeResourceLocation(buf, key.getTypeId());
        key.serialize(buf);
    }

    /**
     * 从数据中解析key
     */
    @Nonnull
    static IStackKey<?> deserializeCommon(ByteBuf buf) {
        ResourceLocation typeId = readResourceLocation(buf);
        // 未知 typeId 回退 EmptyStackKey，避免 Netty 线程抛异常断连（客户端/服务端 jar 版本不一致）
        IStackKey<?> typeStack = StackKeyRegistry.getTypeOrEmpty(typeId);
        return typeStack.deserialize(buf);
    }

    /**
     * NBT序列化
     */
    @Nonnull
    NBTTagCompound serializeNBT();

    /**
     * NBT反序列化
     */
    @Nonnull
    IStackKey<T> deserializeNBT(NBTTagCompound nbt);

    /**
     * 将key序列化为NBT
     */
    static NBTTagCompound serializeNBTCommon(IStackKey<?> key) {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString(
            "type",
            key.getTypeId()
                .toString());
        nbt.setTag("stack", key.serializeNBT());
        return nbt;
    }

    /**
     * 从NBT中解析key
     */
    @Nonnull
    static IStackKey<?> deserializeNBTCommon(NBTTagCompound nbt) {
        ResourceLocation typeId = parseLocationSafe(nbt.getString("type"));
        // 未知 typeId 回退 EmptyStackKey，避免坏档崩服（parseLocationSafe 只保证格式合法，不保证已注册）
        IStackKey<?> typeStack = StackKeyRegistry.getTypeOrEmpty(typeId);
        return typeStack.deserializeNBT(nbt.getCompoundTag("stack"));
    }

    /**
     * 获取对应渲染器，仅在客户端调用
     */
    @Nonnull
    IStackRender getRender();

    /**
     * 获取仅用于渲染显示的堆叠，尽可能返回缓存以提高性能
     */
    @Nonnull
    T getRenderStack();

    /**
     * 强制要求重写哈希码
     */
    int hashCode();

    /**
     * 强制要求重写equals
     */
    boolean equals(Object other);

    // ==================== 静态辅助方法 ====================

    /**
     * 安全解析 ResourceLocation，失败时回退到已注册的 EmptyStackKey 类型 id。
     * 注意：不能回退到 minecraft:air —— 它未注册进 StackKeyRegistry，
     * 坏档/乱码的 type 字符串会让 getType 抛异常崩服/断线（BUGFIX_RECORD #98）。
     */
    static ResourceLocation parseLocationSafe(String s) {
        if (s == null || s.isEmpty()) return EmptyStackKey.INSTANCE.getTypeId();
        try {
            return new ResourceLocation(s);
        } catch (Exception e) {
            return EmptyStackKey.INSTANCE.getTypeId();
        }
    }

    /**
     * 将 ResourceLocation 写入 ByteBuf（域 + 路径分别写入）
     */
    static void writeResourceLocation(ByteBuf buf, ResourceLocation loc) {
        String domain = loc.getResourceDomain();
        String path = loc.getResourcePath();
        byte[] domainBytes = domain.getBytes(StandardCharsets.UTF_8);
        byte[] pathBytes = path.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(domainBytes.length);
        buf.writeBytes(domainBytes);
        buf.writeInt(pathBytes.length);
        buf.writeBytes(pathBytes);
    }

    /**
     * 从 ByteBuf 读取 ResourceLocation（域 + 路径分别读取）。
     * 读序必须与 {@link #writeResourceLocation} 一致：domainLen -> domainBytes -> pathLen -> pathBytes。
     * 注意：#93 曾误把 pathLen 提到 domainBytes 之前读取，导致读写不对称，
     * 每个 key 的 typeId 都读成乱码（"beyo"/"nddi" 这类域名片段），打开网络终端必崩（BUGFIX_RECORD #99）。
     * 每个长度在分配/读取前单独校验（不可用两长度之和判断，int 加法可回绕溢出）。
     */
    static ResourceLocation readResourceLocation(ByteBuf buf) {
        if (buf.readableBytes() < 8) {
            BeyondDimensions.LOGGER.warn("StackKey 类型 id 反序列化：缓冲区不足 8 字节，按空条目处理");
            return EmptyStackKey.INSTANCE.getTypeId();
        }
        int domainLen = buf.readInt();
        if (domainLen < 0 || domainLen > buf.readableBytes()) {
            BeyondDimensions.LOGGER
                .warn("StackKey 类型 id 反序列化：domainLen 非法({})，按空条目处理，" + "疑似客户端/服务端模组 jar 版本不一致或包流错位", domainLen);
            return EmptyStackKey.INSTANCE.getTypeId();
        }
        byte[] domainBytes = new byte[domainLen];
        buf.readBytes(domainBytes);
        if (buf.readableBytes() < 4) {
            BeyondDimensions.LOGGER.warn("StackKey 类型 id 反序列化：pathLen 长度不足，按空条目处理");
            return EmptyStackKey.INSTANCE.getTypeId();
        }
        int pathLen = buf.readInt();
        if (pathLen < 0 || pathLen > buf.readableBytes()) {
            BeyondDimensions.LOGGER
                .warn("StackKey 类型 id 反序列化：pathLen 非法({})，按空条目处理，" + "疑似客户端/服务端模组 jar 版本不一致或包流错位", pathLen);
            return EmptyStackKey.INSTANCE.getTypeId();
        }
        byte[] pathBytes = new byte[pathLen];
        buf.readBytes(pathBytes);
        String domain = new String(domainBytes, StandardCharsets.UTF_8);
        String path = new String(pathBytes, StandardCharsets.UTF_8);
        return new ResourceLocation(domain, path);
    }
}
