package com.wintercogs.beyonddimensions.common.block.entity;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;

public class NetedBlockEntity extends TileEntity {

    private int netId = -1;
    private DimensionsNet net = null;
    private final List<Runnable> onNetChangeRunnables = new ArrayList<>();

    public int getNetId() {
        return netId;
    }

    public void setNetId(int id) {
        boolean needsUpdate = this.netId != id;
        this.netId = id;

        if (worldObj != null) {
            if (needsUpdate) {
                refreshNetCache();
                markDirty();
                worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            }
        }
    }

    public void clearNetId() {
        boolean needsUpdate = this.netId != -1;
        this.netId = -1;
        net = null;
        if (worldObj != null) {
            if (needsUpdate) {
                markDirty();
                worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            }
        }
    }

    public void setNetIdFromPlayer(EntityPlayerMP player) {
        DimensionsNet playerNet = DimensionsNet.getNetFromPlayer(player);
        if (playerNet != null) {
            setNetId(playerNet.getId());
        }
    }

    public void setNetIdFromPlayerOrClean(EntityPlayerMP player) {
        DimensionsNet playerNet = DimensionsNet.getNetFromPlayer(player);
        if (playerNet != null) {
            setNetId(playerNet.getId());
        } else {
            clearNetId();
        }
    }

    public DimensionsNet getNet() {
        if (netId >= 0) {
            if (net == null || net.deleted) {
                refreshNetCache();
            }
            return net;
        }
        return null;
    }

    protected void refreshNetCache() {
        if (worldObj instanceof WorldServer) {
            DimensionsNet netCache = DimensionsNet.getNetFromId(netId);
            if (netCache != null && !netCache.deleted) {
                net = netCache;
            } else {
                net = null;
            }
        }
    }

    public void addNetChangeTask(Runnable runnable) {
        onNetChangeRunnables.add(runnable);
    }

    public void onNetChange() {
        for (Runnable runnable : onNetChangeRunnables) {
            runnable.run();
        }
    }

    @Override
    public void markDirty() {
        super.markDirty();
        onNetChange();
    }

    @Override
    public void validate() {
        super.validate();
        refreshNetCache();
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        netId = nbt.getInteger("netId");
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setInteger("netId", this.netId);
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        writeToNBT(tag);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        readFromNBT(pkt.func_148857_g());
    }
}
