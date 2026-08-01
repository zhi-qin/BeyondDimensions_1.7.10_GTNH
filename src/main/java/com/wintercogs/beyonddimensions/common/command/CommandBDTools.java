package com.wintercogs.beyonddimensions.common.command;

import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;

import cpw.mods.fml.common.event.FMLServerStartingEvent;

/**
 * 1.7.10 适配版服务端命令，移植自 1.20.1 的 ServerCommands。
 * <p>
 * 命令结构：
 * <ul>
 * <li>/bdtools network setOwner &lt;netId&gt; [player]</li>
 * <li>/bdtools network addManager &lt;netId&gt; &lt;player&gt;</li>
 * <li>/bdtools network removeManager &lt;netId&gt; &lt;player&gt;</li>
 * <li>/bdtools network addPlayer &lt;netId&gt; &lt;player&gt;</li>
 * <li>/bdtools network removePlayer &lt;netId&gt; &lt;player&gt;</li>
 * <li>/bdtools network create &lt;player&gt; [slotCapacity] [slotMaxSize]</li>
 * <li>/bdtools network deleteNet &lt;netId&gt;</li>
 * <li>/bdtools network deleteNetByPlayer &lt;player&gt;</li>
 * </ul>
 */
public class CommandBDTools extends CommandBase {

    private static final int OP_LEVEL = 2;

    /**
     * 在 FMLServerStartingEvent 中注册此命令。
     */
    public static void register(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandBDTools());
    }

    @Override
    public String getCommandName() {
        return "bdtools";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "commands.bdtools.usage";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return OP_LEVEL;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            throw new WrongUsageException("commands.bdtools.usage");
        }

        if (!"network".equals(args[0])) {
            throw new WrongUsageException("commands.bdtools.usage");
        }

        if (args.length < 2) {
            throw new WrongUsageException("commands.bdtools.usage");
        }

        String subCommand = args[1];
        switch (subCommand) {
            case "setOwner":
                handleSetOwner(sender, args);
                break;
            case "addManager":
                handleAddManager(sender, args);
                break;
            case "removeManager":
                handleRemoveManager(sender, args);
                break;
            case "addPlayer":
                handleAddPlayer(sender, args);
                break;
            case "removePlayer":
                handleRemovePlayer(sender, args);
                break;
            case "create":
                handleCreate(sender, args);
                break;
            case "deleteNet":
                handleDeleteNet(sender, args);
                break;
            case "deleteNetByPlayer":
                handleDeleteNetByPlayer(sender, args);
                break;
            default:
                throw new WrongUsageException("commands.bdtools.usage");
        }
    }

    @SuppressWarnings("rawtypes")
    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "network");
        }
        if (args.length == 2 && "network".equals(args[0])) {
            return getListOfStringsMatchingLastWord(
                args,
                "setOwner",
                "addManager",
                "removeManager",
                "addPlayer",
                "removePlayer",
                "create",
                "deleteNet",
                "deleteNetByPlayer");
        }
        if (args.length >= 3 && "network".equals(args[0])) {
            // 为需要玩家名的参数提供在线玩家补全
            String sub = args[1];
            boolean needsPlayer = ("setOwner".equals(sub) && args.length == 4)
                || (("addManager".equals(sub) || "removeManager".equals(sub)
                    || "addPlayer".equals(sub)
                    || "removePlayer".equals(sub)) && args.length == 4)
                || ("create".equals(sub) && args.length == 3)
                || ("deleteNetByPlayer".equals(sub) && args.length == 3);
            if (needsPlayer) {
                return getListOfStringsMatchingLastWord(
                    args,
                    MinecraftServer.getServer()
                        .getAllUsernames());
            }
        }
        return null;
    }

    // ==================== 子命令处理 ====================

    private void handleSetOwner(ICommandSender sender, String[] args) {
        if (args.length < 3) {
            throw new WrongUsageException("commands.bdtools.network.setOwner.usage");
        }
        int netId = parseIntBounded(sender, args[2], 0, Integer.MAX_VALUE);
        DimensionsNet net = getNetOrFail(sender, netId);
        if (net == null) return;

        EntityPlayerMP target;
        if (args.length >= 4) {
            target = CommandBase.getPlayer(sender, args[3]);
        } else if (sender instanceof EntityPlayerMP) {
            target = (EntityPlayerMP) sender;
        } else {
            // 对齐 1.20.1 源项目 setOwner：控制台执行且未指定玩家时给出提示而非崩溃
            // （1.7.10 getCommandSenderAsPlayer 对非玩家 sender 抛 EntityNotFoundException）
            sendFailure(sender, "This command must specify a player when run from console.");
            return;
        }

        net.setOwner(target.getUniqueID());
        sendSuccess(
            sender,
            "Set network owner: netId=" + netId
                + ", player="
                + target.getGameProfile()
                    .getName());
    }

    private void handleAddManager(ICommandSender sender, String[] args) {
        if (args.length < 4) {
            throw new WrongUsageException("commands.bdtools.network.addManager.usage");
        }
        int netId = parseIntBounded(sender, args[2], 0, Integer.MAX_VALUE);
        EntityPlayerMP target = CommandBase.getPlayer(sender, args[3]);

        DimensionsNet net = getNetOrFail(sender, netId);
        if (net == null) return;

        net.addManager(target.getUniqueID());
        sendSuccess(
            sender,
            "Added manager: netId=" + netId
                + ", player="
                + target.getGameProfile()
                    .getName());
    }

    private void handleRemoveManager(ICommandSender sender, String[] args) {
        if (args.length < 4) {
            throw new WrongUsageException("commands.bdtools.network.removeManager.usage");
        }
        int netId = parseIntBounded(sender, args[2], 0, Integer.MAX_VALUE);
        EntityPlayerMP target = CommandBase.getPlayer(sender, args[3]);

        DimensionsNet net = getNetOrFail(sender, netId);
        if (net == null) return;

        net.removeManager(target.getUniqueID());
        sendSuccess(
            sender,
            "Removed manager (downgraded to member): netId=" + netId
                + ", player="
                + target.getGameProfile()
                    .getName());
    }

    private void handleAddPlayer(ICommandSender sender, String[] args) {
        if (args.length < 4) {
            throw new WrongUsageException("commands.bdtools.network.addPlayer.usage");
        }
        int netId = parseIntBounded(sender, args[2], 0, Integer.MAX_VALUE);
        EntityPlayerMP target = CommandBase.getPlayer(sender, args[3]);

        DimensionsNet net = getNetOrFail(sender, netId);
        if (net == null) return;

        net.addPlayer(target.getUniqueID());
        sendSuccess(
            sender,
            "Added player to network: netId=" + netId
                + ", player="
                + target.getGameProfile()
                    .getName());
    }

    private void handleRemovePlayer(ICommandSender sender, String[] args) {
        if (args.length < 4) {
            throw new WrongUsageException("commands.bdtools.network.removePlayer.usage");
        }
        int netId = parseIntBounded(sender, args[2], 0, Integer.MAX_VALUE);
        EntityPlayerMP target = CommandBase.getPlayer(sender, args[3]);

        DimensionsNet net = getNetOrFail(sender, netId);
        if (net == null) return;

        net.removePlayer(target.getUniqueID());
        sendSuccess(
            sender,
            "Removed player from network: netId=" + netId
                + ", player="
                + target.getGameProfile()
                    .getName());
    }

    private void handleCreate(ICommandSender sender, String[] args) {
        if (args.length < 3) {
            throw new WrongUsageException("commands.bdtools.network.create.usage");
        }
        EntityPlayerMP target = CommandBase.getPlayer(sender, args[2]);

        long slotCapacity = Long.MAX_VALUE;
        int slotMaxSize = Integer.MAX_VALUE;
        if (args.length >= 4) {
            try {
                slotCapacity = Long.parseLong(args[3]);
            } catch (NumberFormatException e) {
                throw new WrongUsageException("commands.bdtools.network.create.invalidCapacity");
            }
            if (slotCapacity < 1) {
                throw new WrongUsageException("commands.bdtools.network.create.invalidCapacity");
            }
        }
        if (args.length >= 5) {
            slotMaxSize = parseIntBounded(sender, args[4], 1, Integer.MAX_VALUE);
        }

        // 对方不能已有网络
        DimensionsNet existing = DimensionsNet.getNetFromPlayer(target);
        if (existing != null) {
            sendFailure(
                sender,
                "Player already has a network: player=" + target.getGameProfile()
                    .getName() + ", netId=" + existing.getId());
            return;
        }

        DimensionsNet created = DimensionsNet.createNewNetForPlayer(target, slotCapacity, slotMaxSize);
        if (created == null) {
            sendFailure(sender, "Failed to create network.");
            return;
        }

        sendSuccess(
            sender,
            "Created network: netId=" + created.getId()
                + ", owner="
                + target.getGameProfile()
                    .getName()
                + ", slotCapacity="
                + slotCapacity
                + ", slotMaxSize="
                + slotMaxSize);
    }

    private void handleDeleteNet(ICommandSender sender, String[] args) {
        if (args.length < 3) {
            throw new WrongUsageException("commands.bdtools.network.deleteNet.usage");
        }
        int netId = parseIntBounded(sender, args[2], 0, Integer.MAX_VALUE);

        DimensionsNet net = getNetOrFail(sender, netId);
        if (net == null) return;

        net.destroySelf();
        sendSuccess(sender, "Deleted network: netId=" + netId + " (marked deleted).");
    }

    private void handleDeleteNetByPlayer(ICommandSender sender, String[] args) {
        if (args.length < 3) {
            throw new WrongUsageException("commands.bdtools.network.deleteNetByPlayer.usage");
        }
        EntityPlayerMP target = CommandBase.getPlayer(sender, args[2]);

        DimensionsNet net = DimensionsNet.getNetFromPlayer(target);
        if (net == null) {
            sendFailure(
                sender,
                "Player does not belong to any network: player=" + target.getGameProfile()
                    .getName());
            return;
        }

        int netId = net.getId();
        net.destroySelf();
        sendSuccess(
            sender,
            "Deleted the network that the player belongs to: player=" + target.getGameProfile()
                .getName() + ", netId=" + netId + ".");
    }

    // ==================== 辅助方法 ====================

    private DimensionsNet getNetOrFail(ICommandSender sender, int netId) {
        DimensionsNet net = DimensionsNet.getNetFromId(netId);
        if (net == null) {
            sendFailure(sender, "ID does not correspond to any network (or it was deleted).");
        }
        return net;
    }

    private void sendSuccess(ICommandSender sender, String message) {
        sender.addChatMessage(new ChatComponentText(message));
    }

    private void sendFailure(ICommandSender sender, String message) {
        IChatComponent component = new ChatComponentText(message);
        // 1.7.10 中红色文本使用格式化代码
        component.getChatStyle()
            .setColor(net.minecraft.util.EnumChatFormatting.RED);
        sender.addChatMessage(component);
    }
}
