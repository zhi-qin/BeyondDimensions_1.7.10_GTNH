package com.wintercogs.beyonddimensions.integration.module.gt;

import java.math.BigInteger;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.common.block.entity.NetedBlockEntity;
import com.wintercogs.beyonddimensions.common.machine.PopMode;
import com.wintercogs.beyonddimensions.integration.rf.RfNetEnergyPathwayBlockEntity;

import gregtech.api.interfaces.tileentity.IBasicEnergyContainer;
import gregtech.api.interfaces.tileentity.IEnergyConnected;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTECable;

/**
 * GT 放电模式能量通道变体（1.7.10 移植新增）。
 * <p>
 * 在 RF 变体（继承 {@link RfNetEnergyPathwayBlockEntity}，受 CoFH RF 系受电）基础上
 * 实现 GT5U 的 {@link IEnergyConnected}，打通 GT 生态两条能量链路：
 * <ul>
 * <li><b>受电（STOP 模式）</b>：GT 发电机/线缆经 {@code Util.emitEnergyToNetwork} →
 * {@code MTECable.canConnect} 识别本方块为 {@link IEnergyConnected}，调用
 * {@link #injectEnergyUnits} 把 {@code 电压×安培} 存进网络 EU 池（10^40，永不溢出）。</li>
 * <li><b>放电（OPEN 模式）</b>：自有的 6 向邻居循环（不实现
 * {@code IHasWorldObjectAndCoords}，避免依赖 GT 批量工具），逐邻居两类注入：
 * <ul>
 * <li>腿 1：直接贴的 GT 机器/能量仓（{@link IBasicEnergyContainer}）→ 读
 * {@code getInputVoltage()} 按其档位精确注入，永不炸机、无需变压器；</li>
 * <li>腿 2：直接贴的 GT 线缆（{@link MTECable}）→ 读 {@code mVoltage/mAmperage}
 * 按其额定规格注入，防过压/过流自燃。</li>
 * </ul>
 * </li>
 * </ul>
 * <p>
 * 为何 extends RF 而非 Mek 变体（审计 J）：Mek 变体 implements
 * {@code IStrictEnergyAcceptor}，无 Mek 环境（真实 GTNH 即无 Mek）加载 GT 变体时
 * 类解析会触发 NoClassDefFoundError；extends RF 仅依赖 GT + CoFH API，加载安全。
 * <p>
 * 由 {@code BDBlockEntities.resolveEnergyPathwayClass()} 在 gregtech 已加载时经字符串
 * 反射注册；无 GT 环境不会加载本类。四变体共用同一 TE 注册名与 NBT 格式。
 */
public class GtNetEnergyPathwayBlockEntity extends RfNetEnergyPathwayBlockEntity implements IEnergyConnected {

    public GtNetEnergyPathwayBlockEntity() {
        super();
    }

    // ==================== IEnergyConnected（受电：STOP 模式，GT 源→通道） ====================

    /**
     * GT 源/线缆推送能量：按 {@code 电压×安培} 全收进网络 EU 池，返回已接受安培。
     * <p>
     * 账目对平：GT 源按 {@code 电压×返回安培} 扣自身缓冲。EU 池 10^40 容量下理论上
     * 永不溢出；若溢出（不可达边界），按实收能量向下取整折算回安培。
     */
    @Override
    public long injectEnergyUnits(ForgeDirection side, long aVoltage, long aAmperage) {
        DimensionsNet net = getNet();
        if (net == null || !canReceive() || aVoltage <= 0 || aAmperage <= 0) {
            return 0;
        }
        BigInteger eu = BigInteger.valueOf(aVoltage)
            .multiply(BigInteger.valueOf(aAmperage));
        BigInteger leftover = net.insertEu(eu, false);
        if (leftover.signum() <= 0) {
            return aAmperage;
        }
        // 池满（10^40 不可达）：按实收能量折算回安培
        return eu.subtract(leftover)
            .divide(BigInteger.valueOf(aVoltage))
            .longValue();
    }

    @Override
    public boolean inputEnergyFrom(ForgeDirection side) {
        return canReceive();
    }

    @Override
    public boolean outputsEnergyTo(ForgeDirection side) {
        // OPEN 模式下通道主动对外放电：对外宣告"可输出"，使 GT 线缆能经
        // MTECable.canConnect 建立连接（仅当邻居 inputEnergyFrom || outputsEnergyTo 为真时连接），
        // 腿 2 才能经线缆注入能量。STOP 模式不输出——线缆经 inputEnergyFrom（canReceive）连接受电。
        // （不能沿用基类 canExtract()：它在 OPEN 模式为 false，会导致线缆断开、放电恒返回 0。）
        return getNet() != null && getPopMode() == PopMode.OPEN;
    }

    /** 无染色（无色方块），连接一切 GT 节点（含任意颜色的线缆/机器）。 */
    @Override
    public byte getColorization() {
        return -1;
    }

    /** 无色方块不接受染色，忽略并维持无色。 */
    @Override
    public byte setColorization(byte color) {
        return -1;
    }

    // ==================== 放电（OPEN 模式，通道→GT 机器/电线） ====================

    /**
     * 基类处理 RF 邻居（{@code popEnergy}/{@code pullExternalEnergy}，走 RF 池）后，
     * OPEN 模式下追加 GT 邻居放电循环。GT 机器/线缆不是 CoFH {@code IEnergyHandler}，
     * 不会被基类 {@code popEnergy} 重复推送，两分支按邻居类型互斥。
     */
    @Override
    public void workContent() {
        super.workContent();
        if (getPopMode() == PopMode.OPEN) {
            gtDischargeLoop();
        }
    }

    /**
     * 6 向扫 GT 邻居注入 EU（仅 EU 池，不做 RF→EU）。每邻居一次，两条腿互斥：
     * <ul>
     * <li>腿 1：{@link IBasicEnergyContainer} 且 {@code getInputVoltage()>0 &&
     * getInputAmperage()>0}（即电动机器/能量仓）→ 按其档位电压注入。</li>
     * <li>腿 2：其余 {@link IEnergyConnected} 邻居且 MTE 为 {@link MTECable}（GT 线缆）→
     * 按其额定 {@code mVoltage/mAmperage} 注入。</li>
     * </ul>
     * 注入电压与池预算保证 {@code usedAmps×v ≤ pool}，不凭空造 EU（审计 A）。
     */
    private void gtDischargeLoop() {
        DimensionsNet net = getNet();
        if (net == null || worldObj == null) {
            return;
        }

        for (ForgeDirection dir : ForgeDirection.values()) {
            if (dir == ForgeDirection.UNKNOWN) continue;

            TileEntity neighbor = worldObj
                .getTileEntity(xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ);
            if (neighbor == null || neighbor instanceof NetedBlockEntity) continue;
            ForgeDirection inputSide = dir.getOpposite();

            // 腿 1：直接贴的 GT 机器/能量仓（电压>0 且安培>0）→ 电压自动匹配，永不炸。
            // 非电动 GT 方块 getInputVoltage 返回 Integer.MAX_VALUE 但 getInputAmperage=0，
            // 被 getInputAmperage()>0 守卫提前跳过（审计 E）。
            if (neighbor instanceof IBasicEnergyContainer) {
                IBasicEnergyContainer ib = (IBasicEnergyContainer) neighbor;
                if (ib.getInputVoltage() > 0 && ib.getInputAmperage() > 0) {
                    long safeVoltage = ib.getInputVoltage();
                    long ratedAmps = ib.getInputAmperage();
                    // 池预算 floor(pool/v)，BigInteger 比较防溢出（审计 A）
                    BigInteger affordable = net.getEuStorage()
                        .getAmount()
                        .divide(BigInteger.valueOf(safeVoltage));
                    long maxAmps = affordable.compareTo(BigInteger.valueOf(ratedAmps)) < 0 ? affordable.longValue()
                        : ratedAmps;
                    if (maxAmps <= 0) continue;

                    long usedAmps = ib.injectEnergyUnits(inputSide, safeVoltage, maxAmps);
                    if (usedAmps > 0) {
                        net.extractEu(
                            BigInteger.valueOf(usedAmps)
                                .multiply(BigInteger.valueOf(safeVoltage)),
                            false);
                    }
                    continue;
                }
            }

            // 腿 2：直接贴的 GT 线缆（腿 1 的 v>0&&a>0 守卫把返回 0/0 的线缆落到本腿）
            // → 读 MTECable.mVoltage/mAmperage 按其注入，防过压自燃/过流自燃（审计 H）。
            if (neighbor instanceof IEnergyConnected) {
                if (!(neighbor instanceof IGregTechTileEntity)) continue;
                IGregTechTileEntity gt = (IGregTechTileEntity) neighbor;
                if (!(gt.getMetaTileEntity() instanceof MTECable)) continue; // 非 GT 线缆无法安全推断规格
                MTECable cable = (MTECable) gt.getMetaTileEntity();

                long v = cable.mVoltage;
                long wireAmps = cable.mAmperage;
                if (v <= 0 || wireAmps <= 0) continue;

                BigInteger affordable = net.getEuStorage()
                    .getAmount()
                    .divide(BigInteger.valueOf(v));
                long maxAmps = affordable.min(BigInteger.valueOf(wireAmps))
                    .longValue();
                if (maxAmps <= 0) continue;

                IEnergyConnected wire = (IEnergyConnected) neighbor;
                long usedAmps = wire.injectEnergyUnits(inputSide, v, maxAmps);
                if (usedAmps > 0) {
                    net.extractEu(
                        BigInteger.valueOf(usedAmps)
                            .multiply(BigInteger.valueOf(v)),
                        false);
                }
            }
        }
    }
}
