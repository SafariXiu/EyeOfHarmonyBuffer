package com.EyeOfHarmonyBuffer.client.holo;

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
 * 世界全息屏渲染器：只负责"把屏放进世界"（朝向矩阵 + 缩放）。
 * 具体画什么由 HoloScreen.draw(canvas) 决定 —— 本类不再有任何屏类型分派。
 *
 * {@link #renderScreen} 为通用静态入口：实体屏（HoloEntity 公告板）与机器一体屏
 * （renderTESR 固定朝向）共用同一套绘制管线，只是传入的 Frame 不同。
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
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (screen == null || player == null) {
            return;
        }
        renderScreen(screen, Minecraft.getMinecraft().fontRenderer, x, y, z,
            HoloMath.frameFor(entity, player), SCALE);
    }

    /** 通用世界屏绘制：平移 → 套用朝向矩阵(Frame) → 缩放 → setupDrawing → 屏绘制 → 还原。 */
    public static void renderScreen(HoloScreen screen, FontRenderer font,
                                    double x, double y, double z, HoloMath.Frame f, float scale) {
        if (screen == null || font == null || f == null) {
            return;
        }
        GL11.glPushMatrix();
        // 终极防泄漏：保存全部 GL 状态属性（颜色/混合/深度/光照/纹理绑定/多边形偏移等），
        // 渲染后整体恢复，杜绝全息屏渲染污染后续 GUI/物品渲染（字体纹理、glColor、alpha 测试等残留）。
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL11.glTranslated(x, y, z);
            applyFrame(f, screen.w, screen.h, scale);
            setupDrawing();
            HoloCanvas canvas = new HoloCanvas(font);
            screen.draw(canvas);
        } finally {
            // finally 保证异常路径也执行恢复，不留任何 GL 状态残留
            teardownDrawing();
            GL11.glPopAttrib();
        }
        GL11.glPopMatrix();
    }

    private static void applyFrame(HoloMath.Frame f, int w, int h, float scale) {
        float s = 0.0625f * scale;
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

    /** 进入渲染前 GL_LIGHTING 是否开启（teardown 按此恢复，避免污染后续世界/2D GUI 渲染）。 */
    private static boolean wasLightingEnabled = true;
    /** OpenGL 1.4 GL_DEPTH_CLAMP（LWJGL2 GL11 未导出该常量，硬编码 0x864F）。 */
    private static final int GL_DEPTH_CLAMP = 0x864F;

    private static void setupDrawing() {
        wasLightingEnabled = GL11.glIsEnabled(GL11.GL_LIGHTING);
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
        // 深度钳制：屏幕平面贴近/穿过近裁剪面时钳制深度而非裁剪，避免玩家靠近时整面瞬间消失
        GL11.glEnable(GL_DEPTH_CLAMP);
    }

    private static void teardownDrawing() {
        // 恢复默认深度函数（vanilla 用 GL_LESS）
        GL11.glDepthFunc(GL11.GL_LESS);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_BLEND);
        // 恢复设置过的混合函数与光照：不恢复会让后续世界/2D GUI 渲染颜色与光照错乱（GUI 消失/变暗）
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        if (wasLightingEnabled) {
            GL11.glEnable(GL11.GL_LIGHTING);
        } else {
            GL11.glDisable(GL11.GL_LIGHTING);
        }
        GL11.glColor4f(1f, 1f, 1f, 1f);
        // 双保险：恢复 Tessellator 全局颜色为白色（HoloDraw 每次已恢复，这里兜底防异常路径）
        Tessellator.instance.setColorRGBA(255, 255, 255, 255);
        GL11.glDisable(GL_DEPTH_CLAMP);
    }
}