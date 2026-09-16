package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanField;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanWiring;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.PolarZone;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P483（第二版）：**D72 的证据**。
 *
 * <p>⚠ 第一版的教训（记账）：我一开始在任意的 {@code (x, z)} 上打温度，**没有查海陆**，
 * 于是把**陆地**的温度当成了海温（40 度处出现 -16 C 的"海"）。那是 E37 同族的假警报。
 * 冰**只铺在海列**上（{@code fillSeaColumnV2}），所以本版**每个断言都先查
 * {@code PlateField.isLandWithCell}**，只对**真正的海点**下结论。
 *
 * <p>问三件事：
 * <ol>
 *   <li>A. PolarZone 认为的 20 条极点线上，**在真海上**到底铺了多少冰？那些海点的
 *       模型温度是多少？（当前口径 vs 海水冰点判据）</li>
 *   <li>B. 契约的极点在 z=10M，那里**真正的海**上有没有冰？</li>
 *   <li>C. 那 20 条冰带按**面积**占全球多少？落在哪些纬度？</li>
 * </ol>
 */
public class P483 {

    static final int SEED = 1022228679;
    static final long SD = SimTerrain.seedOf(SEED);
    static final int CELL = PlateField.PLATE_CELL;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    /** 海水冰点（盐度 35 的标准值）：-1.8 C。 */
    static final double ICE_T = 271.35;
    static PrintStream rep;

    static void say(String s) { rep.println("[P483] " + s); rep.flush(); System.out.println("[P483] " + s); System.out.flush(); }

    static boolean isSea(int x, int z) { return !PlateField.isLandWithCell(x, z, SD, CELL); }

    /** 季节振幅 A(phi, kappa)（与 SimTerrain.warmestMonthTempK 同式），最冷月 = 年平 - A。 */
    static double amp(int x, int z) {
        double kappa = SimClimate.maritimeInland(x, z, SEED);
        double latDeg = Math.toDegrees(WorldContract.latOf(z));
        double aSea = ZonalTables.aSea(latDeg);
        double a = aSea + (ZonalTables.aLand(latDeg) - aSea) * kappa;
        return a < 0.0 ? 0.0 : a;
    }

    /** 在 z ∈ [z0, z1] 上找出**第一个真正的海点**；找不到返回 Integer.MIN_VALUE。 */
    static int firstSea(int x, int z0, int z1, int step) {
        for (int z = z0; z <= z1; z += step) if (isSea(x, z)) return z;
        for (int z = z1; z >= z0; z -= step) if (isSea(x, z)) return z;
        return Integer.MIN_VALUE;
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p483_report.txt"), "UTF-8");
        say("P483 v2：D72 证据 —— 海冰用的是 1M 纬度周期（**只在真海上断言**）");
        say(String.format(LF, "  SEED=%d  世界契约 Z_CYCLE=%d / MAX_D=%d（赤道->极点 10000 km）",
            SEED, WorldContract.Z_CYCLE, WorldContract.MAX_D));
        say("  PolarZone 用的是 ClimateLatitudes：LAT_CYCLE=1,000,000 / MAX_D=500,000（赤道->极点 500 km）");
        say(String.format(LF, "  FLOE_BAND=%.2f ⇒ 每条旧极点线两侧各 %.0f km 的冰带；每个 1M 周期有 2 条 ⇒ 每 20M 周期 40 条",
            PolarZone.FLOE_BAND, (1.0 - PolarZone.FLOE_BAND) * 500_000));
        say("");
        OceanWiring.onWorld(SEED);
        say(String.format(LF, "  装线：SST_PROVIDER=%s  installedSeed=%d",
            Atmosphere.SST_PROVIDER == null ? "**null**" : "已装", OceanField.installedSeed()));
        say("");

        int[] xs = {-6_000_000, 0, 6_000_000};
        int nBandSea = 0, nBandIce = 0, nWarmIce = 0, nColdNoIce = 0;
        double warmestIce = -1e9; int warmestIceZ = 0, warmestIceX = 0;
        double coldestNoIce = 1e9; int coldestZ = 0, coldestX = 0;

        say("A. 20 条旧极点线上，**在真海上**：(当前口径是否铺冰) vs (模型自己说多冷)");
        say("   zp = 500k + k*1M；每条带在 z 上 [zp-50k, zp+50k]，x 取 {-6M, 0, +6M}");
        say(String.format(LF, "   %3s %9s %8s %7s %9s %9s %8s %6s %6s", "k", "z", "x", "lat", "T年平K", "T最冷K", "T最冷C", "floe", "SST判"));
        for (int k = 0; k < 20; k++) {
            int zp = 500_000 + k * 1_000_000;
            for (int x : xs) {
                int z = firstSea(x, zp - 50_000, zp + 50_000, 10_000);
                if (z == Integer.MIN_VALUE) continue;
                double latDeg = Math.toDegrees(WorldContract.latOf(z));
                double tAnn = SimClimate.surfaceTempK(x, z, SEED);
                double a = amp(x, z);
                double tCold = tAnn - a;
                boolean floe = PolarZone.isPolar(PolarZone.band(x, z, SEED));
                boolean floeNew = tCold < ICE_T;
                nBandSea++;
                if (floe) {
                    nBandIce++;
                    if (tCold > ICE_T + 5.0) nWarmIce++;
                    if (tCold > warmestIce) { warmestIce = tCold; warmestIceZ = z; warmestIceX = x; }
                } else if (tCold < ICE_T) { nColdNoIce++; }
                if (tCold < coldestNoIce) { coldestNoIce = tCold; coldestZ = z; coldestX = x; }
                say(String.format(LF, "   %3d %9d %8d %+7.2f %9.2f %9.2f %8.2f %6s %6s",
                    k, z, x, latDeg, tAnn, tCold, tCold - 273.15, floe ? "冰" : "-", floeNew ? "冰" : "-"));
            }
        }
        say("");
        say(String.format(LF, "  ⇒ 在 %d 个「冰带 ∩ 真海」的采样点上，当前口径铺了冰的是 **%d** 个；", nBandSea, nBandIce));
        say(String.format(LF, "     其中 **%d** 个点的最冷月海温高于冰点 5 K 以上（即模型自己说那片海不该结冰）", nWarmIce));
        if (nBandIce > 0) {
            say(String.format(LF, "  ⇒ **最热的「冰点」**：x=%d z=%d lat=%+.2f  T最冷=%.2f K（%+.2f C）—— 那里在结冰",
                warmestIceX, warmestIceZ, Math.toDegrees(WorldContract.latOf(warmestIceZ)), warmestIce, warmestIce - 273.15));
        }
        say(String.format(LF, "  ⇒ 反向漏掉：本应结冰（T最冷<冰点）但当前口径不铺冰的海点 = **%d** 个", nColdNoIce));
        say("");

        say("B. 契约的极点 z=10M 附近（±1M，步长 250k）在**真海上**的读数");
        int nPoleSea = 0, nPoleIce = 0;
        for (int z = 9_000_000; z <= 11_000_000; z += 250_000) {
            if (!isSea(4_000_000, z)) { continue; }
            nPoleSea++;
            double latDeg = Math.toDegrees(WorldContract.latOf(z));
            double tAnn = SimClimate.surfaceTempK(4_000_000, z, SEED);
            double tCold = tAnn - amp(4_000_000, z);
            boolean floe = PolarZone.isPolar(PolarZone.band(4_000_000, z, SEED));
            boolean floeNew = tCold < ICE_T;
            if (floe) nPoleIce++;
            say(String.format(LF, "   z=%9d lat=%+7.2f  T年平=%7.2f K  T最冷=%7.2f K（%+6.2f C）  floe=%s  floe(SST)=%s",
                z, latDeg, tAnn, tCold, tCold - 273.15, floe ? "冰" : "-", floeNew ? "冰" : "-"));
        }
        say(String.format(LF, "   ⇒ 契约极点附近 %d 个海点里，当前口径铺冰 **%d** 个", nPoleSea, nPoleIce));
        say("");

        say("C. 那 20 条冰带的**纬度位置**与**面积占比**（按 cos(lat) 加权，用世界契约的帐篷函数换算）");
        double tot = 0, ice = 0;
        final int DZ = 10_000;
        for (int z = 0; z < WorldContract.Z_CYCLE; z += DZ) {
            double w = Math.cos(WorldContract.latOf(z));
            tot += w;
            if (PolarZone.isPolar(PolarZone.rawBand(z))) ice += w;
        }
        say(String.format(LF, "   当前口径：冰覆盖全球表面积的 **%.2f%%**（真实地球海冰约占海洋 7%%、占全球约 5%%）",
            100.0 * ice / tot));
        say(String.format(LF, "   纬度分布（每带的纬度区间）："));
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < 20; k++) {
            int zp = 500_000 + k * 1_000_000;
            double l1 = Math.toDegrees(WorldContract.latOf(zp - 50_000));
            double l2 = Math.toDegrees(WorldContract.latOf(zp + 50_000));
            sb.append(String.format(LF, "     k=%2d z=%9d -> 纬度 %+7.2f ~ %+7.2f%n", k, zp, l1, l2));
        }
        say(sb.toString());
        say("   ⇒ 冰**不在两极**，而是每 1M 一条、铺满所有纬度（含赤道带）—— 这正是「像把两块大陆切开」。");
        say("");
        say(String.format(LF, "D. reentryBlocked = %d（必须 0）", OceanField.reentryBlocked));
        say("⚠ 记账：本探针只测量，未改任何东西。");
        rep.flush();
        rep.close();
        System.out.println("JAVA_EXIT=0");
    }
}
