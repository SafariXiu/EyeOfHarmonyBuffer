package probe;

import com.EyeOfHarmonyBuffer.sim.ocean.OceanField;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanWiring;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * P480：**A2 量级不足的物理归因** —— 是求解器错，还是风/几何给的 psi 本来就小？
 *
 * <p>背景（D63 / §184）：A2 在正确的副热带判据域上是 **48.50 mm/s**，锚是 **75 mm/s**（差 1.55 倍）。
 * 用户裁决「**按照物理正确来做**」⇒ **不许调旋钮去凑数**，先判短缺出在哪一层。
 *
 * <h3>物理链条（Sverdrup 内区 + Munk 西边界层）</h3>
 * <pre>
 *   psi_max  ~ curl_mean * W^2 / (8 * rhoH * beta)      （内区 Sverdrup 流函数峰值）
 *   delta    = (A_H / beta)^(1/3)                        （Munk 层厚）
 *   v_band   ~ psi_max / delta                           （西边界层内的速度尺度）
 * </pre>
 *
 * <p>三条判据（跑之前写死）：
 * <ol>
 *   <li><b>自洽性</b>：v_band * delta / psi_max 在各海盆之间应当大致恒定（O(1)）。
 *       若它随盆宽系统漂移，说明求解器的标度不对；若恒定，说明求解器是对的；</li>
 *   <li><b>标度</b>：v_band 对 psi_max/delta 做回归，斜率应 ~O(1)；</li>
 *   <li><b>归因</b>：把 75 mm/s 按 v ∝ W^2 归到本世界的盆宽上，看是否达标 ——
 *       若不达标，短缺在**风（curl）**这一层，不在海洋求解器。</li>
 * </ol>
 */
public class P480 {

    static final int SEED = 1022228679;
    static final int ZC = WorldContract.Z_CYCLE;
    static final int SCAN = 500_000;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static final double RHO_H = 1025.0 * 4000.0;

    static void say(String s) { rep.println("[P480] " + s); rep.flush(); System.out.println("[P480] " + s); System.out.flush(); }

    static double median(double[] v, int n) {
        if (n <= 0) return Double.NaN;
        double[] a = Arrays.copyOf(v, n); Arrays.sort(a);
        return (n % 2 == 1) ? a[n / 2] : 0.5 * (a[n / 2 - 1] + a[n / 2]);
    }
    static double minOf(double[] v, int n) { double m = Double.MAX_VALUE; for (int i = 0; i < n; i++) m = Math.min(m, v[i]); return n == 0 ? Double.NaN : m; }
    static double maxOf(double[] v, int n) { double m = -Double.MAX_VALUE; for (int i = 0; i < n; i++) m = Math.max(m, v[i]); return n == 0 ? Double.NaN : m; }

    static List<int[]> basinsAt(int z) {
        LinkedHashMap<Long, int[]> m = new LinkedHashMap<>();
        for (int x = -WorldContract.MAX_D; x <= WorldContract.MAX_D; x += SCAN) {
            int[] sp = OceanField.spanOf(x, z, SEED);
            if (sp == null) continue;
            long k = ((long) sp[1] << 32) ^ (sp[2] & 0xFFFFFFFFL);
            if (!m.containsKey(k)) m.put(k, new int[]{sp[1], sp[2]});
        }
        return new ArrayList<>(m.values());
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p480_report.txt"), "UTF-8");
        OceanWiring.onWorld(SEED);
        say("P480：A2 量级不足的物理归因（Sverdrup/Munk 标度核对）");
        say(String.format(LF, "  接线：installedSeed=%d  BAND_W=%.0f km  H_TOTAL=%.0f m  A_H=%.1e",
            OceanField.installedSeed(), OceanField.BAND_W / 1000, OceanField.H_TOTAL, OceanField.A_H));
        say("  判据（跑之前写死）：(1) v_band*delta/psi_max 在各盆之间大致恒定；");
        say("                    (2) v_band 对 psi_max/delta 的回归斜率 ~O(1)；");
        say("                    (3) 按 W^2 归一后是否达标。");
        say("");
        int[] lats = {15, 25, 35, -15, -25, -35};
        say(String.format(LF, "  %-6s %-9s %-8s %-11s %-11s %-10s %-10s %-9s %-10s",
            "lat", "盆西km", "宽km", "v_band mm/s", "psi_max", "delta km", "psi/d mm/s", "v*d/psi", "curl Pa/m"));
        double[] xs = new double[128], ys = new double[128], rat = new double[128], vv = new double[128], ww = new double[128];
        int n = 0;
        long t0 = System.nanoTime();
        for (int latDeg : lats) {
            int z = WorldContract.zOfLat(latDeg);   // ★ §727 两倍纬度修正
            double lat = WorldContract.latOf(z, ZC);
            double beta = WorldContract.betaForLatitude(lat, ZC);
            double delta = Math.cbrt(OceanField.A_H / beta);
            for (int[] b : basinsAt(z)) {
                int wx = b[0], ex = b[1];
                double W = ex - wx;
                if (W < 200_000) continue;
                double[] bm = OceanField.bandMeansAt((wx + ex) / 2, z, SEED);
                if (bm == null) continue;
                double vBand = Math.abs(bm[0]) * 1000.0;
                double psi = Math.abs(bm[2]);
                double pred = psi / delta * 1000.0;
                double curl = psi * 8.0 * RHO_H * beta / (W * W);
                say(String.format(LF, "  %-6d %-9d %-8.0f %-11.2f %-11.3e %-10.1f %-10.2f %-9.3f %-10.2e",
                    latDeg, wx / 1000, W / 1000, vBand, psi, delta / 1000.0, pred, vBand / Math.max(1e-9, pred), curl));
                if (n < 128) { xs[n] = pred; ys[n] = vBand; rat[n] = vBand / Math.max(1e-9, pred); vv[n] = vBand; ww[n] = W; n++; }
            }
        }
        say("");
        say(String.format(LF, "  ⇒ %d 个副热带海盆，耗时 %.1f s", n, (System.nanoTime() - t0) / 1e9));
        say(String.format(LF, "  (1) v_band*delta/psi_max：中位 %.3f（范围 %.3f ~ %.3f）", median(rat, n), minOf(rat, n), maxOf(rat, n)));
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (int i = 0; i < n; i++) { sx += xs[i]; sy += ys[i]; sxx += xs[i]*xs[i]; sxy += xs[i]*ys[i]; }
        double a = (n*sxy - sx*sy) / Math.max(1e-12, n*sxx - sx*sx), bb = (sy - a*sx) / n;
        say(String.format(LF, "  (2) 回归 v_band = %.3f * (psi_max/delta) + %.2f", a, bb));
        say(String.format(LF, "      西带 |v| 中位 %.2f mm/s；盆宽中位 %.0f km", median(vv, n), median(ww, n)));
        double wRef = 6_000_000.0;
        double wMed = median(ww, n);
        double anchorScaled = 75.0 * (wMed * wMed) / (wRef * wRef);
        say(String.format(LF, "  (3) 锚 75 mm/s 按 v ∝ W^2 归到盆宽中位 %.0f km（参照 %.0f km）⇒ %.2f mm/s",
            wMed / 1000, wRef / 1000, anchorScaled));
        say(String.format(LF, "      模型西带 |v| 中位 %.2f mm/s vs 归一后的锚 %.2f mm/s ⇒ %s",
            median(vv, n), anchorScaled, median(vv, n) >= anchorScaled ? "达标" : String.format(LF, "**差 %.2fx**", anchorScaled / median(vv, n))));
        say(String.format(LF, "  GATE_A2_ANCHOR_SCALED=%s", median(vv, n) >= anchorScaled ? "PASS" : "FAIL"));
        say("  GATE_A2_SHAPE=REVIEW");
        say("");
        say(String.format(LF, "  reentryBlocked = %d（必须 0）", OceanField.reentryBlocked));
        say("⚠ 记账：本探针只读 OceanField 的公开接口，未改任何生产常量。");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
