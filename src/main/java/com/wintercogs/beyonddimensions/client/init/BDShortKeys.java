package com.wintercogs.beyonddimensions.client.init;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;

import org.lwjgl.input.Keyboard;

import com.wintercogs.beyonddimensions.api.ButtonState;
import com.wintercogs.beyonddimensions.api.dimensionnet.PrimaryNetSwitchAction;
import com.wintercogs.beyonddimensions.common.init.BDPackets;
import com.wintercogs.beyonddimensions.config.CommonConfigRuntime;
import com.wintercogs.beyonddimensions.network.packet.c2s.OpenMagnetGuiPacket;
import com.wintercogs.beyonddimensions.network.packet.c2s.OpenNetGuiPacket;
import com.wintercogs.beyonddimensions.network.packet.c2s.OpenPrimaryNetSwitcherPacket;
import com.wintercogs.beyonddimensions.network.packet.c2s.PickBlockFromNetPacket;
import com.wintercogs.beyonddimensions.network.packet.c2s.PrimaryNetSwitchActionPacket;
import com.wintercogs.beyonddimensions.network.packet.c2s.PutHandItemToNetPacket;
import com.wintercogs.beyonddimensions.network.packet.c2s.ToggleMagnetPacket;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 超越维度模组客户端快捷键注册中心（1.7.10 移植版）。
 * <p>
 * 1.7.10 适配：KeyMapping → KeyBinding；RegisterKeyMappingsEvent → ClientRegistry.registerKeyBinding；
 * GLFW 键码 → org.lwjgl.input.Keyboard；鼠标中键 → -98（与 1.7.10 _pick block_ 键码一致）。
 */
@SideOnly(Side.CLIENT)
public class BDShortKeys {

    private static final List<KeyBindingRunnable> KEY_MAPPINGS_WITH_CALLBACK = new ArrayList<>();
    private static final List<KeyBinding> KEY_MAPPINGS = new ArrayList<>();

    public static final KeyBinding OPEN_GUI_KEY = new KeyBinding(
        "key.beyonddimensions.open_gui",
        Keyboard.KEY_O,
        "key.categories.beyonddimensions");

    public static final KeyBinding OPEN_TERMINAL_QUICK_KEY = new KeyBinding(
        "key.beyonddimensions.open_terminal_quick_key",
        Keyboard.KEY_P,
        "key.categories.beyonddimensions");

    /** 1.7.10 鼠标中键在 KeyBinding 中的内部键码为 -98 */
    public static final KeyBinding MAIN_HAND_ITEM_TRANSFER_KEY = new KeyBinding(
        "key.beyonddimensions.main_hand_item_transfer_key",
        -98,
        "key.categories.beyonddimensions");

    public static final KeyBinding TOGGLE_MAGNET_KEY = new KeyBinding(
        "key.beyonddimensions.toggle_magnet_key",
        Keyboard.KEY_LBRACKET,
        "key.categories.beyonddimensions");

    public static final KeyBinding TOGGLE_MAGNET_ITEM_KEY = new KeyBinding(
        "key.beyonddimensions.toggle_magnet_item_key",
        Keyboard.KEY_NONE,
        "key.categories.beyonddimensions");

    public static final KeyBinding TOGGLE_MAGNET_FLUID_KEY = new KeyBinding(
        "key.beyonddimensions.toggle_magnet_fluid_key",
        Keyboard.KEY_NONE,
        "key.categories.beyonddimensions");

    public static final KeyBinding OPEN_MAGNET_GUI_KEY = new KeyBinding(
        "key.beyonddimensions.open_magnet_gui_key",
        Keyboard.KEY_NONE,
        "key.categories.beyonddimensions");

    public static final KeyBinding OPEN_PRIMARY_NET_SWITCHER_KEY = new KeyBinding(
        "key.beyonddimensions.open_primary_net_switcher_key",
        Keyboard.KEY_U,
        "key.categories.beyonddimensions");

    public static final KeyBinding CYCLE_PRIMARY_NET_KEY = new KeyBinding(
        "key.beyonddimensions.cycle_primary_net_key",
        Keyboard.KEY_RBRACKET,
        "key.categories.beyonddimensions");

    public static void processKeyInput() {
        for (KeyBindingRunnable entry : KEY_MAPPINGS_WITH_CALLBACK) {
            KeyBinding key = entry.keyBinding;
            Runnable runnable = entry.runnable;
            while (key.isPressed()) {
                runnable.run();
            }
        }
    }

    public static void registerKey(KeyBinding keyBinding) {
        KEY_MAPPINGS.add(keyBinding);
    }

    public static void registerKey(KeyBinding keyBinding, Runnable runnable) {
        KEY_MAPPINGS.add(keyBinding);
        KEY_MAPPINGS_WITH_CALLBACK.add(new KeyBindingRunnable(keyBinding, runnable));
    }

    /**
     * 在客户端初始化阶段调用，注册所有快捷键并绑定回调。
     */
    public static void registerKeys() {
        BDShortKeys.registerKey(OPEN_GUI_KEY, () -> {
            EntityClientPlayerMP player = Minecraft.getMinecraft().thePlayer;
            if (player == null) {
                return;
            }

            if (CommonConfigRuntime.uiCraftButton == ButtonState.ENABLED) {
                BDPackets.INSTANCE.sendToServer(
                    new OpenNetGuiPacket(
                        player.getUniqueID()
                            .toString(),
                        OpenNetGuiPacket.NET_CRAFT_MENU));
            } else {
                BDPackets.INSTANCE.sendToServer(
                    new OpenNetGuiPacket(
                        player.getUniqueID()
                            .toString(),
                        OpenNetGuiPacket.NET_MENU));
            }
        });
        BDShortKeys.registerKey(OPEN_TERMINAL_QUICK_KEY, () -> {
            EntityClientPlayerMP player = Minecraft.getMinecraft().thePlayer;
            if (player == null) {
                return;
            }
            BDPackets.INSTANCE.sendToServer(
                new OpenNetGuiPacket(
                    player.getUniqueID()
                        .toString(),
                    OpenNetGuiPacket.NET_CRAFT_TERMINAL));
        });
        BDShortKeys.registerKey(MAIN_HAND_ITEM_TRANSFER_KEY, () -> {
            EntityPlayer player = Minecraft.getMinecraft().thePlayer;
            if (player == null || player.capabilities.isCreativeMode) {
                return;
            }
            ItemStack mainHand = player.getHeldItem();
            if (mainHand != null) {
                if (GuiScreen.isShiftKeyDown()) {
                    BDPackets.INSTANCE.sendToServer(new PutHandItemToNetPacket(0));
                }
            } else {
                MovingObjectPosition hit = Minecraft.getMinecraft().objectMouseOver;
                if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
                    return;
                }
                Item targetBlockItem = Item
                    .getItemFromBlock(player.worldObj.getBlock(hit.blockX, hit.blockY, hit.blockZ));
                if (targetBlockItem == null) {
                    return;
                }
                ItemStack targetStack = new ItemStack(targetBlockItem);
                BDPackets.INSTANCE.sendToServer(new PickBlockFromNetPacket(targetStack));
            }
        });
        BDShortKeys.registerKey(TOGGLE_MAGNET_KEY, () -> {
            EntityClientPlayerMP player = Minecraft.getMinecraft().thePlayer;
            if (player == null) {
                return;
            }
            BDPackets.INSTANCE.sendToServer(new ToggleMagnetPacket(ToggleMagnetPacket.ALL));
        });
        BDShortKeys.registerKey(TOGGLE_MAGNET_ITEM_KEY, () -> {
            EntityClientPlayerMP player = Minecraft.getMinecraft().thePlayer;
            if (player == null) {
                return;
            }
            BDPackets.INSTANCE.sendToServer(new ToggleMagnetPacket(ToggleMagnetPacket.ITEM));
        });
        BDShortKeys.registerKey(TOGGLE_MAGNET_FLUID_KEY, () -> {
            EntityClientPlayerMP player = Minecraft.getMinecraft().thePlayer;
            if (player == null) {
                return;
            }
            BDPackets.INSTANCE.sendToServer(new ToggleMagnetPacket(ToggleMagnetPacket.FLUID));
        });
        BDShortKeys.registerKey(OPEN_MAGNET_GUI_KEY, () -> {
            EntityClientPlayerMP player = Minecraft.getMinecraft().thePlayer;
            if (player == null) {
                return;
            }
            BDPackets.INSTANCE.sendToServer(new OpenMagnetGuiPacket());
        });
        BDShortKeys.registerKey(OPEN_PRIMARY_NET_SWITCHER_KEY, () -> {
            EntityClientPlayerMP player = Minecraft.getMinecraft().thePlayer;
            if (player == null) {
                return;
            }
            BDPackets.INSTANCE.sendToServer(new OpenPrimaryNetSwitcherPacket());
        });
        BDShortKeys.registerKey(CYCLE_PRIMARY_NET_KEY, () -> {
            EntityClientPlayerMP player = Minecraft.getMinecraft().thePlayer;
            if (player == null) {
                return;
            }
            BDPackets.INSTANCE.sendToServer(new PrimaryNetSwitchActionPacket(PrimaryNetSwitchAction.CYCLE_NEXT, -1));
        });

        for (KeyBinding keyBinding : KEY_MAPPINGS) {
            ClientRegistry.registerKeyBinding(keyBinding);
        }
    }

    private static final class KeyBindingRunnable {

        final KeyBinding keyBinding;
        final Runnable runnable;

        KeyBindingRunnable(KeyBinding keyBinding, Runnable runnable) {
            this.keyBinding = keyBinding;
            this.runnable = runnable;
        }
    }
}
