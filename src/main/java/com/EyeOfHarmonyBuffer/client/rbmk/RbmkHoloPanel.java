package com.EyeOfHarmonyBuffer.client.rbmk;

import net.minecraft.client.gui.FontRenderer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 世界面板控件容器：管理 z 排序的绘制与命中。
 * 重叠时：绘制按 z 升序（上层盖下层），命中按 z 降序（上层优先）。
 */
public class RbmkHoloPanel {

    public static final RbmkHoloPanel INSTANCE = new RbmkHoloPanel();

    public final List<RbmkHoloControl> controls = new ArrayList<>();

    private RbmkHoloPanel() {
        controls.add(new RodSlider());
        controls.add(new Az5Button());
    }

    /** 命中：按 z 从高到低找最上层命中的控件（重叠时上层优先）。 */
    public RbmkHoloControl hitAt(int px, int py) {
        RbmkHoloControl top = null;
        for (RbmkHoloControl c : controls) {
            if (c.contains(px, py) && (top == null || c.z > top.z)) {
                top = c;
            }
        }
        return top;
    }

    /** 更新各控件 hover 状态（准星在面板上时，命中最上层控件）。 */
    public void updateHover(int px, int py, boolean hovering) {
        RbmkHoloControl hit = hovering ? hitAt(px, py) : null;
        for (RbmkHoloControl c : controls) {
            c.hovered = (c == hit);
        }
    }

    /** 绘制：按 z 升序（底层先画，高层盖上面）。 */
    public void draw(FontRenderer font) {
        List<RbmkHoloControl> sorted = new ArrayList<>(controls);
        sorted.sort(Comparator.comparingInt(c -> c.z));
        for (RbmkHoloControl c : sorted) {
            c.draw(font);
        }
    }

    /** 棒位滑块：点击轨道设值。 */
    private static class RodSlider extends RbmkHoloControl {
        RodSlider() {
            super(10, 150, 70, 220, 18);
        }

        @Override
        public void draw(FontRenderer font) {
            double rod = RbmkHoloState.rodPos;
            RbmkHoloDraw.drawRect(x, y, x + w, y + h, 0xFF2A2A2A);
            int rodX = x + (int) ((rod / 100.0) * w);
            RbmkHoloDraw.drawRect(rodX - 5, y - 4, rodX + 5, y + h + 4, 0xFF55AAFF);
            if (hovered) {
                RbmkHoloDraw.drawBorder(x, y, w, h, 0xFFFFFFFF, 2);
            }
        }

        @Override
        public void onClick() {
            double ratio = (RbmkHoloState.hoverX - x) / (double) w;
            RbmkHoloState.rodPos = Math.max(0, Math.min(100, ratio * 100));
        }
    }

    /** AZ-5 紧急停堆按钮。 */
    private static class Az5Button extends RbmkHoloControl {
        Az5Button() {
            super(10, 150, 110, 220, 52);
        }

        @Override
        public void draw(FontRenderer font) {
            boolean az5 = RbmkHoloState.az5Pressed;
            int color = az5 ? 0xFFFF6600 : (hovered ? 0xFFFF4444 : 0xFFAA0000);
            RbmkHoloDraw.drawRect(x, y, x + w, y + h, color);
            RbmkHoloDraw.drawRect(x + 2, y + 2, x + w - 2, y + h - 2, 0xFFCC2222);
            if (hovered) {
                RbmkHoloDraw.drawBorder(x, y, w, h, 0xFFFFFFFF, 2);
            }
            font.drawString("AZ-5 紧急停堆", 190, 128, 0xFFFFFFFF);
        }

        @Override
        public void onClick() {
            RbmkHoloState.az5Pressed = !RbmkHoloState.az5Pressed;
        }
    }
}
