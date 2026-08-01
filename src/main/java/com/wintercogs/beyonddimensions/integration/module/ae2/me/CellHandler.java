package com.wintercogs.beyonddimensions.integration.module.ae2.me;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.item.NetedItem;
import com.wintercogs.beyonddimensions.integration.module.ae2.item.NetAEFluidCell;
import com.wintercogs.beyonddimensions.integration.module.ae2.item.NetAEStorageCell;

import appeng.api.implementations.tiles.IChestOrDrive;
import appeng.api.storage.ICellHandler;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.StorageChannel;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * AE2 存储元件处理器（1.7.10 适配版）。
 * <p>
 * 对应源项目（1.20.1）中的 {@code CellHandler}，实现 {@link ICellHandler} 接口，
 * 将 {@link NetAEStorageCell} 物品注册为 AE2 可识别的存储元件。
 * <p>
 * 当 AE2 驱动器或 ME 终端询问该物品时，本处理器返回一个 {@link NetStorageCell}
 * 适配器，将 BD 维度网络的 {@link com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage}
 * 暴露为 AE2 的物品存储。
 * <p>
 * 1.7.10 AE2 将物品/流体分为不同通道，且驱动器每个槽位只绑定一个通道，
 * 因此按物品分流：
 * - {@link NetAEStorageCell} → 物品通道（{@link StorageChannel#ITEMS}）的 {@link NetStorageCell}
 * - {@link NetAEFluidCell} → 流体通道（{@link StorageChannel#FLUIDS}）的 {@link NetFluidStorageCell}
 */
public class CellHandler implements ICellHandler {

    public static final CellHandler INSTANCE = new CellHandler();

    @Override
    public boolean isCell(ItemStack itemstack) {
        return itemstack != null
            && (itemstack.getItem() instanceof NetAEStorageCell || itemstack.getItem() instanceof NetAEFluidCell);
    }

    /**
     * 返回该物品对应的存储处理器。
     * 仅当物品类型与请求通道匹配，且已绑定到有效的维度网络时才返回非 null。
     *
     * @param itemstack 存储元件物品
     * @param host      保存提供者（可为 null）
     * @param channel   存储通道（物品元件仅处理 ITEMS，流体元件仅处理 FLUIDS）
     */
    @Override
    public IMEInventoryHandler getCellInventory(ItemStack itemstack, ISaveProvider host, StorageChannel channel) {
        if (itemstack == null) {
            return null;
        }
        if (itemstack.getItem() instanceof NetAEStorageCell && channel == StorageChannel.ITEMS) {
            return createNetStorageCell(itemstack);
        }
        if (itemstack.getItem() instanceof NetAEFluidCell && channel == StorageChannel.FLUIDS) {
            return createNetFluidStorageCell(itemstack);
        }
        return null;
    }

    private IMEInventoryHandler createNetStorageCell(ItemStack itemstack) {
        int netId = NetedItem.getNetId(itemstack);
        if (netId < 0) {
            return null;
        }
        DimensionsNet net = DimensionsNet.getNetFromId(netId);
        if (net == null) {
            return null;
        }
        return new NetStorageCell(net.getUnifiedStorage(), net);
    }

    private IMEInventoryHandler createNetFluidStorageCell(ItemStack itemstack) {
        int netId = NetedItem.getNetId(itemstack);
        if (netId < 0) {
            return null;
        }
        DimensionsNet net = DimensionsNet.getNetFromId(netId);
        if (net == null) {
            return null;
        }
        return new NetFluidStorageCell(net.getUnifiedStorage());
    }

    /**
     * ME Chest 顶部纹理（不使用 ME Chest，返回 null）。
     */
    @Override
    @SideOnly(Side.CLIENT)
    @Nullable
    public IIcon getTopTexture_Light() {
        return null;
    }

    @Override
    @SideOnly(Side.CLIENT)
    @Nullable
    public IIcon getTopTexture_Medium() {
        return null;
    }

    @Override
    @SideOnly(Side.CLIENT)
    @Nullable
    public IIcon getTopTexture_Dark() {
        return null;
    }

    /**
     * 在 ME Chest 中打开 GUI 的回调。本存储元件不支持 ME Chest 直接打开，
     * 向玩家提示需通过 BD 自身的终端访问。
     */
    @Override
    public void openChestGui(EntityPlayer player, IChestOrDrive chest, ICellHandler cellHandler,
        IMEInventoryHandler inv, ItemStack is, StorageChannel chan) {
        // 不支持 ME Chest GUI，无操作
    }

    /**
     * 返回存储元件状态。
     * 0=缺失, 1=空(绿色), 2=有空间(蓝色), 3=仅物品无类型空间(橙色), 4=满(红色)
     * <p>
     * 直接根据存储元件物品（{@code is}）反查绑定的维度网络存储，
     * 再按元件所属通道判断是否有内容，返回 1（空）或 2（有空间）。
     * 不依赖 AE2 传入的 {@code handler} 实例，避免版本差异（690 无 getInternal 解包装接口）。
     */
    @Override
    public int getStatusForCell(ItemStack is, IMEInventory handler) {
        if (is == null || is.getItem() == null) {
            return 0; // 缺失
        }
        boolean isItemCell = is.getItem() instanceof NetAEStorageCell;
        boolean isFluidCell = is.getItem() instanceof NetAEFluidCell;
        if (!isItemCell && !isFluidCell) {
            return 0;
        }
        int netId = NetedItem.getNetId(is);
        if (netId < 0) {
            return 1; // 未绑定网络，视为空
        }
        DimensionsNet net = DimensionsNet.getNetFromId(netId);
        if (net == null || net.getUnifiedStorage() == null
            || net.getUnifiedStorage()
                .getStorage() == null) {
            return 1;
        }
        for (KeyAmount ka : net.getUnifiedStorage()
            .getStorage()) {
            if (ka == null || ka.isEmpty() || ka.amount() <= 0) {
                continue;
            }
            if (isItemCell && (ka.key() instanceof ItemStackKey || ka.key() instanceof EnergyStackKey)) {
                return 2; // 物品通道有内容（物品 + 能量滴）
            }
            if (isFluidCell && ka.key() instanceof FluidStackKey) {
                return 2; // 流体通道有内容
            }
        }
        return 1; // 空
    }

    /**
     * 返回存储元件在驱动器中的耗电量（AE/t）。
     */
    @Override
    public double cellIdleDrain(ItemStack is, IMEInventory handler) {
        return 1.0;
    }
}
