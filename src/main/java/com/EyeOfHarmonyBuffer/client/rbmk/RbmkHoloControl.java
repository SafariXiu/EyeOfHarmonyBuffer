package com.EyeOfHarmonyBuffer.client.rbmk;

import net.minecraft.client.gui.FontRenderer;

/**
 * 世界面板控件基类。
 * z 层级：越大越靠前。重叠时绘制按 z 升序（上层盖下层），命中按 z 降序（上层优先）。
 */
public abstract class RbmkHoloControl {

    public final int z;
    public final int x, y, w, h;
    public boolean hovered;

    public RbmkHoloControl(int z, int x, int y, int w, int h) {
        this.z = z;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public boolean contains(int px, int py) {
        return px >= x && px <= x + w && py >= y && py <= y + h;
    }

    /** 绘制控件（GL 状态已就绪）。hovered 时自行高亮。 */
    public abstract void draw(FontRenderer font);

    /** 点击回调（命中本控件时调用）。 */
    public abstract void onClick();

    /** 是否可聚焦（接收键盘输入）。 */
    public boolean isFocusable() {
        return false;
    }

    /** 聚焦后键盘输入回调。返回 true 表示消费了该键（阻止游戏动作，如 Enter 开聊天）。 */
    public boolean onKey(char c, int key) {
        return false;
    }

    /** 焦点被移走时回调（如点击别处/右键取消）。 */
    public void onFocusLost() {}
}