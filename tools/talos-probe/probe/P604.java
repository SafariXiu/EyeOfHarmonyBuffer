package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.Radiation;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

// P604 -- bucket 的 beta = min(1, P/E_p) 到底是不是已经正确的空间型？
// 若已是（撒哈拉低、亚洲高），缺口就只是【没接到 q 上】，而不是 beta 本身错。
public class P604 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P604] " + s); System.out.println("[P604] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }

    static final double[][] BOX = {{70, 120, 15, 35}, {0, 30, 20, 35}};
    static final String[] NM = {"亚洲", "撒哈拉"};
    static final double[] OBS = {7.775, 0.105};

    /** {P, kappa, tSl, q, tsPot, Ep, beta, chv} */
    static double[] scan(long sd, int cell, double th, int b) {
        double[] acc = new double[8];
        long n = 0;
        for (double latd = BOX[b][2] + 2.5; latd <= BOX[b][3]; latd += 2.5)
            for (int c = 0; c < 72; c++) {
                double lon = (c + 0.5) * 5.0;
                if (lon < BOX[b][0] || lon > BOX[b][1]) continue;
                int x = xOfLon(lon), z = zOfLat(latd);
                double p = PrecipField.mmPerDay(x, z, sd, cell, th, GRAD);
                double[] d = PrecipField.DIAG.get();
                double k = d[7], tSl = d[6], q = d[5];
                double lat = WorldContract.latOf(z);
                double[] u = Atmosphere.windAt(x, z, sd, cell, th, GRAD);
                double sp = Math.hypot(u[0], u[1]);
                double chv = Radiation.bulkCoeff(k, sp);
                double dec = Atmosphere.subsolarLat(th);
                double absSolar = Radiation.insolation(lat, dec) * (1.0 - Radiation.albedo(k > 0.5, tSl));
                double tsPot = Radiation.skinTempLand(absSolar, tSl, q, chv, 1.0);
                double ep = Radiation.potentialEvapMmDay(tsPot, q, chv);
                double beta = Radiation.bucketBeta(p, ep);
                acc[0] += p; acc[1] += k; acc[2] += tSl; acc[3] += q;
                acc[4] += tsPot; acc[5] += ep; acc[6] += beta; acc[7] += chv;
                n++;
            }
        for (int i = 0; i < 8; i++) acc[i] /= n;
        return acc;
    }

    static void show(String tag, double[] a) {
        say(String.format(LF, "    %-8s P=%7.3f  kappa=%.3f  tSl=%.2fK  q=%.6f  tsPot=%.2fK  Ep=%7.3f  beta=%.4f  chv=%.4f",
            tag, a[0], a[1], a[2], a[3], a[4], a[5], a[6], a[7]));
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p604_report.txt"), "UTF-8");
        say("P604: bucket 的 beta 空间型是否正确？（地球掩膜 + ETOPO1）");
        EarthRef.install();
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);

        say("");
        say("  【JJA】");
        double[] aJ = scan(sd, cell, thS, 0); show("亚洲", aJ);
        double[] sJ = scan(sd, cell, thS, 1); show("撒哈拉", sJ);
        say("  【DJF】");
        double[] aW = scan(sd, cell, thW, 0); show("亚洲", aW);
        double[] sW = scan(sd, cell, thW, 1); show("撒哈拉", sW);

        say("");
        say("  ★ 判据：若 beta_撒哈拉 << beta_亚洲，则 beta 的空间型已经对了，缺口只是【没接到 q 上】。");
        say(String.format(LF, "    JJA: beta 亚洲 %.4f  撒哈拉 %.4f   比 %.3f", aJ[6], sJ[6], sJ[6] / aJ[6]));
        say(String.format(LF, "    DJF: beta 亚洲 %.4f  撒哈拉 %.4f   比 %.3f", aW[6], sW[6], sW[6] / aW[6]));
        say("");
        say("  §387 的独立判据：撒哈拉 beta<=0.1、亚洲 beta>=0.8");
        say(String.format(LF, "    JJA 实测：撒哈拉 %.4f %s   亚洲 %.4f %s",
            sJ[6], sJ[6] <= 0.1 ? "达标 ✓" : "未达标 ✗", aJ[6], aJ[6] >= 0.8 ? "达标 ✓" : "未达标 ✗"));
        say("");
        say(String.format(LF, "  ★ 若把 q 乘上 beta（即 q' = beta*q）会怎样："));
        say(String.format(LF, "    亚洲 JJA q %.6f -> %.6f (x%.3f)", aJ[3], aJ[3] * aJ[6], aJ[6]));
        say(String.format(LF, "    撒哈拉 JJA q %.6f -> %.6f (x%.3f)", sJ[3], sJ[3] * sJ[6], sJ[6]));
        EarthRef.uninstall();
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
