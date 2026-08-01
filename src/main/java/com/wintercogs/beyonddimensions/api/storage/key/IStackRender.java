package com.wintercogs.beyonddimensions.api.storage.key;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 用于 StackKey 的渲染器，实现一般可以使用单例模式
 * <p>
 * 1.7.10 移植版：
 * GuiGraphics → 不存在，使用 x/y 坐标直接绘制；
 * Font → net.minecraft.client.gui.FontRenderer；
 * Component → net.minecraft.util.IChatComponent（通过 getDisplayName/getTooltipLines 返回 String/List&lt;String&gt;）；
 * Player → net.minecraft.entity.player.EntityPlayer；
 * TooltipFlag → boolean advanced；
 * TooltipComponent → 不存在，省略；
 * @OnlyIn(Dist.CLIENT) → @SideOnly(Side.CLIENT)
 */
public interface IStackRender {

    /**
     * UI渲染，即绘制当前资源的图标
     */
    @SideOnly(Side.CLIENT)
    void render(IStackKey<?> key, int x, int y);

    /**
     * 将数量绘制到屏幕上
     */
    void renderAmount(long amount, int x, int y);

    /**
     * 对当前存储数量进行格式化
     */
    String getCountText(long count);

    /**
     * 获取资源名称
     */
    String getDisplayName(IStackKey<?> key);

    /**
     * 获取资源的工具提示行
     *
     * @param key      堆叠 key
     * @param amount   当前数量
     * @param player   玩家（可为 null）
     * @param advanced 是否高级提示模式（对应 1.20.1 的 TooltipFlag）
     * @return 工具提示文本行列表
     */
    List<String> getTooltipLines(IStackKey<?> key, long amount, EntityPlayer player, boolean advanced);

    /**
     * 绘制工具提示，必须标记为仅客户端
     * <p>
     * 1.7.10 中 GuiScreen#drawHoveringText 为 protected，需要 GuiScreen 子类或具体 GUI 实现中调用。
     */
    @SideOnly(Side.CLIENT)
    void renderTooltip(IStackKey<?> key, long amount, int mouseX, int mouseY);
}
