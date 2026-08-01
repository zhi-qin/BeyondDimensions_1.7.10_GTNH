package com.wintercogs.beyonddimensions.common.block.entity;

import java.math.BigInteger;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.common.util.ForgeDirection;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.energy.IEnergyHandler;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import com.wintercogs.beyonddimensions.common.machine.PopMode;
import com.wintercogs.beyonddimensions.common.menu.NetEnergyMenu;
import com.wintercogs.beyonddimensions.config.ServerConfigRuntime;
import com.wintercogs.beyonddimensions.integration.IntegrationHandlerRegistry;
import com.wintercogs.beyonddimensions.util.BDMath;

/**
 * 维度网络能量通道方块实体（1.7.10 移植版）。
 * <p>
 * 对应源项目 1.20.1 的 {@code NetEnergyPathwayBlockEntity}。
 * 源项目通过 ForgeCapabilities.ENERGY 暴露网络的能量存储；
 * 1.7.10 没有 Forge Capability 系统，本类通过实现 {@link IEnergyHandler} 接口
 * 暴露能量交互能力。
 * <p>
 * 弹出模式（{@link PopMode#OPEN}）下仅向外推送能量，不接收外部抽取，
 * 对齐源项目 {@code EnergyStorage(0)} 的行为。
 * <p>
 * 跨模组能量兼容（对齐源项目 Capability 的"通用能量"语义）：
 * <ul>
 * <li>被动接收：RF API（CoFH/Mekanism）环境下，BDBlockEntities 会注册实现了
 * cofh.api.energy.IEnergyHandler / mekanism IStrictEnergyAcceptor 的变体子类，
 * 外部模组可直接向本方块推送能量（走 {@link #receiveEnergy}）</li>
 * <li>主动吸收（STOP 模式）：1.7.10 没有 Capability，外部储能（如 Mekanism 创造能量方块）
 * 无法感知本方块的受能能力，故在 STOP 模式下通过
 * {@link IntegrationHandlerRegistry} 的能量提供者主动抽取相邻外部储能，
 * 等效于源项目中外部模组向 Capability 推送的效果</li>
 * <li>主动弹出（OPEN 模式）：除本模组 IEnergyHandler 外，还会经能量提供者
 * 向外部受能方块（如 Mekanism 机器）推送网络能量</li>
 * </ul>
 */
public class NetEnergyPathwayBlockEntity extends BaseMachineBlockEntity implements IEnergyHandler {

    private PopMode popMode = PopMode.STOP;

    /** 主动抽取开关（默认不抽取；可由 GUI 按钮逐方块覆盖并持久化，新放置时取 config 默认值）。 */
    private boolean activePull = false;

    public NetEnergyPathwayBlockEntity() {
        super();
        // 注册网络切换时的清理任务：popMode 变化需触发客户端重渲染
        addNetChangeTask(this::onNetChanged);
        // 新放置通道的默认抽取状态由 config 控制；读档时随后被 readFromNBT 覆盖
        this.activePull = ServerConfigRuntime.energyPathwayDefaultActivePull;
    }

    // ==================== 网络变更回调 ====================

    /**
     * 网络变更或 popMode 变更时，通知客户端重新渲染方块。
     * 对齐源项目在 {@code invalidateCaps()} / {@code setPopMode()} 中
     * 清理 Capability 缓存的语义——1.7.10 没有 Capability 系统，
     * 改用 {@link #worldObj#markBlockForUpdate} 触发客户端更新。
     */
    private void onNetChanged() {
        if (worldObj != null && !worldObj.isRemote) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    // ==================== PopMode ====================

    public PopMode getPopMode() {
        return popMode;
    }

    public void setPopMode(PopMode newMode) {
        if (this.popMode != newMode) {
            this.popMode = newMode;
            markDirty();
            // 触发客户端重渲染，使 GUI 能量条模式正确显示
            onNetChanged();
        }
    }

    // ==================== 主动抽取 ====================

    public boolean getActivePull() {
        return activePull;
    }

    public void setActivePull(boolean newMode) {
        if (this.activePull != newMode) {
            this.activePull = newMode;
            markDirty();
            // 触发客户端重渲染，使 GUI 抽取按钮状态正确显示
            onNetChanged();
        }
    }

    // ==================== BaseMachine 覆写 ====================

    @Override
    public boolean shouldWork() {
        return super.shouldWork() && getNet() != null;
    }

    @Override
    public int getTicksPerWork() {
        return 1;
    }

    @Override
    public void workContent() {
        super.workContent();
        if (popMode == PopMode.OPEN) {
            popEnergy();
        } else {
            // 默认纯被动（对齐源项目）：仅该方块主动抽取开启时，每 20 tick 主动抽取一次相邻外部储能。
            if (activePull && worldObj != null && worldObj.getTotalWorldTime() % 20 == 0) {
                pullExternalEnergy();
            }
        }
    }

    // ==================== IEnergyHandler 实现 ====================
    // 对应源项目的 EnergyUnifiedStorageHandler，通过自定义 IEnergyHandler 接口
    // 暴露网络的能量存储。方向对本方块无效（网络存储不区分面）。

    @Override
    public int receiveEnergy(ForgeDirection from, int maxReceive, boolean simulate) {
        DimensionsNet net = getNet();
        if (net == null || popMode == PopMode.OPEN) {
            // OPEN 模式下仅弹出，不接收（对齐源项目 EnergyStorage(0)）
            return 0;
        }
        long remaining = net.insertRf(maxReceive, simulate);
        return BDMath.clampLongToInt(maxReceive - Math.min(remaining, maxReceive));
    }

    @Override
    public int extractEnergy(ForgeDirection from, int maxExtract, boolean simulate) {
        DimensionsNet net = getNet();
        if (net == null || popMode == PopMode.OPEN) return 0;
        // 单向换算桥：RF 池优先，不足按 N 从 EU 池换算续供（RF→EU 被禁）
        return BDMath.clampLongToInt(net.extractRf(maxExtract, simulate));
    }

    @Override
    public int getEnergyStored(ForgeDirection from) {
        DimensionsNet net = getNet();
        if (net == null || popMode == PopMode.OPEN) return 0;
        return BDMath.clampLongToInt(getRfBudget(net));
    }

    @Override
    public int getMaxEnergyStored(ForgeDirection from) {
        DimensionsNet net = getNet();
        if (net == null || popMode == PopMode.OPEN) return 0;
        // 与 getEnergyStored（getRfBudget 含 EU 折算）对齐：max 取 RF 池容量与当前总预算
        // 的较大者，避免 EU 池非空时 stored > max 的外部读数异常（进度条超限/误判可提取量，
        // 审计 M4-1）
        long capacity = net.getUnifiedStorage()
            .getSlotCapacity(0);
        long budget = getRfBudget(net);
        long max = budget > capacity ? budget : capacity;
        return BDMath.clampLongToInt(max);
    }

    /**
     * 网络可供给外部 RF 的总预算 = RF 池存量 + EU 池按换算率折算的 RF（EU→RF 唯一方向）。
     * 供 {@link #getEnergyStored}/{@link #popEnergy} 及子类（Mek/RF 变体）使用，
     * 保证推送/读数不凭空造能。
     */
    protected long getRfBudget(DimensionsNet net) {
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

    @Override
    public boolean canConnectEnergy(ForgeDirection from) {
        return getNet() != null;
    }

    public boolean canExtract() {
        // OPEN 模式下仅主动弹出，不允许被抽取（对齐源项目 EnergyStorage(0) 行为）
        return getNet() != null && popMode != PopMode.OPEN;
    }

    public boolean canReceive() {
        return getNet() != null && popMode != PopMode.OPEN;
    }

    // ==================== 能量弹出（OPEN 模式） ====================

    private void popEnergy() {
        DimensionsNet net = getNet();
        if (net == null || worldObj == null) {
            return;
        }

        // RF 池 + EU 池折算 RF 的合并预算；任一池有能量即可弹出（单向 EU→RF 桥）
        if (getRfBudget(net) <= 0) {
            return;
        }

        // 遍历相邻方块，向支持 IEnergyHandler 的方块推送能量
        for (ForgeDirection dir : ForgeDirection.values()) {
            if (dir == ForgeDirection.UNKNOWN) continue;

            int targetX = xCoord + dir.offsetX;
            int targetY = yCoord + dir.offsetY;
            int targetZ = zCoord + dir.offsetZ;
            TileEntity neighbor = worldObj.getTileEntity(targetX, targetY, targetZ);
            if (neighbor == null || neighbor instanceof NetedBlockEntity) continue;

            // 从邻居视角看，能量输入方向为 dir.getOpposite()
            ForgeDirection inputSide = dir.getOpposite();
            // 每轮循环重算预算：邻居 B 可能已接受邻居 A 没吃完的余量，
            // 若不重算会导致 extractRf 扣不出 received，凭空造能。
            int maxExtract = BDMath.clampLongToInt(getRfBudget(net));
            if (maxExtract <= 0) break;

            if (neighbor instanceof IEnergyHandler) {
                IEnergyHandler handler = (IEnergyHandler) neighbor;
                int received = handler.receiveEnergy(inputSide, maxExtract, false);
                if (received > 0) {
                    // RF 池优先，不足按 N 从 EU 池换算续供（RF→EU 被禁）
                    net.extractRf(received, false);
                }
            } else {
                // 兼容联动模组桥接的外部受能方块（如 Mekanism 机器/能量方块）：
                // 源项目中 OPEN 模式会向一切持有能量 Capability 的邻居推送，此处经提供者对齐
                pushEnergyToProviders(net, neighbor, inputSide, maxExtract);
            }
        }
    }

    /**
     * 经 {@link IntegrationHandlerRegistry} 中 stackType 为能量的提供者，
     * 向外部受能方块推送网络能量。首个成功匹配的提供者生效，避免同一邻居被重复推送。
     *
     * @param inputSide 从邻居视角看的输入面
     */
    private void pushEnergyToProviders(DimensionsNet net, TileEntity neighbor, ForgeDirection inputSide,
        int maxExtract) {
        for (IntegrationHandlerRegistry.IExternalHandlerProvider provider : IntegrationHandlerRegistry.getProviders()) {
            if (!EnergyStackKey.ID.equals(provider.getStackTypeId())) continue;
            boolean matched;
            try {
                matched = provider.matches(neighbor);
            } catch (Throwable ignored) {
                continue;
            }
            if (!matched) continue;

            long remaining;
            try {
                remaining = provider.insert(neighbor, EnergyStackKey.INSTANCE, maxExtract, false, inputSide);
            } catch (Throwable ignored) {
                continue;
            }
            long accepted = maxExtract - remaining;
            if (accepted > 0) {
                // RF 池优先，不足按 N 从 EU 池换算续供（RF→EU 被禁）
                net.extractRf(accepted, false);
            }
            return;
        }
    }

    // ==================== 能量吸收（STOP 模式） ====================

    /**
     * 主动从相邻的外部储能抽取能量入网（经 {@link IntegrationHandlerRegistry} 的能量提供者，
     * 如 Mekanism 的能量方块/创造能量方块）。
     * <p>
     * 源项目（1.20.1）中外部模组通过 Forge Capability 主动向网络推送；1.7.10 无 Capability，
     * 外部储能感知不到本方块的受能能力，故以主动抽取等效替代。
     * 抽取流程与 NetPump 一致：模拟插入算出可入网数量 → 实际抽取 → 实际入网。
     */
    private void pullExternalEnergy() {
        DimensionsNet net = getNet();
        if (net == null || worldObj == null) {
            return;
        }
        UnifiedStorage storage = net.getUnifiedStorage();

        for (ForgeDirection dir : ForgeDirection.values()) {
            if (dir == ForgeDirection.UNKNOWN) continue;

            int targetX = xCoord + dir.offsetX;
            int targetY = yCoord + dir.offsetY;
            int targetZ = zCoord + dir.offsetZ;
            TileEntity neighbor = worldObj.getTileEntity(targetX, targetY, targetZ);
            if (neighbor == null || neighbor instanceof NetedBlockEntity) continue;

            // 从邻居视角看，被抽取的面为 dir.getOpposite()
            ForgeDirection extractSide = dir.getOpposite();

            for (IntegrationHandlerRegistry.IExternalHandlerProvider provider : IntegrationHandlerRegistry
                .getProviders()) {
                if (!EnergyStackKey.ID.equals(provider.getStackTypeId())) continue;

                boolean matched;
                try {
                    matched = provider.matches(neighbor);
                } catch (Throwable ignored) {
                    continue;
                }
                if (!matched) continue;

                List<KeyAmount> extractable;
                try {
                    extractable = provider.getExtractableContents(neighbor, extractSide);
                } catch (Throwable ignored) {
                    continue;
                }
                if (extractable == null || extractable.isEmpty()) continue;

                for (KeyAmount ka : extractable) {
                    if (ka == null || ka.isEmpty() || ka.amount() <= 0) continue;
                    if (!(ka.key() instanceof EnergyStackKey)) continue;

                    // 模拟插入算可入网数量
                    long canInsert = ka.amount() - storage.insert(ka.key(), ka.amount(), true)
                        .amount();
                    canInsert = Math.min(canInsert, ka.amount());
                    if (canInsert <= 0) continue;

                    KeyAmount extracted;
                    try {
                        extracted = provider.extract(neighbor, ka.key(), canInsert, false, extractSide);
                    } catch (Throwable ignored) {
                        continue;
                    }
                    if (extracted == null || extracted.isEmpty() || extracted.amount() <= 0) continue;

                    storage.insert(extracted.key(), extracted.amount(), false);
                }
            }
        }
    }

    // ==================== NBT 持久化 ====================

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);

        // 旧数据兼容：优先读取字符串形式的 popMode
        String popModeNew = tag.getString("popMode");
        if (popModeNew != null && !popModeNew.isEmpty()) {
            try {
                this.popMode = PopMode.valueOf(popModeNew);
            } catch (IllegalArgumentException ignored) {
                this.popMode = PopMode.STOP;
            }
        } else if (tag.getBoolean("popMode")) {
            // 更早期的布尔形式
            this.popMode = PopMode.OPEN;
        } else {
            this.popMode = PopMode.STOP;
        }

        this.activePull = tag.getBoolean("activePull");
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setString("popMode", this.popMode.name());
        tag.setBoolean("activePull", this.activePull);
    }

    // ==================== GUI ====================

    public IChatComponent getDisplayName() {
        return new ChatComponentTranslation("menu.title.beyonddimensions.net_energy_menu");
    }

    public Container createMenu(int containerId, InventoryPlayer inventory, EntityPlayer player) {
        return new NetEnergyMenu(inventory, this);
    }
}
