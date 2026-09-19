package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.CoastalLayer;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P660 -- §446 季风源区海温的 go/no-go：季节的【沿岸/地转】信号有多大、方向对不对。
//   OceanField 现在对 4 个相位取平均 => 年平。这里**逐相位**算 tauS / hc / vJet，看季节摆动。
public class P660 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P660] " + s); System.out.println("[P660] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }
    static final double[] PH = {0.0, Math.PI/2, Math.PI, 3*Math.PI/2};

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p660_report.txt"), "UTF-8");
        say("P660: 季风源区的季节沿岸信号（逐相位，未经年平）");
        EarthRef.install();
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        // 三个区域：孟加拉湾 / 阿拉伯海 / 西撒哈拉外海
        double[][] R = {{88.5, 19.3}, {62.0, 15.0}, {-20.0, 26.0}};
        String[] RN = {"孟加拉湾", "阿拉伯海", "西撒外海"};
        say("");
        say("     区域      |  相位      tauS(N/m2)   hc(m)      vJet(m/s)   curl(1/s)");
        for (int r = 0; r < R.length; r++) {
            int x = xOfLon(R[r][0]), z = zOfLat(R[r][1]);
            boolean land = PlateField.isLandWithCell(x, z, sd, cell);
            double f = WorldContract.coriolis(WorldContract.latOf(z));
            double[] t = CoastalLayer.coastTangent(x, z, sd, cell);
            double sum = 0;
            for (int q = 0; q < 4; q++) {
                double th = PH[q];
                double[] ts = Atmosphere.windStress(x, z, sd, cell, th, GRAD);
                double tauS = ts[0] * t[0] + ts[1] * t[1];
                double hc = CoastalLayer.hcLocal(tauS, f);
                double vj = CoastalLayer.jetPeak(hc, f);
                // curl：与 OceanField.curlAtmos 同式
                double[] e = Atmosphere.windStress(x + GRAD, z, sd, cell, th, GRAD);
                double[] w = Atmosphere.windStress(x - GRAD, z, sd, cell, th, GRAD);
                double[] nn = Atmosphere.windStress(x, z + GRAD, sd, cell, th, GRAD);
                double[] ss = Atmosphere.windStress(x, z - GRAD, sd, cell, th, GRAD);
                double cu = ((e[1] - w[1]) - (nn[0] - ss[0])) / (2.0 * GRAD);
                sum += tauS;
                say(String.format(LF, "     %-9s |  %.2fpi  %+10.3e  %8.1f  %+9.4f  %+10.3e%s",
                    RN[r], th / Math.PI, tauS, hc, vj, cu, (land ? "   (陆地!)" : "")));
            }
            say(String.format(LF, "     %-9s |  年平    %+10.3e   （OCeanField 用的就是这个）", RN[r], sum / 4.0));
        }
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
