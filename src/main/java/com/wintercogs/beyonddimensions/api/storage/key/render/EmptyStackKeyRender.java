package com.wintercogs.beyonddimensions.api.storage.key.render;

import java.util.Collections;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.IStackRender;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public final class EmptyStackKeyRender implements IStackRender {

    public static final EmptyStackKeyRender INSTANCE = new EmptyStackKeyRender();

    private EmptyStackKeyRender() {}

    @Override
    @SideOnly(Side.CLIENT)
    public void render(IStackKey<?> key, int x, int y) {}

    @Override
    public void renderAmount(long amount, int x, int y) {}

    @Override
    public String getCountText(long count) {
        return "";
    }

    @Override
    public String getDisplayName(IStackKey<?> key) {
        return "";
    }

    @Override
    public List<String> getTooltipLines(IStackKey<?> key, long amount, EntityPlayer player, boolean advanced) {
        return Collections.emptyList();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderTooltip(IStackKey<?> key, long amount, int mouseX, int mouseY) {}
}
