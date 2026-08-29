package com.EyeOfHarmonyBuffer.client.holo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** 世界面板绘制工具（vanilla Tessellator 矩形 / 贴图）。 */
public class HoloDraw {

    public static void drawRect(int x0, int y0, int x1, int y1, int color) {
        float a = (color >> 24 & 255) / 255f;
        float r = (color >> 16 & 255) / 255f;
        float g = (color >> 8 & 255) / 255f;
        float b = (color & 255) / 255f;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        // 用 Tessellator 自带颜色（glColor4f 不作用于 addVertex 顶点色，且会污染 GL 当前色）
        tess.setColorRGBA_F(r, g, b, a);
        tess.addVertex(x0, y0, 0);
        tess.addVertex(x0, y1, 0);
        tess.addVertex(x1, y1, 0);
        tess.addVertex(x1, y0, 0);
        tess.draw();
        // 恢复 Tessellator 全局颜色为白色：setColorRGBA_F 会污染单例颜色状态，
        // 不恢复会让后续 GUI/物品渲染（共用 Tessellator.instance）用残留暗色 → 物品/选中框变黑。
        tess.setColorRGBA(255, 255, 255, 255);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    public static void drawBorder(int x, int y, int w, int h, int color, int thickness) {
        drawRect(x, y, x + w, y + thickness, color);
        drawRect(x, y + h - thickness, x + w, y + h, color);
        drawRect(x, y, x + thickness, y + h, color);
        drawRect(x + w - thickness, y, x + w, y + h, color);
    }

    /** 带纹理的矩形（贴图）：绑定纹理后用 Tessellator 画带 UV 的四边形。
     *  u0/v0~u1/v1 为纹理坐标（0~1，v 向下）。画完恢复 Tessellator 全局颜色，避免污染后续 GUI 渲染。 */
    public static void drawTexturedRect(int x0, int y0, int x1, int y1, ResourceLocation tex,
                                        float u0, float v0, float u1, float v1, int color) {
        float a = (color >> 24 & 255) / 255f;
        float r = (color >> 16 & 255) / 255f;
        float g = (color >> 8 & 255) / 255f;
        float b = (color & 255) / 255f;
        Minecraft.getMinecraft().getTextureManager().bindTexture(tex);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.setColorRGBA_F(r, g, b, a);
        tess.addVertexWithUV(x0, y0, 0, u0, v0);
        tess.addVertexWithUV(x0, y1, 0, u0, v1);
        tess.addVertexWithUV(x1, y1, 0, u1, v1);
        tess.addVertexWithUV(x1, y0, 0, u1, v0);
        tess.draw();
        tess.setColorRGBA(255, 255, 255, 255);
    }
}
