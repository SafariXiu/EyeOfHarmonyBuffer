package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.HadleyCell;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.StationaryWave;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.nio.file.Files;
import java.util.Locale;

// P593 -- 我那些盒子到底测的是什么底质？
// 关键怀疑：P586/P589/P591 用的是【模型自己的随机地形】，而 §347 主判据（比 >= 10）
// 是定义在【地球掩膜】上的。两者可能不是一个东西。
public class P593 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static final File MASKF = new File(ROOT, "build/eoh_probe/refs/earth_mask.bin");
    static PrintStream rep;
    static void say(String s) { rep.println("[P593] " + s); System.out.println("[P593] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;

    static int NLAT, NLON; static double LAT0, DLAT, LON0, DLON; static byte[] LAND;
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

    static final double[][] BOX = {{70, 120, 15, 35}, {0, 30, 20, 35}, {250, 285, 25, 35}, {150, 210, 25, 35}};
    static final String[] NM = {"亚洲季风区", "撒哈拉", "美国南部", "北太平洋"};

    /** 返回 {P均值, 陆占比, 陆上P均值, 海上P均值, 地球掩膜下的P均值} */
    static double[] scan(long sd, int cell, double th) {
        double[] out = new double[BOX.length * 5];
        for (int b = 0; b < BOX.length; b++) {
            long n = 0, nL = 0, nS = 0; double s = 0, sL = 0, sS = 0, sM = 0; long nM = 0;
            for (double latd = BOX[b][2] + 2.5; latd <= BOX[b][3]; latd += 2.5) {
                int z = WorldContract.zOfLat(latd);
                for (int c = 0; c < 72; c++) {
                    double lon = (c + 0.5) * 5.0;
                    if (lon < BOX[b][0] || lon > BOX[b][1]) continue;
                    int x = (int) Math.round((c + 0.5) * CIRC / 72);
                    double p = PrecipField.mmPerDay(x, z, sd, cell, th, GRAD);
                    s += p; n++;
                    // 模型自己的海陆：用 kappa（0=纯海洋，1=纯陆地）
                    boolean landModel = Atmosphere.kappaMemo(x, z, sd, cell) > 0.5;
                    if (landModel) { sL += p; nL++; } else { sS += p; nS++; }
                    if (earthIsLand(x, z)) { sM += p; nM++; }
                }
            }
            out[b * 5 + 0] = s / n;
            out[b * 5 + 1] = (double) nL / n;
            out[b * 5 + 2] = nL == 0 ? Double.NaN : sL / nL;
            out[b * 5 + 3] = nS == 0 ? Double.NaN : sS / nS;
            out[b * 5 + 4] = nM == 0 ? Double.NaN : sM / nM;
        }
        return out;
    }

    static void show(String tag, double[] r) {
        say("  " + tag);
        for (int b = 0; b < BOX.length; b++)
            say(String.format(LF, "    %-6s P=%6.3f  模型陆占比=%5.1f%%  陆上P=%6.3f  海上P=%6.3f  地球陆地内P=%6.3f",
                NM[b], r[b*5], r[b*5+1]*100, r[b*5+2], r[b*5+3], r[b*5+4]));
        say(String.format(LF, "    => 全框比 %.3f   模型陆上比 %.3f   地球陆地内比 %.3f",
            r[0]/r[5], r[2]/r[7], r[4]/r[9]));
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p593_report.txt"), "UTF-8");
        say("P593: 盒子测的到底是什么底质？（模型随机地形 vs 地球掩膜）");
        loadMask();
        say(String.format(LF, "  地球掩膜: %dx%d  LAT0=%.1f DLAT=%.3f LON0=%.1f DLON=%.3f", NLAT, NLON, LAT0, DLAT, LON0, DLON));
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double th = Atmosphere.theta(0.0);
        say("");
        say(String.format(LF, "  现实参照（P578 的 TRUTH）：亚洲 7.775  撒哈拉 0.105  美南 3.595  北太 2.402  => 亚洲/撒哈拉 = %.1f", 7.775/0.105));
        say(String.format(LF, "  §347 主判据门槛 = 10"));
        say("");
        StationaryWave.ENABLED = false; HadleyCell.ENABLED = false;
        say(String.format(LF, "  [A] 模型自己的随机地形（seed=%d）", SEED));
        show("", scan(sd, cell, th));
        say("");
        say("  [B] 装地球掩膜驱动");
        PlateField.MASK = new PlateField.LandMask() {
            public boolean isLand(int x, int z) { return earthIsLand(x, z); }
        };
        // 换掩膜 => 缓存必须清
        com.EyeOfHarmonyBuffer.sim.runtime.SimClimate.clearCache();
        show("", scan(sd, cell, th));
        PlateField.MASK = null;
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
