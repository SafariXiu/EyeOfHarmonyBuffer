package com.EyeOfHarmonyBuffer.client.holo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

/**
 * 世界全息屏渲染器：只负责"把屏放进世界"（朝向矩阵 + 缩放）。
 * 具体画什么由 HoloScreen.draw(canvas) 决定 —— 本类不再有任何屏类型分派。
 */
@cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
public class HoloRender extends Render {

    /** 世界缩放系数：1 世界单位 = 1/(0.0625*SCALE) 屏像素。 */
    public static final float SCALE = 0.25f;

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        return null;
    }

    @Override
    public void doRender(Entity entity, double x, double y, double z, float entityYaw, float partialTicks) {
        if (!(entity instanceof HoloEntity h)) {
            return;
        }
        HoloScreen screen = h.getScreen();
        if (screen == null) {
            return;
        }
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player == null) {
            return;
        }
        int w = screen.w;
        int hh = screen.h;
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        applyFacing(entity, player, w, hh);
        setupDrawing();
        HoloCanvas canvas = new HoloCanvas(Minecraft.getMinecraft().fontRenderer);
        screen.draw(canvas);
        teardownDrawing();
        GL11.glPopMatrix();
    }

    private static void applyFacing(Entity entity, EntityPlayer player, int w, int h) {
        HoloMath.Frame f = HoloMath.frameFor(entity, player);
        float s = 0.0625f * SCALE;
        FloatBuffer m = BufferUtils.createFloatBuffer(16);
        m.put(new float[] {
            f.rx, f.ry, f.rz, 0f,
            -f.ux, -f.uy, -f.uz, 0f,
            f.nx, f.ny, f.nz, 0f,
            0f, 0f, 0f, 1f
        });
        m.flip();
        GL11.glMultMatrix(m);
        GL11.glScalef(s, s, s);
        GL11.glTranslatef(-w / 2f, -h / 2f, 0);
    }

    private void setupDrawing() {
        GL11.glDisable(GL11.GL_LIGHTING);
        // 深度测试保留开启：全息屏应被实心方块正常遮挡（关掉会一直穿透所有方块画在最上层）。
        // 但屏内所有元素画在同一 z=0 平面，默认 GL_LESS 会把后画的同深度元素全剔除（边框/文字/滑块
        // 都画不出来 → 伪影闪烁）。改用 GL_LEQUAL：同深度按绘制顺序覆盖（画家算法），真实方块因深度
        // 不同仍正常遮挡。
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    private void teardownDrawing() {
        // 恢复默认深度函数（vanilla 用 GL_LESS）
        GL11.glDepthFunc(GL11.GL_LESS);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_BLEND);
    }
}
