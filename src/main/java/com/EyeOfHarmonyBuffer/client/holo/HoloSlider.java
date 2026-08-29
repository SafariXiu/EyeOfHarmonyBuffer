package com.EyeOfHarmonyBuffer.client.holo;

import java.util.function.DoubleConsumer;

/**
 * 水平滑块组件：值 0..1。点击轨道任意处设定值（用点击坐标，不吃 tick 悬停的滞后值）。
 * onChange 收到 0..1 的值；也可用 getValue/setValue 直接读写。
 */
public class HoloSlider extends HoloWidget {

    private final DoubleConsumer onChange;
    private final int track, knob, knobHover, border;
    private double value;

    /** 默认配色滑块。 */
    public HoloSlider(int z, int x, int y, int w, int h, double value, DoubleConsumer onChange) {
        this(z, x, y, w, h, value, 0xFF2A2A2A, 0xFF55AAFF, 0xFF77CCFF, 0xFFFFFFFF, onChange);
    }

    /** 自定义配色滑块（track=轨道色，knob=滑块色，knobHover=悬停滑块色，border=悬停边框）。 */
    public HoloSlider(int z, int x, int y, int w, int h, double value,
                      int track, int knob, int knobHover, int border, DoubleConsumer onChange) {
        super(z, x, y, w, h);
        this.value = clamp01(value);
        this.track = track;
        this.knob = knob;
        this.knobHover = knobHover;
        this.border = border;
        this.onChange = onChange;
    }

    /** 当前值（0..1）。 */
    public double getValue() {
        return value;
    }

    public void setValue(double v) {
        this.value = clamp01(v);
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    @Override
    public void draw(HoloCanvas c) {
        c.rect(x, y, w, h, track);
        int kx = x + (int) Math.round(value * w);
        c.rect(kx - 5, y - 4, 10, h + 8, hovered ? knobHover : knob);
        if (hovered) {
            c.border(x, y, w, h, border, 2);
        }
    }

    @Override
    public void onClick(int u, int v) {
        value = clamp01((u - x) / (double) w);
        if (onChange != null) {
            onChange.accept(value);
        }
    }
}
