package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.GyreRow;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Locale;

/**
 * P280：沿岸风符号的根因定位 + 受控反事实矩阵（不改 src）。
 *
 * 变体维度：K_P（热力项振幅）、CELL_GAIN、cell 项的季节迁移 dCell、是否把 p_ref 的经向梯度算进 p_z、外生贸易风表。
 */
public class P280 {

    static final int SEED = 1022228679;
    /** ⚠ 口径修正（§559，模板 P442:47）：收 {@code long seed} 的入口传本值；收 {@code int worldSeedInt} 的入口仍传裸 {@code SEED}。本次未改任何判据/阈值/输出行。 */
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final int ZC = WorldContract.Z_CYCLE;
    static final double TAU0 = 0.0685, H_T = 4000.0, A_H = 1.9e4, H = 5000.0;
    static final int NM = 96, NQ = 5, GRAD = 15_000;
    static final int CELL = PlateField.PLATE_CELL;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:/moder/EyeOfHarmonyBuffer");
    static PrintStream rep;

    static final int MAXS = 8192;
    static int ns = 0;
    static int[] SX = new int[MAXS], SZ = new int[MAXS], SROW = new int[MAXS], BAND = new int[MAXS];
    static boolean[] KXPOS = new boolean[MAXS];
    static double[] SLAT = new double[MAXS];
    static double[][] KAP = new double[MAXS][5];
    static double[][] KAPB = new double[MAXS][5];
    static final int STEP2 = 150_000;
    static final int NSE = 8;
    static double[][] KAPS = new double[MAXS][5];
    static double[][][] TNS = new double[MAXS][5][NSE];

    static final int[] SDX = {1, -1, 0, 0, 0};
    static final int[] SDZ = {0, 0, 1, -1, 0};

    static double[] SET = new double[NSE];
    /** 归一化热力项：p_thermal = K_P * TN[i][j][s]（TN 单位 K）。 */
    static double[][][] TN = new double[MAXS][5][NSE];

    static final double[] V_ZM = {0.0, -1.2, -2.0, -1.2, 0.0, 0.3, 0.4, 0.2, 0.0, 0.0};

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/p280_report.txt"), "UTF-8");
        long t0 = System.currentTimeMillis();
        for (int s = 0; s < NSE; s++) SET[s] = 2.0 * Math.PI * s / NSE;

        GyreRow.BandedWind wind = new GyreRow.BandedWind(TAU0, ZC);
        for (int r = 0; r < NM; r++) {
            int z = (int) ((r + 0.5) / NM * ZC);
            for (int q = 0; q < NQ; q++) {
                GyreRow.Params p = new GyreRow.Params();
                p.h = H; p.rhoH = 1025.0 * H_T; p.aH = A_H; p.zCycle = ZC;
                GyreRow.Row row = GyreRow.solve((q * 2_300_000 + r * 97_000) % 9_000_000, z, SD, CELL, wind, p);
                if (!row.valid) continue;
                if (row.eastX >= p.maxRow - 20_000) continue;
                if (row.westX <= -p.maxRow + 20_000) continue;
                if ((row.eastX - row.westX) < 2 * Math.PI * p.deltaAt(z)) continue;
                for (int k = 1; k <= 3; k++) {
                    if (ns >= MAXS) break;
                    SX[ns] = row.eastX - k * 25_000; SZ[ns] = z; SLAT[ns] = WorldContract.latOf(z); SROW[ns] = r;
                    ns++;
                }
            }
        }
        say(String.format(LF, "P280：沿岸风符号根因定位  采样点 %d  GRAD=%d m  PLATE_CELL=%d", ns, GRAD, CELL));
        say(String.format(LF, "当前 src 状态：K_P=%.0f Pa/K (H_EFF=%.0f)  CELL_GAIN=%.1f  U_MAX=%.0f  COAST_WIND_ON=%s",
                Atmosphere.K_P, Atmosphere.H_EFF, Atmosphere.CELL_GAIN, Atmosphere.U_MAX, Atmosphere.COAST_WIND_ON));

        for (int i = 0; i < ns; i++) {
            for (int j = 0; j < 5; j++) {
                int xx = SX[i] + SDX[j] * GRAD, zz = SZ[i] + SDZ[j] * GRAD;
                KAP[i][j] = PlateField.landFractionWithCell(xx, zz, SD, CELL, PlateField.COAST_BLEND);
                KAPB[i][j] = PlateField.landFractionWithCell(xx, zz, SD, CELL, 2_400_000);
                int xs = SX[i] + SDX[j] * STEP2, zs = SZ[i] + SDZ[j] * STEP2;
                KAPS[i][j] = PlateField.landFractionWithCell(xs, zs, SD, CELL, PlateField.COAST_BLEND);
                for (int s = 0; s < NSE; s++) {
                    TN[i][j][s] = -Atmosphere.CHI * Atmosphere.seasonalAnomaly(WorldContract.latOf(zz), KAP[i][j], SET[s]);
                    TNS[i][j][s] = -Atmosphere.CHI * Atmosphere.seasonalAnomaly(WorldContract.latOf(zs), KAPS[i][j], SET[s]);
                }
            }
            double a = Math.abs(WorldContract.latOf(SZ[i]));
            BAND[i] = (a < Math.toRadians(48) ? 1 : 0) | (a >= Math.toRadians(10) && a <= Math.toRadians(35) ? 2 : 0)
                    | (a < Math.toRadians(20) ? 4 : 0);
            KXPOS[i] = (KAP[i][0] - KAP[i][1]) > 0;
        }
        int kxPos = 0; double kapSum = 0;
        for (int i = 0; i < ns; i++) { if (KXPOS[i]) kxPos++; kapSum += KAP[i][4]; }
        say(String.format(LF, "采样点体检：kappa 均值 %.3f   dkappa/dx > 0（真东岸）%d/%d = %.0f%%", kapSum / ns, kxPos, ns, 100.0 * kxPos / ns));
        say("  参考：由现有 p_ref 的经向梯度 + Ekman-Rayleigh 导出的 v_zm（m/s，北半球，gamma=2.5e-5）：");
        StringBuilder sb = new StringBuilder("    ");
        for (double ld : new double[]{5, 10, 15, 20, 25, 30, 35, 40, 50, 60, 70}) {
            double lat = Math.toRadians(ld), f2 = WorldContract.coriolis(lat), g = Atmosphere.GAMMA_OCN;
            double pz = (ZonalTables.pRef(ld + 1) - ZonalTables.pRef(ld - 1)) / (2.0 * 111_000.0);
            double v = -g * pz / (Atmosphere.RHO_AIR * (g * g + f2 * f2));
            sb.append(String.format(LF, "%.0f:%+.1f  ", ld, v));
        }
        say(sb.toString());

        // 变体：{名称, K_P, CELL_GAIN, dCellAmp(deg), p_ref进p_z(1/0), 贸易风表倍数}
        String[] name = {
            "A 现状 §60 (K_P=276, cell=0)",
            "B A + p_ref 进 p_z",
            "C 旧基线 (K_P=69, cell=2.8, d=23.44)",
            "D C + 相位修正 d=6",
            "E D + p_ref 进 p_z",
            "F K_P=276 + cell=2.8 + d=6",
            "G F + p_ref 进 p_z",
            "H K_P=115 + cell=2.8 + d=6 + p_ref",
            "I K_P=69 + cell=0 + p_ref 进 p_z",
            "J G + 贸易风表 1x",
            "K E + 贸易风表 1x",
            "L E 但 CELL_GAIN=1.4",
            "M E 但 K_P=115, CELL_GAIN=2.0",
            "N E + 赤道 f 下限 1.2e-5",
            "O E 但跨岸差分步长 150km",
            "P O 且 CELL_GAIN=1.4",
            "Q E 但 cell 用洋盆尺度 kappa(R=2400km)",
        };
        double[][] P = {{276, 0, 0, 0, 0}, {276, 0, 0, 1, 0}, {69, 2.8, 23.44, 0, 0}, {69, 2.8, 6, 0, 0},
                        {69, 2.8, 6, 1, 0}, {276, 2.8, 6, 0, 0}, {276, 2.8, 6, 1, 0}, {115, 2.8, 6, 1, 0},
                        {69, 0, 0, 1, 0}, {276, 2.8, 6, 1, 1}, {69, 2.8, 6, 1, 1},
                        {69, 1.4, 6, 1, 0}, {115, 2.0, 6, 1, 0}, {69, 2.8, 6, 1, 2},
                        {69, 2.8, 6, 1, 3}, {69, 1.4, 6, 1, 3}, {69, 2.8, 6, 1, 0}};
        int NV = name.length;
        boolean[] BASINV = new boolean[NV];
        BASINV[NV - 1] = true;
        long[][] bTot = new long[NV][4], bOk = new long[NV][4];
        double[][] allTau = new double[NV][ns * NSE];
        int[] nTau = new int[NV];
        double[][] seasEq = new double[NV][NSE];   // 10~35 度带的 eq 之和
        long[][] seasN = new long[NV][NSE];
        int[][] latOk = new int[NV][NM], latTot = new int[NV][NM];

        for (int s = 0; s < NSE; s++) {
            double th = SET[s];
            double dObs = Math.toDegrees(Atmosphere.subsolarLat(th));
            for (int i = 0; i < ns; i++) {
                double lat = SLAT[i], latDeg = Math.toDegrees(lat);
                int bd = BAND[i];
                for (int v = 0; v < NV; v++) {
                    double dCell = P[v][2] * Math.cos(th);
                    double[] uv = windVariant(i, s, P[v][0], P[v][1], dCell, BASINV[v], P[v][4], P[v][3] > 0.5);
                    double tz = Atmosphere.RHO_AIR * Atmosphere.cdOf(KAP[i][4]) * Math.hypot(uv[0], uv[1]) * uv[1];
                    double eq = latDeg >= 0 ? -tz : tz;
                    for (int k = 0; k < 4; k++) {
                        boolean in = k == 0 ? (bd & 1) != 0 : k == 1 ? (bd & 2) != 0 : k == 2 ? ((bd & 2) != 0 && KXPOS[i]) : (bd & 4) != 0;
                        if (in) { bTot[v][k]++; if (eq > 0) bOk[v][k]++; }
                    }
                    if ((bd & 2) != 0) { seasEq[v][s] += eq; seasN[v][s]++; }
                    latTot[v][SROW[i]]++; if (eq > 0) latOk[v][SROW[i]]++;
                    allTau[v][nTau[v]++] = Math.abs(tz);
                }
            }
        }

        say("");
        say("  === 反事实矩阵（828 采样点 x 8 季；「向赤道为正」）===");
        say(String.format(LF, "  %-40s %8s %8s %8s %8s %9s %9s", "变体", "|lat|<48", "10~35", "10~35&kxd>0", "|lat|<20", "10~35带", "10~35带"));
        say(String.format(LF, "  %-40s %8s %8s %8s %8s %9s %9s", "", "符号%", "符号%", "符号%", "符号%", "年均eq", "最差季eq"));
        for (int v = 0; v < NV; v++) {
            double[] a = Arrays.copyOf(allTau[v], nTau[v]);
            Arrays.sort(a);
            double sm = 0, mn = 1e9; long cnt = 0;
            for (int s = 0; s < NSE; s++) { double m = seasN[v][s] > 0 ? seasEq[v][s] / seasN[v][s] : 0; sm += m; mn = Math.min(mn, m); cnt++; }
            say(String.format(LF, "  %-40s %7.0f%% %7.0f%% %7.0f%% %7.0f%% %+9.4f %+9.4f",
                    name[v], 100.0 * bOk[v][0] / Math.max(1, bTot[v][0]), 100.0 * bOk[v][1] / Math.max(1, bTot[v][1]),
                    100.0 * bOk[v][2] / Math.max(1, bTot[v][2]), 100.0 * bOk[v][3] / Math.max(1, bTot[v][3]), sm / cnt, mn));
        }
        say(String.format(LF, "  样本数：|lat|<48 %d   10~35度 %d（kxd>0 %d）  |lat|<20 %d", bTot[0][0], bTot[0][1], bTot[0][2], bTot[0][3]));
        say("");
        say("  === 10~35 度带的「向赤道应力」逐季（Pa；正=上升流有利，s=0 是北半球夏至）===");
        say(String.format(LF, "  %-40s %s", "变体", "  夏至     +45     秋分     +135     冬至     +225     春分     +315"));
        for (int v : new int[]{0, 2, 3, 4, 6, 10, 11, 12}) {
            StringBuilder t = new StringBuilder(String.format(LF, "  %-40s", name[v]));
            for (int s = 0; s < NSE; s++) t.append(String.format(LF, " %+8.4f", seasN[v][s] > 0 ? seasEq[v][s] / seasN[v][s] : 0));
            say(t.toString());
        }
        say("");
        say("  === 10~35 度带 |tau_z| 分位（Pa，目标中位 ~0.09）===");
        for (int v = 0; v < NV; v++) {
            double[] a = Arrays.copyOf(allTau[v], nTau[v]);
            Arrays.sort(a);
            say(String.format(LF, "  %-40s p50 %.4f  p75 %.4f  p90 %.4f  p99 %.4f  max %.3f",
                    name[v], a[(int) (0.50 * a.length)], a[(int) (0.75 * a.length)], a[(int) (0.90 * a.length)],
                    a[(int) (0.99 * a.length)], a[a.length - 1]));
        }

        say("");
        say("  === 逐纬度带符号正确率（8 季合计）===");
        say(String.format(LF, "  %8s %6s %9s %9s %9s %9s %9s", "纬度", "样本", "A现状", "C旧基线", "D修相位", "G=D+p_ref", "K=G+贸易风"));
        int[] show = {0, 2, 3, 6, 10};
        for (int r = 0; r < NM; r += 4) {
            int z = (int) ((r + 0.5) / NM * ZC);
            double latDeg = Math.toDegrees(WorldContract.latOf(z));
            StringBuilder t = new StringBuilder(String.format(LF, "  %8.1f %6d", latDeg, latTot[0][r]));
            for (int v : show) t.append(String.format(LF, " %8.0f%%", 100.0 * latOk[v][r] / Math.max(1, latTot[v][r])));
            say(t.toString());
        }
        say("");
        say(String.format(LF, "  用时 %.1f s", (System.currentTimeMillis() - t0) / 1000.0));
        rep.close();
    }

    static double[] windVariant(int i, int s, double kp, double gain, double dCell, boolean basin, double vt, boolean prefPz) {
        double lat = SLAT[i], latDeg = Math.toDegrees(lat);
        double[] pp = new double[5];
        for (int j = 0; j < 5; j++) {
            double k = basin ? KAPB[i][j] : KAP[i][j];
            pp[j] = kp * TN[i][j][s] + gain * ZonalTables.carrier(latDeg - dCell) * (Atmosphere.KAPPA_MEAN - k);
        }
        double px = (pp[0] - pp[1]) / (2.0 * GRAD), pz = (pp[2] - pp[3]) / (2.0 * GRAD);
        boolean floor = vt > 1.5 && vt < 2.5;
        boolean wide = vt > 2.5;
        if (wide) {
            for (int j = 0; j < 5; j++) {
                double k = basin ? KAPB[i][j] : KAPS[i][j];
                pp[j] = kp * TNS[i][j][s] + gain * ZonalTables.carrier(latDeg - dCell) * (Atmosphere.KAPPA_MEAN - k);
            }
            px = (pp[0] - pp[1]) / (2.0 * STEP2); pz = (pp[2] - pp[3]) / (2.0 * STEP2);
        }
        if (prefPz) {
            int st = wide ? STEP2 : GRAD;
            double pN = ZonalTables.pRef(Math.toDegrees(WorldContract.latOf(SZ[i] + st)) - dCell);
            double pS = ZonalTables.pRef(Math.toDegrees(WorldContract.latOf(SZ[i] - st)) - dCell);
            double pzr = (pN - pS) / (2.0 * st);
            if (floor) {
                double g = Atmosphere.gammaOf(basin ? KAPB[i][4] : KAP[i][4]);
                double f2 = WorldContract.coriolis(lat);
                double fm = Math.copySign(Math.max(Math.abs(f2), 1.2e-5), f2 == 0 ? 1 : f2);
                pz += 0.0;
                double vRef = -g * pzr / (Atmosphere.RHO_AIR * (g * g + fm * fm));
                double[] uv0 = Atmosphere.wind(px, pz, basin ? KAPB[i][4] : KAP[i][4], lat, SET[s]);
                uv0[1] += vRef;
                return uv0;
            }
            pz += pzr;
        }
        double[] uv = Atmosphere.wind(px, pz, basin ? KAPB[i][4] : KAP[i][4], lat, SET[s]);
        uv[1] += (vt > 1.5 ? 0 : vt) * vTrade(latDeg);
        return uv;
    }

    static double vTrade(double latDeg) {
        double a = Math.abs(latDeg);
        if (a >= 90) return 0;
        int i = (int) (a / 10.0);
        if (i >= V_ZM.length - 1) return 0;
        double t = a / 10.0 - i;
        return Math.signum(latDeg) * (V_ZM[i] * (1 - t) + V_ZM[i + 1] * t);
    }

    static void say(String s) { System.out.println("[P280] " + s); rep.println("[P280] " + s); }
}
