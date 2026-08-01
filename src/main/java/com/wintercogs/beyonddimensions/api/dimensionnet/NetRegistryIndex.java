package com.wintercogs.beyonddimensions.api.dimensionnet;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

public final class NetRegistryIndex extends WorldSavedData {

    static final String DATA_NAME = "BDNetRegistryIndex";

    private static final String ACTIVE_NET_IDS = "ActiveNetIds";
    private static final String NEXT_NET_ID = "NextNetId";
    private static final String INITIALIZED = "Initialized";
    private static final Pattern LEGACY_NET_FILE_PATTERN = Pattern
        .compile(Pattern.quote(DimensionsNet.NET_DATA_PREFIX) + "(\\d+)\\.dat");

    private final TreeSet<Integer> activeNetIds = new TreeSet<>();
    private int nextNetId;
    private boolean initialized;

    public NetRegistryIndex() {
        super(DATA_NAME);
    }

    public NetRegistryIndex(String name) {
        super(name);
    }

    public static NetRegistryIndex get(MinecraftServer server) {
        World world = server.worldServerForDimension(0);
        NetRegistryIndex index = (NetRegistryIndex) world.mapStorage.loadData(NetRegistryIndex.class, DATA_NAME);
        if (index == null) {
            index = new NetRegistryIndex();
            world.mapStorage.setData(DATA_NAME, index);
        }
        return index;
    }

    public void ensureInitialized(MinecraftServer server) {
        if (initialized) {
            return;
        }

        boolean changed = migrateLegacyData(server);
        changed |= !initialized;
        initialized = true;
        if (changed) {
            markDirty();
        }
    }

    int allocateNetId(MinecraftServer server) {
        ensureInitialized(server);
        return allocateNetId();
    }

    void registerNet(MinecraftServer server, int netId) {
        ensureInitialized(server);
        if (registerNet(netId)) {
            markDirty();
        }
    }

    void unregisterNet(MinecraftServer server, int netId) {
        ensureInitialized(server);
        if (activeNetIds.remove(netId)) {
            markDirty();
        }
    }

    List<Integer> getActiveNetIds(MinecraftServer server) {
        ensureInitialized(server);
        return new ArrayList<>(activeNetIds);
    }

    boolean isKnownNet(MinecraftServer server, int netId) {
        ensureInitialized(server);
        return activeNetIds.contains(netId);
    }

    private boolean migrateLegacyData(MinecraftServer server) {
        boolean changed = false;
        // 1.7.10 WorldSavedData 落盘位置 = <世界目录>/data/（SaveHandler.getMapFileFromName）。
        // 不能用 server.getFile(".")（=服务器根目录，单机为进程 cwd），否则扫描到不存在的目录
        // 迁移静默失效 → 索引丢失时 nextNetId 归零 → id 复用导致网络所有权错乱。
        // 世界目录的 1.7.10 惯用获取方式（与 GT5U 等一致）：worldServerForDimension(0).getSaveHandler().getWorldDirectory()
        File dataPath = new File(
            server.worldServerForDimension(0)
                .getSaveHandler()
                .getWorldDirectory(),
            "data");
        if (!dataPath.isDirectory()) {
            return false;
        }

        File[] files = dataPath.listFiles();
        if (files == null) {
            return false;
        }

        try {
            for (File file : files) {
                Matcher matcher = LEGACY_NET_FILE_PATTERN.matcher(file.getName());
                if (!matcher.matches()) {
                    continue;
                }

                int netId = Integer.parseInt(matcher.group(1));
                DimensionsNet net = DimensionsNet.getNetFromId(server, netId);
                changed |= observeExistingNet(netId, net != null);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to initialize net registry index", exception);
        }

        return changed;
    }

    private boolean observeExistingNet(int netId, boolean activeNetwork) {
        if (netId < 0) {
            return false;
        }

        boolean changed = false;
        if (activeNetwork) {
            changed = activeNetIds.add(netId);
        }
        if (nextNetId <= netId) {
            nextNetId = netId + 1;
            changed = true;
        }
        return changed;
    }

    private boolean registerNet(int netId) {
        if (netId < 0) {
            return false;
        }

        boolean changed = activeNetIds.add(netId);
        if (netId == nextNetId) {
            nextNetId++;
            changed = true;
        }
        return changed;
    }

    private int allocateNetId() {
        return nextNetId;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        activeNetIds.clear();
        int[] activeIds = tag.getIntArray(ACTIVE_NET_IDS);
        for (int netId : activeIds) {
            observeExistingNet(netId, true);
        }
        if (tag.hasKey(NEXT_NET_ID, 3)) {
            nextNetId = Math.max(0, tag.getInteger(NEXT_NET_ID));
        }
        if (tag.hasKey(INITIALIZED, 1)) {
            initialized = tag.getBoolean(INITIALIZED);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        int[] activeIds = new int[activeNetIds.size()];
        int i = 0;
        for (int netId : activeNetIds) {
            activeIds[i++] = netId;
        }
        tag.setIntArray(ACTIVE_NET_IDS, activeIds);
        tag.setInteger(NEXT_NET_ID, nextNetId);
        tag.setBoolean(INITIALIZED, initialized);
    }

}
