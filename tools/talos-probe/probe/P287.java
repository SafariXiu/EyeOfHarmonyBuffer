package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.CoastalLayer;
import com.EyeOfHarmonyBuffer.sim.ocean.GyreRow;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Locale;

/**
 * P287：A3 的季节方向诊断 —— **气压带迁移幅度 CELL_MIGRATION 的扫描**。
 *
 * <p>P286 实测：北半球东带在 day=182（北半球冬至）**变成向极**（+97.5 mm/s @10 度），
 * 而真实加州流/加那利流全年向赤道。本探针扫 CELL_MIGRATION，看它是不是根因。
 */
public class P287 {

    static final int SEED = 1022228679;
    /** ⚠ 口径修正（§559，模板 P442:47）：收 {@code long seed} 的入口传本值；收 {@code int worldSeedInt} 的入口仍传裸 {@code SEED}。本次未改任何判据/阈值/输出行。 */
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final int ZC = WorldContract.Z_CYCLE;
    static final double TAU0 = 0.0685, H_T = 4000.0, A_H = 1.9e4, H = 5000.0, W = 100_000.0;
    static final int GRAD = 500_000;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    static final int NM = 60, NQ = 4;
    static int[] eastX = new int[NM * NQ];
    static int[] eastZ = new int[NM * NQ];
    static boolean[] ok = new boolean[NM * NQ];

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p287_report.txt"), "UTF-8");
        int cell = PlateField.PLATE_CELL;
        GyreRow.BandedWind band = new GyreRow.BandedWind(TAU0, ZC);
        say("P287：A3 季节方向 vs 气压带迁移幅度 CELL_MIGRATION");
        say("  真实锚点：北太平洋高压/亚速尔高压中心 1 月约 32 度、7 月约 36~37 度 ⇒ 迁移幅度约 2~4 度");
        say("");

        // ---- 先把行解好（海盆两端只依赖地形，与风无关），后面复用 ----
        int nOk = 0;
        for (int r = 0; r < NM; r++) {
            int z = (int) ((r + 0.5) / NM * ZC);
            for (int q = 0; q < NQ; q++) {
                int i = r * NQ + q;
                GyreRow.Params p = new GyreRow.Params();
                p.h = H; p.rhoH = 1025.0 * H_T; p.aH = A_H; p.zCycle = ZC;
                GyreRow.Row row = GyreRow.solve((q * 2_300_000 + r * 2_119_000) % 9_000_000, z, SD, cell, band, p);
                if (!row.valid) continue;
                if (row.eastX >= p.maxRow - 20_000 || row.westX <= -p.maxRow + 20_000) continue;
                if ((row.eastX - row.westX) < 2 * Math.PI * p.deltaAt(z)) continue;
                eastX[i] = row.eastX; eastZ[i] = z; ok[i] = true; nOk++;
            }
        }
        say(String.format(LF, "  复用的合格行 %d（%d 纬线 x %d x 起点）", nOk, NM, NQ));
        say("");
        say(String.format(LF, "  %-8s %-8s %8s %8s %8s %8s %8s %8s %8s", "迁移度", "季节", "lat10", "lat20", "lat30", "lat45", "-10", "-20", "-30"));
        final double saved = Atmosphere.CELL_MIGRATION;
        double[] migs = {0, 2, 4, 6, 8, 12};
        for (int mi = 0; mi < migs.length; mi++) {
            Atmosphere.CELL_MIGRATION = Math.toRadians(migs[mi]);
            for (int si = 0; si < 4; si++) {
                double day = si * WorldContract.DAYS_PER_YEAR / 4.0;
                double th = Atmosphere.theta(day);
                double[] tauRow = new double[NM];
                int[] cnt = new int[NM];
                for (int r = 0; r < NM; r++) {
                    double s = 0; int c = 0;
                    for (int q = 0; q < NQ; q++) {
                        int i = r * NQ + q;
                        if (!ok[i]) continue;
                        for (int k = 1; k <= 3; k++) {
                            s += Atmosphere.windStress(eastX[i] - k * 25_000, eastZ[i], SD, cell, th, GRAD)[1];
                            c++;
                        }
                    }
                    tauRow[r] = c > 0 ? s / c : 0;
                    cnt[r] = c;
                }
                double[] hc = CoastalLayer.steadySmoothed(tauRow, (double) ZC / NM);
                StringBuilder sb = new StringBuilder();
                for (int latDeg : new int[]{10, 20, 30, 45, -10, -20, -30}) {
                    int zs = WorldContract.zOfLat(latDeg);
                    int za = WorldContract.zOfLat(Math.abs((double) latDeg));
                    double lat = WorldContract.latOf(zs, ZC);
                    double f = WorldContract.coriolis(lat);
                    double hcv = hc[Math.max(0, Math.min(NM - 1, (int) ((double) za / ZC * NM)))];
                    sb.append(String.format(LF, " %8.1f", CoastalLayer.eastBandContribution(hcv, f, W) * 1000));
                }
                say(String.format(LF, "  %-8s %-8s%s", migs[mi] + "", "d" + (int) day, sb.toString()));
            }
            say("");
        }
        Atmosphere.CELL_MIGRATION = saved;

        // ---- 近岸风的向赤道比例（按季节） ----
        say("B. 近岸风「向赤道分量 > 0」的比例（|lat| 10~45 度，全部合格行）");
        say(String.format(LF, "  %-8s %-8s %8s %10s", "迁移度", "季节", "正比例", "中位 Pa"));
        for (int mi = 0; mi < migs.length; mi++) {
            Atmosphere.CELL_MIGRATION = Math.toRadians(migs[mi]);
            for (int si = 0; si < 4; si++) {
                double day = si * WorldContract.DAYS_PER_YEAR / 4.0;
                double th = Atmosphere.theta(day);
                int pos = 0, n = 0;
                double[] v = new double[4000];
                for (int i = 0; i < NM * NQ; i++) {
                    if (!ok[i]) continue;
                    double lat = WorldContract.latOf(eastZ[i], ZC);
                    if (Math.abs(Math.toDegrees(lat)) < 10 || Math.abs(Math.toDegrees(lat)) > 45) continue;
                    for (int k = 1; k <= 3; k++) {
                        double tz = Atmosphere.windStress(eastX[i] - k * 25_000, eastZ[i], SD, cell, th, GRAD)[1];
                        double eq = Math.toDegrees(lat) >= 0 ? -tz : tz;
                        if (eq > 0) pos++;
                        v[n++] = eq;
                    }
                }
                java.util.Arrays.sort(v, 0, n);
                say(String.format(LF, "  %-8s %-8s %7.0f%% %10.5f", migs[mi] + "", "d" + (int) day, pos * 100.0 / Math.max(1, n), v[n / 2]));
            }
            say("");
        }
        Atmosphere.CELL_MIGRATION = saved;
        rep.close();
    }

    static void say(String s) { System.out.println("[P287] " + s); rep.println("[P287] " + s); }
}
