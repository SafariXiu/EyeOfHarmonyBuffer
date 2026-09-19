package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Locale;

/**
 * P443 **v2**：p' 三项分解 + 「去掉纬向平均那一份」的反事实（审计发现 #1 的量器）。
 *
 * <h3>v1 -> v2 修的两个 bug（都是父 agent 自己犯的，v1 的归因因此作废）</h3>
 * <ol>
 *   <li><b>梯度恒等式没进自检</b>：v1 只验了 <code>pS+pC+pX == pressureAnomaly</code>（值层面，通过），
 *       但错的是**梯度**层面 ⇒ 自检漏掉了唯一出错的地方。<b>v2 增加 px/pz 的恒等式自检</b>。</li>
 *   <li><b>pz 里丢了 d(kappa)/dz</b>：v1 把 pS/pC 写成「固定中心 kappa、只变纬度」，
 *       而生产 pressureAnomaly 的 pz 是在 (x, z+-G) 上取 p'（那里的 kappa 也不同）。
 *       kappa 的海岸线是 (x,z) 平面上的曲线 ⇒ dkappa/dz 很大，漏掉它会把 cell 项的贡献算错量级。
 *       <b>v2 每个分量在「它自己的那个点」上取它自己的 kappa。</b>
 * </li>
 * </ol>
 *
 * <h3>要回答的问题</h3>
 * <pre>
 *   p' = -K_P*CHI*seasonalAnomaly(lat, kappa, theta)   <- (S) 固定 kappa 时**只随纬度变**
 *        -K_P*CHI*(1-kappa)*sstAnom                    <- (X) 海温距平（当前 provider=null ⇒ 恒 0）
 *        + cellPressure(lat, kappa, theta)             <- (C) 副高 cell
 * </pre>
 * u = -(gam*px + f*pz)/den + U_zm。哪些分量在**重造**已经由 U_zm 给定的纬向平均风？
 *
 * <h3>自检（不通过则本探针读数不可引用）</h3>
 * A 段两条：值的恒等式 max|sum - production|、**梯度的恒等式** max|px_sum - px_prod| 与 pz 同理。
 */
public class P443 {

    static final int SEED = 1022228679;
    /** ⚠ 口径修正（§559，模板 P442:47）：收 {@code long seed} 的入口传本值；收 {@code int worldSeedInt} 的入口仍传裸 {@code SEED}。本次未改任何判据/阈值/输出行。 */
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final int ZC = WorldContract.Z_CYCLE;
    static final int CELL = PlateField.PLATE_CELL;
    static final int GRAD = 500_000, DX = 100_000;
    static final int XMIN = -12_000_000, XMAX = 12_000_000;
    static final double LATSTEP = 5.0;
    static final double[] PH4 = {0.0, Math.PI / 2, Math.PI, 3.0 * Math.PI / 2.0};
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static final HashMap<Long, Double> KC = new HashMap<>();

    static void say(String s) { rep.println("[P443] " + s); System.out.println("[P443] " + s); }

    static double kap(int x, int z) {
        long key = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
        Double v = KC.get(key);
        if (v != null) return v;
        double k = Atmosphere.kappaAt(x, z, SD, CELL);
        KC.put(key, k);
        return k;
    }
    static double pS(double latRad, double k, double th) {
        return -Atmosphere.K_P * Atmosphere.CHI * Atmosphere.seasonalAnomaly(latRad, k, th);
    }
    static double pC(double latRad, double k, double th) { return Atmosphere.cellPressure(latRad, k, th); }
    /** 反事实用：季节项只保留相对 kappa=KAPPA_MEAN 参考态的偏离。 */
    static double pSd(double latRad, double k, double th) {
        return -Atmosphere.K_P * Atmosphere.CHI
             * (Atmosphere.seasonalAnomaly(latRad, k, th)
              - Atmosphere.seasonalAnomaly(latRad, Atmosphere.KAPPA_MEAN, th));
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p443_report.txt"), "UTF-8");
        say("P443 v2：p' 三项分解 + 反事实（审计发现 #1）");
        say(String.format(LF, "  SEED=%d  KAPPA_MEAN=%.4f  K_P=%.2f  CHI=%.2f  GRAD=%d km  纬度步长 %.1f",
            SEED, Atmosphere.KAPPA_MEAN, Atmosphere.K_P, Atmosphere.CHI, GRAD / 1000, LATSTEP));
        say(String.format(LF, "  SST_PROVIDER=%s  CELL_GAIN=%.2f  CELL_MIGRATION=%.1f 度  CELL_TROPIC_GATE=%.0f 度",
            Atmosphere.SST_PROVIDER == null ? "null" : "非 null", Atmosphere.CELL_GAIN,
            Math.toDegrees(Atmosphere.CELL_MIGRATION), Atmosphere.CELL_TROPIC_GATE_DEG));
        say("");

        // ---------- A. 值与**梯度**的双重恒等式自检 ----------
        say("A. 恒等式自检（**值和梯度都要过**）");
        double eVal = 0, ePx = 0, ePz = 0, pMax = 0, pzMax = 0;
        for (double latDeg = -80; latDeg <= 80.0001; latDeg += 5) {
            int z = (int) (latDeg / 90.0 * (ZC / 2));
            double lat = WorldContract.latOf(z);
            for (int x = -6_000_000; x <= 6_000_000; x += 1_500_000) {
                double kC = kap(x, z);
                double latP = WorldContract.latOf(z + GRAD), latM = WorldContract.latOf(z - GRAD);
                double kP = kap(x, z + GRAD), kM = kap(x, z - GRAD);
                double kE = kap(x + GRAD, z), kW = kap(x - GRAD, z);
                for (double th : PH4) {
                    double prod = Atmosphere.pressureAnomaly(x, z, SD, CELL, th);
                    eVal = Math.max(eVal, Math.abs(pS(lat, kC, th) + pC(lat, kC, th) - prod));
                    pMax = Math.max(pMax, Math.abs(prod));
                    double pxProd = (Atmosphere.pressureAnomaly(x + GRAD, z, SD, CELL, th)
                                   - Atmosphere.pressureAnomaly(x - GRAD, z, SD, CELL, th)) / (2.0 * GRAD);
                    double pxSum = (pS(lat, kE, th) - pS(lat, kW, th) + pC(lat, kE, th) - pC(lat, kW, th)) / (2.0 * GRAD);
                    ePx = Math.max(ePx, Math.abs(pxSum - pxProd));
                    double pzProd = (Atmosphere.pressureAnomaly(x, z + GRAD, SD, CELL, th)
                                   - Atmosphere.pressureAnomaly(x, z - GRAD, SD, CELL, th)) / (2.0 * GRAD);
                    double pzSum = (pS(latP, kP, th) - pS(latM, kM, th) + pC(latP, kP, th) - pC(latM, kM, th)) / (2.0 * GRAD);
                    ePz = Math.max(ePz, Math.abs(pzSum - pzProd));
                    pzMax = Math.max(pzMax, Math.abs(pzProd));
                }
            }
        }
        say(String.format(LF, "  值  : max|sum(S,C) - production|        = %.3e Pa      （|p'|max = %.1f Pa）", eVal, pMax));
        say(String.format(LF, "  px  : max|pxS+pxC - px_prod|            = %.3e Pa/m    （|px|max = %.3e Pa/m）", ePx, pMax / GRAD));
        say(String.format(LF, "  pz  : max|pzS+pzC - pz_prod|            = %.3e Pa/m    （|pz|max = %.3e Pa/m）", ePz, pzMax));
        boolean ok = eVal < 1e-6 && ePx < 1e-12 && ePz < 1e-12;
        say(ok ? "  ==> 值 + 梯度 双重恒等式成立，读数可引用 OK"
               : "  ==> !! 恒等式不成立，本探针其余读数**不可引用**");
        say("");

        // ---------- B. 逐纬度分解 ----------
        final int NL = 31;
        double[] fLat = new double[NL], fL = new double[NL];
        double[] aZm = new double[NL], aS = new double[NL], aC = new double[NL];
        double[] aTot = new double[NL], aFix = new double[NL], aObs = new double[NL], aRes = new double[NL];
        HashMap<Integer, Integer> idx = new HashMap<>();
        int nLat = 0;
        double gL = 0, gZm = 0, gS = 0, gC = 0, gTot = 0, gFix = 0, gRes = 0;

        for (double latDeg = -62.5; latDeg <= 62.5 + 1e-9; latDeg += LATSTEP) {
            int z = (int) (latDeg / 90.0 * (ZC / 2));
            double lat = WorldContract.latOf(z);
            double latP = WorldContract.latOf(z + GRAD), latM = WorldContract.latOf(z - GRAD);
            int li;
            Integer got = idx.get((int) Math.round(latDeg));
            if (got == null) { li = nLat++; idx.put((int) Math.round(latDeg), li); fLat[li] = latDeg; }
            else li = got;

            int runStart = Integer.MIN_VALUE;
            for (int x = XMIN; x <= XMAX + DX; x += DX) {
                boolean land = (x > XMAX) || PlateField.isLandWithCell(x, z, SD, CELL);
                if (!land) { if (runStart == Integer.MIN_VALUE) runStart = x; continue; }
                if (runStart == Integer.MIN_VALUE) continue;
                int xw = runStart, xe = x - DX, L = xe - xw;
                runStart = Integer.MIN_VALUE;
                if (L < 8 * DX) continue;
                double dlt = Math.cbrt(1.9e4 / Math.max(1e-30,
                    WorldContract.betaForLatitude(Math.toRadians(latDeg), ZC)));
                if (L < 2 * Math.PI * dlt) continue;

                double sZm = 0, sS = 0, sC = 0, sTot = 0, sFix = 0;
                int m = 0;
                for (int xx = xw; xx <= xe; xx += 8 * DX) {
                    double kC = kap(xx, z);
                    double kP = kap(xx, z + GRAD), kM = kap(xx, z - GRAD);
                    double kE = kap(xx + GRAD, z), kW = kap(xx - GRAD, z);
                    for (double th : PH4) {
                        double pxS = (pS(lat, kE, th) - pS(lat, kW, th)) / (2.0 * GRAD);
                        double pxC = (pC(lat, kE, th) - pC(lat, kW, th)) / (2.0 * GRAD);
                        double pzS = (pS(latP, kP, th) - pS(latM, kM, th)) / (2.0 * GRAD);
                        double pzC = (pC(latP, kP, th) - pC(latM, kM, th)) / (2.0 * GRAD);
                        double pxSd = (pSd(lat, kE, th) - pSd(lat, kW, th)) / (2.0 * GRAD);
                        double pzSd = (pSd(latP, kP, th) - pSd(latM, kM, th)) / (2.0 * GRAD);
                        double uZm  = Atmosphere.wind(0, 0, kC, lat, th)[0];
                        double uTot = Atmosphere.windAt(xx, z, SD, CELL, th, GRAD)[0];
                        double uS   = Atmosphere.wind(pxS, pzS, kC, lat, th)[0] - uZm;
                        double uC   = Atmosphere.wind(pxC, pzC, kC, lat, th)[0] - uZm;
                        double uFx  = Atmosphere.wind(pxSd + pxC, pzSd + pzC, kC, lat, th)[0];
                        sZm += uZm; sS += uS; sC += uC; sTot += uTot; sFix += uFx;
                    }
                    m++;
                }
                if (m == 0) continue;
                double w = L / 4.0 / m, res = (sTot - sZm - sS - sC) / 4.0;
                fL[li] += L;
                aZm[li] += sZm * w; aS[li] += sS * w; aC[li] += sC * w;
                aTot[li] += sTot * w; aFix[li] += sFix * w; aRes[li] += res * w;
                gL += L; gZm += sZm * w; gS += sS * w; gC += sC * w;
                gTot += sTot * w; gFix += sFix * w; gRes += res * w;
            }
            if (fL[li] > 0) aObs[li] = com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables.uZmSea(Math.abs(latDeg), Math.PI / 2);
        }

        say("B. 逐纬度：各项贡献的盆均 u（m/s，长度加权，判年均）");
        say(String.format(LF, "  %-7s %8s %10s %10s %10s %10s %9s %9s",
            "纬度", "U_zm", "u_S(季节)", "u_C(cell)", "u_tot(现行)", "u_FIX", "obs 年均", "迭加残差"));
        for (int i = 0; i < nLat; i++) {
            if (fL[i] <= 0) continue;
            double Lw = fL[i];
            say(String.format(LF, "  %-7.1f %8.3f %10.3f %10.3f %10.3f %10.3f %9.3f %9.3f", fLat[i],
                aZm[i] / Lw, aS[i] / Lw, aC[i] / Lw, aTot[i] / Lw, aFix[i] / Lw, aObs[i], aRes[i] / Lw));
        }
        say("");
        say("C. 汇总（长度加权，|lat|<=62.5，活跃海盆内）");
        say(String.format(LF, "  U_zm              = %+.3f m/s", gZm / gL));
        say(String.format(LF, "  u_S（季节项）      = %+.3f m/s", gS / gL));
        say(String.format(LF, "  u_C（cell 项）     = %+.3f m/s", gC / gL));
        say(String.format(LF, "  u_tot（现行）      = %+.3f m/s", gTot / gL));
        say(String.format(LF, "  u_FIX             = %+.3f m/s   （季节项改为相对 KAPPA_MEAN 的偏离）", gFix / gL));
        say(String.format(LF, "  迭加残差 |tot-zm-S-C| = %.3f m/s  （迭代耦合 ⇒ 非零正常；占 |u_tot| 的 %.0f%%）",
            Math.abs(gRes) / gL, 100.0 * Math.abs(gRes) / Math.max(1e-9, Math.abs(gTot / gL))));
        rep.close();
        System.out.println("JAVA_EXIT=0");
    }
}
