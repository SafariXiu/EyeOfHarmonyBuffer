package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.CoastalLayer;
import com.EyeOfHarmonyBuffer.sim.ocean.GyreRow;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P264：**那条链的端到端验证**（landScore -> p' 连续 -> 沿岸风 -> 沿岸层 -> 东边界流）。
 *
 * <p>对照量：
 * <ol>
 *   <li>p' 的跨岸梯度最大值（阶梯 -> 平滑）；</li>
 *   <li>地面风速最大值（原来撞 25 m/s 上限）；</li>
 *   <li>**东边界附近的沿岸风应力 tau_z**（原来 0.00049 Pa）；</li>
 *   <li>沿岸层给出的东带速度（目标 >= 30 mm/s）。</li>
 * </ol>
 */
public class P264 {

    static final int SEED = 1022228679;
    /** ⚠ 口径修正（§559，模板 P442:47）：收 {@code long seed} 的入口传本值；收 {@code int worldSeedInt} 的入口仍传裸 {@code SEED}。本次未改任何判据/阈值/输出行。 */
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final int ZC = WorldContract.Z_CYCLE;
    static final double TAU0 = 0.0685;
    static final double H_TOTAL = 4000.0;
    static final double A_H = 1.9e4;
    static final double GRID = 5000.0;
    static final double W = 100_000.0;
    static final double[] BANDS = {0.15, 0.25, 0.40, 0.55, 0.70, 0.85};
    static final int NB = BANDS.length;

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p264_report.txt"), "UTF-8");
        say("P264：landScore 那条链的端到端验证");
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        say(String.format(LF, "  PLATE_CELL=%d  COAST_BLEND=%d km  tau0=%.4f", cell, PlateField.COAST_BLEND / 1000, TAU0));
        say("");

        // ---------- A. landScore 自检 ----------
        say("A. landScore 自检（应当连续、有界、海岸处约 0）");
        int z0 = (int) (0.25 * (ZC / 2));
        int nS = 0, nL = 0, nC = 0; double smin = 9, smax = -9;
        for (int k = 0; k < 4000; k++) {
            int x = k * 3000;
            double s = PlateField.landScoreWithCell(x, z0, SD, cell);
            if (s < smin) smin = s;
            if (s > smax) smax = s;
            if (s > 0.6) nL++; else if (s < -0.6) nS++; else nC++;
        }
        say(String.format(LF, "   沿 z=%d 扫 4000 点：landScore 范围 [%.3f, %.3f]；内陆 %d / 过渡带 %d / 深海 %d",
            z0, smin, smax, nL, nC, nS));
        say(String.format(LF, "   过渡带占比 %.1f%%（应该与「海岸线长度 x 800 km」量级一致）", nC * 100.0 / 4000));
        say("");

        // ---------- B. p' 连续性与风速 ----------
        say("B. p' 的跨岸梯度 与 地面风速（对照：改之前 |grad p'| 最大 0.086 Pa/m、风速撞 25 m/s 上限）");
        double maxG = 0, maxSp = 0; int n = 0; double sumG = 0;
        for (int r = 0; r < 60; r++) {
            int z = (int) ((r + 0.5) / 60.0 * ZC);
            for (int c = 0; c < 60; c++) {
                int x = c * 200_000;
                double g = Math.abs(Atmosphere.pressureAnomaly(x + 10_000, z, SD, cell, thS)
                                  - Atmosphere.pressureAnomaly(x - 10_000, z, SD, cell, thS)) / 20_000.0;
                if (g > maxG) maxG = g;
                sumG += g; n++;
                double[] uv = Atmosphere.windAt(x, z, SD, cell, thS, 10_000);
                double sp = Math.hypot(uv[0], uv[1]);
                if (sp > maxSp) maxSp = sp;
            }
        }
        say(String.format(LF, "   |grad p'| 最大 %.5f Pa/m（改前 0.086）  平均 %.6f", maxG, sumG / n));
        say(String.format(LF, "   地面风速最大 %.2f m/s（改前 25.00 = 撞上限）", maxSp));
        say("");

        // ---------- C. 东边界附近的沿岸风 ----------
        say("C. 东边界附近的沿岸风应力 tau_z（改前全球平均 |tau_z| = 0.00049 Pa）");
        GyreRow.BandedWind wind = new GyreRow.BandedWind(TAU0, ZC);
        int NM = 80;
        double ds = (double) ZC / NM;
        double[] tauZ = new double[NM];
        say(String.format(LF, "   %-8s %6s %14s %14s %14s", "纬度", "n", "tau_z夏至", "tau_z冬至", "|tau|夏至"));
        for (int m = 0; m < NM; m++) {
            int z = (int) ((m + 0.5) / NM * ZC);
            double s1 = 0, s2 = 0, sa = 0; int c = 0;
            for (int ix = 0; ix < 5; ix++) {
                int x0 = (ix * 2_100_000 + m * 137_000) % 11_000_000;
                GyreRow.Params p = new GyreRow.Params();
                p.h = GRID; p.rhoH = 1025.0 * H_TOTAL; p.aH = A_H; p.zCycle = ZC;
                GyreRow.Row r = GyreRow.solve(x0, z, SD, cell, wind, p);
                if (!r.valid) continue;
                if ((r.eastX - r.westX) < 2 * Math.PI * p.deltaAt(z)) continue;
                for (int k = 1; k <= 3; k++) {
                    double[] a = Atmosphere.windStress(r.eastX - k * 150_000, z, SD, cell, thS, 10_000);
                    double[] b = Atmosphere.windStress(r.eastX - k * 150_000, z, SD, cell, thW, 10_000);
                    s1 += a[1]; s2 += b[1]; sa += Math.hypot(a[0], a[1]); c++;
                }
            }
            tauZ[m] = c > 0 ? s1 / c : 0;
            if (m % 8 == 0 && c > 0) {
                say(String.format(LF, "   %-8.1f %6d %14.5f %14.5f %14.5f",
                    Math.toDegrees(WorldContract.latOf(z, ZC)), c, s1 / c, s2 / c, sa / c));
            }
        }
        double mA = 0; for (double v : tauZ) mA += Math.abs(v);
        say(String.format(LF, "   ⇒ 全球沿岸 |tau_z| 平均 = %.5f Pa（改前 0.00049，理想核对用 0.1）", mA / NM));
        say("");

        // ---------- D. 沿岸层结果 ----------
        say("D. 沿岸层贡献（h_c 由生产 tau_z 沿岸累积）");
        double[] hc = CoastalLayer.steadyPeriodic(tauZ, ds);
        double hmax = 0; for (double v : hc) if (Math.abs(v) > Math.abs(hmax)) hmax = v;
        say(String.format(LF, "   h_c 极值 %.2f m（理想风 -98.75 m）", hmax));
        say(String.format(LF, "   %-8s %12s %14s %16s %14s", "纬度", "h_c m", "沿岸层mm/s", "斯维尔德鲁普mm/s", "合计mm/s"));
        for (int bi = 0; bi < NB; bi++) {
            int z = (int) (BANDS[bi] * (ZC / 2));
            double lat = WorldContract.latOf(z, ZC);
            double f = WorldContract.coriolis(lat);
            double hcv = hc[Math.min(NM - 1, (int) ((double) z / ZC * NM))];
            double cb = CoastalLayer.eastBandContribution(hcv, f, W) * 1000;
            double sv = wind.at(0, z) / (1025.0 * H_TOTAL * WorldContract.betaForLatitude(lat, ZC)) * 1000;
            say(String.format(LF, "   %-8.1f %12.2f %14.3f %16.4f %14.4f",
                Math.toDegrees(lat), hcv, cb, sv, cb + sv));
        }
        say("");

        // ---------- E. 成本 ----------
        say("E. 成本（landScore 是 25 次 O(1) 查询，必须确认预算）");
        long t0 = System.nanoTime();
        int reps = 3, mm = 0;
        for (int k = 0; k < reps; k++)
            for (int r = 0; r < 50; r++)
                for (int c = 0; c < 50; c++) {
                    double[] uv = Atmosphere.windAt(c * 2000, r * 2000, SD, cell, thS, 2000);
                    mm += (uv[0] > 0 ? 1 : 0);
                }
        say(String.format(LF, "   风场 100 km 瓦片（dx=2km、2500 点、含 4 次 p' 差分）= %.0f ms（预算 20000）",
            (System.nanoTime() - t0) / 1e6 / reps));
        say(String.format(LF, "   单独 landScore 调用 1e6 次 = %.0f ms", landCost()));
        say(String.format(LF, "   （校验和 %d）", mm));
        rep.close();
    }

    static double landCost() {
        long t = System.nanoTime();
        double s = 0;
        for (int k = 0; k < 1_000_000; k++) s += PlateField.landScoreWithCell(k * 137, k * 911, SD, PlateField.PLATE_CELL);
        return (System.nanoTime() - t) / 1e6 * (s == 0 ? 1 : 1);
    }

    static void say(String s) { System.out.println("[P264] " + s); rep.println("[P264] " + s); }
}
