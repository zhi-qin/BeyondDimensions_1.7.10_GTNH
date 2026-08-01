package com.wintercogs.beyonddimensions.integration.module.ae2.me;

import java.math.BigInteger;
import java.util.function.Predicate;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.init.BDItems;
import com.wintercogs.beyonddimensions.config.ServerConfigRuntime;
import com.wintercogs.beyonddimensions.integration.module.ae2.AEHelper;
import com.wintercogs.beyonddimensions.integration.module.ae2.item.NetEnergyDrop;

import appeng.api.AEApi;
import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;

/**
 * 将 BD {@link UnifiedStorage} 暴露为 AE2 存储元件的适配器（1.7.10 适配版）。
 * <p>
 * 源项目（1.20.1）实现 {@code StorageCell} 接口并维护增量快照，
 * 1.7.10 AE2 使用 {@link IMEInventoryHandler} 接口且物品/流体分通道，
 * 本类仅处理物品通道（{@link IAEItemStack}），流体通道由调用方自行扩展。
 * <p>
 * 此外，维度网络的能量（{@link EnergyStackKey}）以"能量滴"物品（{@link NetEnergyDrop}）
 * 形式并入本物品通道（1 FE = 1 滴），对应源项目依赖 AppFlux 在 AE2 中展示能量的行为。
 * <p>
 * 设计简化：{@link #getAvailableItems} 每次从 UnifiedStorage 全量重建，
 * 不维护增量快照，避免 1.7.10 弱引用订阅在 AE2 迭代期间的竞态问题。
 */
public class NetStorageCell implements IMEInventoryHandler<IAEItemStack> {

    private final UnifiedStorage storage;
    /**
     * 维度网络引用，供能量滴按 {@link DimensionsNet#extractRf}/{@link DimensionsNet#insertRf}
     * 走 EU→RF 单向换算桥（RF 池优先、EU 兜底），与模组自身能量通道口径一致。
     * 由 {@link CellHandler#createNetStorageCell} 在 net 非空时构造，恒非 null。
     */
    private final DimensionsNet net;

    public NetStorageCell(UnifiedStorage storage, DimensionsNet net) {
        this.storage = storage;
        this.net = net;
    }

    @Override
    public StorageChannel getChannel() {
        return StorageChannel.ITEMS;
    }

    @Override
    public AccessRestriction getAccess() {
        return AccessRestriction.READ_WRITE;
    }

    @Override
    public boolean isPrioritized(IAEItemStack input) {
        // 接受所有物品，无优先级过滤
        return false;
    }

    @Override
    public boolean canAccept(IAEItemStack input) {
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
     * 注入物品到 BD 统一存储。
     *
     * @return 未被存储的剩余物品（AE2 约定：返回 null 表示全部存入）
     */
    @Override
    public IAEItemStack injectItems(IAEItemStack input, Actionable type, BaseActionSource src) {
        if (input == null || input.getStackSize() <= 0) {
            return null;
        }
        boolean simulate = (type == Actionable.SIMULATE);
        // 能量滴：1 FE = 1 滴，注入能量滴即向网络补充能量
        // 经 net.insertRf 走 RF 池（与模组自身能量通道 receiveEnergy 同口径）
        if (isEnergyDrop(input)) {
            long notInserted = net.insertRf(input.getStackSize(), simulate);
            if (notInserted <= 0) {
                return null;
            }
            // 返回未存入的部分
            IAEItemStack leftoverStack = input.copy();
            leftoverStack.setStackSize(notInserted);
            return leftoverStack;
        }
        IStackKey<?> key = AEHelper.fromAEToIStack(input);
        if (key == null) {
            // 无法转换的类型，原样返回（全部拒绝）
            return input;
        }
        long amount = input.getStackSize();
        KeyAmount leftover = storage.insert(key, amount, simulate);
        long notInserted = leftover != null ? leftover.amount() : amount;
        if (notInserted <= 0) {
            return null;
        }
        // 返回未存入的部分
        IAEItemStack leftoverStack = input.copy();
        leftoverStack.setStackSize(notInserted);
        return leftoverStack;
    }

    /**
     * 从 BD 统一存储提取物品。
     *
     * @return 实际提取的物品（null 表示未提取到任何物品）
     */
    @Override
    public IAEItemStack extractItems(IAEItemStack request, Actionable mode, BaseActionSource src) {
        if (request == null || request.getStackSize() <= 0) {
            return null;
        }
        boolean simulate = (mode == Actionable.SIMULATE);
        // 能量滴：1 FE = 1 滴，提取能量滴即从网络取能量
        // 经 net.extractRf 走 RF 池优先、EU→RF 换算兜底（与模组自身能量通道 extractEnergy 同口径），
        // 使 GT EU 供能的网络能量也能经 AE2 终端提取
        if (isEnergyDrop(request)) {
            long extractedAmount = net.extractRf(request.getStackSize(), simulate);
            if (extractedAmount <= 0) {
                return null;
            }
            IAEItemStack result = request.copy();
            result.setStackSize(extractedAmount);
            return result;
        }
        IStackKey<?> key = AEHelper.fromAEToIStack(request);
        if (key == null) {
            return null;
        }
        long amount = request.getStackSize();
        KeyAmount extracted = storage.extract(key, amount, simulate, false);
        long extractedAmount = extracted != null ? extracted.amount() : 0;
        if (extractedAmount <= 0) {
            return null;
        }
        IAEItemStack result = request.copy();
        result.setStackSize(extractedAmount);
        return result;
    }

    /**
     * 向 AE2 报告当前可用物品列表。每次从 UnifiedStorage 全量构建。
     */
    @Override
    public IItemList<IAEItemStack> getAvailableItems(IItemList<IAEItemStack> out) {
        return addAvailableItemsTo(out, null);
    }

    /**
     * 带 iteration 的可用物品列表查询。
     * 显式覆盖以避免 {@link IMEInventory} 默认实现中的循环调用风险，并确保 AE2 网络迭代能正确读取本存储。
     */
    @Override
    public IItemList<IAEItemStack> getAvailableItems(IItemList<IAEItemStack> out, int iteration) {
        return addAvailableItemsTo(out, null);
    }

    private IItemList<IAEItemStack> addAvailableItemsTo(IItemList<IAEItemStack> out, Predicate<IAEItemStack> filter) {
        if (storage == null) {
            return out;
        }
        for (KeyAmount ka : storage.getStorage()) {
            if (ka == null || ka.isEmpty()) {
                continue;
            }
            // 仅添加物品类型（本处理器仅处理 ITEMS 通道；能量以统一能量滴条目单独暴露）
            if (!(ka.key() instanceof ItemStackKey)) {
                continue;
            }
            IAEStack aeStack = AEHelper.fromIStackToAE(ka.key());
            if (!(aeStack instanceof IAEItemStack)) {
                continue;
            }
            IAEItemStack itemStack = (IAEItemStack) aeStack;
            itemStack.setStackSize(ka.amount());
            if (filter != null && !filter.test(itemStack)) {
                continue;
            }
            out.add(itemStack);
        }
        // 网络总能量（RF 池 + EU 池按换算率折算，对齐 getRfBudget）以单个"能量滴"条目暴露进物品通道
        long totalEnergy = getTotalEnergy();
        if (totalEnergy > 0) {
            IAEItemStack drop = makeEnergyDrop(totalEnergy);
            if (drop != null && (filter == null || filter.test(drop))) {
                out.add(drop);
            }
        }
        return out;
    }

    /**
     * 查询单个物品的可用数量。
     */
    @Override
    public IAEItemStack getAvailableItem(@Nonnull IAEItemStack request, int iteration) {
        if (isEnergyDrop(request)) {
            long total = getTotalEnergy();
            if (total <= 0) {
                return null;
            }
            IAEItemStack result = request.copy();
            result.setStackSize(total);
            return result;
        }
        IStackKey<?> key = AEHelper.fromAEToIStack(request);
        if (key == null) {
            return null;
        }
        KeyAmount ka = storage.getStackByKey(key);
        if (ka == null || ka.isEmpty()) {
            return null;
        }
        IAEItemStack result = request.copy();
        result.setStackSize(ka.amount());
        return result;
    }

    /* ================= 能量滴辅助 ================= */

    /**
     * 判断给定的 AE 物品堆栈是否为能量滴（{@link NetEnergyDrop}）。
     */
    private static boolean isEnergyDrop(IAEItemStack stack) {
        if (stack == null) {
            return false;
        }
        ItemStack is = stack.getItemStack();
        return is != null && is.getItem() instanceof NetEnergyDrop;
    }

    /**
     * 以能量滴物品形式构造一个 AE 物品堆栈，数量即 FE 数（1 FE = 1 滴）。
     */
    private static IAEItemStack makeEnergyDrop(long fe) {
        if (fe <= 0 || BDItems.NET_ENERGY_DROP == null) {
            return null;
        }
        ItemStack is = new ItemStack(BDItems.NET_ENERGY_DROP, 1);
        IAEItemStack stack = AEApi.instance()
            .storage()
            .createItemStack(is);
        if (stack == null) {
            return null;
        }
        stack.setStackSize(fe);
        return stack;
    }

    /**
     * 汇总网络中的总能量（FE）＝ RF 池存量 + EU 池按换算率折算的 RF（EU→RF 唯一方向），
     * 对齐 {@code NetEnergyPathwayBlockEntity.getRfBudget} 的口径。EU 池为 BigInteger
     * （10^40 容量），折算后超 long 上限时封顶为 Long.MAX_VALUE。
     */
    private long getTotalEnergy() {
        if (net == null) {
            return 0;
        }
        long rf = net.getUnifiedStorage()
            .getStackByKey(EnergyStackKey.INSTANCE)
            .amount();
        int rate = ServerConfigRuntime.gtEuToRfRate;
        if (rate <= 0 || net.getEuStorage()
            .isEmpty()) {
            return rf;
        }
        BigInteger euInRf = net.getEuStorage()
            .getAmount()
            .multiply(BigInteger.valueOf(rate));
        if (euInRf.compareTo(BigInteger.valueOf(Long.MAX_VALUE - rf)) >= 0) {
            return Long.MAX_VALUE; // 封顶，防 long 溢出
        }
        return rf + euInRf.longValue();
    }
}
