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
import com.wintercogs.beyonddimensions.common.machine.FuzzyMode;
import com.wintercogs.beyonddimensions.common.machine.ReceiveMode;
import com.wintercogs.beyonddimensions.common.machine.RedStoneControlMode;
import com.wintercogs.beyonddimensions.common.menu.widget.slot.FlagStackTypedSlot;

/**
 * 网络补货器菜单（1.7.10 移植版）。
 * <p>
 * 1.7.10 适配：CommonTextures 常量内联；InventoryMenu.EMPTY_ARMOR_SLOT_* →
 * 移除（1.7.10 无此背景图标资源），用 TODO 标注。
 */
public class NetRestockerMenu extends BDBaseMenu {

    // TOP_BASE_COMMON_HEIGHT + COMMON_CONNECTION_HEIGHT*2 + 1 = 24 + 16 + 1 = 41
    private static final int slotStartY = 41;
    // 24 + 16 + 72 + 8 + 7 = 127
    private static final int invSlotStartY = 127;
    public static final int EXTRA_SLOT_START_X = 8;
    // TOP_BASE_COMMON_HEIGHT - 1 = 23
    public static final int EXTRA_SLOT_Y = 23;

    private IStackHandler storage;
    private boolean initialized;

    // 非 final：1.7.10 openGui 流程可能导致背包 ItemStack 对象被替换，需经 refreshMenuStack 实时刷新（审计 M5-1）
    public ItemStack menuStack;

    private RedStoneControlMode lastControlMode;
    private FuzzyMode lastFuzzyMode;
    private ReceiveMode lastReceiveMode;

    /**
     * 客户端构造函数
     */
    public NetRestockerMenu(InventoryPlayer playerInventory) {
        this(playerInventory, null);
    }

    /**
     * 服务端构造函数
     */
    public NetRestockerMenu(InventoryPlayer playerInventory, ItemStack menuStack) {
        super(playerInventory);
        this.menuStack = menuStack;

        initialized = false;
        this.storage = new StackHandler(41) {

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

        // TODO: 1.20.1 使用 InventoryMenu.EMPTY_ARMOR_SLOT_HELMET/CHESTPLATE/LEGGINGS/BOOTS/SHIELD
        // 作为空槽位背景图标。1.7.10 没有这些资源，背景图标暂未实现。
        this.addSlotToContainer(createExtraSlot(36, EXTRA_SLOT_START_X, EXTRA_SLOT_Y));
        this.addSlotToContainer(createExtraSlot(37, EXTRA_SLOT_START_X + 18, EXTRA_SLOT_Y));
        this.addSlotToContainer(createExtraSlot(38, EXTRA_SLOT_START_X + 36, EXTRA_SLOT_Y));
        this.addSlotToContainer(createExtraSlot(39, EXTRA_SLOT_START_X + 54, EXTRA_SLOT_Y));
        this.addSlotToContainer(createExtraSlot(40, EXTRA_SLOT_START_X + 72, EXTRA_SLOT_Y));
    }

    private Slot createExtraSlot(int slotIndex, int x, int y) {
        return new FlagStackTypedSlot(this, storage, slotIndex, x, y);
    }

    private void addPlayerInv(InventoryPlayer playerInventory) {
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
     * 实时从玩家背包查找当前有效的补货器 ItemStack（审计 M5-1）。
     * 1.7.10 openGui 流程可能导致背包 ItemStack 对象被替换（同 NetFeederMenu 注释），
     * 不能依赖构造时缓存的 {@link #menuStack} 引用；写回 NBT 前必须先刷新。
     */
    public ItemStack refreshMenuStack() {
        if (player == null || player.inventory == null) return menuStack;
        ItemStack held = player.inventory.getCurrentItem();
        if (held != null && held.getItem() == BDItems.NET_RESTOCKER_ITEM) {
            this.menuStack = held;
            return held;
        }
        for (int i = 0; i < player.inventory.mainInventory.length; i++) {
            ItemStack stack = player.inventory.mainInventory[i];
            if (stack != null && stack.getItem() == BDItems.NET_RESTOCKER_ITEM) {
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
            || lastFuzzyMode != BaseMachineItem.getFuzzyModeOrDefault(menuStack, FuzzyMode.DISABLE)
            || lastReceiveMode != BaseMachineItem.getReceiveModeOrDefault(menuStack, ReceiveMode.STOP);

        if (result) {
            lastControlMode = BaseMachineItem.getControlModeOrDefault(menuStack, RedStoneControlMode.IGNORE);
            lastFuzzyMode = BaseMachineItem.getFuzzyModeOrDefault(menuStack, FuzzyMode.DISABLE);
            lastReceiveMode = BaseMachineItem.getReceiveModeOrDefault(menuStack, ReceiveMode.STOP);
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
            "fuzzy_mode",
            BaseMachineItem.getFuzzyModeOrDefault(menuStack, FuzzyMode.DISABLE)
                .name());
        tag.setString(
            "receive_mode",
            BaseMachineItem.getReceiveModeOrDefault(menuStack, ReceiveMode.STOP)
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
            BaseMachineItem.setFuzzyMode(menuStack, FuzzyMode.valueOf(tag.getString("fuzzy_mode")));
        } catch (IllegalArgumentException ignored) {}
        try {
            BaseMachineItem.setReceiveMode(menuStack, ReceiveMode.valueOf(tag.getString("receive_mode")));
        } catch (IllegalArgumentException ignored) {}
    }
}
