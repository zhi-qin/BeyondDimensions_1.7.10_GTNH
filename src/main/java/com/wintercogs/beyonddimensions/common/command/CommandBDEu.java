package com.wintercogs.beyonddimensions.common.command;

import java.math.BigDecimal;
import java.math.BigInteger;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.eu.NetEuStorage;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import com.wintercogs.beyonddimensions.config.ServerConfigRuntime;

import cpw.mods.fml.common.event.FMLServerStartingEvent;

/**
 * 临时 OP 调试命令（Part 1 Phase A 验证用，Phase B 接入真实交互后移除）。
 * <p>
 * 用于驱动单向换算桥（EU→RF，唯一方向）与 EU 池注入，无需 GT 环境即可验证：
 * <ul>
 * <li>/bdeu add &lt;amount&gt; — 向网络 EU 池注入能量（支持 1e39 科学计数）</li>
 * <li>/bdeu get — 显示网络 EU 池 / RF 池存量</li>
 * <li>/bdeu rf &lt;amount&gt; — 向 RF 池注入能量，报告未接受余量</li>
 * <li>/bdeu toRf &lt;amount&gt; — 模拟 RF 消费者抽 amount RF（RF 池不足时按 N 换算 EU→RF）</li>
 * </ul>
 */
public class CommandBDEu extends CommandBase {

    private static final int OP_LEVEL = 2;

    /**
     * 在 FMLServerStartingEvent 中注册此命令。
     */
    public static void register(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandBDEu());
    }

    @Override
    public String getCommandName() {
        return "bdeu";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "commands.bdeu.usage";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return OP_LEVEL;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            throw new WrongUsageException("commands.bdeu.usage");
        }
        switch (args[0]) {
            case "add":
                handleAdd(sender, args);
                break;
            case "get":
                handleGet(sender, args);
                break;
            case "rf":
                handleRf(sender, args);
                break;
            case "toRf":
                handleToRf(sender, args);
                break;
            default:
                throw new WrongUsageException("commands.bdeu.usage");
        }
    }

    @SuppressWarnings("rawtypes")
    @Override
    public java.util.List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "add", "get", "rf", "toRf");
        }
        return null;
    }

    // ==================== 子命令处理 ====================

    private void handleAdd(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            throw new WrongUsageException("commands.bdeu.add.usage");
        }
        BigInteger amount = parseBig(args[1]);
        if (amount == null || amount.signum() <= 0) {
            throw new WrongUsageException("commands.bdeu.add.invalid");
        }
        DimensionsNet net = getNetOrFail(sender);
        if (net == null) return;

        BigInteger leftover = net.insertEu(amount, false);
        send(
            sender,
            "EU池 +" + amount
                + "，未接受余量 "
                + leftover
                + "；当前 EU "
                + net.getEuStorage()
                    .getAmount());
    }

    private void handleGet(ICommandSender sender, String[] args) {
        DimensionsNet net = getNetOrFail(sender);
        if (net == null) return;

        long rf = net.getUnifiedStorage()
            .getStackByKey(EnergyStackKey.INSTANCE)
            .amount();
        send(
            sender,
            "EU " + net.getEuStorage()
                .getAmount()
                + " / "
                + NetEuStorage.DEFAULT_CAPACITY
                + "，RF "
                + rf
                + "，换算率 "
                + ServerConfigRuntime.gtEuToRfRate
                + "（EU→RF）");
    }

    private void handleRf(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            throw new WrongUsageException("commands.bdeu.rf.usage");
        }
        long amount = parseLong(args[1]);
        if (amount <= 0) {
            throw new WrongUsageException("commands.bdeu.rf.invalid");
        }
        DimensionsNet net = getNetOrFail(sender);
        if (net == null) return;

        long leftover = net.insertRf(amount, false);
        send(
            sender,
            "RF池 +" + amount
                + "，未接受余量 "
                + leftover
                + "；当前 RF "
                + net.getUnifiedStorage()
                    .getStackByKey(EnergyStackKey.INSTANCE)
                    .amount());
    }

    private void handleToRf(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            throw new WrongUsageException("commands.bdeu.toRf.usage");
        }
        long demand = parseLong(args[1]);
        if (demand <= 0) {
            throw new WrongUsageException("commands.bdeu.toRf.invalid");
        }
        DimensionsNet net = getNetOrFail(sender);
        if (net == null) return;

        long got = net.extractRf(demand, false);
        send(
            sender,
            "extractRf(" + demand
                + ") 实取 "
                + got
                + " RF（换算率 "
                + ServerConfigRuntime.gtEuToRfRate
                + "，RF池不足时 EU→RF）；当前 EU "
                + net.getEuStorage()
                    .getAmount());
    }

    // ==================== 辅助方法 ====================

    private DimensionsNet getNetOrFail(ICommandSender sender) {
        // 1.7.10 getCommandSenderAsPlayer 对非玩家 sender（控制台/命令方块）抛异常，先判定
        if (!(sender instanceof EntityPlayerMP)) {
            send(sender, "该命令仅限玩家执行（需要所属维度网络）。");
            return null;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        DimensionsNet net = DimensionsNet.getNetFromPlayer(player);
        if (net == null) {
            send(sender, "你当前不属于任何维度网络。");
        }
        return net;
    }

    /** 解析科学计数法字符串（1e39）为 BigInteger；失败返回 null。 */
    private BigInteger parseBig(String s) {
        try {
            return new BigDecimal(s).toBigInteger();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 解析为 long 并校验在正 long 范围；失败返回 -1。 */
    private long parseLong(String s) {
        try {
            BigInteger big = new BigDecimal(s).toBigInteger();
            if (big.signum() <= 0 || big.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
                return -1;
            }
            return big.longValue();
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void send(ICommandSender sender, String message) {
        sender.addChatMessage(new ChatComponentText(message));
    }
}
