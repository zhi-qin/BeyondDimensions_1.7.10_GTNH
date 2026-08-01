package com.wintercogs.beyonddimensions.client.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import com.wintercogs.beyonddimensions.api.dimensionnet.NetControlAction;
import com.wintercogs.beyonddimensions.api.dimensionnet.NetPermissionLevel;
import com.wintercogs.beyonddimensions.api.dimensionnet.PlayerPermissionInfo;
import com.wintercogs.beyonddimensions.api.ids.BDConstants;
import com.wintercogs.beyonddimensions.client.gui.widget.button.PermissionInfoButton;
import com.wintercogs.beyonddimensions.common.init.BDPackets;
import com.wintercogs.beyonddimensions.common.menu.NetControlMenu;
import com.wintercogs.beyonddimensions.network.packet.c2s.NetControlActionPacket;
import com.wintercogs.beyonddimensions.util.GuiRenderHelper;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 网络管理界面 GUI（1.7.10 移植版）。
 * <p>
 * 显示网络成员列表及权限管理按钮。
 * <p>
 * 控件：权限信息按钮列表（PermissionInfoButton）、操作按钮（设置所有者/管理员/移除）。
 * 点击操作按钮发送 NetControlActionPacket 到服务端。
 * 1.7.10 客户端 menu.playerInfo 可能因 PlayerPermissionInfoPacket 未完整实现而为空。
 */
@SideOnly(Side.CLIENT)
public class GuiNetControl extends GuiBase {

    private static final int ID_OWNER_BUTTON = 200;
    private static final int ID_MANAGER_BUTTON = 201;
    private static final int ID_REMOVE_MANAGER_BUTTON = 202;
    private static final int ID_REMOVE_MEMBER_BUTTON = 203;
    private static final int PERMISSION_BUTTON_BASE_ID = 300;
    private static final int MAX_SHOW_PLAYERS = 20;

    private static final ResourceLocation GUI_TEXTURE = new ResourceLocation(
        BDConstants.MODID,
        "textures/gui/net_control.png");
    private static final int TEXTURE_WIDTH = 256;
    private static final int TEXTURE_HEIGHT = 256;

    protected final NetControlMenu menu;

    private GuiButton ownerButton;
    private GuiButton managerButton;
    private GuiButton removeManagerButton;
    private GuiButton removeMemberButton;

    private List<PermissionInfoButton> permissionButtons = new ArrayList<>();
    private UUID selectedPlayerId = null;
    private String selectedPlayerName = "";
    private NetPermissionLevel selectedPlayerLevel = null;

    public GuiNetControl(InventoryPlayer inventory) {
        super(new NetControlMenu(inventory));
        this.menu = (NetControlMenu) this.inventorySlots;
        this.xSize = 256;
        this.ySize = 235;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.guiLeft = (this.width - 256) / 2;
        this.guiTop = (this.height - 235) / 2;
        initWidgets();
    }

    /** 初始化操作按钮 */
    protected void initWidgets() {
        int buttonX = this.guiLeft + 110;
        int buttonWidth = 100;
        int buttonHeight = 20;
        int startY = this.guiTop + 60;
        int step = 25;

        ownerButton = new GuiButton(
            ID_OWNER_BUTTON,
            buttonX,
            startY,
            buttonWidth,
            buttonHeight,
            StatCollector.translateToLocal("menu.button.beyonddimensions.setowner"));
        this.buttonList.add(ownerButton);

        managerButton = new GuiButton(
            ID_MANAGER_BUTTON,
            buttonX,
            startY + step,
            buttonWidth,
            buttonHeight,
            StatCollector.translateToLocal("menu.button.beyonddimensions.setmanager"));
        this.buttonList.add(managerButton);

        removeManagerButton = new GuiButton(
            ID_REMOVE_MANAGER_BUTTON,
            buttonX,
            startY + step * 2,
            buttonWidth,
            buttonHeight,
            StatCollector.translateToLocal("menu.button.beyonddimensions.removemanager"));
        this.buttonList.add(removeManagerButton);

        removeMemberButton = new GuiButton(
            ID_REMOVE_MEMBER_BUTTON,
            buttonX,
            startY + step * 3,
            buttonWidth,
            buttonHeight,
            StatCollector.translateToLocal("menu.button.beyonddimensions.removemember"));
        this.buttonList.add(removeMemberButton);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        rebuildPermissionButtons();
        updateActionButtons();
    }

    /** 根据菜单数据重建权限信息按钮列表 */
    private void rebuildPermissionButtons() {
        for (PermissionInfoButton button : permissionButtons) {
            this.buttonList.remove(button);
        }
        permissionButtons.clear();

        if (menu.playerInfo == null || menu.playerInfo.isEmpty()) {
            // 权限数据尚未同步（网络包到达前）或网络无成员：无可重建的按钮，直接返回。
            // 保留 selectedPlayerId 以便后续同步到达后操作按钮仍可发送（审计 M3-7 清除死代码分支）。
            return;
        }

        int x = this.guiLeft + 11;
        int y = this.guiTop + 18;
        int buttonHeight = 10;
        int count = 0;

        // 对齐源项目 NetControlGUI：成员列表先按权限级别、再按名称排序，避免 HashMap 无序遍历
        List<Map.Entry<UUID, PlayerPermissionInfo>> sortedEntries = new ArrayList<>(menu.playerInfo.entrySet());
        sortedEntries.sort(
            Comparator.comparing(
                (Map.Entry<UUID, PlayerPermissionInfo> e) -> e.getValue()
                    .level())
                .thenComparing(
                    e -> e.getValue()
                        .name()));

        for (Map.Entry<UUID, PlayerPermissionInfo> entry : sortedEntries) {
            if (count >= MAX_SHOW_PLAYERS) break;
            UUID playerId = entry.getKey();
            PlayerPermissionInfo info = entry.getValue();
            int buttonY = y + count * buttonHeight;
            PermissionInfoButton button = new PermissionInfoButton(
                PERMISSION_BUTTON_BASE_ID + count,
                x,
                buttonY,
                84,
                buttonHeight,
                playerId,
                info,
                info.name(),
                btn -> {
                    PermissionInfoButton pib = (PermissionInfoButton) btn;
                    selectedPlayerId = pib.getPlayerId();
                    selectedPlayerName = pib.getPermissionInfo()
                        .name();
                    selectedPlayerLevel = pib.getPermissionInfo()
                        .level();
                });
            permissionButtons.add(button);
            this.buttonList.add(button);
            count++;
        }

        // 同步选中玩家信息
        boolean found = false;
        for (PermissionInfoButton button : permissionButtons) {
            if (button.getPlayerId()
                .equals(selectedPlayerId)) {
                selectedPlayerName = button.getPermissionInfo()
                    .name();
                selectedPlayerLevel = button.getPermissionInfo()
                    .level();
                found = true;
                break;
            }
        }
        if (!found && selectedPlayerId != null) {
            selectedPlayerName = "";
            selectedPlayerLevel = null;
        }
    }

    /** 根据是否有选中玩家更新操作按钮可用状态 */
    private void updateActionButtons() {
        boolean hasSelection = selectedPlayerId != null;
        ownerButton.enabled = hasSelection;
        managerButton.enabled = hasSelection;
        removeManagerButton.enabled = hasSelection;
        removeMemberButton.enabled = hasSelection;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (selectedPlayerId == null) return;
        NetControlAction action = null;
        switch (button.id) {
            case ID_OWNER_BUTTON:
                action = NetControlAction.SetOwner;
                break;
            case ID_MANAGER_BUTTON:
                action = NetControlAction.SetManager;
                break;
            case ID_REMOVE_MANAGER_BUTTON:
                action = NetControlAction.RemoveManager;
                break;
            case ID_REMOVE_MEMBER_BUTTON:
                action = NetControlAction.RemovePlayer;
                break;
            default:
                return;
        }
        BDPackets.INSTANCE.sendToServer(new NetControlActionPacket(selectedPlayerId, action));
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GuiRenderHelper.resetColor();
        GuiRenderHelper.blit(
            GUI_TEXTURE,
            this.guiLeft,
            this.guiTop,
            this.xSize,
            this.ySize,
            0,
            0,
            this.xSize,
            this.ySize,
            TEXTURE_WIDTH,
            TEXTURE_HEIGHT);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        this.fontRendererObj
            .drawString(StatCollector.translateToLocal("gui.beyonddimensions.net_control"), 11, 8, 4210752);
        this.fontRendererObj.drawString(StatCollector.translateToLocal("container.inventory"), 8, 142, 4210752);

        // 显示选中玩家的权限级别
        if (selectedPlayerLevel == null) {
            this.fontRendererObj.drawString(
                StatCollector.translateToLocal("menu.text.beyonddimensions.permission.level.zero"),
                110,
                10,
                4210752);
        } else {
            String levelText = StatCollector.translateToLocal("menu.text.beyonddimensions.permission.level.prefix")
                + " "
                + selectedPlayerLevel.name();
            this.fontRendererObj.drawString(levelText, 110, 10, 4210752);
        }

        // 显示选中玩家的名称
        String nameText = StatCollector.translateToLocal("menu.text.beyonddimensions.name.player") + " "
            + selectedPlayerName;
        this.fontRendererObj.drawString(nameText, 110, 25, 4210752);
    }
}
