package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.CoastalLayer;
import com.EyeOfHarmonyBuffer.sim.ocean.GyreRow;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanWiring;

import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;

/**
 * P285：A2/A3 用**真实大气风应力**重测。
 *
 * <p>发现：P276（A2 西边界 74.1 mm/s）**完全用 BandedWind 解析占位风**测的，
 * 只有 P275 的东边界沿岸层用了真实大气。本探针把两者放到同一批行上做受控对照。
 *
 * <p>做法：把真实大气风应力旋度预计算成 x 方向的格点（步长 50 km），
 * 再用插值喂给 GyreRow.solve —— 不改 GyreRow 一行代码。
 */
public class P285 {

    static final int SEED = 1022228679;
    static final int ZC = WorldContract.Z_CYCLE;
    static final double TAU0 = 0.0685, H_T = 4000.0, A_H = 1.9e4, H = 5000.0, W = 100_000.0;
    static final int GRAD = 500_000;
    static final int CLAT = 50_000;                 // 旋度格点步长
    static final int NLAT = 2 * (24_000_000 / CLAT) + 1;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    static final HashMap<Integer, double[]> CACHE = new HashMap<Integer, double[]>();

    public static void main(String[] args) throws Exception {

        // ── 第三步接线（§162）：验收必须在**生产口径**下跑 ──
        // 生产由 WorldChunkManagerTalos2 -> OceanWiring.onWorld 装 SST'；这里调**同一个入口**，
        // 保证「验收测的」与「生产跑的」是同一段代码（否则就是口径偏移）。
        // 预热是后台线程，本探针不等待 —— 它自己在采样时按行懒解，代价一样。
        OceanWiring.onWorld(SEED);
        System.out.println("[P285] 接线口径：installedSeed=" + com.EyeOfHarmonyBuffer.sim.ocean.OceanField.installedSeed()
            + "  ENABLED=" + com.EyeOfHarmonyBuffer.sim.ocean.OceanField.ENABLED);
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p285_report.txt"), "UTF-8");
        int cell = PlateField.PLATE_CELL;
        double day = Double.parseDouble(System.getProperty("eoh.day", "0"));
        double th = Atmosphere.theta(day);
        final GyreRow.BandedWind band = new GyreRow.BandedWind(TAU0, ZC);
        GyreRow.WindCurl real = new GyreRow.WindCurl() {
            @Override public double at(int x, int z) { return realCurl(x, z, cell, th); }
        };

        say("P285：A2/A3 的真实大气风重测（GRAD=" + GRAD/1000 + " km，day=" + day + " 即 Theta 相位）");
        say(String.format(LF, "  PLATE_CELL=%d  Z_CYCLE=%d  maxRow=%d  旋度格点步长 %d km（%d 点，x=-24M..+24M）",
            cell, ZC, new GyreRow.Params().maxRow, CLAT/1000, NLAT));
        say("");

        // ================= A. 受控对照：同一批行，占位风 vs 真实大气风 =================
        say("A. 西边界流受控对照（同一批 (lat,x) 行，唯一变量 = 风应力旋度的来源）");
        double[] wBand = new double[4000], wReal = new double[4000];
        double[] pBand = new double[4000], pReal = new double[4000];
        int nb = 0, nr = 0, nPair = 0;
        double[] ratio = new double[4000];
        int nratio = 0;
        double spBand = 0, spReal = 0; int nsBand = 0, nsReal = 0;
        long t0 = System.nanoTime();
        for (int li = 0; li < 34; li++) {
            int latSign = li < 17 ? 1 : -1;
            int latDeg = 5 + (li % 17) * 5;
            int z = (int) (latSign * latDeg / 90.0 * (ZC / 2));
            for (int q = 0; q < 8; q++) {
                int x = (q * 2_300_000 + li * 97_000) % 9_000_000;
                GyreRow.Params pB = params();
                GyreRow.Row rb = GyreRow.solve(x, z, SEED, cell, band, pB);
                GyreRow.Params pR = params();
                GyreRow.Row rr = GyreRow.solve(x, z, SEED, cell, real, pR);
                if (!rb.valid || !rr.valid) continue;
                if (rb.eastX >= pB.maxRow - 20_000 || rb.westX <= -pB.maxRow + 20_000) continue;
                if ((rb.eastX - rb.westX) < 2 * Math.PI * pB.deltaAt(z)) continue;
                wBand[nb] = rb.westBandMean * 1000.0;
                wReal[nr] = rr.westBandMean * 1000.0;
                pBand[nb] = band100(rb);
                pReal[nr] = band100(rr);
                nb++; nr++;
                if (wBand[nb-1] > 0.01) {
                    ratio[nratio++] = wReal[nr-1] / wBand[nb-1];
                }
                spBand += (rb.eastX - rb.westX) / 1e6; nsBand++;
                spReal += (rr.eastX - rr.westX) / 1e6; nsReal++;
            }
        }
        double secs = (System.nanoTime() - t0) / 1e9;
        say(String.format(LF, "   合格行 %d（每纬度 8 个 x 起点，纬度 ±5..±85 步长 5）", nb));
        say(String.format(LF, "   平均海盆宽度 %.1f Mm（两风场应逐位相同：%.1f vs %.1f）",
            spBand / Math.max(1, nsBand), spBand / Math.max(1, nsBand), spReal / Math.max(1, nsReal)));
        say("");
        say(String.format(LF, "   %-10s %7s %9s %9s %9s %9s %9s %9s", "口径", "n", "p10", "p25", "p50", "p75", "p90", "max"));
        stat("占位风 past", wBand, nb);
        stat("真实风 past", wReal, nr);
        stat("占位风 p100km", pBand, nb);
        stat("真实风 p100km", pReal, nr);
        say("");
        double[] rt = Arrays.copyOf(ratio, nratio);
        Arrays.sort(rt);
        say(String.format(LF, "   配对比值 真实/占位（past）：n=%d  p10 %.2f  **p50 %.2f**  p90 %.2f", nratio,
            rt[(int)(0.10*nratio)], rt[nratio/2], rt[(int)(0.90*nratio)]));
        int pb75 = 0, pr75 = 0;
        for (int i = 0; i < nb; i++) if (wBand[i] >= 75) pb75++;
        for (int i = 0; i < nr; i++) if (wReal[i] >= 75) pr75++;
        say(String.format(LF, "   >=75 mm/s 占比：占位风 %d/%d = %.0f%%   真实风 %d/%d = %.0f%%", pb75, nb, pb75*100.0/nb, pr75, nr, pr75*100.0/nr));
        say(String.format(LF, "   （A 段耗时 %.0f s，含真实旋度格点）", secs));
        say("");

        // ================= B. 东边界沿岸层（真实大气，大样本） =================
        say("B. 东边界沿岸层（真实大气风应力，大样本）");
        int NM = 120, NQ = 6;
        double[] tab = new double[NM * NQ * 3];
        int nt = 0, capped = 0, nsp = 0;
        double[] dirEq = new double[NM * NQ * 3];
        int nd = 0;
        double[] eastAll = new double[NM * NQ];
        int neAll = 0;
        double[] tauRow = new double[NM];
        for (int r = 0; r < NM; r++) {
            int z = (int) ((r + 0.5) / NM * ZC);
            double lat = WorldContract.latOf(z, ZC);
            double s = 0; int c = 0;
            for (int q = 0; q < NQ; q++) {
                GyreRow.Params p = params();
                GyreRow.Row row = GyreRow.solve((q * 2_300_000 + r * 97_000) % 9_000_000, z, SEED, cell, band, p);
                if (!row.valid) continue;
                if (row.eastX >= p.maxRow - 20_000 || row.westX <= -p.maxRow + 20_000) continue;
                if ((row.eastX - row.westX) < 2 * Math.PI * p.deltaAt(z)) continue;
                for (int k = 1; k <= 3; k++) {
                    int xx = row.eastX - k * 25_000;
                    double[] uv0 = Atmosphere.windAt(xx, z, SEED, cell, th, GRAD);
                    if (Math.hypot(uv0[0], uv0[1]) >= 24.5) capped++;
                    nsp++;
                    double tz = Atmosphere.windStress(xx, z, SEED, cell, th, GRAD)[1];
                    s += tz; c++;
                    double eq = Math.toDegrees(lat) >= 0 ? -tz : tz;
                    if (Math.abs(lat) < Math.toRadians(48)) dirEq[nd++] = eq;
                }
            }
            tauRow[r] = c > 0 ? s / c : 0;
        }
        int tot = 0;
        say(String.format(LF, "   采样：%d 条纬线 x %d 个 x 起点，近岸 3 点/行；合格样点 %d", NM, NQ, nsp));
        say(String.format(LF, "   撞 U_MAX 上限比例 %d/%d = %.0f%%", capped, nsp, nsp > 0 ? capped*100.0/nsp : 0));
        double[] e2 = Arrays.copyOf(dirEq, nd);
        Arrays.sort(e2);
        int pos = 0; for (double v : e2) if (v > 0) pos++;
        say(String.format(LF, "   |lat|<48 向赤道分量：中位 %+.5f Pa  正比例 %d/%d = %.0f%%", nd>0?e2[nd/2]:0, pos, nd, nd>0?pos*100.0/nd:0));
        double[] hc = CoastalLayer.steadySmoothed(tauRow, (double) ZC / NM);
        say("");
        say(String.format(LF, "   %-9s %8s %11s %11s %11s", "纬度", "样本", "沿岸层mm/s", "斯维尔德鲁普", "合计"));
        for (int r = 0; r < NM; r += 6) {
            int z = (int) ((r + 0.5) / NM * ZC);
            double lat = WorldContract.latOf(z, ZC);
            double f = WorldContract.coriolis(lat);
            double hcv = hc[Math.min(NM - 1, (int) ((double) z / ZC * NM))];
            double cb = CoastalLayer.eastBandContribution(hcv, f, W) * 1000;
            double sv = band.at(0, z) / (1025.0 * H_T * WorldContract.betaForLatitude(lat, ZC)) * 1000;
            eastAll[neAll++] = cb + sv;
            say(String.format(LF, "   %-9.1f %8s %11.2f %11.2f %11.2f", Math.toDegrees(lat),
                "-", cb, sv, cb + sv));
        }
        say("");
        say("   东带 >= 20 mm/s 的纬线比例（沿岸层 + 斯维尔德鲁普）：");
        int cnt20 = 0, cntAll = 0;
        double[] ea = Arrays.copyOf(eastAll, neAll);
        Arrays.sort(ea);
        for (double v : ea) { cntAll++; if (Math.abs(v) >= 20) cnt20++; }
        say(String.format(LF, "     %d/%d = %.0f%%   p10 %.1f p50 %.1f p90 %.1f  max %.1f mm/s",
            cnt20, cntAll, cntAll>0?cnt20*100.0/cntAll:0, ea[(int)(0.1*cntAll)], ea[cntAll/2], ea[(int)(0.9*cntAll)], ea[cntAll-1]));
        say("");
        say("C. 成本：A 段的真实旋度格点缓存条目 = " + CACHE.size() + " 条纬度（每条 " + NLAT + " 点 x 4 次 windStress）");
        rep.close();
    }

    static GyreRow.Params params() {
        GyreRow.Params p = new GyreRow.Params();
        p.h = H; p.rhoH = 1025.0 * H_T; p.aH = A_H; p.zCycle = ZC;
        return p;
    }

    static double band100(GyreRow.Row r) {
        int q = Math.min(r.n, (int) (W / r.h));
        double s = 0;
        for (int i = 0; i < q; i++) s += r.v[i];
        return Math.abs(s / q * 1000.0);
    }

    /** 真实大气风应力的旋度，从 50 km 格点插值。 */
    static double realCurl(int x, int z, int cell, double th) {
        double[] lat = lattice(z, cell, th);
        double f = (x + 24_000_000.0) / CLAT;
        int i = (int) Math.floor(f);
        if (i < 0) return lat[0];
        if (i >= NLAT - 1) return lat[NLAT - 1];
        double t = f - i;
        return lat[i] * (1 - t) + lat[i + 1] * t;
    }

    static synchronized double[] lattice(int z, int cell, double th) {
        Integer key = Integer.valueOf(z);
        double[] a = CACHE.get(key);
        if (a != null) return a;
        a = new double[NLAT];
        int g = GRAD;
        for (int i = 0; i < NLAT; i++) {
            int x = (int) ((long) (i - NLAT / 2) * CLAT);
            double tyE = Atmosphere.windStress(x + g, z, SEED, cell, th, g)[1];
            double tyW = Atmosphere.windStress(x - g, z, SEED, cell, th, g)[1];
            double txN = Atmosphere.windStress(x, z + g, SEED, cell, th, g)[0];
            double txS = Atmosphere.windStress(x, z - g, SEED, cell, th, g)[0];
            a[i] = (tyE - tyW) / (2.0 * g) - (txN - txS) / (2.0 * g);
        }
        CACHE.put(key, a);
        return a;
    }

    static void stat(String name, double[] v, int n) {
        double[] s = Arrays.copyOf(v, n);
        Arrays.sort(s);
        say(String.format(LF, "   %-14s %7d %9.2f %9.2f %9.2f %9.2f %9.2f %9.2f", name, n,
            s[(int)(0.10*n)], s[(int)(0.25*n)], s[n/2], s[(int)(0.75*n)], s[(int)(0.90*n)], s[n-1]));
    }

    static void say(String s) { System.out.println("[P285] " + s); rep.println("[P285] " + s); }
}
