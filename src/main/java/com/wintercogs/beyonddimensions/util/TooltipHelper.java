package com.wintercogs.beyonddimensions.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.util.IChatComponent;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 工具提示缓存管理（1.7.10 适配版）。
 * <p>
 * 1.7.10 没有 {@code @Mod.EventBusSubscriber}，需要手动注册到 FML 事件总线。
 * 注册方式：{@code FMLCommonHandler.instance().bus().register(new TooltipHelper());}
 * <p>
 * 1.7.10 没有 {@code Component}，使用 {@code IChatComponent}。
 * 1.7.10 没有 {@code TooltipFlag}，使用 {@code boolean advanced}。
 */
@SideOnly(Side.CLIENT)
public class TooltipHelper {

    private static final AtomicLong TICK_COUNTER = new AtomicLong(0);
    private static long lastTooltipTick = 0;

    /**
     * 工具提示缓存条目。
     */
    public static class TooltipCacheEntry {

        public final List<IChatComponent> tooltip;
        public final long generatedTick;

        public TooltipCacheEntry(List<IChatComponent> tooltip, long generatedTick) {
            this.tooltip = tooltip;
            this.generatedTick = generatedTick;
        }
    }

    private static final Map<IStackKey<?>, TooltipCacheEntry> TOOLTIP_CACHE = new HashMap<>();

    /**
     * 客户端 Tick 事件处理。
     * 需要在客户端初始化时注册到 FML 事件总线。
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            TICK_COUNTER.incrementAndGet();
        }
    }

    /**
     * 获取当前 tick 计数。
     */
    public static long getCurrentTick() {
        return TICK_COUNTER.get();
    }

    /**
     * 缓存工具提示。
     */
    public static void cacheTooltip(IStackKey<?> key, List<IChatComponent> tooltip) {
        TOOLTIP_CACHE.put(key, new TooltipCacheEntry(tooltip, getCurrentTick()));
        lastTooltipTick = getCurrentTick();
    }

    /**
     * 获取缓存的工具提示。
     *
     * @param key    堆叠键
     * @param maxAge 最大存活 tick 数
     * @return 缓存的工具提示，如果过期或不存在则返回 null
     */
    public static List<IChatComponent> getCachedTooltip(IStackKey<?> key, long maxAge) {
        TooltipCacheEntry entry = TOOLTIP_CACHE.get(key);
        if (entry == null) return null;
        if (getCurrentTick() - entry.generatedTick > maxAge) {
            TOOLTIP_CACHE.remove(key);
            return null;
        }
        return entry.tooltip;
    }

    /**
     * 清除所有缓存。
     */
    public static void clearCache() {
        TOOLTIP_CACHE.clear();
    }

    /**
     * 获取上次生成工具提示的 tick。
     */
    public static long getLastTooltipTick() {
        return lastTooltipTick;
    }
}
