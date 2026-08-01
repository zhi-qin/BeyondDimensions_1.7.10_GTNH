package com.wintercogs.beyonddimensions.client.gui;

import net.minecraft.entity.player.InventoryPlayer;

import com.wintercogs.beyonddimensions.common.menu.DimensionsCraftMenuTerminal;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 终端合成界面 GUI（1.7.10 移植版）。
 * <p>
 * 与 {@link GuiDimensionsCraft} 的区别：不显示工艺槽切换按钮（由终端物品触发）。
 * 渲染逻辑与合成界面一致。
 */
@SideOnly(Side.CLIENT)
public class GuiDimensionsCraftTerminal extends GuiDimensionsCraft {

    public GuiDimensionsCraftTerminal(InventoryPlayer inventory) {
        super(new DimensionsCraftMenuTerminal(inventory));
        this.xSize = 194;
        this.ySize = rebuildImageHeight();
    }

    public GuiDimensionsCraftTerminal(InventoryPlayer inventory, DimensionsCraftMenuTerminal menu) {
        super(menu);
        this.xSize = 194;
        this.ySize = rebuildImageHeight();
    }

    /**
     * 终端合成界面不显示"工艺切换"按钮（对齐源项目 DimensionsTerminalCraftGUI 的空覆写）：
     * 终端界面自身即合成界面，无需提供切回普通网络界面的入口。
     */
    @Override
    protected void addCraftButton() {
        // 清空：不添加工艺切换按钮
    }
}
