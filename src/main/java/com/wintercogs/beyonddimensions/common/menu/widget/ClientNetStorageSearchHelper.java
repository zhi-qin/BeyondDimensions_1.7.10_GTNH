package com.wintercogs.beyonddimensions.common.menu.widget;

import java.util.*;
import java.util.stream.Collectors;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.util.TinyPinyinUtils;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 专用于ClientNetStorage，内部集成搜索用的方法和字段（1.7.10 移植版）。
 * <p>
 * 1.7.10 适配：Component.getString() → IStackRender 直接返回 String；
 * TooltipFlag → boolean advanced；Minecraft.getInstance() → Minecraft.getMinecraft()；
 * Tag 搜索 → 存根（1.7.10 无 Tag 系统）；JEI Characters 拼音匹配 → 跳过。
 */
@SideOnly(Side.CLIENT)
public class ClientNetStorageSearchHelper {

    private String originalSearchText = "";
    private final List<String> searchTexts = new ArrayList<>();

    /**
     * 当前搜索条件下的最终匹配缓存。搜索文本变化时必须清空。
     */
    private final Map<IStackKey<?>, Boolean> matchCache = new HashMap<>();

    /**
     * 以下缓存不依赖当前搜索条件，因此无需在搜索文本变化时清空。
     */
    private final Map<IStackKey<?>, String> nameCache = new HashMap<>();
    private final Map<IStackKey<?>, String> modidCache = new HashMap<>();
    private final Map<IStackKey<?>, List<String>> tooltipCache = new HashMap<>();

    public void loadTexts(String text) {
        Objects.requireNonNull(text, "searchText cannot be null");
        if (this.originalSearchText.equals(text)) return;

        this.originalSearchText = text;
        this.searchTexts.clear();
        this.matchCache.clear();

        if (text.isEmpty()) {
            return;
        }

        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean escaping = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (escaping) {
                current.append(c);
                escaping = false;
                continue;
            }

            if (c == '\\') {
                escaping = true;
                continue;
            }

            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }

            if (Character.isWhitespace(c) && !inQuotes) {
                if (current.length() > 0) {
                    this.searchTexts.add(
                        current.toString()
                            .toLowerCase(Locale.ENGLISH));
                    current.setLength(0);
                }
                continue;
            }

            current.append(c);
        }

        if (escaping) {
            current.append('\\');
        }

        if (current.length() > 0) {
            this.searchTexts.add(
                current.toString()
                    .toLowerCase(Locale.ENGLISH));
        }
    }

    /**
     * 可用于对外搜索匹配的接口
     */
    public boolean matches(IStackKey<?> key) {
        Objects.requireNonNull(key, "key cannot be null");
        if (originalSearchText.isEmpty()) return true;

        Boolean cached = this.matchCache.get(key);
        if (cached != null) {
            return cached;
        }

        boolean result = true;

        for (String searchText : this.searchTexts) {
            if (!matchesSingleSearchText(new KeyAmount(key, 1), searchText)) {
                result = false;
                break;
            }
        }

        this.matchCache.put(key, result);
        return result;
    }

    /**
     * 单个 searchText：先按 | 拆分，多个部分按或合并；每个部分可带 - 前缀表示取反；
     * 再根据 @ / $ / 默认 选择匹配范围。
     */
    private boolean matchesSingleSearchText(KeyAmount keyAmount, String searchText) {
        String[] orParts = searchText.split("\\|", -1);

        for (String part : orParts) {
            if (part.isEmpty()) {
                continue;
            }

            if (matchesSingleOrPart(keyAmount, part)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 单个 or 分支的匹配。先处理 -，再处理 @/$/默认。
     */
    private boolean matchesSingleOrPart(KeyAmount keyAmount, String part) {
        boolean negated = false;
        String actual = part;

        if (!actual.isEmpty() && actual.charAt(0) == '-') {
            negated = true;
            actual = actual.substring(1);
        }

        if (actual.isEmpty()) {
            return true;
        }

        boolean matched;
        char prefix = actual.charAt(0);
        String needle;

        switch (prefix) {
            case '@':
                needle = actual.substring(1);
                matched = needle.isEmpty() || matchesModId(keyAmount, needle);
                break;
            case '$':
                needle = actual.substring(1);
                matched = needle.isEmpty() || matchesTooltip(keyAmount, needle);
                break;
            default:
                matched = matchesName(keyAmount, actual);
                break;
        }

        return negated ? !matched : matched;
    }

    private boolean matchesModId(KeyAmount keyAmount, String needle) {
        return checkTextMatches(getModId(keyAmount.key()), needle);
    }

    private boolean matchesTooltip(KeyAmount keyAmount, String needle) {
        for (String line : getTooltips(keyAmount)) {
            if (checkTextMatches(line, needle)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesName(KeyAmount keyAmount, String needle) {
        return checkTextMatches(getName(keyAmount.key()), needle);
    }

    /**
     * 检查文本是否匹配名称
     */
    private boolean checkTextMatches(String srcText, String inputText) {
        boolean matchText = srcText.contains(inputText);

        boolean matchPinyin;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || !mc.gameSettings.language.startsWith("zh")) {
            matchPinyin = false;
        } else if (Loader.isModLoaded("jecharacters")) {
            // 1.7.10 的 JEI Characters 拼音匹配 —— 待确认是否有对应 API
            matchPinyin = false;
        } else {
            String allPinyin = TinyPinyinUtils.toPinyin(srcText)
                .toLowerCase(Locale.ENGLISH);
            String firstPinyin = TinyPinyinUtils.toPinyinFirstLetter(srcText)
                .toLowerCase(Locale.ENGLISH);
            matchPinyin = allPinyin.contains(inputText) || firstPinyin.contains(inputText);
        }

        return matchText || matchPinyin;
    }

    private String getName(IStackKey<?> key) {
        return this.nameCache.computeIfAbsent(
            key,
            k -> k.getRender()
                .getDisplayName(k)
                .toLowerCase(Locale.ENGLISH));
    }

    private String getModId(IStackKey<?> key) {
        return this.modidCache.computeIfAbsent(
            key,
            k -> k.getModId()
                .toLowerCase(Locale.ENGLISH));
    }

    private List<String> getTooltips(KeyAmount keyAmount) {
        return this.tooltipCache.computeIfAbsent(keyAmount.key(), k -> {
            Minecraft mc = Minecraft.getMinecraft();
            EntityPlayer player = mc.thePlayer;
            Objects.requireNonNull(player, "cannot run text matches when player is null");

            boolean advanced = mc.gameSettings.advancedItemTooltips;
            List<String> tooltips = k.getRender()
                .getTooltipLines(k, keyAmount.amount(), player, advanced);

            return tooltips.stream()
                .map(line -> line.toLowerCase(Locale.ENGLISH))
                .collect(Collectors.toList());
        });
    }

    /**
     * 当某些 key 的显示名 / tooltip 可能会在运行时变化，可以在合适时机调用它清空缓存
     */
    public void clearDerivedCaches() {
        this.matchCache.clear();
        this.nameCache.clear();
        this.modidCache.clear();
        this.tooltipCache.clear();
    }
}
