package probe;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.GyreRow;

import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Locale;

/**
 * P256：**海盆宽度是最大的合法杠杆吗？**（新增裁决项 D5）
 *
 * <p>§23.3 修正 beta 后，涡旋输运 10.4~17.0 Sv vs 真实副热带涡 30 Sv，差 1.8~2.9 倍。
 * 其中「海盆宽 3147 -> 5500~9100 km」是最合法的杠杆（地球大洋就是 5000~10000 km 宽），
 * 而 PLATE_CELL=600 km 是按**海岸形态**选的（P243），从未按**海盆宽度/输运**选过。
 *
 * <p>本探针扫 PLATE_CELL，同时量三件事，防止「为了输运把海岸搞没了」：
 * <ol>
 *   <li>海岸连贯性：陆地占比 + 每 10^4 km 的海岸穿越次数；</li>
 *   <li>海盆宽度分布：合格盆比例 / 中位盆宽 / 最大盆宽；</li>
 *   <li>输运与带内均速：psi_max（Sv）、带内均速@100km、x5 表层达标数。</li>
 * </ol>
 */
public class P256 {

    static final int SEED = 1022228679;
    /** ⚠ 口径修正（§559，模板 P442:47）：收 {@code long seed} 的入口传本值；收 {@code int worldSeedInt} 的入口仍传裸 {@code SEED}。本次未改任何判据/阈值/输出行。 */
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final int ZC = com.EyeOfHarmonyBuffer.sim.world.WorldContract.Z_CYCLE;
    static final double TAU0 = 0.0685;
    static final double H_TOTAL = 4000.0;
    static final double A_H = 1.9e4;
    static final double OMEGA = 7.2921e-5;
    static final double H = 5000.0;
    static final double[] BANDS = {0.15, 0.25, 0.40, 0.55, 0.70, 0.85};
    static final int NB = BANDS.length;
    static final int[] CELLS = {600_000, 1_000_000, 1_600_000, 2_400_000, 3_500_000};
    static final int NCOL = 8;
    static final int XSTEP = 1_500_000;
    static final double WREF = 100_000.0;
    static final double SURF = 5.0;

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p256_report.txt"), "UTF-8");
        say("P256：板块尺度 vs 海盆宽度 vs 输运（D5）");
        say(String.format(LF, "  h=%.0f m tau0=%.4f A_H=%.3e H=%.0f m  参考带宽=%.0f km  表层因子=%.1f",
            H, TAU0, A_H, H_TOTAL, WREF / 1000, SURF));
        say(String.format(LF, "  目标：真实副热带涡 30 Sv = %.0f m^2/s；带内均速@100km 真实 75 mm/s",
            30.0 * 1e6 / H_TOTAL));
        say("");

        say("A. 海岸连贯性（沿 6 条纬度行扫 x = 0..10^4 km，步长 5 km）");
        say(String.format(LF, "  %12s %12s %14s %14s", "PLATE_CELL", "陆地占比", "海岸穿越/1e4km", "最长连续水段 km"));
        for (int ci = 0; ci < CELLS.length; ci++) {
            int cell = CELLS[ci];
            double landFrac = 0, cross = 0, longest = 0; int cnt = 0;
            for (int bi = 0; bi < NB; bi++) {
                int z = (int) (BANDS[bi] * (ZC / 2));
                int nLand = 0, nCross = 0, run = 0, best = 0;
                boolean prev = PlateField.isLandWithCell(0, z, SD, cell);
                for (int xi = 0; xi < 2000; xi++) {
                    int x = xi * 5000;
                    boolean land = PlateField.isLandWithCell(x, z, SD, cell);
                    if (land) nLand++;
                    if (land != prev) nCross++;
                    if (!land) { run++; if (run > best) best = run; } else run = 0;
                    prev = land;
                }
                landFrac += nLand / 2000.0; cross += nCross; longest += best * 5.0; cnt++;
            }
            say(String.format(LF, "  %12d %11.1f%% %13.1f %13.0f",
                cell, landFrac / cnt * 100, cross / cnt, longest / cnt));
        }
        say("");

        say("B. 海盆宽度 + 输运（每条纬度行取 " + NCOL + " 个 x 位置，x 步长 " + (XSTEP / 1000) + " km）");
        say(String.format(LF, "  %12s %7s %10s %10s %12s %12s %12s %10s",
            "PLATE_CELL", "合格率", "盆宽中位", "盆宽最大", "psi_max中位", "=Sv中位", "均速@100km", "x5达标"));
        for (int ci = 0; ci < CELLS.length; ci++) {
            int cell = CELLS[ci];
            double[] widths = new double[NB * NCOL];
            double[] psis = new double[NB * NCOL];
            double[] means = new double[NB * NCOL];
            int c = 0, tot = 0;
            for (int bi = 0; bi < NB; bi++) {
                int z = (int) (BANDS[bi] * (ZC / 2));
                double latRad = BANDS[bi] * Math.PI / 2.0;
                double beta = 2.0 * OMEGA * Math.PI / ZC * Math.cos(latRad);
                GyreRow.Params pb = new GyreRow.Params();
                pb.h = H; pb.rhoH = 1025.0 * H_TOTAL; pb.aH = A_H; pb.beta = beta;
                double gate = Math.PI * pb.delta();
                GyreRow.BandedWind wind = new GyreRow.BandedWind(TAU0, ZC);
                for (int ix = 0; ix < NCOL; ix++) {
                    tot++;
                    GyreRow.Row r = GyreRow.solve(ix * XSTEP, z, SD, cell, wind, pb);
                    if (!r.valid) continue;
                    if ((r.eastX - r.westX) < 2 * gate) continue;
                    int n = r.n;
                    double L = r.eastX - r.westX;
                    double cum = 0, pm = 0;
                    for (int i = 0; i < n; i++) {
                        cum += r.v[i] * H;
                        if (Math.abs(cum) > Math.abs(pm)) pm = cum;
                    }
                    int q = Math.min(n, (int) (WREF / H));
                    double s = 0;
                    for (int i = 0; i < q; i++) s += r.v[i];
                    widths[c] = L / 1000.0;
                    psis[c] = pm;
                    means[c] = s / q * 1000;
                    c++;
                }
            }
            if (c == 0) { say(String.format(LF, "  %12d  无合格盆", cell)); continue; }
            double[] w2 = Arrays.copyOf(widths, c);
            Arrays.sort(w2);
            double[] p2 = Arrays.copyOf(psis, c);
            Arrays.sort(p2);
            double[] m2 = Arrays.copyOf(means, c);
            Arrays.sort(m2);
            int p50 = 0, p100 = 0;
            for (int i = 0; i < c; i++) {
                if (Math.abs(m2[i]) * SURF >= 50) p50++;
                if (Math.abs(m2[i]) * SURF >= 100) p100++;
            }
            say(String.format(LF, "  %12d %6d/%d %9.0f %10.0f %11.0f %11.1f %9.2f mm/s %4d/%d,%d/%d",
                cell, c, tot, w2[c / 2], w2[c - 1], p2[c / 2], p2[c / 2] * H_TOTAL / 1e6,
                Math.abs(m2[c / 2]), p50, c, p100, c));
        }
        say("");
        say("C. 说明");
        say("  - 合格率低不一定是坏事：大陆多 ⇒ 窄海峡多 ⇒ 那些行本来就不该有西边界流。");
        say("  - 但「盆宽中位」才是输运杠杆 —— psi_max = curl*L/(rho0*H*beta) 与 L 成正比。");
        say(String.format(LF, "  - 从 600 km 提到中位盆宽 5500 km 以上，才够补上 §23.3 的 1.8~2.9 倍缺口。"));
        rep.close();
    }

    static void say(String s) { System.out.println("[P256] " + s); rep.println("[P256] " + s); }
}
