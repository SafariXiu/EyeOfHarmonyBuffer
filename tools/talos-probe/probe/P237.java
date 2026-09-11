package probe;

import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.ClimateSample;
import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.GlobalClimate;
import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.RelaxedClimate;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.NoiseContinentGrid;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.PolarZone;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * P237：**极地三件套出图**（海陆 / 洋流 / 海温 三联，外加一张叠了结构的合成图）。
 *
 * 一次采样三张图：X 跨 1,000km（10 个气候瓦片）、Z 跨 1,000km（一个完整纬度周期，1 个瓦片行）
 * ⇒ 工作集 10 < CACHE_LIMIT(16)，不会把 237MB 的窗口缓存抖掉。
 *
 * 三联内容：
 *   A 海陆 + 极地结构：绿=陆、蓝=海；白=实心冰盖、淡青=浮冰带、洋红=虚拟墙、灰蓝=冷带爬升段
 *   B 洋流：颜色 = 流速（m/s），箭头 = 方向
 *   C 海温：[-1,1]
 *
 * 用法：runprobe4.bat P237   输出：build\eoh_probe\mtn\maps\polar_*.png
 */
public class P237 {

    static final int SEED = 1022228679;
    static final int X0 = -500_000, Z0 = 0;
    static final int SPAN = 1_000_000;
    static final int STRIDE = 1000;
    static final int N = SPAN / STRIDE;              // 1000

    static File ROOT = new File("K:\\moder\\EyeOfHarmonyBuffer");
    static File OUT;

    public static void main(String[] args) throws Exception {
        RelaxedClimate.PREHEAT = false;
        OUT = new File(ROOT, "build\\eoh_probe\\mtn\\maps");
        OUT.mkdirs();

        double[] cur = new double[N * N];          // 流速
        double[] cux = new double[N * N];
        double[] cuy = new double[N * N];
        double[] sst = new double[N * N];
        boolean[] isLand = new boolean[N * N];

        long t0 = System.nanoTime();
        for (int py = 0; py < N; py++) {
            int z = Z0 + py * STRIDE;
            for (int px = 0; px < N; px++) {
                int x = X0 + px * STRIDE;
                int i = py * N + px;
                ClimateSample s = GlobalClimate.sample(x, z, SEED);
                isLand[i] = s.isLand;
                cux[i] = s.currentX;
                cuy[i] = s.currentZ;
                cur[i] = Math.sqrt(s.currentX * s.currentX + s.currentZ * s.currentZ);
                sst[i] = s.seaTemperature;
            }
            if (py % 100 == 0) {
                System.out.println("[P237] row " + py + "/" + N + "  "
                    + ((System.nanoTime() - t0) / 1_000_000) + " ms  缓存 " + RelaxedClimate.cacheHitRate());
            }
        }
        System.out.println("[P237] 采样完成 " + ((System.nanoTime() - t0) / 1_000_000) + " ms");

        BufferedImage pLand = new BufferedImage(N, N, BufferedImage.TYPE_INT_RGB);
        BufferedImage pCur = new BufferedImage(N, N, BufferedImage.TYPE_INT_RGB);
        BufferedImage pSst = new BufferedImage(N, N, BufferedImage.TYPE_INT_RGB);
        for (int py = 0; py < N; py++) {
            for (int px = 0; px < N; px++) {
                int i = py * N + px;
                int x = X0 + px * STRIDE, z = Z0 + py * STRIDE;
                pLand.setRGB(px, py, landColor(x, z, isLand[i], cur[i]));
                pCur.setRGB(px, py, speedColor(cur[i]));
                pSst.setRGB(px, py, sstColor(sst[i]));
            }
        }
        overlay(pLand, cur, cux, cuy, false);
        overlay(pCur, cur, cux, cuy, true);
        overlay(pSst, cur, cux, cuy, false);
        write(pLand, "polar_A_land.png");
        write(pCur, "polar_B_current.png");
        write(pSst, "polar_C_sst.png");

        BufferedImage combo = new BufferedImage(N * 3 + 16, N, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = combo.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, combo.getWidth(), combo.getHeight());
        g.drawImage(pLand, 0, 0, null);
        g.drawImage(pCur, N + 8, 0, null);
        g.drawImage(pSst, 2 * N + 16, 0, null);
        g.setFont(new Font("SansSerif", Font.BOLD, 18));
        label(g, 12, "A land/sea + polar", Color.WHITE);
        label(g, N + 20, "B current (m/s)", Color.WHITE);
        label(g, 2 * N + 28, "C SST", Color.WHITE);
        g.dispose();
        write(combo, "polar_combo.png");

        int floe = 0, core = 0, wallN = 0, ocean = 0;
        for (int py = 0; py < N; py++) {
            int z = Z0 + py * STRIDE;
            for (int px = 0; px < N; px++) {
                int x = X0 + px * STRIDE;
                if (PolarZone.isWallCell(PolarZone.rawBand(z))) wallN++;
                double be = PolarZone.band(x, z, SEED);
                if (PolarZone.isCore(be)) core++;
                else if (PolarZone.isFloeOcean(be)) floe++;
                if (!isLand[py * N + px]) ocean++;
            }
        }
        double tot = (double) N * N;
        System.out.println(String.format("[P237] 海 %.1f%% | 冰盖 %.2f%% | 浮冰带 %.2f%% | 虚拟墙 %.2f%%",
            100 * ocean / tot, 100 * core / tot, 100 * floe / tot, 100 * wallN / tot));
        System.out.println("P237_STATUS=DONE");
        System.exit(0);
    }

    static void label(Graphics2D g, int x, String s, Color c) {
        g.setColor(Color.BLACK);
        g.drawString(s, x + 1, 25);
        g.drawString(s, x - 1, 25);
        g.setColor(c);
        g.drawString(s, x, 24);
    }

    static int landColor(int x, int z, boolean land, double speed) {
        double be = PolarZone.band(x, z, SEED);
        if (PolarZone.isWallCell(PolarZone.rawBand(z))) return rgb(255, 40, 200);
        if (PolarZone.isCore(be)) return rgb(252, 252, 255);
        if (PolarZone.isFloeOcean(be)) {
            double t = (be - PolarZone.FLOE_BAND) / (PolarZone.CORE_BAND - PolarZone.FLOE_BAND);
            return mix(rgb(148, 205, 235), rgb(226, 245, 255), clamp(t, 0, 1));
        }
        int base = land ? rgb(62, 148, 72) : rgb(22, 72, 140);
        double cw = PolarZone.coldWeight(be);
        return cw > 0 ? mix(base, rgb(118, 168, 216), cw) : base;
    }

    static int speedColor(double v) {
        double t = clamp(v / 0.45, 0, 1);
        if (t < 0.25) return mix(rgb(8, 20, 48), rgb(30, 90, 190), t / 0.25);
        if (t < 0.5) return mix(rgb(30, 90, 190), rgb(40, 190, 170), (t - 0.25) / 0.25);
        if (t < 0.75) return mix(rgb(40, 190, 170), rgb(240, 210, 60), (t - 0.5) / 0.25);
        return mix(rgb(240, 210, 60), rgb(250, 60, 40), (t - 0.75) / 0.25);
    }

    static int sstColor(double t) {
        double v = clamp(t, -1, 1);
        if (v < 0) return mix(rgb(10, 25, 90), rgb(235, 240, 250), 1 + v);
        return mix(rgb(235, 240, 250), rgb(200, 30, 30), v);
    }

    static void overlay(BufferedImage img, double[] cur, double[] ux, double[] uy, boolean arrows) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        if (arrows) {
            g.setStroke(new BasicStroke(1.4f));
            for (int py = 20; py < N; py += 40) {
                for (int px = 20; px < N; px += 40) {
                    int i = py * N + px;
                    double m = cur[i];
                    if (m < 0.02) continue;
                    double fx = ux[i] / m, fy = uy[i] / m;
                    int len = (int) (8 + 26 * clamp(m / 0.45, 0, 1));
                    int ex = px + (int) (fx * len), ey = py + (int) (fy * len);
                    g.setColor(m > 0.30 ? new Color(20, 10, 10) : new Color(240, 240, 240));
                    g.drawLine(px, py, ex, ey);
                    double ang = Math.atan2(ey - py, ex - px);
                    g.drawLine(ex, ey, ex - (int) (Math.cos(ang - 0.5) * 5), ey - (int) (Math.sin(ang - 0.5) * 5));
                    g.drawLine(ex, ey, ex - (int) (Math.cos(ang + 0.5) * 5), ey - (int) (Math.sin(ang + 0.5) * 5));
                }
            }
        }
        // 结构参考线：冰盖外缘 / 浮冰带外缘 / 虚拟墙中心
        g.setStroke(new BasicStroke(1.0f));
        for (int py = 0; py < N; py++) {
            int z = Z0 + py * STRIDE;
            double b = PolarZone.rawBand(z);
            if (PolarZone.isWallCell(b)) drawRowLine(g, py, new Color(255, 40, 200, 90));
            if (Math.abs(b - PolarZone.FLOE_BAND) < 0.0025) drawRowLine(g, py, new Color(255, 255, 255, 140));
            if (Math.abs(b - PolarZone.CORE_BAND) < 0.0025) drawRowLine(g, py, new Color(0, 0, 0, 160));
        }
        g.dispose();
    }

    static void drawRowLine(Graphics2D g, int py, Color c) {
        g.setColor(c);
        g.drawLine(0, py, N - 1, py);
    }

    static void write(BufferedImage img, String name) throws Exception {
        File f = new File(OUT, name);
        ImageIO.write(img, "png", f);
        System.out.println("[P237] wrote " + f.getCanonicalPath());
    }

    static int rgb(int r, int gg, int b) { return (r << 16) | (gg << 8) | b; }

    static int mix(int a, int b, double t) {
        double u = clamp(t, 0, 1);
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return rgb((int) (ar + (br - ar) * u), (int) (ag + (bg - ag) * u), (int) (ab + (bb - ab) * u));
    }

    static double clamp(double v, double lo, double hi) { return v < lo ? lo : (v > hi ? hi : v); }
}
