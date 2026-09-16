package probe;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Locale;

/** P288：**造山补丁落地校验** —— 复现子代理的校验和 076de5e9976c8857 与高程分布。 */
public class P288 {

    static final long SEED = 1022228679L;
    static final int DX = 20_000, NX = 1001, NZ = 1001;
    static final int CELL = PlateField.PLATE_CELL;
    static final int BIN = 100, LO = -11_000, HI = 9_000, NB = (HI - LO) / BIN + 1;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p288_report.txt"), "UTF-8");
        say("P288：造山补丁落地校验（源码已打补丁，不是 scratch）");
        say(String.format(LF, "  cell=%d  网格 %dx%d  dx=%d km", CELL, NX, NZ, DX/1000));
        double[] wAll = new double[NB], wLand = new double[NB], wLandNoAnt = new double[NB];
        double allW = 0, landW = 0, landNoAntW = 0, maxE = -1e18, minE = 1e18;
        long h = 0xcbf29ce484222325L;
        java.util.ArrayList<Double> landVals = new java.util.ArrayList<Double>();
        long t0 = System.nanoTime();
        for (int r = 0; r < NZ; r++) {
            int z = r * DX;
            double lat = WorldContract.latOf(z);
            double w = Math.cos(lat);
            boolean antarctic = Math.toDegrees(lat) < -60.0;
            for (int c = 0; c < NX; c++) {
                int x = c * DX;
                double e = PlateField.elevationWithCell(x, z, SEED, CELL);
                h ^= Double.doubleToLongBits(e);
                h *= 0x100000001B3L;
                int b = (int) Math.floor((e - LO) / BIN);
                if (b < 0) b = 0; if (b >= NB) b = NB - 1;
                wAll[b] += w; allW += w;
                if (e >= 0) {
                    wLand[b] += w; landW += w; landVals.add(e);
                    if (!antarctic) { wLandNoAnt[b] += w; landNoAntW += w; }
                    if (e > maxE) maxE = e;
                } else if (e < minE) minE = e;
            }
        }
        double ms = (System.nanoTime() - t0) / 1e6;
        say(String.format(LF, "  ELEV_CHECKSUM = %016x   （目标 076de5e9976c8857）%s", h,
            h == 0x076de5e9976c8857L ? "  ★ 逐位一致 ✓" : "  **不一致**"));
        say(String.format(LF, "  格点耗时 %.1f s  最高 %.1f m  最低 %.1f m  陆地占比 %.3f %%", ms/1000, maxE, minE, 100*landW/allW));
        for (int[] th : new int[][]{{1000,2000,3000,4000,5000,6000,7000}}) {
            for (int t : th) {
                say(String.format(LF, "  > %5d m 占陆地 %7.4f %%   （不含南极 %7.4f %%）", t, 100*frac(wLand, landW, t), 100*frac(wLandNoAnt, landNoAntW, t)));
            }
        }
        double[] lv = new double[landVals.size()];
        for (int i = 0; i < lv.length; i++) lv[i] = landVals.get(i);
        Arrays.sort(lv);
        say(String.format(LF, "  陆地高程 平均 %.1f  中位 %.0f  p25 %.0f  p75 %.0f  p90 %.0f  p99 %.0f",
            mean(lv), lv[lv.length/2], lv[(int)(0.25*lv.length)], lv[(int)(0.75*lv.length)], lv[(int)(0.90*lv.length)], lv[(int)(0.99*lv.length)]));
        rep.close();
    }

    static double frac(double[] w, double tot, int th) {
        double s = 0;
        for (int b = 0; b < NB; b++) if (LO + b * BIN >= th) s += w[b];
        return s / tot;
    }
    static double mean(double[] v) { double s = 0; for (double x : v) s += x; return s / v.length; }
    static void say(String s) { System.out.println("[P288] " + s); rep.println("[P288] " + s); }
}
