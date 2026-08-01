package com.wintercogs.beyonddimensions.api.storage.eu;

import java.math.BigInteger;

import net.minecraft.nbt.NBTTagCompound;

/**
 * 维度网络的 EU 能量池（1.7.10 移植新增）。
 * <p>
 * GTNH 生态中 GT5U 的 EU 包电协议与 RF 异构，MAX 电压/极端电流量级下
 * 统一 RF 池（long）存在存储上限问题，故为网络独立维护一个 BigInteger 容量的
 * EU 池，容量 {@value #DEFAULT_CAPACITY}（10^40 EU），任何现实来源灌不满。
 * <p>
 * 换算方向：只允许 EU→RF（GTNH 设计理念，RF 不可反向制造 EU），换算桥逻辑在
 * {@code DimensionsNet.extractRf} 中，本类仅负责 EU 池自身的存取。
 * <p>
 * 容量为共享静态常量，不随网络存储；每个网络只存 amount 一个 BigInteger。
 * NBT 以十进制字符串（{@code "euAmount"}）持久化。
 */
public class NetEuStorage {

    public static final BigInteger DEFAULT_CAPACITY = BigInteger.TEN.pow(40);

    private static final String NBT_KEY_AMOUNT = "euAmount";

    private BigInteger amount = BigInteger.ZERO;

    private final Runnable onChange;

    public NetEuStorage() {
        this(null);
    }

    public NetEuStorage(Runnable onChange) {
        this.onChange = onChange;
    }

    public BigInteger getAmount() {
        return amount;
    }

    public BigInteger getCapacity() {
        return DEFAULT_CAPACITY;
    }

    public boolean isEmpty() {
        return amount.signum() <= 0;
    }

    /**
     * 存入能量，封顶在容量内。
     *
     * @return 未接受的余量（0 表示全部存入）
     */
    public BigInteger insert(BigInteger insertAmount, boolean simulate) {
        if (insertAmount == null || insertAmount.signum() <= 0) {
            return BigInteger.ZERO;
        }
        BigInteger accepted = insertAmount.min(DEFAULT_CAPACITY.subtract(amount));
        if (accepted.signum() < 0) {
            accepted = BigInteger.ZERO;
        }
        if (!simulate && accepted.signum() > 0) {
            amount = amount.add(accepted);
            notifyChanged();
        }
        return insertAmount.subtract(accepted);
    }

    /**
     * 取出能量。
     *
     * @return 实际取出的能量（≤ demand）
     */
    public BigInteger extract(BigInteger extractAmount, boolean simulate) {
        if (extractAmount == null || extractAmount.signum() <= 0) {
            return BigInteger.ZERO;
        }
        BigInteger extracted = extractAmount.min(amount);
        if (!simulate && extracted.signum() > 0) {
            amount = amount.subtract(extracted);
            notifyChanged();
        }
        return extracted;
    }

    public void clear() {
        if (amount.signum() == 0) {
            return;
        }
        amount = BigInteger.ZERO;
        notifyChanged();
    }

    public void writeToNBT(NBTTagCompound tag) {
        tag.setString(NBT_KEY_AMOUNT, amount.toString());
    }

    public void readFromNBT(NBTTagCompound tag) {
        if (tag.hasKey(NBT_KEY_AMOUNT)) {
            try {
                amount = new BigInteger(tag.getString(NBT_KEY_AMOUNT));
            } catch (NumberFormatException ignored) {
                amount = BigInteger.ZERO;
            }
        } else {
            amount = BigInteger.ZERO;
        }
    }

    private void notifyChanged() {
        if (onChange != null) {
            onChange.run();
        }
    }
}
