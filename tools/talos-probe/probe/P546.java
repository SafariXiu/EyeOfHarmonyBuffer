package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.nio.file.Files;
import java.util.Locale;

/**
 * P546 —— **副热带 Q-D 诊断：降水分解**。
 *
 * <p>目的：把 {@code PrecipField.mmPerDay} 在三个锚带上按**项**拆开，回答
 * 「副热夏 −48% 缺的是哪一项、副热冬 +58% 多的又是哪一项」。
 *
 * <p>合成式（逐字镜像 {@code mmPerDay} 的 780-821 行）：
 * <pre>
 *   term1 = precip(q, wEff(lat, theta, divU))          // 纬向平均上升支 + 局地辐合
 *   term2 = (withEddy && mfc > 0) ? mfc*depl*depletionCol/RHO_WATER : 0   // 风暴轴（只取辐合侧）
 *   term3 = SHALLOW_FLOOR ? ALPHA_SH*eSh/RHO_WATER : 0                     // 浅对流**地板**
 *   total = term1 + term2;  final = SHALLOW_FLOOR ? max(total, term3) : total
 * </pre>
 *
 * <p><b>自证</b>：每个带-季节的前若干个点上，重算的 {@code final} 必须与
 * {@code PrecipField.mmPerDay(...)} **逐位相同** —— 否则这套分解是假的。
 *
 * <p><b>驱动</b>：地球真实陆海掩膜（{@code refs/earth_mask.bin}）—— 与 P542 同一台仪器，
 * 边界条件钉死，所以看到的是**纯物理**。
 */
public class P546 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static final File MASKF = new File(ROOT, "build/eoh_probe/refs/earth_mask.bin");
    static PrintStream rep;
    static void say(String s) { rep.println("[P546] " + s); System.out.println("[P546] " + s); }

    static final double CIRC = 40_000_000.0;
    static final int NX = 120;                 // 40,000 km / 120 = 333 km 步长
    static final int GRAD = 500_000;
    static final int SEED = 1022228679;

    static int NLAT, NLON;
    static double LAT0, DLAT, LON0, DLON;
    static byte[] LAND;

    static void loadMask() throws IOException {
        byte[] all = Files.readAllBytes(MASKF.toPath());
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(all).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        bb.position(4);
        NLAT = bb.getInt(); NLON = bb.getInt();
        LAT0 = bb.getDouble(); DLAT = bb.getDouble(); LON0 = bb.getDouble(); DLON = bb.getDouble();
        LAND = new byte[NLAT * NLON];
        bb.get(LAND);
    }

    static boolean earthIsLand(int x, int z) {
        double lat = Math.toDegrees(WorldContract.latOf(z, WorldContract.Z_CYCLE));
        double lon = (x / CIRC) * 360.0;
        lon -= Math.floor(lon / 360.0) * 360.0;
        int i = (int) Math.round((lat - LAT0) / DLAT);
        int j = (int) Math.round((lon - LON0) / DLON);
        if (i < 0) i = 0; if (i >= NLAT) i = NLAT - 1;
        j = ((j % NLON) + NLON) % NLON;
        return LAND[i * NLON + j] != 0;
    }

    /** 分解结果：{term1, term2, term3, total, final}（mm/day）+ 诊断中间量。 */
    static double[] decompose(int x, int z, long sd, int cell, double theta, double[] diag) {
        double lat = WorldContract.latOf(z);
        double k = Atmosphere.kappaAt(x, z, sd, cell);
        double elev = PlateField.elevationWithCell(x, z, sd, cell);
        double tSl = Atmosphere.annualSeaLevelTemp(lat, k, Atmosphere.sstAnom(x, z))
                   + Atmosphere.seasonalAnomaly(lat, k, theta);
        double[] u0 = Atmosphere.windAt(x, z, sd, cell, theta, GRAD);
        double hUp = PrecipField.upwindElev(x, z, sd, cell, u0[0], u0[1]);
        double depl = PrecipField.depletion(hUp, k);
        double tQ = PrecipField.Q_AT_SURFACE_TEMP
                  ? tSl - Atmosphere.GAMMA * Math.max(0.0, elev) * k : tSl;
        double q = PrecipField.moisture(tQ, k > 0.0 ? hUp : 0.0, k);
        double[] ux = Atmosphere.windAt(x + GRAD, z, sd, cell, theta, GRAD);
        double[] uw = Atmosphere.windAt(x - GRAD, z, sd, cell, theta, GRAD);
        double[] un = Atmosphere.windAt(x, z + GRAD, sd, cell, theta, GRAD);
        double[] us = Atmosphere.windAt(x, z - GRAD, sd, cell, theta, GRAD);
        double divU = (ux[0] - uw[0]) / (2.0 * GRAD) + (un[1] - us[1]) / (2.0 * GRAD);

        double shifted = Math.toDegrees(lat - PrecipField.precipSubsolarLat(theta));
        double wZm = ZonalTables.wZm(shifted);
        double wLoc = PrecipField.W_LOC_MAX * Math.tanh((-PrecipField.H_BL * divU) / PrecipField.W_LOC_MAX);
        double wE = PrecipField.SPLIT_ASCENT
                  ? Math.max(0.0, wZm) + Math.max(0.0, wLoc) : wZm + wLoc;

        double t1 = PrecipField.precip(q, wE);
        double t2 = 0.0;
        double mfc = PrecipField.eddyMfc(lat, theta);
        if (mfc > 0.0) t2 = mfc * depl * PrecipField.depletionCol(hUp, k) / PrecipField.RHO_WATER;
        double t3 = 0.0;
        if (PrecipField.SHALLOW_FLOOR) {
            double tSfcF = Atmosphere.surfaceTemp(x, z, sd, cell, theta);
            double qsSfcF = PrecipField.qSat(tSfcF);
            double spF = Math.hypot(u0[0], u0[1]);
            double vEff = Math.sqrt(spF * spF + PrecipField.V_GUST * PrecipField.V_GUST);
            double betaF = 1.0 - Atmosphere.clamp01(k);
            double eSh = Atmosphere.RHO_AIR * Atmosphere.cdOf(k) * vEff
                       * Math.max(0.0, betaF * qsSfcF - q);
            t3 = PrecipField.ALPHA_SH * eSh / PrecipField.RHO_WATER;
        }
        double tot = t1 + t2;
        double fin = PrecipField.SHALLOW_FLOOR ? Math.max(tot, t3) : tot;
        double C = 86400.0 * 1000.0;
        if (diag != null) {
            diag[0] = k; diag[1] = q; diag[2] = divU; diag[3] = wZm; diag[4] = wLoc;
            diag[5] = mfc; diag[6] = depl; diag[7] = deg2(lat);
        }
        return new double[]{t1 * C, t2 * C, t3 * C, tot * C, fin * C};
    }
    static double deg2(double rad) { return Math.toDegrees(rad); }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p546_report.txt"), "UTF-8");
        say("P546：副热带 Q-D 诊断 —— 降水分解（地球掩膜驱动，纯物理）");
        say("  契约自证：Z_CYCLE=" + WorldContract.Z_CYCLE + "  gain=" + PrecipField.EDDY_PHYS_GAIN
            + "  closure=" + PrecipField.EDDY_CLOSURE + "  SHALLOW_FLOOR=" + PrecipField.SHALLOW_FLOOR
            + "  ALPHA_SH=" + PrecipField.ALPHA_SH);
        loadMask();
        PlateField.MASK = new PlateField.LandMask() {
            public boolean isLand(int x, int z) { return earthIsLand(x, z); }
        };
        say("  ✔ 地球掩膜已装载（与 P542 同一台仪器）  NX=" + NX + " 步长 " + (int) (CIRC / NX / 1000) + " km");
        say("");

        long sd = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);

        double[][] bands = {{2.5, 12.5}, {27.5, 37.5}, {47.5, 62.5}};
        String[] bname = {"赤道 2.5-12.5", "副热 27.5-37.5", "中纬 47.5-62.5"};
        double[] gpcpS = {6.585, 2.301, 2.534};
        double[] gpcpW = {3.580, 2.391, 2.432};

        for (int si = 0; si < 2; si++) {
            double theta = (si == 0) ? thS : thW;
            String sn = (si == 0) ? "夏至 JJA" : "冬至 DJF";
            say("=== " + sn + " ===");
            say("  带            term1(w_zm+wLoc)   term2(风暴轴)   term3(浅对流地板)   min(t1+t2,t3)取谁   合计     GPCP     偏差");
            for (int bi = 0; bi < bands.length; bi++) {
                double lo = bands[bi][0], hi = bands[bi][1];
                double s1 = 0, s2 = 0, s3 = 0, sfin = 0; long n = 0;
                int floorWin = 0;
                double dq = 0, ddiv = 0, dwzm = 0, dwloc = 0, dmfc = 0, dk = 0;
                // ★★ 只取**北半球两个分支**（xk = 0 升 / 1 降）。
                //   锚点是「45~55**N**」这类**北半球**纬向平均；而 theta 是**全局季节** ——
                //   模型靠 lat 与 precipSubsolarLat 的符号差自动给南北半球相反的季节。
                //   第一版用四个分支（含南半球）⇒ 把 (NH 夏 + SH 冬)/2 当成了 NH 夏，
                //   副热 JJA 因此从 1.194 变成 4.306 —— **整个分解差点白做**。
                for (int xk = 0; xk < 2; xk++) {                     // 北半球：升 + 降
                    for (double latd = lo + 1.0; latd < hi; latd += (hi - lo) / 4.0) {
                        int z = zForAbsLat(latd, xk);
                        for (int c = 0; c < NX; c++) {
                            int x = (int) Math.round((c + 0.5) * CIRC / NX);
                            double[] dg = new double[8];
                            double[] r = decompose(x, z, sd, cell, theta, dg);
                            s1 += r[0]; s2 += r[1]; s3 += r[2]; sfin += r[4];
                            if (r[3] < r[2]) floorWin++;
                            dq += dg[1]; ddiv += dg[2]; dwzm += dg[3]; dwloc += dg[4];
                            dmfc += dg[5]; dk += dg[0];
                            n++;
                        }
                    }
                }
                double ref = (si == 0) ? gpcpS[bi] : gpcpW[bi];
                say(String.format(LF, "  %-14s %10.3f %12.3f %14.3f   地板胜 %4.1f%%   %8.3f  %7.3f  %+7.1f%%",
                    bname[bi], s1 / n, s2 / n, s3 / n, 100.0 * floorWin / n, sfin / n, ref,
                    100.0 * (sfin / n / ref - 1)));
                say(String.format(LF, "      （诊断）kappa=%.3f  q=%.5f  divU=%+.3e  w_zm=%+.3e  wLoc=%+.3e  mfc=%+.3e",
                    dk / n, dq / n, ddiv / n, dwzm / n, dwloc / n, dmfc / n));
            }
            say("");
        }

        // ---- 自证：重算必须与 mmPerDay 逐位相同 ----
        say("=== 自证：重算的 final 与 PrecipField.mmPerDay 逐位对照 ===");
        double mx = 0; int bad = 0;
        for (int bi = 0; bi < bands.length; bi++) {
            for (double th : new double[]{thS, thW}) {
                for (int c = 0; c < 6; c++) {
                    int x = (int) Math.round((c + 0.5) * CIRC / NX);
                    int z = zForAbsLat((bands[bi][0] + bands[bi][1]) / 2.0, 0);
                    double mine = decompose(x, z, sd, cell, th, null)[4];
                    double ref = PrecipField.mmPerDay(x, z, sd, cell, th, GRAD);
                    double d = Math.abs(mine - ref);
                    if (d > mx) mx = d;
                    if (d > 1e-9) bad++;
                }
            }
        }
        say(String.format(LF, "  36 个对照点：最大绝对差 = %.3e   超差点数 = %d   ⇒ %s",
            mx, bad, bad == 0 ? "分解与生产逐位相同 OK" : "**分解与生产不一致，结论作废**"));
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }

    /** 取 |lat| 指定值在第 k 个分支（0..3）上的 z。 */
    static int zForAbsLat(double absLatDeg, int branch) {
        int q = WorldContract.zOfLat(absLatDeg);   // 该分支内到赤道的弧长
        switch (branch) {
            case 0: return q;                                        // N 升： lat = +abs
            case 1: return 2 * WorldContract.MAX_D - q;              // N 降
            case 2: return 2 * WorldContract.MAX_D + q;              // S 降： lat = -abs
            default: return 4 * WorldContract.MAX_D - q;             // S 升
        }
    }
}
