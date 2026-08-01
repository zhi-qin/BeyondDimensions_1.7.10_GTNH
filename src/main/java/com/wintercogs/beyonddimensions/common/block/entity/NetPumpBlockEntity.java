package com.wintercogs.beyonddimensions.common.block.entity;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.energy.IEnergyHandler;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.StackHandler;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.machine.FilterMode;
import com.wintercogs.beyonddimensions.common.menu.NetPumpMenu;
import com.wintercogs.beyonddimensions.integration.IntegrationHandlerRegistry;
import com.wintercogs.beyonddimensions.util.BDMath;

/**
 * 网络泵方块实体（1.7.10 移植版）。
 * <p>
 * 1.20.1 原版实现：
 * - 通过 CapabilityHelper.BlockCapabilityMap 遍历相邻方块的所有能力
 * - 使用 StackHandlerWrapperHelper 将不同来源（IItemHandler/IFluidHandler/IEnergyStorage）统一包装为
 * IStackHandlerWrapper，再通过统一接口抽取
 * <p>
 * 1.7.10 适配：
 * - 没有 Capability 系统，直接通过 instanceof 检查相邻 TileEntity 的 ISidedInventory / IFluidHandler / IEnergyHandler
 * - 物品抽取仅限 {@link ISidedInventory} 面向泵一侧允许抽出的"输出槽"（{@code canExtractItem} 判定），
 * 普通 IInventory（如箱子，无输入/输出槽概念）不再抽取物品——GTNH 机器输入/输出槽语义，
 * 设计偏离详见 PORTING_DEVIATIONS.md（源项目 1.20.1 会抽取全部 IItemHandler 槽位）
 * - 能量部分使用自定义 IEnergyHandler 接口（对应 CoFH 的 cofh.api.energy.IEnergyHandler）
 * - 抽取逻辑保持一致：模拟插入算出可入网数量 → 从相邻槽位抽出 → 实际插入网络
 */
public class NetPumpBlockEntity extends BaseMachineBlockEntity {

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

    /** 缓存的相邻 ISidedInventory（物品；仅抽取面向泵一侧允许抽出的"输出槽"） */
    private final List<ISidedInventory> neighborInventories = new ArrayList<>();
    /** 缓存的相邻 IFluidHandler（流体） */
    private final List<IFluidHandler> neighborFluidHandlers = new ArrayList<>();
    /** 缓存对应的流体输入方向（从邻居看向本方块，逐面抽取用） */
    private final List<ForgeDirection> neighborFluidDirections = new ArrayList<>();
    /** 缓存对应的输入方向（用于面限制） */
    private final List<ForgeDirection> neighborDirections = new ArrayList<>();
    /** 缓存的相邻 IEnergyHandler（能量） */
    private final List<IEnergyHandler> neighborEnergyHandlers = new ArrayList<>();
    /** 缓存对应的能量输入方向（从邻居看向本方块） */
    private final List<ForgeDirection> neighborEnergyDirections = new ArrayList<>();

    /**
     * 缓存的相邻外部处理器提供者（联动模组资源，如 Botania Mana、Mekanism Gas）。
     * 三个列表按相同下标一一对应：provider 处理 neighborExternalTEs.get(i) 这个方块，
     * 抽取/插入方向为 neighborExternalDirections.get(i)（从邻居看向本方块）。
     */
    private final List<IntegrationHandlerRegistry.IExternalHandlerProvider> neighborExternalProviders = new ArrayList<>();
    private final List<TileEntity> neighborExternalTEs = new ArrayList<>();
    private final List<ForgeDirection> neighborExternalDirections = new ArrayList<>();

    private boolean needsCapabilityUpdate = true;

    public NetPumpBlockEntity() {
        super();
    }

    public StackHandler getFilterSlots() {
        return filterSlots;
    }

    // ==================== BaseMachine 覆写 ====================

    @Override
    public void updateEntity() {
        if (!worldObj.isRemote && worldObj.getTotalWorldTime() % 20 == 0) {
            setNeedsCapabilityUpdate();
        }
        super.updateEntity();
    }

    @Override
    public boolean shouldWork() {
        return super.shouldWork() && getNet() != null;
    }

    @Override
    public int getTicksPerWork() {
        return 10;
    }

    @Override
    public void workStart() {
        if (worldObj == null || !needsCapabilityUpdate) return;

        neighborInventories.clear();
        neighborFluidHandlers.clear();
        neighborFluidDirections.clear();
        neighborDirections.clear();
        neighborEnergyHandlers.clear();
        neighborEnergyDirections.clear();
        neighborExternalProviders.clear();
        neighborExternalTEs.clear();
        neighborExternalDirections.clear();

        // 预取联动模组提供者列表（无联动模组加载时为空，不影响性能）
        List<IntegrationHandlerRegistry.IExternalHandlerProvider> providers = IntegrationHandlerRegistry.getProviders();

        for (ForgeDirection dir : ForgeDirection.values()) {
            if (dir == ForgeDirection.UNKNOWN) continue;
            int tx = xCoord + dir.offsetX;
            int ty = yCoord + dir.offsetY;
            int tz = zCoord + dir.offsetZ;
            TileEntity neighbor = worldObj.getTileEntity(tx, ty, tz);
            if (neighbor == null || neighbor instanceof NetedBlockEntity) continue;

            // 相对方向：从邻居看向本方块
            ForgeDirection opposite = dir.getOpposite();

            // 物品仅缓存 ISidedInventory：GTNH 机器有输入/输出槽之分，抽取限定在
            // 面向泵一侧允许抽出的"输出槽"（由 workContent 的 canExtractItem 判定）；
            // 普通 IInventory（如箱子，无输入/输出槽概念）不再被抽取物品。
            if (neighbor instanceof ISidedInventory) {
                neighborInventories.add((ISidedInventory) neighbor);
                neighborDirections.add(opposite);
            }
            if (neighbor instanceof IFluidHandler) {
                neighborFluidHandlers.add((IFluidHandler) neighbor);
                neighborFluidDirections.add(opposite);
            }
            if (neighbor instanceof IEnergyHandler) {
                neighborEnergyHandlers.add((IEnergyHandler) neighbor);
                neighborEnergyDirections.add(opposite);
            }

            // 检查联动模组外部处理器（Botania Mana / Mekanism Gas 等）
            // 同一 TE 可能被多个 provider 匹配（理论情况），全部缓存
            for (IntegrationHandlerRegistry.IExternalHandlerProvider provider : providers) {
                try {
                    if (provider.matches(neighbor)) {
                        neighborExternalProviders.add(provider);
                        neighborExternalTEs.add(neighbor);
                        neighborExternalDirections.add(opposite);
                    }
                } catch (Throwable ignored) {
                    // 联动模组实现异常时安全跳过，避免影响主功能
                }
            }
        }

        needsCapabilityUpdate = false;
    }

    @Override
    public void workContent() {
        DimensionsNet net = getNet();
        if (net == null) return;
        UnifiedStorage storage = net.getUnifiedStorage();

        // 1. 抽取物品（仅抽取面向泵一侧允许抽出的"输出槽"）
        for (int i = 0; i < neighborInventories.size(); i++) {
            ISidedInventory inv = neighborInventories.get(i);
            if (inv == null) continue;
            // 邻居看向本方块的方向（ISidedInventory 的 side 参数语义）
            ForgeDirection extractSide = neighborDirections.get(i);
            for (int slot = 0; slot < inv.getSizeInventory(); slot++) {
                ItemStack stack = inv.getStackInSlot(slot);
                if (stack == null || stack.stackSize <= 0) continue;
                // GTNH 语义：输入槽（canExtractItem=false）不被抽取，仅输出槽可被抽出
                if (!inv.canExtractItem(slot, stack, extractSide.ordinal())) continue;

                IStackKey<?> itemKey = new ItemStackKey(stack);
                if (!matchesFilter(itemKey)) continue;

                // 模拟插入算可入网数量
                long canInsert = stack.stackSize - storage.insert(itemKey, stack.stackSize, true)
                    .amount();
                canInsert = Math.min(canInsert, stack.stackSize);
                if (canInsert <= 0) continue;

                int toExtract = BDMath.clampLongToInt(canInsert);
                ItemStack extracted = inv.decrStackSize(slot, toExtract);
                if (extracted == null || extracted.stackSize <= 0) continue;

                storage.insert(new ItemStackKey(extracted), extracted.stackSize, false);
                inv.markDirty();
            }
        }

        // 2. 抽取流体
        for (int i = 0; i < neighborFluidHandlers.size(); i++) {
            IFluidHandler handler = neighborFluidHandlers.get(i);
            if (handler == null) continue;
            // 逐面访问（从邻居看向本方块），对齐物品/能量/联动资源的按面追踪；
            // 用 UNKNOWN 会被只接受指定面的机器（GT 系）拒绝导致流体滞留
            ForgeDirection extractSide = neighborFluidDirections.get(i);
            // 先获取所有 tank 信息，按 tank 尝试抽取
            FluidTankInfo[] infos;
            try {
                infos = handler.getTankInfo(extractSide);
            } catch (Throwable ignored) {
                infos = null;
            }
            if (infos == null) continue;

            for (FluidTankInfo info : infos) {
                if (info == null || info.fluid == null || info.fluid.amount <= 0) continue;
                FluidStackKey fluidKey = new FluidStackKey(info.fluid);
                if (!matchesFilter(fluidKey)) continue;

                long canInsert = info.fluid.amount - storage.insert(fluidKey, info.fluid.amount, true)
                    .amount();
                canInsert = Math.min(canInsert, info.fluid.amount);
                if (canInsert <= 0) continue;

                int toDrain = BDMath.clampLongToInt(canInsert);
                // 1.7.10 IFluidHandler.drain 的第三参数 doDrain：true=实际抽取，false=仅模拟
                // 此处需要实际抽取，必须传 true（原代码传 false 导致流体被复制而非转移）
                FluidStack drained = handler.drain(extractSide, toDrain, true);
                if (drained == null || drained.amount <= 0) continue;

                storage.insert(new FluidStackKey(drained), drained.amount, false);
            }
        }

        // 3. 抽取能量
        for (int i = 0; i < neighborEnergyHandlers.size(); i++) {
            IEnergyHandler handler = neighborEnergyHandlers.get(i);
            if (handler == null) continue;
            if (!matchesFilter(EnergyStackKey.INSTANCE)) continue;

            // 从邻居视角看，被抽取的方向为 neighborEnergyDirections.get(i)
            ForgeDirection extractSide = neighborEnergyDirections.get(i);
            int stored = handler.getEnergyStored(extractSide);
            if (stored <= 0) continue;

            // 模拟插入算可入网数量
            long canInsert = stored - storage.insert(EnergyStackKey.INSTANCE, stored, true)
                .amount();
            canInsert = Math.min(canInsert, stored);
            if (canInsert <= 0) continue;

            int toExtract = BDMath.clampLongToInt(canInsert);
            int extracted = handler.extractEnergy(extractSide, toExtract, false);
            if (extracted <= 0) continue;

            storage.insert(EnergyStackKey.INSTANCE, extracted, false);
        }

        // 4. 抽取联动模组资源（Botania Mana / Mekanism Gas 等）
        // 通过 IntegrationHandlerRegistry 注册的外部处理器提供者桥接
        for (int i = 0; i < neighborExternalProviders.size(); i++) {
            IntegrationHandlerRegistry.IExternalHandlerProvider provider = neighborExternalProviders.get(i);
            TileEntity neighbor = neighborExternalTEs.get(i);
            ForgeDirection side = neighborExternalDirections.get(i);
            if (provider == null || neighbor == null) continue;

            List<KeyAmount> extractable;
            try {
                extractable = provider.getExtractableContents(neighbor, side);
            } catch (Throwable ignored) {
                continue;
            }
            if (extractable == null || extractable.isEmpty()) continue;

            for (KeyAmount ka : extractable) {
                if (ka == null || ka.isEmpty() || ka.amount() <= 0) continue;
                if (!matchesFilter(ka.key())) continue;

                // 模拟插入算可入网数量
                long canInsert = ka.amount() - storage.insert(ka.key(), ka.amount(), true)
                    .amount();
                canInsert = Math.min(canInsert, ka.amount());
                if (canInsert <= 0) continue;

                KeyAmount extracted;
                try {
                    extracted = provider.extract(neighbor, ka.key(), canInsert, false, side);
                } catch (Throwable ignored) {
                    continue;
                }
                if (extracted == null || extracted.isEmpty() || extracted.amount() <= 0) continue;

                storage.insert(extracted.key(), extracted.amount(), false);
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

    public void setNeedsCapabilityUpdate() {
        needsCapabilityUpdate = true;
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
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setTag("filter_slots", filterSlots.serializeNBT());
        tag.setString("filter_type", filterMode.name());
    }

    // ==================== 生命周期 ====================

    @Override
    public void validate() {
        super.validate();
        setNeedsCapabilityUpdate();
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        // 卸载时清空缓存，避免持有世界卸载后的 TE 引用
        neighborInventories.clear();
        neighborFluidHandlers.clear();
        neighborFluidDirections.clear();
        neighborDirections.clear();
        neighborEnergyHandlers.clear();
        neighborEnergyDirections.clear();
        neighborExternalProviders.clear();
        neighborExternalTEs.clear();
        neighborExternalDirections.clear();
        needsCapabilityUpdate = true;
    }

    // ==================== GUI ====================

    public IChatComponent getDisplayName() {
        return new ChatComponentTranslation("menu.title.beyonddimensions.pump_menu");
    }

    public Container createMenu(int containerId, InventoryPlayer inventory, EntityPlayer player) {
        return new NetPumpMenu(inventory);
    }
}
