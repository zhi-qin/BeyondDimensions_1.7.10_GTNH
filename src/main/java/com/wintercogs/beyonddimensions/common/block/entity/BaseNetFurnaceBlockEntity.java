package com.wintercogs.beyonddimensions.common.block.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;

import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.StackHandler;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.init.BDItems;
import com.wintercogs.beyonddimensions.common.item.MatterCompressionBall;
import com.wintercogs.beyonddimensions.common.machine.AutoSortMode;
import com.wintercogs.beyonddimensions.common.machine.FurnaceRecipeType;
import com.wintercogs.beyonddimensions.common.machine.PopMode;
import com.wintercogs.beyonddimensions.common.machine.ReceiveMode;
import com.wintercogs.beyonddimensions.common.menu.NetFurnaceMenu;

/**
 * 网络熔炉基类（1.7.10 移植版）。
 * <p>
 * 支持同时处理 9 个熔炼槽位，1 个燃料槽位，8 个输入/燃料标记槽位。
 * 燃料类型支持物品、能量（FE）、熔岩流体。
 * 支持弹出模式（PopMode）、接收模式（ReceiveMode）、自动整理模式（AutoSortMode）。
 */
public abstract class BaseNetFurnaceBlockEntity extends BaseMachineBlockEntity {

    /** 同时处理的任务格数 */
    private static final int capacity = 9;

    public int getCapacity() {
        return capacity;
    }

    /** 同时能用的标记格数 */
    private static final int filterCapacity = 8;

    public int getFilterCapacity() {
        return filterCapacity;
    }

    /** 燃料槽个数 */
    private static final int fuelCapacity = 1;

    public int getFuelCapacity() {
        return fuelCapacity;
    }

    /** 是否弹出输出物 */
    public PopMode popMode = PopMode.STOP;

    /** 是否将输出物送回网络 */
    public ReceiveMode receiveMode = ReceiveMode.STOP;

    /** 自动整理内容物 */
    public AutoSortMode sortMode = AutoSortMode.STOP;

    private final FurnaceRecipeType recipeType;
    private final String displayNameKey;

    /** 槽位剩余燃烧 tick */
    private List<Integer> litTime = new ArrayList<>(Collections.nCopies(capacity, 0));

    public List<Integer> getLitTime() {
        return litTime;
    }

    public void setLitTime(List<Integer> litTime) {
        this.litTime = litTime;
    }

    /** 槽位燃料总 tick */
    private List<Integer> litDuration = new ArrayList<>(Collections.nCopies(capacity, 0));

    public List<Integer> getLitDuration() {
        return litDuration;
    }

    public void setLitDuration(List<Integer> litDuration) {
        this.litDuration = litDuration;
    }

    /** 槽位为此次配方燃烧的 tick */
    private List<Integer> cookTime = new ArrayList<>(Collections.nCopies(capacity, 0));

    public List<Integer> getCookTime() {
        return cookTime;
    }

    public void setCookTime(List<Integer> cookTime) {
        this.cookTime = cookTime;
    }

    /** 槽位配方所需 tick */
    private List<Integer> cookTimeTotal = new ArrayList<>(Collections.nCopies(capacity, 0));

    public List<Integer> getCookTimeTotal() {
        return cookTimeTotal;
    }

    public void setCookTimeTotal(List<Integer> cookTimeTotal) {
        this.cookTimeTotal = cookTimeTotal;
    }

    /** 输入标记 */
    private final StackHandler inputFilterSlots = new StackHandler(filterCapacity) {

        @Override
        public void onChange() {
            if (worldObj != null && !worldObj.isRemote) markDirty();
        }

        @Override
        public boolean isStackValid(int slot, IStackKey<?> key) {
            if (!(key instanceof ItemStackKey itemKey)) return false;
            if (worldObj == null) return false;
            ItemStack inputStack = itemKey.getReadOnlyStack();
            return inputStack != null && FurnaceRecipes.smelting()
                .getSmeltingResult(inputStack) != null;
        }
    };

    public StackHandler getInputFilterSlots() {
        return inputFilterSlots;
    }

    /** 燃料标记 */
    private final StackHandler fuelFilterSlots = new StackHandler(filterCapacity) {

        @Override
        public void onChange() {
            if (worldObj != null && !worldObj.isRemote) markDirty();
        }

        @Override
        public boolean isStackValid(int slot, IStackKey<?> key) {
            if (key instanceof EnergyStackKey) return true;
            if (key instanceof FluidStackKey fluidKey) {
                return getFluidBurnTicksPer1000Mb(fluidKey.getSource()) > 0;
            }
            if (key instanceof ItemStackKey itemKey) {
                if (worldObj == null) return false;
                ItemStack fuelStack = itemKey.getReadOnlyStack();
                return fuelStack != null && TileEntityFurnace.getItemBurnTime(fuelStack) > 0;
            }
            return false;
        }
    };

    public StackHandler getFuelFilterSlots() {
        return fuelFilterSlots;
    }

    /** 输入存储 */
    private final StackHandler inputStorageSlots = new StackHandler(capacity) {

        @Override
        public void onChange() {
            if (worldObj != null && !worldObj.isRemote) markDirty();
        }

        @Override
        public boolean isStackValid(int slot, IStackKey<?> key) {
            if (!(key instanceof ItemStackKey itemKey)) return false;
            if (worldObj == null) return false;
            ItemStack inputStack = itemKey.getReadOnlyStack();
            return inputStack != null && FurnaceRecipes.smelting()
                .getSmeltingResult(inputStack) != null;
        }
    };

    public StackHandler getInputStorageSlots() {
        return inputStorageSlots;
    }

    /** 输出存储 */
    private final StackHandler outputStorageSlots = new StackHandler(capacity) {

        @Override
        public void onChange() {
            if (worldObj != null && !worldObj.isRemote) markDirty();
        }
    };

    public StackHandler getOutputStorageSlots() {
        return outputStorageSlots;
    }

    /** 燃料存储 */
    private final StackHandler fuelStorageSlots = new StackHandler(fuelCapacity) {

        @Override
        public void onChange() {
            if (worldObj != null && !worldObj.isRemote) markDirty();
        }

        @Override
        public boolean isStackValid(int slot, IStackKey<?> key) {
            if (key instanceof EnergyStackKey) return true;
            if (key instanceof FluidStackKey fluidKey) {
                return getFluidBurnTicksPer1000Mb(fluidKey.getSource()) > 0;
            }
            if (key instanceof ItemStackKey itemKey) {
                if (worldObj == null) return false;
                ItemStack fuelStack = itemKey.getReadOnlyStack();
                return fuelStack != null && TileEntityFurnace.getItemBurnTime(fuelStack) > 0;
            }
            return false;
        }
    };

    public StackHandler getFuelStorageSlots() {
        return fuelStorageSlots;
    }

    /** 燃料返回物存储 */
    private final StackHandler fuelReturnSlots = new StackHandler(fuelCapacity) {

        @Override
        public void onChange() {
            if (worldObj != null && !worldObj.isRemote) markDirty();
        }
    };

    public StackHandler getFuelReturnSlots() {
        return fuelReturnSlots;
    }

    /**
     * 流体燃料每 1000mB（一桶）的燃烧 tick 注册表（1.7.10 扩展，原版仅熔岩）。
     * 杂酚油对齐 GTNH 设定：1000mB = 6400 tick = 32 个物品
     * （GTNH pack commit 6f25b10：furnace.setFuel(<Railcraft:fluid.creosote.bucket>, 6400)）。
     * 熔岩保持原版行为：1000mB = 20000 tick。
     */
    private static final Map<String, Integer> FLUID_BURN_TICKS_PER_1000MB = new HashMap<>();

    static {
        FLUID_BURN_TICKS_PER_1000MB.put("lava", 20000);
        FLUID_BURN_TICKS_PER_1000MB.put("creosote", 6400); // GT 流体
        FLUID_BURN_TICKS_PER_1000MB.put("creosote.oil", 6400); // Railcraft 流体
    }

    /** 查询流体每 1000mB 的燃烧 tick；未注册（不可燃烧）的流体返回 0。 */
    private static int getFluidBurnTicksPer1000Mb(Fluid fluid) {
        if (fluid == null) return 0;
        Integer ticks = FLUID_BURN_TICKS_PER_1000MB.get(fluid.getName());
        return ticks != null ? ticks : 0;
    }

    public BaseNetFurnaceBlockEntity(FurnaceRecipeType recipeType, String displayNameKey) {
        super();
        this.recipeType = recipeType;
        this.displayNameKey = displayNameKey;
    }

    @Override
    public int getTicksPerWork() {
        return 1;
    }

    @Override
    public boolean shouldWork() {
        if (worldObj == null) return false;

        // 无论是否工作，总是先降低燃料持续时间
        for (int i = 0; i < litTime.size(); i++) {
            litTime.set(i, Math.max(0, litTime.get(i) - 1));
        }
        // 更新方块状态
        setLit(
            !litTime.stream()
                .allMatch(t -> t <= 0));

        // 总是保存区块
        markDirty();

        // 输入槽为空 并且 标记槽无物品，可以判为无工作意图
        return super.shouldWork() && (!inputStorageSlots.isEmpty() || !inputFilterSlots.isEmpty()
            || !outputStorageSlots.isEmpty()
            || !fuelReturnSlots.isEmpty()
            || !fuelStorageSlots.isEmpty()
            || !fuelFilterSlots.isEmpty());
    }

    @Override
    public void workStart() {
        super.workStart();

        var net = getNet();
        UnifiedStorage storage = net == null ? null : net.getUnifiedStorage();
        if (storage != null) {
            // 1.尝试按照标记槽位从网络抽取原料
            for (int inputSlot = 0; inputSlot < capacity; inputSlot++) {
                if (!inputStorageSlots.getStackBySlot(inputSlot)
                    .isEmpty()) continue;

                for (KeyAmount filterStack : inputFilterSlots.getStorage()) {
                    if (!inputStorageSlots.getStackBySlot(inputSlot)
                        .isEmpty()) break; // 如果已经插入过则直接跳过
                    if (!(filterStack.key() instanceof ItemStackKey filterItem) || filterItem.isEmpty()) continue;

                    KeyAmount extracted = storage
                        .extract(filterItem, filterItem.getVanillaMaxStackSize(), false, false);
                    if (extracted.isEmpty()) continue;

                    KeyAmount remaining = inputStorageSlots
                        .insert(inputSlot, extracted.key(), extracted.amount(), false);
                    if (!remaining.isEmpty()) {
                        storage.insert(remaining.key(), remaining.amount(), false);
                    }
                }
            }
            // 2.如果开启了自动整理，则每tick进行一次快速整理
            if (sortMode == AutoSortMode.OPEN) {
                long[] amounts = new long[capacity];
                Map<IStackKey<?>, List<Integer>> groupSlots = new HashMap<>();
                Map<IStackKey<?>, Long> groupTotal = new HashMap<>();
                List<Integer> emptySlots = new ArrayList<>();

                for (int i = 0; i < capacity; i++) {
                    KeyAmount stack = inputStorageSlots.getStackBySlot(i);
                    if (stack.isEmpty()) {
                        emptySlots.add(i);
                        continue;
                    }

                    IStackKey<?> key = stack.key();
                    long amount = stack.amount();
                    amounts[i] = amount;
                    groupSlots.computeIfAbsent(key, k -> new ArrayList<>())
                        .add(i);
                    groupTotal.put(key, groupTotal.getOrDefault(key, 0L) + amount);
                }

                for (Map.Entry<IStackKey<?>, List<Integer>> entry : groupSlots.entrySet()) {
                    List<Integer> typedSlots = entry.getValue();
                    long total = groupTotal.get(entry.getKey());

                    int k = (int) Math.min(total, typedSlots.size() + emptySlots.size());

                    while (typedSlots.size() < k && !emptySlots.isEmpty()) {
                        int idx = emptySlots.remove(emptySlots.size() - 1);
                        typedSlots.add(idx);
                    }

                    long base = total / k;
                    int extra = (int) (total % k);

                    int surplusPtr = 0, deficitPtr = 0;
                    while (true) {
                        while (surplusPtr < k) {
                            int idx = typedSlots.get(surplusPtr);
                            long target = base + (surplusPtr < extra ? 1 : 0);
                            if (amounts[idx] > target) break;
                            surplusPtr++;
                        }

                        while (deficitPtr < k) {
                            int idx = typedSlots.get(deficitPtr);
                            long target = base + (deficitPtr < extra ? 1 : 0);
                            if (amounts[idx] < target) break;
                            deficitPtr++;
                        }

                        if (surplusPtr >= k || deficitPtr >= k) break;

                        int from = typedSlots.get(surplusPtr);
                        int to = typedSlots.get(deficitPtr);

                        long surplus = amounts[from] - (base + (surplusPtr < extra ? 1 : 0));
                        long deficit = (base + (deficitPtr < extra ? 1 : 0)) - amounts[to];
                        long move = Math.min(surplus, deficit);

                        KeyAmount moved = inputStorageSlots.extract(from, move, false);
                        KeyAmount leftover = inputStorageSlots.insert(to, moved.key(), moved.amount(), false);
                        if (!leftover.isEmpty()) {
                            inputStorageSlots.insert(from, leftover.key(), leftover.amount(), false);
                            break;
                        }

                        amounts[from] -= move;
                        amounts[to] += move;
                    }
                }
            }
            // 3.尝试按燃料标记从网络抽取燃料
            for (int fuelSlot = 0; fuelSlot < fuelCapacity; fuelSlot++) {
                if (!fuelStorageSlots.getStackBySlot(fuelSlot)
                    .isEmpty()) continue;

                for (KeyAmount filterStack : fuelFilterSlots.getStorage()) {
                    if (filterStack.isEmpty()) continue;
                    if (!fuelStorageSlots.getStackBySlot(fuelSlot)
                        .isEmpty()) break;

                    KeyAmount extracted = storage.extract(
                        filterStack.key(),
                        filterStack.key()
                            .getVanillaMaxStackSize(),
                        false,
                        false);
                    if (extracted.isEmpty()) continue;

                    KeyAmount remaining = fuelStorageSlots.insert(fuelSlot, extracted.key(), extracted.amount(), false);
                    if (!remaining.isEmpty()) {
                        storage.insert(remaining.key(), remaining.amount(), false);
                    }
                }
            }
        }
        // 4.尝试将燃料分配到燃烧时间
        for (int litSlot = 0; litSlot < capacity; litSlot++) {
            if (litTime.get(litSlot) > 0 || inputStorageSlots.getStackBySlot(litSlot)
                .isEmpty()) continue;

            for (KeyAmount fuelStack : fuelStorageSlots.getStorage()) {
                if (fuelStack.isEmpty()) continue;

                IStackKey<?> fuelKey = fuelStack.key();
                if (fuelKey instanceof EnergyStackKey) {
                    int burnTime = (int) Math.min(fuelStack.amount(), 20000);
                    if (burnTime <= 0) continue;

                    fuelStorageSlots.extract(fuelKey, burnTime, false, false);
                    litTime.set(litSlot, burnTime);
                    litDuration.set(litSlot, burnTime);
                } else if (fuelKey instanceof FluidStackKey fuelFluid) {
                    int burnTicksPer1000Mb = getFluidBurnTicksPer1000Mb(fuelFluid.getSource());
                    if (burnTicksPer1000Mb <= 0) continue;

                    int burnNum = (int) Math.min(fuelStack.amount(), 1000);
                    int burnTime = burnNum * burnTicksPer1000Mb / 1000;
                    if (burnTime <= 0) continue;

                    fuelStorageSlots.extract(fuelFluid, burnNum, false, false);
                    litTime.set(litSlot, burnTime);
                    litDuration.set(litSlot, burnTime);
                } else if (fuelKey instanceof ItemStackKey fuelItem) {
                    int burnTime = TileEntityFurnace.getItemBurnTime(fuelItem.getReadOnlyStack());
                    if (burnTime <= 0) continue;

                    ItemStack fuelReadOnly = fuelItem.getReadOnlyStack();
                    ItemStack returnItem = null;
                    if (fuelReadOnly.getItem()
                        .hasContainerItem(fuelReadOnly)) {
                        returnItem = fuelReadOnly.getItem()
                            .getContainerItem(fuelReadOnly);
                    }
                    if (returnItem == null) {
                        fuelStorageSlots.extract(fuelItem, 1, false, false);
                        litTime.set(litSlot, burnTime);
                        litDuration.set(litSlot, burnTime);
                        continue;
                    }

                    IStackKey<?> returnKey = new ItemStackKey(returnItem);
                    int returnCount = returnItem.stackSize;
                    if (!fuelReturnSlots.insert(returnKey, returnCount, true)
                        .isEmpty()) {
                        litTime.set(litSlot, 0);
                        litDuration.set(litSlot, 0);
                        continue;
                    }

                    fuelReturnSlots.insert(returnKey, returnCount, false);
                    fuelStorageSlots.extract(fuelItem, 1, false, false);
                    litTime.set(litSlot, burnTime);
                    litDuration.set(litSlot, burnTime);
                }
            }
        }
    }

    @Override
    public void workContent() {
        super.workContent();
        if (worldObj == null) return;

        // 开始熔炼
        for (int inputSlot = 0; inputSlot < capacity; inputSlot++) {
            if (litTime.get(inputSlot) <= 0) continue;

            KeyAmount inputStack = inputStorageSlots.getStackBySlot(inputSlot);
            if (!(inputStack.key() instanceof ItemStackKey inputItem) || inputItem.isEmpty()) {
                cookTime.set(inputSlot, 0);
                cookTimeTotal.set(inputSlot, 0);
                continue;
            }

            ItemStack inputReadOnly = inputItem.getReadOnlyStack();
            ItemStack resultItem = FurnaceRecipes.smelting()
                .getSmeltingResult(inputReadOnly);
            if (resultItem == null) {
                cookTime.set(inputSlot, 0);
                cookTimeTotal.set(inputSlot, 0);
                continue;
            }

            int totalCookTime = recipeType.getCookTime();
            cookTimeTotal.set(inputSlot, totalCookTime);
            if (cookTime.get(inputSlot) < totalCookTime) {
                cookTime.set(inputSlot, cookTime.get(inputSlot) + 1);
                continue;
            }

            ItemStackKey resultKey = new ItemStackKey(resultItem);
            int resultCount = resultItem.stackSize;

            // 如果能完全输出，则输出，并重设熔炼时间
            if (!outputStorageSlots.insert(inputSlot, resultKey, resultCount, true)
                .isEmpty()) continue;

            outputStorageSlots.insert(inputSlot, resultKey, resultCount, false);
            inputStorageSlots.extract(inputSlot, 1, false);
            cookTime.set(inputSlot, 0);
            cookTimeTotal.set(inputSlot, totalCookTime);
        }
    }

    @Override
    public void workEnd() {
        super.workEnd();
        if (worldObj == null || worldObj.isRemote) return;

        ArrayList<IInventory> otherStorages = new ArrayList<>();
        if (popMode == PopMode.OPEN) {
            for (ForgeDirection dir : ForgeDirection.values()) {
                if (dir == ForgeDirection.UNKNOWN) continue;
                int targetX = xCoord + dir.offsetX;
                int targetY = yCoord + dir.offsetY;
                int targetZ = zCoord + dir.offsetZ;
                TileEntity neighbor = worldObj.getTileEntity(targetX, targetY, targetZ);
                if (neighbor == null || neighbor instanceof NetedBlockEntity) continue;
                if (neighbor instanceof IInventory) {
                    otherStorages.add((IInventory) neighbor);
                }
            }
        }

        var net = receiveMode == ReceiveMode.OPEN ? getNet() : null;
        UnifiedStorage storage = net == null ? null : net.getUnifiedStorage();

        // 输出槽处理
        for (int outputSlot = 0; outputSlot < capacity; outputSlot++) {
            KeyAmount outputStack = outputStorageSlots.getStackBySlot(outputSlot);
            if (outputStack.isEmpty()) continue;

            // 弹出模式
            for (IInventory otherStorage : otherStorages) {
                for (int otherSlot = 0; otherSlot < otherStorage.getSizeInventory(); otherSlot++) {
                    KeyAmount extracted = outputStorageSlots.extract(
                        outputSlot,
                        outputStack.key()
                            .getVanillaMaxStackSize(),
                        false);
                    if (!(extracted.key() instanceof ItemStackKey)) continue;

                    ItemStack stackToInsert = (ItemStack) extracted.toStack();
                    int remaining = insertIntoInventorySlot(otherStorage, otherSlot, stackToInsert);
                    if (remaining > 0) {
                        outputStorageSlots.insert(outputSlot, extracted.key(), remaining, false);
                    }
                }
            }

            // 转移至网络
            if (storage != null) {
                KeyAmount extracted = outputStorageSlots.extract(outputSlot, outputStack.amount(), false);
                if (extracted.isEmpty()) continue;

                KeyAmount remaining = storage.insert(extracted.key(), extracted.amount(), false);
                if (!remaining.isEmpty()) {
                    outputStorageSlots.insert(outputSlot, remaining.key(), remaining.amount(), false);
                }
            }
        }

        // 燃料返回槽处理
        for (int returnSlot = 0; returnSlot < fuelCapacity; returnSlot++) {
            KeyAmount returnStack = fuelReturnSlots.getStackBySlot(returnSlot);
            if (returnStack.isEmpty()) continue;

            // 弹出模式
            for (IInventory otherStorage : otherStorages) {
                for (int otherSlot = 0; otherSlot < otherStorage.getSizeInventory(); otherSlot++) {
                    KeyAmount extracted = fuelReturnSlots.extract(
                        returnSlot,
                        returnStack.key()
                            .getVanillaMaxStackSize(),
                        false);
                    if (!(extracted.key() instanceof ItemStackKey)) continue;

                    ItemStack stackToInsert = (ItemStack) extracted.toStack();
                    int remaining = insertIntoInventorySlot(otherStorage, otherSlot, stackToInsert);
                    if (remaining > 0) {
                        fuelReturnSlots.insert(returnSlot, extracted.key(), remaining, false);
                    }
                }
            }

            // 转移至网络
            if (storage != null) {
                KeyAmount extracted = fuelReturnSlots.extract(returnSlot, returnStack.amount(), false);
                if (extracted.isEmpty()) continue;

                KeyAmount remaining = storage.insert(extracted.key(), extracted.amount(), false);
                if (!remaining.isEmpty()) {
                    fuelReturnSlots.insert(returnSlot, remaining.key(), remaining.amount(), false);
                }
            }
        }

        // 燃料槽处理-如果开始接收模式，在不标记能量时，将能量或流体等不方便存取的堆叠收回网络
        for (int fuelSlot = 0; fuelSlot < fuelCapacity; fuelSlot++) {
            KeyAmount fuelStack = fuelStorageSlots.getStackBySlot(fuelSlot);
            if (fuelStack.isEmpty()) continue;
            if (!(fuelStack.key() instanceof EnergyStackKey || fuelStack.key() instanceof FluidStackKey)) continue;
            if (storage == null || fuelFilterSlots.hasStack(fuelStack.key())) continue;

            KeyAmount extracted = fuelStorageSlots.extract(fuelSlot, fuelStack.amount(), false);
            if (extracted.isEmpty()) continue;

            KeyAmount remaining = storage.insert(extracted.key(), extracted.amount(), false);
            if (!remaining.isEmpty()) {
                fuelStorageSlots.insert(fuelSlot, remaining.key(), remaining.amount(), false);
            }
        }
    }

    public void dropContent() {
        if (worldObj == null || worldObj.isRemote) return;

        List<KeyAmount> dropList = new ArrayList<>();
        // 对齐 1.20.1 源项目：仅弹出 4 个存储 handler，不含过滤器槽位。
        // 过滤器槽位（inputFilterSlots/fuelFilterSlots）是幽灵槽位，玩家设置标记时物品不被消耗，
        // 若弹出会造成物品复制。
        StackHandler[] handlers = { inputStorageSlots, outputStorageSlots, fuelStorageSlots, fuelReturnSlots };
        for (StackHandler handler : handlers) {
            for (KeyAmount stack : handler.getStorage()) {
                if (stack.isEmpty()) continue;

                if (stack.key() instanceof ItemStackKey itemKey
                    && itemKey.getSource() instanceof MatterCompressionBall) {
                    dropItemAtBlock(itemKey.copyStackWithCount(stack.amount()));
                    continue;
                }

                dropList.add(stack);
            }
        }

        if (dropList.isEmpty()) return;

        ItemStack ball = new ItemStack(BDItems.MATTER_COMPRESS_BALL, 1, 0);
        MatterCompressionBall.setIStackList(ball, dropList);
        dropItemAtBlock(ball);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        this.inputFilterSlots.deserializeNBT(nbt.getCompoundTag("input_filter_slots"));
        this.fuelFilterSlots.deserializeNBT(nbt.getCompoundTag("fuel_filter_slots"));
        this.inputStorageSlots.deserializeNBT(nbt.getCompoundTag("input_storage_slots"));
        this.outputStorageSlots.deserializeNBT(nbt.getCompoundTag("output_storage_slots"));
        this.fuelStorageSlots.deserializeNBT(nbt.getCompoundTag("fuel_storage_slots"));
        this.fuelReturnSlots.deserializeNBT(nbt.getCompoundTag("fuel_return_slots"));
        this.litTime = loadIntList(nbt, "lit_time", capacity);
        this.litDuration = loadIntList(nbt, "lit_duration", capacity);
        this.cookTime = loadIntList(nbt, "cook_time", capacity);
        this.cookTimeTotal = loadIntList(nbt, "cook_time_total", capacity);
        String popModeStr = nbt.getString("pop_mode");
        if (popModeStr != null && !popModeStr.isEmpty()) {
            try {
                this.popMode = PopMode.valueOf(popModeStr);
            } catch (IllegalArgumentException ignored) {
                this.popMode = PopMode.STOP;
            }
        }
        String receiveModeStr = nbt.getString("receive_mode");
        if (receiveModeStr != null && !receiveModeStr.isEmpty()) {
            try {
                this.receiveMode = ReceiveMode.valueOf(receiveModeStr);
            } catch (IllegalArgumentException ignored) {
                this.receiveMode = ReceiveMode.STOP;
            }
        }
        if (nbt.hasKey("sort_mode")) {
            try {
                this.sortMode = AutoSortMode.valueOf(nbt.getString("sort_mode"));
            } catch (IllegalArgumentException ignored) {
                this.sortMode = AutoSortMode.STOP;
            }
        } else {
            this.sortMode = AutoSortMode.STOP;
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setTag("input_filter_slots", this.inputFilterSlots.serializeNBT());
        nbt.setTag("fuel_filter_slots", this.fuelFilterSlots.serializeNBT());
        nbt.setTag("input_storage_slots", this.inputStorageSlots.serializeNBT());
        nbt.setTag("output_storage_slots", this.outputStorageSlots.serializeNBT());
        nbt.setTag("fuel_storage_slots", this.fuelStorageSlots.serializeNBT());
        nbt.setTag("fuel_return_slots", this.fuelReturnSlots.serializeNBT());
        nbt.setIntArray("lit_time", toIntArray(litTime));
        nbt.setIntArray("lit_duration", toIntArray(litDuration));
        nbt.setIntArray("cook_time", toIntArray(cookTime));
        nbt.setIntArray("cook_time_total", toIntArray(cookTimeTotal));
        nbt.setString("pop_mode", this.popMode.name());
        nbt.setString("receive_mode", this.receiveMode.name());
        nbt.setString("sort_mode", this.sortMode.name());
    }

    public void setLit(boolean lit) {
        if (worldObj == null || worldObj.isRemote) return;

        int meta = worldObj.getBlockMetadata(xCoord, yCoord, zCoord);
        int newMeta = (meta & 7) | (lit ? 8 : 0);
        if (newMeta != meta) {
            worldObj.setBlockMetadataWithNotify(xCoord, yCoord, zCoord, newMeta, 2);
        }
    }

    public String getDisplayName() {
        return StatCollector.translateToLocal(displayNameKey);
    }

    public Container createMenu(int containerId, InventoryPlayer inventory, EntityPlayer player) {
        return new NetFurnaceMenu(inventory);
    }

    // ==================== 辅助方法 ====================

    private void dropItemAtBlock(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) return;
        EntityItem entity = new EntityItem(worldObj, xCoord + 0.5, yCoord + 0.5, zCoord + 0.5, stack);
        entity.delayBeforeCanPickup = 10;
        worldObj.spawnEntityInWorld(entity);
    }

    private static int insertIntoInventorySlot(IInventory inv, int slot, ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) return 0;
        if (!inv.isItemValidForSlot(slot, stack)) return stack.stackSize;

        int maxStackSize = Math.min(inv.getInventoryStackLimit(), stack.getMaxStackSize());
        ItemStack existing = inv.getStackInSlot(slot);

        if (existing == null) {
            int toInsert = Math.min(stack.stackSize, maxStackSize);
            ItemStack newStack = stack.copy();
            newStack.stackSize = toInsert;
            inv.setInventorySlotContents(slot, newStack);
            inv.markDirty();
            return stack.stackSize - toInsert;
        }

        if (existing.getItem() == stack.getItem() && existing.getItemDamage() == stack.getItemDamage()
            && ItemStack.areItemStackTagsEqual(existing, stack)
            && existing.stackSize < maxStackSize) {
            int canInsert = Math.min(maxStackSize - existing.stackSize, stack.stackSize);
            existing.stackSize += canInsert;
            inv.setInventorySlotContents(slot, existing);
            inv.markDirty();
            return stack.stackSize - canInsert;
        }

        return stack.stackSize;
    }

    private static List<Integer> loadIntList(NBTTagCompound nbt, String key, int size) {
        int[] arr = nbt.getIntArray(key);
        List<Integer> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(i < arr.length ? arr[i] : 0);
        }
        return list;
    }

    private static int[] toIntArray(List<Integer> list) {
        int[] arr = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }
}
