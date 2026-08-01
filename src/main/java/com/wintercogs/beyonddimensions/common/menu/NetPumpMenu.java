package com.wintercogs.beyonddimensions.common.menu;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.nbt.NBTTagCompound;

import com.wintercogs.beyonddimensions.api.storage.handler.IStackHandler;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.StackHandler;
import com.wintercogs.beyonddimensions.common.block.entity.NetPumpBlockEntity;
import com.wintercogs.beyonddimensions.common.machine.FilterMode;
import com.wintercogs.beyonddimensions.common.machine.RedStoneControlMode;
import com.wintercogs.beyonddimensions.common.menu.widget.slot.FlagStackTypedSlot;

/**
 * 网络泵菜单（1.7.10 移植版）。
 * <p>
 * 1.7.10 适配：CommonTextures 常量内联（TOP_BASE_COMMON_HEIGHT=24, FILTER_SLOTS_HEIGHT=18,
 * COMMON_CONNECTION_HEIGHT=8）。
 */
public class NetPumpMenu extends BDBaseMenu {

    // CommonTextures.TOP_BASE_COMMON_HEIGHT + 1 = 25
    private static final int slotStartY = 25;
    // 24 + 18*4 + 8 + 7 = 111
    private static final int invSlotStartY = 111;

    private IStackHandler storage;

    public final NetPumpBlockEntity be;

    /**
     * 客户端构造函数
     */
    public NetPumpMenu(InventoryPlayer playerInventory) {
        this(playerInventory, null);
    }

    /**
     * 服务端构造函数
     */
    public NetPumpMenu(InventoryPlayer playerInventory, NetPumpBlockEntity be) {
        super(playerInventory);

        this.be = be;

        if (be == null || player.worldObj.isRemote) {
            this.storage = new StackHandler(36);
        } else {
            this.storage = be.getFilterSlots();
        }

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

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return be != null && !be.isInvalid();
    }

    @Override
    protected boolean shouldSendQuickData() {
        return false;
    }

    @Override
    protected void writeQuickDataTag(NBTTagCompound tag) {
        super.writeQuickDataTag(tag);
        if (be == null) return;
        tag.setString("filter_type", be.filterMode.name());
        tag.setString("control_mode", be.controlMode.name());
    }

    @Override
    public void readQuickDataTag(NBTTagCompound tag) {
        super.readQuickDataTag(tag);
        if (be == null) return;
        try {
            be.filterMode = FilterMode.valueOf(tag.getString("filter_type"));
        } catch (IllegalArgumentException ignored) {}
        try {
            be.controlMode = RedStoneControlMode.valueOf(tag.getString("control_mode"));
        } catch (IllegalArgumentException ignored) {}
        if (!player.worldObj.isRemote) {
            be.markDirty();
            player.worldObj.markBlockForUpdate(be.xCoord, be.yCoord, be.zCoord);
        }
    }
}
