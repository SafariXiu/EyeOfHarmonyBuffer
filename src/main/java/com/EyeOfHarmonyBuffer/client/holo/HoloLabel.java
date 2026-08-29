package com.EyeOfHarmonyBuffer.client.holo;

/**
 * 静态文字标签组件：显示一行文本（不响应点击，w/h 为 0 不参与命中）。
 * 用于需要作为控件放在 widget 列表里的静态文案。
 */
public class HoloLabel extends HoloWidget {

    private final String text;
    private final int color;

    public HoloLabel(int z, int x, int y, String text, int color) {
        super(z, x, y, 0, 0);
        this.text = text;
        this.color = color;
    }

    @Override
    public void draw(HoloCanvas c) {
        if (text != null && !text.isEmpty()) {
            c.text(x, y, text, color);
        }
    }

    @Override
    public void onClick(int u, int v) {
        // 标签不响应点击
    }
}
