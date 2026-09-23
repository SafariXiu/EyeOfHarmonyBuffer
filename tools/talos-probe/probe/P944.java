package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.ocean.*;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*; import java.util.*;

// P944 -- P477 的 2x2 归因矩阵：直接通路 dTdz vs 间接通路 airT/风.
public class P944 {
    static final int SEED = 1022228679;
    static final int ZC = WorldContract.Z_CYCLE;
    static final int SCAN = 500_000;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static final double WBC_MIN = 0.5;
    static final int[] LATS = {15,25,35,45,55,-15,-25,-35,-45};
    static PrintStream rep;
    static void say(String s) { rep.println("[P944] " + s); rep.flush(); System.out.println("[P944] " + s); System.out.flush(); }

    /** tanh 的逆：t = ANOM_MAX * atanh(Tprime / ANOM_MAX)。用于把 T-prime 还原成 tanh 的宗量。 */
    static double atnh(double k) {
        double u = k / SeaSurfaceTemp.ANOM_MAX;
        if (u > 0.999999) u = 0.999999;
        if (u < -0.999999) u = -0.999999;
        return SeaSurfaceTemp.ANOM_MAX * 0.5 * Math.log((1.0 + u) / (1.0 - u));
    }

    static double median(double[] v, int n) { if (n<=0) return Double.NaN; double[] a=Arrays.copyOf(v,n); Arrays.sort(a); return (n%2==1)?a[n/2]:0.5*(a[n/2-1]+a[n/2]); }

    static List<int[]> basinsAt(int z) {
        LinkedHashMap<Long,int[]> m = new LinkedHashMap<>();
        for (int x = -WorldContract.MAX_D; x <= WorldContract.MAX_D; x += SCAN) {
            int[] sp = OceanField.spanOf(x, z, SEED);
            if (sp == null) continue;
            long k = ((long) sp[1] << 32) ^ (sp[2] & 0xFFFFFFFFL);
            if (!m.containsKey(k)) m.put(k, new int[]{sp[1], sp[2]});
        }
        return new ArrayList<>(m.values());
    }

    static void setCell(boolean legacy, int force) {
        Atmosphere.ZMSLK_LEGACY = legacy;
        OceanField.DTDZ_FORCE = force;
        SimClimate.clearCache();
        HadleyCell.resetCache();
    }

    public static void main(String[] a) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p944_report.txt"), "UTF-8");
        OceanField.install(SEED);
        say("P944: P477 的 2x2 归因矩阵  SEED=" + SEED + "  ENABLED=" + OceanField.ENABLED);
        say("  格B = (legacy airT, legacy dTdz) 应逐位复现基线 A2E3771F 的 P477 表");
        say("  格A = (new airT, new dTdz) = 当前生产;  折半格给出两条通路各自的贡献");
        say("");
        // 盆地表只建一次 => 四格逐盆对齐
        ArrayList<int[]> rows = new ArrayList<>();
        for (int latDeg : LATS) {
            int z = (int)((double) latDeg / 90.0 * (ZC / 2));
            for (int[] b : basinsAt(z)) { if (b[1]-b[0] >= 200_000) rows.add(new int[]{latDeg, b[0], b[1]}); }
        }
        int nb = rows.size();
        say("  盆数 = " + nb);
        int[][] xs = new int[nb][];
        int[] ms = new int[nb];
        int[] zs = new int[nb];
        for (int i = 0; i < nb; i++) {
            int wx = rows.get(i)[1], width = rows.get(i)[2]-wx;
            int step = Math.max(5_000, width / 3000); int m = width / step + 1;
            int[] xx = new int[m]; for (int k = 0; k < m; k++) xx[k] = wx + k*step;
            xs[i] = xx; ms[i] = m;
            zs[i] = (int)((double) rows.get(i)[0] / 90.0 * (ZC / 2));
        }
        String[] tags = {"B(legacy,legacy)", "C(legacy,newDt)", "D(newAir,legacyDt)", "A(new,new)"};
        boolean[] leg = {true, true, false, false};
        int[] frc = {1, 2, 1, 0};
        double[][] wpk = new double[4][nb], tw = new double[4][nb], fwhm = new double[4][nb], cpk = new double[4][nb];
        double[] nWarm = new double[4], medWarm = new double[4], inBand = new double[4], nCold = new double[4], medCold = new double[4], medFw = new double[4];
        for (int c = 0; c < 4; c++) {
            setCell(leg[c], frc[c]);
            double[] wW = new double[nb]; int nW = 0; double[] wC = new double[nb]; int nC = 0; double[] fW = new double[nb];
            for (int i = 0; i < nb; i++) {
                int z = (int)((double) rows.get(i)[0] / 90.0 * (ZC / 2));
                int m = ms[i]; int[] xx = xs[i];
                double[] tv = new double[m];
                for (int k = 0; k < m; k++) tv[k] = OceanField.anomalyAt(xx[k], z, SEED);
                int iW = 0, iC = 0;
                for (int k = 1; k < m; k++) { if (tv[k] > tv[iW]) iW = k; if (tv[k] < tv[iC]) iC = k; }
                double base; { double[] mid = Arrays.copyOfRange(tv, m/4, 3*m/4); Arrays.sort(mid); base = mid[mid.length/2]; }
                double wPk = tv[iW], cPk = tv[iC];
                double wHalf = base + 0.5*(wPk-base); double wWid = 0;
                if (wPk > base) { int p = iW; while (p > 0 && tv[p-1] > wHalf) p--; int q = iW; while (q < m-1 && tv[q+1] > wHalf) q++; wWid = (q-p)*(double)(xx[1]-xx[0]); }
                wpk[c][i] = wPk; cpk[c][i] = cPk; tw[c][i] = tv[Math.min(1, m-1)]; fwhm[c][i] = wWid/1000.0;
                int absLat = Math.abs(rows.get(i)[0]); boolean sub = absLat >= 15 && absLat <= 45;
                if (tw[c][i] >= WBC_MIN && sub) { wW[nW] = wPk; fW[nW] = wWid/1000.0; nW++; }
                if (tw[c][i] <= -WBC_MIN && sub) { wC[nC] = cPk; nC++; }
            }
            int ib = 0; for (int k = 0; k < nW; k++) if (wW[k] >= 4.0 && wW[k] <= 8.0) ib++;
            nWarm[c] = nW; medWarm[c] = median(wW, nW); inBand[c] = ib;
            nCold[c] = nC; medCold[c] = median(wC, nC); medFw[c] = median(fW, nW);
            say("  " + tags[c] + "  done: nWarm=" + nW + " nCold=" + nC);
        }
        say("");
        say("  ===== 矩阵汇总 =====");
        say(String.format(LF, "  %-18s %6s %9s %7s %6s %9s %8s  %-6s %-6s %-6s", "cell", "nWarm", "medWarm", "inBand", "nCold", "medCold", "medFWHM", "A5_WARM", "A5_COLD", "A4"));
        for (int c = 0; c < 4; c++) {
            say(String.format(LF, "  %-18s %6.0f %+9.2f %7.0f %6.0f %+9.2f %8.0f  %-6s %-6s %-6s", tags[c], nWarm[c], medWarm[c], inBand[c], nCold[c], medCold[c], medFw[c],
                (medWarm[c] >= 4.0 && medWarm[c] <= 8.0) ? "PASS" : "FAIL", (medCold[c] <= -4.0) ? "PASS" : "FAIL", (medFw[c] >= 50) ? "PASS" : "FAIL"));
        }
        say("");
        say("  ===== 逐盆 暖峰 T-prime (K) =====");
        say(String.format(LF, "  %5s %8s %9s | %9s %9s %9s %9s | %s", "lat", "wxKm", "widthKm", tags[0], tags[1], tags[2], tags[3], "类(WB|WC|WD|WA)"));
        for (int i = 0; i < nb; i++) {
            StringBuilder cls = new StringBuilder();
            for (int c = 0; c < 4; c++) cls.append(tw[c][i] >= WBC_MIN ? "B" : (tw[c][i] <= -WBC_MIN ? "C" : "-"));
            say(String.format(LF, "  %5d %8d %9d | %+9.2f %+9.2f %+9.2f %+9.2f | %s", rows.get(i)[0], rows.get(i)[1]/1000, (rows.get(i)[2]-rows.get(i)[1])/1000,
                wpk[0][i], wpk[1][i], wpk[2][i], wpk[3][i], cls));
        }
        say("");
        say("");
        say("  ===== 每盆 dTdz 实测 (OceanField 口径: latOf(z +/- 50000), 端点单位=弧度) =====");
        say(String.format(LF, "  %5s %10s %9s %9s | %9s %9s %10s | %9s %9s %10s | %9s %9s | %7s %7s %7s",
            "lat", "z", "lat+50d", "lat-50d", "o(z+)", "o(z-)", "dOld", "n(z+)", "n(z-)", "dNew", "dN/dO", "dObs", "tB", "tC", "tR"));
        for (int i = 0; i < nb; i++) {
            int z = zs[i];
            double lT = WorldContract.latOf(z + 50_000), lM = WorldContract.latOf(z - 50_000);
            double oT = Atmosphere.zmslkLegacy(lT), oM = Atmosphere.zmslkLegacy(lM);
            double nT = Atmosphere.zmslkNew(lT), nM = Atmosphere.zmslkNew(lM);
            double bT = ZonalTables.tZmSlAnnual(lT), bM = ZonalTables.tZmSlAnnual(lM);
            double dOld = (oT - oM) / 100_000.0, dNew = (nT - nM) / 100_000.0, dObs = (bT - bM) / 100_000.0;
            double tB = atnh(wpk[0][i]), tC = atnh(wpk[1][i]);
            say(String.format(LF, "  %5d %10d %9.2f %9.2f | %9.3f %9.3f %10.5f | %9.3f %9.3f %10.5f | %9.4f %9.4f | %7.3f %7.3f %7.3f",
                rows.get(i)[0], z, Math.toDegrees(lT), Math.toDegrees(lM), oT, oM, dOld, nT, nM, dNew,
                dNew / dOld, dObs, tB, tC, tC / tB));
        }
        say("  列义: o/n = 旧式/新式 zmslk 在两端点的取值; dOld/dNew/dObs = 梯度(K/m); tB/tC = 把 T-prime 反解回 tanh 宗量;");
        say("        若 tR 与 dN/dO 逐行相等 => 变化 100% 由 dTdz 定量解释(无需别的因素).");
        say("");
        say("  判读: 格B 必须逐位等于基线 A2E3771F 的 P477 暖峰列 => 开关忠实.");
        say("        若 格C == 格A 则直接通路 dTdz 独自解释全部变化; 若 格D == 格A 则间接通路独自解释.");
        say("DONE");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
