package com.wintercogs.beyonddimensions.integration.module.nei;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.Item;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;

import codechicken.nei.BookmarkPanel;
import codechicken.nei.ItemPanels;
import codechicken.nei.bookmark.BookmarkGrid;
import codechicken.nei.bookmark.BookmarkItem;
import codechicken.nei.bookmark.BookmarksGridSlot;
import codechicken.nei.recipe.chain.RecipeChainMath;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * NEI 合成链精准暴露处理器（方案 A）。
 * <p>
 * 解决 1.7.10 GTNH NEI 特有功能与本项目存储机制冲突：NEI 合成链材料检查（
 * {@code RecipeChainTooltipLineHandler} → {@code AutoCraftingManager.getInventoryItems}）
 * 只能遍历 GUI 容器槽位，而网络存储有几千种、且会被搜索过滤。本处理器逐帧检测
 * 当前悬停的收藏夹组，把"该组合成链所需物品"按槽位位置暴露到非活跃槽位，NEI 读到的
 * 即网络真实库存（数量每 tick 从 {@code menu.storage} 实时读取），与库存种类数完全解耦。
 * <p>
 * 关键点：
 * - 悬停组检测基于当前视图鼠标位置（{@code BookmarkPanel.getSlotMouseOver}），分页无关；
 * - 暴露必须用网络真实 key（按 item+meta 的 {@code isSame} 匹配收集），兼容 GT 工具耐久 NBT 变体；
 * - 跳过当前页已显示条目（防双计数）；
 * - 任何 NEI 内部异常均回退为清空暴露，退化为修复前行为，不影响 GUI 正常功能。
 */
@SideOnly(Side.CLIENT)
public class NeiBookmarkExposureHandler {

    public static final NeiBookmarkExposureHandler INSTANCE = new NeiBookmarkExposureHandler();

    /** 暴露容量 = 存储槽位池大小（与 {@link DimensionsNetMenu#MAX_STORAGE_ROWS} 一致） */
    private static final int EXPOSURE_CAPACITY = DimensionsNetMenu.MAX_STORAGE_ROWS * 9;

    /** 悬停同组时网络 key 集的重扫间隔（tick），捕捉悬停期间新入库的匹配条目 */
    private static final int REFRESH_INTERVAL = 100;

    private final LinkedHashMap<Integer, GroupCache> cache = new LinkedHashMap<Integer, GroupCache>(16, 0.75f, true) {

        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, GroupCache> eldest) {
            return size() > 8;
        }
    };

    private int lastGroupId = -1;
    private int tickCounter = 0;

    private NeiBookmarkExposureHandler() {}

    /**
     * 每 tick 由终端 GUI 的 updateScreen 经 {@code NeiExposureBridge} 调用。
     */
    public void update(DimensionsNetMenu menu, int mouseX, int mouseY) {
        if (menu == null) return;
        try {
            BookmarkPanel panel = ItemPanels.bookmarkPanel;
            int groupId = detectGroupId(panel, mouseX, mouseY);
            if (groupId < 0) {
                menu.clearNeiExposure();
                lastGroupId = -1;
                return;
            }

            boolean groupChanged = (groupId != lastGroupId);
            lastGroupId = groupId;

            GroupCache gc = cache.get(groupId);
            if (gc == null || groupChanged) {
                gc = buildGroupCache(menu, groupId);
                cache.put(groupId, gc);
            }
            if (gc == null || gc.networkKeys.isEmpty()) {
                menu.clearNeiExposure();
                return;
            }

            // 慢周期重扫网络 key 集（新入库的匹配条目；链内 key 集不变，无需重跑 chain math）
            if (++tickCounter % REFRESH_INTERVAL == 0) {
                gc.networkKeys = scanNetworkKeys(menu, gc.chainKeys);
            }

            menu.setNeiExposure(buildExposure(menu, gc.networkKeys));
        } catch (Throwable th) {
            // 回退：NEI 内部结构变化或异常时退化为修复前行为（槽位返回空），不影响 GUI
            try {
                menu.clearNeiExposure();
            } catch (Throwable ignored) {
                // 忽略
            }
            lastGroupId = -1;
        }
    }

    /** 当前视图下鼠标悬停的收藏夹组 id；无悬停返回 -1（分页无关：基于当前显示页） */
    private int detectGroupId(BookmarkPanel panel, int mouseX, int mouseY) {
        if (panel == null) return -1;
        if (!panel.contains(mouseX, mouseY)) return -1;
        BookmarksGridSlot slot = panel.getSlotMouseOver(mouseX, mouseY);
        if (slot != null) return slot.getGroupId();
        BookmarkGrid grid = getGrid(panel);
        if (grid != null) {
            int row = grid.getHoveredRowIndex(true);
            if (row >= 0) return grid.getRowGroupId(row);
        }
        return -1;
    }

    /** 构建某组的缓存：链内 key 集（createRecipeChainMath，O(链²)）+ 网络 key 集（O(类型) 扫描） */
    private GroupCache buildGroupCache(DimensionsNetMenu menu, int groupId) {
        BookmarkGrid grid = getGrid(ItemPanels.bookmarkPanel);
        if (grid == null) return new GroupCache(Collections.<ItemStackKey>emptyList());
        RecipeChainMath math = grid.createRecipeChainMath(groupId);
        List<ItemStackKey> chainKeys = new ArrayList<>();
        collectChainKeys(math.initialItems, chainKeys);
        collectChainKeys(math.recipeIngredients, chainKeys);
        collectChainKeys(math.recipeResults, chainKeys);
        GroupCache gc = new GroupCache(chainKeys);
        gc.networkKeys = scanNetworkKeys(menu, chainKeys);
        return gc;
    }

    /**
     * 单次扫描网络存储，收集与链内物品 item+meta 相同（isSame）且存在数量的网络真实 key。
     * <p>
     * 性能硬约束：链内 key 先按 Item 建立哈希索引，每条存储条目只与该 Item 的少量候选比较，
     * 整体 O(类型)；禁止对全链 key 列表线性匹配（会退化为 O(类型 × 链)）。
     */
    private List<ItemStackKey> scanNetworkKeys(DimensionsNetMenu menu, List<ItemStackKey> chainKeys) {
        if (chainKeys.isEmpty()) return Collections.emptyList();

        Map<Item, List<ItemStackKey>> chainIndex = new HashMap<>();
        for (ItemStackKey key : chainKeys) {
            Item item = key.getSource();
            if (item == null) continue;
            List<ItemStackKey> list = chainIndex.computeIfAbsent(item, i -> new ArrayList<>());
            if (!containsSame(list, key)) list.add(key);
        }

        List<ItemStackKey> result = new ArrayList<>();
        for (KeyAmount ka : menu.storage.getStorage()) {
            if (ka == null || ka.isEmpty()) continue;
            IStackKey<?> key = ka.key();
            if (!(key instanceof ItemStackKey)) continue;
            ItemStackKey ik = (ItemStackKey) key;
            List<ItemStackKey> candidates = chainIndex.get(ik.getSource());
            if (candidates == null) continue;
            for (ItemStackKey candidate : candidates) {
                if (ik.isSame(candidate)) {
                    result.add(ik);
                    break;
                }
            }
        }
        return result;
    }

    /** 构建暴露数组：数量实时读取，跳过当前页已显示条目，从活跃槽位数之后起填 */
    private KeyAmount[] buildExposure(DimensionsNetMenu menu, List<ItemStackKey> networkKeys) {
        KeyAmount[] exposure = new KeyAmount[EXPOSURE_CAPACITY];
        Set<ItemStackKey> pageKeys = new HashSet<>();
        for (KeyAmount ka : menu.getDisplayedStorageEntries()) {
            pageKeys.add((ItemStackKey) ka.key());
        }
        int pos = menu.getLines() * 9;
        for (ItemStackKey key : networkKeys) {
            if (pos >= exposure.length) break;
            if (pageKeys.contains(key)) continue;
            KeyAmount ka = menu.storage.getStackByKey(key);
            if (ka == null || ka.isEmpty()) continue;
            exposure[pos++] = ka;
        }
        return exposure;
    }

    private void collectChainKeys(List<BookmarkItem> items, List<ItemStackKey> out) {
        if (items == null) return;
        for (BookmarkItem item : items) {
            if (item == null || item.itemStack == null || item.itemStack.getItem() == null) continue;
            ItemStackKey key = new ItemStackKey(item.itemStack);
            if (!containsSame(out, key)) out.add(key);
        }
    }

    private boolean containsSame(List<ItemStackKey> keys, ItemStackKey key) {
        for (ItemStackKey k : keys) {
            if (k.isSame(key)) return true;
        }
        return false;
    }

    /**
     * 反射沿父类链取 {@link BookmarkPanel} 的 grid 字段（继承自 CodeChickenLib
     * {@code PanelWidget<BookmarkGrid>}，可见性未定；NEI 版本已钉死，稳定）。
     */
    private BookmarkGrid getGrid(BookmarkPanel panel) {
        Field field = findField(panel.getClass(), "grid");
        if (field == null) return null;
        try {
            return (BookmarkGrid) field.get(panel);
        } catch (Exception e) {
            return null;
        }
    }

    private Field findField(Class<?> clazz, String name) {
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                // 继续沿父类查找
            }
        }
        return null;
    }

    /** 某收藏组的暴露缓存：链内 key 集 + 网络 key 集（网络 key 集按慢周期重扫刷新） */
    private static final class GroupCache {

        final List<ItemStackKey> chainKeys;
        List<ItemStackKey> networkKeys = Collections.emptyList();

        GroupCache(List<ItemStackKey> chainKeys) {
            this.chainKeys = chainKeys;
        }
    }
}
