package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.CoastalLayer;
import com.EyeOfHarmonyBuffer.sim.ocean.GyreRow;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P263：**沿岸上升流层**（东边界流的真身）的验证 + 生产测量。
 *
 * <p>A：理想沿岸风下核对解析律。
 * <p>B：用 M3 的真实风场量**东边界附近的沿岸风应力**（这是沿岸层唯一的驱动源）。
 * <p>C：东带总量 = 斯维尔德鲁普（现有）+ 沿岸层（新增）。
 */
public class P263 {

    static final int SEED = 1022228679;
    static final int ZC = WorldContract.Z_CYCLE;
    static final int MAX_D = WorldContract.MAX_D;
    static final double TAU0 = 0.0685;
    static final double H_TOTAL = 4000.0;
    static final double A_H = 1.9e4;
    static final double GRID = 5000.0;
    static final double W = 100_000.0;
    static final double[] BANDS = {0.15, 0.25, 0.40, 0.55, 0.70, 0.85};
    static final int NB = BANDS.length;

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p263_report.txt"), "UTF-8");
        say("P263：沿岸上升流层 —— 验证 + 生产测量");
        say(String.format(LF, "  g'=%.3f  H1=%.0f  L_relax=%.0f km  R_d(f@30)=%.1f km  W=%.0f km",
            CoastalLayer.G_PRIME, CoastalLayer.H_THERMOCLINE, CoastalLayer.L_RELAX / 1000,
            CoastalLayer.rossbyRadius(WorldContract.coriolis(Math.toRadians(30))) / 1000, W / 1000));
        say("");

        // ---------- A. 理想沿岸风核对 ----------
        say("A. 理想核对：均匀沿岸风 tau_along = -0.1 Pa（向赤道），沿着 20,000 km 的海岸线");
        int NM = 80;
        double ds = (double) ZC / NM;
        double[] tau = new double[NM];
        for (int i = 0; i < NM; i++) tau[i] = -0.1;
        double[] hc = CoastalLayer.steadyPeriodic(tau, ds);
        double hmax = 0;
        for (double v : hc) if (Math.abs(v) > Math.abs(hmax)) hmax = v;
        double analytic = -0.1 * CoastalLayer.L_RELAX / (CoastalLayer.RHO * CoastalLayer.G_PRIME * CoastalLayer.H_THERMOCLINE);
        say(String.format(LF, "   h_c 最大 %.2f m（解析 tau*L_relax/(rho*g'*H1) = %.2f m）", hmax, analytic));
        say(String.format(LF, "   %-8s %8s %10s %12s %12s %14s", "纬度", "f", "R_d km", "v_max m/s", "T_E Sv", "东带贡献mm/s"));
        for (int bi = 0; bi < NB; bi++) {
            int z = (int) (BANDS[bi] * (ZC / 2));
            double lat = WorldContract.latOf(z, ZC);
            double f = WorldContract.coriolis(lat);
            double rd = CoastalLayer.rossbyRadius(f);
            double vmax = CoastalLayer.jetPeak(hmax, f);
            double sv = Math.abs(vmax) * rd * CoastalLayer.H_THERMOCLINE / 1e6;
            double band = CoastalLayer.eastBandContribution(hmax, f, W) * 1000;
            say(String.format(LF, "   %-8.1f %8.2e %10.1f %12.4f %12.2f %14.2f",
                Math.toDegrees(lat), f, rd / 1000, vmax, sv, band));
        }
        say("   对照：真实加州流系统 ~10 Sv、表层 0.2~0.5 m/s；A3 目标 30 mm/s");
        say("");

        // ---------- B. 生产风的沿岸分量 ----------
        say("B. 生产风场：东边界附近的沿岸风应力 tau_z（Pa）");
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        int cell = PlateField.PLATE_CELL;
        GyreRow.BandedWind wind = new GyreRow.BandedWind(TAU0, ZC);
        say(String.format(LF, "   %-8s %8s %14s %14s %14s %14s", "纬度", "n", "tau_z夏至", "tau_z冬至", "|tau|夏至", "东带斯维尔德rup"));
        double[] tauZ = new double[NM];
        for (int m = 0; m < NM; m++) {
            int z = (int) ((m + 0.5) / NM * ZC);
            double sum = 0, sumS = 0; int n = 0;
            for (int ix = 0; ix < 6; ix++) {
                int x0 = (ix * 1_800_000 + m * 137_000) % 11_000_000;
                GyreRow.Params p = new GyreRow.Params();
                p.h = GRID; p.rhoH = 1025.0 * H_TOTAL; p.aH = A_H; p.zCycle = ZC;
                GyreRow.Row r = GyreRow.solve(x0, z, SEED, cell, wind, p);
                if (!r.valid) continue;
                double gate = Math.PI * p.deltaAt(z);
                if ((r.eastX - r.westX) < 2 * gate) continue;
                int xe = r.eastX;
                for (int k = 1; k <= 3; k++) {
                    int xq = xe - k * 150_000;
                    double[] ts = Atmosphere.windStress(xq, z, SEED, cell, thS, 10_000);
                    sum += ts[1];
                    sumS += Math.hypot(ts[0], ts[1]);
                    n++;
                }
            }
            tauZ[m] = n > 0 ? sum / n : 0;
            if (m % 8 == 0 && n > 0) {
                int zz = (int) ((m + 0.5) / NM * ZC);
                say(String.format(LF, "   %-8.1f %8d %14.5f %14s %14.5f %14s",
                    Math.toDegrees(WorldContract.latOf(zz, ZC)), n, tauZ[m], "-", sumS / n, "-"));
            }
        }
        double meanAbs = 0; int nn = 0;
        for (double v : tauZ) { meanAbs += Math.abs(v); nn++; }
        say(String.format(LF, "   ⇒ 全球沿岸 |tau_z| 平均 = %.5f Pa（理想核对用的是 0.1 Pa）", meanAbs / nn));
        say("");

        // ---------- C. 东带总量 ----------
        say("C. 东带总量（负号 = 向赤道；正压斯维尔德鲁普 + 沿岸层）");
        double[] hcProd = CoastalLayer.steadyPeriodic(tauZ, ds);
        say(String.format(LF, "   %-8s %14s %14s %14s %14s", "纬度", "h_c m", "沿岸层mm/s", "斯维尔德鲁普mm/s", "合计mm/s"));
        for (int bi = 0; bi < NB; bi++) {
            int z = (int) (BANDS[bi] * (ZC / 2));
            double lat = WorldContract.latOf(z, ZC);
            double f = WorldContract.coriolis(lat);
            int m = (int) ((double) z / ZC * NM);
            double hcv = hcProd[Math.min(NM - 1, m)];
            double cb = CoastalLayer.eastBandContribution(hcv, f, W) * 1000;
            double curl = wind.at(0, z);
            double beta = WorldContract.betaForLatitude(lat, ZC);
            double svBand = curl / (1025.0 * H_TOTAL * beta) * 1000;
            say(String.format(LF, "   %-8.1f %14.2f %14.2f %14.4f %14.4f",
                Math.toDegrees(lat), hcv, cb, svBand, cb + svBand));
        }
        say("");
        say("D. 说明");
        say("  - 沿岸层唯一的驱动源是**东边界附近的沿岸风应力 tau_z**。");
        say("  - 斯维尔德鲁普东带与 Z_CYCLE 无关（已两次逐位验证）；沿岸层正比于沿岸长度。");
        say("  - 本探针的 tau_z 取自 M3 风场；若它太小，瓶颈就在**风场缺沿岸分量**（§29.7），不在本模块。");
        rep.close();
    }

    static void say(String s) { System.out.println("[P263] " + s); rep.println("[P263] " + s); }
}
