package com.wintercogs.beyonddimensions.api.storage.key.impl;

import java.util.Arrays;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import com.wintercogs.beyonddimensions.api.ids.BDConstants;
import com.wintercogs.beyonddimensions.api.storage.key.ByteBufHelper;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.IStackRender;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.render.ItemStackKeyRender;
import com.wintercogs.beyonddimensions.api.util.NbtEq;
import com.wintercogs.beyonddimensions.util.BDMath;
import com.wintercogs.beyonddimensions.util.NbtCanonicalBytesHelper;
import com.wintercogs.beyonddimensions.util.RegistryUtil;

import io.netty.buffer.ByteBuf;

/**
 * 1.7.10 适配版：Key = item + meta + tag
 */
public final class ItemStackKey implements IStackKey<ItemStack> {

    public static final ResourceLocation ID = new ResourceLocation(BDConstants.MODID, "stack_type/item");
    public static final ItemStackKey EMPTY = new ItemStackKey(null, 0, null);

    private static final long CUSTOM_MAX_STACK_SIZE = Long.MAX_VALUE;

    private final Item item;
    private final int meta;
    private final NBTTagCompound tag;

    private transient ItemStack serverCache;
    private transient ItemStack clientCache;
    private transient int vanillaMaxSize = -1;

    // 惰性缓存跨线程（Netty 同步 vs 渲染/逻辑线程）访问，置 volatile 保证可见性（审计 M1-8）
    private transient volatile byte[] signatureBytes;
    private transient volatile int hashCache;
    private transient volatile boolean hashReady;

    private ItemStackKey(Item item, int meta, @Nullable NBTTagCompound tag) {
        this.item = item;
        this.meta = meta;
        this.tag = (tag == null) ? null : (NBTTagCompound) tag.copy();
    }

    public ItemStackKey(ItemStack stack) {
        this(
            stack == null ? null : stack.getItem(),
            stack == null ? 0 : stack.getItemDamage(),
            stack == null ? null : stack.getTagCompound());
    }

    @Override
    public ResourceLocation getTypeId() {
        return ID;
    }

    @Override
    @Nullable
    public KeyAmount fromStackObject(Object stack) {
        if (stack instanceof ItemStack) {
            ItemStack s = (ItemStack) stack;
            return new KeyAmount(new ItemStackKey(s), s.stackSize);
        }
        return null;
    }

    @Override
    @Nullable
    public IStackKey<ItemStack> fromSourceObject(Object key, NBTTagCompound dataComponentPatch) {
        if (key instanceof Item) {
            Item it = (Item) key;
            ItemStack itemStack = new ItemStack(it, 1, 0);
            if (dataComponentPatch != null) itemStack.setTagCompound((NBTTagCompound) dataComponentPatch.copy());
            return new ItemStackKey(itemStack);
        }
        return null;
    }

    @Override
    public ItemStack getReadOnlyStack() {
        if (this.item == null) {
            return null;
        }
        if (this.serverCache == null) {
            this.serverCache = buildItemStack(this.item, this.meta, this.tag, 1);
        }
        ItemStack cache = this.serverCache;
        if (cache.getItem() != this.item) {
            this.serverCache = buildItemStack(this.item, this.meta, this.tag, 1);
            return this.serverCache;
        }
        cache.stackSize = 1;
        return cache;
    }

    @Override
    public Class<ItemStack> getStackClass() {
        return ItemStack.class;
    }

    @Override
    @Nonnull
    public Item getSource() {
        return item;
    }

    @Override
    public Class<?> getSourceClass() {
        return Item.class;
    }

    @Override
    public String getModId() {
        ResourceLocation key = RegistryUtil.getItemId(item) != null ? new ResourceLocation(RegistryUtil.getItemId(item))
            : new ResourceLocation("unknown", "unknown");
        return key.getResourceDomain();
    }

    @Override
    public boolean isEmpty() {
        // 空物品（空气物品的 ItemStack）也视为空，与源项目 isEmpty 语义一致（审计 M1-4）。
        // 1.7.10 无 Items.air 字段，空气物品经 Item.getItemById(0) 获取
        return this == EMPTY || this.item == null || this.item == Item.getItemById(0);
    }

    @Override
    public IStackKey<ItemStack> getEmpty() {
        return EMPTY;
    }

    @Override
    public ItemStack getEmptyStack() {
        return null;
    }

    @Override
    public ItemStack copyStack() {
        return copyStackWithCount(1);
    }

    @Override
    public ItemStack copyStackWithCount(long count) {
        if (this.item == null) return null;
        return buildItemStack(this.item, this.meta, this.tag, BDMath.clampLongToInt(count));
    }

    @Override
    public long getVanillaMaxStackSize() {
        if (this.item == null) return 1;
        if (vanillaMaxSize <= 0) {
            ItemStack tmp = copyStackWithCount(1);
            vanillaMaxSize = tmp.getItem()
                .getItemStackLimit(tmp);
        }
        return Math.min(vanillaMaxSize, getCustomMaxStackSize());
    }

    @Override
    public long getCustomMaxStackSize() {
        return CUSTOM_MAX_STACK_SIZE;
    }

    @Override
    public boolean isSame(IStackKey<?> other) {
        if (this == other) return true;
        if (other instanceof ItemStackKey) {
            ItemStackKey o = (ItemStackKey) other;
            return this.item == o.item && this.meta == o.meta;
        }
        return false;
    }

    @Override
    public boolean isSameTypeSameComponents(IStackKey<?> other) {
        if (this == other) return true;
        if (!(other instanceof ItemStackKey)) return false;
        ItemStackKey o = (ItemStackKey) other;
        if (this.item != o.item || this.meta != o.meta) return false;

        this.ensureSignatureBytes();
        o.ensureSignatureBytes();
        if (this.signatureBytes.length > 0 && o.signatureBytes.length > 0) {
            return Arrays.equals(this.signatureBytes, o.signatureBytes);
        }

        return NbtEq.equalsRelaxed(this.tag, o.tag);
    }

    @Override
    public void serialize(ByteBuf buf) {
        boolean hasItem = this.item != null;
        buf.writeBoolean(hasItem);
        if (!hasItem) return;

        ByteBufHelper.writeUtf8(buf, RegistryUtil.getItemId(this.item));
        buf.writeInt(this.meta);
        ByteBufHelper.writeNbt(buf, this.tag);
    }

    @Override
    @Nonnull
    public IStackKey<ItemStack> deserialize(ByteBuf buf) {
        boolean hasItem = buf.readBoolean();
        if (!hasItem) return EMPTY;

        String itemId = ByteBufHelper.readUtf8(buf);
        int meta = buf.readInt();
        NBTTagCompound tag = ByteBufHelper.readNbt(buf);
        Item it = RegistryUtil.getItemById(itemId);
        return new ItemStackKey(it, meta, tag);
    }

    @Override
    @Nonnull
    public NBTTagCompound serializeNBT() {
        NBTTagCompound out = new NBTTagCompound();
        String itemId = this.item == null ? null : RegistryUtil.getItemId(this.item);
        if (itemId == null) {
            // 空物品或未注册物品：写出空标签（deserializeNBT 读到无 "item" 键即返回 EMPTY），
            // 避免 setString 写入 null 后在 NBT 落盘时 NPE（审计 M1-7）
            return out;
        }
        out.setString("item", itemId);
        out.setInteger("meta", this.meta);
        if (this.tag != null) out.setTag("tag", this.tag.copy());
        return out;
    }

    @Override
    @Nonnull
    public IStackKey<ItemStack> deserializeNBT(NBTTagCompound nbt) {
        if (nbt == null) return EMPTY;

        if (nbt.hasKey("item", 8)) {
            Item it = RegistryUtil.getItemById(nbt.getString("item"));
            int meta = nbt.hasKey("meta", 99) ? nbt.getInteger("meta") : 0;
            NBTTagCompound tag = nbt.hasKey("tag", 10) ? nbt.getCompoundTag("tag") : null;
            return new ItemStackKey(it, meta, tag);
        }

        return EMPTY;
    }

    @Override
    @Nonnull
    public IStackRender getRender() {
        return ItemStackKeyRender.INSTANCE;
    }

    @Override
    @Nonnull
    public ItemStack getRenderStack() {
        if (this.item == null) return null;
        if (this.clientCache == null) {
            this.clientCache = buildItemStack(this.item, this.meta, this.tag, 1);
        }
        ItemStack cache = this.clientCache;
        if (cache.getItem() != this.item) {
            this.clientCache = buildItemStack(this.item, this.meta, this.tag, 1);
            return this.clientCache;
        }
        cache.stackSize = 1;
        return cache;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other instanceof ItemStackKey) return isSameTypeSameComponents((ItemStackKey) other);
        return false;
    }

    @Override
    public int hashCode() {
        if (!hashReady) {
            ensureSignatureBytes();
            int base = 31 + (item == null ? 0 : item.hashCode());
            base = 31 * base + meta;
            int nbtPart = (signatureBytes.length > 0) ? Arrays.hashCode(signatureBytes) : (31 * NbtEq.hashRelaxed(tag));
            hashCache = 31 * base + nbtPart;
            hashReady = true;
        }
        return hashCache;
    }

    private static @Nullable NBTTagCompound copyTagOrNull(@Nullable NBTTagCompound in) {
        return in == null ? null : (NBTTagCompound) in.copy();
    }

    private static @Nonnull ItemStack buildItemStack(Item item, int meta, @Nullable NBTTagCompound tag, int count) {
        ItemStack stack = new ItemStack(item, count, meta);
        if (tag != null) stack.setTagCompound((NBTTagCompound) tag.copy());
        return stack;
    }

    private void ensureSignatureBytes() {
        if (this.signatureBytes != null && this.signatureBytes.length > 0) return;
        byte[] out = NbtCanonicalBytesHelper.toCanonicalBytes(this.tag, null);
        this.signatureBytes = (out != null) ? out : new byte[0];
    }
}
