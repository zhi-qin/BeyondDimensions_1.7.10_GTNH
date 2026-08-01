package com.wintercogs.beyonddimensions.integration.module.ae2.me;

import java.util.function.Predicate;

import javax.annotation.Nonnull;

import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.integration.module.ae2.AEHelper;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;

/**
 * 将 BD {@link UnifiedStorage} 暴露为 AE2 流体存储元件的适配器（1.7.10 适配版）。
 * <p>
 * 对应源项目（1.20.1）中 {@code NetStorageCell} 的流体部分：1.20.1 通过统一的
 * {@code AEKey} 在同一个硬盘上暴露物品+流体，1.7.10 AE2 将物品/流体分为不同通道，
 * 因此本类作为 {@link com.wintercogs.beyonddimensions.integration.module.ae2.item.NetAEFluidCell}
 * 的处理器，仅处理流体通道（{@link IAEFluidStack} / {@link FluidStackKey}）。
 * <p>
 * 与 {@link NetStorageCell}（物品通道）结构一致：每次从 UnifiedStorage 全量重建，
 * 不维护增量快照，避免 1.7.10 弱引用订阅在 AE2 迭代期间的竞态问题。
 */
public class NetFluidStorageCell implements IMEInventoryHandler<IAEFluidStack> {

    private final UnifiedStorage storage;

    public NetFluidStorageCell(UnifiedStorage storage) {
        this.storage = storage;
    }

    @Override
    public StorageChannel getChannel() {
        return StorageChannel.FLUIDS;
    }

    @Override
    public AccessRestriction getAccess() {
        return AccessRestriction.READ_WRITE;
    }

    @Override
    public boolean isPrioritized(IAEFluidStack input) {
        // 接受所有流体，无优先级过滤
        return false;
    }

    @Override
    public boolean canAccept(IAEFluidStack input) {
        if (input == null) {
            return false;
        }
        return AEHelper.fromAEToIStack(input) != null;
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public int getSlot() {
        return 0;
    }

    @Override
    public boolean validForPass(int i) {
        return true;
    }

    /**
     * 注入流体到 BD 统一存储。
     *
     * @return 未被存储的剩余流体（AE2 约定：返回 null 表示全部存入）
     */
    @Override
    public IAEFluidStack injectItems(IAEFluidStack input, Actionable type, BaseActionSource src) {
        if (input == null || input.getStackSize() <= 0) {
            return null;
        }
        IStackKey<?> key = AEHelper.fromAEToIStack(input);
        if (key == null) {
            // 无法转换的类型，原样返回（全部拒绝）
            return input;
        }
        boolean simulate = (type == Actionable.SIMULATE);
        long amount = input.getStackSize();
        KeyAmount leftover = storage.insert(key, amount, simulate);
        long notInserted = leftover != null ? leftover.amount() : amount;
        if (notInserted <= 0) {
            return null;
        }
        // 返回未存入的部分
        IAEFluidStack leftoverStack = input.copy();
        leftoverStack.setStackSize(notInserted);
        return leftoverStack;
    }

    /**
     * 从 BD 统一存储提取流体。
     *
     * @return 实际提取的流体（null 表示未提取到任何流体）
     */
    @Override
    public IAEFluidStack extractItems(IAEFluidStack request, Actionable mode, BaseActionSource src) {
        if (request == null || request.getStackSize() <= 0) {
            return null;
        }
        IStackKey<?> key = AEHelper.fromAEToIStack(request);
        if (key == null) {
            return null;
        }
        boolean simulate = (mode == Actionable.SIMULATE);
        long amount = request.getStackSize();
        KeyAmount extracted = storage.extract(key, amount, simulate, false);
        long extractedAmount = extracted != null ? extracted.amount() : 0;
        if (extractedAmount <= 0) {
            return null;
        }
        IAEFluidStack result = request.copy();
        result.setStackSize(extractedAmount);
        return result;
    }

    /**
     * 向 AE2 报告当前可用流体列表。每次从 UnifiedStorage 全量构建。
     */
    @Override
    public IItemList<IAEFluidStack> getAvailableItems(IItemList<IAEFluidStack> out) {
        return addAvailableItemsTo(out, null);
    }

    /**
     * 带 iteration 的可用流体列表查询。
     */
    @Override
    public IItemList<IAEFluidStack> getAvailableItems(IItemList<IAEFluidStack> out, int iteration) {
        return addAvailableItemsTo(out, null);
    }

    private IItemList<IAEFluidStack> addAvailableItemsTo(IItemList<IAEFluidStack> out,
        Predicate<IAEFluidStack> filter) {
        if (storage == null) {
            return out;
        }
        for (KeyAmount ka : storage.getStorage()) {
            if (ka == null || ka.isEmpty()) {
                continue;
            }
            // 仅添加流体类型（本处理器仅处理 FLUIDS 通道）
            if (!(ka.key() instanceof FluidStackKey)) {
                continue;
            }
            IAEStack aeStack = AEHelper.fromIStackToAE(ka.key());
            if (!(aeStack instanceof IAEFluidStack)) {
                continue;
            }
            IAEFluidStack fluidStack = (IAEFluidStack) aeStack;
            fluidStack.setStackSize(ka.amount());
            if (filter != null && !filter.test(fluidStack)) {
                continue;
            }
            out.add(fluidStack);
        }
        return out;
    }

    /**
     * 查询单个流体的可用数量。
     */
    @Override
    public IAEFluidStack getAvailableItem(@Nonnull IAEFluidStack request, int iteration) {
        IStackKey<?> key = AEHelper.fromAEToIStack(request);
        if (key == null) {
            return null;
        }
        KeyAmount ka = storage.getStackByKey(key);
        if (ka == null || ka.isEmpty()) {
            return null;
        }
        IAEFluidStack result = request.copy();
        result.setStackSize(ka.amount());
        return result;
    }
}
