package com.wintercogs.beyonddimensions.common.block.entity;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;
import net.minecraftforge.fluids.BlockFluidBase;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.StackHandler;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.init.BDFluids;
import com.wintercogs.beyonddimensions.common.machine.FilterMode;
import com.wintercogs.beyonddimensions.common.machine.HopperFluidMode;
import com.wintercogs.beyonddimensions.common.machine.HopperItemMode;
import com.wintercogs.beyonddimensions.common.machine.HopperNBTMode;
import com.wintercogs.beyonddimensions.common.machine.HopperRangeMode;
import com.wintercogs.beyonddimensions.common.machine.HopperXpMode;
import com.wintercogs.beyonddimensions.common.menu.NetHopperMenu;

/**
 * 网络漏斗方块实体（1.7.10 移植版）。
 * <p>
 * 1.20.1 原版实现：
 * - 实现 MenuProvider 接口以提供 GUI
 * - 通过 AABB 搜索范围内的 ItemEntity / ExperienceOrb
 * - 通过 FluidState 抽取世界流体（含水方块等 BucketPickup 块）
 * <p>
 * 1.7.10 适配：
 * - 移除 MenuProvider（1.7.10 由 BDGuiHandler 分发 GUI），保留 createMenu 方法
 * - AABB → AxisAlignedBB；ItemEntity → EntityItem；ExperienceOrb → EntityXPOrb
 * - 使用 worldObj.selectEntitiesWithinAABB(Class, AxisAlignedBB, IEntitySelector)
 * - FluidState 不存在，改用 BlockFluidBase/BlockLiquid + 元数据判断流体源块
 * - 区块最小/最大高度固定使用 0..256（1.7.10 世界无自定义高度概念）
 */
public class NetHopperBlockEntity extends BaseMachineBlockEntity {

    private static final int capacity = 36;
    private final StackHandler filterSlots = new StackHandler(capacity) {

        @Override
        public void onChange() {
            if (worldObj != null && !worldObj.isRemote) {
                markDirty();
            }
        }
    };

    public FilterMode filterMode = FilterMode.BLACK;
    public HopperItemMode hopperItemMode = HopperItemMode.ALLOW;
    public HopperFluidMode hopperFluidMode = HopperFluidMode.DENY;
    public HopperNBTMode hopperNBTMode = HopperNBTMode.DENY;
    public HopperXpMode hopperXpMode = HopperXpMode.DENY;
    public HopperRangeMode hopperRangeMode = HopperRangeMode.RADIUS_MID;

    private List<EntityItem> itemEntities = new ArrayList<>();
    private List<EntityXPOrb> xpEntities = new ArrayList<>();

    public NetHopperBlockEntity() {
        super();
    }

    public StackHandler getFilterSlots() {
        return filterSlots;
    }

    // ==================== BaseMachine 覆写 ====================

    @Override
    public boolean shouldWork() {
        return super.shouldWork() && getNet() != null;
    }

    @Override
    public int getTicksPerWork() {
        switch (hopperRangeMode) {
            case RADIUS_LOWEST:
                return 5;
            case RADIUS_LOW:
                return 10;
            case RADIUS_MID:
                return 20;
            case RADIUS_HIGH:
                return 60;
            case RADIUS_HIGHEST:
                return 100;
            case CHUNK_MODE:
                return 1200;
            default:
                return 20;
        }
    }

    @Override
    public void workStart() {
        AxisAlignedBB searchArea = getSearchArea();
        if (hopperItemMode == HopperItemMode.ALLOW) {
            refreshItemEntityCache(searchArea);
        }
        if (hopperXpMode == HopperXpMode.ALLOW) {
            refreshXpEntityCache(searchArea);
        }
    }

    @Override
    public void workContent() {
        UnifiedStorage storage = getNet().getUnifiedStorage(); // getNet已在shouldWork完成null检查

        // 开始收集物品
        if (hopperItemMode == HopperItemMode.ALLOW) {
            for (EntityItem itemEntity : itemEntities) {
                if (itemEntity == null || itemEntity.isDead) continue;
                ItemStack itemStack = itemEntity.getEntityItem();
                if (itemStack == null || itemStack.stackSize <= 0) continue;
                IStackKey<?> itemKey = new ItemStackKey(itemStack);
                if (matchesFilter(itemKey)) {
                    // 模拟插入：返回值 isEmpty 表示能全部插入
                    KeyAmount simRemain = storage.insert(itemKey, itemStack.stackSize, true);
                    if (simRemain.isEmpty()) {
                        // 实际插入
                        storage.insert(itemKey, itemStack.stackSize, false);
                        itemEntity.setDead();
                    }
                }
            }
        }

        // 开始收集经验球（转为 XP 流体）
        if (hopperXpMode == HopperXpMode.ALLOW) {
            for (EntityXPOrb orb : xpEntities) {
                if (orb == null || orb.isDead) continue;
                int xp = orb.xpValue;
                if (xp <= 0) continue;
                long xpFluid = xp * 20L;
                FluidStack xpStack = new FluidStack(BDFluids.XP_FLUID, 1);
                FluidStackKey xpKey = new FluidStackKey(xpStack);

                if (storage.insert(xpKey, xpFluid, true)
                    .isEmpty()) {
                    storage.insert(xpKey, xpFluid, false);
                    orb.setDead();
                }
            }
        }

        // 开始抽取流体
        if (hopperFluidMode == HopperFluidMode.ALLOW) {
            fluidCollect(getSearchArea());
        }
    }

    // ==================== 搜索区域 ====================

    private AxisAlignedBB getSearchArea() {
        if (hopperRangeMode != HopperRangeMode.CHUNK_MODE) {
            int radius;
            switch (hopperRangeMode) {
                case RADIUS_LOWEST:
                    radius = 2;
                    break;
                case RADIUS_LOW:
                    radius = 3;
                    break;
                case RADIUS_MID:
                    radius = 5;
                    break;
                case RADIUS_HIGH:
                    radius = 7;
                    break;
                case RADIUS_HIGHEST:
                    radius = 10;
                    break;
                default:
                    radius = 1;
                    break;
            }
            // 对齐 1.20.1 源项目：AABB max 使用 xCoord + radius（不含 +1），
            // 源项目实体搜索范围宽度为 2*radius，移植版原 +1 会导致每维多搜 1 格
            return AxisAlignedBB.getBoundingBox(
                xCoord - radius,
                yCoord - radius,
                zCoord - radius,
                xCoord + radius,
                yCoord + radius,
                zCoord + radius);
        } else {
            // 区块模式：当前 TE 所在 chunk 的整个 16x16 区域，y 轴覆盖 0..256
            int chunkX = xCoord >> 4;
            int chunkZ = zCoord >> 4;
            int minX = chunkX << 4;
            int maxX = minX + 15;
            int minZ = chunkZ << 4;
            int maxZ = minZ + 15;
            int minY = 0;
            int maxY = 255;
            // 对齐 1.20.1 源项目：CHUNK 模式 AABB 为 [min, max]（maxX+15），无 +1 补偿。
            // fluidCollect 循环用闭区间 <=，恰好覆盖整个 16x16 区块；此前 AABB max 加 +1 配合
            // 开区间 < 循环虽覆盖同样方块，但实体收集会多收区块外 1 格，属边界不一致。
            return AxisAlignedBB.getBoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }

    @SuppressWarnings("unchecked")
    private void refreshItemEntityCache(AxisAlignedBB searchArea) {
        // 1.7.10 中 selectEntitiesWithinAABB 返回 List<Entity>，需要手动过滤
        List<EntityItem> result = new ArrayList<>();
        List<?> raw = worldObj.selectEntitiesWithinAABB(EntityItem.class, searchArea, entity -> {
            if (!(entity instanceof EntityItem)) return false;
            ItemStack stack = ((EntityItem) entity).getEntityItem();
            if (stack == null) return false;
            // NBT 过滤
            if (hopperNBTMode == HopperNBTMode.DENY) {
                return stack.stackTagCompound == null;
            }
            return true;
        });
        for (Object o : raw) {
            if (o instanceof EntityItem) result.add((EntityItem) o);
        }
        this.itemEntities = result;
    }

    @SuppressWarnings("unchecked")
    private void refreshXpEntityCache(AxisAlignedBB searchArea) {
        List<EntityXPOrb> result = new ArrayList<>();
        List<?> raw = worldObj.selectEntitiesWithinAABB(EntityXPOrb.class, searchArea, entity -> true);
        for (Object o : raw) {
            if (o instanceof EntityXPOrb) result.add((EntityXPOrb) o);
        }
        this.xpEntities = result;
    }

    // ==================== 流体收集 ====================

    private void fluidCollect(AxisAlignedBB searchArea) {
        if (worldObj == null || worldObj.isRemote) return;

        int minX = MathHelper.floor_double(searchArea.minX);
        int minY = MathHelper.floor_double(searchArea.minY);
        int minZ = MathHelper.floor_double(searchArea.minZ);
        int maxX = MathHelper.floor_double(searchArea.maxX);
        int maxY = MathHelper.floor_double(searchArea.maxY);
        int maxZ = MathHelper.floor_double(searchArea.maxZ);

        // 对齐 1.20.1 源项目：循环使用闭区间 <=，RADIUS 模式覆盖 [pos-r, pos+r]（2r+1 格）。
        // 此前用开区间 < 使每个方向最外 1 格流体收不到（与实体收集 selectEntitiesWithinAABB 的
        // >= 边界判定自相矛盾）；CHUNK 模式 AABB 已改为 [min, max]（无 +1），<= 恰好覆盖整个区块。
        for (int x = minX; x <= maxX; ++x) {
            for (int y = minY; y <= maxY; ++y) {
                for (int z = minZ; z <= maxZ; ++z) {
                    Block block = worldObj.getBlock(x, y, z);
                    if (block == null) continue;

                    Fluid fluid = null;
                    int amount = 0;

                    if (block instanceof BlockFluidBase) {
                        BlockFluidBase fluidBlock = (BlockFluidBase) block;
                        int meta = worldObj.getBlockMetadata(x, y, z);
                        // Forge BlockFluidBase: meta 0 表示源块，> 0 表示流动
                        if (meta == 0) {
                            fluid = fluidBlock.getFluid();
                            amount = 1000; // BUCKET_VOLUME
                        }
                    } else if (block instanceof BlockLiquid) {
                        int meta = worldObj.getBlockMetadata(x, y, z);
                        // 1.7.10 中 BlockLiquid 元数据 0 表示源块，1-7 表示流动
                        if (meta == 0) {
                            // 通过 FluidRegistry 推断流体：水/熔岩
                            if (block == net.minecraft.init.Blocks.water
                                || block == net.minecraft.init.Blocks.flowing_water) {
                                fluid = FluidRegistry.WATER;
                            } else if (block == net.minecraft.init.Blocks.lava
                                || block == net.minecraft.init.Blocks.flowing_lava) {
                                    fluid = FluidRegistry.LAVA;
                                } else {
                                    // 其他注册到 FluidRegistry 的液体方块
                                    fluid = FluidRegistry.lookupFluidForBlock(block);
                                }
                            amount = 1000;
                        }
                    }

                    if (fluid == null || amount <= 0) continue;

                    FluidStack extracted = new FluidStack(fluid, amount);
                    UnifiedStorage storage = getNet().getUnifiedStorage();
                    FluidStackKey fluidKey = new FluidStackKey(extracted);
                    if (matchesFilter(fluidKey)) {
                        if (storage.insert(fluidKey, extracted.amount, true)
                            .isEmpty()) {
                            storage.insert(fluidKey, extracted.amount, false);
                            // 清空方块（1.7.10 中直接置为空气）
                            worldObj.setBlockToAir(x, y, z);
                        }
                    }
                }
            }
        }
    }

    // ==================== 过滤匹配 ====================

    private boolean matchesFilter(IStackKey<?> otherStack) {
        switch (filterMode) {
            case BLACK: {
                for (KeyAmount stack : filterSlots.getStorage()) {
                    if (stack.key()
                        .isSame(otherStack)) return false;
                }
                return true;
            }
            case WHITE: {
                for (KeyAmount stack : filterSlots.getStorage()) {
                    if (stack.key()
                        .isSame(otherStack)) return true;
                }
                return false;
            }
            case IGNORE: {
                return true;
            }
            default:
                return false;
        }
    }

    // ==================== NBT 持久化 ====================

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        if (tag.hasKey("filter_slots")) {
            filterSlots.deserializeNBT(tag.getCompoundTag("filter_slots"));
        }
        String filterModeStr = tag.getString("filter_type");
        if (filterModeStr != null && !filterModeStr.isEmpty()) {
            try {
                filterMode = FilterMode.valueOf(filterModeStr);
            } catch (IllegalArgumentException ignored) {}
        }
        String hopperFluidStr = tag.getString("hopper_fluid_mode");
        if (hopperFluidStr != null && !hopperFluidStr.isEmpty()) {
            try {
                hopperFluidMode = HopperFluidMode.valueOf(hopperFluidStr);
            } catch (IllegalArgumentException ignored) {}
        }
        String hopperNbtStr = tag.getString("hopper_nbt_mode");
        if (hopperNbtStr != null && !hopperNbtStr.isEmpty()) {
            try {
                hopperNBTMode = HopperNBTMode.valueOf(hopperNbtStr);
            } catch (IllegalArgumentException ignored) {}
        }
        String hopperRangeStr = tag.getString("hopper_range_mode");
        if (hopperRangeStr != null && !hopperRangeStr.isEmpty()) {
            try {
                hopperRangeMode = HopperRangeMode.valueOf(hopperRangeStr);
            } catch (IllegalArgumentException ignored) {}
        }
        if (tag.hasKey("hopper_item_model")) {
            try {
                hopperItemMode = HopperItemMode.valueOf(tag.getString("hopper_item_model"));
            } catch (IllegalArgumentException ignored) {}
        }
        if (tag.hasKey("hopper_xp_mode")) {
            try {
                hopperXpMode = HopperXpMode.valueOf(tag.getString("hopper_xp_mode"));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setTag("filter_slots", filterSlots.serializeNBT());
        tag.setString("filter_type", filterMode.name());
        tag.setString("hopper_fluid_mode", hopperFluidMode.name());
        tag.setString("hopper_nbt_mode", hopperNBTMode.name());
        tag.setString("hopper_range_mode", hopperRangeMode.name());
        tag.setString("hopper_item_model", hopperItemMode.name());
        tag.setString("hopper_xp_mode", hopperXpMode.name());
    }

    // ==================== GUI ====================

    public IChatComponent getDisplayName() {
        return new ChatComponentTranslation("menu.title.beyonddimensions.hopper_menu");
    }

    public Container createMenu(int containerId, InventoryPlayer inventory, EntityPlayer player) {
        return new NetHopperMenu(inventory);
    }
}
