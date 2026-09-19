package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P664 -- §451：pzRef 进 v 的替代（§422 选项 D）实测。
//   判据一：赤道纬向平均 divU 回到 ~1e-6 量级且【保持负号】（§422 的目标）
//   判据二：25~30N 的符号是否修好（§422 实测那里是翻的）
//   判据三：两个盒子的 P 怎么变（§436 的教训：ω 更正确 ⇒ 热带更干）
public class P664 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P664] " + s); System.out.println("[P664] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }
    static final double[][] BOX = {{70, 120, 15, 35}, {0, 30, 20, 35}};
    static final String[] NM = {"亚洲", "撒哈拉"};

    static double[] box(long sd, int cell, double th, int b) {
        double sP = 0, sD = 0, sW = 0; long n = 0;
        for (double latd = BOX[b][2] + 2.5; latd <= BOX[b][3]; latd += 2.5)
            for (int c = 0; c < 72; c++) {
                double lon = (c + 0.5) * 5.0;
                if (lon < BOX[b][0] || lon > BOX[b][1]) continue;
                int x = xOfLon(lon), z = zOfLat(latd);
                double pm = PrecipField.mmPerDay(x, z, sd, cell, th, GRAD);
                double[] d = PrecipField.DIAG.get();
                sP += pm; sD += d[0]; sW += d[3]; n++;
            }
        return new double[]{sP / n, sD / n, sW / n};
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p664_report.txt"), "UTF-8");
        say("P664: pzRef 进 v 的替代（§451 / §422 选项 D）");
        EarthRef.install();
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0);

        say("");
        say("段 1 `v_zm`（由 w_zm 经连续性导出，m/s）与校验");
        say(String.format(LF, "     wbar_c = %+.4e m/s   （w_zm 的余弦加权均值，流函数闭合项）", Atmosphere.vzWbarC()));
        say("     纬度   v_zm(m/s)   -H_bl*div(v_zm)(m/s)   w_zm(m/s)");
        for (double latd = 0; latd <= 60; latd += 5) {
            say(String.format(LF, "     %5.1f  %+9.4f   %+12.4e          %+10.4e", latd,
                Atmosphere.vZmAt(latd), -Atmosphere.H_BL * Atmosphere.divVzmAt(latd), ZonalTables.wZm(latd)));
        }

        int[] MODE = {0, 1};
        String[] MN = {"模式 0（现状 pzRef 直接进 v）", "模式 1（选项 D：w_zm 经连续性导出）"};
        say("");
        say("段 2 纬向平均 divU（s^-1）—— 判据：赤道回到 ~-1e-6 量级且保持负号；25~30N 符号修好");
        say("     纬度  |  " + MN[0] + "  |  " + MN[1]);
        double[][] divTab = new double[13][2];
        int ri = 0;
        for (double latd = 0; latd <= 60; latd += 5) {
            for (int m = 0; m < 2; m++) {
                Atmosphere.PZREF_VZ_MODE = MODE[m];
                double s = 0; int cnt = 0;
                for (int c = 0; c < 72; c++) {
                    double lon = (c + 0.5) * 5.0;
                    PrecipField.mmPerDay(xOfLon(lon), zOfLat(latd), sd, cell, thS, GRAD);
                    s += PrecipField.DIAG.get()[0]; cnt++;
                }
                divTab[ri][m] = s / cnt;
            }
            say(String.format(LF, "     %5.1f  |  %+11.3e  |  %+11.3e", latd, divTab[ri][0], divTab[ri][1]));
            ri++;
        }

        say("");
        say("段 3 两个盒子（仪器与 P601/P646 一致：亚洲 70-120E/15-35N，撒哈拉 0-30E/20-35N）");
        say("     模式                        | JJA 亚洲 P  撒哈拉 P   比 | DJF 亚洲 P  撒哈拉 P   比");
        for (int m = 0; m < 2; m++) {
            Atmosphere.PZREF_VZ_MODE = MODE[m];
            SimClimate.clearCache(); SoilMoisture.invalidate(); StationaryWave.invalidate();
            double[] a = box(sd, cell, thS, 0), b = box(sd, cell, thS, 1);
            double[] c2 = box(sd, cell, Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0), 0);
            double[] d2 = box(sd, cell, Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0), 1);
            say(String.format(LF, "     %-27s | %8.3f %8.3f %6.2f | %8.3f %8.3f %6.2f", MN[m],
                a[0], b[0], a[0] / b[0], c2[0], d2[0], c2[0] / d2[0]));
            say(String.format(LF, "       （诊断）亚洲 divU %+.3e wEff %+.3e | 撒哈拉 divU %+.3e wEff %+.3e",
                a[1], a[2], b[1], b[2]));
        }
        Atmosphere.PZREF_VZ_MODE = 0;
        SimClimate.clearCache(); SoilMoisture.invalidate(); StationaryWave.invalidate();
        say("");
        say("观测锚：亚洲 JJA 7.667 / 撒哈拉 0.105（比 72.76）；DJF 0.763 / 0.392（比 1.95）");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}