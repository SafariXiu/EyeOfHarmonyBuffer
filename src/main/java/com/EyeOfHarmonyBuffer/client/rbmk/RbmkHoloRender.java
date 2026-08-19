package com.EyeOfHarmonyBuffer.client.rbmk;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
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
 * 朝向基与 RbmkHoloMath 共用（渲染/拾取坐标一致），控件绘制走 RbmkHoloPanel（z 排序）。
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

    private static void applyFacing(Entity entity, EntityPlayer player) {
        RbmkHoloMath.Frame f = RbmkHoloMath.frameFor(entity, player);
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
        double rod = RbmkHoloState.rodPos;
        boolean az5 = RbmkHoloState.az5Pressed;

        // 背景 + 顶边框
        RbmkHoloDraw.drawRect(0, 0, W, H, 0xE6101820);
        RbmkHoloDraw.drawRect(0, 0, W, 3, 0xFF2A6B8F);
        font.drawString("RBMK-1000 · 四号机组 · 自写面板", 20, 14, 0xFFFFFFFF);

        // 控制棒文字 + 刻度（滑块本体由控件绘制）
        font.drawString("控制棒: " + (int) rod + "%", 20, 72, 0xFFFFFFFF);
        font.drawString("0%", 150, 92, 0xFF888888);
        font.drawString("100%", 355, 92, 0xFF888888);
        font.drawString("\u624b\u52a8:", 348, 72, 0xFF888888);

        // 控件（含 hover 高亮；内部按 z 排序绘制）
        RbmkHoloPanel.INSTANCE.draw(font);

        // 状态行
        RbmkHoloDraw.drawRect(20, 190, 460, 228, 0xFF0A0A0A);
        String state = az5 ? "✓ AZ-5 已按下 (停堆状态)" : "运行中";
        font.drawString(state, 24, 196, az5 ? 0xFFFF8800 : 0xFF88FF88);
        font.drawString("面板交互: 准星对准 + 左键", 24, 208, 0xFFAAAAAA);

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_CULL_FACE);
    }
}