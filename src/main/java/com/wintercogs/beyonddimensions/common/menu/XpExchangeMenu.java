package com.wintercogs.beyonddimensions.common.menu;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.wintercogs.beyonddimensions.common.item.XpExchangeItem;
import com.wintercogs.beyonddimensions.common.item.XpExchangeSettings;

/**
 * 经验交换菜单（1.7.10 移植版）。
 * <p>
 * 1.7.10 适配：CommonTextures 常量内联（TOP_BASE_COMMON_HEIGHT=24，
 * COMMON_CONNECTION_HEIGHT=16，invSlotStartY = 24 + 16*5 + 7 = 111）；
 * CompoundTag → NBTTagCompound；stillValid → canInteractWith；
 * tag.putBoolean → setBoolean；tag.putInt → setInteger；
 * tag.contains → hasKey；menuStack.isEmpty() → menuStack == null。
 */
public class XpExchangeMenu extends BDBaseMenu {

    // CommonTextures.TOP_BASE_COMMON_HEIGHT + COMMON_CONNECTION_HEIGHT * 5 + 7 = 24 + 80 + 7 = 111
    private static final int invSlotStartY = 111;

    // 非 final：1.7.10 openGui 流程可能导致背包 ItemStack 对象被替换，需经 refreshMenuStack 实时刷新（审计 M5-1）
    public ItemStack menuStack;

    private boolean lastKeepMode;
    private int lastTargetLevel;

    /**
     * 客户端构造函数
     */
    public XpExchangeMenu(InventoryPlayer playerInventory) {
        this(playerInventory, null);
    }

    /**
     * 服务端构造函数
     */
    public XpExchangeMenu(InventoryPlayer playerInventory, ItemStack menuStack) {
        super(playerInventory);
        this.menuStack = menuStack;
        if (menuStack != null) {
            XpExchangeSettings.ensureComponents(this.menuStack);
        }
        addPlayerInv(playerInventory);
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
     * 实时从玩家背包查找当前有效的经验交换器 ItemStack（审计 M5-1）。
     * 1.7.10 openGui 流程可能导致背包 ItemStack 对象被替换（同 NetFeederMenu 注释），
     * 不能依赖构造时缓存的 {@link #menuStack} 引用；写回 NBT 前必须先刷新。
     */
    public ItemStack refreshMenuStack() {
        if (player == null || player.inventory == null) return menuStack;
        ItemStack held = player.inventory.getCurrentItem();
        if (held != null && held.getItem() instanceof XpExchangeItem) {
            this.menuStack = held;
            return held;
        }
        for (int i = 0; i < player.inventory.mainInventory.length; i++) {
            ItemStack stack = player.inventory.mainInventory[i];
            if (stack != null && stack.getItem() instanceof XpExchangeItem) {
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
        XpExchangeSettings.ensureComponents(menuStack);
        boolean currentKeepMode = XpExchangeItem.getOrDefaultXpNetKeepMode(menuStack, false);
        int currentTargetLevel = XpExchangeSettings.getTargetLevel(menuStack);
        boolean result = super.shouldSendQuickData() || lastKeepMode != currentKeepMode
            || lastTargetLevel != currentTargetLevel;

        if (result) {
            lastKeepMode = currentKeepMode;
            lastTargetLevel = currentTargetLevel;
        }

        return result;
    }

    @Override
    protected void writeQuickDataTag(NBTTagCompound tag) {
        super.writeQuickDataTag(tag);
        if (menuStack == null) return;
        tag.setBoolean("xp_keep_mode", XpExchangeItem.getOrDefaultXpNetKeepMode(menuStack, false));
        tag.setInteger("xp_target_level", XpExchangeSettings.getTargetLevel(menuStack));
    }

    @Override
    public void readQuickDataTag(NBTTagCompound tag) {
        super.readQuickDataTag(tag);
        if (menuStack == null) return;
        XpExchangeSettings.ensureComponents(menuStack);

        if (tag.hasKey("xp_keep_mode")) {
            XpExchangeItem.setXpNetKeepMode(menuStack, tag.getBoolean("xp_keep_mode"));
        }

        if (tag.hasKey("xp_target_level")) {
            XpExchangeSettings.setTargetLevel(menuStack, tag.getInteger("xp_target_level"));
        }
    }
}
