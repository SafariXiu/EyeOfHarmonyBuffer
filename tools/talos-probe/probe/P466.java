package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.CoastalLayer;
import com.EyeOfHarmonyBuffer.sim.ocean.GyreRow;
import com.EyeOfHarmonyBuffer.sim.ocean.SeaSurfaceTemp;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P466：**第三步接线的口径前置测量** —— 海洋该由「合成纬向带风」还是「模型自己的风」驱动？
 *
 * <p>{@link GyreRow.WindCurl} 的 javadoc 写着「M3 完成后由真实大气层实现」，
 * 所以接模型自己的风**是项目原定意图**。但参考探针 P265/P292 用的是
 * {@code BandedWind(tau0=0.0685)} —— 一个**只有纬度依赖**的合成带风。
 *
 * <p>本探针把两者放在**同一批纬度行**上解，报：curl 剖面、psi/v 峰值、西/东带均、
 * 以及**代价**（每行要多少次 windStress）。
 */
public class P466 {

    static final int SEED = 1022228679;
    /** ⚠ 口径修正（§559，模板 P442:47）：收 {@code long seed} 的入口传本值；收 {@code int worldSeedInt} 的入口仍传裸 {@code SEED}。本次未改任何判据/阈值/输出行。 */
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final int ZC = WorldContract.Z_CYCLE;
    static final double TAU0 = 0.0685, H_TOTAL = 4000.0, A_H = 1.9e4, GRID = 5000.0;
    static final int GRAD = 500_000;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static final int CELL = PlateField.PLATE_CELL;

    static void say(String s) { rep.println("[P466] " + s); System.out.println("[P466] " + s); }

    /** 用**模型自己的风应力**算旋度：curl = d(tau_z)/dx - d(tau_x)/dz。 */
    static final class AtmosCurl implements GyreRow.WindCurl {
        final double theta;
        AtmosCurl(double theta) { this.theta = theta; }
        @Override public double at(int x, int z) {
            double[] e = Atmosphere.windStress(x + GRAD, z, SD, CELL, theta, GRAD);
            double[] w = Atmosphere.windStress(x - GRAD, z, SD, CELL, theta, GRAD);
            double[] n = Atmosphere.windStress(x, z + GRAD, SD, CELL, theta, GRAD);
            double[] s = Atmosphere.windStress(x, z - GRAD, SD, CELL, theta, GRAD);
            return (e[1] - w[1]) / (2.0 * GRAD) - (n[0] - s[0]) / (2.0 * GRAD);
        }
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p466_report.txt"), "UTF-8");
        say("P466：第三步接线的口径前置测量 —— 海洋由「合成带风」还是「模型自己的风」驱动？");
        say(String.format(LF, "  h=%.0f m  rhoH=%.0f  aH=%.1e  TAU0=%.4f  GRAD=%d km",
            GRID, CoastalLayer.RHO * H_TOTAL, A_H, TAU0, GRAD / 1000));
        say("");
        GyreRow.BandedWind band = new GyreRow.BandedWind(TAU0, ZC);
        int[] lats = {30, 45, 60, -45};
        say(String.format(LF, "  %-6s %-8s %6s %11s %11s %11s %11s %9s", "lat", "curlSrc", "n", "psiMax", "vPeak m/s", "westBand", "eastBand", "秒"));
        for (int latDeg : lats) {
            int z = (int) ((double) latDeg / 90.0 * (ZC / 2));
            for (int which = 0; which < 2; which++) {
                GyreRow.WindCurl wc = (which == 0) ? band : new AtmosCurl(Atmosphere.theta(0.0));
                GyreRow.Params p = new GyreRow.Params();
                p.h = GRID; p.rhoH = CoastalLayer.RHO * H_TOTAL; p.aH = A_H; p.zCycle = ZC;
                long t0 = System.nanoTime();
                GyreRow.Row row = GyreRow.solve(5_500_000, z, SD, CELL, wc, p);
                double dt = (System.nanoTime() - t0) / 1e9;
                if (!row.valid) { say(String.format(LF, "  %-6d %-8s  invalid", latDeg, which == 0 ? "band" : "atmos")); continue; }
                say(String.format(LF, "  %-6d %-8s %6d %11.3e %11.4f %11.3e %11.3e %9.2f",
                    latDeg, which == 0 ? "band" : "atmos", row.n, row.psiMax, row.vPeak, row.westBandMean, row.eastBandMean, dt));
            }
            // 两种 curl 的剖面差异（每隔 1/8 盆宽采一个点，用 band 行的两端）
            GyreRow.Params p = new GyreRow.Params();
            p.h = GRID; p.rhoH = CoastalLayer.RHO * H_TOTAL; p.aH = A_H; p.zCycle = ZC;
            GyreRow.Row rb = GyreRow.solve(5_500_000, z, SD, CELL, band, p);
            if (rb.valid) {
                AtmosCurl ac = new AtmosCurl(Atmosphere.theta(0.0));
                StringBuilder sb = new StringBuilder();
                for (int k = 1; k <= 7; k++) {
                    int x = rb.westX + (int) ((rb.eastX - rb.westX) * k / 8.0);
                    double cb = band.at(x, z), ca = ac.at(x, z);
                    sb.append(String.format(LF, " [%+.2e/%+.2e]", cb, ca));
                }
                say("         curl 逐点（带风/大气）:" + sb);
            }
        }
        say("");
        say("D. 海温异常的量级（用 band 行的 v，乘 SURF_FACTOR）");
        double dTdz = 0;
        for (int latDeg : new int[]{30, 45}) {
            int z = (int) ((double) latDeg / 90.0 * (ZC / 2));
            dTdz = (Atmosphere.tZonalMean(WorldContract.latOf(z + 50_000, ZC))
                  - Atmosphere.tZonalMean(WorldContract.latOf(z - 50_000, ZC))) / 100_000.0;
            say(String.format(LF, "  lat %+d: dT_zm/dz = %+.3e K/m", latDeg, dTdz));
        }
        say("");
        say("⚠ 记账：本探针只测量，**未改任何生产代码**。");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
