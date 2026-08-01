package com.wintercogs.beyonddimensions.api.storage.key.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.IStackRender;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.util.StringFormat;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public final class ItemStackKeyRender implements IStackRender {

    public static final ItemStackKeyRender INSTANCE = new ItemStackKeyRender();

    private static final RenderItem renderItem = new RenderItem();

    private ItemStackKeyRender() {}

    @Override
    @SideOnly(Side.CLIENT)
    public void render(IStackKey<?> key, int x, int y) {
        if (!(key instanceof ItemStackKey)) return;
        ItemStack stack = ((ItemStackKey) key).getRenderStack();
        if (stack == null) return;

        RenderHelper.enableGUIStandardItemLighting();
        renderItem.renderItemAndEffectIntoGUI(
            Minecraft.getMinecraft().fontRenderer,
            Minecraft.getMinecraft()
                .getTextureManager(),
            stack,
            x,
            y);
        renderItem.renderItemOverlayIntoGUI(
            Minecraft.getMinecraft().fontRenderer,
            Minecraft.getMinecraft()
                .getTextureManager(),
            stack,
            x,
            y,
            null);
        RenderHelper.disableStandardItemLighting();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderAmount(long amount, int x, int y) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        String text = getCountText(amount);
        font.drawStringWithShadow(text, x + 19 - 2 - font.getStringWidth(text), y + 6 + 3, 0xFFFFFF);
    }

    @Override
    public String getCountText(long count) {
        // 对齐 1.20.1 源项目 ItemStackKeyRender：StringFormat.formatCount（k/M/G/T/P/E 单位）
        if (count < 0) return "";
        return StringFormat.formatCount(count);
    }

    @Override
    public String getDisplayName(IStackKey<?> key) {
        if (!(key instanceof ItemStackKey)) return "";
        ItemStack stack = ((ItemStackKey) key).getRenderStack();
        if (stack == null) return "";
        try {
            return stack.getDisplayName();
        } catch (Throwable e) {
            // 1.7.10 部分第三方物品的 getDisplayName 实现不健壮（例如 TST ItemCardigan
            // 对无 NBT 的 stack 直接 NPE）。该方法会在网络包处理中被调用（排序索引构建），
            // 异常会导致 FML 判定频道致命错误并断开连接，因此回退到 Item 级非本地化翻译。
            return safeFallbackName(stack);
        }
    }

    /**
     * 第三方物品显示名异常时的安全回退：Item 级 getUnlocalizedName() 不接受 ItemStack，
     * 不会触发按 stack 分派的第三方重写逻辑。
     */
    private static String safeFallbackName(ItemStack stack) {
        try {
            Item item = stack.getItem();
            if (item != null) {
                return StatCollector.translateToLocal(item.getUnlocalizedName());
            }
        } catch (Throwable ignored) {
            // 回退路径自身不再抛出
        }
        return "";
    }

    @Override
    public List<String> getTooltipLines(IStackKey<?> key, long amount, EntityPlayer player, boolean advanced) {
        if (!(key instanceof ItemStackKey)) return Collections.emptyList();
        ItemStack stack = ((ItemStackKey) key).getRenderStack();
        if (stack == null) return Collections.emptyList();
        try {
            // 对齐 1.20.1 源项目：在物品 tooltip 末尾追加"已存储：X个"数量行
            List<String> tooltips = stack.getTooltip(player, advanced);
            tooltips.add(StatCollector.translateToLocalFormatted("istack.beyonddimensions.storage_num.item", amount));
            return tooltips;
        } catch (Throwable e) {
            // 与 getDisplayName 同理：第三方物品的 addInformation 可能抛异常
            // （搜索匹配/悬停提示均走此路径），回退为单行显示名。
            List<String> fallback = new ArrayList<>();
            fallback.add(getDisplayName(key));
            return fallback;
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderTooltip(IStackKey<?> key, long amount, int mouseX, int mouseY) {
        // 1.7.10 GuiScreen#drawHoveringText 为 protected，需要 GuiScreen 子类或反射调用。
        // 在 GUI 阶段实现具体的提示框绘制。
    }
}
