package com.wintercogs.beyonddimensions.common.menu;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.nbt.NBTTagCompound;

import com.wintercogs.beyonddimensions.api.storage.handler.impl.StackHandler;
import com.wintercogs.beyonddimensions.common.machine.FuzzyMode;
import com.wintercogs.beyonddimensions.common.machine.PopMode;
import com.wintercogs.beyonddimensions.common.machine.RedStoneControlMode;
import com.wintercogs.beyonddimensions.common.menu.widget.slot.FlagStackTypedSlot;
import com.wintercogs.beyonddimensions.common.menu.widget.slot.OrderedStackTypedSlot;
import com.wintercogs.beyonddimensions.config.CommonConfigRuntime;

/**
 * 网络接口菜单（1.7.10 移植版）。
 * <p>
 * 1.7.10 适配：CommonTextures 常量内联（TOP_BASE_COMMON_HEIGHT=24, COMMON_SLOTS_HEIGHT=18,
 * FILTER_SLOTS_HEIGHT=18, COMMON_CONNECTION_HEIGHT=8）。
 * 移除 1.20.1 的 HolderLookup/RegistryOps/Codec 解码，客户端使用空 StackHandler。
 */
public class NetInterfaceBaseMenu extends BDBaseMenu {

    // 1 + TOP_BASE_COMMON_HEIGHT = 1 + 24 = 25
    private static final int slotStartY = 25;
    // 6 + 25 + COMMON_SLOTS_HEIGHT*3 + FILTER_SLOTS_HEIGHT*3 + COMMON_CONNECTION_HEIGHT
    // = 6 + 25 + 54 + 54 + 8 = 147
    private static final int invSlotStartY = 147;

    public StackHandler storage;
    public StackHandler flagStorage;

    private NetInterfaceAccess access;

    /**
     * 客户端构造函数
     */
    public NetInterfaceBaseMenu(InventoryPlayer playerInventory) {
        this(playerInventory, (NetInterfaceAccess) null);
    }

    /**
     * 服务端构造函数（携带真实的 NetInterfaceAccess）
     */
    public NetInterfaceBaseMenu(InventoryPlayer playerInventory, NetInterfaceAccess access) {
        super(playerInventory);

        if (access == null) {
            // 客户端或无 access 时使用与服务端一致的槽位数量
            ClientAccess clientAccess = new ClientAccess();
            this.storage = clientAccess.getStackHandler();
            this.flagStorage = clientAccess.getFakeStackHandler();
            this.access = clientAccess;
        } else {
            this.storage = access.getStackHandler();
            this.flagStorage = access.getFakeStackHandler();
            this.access = access;
        }

        addPlayerInv(playerInventory);
        addStorageSlots();
        addFlagSlots();
    }

    public NetInterfaceAccess getAccess() {
        return this.access;
    }

    private void addStorageSlots() {
        // 动态添加存储槽
        vanillaQuickMoveStartIndex = this.inventorySlots.size();

        final int slotCount = storage.getSlots();
        final int cols = 9; // 每行列数
        final int x0 = 8; // 起始 X
        final int y0 = slotStartY + 18; // 起始 Y（保持原偏移）
        final int dx = 18; // 横向间距
        final int dy = 36; // 纵向间距

        for (int i = 0; i < slotCount; i++) {
            int col = i % cols;
            int row = i / cols;
            int x = x0 + col * dx;
            int y = y0 + row * dy;

            this.addSlotToContainer(
                new OrderedStackTypedSlot(this, storage, i, inventoryStartIndex, inventoryEndIndex, x, y));
        }

        vanillaQuickMoveEndIndex = this.inventorySlots.size();
    }

    private void addFlagSlots() {
        // 动态添加标记槽
        final int slotCount = flagStorage.getSlots();
        final int cols = 9; // 每行列数
        final int x0 = 8; // 起始 X
        final int y0 = slotStartY; // 起始 Y
        final int dx = 18; // 横向间距
        final int dy = 36; // 纵向间距

        for (int i = 0; i < slotCount; i++) {
            int col = i % cols;
            int row = i / cols;
            int x = x0 + col * dx;
            int y = y0 + row * dy;

            this.addSlotToContainer(new FlagStackTypedSlot(this, flagStorage, i, x, y));
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

    // 模式状态缓存：仅变化时才发送 QuickDataTag 包（原实现恒返回 true，每个接口 GUI 每 tick 发包）
    private RedStoneControlMode lastControlMode = null;
    private FuzzyMode lastFuzzyMode = null;
    private PopMode lastPopMode = null;

    @Override
    protected boolean shouldSendQuickData() {
        if (access == null) {
            return false;
        }
        RedStoneControlMode controlMode = access.getControlMode();
        FuzzyMode fuzzyMode = access.getFuzzyMode();
        PopMode popMode = access.getPopMode();
        boolean changed = controlMode != lastControlMode || fuzzyMode != lastFuzzyMode || popMode != lastPopMode;
        lastControlMode = controlMode;
        lastFuzzyMode = fuzzyMode;
        lastPopMode = popMode;
        // 首次（缓存为 null）返回 true，向客户端下发初始模式状态
        return changed;
    }

    @Override
    protected void writeQuickDataTag(NBTTagCompound tag) {
        super.writeQuickDataTag(tag);
        if (access == null) return;
        tag.setString(
            "popMode",
            access.getPopMode()
                .name());
        tag.setString(
            "controlMode",
            access.getControlMode()
                .name());
        tag.setString(
            "fuzzyMode",
            access.getFuzzyMode()
                .name());
    }

    @Override
    public void readQuickDataTag(NBTTagCompound tag) {
        super.readQuickDataTag(tag);
        if (access == null || !access.isMenuValid()) {
            return;
        }
        if (access.canConfigurePopMode()) {
            try {
                access.setPopMode(PopMode.valueOf(tag.getString("popMode")));
            } catch (IllegalArgumentException ignored) {}
        }
        try {
            access.setControlMode(RedStoneControlMode.valueOf(tag.getString("controlMode")));
        } catch (IllegalArgumentException ignored) {}
        try {
            access.setFuzzyMode(FuzzyMode.valueOf(tag.getString("fuzzyMode")));
        } catch (IllegalArgumentException ignored) {}
        // 服务端读取新数据之后利用 markDirty/markBlockForUpdate 将数据发送给附近所有玩家
        if (!player.worldObj.isRemote) {
            access.onMenuDataChanged();
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return access != null && access.isMenuValid();
    }

    /**
     * 客户端占位 access（1.7.10 移植版）
     * <p>
     * 1.20.1 源码通过 FriendlyByteBuf + Codec 解码服务端发来的 StackHandler 数据。
     * 1.7.10 没有这些机制，客户端使用空 StackHandler，数据同步由 BDBaseMenu 的
     * detectAndSendChanges + 自定义 packet 机制处理。
     */
    private static final class ClientAccess implements NetInterfaceAccess {

        // 客户端使用与服务端一致的槽位数量，确保 GUI 槽位布局双端一致
        private final StackHandler stackHandler = new StackHandler(CommonConfigRuntime.interfaceUsableCapacity);
        private final StackHandler fakeStackHandler = new StackHandler(CommonConfigRuntime.interfaceUsableCapacity);
        private final NetInterfaceSettings settings = new NetInterfaceSettings();
        private RedStoneControlMode controlMode = RedStoneControlMode.IGNORE;

        @Override
        public StackHandler getStackHandler() {
            return this.stackHandler;
        }

        @Override
        public StackHandler getFakeStackHandler() {
            return this.fakeStackHandler;
        }

        @Override
        public NetInterfaceSettings getNetInterfaceSettings() {
            return this.settings;
        }

        @Override
        public RedStoneControlMode getControlMode() {
            return this.controlMode;
        }

        @Override
        public void setControlMode(RedStoneControlMode controlMode) {
            this.controlMode = controlMode;
        }

        @Override
        public boolean canConfigurePopMode() {
            return false;
        }

        @Override
        public boolean isMenuValid() {
            return true;
        }

        @Override
        public void onMenuDataChanged() {}
    }
}
