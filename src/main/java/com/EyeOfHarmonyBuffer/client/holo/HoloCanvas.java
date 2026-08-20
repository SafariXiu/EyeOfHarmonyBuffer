package com.EyeOfHarmonyBuffer.client.holo;

import net.minecraft.client.gui.FontRenderer;

/**
 * 世界屏绘制画布：屏/控件绘制只通过本类，不直接碰 GL/Tessellator。
 * 封装 HoloDraw 图元与文字绘制，并内置文字布局原语：
 * 测量（textWidth）、水平对齐（左/中/右）、矩形内垂直居中、完全居中、
 * 多行竖排（textLines/textLinesCentered/textLinesWithColors）、自动换行（textWrapped）。
 * 屏的绘制不再需要手算每个文字坐标 —— 用这些原语按"锚点"摆放。
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

    /** 文本像素宽度。 */
    public int textWidth(String s) {
        return s == null ? 0 : font.getStringWidth(s);
    }

    // ==================== 图元 ====================

    /** 实心矩形（x,y 左上角，w/h 为宽高，color 为 ARGB）。 */
    public void rect(int x, int y, int w, int h, int color) {
        HoloDraw.drawRect(x, y, x + w, y + h, color);
    }

    /** 边框（thickness 像素）。 */
    public void border(int x, int y, int w, int h, int color, int thickness) {
        HoloDraw.drawBorder(x, y, w, h, color, thickness);
    }

    // ==================== 单行文字 ====================

    /** 文字（左上角对齐）。 */
    public void text(int x, int y, String s, int color) {
        if (s != null && !s.isEmpty()) {
            font.drawString(s, x, y, color);
        }
    }

    /** 水平居中：centerX 为文字中心线，y 为顶缘。 */
    public void textCentered(int centerX, int y, String s, int color) {
        if (s != null && !s.isEmpty()) {
            font.drawString(s, centerX - font.getStringWidth(s) / 2, y, color);
        }
    }

    /** 右对齐：rightX 为文字右缘，y 为顶缘。 */
    public void textRight(int rightX, int y, String s, int color) {
        if (s != null && !s.isEmpty()) {
            font.drawString(s, rightX - font.getStringWidth(s), y, color);
        }
    }

    /** 在矩形内垂直居中：x 为文字左缘，boxY/boxH 为矩形（y 自动算到矩形竖向中间）。 */
    public void textInBox(int x, int boxY, int boxH, String s, int color) {
        if (s != null && !s.isEmpty()) {
            font.drawString(s, x, boxY + (boxH - font.FONT_HEIGHT) / 2, color);
        }
    }

    /** 在矩形内完全居中：boxX/boxY/boxW/boxH 为矩形。 */
    public void textCenteredInBox(int boxX, int boxY, int boxW, int boxH, String s, int color) {
        if (s != null && !s.isEmpty()) {
            font.drawString(s, boxX + (boxW - font.getStringWidth(s)) / 2,
                boxY + (boxH - font.FONT_HEIGHT) / 2, color);
        }
    }

    /** 完全居中：cx/cy 为文字中心。 */
    public void textCenteredBoth(int cx, int cy, String s, int color) {
        if (s != null && !s.isEmpty()) {
            font.drawString(s, cx - font.getStringWidth(s) / 2, cy - font.FONT_HEIGHT / 2, color);
        }
    }

    // ==================== 多行文字 ====================

    /** 竖排多行（lineHeight 行距，默认用 font.FONT_HEIGHT）。 */
    public void textLines(int x, int y, String[] lines, int color, int lineHeight) {
        if (lines == null) {
            return;
        }
        int h = lineHeight > 0 ? lineHeight : font.FONT_HEIGHT;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i] != null && !lines[i].isEmpty()) {
                font.drawString(lines[i], x, y + i * h, color);
            }
        }
    }

    /** 竖排多行，水平居中于 centerX。 */
    public void textLinesCentered(int centerX, int y, String[] lines, int color, int lineHeight) {
        if (lines == null) {
            return;
        }
        int h = lineHeight > 0 ? lineHeight : font.FONT_HEIGHT;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i] != null && !lines[i].isEmpty()) {
                font.drawString(lines[i], centerX - font.getStringWidth(lines[i]) / 2, y + i * h, color);
            }
        }
    }

    /** 竖排多行，每行独立颜色（colors 与 lines 等长，可为 null 表示统一用 color）。 */
    public void textLinesWithColors(int x, int y, String[] lines, int[] colors, int color, int lineHeight) {
        if (lines == null) {
            return;
        }
        int h = lineHeight > 0 ? lineHeight : font.FONT_HEIGHT;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i] != null && !lines[i].isEmpty()) {
                font.drawString(lines[i], x, y + i * h, colors != null && i < colors.length ? colors[i] : color);
            }
        }
    }

    /** 自动换行绘制（按空格断词；单词超宽按字符截断）。返回实际行数。 */
    public int textWrapped(int x, int y, int maxWidth, String text, int color, int lineHeight) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int h = lineHeight > 0 ? lineHeight : font.FONT_HEIGHT;
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        int n = 0;
        for (String word : words) {
            String probe = line.length() == 0 ? word : line + " " + word;
            if (line.length() > 0 && font.getStringWidth(probe) > maxWidth) {
                font.drawString(line.toString(), x, y + n * h, color);
                n++;
                line.setLength(0);
            }
            line.append(line.length() == 0 ? word : " " + word);
        }
        if (line.length() > 0) {
            font.drawString(line.toString(), x, y + n * h, color);
            n++;
        }
        return n;
    }
}
