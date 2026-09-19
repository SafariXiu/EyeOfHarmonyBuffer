package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.CoastalLayer;
import com.EyeOfHarmonyBuffer.sim.ocean.GyreRow;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/** P273：**沿岸风的纬向剖面** —— 看它为什么在 22.5 度以上塌掉。 */
public class P273 {

    static final int SEED = 1022228679;
    /** ⚠ 口径修正（§559，模板 P442:47）：收 {@code long seed} 的入口传本值；收 {@code int worldSeedInt} 的入口仍传裸 {@code SEED}。本次未改任何判据/阈值/输出行。 */
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final int ZC = WorldContract.Z_CYCLE;
    static final double TAU0 = 0.0685, H_T = 4000.0, A_H = 1.9e4, H = 5000.0;

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p273_report.txt"), "UTF-8");
        int cell = PlateField.PLATE_CELL;
        double th = Atmosphere.theta(0.0);
        int GRAD = 15_000, NM = 64;
        GyreRow.BandedWind wind = new GyreRow.BandedWind(TAU0, ZC);
        say("P273：沿岸风 tau_z 的纬向剖面（CELL_GAIN=%.2f）", Atmosphere.CELL_GAIN);
        say("  「向赤道为正」的约定：北半球副热带上升流有利风是**南向 = tau_z < 0**；南半球反过来。");
        say("");
        double[] tau = new double[NM];
        double sd = (double) ZC / NM;
        say(String.format(LF, "   %-8s %6s %9s %12s %12s %10s", "纬度", "真海岸", "kappa岸", "tau_z Pa", "向赤道分量", "h_c m"));
        for (int r = 0; r < NM; r++) {
            int z = (int) ((r + 0.5) / NM * ZC);
            GyreRow.Params p = new GyreRow.Params();
            p.h = H; p.rhoH = 1025.0 * H_T; p.aH = A_H; p.zCycle = ZC;
            GyreRow.Row row = GyreRow.solve((r * 311_000) % 9_000_000, z, SD, cell, wind, p);
            boolean real = row.valid && row.eastX < p.maxRow - 20_000 && row.westX > -p.maxRow + 20_000
                        && (row.eastX - row.westX) > 2 * Math.PI * p.deltaAt(z);
            double tz = 0; double kx = 0;
            if (real) {
                double s = 0; int c = 0;
                for (int k = 1; k <= 4; k++) {
                    s += Atmosphere.windStress(row.eastX - k * 60_000, z, SD, cell, th, GRAD)[1];
                    c++;
                }
                tz = s / c;
                kx = Atmosphere.kappaAt(row.eastX - 100_000, z, SD, cell);
            }
            tau[r] = tz;
            if (r % 4 == 0) {
                double lat = WorldContract.latOf(z, ZC);
                double eq = Math.toDegrees(lat) >= 0 ? -tz : tz;   // 北半球向赤道 = 向南 = 负
                say(String.format(LF, "   %-8.1f %6s %9.3f %12.5f %12.5f %10s",
                    Math.toDegrees(lat), real ? "Y" : "-", kx, tz, eq, "-"));
            }
        }
        double[] hc = CoastalLayer.steadySmoothed(tau, sd);
        say("");
        say("  累积 h_c（沿岸层）在若干纬度的值：");
        for (int r = 0; r < NM; r += 6) {
            int z = (int) ((r + 0.5) / NM * ZC);
            say(String.format(LF, "    纬度 %7.1f 度  h_c = %+9.2f m", Math.toDegrees(WorldContract.latOf(z, ZC)), hc[r]));
        }
        say("");
        int nEq = 0, nTot = 0;
        for (int r = 0; r < NM; r++) {
            int z = (int) ((r + 0.5) / NM * ZC);
            if (tau[r] == 0) continue;
            double lat = WorldContract.latOf(z, ZC);
            double eq = Math.toDegrees(lat) >= 0 ? -tau[r] : tau[r];
            if (Math.abs(lat) < Math.toRadians(45)) { nTot++; if (eq > 0) nEq++; }
        }
        say(String.format(LF, "  |纬度| < 45 度里有 {}/{} 个真海岸的沿岸风是「向赤道」（上升流有利）", nEq, nTot));
        rep.close();
    }

    static void say(String s) { System.out.println("[P273] " + s); rep.println("[P273] " + s); }

    static void say(String fmt, Object... a) {
        String s = String.format(LF, fmt, a);
        System.out.println("[P273] " + s); rep.println("[P273] " + s);
    }
}
