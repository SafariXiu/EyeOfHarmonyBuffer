package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P649 -- §435 两件事：
//  (a) 修好 P648 的 divU 列（DIAG 是 ThreadLocal 引用，会被邻居调用覆盖 -- 我的仪器缺陷）；
//  (b) 【水汽来向距离 fetch】的 go/no-go：从每点沿风【逆推】直到海面，量它在陆上走了多远。
//      沙漠应当在陆上走得很远；季风区应当在海洋近旁。这两个量都只来自模型自己的场。
public class P649 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P649] " + s); System.out.println("[P649] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static final double COL = PrecipField.RHO_AIR * PrecipField.H_MOIST;
    static final double STEP = 50_000.0;       // 逆推步长 50 km
    static final double MAXF = 3_000_000.0;    // 最多逆推 3000 km
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }
    static final double[][] BOX = {{70, 120, 15, 35}, {0, 30, 20, 35}};
    static final String[] NM = {"亚洲  ", "撒哈拉"};

    /** 沿风逆推：返回 {fetch 距离, 是否找到海}。全部用模型自己的风场与海陆掩膜。 */
    static double[] fetch(int x0, int z0, long sd, int cell, double th) {
        double x = x0, z = z0;
        for (double d = 0; d < MAXF; d += STEP) {
            double[] uv = Atmosphere.windAt((int) Math.round(x), (int) Math.round(z), sd, cell, th, GRAD);
            double sp = Math.hypot(uv[0], uv[1]);
            if (sp < 1.0e-3) return new double[]{d, 0};        // 静风：停在原地
            x -= uv[0] / sp * STEP;                            // 逆着风走
            z -= uv[1] / sp * STEP;
            z = Math.max(0, Math.min(WorldContract.MAX_D, z));
            if (!PlateField.isLandWithCell((int) Math.round(x), (int) Math.round(z), sd, cell))
                return new double[]{d, 1};                     // 到海了
        }
        return new double[]{MAXF, 0};
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p649_report.txt"), "UTF-8");
        say("P649: (a) divU 三列修正  (b) 水汽来向距离 fetch 的 go/no-go");
        EarthRef.install();
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        say(String.format(LF, "  StationaryWave.ENABLED=%s（假 ⇒ d[2]==d[0]）  COL=%.1f  逆推步长=%.0f km",
            StationaryWave.ENABLED, COL, STEP / 1000));
        say("");
        say("     季节  盒子   |  d[0]原divU     d[1]波项     d[2]总divU  |  来向距离(km)  到海率");
        for (int s = 0; s < 2; s++) {
            double th = (s == 0) ? thS : thW;
            for (int b = 0; b < 2; b++) {
                double s0 = 0, s1 = 0, s2 = 0, sf = 0, sr = 0; long n = 0;
                for (double latd = BOX[b][2] + 2.5; latd <= BOX[b][3]; latd += 2.5)
                    for (int c = 0; c < 72; c++) {
                        double lon = (c + 0.5) * 5.0;
                        if (lon < BOX[b][0] || lon > BOX[b][1]) continue;
                        int x = xOfLon(lon), z = zOfLat(latd);
                        PrecipField.mmPerDay(x, z, sd, cell, th, GRAD);
                        double[] d = PrecipField.DIAG.get();
                        double a0 = d[0], a1 = d[1], a2 = d[2];   // 立刻取出，避免被后续调用覆盖
                        double[] f = fetch(x, z, sd, cell, th);
                        s0 += a0; s1 += a1; s2 += a2; sf += f[0]; sr += f[1]; n++;
                    }
                say(String.format(LF, "     %s  %s | %+.3e  %+.3e  %+.3e |  %10.0f   %5.1f%%",
                    (s == 0 ? "JJA" : "DJF"), NM[b], s0 / n, s1 / n, s2 / n, sf / n, 100.0 * sr / n));
            }
        }
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
