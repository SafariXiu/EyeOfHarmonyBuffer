package com.EyeOfHarmonyBuffer.client.rbmk;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

/**
 * 自写世界全息面板渲染器（不使用 ModularUI2 的 ModularScreen）。
 * 用 vanilla Tessellator（矩形）+ FontRenderer（文字）在世界平面上直接绘制，
 * 配合自写 facing 矩阵使面板始终面向玩家。完全控制 GL 状态。
 */
@cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
public class RbmkHoloRender extends Render {

    public static final int W = 480;
    public static final int H = 270;
    public static final float SCALE = 0.25f;

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        return null;
    }

    @Override
    public void doRender(Entity entity, double x, double y, double z, float entityYaw, float partialTicks) {
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player == null) {
            return;
        }
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        applyFacing(entity, player);
        drawPanel();
        GL11.glPopMatrix();
    }

    /** 面板平面面向玩家：局部 (u,v)，u 向右，v 向下（GUI 惯例），法向 n 朝玩家。 */
    private static void applyFacing(Entity entity, EntityPlayer player) {
        float nX = (float) (player.posX - entity.posX);
        float nY = (float) (player.posY + player.getEyeHeight() - entity.posY);
        float nZ = (float) (player.posZ - entity.posZ);
        float len = (float) Math.sqrt(nX * nX + nY * nY + nZ * nZ);
        if (len < 1e-4f) {
            nX = 0f;
            nY = 0f;
            nZ = 1f;
        } else {
            nX /= len;
            nY /= len;
            nZ /= len;
        }
        // right = normalize(cross(up, n))，up = (0,1,0) → (nZ, 0, -nX)
        float rX = nZ;
        float rY = 0f;
        float rZ = -nX;
        float rl = (float) Math.sqrt(rX * rX + rY * rY + rZ * rZ);
        if (rl < 1e-4f) {
            rX = 1f;
            rY = 0f;
            rZ = 0f;
        } else {
            rX /= rl;
            rY /= rl;
            rZ /= rl;
        }
        // up2 = cross(n, right)
        float uX = nY * rZ - nZ * rY;
        float uY = nZ * rX - nX * rZ;
        float uZ = nX * rY - nY * rX;

        float s = 0.0625f * SCALE;
        FloatBuffer m = BufferUtils.createFloatBuffer(16);
        m.put(new float[] {
            rX, rY, rZ, 0f,
            -uX, -uY, -uZ, 0f,
            nX, nY, nZ, 0f,
            0f, 0f, 0f, 1f
        });
        m.flip();
        GL11.glMultMatrix(m);
        GL11.glScalef(s, s, s);
        GL11.glTranslatef(-W / 2f, -H / 2f, 0);
    }

    private void drawPanel() {
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_TEXTURE_2D);

        FontRenderer font = Minecraft.getMinecraft().fontRenderer;

        // 背景
        drawRect(0, 0, W, H, 0xE6101820);
        // 顶边框
        drawRect(0, 0, W, 3, 0xFF2A6B8F);
        // 标题
        font.drawString("RBMK-1000 · 四号机组 · 自写面板 PoC", 20, 14, 0xFFFFFFFF);

        // 滑块轨道 + 滑块（棒位 62%）
        drawRect(150, 70, 370, 88, 0xFF2A2A2A);
        int rodX = 150 + (int) ((62.0 / 100.0) * 220);
        drawRect(rodX - 5, 66, rodX + 5, 92, 0xFF55AAFF);
        font.drawString("控制棒: 62%", 20, 72, 0xFFFFFFFF);
        font.drawString("0%", 150, 92, 0xFF888888);
        font.drawString("100%", 355, 92, 0xFF888888);

        // AZ-5 红色大按钮
        drawRect(150, 110, 370, 162, 0xFFAA0000);
        drawRect(152, 112, 368, 160, 0xFFCC2222);
        font.drawString("AZ-5 紧急停堆", 190, 128, 0xFFFFFFFF);

        // 状态行
        drawRect(20, 190, 460, 210, 0xFF0A0A0A);
        font.drawString("运行中 · 自写世界面板 PoC", 24, 196, 0xFF88FF88);

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_CULL_FACE);
    }

    private void drawRect(int x0, int y0, int x1, int y1, int color) {
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
}
