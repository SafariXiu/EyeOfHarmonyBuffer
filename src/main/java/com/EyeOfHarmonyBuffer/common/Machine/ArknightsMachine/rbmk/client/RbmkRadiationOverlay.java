package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.rbmk.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import java.util.Random;

/**
 * 辐射视觉效果（GUI overlay，vanilla 路径，Angelica 兼容）：
 * - 灰罩：全屏半透明灰色（等级越高越深）
 * - 雪花：屏幕边缘环形区域动态噪点，"从外到内"由边缘带宽控制
 * 等级 4 起有雪花（4=边缘一点，7=从外到内占 50% 屏）。
 * PoC：currentLevel 用 /rbmkrad <1-7> 临时设；辐射系统接入后改为读玩家实时等级。
 */
@SideOnly(Side.CLIENT)
public class RbmkRadiationOverlay {

    /** 临时：当前玩家辐射等级（PoC 命令设置；接入系统后从辐射源实时算） */
    private static int currentLevel = 0;

    public static void setLevel(int level) {
        currentLevel = level;
    }

    public static int getLevel() {
        return currentLevel;
    }

    // 等级 → 视觉参数（集中可调）
    // 注意：6 级使用原 7 级效果；7 级更强（覆盖率/灰度再拉高）
    private static double coverageFor(int level) {
        switch (level) {
            case 4: return 0.06;
            case 5: return 0.18;
            case 6: return 0.50;
            case 7: return 0.62;
            default: return 0.0;
        }
    }

    private static float grayFor(int level) {
        switch (level) {
            case 4: return 0.12f;
            case 5: return 0.25f;
            case 6: return 0.65f;
            case 7: return 0.78f;
            default: return 0.0f;
        }
    }

    private static float densityFor(int level) {
        switch (level) {
            case 4: return 0.30f;
            case 5: return 0.55f;
            case 6: return 1.00f;
            case 7: return 1.00f;
            default: return 0.0f;
        }
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        if (currentLevel < 4) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            return;
        }
        int sw = event.resolution.getScaledWidth();
        int sh = event.resolution.getScaledHeight();

        // 灰罩
        float gray = grayFor(currentLevel);
        if (gray > 0.001f) {
            Gui.drawRect(0, 0, sw, sh, argb(gray));
        }

        // 边缘雪花
        double cov = coverageFor(currentLevel);
        float density = densityFor(currentLevel);
        if (cov > 0.001 && density > 0.001) {
            drawStaticNoise(sw, sh, cov, density);
        }
    }

    /** 灰罩 ARGB（半透明白/浅灰） */
    private static int argb(float alpha) {
        int a = (int) (alpha * 255);
        int c = 168; // 浅灰底
        return (a << 24) | (c << 16) | (c << 8) | c;
    }

    /**
     * 在椭圆环内撒噪点：内椭圆之外（且屏幕内）为雪花区。
     * 内椭圆半轴 = 主半轴 × coverage（左右长、上下短，贴合屏幕比例）。
     */
    private static void drawStaticNoise(int sw, int sh, double coverage, float density) {
        double cx = sw / 2.0;
        double cy = sh / 2.0;
        // 内椭圆半轴 = 主半轴 × (1 − coverage)：coverage 越大（等级越高）内椭圆越小 → 雪花区（外圈）越大
        double a = sw / 2.0 * (1.0 - coverage);   // 内椭圆水平半轴
        double b = sh / 2.0 * (1.0 - coverage);   // 内椭圆垂直半轴
        a = Math.max(a, 1.0);
        b = Math.max(b, 1.0);

        // 边缘（雪花）面积 = 全屏 − 内椭圆面积
        long total = (long) sw * sh;
        double innerEllipse = Math.PI * a * b;
        long bandArea = Math.max(1, (long) (total - innerEllipse));
        // 羽化：边界处概率从 0 平滑升到 1，消除切割感
        double fade = 0.30; // 羽化带宽度（归一化椭圆距离）
        int pointCount = (int) (bandArea * 0.06f * density * 1.8); // 多点补偿羽化丢弃
        pointCount = Math.max(0, Math.min(pointCount, 20000));
        Random rnd = new Random();
        for (int i = 0; i < pointCount; i++) {
            int x = rnd.nextInt(sw);
            int y = rnd.nextInt(sh);
            // 到内椭圆边界的归一化距离：1.0 = 边界，>1 向外
            double nx = (x - cx) / a;
            double ny = (y - cy) / b;
            double d = Math.sqrt(nx * nx + ny * ny);
            if (d <= 1.0) {
                continue; // 内椭圆内 = 中心干净区
            }
            // 羽化：d ∈ (1, 1+fade) 概率线性升，之外全画
            double p = Math.min(1.0, (d - 1.0) / fade);
            if (rnd.nextDouble() > p) {
                continue;
            }
            int v = 150 + rnd.nextInt(106); // 150~255 灰白
            int alpha = 100 + rnd.nextInt(130);
            int col = (alpha << 24) | (v << 16) | (v << 8) | v;
            Gui.drawRect(x, y, x + 2, y + 2, col);
        }
    }
}
