package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.HadleyCell;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.StationaryWave;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

// P597 -- 地球验证层接入 ETOPO1 地形 + 「生产纯度」常驻门。
// 用户要求：「必须严格区分开，别把他们和我们自己的混在一起」。
public class P597 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P597] " + s); System.out.println("[P597] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;

    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }

    static final Object[][] PTS = {
        {29.65, 91.10, "西藏 拉萨一带", 3650.0},
        {30.00, 81.00, "喜马拉雅西段", 5000.0},
        {28.00, 86.90, "珠峰一带", 5500.0},
        {-16.0, -68.0, "玻利维亚高原", 3800.0},
        {40.00, -106.0, "落基山", 2500.0},
        {72.00, -40.0, "格陵兰冰盖", 2500.0},
        {25.00, 10.00, "撒哈拉中部", 400.0},
        {-5.00, -60.0, "亚马逊", 100.0},
    };

    static double worstDev(long sd, int cell) {
        double w = 0;
        for (Object[] p : PTS) {
            double ref = (double) p[3];
            double h = PlateField.elevationWithCell(xOfLon((double) p[1]), zOfLat((double) p[0]), sd, cell);
            w = Math.max(w, Math.abs(h - ref) / Math.max(1.0, ref));
        }
        return w;
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p597_report.txt"), "UTF-8");
        say("P597: ETOPO1 地形接入 + 生产纯度门");
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;

        say("");
        say("  [A] 生产纯度门（地球层必须完全不在场）");
        boolean pure = PlateField.MASK == null && PlateField.ELEV == null
                    && !StationaryWave.ENABLED && !StationaryWave.CLOSED_LOOP
                    && StationaryWave.Q_SCALE == 1.0 && StationaryWave.Q_PER_MMDAY == 1.0e-3
                    && !HadleyCell.ENABLED;
        say(String.format(LF, "      PlateField.MASK=%s  PlateField.ELEV=%s", PlateField.MASK, PlateField.ELEV));
        say(String.format(LF, "      StationaryWave.ENABLED=%s  CLOSED_LOOP=%s  Q_SCALE=%.2f  Q_PER_MMDAY=%.1e",
            StationaryWave.ENABLED, StationaryWave.CLOSED_LOOP, StationaryWave.Q_SCALE, StationaryWave.Q_PER_MMDAY));
        say(String.format(LF, "      HadleyCell.ENABLED=%s", HadleyCell.ENABLED));
        say(String.format(LF, "      => %s", pure ? "生产态纯净 ✓" : "★ 有东西留在场上 ✗"));

        // 记录未装地球层时的程序地形（用于后面证明装卸可逆）
        double[] before = new double[PTS.length];
        for (int i = 0; i < PTS.length; i++)
            before[i] = PlateField.elevationWithCell(xOfLon((double) PTS[i][1]), zOfLat((double) PTS[i][0]), sd, cell);

        say("");
        say(String.format(LF, "  [B] 未装地球层：与真实海拔最大相对偏差 = %.0f%%（P596 是 4219%%）", worstDev(sd, cell) * 100));

        say("");
        say("  [C] 装上地球层（掩膜 + ETOPO1 高程，同一 npz 同源）");
        EarthRef.install();
        say(String.format(LF, "      地球层头部：nlat=%d nlon=%d lat0=%.2f dlat=%.3f lon0=%.2f dlon=%.3f",
            EarthRef.nlat, EarthRef.nlon, EarthRef.lat0, EarthRef.dlat, EarthRef.lon0, EarthRef.dlon));
        say("      地点                          模型海拔(m)   真实量级(m)   相对偏差");
        for (Object[] p : PTS) {
            double ref = (double) p[3];
            double h = PlateField.elevationWithCell(xOfLon((double) p[1]), zOfLat((double) p[0]), sd, cell);
            say(String.format(LF, "      %-22s  %10.1f    %8.0f     %6.1f%%",
                (String) p[2], h, ref, 100.0 * Math.abs(h - ref) / Math.max(1.0, ref)));
        }
        say(String.format(LF, "      => 最大相对偏差 = %.0f%%", worstDev(sd, cell) * 100));

        say("");
        say("  [D] 掩膜 vs 高程 一致性（修掉「掩膜说陆地、高程说海底」）");
        long n = 0, bad = 0;
        for (double latd = -88; latd <= 88; latd += 2)
            for (int c = 0; c < 180; c++) {
                int x = xOfLon((c + 0.5) * 2.0), z = zOfLat(latd);
                boolean isL = EarthRef.isLand(x, z);
                boolean hPos = EarthRef.elevMeters(x, z) > 0;
                if (isL != hPos) bad++;
                n++;
            }
        say(String.format(LF, "      %d 个采样点中，isLand 与 (h>0) 不一致的 = %d  (%.3f%%)  %s",
            n, bad, 100.0 * bad / n, bad == 0 ? "完全一致 ✓" : "★ 仍不一致 ★"));

        say("");
        say("  [E] 卸载后必须逐位回到原状");
        EarthRef.uninstall();
        double worstBack = 0;
        for (int i = 0; i < PTS.length; i++) {
            double now = PlateField.elevationWithCell(xOfLon((double) PTS[i][1]), zOfLat((double) PTS[i][0]), sd, cell);
            if (Double.doubleToLongBits(now) != Double.doubleToLongBits(before[i])) worstBack = 1;
        }
        say(String.format(LF, "      MASK=%s  ELEV=%s   程序地形逐位还原 = %s",
            PlateField.MASK, PlateField.ELEV, worstBack == 0 ? "是 ✓" : "★ 否 ★"));
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
