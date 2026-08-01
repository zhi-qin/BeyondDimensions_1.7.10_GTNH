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
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.item.BaseMachineItem;
import com.wintercogs.beyonddimensions.common.item.NetFeederItem;
import com.wintercogs.beyonddimensions.common.machine.FeederMode;
import com.wintercogs.beyonddimensions.common.machine.RedStoneControlMode;
import com.wintercogs.beyonddimensions.common.menu.widget.slot.FlagStackTypedSlot;

/**
 * 网络喂食器菜单（1.7.10 移植版）。
 * <p>
 * 1.7.10 适配：CommonTextures 常量内联；getFoodProperties → isEdibleFood（可食用判断）。
 * stack.isEmpty() → stack == null。
 */
public class NetFeederMenu extends BDBaseMenu {

    // CommonTextures.TOP_BASE_COMMON_HEIGHT + 1 = 25
    private static final int slotStartY = 25;
    // 24 + 18*4 + 8 + 7 = 111
    private static final int invSlotStartY = 111;

    // storage的初始数据由itemStack提供，随后storage每次变化都重新向其中写入数据
    private IStackHandler storage;
    private boolean initialized; // initialized必须在初始数据提供完成之后才能设置为true

    /**
     * 当前喂食器物品引用。1.7.10 中 {@code player.openGui} 流程可能导致背包
     * ItemStack 对象被替换，因此此字段会通过 {@link #findCurrentFeederStack()}
     * 实时刷新，不保证与构造时传入的对象相同。
     */
    public ItemStack menuStack;

    private RedStoneControlMode lastControlMode;
    private FeederMode lastFeederMode;

    /**
     * 客户端构造函数
     */
    public NetFeederMenu(InventoryPlayer playerInventory) {
        this(playerInventory, null);
    }

    /**
     * 服务端构造函数
     */
    public NetFeederMenu(InventoryPlayer playerInventory, ItemStack menuStack) {
        super(playerInventory);
        this.menuStack = menuStack;

        initialized = false;
        // 创建带回调的 storage
        this.storage = new StackHandler(36) {

            @Override
            public void onChange() {
                super.onChange();
                if (!player.worldObj.isRemote && initialized) {
                    // 注意：此处不能写 storage.getStorage()，因为在匿名 StackHandler 子类内部
                    // storage 会解析到 StackHandler.storage (ArrayList) 而非外层 IStackHandler 字段
                    List<KeyAmount> snapshot = new ArrayList<>(getStorage());
                    // 1.7.10 中 player.openGui 流程可能导致背包 ItemStack 对象被替换，
                    // 缓存的 menuStack 引用可能指向旧对象。实时从背包查找确保写入当前有效的 ItemStack。
                    ItemStack target = findCurrentFeederStack();
                    if (target != null) {
                        BaseMachineItem.setFilterSlots(target, snapshot);
                    }
                }
            }

            @Override
            public boolean isStackValid(int slot, IStackKey<?> stack) {
                if (!super.isStackValid(slot, stack)) return false;
                if (stack instanceof ItemStackKey itemStackKey) {
                    ItemStack readOnly = itemStackKey.getReadOnlyStack();
                    // 1.7.10 适配：getFoodProperties(player) != null → 可食用判断
                    // （ItemFood 或覆盖 getItemUseAction 返回 eat 的自定义可食用物品）
                    return readOnly != null && NetFeederItem.isEdibleFood(readOnly);
                }
                return false;
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

    /**
     * 实时从玩家背包查找当前有效的喂食器 ItemStack。
     * 1.7.10 中 {@code player.openGui} 流程可能导致背包 ItemStack 对象被替换（不同于
     * 1.20.1 的 {@code NetworkHooks.openScreen} 直接传递引用），因此不能依赖构造时
     * 缓存的 {@link #menuStack} 引用，必须每次从背包实时获取。
     */
    public ItemStack findCurrentFeederStack() {
        ItemStack held = player.inventory.getCurrentItem();
        if (held != null && held.getItem() instanceof NetFeederItem) {
            this.menuStack = held;
            return held;
        }
        for (int i = 0; i < player.inventory.mainInventory.length; i++) {
            ItemStack stack = player.inventory.mainInventory[i];
            if (stack != null && stack.getItem() instanceof NetFeederItem) {
                this.menuStack = stack;
                return stack;
            }
        }
        return menuStack; // 兜底：使用缓存引用
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
     * Shift+点击玩家背包物品时，将物品标记到第一个空的过滤槽位。
     * <p>
     * 源码项目中 {@code vanillaQuickMoveStartIndex/EndIndex} 未设置，Shift+点击路径同样无效。
     * 源码项目的标记方式是「拾起物品→点击过滤槽」，但用户习惯 Shift+点击标记，此处补充支持。
     * 注意：不扣除背包物品（过滤槽仅做标记），且仅标记可食用物品。
     */
    @Override
    public void customClickHandler(int slotIndex, KeyAmount clickedStack, int button, boolean shiftDown) {
        if (shiftDown && slotIndex >= inventoryStartIndex && slotIndex < inventoryEndIndex && !clickedStack.isEmpty()) {
            // 校验是否为可食用物品（与 isStackValid 一致）
            if (clickedStack.key() instanceof ItemStackKey itemKey) {
                ItemStack readOnly = itemKey.getReadOnlyStack();
                if (readOnly != null && NetFeederItem.isEdibleFood(readOnly)) {
                    for (int i = 0; i < storage.getSlots(); i++) {
                        if (storage.getStackBySlot(i)
                            .isEmpty()) {
                            storage.setStackDirectly(i, clickedStack.key(), 1);
                            break;
                        }
                    }
                }
            }
            return;
        }
        super.customClickHandler(slotIndex, clickedStack, button, shiftDown);
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        ItemStack current = findCurrentFeederStack();
        return current != null && current.stackSize > 0;
    }

    @Override
    protected boolean shouldSendQuickData() {
        ItemStack current = findCurrentFeederStack();
        if (current == null) return false;
        boolean result = super.shouldSendQuickData()
            || lastControlMode != BaseMachineItem.getControlModeOrDefault(current, RedStoneControlMode.IGNORE)
            || lastFeederMode != BaseMachineItem.getFeederModeOrDefault(current, FeederMode.NORMAL);

        if (result) {
            lastControlMode = BaseMachineItem.getControlModeOrDefault(current, RedStoneControlMode.IGNORE);
            lastFeederMode = BaseMachineItem.getFeederModeOrDefault(current, FeederMode.NORMAL);
        }

        return result;
    }

    @Override
    protected void writeQuickDataTag(NBTTagCompound tag) {
        super.writeQuickDataTag(tag);
        ItemStack current = findCurrentFeederStack();
        if (current == null) return;
        tag.setString(
            "control_mode",
            BaseMachineItem.getControlModeOrDefault(current, RedStoneControlMode.IGNORE)
                .name());
        tag.setString(
            "feeder_mode",
            BaseMachineItem.getFeederModeOrDefault(current, FeederMode.NORMAL)
                .name());
    }

    @Override
    public void readQuickDataTag(NBTTagCompound tag) {
        super.readQuickDataTag(tag);
        ItemStack current = findCurrentFeederStack();
        if (current == null) return;
        try {
            BaseMachineItem.setControlMode(current, RedStoneControlMode.valueOf(tag.getString("control_mode")));
        } catch (IllegalArgumentException ignored) {}
        try {
            BaseMachineItem.setFeederMode(current, FeederMode.valueOf(tag.getString("feeder_mode")));
        } catch (IllegalArgumentException ignored) {}
    }
}
