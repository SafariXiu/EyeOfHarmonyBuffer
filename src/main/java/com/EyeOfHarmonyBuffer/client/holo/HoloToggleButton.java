package com.EyeOfHarmonyBuffer.client.holo;

import java.util.function.Consumer;

/**
 * 开关按钮组件：每次点击翻转按压态。onToggle 收到新状态（true=按下）。
 * 绘制继承 HoloButton（按下时用 active 色）。用作停机/启停类按钮。
 */
public class HoloToggleButton extends HoloButton {

    private final Consumer<Boolean> onToggle;

    public HoloToggleButton(int z, int x, int y, int w, int h, String label,
                            int base, int hover, int active, int text, int inner,
                            Consumer<Boolean> onToggle) {
        super(z, x, y, w, h, label, base, hover, active, text, inner, null);
        this.onToggle = onToggle;
    }

    @Override
    public void onClick(int u, int v) {
        setPressed(!isPressed());
        if (onToggle != null) {
            onToggle.accept(isPressed());
        }
    }
}
