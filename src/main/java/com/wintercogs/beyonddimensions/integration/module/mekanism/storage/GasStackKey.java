package com.wintercogs.beyonddimensions.integration.module.mekanism.storage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import com.wintercogs.beyonddimensions.api.ids.BDConstants;
import com.wintercogs.beyonddimensions.api.storage.key.ByteBufHelper;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.IStackRender;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.integration.OtherModIds;
import com.wintercogs.beyonddimensions.util.BDMath;

import io.netty.buffer.ByteBuf;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasRegistry;
import mekanism.api.gas.GasStack;

/**
 * 1.7.10 适配版：Key = gas
 * <p>
 * - 不可变，Key 层不带数量
 * - 1.7.10 中 Gas 可为 null（GasRegistry.getGas 找不到时返回 null），用 null 表示空 Gas
 * - GasStack 可为 null，或 gas == null，或 amount <= 0 视为空
 * - read-only/render 返回 amount=1 的缓存
 * - serialize/serializeNBT 只写 payload（typeId 由 common 方法包一层）
 */
public final class GasStackKey implements IStackKey<GasStack> {

    public static final ResourceLocation ID = new ResourceLocation(BDConstants.MODID, "stack_type/chemicals/gas");

    public static final GasStackKey EMPTY = new GasStackKey((Gas) null);

    private static final long CUSTOM_MAX_STACK_SIZE = Long.MAX_VALUE;

    // ===== 不可变要素 =====
    private final Gas gas;

    // ===== 缓存（amount 恒为 1）=====
    private transient GasStack serverCache;
    private transient GasStack clientCache;

    private transient int hashCache;
    private transient boolean hashReady;

    private GasStackKey(Gas gas) {
        this.gas = gas; // null 表示空
    }

    public GasStackKey(GasStack stack) {
        this(stack == null ? null : stack.getGas());
    }

    // ---------------- IStackKey ----------------

    @Override
    public ResourceLocation getTypeId() {
        return ID;
    }

    @Override
    @Nullable
    public KeyAmount fromStackObject(Object stack) {
        if (stack instanceof GasStack) {
            GasStack s = (GasStack) stack;
            return new KeyAmount(new GasStackKey(s), s.amount);
        }
        return null;
    }

    @Override
    @Nullable
    public IStackKey<GasStack> fromSourceObject(Object key, NBTTagCompound dataComponentPatch) {
        // GasStack 没有额外 NBT 语义，忽略 dataComponentPatch
        if (key instanceof Gas) {
            return new GasStackKey((Gas) key);
        }
        return null;
    }

    @Override
    public GasStack getReadOnlyStack() {
        if (this.gas == null) {
            return null;
        }
        if (this.serverCache == null) {
            this.serverCache = new GasStack(this.gas, 1);
        }
        GasStack cache = this.serverCache;
        // 缓存被外界换了 type（理论不该发生，但保险）就重建
        if (cache.getGas() != this.gas) {
            this.serverCache = new GasStack(this.gas, 1);
            return this.serverCache;
        }
        cache.amount = 1;
        return cache;
    }

    @Override
    public Class<GasStack> getStackClass() {
        return GasStack.class;
    }

    @Override
    @Nonnull
    public Object getSource() {
        return gas;
    }

    @Override
    public Class<?> getSourceClass() {
        return Gas.class;
    }

    @Override
    public String getModId() {
        // 1.7.10 GasRegistry 不记录 Gas 所属模组，所有 Gas 均通过 Mekanism 的 GasRegistry 注册，
        // 因此统一返回 "mekanism"；空 Gas 返回 "unknown"
        if (this.gas == null) return "unknown";
        return OtherModIds.MEKANISM;
    }

    @Override
    public boolean isEmpty() {
        return this == EMPTY || this.gas == null;
    }

    @Override
    public IStackKey<GasStack> getEmpty() {
        return EMPTY;
    }

    @Override
    public GasStack getEmptyStack() {
        return null;
    }

    @Override
    public GasStack copyStack() {
        return copyStackWithCount(1);
    }

    @Override
    public GasStack copyStackWithCount(long count) {
        if (this.gas == null || count <= 0) return null;
        return new GasStack(this.gas, BDMath.clampLongToInt(count));
    }

    @Override
    public long getVanillaMaxStackSize() {
        // 对齐源项目：Gas 单槽最大容量 64000
        return 64_000L;
    }

    @Override
    public long getCustomMaxStackSize() {
        return CUSTOM_MAX_STACK_SIZE;
    }

    @Override
    public boolean isSame(IStackKey<?> other) {
        if (this == other) return true;
        if (other instanceof GasStackKey) {
            GasStackKey o = (GasStackKey) other;
            return this.gas == o.gas;
        }
        return false;
    }

    @Override
    public boolean isSameTypeSameComponents(IStackKey<?> other) {
        // GasStack 没有额外 NBT 语义，精确匹配就是 type 相等
        return isSame(other);
    }

    /**
     * 网络序列化：只写 payload（typeId 由 IStackKey.serializeCommon 写）
     */
    @Override
    public void serialize(ByteBuf buf) {
        boolean hasGas = this.gas != null;
        buf.writeBoolean(hasGas);
        if (!hasGas) return;

        ByteBufHelper.writeUtf8(buf, this.gas.getName());
    }

    @Override
    @Nonnull
    public IStackKey<GasStack> deserialize(ByteBuf buf) {
        boolean hasGas = buf.readBoolean();
        if (!hasGas) return EMPTY;

        String name = ByteBufHelper.readUtf8(buf);
        Gas g = GasRegistry.getGas(name);
        if (g == null) return EMPTY;
        return new GasStackKey(g);
    }

    /**
     * NBT 序列化：只写 payload（外层由 serializeNBTCommon 写 type）
     */
    @Override
    @Nonnull
    public NBTTagCompound serializeNBT() {
        NBTTagCompound out = new NBTTagCompound();
        if (this.gas != null) {
            out.setString("gas", this.gas.getName());
        }
        return out;
    }

    @Override
    @Nonnull
    public IStackKey<GasStack> deserializeNBT(NBTTagCompound nbt) {
        if (nbt == null) return EMPTY;

        // 新格式：gas 字段
        if (nbt.hasKey("gas", 8)) {
            Gas g = GasRegistry.getGas(nbt.getString("gas"));
            if (g == null) return EMPTY;
            return new GasStackKey(g);
        }

        // 旧格式：Stack 复合标签（兼容 GasStack.readFromNBT）
        if (nbt.hasKey("Stack", 10)) {
            try {
                GasStack gs = GasStack.readFromNBT(nbt.getCompoundTag("Stack"));
                if (gs == null || gs.getGas() == null) return EMPTY;
                return new GasStackKey(gs);
            } catch (Throwable t) {
                return EMPTY;
            }
        }

        return EMPTY;
    }

    @Override
    @Nonnull
    public IStackRender getRender() {
        return GasStackKeyRender.INSTANCE;
    }

    @Override
    @Nonnull
    public GasStack getRenderStack() {
        if (this.gas == null) {
            return null;
        }
        if (this.clientCache == null) {
            this.clientCache = new GasStack(this.gas, 1);
        }
        GasStack cache = this.clientCache;
        if (cache.getGas() != this.gas) {
            this.clientCache = new GasStack(this.gas, 1);
            return this.clientCache;
        }
        cache.amount = 1;
        return cache;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other instanceof GasStackKey) {
            return this.gas == ((GasStackKey) other).gas;
        }
        return false;
    }

    @Override
    public int hashCode() {
        if (!hashReady) {
            // 与旧版一致：只看 type
            hashCache = 31 + (gas == null ? 0 : gas.hashCode());
            hashReady = true;
        }
        return hashCache;
    }
}
