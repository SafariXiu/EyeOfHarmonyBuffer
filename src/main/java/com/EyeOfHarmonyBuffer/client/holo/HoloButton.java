package com.EyeOfHarmonyBuffer.client.holo;

/**
 * 通用按钮组件。支持：普通按钮（点击回调）与按压态（pressed，供开关类按钮复用）。
 * 标签水平垂直居中；hover 白框高亮。按下态用 active 色。
 */
public class HoloButton extends HoloWidget {

    private final String label;
    private final int base, hover, active, text, inner;
    private boolean pressed;
    private final Runnable action;

    /** 默认配色按钮。 */
    public HoloButton(int z, int x, int y, int w, int h, String label, Runnable action) {
        this(z, x, y, w, h, label, 0xFF2A2A2A, 0xFF3D4A55, 0xFF556677, 0xFFFFFFFF, 0, action);
    }

    /** 自定义配色按钮。inner=内部内嵌矩形色（0=无）。 */
    public HoloButton(int z, int x, int y, int w, int h, String label,
                      int base, int hover, int active, int text, int inner, Runnable action) {
        super(z, x, y, w, h);
        this.label = label;
        this.base = base;
        this.hover = hover;
        this.active = active;
        this.text = text;
        this.inner = inner;
        this.action = action;
    }

    public boolean isPressed() {
        return pressed;
    }

    public void setPressed(boolean p) {
        this.pressed = p;
    }

    @Override
    public void draw(HoloCanvas c) {
        int col = pressed ? active : (hovered ? hover : base);
        c.rect(x, y, w, h, col);
        if (inner != 0) {
            c.rect(x + 2, y + 2, w - 4, h - 4, inner);
        }
        if (hovered) {
            c.border(x, y, w, h, 0xFFFFFFFF, 2);
        }
        if (label != null && !label.isEmpty()) {
            int tw = c.font().getStringWidth(label);
            c.text(x + Math.max(2, (w - tw) / 2), y + Math.max(2, (h - 9) / 2), label, text);
        }
    }

    @Override
    public void onClick(int u, int v) {
        if (action != null) {
            action.run();
        }
    }
}
