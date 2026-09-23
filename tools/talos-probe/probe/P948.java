package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*; import java.util.*;

// P948 -- 逐纬带量 E 与 P/E (模型自己的表面通量), 陆/洋分开.
public class P948 {
    static final int WORLD = 1022228679;
    static final int CELL  = PlateField.PLATE_CELL;
    static final int GRAD  = 500_000;
    static final int NX    = 60;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P948] " + s); rep.flush(); System.out.println("[P948] " + s); System.out.flush(); }
    static final double MM = 86400.0 * 1000.0;

    public static void main(String[] a) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p948_report.txt"), "UTF-8");
        long seed = SimTerrain.seedOf(WORLD);
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        say("P948: 逐纬带的 E 与 P/E (模型自己的表面通量)  SEED=" + WORLD + "  CELL=" + CELL + "  GRAD=" + GRAD + "  NX=" + NX);
        say(String.format(LF, "  SHALLOW_FLOOR=%s  ALPHA_SH=%.3f  V_GUST=%.2f  RHO_AIR=%.4f",
            PrecipField.SHALLOW_FLOOR, PrecipField.ALPHA_SH, PrecipField.V_GUST, PrecipField.RHO_AIR));
        say(String.format(LF, "  DIAG 槽位: [8]=P(涡动前) [9]=P(涡动后) [10]=pSh(浅对流地板) [3]=wEff [5]=q [6]=tSl [7]=kappa"));
        say("");
        say(String.format(LF, "  %4s %7s | %9s %9s %9s | %9s %9s | %7s %7s | %8s",
            "lat", "land%", "P_preEd", "P_postEd", "P_floor", "P_final", "E", "P/E_all", "P/E_sea", "mfc"));
        for (int i = 1; i <= 17; i++) {
            double latDeg = i * 5.0;
            int z = (int) (latDeg / 90.0 * WorldContract.MAX_D);
            double[] acc = new double[10];  // 0 nAll 1 nSea 2 Ppre 3 Ppost 4 Pfloor 5 Pfinal 6 E 7 PpreSea 8 Esea 9 nland
            for (int s = 0; s < NX; s++) {
                int x = -SPANH() + (int) ((long) (2 * SPANH()) * s / NX) + 250_000;
                double k = Atmosphere.kappaMemo(x, z, seed, CELL);
                boolean sea = k <= 0.5;
                double pf = PrecipField.mmPerDay(x, z, seed, CELL, thW, GRAD);
                double[] d = PrecipField.DIAG.get();
                double pPre = d[8] * MM, pPost = d[9] * MM, pFl = d[10] * MM;
                double e = PrecipField.SHALLOW_FLOOR ? (d[10] / PrecipField.ALPHA_SH) * MM : 0.0;
                acc[0] += 1; if (sea) acc[1] += 1; else acc[9] += 1;
                acc[2] += pPre; acc[3] += pPost; acc[4] += pFl; acc[5] += pf; acc[6] += e;
                if (sea) { acc[7] += pPre; acc[8] += e; }
            }
            double n = acc[0], ns = Math.max(1, acc[1]);
            double pPre = acc[2] / n, pPost = acc[3] / n, pFl = acc[4] / n, pf = acc[5] / n, e = acc[6] / n;
            double mfc = PrecipField.eddyMfc(Math.toRadians(latDeg), thW);
            say(String.format(LF, "  %4.0f %6.1f%% | %9.3f %9.3f %9.3f | %9.3f %9.3f | %7.3f %7.3f | %+8.2f",
                latDeg, 100.0 * acc[9] / n, pPre, pPost, pFl, pf, e,
                e > 1e-9 ? pPost / e : Double.NaN, (acc[8] / ns) > 1e-9 ? (acc[7] / ns) / (acc[8] / ns) : Double.NaN, mfc));
        }
        say("");
        say("  列义: P_preEd=涡动前(纯 wEff 通道)  P_postEd=加涡动后  P_floor=ALPHA_SH*E  P_final=返回(max)  E=表面蒸发(由地板反解)");
        say("        P/E_all=全格点, P/E_sea=只海洋.  mm/day 单位.");
        say("  GPCP 观测(独立列, 冬=DJF for NH): 2.5~12.5 = 3.580 ; 27.5~37.5 = 2.391 ; 47.5~62.5 = 2.432");
        say("DONE");
        rep.flush(); System.out.println("JAVA_EXIT=0");
    }
    static int SPANH() { return WorldContract.MAX_D * 2; }
}
