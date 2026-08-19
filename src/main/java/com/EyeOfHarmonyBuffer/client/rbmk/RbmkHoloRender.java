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
import java.util.Random;

/**
 * 自写世界全息面板渲染器。
 * 按 RbmkHoloEntity.viewType 分派：0 = 控制面板（RbmkHoloPanel 控件），1 = 堆芯俯瞰大屏（完整栅格）。
 */
@cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
public class RbmkHoloRender extends Render {

    public static final int W = 480;
    public static final int H = 270;
    public static final float SCALE = 0.25f;

    // 堆芯俯瞰大屏视口
    public static final int CORE_W = 760;
    public static final int CORE_H = 700;

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
        int view = (entity instanceof RbmkHoloEntity) ? ((RbmkHoloEntity) entity).viewType : 0;
        int w = view == 1 ? CORE_W : W;
        int h = view == 1 ? CORE_H : H;
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        applyFacing(entity, player, w, h);
        if (view == 1) {
            drawCoreView(w, h);
        } else {
            drawControlPanel(w, h);
        }
        GL11.glPopMatrix();
    }

    private static void applyFacing(Entity entity, EntityPlayer player, int w, int h) {
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
        GL11.glTranslatef(-w / 2f, -h / 2f, 0);
    }

    private void setupDrawing() {
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    private void teardownDrawing() {
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_CULL_FACE);
    }

    // ==================== 控制面板（viewType 0） ====================

    private void drawControlPanel(int w, int h) {
        setupDrawing();
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        double rod = RbmkHoloState.rodPos;
        boolean az5 = RbmkHoloState.az5Pressed;

        RbmkHoloDraw.drawRect(0, 0, w, h, 0xE6101820);
        RbmkHoloDraw.drawRect(0, 0, w, 3, 0xFF2A6B8F);
        font.drawString("RBMK-1000 · 四号机组 · 自写面板", 20, 14, 0xFFFFFFFF);
        font.drawString("控制棒: " + (int) rod + "%", 20, 72, 0xFFFFFFFF);
        font.drawString("0%", 150, 92, 0xFF888888);
        font.drawString("100%", 355, 92, 0xFF888888);
        font.drawString("手动:", 348, 72, 0xFF888888);

        RbmkHoloPanel.INSTANCE.draw(font);

        // 未激活：面板整体边框提示
        if (!RbmkHoloState.activated) {
            RbmkHoloDraw.drawBorder(0, 0, w, h, 0xFFCCAA00, 2);
        }
        RbmkHoloDraw.drawRect(20, 190, 460, 228, 0xFF0A0A0A);
        String state = az5 ? "✓ AZ-5 已按下 (停堆状态)" : "运行中";
        font.drawString(state, 24, 196, az5 ? 0xFFFF8800 : 0xFF88FF88);
        boolean act = RbmkHoloState.activated;
        String ops = act ? "已激活 - 右键退出面板" : "未激活 - 左键点击面板以激活";
        font.drawString(ops, 24, 208, act ? 0xFFAAAAAA : 0xFFFFCC00);

        teardownDrawing();
    }

    // ==================== 堆芯俯瞰大屏（viewType 1） ====================

    // 栅格类型：0=圆外空，1=反射层石墨，2=燃料通道，3=控制棒
    private static final int CORE_GRID = 48;
    private static final byte[][] core = new byte[CORE_GRID][CORE_GRID];
    private static int coreFuel = 0, coreRods = 0, coreGraph = 0;
    private static boolean coreReady = false;

    /** 按真实比例程序生成堆芯栅格（1661 燃料 / 211 棒 / 反射层 的示意比例）。 */
    private static void ensureCore() {
        if (coreReady) {
            return;
        }
        Random rnd = new Random(20260826L);
        double half = (CORE_GRID - 1) / 2.0;
        double radius = half - 0.5;
        for (int yy = 0; yy < CORE_GRID; yy++) {
            for (int xx = 0; xx < CORE_GRID; xx++) {
                double d = Math.sqrt((xx - half) * (xx - half) + (yy - half) * (yy - half));
                if (d > radius + 0.5) {
                    core[yy][xx] = 0;
                } else if (d > radius - 2.5) {
                    core[yy][xx] = 1;      // 反射层
                    coreGraph++;
                } else {
                    core[yy][xx] = 2;      // 燃料
                    coreFuel++;
                }
            }
        }
        // 控制棒穿插：燃料区约 1/8 → 控制棒
        for (int yy = 0; yy < CORE_GRID; yy++) {
            for (int xx = 0; xx < CORE_GRID; xx++) {
                if (core[yy][xx] == 2 && rnd.nextInt(8) == 0) {
                    core[yy][xx] = 3;
                    coreRods++;
                    coreFuel--;
                }
            }
        }
        coreReady = true;
    }

    private void drawCoreView(int w, int h) {
        ensureCore();
        setupDrawing();
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;

        RbmkHoloDraw.drawRect(0, 0, w, h, 0xFF05070A);
        RbmkHoloDraw.drawRect(0, 0, w, 3, 0xFF2A6B8F);
        font.drawString("堆芯俯瞰 · 完整复刻示意", 20, 12, 0xFFFFFFFF);

        // 栅格区
        int top = 40;
        int bottom = h - 26;
        int cell = Math.min((w - 30) / CORE_GRID, (bottom - top) / CORE_GRID);
        int gap = 2;
        int originX = (w - (cell * CORE_GRID + gap * (CORE_GRID - 1))) / 2;
        int originY = top + ((bottom - top) - (cell * CORE_GRID + gap * (CORE_GRID - 1))) / 2;

        for (int yy = 0; yy < CORE_GRID; yy++) {
            for (int xx = 0; xx < CORE_GRID; xx++) {
                byte t = core[yy][xx];
                if (t == 0) {
                    continue; // 圆外空
                }
                int color;
                switch (t) {
                    case 1:  color = 0xFF343C46; break;  // 反射层石墨
                    case 3:  color = 0xFF3D6BE0; break;  // 控制棒（蓝）
                    default: color = 0xFF9AA4AE; break;  // 燃料（中性灰）
                }
                int px = originX + xx * (cell + gap);
                int py = originY + yy * (cell + gap);
                RbmkHoloDraw.drawRect(px, py, px + cell, py + cell, color);
            }
        }

        // 图例 + 计数
        font.drawString("图例: 燃料=灰 控制棒=蓝 反射层=深灰"
            + "    [燃料 " + coreFuel + " / 控制棒 " + coreRods + " / 反射 " + coreGraph + "]", 20, bottom + 4, 0xFFAAAAAA);

        teardownDrawing();
    }
}
