package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.CoastalLayer;
import com.EyeOfHarmonyBuffer.sim.ocean.GyreRow;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P504 (D81): 度量 GyreRow.solve 的 maxRow 截断发生率，以及被截断那一侧
 * 由人工墙贡献的 vPeak 占比。
 *
 * <p>Pass A 只做几何普查（不调风，便宜）：扫 79 个 z x 5 个 x，
 * 用与生产 solve() 逐字相同的两个 while 找端点，判定端点是被陆地还是被
 * maxRow 上限截断的（判据 = 上限之外仍是海）。
 *
 * <p>Pass B 对截断行与同纬度对照行做完整 solve，给出 vPeak / psiMax /
 * vPeak 到两侧墙的距离 / 中段 |v| 均值（内区代理量）。
 *
 * <p>不改任何生产代码。口径与 P488 一致：suppressSst(true) + beginMemo()。
 */
public class P504 {

    static final int SEED = 1022228679;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    static void say(String s) { rep.println("[P504] " + s); rep.flush(); System.out.println("[P504] " + s); System.out.flush(); }

    static final int[] XS = {0, -4_000_000, 4_000_000, -8_000_000, 8_000_000};

    static double curlAt(int x, int z, long seed, int cell) {
        double c = 0;
        for (double th : OceanField.PH4) {
            double[] e = Atmosphere.windStress(x + OceanField.GRAD, z, seed, cell, th, OceanField.GRAD);
            double[] w = Atmosphere.windStress(x - OceanField.GRAD, z, seed, cell, th, OceanField.GRAD);
            double[] n = Atmosphere.windStress(x, z + OceanField.GRAD, seed, cell, th, OceanField.GRAD);
            double[] s = Atmosphere.windStress(x, z - OceanField.GRAD, seed, cell, th, OceanField.GRAD);
            c += ((e[1] - w[1]) - (n[0] - s[0])) / (2.0 * OceanField.GRAD) / OceanField.PH4.length;
        }
        return c;
    }

    static GyreRow.Params params() {
        GyreRow.Params p = new GyreRow.Params();
        p.h = OceanField.ROW_H; p.rhoH = CoastalLayer.RHO * OceanField.H_TOTAL; p.aH = OceanField.A_H;
        p.zCycle = WorldContract.Z_CYCLE;
        return p;
    }

    static final class Curl implements GyreRow.WindCurl {
        final long seed; final int cell;
        Curl(long s, int c) { seed = s; cell = c; }
        @Override public double at(int x, int z) { return curlAt(x, z, seed, cell); }
    }

    public static void main(String[] args) throws Exception {
        File dir = new File(ROOT, "build" + File.separator + "eoh_probe" + File.separator + "refs");
        dir.mkdirs();
        rep = new PrintStream(new File(dir, "P504_trunc.txt"), "UTF-8");
        say("=== P504 (D81) GyreRow maxRow 截断普查 ===");
        long seed = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        GyreRow.Params p0 = params();
        say(String.format(LF, "seedOf(%d)=%d  PLATE_CELL=%d  h=%d  maxRow=%d (=%.0f km 单侧扫描上限)",
            SEED, seed, cell, (int) p0.h, p0.maxRow, p0.maxRow / 1000.0));
        say("");

        // ---------- Pass A：几何普查 ----------
        int nTot = 0, nLand = 0, nInvalid = 0, nW = 0, nE = 0, nAny = 0;
        double widest = 0; int widestZ = 0, widestX = 0;
        double[] truncLat = new double[64];
        int[] truncZ = new int[64], truncX = new int[64];
        int nTrunc = 0;
        for (int z = -9_750_000; z <= 9_750_000; z += 250_000) {
            for (int x : XS) {
                nTot++;
                if (PlateField.isLandWithCell(x, z, seed, cell)) { nLand++; continue; }
                GyreRow.Params p = p0; if (!p.hasBeta()) p = p.withBeta(p.betaAt(z));
                int h = (int) p.h;
                int xw = x; while (xw > -p.maxRow && !PlateField.isLandWithCell(xw - h, z, seed, cell)) xw -= h;
                int xe = x; while (xe < p.maxRow && !PlateField.isLandWithCell(xe + h, z, seed, cell)) xe += h;
                int n = (xe - xw) / h + 1;
                if (n < 16) { nInvalid++; continue; }
                double wkm = (xe - xw) / 1000.0;
                if (wkm > widest) { widest = wkm; widestZ = z; widestX = x; }
                boolean wt = xw <= -p.maxRow && !PlateField.isLandWithCell(xw - h, z, seed, cell);
                boolean et = xe >= p.maxRow && !PlateField.isLandWithCell(xe + h, z, seed, cell);
                // 纯上限截断（不看上限外是否有陆）：用来分辨两种成因
                boolean capW = xw <= -p.maxRow, capE = xe >= p.maxRow;
                if (wt) nW++; if (et) nE++; if (wt || et) {
                    nAny++;
                    if (nTrunc < 64) { truncZ[nTrunc] = z; truncX[nTrunc] = x; truncLat[nTrunc] = Math.toDegrees(WorldContract.latOf(z)); nTrunc++; }
                }
                if (capW || capE) {
                    say(String.format(LF, "  [上限触发] x=%9d z=%9d lat=%7.3f  xw=%9d xe=%9d 宽=%8.1f km  capW=%s capE=%s 人工墙W=%s E=%s",
                        x, z, Math.toDegrees(WorldContract.latOf(z)), xw, xe, wkm, capW, capE, wt, et));
                }
            }
        }
        say("");
        say(String.format(LF, "PassA 采样 %d 点：陆地 %d、n<16 无效 %d、有效海盆 %d", nTot, nLand, nInvalid, nTot - nLand - nInvalid));
        say(String.format(LF, "  人工墙(上限外仍是海) 西侧 %d 处、东侧 %d 处；任一例 %d 处 (占有效海盆 %.3f%%)",
            nW, nE, nAny, 100.0 * nAny / Math.max(1, nTot - nLand - nInvalid)));
        say(String.format(LF, "  最宽海盆 %.1f km (x=%d z=%d lat=%.3f)", widest, widestX, widestZ, Math.toDegrees(WorldContract.latOf(widestZ))));
        say(String.format(LF, "  单侧扫描上限 %.0f km ⇒ 两侧合计可达 %.0f km；地球周长 40075 km 作参照", p0.maxRow / 1000.0, 2 * p0.maxRow / 1000.0));
        say("");

        // ---------- Pass B：完整 solve 对比 ----------
        Atmosphere.suppressSst(true);
        Atmosphere.beginMemo();
        say("--- PassB：对每个截断点做完整 solve（对照 = 同 z 的另一个 x）---");
        say(String.format(LF, "  %-9s %-9s %7s %9s %9s %8s %11s %11s %11s %9s %9s",
            "x", "z", "lat", "xw", "xe", "宽km", "vPeak", "psiMax", "wbcSpeed", "峰距Wkm", "峰距Ekm"));
        for (int i = 0; i < nTrunc; i++) {
            fullSolve(truncX[i], truncZ[i], seed, cell, p0, "TRUNC");
        }
        // 对照：同一 z，另外的 x
        say("");
        say("--- 对照（未被截断的同纬度行）---");
        for (int i = 0; i < Math.min(nTrunc, 12); i++) {
            for (int x : XS) {
                if (x == truncX[i]) continue;
                if (PlateField.isLandWithCell(x, truncZ[i], seed, cell)) continue;
                fullSolve(x, truncZ[i], seed, cell, p0, "ctrl");
                break;
            }
        }
        Atmosphere.suppressSst(false);
        say("");
        say("判读：若人工墙行的 vPeak 出现在距墙 1~2 个 delta 内、且远大于中段内区 |v|，");
        say("      则该 vPeak 是求解器自己造出来的（D81 成立）；若截断发生率本来就是 0，");
        say("      则 D81 是理论缺陷而非实际缺陷，修它不影响任何验收读数。");
        rep.flush(); rep.close();
        System.out.println("JAVA_EXIT=0");
    }

    static void fullSolve(int x, int z, long seed, int cell, GyreRow.Params p0, String tag) {
        GyreRow.Params p = p0; if (!p.hasBeta()) p = p.withBeta(p.betaAt(z));
        GyreRow.Row r = GyreRow.solve(x, z, seed, cell, new Curl(seed, cell), p);
        if (!r.valid) { say(String.format(LF, "  %-9d %-9d %7.3f   (无效 n=%d)", x, z, Math.toDegrees(WorldContract.latOf(z)), r.n)); return; }
        double h = r.h;
        int vk = 0; double vm = 0;
        int mid0 = r.n / 3, mid1 = 2 * r.n / 3; double midAcc = 0;
        for (int i = 0; i < r.n; i++) {
            double a = Math.abs(r.v[i]);
            if (a > vm) { vm = a; vk = i; }
            if (i >= mid0 && i <= mid1) midAcc += a;
        }
        double mid = midAcc / Math.max(1, mid1 - mid0 + 1);
        say(String.format(LF, "  %-9d %-9d %7.3f %9d %9d %8.1f %11.4g %11.4g %11.4g %9.1f %9.1f  %s%s  内区|v|均=%.4g",
            x, z, Math.toDegrees(WorldContract.latOf(z)), r.westX, r.eastX, (r.eastX - r.westX) / 1000.0,
            r.vPeak, r.psiMax, r.wbcSpeed, (r.westX + vk * h - r.westX) / 1000.0, (r.eastX - (r.westX + vk * h)) / 1000.0,
            tag, r.westTruncated ? " [W人工墙]" : "", mid) + (r.eastTruncated ? " [E人工墙]" : ""));
    }
}
