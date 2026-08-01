package com.wintercogs.beyonddimensions.integration.module.botania.storage;

import java.util.Collections;
import java.util.List;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.ForgeDirection;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.integration.IntegrationHandlerRegistry;

import vazkii.botania.api.mana.IManaBlock;
import vazkii.botania.api.mana.IManaCollector;
import vazkii.botania.api.mana.IManaReceiver;
import vazkii.botania.api.mana.spark.ISparkAttachable;

/**
 * Mana 外部处理器提供者（1.7.10 适配新增的桥接类）。
 * <p>
 * 让 NetPump/NetInterface 能识别并抽取 Botania 的 IManaReceiver 方块。
 * <p>
 * 实现要点：
 * - matches: 检查 te 是否为 IManaReceiver（可接收/抽取 Mana 的方块）
 * - getExtractableContents: 返回当前 Mana 数量
 * - extract: 调用 recieveMana(-amount) 抽取（负数表示取出）
 * - insert: 调用 recieveMana(amount) 插入
 * <p>
 * 注意：Botania 1.7.10 的 IManaBlock 有 int getCurrentMana()，
 * IManaReceiver extends IManaBlock，额外有 void recieveMana(int mana) 和 boolean isFull()。
 * 仅 IManaReceiver 可通过 recieveMana 改变 Mana 数量，故 matches 必须检查 IManaReceiver。
 */
public class ManaHandlerProvider implements IntegrationHandlerRegistry.IExternalHandlerProvider {

    @Override
    public ResourceLocation getStackTypeId() {
        return ManaStackKey.ID;
    }

    @Override
    public boolean matches(TileEntity te) {
        // 必须是 IManaReceiver 才能通过 recieveMana 抽取/插入 Mana
        // IManaBlock 仅提供 getCurrentMana()，无法修改 Mana 储量
        return te instanceof IManaReceiver;
    }

    @Override
    public List<KeyAmount> getExtractableContents(TileEntity te, ForgeDirection side) {
        if (te instanceof IManaBlock) {
            int current = ((IManaBlock) te).getCurrentMana();
            if (current > 0) {
                return Collections.singletonList(new KeyAmount(ManaStackKey.INSTANCE, current));
            }
        }
        return Collections.emptyList();
    }

    @Override
    public KeyAmount extract(TileEntity te, IStackKey<?> key, long amount, boolean simulate, ForgeDirection side) {
        if (!(te instanceof IManaReceiver) || !ManaStackKey.INSTANCE.isSame(key)) {
            return new KeyAmount(ManaStackKey.INSTANCE, 0);
        }
        IManaReceiver receiver = (IManaReceiver) te;
        int current = ((IManaBlock) te).getCurrentMana();
        long toExtract = Math.min(amount, current);
        if (toExtract <= 0) {
            return new KeyAmount(ManaStackKey.INSTANCE, 0);
        }
        // recieveMana 接受 int，需钳制到 int 范围
        int toExtractInt = (int) Math.min(toExtract, Integer.MAX_VALUE);
        if (!simulate) {
            receiver.recieveMana(-toExtractInt);
        }
        return new KeyAmount(ManaStackKey.INSTANCE, toExtractInt);
    }

    @Override
    public long insert(TileEntity te, IStackKey<?> key, long amount, boolean simulate, ForgeDirection side) {
        if (!(te instanceof IManaReceiver) || !ManaStackKey.INSTANCE.isSame(key)) {
            return amount;
        }
        IManaReceiver receiver = (IManaReceiver) te;
        if (receiver.isFull()) {
            return amount;
        }
        // 对齐源项目 ManaHandlerWrapper.insert 的容量钳制语义：
        // 实际插入量按可用容量钳制，未插入部分（leftover）返回给调用方，
        // 避免 Botania 1.7.10 TilePool.recieveMana 内部钳制（min(current+mana, manaCap)）
        // 导致接口从网络扣了全部 Mana 而池子只收下部分、Mana 静默丢失。
        long actInsert = Math.min(amount, availableManaSpace(receiver));
        if (actInsert <= 0) {
            return amount;
        }
        // recieveMana 接受 int，需钳制到 int 范围
        int actInsertInt = (int) Math.min(actInsert, Integer.MAX_VALUE);
        if (!simulate) {
            // 实际插入量以 recieveMana 前后的当前 Mana 差值为准：Botania 内部会对
            // min(current+mana, cap) 钳制，未知容量接收方按 max(1000,current) 估算会高估，
            // 若直接按 actInsertInt 返回值会导致接口从网络多扣 Mana 而接收方只收下部分。
            int before = ((IManaBlock) te).getCurrentMana();
            receiver.recieveMana(actInsertInt);
            int after = ((IManaBlock) te).getCurrentMana();
            int actualInserted = Math.max(0, after - before);
            return amount - actualInserted;
        }
        return amount - actInsertInt;
    }

    /**
     * 计算接收方的可用 Mana 容量，对齐源项目 ManaContainerWrapper.getMaxMana 的容量判定语义：
     * - IManaCollector：getMaxMana() - 当前量
     * - ISparkAttachable（1.7.10 魔力池 TilePool 也实现该接口）：getAvailableSpaceForMana() 直接给出可用空间
     * - 其他未知容量接收方：源项目兜底近似 max(1000, 当前量) 作为容量
     */
    private long availableManaSpace(IManaReceiver receiver) {
        int current = ((IManaBlock) receiver).getCurrentMana();
        if (receiver instanceof IManaCollector) {
            return Math.max(0, (long) ((IManaCollector) receiver).getMaxMana() - current);
        }
        if (receiver instanceof ISparkAttachable) {
            return Math.max(0, ((ISparkAttachable) receiver).getAvailableSpaceForMana());
        }
        int capacityApprox = Math.max(1000, current);
        return Math.max(0, (long) capacityApprox - current);
    }
}
