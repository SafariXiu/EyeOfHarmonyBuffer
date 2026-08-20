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
 * 自写世界全息面板渲染器。
 * 按 RbmkHoloEntity.viewType 分派：0 = 控制面板（RbmkHoloPanel 控件），1 = 堆芯俯瞰大屏（完整栅格）。
 */
@cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
public class RbmkHoloRender extends Render {

    public static final int W = 480;
    public static final int H = 270;
    public static final float SCALE = 0.25f;

    // 堆芯俯瞰大屏视口（扩大一圈，容纳 48×48 满栅格）
    public static final int CORE_W = 820;
    public static final int CORE_H = 830;

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

    private void drawCoreView(int w, int h) {
        RbmkCoreData.ensureLoaded();
        int grid = RbmkCoreData.GRID;
        setupDrawing();
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;

        RbmkHoloDraw.drawRect(0, 0, w, h, 0xFF05070A);
        RbmkHoloDraw.drawRect(0, 0, w, 3, 0xFF2A6B8F);
        font.drawString("堆芯俯瞰 · 完整复刻示意", 20, 12, 0xFFFFFFFF);

        // 栅格区
        int top = 40;
        int bottom = h - 26;
        int gap = 2;
        int cell = Math.min((w - 30 - gap * (grid - 1)) / grid,
            (bottom - top - gap * (grid - 1)) / grid);
        cell = Math.max(cell, 1);
        int originX = (w - (cell * grid + gap * (grid - 1))) / 2;
        int originY = top + ((bottom - top) - (cell * grid + gap * (grid - 1))) / 2;

        for (int yy = 0; yy < grid; yy++) {
            for (int xx = 0; xx < grid; xx++) {
                int t = RbmkCoreData.at(yy, xx);
                if (t == 0) {
                    continue; // 石墨砌体('.') 不画
                }
                int color;
                switch (t) {
                    case 1:  color = 0xFF9AA4AE; break;  // F 燃料压力管（灰）
                    case 2:  color = 0xFF44C955; break;  // R 普通控制棒（绿）
                    case 3:  color = 0xFFE6CC3A; break;  // S 缩短吸收棒 UA（黄）
                    case 4:  color = 0xFFE04848; break;  // A 自动控制棒（红）
                    case 5:  color = 0xFF3D6BE0; break;  // L LAR 棒（蓝）
                    default: color = 0xFF343C46; break;
                }
                int px = originX + xx * (cell + gap);
                int py = originY + yy * (cell + gap);
                RbmkHoloDraw.drawRect(px, py, px + cell, py + cell, color);
            }
        }

        // 图例 + 计数
        font.drawString("石墨=深灰 燃料=灰F 控制=绿R 缩短=黄S 自动=红A LAR=蓝L"
            + "    [燃料 " + RbmkCoreData.getFuel() + " / 控制棒 " + RbmkCoreData.getRods()
            + " (R" + RbmkCoreData.getControlRods() + " S" + RbmkCoreData.getShortRods()
            + " A" + RbmkCoreData.getAutoRods() + " L" + RbmkCoreData.getLarRods()
            + ") / 石墨 " + RbmkCoreData.getGraphite() + "]", 20, bottom + 4, 0xFFAAAAAA);

        teardownDrawing();
    }
}