package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P445：**10 度纬线上的假辐合对降水的影响**（审计发现 D1 的下游后果）。
 *
 * <p>P444 实测：v 是分段常数，在每条 10 度纬度线上阶跃 0.4~3.0 m/s、赤道 20.35 m/s，
 * 5 度线上恰好为 0。{@link PrecipField} 的 divU 里含 dv/dz ⇒ 这些线上应出现假辐合/辐散。
 *
 * <p>本探针：在 z 上以细步长扫过 30 度线与赤道，打印 divU / wLoc（= −H_BL*divU）/ wEff / mmPerDay，
 * 看尖峰的宽度与高度，并与「10 度线之外」的基线对照。
 */
public class P445 {

    static final int SEED = 1022228679;
    /** ⚠ 口径修正（§559，模板 P442:47）：收 {@code long seed} 的入口传本值；收 {@code int worldSeedInt} 的入口仍传裸 {@code SEED}。本次未改任何判据/阈值/输出行。 */
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final int ZC = WorldContract.Z_CYCLE;
    static final int CELL = PlateField.PLATE_CELL;
    static final int GRAD = 500_000;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    static void say(String s) { rep.println("[P445] " + s); System.out.println("[P445] " + s); }

    static double divU(int x, int z, double th) {
        double[] ux = Atmosphere.windAt(x + GRAD, z, SD, CELL, th, GRAD);
        double[] uw = Atmosphere.windAt(x - GRAD, z, SD, CELL, th, GRAD);
        double[] un = Atmosphere.windAt(x, z + GRAD, SD, CELL, th, GRAD);
        double[] us = Atmosphere.windAt(x, z - GRAD, SD, CELL, th, GRAD);
        return (ux[0] - uw[0]) / (2.0 * GRAD) + (un[1] - us[1]) / (2.0 * GRAD);
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p445_report.txt"), "UTF-8");
        say("P445：10 度纬线上的假辐合 -> 降水尖峰（D1 的下游）");
        say(String.format(LF, "  SEED=%d  GRAD=%d km  H_BL=%.0f m  W_LOC_MAX=%.2e m/s  UPWIND_STEP=%.0f km",
            SEED, GRAD / 1000, Atmosphere.H_BL, PrecipField.W_LOC_MAX, PrecipField.UPWIND_STEP / 1000));
        say("");

        int x = 0;
        for (int L : new int[]{-30, 0, 30}) {
            int zc = (int) (L / 90.0 * (ZC / 2));
            say(String.format(LF, "  === 跨 %d 度线（z = %d，线两侧各扫 20 个采样）===", L, zc));
            say(String.format(LF, "    %-12s %12s %13s %13s %13s %13s", "z 偏移 m", "divU 1/s", "wLoc(未限)", "wLoc(限幅)", "wEff m/s", "mm/day"));
            for (int dz = -20; dz <= 20; dz++) {
                int z = zc + dz;
                double d = divU(x, z, 0.0);
                double wl = -Atmosphere.H_BL * d;
                double wlc = Math.max(-PrecipField.W_LOC_MAX, Math.min(PrecipField.W_LOC_MAX, wl));
                double lat = WorldContract.latOf(z);
                double k = Atmosphere.kappaAt(x, z, SD, CELL);
                double elev = PlateField.elevationWithCell(x, z, SD, CELL);
                double tSl = Atmosphere.tZonalMean(lat) + Atmosphere.seasonalAnomaly(lat, k, 0.0);
                double hUp = Math.max(0.0, elev);
                double q = PrecipField.moisture(tSl, k > 0.0 ? hUp : 0.0, k);
                double w = PrecipField.wEff(lat, 0.0, d);
                if (dz % 2 == 0 || Math.abs(dz) <= 2)
                    say(String.format(LF, "    %-12d %12.4e %13.3e %13.3e %13.3e %13.4f", dz, d, wl, wlc, w,
                        PrecipField.mmPerDay(x, z, SD, CELL, 0.0, GRAD)));
            }
            say("");
        }

        say("  === 对照：同一 x 上一条**不在 10 度网格上**的纬线（15 度）===");
        int z15 = (int) (15 / 90.0 * (ZC / 2));
        for (int dz = -4; dz <= 4; dz += 2) {
            say(String.format(LF, "    z 偏移 %-4d  divU = %12.4e    mm/day = %13.4f", dz,
                divU(x, z15 + dz, 0.0),
                PrecipField.mmPerDay(x, z15 + dz, SD, CELL, 0.0, GRAD)));
        }
        say("");
        say("  === divU 的「台阶」有多大：跨 10 度线前后的 divU 差（1 m 分辨率）===");
        for (int L : new int[]{-60, -30, 0, 30, 60}) {
            int zc = (int) (L / 90.0 * (ZC / 2));
            double d0 = divU(x, zc - 1, 0.0), d1 = divU(x, zc + 1, 0.0);
            say(String.format(LF, "    %+4d 度: divU %+.4e -> %+.4e   跳变 %.4e 1/s   (wLoc 跳变 %.3e m/s)",
                L, d0, d1, Math.abs(d1 - d0), Math.abs(-Atmosphere.H_BL * (d1 - d0))));
        }
        rep.close();
        System.out.println("JAVA_EXIT=0");
    }
}
