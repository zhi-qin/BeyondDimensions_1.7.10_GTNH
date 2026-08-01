package com.wintercogs.beyonddimensions.integration.module.nei;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.wintercogs.beyonddimensions.BeyondDimensions;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EmptyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.init.BDPackets;
import com.wintercogs.beyonddimensions.common.menu.DimensionsCraftMenu;
import com.wintercogs.beyonddimensions.network.packet.c2s.AutoCraftC2SPacket;
import com.wintercogs.beyonddimensions.network.packet.c2s.RecipeFillC2SPacket;
import com.wintercogs.beyonddimensions.util.RegistryUtil;

import codechicken.nei.NEIClientUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.api.IOverlayHandler;
import codechicken.nei.recipe.GuiOverlayButton.ItemOverlayState;
import codechicken.nei.recipe.IRecipeHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * NEI 配方补全 overlay handler（1.7.10 适配版）。
 * <p>
 * 对应源项目（1.20.1）中的 JEI 配方转移体系：
 * {@code CraftMenuRecipeTransferHandler} / {@code CraftTerminalRecipeTransferHandler}
 * / {@code MenuUniversalRecipeTransfer} / {@code TerminalUniversalRecipeTransfer}
 * + {@code TransferHelper}（合成槽 + 网络存储 + 背包收集可用物，发 {@code RecipeFillC2SPacket}）。
 * <p>
 * NEI 的「补全」(Fill Crafting Grid) 按钮经 {@code RecipeInfo.overlayMap}
 * 按 GUI 类 + ident 精确匹配 {@link IOverlayHandler}。注册后（ident "crafting"），
 * 点击补全会调用 {@link #transferRecipe}，本类将配方原料映射到合成板槽位，
 * 汇总 工艺槽现有物 + 维度网络存储 + 玩家背包 的可用量，计算方案后由
 * {@link RecipeFillC2SPacket} 发往服务端执行实际取物填充。
 * <p>
 * 仅作用于 {@link DimensionsCraftMenu} 及其子类（含合成终端）。
 */
@SideOnly(Side.CLIENT)
public class RecipeTransferOverlayHandler implements IOverlayHandler {

    /** NEI 合成配方网格坐标原点（Shaped/ShapelessRecipeHandler 均为 25 + x*18, 6 + y*18）。 */
    private static final int GRID_ORIGIN_X = 25;
    private static final int GRID_ORIGIN_Y = 6;
    private static final int GRID_CELL_SIZE = 18;

    @Override
    public void overlayRecipe(GuiContainer firstGui, IRecipeHandler recipe, int recipeIndex, boolean maxTransfer) {
        doTransfer(firstGui, recipe, recipeIndex, maxTransfer);
    }

    @Override
    public int transferRecipe(GuiContainer firstGui, IRecipeHandler recipe, int recipeIndex, int multiplier) {
        // 对齐源项目 TransferHelper 的 maxTransfer 语义：单击 1 组，shift 最大
        boolean maxTransfer = (multiplier == 0) ? NEIClientUtils.shiftKey() : (multiplier > 1);
        return doTransfer(firstGui, recipe, recipeIndex, maxTransfer);
    }

    @Override
    public boolean canFillCraftingGrid(GuiContainer firstGui, IRecipeHandler recipe, int recipeIndex) {
        return firstGui != null && firstGui.inventorySlots instanceof DimensionsCraftMenu;
    }

    /**
     * NEI 自动合成（Shift+C，合成树/收藏组）是否可执行。
     * <p>
     * 注意：AutoCraftingManager 在**后台线程**调用本方法，绝不能调用
     * {@code presenceOverlay}/getIngredientStacks（其内部 getCycledIngredients 会访问
     * LWJGL Keyboard → 主线程专属，后台线程抛 IllegalStateException）。
     * 这里只做容器类型检查，材料是否充足由 {@link #craft} 实际取物决定（BUGFIX_RECORD #102）。
     */
    @Override
    public boolean canCraft(GuiContainer firstGui, IRecipeHandler handler, int recipeIndex) {
        return firstGui != null && firstGui.inventorySlots instanceof DimensionsCraftMenu;
    }

    /**
     * NEI 自动合成执行（Shift+C / 合成树 / 收藏组）。
     * <p>
     * 必须把真正的填充调度到主线程（getIngredientStacks 需访问 LWJGL Keyboard），
     * 并返回 false 终止 AutoCraftingManager 的 do-while 循环——否则客户端网络存储量
     * 不会随服务端提取即时下降，"材料在场"恒为真，循环无限刷包导致客户端卡死
     * （复现：Shift+C 合成螺栓，锯子上台后卡住，后台 [BD-NEI] 日志刷屏；BUGFIX_RECORD #102）。
     * <p>
     * NEI 传入的 {@code multiplier} = 配方执行次数（如合成树里 10 铁锭 = 5 次锻造锤合成
     * = 5 铁板时 multiplier 为 5）。整批一次发给服务端，由
     * {@link DimensionsCraftMenu#autoCraft} 逐次"填充→取走结果→产出"（BUGFIX_RECORD #103）。
     */
    @Override
    public boolean craft(GuiContainer firstGui, IRecipeHandler handler, int recipeIndex, int multiplier) {
        if (firstGui == null || !(firstGui.inventorySlots instanceof DimensionsCraftMenu)) {
            return false;
        }
        final GuiContainer gui = firstGui;
        // 上限 64 与 NEI AutoCraftingManager 的单批步长一致；服务端也会再次兜底
        final int crafts = Math.max(1, Math.min(64, multiplier));
        // func_152344_a = Minecraft.addScheduledTask（1.7.10 MCP stable_12 未映射该方法名，用 SRG 名调用）
        Minecraft.getMinecraft()
            .func_152344_a(() -> sendAutoCraft(gui, handler, recipeIndex, crafts));
        return false;
    }

    /**
     * 材料在场提示（NEI 绿/红点）：除玩家背包与工艺槽外，额外计入维度网络存储，
     * 使「缺少」提示能正确反映网络库存。
     * <p>
     * 精准识别：按 item+meta（{@link ItemStackKey#isSame}，忽略 NBT）聚合可用物，
     * 并只按 NEI 当前渲染物（{@code ps.item}，用户所见配方图标）的精确 meta 判断在场。
     * 不再按 Item 聚合——1.7.10 GT5 中研钵(meta=24)/软锤(meta=14)/小刀(meta=34)是同一
     * Item（gt.metatool.01）的不同 meta，按 Item 聚合会把「只有小刀」误判为「有研钵」；
     * 哈密瓜等不同 meta 变体同理会被错误累加（对齐 AE2 {@code Platform.isSameItem} 的
     * item+meta 识别语义，而非 AE2 {@code isSameItemType} 对可耐久工具忽略 meta 的宽松判定——
     * GT 工具 meta 标识工具类型，耐久/材料存于 NBT，忽略 meta 会把不同工具混为一种）。
     */
    @Override
    public List<ItemOverlayState> presenceOverlay(GuiContainer firstGui, IRecipeHandler recipe, int recipeIndex) {
        final List<ItemOverlayState> result = new ArrayList<>();
        if (firstGui == null || !(firstGui.inventorySlots instanceof DimensionsCraftMenu)) {
            return result;
        }
        DimensionsCraftMenu menu = (DimensionsCraftMenu) firstGui.inventorySlots;

        // 按 item+meta 聚合（NBT 不同的同 meta 物品经 isSame 合并计数）
        final Map<ItemStackKey, Long> available = new HashMap<>();

        for (int i = menu.craftSlotStartIndex; i < menu.craftSlotEndIndex; i++) {
            Slot slot = menu.getSlot(i);
            if (slot.getHasStack()) {
                ItemStack s = slot.getStack();
                if (s != null) {
                    mergeBySame(available, new ItemStackKey(s), s.stackSize);
                }
            }
        }

        if (menu.storage != null) {
            for (KeyAmount ka : menu.storage.getStorage()) {
                if (ka == null || ka.isEmpty()) continue;
                if (ka.key() instanceof ItemStackKey) {
                    mergeBySame(available, (ItemStackKey) ka.key(), ka.amount());
                }
            }
        }

        EntityPlayer player = firstGui.mc != null ? firstGui.mc.thePlayer : null;
        if (player != null && player.inventory != null) {
            ItemStack[] main = player.inventory.mainInventory;
            if (main != null) {
                for (ItemStack s : main) {
                    if (s != null) {
                        mergeBySame(available, new ItemStackKey(s), s.stackSize);
                    }
                }
            }
        }

        List<PositionedStack> ingredients = recipe.getIngredientStacks(recipeIndex);
        if (ingredients != null) {
            for (PositionedStack ps : ingredients) {
                if (ps == null) continue;
                result.add(new ItemOverlayState(ps, tryAllocate(available, ps.item, ps.items)));
            }
        }
        return result;
    }

    /**
     * 逐槽判断材料是否可用并分配（模拟 NEI 默认 presenceOverlay 的「逐槽分配」语义）。
     * <p>
     * 优先按 NEI 当前渲染物（{@code preferred}，用户所见配方图标）的 item+meta 匹配，需求量取该候选
     * stackSize（NEI 配方槽 stackSize 即配方所需数量）；可用量不足则不消耗、判缺少。preferred 为空或
     * 不可用时回退到候选变体（{@code candidates}）的 item+meta 精确匹配。匹配命中的槽位会从
     * {@code available} 中扣除所需数量——只有 1 个哈密瓜时，3 个各需 1 个的槽位仅第 1 个打勾，
     * 不会每个槽位都显示在场。该方法会修改 {@code available}（模拟分配消耗）。
     *
     * @param available  可用物池（item+meta 聚合，会被消耗）
     * @param preferred  NEI 当前渲染的候选（可为 null）
     * @param candidates 配方槽全部候选变体（可为 null）
     */
    private static boolean tryAllocate(Map<ItemStackKey, Long> available, ItemStack preferred, ItemStack[] candidates) {
        if (preferred != null && preferred.getItem() != null) {
            ItemStackKey target = new ItemStackKey(preferred);
            long required = Math.max(1, preferred.stackSize);
            if (amountBySame(available, target) >= required) {
                consumeBySame(available, target, required);
                return true;
            }
        }
        if (candidates != null) {
            for (ItemStack alt : candidates) {
                if (alt == null || alt.getItem() == null) continue;
                ItemStackKey target = new ItemStackKey(alt);
                long required = Math.max(1, alt.stackSize);
                if (amountBySame(available, target) >= required) {
                    consumeBySame(available, target, required);
                    return true;
                }
            }
        }
        return false;
    }

    /** 按 item+meta（isSame，忽略 NBT）合并可用物数量，NBT 不同的同 meta 条目归并到一组 */
    private static void mergeBySame(Map<ItemStackKey, Long> available, ItemStackKey key, long amount) {
        if (amount <= 0 || key == null || key.isEmpty()) return;
        for (Map.Entry<ItemStackKey, Long> e : available.entrySet()) {
            if (e.getKey()
                .isSame(key)) {
                e.setValue(e.getValue() + amount);
                return;
            }
        }
        available.put(key, amount);
    }

    /** 按 item+meta（isSame，忽略 NBT）查询可用物数量，累加 NBT 不同的同 meta 条目 */
    private static long amountBySame(Map<ItemStackKey, Long> available, ItemStackKey key) {
        long total = 0L;
        for (Map.Entry<ItemStackKey, Long> e : available.entrySet()) {
            if (e.getKey()
                .isSame(key)) {
                total += e.getValue();
            }
        }
        return total;
    }

    /** 按 item+meta（isSame，忽略 NBT）消耗可用物数量（模拟 presenceOverlay 的逐槽分配） */
    private static void consumeBySame(Map<ItemStackKey, Long> available, ItemStackKey key, long amount) {
        long remaining = amount;
        for (Map.Entry<ItemStackKey, Long> e : available.entrySet()) {
            if (remaining <= 0) return;
            if (e.getKey()
                .isSame(key)) {
                long take = Math.min(e.getValue(), remaining);
                e.setValue(e.getValue() - take);
                remaining -= take;
            }
        }
    }

    /**
     * 计算转移方案并发送 {@link RecipeFillC2SPacket}（补全按钮路径，对齐源项目 TransferHelper）。
     *
     * @return 成功填写的配方组数
     */
    private int doTransfer(GuiContainer gui, IRecipeHandler handler, int recipeIndex, boolean maxTransfer) {
        if (gui == null || !(gui.inventorySlots instanceof DimensionsCraftMenu)) {
            return 0;
        }
        if (gui.mc == null || gui.mc.thePlayer == null) {
            return 0;
        }

        final PlanResult planResult = computePlans(gui, handler, recipeIndex);

        final long transferMultiplier = planResult.hasMissing ? 1
            : getTransferMultiplier(planResult.plans, planResult.pool, maxTransfer);

        final List<IStackKey<?>> outKeys = new ArrayList<>(planResult.plans.length);
        final List<Long> outAmts = new ArrayList<>(planResult.plans.length);
        for (TransferPlan plan : planResult.plans) {
            outKeys.add(plan.key);
            outAmts.add(plan.required * transferMultiplier);
        }

        BDPackets.INSTANCE.sendToServer(new RecipeFillC2SPacket(outKeys, outAmts));

        return (int) Math.max(1, transferMultiplier);
    }

    /**
     * 服务端自动合成（NEI Shift+C / 合成树）。
     * <p>
     * 与 {@link #doTransfer}（只填充、不取成品）不同：本方法发送"单次配方量"的键/数量
     * 和 NEI 传入的目标合成次数 {@code multiplier}，服务端在
     * {@link DimensionsCraftMenu#autoCraft} 中逐次执行"填充→取走结果→产出"，
     * 使 10 铁锭 = 5 次锻造锤合成 = 5 铁板（BUGFIX_RECORD #103）。
     * 任一必需材料缺失则不发送（NEI 绿点已按材料在场过滤，正常不会走到）。
     */
    private void sendAutoCraft(GuiContainer gui, IRecipeHandler handler, int recipeIndex, int multiplier) {
        if (gui == null || !(gui.inventorySlots instanceof DimensionsCraftMenu)) {
            return;
        }
        if (gui.mc == null || gui.mc.thePlayer == null) {
            return;
        }

        final PlanResult planResult = computePlans(gui, handler, recipeIndex);
        if (planResult.hasMissing) {
            return;
        }

        final List<IStackKey<?>> outKeys = new ArrayList<>(planResult.plans.length);
        final List<Long> outAmts = new ArrayList<>(planResult.plans.length);
        for (TransferPlan plan : planResult.plans) {
            outKeys.add(plan.key);
            outAmts.add(plan.required);
        }

        BDPackets.INSTANCE.sendToServer(new AutoCraftC2SPacket(outKeys, outAmts, multiplier));
    }

    /**
     * 计算配方补全方案（补全与自动合成共用的计划构建逻辑）。
     * <p>
     * 构建可用物池（工艺槽现有物 + 维度网络存储 + 玩家背包）→ 按槽位映射 NEI 配方 →
     * 为每个有配方的槽位选出最优可用物（{@link #findBestAvailable}，item+meta 精准识别）。
     * 任一被配方使用的槽位找不到可用物时置 hasMissing。
     */
    private PlanResult computePlans(GuiContainer gui, IRecipeHandler handler, int recipeIndex) {
        final DimensionsCraftMenu menu = (DimensionsCraftMenu) gui.inventorySlots;

        final Map<Item, List<Avail>> pool = new HashMap<>();

        // 1. 工艺槽现有物
        for (int i = menu.craftSlotStartIndex; i < menu.craftSlotEndIndex; i++) {
            Slot slot = menu.getSlot(i);
            if (slot.getHasStack()) {
                ItemStack s = slot.getStack();
                if (s != null && s.getItem() != null && s.stackSize > 0) {
                    addAvail(pool, new ItemStackKey(s), s.stackSize);
                }
            }
        }

        // 2. 维度网络存储
        if (menu.storage != null) {
            for (KeyAmount ka : menu.storage.getStorage()) {
                if (ka == null || ka.isEmpty()) continue;
                if (ka.key() instanceof ItemStackKey) {
                    addAvail(pool, (ItemStackKey) ka.key(), ka.amount());
                }
            }
        }

        // 3. 玩家背包
        ItemStack[] main = gui.mc.thePlayer.inventory.mainInventory;
        if (main != null) {
            for (ItemStack s : main) {
                if (s != null && s.getItem() != null && s.stackSize > 0) {
                    addAvail(pool, new ItemStackKey(s), s.stackSize);
                }
            }
        }

        final List<PositionedStack> ingredients = handler.getIngredientStacks(recipeIndex);
        final TransferPlan[] plans = new TransferPlan[9];
        Arrays.fill(plans, TransferPlan.empty());
        boolean hasMissing = false;

        if (ingredients != null) {
            for (PositionedStack ps : ingredients) {
                if (ps == null) continue;
                int craftIndex = mapToCraftIndex(ps);
                if (craftIndex < 0 || craftIndex >= plans.length) continue;

                final List<ItemStack> candidates = new ArrayList<>();
                if (ps.items != null) {
                    for (ItemStack s : ps.items) {
                        if (s != null && s.getItem() != null && s.stackSize > 0) {
                            candidates.add(s);
                        }
                    }
                }
                if (candidates.isEmpty()) continue;

                final long required = requiredCountFor(candidates);
                // 传入 NEI 当前渲染的候选（ps.item，即用户所见配方图标），确保 GT 工具等多 meta 候选
                // 优先填充配方实际需要的变体（见 findBestAvailable）
                final AvailableGroup selected = findBestAvailable(ps.item, candidates, pool, required);
                if (selected != null) {
                    consume(pool.get(selected.key.getSource()), selected.key, required);
                    plans[craftIndex] = new TransferPlan(selected.key, required);
                } else {
                    hasMissing = true;
                }
                // 诊断日志：确认补全选中了哪个候选变体（GT 工具多 meta / NBT 选错排查）
                BeyondDimensions.LOGGER.info(
                    "[BD-NEI] slot {} required={} rendered={} candidates=[{}] selected={}",
                    craftIndex,
                    required,
                    describeStack(ps.item),
                    describeStacks(candidates),
                    selected == null ? "null" : describeStack(selected.key.getReadOnlyStack()));
            }
        }

        return new PlanResult(plans, pool, hasMissing);
    }

    /** 将 NEI 配方网格坐标映射为合成板槽位下标（行优先，0..8），越界返回 -1。 */
    private static int mapToCraftIndex(PositionedStack ps) {
        int col = (ps.relx - GRID_ORIGIN_X) / GRID_CELL_SIZE;
        int row = (ps.rely - GRID_ORIGIN_Y) / GRID_CELL_SIZE;
        if (col < 0 || col > 2 || row < 0 || row > 2) {
            return -1;
        }
        return row * 3 + col;
    }

    /** 该槽位需求数量：默认取候选堆叠中最大的 count（典型为 1）。 */
    private static long requiredCountFor(List<ItemStack> candidates) {
        int max = 0;
        for (ItemStack s : candidates) {
            if (s != null) max = Math.max(max, s.stackSize);
        }
        return Math.max(1, max);
    }

    /**
     * 计算某合成槽位的最优可用物。
     * <p>
     * 精准识别：按 item+meta（{@link ItemStackKey#isSame}，忽略 NBT）匹配候选变体，并优先取
     * NEI 当前渲染物（{@code preferred}）。1.7.10 GT5 中不同工具（研钵=24/软锤=14/小刀=34）
     * 是同一 Item（gt.metatool.01）的不同 meta，工具的材料、耐久等次要属性存于 NBT（GT.ToolStats），
     * meta 才标识工具类型，因此必须按 meta 精确匹配、忽略 NBT 差异（网络研钵与 NEI 候选研钵
     * 材料/耐久不同仍视为同一工具）。
     * <p>
     * 不再退回按 Item 通配聚合（源项目 1.20.1 的 findBestAvailable 按 Item 聚合，但那是 GT 工具
     * 已是不同 Item 的环境；1.7.10 GT5 按 Item 聚合会把软锤/小刀误当成研钵，哈密瓜等不同 meta
     * 变体也会被错误累加而误判数量足够）。无候选变体的精确 meta 可用即视为缺少材料。
     * 匹配命中的组始终携带网络存储的真实 key，保证服务端按 key 提取时能精确命中网络中的同类型物品。
     *
     * @param preferred  NEI 当前渲染的候选物品（用户所见配方图标），优先精确匹配它
     * @param candidates 配方槽全部候选变体
     */
    private static AvailableGroup findBestAvailable(@Nullable ItemStack preferred, List<ItemStack> candidates,
        Map<Item, List<Avail>> pool, long required) {
        // 按 item+meta 精确匹配候选变体
        final ArrayList<AvailableGroup> exactGroups = new ArrayList<>();
        for (ItemStack alt : candidates) {
            if (alt == null || alt.getItem() == null) continue;
            final ItemStackKey target = new ItemStackKey(alt);
            final List<Avail> list = pool.get(target.getSource());
            if (list == null || list.isEmpty()) continue;

            // 按 item+meta（isSame）匹配：GT5 工具材料/耐久等存于 NBT（GT.ToolStats）、meta 才是工具类型
            // 标识（研钵=24、软锤=14），必须忽略 NBT 差异，否则网络研钵与 NEI 候选研钵因材料/耐久不同而
            // 匹配失败、落回按 Item 聚合误选软锤；普通物品同 meta 即视为可填（对齐源项目按 Item 聚合的
            // 宽松度，且精确到 meta，能正确区分同 Item 不同 meta 的变体）。
            long remaining = 0;
            ItemStackKey matchedKey = null;
            for (Avail avail : list) {
                if (avail.remain <= 0) continue;
                if (avail.key.isSame(target)) {
                    remaining += avail.remain;
                    if (matchedKey == null) matchedKey = avail.key;
                }
            }
            if (remaining >= required && matchedKey != null) {
                // 组的 key 用网络存储的真实 key（而非候选 key），服务端按 key 提取时可精确命中网络物品
                addAvailable(exactGroups, matchedKey, remaining);
            }
        }
        if (!exactGroups.isEmpty()) {
            // 优先取 NEI 当前渲染物（用户所见）的匹配组
            if (preferred != null && preferred.getItem() != null) {
                final ItemStackKey preferredKey = new ItemStackKey(preferred);
                for (AvailableGroup group : exactGroups) {
                    if (group.key.isSame(preferredKey)) {
                        return group;
                    }
                }
            }
            // 否则选剩余最多的精确匹配组
            AvailableGroup best = null;
            for (AvailableGroup group : exactGroups) {
                if (best == null || group.remaining > best.remaining) {
                    best = group;
                }
            }
            return best;
        }

        // 阶段二（已删除）：原逻辑在无精确匹配时按 Item 聚合可用物（pool.get(item)）——
        // 在 1.7.10 GT5 中会把同一 Item 不同 meta 的工具族混为一种（软锤/小刀被当成研钵），
        // 哈密瓜等不同 meta 变体也会被错误累加而误判数量足够。精准识别要求 item+meta 严格匹配，
        // 无任何候选变体的精确 meta 即视为缺少材料（对齐 AE2 isSameItem 的 item+meta 语义）。
        return null;
    }

    /** 诊断日志用：描述候选列表（item@meta + 是否带 NBT）。 */
    private static String describeStacks(List<ItemStack> stacks) {
        if (stacks == null || stacks.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ItemStack s : stacks) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(describeStack(s));
        }
        return sb.toString();
    }

    /** 诊断日志用：描述单个物品（item@meta + 是否带 NBT）。 */
    private static String describeStack(@Nullable ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return "null";
        }
        return RegistryUtil.getItemId(stack.getItem()) + "@"
            + stack.getItemDamage()
            + (stack.getTagCompound() != null ? "(nbt)" : "");
    }

    private static void addAvailable(List<AvailableGroup> groups, ItemStackKey key, long remaining) {
        for (AvailableGroup group : groups) {
            if (group.key.isSameTypeSameComponents(key)) {
                group.remaining += remaining;
                return;
            }
        }
        groups.add(new AvailableGroup(key, remaining));
    }

    private static long getTransferMultiplier(TransferPlan[] plans, Map<Item, List<Avail>> pool, boolean maxTransfer) {
        if (!maxTransfer) return 1;

        final ArrayList<RequiredGroup> requiredGroups = new ArrayList<>();
        long multiplier = Long.MAX_VALUE;
        boolean hasMaterial = false;

        for (TransferPlan plan : plans) {
            if (plan.isEmpty()) continue;

            hasMaterial = true;
            addRequired(requiredGroups, plan.itemKey, plan.required);
            long maxBySlot = Math.max(1, plan.itemKey.getVanillaMaxStackSize() / plan.required);
            multiplier = Math.min(multiplier, maxBySlot);
        }

        if (!hasMaterial) return 1;

        for (RequiredGroup group : requiredGroups) {
            long available = getAvailable(pool, group.key);
            multiplier = Math.min(multiplier, available / group.required);
        }

        return Math.max(1, multiplier == Long.MAX_VALUE ? 1 : multiplier);
    }

    private static void addRequired(List<RequiredGroup> groups, ItemStackKey key, long required) {
        for (RequiredGroup group : groups) {
            if (group.key.isSameTypeSameComponents(key)) {
                group.required += required;
                return;
            }
        }
        groups.add(new RequiredGroup(key, required));
    }

    private static long getAvailable(Map<Item, List<Avail>> pool, ItemStackKey key) {
        long available = 0;
        List<Avail> entries = pool.get(key.getSource());
        if (entries == null) return 0;

        for (Avail avail : entries) {
            if (avail.key.isSameTypeSameComponents(key)) {
                available += avail.amount;
            }
        }
        return available;
    }

    private static void addAvail(Map<Item, List<Avail>> pool, ItemStackKey key, long amount) {
        if (amount <= 0) return;
        pool.computeIfAbsent(key.getSource(), i -> new ArrayList<>())
            .add(new Avail(key, amount));
    }

    private static void consume(List<Avail> entries, ItemStackKey key, long amount) {
        if (entries == null || amount <= 0) return;

        long remaining = amount;
        for (Avail avail : entries) {
            if (remaining <= 0) return;
            if (!avail.key.isSameTypeSameComponents(key)) continue;

            long take = Math.min(avail.remain, remaining);
            if (take > 0) {
                avail.remain -= take;
                remaining -= take;
            }
        }
    }

    /** 配方补全方案计算结果：槽位方案 + 可用物池 + 是否有缺失材料。 */
    private static final class PlanResult {

        final TransferPlan[] plans;
        final Map<Item, List<Avail>> pool;
        final boolean hasMissing;

        PlanResult(TransferPlan[] plans, Map<Item, List<Avail>> pool, boolean hasMissing) {
            this.plans = plans;
            this.pool = pool;
            this.hasMissing = hasMissing;
        }
    }

    /** 单个合成槽位的转移方案。 */
    private static final class TransferPlan {

        final IStackKey<?> key;
        final ItemStackKey itemKey;
        final long required;

        TransferPlan(ItemStackKey key, long required) {
            this.key = key;
            this.itemKey = key;
            this.required = required;
        }

        static TransferPlan empty() {
            return new TransferPlan(EmptyStackKey.INSTANCE, null, 0);
        }

        private TransferPlan(IStackKey<?> key, ItemStackKey itemKey, long required) {
            this.key = key;
            this.itemKey = itemKey;
            this.required = required;
        }

        boolean isEmpty() {
            return itemKey == null || key.isEmpty() || required <= 0;
        }
    }

    private static final class AvailableGroup {

        final ItemStackKey key;
        long remaining;

        AvailableGroup(ItemStackKey key, long remaining) {
            this.key = key;
            this.remaining = remaining;
        }
    }

    private static final class RequiredGroup {

        final ItemStackKey key;
        long required;

        RequiredGroup(ItemStackKey key, long required) {
            this.key = key;
            this.required = required;
        }
    }

    /** 可用条目：仅 Key + 可用数量；不创建/复制 ItemStack。 */
    private static final class Avail {

        final ItemStackKey key;
        final long amount;
        long remain;

        Avail(ItemStackKey key, long amount) {
            this.key = key;
            this.amount = amount;
            this.remain = amount;
        }
    }
}
