package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanField;
import com.EyeOfHarmonyBuffer.sim.ocean.SeaSurfaceTemp;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P663 -- §447 的结构上限：模型的热成风闭合在热带能给出多大的经向 SST 异常？
//   对比口径 = COBE-SST2 1991-2020 的【纬向平均已扣除】区域异常（caliber 2），
//   因为风应力旋度的季节循环只能制造**经向**结构，不能制造纬向平均的季节循环。
public class P663 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P663] " + s); System.out.println("[P663] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p663_report.txt"), "UTF-8");
        say("P663: 热带经向 SST 异常的结构上限（热成风闭合 vs 观测 caliber 2）");
        EarthRef.install();
        OceanField.install(SEED);
        OceanField.PHASE_SEASONAL = false;
        SimClimate.clearCache();
        SimTerrain.seedOf(SEED);

        say("");
        say("段 1 解行用的 dT/dz（本世界自己的纬向平均海平面温度的经向梯度，K/m）");
        say("     纬度  dT/dz(K/m)  1000km温差(K) | T'(v=0.02)  T'(0.05)  T'(0.10)  T'(0.20)  [m/s 表层流速]");
        for (double latd = 2.5; latd <= 45.0; latd += 5.0) {
            int z = zOfLat(latd);
            double dTdz = (Atmosphere.zonalMeanSeaLevelK(WorldContract.latOf(z + 50_000))
                         - Atmosphere.zonalMeanSeaLevelK(WorldContract.latOf(z - 50_000))) / 100_000.0;
            say(String.format(LF, "     %5.1f  %+11.4e  %+11.3f | %+9.4f %+9.4f %+9.4f %+9.4f",
                latd, dTdz, dTdz * 1.0e6,
                SeaSurfaceTemp.anomaly(0.02 * SeaSurfaceTemp.SURF_FACTOR, dTdz),
                SeaSurfaceTemp.anomaly(0.05 * SeaSurfaceTemp.SURF_FACTOR, dTdz),
                SeaSurfaceTemp.anomaly(0.10 * SeaSurfaceTemp.SURF_FACTOR, dTdz),
                SeaSurfaceTemp.anomaly(0.20 * SeaSurfaceTemp.SURF_FACTOR, dTdz)));
        }

        say("");
        say("段 2 模型年平区域异常 vs 观测 caliber 2 年平（纬向平均已扣）—— 量级核对");
        say("     区域        | 模型 SST'(K) | 观测 ANN'(K) | 模型/观测");
        Object[][] BX = {
            {"孟加拉湾", 88.5, 19.3, 1.73}, {"阿拉伯海", 62.0, 15.0, 0.26},
            {"南海", 115.0, 15.0, 0.61}, {"索马里外海", 52.0, 5.0, -0.74},
            {"西撒外海", -20.0, 26.0, -2.42}, {"中西大西洋", -40.0, 15.0, -1.24}
        };
        for (int b = 0; b < BX.length; b++) {
            int x = xOfLon((Double) BX[b][1]), z = zOfLat((Double) BX[b][2]);
            double m = Atmosphere.sstAnom(x, z);
            double o = (Double) BX[b][3];
            say(String.format(LF, "     %-11s | %+11.4f | %+11.2f | %6.1f%%", BX[b][0], m, o, 100.0 * m / o));
        }

        say("");
        say("段 3 季节摆动 A/B：模型（逐相位）vs 观测 caliber 2 的 JJA-DJF");
        say("     区域        | 模型 JJA-DJF(K) OFF | ON | 观测 JJA'-DJF'(K) | 比值 ON/观测");
        double[][] OBS = {{1.38},{ -0.38},{1.64},{-0.99},{-1.79},{-0.63}};
        for (int b = 0; b < BX.length; b++) {
            int x = xOfLon((Double) BX[b][1]), z = zOfLat((Double) BX[b][2]);
            OceanField.PHASE_SEASONAL = false; SimClimate.clearCache();
            double off = Atmosphere.sstAnom(x, z, 0.0) - Atmosphere.sstAnom(x, z, Math.PI);
            OceanField.PHASE_SEASONAL = true; SimClimate.clearCache();
            double on = Atmosphere.sstAnom(x, z, 0.0) - Atmosphere.sstAnom(x, z, Math.PI);
            double o = OBS[b][0];
            say(String.format(LF, "     %-11s | %+19.4f | %+.4f | %+16.2f | %+6.3f",
                BX[b][0], off, on, o, on / o));
        }
        OceanField.PHASE_SEASONAL = false;
        SimClimate.clearCache();
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}