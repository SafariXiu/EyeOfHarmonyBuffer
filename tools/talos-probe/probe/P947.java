package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*; import java.util.*;

// P947 -- <kappa> 重测：P420 口径, 但地形是 TalosField (§567 之后的当前生产地形).
public class P947 {
    static final int WORLD = 1022228679;
    static final int CELL  = PlateField.PLATE_CELL;
    static final int NLAT  = 19;
    static final int NW    = 8;
    static final int NZ    = 6;
    static final int NP    = 2001;
    static final int SPAN  = 40_000_000;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P947] " + s); rep.flush(); System.out.println("[P947] " + s); System.out.flush(); }

    public static void main(String[] a) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p947_report.txt"), "UTF-8");
        long seed = SimTerrain.seedOf(WORLD);
        say("P947: <kappa> 重测 (P420 口径, 当前 TalosField 地形)");
        say(String.format(LF, "  worldSeed=%d  seed=%d  PLATE_CELL=%d", WORLD, seed, CELL));
        say(String.format(LF, "  NLAT=%d NW=%d NZ=%d NP=%d  => 每纬 %d 点, 总计 %d 点",
            NLAT, NW, NZ, NP, NW * NZ * NP, NLAT * NW * NZ * NP));
        say(String.format(LF, "  KAPPA_MEAN(现行) = %.4f", Atmosphere.KAPPA_MEAN));
        say("");
        double[] gwK = new double[NW], gwL = new double[NW], gwW = new double[NW];
        double[] latK = new double[NLAT], latL = new double[NLAT];
        double sK = 0, sL = 0, sW = 0;
        say(String.format(LF, "  %5s %10s %10s %10s %8s", "lat", "kappa", "landFrac", "winSD", "cosW"));
        long t0 = System.nanoTime();
        for (int i = 0; i < NLAT; i++) {
            double latDeg = i * 5.0;
            int z0 = (int) (latDeg / 90.0 * WorldContract.MAX_D);
            double cw = Math.cos(Math.toRadians(latDeg));
            double sumK = 0, sumL = 0; long n = 0;
            double[] thisW = new double[NW];
            for (int w = 0; w < NW; w++) {
                int x0 = -SPAN / 2 + w * (SPAN / NW);
                int x1 = x0 + SPAN / NW;
                double wk = 0, wl = 0; long wn = 0;
                for (int zl = 0; zl < NZ; zl++) {
                    int z = z0 + (zl - (NZ - 1) / 2) * 5_000;
                    for (int k = 0; k < NP; k++) {
                        int x = x0 + (int) ((long) (x1 - x0) * k / (NP - 1));
                        wk += Atmosphere.kappaAt(x, z, seed, CELL);
                        wl += PlateField.isLandWithCell(x, z, seed, CELL) ? 1.0 : 0.0;
                        wn++;
                    }
                }
                thisW[w] = wk / wn;
                gwK[w] += cw * thisW[w]; gwL[w] += cw * (wl / wn); gwW[w] += cw;
                sumK += wk; sumL += wl; n += wn;
            }
            latK[i] = sumK / n; latL[i] = sumL / n;
            sK += cw * latK[i]; sL += cw * latL[i]; sW += cw;
            double m = 0; for (int w = 0; w < NW; w++) m += thisW[w]; m /= NW;
            double v = 0; for (int w = 0; w < NW; w++) v += (thisW[w] - m) * (thisW[w] - m);
            say(String.format(LF, "  %5.0f %10.4f %10.4f %10.4f %8.4f", latDeg, latK[i], latL[i], Math.sqrt(v / (NW - 1)), cw));
        }
        say(String.format(LF, "  (耗时 %.1f s)", (System.nanoTime() - t0) / 1e9));
        say("");
        // 每个窗口自己的全球面积加权均值 => 8 个独立估计 => SE
        double sw = 0; double[] gm = new double[NW];
        for (int w = 0; w < NW; w++) { gm[w] = gwK[w] / gwW[w]; sw += gm[w]; }
        sw /= NW;
        double vv = 0; for (int w = 0; w < NW; w++) vv += (gm[w] - sw) * (gm[w] - sw); vv /= (NW - 1);
        double se = Math.sqrt(vv / NW);
        double gl = 0, gwl = 0;
        for (int w = 0; w < NW; w++) { gl += gwL[w] / NW; }
        say("  ---- 三个独立口径 ----");
        say(String.format(LF, "  (1) kappa 场, cos 面积加权全球 <kappa> = %.4f  (窗口间 SD=%.4f, SE=%.4f)", sw, Math.sqrt(vv), se));
        say(String.format(LF, "  (2) isLand 面积加权全球陆地占比          = %.4f  (%.2f%%)", gl, 100 * gl));
        double smp = 0; for (int i = 0; i < NLAT; i++) smp += latK[i]; smp /= NLAT;
        double sml = 0; for (int i = 0; i < NLAT; i++) sml += latL[i]; sml /= NLAT;
        say(String.format(LF, "  (3) 不做 cos 加权的简单平均              = %.4f  (陆地占比 %.4f)", smp, sml));
        say("");
        say("  8 个窗口各自的全球均值: " + Arrays.toString(gm));
        say("");
        double km = Atmosphere.KAPPA_MEAN;
        say(String.format(LF, "  === 对照 ==="));
        say(String.format(LF, "  KAPPA_MEAN(现行, 旧地形实测) = %.4f", km));
        say(String.format(LF, "  本次 TalosField 实测          = %.4f +- %.4f", sw, se));
        say(String.format(LF, "  差 (实测 - 现行)              = %+.4f   (%.1f%%)", sw - km, 100 * (sw - km) / km));
        say(String.format(LF, "  是否超出 3 SE                 = %s", (Math.abs(sw - km) > 3 * se) ? "是 => 常量已过期" : "否"));
        say("");
        // 逐纬剖面: 平还是不平?
        double mk = 0; for (int i = 0; i < NLAT; i++) mk += latK[i]; mk /= NLAT;
        double sd = 0; for (int i = 0; i < NLAT; i++) sd += (latK[i] - mk) * (latK[i] - mk); sd = Math.sqrt(sd / (NLAT - 1));
        double mnt = Double.MAX_VALUE, mxt = -Double.MAX_VALUE;
        for (int i = 0; i < NLAT; i++) { mnt = Math.min(mnt, latK[i]); mxt = Math.max(mxt, latK[i]); }
        say(String.format(LF, "  逐纬剖面: 简单均值 %.4f  SD %.4f  范围 %.4f ~ %.4f", mk, sd, mnt, mxt));
        say(String.format(LF, "  (旧地形的说法是「平的, 19 个纬度全在 0.304~0.341, 无一超过 2sigma」)"));
        say("DONE");
        rep.flush(); System.out.println("JAVA_EXIT=0");
    }
}
