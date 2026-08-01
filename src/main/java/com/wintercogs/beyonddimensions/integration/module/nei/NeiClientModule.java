package com.wintercogs.beyonddimensions.integration.module.nei;

import com.wintercogs.beyonddimensions.client.event.listener.NeiSearchBridge;
import com.wintercogs.beyonddimensions.client.gui.GuiDimensionsCraft;
import com.wintercogs.beyonddimensions.client.gui.GuiDimensionsCraftTerminal;
import com.wintercogs.beyonddimensions.client.gui.GuiDimensionsNet;
import com.wintercogs.beyonddimensions.integration.IIntegrationClientModule;
import com.wintercogs.beyonddimensions.integration.OtherModIds;

import codechicken.nei.LayoutManager;
import codechicken.nei.SearchField;
import codechicken.nei.api.API;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * NEI 客户端联动模块（1.7.10 适配版）。
 * <p>
 * 对应源项目（1.20.1）中的 JEI 客户端插件注册逻辑：
 * - 注册 {@link NeiGuiHandler} 为 NEI GUI 处理器
 * - 通过 {@link API#registerNEIGuiHandler} 让 NEI 识别 BD GUI 的幽灵槽位与拖拽支持
 * <p>
 * 该模块仅在客户端侧生效，由 {@link com.wintercogs.beyonddimensions.integration.IntegrationManager#bootstrapClient}
 * 在 postInit 阶段通过反射实例化。类级 {@code @SideOnly(CLIENT)}：服务端构建时剥离整个类
 * （仅字符串反射引用，无编译期依赖，剥离安全），避免客户端类混入服务端加载路径（审计 M2-3）。
 */
@SideOnly(Side.CLIENT)
public class NeiClientModule implements IIntegrationClientModule {

    private static boolean registered = false;

    @Override
    public String modId() {
        return OtherModIds.NEI;
    }

    @Override
    public void onBootstrapClient(FMLPostInitializationEvent event) {
        if (registered) {
            return;
        }
        registered = true;
        // 注册 NEI GUI 处理器，提供 BD GUI 的幽灵物品拖拽与面板遮挡处理
        API.registerNEIGuiHandler(new NeiGuiHandler());
        // 注册配方补全 overlay handler（对齐源项目 JEI 配方转移体系）：
        // NEI 的「补全」按钮按 GUI 类精确匹配，合成界面与终端需分别注册（ident "crafting"）
        API.registerGuiOverlayHandler(GuiDimensionsCraft.class, new RecipeTransferOverlayHandler(), "crafting");
        API.registerGuiOverlayHandler(GuiDimensionsCraftTerminal.class, new RecipeTransferOverlayHandler(), "crafting");
        // 注册 NEI 合成链精准暴露桥（方案 A）：终端 GUI 每 tick 经此桥同步链条目到非活跃槽位，
        // 修复搜索过滤泄漏到 NEI 收藏夹合成链材料检查的问题
        GuiDimensionsNet.registerNeiExposureBridge(
            (menu, mouseX, mouseY) -> NeiBookmarkExposureHandler.INSTANCE.update(menu, mouseX, mouseY));
        // 注册 NEI 搜索文本双向同步桥（对齐源项目 searchTextWithJEIEMI 的 JEI/EMI 同步，
        // 1.7.10 等价物为 NEI 搜索栏）：BD 搜索框文本变化推送到 NEI 物品面板过滤，
        // 每 tick 从 NEI 搜索栏读回（NEI 侧优先覆盖）。
        GuiDimensionsNet.registerNeiSearchBridge(new NeiSearchBridge() {

            @Override
            public void pushSearchText(String text) {
                SearchField searchField = LayoutManager.searchField;
                if (searchField != null && !text.equals(searchField.text())) {
                    searchField.setText(text);
                }
            }

            @Override
            public String readSearchText() {
                SearchField searchField = LayoutManager.searchField;
                return searchField == null ? null : searchField.text();
            }
        });
    }
}
