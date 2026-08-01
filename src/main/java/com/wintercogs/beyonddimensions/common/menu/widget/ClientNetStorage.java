package com.wintercogs.beyonddimensions.common.menu.widget;

import java.util.*;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.wintercogs.beyonddimensions.api.ButtonState;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 维度网络UI特化的IStackKey客户端存储（1.7.10 移植版）。
 * 为其指定一个源存储，负责从源存储处同步数据、应用搜索、排序功能。
 * <p>
 * 1.7.10 适配：record → 静态内部类；CreativeModeTab → CreativeTabs；
 * switch 表达式 → switch 语句；storage 字段 → storageMap。
 */
@SideOnly(Side.CLIENT)
public class ClientNetStorage extends AbstractUnorderedStackHandler {

    private static final int CREATIVE_SORT_LAST = Integer.MAX_VALUE;

    /**
     * 原有的，对客户端而言绝对真实的存储
     */
    private final AbstractUnorderedStackHandler sourceStorage;

    private final Set<IStackKey<?>> pendingCache = new HashSet<>();

    private boolean mustUpdateAllFromSource = true;

    private final AutoCloseable anySubscriber;
    private final AutoCloseable deltaSubscriber;

    private final ClientNetStorageSearchHelper searchHelper = new ClientNetStorageSearchHelper();

    private final Map<Item, CreativeRank> creativeRankCache = new IdentityHashMap<>();
    private boolean creativeRankCacheBuilt = false;

    private List<Integer> cacheIndexes = null;

    private SortProperties lastSortProperties = new SortProperties(ButtonState.DISABLED, ButtonState.DISABLED, false);

    public ClientNetStorage(AbstractUnorderedStackHandler sourceStorage) {
        // 初始化为KEEP_ZERO，后续调用时在必要时手动设置
        super(ZeroPolicy.KEEP_ZERO, UiTimestampPolicy.NONE);

        this.sourceStorage = sourceStorage;

        this.anySubscriber = this.sourceStorage.subscribeAnyWeak(this, ClientNetStorage::markForceAllUpdate);
        this.deltaSubscriber = this.sourceStorage.subscribeDeltaWeak(this, ClientNetStorage::loadFromDeltaSubscription);
    }

    public void markForceAllUpdate() {
        this.mustUpdateAllFromSource = true;
    }

    public void setSearchText(String newSearchText) {
        Objects.requireNonNull(newSearchText);
        this.searchHelper.loadTexts(newSearchText);
    }

    private void loadFromDeltaSubscription(IStackKey<?> key, long delta, Boolean insert) {
        if (mustUpdateAllFromSource) {
            pendingCache.clear();
            return;
        }

        if (delta != 0) {
            pendingCache.add(key);
        }
    }

    public void resolvePendingOrAllUpdate(boolean onlyAmountUpdate) {
        boolean anyChanged = false;
        if (mustUpdateAllFromSource) {
            pendingCache.clear();
            updateViewFromStorage(onlyAmountUpdate);
            this.mustUpdateAllFromSource = false;
            anyChanged = true;
        } else {
            Iterator<IStackKey<?>> it = pendingCache.iterator();
            while (it.hasNext()) {
                IStackKey<?> key = it.next();
                if (key.isEmpty()) {
                    // 空 key 不会在 sourceStorage 中存在有效条目，直接移除；
                    // 若仅 continue 而不移除，迭代器不推进会导致客户端主线程死循环。
                    it.remove();
                    continue;
                }

                long newAmount = sourceStorage.getStackByKey(key)
                    .amount();
                if (this.hasStack(key)) {
                    anyChanged = true;
                    this.setAmountByKey(key, newAmount);
                } else if (!onlyAmountUpdate && matchFilter(key)) {
                    anyChanged = true;
                    this.setAmountByKey(key, newAmount);
                }

                it.remove();
            }
        }

        if (anyChanged) {
            this.cacheIndexes = null;
        }
    }

    /**
     * 从真实存储处更新视图状态
     */
    private void updateViewFromStorage(boolean onlyAmountUpdate) {
        if (onlyAmountUpdate) {
            for (IStackKey<?> key : this.storageMap.keySet()) {
                long amount = sourceStorage.getStackByKey(key)
                    .amount();
                this.setAmountByKey(key, amount);
            }
        } else {
            this.clearStorage();
            for (KeyAmount ka : this.sourceStorage.getStorage()) {
                if (ka == null || !matchFilter(ka.key())) continue;
                this.setAmountByKey(ka.key(), ka.amount());
            }
        }
    }

    /**
     * 根据当前存储的状态以及对应的排序策略，返回一个下标数组
     */
    public List<Integer> buildSortedIndex(ButtonState primarySortPolicy, ButtonState secondarySortPolicy,
        boolean reverse) {
        if (cacheIndexes != null && primarySortPolicy == lastSortProperties.primarySortPolicy
            && secondarySortPolicy == lastSortProperties.secondarySortPolicy
            && reverse == lastSortProperties.reverse) {
            return cacheIndexes;
        }

        if (primarySortPolicy == null) primarySortPolicy = ButtonState.SORT_NAME;
        final boolean useSecondary = (secondarySortPolicy != null && secondarySortPolicy != primarySortPolicy);

        final boolean needNameSort = (primarySortPolicy == ButtonState.SORT_NAME)
            || (useSecondary && secondarySortPolicy == ButtonState.SORT_NAME);
        final boolean needModIdSort = (primarySortPolicy == ButtonState.SORT_MODID)
            || (useSecondary && secondarySortPolicy == ButtonState.SORT_MODID);
        final boolean needQuantitySort = (primarySortPolicy == ButtonState.SORT_QUANTITY)
            || (useSecondary && secondarySortPolicy == ButtonState.SORT_QUANTITY);
        final boolean needMaxStackSort = (primarySortPolicy == ButtonState.SORT_MAX_STACK)
            || (useSecondary && secondarySortPolicy == ButtonState.SORT_MAX_STACK);
        final boolean needCreationTimeSort = (primarySortPolicy == ButtonState.SORT_INSERTED_TIME)
            || (useSecondary && secondarySortPolicy == ButtonState.SORT_INSERTED_TIME);
        final boolean needModificationTimeSort = (primarySortPolicy == ButtonState.SORT_MODIFIED_TIME)
            || (useSecondary && secondarySortPolicy == ButtonState.SORT_MODIFIED_TIME);
        final boolean needCreativeTabSort = (primarySortPolicy == ButtonState.SORT_CREATIVE_TAB)
            || (useSecondary && secondarySortPolicy == ButtonState.SORT_CREATIVE_TAB);

        if (needCreativeTabSort) {
            ensureCreativeRankCache();
        }

        final Map<IStackKey<?>, Long> creationTimeMap = needCreationTimeSort ? sourceStorage.getCreationTimeMap()
            : null;
        final Map<IStackKey<?>, Long> modificationTimeMap = needModificationTimeSort
            ? sourceStorage.getLastModifiedTimeMap()
            : null;

        final ArrayList<Row> rows = new ArrayList<>(
            this.getStorage()
                .size());

        for (int i = 0; i < this.getStorage()
            .size(); i++) {
            KeyAmount ka = this.getStorage()
                .get(i);
            if (ka == null || ka.isEmpty()) continue;

            IStackKey<?> key = ka.key();

            String displayName = null;
            String modIdSort = null;

            if (needNameSort) {
                displayName = key.getRender()
                    .getDisplayName(key);
            }
            if (needModIdSort) {
                modIdSort = key.getModId();
            }

            long amt = needQuantitySort ? ka.amount() : 0L;
            long maxStack = needMaxStackSort ? key.getVanillaMaxStackSize() : 0L;
            long ctime = (needCreationTimeSort && creationTimeMap != null) ? creationTimeMap.getOrDefault(key, 0L) : 0L;
            long mtime = (needModificationTimeSort && modificationTimeMap != null)
                ? modificationTimeMap.getOrDefault(key, 0L)
                : 0L;
            int creativeTabOrder = CREATIVE_SORT_LAST;
            int creativeItemOrder = CREATIVE_SORT_LAST;
            if (needCreativeTabSort) {
                CreativeRank rank = getCreativeRank(key);
                if (rank != null) {
                    creativeTabOrder = rank.tabOrder;
                    creativeItemOrder = rank.itemOrder;
                }
            }

            rows.add(
                new Row(i, displayName, modIdSort, amt, maxStack, ctime, mtime, creativeTabOrder, creativeItemOrder));
        }

        if (!rows.isEmpty()) {
            final Comparator<Row> primary = buildRowComparator(primarySortPolicy);
            if (useSecondary) {
                final Comparator<Row> secondary = buildRowComparator(secondarySortPolicy);
                Collections.sort(rows, primary.thenComparing(secondary));
            } else {
                Collections.sort(rows, primary);
            }
            if (reverse) {
                Collections.reverse(rows);
            }
        }

        ArrayList<Integer> result = new ArrayList<>(rows.size());
        for (Row row : rows) {
            result.add(row.idx);
        }
        this.cacheIndexes = result;
        this.lastSortProperties = new SortProperties(primarySortPolicy, secondarySortPolicy, reverse);
        return result;
    }

    /**
     * 搜索过滤逻辑
     */
    private boolean matchFilter(IStackKey<?> key) {
        return this.searchHelper.matches(key);
    }

    /**
     * 比较 Row 中已准备好的字段
     */
    private Comparator<Row> buildRowComparator(ButtonState state) {
        switch (state) {
            case SORT_CREATIVE_TAB:
                return new Comparator<Row>() {

                    @Override
                    public int compare(Row a, Row b) {
                        int cmp = Integer.compare(a.creativeTabOrder, b.creativeTabOrder);
                        if (cmp != 0) return cmp;
                        return Integer.compare(a.creativeItemOrder, b.creativeItemOrder);
                    }
                };
            case SORT_QUANTITY:
                return new Comparator<Row>() {

                    @Override
                    public int compare(Row a, Row b) {
                        return Long.compare(a.amount, b.amount);
                    }
                };
            case SORT_MAX_STACK:
                return new Comparator<Row>() {

                    @Override
                    public int compare(Row a, Row b) {
                        return Long.compare(a.maxStack, b.maxStack);
                    }
                };
            case SORT_NAME:
                return new Comparator<Row>() {

                    @Override
                    public int compare(Row a, Row b) {
                        if (a.name == null && b.name == null) return 0;
                        if (a.name == null) return -1;
                        if (b.name == null) return 1;
                        return a.name.compareTo(b.name);
                    }
                };
            case SORT_MODID:
                return new Comparator<Row>() {

                    @Override
                    public int compare(Row a, Row b) {
                        if (a.modIdSort == null && b.modIdSort == null) return 0;
                        if (a.modIdSort == null) return -1;
                        if (b.modIdSort == null) return 1;
                        return a.modIdSort.compareTo(b.modIdSort);
                    }
                };
            case SORT_INSERTED_TIME:
                return new Comparator<Row>() {

                    @Override
                    public int compare(Row a, Row b) {
                        return Long.compare(a.ctime, b.ctime);
                    }
                };
            case SORT_MODIFIED_TIME:
                return new Comparator<Row>() {

                    @Override
                    public int compare(Row a, Row b) {
                        return Long.compare(a.mtime, b.mtime);
                    }
                };
            default:
                return new Comparator<Row>() {

                    @Override
                    public int compare(Row a, Row b) {
                        if (a.name == null && b.name == null) return 0;
                        if (a.name == null) return -1;
                        if (b.name == null) return 1;
                        return a.name.compareTo(b.name);
                    }
                };
        }
    }

    /**
     * 排序行数据（1.7.10 无 record，使用静态内部类）
     */
    private static final class Row {

        final int idx;
        final String name;
        final String modIdSort;
        final long amount;
        final long maxStack;
        final long ctime;
        final long mtime;
        final int creativeTabOrder;
        final int creativeItemOrder;

        Row(int idx, String name, String modIdSort, long amount, long maxStack, long ctime, long mtime,
            int creativeTabOrder, int creativeItemOrder) {
            this.idx = idx;
            this.name = name;
            this.modIdSort = modIdSort;
            this.amount = amount;
            this.maxStack = maxStack;
            this.ctime = ctime;
            this.mtime = mtime;
            this.creativeTabOrder = creativeTabOrder;
            this.creativeItemOrder = creativeItemOrder;
        }
    }

    /**
     * 创造模式标签页排序缓存（1.7.10 无 record，使用静态内部类）
     */
    private static final class CreativeRank {

        final int tabOrder;
        final int itemOrder;

        CreativeRank(int tabOrder, int itemOrder) {
            this.tabOrder = tabOrder;
            this.itemOrder = itemOrder;
        }
    }

    /**
     * 排序属性（1.7.10 无 record，使用静态内部类）
     */
    private static final class SortProperties {

        final ButtonState primarySortPolicy;
        final ButtonState secondarySortPolicy;
        final boolean reverse;

        SortProperties(ButtonState primarySortPolicy, ButtonState secondarySortPolicy, boolean reverse) {
            this.primarySortPolicy = primarySortPolicy;
            this.secondarySortPolicy = secondarySortPolicy;
            this.reverse = reverse;
        }
    }

    private void ensureCreativeRankCache() {
        if (creativeRankCacheBuilt) return;

        creativeRankCache.clear();
        int tabOrder = 0;
        // 1.7.10: CreativeTabs.creativeTabArray 是静态数组
        CreativeTabs[] tabs = CreativeTabs.creativeTabArray;
        if (tabs != null) {
            for (CreativeTabs tab : tabs) {
                if (tab == null) continue;
                // 1.7.10: 跳过搜索标签和库存标签
                if (tab == CreativeTabs.tabAllSearch || tab == CreativeTabs.tabInventory) continue;

                List<ItemStack> displayItems = new ArrayList<>();
                tab.displayAllReleventItems(displayItems);

                int itemOrder = 0;
                for (ItemStack stack : displayItems) {
                    if (stack == null || stack.getItem() == null) continue;
                    Item item = stack.getItem();
                    if (!creativeRankCache.containsKey(item)) {
                        creativeRankCache.put(item, new CreativeRank(tabOrder, itemOrder));
                    }
                    itemOrder++;
                }

                tabOrder++;
            }
        }

        creativeRankCacheBuilt = true;
    }

    private CreativeRank getCreativeRank(IStackKey<?> key) {
        if (!(key instanceof ItemStackKey)) {
            return null;
        }

        ItemStackKey itemStackKey = (ItemStackKey) key;
        Object source = itemStackKey.getSource();
        if (source instanceof Item) {
            return creativeRankCache.get((Item) source);
        }
        return null;
    }
}
