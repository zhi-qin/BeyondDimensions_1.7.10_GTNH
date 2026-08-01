package com.wintercogs.beyonddimensions.api.storage.key.impl;

import java.util.Arrays;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.wintercogs.beyonddimensions.api.ids.BDConstants;
import com.wintercogs.beyonddimensions.api.storage.key.ByteBufHelper;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.IStackRender;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.render.FluidStackKeyRender;
import com.wintercogs.beyonddimensions.api.util.NbtEq;
import com.wintercogs.beyonddimensions.util.BDMath;
import com.wintercogs.beyonddimensions.util.NbtCanonicalBytesHelper;
import com.wintercogs.beyonddimensions.util.RegistryUtil;

import io.netty.buffer.ByteBuf;

/**
 * 1.7.10 适配版：Key = fluid + tag
 */
public final class FluidStackKey implements IStackKey<FluidStack> {

    public static final ResourceLocation ID = new ResourceLocation(BDConstants.MODID, "stack_type/fluid");
    public static final FluidStackKey EMPTY = new FluidStackKey(null, null);

    private static final long CUSTOM_MAX_STACK_SIZE = Long.MAX_VALUE;

    private final Fluid fluid;
    private final NBTTagCompound tag;

    private transient FluidStack serverCache;
    private transient FluidStack clientCache;
    private transient int vanillaMaxSize = -1;

    private transient byte[] signatureBytes;
    private transient int hashCache;
    private transient boolean hashReady;

    private FluidStackKey(Fluid fluid, @Nullable NBTTagCompound tag) {
        this.fluid = fluid;
        this.tag = (tag == null) ? null : (NBTTagCompound) tag.copy();
    }

    public FluidStackKey(FluidStack stack) {
        this(stack == null ? null : stack.getFluid(), stack == null ? null : stack.tag);
    }

    @Override
    public ResourceLocation getTypeId() {
        return ID;
    }

    @Override
    @Nullable
    public KeyAmount fromStackObject(Object stack) {
        if (stack instanceof FluidStack) {
            FluidStack s = (FluidStack) stack;
            return new KeyAmount(new FluidStackKey(s), s.amount);
        }
        return null;
    }

    @Override
    @Nullable
    public IStackKey<FluidStack> fromSourceObject(Object key, NBTTagCompound dataComponentPatch) {
        if (key instanceof Fluid) {
            Fluid f = (Fluid) key;
            FluidStack fluidStack = new FluidStack(f, 1);
            if (dataComponentPatch != null) fluidStack.tag = (NBTTagCompound) dataComponentPatch.copy();
            return new FluidStackKey(fluidStack);
        }
        return null;
    }

    @Override
    public FluidStack getReadOnlyStack() {
        if (this.fluid == null) {
            return null;
        }
        if (this.serverCache == null) {
            this.serverCache = buildFluidStack(this.fluid, this.tag, 1);
        }
        FluidStack cache = this.serverCache;
        if (cache.getFluid() != this.fluid) {
            this.serverCache = buildFluidStack(this.fluid, this.tag, 1);
            return this.serverCache;
        }
        cache.amount = 1;
        return cache;
    }

    @Override
    public Class<FluidStack> getStackClass() {
        return FluidStack.class;
    }

    @Override
    @Nonnull
    public Fluid getSource() {
        return fluid;
    }

    @Override
    public Class<?> getSourceClass() {
        return Fluid.class;
    }

    @Override
    public String getModId() {
        String fluidName = RegistryUtil.getFluidName(fluid);
        ResourceLocation key = (fluidName != null && !fluidName.isEmpty()) ? new ResourceLocation(fluidName)
            : new ResourceLocation("unknown", "unknown");
        return key.getResourceDomain();
    }

    @Override
    public boolean isEmpty() {
        return this == EMPTY || this.fluid == null;
    }

    @Override
    public IStackKey<FluidStack> getEmpty() {
        return EMPTY;
    }

    @Override
    public FluidStack getEmptyStack() {
        return null;
    }

    @Override
    public FluidStack copyStack() {
        return copyStackWithCount(1);
    }

    @Override
    public FluidStack copyStackWithCount(long count) {
        if (this.fluid == null) return null;
        return buildFluidStack(this.fluid, this.tag, BDMath.clampLongToInt(count));
    }

    @Override
    public long getVanillaMaxStackSize() {
        if (this.fluid == null) return 1;
        // 对齐 1.20.1 源项目：流体单槽最大容量 64000 mB（64 桶），
        // 不缓存以保持与源项目一致的动态行为
        return Math.min(64_000L, getCustomMaxStackSize());
    }

    @Override
    public long getCustomMaxStackSize() {
        return CUSTOM_MAX_STACK_SIZE;
    }

    @Override
    public boolean isSame(IStackKey<?> other) {
        if (this == other) return true;
        if (other instanceof FluidStackKey) {
            FluidStackKey o = (FluidStackKey) other;
            return this.fluid == o.fluid;
        }
        return false;
    }

    @Override
    public boolean isSameTypeSameComponents(IStackKey<?> other) {
        if (this == other) return true;
        if (!(other instanceof FluidStackKey)) return false;
        FluidStackKey o = (FluidStackKey) other;
        if (this.fluid != o.fluid) return false;

        this.ensureSignatureBytes();
        o.ensureSignatureBytes();
        if (this.signatureBytes.length > 0 && o.signatureBytes.length > 0) {
            return Arrays.equals(this.signatureBytes, o.signatureBytes);
        }

        return NbtEq.equalsRelaxed(this.tag, o.tag);
    }

    @Override
    public void serialize(ByteBuf buf) {
        boolean hasFluid = this.fluid != null;
        buf.writeBoolean(hasFluid);
        if (!hasFluid) return;

        buf.writeInt(FluidRegistry.getFluidID(this.fluid));
        ByteBufHelper.writeNbt(buf, this.tag);
    }

    @Override
    @Nonnull
    public IStackKey<FluidStack> deserialize(ByteBuf buf) {
        boolean hasFluid = buf.readBoolean();
        if (!hasFluid) return EMPTY;

        int fluidId = buf.readInt();
        NBTTagCompound tag = ByteBufHelper.readNbt(buf);
        Fluid f = FluidRegistry.getFluid(fluidId);
        return new FluidStackKey(f, tag);
    }

    @Override
    @Nonnull
    public NBTTagCompound serializeNBT() {
        NBTTagCompound out = new NBTTagCompound();
        out.setString("fluid", RegistryUtil.getFluidName(this.fluid));
        if (this.tag != null) out.setTag("tag", this.tag.copy());
        return out;
    }

    @Override
    @Nonnull
    public IStackKey<FluidStack> deserializeNBT(NBTTagCompound nbt) {
        if (nbt == null) return EMPTY;

        if (nbt.hasKey("fluid", 8)) {
            Fluid f = RegistryUtil.getFluidByName(nbt.getString("fluid"));
            NBTTagCompound tag = nbt.hasKey("tag", 10) ? nbt.getCompoundTag("tag") : null;
            return new FluidStackKey(f, tag);
        }

        return EMPTY;
    }

    @Override
    @Nonnull
    public IStackRender getRender() {
        return FluidStackKeyRender.INSTANCE;
    }

    @Override
    @Nonnull
    public FluidStack getRenderStack() {
        if (this.fluid == null) return null;
        if (this.clientCache == null) {
            this.clientCache = buildFluidStack(this.fluid, this.tag, 1);
        }
        FluidStack cache = this.clientCache;
        if (cache.getFluid() != this.fluid) {
            this.clientCache = buildFluidStack(this.fluid, this.tag, 1);
            return this.clientCache;
        }
        cache.amount = 1;
        return cache;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other instanceof FluidStackKey) return isSameTypeSameComponents((FluidStackKey) other);
        return false;
    }

    @Override
    public int hashCode() {
        if (!hashReady) {
            ensureSignatureBytes();
            int base = 31 + (fluid == null ? 0 : fluid.hashCode());
            int nbtPart = (signatureBytes.length > 0) ? Arrays.hashCode(signatureBytes) : (31 * NbtEq.hashRelaxed(tag));
            hashCache = 31 * base + nbtPart;
            hashReady = true;
        }
        return hashCache;
    }

    private static @Nullable NBTTagCompound copyTagOrNull(@Nullable NBTTagCompound in) {
        return in == null ? null : (NBTTagCompound) in.copy();
    }

    private static @Nonnull FluidStack buildFluidStack(Fluid fluid, @Nullable NBTTagCompound tag, int amount) {
        FluidStack stack = new FluidStack(fluid, amount);
        if (tag != null) stack.tag = (NBTTagCompound) tag.copy();
        return stack;
    }

    private void ensureSignatureBytes() {
        if (this.signatureBytes != null && this.signatureBytes.length > 0) return;
        byte[] out = NbtCanonicalBytesHelper.toCanonicalBytes(this.tag, null);
        this.signatureBytes = (out != null) ? out : new byte[0];
    }
}
