package com.wintercogs.beyonddimensions.api.dimensionnet;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

import com.wintercogs.beyonddimensions.api.storage.eu.NetEuStorage;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.init.BDItems;
import com.wintercogs.beyonddimensions.config.ServerConfigRuntime;
import com.wintercogs.beyonddimensions.util.PlayerNameHelper;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * 此类即模组概念中的"维度网络"，并实际负责存储和持久化数据
 * <p>
 * 使用 {@link DimensionsNet#createNewNetForPlayer(EntityPlayer, long, int)} 来创建一个持久化保存的维度网络
 * <p>
 */
public class DimensionsNet extends WorldSavedData {

    static final String NET_DATA_PREFIX = "BDNet_";
    public static final int NO_PRIMARY_NET_ID = -1;
    public static final int MAX_NETWORK_NAME_LENGTH = 48;
    private static final String CUSTOM_NAME_TAG = "custom_name";

    /**
     * 作为网络的唯一标识符，id从0开始，小于0的id均可以认为是无效网络
     * <p>
     * 被删除的网络均使用-99作为特殊标记
     */
    private int id;

    /**
     * 玩家自定义网络名。空字符串表示未命名，展示时回退到本地化默认名。
     */
    private String customName = "";

    /**
     * deleted为真则表示网络被删除，被删除的网络仍可被 WorldSavedData 的方法获得，但不应该被使用
     * <p>
     * 此数据会随着 WorldSavedData 持久化保存
     */
    public boolean deleted = false;

    /**
     * 网络所有者
     */
    private UUID owner;

    /**
     * 网络管理员，包含所有者
     */
    private final Set<UUID> managers = new HashSet<>();

    /**
     * 网络成员，包含所有的管理员
     */
    private final Set<UUID> players = new HashSet<>();

    /**
     * 通用存储空间，存储任何实现了 IStackKey 的资源类型
     */
    private final UnifiedStorage unifiedStorage;

    /**
     * EU 能量池（BigInteger 容量 10^40），独立于 unifiedStorage 的 RF 池。
     * <p>
     * 换算方向仅 EU→RF（GTNH 设计理念，RF 不可反向制造 EU），桥方法见
     * {@link #insertEu} / {@link #extractEu} / {@link #insertRf} / {@link #extractRf}。
     */
    private final NetEuStorage euStorage;

    /**
     * 标记网络是否为一个临时网络，临时网络通常用于客户端菜单的同步中，作为资源容器使用
     * <p>
     * 临时网络不会执行生成破碎的时空结晶之类的操作
     */
    private final boolean temporary;

    /**
     * currentTime是流动的倒计时，用于生成破碎时空结晶，该数据持久化保存
     * <p>
     * holdTime是固定的时间间隔，用于确定多久生成一次时间间隔，每当currentTime归零，holdTime会为它赋值
     */
    private int currentTime = 0;

    /**
     * 构造函数（用于临时网络，不会持久化保存）
     *
     * @param temporary 为真则说明是临时网络
     */
    public DimensionsNet(boolean temporary) {
        super(buildNetDataName(NO_PRIMARY_NET_ID));
        unifiedStorage = new UnifiedStorage(this, AbstractUnorderedStackHandler.UiTimestampPolicy.AUTO);
        euStorage = new NetEuStorage(this::markDirty);
        this.temporary = temporary;
        // 1.7.10 的 gameevent.TickEvent 在 FML 总线上触发，而非 MinecraftForge.EVENT_BUS。
        // 临时网络（如 NetControlMenu 构造期占位）不参与结晶生成，无需注册总线监听——
        // 否则每个玩家每次开关菜单都会泄漏一份订阅（审计 M1-6/M5-6）。
        if (!temporary) {
            FMLCommonHandler.instance()
                .bus()
                .register(this);
        }
    }

    /**
     * 构造函数（用于持久化网络，由 mapStorage.loadData 调用）
     */
    public DimensionsNet(String name) {
        super(name);
        unifiedStorage = new UnifiedStorage(this, AbstractUnorderedStackHandler.UiTimestampPolicy.AUTO);
        euStorage = new NetEuStorage(this::markDirty);
        this.temporary = false;
        // 1.7.10 的 gameevent.TickEvent 在 FML 总线上触发，而非 MinecraftForge.EVENT_BUS
        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    // 基本函数

    /**
     * 用于构造 WorldSavedData 的工厂方法
     */
    public static DimensionsNet create() {
        return new DimensionsNet(false);
    }

    /**
     * 用于创建一个维度网络，仅在服务端调用
     *
     * @param player                传入的玩家会作为网络所有者
     * @param defaultSlotCapability 新网络单个槽位可存储的容量
     * @param defaultSlotMaxSize    新网络所拥有的槽位数量
     * @return 返回新创建的维度网络，但如果传入的player加入了一个网络，只会返回其当前所在的网络
     */
    @Nullable
    public static DimensionsNet createNewNetForPlayer(EntityPlayer player, long defaultSlotCapability,
        int defaultSlotMaxSize) {
        DimensionsNet net = DimensionsNet.getNetFromPlayer(player);
        if (net != null) {
            return net;
        }

        MinecraftServer server = getRunningServer();
        if (server != null) {
            int allocatedNetId = NetRegistryIndex.get(server)
                .allocateNetId(server);
            String netDataName = DimensionsNet.buildNetDataName(allocatedNetId);

            World world = server.worldServerForDimension(0);
            DimensionsNet newNet = (DimensionsNet) world.mapStorage.loadData(DimensionsNet.class, netDataName);
            if (newNet != null && !newNet.deleted) {
                // 防御：索引丢失/损坏导致新 id 撞上已存在的活跃网络时，
                // 拒绝覆盖（否则 setOwner 会接管旧网络的存储与成员，跨玩家数据泄露）。
                // 注意：allocateNetId 只返回 nextNetId 而不递增，必须仍调用 registerNet，
                // 让 nextNetId 越过撞车 id——否则 nextNetId 永久卡死在该 id，
                // 之后每次创建新网络都会拿到这个旧网络（Bug 审计 M1-1）。
                NetRegistryIndex.get(server)
                    .registerNet(server, allocatedNetId);
                return newNet;
            }
            if (newNet == null) {
                newNet = new DimensionsNet(netDataName);
                world.mapStorage.setData(netDataName, newNet);
            } else {
                // 撞上已删除网络的残留文件：重建新实例覆盖，避免"复活"旧网络
                newNet = new DimensionsNet(netDataName);
                world.mapStorage.setData(netDataName, newNet);
            }

            newNet.setId(allocatedNetId);
            NetRegistryIndex.get(server)
                .registerNet(server, allocatedNetId);
            newNet.setOwner(player.getUniqueID());
            newNet.addManager(player.getUniqueID());
            newNet.addPlayer(player.getUniqueID());
            newNet.markDirty();
            newNet.unifiedStorage.setSlotCapacity(defaultSlotCapability);
            newNet.unifiedStorage.setSlotMaxSize(defaultSlotMaxSize);

            return newNet;
        }
        return null;
    }

    public static String buildNetDataName(int netId) {
        return NET_DATA_PREFIX + netId;
    }

    /**
     * 尝试从数字id获取一个维度网络，仅在服务端调用
     *
     * @param id 数字id
     * @return 返回找到的网络，如果数字id对应的网络不存在或者不合法(例如被删除)，则直接返回null
     */
    @Nullable
    public static DimensionsNet getNetFromId(int id) {
        MinecraftServer server = getRunningServer();
        if (server == null) {
            return null;
        }
        return getNetFromId(server, id);
    }

    /**
     * 获取当前运行中的服务端实例（对齐源项目 ServerLifecycleHooks.getCurrentServer()
     * / player.getServer() 语义）。1.7.10 的 MinecraftServer.getServer() 静态引用在退出
     * 单机后不会置空，多人客户端会残留已停止的服务端实例——其主世界已卸载，再经
     * worldServerForDimension(0) 会让 GT 的 DimensionManager 抛 "Cannot Hotload Dim"
     * （OpenGuiHandler 触发 → Netty 线程异常 → 客户端"连接已丢失"）。故用 isServerRunning() 过滤。
     */
    private static MinecraftServer getRunningServer() {
        MinecraftServer server = MinecraftServer.getServer();
        return server != null && server.isServerRunning() ? server : null;
    }

    @Nullable
    static DimensionsNet getNetFromId(MinecraftServer server, int id) {
        if (id < 0) {
            return null;
        }

        World world = server.worldServerForDimension(0);
        DimensionsNet net = (DimensionsNet) world.mapStorage.loadData(DimensionsNet.class, buildNetDataName(id));
        if (net != null && !net.deleted) {
            return net;
        }
        if (net != null) {
            // 已删除网络：loadData 会经 DimensionsNet(String) 构造器注册 FML 总线监听，
            // 但此处按约定返回 null 后该实例被遗弃，必须注销监听，否则每次启动
            // 扫描残留 .dat 都会泄漏一份被总线强引用的实例（对应 destroySelf 中的 unregister）
            FMLCommonHandler.instance()
                .bus()
                .unregister(net);
        }
        return null;
    }

    /**
     * 尝试从玩家获取维度网络，仅在服务端调用
     *
     * @param player 玩家
     * @return 返回玩家所在的维度网络，如果不存在，则返回null
     */
    @Deprecated
    @Nullable
    public static DimensionsNet getNetFromPlayer(EntityPlayer player) {
        return getPrimaryNetFromPlayer(player);
    }

    @Nullable
    public static DimensionsNet getPrimaryNetFromPlayer(EntityPlayer player) {
        MinecraftServer server = getRunningServer();
        if (server == null) {
            return null;
        }

        PlayerNetIndex index = PlayerNetIndex.get(server);
        int primaryNetId = index.getPrimaryNetId(player.getUniqueID());
        if (primaryNetId == PlayerNetIndex.NO_PRIMARY_NET) {
            return null;
        }

        return getNetFromId(server, primaryNetId);
    }

    public static List<DimensionsNet> getAllNetFromPlayer(EntityPlayer player) {
        MinecraftServer server = getRunningServer();
        if (server == null) {
            return new ArrayList<>();
        }

        PlayerNetIndex index = PlayerNetIndex.get(server);
        List<Integer> netIds = index.getAllNetIds(player.getUniqueID());
        if (netIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<DimensionsNet> nets = new ArrayList<>(netIds.size());
        for (int netId : netIds) {
            DimensionsNet net = getNetFromId(server, netId);
            if (net != null) {
                nets.add(net);
            }
        }
        return nets;
    }

    public static boolean hasAnyNet(EntityPlayer player) {
        MinecraftServer server = getRunningServer();
        return server != null && PlayerNetIndex.get(server)
            .hasAnyMembership(player.getUniqueID());
    }

    public static boolean hasPrimaryNet(EntityPlayer player) {
        return getPrimaryNetFromPlayer(player) != null;
    }

    public static boolean setPrimaryNetForPlayer(EntityPlayer player, @Nullable DimensionsNet net) {
        MinecraftServer server = getRunningServer();
        if (server == null) {
            return false;
        }

        PlayerNetIndex index = PlayerNetIndex.get(server);
        return net == null ? index.setPrimary(player.getUniqueID(), PlayerNetIndex.NO_PRIMARY_NET)
            : index.setPrimary(player.getUniqueID(), net.getId());
    }

    public static void clearPrimaryNetForPlayer(EntityPlayer player) {
        MinecraftServer server = getRunningServer();
        if (server == null) {
            return;
        }

        PlayerNetIndex.get(server)
            .clearPrimary(player.getUniqueID());
    }

    // 功能函数

    /**
     * 获取网络id
     */
    public int getId() {
        return id;
    }

    /**
     * 获取用于展示的网络名。自定义名为空时，返回本地化默认名。
     */
    public IChatComponent getNetworkName() {
        return getNetworkName(this.id, this.customName);
    }

    /**
     * 供客户端快照等没有 DimensionsNet 实例的场景复用同一命名规则。
     */
    public static IChatComponent getNetworkName(int netId, @Nullable String customName) {
        String sanitizedName = sanitizeCustomName(customName);
        if (!sanitizedName.isEmpty()) {
            return new ChatComponentText(sanitizedName);
        }

        return new ChatComponentTranslation("menu.text.beyonddimensions.net.default_name", netId);
    }

    public String getCustomName() {
        return customName;
    }

    public boolean hasCustomName() {
        return !customName.isEmpty();
    }

    public void setCustomName(@Nullable String customName) {
        String sanitizedName = sanitizeCustomName(customName);
        if (Objects.equals(this.customName, sanitizedName)) {
            return;
        }

        this.customName = sanitizedName;
        markDirty();
    }

    /**
     * 设置网络id
     */
    public void setId(int id) {
        this.id = id;
        markDirty();
    }

    /**
     * 获取网络所有者的uuid
     */
    public UUID getOwner() {
        return owner;
    }

    /**
     * 设置新的网络所有者
     * <p>
     * 注意，这不会把原所有者从网络中删除，他会成为网络管理员
     */
    public void setOwner(UUID owner) {
        this.owner = owner;
        addManager(owner);
        markDirty();
    }

    /**
     * 获取包含所有管理员uuid的集合
     */
    public Set<UUID> getManagers() {
        return managers;
    }

    /**
     * 添加一个网络管理员
     *
     * @param managerId 新增管理员的uuid
     */
    public void addManager(UUID managerId) {
        managers.add(managerId);
        addPlayer(managerId);
        markDirty();
    }

    /**
     * 移除一个网络管理员，该管理员将会降级为成员
     * <p>
     * 不能直接移除当前所有者
     */
    public void removeManager(UUID managerId) {
        if (managerId.equals(owner)) {
            return;
        }
        managers.remove(managerId);
        markDirty();
    }

    /**
     * 获取当前网络所有的玩家集合
     */
    public Set<UUID> getPlayers() {
        return players;
    }

    /**
     * 添加一个网络成员
     */
    public void addPlayer(UUID playerId) {
        if (players.add(playerId)) {
            syncPlayerMembership(playerId, true);
            markDirty();
        }
    }

    /**
     * 从网络移除一个玩家，你不能直接移除当前所有者
     * <p>
     * 但是你可以直接移除任何其他成员
     */
    public void removePlayer(UUID playerId) {
        if (playerId.equals(owner)) {
            return;
        }
        if (players.remove(playerId)) {
            managers.remove(playerId);
            syncPlayerRemoval(playerId);
            markDirty();
        }
    }

    /**
     * 传入的玩家是否为所有者
     *
     * @param player 玩家
     * @return 是所有者则返回真
     */
    public boolean isOwner(EntityPlayer player) {
        return player.getUniqueID()
            .equals(getOwner());
    }

    /**
     * 传入的玩家uuid是否为所有者
     *
     * @param playerId 玩家的uuid
     * @return 是所有者则返回真
     */
    public boolean isOwner(UUID playerId) {
        return playerId.equals(getOwner());
    }

    /**
     * 传入的玩家是否为管理员
     */
    public boolean isManager(EntityPlayer player) {
        return managers.contains(player.getUniqueID());
    }

    /**
     * 传入的玩家uuid是否为管理员
     */
    public boolean isManager(UUID playerId) {
        return managers.contains(playerId);
    }

    /**
     * 传入的玩家uuid是否为网络成员
     */
    public boolean isPlayer(UUID playerId) {
        return players.contains(playerId);
    }

    /**
     * 合并另一个网络，其所有资源，玩家均被合并，但其绑定的方块会自动解绑（通过标记另一个网络为被删除实现）
     * <p>
     * 仅在服务端使用
     *
     * @param otherNet 被合并的网络
     */
    public void mergeOtherNet(DimensionsNet otherNet) {
        // 合并玩家和管理员
        MinecraftServer server = MinecraftServer.getServer();
        for (Map.Entry<UUID, PlayerPermissionInfo> entry : otherNet.getPlayerPermissionInfoMap(server)
            .entrySet()) {
            if (entry.getValue()
                .level() == NetPermissionLevel.Owner
                || entry.getValue()
                    .level() == NetPermissionLevel.Manager) {
                addManager(entry.getKey());
            } else if (entry.getValue()
                .level() == NetPermissionLevel.Member) {
                    addPlayer(entry.getKey());
                }
        }
        // 合并统一存储系统
        for (KeyAmount stack : otherNet.getUnifiedStorage()
            .getStorage()) {
            unifiedStorage.insert(stack.key(), stack.amount(), false);
        }

        // 合并 EU 能量池（相加，封顶 10^40）
        euStorage.insert(otherNet.euStorage.getAmount(), false);

        // 销毁另一个网络
        otherNet.destroySelf();
    }

    /**
     * 销毁当前网络
     */
    public void destroySelf() {
        int previousNetId = this.id;
        List<UUID> playerIds = new ArrayList<>(this.players);
        for (UUID playerId : playerIds) {
            syncPlayerRemoval(playerId, previousNetId);
        }

        // 这里有一些问题。即我们实际上无法删除已经存在的 WorldSavedData。
        // 所以我们要做的是巧妙地将此 WorldSavedData 有关数据指向移除。
        // 然后将所有对应的存储容量设置为0
        this.owner = null;
        this.managers.clear();
        this.players.clear();
        this.id = -99; // 用-99作为被删除的特殊标记
        this.unifiedStorage.clearStorage();
        this.euStorage.clear();
        this.deleted = true;
        // 从 FML 总线注销 tick 监听，避免已删网络实例被总线强引用泄漏并逐 tick 空转（审计 M1-6）
        FMLCommonHandler.instance()
            .bus()
            .unregister(this);
        MinecraftServer server = MinecraftServer.getServer();
        if (server != null) {
            NetRegistryIndex.get(server)
                .unregisterNet(server, previousNetId);
        }
        markDirty();
    }

    /**
     * 获取一份当前网络所有玩家的UUID以及其对应的最高权限等级的映射
     */
    public HashMap<UUID, PlayerPermissionInfo> getPlayerPermissionInfoMap(MinecraftServer dataProvider) {

        HashMap<UUID, PlayerPermissionInfo> infoMap = new HashMap<>();
        for (UUID playerId : players) {
            if (isOwner(playerId)) {
                infoMap.put(
                    playerId,
                    new PlayerPermissionInfo(
                        PlayerNameHelper.getPlayerName(dataProvider, playerId),
                        NetPermissionLevel.Owner));
            } else if (isManager(playerId)) {
                infoMap.put(
                    playerId,
                    new PlayerPermissionInfo(
                        PlayerNameHelper.getPlayerName(dataProvider, playerId),
                        NetPermissionLevel.Manager));
            } else {
                infoMap.put(
                    playerId,
                    new PlayerPermissionInfo(
                        PlayerNameHelper.getPlayerName(dataProvider, playerId),
                        NetPermissionLevel.Member));
            }
        }
        return infoMap;
    }

    /**
     * 获取当前网络所携带的统一存储空间，统一存储空间是网络存储资源的地方
     *
     * @return 当前网络的统一存储空间
     */
    public UnifiedStorage getUnifiedStorage() {
        return this.unifiedStorage;
    }

    /**
     * 获取当前网络的 EU 能量池（BigInteger 容量，独立于 RF 池）
     */
    public NetEuStorage getEuStorage() {
        return this.euStorage;
    }

    // ==================== 能量换算桥（EU→RF 单向） ====================
    // GTNH 设计理念：EU 是上游能量，RF 不可反向制造 EU。
    // 故只允许 EU→RF 换算：GT 机器只能从 EU 池取电；RF 池空时可经 extractRf 换算续供。

    /**
     * 向 EU 池存入能量（仅 EU 池，不溢出到 RF）。
     *
     * @return 未接受的余量（10^40 容量下恒为 0）
     */
    public BigInteger insertEu(BigInteger amount, boolean simulate) {
        return euStorage.insert(amount, simulate);
    }

    /**
     * 从 EU 池取出能量（仅 EU 池，无 RF→EU 兜底）。
     *
     * @return 实际取出的能量（≤ demand）
     */
    public BigInteger extractEu(BigInteger demand, boolean simulate) {
        return euStorage.extract(demand, simulate);
    }

    /**
     * 向 RF 池存入能量（仅 RF 池，满则 leftover 丢弃，不溢出到 EU）。
     *
     * @return 未接受的余量
     */
    public long insertRf(long amount, boolean simulate) {
        return unifiedStorage.insert(EnergyStackKey.INSTANCE, amount, simulate)
            .amount();
    }

    /**
     * 从 RF 池取出能量：RF 池优先，不足部分经 EU→RF 换算续供（唯一允许方向）。
     * <p>
     * 返回值封顶在 demand，绝不超需求；ceil 用 BigInteger 计算防 long 溢出（审计 B/C）。
     *
     * @return 实际取出的 RF 能量（≤ demand）
     */
    public long extractRf(long demand, boolean simulate) {
        long fromRf = unifiedStorage.extract(EnergyStackKey.INSTANCE, demand, simulate, false)
            .amount();
        long missing = demand - fromRf;
        if (missing <= 0) {
            return fromRf;
        }
        int rate = ServerConfigRuntime.gtEuToRfRate;
        if (rate <= 0) {
            return fromRf;
        }
        // ceil(missing / rate)，BigInteger 防 long 溢出（审计 C）
        BigInteger ceilEu = BigInteger.valueOf(missing)
            .add(BigInteger.valueOf(rate - 1))
            .divide(BigInteger.valueOf(rate));
        BigInteger euUsed = euStorage.extract(ceilEu, simulate);
        // euUsed×rate 封顶在 missing，绝不超需求（审计 B）
        long fromEu = euUsed.multiply(BigInteger.valueOf(rate))
            .min(BigInteger.valueOf(missing))
            .longValue();
        return fromRf + fromEu;
    }

    /**
     * 用于执行定期操作，目前仅用于生成破碎的时空结晶
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        // 1.7.10 的 ServerTickEvent 每 tick 触发两次（Phase.START + Phase.END），
        // 而源项目 1.20.1 每 tick 只触发一次。这里过滤 END、仅在 START 阶段计数，
        // 使 currentTime 每 tick 递增 1，对齐源项目"每 10 分钟生成一个"的行为。
        if (event.phase == TickEvent.Phase.END) {
            return;
        }

        // 不对临时网络执行倒计时；已删除网络不参与（destroySelf 后实例仍留在 FML 总线上，
        // 不加守卫会持续递增 + markDirty + 向已清空存储插结晶，文件永不消亡）
        if (temporary || deleted || ServerConfigRuntime.crystalGenerateTime <= 0) {
            return;
        }

        currentTime++;
        // 逐 tick markDirty 会让每个自动存档都重写全部网络的整档数据（网络多时序列化+磁盘
        // 开销可观，审计 M1-14）。改为每 200 tick（10 秒）落盘一次倒计时进度：currentTime
        // 仍按原语义持久化（重启丢失最多 10 秒进度，对 10 分钟生成周期无感知影响），
        // 而脏标记频率降低 200 倍。
        if (currentTime % 200 == 0) {
            markDirty();
        }
        if (currentTime >= ServerConfigRuntime.crystalGenerateTime * 20) {
            ItemStack stack = new ItemStack(BDItems.SHATTERED_SPACE_TIME_CRYSTALLIZATION, 1, 0);
            this.unifiedStorage.insert(new ItemStackKey(stack), stack.stackSize, false);
            currentTime = 0;
            markDirty();
        }
    }

    private void syncPlayerMembership(UUID playerId, boolean switchPrimary) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || id < 0) {
            return;
        }

        PlayerNetIndex.get(server)
            .addMembership(playerId, id, switchPrimary);
    }

    private void syncPlayerRemoval(UUID playerId) {
        syncPlayerRemoval(playerId, this.id);
    }

    private static void syncPlayerRemoval(UUID playerId, int netId) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || netId < 0) {
            return;
        }

        PlayerNetIndex.get(server)
            .removeMembership(playerId, netId);
    }

    /**
     * 从 NBT 读取数据（WorldSavedData 抽象方法）
     */
    @Override
    public void readFromNBT(NBTTagCompound tag) {
        this.id = tag.getInteger("Id");
        if (tag.hasKey(CUSTOM_NAME_TAG)) {
            this.customName = sanitizeCustomName(tag.getString(CUSTOM_NAME_TAG));
        }

        // 读取所有者 UUID
        if (tag.hasKey("OwnerMost", 4) && tag.hasKey("OwnerLeast", 4)) {
            this.owner = new UUID(tag.getLong("OwnerMost"), tag.getLong("OwnerLeast"));
        }

        // 读取统一存储
        if (tag.hasKey("UnifiedStorage", 10)) {
            unifiedStorage.deserializeNBT(tag.getCompoundTag("UnifiedStorage"));
        }

        // 读取 EU 能量池（无 EuStorage 标签 → amount=0，旧存档兼容）
        if (tag.hasKey("EuStorage", 10)) {
            euStorage.readFromNBT(tag.getCompoundTag("EuStorage"));
        }

        // 旧数据兼容 - EnergyStorage
        if (tag.hasKey("EnergyStorage", 10)) {
            NBTTagCompound energyTag = tag.getCompoundTag("EnergyStorage");
            if (energyTag.hasKey("Energy", 4)) {
                unifiedStorage.insert(EnergyStackKey.INSTANCE, energyTag.getLong("Energy"), false);
            }
        }

        // 读取管理员列表
        managers.clear();
        if (tag.hasKey("Managers", 9)) {
            NBTTagList managerList = tag.getTagList("Managers", 8);
            for (int i = 0; i < managerList.tagCount(); i++) {
                try {
                    managers.add(UUID.fromString(managerList.getStringTagAt(i)));
                } catch (Exception ignored) {}
            }
        }

        // 读取玩家列表
        players.clear();
        if (tag.hasKey("Players", 9)) {
            NBTTagList playerList = tag.getTagList("Players", 8);
            for (int i = 0; i < playerList.tagCount(); i++) {
                try {
                    players.add(UUID.fromString(playerList.getStringTagAt(i)));
                } catch (Exception ignored) {}
            }
        }

        // 读取倒计时
        this.currentTime = tag.getInteger("currentTime");

        // 读取删除状态
        if (tag.hasKey("Deleted")) {
            this.deleted = tag.getBoolean("Deleted");
        }
    }

    /**
     * 将数据写入 NBT（WorldSavedData 抽象方法）
     */
    @Override
    public void writeToNBT(NBTTagCompound tag) {
        // 保存 ID
        tag.setInteger("Id", this.id);
        if (!this.customName.isEmpty()) {
            tag.setString(CUSTOM_NAME_TAG, this.customName);
        }

        // 保存网络所有者 UUID
        if (this.owner != null) {
            tag.setLong("OwnerMost", this.owner.getMostSignificantBits());
            tag.setLong("OwnerLeast", this.owner.getLeastSignificantBits());
        }

        // 旧数据标记
        if (!tag.hasKey("OldDataTag")) {
            tag.setBoolean("OldDataTag", true);
        }

        // 保存网络管理者
        NBTTagList managerListTag = new NBTTagList();
        for (UUID manager : managers) {
            managerListTag.appendTag(new NBTTagString(manager.toString()));
        }
        tag.setTag("Managers", managerListTag);

        // 保存绑定的玩家列表
        NBTTagList playerListTag = new NBTTagList();
        for (UUID player : players) {
            playerListTag.appendTag(new NBTTagString(player.toString()));
        }
        tag.setTag("Players", playerListTag);

        // 保存存储
        tag.setTag("UnifiedStorage", unifiedStorage.serializeNBT());

        // 保存 EU 能量池（BigInteger 十进制字符串）
        NBTTagCompound euStorageTag = new NBTTagCompound();
        euStorage.writeToNBT(euStorageTag);
        tag.setTag("EuStorage", euStorageTag);

        // 保存倒计时
        tag.setInteger("currentTime", this.currentTime);

        // 保存删除状态
        tag.setBoolean("Deleted", this.deleted);
    }

    private static String sanitizeCustomName(@Nullable String customName) {
        if (customName == null) {
            return "";
        }

        String trimmedName = customName.trim();
        if (trimmedName.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder(Math.min(trimmedName.length(), MAX_NETWORK_NAME_LENGTH));
        int appendedCodePoints = 0;
        for (int offset = 0; offset < trimmedName.length() && appendedCodePoints < MAX_NETWORK_NAME_LENGTH;) {
            int codePoint = trimmedName.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isISOControl(codePoint)) {
                continue;
            }

            builder.appendCodePoint(codePoint);
            appendedCodePoints++;
        }
        return builder.toString()
            .trim();
    }
}
