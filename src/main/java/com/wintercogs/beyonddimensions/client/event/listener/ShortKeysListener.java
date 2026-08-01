package com.wintercogs.beyonddimensions.client.event.listener;

import com.wintercogs.beyonddimensions.client.init.BDShortKeys;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 快捷键输入监听器（1.7.10 移植版）。
 * <p>
 * 1.7.10 适配：TickEvent.ClientTickEvent → InputEvent.KeyInputEvent / MouseInputEvent；
 * 监听键盘/鼠标按键事件，调用 BDShortKeys 处理已注册的快捷键回调。
 * <p>
 * 注意：1.7.10 的 {@link InputEvent.KeyInputEvent} 仅由键盘事件触发，鼠标按键（含侧键 Mouse4/Mouse5）
 * 需要通过 {@link InputEvent.MouseInputEvent} 单独监听，否则绑定到鼠标按键的快捷键无法响应。
 */
@SideOnly(Side.CLIENT)
public class ShortKeysListener {

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        BDShortKeys.processKeyInput();
    }

    @SubscribeEvent
    public void onMouseInput(InputEvent.MouseInputEvent event) {
        BDShortKeys.processKeyInput();
    }
}
