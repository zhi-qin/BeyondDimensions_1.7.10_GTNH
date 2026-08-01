package com.wintercogs.beyonddimensions.integration.module.ae2.item;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.wintercogs.beyonddimensions.common.item.NetedItem;

/**
 * AE2 存储元件物品（1.7.10 适配版）。
 * <p>
 * 继承 {@link NetedItem} 以复用维度网络绑定逻辑（右键潜行绑定/解绑网络）。
 * <p>
 * 与源项目（1.20.1）的差异：
 * - 1.20.1 使用 {@code InteractionResultHolder} / {@code Level} / {@code Player}
 * - 1.7.10 使用 {@link ItemStack#onItemRightClick(World, EntityPlayer)} 返回 ItemStack
 * <p>
 * 该物品本身不导入 AE2 类，可在无 AE2 环境下安全加载，
 * AE2 通过 {@link com.wintercogs.beyonddimensions.integration.module.ae2.me.CellHandler}
 * 识别此物品为存储元件。
 */
public class NetAEStorageCell extends NetedItem {

    public NetAEStorageCell() {
        super();
        // 设置为最大堆叠 1，存储元件不可堆叠
        setMaxStackSize(1);
    }

    /**
     * 右键使用：非潜行时阻止默认交互（避免误触发绑定），
     * 潜行时由父类 {@link NetedItem#onItemRightClick} 处理绑定/解绑。
     */
    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        // 非潜行时不处理，直接返回（让 AE2 驱动器等容器正常工作）
        if (!player.isSneaking()) {
            return stack;
        }
        return super.onItemRightClick(stack, world, player);
    }
}
