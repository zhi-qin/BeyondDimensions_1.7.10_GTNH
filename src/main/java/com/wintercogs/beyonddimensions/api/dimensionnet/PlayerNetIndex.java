package com.wintercogs.beyonddimensions.api.dimensionnet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

public final class PlayerNetIndex extends WorldSavedData {

    static final String DATA_NAME = "BDPlayerNetIndex";
    static final int NO_PRIMARY_NET = -1;

    private static final String PRIMARY_NET_ENTRIES = "PrimaryNetEntries";
    private static final String PLAYER_ID = "PlayerId";
    private static final String PRIMARY_NET_ID = "PrimaryNetId";

    private final Map<UUID, Integer> primaryNetIds = new HashMap<>();
    private final Map<UUID, LinkedHashSet<Integer>> allNetIds = new HashMap<>();

    public PlayerNetIndex() {
        super(DATA_NAME);
    }

    public PlayerNetIndex(String name) {
        super(name);
    }

    public static PlayerNetIndex get(MinecraftServer server) {
        World world = server.worldServerForDimension(0);
        PlayerNetIndex index = (PlayerNetIndex) world.mapStorage.loadData(PlayerNetIndex.class, DATA_NAME);
        if (index == null) {
            index = new PlayerNetIndex();
            world.mapStorage.setData(DATA_NAME, index);
        }
        return index;
    }

    static PlayerNetIndex getIfPresent(MinecraftServer server) {
        World world = server.worldServerForDimension(0);
        return (PlayerNetIndex) world.mapStorage.loadData(PlayerNetIndex.class, DATA_NAME);
    }

    void clearRuntime() {
        allNetIds.clear();
    }

    public void rebuildFromServer(MinecraftServer server) {
        clearRuntime();
        for (int netId : NetRegistryIndex.get(server)
            .getActiveNetIds(server)) {
            DimensionsNet net = DimensionsNet.getNetFromId(server, netId);
            if (net == null) {
                continue;
            }
            for (UUID playerId : net.getPlayers()) {
                addMembership(playerId, net.getId(), false);
            }
        }
        if (reconcilePrimaryMappings()) {
            markDirty();
        }
    }

    void addMembership(UUID playerId, int netId, boolean switchPrimary) {
        if (netId < 0) {
            return;
        }

        LinkedHashSet<Integer> memberships = allNetIds.get(playerId);
        if (memberships == null) {
            memberships = new LinkedHashSet<>();
            allNetIds.put(playerId, memberships);
        }
        if (memberships.add(netId)) {
            if (switchPrimary) {
                primaryNetIds.put(playerId, netId);
            }
            markDirty();
        }
    }

    void removeMembership(UUID playerId, int netId) {
        LinkedHashSet<Integer> memberships = allNetIds.get(playerId);
        if (memberships == null || !memberships.remove(netId)) {
            return;
        }

        if (memberships.isEmpty()) {
            allNetIds.remove(playerId);
            primaryNetIds.remove(playerId);
            markDirty();
            return;
        }

        Integer primaryNetId = primaryNetIds.get(playerId);
        if (primaryNetId != null && primaryNetId == netId) {
            primaryNetIds.put(playerId, getSmallestNetId(memberships));
        }
        markDirty();
    }

    void clearPrimary(UUID playerId) {
        LinkedHashSet<Integer> memberships = allNetIds.get(playerId);
        if (memberships == null || memberships.isEmpty()) {
            if (primaryNetIds.remove(playerId) != null) {
                markDirty();
            }
            return;
        }

        Integer previous = primaryNetIds.put(playerId, NO_PRIMARY_NET);
        if (previous == null || previous != NO_PRIMARY_NET) {
            markDirty();
        }
    }

    boolean setPrimary(UUID playerId, int netId) {
        boolean changed;
        if (netId == NO_PRIMARY_NET) {
            LinkedHashSet<Integer> memberships = allNetIds.get(playerId);
            if (memberships == null || memberships.isEmpty()) {
                changed = primaryNetIds.remove(playerId) != null;
            } else {
                Integer previous = primaryNetIds.put(playerId, NO_PRIMARY_NET);
                changed = previous == null || previous != NO_PRIMARY_NET;
            }
        } else {
            LinkedHashSet<Integer> memberships = allNetIds.get(playerId);
            if (memberships == null || !memberships.contains(netId)) {
                return false;
            }

            Integer previous = primaryNetIds.put(playerId, netId);
            changed = previous == null || previous != netId;
        }

        if (changed) {
            markDirty();
        }
        return changed;
    }

    int getPrimaryNetId(UUID playerId) {
        Integer result = primaryNetIds.get(playerId);
        return result != null ? result : NO_PRIMARY_NET;
    }

    boolean hasAnyMembership(UUID playerId) {
        LinkedHashSet<Integer> memberships = allNetIds.get(playerId);
        return memberships != null && !memberships.isEmpty();
    }

    List<Integer> getAllNetIds(UUID playerId) {
        LinkedHashSet<Integer> memberships = allNetIds.get(playerId);
        if (memberships == null || memberships.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(memberships);
    }

    Map<UUID, Integer> copyPrimaryNetIds() {
        return new HashMap<>(primaryNetIds);
    }

    boolean reconcilePrimaryMappings() {
        boolean changed = false;
        Set<UUID> playerIds = new HashSet<>(allNetIds.keySet());
        playerIds.addAll(primaryNetIds.keySet());

        for (UUID playerId : playerIds) {
            LinkedHashSet<Integer> memberships = allNetIds.get(playerId);
            if (memberships == null || memberships.isEmpty()) {
                changed |= primaryNetIds.remove(playerId) != null;
                continue;
            }

            if (!primaryNetIds.containsKey(playerId)) {
                primaryNetIds.put(playerId, getSmallestNetId(memberships));
                changed = true;
                continue;
            }

            int primaryNetId = primaryNetIds.get(playerId);
            if (primaryNetId == NO_PRIMARY_NET) {
                continue;
            }

            if (!memberships.contains(primaryNetId)) {
                primaryNetIds.put(playerId, getSmallestNetId(memberships));
                changed = true;
            }
        }
        return changed;
    }

    private static int getSmallestNetId(LinkedHashSet<Integer> memberships) {
        int smallest = Integer.MAX_VALUE;
        for (int membership : memberships) {
            smallest = Math.min(smallest, membership);
        }
        return smallest;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        NBTTagList entryList = tag.getTagList(PRIMARY_NET_ENTRIES, 10);
        for (int i = 0; i < entryList.tagCount(); i++) {
            NBTTagCompound entry = entryList.getCompoundTagAt(i);
            if (!entry.hasKey(PLAYER_ID + "Most", 4) || !entry.hasKey(PLAYER_ID + "Least", 4)
                || !entry.hasKey(PRIMARY_NET_ID)) {
                continue;
            }
            UUID uuid = new UUID(entry.getLong(PLAYER_ID + "Most"), entry.getLong(PLAYER_ID + "Least"));
            primaryNetIds.put(uuid, entry.getInteger(PRIMARY_NET_ID));
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        NBTTagList entryList = new NBTTagList();
        for (Map.Entry<UUID, Integer> entry : copyPrimaryNetIds().entrySet()) {
            NBTTagCompound data = new NBTTagCompound();
            data.setLong(
                PLAYER_ID + "Most",
                entry.getKey()
                    .getMostSignificantBits());
            data.setLong(
                PLAYER_ID + "Least",
                entry.getKey()
                    .getLeastSignificantBits());
            data.setInteger(PRIMARY_NET_ID, entry.getValue());
            entryList.appendTag(data);
        }
        tag.setTag(PRIMARY_NET_ENTRIES, entryList);
    }

}
