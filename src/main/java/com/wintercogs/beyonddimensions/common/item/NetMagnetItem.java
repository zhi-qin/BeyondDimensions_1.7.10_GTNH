package com.wintercogs.beyonddimensions.common.item;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.wintercogs.beyonddimensions.BeyondDimensions;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.gui.BDGuiHandler;
import com.wintercogs.beyonddimensions.common.init.BDFluids;
import com.wintercogs.beyonddimensions.common.machine.FilterMode;
import com.wintercogs.beyonddimensions.common.machine.HopperFluidMode;
import com.wintercogs.beyonddimensions.common.machine.HopperItemMode;
import com.wintercogs.beyonddimensions.common.machine.HopperNBTMode;
import com.wintercogs.beyonddimensions.common.machine.HopperRangeMode;
import com.wintercogs.beyonddimensions.common.machine.HopperXpMode;

import cpw.mods.fml.common.FMLCommonHandler;

public class NetMagnetItem extends BaseMachineItem {

    public static final int CAPACITY = 36;

    public NetMagnetItem() {
        super();
        setMaxStackSize(1);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (player.isSneaking()) {
            return super.onItemRightClick(stack, world, player);
        }

        if (world.isRemote) {
            return stack;
        }

        player.openGui(
            BeyondDimensions.instance,
            BDGuiHandler.NET_MAGNET_MENU,
            world,
            (int) player.posX,
            (int) player.posY,
            (int) player.posZ);
        return stack;
    }

    @Override
    public void checkComponents(ItemStack stack) {
        super.checkComponents(stack);
        if (!hasFilterSlots(stack)) setFilterSlots(stack, emptyFilterSlots(CAPACITY));
        if (!hasFilterMode(stack)) setFilterMode(stack, FilterMode.BLACK);
        if (!hasHopperItemMode(stack)) setHopperItemMode(stack, HopperItemMode.ALLOW);
        if (!hasHopperXpMode(stack)) setHopperXpMode(stack, HopperXpMode.DENY);
        if (!hasHopperNBTMode(stack)) setHopperNBTMode(stack, HopperNBTMode.DENY);
        if (!hasHopperFluidMode(stack)) setHopperFluidMode(stack, HopperFluidMode.DENY);
        if (!hasHopperRangeMode(stack)) setHopperRangeMode(stack, HopperRangeMode.RADIUS_MID);
    }

    @Override
    public boolean shouldWork(ItemStack stack, World world, Entity holder, int slotId, boolean isSelected) {
        return super.shouldWork(stack, world, holder, slotId, isSelected) && NetedItem.getNet(stack) != null;
    }

    @Override
    public void workContent(ItemStack stack, World world, Entity holder, int slotId, boolean isSelected) {
        super.workContent(stack, world, holder, slotId, isSelected);

        FilterMode filterMode = getFilterModeOrDefault(stack, FilterMode.BLACK);
        HopperItemMode hopperItemMode = getHopperItemModeOrDefault(stack, HopperItemMode.ALLOW);
        HopperXpMode hopperXpMode = getHopperXpModeOrDefault(stack, HopperXpMode.DENY);
        HopperNBTMode hopperNBTMode = getHopperNBTModeOrDefault(stack, HopperNBTMode.DENY);
        HopperFluidMode hopperFluidMode = getHopperFluidModeOrDefault(stack, HopperFluidMode.DENY);
        HopperRangeMode hopperRangeMode = getHopperRangeModeOrDefault(stack, HopperRangeMode.RADIUS_MID);
        List<KeyAmount> filterSlots = getFilterSlotsOrDefault(stack, new ArrayList<KeyAmount>());

        AxisAlignedBB searchArea = getSearchArea(hopperRangeMode, holder);

        List<EntityItem> itemEntities = hopperItemMode == HopperItemMode.ALLOW
            ? refreshItemEntityCache(hopperNBTMode, world, searchArea)
            : new ArrayList<EntityItem>();
        List<EntityXPOrb> xpEntities = hopperXpMode == HopperXpMode.ALLOW ? refreshXpEntityCache(world, searchArea)
            : new ArrayList<EntityXPOrb>();

        UnifiedStorage storage = NetedItem.getNet(stack)
            .getUnifiedStorage();

        // 收集物品
        if (hopperItemMode == HopperItemMode.ALLOW) {
            for (EntityItem itemEntity : itemEntities) {
                if (itemEntity != null && !itemEntity.isDead) {
                    ItemStack itemStack = itemEntity.getEntityItem();
                    ItemStackKey itemKey = new ItemStackKey(itemStack);
                    if (matchesFilter(filterMode, filterSlots, itemKey)) {
                        int count = itemStack.stackSize;

                        if (storage.insert(itemKey, count, true)
                            .isEmpty()) {
                            if (holder instanceof EntityPlayer player) {
                                // 对齐源项目：补发 Forge 拾取事件（可取消）+ FML ItemPickupEvent。
                                // 磁铁绕过 vanilla 拾取路径（EntityItem.onCollideWithPlayer），
                                // 依赖拾取事件的第三方模组逻辑与拾取动画会缺失（审计 M4-3）。
                                if (MinecraftForge.EVENT_BUS.post(new EntityItemPickupEvent(player, itemEntity))) {
                                    // 拾取被第三方模组取消：保留实体与网络库存，跳过此物品
                                    continue;
                                }
                                // 1.7.10: onItemPickup 仅发送收集动画包，不触发拾取事件，
                                // 事件需手动补发
                                itemStack.stackSize = 0;
                                FMLCommonHandler.instance()
                                    .firePlayerItemPickupEvent(player, itemEntity);
                                player.onItemPickup(itemEntity, count);
                                itemStack.stackSize = count;
                            }

                            itemEntity.setDead();
                            storage.insert(itemKey, count, false);
                        }
                    }
                }
            }
        }

        // 收集经验球
        if (hopperXpMode == HopperXpMode.ALLOW) {
            for (EntityXPOrb orb : xpEntities) {
                if (orb != null && !orb.isDead) {
                    int xp = orb.xpValue;
                    if (xp > 0) {
                        long xpFluid = xp * 20L;
                        FluidStackKey xpStack = new FluidStackKey(new FluidStack(BDFluids.XP_FLUID, 1));

                        if (storage.insert(xpStack, xpFluid, true)
                            .isEmpty()) {
                            orb.setDead();
                            storage.insert(xpStack, xpFluid, false);
                        }
                    }
                }
            }
        }

        // 收集流体
        if (hopperFluidMode == HopperFluidMode.ALLOW) {
            fluidCollect(filterMode, filterSlots, storage, world, searchArea);
        }
    }

    @Override
    public int getTicksPerWork(ItemStack stack, World world, Entity holder, int slotId, boolean isSelected) {
        HopperRangeMode hopperRangeMode = getHopperRangeModeOrDefault(stack, HopperRangeMode.RADIUS_MID);
        HopperFluidMode hopperFluidMode = getHopperFluidModeOrDefault(stack, HopperFluidMode.DENY);
        if (hopperFluidMode == HopperFluidMode.ALLOW) {
            switch (hopperRangeMode) {
                case RADIUS_LOWEST:
                case RADIUS_LOW:
                    return 0;
                case RADIUS_MID:
                    return 10;
                case RADIUS_HIGH:
                    return 20;
                case RADIUS_HIGHEST:
                    return 50;
                case CHUNK_MODE:
                    return 1200;
                default:
                    return 10;
            }
        } else {
            switch (hopperRangeMode) {
                case RADIUS_LOWEST:
                case RADIUS_LOW:
                    return 0;
                case RADIUS_MID:
                    return 2;
                case RADIUS_HIGH:
                    return 5;
                case RADIUS_HIGHEST:
                    return 10;
                case CHUNK_MODE:
                    return 1200;
                default:
                    return 2;
            }
        }
    }

    private AxisAlignedBB getSearchArea(HopperRangeMode hopperRangeMode, Entity holder) {
        int posX = MathHelper.floor_double(holder.posX);
        int posY = MathHelper.floor_double(holder.posY);
        int posZ = MathHelper.floor_double(holder.posZ);

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
            return AxisAlignedBB.getBoundingBox(
                posX - radius,
                posY - radius,
                posZ - radius,
                posX + radius,
                posY + radius,
                posZ + radius);
        } else {
            int chunkX = posX >> 4;
            int chunkZ = posZ >> 4;
            int minX = chunkX << 4;
            int maxX = minX + 15;
            int minZ = chunkZ << 4;
            int maxZ = minZ + 15;
            int minY = 0;
            int maxY = worldHeight(holder.worldObj);
            return AxisAlignedBB.getBoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }

    private int worldHeight(World world) {
        return world != null ? world.getHeight() : 256;
    }

    private List<EntityItem> refreshItemEntityCache(HopperNBTMode hopperNBTMode, World world,
        AxisAlignedBB searchArea) {
        @SuppressWarnings("unchecked")
        List<EntityItem> list = world.getEntitiesWithinAABB(EntityItem.class, searchArea);
        List<EntityItem> result = new ArrayList<>();
        for (EntityItem itemEntity : list) {
            if (hopperNBTMode == HopperNBTMode.DENY) {
                if (itemEntity.getEntityItem() != null && !itemEntity.getEntityItem()
                    .hasTagCompound()) {
                    result.add(itemEntity);
                }
            } else {
                result.add(itemEntity);
            }
        }
        return result;
    }

    private List<EntityXPOrb> refreshXpEntityCache(World world, AxisAlignedBB searchArea) {
        @SuppressWarnings("unchecked")
        List<EntityXPOrb> list = world.getEntitiesWithinAABB(EntityXPOrb.class, searchArea);
        return list;
    }

    private void fluidCollect(FilterMode filterMode, List<KeyAmount> filterSlots, UnifiedStorage storage, World world,
        AxisAlignedBB searchArea) {
        int minX = MathHelper.floor_double(searchArea.minX);
        int minY = MathHelper.floor_double(searchArea.minY);
        int minZ = MathHelper.floor_double(searchArea.minZ);
        int maxX = MathHelper.floor_double(searchArea.maxX);
        int maxY = MathHelper.floor_double(searchArea.maxY);
        int maxZ = MathHelper.floor_double(searchArea.maxZ);

        for (int x = minX; x <= maxX; ++x) {
            for (int y = minY; y <= maxY; ++y) {
                for (int z = minZ; z <= maxZ; ++z) {
                    Block block = world.getBlock(x, y, z);
                    if (block == null) continue;

                    Fluid fluid = FluidRegistry.lookupFluidForBlock(block);
                    if (fluid == null) continue;

                    int meta = world.getBlockMetadata(x, y, z);
                    int amount = meta == 0 ? 1000 : 0;
                    if (amount <= 0) continue;

                    FluidStack extracted = new FluidStack(fluid, 1);
                    FluidStackKey fluidKey = new FluidStackKey(extracted);
                    if (!matchesFilter(filterMode, filterSlots, fluidKey)) continue;

                    if (storage.insert(fluidKey, amount, true)
                        .isEmpty()) {
                        storage.insert(fluidKey, amount, false);
                        world.setBlockToAir(x, y, z);
                    }
                }
            }
        }
    }

    private boolean matchesFilter(FilterMode filterMode, List<KeyAmount> filterSlots, IStackKey<?> otherStack) {
        switch (filterMode) {
            case BLACK:
                for (KeyAmount stack : filterSlots) {
                    if (stack.key()
                        .isSame(otherStack)) return false;
                }
                return true;
            case WHITE:
                for (KeyAmount stack : filterSlots) {
                    if (stack.key()
                        .isSame(otherStack)) return true;
                }
                return false;
            case IGNORE:
            default:
                return true;
        }
    }
}
