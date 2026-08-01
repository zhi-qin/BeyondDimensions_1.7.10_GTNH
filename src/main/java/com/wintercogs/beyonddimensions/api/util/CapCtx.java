package com.wintercogs.beyonddimensions.api.util;

import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 能力上下文（1.7.10 适配版）。
 * 替代 1.20.1 的 record，使用常规类保存位置、世界、方块实体和方向信息。
 */
public final class CapCtx {

    public final int x;
    public final int y;
    public final int z;
    public final World world;
    public final TileEntity tileEntity;
    public final ForgeDirection side;

    public CapCtx(int x, int y, int z, World world, @Nullable TileEntity tileEntity, @Nullable ForgeDirection side) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.world = world;
        this.tileEntity = tileEntity;
        this.side = side;
    }

    public CapCtx(int x, int y, int z, World world) {
        this(x, y, z, world, null, null);
    }

    public CapCtx(int x, int y, int z, World world, @Nullable ForgeDirection side) {
        this(x, y, z, world, null, side);
    }

    public CapCtx(World world, TileEntity tileEntity) {
        this(
            tileEntity != null ? tileEntity.xCoord : 0,
            tileEntity != null ? tileEntity.yCoord : 0,
            tileEntity != null ? tileEntity.zCoord : 0,
            world,
            tileEntity,
            null);
    }

    public CapCtx(World world, TileEntity tileEntity, @Nullable ForgeDirection side) {
        this(
            tileEntity != null ? tileEntity.xCoord : 0,
            tileEntity != null ? tileEntity.yCoord : 0,
            tileEntity != null ? tileEntity.zCoord : 0,
            world,
            tileEntity,
            side);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CapCtx capCtx = (CapCtx) o;
        return x == capCtx.x && y == capCtx.y
            && z == capCtx.z
            && Objects.equals(world, capCtx.world)
            && Objects.equals(side, capCtx.side);
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z, world, side);
    }

    @Override
    public String toString() {
        return "CapCtx{x=" + x
            + ", y="
            + y
            + ", z="
            + z
            + ", dim="
            + (world != null ? world.provider.dimensionId : "null")
            + ", side="
            + side
            + "}";
    }
}
