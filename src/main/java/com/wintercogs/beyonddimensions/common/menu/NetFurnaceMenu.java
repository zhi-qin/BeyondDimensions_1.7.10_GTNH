package com.wintercogs.beyonddimensions.common.menu;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.nbt.NBTTagCompound;

import com.wintercogs.beyonddimensions.api.storage.handler.IStackHandler;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.StackHandler;
import com.wintercogs.beyonddimensions.common.block.entity.BaseNetFurnaceBlockEntity;
import com.wintercogs.beyonddimensions.common.machine.AutoSortMode;
import com.wintercogs.beyonddimensions.common.machine.PopMode;
import com.wintercogs.beyonddimensions.common.machine.ReceiveMode;
import com.wintercogs.beyonddimensions.common.machine.RedStoneControlMode;
import com.wintercogs.beyonddimensions.common.menu.widget.slot.FlagStackTypedSlot;
import com.wintercogs.beyonddimensions.common.menu.widget.slot.OrderedStackTypedSlot;

/**
 * 网络熔炉菜单（1.7.10 移植版）。
 * <p>
 * 1.7.10 适配：AbstractContainerMenu → Container（经 BDBaseMenu）；
 * MenuType → 移除；FriendlyByteBuf → 移除；Inventory → InventoryPlayer；
 * addSlot → addSlotToContainer；stillValid → canInteractWith。
 */
public class NetFurnaceMenu extends BDBaseMenu {

    private static final int invSlotStartY = 128;

    private IStackHandler inputFilterSlots;
    private IStackHandler fuelFilterSlots;
    private IStackHandler inputStorageSlots;
    private IStackHandler outputStorageSlots;
    private IStackHandler fuelStorageSlots;
    private IStackHandler fuelReturnSlots;

    // 用于对比上一tick所用的信息缓存
    private List<Integer> lastLitTime = new ArrayList<>();
    private List<Integer> lastLitDuration = new ArrayList<>();
    private List<Integer> lastCookTime = new ArrayList<>();
    private List<Integer> lastCookTimeTotal = new ArrayList<>();

    public final BaseNetFurnaceBlockEntity be;

    /**
     * 客户端构造函数（仅使用 InventoryPlayer，创建临时存储）
     */
    public NetFurnaceMenu(InventoryPlayer playerInventory) {
        this(playerInventory, null);
    }

    /**
     * 服务端构造函数（携带真实的 BlockEntity）
     */
    public NetFurnaceMenu(InventoryPlayer playerInventory, BaseNetFurnaceBlockEntity be) {
        super(playerInventory);

        this.be = be;

        if (be == null || player.worldObj.isRemote) {
            // 客户端或无 TE 时使用临时存储
            int filterCap = be != null ? be.getFilterCapacity() : 8;
            int cap = be != null ? be.getCapacity() : 9;
            int fuelCap = be != null ? be.getFuelCapacity() : 1;
            this.inputFilterSlots = new StackHandler(filterCap);
            this.fuelFilterSlots = new StackHandler(filterCap);
            this.inputStorageSlots = new StackHandler(cap);
            this.outputStorageSlots = new StackHandler(cap);
            this.fuelStorageSlots = new StackHandler(fuelCap);
            this.fuelReturnSlots = new StackHandler(fuelCap);
        } else {
            this.inputFilterSlots = be.getInputFilterSlots();
            this.fuelFilterSlots = be.getFuelFilterSlots();
            this.inputStorageSlots = be.getInputStorageSlots();
            this.outputStorageSlots = be.getOutputStorageSlots();
            this.fuelStorageSlots = be.getFuelStorageSlots();
            this.fuelReturnSlots = be.getFuelReturnSlots();
        }

        addPlayerInv(playerInventory);
        addFilterSlots();
        addStorageSlots();
    }

    private void addFilterSlots() {
        for (int i = 0; i < 8; i++) {
            FlagStackTypedSlot flagSlot = new FlagStackTypedSlot(this, inputFilterSlots, i, 7, 38 + i * 18);
            this.addSlotToContainer(flagSlot);
        }
        for (int i = 0; i < 8; i++) {
            FlagStackTypedSlot flagSlot = new FlagStackTypedSlot(this, fuelFilterSlots, i, 207, 38 + i * 18);
            this.addSlotToContainer(flagSlot);
        }
    }

    private void addStorageSlots() {
        vanillaQuickMoveStartIndex = this.inventorySlots.size();
        for (int i = 0; i < 9; i++) {
            OrderedStackTypedSlot storageSlot = new OrderedStackTypedSlot(
                this,
                inputStorageSlots,
                i,
                inventoryStartIndex,
                inventoryEndIndex,
                31 + i * 19,
                38);
            this.addSlotToContainer(storageSlot);
        }
        // 燃料
        this.addSlotToContainer(
            new OrderedStackTypedSlot(this, fuelStorageSlots, 0, inventoryStartIndex, inventoryEndIndex, 207, 186));
        vanillaQuickMoveEndIndex = this.inventorySlots.size();
        // 燃料返回物槽
        this.addSlotToContainer(
            new OrderedStackTypedSlot(this, fuelReturnSlots, 0, inventoryStartIndex, inventoryEndIndex, 7, 186));
        // 输出槽
        for (int i = 0; i < 9; i++) {
            OrderedStackTypedSlot storageSlot = new OrderedStackTypedSlot(
                this,
                outputStorageSlots,
                i,
                inventoryStartIndex,
                inventoryEndIndex,
                31 + i * 19,
                90);
            this.addSlotToContainer(storageSlot);
        }
    }

    private void addPlayerInv(InventoryPlayer playerInventory) {
        // 添加背包以及快捷栏
        inventoryStartIndex = this.inventorySlots.size();
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlotToContainer(
                    new Slot(playerInventory, col + row * 9 + 9, 35 + col * 18, invSlotStartY + row * 18));
            }
        }
        for (int col = 0; col < 9; ++col) {
            this.addSlotToContainer(new Slot(playerInventory, col, 35 + col * 18, 4 + invSlotStartY + 3 * 18));
        }
        inventoryEndIndex = this.inventorySlots.size();
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return be != null && !be.isInvalid();
    }

    // 服务端在数值不同时主动发送消息
    @Override
    protected boolean shouldSendQuickData() {
        if (be == null) return false;
        boolean shouldSendQuickData = super.shouldSendQuickData() || !eq(lastLitTime, be.getLitTime())
            || !eq(lastLitDuration, be.getLitDuration())
            || !eq(lastCookTime, be.getCookTime())
            || !eq(lastCookTimeTotal, be.getCookTimeTotal());
        if (shouldSendQuickData) {
            lastLitTime = new ArrayList<>(be.getLitTime());
            lastLitDuration = new ArrayList<>(be.getLitDuration());
            lastCookTime = new ArrayList<>(be.getCookTime());
            lastCookTimeTotal = new ArrayList<>(be.getCookTimeTotal());
        }
        return shouldSendQuickData;
    }

    private static boolean eq(List<Integer> a, List<Integer> b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    @Override
    protected void writeQuickDataTag(NBTTagCompound tag) {
        super.writeQuickDataTag(tag);
        if (be == null) return;
        tag.setString("pop_mode", be.popMode.name());
        tag.setString("receive_mode", be.receiveMode.name());
        tag.setString("control_mode", be.controlMode.name());
        tag.setString("sort_mode", be.sortMode.name());
        tag.setIntArray("lit_time", toIntArray(be.getLitTime()));
        tag.setIntArray("lit_duration", toIntArray(be.getLitDuration()));
        tag.setIntArray("cook_time", toIntArray(be.getCookTime()));
        tag.setIntArray("cook_time_total", toIntArray(be.getCookTimeTotal()));
    }

    @Override
    public void readQuickDataTag(NBTTagCompound tag) {
        super.readQuickDataTag(tag);
        if (be == null) return;
        try {
            be.popMode = PopMode.valueOf(tag.getString("pop_mode"));
        } catch (IllegalArgumentException ignored) {}
        try {
            be.receiveMode = ReceiveMode.valueOf(tag.getString("receive_mode"));
        } catch (IllegalArgumentException ignored) {}
        try {
            be.controlMode = RedStoneControlMode.valueOf(tag.getString("control_mode"));
        } catch (IllegalArgumentException ignored) {}
        try {
            be.sortMode = AutoSortMode.valueOf(tag.getString("sort_mode"));
        } catch (IllegalArgumentException ignored) {}
        if (!player.worldObj.isRemote) // 服务端读取按钮信息
        {
            be.markDirty();
            player.worldObj.markBlockForUpdate(be.xCoord, be.yCoord, be.zCoord);
        } else // 客户端读取全部信息
        {
            be.setLitTime(toIntList(tag.getIntArray("lit_time")));
            be.setLitDuration(toIntList(tag.getIntArray("lit_duration")));
            be.setCookTime(toIntList(tag.getIntArray("cook_time")));
            be.setCookTimeTotal(toIntList(tag.getIntArray("cook_time_total")));
        }
    }

    private static int[] toIntArray(List<Integer> list) {
        int[] arr = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }

    private static List<Integer> toIntList(int[] arr) {
        List<Integer> list = new ArrayList<>(arr.length);
        for (int v : arr) {
            list.add(v);
        }
        return list;
    }
}
