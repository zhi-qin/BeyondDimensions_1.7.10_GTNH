package com.wintercogs.beyonddimensions.common.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler;
import com.wintercogs.beyonddimensions.client.gui.*;
import com.wintercogs.beyonddimensions.common.block.entity.BaseNetFurnaceBlockEntity;
import com.wintercogs.beyonddimensions.common.block.entity.NetEnergyPathwayBlockEntity;
import com.wintercogs.beyonddimensions.common.block.entity.NetHopperBlockEntity;
import com.wintercogs.beyonddimensions.common.block.entity.NetInterfaceBlockEntity;
import com.wintercogs.beyonddimensions.common.block.entity.NetPumpBlockEntity;
import com.wintercogs.beyonddimensions.common.block.entity.NetTerminalBlockEntity;
import com.wintercogs.beyonddimensions.common.init.BDItems;
import com.wintercogs.beyonddimensions.common.item.NetTerminalItem;
import com.wintercogs.beyonddimensions.common.item.NetedItem;
import com.wintercogs.beyonddimensions.common.menu.*;

import cpw.mods.fml.common.network.IGuiHandler;

public class BDGuiHandler implements IGuiHandler {

    public static final int DIMENSIONS_NET_MENU = 0;
    public static final int DIMENSIONS_CRAFT_MENU = 1;
    public static final int NET_CONTROL_MENU = 2;
    public static final int NET_ENERGY_MENU = 3;
    public static final int NET_INTERFACE_MENU = 4;
    public static final int DIMENSIONS_CRAFT_MENU_TERMINAL = 5;
    public static final int NET_PUMP_MENU = 6;
    public static final int NET_HOPPER_MENU = 7;
    public static final int NET_FURNACE_MENU = 8;
    public static final int NET_MAGNET_MENU = 9;
    public static final int NET_FEEDER_MENU = 10;
    public static final int NET_RESTOCKER_MENU = 11;
    public static final int XP_EXCHANGE_MENU = 12;
    public static final int PRIMARY_NET_SWITCHER_MENU = 13;

    @Override
    public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        switch (ID) {
            case DIMENSIONS_NET_MENU: {
                DimensionsNet net = DimensionsNet.getNetFromPlayer(player);
                if (net != null) {
                    return new DimensionsNetMenu(player.inventory, net.getUnifiedStorage(), net);
                }
                return new DimensionsNetMenu(player.inventory);
            }
            case DIMENSIONS_CRAFT_MENU: {
                DimensionsNet net = DimensionsNet.getNetFromPlayer(player);
                if (net != null) {
                    return new DimensionsCraftMenu(player.inventory, net.getUnifiedStorage(), null);
                }
                return new DimensionsCraftMenu(player.inventory);
            }
            case NET_CONTROL_MENU:
                return new NetControlMenu(player.inventory);
            case NET_ENERGY_MENU: {
                TileEntity te = world.getTileEntity(x, y, z);
                if (te instanceof NetEnergyPathwayBlockEntity) {
                    return new NetEnergyMenu(player.inventory, (NetEnergyPathwayBlockEntity) te);
                }
                return new NetEnergyMenu(player.inventory);
            }
            case NET_INTERFACE_MENU: {
                TileEntity te = world.getTileEntity(x, y, z);
                if (te instanceof NetInterfaceBlockEntity) {
                    return new NetInterfaceBaseMenu(player.inventory, (NetInterfaceBlockEntity) te);
                }
                return new NetInterfaceBaseMenu(player.inventory);
            }
            case DIMENSIONS_CRAFT_MENU_TERMINAL: {
                TileEntity te = world.getTileEntity(x, y, z);
                if (te instanceof NetTerminalBlockEntity) {
                    net.minecraft.inventory.Container menu = ((NetTerminalBlockEntity) te)
                        .createMenu(player.inventory, player);
                    if (menu != null) {
                        return menu;
                    }
                }
                // 便携终端模式：从玩家背包查找 NetTerminalItem，读取绑定的网络与工艺槽
                ItemStack terminalStack = findTerminalStackInInventory(player);
                if (terminalStack != null) {
                    int netId = NetedItem.getNetId(terminalStack);
                    if (netId >= 0) {
                        DimensionsNet net = DimensionsNet.getNetFromId(netId);
                        if (net != null) {
                            AbstractUnorderedStackHandler storage = net.getUnifiedStorage();
                            ItemStack[] craftItems = loadCraftSlotsFromNBT(terminalStack);
                            return new DimensionsCraftMenuTerminal(player.inventory, storage, craftItems);
                        }
                    }
                }
                return new DimensionsCraftMenuTerminal(player.inventory);
            }
            case NET_PUMP_MENU: {
                TileEntity te = world.getTileEntity(x, y, z);
                if (te instanceof NetPumpBlockEntity) {
                    return new NetPumpMenu(player.inventory, (NetPumpBlockEntity) te);
                }
                return new NetPumpMenu(player.inventory);
            }
            case NET_HOPPER_MENU: {
                TileEntity te = world.getTileEntity(x, y, z);
                if (te instanceof NetHopperBlockEntity) {
                    return new NetHopperMenu(player.inventory, (NetHopperBlockEntity) te);
                }
                return new NetHopperMenu(player.inventory);
            }
            case NET_FURNACE_MENU: {
                TileEntity te = world.getTileEntity(x, y, z);
                if (te instanceof BaseNetFurnaceBlockEntity) {
                    return new NetFurnaceMenu(player.inventory, (BaseNetFurnaceBlockEntity) te);
                }
                return new NetFurnaceMenu(player.inventory);
            }
            case NET_MAGNET_MENU: {
                ItemStack magnet = findHeldFirstInInventory(player, BDItems.NET_MAGNET_ITEM);
                if (magnet == null) return null;
                return new NetMagnetMenu(player.inventory, magnet);
            }
            case NET_FEEDER_MENU: {
                ItemStack feeder = findFeederStackInInventory(player);
                if (feeder == null) return null;
                return new NetFeederMenu(player.inventory, feeder);
            }
            case NET_RESTOCKER_MENU: {
                ItemStack restocker = findHeldFirstInInventory(player, BDItems.NET_RESTOCKER_ITEM);
                if (restocker == null) return null;
                return new NetRestockerMenu(player.inventory, restocker);
            }
            case XP_EXCHANGE_MENU: {
                ItemStack xp = findHeldFirstInInventory(player, BDItems.XP_EXCHANGE_ITEM);
                if (xp == null) return null;
                return new XpExchangeMenu(player.inventory, xp);
            }
            case PRIMARY_NET_SWITCHER_MENU:
                return new PrimaryNetSwitcherMenu(player.inventory);
            default:
                return null;
        }
    }

    @Override
    public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        switch (ID) {
            case DIMENSIONS_NET_MENU: {
                DimensionsNet net = DimensionsNet.getNetFromPlayer(player);
                if (net != null) {
                    return new GuiDimensionsNet(player.inventory, net.getUnifiedStorage());
                }
                return new GuiDimensionsNet(player.inventory);
            }
            case DIMENSIONS_CRAFT_MENU: {
                DimensionsNet net = DimensionsNet.getNetFromPlayer(player);
                if (net != null) {
                    return new GuiDimensionsCraft(player.inventory, net.getUnifiedStorage(), null);
                }
                return new GuiDimensionsCraft(player.inventory);
            }
            case NET_CONTROL_MENU:
                return new GuiNetControl(player.inventory);
            case NET_ENERGY_MENU: {
                TileEntity te = world.getTileEntity(x, y, z);
                if (te instanceof NetEnergyPathwayBlockEntity) {
                    return new GuiNetEnergy(player.inventory, (NetEnergyPathwayBlockEntity) te);
                }
                return new GuiNetEnergy(player.inventory);
            }
            case NET_INTERFACE_MENU: {
                TileEntity te = world.getTileEntity(x, y, z);
                if (te instanceof NetInterfaceBlockEntity) {
                    return new GuiNetInterface(player.inventory, (NetInterfaceBlockEntity) te);
                }
                return new GuiNetInterface(player.inventory);
            }
            case DIMENSIONS_CRAFT_MENU_TERMINAL: {
                TileEntity te = world.getTileEntity(x, y, z);
                if (te instanceof NetTerminalBlockEntity) {
                    net.minecraft.inventory.Container menu = ((NetTerminalBlockEntity) te)
                        .createMenu(player.inventory, player);
                    if (menu instanceof DimensionsCraftMenuTerminal) {
                        return new GuiDimensionsCraftTerminal(player.inventory, (DimensionsCraftMenuTerminal) menu);
                    }
                    // 客户端方块 TE 的 getNet() 恒为 null（网络缓存仅服务端加载），createMenu 返回 null。
                    // 按与服务端一致的方块模式直接构造客户端菜单：存储内容经网络同步包
                    // （DisorderedSlotGroupSync/OrderedStackTypedSlot/QuickDataTag）写入菜单存储，
                    // 客户端无需本地网络引用；传 blockEntity 使菜单结构/关闭语义与方块终端对齐，
                    // 避免落入便携终端分支造成结构不对称（审计 M2-2）。
                    DimensionsCraftMenuTerminal blockMenu = new DimensionsCraftMenuTerminal(
                        player.inventory,
                        null,
                        null,
                        (NetTerminalBlockEntity) te);
                    return new GuiDimensionsCraftTerminal(player.inventory, blockMenu);
                }
                // 便携终端模式：与服务端对齐，从玩家背包查找 NetTerminalItem 并读取绑定的网络
                ItemStack terminalStack = findTerminalStackInInventory(player);
                if (terminalStack != null) {
                    int netId = NetedItem.getNetId(terminalStack);
                    if (netId >= 0) {
                        DimensionsNet net = DimensionsNet.getNetFromId(netId);
                        if (net != null) {
                            AbstractUnorderedStackHandler storage = net.getUnifiedStorage();
                            ItemStack[] craftItems = loadCraftSlotsFromNBT(terminalStack);
                            DimensionsCraftMenuTerminal menu = new DimensionsCraftMenuTerminal(
                                player.inventory,
                                storage,
                                craftItems);
                            return new GuiDimensionsCraftTerminal(player.inventory, menu);
                        }
                    }
                }
                return new GuiDimensionsCraftTerminal(player.inventory);
            }
            case NET_PUMP_MENU: {
                TileEntity te = world.getTileEntity(x, y, z);
                if (te instanceof NetPumpBlockEntity) {
                    return new GuiNetPump(player.inventory, (NetPumpBlockEntity) te);
                }
                return new GuiNetPump(player.inventory);
            }
            case NET_HOPPER_MENU: {
                TileEntity te = world.getTileEntity(x, y, z);
                if (te instanceof NetHopperBlockEntity) {
                    return new GuiNetHopper(player.inventory, (NetHopperBlockEntity) te);
                }
                return new GuiNetHopper(player.inventory);
            }
            case NET_FURNACE_MENU: {
                TileEntity te = world.getTileEntity(x, y, z);
                if (te instanceof BaseNetFurnaceBlockEntity) {
                    return new GuiNetFurnace(player.inventory, (BaseNetFurnaceBlockEntity) te);
                }
                return new GuiNetFurnace(player.inventory);
            }
            case NET_MAGNET_MENU: {
                ItemStack magnet = findHeldFirstInInventory(player, BDItems.NET_MAGNET_ITEM);
                if (magnet == null) return null;
                return new GuiNetMagnet(player.inventory, magnet);
            }
            case NET_FEEDER_MENU: {
                ItemStack feeder = findFeederStackInInventory(player);
                if (feeder == null) return null;
                return new GuiNetFeeder(player.inventory, feeder);
            }
            case NET_RESTOCKER_MENU: {
                ItemStack restocker = findHeldFirstInInventory(player, BDItems.NET_RESTOCKER_ITEM);
                if (restocker == null) return null;
                return new GuiNetRestocker(player.inventory, restocker);
            }
            case XP_EXCHANGE_MENU: {
                ItemStack xp = findHeldFirstInInventory(player, BDItems.XP_EXCHANGE_ITEM);
                if (xp == null) return null;
                return new GuiXpExchange(player.inventory, xp);
            }
            case PRIMARY_NET_SWITCHER_MENU:
                return new GuiPrimaryNetSwitcher(player.inventory);
            default:
                return null;
        }
    }

    /** 在玩家背包中查找指定 Item 的物品栈 */
    private static ItemStack findItemInInventory(EntityPlayer player, Item target) {
        if (player == null || player.inventory == null || target == null) return null;
        for (int i = 0; i < player.inventory.mainInventory.length; i++) {
            ItemStack stack = player.inventory.mainInventory[i];
            if (stack != null && stack.getItem() == target) {
                return stack;
            }
        }
        return null;
    }

    /**
     * 查找用于打开菜单的物品栈：优先手持物品（对齐源项目 use 的 getItemInHand 语义），
     * 回退到背包搜索。若直接返回背包中第一个匹配，当玩家手持的不是背包内第一个时，
     * GUI 会编辑错误的物品栈（磁铁/补货器/经验交换物品与喂食器 Bug 2 同类根因）。
     */
    private static ItemStack findHeldFirstInInventory(EntityPlayer player, Item target) {
        if (player == null || player.inventory == null || target == null) return null;
        ItemStack held = player.inventory.getCurrentItem();
        if (held != null && held.getItem() == target) {
            return held;
        }
        return findItemInInventory(player, target);
    }

    /**
     * 查找用于打开喂食器菜单的物品栈：优先手持物品（对齐源项目 NetFeederItem.use 的
     * {@code getItemInHand(MAIN_HAND)} 语义），回退到背包搜索。
     * 若直接返回背包中第一个喂食器，当玩家手持的不是背包内第一个时，GUI 会编辑错误的
     * 物品栈，导致关闭 UI 后手持喂食器的过滤槽仍为空（Bug 2 根因）。
     */
    private static ItemStack findFeederStackInInventory(EntityPlayer player) {
        if (player == null || player.inventory == null) return null;
        ItemStack held = player.inventory.getCurrentItem();
        if (held != null && held.getItem() == BDItems.NET_FEEDER_ITEM) {
            return held;
        }
        return findItemInInventory(player, BDItems.NET_FEEDER_ITEM);
    }

    /**
     * 在玩家背包中查找 NetTerminalItem（优先主手）。
     * 对齐源项目 NetTerminalItem.contextMap 的查找语义。
     */
    private static ItemStack findTerminalStackInInventory(EntityPlayer player) {
        if (player == null || player.inventory == null) return null;
        // 优先检查主手
        ItemStack mainHand = player.inventory.getCurrentItem();
        if (mainHand != null && mainHand.getItem() instanceof NetTerminalItem) {
            return mainHand;
        }
        // 回退：搜索整个背包
        for (int i = 0; i < player.inventory.mainInventory.length; i++) {
            ItemStack stack = player.inventory.mainInventory[i];
            if (stack != null && stack.getItem() instanceof NetTerminalItem) {
                return stack;
            }
        }
        return null;
    }

    /**
     * 从 terminalStack 的 NBT 读取 craft_slots（9 个工艺槽物品）。
     * 对齐源项目 NetTerminalItem.createMenu 中的 craft_slots 读取逻辑。
     */
    private static ItemStack[] loadCraftSlotsFromNBT(ItemStack terminalStack) {
        ItemStack[] craftItems = new ItemStack[9];
        if (terminalStack == null || !terminalStack.hasTagCompound()) {
            return craftItems;
        }
        NBTTagCompound tag = terminalStack.getTagCompound();
        if (tag.hasKey("craft_slots", 9)) {
            NBTTagList slotsTag = tag.getTagList("craft_slots", 10);
            for (int i = 0; i < 9 && i < slotsTag.tagCount(); i++) {
                NBTTagCompound itemTag = slotsTag.getCompoundTagAt(i);
                craftItems[i] = ItemStack.loadItemStackFromNBT(itemTag);
            }
        }
        return craftItems;
    }
}
