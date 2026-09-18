package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.nio.file.Files;
import java.util.Locale;

/**
 * P547 —— **模型里到底有没有季风？**（经向对比，而不是带平均）
 *
 * <p>为什么必须这样问：{@code divU} 在亚洲是强辐合、在撒哈拉是辐散，
 * **带平均会把两者抵消** ⇒ 副热带的带平均值答不出「有没有季风」。
 * 只有**同纬度、不同经度**的对比能回答。
 *
 * <p>驱动：地球真实掩膜（与 P542/P546 同一台仪器）⇒ 边界条件钉死。
 * 对照：地球观测的 JJA 降水在这些框里的量级（写死在代码里作**量级**参照，见下）。
 */
public class P547 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static final File MASKF = new File(ROOT, "build/eoh_probe/refs/earth_mask.bin");
    static PrintStream rep;
    static void say(String s) { rep.println("[P547] " + s); System.out.println("[P547] " + s); }

    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static final int SEED = 1022228679;

    static int NLAT, NLON;
    static double LAT0, DLAT, LON0, DLON;
    static byte[] LAND;

    static void loadMask() throws IOException {
        byte[] all = Files.readAllBytes(MASKF.toPath());
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(all).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        bb.position(4);
        NLAT = bb.getInt(); NLON = bb.getInt();
        LAT0 = bb.getDouble(); DLAT = bb.getDouble(); LON0 = bb.getDouble(); DLON = bb.getDouble();
        LAND = new byte[NLAT * NLON];
        bb.get(LAND);
    }

    static boolean earthIsLand(int x, int z) {
        double lat = Math.toDegrees(WorldContract.latOf(z, WorldContract.Z_CYCLE));
        double lon = (x / CIRC) * 360.0;
        lon -= Math.floor(lon / 360.0) * 360.0;
        int i = (int) Math.round((lat - LAT0) / DLAT);
        int j = (int) Math.round((lon - LON0) / DLON);
        if (i < 0) i = 0; if (i >= NLAT) i = NLAT - 1;
        j = ((j % NLON) + NLON) % NLON;
        return LAND[i * NLON + j] != 0;
    }

    /** 北纬 latDeg 对应的 z（分支 0 = N 升）。 */
    static int zOfLatN(double latDeg) {
        return (int) Math.round(latDeg / 90.0 * WorldContract.MAX_D);
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p547_report.txt"), "UTF-8");
        say("P547：模型里到底有没有季风？（经向对比，地球掩膜驱动）");
        say("  契约自证：Z_CYCLE=" + WorldContract.Z_CYCLE + "  TEMP=" + PlateField.TALOS_TERRAIN
            + "  PLATEAU_AMP=" + Atmosphere.PLATEAU_AMP);
        loadMask();
        PlateField.MASK = new PlateField.LandMask() {
            public boolean isLand(int x, int z) { return earthIsLand(x, z); }
        };
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        say("");

        // 纬向剖面：每条纬线上每 2.5 度经度采一个点
        double[] lats = {15, 20, 25, 30, 35, 40, 45};
        int NLONP = 144;
        double[][] profS = new double[lats.length][NLONP];
        double[][] profW = new double[lats.length][NLONP];
        double[][] landS = new double[lats.length][NLONP];
        for (int li = 0; li < lats.length; li++) {
            int z = zOfLatN(lats[li]);
            for (int c = 0; c < NLONP; c++) {
                int x = (int) Math.round((c + 0.5) * CIRC / NLONP);
                profS[li][c] = PrecipField.mmPerDay(x, z, sd, cell, thS, GRAD);
                profW[li][c] = PrecipField.mmPerDay(x, z, sd, cell, thW, GRAD);
                landS[li][c] = earthIsLand(x, z) ? 1.0 : 0.0;
            }
        }
        say("=== A. 北纬 30 度的经向剖面（JJA，每 5 度经度一个点，单位 mm/day）===");
        int li30 = 3;
        StringBuilder a = new StringBuilder("  经度:  ");
        StringBuilder b = new StringBuilder("  JJA :  ");
        for (int c = 0; c < NLONP; c += 2) {
            double lon = (c + 0.5) * 360.0 / NLONP;
            a.append(String.format(LF, "%4.0f", lon));
            b.append(String.format(LF, "%4.1f", profS[li30][c]));
        }
        say(a.toString());
        say(b.toString());
        say("");

        // 框：{lonLo, lonHi, latLo, latHi, 名字, 地球 JJA 观测参照（mm/day，量级）}
        Object[][] boxes = {
            {70.0, 120.0, 15.0, 35.0, "亚洲季风区 70-120E", 7.0},
            {0.0, 30.0, 20.0, 35.0, "撒哈拉 0-30E", 0.3},
            {250.0, 285.0, 25.0, 35.0, "美国南部 110-75W", 3.0},
            {150.0, 210.0, 25.0, 35.0, "北太平洋 150E-150W", 1.0},
            {300.0, 350.0, 25.0, 35.0, "北大西洋 60-10W", 2.0},
        };
        say("=== B. 关键框的平均降水（模型 JJA / DJF），与地球量级对照 ===");
        say("  框                        模型JJA   模型DJF   地球JJA(量级)   陆占比");
        for (Object[] bx : boxes) {
            double lo = (double) bx[0], hi = (double) bx[1];
            double la = (double) bx[2], hb = (double) bx[3];
            double ss = 0, sw = 0, sl = 0; long n = 0;
            for (int li = 0; li < lats.length; li++) {
                if (lats[li] < la || lats[li] > hb) continue;
                for (int c = 0; c < NLONP; c++) {
                    double lon = (c + 0.5) * 360.0 / NLONP;
                    if (lon < lo || lon > hi) continue;
                    ss += profS[li][c]; sw += profW[li][c]; sl += landS[li][c]; n++;
                }
            }
            if (n == 0) { say("  " + bx[4] + "  （无采样点）"); continue; }
            say(String.format(LF, "  %-24s %8.2f %9.2f %12.1f %10.0f%%",
                bx[4], ss / n, sw / n, (double) bx[5], 100.0 * sl / n));
        }
        say("");
        say("=== C. 判据：同纬度带里「陆上 vs 海上」的 JJA 差异（这是季风的定义性对比）===");
        for (int li = 0; li < lats.length; li++) {
            double land = 0, sea = 0; long nl = 0, ns = 0;
            for (int c = 0; c < NLONP; c++) {
                if (landS[li][c] > 0.5) { land += profS[li][c]; nl++; } else { sea += profS[li][c]; ns++; }
            }
            double lm = nl > 0 ? land / nl : 0, sm = ns > 0 ? sea / ns : 0;
            say(String.format(LF, "  %2.0fN  JJA: 陆 %6.2f (%3d 点)   海 %6.2f (%3d 点)   陆-海 %+6.2f   %s",
                lats[li], lm, nl, sm, ns, lm - sm, (lm - sm) > 0.5 ? "陆更湿（有季风样信号）" : "陆不更湿"));
        }
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
