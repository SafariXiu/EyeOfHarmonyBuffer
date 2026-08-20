package com.EyeOfHarmonyBuffer.client.holo;

import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;

/** 世界面板绘制工具（vanilla Tessellator 矩形）。 */
public class HoloDraw {

    public static void drawRect(int x0, int y0, int x1, int y1, int color) {
        float a = (color >> 24 & 255) / 255f;
        float r = (color >> 16 & 255) / 255f;
        float g = (color >> 8 & 255) / 255f;
        float b = (color & 255) / 255f;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(r, g, b, a);
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.addVertex(x0, y0, 0);
        tess.addVertex(x0, y1, 0);
        tess.addVertex(x1, y1, 0);
        tess.addVertex(x1, y0, 0);
        tess.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    public static void drawBorder(int x, int y, int w, int h, int color, int thickness) {
        drawRect(x, y, x + w, y + thickness, color);
        drawRect(x, y + h - thickness, x + w, y + h, color);
        drawRect(x, y, x + thickness, y + h, color);
        drawRect(x + w - thickness, y, x + w, y + h, color);
    }
}
