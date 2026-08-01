package com.wintercogs.beyonddimensions.api.storage.key;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import net.minecraft.util.ResourceLocation;

import com.wintercogs.beyonddimensions.BeyondDimensions;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EmptyStackKey;

/**
 * StackKey 类型注册表
 * <p>
 * 1.7.10 移植版：ResourceLocation → net.minecraft.util.ResourceLocation,
 * List.copyOf() → Collections.unmodifiableList(new ArrayList<>(...)),
 * 
 * @NotNull → javax.annotation.Nonnull
 */
public final class StackKeyRegistry {

    private static final Map<ResourceLocation, IStackKey<?>> TYPES = new HashMap<>();

    private StackKeyRegistry() {}

    /**
     * 注册一个 StackKey 类型
     */
    public static <T> void registerType(IStackKey<T> type) {
        if (TYPES.containsKey(type.getTypeId())) {
            throw new IllegalStateException("尝试注册重复类型的Key: " + type.getTypeId());
        }
        TYPES.put(type.getTypeId(), type);
    }

    /**
     * 根据 ResourceLocation 获取对应的 StackKey 类型
     */
    @SuppressWarnings("unchecked")
    @Nonnull
    public static <T> IStackKey<T> getType(ResourceLocation id) {
        IStackKey<?> type = TYPES.get(id);
        if (type == null) {
            throw new IllegalArgumentException("注册表中不存在此类型的Key，请先注册再使用: " + id);
        }
        return (IStackKey<T>) type;
    }

    /**
     * 根据 ResourceLocation 获取对应的 StackKey 类型；未注册时回退到 EmptyStackKey。
     * <p>
     * 供网络/NBT 反序列化使用：客户端/服务端 jar 版本不一致或恶意数据携带未知 typeId 时，
     * 不应抛异常断开连接（网络路径在 Netty 线程抛异常即断连），而应降级为空条目。
     * 由于 EmptyStackKey 的 serialize/deserialize 均不读写负载字节，回退后数据流仍保持对齐。
     */
    @Nonnull
    public static IStackKey<?> getTypeOrEmpty(ResourceLocation id) {
        IStackKey<?> type = TYPES.get(id);
        if (type == null) {
            BeyondDimensions.LOGGER.warn("StackKey 类型未注册，按空条目处理（可能客户端/服务端模组 jar 版本不一致）: {}", id);
            return EmptyStackKey.INSTANCE;
        }
        return type;
    }

    /**
     * 获取所有已注册类型的不可变列表
     */
    public static List<IStackKey<?>> getAllTypes() {
        return Collections.unmodifiableList(new ArrayList<>(TYPES.values()));
    }
}
