package com.wintercogs.beyonddimensions.common.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.NetPermissionLevel;
import com.wintercogs.beyonddimensions.api.dimensionnet.PrimaryNetOption;
import com.wintercogs.beyonddimensions.common.init.BDPackets;
import com.wintercogs.beyonddimensions.network.packet.both.QuickDataTagPacket;

/**
 * 主网络切换菜单（1.7.10 移植版）。
 * <p>
 * 1.7.10 适配：ServerPlayer → EntityPlayerMP；player.getUUID() → player.getUniqueID()；
 * CompoundTag → NBTTagCompound；ListTag → NBTTagList；
 * tag.getList/put/getInt/contains → getTagList/setTag/getInteger/hasKey。
 */
public class PrimaryNetSwitcherMenu extends BDBaseMenu {

    private static final String CURRENT_PRIMARY_NET_ID = "CurrentPrimaryNetId";
    private static final String OPTIONS = "Options";

    public int currentPrimaryNetId = DimensionsNet.NO_PRIMARY_NET_ID;
    public List<PrimaryNetOption> options = new ArrayList<>();

    private NBTTagCompound lastSnapshotTag = new NBTTagCompound();

    public PrimaryNetSwitcherMenu(InventoryPlayer playerInventory) {
        super(playerInventory);

        if (!player.worldObj.isRemote) {
            refreshSnapshot();
        }
    }

    @Override
    protected void initUpdate() {
        sendSnapshot();
    }

    @Override
    protected void updateChange() {
        if (refreshSnapshot()) {
            sendSnapshot();
        }
    }

    @Override
    protected void writeQuickDataTag(NBTTagCompound tag) {
        super.writeQuickDataTag(tag);
        tag.setInteger(CURRENT_PRIMARY_NET_ID, currentPrimaryNetId);

        NBTTagList optionList = new NBTTagList();
        for (PrimaryNetOption option : options) {
            optionList.appendTag(option.save());
        }
        tag.setTag(OPTIONS, optionList);
    }

    @Override
    public void readQuickDataTag(NBTTagCompound tag) {
        super.readQuickDataTag(tag);
        currentPrimaryNetId = tag.hasKey(CURRENT_PRIMARY_NET_ID) ? tag.getInteger(CURRENT_PRIMARY_NET_ID)
            : DimensionsNet.NO_PRIMARY_NET_ID;

        NBTTagList optionList = tag.getTagList(OPTIONS, 10);
        List<PrimaryNetOption> loadedOptions = new ArrayList<>(optionList.tagCount());
        for (int i = 0; i < optionList.tagCount(); i++) {
            loadedOptions.add(PrimaryNetOption.load(optionList.getCompoundTagAt(i)));
        }
        options = loadedOptions;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return true;
    }

    private boolean refreshSnapshot() {
        if (!(player instanceof EntityPlayerMP)) {
            return false;
        }

        EntityPlayerMP serverPlayer = (EntityPlayerMP) player;
        int nextPrimaryNetId = resolveCurrentPrimaryNetId(serverPlayer);
        List<PrimaryNetOption> nextOptions = buildOptions(serverPlayer);
        NBTTagCompound nextSnapshotTag = createSnapshotTag(nextPrimaryNetId, nextOptions);
        if (Objects.equals(nextSnapshotTag, lastSnapshotTag)) {
            return false;
        }

        currentPrimaryNetId = nextPrimaryNetId;
        options = nextOptions;
        lastSnapshotTag = (NBTTagCompound) nextSnapshotTag.copy();
        return true;
    }

    private void sendSnapshot() {
        if (player instanceof EntityPlayerMP) {
            NBTTagCompound snapshotTag = new NBTTagCompound();
            writeQuickDataTag(snapshotTag);
            BDPackets.INSTANCE.sendTo(new QuickDataTagPacket(snapshotTag), (EntityPlayerMP) player);
        }
    }

    private static int resolveCurrentPrimaryNetId(EntityPlayerMP player) {
        DimensionsNet currentPrimaryNet = DimensionsNet.getPrimaryNetFromPlayer(player);
        return currentPrimaryNet == null ? DimensionsNet.NO_PRIMARY_NET_ID : currentPrimaryNet.getId();
    }

    private static List<PrimaryNetOption> buildOptions(EntityPlayerMP player) {
        UUID playerId = player.getUniqueID();
        List<DimensionsNet> nets = new ArrayList<>(DimensionsNet.getAllNetFromPlayer(player));
        nets.sort((left, right) -> Integer.compare(left.getId(), right.getId()));

        List<PrimaryNetOption> builtOptions = new ArrayList<>(nets.size());
        for (DimensionsNet net : nets) {
            builtOptions.add(new PrimaryNetOption(net.getId(), resolvePermission(net, playerId), net.getCustomName()));
        }
        return builtOptions;
    }

    private static NetPermissionLevel resolvePermission(DimensionsNet net, UUID playerId) {
        if (net.isOwner(playerId)) {
            return NetPermissionLevel.Owner;
        }
        if (net.isManager(playerId)) {
            return NetPermissionLevel.Manager;
        }
        return NetPermissionLevel.Member;
    }

    private static NBTTagCompound createSnapshotTag(int primaryNetId, List<PrimaryNetOption> options) {
        NBTTagCompound snapshotTag = new NBTTagCompound();
        snapshotTag.setInteger(CURRENT_PRIMARY_NET_ID, primaryNetId);
        NBTTagList optionList = new NBTTagList();
        for (PrimaryNetOption option : options) {
            optionList.appendTag(option.save());
        }
        snapshotTag.setTag(OPTIONS, optionList);
        return snapshotTag;
    }
}
