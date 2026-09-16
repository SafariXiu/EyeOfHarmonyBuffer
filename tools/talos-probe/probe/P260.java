package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables;
import com.EyeOfHarmonyBuffer.sim.export.MapWriter;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P260：**M3 切片 2 —— 风场**（目标 B3 季风反转；同时产出 M2 需要的风应力旋度剖面）。
 *
 * <p>风 = Ekman-Rayleigh(f, gamma(海/陆), grad p_sl) + U_zm(phi - Delta_phi)。
 * 赤道与中纬同一个公式，季风反转必须是**自动**发生的（没有任何 if 分支）。
 */
public class P260 {

    static final int SEED = 1022228679;
    static final int XSTEP = 20_000, ZSTEP = 200_000;   // z 覆盖一整个气候周期 0~20M
    static final int NX = 551, NZ = 101;
    static final int GRAD = 10_000;

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static final File MAPDIR = new File(ROOT, "build/eoh_probe/mtn/map");
    static PrintStream rep;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p260_report.txt"), "UTF-8");
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0);
        double thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        say("P260：M3 切片 2 —— 风场 + 季风反转 + 风应力旋度");
        say(String.format(LF, "  PLATE_CELL=%d  网格 %dx%d dx=%dkm dz=%dkm  差分步长=%dkm  赤道/中纬同一公式",
            cell, NX, NZ, XSTEP / 1000, ZSTEP / 1000, GRAD / 1000));
        say("");

        // ---------------- A. 相位与气压带迁移 ----------------
        say("A. 季节相位与气压带迁移（约定：Theta=0 是北半球夏至）");
        say(String.format(LF, "   直射点纬度：夏至 %+.2f 度 / 冬至 %+.2f 度 / 春分 %+.2f 度",
            Math.toDegrees(Atmosphere.subsolarLat(thS)),
            Math.toDegrees(Atmosphere.subsolarLat(thW)),
            Math.toDegrees(Atmosphere.subsolarLat(Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 4.0)))));
        for (int k = 0; k < 2; k++) {
            double th = k == 0 ? thS : thW;
            double bestN = 0, bestPN = 1e9, bestS = 0, bestPS = 1e9;
            for (int zi = 0; zi < 180; zi++) {
                double latDeg = -89.5 + zi;
                double p = ZonalTables.pRef(latDeg - Math.toDegrees(Atmosphere.subsolarLat(th)));
                if (latDeg > 0 && p < bestPN) { bestPN = p; bestN = latDeg; }
                if (latDeg < 0 && p < bestPS) { bestPS = p; bestS = latDeg; }
            }
            say(String.format(LF, "   %s：副极地低压 北半球 %+.1f 度 / 南半球 %+.1f 度（%.1f hPa）",
                k == 0 ? "夏至" : "冬至", bestN, bestS, bestPN / 100));
        }
        say("");

        // ---------------- B. 风场 ----------------
        say("B. 风场");
        double[][][] store = new double[2][][];
        double[][] landStore = new double[1][];
        for (int k = 0; k < 2; k++) {
            double th = k == 0 ? thS : thW;
            double[] sp = new double[NX * NZ], uu = new double[NX * NZ], vv = new double[NX * NZ], mask = new double[NX * NZ];
            long t0 = System.nanoTime();
            for (int r = 0; r < NZ; r++) {
                int z = r * ZSTEP;
                for (int c = 0; c < NX; c++) {
                    int x = c * XSTEP;
                    int i = r * NX + c;
                    double[] uv = Atmosphere.windAt(x, z, SEED, cell, th, GRAD);
                    uu[i] = uv[0]; vv[i] = uv[1];
                    sp[i] = Math.hypot(uv[0], uv[1]);
                    mask[i] = PlateField.isLandWithCell(x, z, SEED, cell) ? 1.0 : 0.0;
                }
            }
            double ms = (System.nanoTime() - t0) / 1e6;
            store[k] = new double[][]{uu, vv};
            landStore[0] = mask;
            String tag = k == 0 ? "summer" : "winter";
            MapWriter.writePngWithVectors(new File(MAPDIR, "p260_wind_" + tag + ".png"), NX, NZ, sp, 0, 20,
                MapWriter.THERMAL, uu, vv, 10, 0.9);
            say(String.format(LF, "   %-7s |U| min %.2f / max %.2f / mean %.2f m/s   渲染 %.0f ms   -> p260_wind_%s.png",
                tag, min(sp), max(sp), mean(sp), ms, tag));
        }
        say("");

        // ---------------- C. 风带核对（只取海洋点，隔离纬向表） ----------------
        say("C. 风带核对：每个纬度上海洋点的平均纬向风 vs 外生表 U_zm");
        say(String.format(LF, "   %-8s %10s %14s %14s %10s", "纬度", "U_zm表", "实测u夏至", "实测u冬至", "方向"));
        double[] probeLats = {0, 10, 20, 30, 40, 50, 60, 70, 80};
        for (double latDeg : probeLats) {
            int z = (int) Math.round(Math.abs(latDeg) / 90.0 * WorldContract.MAX_D);
            double sumS = 0, sumW = 0; int n = 0;
            for (int c = 0; c < NX; c++) {
                int x = c * XSTEP;
                if (PlateField.isLandWithCell(x, z, SEED, cell)) continue;
                sumS += store[0][0][(z / ZSTEP) * NX + c];
                sumW += store[1][0][(z / ZSTEP) * NX + c];
                n++;
            }
            double ms2 = n > 0 ? sumS / n : Double.NaN, mw = n > 0 ? sumW / n : Double.NaN;
            say(String.format(LF, "   %-8.0f %9.1f %11.2f %14.2f %10s", latDeg, ZonalTables.uZm(latDeg), ms2, mw,
                Math.abs(ms2) < 1.0 ? "（过渡带）" : (ms2 > 0 ? "西风" : "东风")));
        }
        say("");

        // ---------------- D. 季风反转（B3 的判据） ----------------
        say("D. 季风反转：同一地点 夏至风 · 冬至风 < 0 即「反相」");
        say(String.format(LF, "   %-14s %8s %12s %14s %14s", "纬度带", "点数", "反相比例", "平均|dU| m/s", "海陆"));
        for (int bandIdx = 0; bandIdx < 3; bandIdx++) {
            int z0, z1; String name;
            int md = WorldContract.MAX_D;
            if (bandIdx == 0) { z0 = 0; z1 = md / 9; name = "0~10度(赤道)"; }
            else if (bandIdx == 1) { z0 = 2 * md / 9; z1 = 4 * md / 9; name = "20~40度(副热带)"; }
            else { z0 = 5 * md / 9; z1 = 7 * md / 9; name = "50~70度(中高纬)"; }
            for (int wantLand = 0; wantLand < 2; wantLand++) {
                int n = 0, rev = 0; double dsum = 0;
                for (int r = z0 / ZSTEP; r <= z1 / ZSTEP && r < NZ; r++) {
                    int z = r * ZSTEP;
                    for (int c = 0; c < NX; c++) {
                        int x = c * XSTEP;
                        boolean land = PlateField.isLandWithCell(x, z, SEED, cell);
                        if ((wantLand == 1) != land) continue;
                        int i = r * NX + c;
                        double us = store[0][0][i], vs = store[0][1][i];
                        double uw = store[1][0][i], vw = store[1][1][i];
                        double dot = us * uw + vs * vw;
                        if (dot < 0) rev++;
                        dsum += Math.hypot(us - uw, vs - vw);
                        n++;
                    }
                }
                if (n == 0) { say(String.format(LF, "   %-14s %8d %12s", name, 0, "-")); continue; }
                say(String.format(LF, "   %-14s %8d %11.1f%% %14.2f %14s",
                    name, n, rev * 100.0 / n, dsum / n, wantLand == 1 ? "陆地" : "海洋"));
            }
        }
        say("");

        // ---------------- E. 风应力与旋度（M2 的接口） ----------------
        say("E. 风应力旋度剖面（M2 的 BandedWind 占位应当被它替换）");
        int CXN = 101, CZN = 51;
        double[] curlAcc = new double[CZN];
        int[] curlN = new int[CZN];
        double[] spdAcc = new double[CZN];
        long t1 = System.nanoTime();
        for (int r = 0; r < CZN; r++) {
            int z = r * (WorldContract.MAX_D / (CZN - 1));
            for (int c = 0; c < CXN; c++) {
                int x = c * 20_000;
                double[] tE = Atmosphere.windStress(x + GRAD, z, SEED, cell, thS, GRAD);
                double[] tW2 = Atmosphere.windStress(x - GRAD, z, SEED, cell, thS, GRAD);
                double[] tN = Atmosphere.windStress(x, z + GRAD, SEED, cell, thS, GRAD);
                double[] tS = Atmosphere.windStress(x, z - GRAD, SEED, cell, thS, GRAD);
                double curl = (tE[1] - tW2[1]) / (2.0 * GRAD) - (tN[0] - tS[0]) / (2.0 * GRAD);
                double[] t0 = Atmosphere.windStress(x, z, SEED, cell, thS, GRAD);
                curlAcc[r] += curl; spdAcc[r] += Math.hypot(t0[0], t0[1]); curlN[r]++;
            }
        }
        double tc = (System.nanoTime() - t1) / 1e6;
        say(String.format(LF, "   %-8s %14s %14s %14s", "纬度", "tau模Pa", "逐点curl均值", "连贯curl(块均)"));
        for (int r = 0; r < CZN; r += 5) {
            say(String.format(LF, "   %-8.0f %14.4f %14.3e %14s", Math.toDegrees(WorldContract.latOf(r * (WorldContract.MAX_D / (CZN - 1)))),
                spdAcc[r] / curlN[r], curlAcc[r] / curlN[r], "见 TSV"));
        }
        say(String.format(LF, "   （区域 x=0..2000km, z=0..极点；%.0f ms）", tc));
        say(String.format(LF, "   参考：BandedWind 占位在 tau0=0.0685 下给出 |curl| 峰值 %.3e Pa/m",
            3.0 * Math.PI * 0.0685 / WorldContract.MAX_D));
        say("");

        // ---------------- F. 成本与确定性 ----------------
        say("F. 成本与确定性");
        long t2 = System.nanoTime();
        int reps = 5, m = 0;
        for (int k = 0; k < reps; k++) {
            for (int r = 0; r < 50; r++) {
                for (int c = 0; c < 50; c++) {
                    double[] uv = Atmosphere.windAt(c * 2000, r * 2000, SEED, cell, thS, 2000);
                    m += (uv[0] > 0 ? 1 : 0);
                }
            }
        }
        double perTile = (System.nanoTime() - t2) / 1e6 / reps;
        say(String.format(LF, "   风场：dx=2km 的 100 km 瓦片（2500 点，含 4 次 p_sl 差分）= %.0f ms（预算 20000 ms）", perTile));
        double[] a1 = Atmosphere.windAt(123456, 234567, SEED, cell, thS, GRAD);
        double[] a2 = Atmosphere.windAt(123456, 234567, SEED, cell, thS, GRAD);
        say(String.format(LF, "   确定性：%.12f vs %.12f  %s", a1[0], a2[0], a1[0] == a2[0] ? "逐位相同 OK" : "**不同**"));
        say(String.format(LF, "   （校验和 %d）", m));
        rep.close();
    }

    static double min(double[] v) { double m = v[0]; for (double x : v) if (x < m) m = x; return m; }
    static double max(double[] v) { double m = v[0]; for (double x : v) if (x > m) m = x; return m; }
    static double mean(double[] v) { double s = 0; for (double x : v) s += x; return s / v.length; }

    static void say(String s) { System.out.println("[P260] " + s); rep.println("[P260] " + s); }
}
