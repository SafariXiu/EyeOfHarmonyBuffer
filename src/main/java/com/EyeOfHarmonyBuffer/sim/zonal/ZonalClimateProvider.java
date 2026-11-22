package com.EyeOfHarmonyBuffer.sim.zonal;

import com.EyeOfHarmonyBuffer.sim.atmos.VerticalColumn;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import com.EyeOfHarmonyBuffer.sim2.land.ZonalLandProfile;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * ★ 2026-11-15：19-latitude self-computed climate baseline provider.
 *
 * <p>Default OFF: no solve, no class-side output, configStamp unchanged.
 * When ON, solves a mixed land/ocean run plus an all-ocean run once, derives the
 * land end-member by linear unmixing, caches annual means and harmonics, and exposes
 * O(1) latitude queries. (The all-land run is numerically unstable at 24 steps/year.)
 *
 * <p>The provider only replaces the zonal-mean baseline. Local kappa, SST' and
 * elevation are still applied by the existing {@code Atmosphere} path.
 */
public final class ZonalClimateProvider {

    /**
     * Production switch.
     *
     * <p>⚠⚠ <b>2026-11-18 夜【已退回 false】（历史，见下方原文）</b>：翻 true 后实测
     * 海表能量收支从 −10 恶化到 **+50 W/m²**（NSFLUX），即新气候下 SST 不是稳态根
     * （违反 A0-②）。用户裁决走「乙再甲」：先在旧基线上做气压带的结构修复（B/C 拆分），
     * 再单独解决 provider 的收敛。⟹ 本行当前值 = <b>false</b>。
     *
     * <p>★ <b>2026-11-18（用户裁决，已被上一条取代为历史）：默认翻为 true</b> —— 自算纬向骨架的目标（T/u/w/q 不再查表）
     * 已由用户裁定为当前主线，第二节冻结同时解除（见 工作目录/长期/冻结清单-2026-10-21.md §八）。
     *
     * <p>⚠ 本轮翻默认时**已知的三条代价**（不隐瞒，见 工作目录/中期/风-3.50与南北不对称-取证-2026-11-18.md）：
     * <ol>
     *   <li><b>首次求解 2 × ~513 s（实测 2026-11-15）</b>，且解依赖 {@link ZonalLandProfile} 的世界种子
     *       ⟹ 每换一个世界种子就要重解一次；生产必须先解一次落盘（A0-②），否则会卡住世界加载；</li>
     *   <li>ON 时 {@code SimClimate.configStamp()} 折入本 provider 的 stamp ⟹ 全部气候瓦片失效
     *       （世界预热重新付一次）；</li>
     *   <li>2026-11-15 首次试开时 NTHC5（海陆温差口径比 0.265 > 0.20）FAIL —— 该门对"更暖的世界"的
     *       敏感性未解决，翻默认后它仍是红的。</li>
     * </ol>
     * <p>回滚：把本行改回 false 即逐位回到表驱动路径（{@code configStamp} 也随之复位）。
     */
    public static volatile boolean ON = false;

    /** Current first version: 2 years x 24 steps, matching the 2026-11-14 run. */
    public static final int YEARS = 2;
    public static final int STEPS_PER_YEAR = 24;
    /** External Earth OHT removed 2026-11-15; 0 = use only model diffusion. */
    public static final double OHT_SCALE = 0.0;
    /** Cache format version; bump when the solution layout/semantics change. */
    private static final int CACHE_VERSION = 4;
    private static final String CACHE_ENV = "EOH_ZONAL_CACHE";
    private static final String SEED_ENV = "EOH_ZONAL_SEED";

    private static volatile Solution SOLUTION = null;
    /** Recursion guard: SeasonalZonal's diffusivity reads the legacy Atmosphere path. */
    private static final ThreadLocal<Boolean> SOLVING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private ZonalClimateProvider() {}

    /** True while the provider is solving; Atmosphere hooks must use the legacy path. */
    public static boolean isSolving() {
        return SOLVING.get().booleanValue();
    }

    public static final class Solution {
        public final double[] latDeg = new double[SeasonalZonal.NL];
        public final double[] oceanAnnualK = new double[SeasonalZonal.NL];
        public final double[] landAnnualK = new double[SeasonalZonal.NL];
        public final double[] oceanAmpK = new double[SeasonalZonal.NL];
        public final double[] landAmpK = new double[SeasonalZonal.NL];
        public final double[] oceanPhaseRad = new double[SeasonalZonal.NL];
        public final double[] landPhaseRad = new double[SeasonalZonal.NL];
        public long ms = 0L;
    }

    /** Solve once, then return the cached solution. Call only when {@link #ON} is true. */
    public static Solution get() {
        Solution s = SOLUTION;
        if (s != null) return s;
        synchronized (ZonalClimateProvider.class) {
            if (SOLUTION == null) {
                if (!ZonalLandProfile.hasSeed()) ZonalLandProfile.setSeed(fallbackSeed());
                s = loadCache();
                if (s == null) {
                    s = solve();
                    saveCache(s);
                }
                SOLUTION = s;
            }
            return SOLUTION;
        }
    }

    /** Drop the in-memory and on-disk cache (world seed / switch change). */
    public static void invalidate() {
        SOLUTION = null;
        String p = cachePath();
        if (p != null) {
            try { Files.deleteIfExists(Paths.get(p)); } catch (IOException ignored) { }
        }
    }

    /** O(1) interpolation on the 10-degree grid; latRad is signed radians. */
    public static double oceanAnnualK(double latRad) {
        return interp(get().oceanAnnualK, Math.toDegrees(latRad));
    }

    public static double landAnnualK(double latRad) {
        return interp(get().landAnnualK, Math.toDegrees(latRad));
    }

    /** Sea-level land minus ocean annual temperature [K]. */
    public static double landMinusOceanK(double latRad) {
        return landAnnualK(latRad) - oceanAnnualK(latRad);
    }

    /** Zonal-mean sea-level annual temperature at local land fraction kappa [K]. */
    public static double zonalMeanK(double latRad, double kappa) {
        double k = clamp01(kappa);
        return oceanAnnualK(latRad) + k * landMinusOceanK(latRad);
    }

    /** Annual harmonic at this latitude, mixed by local land fraction kappa. */
    public static double seasonalAnomaly(double latRad, double kappa, double theta) {
        double k = clamp01(kappa);
        double lat = Math.abs(Math.toDegrees(latRad));
        double hemi = latRad < 0.0 ? Math.PI : 0.0;
        double ao = interp(get().oceanAmpK, lat);
        double al = interp(get().landAmpK, lat);
        double po = interp(get().oceanPhaseRad, lat);
        double pl = interp(get().landPhaseRad, lat);
        return (1.0 - k) * ao * Math.cos(theta - po - hemi)
             + k * al * Math.cos(theta - pl - hemi);
    }

    /** Config identity for SimClimate.configStamp(); only called when ON. */
    public static long stamp() {
        long h = 0x7A190L;
        h = h * 31 + CACHE_VERSION;
        h = h * 31 + YEARS;
        h = h * 31 + STEPS_PER_YEAR;
        h = h * 31 + Double.doubleToLongBits(OHT_SCALE);
        h = h * 31 + Double.doubleToLongBits(WorldContract.R_EFF);
        h = h * 31 + Double.doubleToLongBits(WorldContract.OMEGA);
        h = h * 31 + Double.doubleToLongBits(WorldContract.S0_TSI);
        h = h * 31 + ZonalLandProfile.currentSeed();
        if (VerticalColumn.CLOUD_LW_ON) h = h * 31 + 0x7A191L;
        if (VerticalColumn.CLOUD_SW_ON) {
            h = h * 31 + 0x7A192L;
            h = h * 31 + Double.doubleToLongBits(VerticalColumn.CLOUD_ALPHA_CLEAR);
        }
        return h;
    }

    private static long fallbackSeed() {
        String s = System.getenv(SEED_ENV);
        if (s == null || s.isEmpty()) s = System.getProperty("eoh.zonal.seed", "0");
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return 0L; }
    }

    private static String cachePath() {
        String p = System.getenv(CACHE_ENV);
        if (p == null || p.isEmpty()) p = System.getProperty("eoh.zonal.cache", "");
        if (p != null && !p.isEmpty()) return p;
        // ★★★★★ 2026-11-22（P2/P4 共用前置）：**默认缓存路径**。
        //   为什么必须有它：首解是**分钟级**（实测 >5 min 未返回），而原来没有 env/-D 时
        //   cachePath() 返回 null ⟹ **每个 JVM 重算一遍** ⟹ A0-②「解一次→缓存」在实现上不成立。
        //   现在默认落到 <user.dir>/eoh_cache/，文件名由 stamp() 命名（已含 seed / 配置 / 行星参数），
        //   所以换种子、改开关都会自动换文件，不会串味。
        String dir = System.getProperty("eoh.zonal.cacheDir", "");
        if (dir == null || dir.isEmpty()) {
            dir = System.getProperty("user.dir", ".") + java.io.File.separator + "eoh_cache";
        }
        return dir + java.io.File.separator + "zonal_" + Long.toHexString(stamp()) + ".bin";
    }

    private static Solution loadCache() {
        String p = cachePath();
        if (p == null) return null;
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new FileInputStream(p)))) {
            if (in.readLong() != stamp()) return null;
            Solution s = new Solution();
            readArray(in, s.oceanAnnualK);
            readArray(in, s.landAnnualK);
            readArray(in, s.oceanAmpK);
            readArray(in, s.landAmpK);
            readArray(in, s.oceanPhaseRad);
            readArray(in, s.landPhaseRad);
            for (int i = 0; i < SeasonalZonal.NL; i++) s.latDeg[i] = -90.0 + 10.0 * i;
            s.ms = in.readLong();
            return s;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static void saveCache(Solution s) {
        String p = cachePath();
        if (p == null) return;
        Path path = Paths.get(p);
        Path tmp = Paths.get(p + ".tmp");
        try {
            if (path.getParent() != null) Files.createDirectories(path.getParent());
            try (DataOutputStream out = new DataOutputStream(
                    new BufferedOutputStream(new FileOutputStream(tmp.toFile())))) {
                out.writeLong(stamp());
                writeArray(out, s.oceanAnnualK);
                writeArray(out, s.landAnnualK);
                writeArray(out, s.oceanAmpK);
                writeArray(out, s.landAmpK);
                writeArray(out, s.oceanPhaseRad);
                writeArray(out, s.landPhaseRad);
                out.writeLong(s.ms);
            }
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            try { Files.deleteIfExists(tmp); } catch (IOException ignored2) { }
        }
    }

    private static void writeArray(DataOutputStream out, double[] a) throws IOException {
        out.writeInt(a.length);
        for (double v : a) out.writeDouble(v);
    }

    private static void readArray(DataInputStream in, double[] a) throws IOException {
        int n = in.readInt();
        if (n != a.length) throw new IOException("bad length " + n);
        for (int i = 0; i < n; i++) a[i] = in.readDouble();
    }

    private static Solution solve() {
        boolean savedBand = VerticalColumn.BAND_LW;
        boolean savedO3 = VerticalColumn.O3_SW;
        boolean savedConv = VerticalColumn.CONV_TO_CLOUD_TOP;
        boolean savedFixed = VerticalColumn.FIXED_TS;
        double savedAlb = VerticalColumn.ALBEDO;
        long t0 = System.currentTimeMillis();
        SOLVING.set(Boolean.TRUE);
        try {
            // The provider is the longwave-A arm: set the three switches only for this solve.
            VerticalColumn.BAND_LW = true;
            VerticalColumn.O3_SW = true;
            VerticalColumn.CONV_TO_CLOUD_TOP = true;
            if (!ZonalLandProfile.hasSeed()) ZonalLandProfile.setSeed(0L);

            // Mixed run: the actual land/ocean profile (stable, matches the 283.2 K note).
            SeasonalZonal.Result rm = SeasonalZonal.run(-1.0, YEARS, STEPS_PER_YEAR, OHT_SCALE);
            // Ocean end-member (stable). The all-land end-member is numerically unstable
            // (NaN at high latitudes at 24 steps/year), so land is derived by linear unmixing.
            SeasonalZonal.Result ro = SeasonalZonal.run(0.0, YEARS, STEPS_PER_YEAR, OHT_SCALE);

            Solution s = new Solution();
            double[] mAnn = new double[SeasonalZonal.NL];
            double[] mAmp = new double[SeasonalZonal.NL];
            double[] mPhase = new double[SeasonalZonal.NL];
            fillHarmonics(rm, mAnn, mAmp, mPhase);
            fillHarmonics(ro, s.oceanAnnualK, s.oceanAmpK, s.oceanPhaseRad);
            for (int i = 0; i < SeasonalZonal.NL; i++) {
                s.latDeg[i] = rm.latDeg[i];
                double latRad = Math.toRadians(rm.latDeg[i]);
                double k = clamp01(ZonalLandProfile.at(latRad));
                if (k < 1.0e-3) {
                    s.landAnnualK[i] = s.oceanAnnualK[i];
                    s.landAmpK[i] = s.oceanAmpK[i];
                    s.landPhaseRad[i] = s.oceanPhaseRad[i];
                    continue;
                }
                double oa = s.oceanAmpK[i] * Math.cos(s.oceanPhaseRad[i]);
                double ob = s.oceanAmpK[i] * Math.sin(s.oceanPhaseRad[i]);
                double ma = mAmp[i] * Math.cos(mPhase[i]);
                double mb = mAmp[i] * Math.sin(mPhase[i]);
                double la = (mAnn[i] - (1.0 - k) * s.oceanAnnualK[i]) / k;
                double aa = (ma - (1.0 - k) * oa) / k;
                double bb = (mb - (1.0 - k) * ob) / k;
                if (!Double.isFinite(la) || !Double.isFinite(aa) || !Double.isFinite(bb)) {
                    s.landAnnualK[i] = s.oceanAnnualK[i];
                    s.landAmpK[i] = s.oceanAmpK[i];
                    s.landPhaseRad[i] = s.oceanPhaseRad[i];
                } else {
                    s.landAnnualK[i] = la;
                    s.landAmpK[i] = Math.hypot(aa, bb);
                    s.landPhaseRad[i] = Math.atan2(bb, aa);
                }
            }
            // P293: the annual harmonic must be odd about the equator (same amplitude,
            // opposite phase). Symmetrize the two hemispheres after the unmixing.
            symmetricHarmonics(s.oceanAmpK, s.oceanPhaseRad);
            symmetricHarmonics(s.landAmpK, s.landPhaseRad);
            s.ms = System.currentTimeMillis() - t0;
            return s;
        } finally {
            VerticalColumn.BAND_LW = savedBand;
            VerticalColumn.O3_SW = savedO3;
            VerticalColumn.CONV_TO_CLOUD_TOP = savedConv;
            VerticalColumn.FIXED_TS = savedFixed;
            VerticalColumn.ALBEDO = savedAlb;
            SOLVING.set(Boolean.FALSE);
        }
    }

    private static void symmetricHarmonics(double[] amp, double[] phase) {
        for (int i = 0; i < SeasonalZonal.NL / 2; i++) {
            int j = SeasonalZonal.NL - 1 - i;
            amp[i] = amp[j];
            phase[i] = phase[j] + Math.PI;
        }
    }

    private static void fillHarmonics(SeasonalZonal.Result r,
            double[] mean, double[] amp, double[] phase) {
        for (int i = 0; i < SeasonalZonal.NL; i++) {
            double[] ts = r.ts[i];
            int n = ts.length;
            double sum = 0.0, ca = 0.0, sa = 0.0;
            for (int j = 0; j < n; j++) {
                double theta = 2.0 * Math.PI * j / n;
                sum += ts[j];
                ca += ts[j] * Math.cos(theta);
                sa += ts[j] * Math.sin(theta);
            }
            mean[i] = sum / n;
            double a = 2.0 * ca / n;
            double b = 2.0 * sa / n;
            amp[i] = Math.hypot(a, b);
            phase[i] = Math.atan2(b, a);
            // Production has the A(0)=0 hard constraint; keep it here too.
            if (Math.abs(r.latDeg[i]) < 1.0e-9) {
                amp[i] = 0.0;
                phase[i] = 0.0;
            }
        }
    }

    private static double interp(double[] a, double latDeg) {
        double x = Math.max(-90.0, Math.min(90.0, latDeg));
        double p = (x + 90.0) / 10.0;
        int i = (int) Math.floor(p);
        if (i >= a.length - 1) return a[a.length - 1];
        double f = p - i;
        return a[i] + f * (a[i + 1] - a[i]);
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}
