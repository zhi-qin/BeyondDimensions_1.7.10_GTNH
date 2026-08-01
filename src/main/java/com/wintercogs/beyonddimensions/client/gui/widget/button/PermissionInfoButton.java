package com.wintercogs.beyonddimensions.client.gui.widget.button;

import java.util.UUID;

import net.minecraft.client.gui.GuiButton;

import com.wintercogs.beyonddimensions.api.dimensionnet.PlayerPermissionInfo;
import com.wintercogs.beyonddimensions.client.gui.widget.shared.OnPress;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 权限信息按钮（1.7.10 移植版）。
 * <p>
 * 移植自 1.20.1 的 {@code PermissionInfoButton}（继承 {@code Button}），
 * 1.7.10 改为继承 {@link GuiButton}。按钮本身不绘制图标，仅作为携带玩家
 * UUID 与权限信息的可点击行使用，由宿主 GUI 在 {@code actionPerformed} 中处理。
 */
@SideOnly(Side.CLIENT)
public class PermissionInfoButton extends GuiButton {

    private UUID playerId;
    private PlayerPermissionInfo permissionInfo;

    public PermissionInfoButton(int id, int x, int y, int width, int height, UUID playerId,
        PlayerPermissionInfo playerPermissionInfo, String message, OnPress onPress) {
        super(id, x, y, width, height, message == null ? "" : message);
        this.playerId = playerId;
        this.permissionInfo = playerPermissionInfo;
        this.onPress = onPress;
    }

    @Override
    public boolean mousePressed(net.minecraft.client.Minecraft mc, int mouseX, int mouseY) {
        if (super.mousePressed(mc, mouseX, mouseY)) {
            if (this.onPress != null) {
                this.onPress.onPress(this);
            }
            return true;
        }
        return false;
    }

    public PlayerPermissionInfo getPermissionInfo() {
        return this.permissionInfo;
    }

    public void setPermissionInfo(PlayerPermissionInfo permissionInfo) {
        this.permissionInfo = permissionInfo;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public void setPlayerId(UUID playerId) {
        this.playerId = playerId;
    }

    private final OnPress onPress;
}
