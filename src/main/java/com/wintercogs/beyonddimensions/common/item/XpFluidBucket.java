package com.wintercogs.beyonddimensions.common.item;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidHandler;

import com.wintercogs.beyonddimensions.common.init.BDFluids;

/**
 * XP 流体桶（1.7.10 移植版）。
 * <p>
 * 对齐源项目 1.20.1 BucketItem 的右键行为：指向流体容器时整桶倒入容器
 * （IFluidHandler.fill，容量不足一整桶时不动）；否则在可替换方块处放置 XP 流体方块；
 * 成功后消耗满桶并返还空桶（创造模式不消耗）。
 * 1.7.10 无 BucketItem，故手写 onItemRightClick 实现（审计 M5-7）；
 * 空桶舀取由 FluidContainerRegistry 映射 + 槽位交互（OrderedStackTypedSlot）承担。
 */
public class XpFluidBucket extends Item {

    public XpFluidBucket() {
        setMaxStackSize(1);
        setContainerItem(Items.bucket);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        MovingObjectPosition mop = this.getMovingObjectPositionFromPlayer(world, player, false);
        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            return stack;
        }

        int x = mop.blockX;
        int y = mop.blockY;
        int z = mop.blockZ;
        if (!world.canMineBlock(player, x, y, z)) {
            return stack;
        }

        // 1. 指向流体容器：整桶倒入容器（容量不足一整桶时不动）
        if (world.getTileEntity(x, y, z) instanceof IFluidHandler handler) {
            FluidStack fluid = new FluidStack(BDFluids.XP_FLUID, FluidContainerRegistry.BUCKET_VOLUME);
            if (handler.fill(ForgeDirection.UNKNOWN, fluid, false) == FluidContainerRegistry.BUCKET_VOLUME) {
                handler.fill(ForgeDirection.UNKNOWN, fluid, true);
                return consumeBucket(stack, player);
            }
            return stack;
        }

        // 2. 面向方块侧边放置流体方块
        ForgeDirection side = ForgeDirection.getOrientation(mop.sideHit);
        int px = x + side.offsetX;
        int py = y + side.offsetY;
        int pz = z + side.offsetZ;
        if (!player.canPlayerEdit(px, py, pz, mop.sideHit, stack)) {
            return stack;
        }
        if (world.isAirBlock(px, py, pz) || world.getBlock(px, py, pz)
            .isReplaceable(world, px, py, pz)) {
            world.setBlock(px, py, pz, BDFluids.XP_FLUID_BLOCK, 0, 3);
            return consumeBucket(stack, player);
        }
        return stack;
    }

    /**
     * 消耗满桶并返还空桶（对齐原版 ItemBucket 的返还逻辑；创造模式不消耗）。
     */
    private ItemStack consumeBucket(ItemStack stack, EntityPlayer player) {
        if (player.capabilities.isCreativeMode) {
            return stack;
        }
        if (--stack.stackSize <= 0) {
            return new ItemStack(Items.bucket);
        }
        if (!player.inventory.addItemStackToInventory(new ItemStack(Items.bucket))) {
            player.dropPlayerItemWithRandomChoice(new ItemStack(Items.bucket), false);
        }
        return stack;
    }
}
