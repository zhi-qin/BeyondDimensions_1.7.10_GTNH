package com.wintercogs.beyonddimensions.common.menu;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.wintercogs.beyonddimensions.api.storage.handler.IStackHandler;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.StackHandler;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.common.init.BDItems;
import com.wintercogs.beyonddimensions.common.item.BaseMachineItem;
import com.wintercogs.beyonddimensions.common.machine.FilterMode;
import com.wintercogs.beyonddimensions.common.machine.HopperFluidMode;
import com.wintercogs.beyonddimensions.common.machine.HopperItemMode;
import com.wintercogs.beyonddimensions.common.machine.HopperNBTMode;
import com.wintercogs.beyonddimensions.common.machine.HopperRangeMode;
import com.wintercogs.beyonddimensions.common.machine.HopperXpMode;
import com.wintercogs.beyonddimensions.common.machine.RedStoneControlMode;
import com.wintercogs.beyonddimensions.common.menu.widget.slot.FlagStackTypedSlot;

/**
 * 网络磁铁菜单（1.7.10 移植版）。
 * <p>
 * 1.7.10 适配：CommonTextures 常量内联；InventoryHelper.findItemInPlayerInventory →
 * 由 BDGuiHandler 查找并传入 menuStack。
 */
public class NetMagnetMenu extends BDBaseMenu {

    // CommonTextures.TOP_BASE_COMMON_HEIGHT + 1 = 25
    private static final int slotStartY = 25;
    // 24 + 18*4 + 8 + 7 = 111
    private static final int invSlotStartY = 111;

    // storage的初始数据由itemStack提供，随后storage每次变化都重新向其中写入数据
    private IStackHandler storage;
    private boolean initialized;

    // 非 final：1.7.10 openGui 流程可能导致背包 ItemStack 对象被替换，需经 refreshMenuStack 实时刷新（审计 M5-1）
    public ItemStack menuStack;

    private RedStoneControlMode lastControlMode;
    private FilterMode lastFilterMode;
    private HopperItemMode lastHopperItemMode;
    private HopperXpMode lastHopperXpMode;
    private HopperNBTMode lastHopperNBTMode;
    private HopperFluidMode lastHopperFluidMode;
    private HopperRangeMode lastHopperRangeMode;

    /**
     * 客户端构造函数
     */
    public NetMagnetMenu(InventoryPlayer playerInventory) {
        this(playerInventory, null);
    }

    /**
     * 服务端构造函数
     */
    public NetMagnetMenu(InventoryPlayer playerInventory, ItemStack menuStack) {
        super(playerInventory);
        this.menuStack = menuStack;

        initialized = false;
        this.storage = new StackHandler(36) {

            @Override
            public void onChange() {
                super.onChange();
                if (!player.worldObj.isRemote && initialized) {
                    // 注意：此处不能写 storage.getStorage()，因为在匿名 StackHandler 子类内部
                    // storage 会解析到 StackHandler.storage (ArrayList) 而非外层 IStackHandler 字段
                    // 写回前必须先 refreshMenuStack，避免写到被替换的旧 menuStack 引用导致过滤标记丢失
                    ItemStack target = refreshMenuStack();
                    if (target != null) {
                        BaseMachineItem.setFilterSlots(target, new ArrayList<>(getStorage()));
                    }
                }
            }
        };

        // 为服务端注入真实数据，客户端由槽位同步
        if (menuStack != null && !player.worldObj.isRemote) {
            List<KeyAmount> stacks = BaseMachineItem.getFilterSlotsOrDefault(menuStack, new ArrayList<>());
            for (int i = 0; i < stacks.size(); i++) {
                storage.insert(
                    i,
                    stacks.get(i)
                        .key(),
                    stacks.get(i)
                        .amount(),
                    false);
            }
        }
        initialized = true;

        addPlayerInv(playerInventory);
        addFlagSlots();
    }

    private void addFlagSlots() {
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                FlagStackTypedSlot flagSlot = new FlagStackTypedSlot(
                    this,
                    storage,
                    row * 9 + col,
                    8 + col * 18,
                    slotStartY + row * 18);
                this.addSlotToContainer(flagSlot);
            }
        }
    }

    private void addPlayerInv(InventoryPlayer playerInventory) {
        // 添加背包以及快捷栏
        inventoryStartIndex = this.inventorySlots.size();
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlotToContainer(
                    new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, invSlotStartY + row * 18));
            }
        }
        for (int col = 0; col < 9; ++col) {
            this.addSlotToContainer(new Slot(playerInventory, col, 8 + col * 18, 4 + invSlotStartY + 3 * 18));
        }
        inventoryEndIndex = this.inventorySlots.size();
    }

    /**
     * 实时从玩家背包查找当前有效的磁铁 ItemStack（审计 M5-1）。
     * 1.7.10 openGui 流程可能导致背包 ItemStack 对象被替换（同 NetFeederMenu 注释），
     * 不能依赖构造时缓存的 {@link #menuStack} 引用；写回 NBT 前必须先刷新。
     */
    public ItemStack refreshMenuStack() {
        if (player == null || player.inventory == null) return menuStack;
        ItemStack held = player.inventory.getCurrentItem();
        if (held != null && held.getItem() == BDItems.NET_MAGNET_ITEM) {
            this.menuStack = held;
            return held;
        }
        for (int i = 0; i < player.inventory.mainInventory.length; i++) {
            ItemStack stack = player.inventory.mainInventory[i];
            if (stack != null && stack.getItem() == BDItems.NET_MAGNET_ITEM) {
                this.menuStack = stack;
                return stack;
            }
        }
        return menuStack; // 兜底：使用缓存引用
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        refreshMenuStack();
        return menuStack != null && menuStack.stackSize > 0;
    }

    @Override
    protected boolean shouldSendQuickData() {
        refreshMenuStack();
        if (menuStack == null) return false;
        boolean result = super.shouldSendQuickData()
            || lastControlMode != BaseMachineItem.getControlModeOrDefault(menuStack, RedStoneControlMode.IGNORE)
            || lastFilterMode != BaseMachineItem.getFilterModeOrDefault(menuStack, FilterMode.BLACK)
            || lastHopperItemMode != BaseMachineItem.getHopperItemModeOrDefault(menuStack, HopperItemMode.ALLOW)
            || lastHopperXpMode != BaseMachineItem.getHopperXpModeOrDefault(menuStack, HopperXpMode.DENY)
            || lastHopperNBTMode != BaseMachineItem.getHopperNBTModeOrDefault(menuStack, HopperNBTMode.DENY)
            || lastHopperFluidMode != BaseMachineItem.getHopperFluidModeOrDefault(menuStack, HopperFluidMode.DENY)
            || lastHopperRangeMode
                != BaseMachineItem.getHopperRangeModeOrDefault(menuStack, HopperRangeMode.RADIUS_MID);

        if (result) {
            lastControlMode = BaseMachineItem.getControlModeOrDefault(menuStack, RedStoneControlMode.IGNORE);
            lastFilterMode = BaseMachineItem.getFilterModeOrDefault(menuStack, FilterMode.BLACK);
            lastHopperItemMode = BaseMachineItem.getHopperItemModeOrDefault(menuStack, HopperItemMode.ALLOW);
            lastHopperXpMode = BaseMachineItem.getHopperXpModeOrDefault(menuStack, HopperXpMode.DENY);
            lastHopperNBTMode = BaseMachineItem.getHopperNBTModeOrDefault(menuStack, HopperNBTMode.DENY);
            lastHopperFluidMode = BaseMachineItem.getHopperFluidModeOrDefault(menuStack, HopperFluidMode.DENY);
            lastHopperRangeMode = BaseMachineItem.getHopperRangeModeOrDefault(menuStack, HopperRangeMode.RADIUS_MID);
        }

        return result;
    }

    @Override
    protected void writeQuickDataTag(NBTTagCompound tag) {
        super.writeQuickDataTag(tag);
        if (menuStack == null) return;
        tag.setString(
            "control_mode",
            BaseMachineItem.getControlModeOrDefault(menuStack, RedStoneControlMode.IGNORE)
                .name());
        tag.setString(
            "filter_type",
            BaseMachineItem.getFilterModeOrDefault(menuStack, FilterMode.BLACK)
                .name());
        tag.setString(
            "hopper_item_mode",
            BaseMachineItem.getHopperItemModeOrDefault(menuStack, HopperItemMode.ALLOW)
                .name());
        tag.setString(
            "hopper_xp_mode",
            BaseMachineItem.getHopperXpModeOrDefault(menuStack, HopperXpMode.DENY)
                .name());
        tag.setString(
            "hopper_nbt_mode",
            BaseMachineItem.getHopperNBTModeOrDefault(menuStack, HopperNBTMode.DENY)
                .name());
        tag.setString(
            "hopper_fluid_mode",
            BaseMachineItem.getHopperFluidModeOrDefault(menuStack, HopperFluidMode.DENY)
                .name());
        tag.setString(
            "hopper_range_mode",
            BaseMachineItem.getHopperRangeModeOrDefault(menuStack, HopperRangeMode.RADIUS_MID)
                .name());
    }

    @Override
    public void readQuickDataTag(NBTTagCompound tag) {
        super.readQuickDataTag(tag);
        if (menuStack == null) return;
        try {
            BaseMachineItem.setControlMode(menuStack, RedStoneControlMode.valueOf(tag.getString("control_mode")));
        } catch (IllegalArgumentException ignored) {}
        try {
            BaseMachineItem.setFilterMode(menuStack, FilterMode.valueOf(tag.getString("filter_type")));
        } catch (IllegalArgumentException ignored) {}
        try {
            BaseMachineItem.setHopperItemMode(menuStack, HopperItemMode.valueOf(tag.getString("hopper_item_mode")));
        } catch (IllegalArgumentException ignored) {}
        try {
            BaseMachineItem.setHopperXpMode(menuStack, HopperXpMode.valueOf(tag.getString("hopper_xp_mode")));
        } catch (IllegalArgumentException ignored) {}
        try {
            BaseMachineItem.setHopperNBTMode(menuStack, HopperNBTMode.valueOf(tag.getString("hopper_nbt_mode")));
        } catch (IllegalArgumentException ignored) {}
        try {
            BaseMachineItem.setHopperFluidMode(menuStack, HopperFluidMode.valueOf(tag.getString("hopper_fluid_mode")));
        } catch (IllegalArgumentException ignored) {}
        try {
            BaseMachineItem.setHopperRangeMode(menuStack, HopperRangeMode.valueOf(tag.getString("hopper_range_mode")));
        } catch (IllegalArgumentException ignored) {}
    }
}
