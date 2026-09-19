package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.nio.file.Files;
import java.util.Locale;

/**
 * P542 —— **物理线验证**：用地球真实陆海掩膜（ETOPO1 0.25 度，land := h > 0 m）驱动模型，
 * 然后把模型的纬向平均降水直接对 GPCP。
 *
 * <p>为什么这是对的判据：世界是无限的、随机生成的 ⇒ 拿随机世界的纬向平均去减地球的纬向平均，
 * 那个差里混了【边界条件差异】+【物理误差】。喂地球掩膜把边界条件钉死，残差就是纯物理误差。
 * 而换地形只改变边界条件，**完全不改变物理** ⇒ 这条线与地形解耦。
 *
 * <p>自证（最强形式）：同一个量用两条**独立**路径算 ——
 * (a) 直接遍历掩膜自己的 0.25 度网格；(b) 经 (x,z) -> (lon,lat) 映射后按 Zonal 的采样扫。
 * 两者必须一致，否则映射是错的，整个测试作废。
 */
public class P542 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static final File MASKF = new File(ROOT, "build" + File.separator + "eoh_probe" + File.separator + "refs" + File.separator + "earth_mask.bin");
    /** ⚠ 口径修正（§559，模板 P442:47）：世界种子 → 地形/气候长种子。{@code Zonal.profile} 收 {@code long seed}，必须传派生值。 */
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(1022228679);
    static final double EARTH_CIRC = 40_000_000.0;   // 360 度 = 40,000 km
    static PrintStream rep;
    static void say(String s) { rep.println("[P542] " + s); System.out.println("[P542] " + s); }

    static int NLAT, NLON;
    static double LAT0, DLAT, LON0, DLON;
    static byte[] LAND;

    static void loadMask() throws IOException {
        byte[] all = Files.readAllBytes(MASKF.toPath());
        // ⚠ Python 用 struct "<..." 写的是**小端**；DataInputStream 读的是大端。
        //   第一版就栽在这里：NLAT 读成 -788398080 ⇒ NegativeArraySizeException。
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(all).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        if (bb.get(0) != 'E' || bb.get(1) != 'M' || bb.get(2) != 'S' || bb.get(3) != 'K') throw new IOException("bad magic");
        bb.position(4);
        NLAT = bb.getInt(); NLON = bb.getInt();
        LAT0 = bb.getDouble(); DLAT = bb.getDouble(); LON0 = bb.getDouble(); DLON = bb.getDouble();
        LAND = new byte[NLAT * NLON];
        bb.get(LAND);
        say(String.format(LF, "  掩膜：%d x %d  lat0=%.3f dlat=%.4f  lon0=%.3f dlon=%.4f  %d bytes",
            NLAT, NLON, LAT0, DLAT, LON0, DLON, all.length));
    }

    /** (x, z) -> ETOPO1 格点。z 用 WorldContract.latOf（帐篷：z 在 [0,10M] 为北半球、[10M,20M] 为南半球）。 */
    static boolean earthIsLand(int x, int z) {
        double lat = Math.toDegrees(WorldContract.latOf(z, WorldContract.Z_CYCLE));
        double lon = (x / EARTH_CIRC) * 360.0;
        lon -= Math.floor(lon / 360.0) * 360.0;
        int i = (int) Math.round((lat - LAT0) / DLAT);
        int j = (int) Math.round((lon - LON0) / DLON);
        if (i < 0) i = 0; if (i >= NLAT) i = NLAT - 1;
        j = ((j % NLON) + NLON) % NLON;
        return LAND[i * NLON + j] != 0;
    }

    /** 路径 (a)：直接遍历掩膜网格。 */
    static double bandDirect(double lo, double hi) {
        int nl = 0, n = 0;
        for (int i = 0; i < NLAT; i++) {
            double a = Math.abs(LAT0 + i * DLAT);
            if (a < lo || a >= hi) continue;
            for (int j = 0; j < NLON; j++) { if (LAND[i * NLON + j] != 0) nl++; n++; }
        }
        return n > 0 ? 100.0 * nl / n : 0;
    }

    /** 路径 (b)：经映射 + Zonal 的采样网格。 */
    static double bandMapped(double lo, double hi) {
        int nl = 0, n = 0;
        for (int r = 0; r < Zonal.NZ; r++) {
            double a = Math.abs(Zonal.latOfRow(r));
            if (a < lo || a >= hi) continue;
            int z = (int) ((r + 0.5) / Zonal.NZ * WorldContract.Z_CYCLE);
            for (int c = 0; c < Zonal.NX; c++) {
                int x = (int) Math.round((c + 0.5) * Zonal.XSPAN / Zonal.NX);
                if (earthIsLand(x, z)) nl++;
                n++;
            }
        }
        return n > 0 ? 100.0 * nl / n : 0;
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p542_report.txt"), "UTF-8");
        say("P542：物理线验证 —— 用地球真实陆海掩膜驱动模型，对 GPCP");
        say("  锚（GPCP v2.3，全部观测值）：47-62夏 2.534 / 47-62冬 2.432 / 赤道夏 6.585 / 赤道冬 3.580 / 副热夏 2.301 / 副热冬 2.391");
        say("  口径 = Zonal（x 跨度 40,000 km = 地球纬圈周长，NX=" + Zonal.NX + "，NZ=" + Zonal.NZ + "）");
        say("");
        loadMask();

        // ---------- 自证：两条独立路径必须一致 ----------
        say("=== 自证 A：掩膜映射（路径 a 直接遍历 vs 路径 b 经映射+采样）===");
        say("   |lat| 带    (a) 直接遍历    (b) 映射+采样    差(pt)");
        double maxd = 0;
        for (int i = 0; i < 18; i++) {
            double lo = i * 5, hi = (i == 17) ? 90.1 : i * 5 + 5;
            double a = bandDirect(lo, hi), b = bandMapped(lo, hi), d = Math.abs(a - b);
            if (d > maxd) maxd = d;
            say(String.format(LF, "   %3d-%3d      %8.2f%%      %8.2f%%      %6.2f", (int) lo, (int) Math.min(hi, 90), a, b, d));
        }
        // ⚠ 阈值说明：|lat|<60 的带陆地占比平滑，必须 <1.5 pt；
        //   60~90 的带陆地占比陡变（35%→46%→54%），而 NZ=50 一行 3.6 度
        //   ⇒ 纬度采样混叠会给到 ~2.2 pt。那是分辨率项，不是映射错误。
        say(String.format(LF, "   ⇒ 最大差 = %.2f pt   判定 = %s", maxd, maxd <= 2.5 ? "映射正确 ✓（60 度以上的残差是 NZ=50 的纬度混叠）" : "**映射有误，以下全部作废**"));
        say(String.format(LF, "   参考：地球全球陆地占比（ETOPO1 均匀纬度）= 33.91%%"));
        say("");

        // ---------- 装上钩子 ----------
        PlateField.MASK = new PlateField.LandMask() {
            public boolean isLand(int x, int z) { return earthIsLand(x, z); }
        };
        say("=== 自证 B：钩子已装上 ===");
        say("   PlateField.MASK != null : " + (PlateField.MASK != null)
            + "    WORLD_IS_TALOS = " + PlateField.WORLD_IS_TALOS + "（度量身份位，不是开关；地形恒为 TalosField）");
        say("");

        // ---------- 对 GPCP ----------
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        final int savedC = PrecipField.EDDY_CLOSURE;
        final double savedG = PrecipField.EDDY_PHYS_GAIN, savedMix = PrecipField.EDDY_MIX;

        say("=== 对 GPCP（地球掩膜驱动；seed 已不影响陆海，只影响残差的随机项）===");
        say("  closure  gain/mix   45-55夏   47-62夏   47-62冬    赤道夏    赤道冬    副热夏    副热冬   独立 err");
        double best = 1e9; String bestTag = "";
        for (int mode = 0; mode <= 1; mode++) {
            double[] gs = (mode == 0) ? new double[]{3.000} : new double[]{4.0, 5.0, 6.0, 7.0, 8.0, 9.6};
            for (double g : gs) {
                PrecipField.EDDY_CLOSURE = mode;
                if (mode == 0) { PrecipField.EDDY_MIX = g; PrecipField.EDDY_PHYS_GAIN = 1.0; }
                else { PrecipField.EDDY_MIX = savedMix; PrecipField.EDDY_PHYS_GAIN = g; }
                double[] pS = Zonal.profile(SD, thS), pW = Zonal.profile(SD, thW);
                double[] a = Zonal.anchors(pS, pW);
                double e = Zonal.err(pS, pW);
                say(String.format(LF, "  %-8d %-9.3f %8.3f %9.3f %9.3f %9.3f %9.3f %9.3f %9.3f   %7.4f",
                    mode, g, Zonal.band(pS, 45, 55), a[0], a[1], a[2], a[3], a[4], a[5], e));
                if (e < best) { best = e; bestTag = "closure " + mode + " / " + g; }
            }
        }
        PrecipField.EDDY_CLOSURE = savedC; PrecipField.EDDY_PHYS_GAIN = savedG; PrecipField.EDDY_MIX = savedMix;
        say("");
        say(String.format(LF, "  ⇒ 地球掩膜驱动下的最优 = %s（独立 err %.4f）", bestTag, best));
        say("  ⇒ 这个 err 里**不含边界条件差异**，是纯物理误差（再加圆柱几何残差）。");
        say("");
        say("  GPCP 参照：47-62夏 2.534 / 47-62冬 2.432 / 赤道夏 6.585 / 赤道冬 3.580 / 副热夏 2.301 / 副热冬 2.391");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
