package com.wintercogs.beyonddimensions.common.menu;

import java.util.*;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;

import com.wintercogs.beyonddimensions.api.ButtonState;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.UnorderedStackHandlerRemoveZero;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.init.BDPackets;
import com.wintercogs.beyonddimensions.common.menu.widget.ClientNetStorage;
import com.wintercogs.beyonddimensions.common.menu.widget.slot.AbstractStackTypedSlot;
import com.wintercogs.beyonddimensions.common.menu.widget.slot.DisorderedSlotGroupSync;
import com.wintercogs.beyonddimensions.common.menu.widget.slot.DisorderedStackTypedSlot;
import com.wintercogs.beyonddimensions.config.CommonConfigRuntime;
import com.wintercogs.beyonddimensions.network.packet.s2c.SyncEuStoragePacket;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 打开维度网络时候所用到的Menu（1.7.10 移植版）。
 * 处理网络同步以及点击操作等问题。
 * <p>
 * 1.7.10 适配：MenuType → 移除；FriendlyByteBuf → 移除；
 * player.level().isClientSide() → player.worldObj.isRemote；
 * slot.y → slot.yDisplayPosition。
 */
public class DimensionsNetMenu extends BDBaseMenu {

    /// 客户端数据
    public int maxLines = 6;
    public int lineData = 0;
    public int maxLineData = 0;
    private String searchText = "";
    public AbstractUnorderedStackHandler storage;
    public ClientNetStorage clientNetStorage;

    /** 存储槽位池行数上限（含屏幕外非活跃槽位，供 NEI 合成链精准暴露使用，方案 A） */
    public static final int MAX_STORAGE_ROWS = 99;

    /**
     * NEI 合成链精准暴露（客户端专用）：按存储槽位绝对位置索引的非活跃槽位条目，null=空（方案 A）。
     * 注意：此处禁止写 `= null` 初始化器 —— @SideOnly(CLIENT) 字段在专用服务端会被 FML 剥离，
     * 而构造器里的 putfield 指令不会被剥离，服务端构造本菜单时即抛 NoSuchFieldError 崩服；
     * 引用类型默认值即为 null，无需显式初始化（BUGFIX_RECORD #97）。
     */
    @SideOnly(Side.CLIENT)
    private KeyAmount[] neiExposure;

    /** EU 池存量（十进制字符串），由服务端 SyncEuStoragePacket 同步（移植新增）。 */
    public String euAmount = "0";

    /** 服务端持有的网络引用，用于 detectAndSendChanges 时同步 EU 池（客户端为 null）。 */
    private DimensionsNet serverNet;
    private String lastEuAmountSent;

    public boolean hasShiftDown = false;

    protected int storageStartIndex;
    protected int storageEndIndex;

    /**
     * 服务端构造函数
     */
    public DimensionsNetMenu(InventoryPlayer playerInventory, AbstractUnorderedStackHandler data) {
        this(playerInventory, data, null);
    }

    /**
     * 服务端构造函数（携带网络引用，供 EU 池同步；非网络终端菜单传 null）
     */
    public DimensionsNetMenu(InventoryPlayer playerInventory, AbstractUnorderedStackHandler data,
        DimensionsNet serverNet) {
        super(playerInventory);

        this.serverNet = serverNet;

        if (player.worldObj.isRemote) {
            this.maxLines = CommonConfigRuntime.uiPageNum;
            this.searchText = CommonConfigRuntime.uiSearch;
        }

        this.storage = data;
        if (player.worldObj.isRemote) {
            clientNetStorage = new ClientNetStorage(storage);
        } else {
            clientNetStorage = null;
        }

        addSlotGroupSync(new DisorderedSlotGroupSync(this, slotGroupSyncs.size(), storage) {

            @Override
            @SideOnly(Side.CLIENT)
            public void afterLoadChange() {
                updateViewerStorage(hasShiftDown);
            }
        });

        addPlayerInv(playerInventory);
        addStorageSlots();
    }

    /**
     * 客户端构造函数（从 TileEntity 获取存储）
     */
    public DimensionsNetMenu(InventoryPlayer playerInventory) {
        this(
            playerInventory,
            new UnorderedStackHandlerRemoveZero(AbstractUnorderedStackHandler.UiTimestampPolicy.NONE));
    }

    protected void addStorageSlots() {
        storageStartIndex = inventorySlots.size();
        vanillaQuickMoveStartIndex = storageStartIndex;
        if (player.worldObj.isRemote && clientNetStorage != null) {
            for (int row = 0; row < MAX_STORAGE_ROWS; ++row) {
                for (int col = 0; col < 9; ++col) {
                    DisorderedStackTypedSlot newSlot = new DisorderedStackTypedSlot(
                        this,
                        clientNetStorage,
                        -1,
                        inventoryStartIndex,
                        inventoryEndIndex,
                        8 + col * 18,
                        25 + row * 18);
                    newSlot.setStorageSlotPosition(row * 9 + col);
                    if (row >= getLines()) {
                        newSlot.setActive(false);
                        // 1.7.10 的 GuiContainer 不认识 active 概念，会渲染并悬停判定全部槽位，
                        // 构造时即需将非活跃槽位移出屏幕，否则其坐标落在物品栏区域造成高亮/点击错位
                        newSlot.yDisplayPosition = -9999;
                    }
                    this.addSlotToContainer(newSlot);
                }
            }
        } else {
            for (int row = 0; row < MAX_STORAGE_ROWS; ++row) {
                for (int col = 0; col < 9; ++col) {
                    DisorderedStackTypedSlot newSlot = new DisorderedStackTypedSlot(
                        this,
                        storage,
                        -1,
                        inventoryStartIndex,
                        inventoryEndIndex,
                        8 + col * 18,
                        25 + row * 18);
                    newSlot.setStorageSlotPosition(row * 9 + col);
                    if (row >= getLines()) {
                        newSlot.setActive(false);
                        // 1.7.10 的 GuiContainer 不认识 active 概念，会渲染并悬停判定全部槽位，
                        // 构造时即需将非活跃槽位移出屏幕，否则其坐标落在物品栏区域造成高亮/点击错位
                        newSlot.yDisplayPosition = -9999;
                    }
                    this.addSlotToContainer(newSlot);
                }
            }
        }
        storageEndIndex = inventorySlots.size();
        vanillaQuickMoveEndIndex = storageEndIndex;
    }

    protected void addPlayerInv(InventoryPlayer playerInventory) {
        inventoryStartIndex = inventorySlots.size();
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlotToContainer(
                    new Slot(
                        playerInventory,
                        col + row * 9 + 9,
                        8 + col * 18,
                        25 + (getLines() - 1) * 18 + 26 + 6 + row * 18));
            }
        }
        for (int col = 0; col < 9; ++col) {
            this.addSlotToContainer(
                new Slot(playerInventory, col, 8 + col * 18, 25 + (getLines() - 1) * 18 + 26 + 6 + 3 * 18 + 4));
        }
        inventoryEndIndex = inventorySlots.size();
    }

    public void rebuildSlots() {
        int sSlotNum = 0;
        for (int i = 0; i < inventorySlots.size(); i++) {
            Slot slot = (Slot) inventorySlots.get(i);
            if (slot instanceof AbstractStackTypedSlot) {
                AbstractStackTypedSlot sSlot = (AbstractStackTypedSlot) slot;
                boolean shouldBeActive = sSlotNum / 9 < getLines();
                sSlot.setActive(shouldBeActive);
                // 1.7.10 的 GuiContainer 不认识 active 概念，
                // 需将非活跃槽位移出屏幕，避免其在 GUI 背景外渲染并被点击
                if (shouldBeActive) {
                    sSlot.yDisplayPosition = 25 + (sSlotNum / 9) * 18;
                } else {
                    sSlot.yDisplayPosition = -9999;
                }
                sSlotNum++;
            }
        }

        int slotNum = 0;
        for (int i = inventoryStartIndex; i < inventoryEndIndex; ++i) {
            Slot slot = (Slot) inventorySlots.get(i);
            if (slotNum / 9 < 3) {
                slot.yDisplayPosition = 25 + (getLines() - 1) * 18 + 26 + 6 + slotNum / 9 * 18;
            } else {
                slot.yDisplayPosition = 25 + (getLines() - 1) * 18 + 26 + 6 + 3 * 18 + 4;
            }
            slotNum++;
        }
    }

    public int getLines() {
        return maxLines;
    }

    public void reduceLines() {
        maxLines--;
    }

    public void addLines() {
        maxLines++;
    }

    public void setLines(int lines) {
        this.maxLines = lines;
    }

    /**
     * 客户端专用：使用当前客户端的真存储来更新视觉存储
     */
    @SideOnly(Side.CLIENT)
    public void updateViewerStorage(boolean onlyAmountUpdate) {
        if (clientNetStorage == null) return;

        clientNetStorage.resolvePendingOrAllUpdate(onlyAmountUpdate);
        if (!onlyAmountUpdate) buildIndexList();
    }

    /**
     * 当确定真存储不会变化，但是排序可能发生变化时调用
     */
    @SideOnly(Side.CLIENT)
    public void buildIndexList() {
        if (!this.player.worldObj.isRemote || clientNetStorage == null) {
            return;
        }

        List<Integer> indexes = clientNetStorage.buildSortedIndex(
            CommonConfigRuntime.uiSortButton,
            CommonConfigRuntime.uiSecondSortButton,
            CommonConfigRuntime.uiReverseButton == ButtonState.ENABLED);

        updateScrollLineData(indexes.size());

        ArrayList<Integer> indexList = new ArrayList<>();
        for (int i = 0; i < getLines() * 9; i++) {
            if (i + lineData * 9 < indexes.size()) {
                indexList.add(indexes.get(i + lineData * 9));
            } else {
                indexList.add(-1);
            }
        }
        loadIndexList(indexList);
    }

    public void loadIndexList(ArrayList<Integer> list) {
        int listIndex = 0;
        for (int slotIndex = storageStartIndex; listIndex < list.size() && slotIndex < storageEndIndex; slotIndex++) {
            ((AbstractStackTypedSlot) inventorySlots.get(slotIndex)).setTheSlotIndex(list.get(listIndex));
            listIndex++;
        }
    }

    /// ===== NEI 合成链精准暴露（方案 A，客户端专用）=====

    @SideOnly(Side.CLIENT)
    public void setNeiExposure(KeyAmount[] exposure) {
        this.neiExposure = exposure;
    }

    @SideOnly(Side.CLIENT)
    public void clearNeiExposure() {
        this.neiExposure = null;
    }

    /**
     * 供非活跃槽位 {@link com.wintercogs.beyonddimensions.common.menu.widget.slot.DisorderedStackTypedSlot#getStack}
     * 读取 NEI 合成链暴露条目（null=空）。
     */
    @SideOnly(Side.CLIENT)
    public KeyAmount getNeiExposureEntry(int storageSlotPosition) {
        if (neiExposure == null || storageSlotPosition < 0 || storageSlotPosition >= neiExposure.length) return null;
        return neiExposure[storageSlotPosition];
    }

    /**
     * 当前页（活跃槽位）显示的存储条目，供 NEI 暴露构建时跳过已显示项（防双计数）。
     */
    @SideOnly(Side.CLIENT)
    public List<KeyAmount> getDisplayedStorageEntries() {
        List<KeyAmount> result = new ArrayList<>();
        int end = Math.min(storageStartIndex + getLines() * 9, storageEndIndex);
        for (int i = storageStartIndex; i < end; i++) {
            Slot slot = inventorySlots.get(i);
            if (slot instanceof AbstractStackTypedSlot) {
                KeyAmount ka = ((AbstractStackTypedSlot) slot).getTypedStackFromUnifiedStorage();
                if (ka != null && !ka.isEmpty() && ka.key() instanceof ItemStackKey) {
                    result.add(ka);
                }
            }
        }
        return result;
    }

    @SideOnly(Side.CLIENT)
    public void loadSearchText(String text) {
        if (clientNetStorage == null) return;

        this.searchText = text.toLowerCase(Locale.ENGLISH);
        this.clientNetStorage.setSearchText(searchText);
    }

    @SideOnly(Side.CLIENT)
    public void markForceAllUpdateClientView() {
        if (clientNetStorage == null) return;
        this.clientNetStorage.markForceAllUpdate();
    }

    public void updateScrollLineData(int dataSize) {
        maxLineData = dataSize / 9;
        if (dataSize % 9 != 0) {
            maxLineData++;
        }
        maxLineData -= getLines();
        maxLineData = Math.max(maxLineData, 0);
        lineData = Math.max(lineData, 0);
        lineData = Math.min(lineData, maxLineData);
    }

    @Override
    protected void updateChange() {
        super.updateChange();
        // 服务端持有网络引用时，EU 池存量变化即下发客户端（终端 EU 能量条数据源）
        if (serverNet != null) {
            String current = serverNet.getEuStorage()
                .getAmount()
                .toString();
            if (!current.equals(lastEuAmountSent)) {
                lastEuAmountSent = current;
                BDPackets.INSTANCE.sendTo(new SyncEuStoragePacket(current), (EntityPlayerMP) player);
            }
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return true;
    }
}
