package probe;

import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.RelaxedClimate;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.NoiseContinentGrid;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.PolarZone;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * P238：极地放大图（纯几何，零气候采样）+ 极地急流的**数值**验证。
 *
 * 放大图不需要气候场（海陆来自 isLand，极地带来自 PolarZone），所以是秒级的；
 * 数值验证只取 x ∈ [-50k, 50k]（1 个瓦片），避免为了一行数字建 10 个 237MB 窗口。
 */
public class P238 {

    static final int SEED = 1022228679;
    static File ROOT = new File("K:\\moder\\EyeOfHarmonyBuffer");

    public static void main(String[] args) throws Exception {
        RelaxedClimate.PREHEAT = false;
        File out = new File(ROOT, "build\\eoh_probe\\mtn\\maps");
        out.mkdirs();

        // ---- A) 极带放大图：x 跨 1M（1000km），z ∈ [380k, 620k]（240km），步长 250m ----
        int x0 = -500_000, x1 = 500_000, z0 = 380_000, z1 = 620_000, st = 250;
        int w = (x1 - x0) / st, h = (z1 - z0) / st;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int py = 0; py < h; py++) {
            int z = z0 + py * st;
            double raw = PolarZone.rawBand(z);
            for (int px = 0; px < w; px++) {
                int x = x0 + px * st;
                double be = PolarZone.band(x, z, SEED);
                // 唯一渲染判据（见 PolarZone.RENDER_RULE_VERSION）：底色 = **真实海陆**，
                // 极地结构只做半透明标记；浮冰带里"海上的浮冰"与"陆上的雪盖"必须能分辨。
                // 旧写法 if (isFloeOcean(be)) c = 淡青; 会把浮冰带里的真实陆地整片刷成海色。
                boolean land = NoiseContinentGrid.isLand(x, z, SEED);
                int base = land ? 0x3E9448 : 0x16488C;
                int c;
                switch (PolarZone.overlayCode(be, raw, land)) {
                    case PolarZone.OV_WALL:      c = mix(base, 0xFF28C8, 0.55); break;
                    case PolarZone.OV_FLOE_SEA:  c = mix(base, 0xAAD7F0, 0.75); break;
                    case PolarZone.OV_FLOE_LAND: c = mix(base, 0xEBF8F0, 0.45); break;
                    case PolarZone.OV_COLD:      c = mix(base, land ? 0x76A8D8 : 0x5A82BE,
                                                     PolarZone.coldWeight(be) * 0.55); break;
                    default:                     c = base;
                }
                img.setRGB(px, py, c);
            }
        }
        Graphics2D g = img.createGraphics();
        g.setFont(new Font("Monospaced", Font.BOLD, 22));
        g.setColor(Color.BLACK);
        g.drawString("x -500km..+500km   z 380km..620km   stride 250m", 14, 30);
        g.drawString("magenta=virtual wall   white=solid ice cap   cyan=floe band(ocean)", 14, 58);
        g.setStroke(new BasicStroke(2f));
        g.setColor(new Color(255, 255, 255, 120));
        g.drawLine(0, (500_000 - z0) / st, w - 1, (500_000 - z0) / st);
        g.dispose();
        File f1 = new File(out, "polar_zoom_1M.png");
        ImageIO.write(img, "png", f1);
        System.out.println("[P238] wrote " + f1.getCanonicalPath() + "  " + w + "x" + h);

        // ---- B) 冰缘蜿蜒的**数值**验证：直接量浮冰带外缘在 z 上的位置 ----
        System.out.println("[P238] 浮冰带外缘 z 位置（每隔 50km 取一列；理论 450000 + 平移量）");
        StringBuilder sb = new StringBuilder("[P238]   ");
        for (int x = -250_000; x <= 250_000; x += 50_000) {
            double lo = 0, hi = 500_000;
            for (int it = 0; it < 40; it++) {          // 二分找 be = FLOE_BAND 的 z
                double mid = 0.5 * (lo + hi);
                if (PolarZone.band(x, (int) mid, SEED) > PolarZone.FLOE_BAND) hi = mid; else lo = mid;
            }
            sb.append(String.format("x=%+4dk z=%6.0f  ", x / 1000, 0.5 * (lo + hi)));
        }
        System.out.println(sb.toString());

        // ---- C) 急流数值验证（只碰 1~2 个瓦片）----
        System.out.println("[P238] 生产洋流在极带上的 z 剖面（x=0）：bandD | u | v");
        System.out.println("[P238]   规定洋流已全部删除，所以 u/v 就是 BarotropicGyre 解出来的全部");
        for (int z = 370_000; z <= 600_000; z += 5_000) {
            double[] c = RelaxedClimate.sampleCurrent(0, z, SEED);
            double raw = PolarZone.rawBand(z);
            double jet = 0.0;
            if (raw < 0.74) continue;
            System.out.println(String.format("[P238]   bandD=%.2f z=%6d | u=%+.4f v=%+.4f | jet=%+.4f | u-jet=%+.4f",
                raw, z, c[0], c[1], jet, c[0] - jet));
        }
        System.out.println("P238_STATUS=DONE");
        System.exit(0);
    }

    static int mix(int a, int b, double t) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    static double cl(double v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }
}
