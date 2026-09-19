package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

// P610 -- 诊断：地球掩膜下撒哈拉的 divU 从哪一项来？
public class P610 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P610] " + s); System.out.println("[P610] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }
    static final double[][] BOX = {{70, 120, 15, 35}, {0, 30, 20, 35}};
    static final String[] NM = {"亚洲", "撒哈拉"};

    static double[] scan(long sd, int cell, double th, int b) {
        double[] a = new double[6]; long n = 0;
        for (double latd = BOX[b][2] + 2.5; latd <= BOX[b][3]; latd += 2.5)
            for (int c = 0; c < 72; c++) {
                double lon = (c + 0.5) * 5.0;
                if (lon < BOX[b][0] || lon > BOX[b][1]) continue;
                int x = xOfLon(lon), z = zOfLat(latd);
                PrecipField.mmPerDay(x, z, sd, cell, th, GRAD);
                double du = PrecipField.DIAG.get()[0];
                double k = Atmosphere.kappaMemo(x, z, sd, cell);
                double lat = WorldContract.latOf(z);
                double pp = Atmosphere.pressureAnomaly(x, z, sd, cell, th);
                double cp = Atmosphere.cellPressure(lat, k, th);
                double sa = Atmosphere.seasonalAnomaly(lat, k, th);
                double ev = PlateField.elevationWithCell(x, z, sd, cell);
                a[0] += du; a[1] += k; a[2] += pp; a[3] += cp; a[4] += sa; a[5] += ev; n++;
            }
        for (int i = 0; i < 6; i++) a[i] /= n;
        return a;
    }

    static void show(String tag, long sd, int cell, double th) {
        SimClimate.clearCache();
        for (int b = 0; b < 2; b++) {
            double[] r = scan(sd, cell, th, b);
            say(String.format(LF, "      %-24s %-5s divU=%+.4e  k=%.3f  p=%+8.1f Pa  cellP=%+7.1f Pa  seas=%+7.2f K  elev=%.0f m",
                tag, NM[b], r[0], r[1], r[2], r[3], r[4], r[5]));
        }
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p610_report.txt"), "UTF-8");
        say("P610: 撒哈拉的 divU 归因（地球掩膜 + ETOPO1，JJA）");
        EarthRef.install();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double th = Atmosphere.theta(0.0);
        say(String.format(LF, "  KAPPA_MEAN=%.3f  CELL_GAIN=%.2f", Atmosphere.KAPPA_MEAN, Atmosphere.CELL_GAIN));
        say("");
        say("  [A] 拆 p 的两项");
        Atmosphere.PA_NO_CELL = false; Atmosphere.PA_NO_THERMAL = false; Atmosphere.PA_FIXED_KAPPA = Double.NaN;
        show("全开(基线)", sd, cell, th);
        Atmosphere.PA_NO_CELL = true; show("只留热力(关cell)", sd, cell, th);
        Atmosphere.PA_NO_CELL = false; Atmosphere.PA_NO_THERMAL = true; show("只留cell(关热力)", sd, cell, th);
        Atmosphere.PA_NO_THERMAL = false;
        say("");
        say("  [B] 杀掉 cellPressure 里 kappa 因子的水平梯度");
        for (double kf : new double[]{0.85, 0.5, 1.0}) {
            Atmosphere.PA_FIXED_KAPPA = kf;
            show(String.format(LF, "FIXED_KAPPA=%.2f", kf), sd, cell, th);
        }
        Atmosphere.PA_FIXED_KAPPA = Double.NaN;
        say("");
        say("  [C] cellPressure 的纬度因子（kappa 用 0.85）");
        say("      纬度   shifted   carrier(shifted)   tropicGate   (KAPPA_MEAN-0.85)");
        for (double latd = 5; latd <= 45; latd += 5) {
            double sh = latd - Math.toDegrees(Atmosphere.CELL_MIGRATION * Math.cos(-Atmosphere.CELL_LAG));
            say(String.format(LF, "      %4.0f   %7.2f   %+8.4f        %.4f       %+.3f",
                latd, sh, com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables.carrier(sh), 1.0, Atmosphere.KAPPA_MEAN - 0.85));
        }
        Atmosphere.PA_NO_CELL = false; Atmosphere.PA_NO_THERMAL = false;
        EarthRef.uninstall();
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}