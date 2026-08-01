package com.wintercogs.beyonddimensions.common.block.entity;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import com.wintercogs.beyonddimensions.api.energy.IEnergyHandler;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.StackHandler;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EmptyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.init.BDItems;
import com.wintercogs.beyonddimensions.common.item.MatterCompressionBall;
import com.wintercogs.beyonddimensions.common.machine.FuzzyMode;
import com.wintercogs.beyonddimensions.common.machine.PopMode;
import com.wintercogs.beyonddimensions.common.machine.RedStoneControlMode;
import com.wintercogs.beyonddimensions.common.menu.NetInterfaceAccess;
import com.wintercogs.beyonddimensions.common.menu.NetInterfaceBaseMenu;
import com.wintercogs.beyonddimensions.common.menu.NetInterfaceSettings;
import com.wintercogs.beyonddimensions.config.CommonConfigRuntime;
import com.wintercogs.beyonddimensions.integration.IntegrationHandlerRegistry;
import com.wintercogs.beyonddimensions.util.BDMath;

/**
 * 网络接口方块实体（1.7.10 移植版）。
 * <p>
 * 1.20.1 原版实现：
 * - 实现 MenuProvider，提供 GUI
 * - 通过 Forge Capability 系统将 stackHandler 暴露给相邻方块
 * - 通过 CapabilityHelper + StackHandlerWrapperHelper 统一抽取相邻方块的物品/流体/能量
 * <p>
 * 1.7.10 适配：
 * - 直接实现 IInventory/IFluidHandler，将调用委托给 stackHandler
 * - popStack 中通过 instanceof 检查相邻 TileEntity 的 IInventory/IFluidHandler/IEnergyHandler
 * - 能量部分使用自定义 IEnergyHandler 接口（对应 CoFH 的 cofh.api.energy.IEnergyHandler）
 * - 移除 CapabilityHelper/LazyOptional 相关代码
 */
public class NetInterfaceBlockEntity extends BaseMachineBlockEntity
    implements NetInterfaceAccess, IInventory, IFluidHandler {

    private static final int capacity = CommonConfigRuntime.interfaceUsableCapacity;

    /** 标记槽位（只由 UI 控制，不存实际物品） */
    private final StackHandler fakeStackHandler = new StackHandler(capacity) {

        @Override
        public void onChange() {
            if (worldObj != null && !worldObj.isRemote) {
                markDirty();
            }
        }
    };

    /** 实际存储槽位 */
    private final StackHandler stackHandler = new StackHandler(capacity) {

        @Override
        public void onChange() {
            if (worldObj != null && !worldObj.isRemote) {
                markDirty();
            }
        }
    };

    private final NetInterfaceSettings settings = new NetInterfaceSettings();

    private int redstoneLevel = 0;

    /** 缓存的相邻 IInventory（物品） */
    private final List<IInventory> neighborInventories = new ArrayList<>();
    /** 与 neighborInventories 一一对应的邻居输入面（邻居看向本方块的方向），ISidedInventory.canInsertItem 按面判定（GT 系） */
    private final List<ForgeDirection> neighborInventoryDirections = new ArrayList<>();
    /** 缓存的相邻 IFluidHandler（流体） */
    private final List<IFluidHandler> neighborFluidHandlers = new ArrayList<>();
    /** 与 neighborFluidHandlers 一一对应的邻居输入面（邻居看向本方块的方向），审计 M4-2 */
    private final List<ForgeDirection> neighborFluidDirections = new ArrayList<>();
    /** 缓存的相邻 IEnergyHandler（能量） */
    private final List<IEnergyHandler> neighborEnergyHandlers = new ArrayList<>();
    /** 缓存对应的能量输入方向（从邻居看向本方块） */
    private final List<ForgeDirection> neighborEnergyDirections = new ArrayList<>();

    /**
     * 缓存的相邻外部处理器提供者（联动模组资源，如 Botania Mana、Mekanism Gas）。
     * 三个列表按相同下标一一对应：provider 处理 neighborExternalTEs.get(i) 这个方块，
     * 插入方向为 neighborExternalDirections.get(i)（从邻居看向本方块）。
     */
    private final List<IntegrationHandlerRegistry.IExternalHandlerProvider> neighborExternalProviders = new ArrayList<>();
    private final List<TileEntity> neighborExternalTEs = new ArrayList<>();
    private final List<ForgeDirection> neighborExternalDirections = new ArrayList<>();

    private boolean needsCapabilityUpdate = true;

    public NetInterfaceBlockEntity() {
        super();
    }

    public StackHandler getStackHandler() {
        return this.stackHandler;
    }

    public StackHandler getFakeStackHandler() {
        return this.fakeStackHandler;
    }

    @Override
    public NetInterfaceSettings getNetInterfaceSettings() {
        return this.settings;
    }

    @Override
    public void setControlMode(RedStoneControlMode controlMode) {
        this.controlMode = controlMode;
    }

    @Override
    public boolean isMenuValid() {
        return !this.isInvalid();
    }

    @Override
    public void onMenuDataChanged() {
        if (this.worldObj != null && !this.worldObj.isRemote) {
            this.markDirty();
            this.worldObj.notifyBlockChange(xCoord, yCoord, zCoord, getBlockType());
        }
    }

    public int getRedstoneLevel() {
        return redstoneLevel;
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
    public int getTicksPerWork() {
        return 9;
    }

    @Override
    public boolean shouldWork() {
        if (worldObj == null) return false;

        // 计算红石信号强度：非空槽位数 / 总槽位数 * 15
        int totalSlots = stackHandler.getSlots();
        int notEmpty = 0;
        for (int i = 0; i < totalSlots; i++) {
            KeyAmount ka = stackHandler.getStackBySlot(i);
            if (!ka.isEmpty() && ka.key() != EmptyStackKey.INSTANCE) {
                notEmpty++;
            }
        }
        int newRedstoneLevel = totalSlots > 0 ? (int) (((float) notEmpty / totalSlots) * 15) : 0;
        if (redstoneLevel != newRedstoneLevel) {
            redstoneLevel = newRedstoneLevel;
            worldObj.notifyBlocksOfNeighborChange(xCoord, yCoord, zCoord, getBlockType());
        }

        return super.shouldWork();
    }

    @Override
    public void workContent() {
        super.workContent();

        if (getNet() != null) {
            if (CommonConfigRuntime.interfaceCanReceiveResource) {
                transferToNet();
            }
            if (CommonConfigRuntime.interfaceCanOutputResource) {
                transferFromNet();
            }
        }

        if (CommonConfigRuntime.interfaceCanPopResource) {
            if (getPopMode() == PopMode.OPEN) {
                updateCapabilityCache();
                popStack();
            }
        }
    }

    // ==================== 网络传输 ====================

    public void transferToNet() {
        NetInterfaceAccess.transferToNet(getNet(), stackHandler, fakeStackHandler, capacity);
    }

    public void transferFromNet() {
        NetInterfaceAccess.transferFromNet(getNet(), stackHandler, fakeStackHandler, capacity, getFuzzyMode());
    }

    // ==================== 弹出物品到相邻方块 ====================

    public void updateCapabilityCache() {
        if (worldObj == null || !needsCapabilityUpdate) return;

        neighborInventories.clear();
        neighborInventoryDirections.clear();
        neighborFluidHandlers.clear();
        neighborFluidDirections.clear();
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

            if (neighbor instanceof IInventory) {
                neighborInventories.add((IInventory) neighbor);
                // 记录邻居输入面（邻居看向本方块），插入物品时按面判定 canInsertItem，
                // 避免 GT 输出槽/临时存储槽被误塞物品（isItemValidForSlot 仅检查 isValidSlot）
                neighborInventoryDirections.add(dir.getOpposite());
            }
            if (neighbor instanceof IFluidHandler) {
                neighborFluidHandlers.add((IFluidHandler) neighbor);
                // 记录邻居输入面（邻居看向本方块），弹出流体时按面填充，避免 UNKNOWN
                // 被"只接受指定面"的机器（GT 系）拒绝导致流体滞留（审计 M4-2）
                neighborFluidDirections.add(dir.getOpposite());
            }
            if (neighbor instanceof IEnergyHandler) {
                neighborEnergyHandlers.add((IEnergyHandler) neighbor);
                neighborEnergyDirections.add(dir.getOpposite());
            }

            // 检查联动模组外部处理器（Botania Mana / Mekanism Gas 等）
            ForgeDirection opposite = dir.getOpposite();
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

    public void setNeedsCapabilityUpdate() {
        needsCapabilityUpdate = true;
    }

    /**
     * 将 stackHandler 中的物品/流体弹出至相邻方块。
     * 1.20.1 中通过 handlerCache + StackHandlerWrapperHelper 统一处理；
     * 1.7.10 中需要分别对 IInventory 和 IFluidHandler 处理。
     */
    public void popStack() {
        // 1. 弹出物品
        for (int i = 0; i < capacity; i++) {
            KeyAmount flag = fakeStackHandler.getStackBySlot(i);
            if (flag.isEmpty()) continue;
            // 仅当 flag 是 ItemStackKey 时，才向相邻 IInventory 弹出
            if (!(flag.key() instanceof ItemStackKey)) continue;

            // fuzzy 模式判断
            KeyAmount current = stackHandler.getStackBySlot(i);
            if (current.isEmpty()) continue;

            if (getFuzzyMode() == FuzzyMode.ENABLE) {
                if (!flag.key()
                    .isSame(current.key())) continue;
            } else {
                if (!flag.key()
                    .isSameTypeSameComponents(current.key())) continue;
            }

            // 向所有相邻 IInventory 尝试插入
            for (int invIndex = 0; invIndex < neighborInventories.size(); invIndex++) {
                if (current.isEmpty()) break;
                IInventory inv = neighborInventories.get(invIndex);
                // 与 neighborInventories 一一对应的邻居输入面（邻居看向本方块），用于 canInsertItem 按面判定
                ForgeDirection side = invIndex < neighborInventoryDirections.size()
                    ? neighborInventoryDirections.get(invIndex)
                    : ForgeDirection.UNKNOWN;
                ItemStack stackToInsert = ((ItemStackKey) current.key())
                    .copyStackWithCount((int) Math.min(current.amount(), Integer.MAX_VALUE));
                if (stackToInsert == null || stackToInsert.stackSize <= 0) continue;

                int remaining = insertIntoInventory(inv, stackToInsert, side);
                int inserted = stackToInsert.stackSize - remaining;
                if (inserted > 0) {
                    stackHandler.extract(i, inserted, false);
                    current = stackHandler.getStackBySlot(i);
                }
            }
        }

        // 2. 弹出流体
        for (int i = 0; i < capacity; i++) {
            KeyAmount flag = fakeStackHandler.getStackBySlot(i);
            if (flag.isEmpty()) continue;
            if (!(flag.key() instanceof FluidStackKey)) continue;

            KeyAmount current = stackHandler.getStackBySlot(i);
            if (current.isEmpty()) continue;

            if (getFuzzyMode() == FuzzyMode.ENABLE) {
                if (!flag.key()
                    .isSame(current.key())) continue;
            } else {
                if (!flag.key()
                    .isSameTypeSameComponents(current.key())) continue;
            }

            FluidStack fluidToInsert = ((FluidStackKey) current.key())
                .copyStackWithCount((int) Math.min(current.amount(), Integer.MAX_VALUE));
            if (fluidToInsert == null || fluidToInsert.amount <= 0) continue;

            for (int h = 0; h < neighborFluidHandlers.size(); h++) {
                if (current.isEmpty()) break;
                IFluidHandler handler = neighborFluidHandlers.get(h);
                // 按邻居输入面填充（与缓存一一对应），UNKNOWN 会被"只接受指定面"的机器拒绝（审计 M4-2）
                ForgeDirection side = h < neighborFluidDirections.size() ? neighborFluidDirections.get(h)
                    : ForgeDirection.UNKNOWN;
                // 1.7.10 IFluidHandler.fill 的第三参数 doFill：true=实际填充，false=仅模拟
                // 此处需要实际填充，必须传 true（原代码传 false 导致流体从接口删除但未加入邻居）
                int filled = handler.fill(side, fluidToInsert, true);
                if (filled <= 0 && side != ForgeDirection.UNKNOWN) {
                    // GT 蒸汽机正面（mMainFacing）不是液体输入面（MTEBasicMachineBronze.isLiquidInput
                    // = side != mMainFacing），具体面被拒时回退 UNKNOWN：GT 外层 BaseMetaTileEntity.fill
                    // 对 side == UNKNOWN 跳过 isLiquidInput/封盖检查（蒸汽注得进，GTModHandler.isSteam
                    // + 蒸汽罐未满即可）；只接受指定面的机器仍会在具体面成功时命中，不受影响
                    filled = handler.fill(ForgeDirection.UNKNOWN, fluidToInsert, true);
                }
                if (filled <= 0) continue;
                stackHandler.extract(i, filled, false);
                current = stackHandler.getStackBySlot(i);
                if (current.isEmpty()) break;
                fluidToInsert = ((FluidStackKey) current.key())
                    .copyStackWithCount((int) Math.min(current.amount(), Integer.MAX_VALUE));
            }
        }

        // 3. 弹出能量
        for (int i = 0; i < capacity; i++) {
            KeyAmount flag = fakeStackHandler.getStackBySlot(i);
            if (flag.isEmpty()) continue;
            if (!(flag.key() instanceof EnergyStackKey)) continue;

            KeyAmount current = stackHandler.getStackBySlot(i);
            if (current.isEmpty()) continue;

            if (getFuzzyMode() == FuzzyMode.ENABLE) {
                if (!flag.key()
                    .isSame(current.key())) continue;
            } else {
                if (!flag.key()
                    .isSameTypeSameComponents(current.key())) continue;
            }

            for (int j = 0; j < neighborEnergyHandlers.size(); j++) {
                if (current.isEmpty()) break;
                IEnergyHandler handler = neighborEnergyHandlers.get(j);
                ForgeDirection inputSide = neighborEnergyDirections.get(j);
                int toSend = BDMath.clampLongToInt(current.amount());
                if (toSend <= 0) break;
                int received = handler.receiveEnergy(inputSide, toSend, false);
                if (received <= 0) continue;
                stackHandler.extract(i, received, false);
                current = stackHandler.getStackBySlot(i);
            }
        }

        // 4. 弹出联动模组资源（Botania Mana / Mekanism Gas 等）
        // 仅处理未被上述三类（Item/Fluid/Energy）覆盖的资源类型
        for (int i = 0; i < capacity; i++) {
            KeyAmount flag = fakeStackHandler.getStackBySlot(i);
            if (flag.isEmpty()) continue;
            IStackKey<?> flagKey = flag.key();
            // 跳过已由前面三类处理的类型
            if (flagKey instanceof ItemStackKey || flagKey instanceof FluidStackKey
                || flagKey instanceof EnergyStackKey) {
                continue;
            }

            KeyAmount current = stackHandler.getStackBySlot(i);
            if (current.isEmpty()) continue;

            if (getFuzzyMode() == FuzzyMode.ENABLE) {
                if (!flagKey.isSame(current.key())) continue;
            } else {
                if (!flagKey.isSameTypeSameComponents(current.key())) continue;
            }

            // 查找匹配该资源类型的 provider（按 typeId 匹配）
            net.minecraft.util.ResourceLocation slotTypeId = current.key()
                .getTypeId();
            for (int j = 0; j < neighborExternalProviders.size(); j++) {
                if (current.isEmpty()) break;
                IntegrationHandlerRegistry.IExternalHandlerProvider provider = neighborExternalProviders.get(j);
                if (provider == null) continue;
                if (!slotTypeId.equals(provider.getStackTypeId())) continue;

                TileEntity neighbor = neighborExternalTEs.get(j);
                ForgeDirection side = neighborExternalDirections.get(j);
                long toSend = current.amount();
                if (toSend <= 0) break;

                long leftover;
                try {
                    leftover = provider.insert(neighbor, current.key(), toSend, false, side);
                } catch (Throwable ignored) {
                    continue;
                }
                if (leftover < 0) leftover = 0;
                long inserted = toSend - leftover;
                if (inserted <= 0) continue;
                stackHandler.extract(i, inserted, false);
                current = stackHandler.getStackBySlot(i);
            }
        }
    }

    /**
     * 将 ItemStack 插入到 IInventory 的所有槽位中，返回剩余未插入的数量。
     * <p>
     * 对齐 1.20.1 源项目经 IItemHandler.insert 仅写入"可插入槽"的语义：对 ISidedInventory
     * 额外按面判定 canInsertItem（与 vanilla 漏斗同款）。GT 机器的输出槽/临时存储槽
     * isItemValidForSlot 仅检查 isValidSlot 会放行，canInsertItem 经 allowPutStack
     * 限定输入槽区间后才会拒绝。
     */
    private static int insertIntoInventory(IInventory inv, ItemStack stack, ForgeDirection side) {
        if (stack == null || stack.stackSize <= 0) return 0;
        int remaining = stack.stackSize;
        boolean sided = inv instanceof ISidedInventory;
        int sideOrdinal = side == null ? ForgeDirection.UNKNOWN.ordinal() : side.ordinal();

        for (int slot = 0; slot < inv.getSizeInventory() && remaining > 0; slot++) {
            if (!inv.isItemValidForSlot(slot, stack)) continue;
            if (sided && !((ISidedInventory) inv).canInsertItem(slot, stack, sideOrdinal)) continue;
            int maxStackSize = Math.min(inv.getInventoryStackLimit(), stack.getMaxStackSize());
            ItemStack existing = inv.getStackInSlot(slot);

            if (existing == null) {
                int toInsert = Math.min(remaining, maxStackSize);
                ItemStack newStack = stack.copy();
                newStack.stackSize = toInsert;
                inv.setInventorySlotContents(slot, newStack);
                inv.markDirty();
                remaining -= toInsert;
            } else if (existing.isItemEqual(stack) && ItemStack.areItemStackTagsEqual(existing, stack)
                && existing.stackSize < maxStackSize) {
                    int canInsert = Math.min(maxStackSize - existing.stackSize, remaining);
                    existing.stackSize += canInsert;
                    inv.setInventorySlotContents(slot, existing);
                    inv.markDirty();
                    remaining -= canInsert;
                }
        }

        return remaining;
    }

    // ==================== 掉落物品 ====================

    public void dropContent() {
        if (worldObj == null || worldObj.isRemote) return;

        List<KeyAmount> dropList = new ArrayList<>();
        for (KeyAmount stack : stackHandler.getStorage()) {
            if (!stack.isEmpty()) {
                // 如果内含物质球，直接弹出，防止 NBT 套娃
                if (stack.key() instanceof ItemStackKey itemStackKey
                    && itemStackKey.getSource() instanceof MatterCompressionBall) {
                    dropItemAtBlock(itemStackKey.copyStackWithCount((int) Math.min(stack.amount(), Integer.MAX_VALUE)));
                } else {
                    dropList.add(stack);
                }
            }
        }

        if (dropList.isEmpty()) return;

        ItemStack ball = new ItemStack(BDItems.MATTER_COMPRESS_BALL, 1, 0);
        MatterCompressionBall.setIStackList(ball, dropList);
        dropItemAtBlock(ball);
    }

    private void dropItemAtBlock(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) return;
        EntityItem entity = new EntityItem(worldObj, xCoord + 0.5, yCoord + 0.5, zCoord + 0.5, stack);
        entity.delayBeforeCanPickup = 10;
        worldObj.spawnEntityInWorld(entity);
    }

    // ==================== NBT 持久化 ====================

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        if (tag.hasKey("inventory")) {
            this.stackHandler.deserializeNBT(tag.getCompoundTag("inventory"));
        }
        if (tag.hasKey("flags")) {
            this.fakeStackHandler.deserializeNBT(tag.getCompoundTag("flags"));
        }

        // 旧数据兼容：pop_mode
        String popModeNew = tag.getString("pop_mode");
        if (popModeNew != null && !popModeNew.isEmpty()) {
            try {
                setPopMode(PopMode.valueOf(popModeNew));
            } catch (IllegalArgumentException ignored) {
                setPopMode(PopMode.STOP);
            }
        } else if (tag.hasKey("popMode") && !tag.getString("popMode")
            .isEmpty()) {
                try {
                    setPopMode(PopMode.valueOf(tag.getString("popMode")));
                } catch (IllegalArgumentException ignored) {
                    setPopMode(PopMode.STOP);
                }
            } else if (tag.hasKey("popMode") && tag.getBoolean("popMode")) {
                setPopMode(PopMode.OPEN);
            } else {
                setPopMode(PopMode.STOP);
            }

        String fuzzyModeNew = tag.getString("fuzzy_mode");
        if (fuzzyModeNew != null && !fuzzyModeNew.isEmpty()) {
            try {
                setFuzzyMode(FuzzyMode.valueOf(fuzzyModeNew));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setTag("inventory", stackHandler.serializeNBT());
        tag.setTag("flags", fakeStackHandler.serializeNBT());
        tag.setString("pop_mode", getPopMode().name());
        tag.setString("fuzzy_mode", getFuzzyMode().name());
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
        neighborInventories.clear();
        neighborInventoryDirections.clear();
        neighborFluidHandlers.clear();
        neighborFluidDirections.clear();
        neighborEnergyHandlers.clear();
        neighborEnergyDirections.clear();
        neighborExternalProviders.clear();
        neighborExternalTEs.clear();
        neighborExternalDirections.clear();
        needsCapabilityUpdate = true;
    }

    // ==================== IInventory（暴露给相邻方块） ====================

    @Override
    public int getSizeInventory() {
        return stackHandler.getSlots();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        KeyAmount ka = stackHandler.getStackBySlot(slot);
        if (ka.isEmpty() || !(ka.key() instanceof ItemStackKey)) return null;
        return ((ItemStackKey) ka.key()).copyStackWithCount((int) Math.min(ka.amount(), Integer.MAX_VALUE));
    }

    @Override
    public ItemStack decrStackSize(int slot, int count) {
        // 槽位已被流体/能量占用时，物品视图应视为"无物品"（外部设备只应操作物品槽），
        // 不得对流体槽执行 extract（否则流体被扣除而设备误以为没东西，造成流体静默丢失）
        KeyAmount current = stackHandler.getStackBySlot(slot);
        if (current.isEmpty() || !(current.key() instanceof ItemStackKey)) return null;
        KeyAmount extracted = stackHandler.extract(slot, count, false);
        if (extracted.isEmpty() || !(extracted.key() instanceof ItemStackKey)) return null;
        return ((ItemStackKey) extracted.key())
            .copyStackWithCount((int) Math.min(extracted.amount(), Integer.MAX_VALUE));
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        return null;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        // 槽位已被流体/能量占用时拒绝物品覆盖（1.7.10 单 IInventory 无 Capability 类型隔离，
        // 外部漏斗/管道看到物品视图空槽会直接 set 覆盖，抹掉槽内流体；源项目经
        // ITEM_HANDLER/FLUID_HANDLER 分离规避。此处以类型守卫防御）
        KeyAmount current = stackHandler.getStackBySlot(slot);
        if (!current.isEmpty() && !(current.key() instanceof ItemStackKey)) {
            return;
        }
        if (stack == null || stack.stackSize <= 0) {
            stackHandler.setStackDirectly(slot, EmptyStackKey.INSTANCE, 0);
        } else {
            stackHandler.setStackDirectly(slot, new ItemStackKey(stack), stack.stackSize);
        }
        markDirty();
    }

    @Override
    public String getInventoryName() {
        return "container.beyonddimensions.net_interface";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return true;
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return stack != null;
    }

    // ==================== IFluidHandler（暴露给相邻方块） ====================

    @Override
    public int fill(ForgeDirection from, FluidStack resource, boolean doFill) {
        if (resource == null || resource.amount <= 0) return 0;
        FluidStackKey key = new FluidStackKey(resource);
        // doFill=true → simulate=false（实际填充）；doFill=false → simulate=true（仅模拟）
        long remaining = stackHandler.insert(key, resource.amount, !doFill)
            .amount();
        long actual = resource.amount - Math.min(remaining, resource.amount);
        if (actual > 0 && doFill) {
            markDirty();
        }
        return (int) Math.min(actual, Integer.MAX_VALUE);
    }

    @Override
    public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain) {
        if (resource == null || resource.amount <= 0) return null;
        FluidStackKey key = new FluidStackKey(resource);
        KeyAmount extracted = stackHandler.extract(key, resource.amount, !doDrain, false);
        if (extracted.isEmpty()) return null;
        if (extracted.amount() > 0 && doDrain) {
            markDirty();
        }
        return ((FluidStackKey) extracted.key())
            .copyStackWithCount((int) Math.min(extracted.amount(), Integer.MAX_VALUE));
    }

    @Override
    public FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain) {
        // 按数量导出时，尝试从第一个流体条目导出
        for (KeyAmount ka : stackHandler.getStorage()) {
            if (ka.isEmpty() || !(ka.key() instanceof FluidStackKey)) continue;
            KeyAmount extracted = stackHandler.extract(ka.key(), maxDrain, !doDrain, false);
            if (extracted.isEmpty()) continue;
            if (extracted.amount() > 0 && doDrain) {
                markDirty();
            }
            return ((FluidStackKey) extracted.key())
                .copyStackWithCount((int) Math.min(extracted.amount(), Integer.MAX_VALUE));
        }
        return null;
    }

    @Override
    public boolean canFill(ForgeDirection from, Fluid fluid) {
        return true;
    }

    @Override
    public boolean canDrain(ForgeDirection from, Fluid fluid) {
        return true;
    }

    @Override
    public FluidTankInfo[] getTankInfo(ForgeDirection from) {
        List<FluidTankInfo> infos = new ArrayList<>();
        for (KeyAmount ka : stackHandler.getStorage()) {
            if (ka.isEmpty() || !(ka.key() instanceof FluidStackKey)) continue;
            FluidStack fs = ((FluidStackKey) ka.key())
                .copyStackWithCount((int) Math.min(ka.amount(), Integer.MAX_VALUE));
            infos.add(new FluidTankInfo(fs, Integer.MAX_VALUE));
        }
        return infos.toArray(new FluidTankInfo[0]);
    }

    // ==================== GUI ====================

    public IChatComponent getDisplayName() {
        return new ChatComponentTranslation("menu.title.beyonddimensions.net_interface_menu");
    }

    public Container createMenu(int containerId, InventoryPlayer inventory, EntityPlayer player) {
        return new NetInterfaceBaseMenu(inventory);
    }

    // markDirty 重写以满足编译器：BaseMachineBlockEntity 已有 markDirty，无需重写
    // 通过 Block 在 onBlockBreak 中调用 dropContent
}
