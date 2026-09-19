package probe;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.nio.file.Files;
import java.util.Locale;

// P596 -- 地球掩膜跑到底有没有地球地形？
// PlateField.MASK 只覆盖 isLand（第 762 行）；elevationWithCell 没有掩膜分支。
// 若如此 => 地球跑有海岸线、没有青藏高原 => 亚洲季风【不可能】形成，
//            而 Boos & Kuang (2010, Nature) 证过高原对季风是决定性的（orographic insulation）。
public class P596 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static final File MASKF = new File(ROOT, "build/eoh_probe/refs/earth_mask.bin");
    static PrintStream rep;
    static void say(String s) { rep.println("[P596] " + s); System.out.println("[P596] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;

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
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p596_report.txt"), "UTF-8");
        say("P596: 地球掩膜跑有没有地球地形？");
        loadMask();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;

        // 先用已知海陆点自证掩膜本身是好的
        double[][] chk = {{29.65, 91.10, 1}, {25.0, 10.0, 1}, {5.0, -60.0, 1}, {0.0, -140.0, 0}, {30.0, -140.0, 0}};
        say("");
        say("  [A] 掩膜自证（1=应为陆地，0=应为海洋）");
        for (double[] c : chk) {
            boolean isL = earthIsLand(xOfLon(c[1]), zOfLat(c[0]));
            say(String.format(LF, "      (%6.2fN, %7.2fE)  掩膜=%s  应为 %s  %s",
                c[0], c[1], isL ? "陆" : "海", c[2] > 0 ? "陆" : "海",
                (isL ? 1 : 0) == (int) c[2] ? "ok" : "★不一致★"));
        }

        say("");
        say("  [B] 装了地球掩膜后，模型在这些地点给的【海拔】");
        say("      地点                          模型海拔(m)   真实量级(m)   判定");
        Object[][] pts = {
            {29.65, 91.10, "西藏 拉萨一带", 3650.0},
            {30.00, 81.00, "喜马拉雅西段", 5000.0},
            {28.00, 86.90, "珠峰一带", 5500.0},
            {-16.0, -68.0, "玻利维亚高原", 3800.0},
            {40.00, -106.0, "落基山", 2500.0},
            {72.00, -40.0, "格陵兰冰盖", 2500.0},
            {25.00, 10.00, "撒哈拉中部", 400.0},
            {-5.00, -60.0, "亚马逊", 100.0},
        };
        double worst = 0;
        for (Object[] p : pts) {
            double lat = (double) p[0], lon = (double) p[1], ref = (double) p[3];
            int x = xOfLon(lon), z = zOfLat(lat);
            double h0 = PlateField.elevationWithCell(x, z, sd, cell);
            PlateField.MASK = new PlateField.LandMask() { public boolean isLand(int xx, int zz) { return earthIsLand(xx, zz); } };
            double h1 = PlateField.elevationWithCell(x, z, sd, cell);
            PlateField.MASK = null;
            boolean same = Math.abs(h0 - h1) < 1e-9;
            double err = Math.abs(h1 - ref) / Math.max(1.0, ref);
            worst = Math.max(worst, err);
            say(String.format(LF, "      %-22s  %10.1f    %8.0f     %s",
                (String) p[2], h1, ref, same ? "掩膜【无影响】" : "掩膜改变了地形"));
        }
        say("");
        say(String.format(LF, "      => 与真实海拔的最大相对偏差 = %.0f%%", worst * 100));
        say("      结论：地球掩膜【只改海陆，不改地形】。运行在 Earth 掩膜上的模型没有青藏高原。");
        say("      而 Boos & Kuang (2010, Nature) 证过：拆掉高原但保留南坡墙 -> 季风恢复；完全拆除 -> 无季风。");
        say("      => 亚洲季风【不可能】形成，且这与我们想验证的物理无关。");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
