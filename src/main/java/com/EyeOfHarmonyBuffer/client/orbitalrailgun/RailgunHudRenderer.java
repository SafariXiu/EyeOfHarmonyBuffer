package com.EyeOfHarmonyBuffer.client.orbitalrailgun;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;

/**
 * 轨道炮瞄准 HUD（阶段1：2D 方案）。
 * 充能时隐藏原版 HUD，由本类绘制：旋转准星 + 充能进度条 + 状态提示。
 */
public final class RailgunHudRenderer {

    private RailgunHudRenderer() {}

    public static void render(Minecraft mc, RailgunClientState state, ScaledResolution resolution, boolean postGuiActive) {
        int w = resolution.getScaledWidth();
        int h = resolution.getScaledHeight();
        int cx = w / 2;
        int cy = h / 2;

        boolean ready = state.isReady();
        int color = ready ? 0xFF66FF66 : 0xFF9EEDED;

        // 旋转准星（四个方向的短线，随充能旋转展开）。
        // 后处理 GUI 激活时准星由 gui.fsh 全屏绘制（带旋转/扫描线），这里跳过避免重影
        if (!postGuiActive) {
            float progress = Math.min(1.0F, state.getChargeTicks() / 40.0F);
            float rot = progress * 0.35F;
            drawRotatingCrosshair(cx, cy, rot, color, ready);

            // 中心点
            drawRect(cx - 1, cy - 1, cx + 1, cy + 1, color);
        }

        // 充能进度条
        int barW = 64;
        int barH = 3;
        int barX = cx - barW / 2;
        int barY = cy + 18;
        drawRect(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, 0xAA000000);
        float fill = Math.min(1.0F, state.getChargeTicks() / (float) Math.max(1, state.getChargeWarmupTicks()));
        drawRect(barX, barY, barX + (int) (barW * fill), barY + barH, ready ? 0xFF66FF66 : 0xFF9EEDED);

        // 状态提示
        String text;
        if (ready && !state.hasTarget()) {
            text = TextLocalization.EOHB_OrbitalRailgun_AimAtBlock;
        } else if (ready) {
            text = TextLocalization.EOHB_OrbitalRailgun_Ready;
        } else {
            text = null;
        }
        if (text != null) {
            int textW = mc.fontRenderer.getStringWidth(text);
            mc.fontRenderer.drawStringWithShadow(text, cx - textW / 2, cy - 24, ready ? 0xFF66FF66 : 0xFFFF5555);
        }
    }

    private static void drawRotatingCrosshair(int cx, int cy, float rot, int color, boolean ready) {
        GL11.glPushMatrix();
        GL11.glTranslatef(cx, cy, 0.0F);
        GL11.glRotatef(rot * 45.0F, 0.0F, 0.0F, 1.0F);

        int inner = 8;
        int outer = ready ? 16 : 13;
        drawRect(-1, -outer, 1, -inner, color);
        drawRect(-1, inner, 1, outer, color);
        drawRect(-outer, -1, -inner, 1, color);
        drawRect(inner, -1, outer, 1, color);

        GL11.glPopMatrix();
    }

    private static void drawRect(int x1, int y1, int x2, int y2, int color) {
        Gui.drawRect(x1, y1, x2, y2, color);
    }
}
