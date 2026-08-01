package com.wintercogs.beyonddimensions;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

import com.wintercogs.beyonddimensions.api.ButtonState;
import com.wintercogs.beyonddimensions.config.CommonConfigRuntime;
import com.wintercogs.beyonddimensions.config.ServerConfigRuntime;

public class Config {

    public static Configuration CONFIG;
    public static Configuration WORLD_CONFIG;

    public static void init(File configFile) {
        CONFIG = new Configuration(configFile);
        loadCommonConfig();
        if (CONFIG.hasChanged()) {
            CONFIG.save();
        }
    }

    public static void initWorldConfig(File worldConfigFile) {
        WORLD_CONFIG = new Configuration(worldConfigFile);
        loadServerConfig();
        if (WORLD_CONFIG.hasChanged()) {
            WORLD_CONFIG.save();
        }
    }

    public static void loadCommonConfig() {
        String category = "ui";
        CommonConfigRuntime.uiSortButton = readEnum(
            "ui_sort_button",
            ButtonState.SORT_NAME,
            "存储UI搜索按钮值 (除非你知道你在做什么，否则不要手动修改)");
        CommonConfigRuntime.uiSecondSortButton = readEnum(
            "ui_second_sort_button",
            ButtonState.SORT_INSERTED_TIME,
            "存储UI搜索按钮值 (除非你知道你在做什么，否则不要手动修改)");
        CommonConfigRuntime.uiReverseButton = readEnum(
            "ui_reverse_button",
            ButtonState.DISABLED,
            "存储UI倒序按钮值 (除非你知道你在做什么，否则不要手动修改)");
        CommonConfigRuntime.uiSearchButton = readEnum(
            "ui_search_button",
            ButtonState.DISABLED,
            "存储UI搜索按钮值 (除非你知道你在做什么，否则不要手动修改)");
        CommonConfigRuntime.uiCraftButton = readEnum("ui_craft_button", ButtonState.DISABLED, "决定打开菜单时是否显示合成槽");
        CommonConfigRuntime.uiCraftReturnButton = readEnum(
            "ui_craft_return_button",
            ButtonState.DISABLED,
            "决定工艺菜单关闭时，物品优先转移的方向；启用则优先向存储，关闭则优先向背包");
        CommonConfigRuntime.uiPageNum = CONFIG
            .getInt("ui_page_num", category, 5, 2, 99, "存储UI当前显示的总页数 (除非你知道你在做什么，否则不要手动修改)");
        CommonConfigRuntime.uiSearch = CONFIG.getString("ui_search", category, "", "存储UI搜索框内容 (除非你知道你在做什么，否则不要手动修改)");
        CommonConfigRuntime.searchTextWithJEIEMI = CONFIG
            .getBoolean("search_text_with_jei_emi", category, true, "是否与NEI同步搜索");
        CommonConfigRuntime.emiAllowNetworkStorageInfo = CONFIG
            .getBoolean("emi_allow_network_storage_info", category, false, "是否允许NEI获取维度网络内全部物品信息");

        category = "interface";
        CommonConfigRuntime.interfaceCanReceiveResource = CONFIG
            .getBoolean("interface_can_receive_resource", category, true, "是否允许网络接口将资源送入网络");
        CommonConfigRuntime.interfaceCanOutputResource = CONFIG
            .getBoolean("interface_can_output_resource", category, true, "是否允许网络接口从网络提取标记的资源");
        CommonConfigRuntime.interfaceCanPopResource = CONFIG
            .getBoolean("interface_can_pop_resource", category, true, "是否允许网络接口将内容物弹出到附近容器");
        CommonConfigRuntime.interfaceUsableCapacity = CONFIG
            .getInt("interface_usable_capacity", category, 27, 1, 27, "网络接口有多少个槽位实际可用？");

        category = "energy";
        ServerConfigRuntime.energyPathwayDefaultActivePull = CONFIG.getBoolean(
            "energy_pathway_default_active_pull",
            category,
            false,
            "维度能量通道放置时默认是否启用主动抽取（默认不抽取；每方块可用 GUI 按钮单独覆盖并持久化）");
    }

    /**
     * 安全读取枚举型配置项：玩家手改配置文件为非法字符串时回退默认值，
     * 避免 {@code ButtonState.valueOf} 抛 IllegalArgumentException 导致启动崩溃
     * （源项目 1.20.1 的 ForgeConfigSpec 自带解析保护，1.7.10 Configuration 无）。
     */
    private static <E extends Enum<E>> E readEnum(String key, E defaultValue, String comment) {
        String raw = CONFIG.getString(key, "ui", defaultValue.name(), comment);
        try {
            return (E) Enum.valueOf(defaultValue.getDeclaringClass(), raw);
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return defaultValue;
        }
    }

    public static void loadServerConfig() {
        String category = "server";
        ServerConfigRuntime.fragmentTransferTime = WORLD_CONFIG
            .getInt("fragmentTransferTime", category, 3600, 1, Integer.MAX_VALUE, "碎片转化间隔") * 20L;
        ServerConfigRuntime.crystalGenerateTime = WORLD_CONFIG
            .getInt("crystalGenerateTime", category, 600, 0, Integer.MAX_VALUE, "结晶生成间隔（0代表不生成）");
    }

    /**
     * 写入并保存公共 UI 配置项。
     * 对齐源项目 ForgeConfigSpec 的 {@code Config.INSTANCE.commonConfig.X.set(...)} 即时持久化语义：
     * 按钮回调修改运行时值后写回配置文件，使排序/合成栏等 UI 状态跨重启保留。
     */
    public static void setUiString(String key, String value, String defaultValue, String comment) {
        if (CONFIG == null) {
            return;
        }
        CONFIG.get("ui", key, defaultValue, comment)
            .set(value);
        if (CONFIG.hasChanged()) {
            CONFIG.save();
        }
    }

    /**
     * 写入并保存布尔型 UI 配置项（Shift+Z 切换「与NEI同步搜索」时使用，
     * 对齐源项目 ForgeConfigSpec 的即时持久化语义）。
     */
    public static void setUiBoolean(String key, boolean value) {
        if (CONFIG == null) {
            return;
        }
        CONFIG.get("ui", key, true, "是否与NEI同步搜索")
            .set(value);
        if (CONFIG.hasChanged()) {
            CONFIG.save();
        }
    }

    public static void save() {
        if (CONFIG != null && CONFIG.hasChanged()) {
            CONFIG.save();
        }
        if (WORLD_CONFIG != null && WORLD_CONFIG.hasChanged()) {
            WORLD_CONFIG.save();
        }
    }
}
