package com.EyeOfHarmonyBuffer.client.holo;

import net.minecraft.client.gui.FontRenderer;

/**
 * 世界屏绘制画布：屏/控件绘制只通过本类，不直接碰 GL/Tessellator。
 * 封装 HoloDraw 图元与文字绘制，统一矩形/边框/文字三个能力。
 */
public class HoloCanvas {

    private final FontRenderer font;

    public HoloCanvas(FontRenderer font) {
        this.font = font;
    }

    /** 供需要测量文字宽度的控件使用（如居中）。 */
    public FontRenderer font() {
        return font;
    }

    /** 实心矩形（x,y 左上角，w/h 为宽高，color 为 ARGB）。 */
    public void rect(int x, int y, int w, int h, int color) {
        HoloDraw.drawRect(x, y, x + w, y + h, color);
    }

    /** 边框（thickness 像素）。 */
    public void border(int x, int y, int w, int h, int color, int thickness) {
        HoloDraw.drawBorder(x, y, w, h, color, thickness);
    }

    /** 文字。 */
    public void text(int x, int y, String s, int color) {
        if (s != null && !s.isEmpty()) {
            font.drawString(s, x, y, color);
        }
    }
}
