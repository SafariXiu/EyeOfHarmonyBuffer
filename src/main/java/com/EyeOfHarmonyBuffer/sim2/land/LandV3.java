package com.EyeOfHarmonyBuffer.sim2.land;

/**
 * 海陆分布 v3：**地形只由连续标量场决定**，不含任何 Voronoi 几何。
 *
 * <h2>为什么重写</h2>
 * v2（{@link LandField}）把地形建立在"到板块边界的距离"上，而那个距离来自 Voronoi 中垂线：
 * 换细胞对时跳、顶点处折角、放大后留直边。所有补丁（5 点平均、softplus、soft-max、plateau 分支）
 * 都只是在掩盖这个几何事实。本类把这些全部扔掉。
 *
 * <h2>三层</h2>
 * <pre>
 *   L1 构造强度  T(x) = Σⱼ wⱼ·|vⱼ − v̄|² / Σⱼ wⱼ        （加权速度方差）
 *   L2 大陆度    c(x) = 脊化噪声（与 v2 同一套，保证海岸线风格不变）
 *   L3 抬升      U(x) = A · T^γ · gate(c)                 （山带）
 * </pre>
 *
 * <p><b>T 为什么是正确的量</b>：
 * <ul>
 *   <li>它是【方差】：恒 ≥ 0、在板块内部为 0、在边界处大；</li>
 *   <li>它是【平方和】：处处二阶可导，没有 {@code |ε|} 那种零点 V 形折点；</li>
 *   <li>它不含方向、不含距离、不选"最近的哪一条边界" ⟹ 没有 Voronoi 几何。</li>
 * </ul>
 *
 * <h2>与 LEM 的关系</h2>
 * {@link #uplift} 就是旧 {@code TalosLandErosion}（stream power + 河流）的**抬升输入 U(x)**。
 * LEM 负责把 U 演化成排水平衡态，从而产生分叉山脊、河谷、水系；本类只负责"哪里该抬升、抬多少"。
 */
public final class LandV3 {

    private LandV3() {}

    /** 是否启用（接线用；不改变 v2 任何行为）。 */
    public static boolean ENABLED = true;

    // ================= 缓冲 =================
    private static final class Cell {
        double sx, sz, d;
        long id;
    }
    private static final ThreadLocal<Cell[]> SC = new ThreadLocal<Cell[]>() {
        @Override protected Cell[] initialValue() {
            Cell[] a = new Cell[9];
            for (int i = 0; i < 9; i++) a[i] = new Cell();
            return a;
        }
    };
    private static final ThreadLocal<double[]> VB = new ThreadLocal<double[]>() {
        @Override protected double[] initialValue() { return new double[18]; }   // 9 个速度 ×2
    };
    private static final ThreadLocal<double[]> HB = new ThreadLocal<double[]>() {
        @Override protected double[] initialValue() { return new double[1]; }
    };

    // ================= 参数（都有出处或实测标定） =================
    /** 构造强度到抬升的幅度（m 厚度）。实测标定：使强边界 ≈ 6 km 峰。 */
    public static double A_UPLIFT = 30_000.0;
    public static double U0 = 42_000.0;
    public static double R_S = 1_800_000.0;
    /** softmin scale for the boundary distance (m). */
    public static double SOFTMIN_L = 120_000.0;
    /** Smoothing length of the velocity field (m). */
    public static double VEL_L = 260_000.0;
    /** finite-difference step for the coast distance (blocks). */
    public static double COAST_FD_STEP = 500.0;
    public static double V_REF = 1.0;
    public static double Z_TRENCH_N = -1.0, Z_CONT_N = 1.0;
    /** Island arc: chain offset from the trench (m from the boundary). */
    public static double ARC_GATE_W = 0.18;
    /** Radial profile of the arc band around ARC_DIST (km). Wider = softer fan at vertices. */
    public static double ARC_WIDTH_K = 1.0;
    // ARC_WIDTH narrowed 160 km -> 90 km: the 320 km band was a solid continent-scale mass
// rather than a chain. Amplitude must stay high (the abyssal floor is -4 km, so ~27,000
// thickness is needed just to emerge) -- the width was what made it a block.
    public static double ARC_AMP = 32_000.0, ARC_DIST = 320_000.0, ARC_WIDTH = 90_000.0;
    public static double ARC_MASK_WL = 140_000.0, ARC_MASK_LO = 0.32, ARC_MASK_HI = 0.68;
    /** Mid-ocean ridge: broad dome over a divergent boundary. */
    public static double RIDGE_OC_AMP = 8_000.0, RIDGE_OC_W = 350_000.0;


    /** T 的幂次：>1 让只有真正强的边界长出山（弱边界留丘陵）。 */
    public static double GAMMA_T = 1.35;
    public static double T_LO = 0.15, T_HI = 1.20;
    /** T 的归一化基准（强边界的量级）。 */
    public static double T_REF = 0.55;
    /** 速度方差核宽：决定山带多宽。小 ⟹ 只有边界附近才有抬升。 */
    public static double T_L = 150_000.0;
    /** 大陆门：c 高于此值才可能造山（避免海上长山）。 */
    public static double LAND_GATE_LO = 0.50;
    public static double LAND_GATE_HI = 0.62;
    /** 低地/盆地（m 厚度）：海底热沉降 + 陆上减薄。 */
    public static double LOW_AMP = 6_000.0, LOW_WL = 4_000_000.0;
    /** 克拉通基底抬升（m 厚度）：让大陆内部有 ~500 m 的台地，而不是贴着海岸剖面。 */
    public static double CRATON_M = 1_600.0;
    /** LEM calibration: scales ONLY the uplift term (base/craton untouched). */
    public static double UPLIFT_GAIN = 1.0;
    /** Ridged multifractal detail added to the uplift so the LEM carves noise ridges, not the Voronoi web. */
    public static double RIDGE_AMP = 2_600.0, RIDGE_WL = 220_000.0;
    /**
     * Multiplicative ridge modulation of the PRIMARY uplift (0 = off).
     *
     * <p>The primary term {@code U0*fd*force*hz} is a product of four SMOOTH fields, so its
     * core saturates into a flat-topped plateau. A small ADDITIVE detail (the old RIDGE_AMP,
     * 118 m against a 6,360 m term) could not change that. This multiplies the term instead.
     *
     * <p>Only THREE octaves are used (220 / 110 / 55 km): the 6.9 km octave of ridgedMF would
     * inject ~10,000 thickness of relief at 7 km wavelength, i.e. cliffs -- the same trap as
     * the MESO_AMP calibration.
     */
    public static double RIDGE_MOD = 0.45;
    public static int RIDGE_OCT = 6;
    public static double FINE_AMP = 220.0, FINE_WL = 9_000.0;
    /** 细节噪声（m 厚度）。 */
    public static double DET1 = 900.0, DWL1 = 900_000.0;
    public static double DET2 = 380.0, DWL2 = 320_000.0;
    public static double DET3 = 150.0, DWL3 = 110_000.0;
    /** Mesoscale relief (D8 drainage needs this band to organise into networks). */
    public static double MESO_AMP = 4_500.0, MESO_WL = 42_000.0;
    /**
     * Width of the land/sea gate in continentality units.
     *
     * <p>The old code used a hard 0.04 window: {@code smoothstep(cut-0.02, cut+0.02, c)}.
     * Because {@code c} is a weighted sum of three noises it can cross that razor in under
     * 1 km, which flips {@code base} between the abyssal value and the continental value --
     * measured as a 7,769 m height step between two samples 1 km apart, and it is what the
     * global map shows as speckle over the ocean.
     */
    public static double COAST_GATE = 0.04;

    /**
     * Coastal fade of the mid-scale detail term, in CRUST-THICKNESS units.
     *
     * <p>{@code det} is multiplied by {@code smoothstep(LO, HI, |base + dt|)}: no detail at
     * the coast, full detail once the crust is HI thick relative to sea level. The paper's
     * shoreline is the zero crossing of a smooth elevation field, not an isoline of a 42 km
     * noise; without this fade the latter wins and coasts become wide, ragged and lobed.
     *
     * <p>{@code HI = 0} disables the fade (old behaviour).
     */
    public static double COAST_DET_LO = 0.0;
    public static double COAST_DET_HI = 6000.0;

    /**
     * Fractal coastal detail (A). A slope-preserving fBm is added to the land/sea
     * THRESHOLD, so the shoreline gets self-similar roughness at every scale while the
     * land fraction and `isLand == (height > 0)` stay untouched.
     * 0 disables it.
     */
    public static double COAST_FBM_AMP = 0.010;   // amplitude in continentality units
    public static double COAST_FBM_WL = 400_000.0;  // coarsest wavelength, blocks
    public static int COAST_FBM_OCT = 6;
    /**
     * Uplift scale (thickness) at which the fractal-coast amplitude is halved. Island belts
     * live on uplift, so damping the threshold noise there keeps small islands alive instead
     * of letting it shift their coastline away. 0 disables the damping.
     */
    public static double COAST_FBM_DTREF = 1_500.0;

    /** Put the orogenic belt on the overriding plate only (see upliftPaper). */
    public static boolean OROG_SIDE_ON = true;
    /** Half-width of the continentality contrast that decides which side overrides. */
    public static double OROG_SIDE_T = 0.15;
    /**
     * Continentality thresholds (on bf[8] = the pair's MIN continentality) above which a
     * boundary counts as a continent-continent collision, where BOTH sides deform.
     * Calibrated from the measured bf[8] distribution (concentrated in 0.3..0.6), not guessed:
     * the earlier 0.50..0.90 put `both` at 0.008, which silently disabled collisions.
     */
    public static double OROG_COLLIDE_LO = 0.45;
    public static double OROG_COLLIDE_HI = 0.75;
    /**
     * Half-width (blocks) of the collision uplift peak. Collisions need a profile that is
     * MAXIMUM AT the boundary: the fd shape peaks at R_S/3 and is zero at D=0, so applying
     * it to both sides produced two parallel ranges with a valley along the boundary.
     */
    public static double OROG_COLLIDE_W = 1_000_000.0;

    /**
     * Oceanic trench on the SUBDUCTING side. The paper's ocean floor reaches zt = -10 km at
     * the trench, while the abyssal plain sits at za = -6 km. `profile` is zeroed on that
     * side by the overriding gate, so the trench needs its own (negative) profile.
     * Amplitude is in crust-thickness units (1,000 thickness ~ 151 m).
     */
    // NOTE: `profile` is DIMENSIONLESS (fd is 0..1) and gets multiplied by U0 (=42,000),
    // so this amplitude is expressed in units of U0, not in crust thickness. Using 40,000
    // here made U0*profile ~ -1.2e9 thickness (observed h = -46,504 km).
    public static double TRENCH_AMP = 0.90;        // 0 disables; 0.9 ~= -5.7 km of trench
    public static double TRENCH_OFF = 60_000.0;    // blocks seaward of the bisector
    public static double TRENCH_W   = 120_000.0;   // Gaussian half-width
    /** Diagnostics: record the components of f() for the last height() call on this thread. */
    public static volatile boolean DEBUG_COMPONENTS = false;
    private static final ThreadLocal<double[]> DBG = new ThreadLocal<double[]>() {
        @Override protected double[] initialValue() { return new double[13]; }
    };

    /**
     * Components of f() at one point: {c, cut, su, base, dt, det, fv, height}.
     * Turns DEBUG_COMPONENTS on for the duration of the call.
     */
    public static double[] components(long ws, double x, double z) {
        boolean old = DEBUG_COMPONENTS;
        DEBUG_COMPONENTS = true;
        height(ws, x, z);
        DEBUG_COMPONENTS = old;
        double[] d = DBG.get();
        return new double[]{d[0], d[1], d[2], d[3], d[4], d[5], d[6], d[7], d[8], d[9], d[10], d[11], d[12]};
    }

    // ================= 公开 API =================

    private static final java.util.concurrent.ConcurrentHashMap<Long, Double> CUTS =
            new java.util.concurrent.ConcurrentHashMap<Long, Double>();
    private static volatile long CUT_LAST_WS = Long.MIN_VALUE;
    private static volatile double CUT_LAST_V;

    /** 大陆度阈值：二分到目标陆占比。 */
    public static double cut(long ws) {
        // Single-entry volatile cache: the ConcurrentHashMap lookup boxed the long and
        // showed up as 3.7% of samples in the load profile.
        if (ws == CUT_LAST_WS) return CUT_LAST_V;
        Double v = CUTS.get(ws);
        if (v != null) { CUT_LAST_WS = ws; CUT_LAST_V = v.doubleValue(); return v.doubleValue(); }
        double lo = 0.20, hi = 0.95;
        for (int it = 0; it < 14; it++) {
            double mid = 0.5 * (lo + hi);
            if (landFrac(ws, mid) > LandContract.LAND_TARGET) lo = mid; else hi = mid;
        }
        double c = 0.5 * (lo + hi);
        CUTS.put(ws, c);
        CUT_LAST_WS = ws;
        CUT_LAST_V = c;
        return c;
    }

    private static double landFrac(long ws, double c0) {
        int n = 96; double span = 40_000_000.0; int land = 0, tot = 0;
        for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) {
            double x = i * span / n, z = j * span / n;
            if (continentality(ws, x, z) > c0) land++;
            tot++;
        }
        return land / (double) tot;
    }

    /** 地形高度（米）。不含 LEM：这是演化前的 uplift 结果。 */
    /** Land test consistent with the terrain sign (h > 0 iff continentality > cut). */
    public static boolean isLand(long ws, double x, double z) {
        // MUST be the terrain's own sign. Using continentality > cut disagreed with h > 0
        // at 1.75% of sampled points (up to 2830 m), i.e. SimTerrain would call a cell land
        // while the height there is below sea level -> a step at the coast.
        return height(ws, x, z) > 0.0;
    }

    /**
     * Signed distance to the coastline (blocks; land &lt; 0, sea &gt; 0).
     * Pure GEOMETRY, not terrain: delegated to the legacy field on purpose so that the
     * beach/shelf logic does not change just because the height model changed.
     */
    public static double coastDist(long ws, double x, double z) {
        // Must be derived from THIS field, not delegated to the legacy one: at (0,0) the
        // legacy field reported 46 km offshore while LandV3 has 900 m of land there, which
        // would put beaches and shelves in the wrong place.
        // signed distance to the c == cut contour: (c - cut) / |grad c|, negated so that
        // land is negative and sea positive (same convention as the legacy field).
        final double step = COAST_FD_STEP;
        final double c0 = height(ws, x, z);
        final double gx = (height(ws, x + step, z) - height(ws, x - step, z)) / (2.0 * step);
        final double gz = (height(ws, x, z + step) - height(ws, x, z - step)) / (2.0 * step);
        final double g = Math.sqrt(gx * gx + gz * gz);
        if (g < 1e-12) return c0 > 0.0 ? -1e9 : 1e9;
        return -c0 / g;
    }

    /** Config stamp so caches invalidate when the model or its constants change. */
    public static long configStamp() {
        long h = 0x51ED2701L;
        h = h * 31 + (USE_PAPER ? 1 : 0);
        h = h * 31 + Double.doubleToLongBits(U0);
        h = h * 31 + Double.doubleToLongBits(R_S);
        h = h * 31 + Double.doubleToLongBits(SOFTMIN_L);
        h = h * 31 + Double.doubleToLongBits(VEL_L);
        h = h * 31 + Double.doubleToLongBits(ARC_AMP);
        h = h * 31 + Double.doubleToLongBits(RIDGE_OC_AMP);
        h = h * 31 + Double.doubleToLongBits(LandContract.LAND_TARGET);
        return h;
    }

    /** Memo table size (power of two). */
    private static final int HC_SIZE = 1 << 12;
    /** Guard for the per-thread height memo (a different seed invalidates it). */
    private static volatile long HC_WS = Long.MIN_VALUE;


    // The key is the EXACT double bit pattern: height() is a function of the precise
    // coordinate, so flooring would alias two sub-block positions and return a wrong value.
    private static int hcIndex(long ax, long az) {
        long h = ax * 0x9E3779B97F4A7C15L ^ az * 0xC2B2AE3D27D4EB4FL;
        return (int) ((h >>> 40) & (HC_SIZE - 1));
    }

    public static double height(long ws, double x, double z) {
        long ax = Double.doubleToLongBits(x), az = Double.doubleToLongBits(z);
        St st = ST.get();
        int idx = hcIndex(ax, az);
        if (ws == HC_WS && st.mx[idx] == ax && st.mz[idx] == az) return st.mv[idx];
        f(ws, x, z, st.h);
        st.mx[idx] = ax; st.mz[idx] = az; st.mv[idx] = st.h[0];
        HC_WS = ws;
        return st.h[0];
    }

    /** Paper model (Cortial et al. 2019) vs the variance model. Default: variance. */
    public static boolean USE_PAPER = true;

    // A/B note (2026-10-24): merging the two exponentials in boundaryField into
    // exp(-(aL+aV)) was measured and REJECTED: it is only 7.5% faster (1243 -> 1149 ns)
    // while perturbing 2.35% of world points by up to 3.4e-10 m. Keep the two-exp form.

    /** Invalidate the per-thread height memo (probes only). */
    public static void clearHeightCache() { HC_WS = Long.MIN_VALUE; }

    /** 构造抬升场（m 厚度）：交给 LEM 当输入 U(x)。 */
    public static double uplift(long ws, double x, double z) {
        if (USE_PAPER) return upliftPaper(ws, x, z);
        double c = continentality(ws, x, z);
        double gate = smoothstep(LAND_GATE_LO, LAND_GATE_HI, c);
        double t = tectonicIntensity(ws, x, z);
        double tn = Math.max(0.0, t / T_REF);
        double t2 = tn * tn;
        double r = t2 / (1.0 + t2);
        double up = A_UPLIFT * r * gate;
        if (RIDGE_AMP > 0.0) up += RIDGE_AMP * ridgedMF(ws, x, z) * gate * (0.30 + 0.70 * r);
        if (FINE_AMP > 0.0) {
            up += FINE_AMP * gate * ((LandNoise.value(x / FINE_WL, z / FINE_WL, ws ^ 0x9AL) - 0.5) * 2.0
                   + 0.5 * (LandNoise.value(x / (FINE_WL * 0.4), z / (FINE_WL * 0.4), ws ^ 0x9BL) - 0.5) * 2.0);
        }
        return up;
    }

    /** Paper model implementation (kept for A/B). */
    /**
     * Island arcs (convergent) and mid-ocean ridges (divergent), shared by BOTH uplift
     * paths. Cortial et al. treat these as separate landforms; a pure uplift field cannot
     * produce either, because both are keyed to the boundary TYPE.
     */
    /** Island-arc strength in [0,1] (same product the uplift uses, without the amplitude). */
    public static double arcRaw(long ws, double x, double z) {
        double qx = x + warpX(x, z), qz = z + warpZ(x, z);
        // Debug-only entry point: no window cache (boundaryField does its own caching).
        Cell[] cs = SC.get();
        cells9(qx, qz, cs);
        double[] bf = BF.get();
        boundaryField(ws, x, z, bf);
        if (bf[3] <= 0.0) return 0.0;
        double c = continentality(ws, x, z);
        double gate = smoothstep(LAND_GATE_LO, LAND_GATE_HI, c);
        double signed = bf[4];
        // Wide gate: signed is a weighted average of per-pair convergence, and near a
        // junction the weights swap so it oscillates around 0 in angle -> a razor-thin 0.04
        // gate turned that into a fan of radial ridges. ARC_GATE_W makes the transition broad.
        double wArc = smoothstep(-ARC_GATE_W, ARC_GATE_W, signed);
        double ad = (bf[0] - ARC_DIST) / ARC_WIDTH;
        double arc = Math.exp(-ad * ad);
        double m = LandNoise.value(x / ARC_MASK_WL, z / ARC_MASK_WL, ws ^ 0x7C1L);
        double mask = smoothstep(ARC_MASK_LO, ARC_MASK_HI, m);
        return wArc * arc * mask * (1.0 - gate) * Math.min(1.0, Math.abs(signed));
    }

    static double landformExtra(long ws, double x, double z, double[] bf, double D, double gate) {
        double signed = bf[4];
        // Wide gate: signed is a weighted average of per-pair convergence, and near a
        // junction the weights swap so it oscillates around 0 in angle -> a razor-thin 0.04
        // gate turned that into a fan of radial ridges. ARC_GATE_W makes the transition broad.
        // (A) An island arc belongs to the OVERRIDING side of a CONVERGENT boundary; an
        // oceanic ridge belongs to a DIVERGENT one. Previously the ridge used (1-wArc),
        // i.e. the opposite side of ANY boundary, which is why every boundary showed two
        // parallel chains (arc on one side, ridge on the other).
        double sideArc = smoothstep(-OROG_SIDE_T, OROG_SIDE_T, bf[7]);
        double conv = smoothstep(0.0, 0.15, bf[1]);
        // bf[1] is frequently exactly 0, so gate on "not convergent" (<=0) instead of < 0.
        double div = smoothstep(0.15, 0.0, bf[1]);
        // bf[1] (force) is frequently 0, so gating the ARC on convergence killed it.
        double wArc = sideArc;
        double extra = 0.0;
        // MONOTONIC profile from the boundary. The ring exp(-((D-320)/160)^2) wrapped around
        // every Voronoi vertex as many concentric arcs -- that IS the "fan"/terracing. A
        // monotone decay has no ring to wrap, so the artifact cannot form.
        // Ring at ARC_DIST from the boundary (same profile as arcRaw). Using D directly
        // put the whole ARC_AMP on the boundary itself.
        double ad = (D - ARC_DIST) / ARC_WIDTH;
        double arc = Math.exp(-ad * ad);
        if (arc > 1e-4 && ARC_AMP > 0.0) {
            // World-space fBm mask. The old version projected the point on the boundary
            // tangent and sampled a 1-D noise of that arc-length coordinate; the tangent
            // spins at triple junctions and the projection JUMPS when the nearest boundary
            // point switches, which produced the radiating sunburst fans and hard folds.
            // A 2-D noise in world space is continuous everywhere.
            double mw = ARC_MASK_WL;
            double m = 0.60 * LandNoise.value(x / mw, z / mw, ws ^ 0x7C1L)
                    + 0.32 * LandNoise.value(x / (mw * 0.45) + 3.1, z / (mw * 0.45) - 1.7, ws ^ 0x7C2L)
                    + 0.24 * LandNoise.value(x / (mw * 0.20) - 2.3, z / (mw * 0.20) + 5.9, ws ^ 0x7C3L);
            m /= 1.06;
            double mask = smoothstep(ARC_MASK_LO, ARC_MASK_HI, m);
            // An arc sits on the OCEANIC side of a convergent boundary and must be able to
            // raise the abyssal floor above sea level, so it must NOT be multiplied by the
            // LAND gate (that suppressed it to zero in the deep ocean and left a dotted line
            // only where c happened to cross the gate). Use the oceanic weight instead.
            extra += wArc * ARC_AMP * arc * mask * (1.0 - gate);
        }
        double rd = D / RIDGE_OC_W;
        double dome = Math.exp(-rd * rd);
        if (dome > 1e-4 && RIDGE_OC_AMP > 0.0) {
            double oceanic = 1.0 - gate;
            extra += div * RIDGE_OC_AMP * dome * oceanic;
        }
        return extra;
    }

    public static double upliftPaper(long ws, double x, double z) {
        double c = continentality(ws, x, z);
        double gate = smoothstep(LAND_GATE_LO, LAND_GATE_HI, c);
        double detail = 0.0;
        if (RIDGE_AMP > 0.0) detail += RIDGE_AMP * ridgedMF(ws, x, z) * gate * 0.30;
        if (FINE_AMP > 0.0) {
            detail += FINE_AMP * gate * ((LandNoise.value(x / FINE_WL, z / FINE_WL, ws ^ 0x9AL) - 0.5) * 2.0
                    + 0.5 * (LandNoise.value(x / (FINE_WL * 0.4), z / (FINE_WL * 0.4), ws ^ 0x9BL) - 0.5) * 2.0);
        }
        double[] bf = BF.get();
        boundaryField(ws, x, z, bf);
        // no early returns: they dropped the arc/ridge terms (a cliff of exactly
        // RIDGE_OC_AMP where fd reached its compact-support limit).
        double D = bf[0];
        double force = bf[1];
        double plunge = bf[2];
        double tt = D / R_S;
        double fd = (tt > 0.0 && tt < 1.0) ? (27.0 / 4.0) * tt * (1.0 - tt) * (1.0 - tt) : 0.0;

        double zPlunge = (plunge - 0.5) * 2.0;
        double zi = Math.max(0.0, Math.min(1.0, (zPlunge - Z_TRENCH_N) / (Z_CONT_N - Z_TRENCH_N)));
        double hz = zi * zi;
        // Which side overrides? bf[7] is the per-pair continentality contrast (decisive,
        // not a 0.5/0.5 split), bf[8] the pair's min continentality (collision case).
        // fd is ZERO at the boundary, so a collision -- which peaks exactly there -- needs
        // its own single-peak profile instead of fd.
        double wSideO = 1.0, bothO = 0.0, profile = fd;
        if (OROG_SIDE_ON && D > 0.0) {
            wSideO = smoothstep(-OROG_SIDE_T, OROG_SIDE_T, bf[7]);
            bothO = smoothstep(OROG_COLLIDE_LO, OROG_COLLIDE_HI, bf[8]);
            double cd = D / OROG_COLLIDE_W;
            profile = (1.0 - bothO) * wSideO * fd + bothO * Math.exp(-cd * cd);
            // Subducting side gets the trench (negative), NOT a mirror-image mountain belt.
            if (TRENCH_AMP > 0.0) {
                double subSide = smoothstep(OROG_SIDE_T, -OROG_SIDE_T, bf[7]);
                double td = (D - TRENCH_OFF) / TRENCH_W;
                profile -= TRENCH_AMP * subSide * (1.0 - bothO) * Math.exp(-td * td);
            }
        }
        // profile can now be NEGATIVE (trench). strength modulates the ridge detail and
        // must stay in [0,1]: an unclamped lower bound sent detail*(0.35+0.65*strength)
        // to -3e8 thickness (observed h = -46,504 km).
        double strength = Math.max(0.0, Math.min(1.0, profile * force * hz));
        double extra = landformExtra(ws, x, z, bf, D, gate);
        double ridgeMod = 1.0;
        if (RIDGE_MOD > 0.0) {
            ridgeMod = (1.0 - RIDGE_MOD) + RIDGE_MOD * ridgedMF3(ws, x, z);
        }
        return U0 * UPLIFT_GAIN * profile * force * hz * ridgeMod
                + detail * (0.35 + 0.65 * strength) + extra;
    }

    /**
     * Continuous velocity field: v(x) = sum w_j v_j / sum w_j.
     *
     * <p>The per-plate constant is what every Lagrangian/path-integral scheme needs to be
     * C-infinity. With a piecewise-constant field the back-trace path is IDENTICAL for every
     * cell of a plate, so the path integral degenerates to a 1-D smoothing and bands.
     */
    public static void velocity(long ws, double x, double z, double[] out) {
        double qx = x + warpX(x, z), qz = z + warpZ(x, z);
        Cell[] cs = SC.get();
        cells9(qx, qz, cs);
        double[] vb = VB.get();
        for (int j = 0; j < 9; j++) vel(ws, cs[j], vb, j * 2);
        double dmin = 1e18;
        for (int j = 0; j < 9; j++) if (cs[j].d < dmin) dmin = cs[j].d;
        double L = VEL_L, sum = 0, sx = 0, sz = 0;
        for (int j = 0; j < 9; j++) {
            double e = cs[j].d - dmin;
            double w = Math.exp(-(e * e) / (2.0 * L * L));
            sum += w; sx += w * vb[j * 2]; sz += w * vb[j * 2 + 1];
        }
        out[0] = sx / sum; out[1] = sz / sum;
    }

    /** Coarse-only detail (RIDGE, 220 km). The 9 km FINE term must NOT be sampled on a
     *  25 km solver grid: it aliases into grid-scale stripes. */
    public static double detailCoarse(long ws, double x, double z) {
        double c = continentality(ws, x, z);
        double gate = smoothstep(LAND_GATE_LO, LAND_GATE_HI, c);
        if (RIDGE_AMP <= 0.0) return 0.0;
        // Only octaves ABOVE the solver sample spacing: the 55/27/14/7 km octaves of
        // ridgedMF alias into a 25 km-period sawtooth (measured: h cycles 2139/2104/2245).
        return RIDGE_AMP * ridgedMFCoarse(ws, x, z) * gate * 0.30;
    }

    /** Same as upliftPaper but WITHOUT the fractal detail: this is what the tectonic
     *  path integral must integrate (detail is a surface texture, not an accumulated rate;
     *  integrating it aliases into stripes). */
    public static double upliftPaperSmooth(long ws, double x, double z) {
        double[] bf = BF.get();
        boundaryField(ws, x, z, bf);
        double c = continentality(ws, x, z);
        double gate = smoothstep(LAND_GATE_LO, LAND_GATE_HI, c);
        double D = bf[3] > 0.0 ? bf[0] : 1e9;
        double tt = D / R_S;
        double fd = (tt > 0.0 && tt < 1.0) ? (27.0 / 4.0) * tt * (1.0 - tt) * (1.0 - tt) : 0.0;
        double zPlunge = (bf[2] - 0.5) * 2.0;
        double zi = Math.max(0.0, Math.min(1.0, (zPlunge - Z_TRENCH_N) / (Z_CONT_N - Z_TRENCH_N)));
        double base = U0 * UPLIFT_GAIN * fd * bf[1] * zi * zi;
        return base + landformExtra(ws, x, z, bf, D, gate);
    }

    /**
     * All per-thread scratch for the height hot path, in ONE object.
     *
     * <p>Profiling showed {@code ThreadLocal.get} as the single largest cost (21.8% of a
     * world-load recording): one {@code height()} sample touched six different thread-locals
     * (memo, h-buffer, cells, velocities, window key, continentality scratch). Fetching one
     * state object instead cuts that to two lookups, and the memo lives here as well.
     */
    private static final class St {
        final long[] mx = new long[1 << 12];
        final long[] mz = new long[1 << 12];
        final double[] mv = new double[1 << 12];
        final double[] h = new double[8];
        final Cell[] cs = new Cell[9];
        final double[] vb = new double[18];
        final double[] cc = new double[9];
        final long[] key = {Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE};
        // Precomputed, point-independent pair table (see boundaryField).
        final int[] pa = new int[36];
        final int[] pb = new int[36];
        final double[] pAb = new double[36];
        final double[] pNx = new double[36];
        final double[] pNz = new double[36];
        final double[] pConvS = new double[36];
        final double[] pConv = new double[36];
        final double[] pCcMin = new double[36];
        /** continentality(cellA) - continentality(cellB) for the pair. */
        final double[] pSide = new double[36];
        /** min(continentality(A), continentality(B)) for the pair. */
        final double[] pContMin = new double[36];
        int nPair = 0;
        St() {
            java.util.Arrays.fill(mx, Long.MIN_VALUE);
            for (int i = 0; i < 9; i++) cs[i] = new Cell();
        }
    }

    private static final ThreadLocal<St> ST = new ThreadLocal<St>() {
        @Override protected St initialValue() { return new St(); }
    };

    private static final ThreadLocal<double[]> BF = new ThreadLocal<double[]>() {
        @Override protected double[] initialValue() { return new double[9]; }
    };

    /** Continuous boundary field over ALL plate pairs (softmin distance + soft-weighted force). */
    public static void boundaryField(long ws, double x, double z, double[] out) {
        double qx = x + warpX(x, z), qz = z + warpZ(x, z);
        St st = ST.get();
        Cell[] cs = st.cs;
        double[] vb = st.vb;
        long[] key = st.key;
        final double PL = LandContract.PLATE_CELL;
        long ci = (long) Math.floor(qx / PL), cj = (long) Math.floor(qz / PL);
        // The 3x3 window and its 9 cell velocities only change when the sample crosses a
        // plate-cell boundary; consecutive columns reuse them (36 noise evals saved).
        // NOTE: cells9() also fills cs[j].d, which depends on the QUERY POINT. The cache
        // below must therefore refresh the distances on every call (positions/ids are
        // window-constant, distances are not). Caching them made D/force piecewise constant
        // per PLATE_CELL -> 6,000 km square blocks and zero uplift inside each cell.
        for (int j = 0; j < 9; j++) {
            double ddx = qx - cs[j].sx, ddz = qz - cs[j].sz;
            cs[j].d = Math.sqrt(ddx * ddx + ddz * ddz);
        }
        if (key[0] != ci || key[1] != cj || key[2] != ws) {
            cells9(qx, qz, cs);
            for (int j = 0; j < 9; j++) vel(ws, cs[j], vb, j * 2);
            key[0] = ci; key[1] = cj; key[2] = ws;
            // continentality is window-constant too
            for (int j = 0; j < 9; j++) st.cc[j] = continentality(ws, cs[j].sx, cs[j].sz);
            // ---- point-independent pair table ----
            int np = 0;
            for (int a = 0; a < 9; a++) {
                for (int b = a + 1; b < 9; b++) {
                    if (cs[a].id == cs[b].id) continue;
                    double abx = cs[b].sx - cs[a].sx, abz = cs[b].sz - cs[a].sz;
                    double ab = Math.sqrt(abx * abx + abz * abz);
                    if (ab < 1.0) continue;
                    double ln = ab < 1.0 ? 1.0 : ab;
                    double nx = abx / ln, nz = abz / ln;
                    double dvx = vb[b * 2] - vb[a * 2], dvz = vb[b * 2 + 1] - vb[a * 2 + 1];
                    double conv = -(dvx * nx + dvz * nz);
                    double convS = 0.5 * (conv + Math.sqrt(conv * conv + 0.01));
                    st.pa[np] = a; st.pb[np] = b; st.pAb[np] = ab;
                    st.pNx[np] = nx; st.pNz[np] = nz;
                    st.pConvS[np] = convS; st.pConv[np] = conv;
                    st.pCcMin[np] = Math.min(st.cc[a], st.cc[b]);
                    {
                        double cA = continentality(ws, cs[a].sx, cs[a].sz);
                        double cB = continentality(ws, cs[b].sx, cs[b].sz);
                        st.pSide[np] = cA - cB;
                        st.pContMin[np] = Math.min(cA, cB);
                    }
                    np++;
                }
            }
            st.nPair = np;
        }
        final double Lm = SOFTMIN_L;
        // Locality gate: a cell 6000 km away can still sit near the bisector and contribute
        // a LARGE exp(-d/L), then vanish when the 3x3 window shifts -> D jumped 235 km.
        double dminC = 1e18;
        for (int j = 0; j < 9; j++) if (cs[j].d < dminC) dminC = cs[j].d;
        double sw = 0, sconv = 0, splunge = 0, ssign = 0, snx = 0, snz = 0;
        double sside = 0, scontMin = 0;
        final int np = st.nPair;
        for (int q = 0; q < np; q++) {
            final int a = st.pa[q], b = st.pb[q];
            double ra = Math.max(1.0, cs[a].d), rb = Math.max(1.0, cs[b].d);
            double d = Math.abs(ra * ra - rb * rb) / (2.0 * st.pAb[q]);
            double eC = cs[a].d - dminC;
            double w = Math.exp(-d / Lm) * Math.exp(-(eC * eC) / (2.0 * VEL_L * VEL_L));
            snx += w * st.pNx[q]; snz += w * st.pNz[q];
            sw += w;
            sconv += w * st.pConvS[q];
            splunge += w * st.pCcMin[q];
            ssign += w * st.pConv[q];
            sside += w * st.pSide[q] * (cs[a].d < cs[b].d ? 1.0 : -1.0);
            scontMin += w * st.pContMin[q];
        }
        out[7] = 0.0; out[8] = 1.0;
        if (sw >= 1e-9) { out[7] = sside / sw; out[8] = scontMin / sw; }
        if (sw < 1e-9) { out[0] = 1e9; out[1] = 0; out[2] = 0; out[3] = 0; out[4] = 0; out[5] = 0; out[6] = 0; return; }
        // clamp at 0: when several pairs sit on their bisector at once, sum(w) > 1 and
        // -L*ln(sum) goes NEGATIVE, which flattened the mid-ocean ridge dome into a
        // 6 km plateau. A distance is non-negative by definition. The clamp is C1-safe
        // for the dome (its derivative vanishes at D = 0).
        out[0] = Math.max(0.0, -Lm * Math.log(sw));
        out[1] = sconv / sw;
        out[2] = splunge / sw;
        out[3] = 1.0;
        out[4] = ssign / sw;
        out[5] = snx / sw;    // weighted boundary NORMAL (normalised below)
        out[6] = snz / sw;   // SIGNED convergence: <0 = diverging (mid-ocean ridge)
    }



    /**
     * L1 构造强度 T(x)：9 个近邻细胞速度的**加权方差**。
     *
     * <p>方差而不是"两两速度差"：方差对成员置换不变，所以换细胞时不跳；
     * 且它是平方和，处处可导。
     */
    public static double tectonicIntensity(long ws, double x, double z) {
        double qx = x + warpX(x, z), qz = z + warpZ(x, z);
        Cell[] cs = SC.get();
        cells9(qx, qz, cs);
        double[] vb = VB.get();
        for (int j = 0; j < 9; j++) {
            vel(ws, cs[j], vb, j * 2);
        }
        double L2 = T_L * T_L;
        // RELATIVE weights (anchored at the nearest seed). With an absolute Gaussian and
        // L=600 km inside 6000 km cells, W underflows to ~1e-11 at cell centres and the old
        // if (W<1e-12) return 0 produced an exact 0 -> a 3.4 km/km cliff in the uplift.
        double dmin = 1e18;
        for (int j = 0; j < 9; j++) {
            double dx = qx - cs[j].sx, dz = qz - cs[j].sz;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d < dmin) dmin = d;
        }
        double W = 0, mx = 0, mz = 0;
        for (int j = 0; j < 9; j++) {
            double dx = qx - cs[j].sx, dz = qz - cs[j].sz;
            double d = Math.sqrt(dx * dx + dz * dz);
            double e = d - dmin;
            double w = Math.exp(-(e * e) / (2.0 * L2));
            W += w; mx += w * vb[j * 2]; mz += w * vb[j * 2 + 1];
        }
        mx /= W; mz /= W;
        double var = 0;
        for (int j = 0; j < 9; j++) {
            double dx = qx - cs[j].sx, dz = qz - cs[j].sz;
            double d = Math.sqrt(dx * dx + dz * dz);
            double e = d - dmin;
            double w = Math.exp(-(e * e) / (2.0 * L2));
            double ex = vb[j * 2] - mx, ez = vb[j * 2 + 1] - mz;
            var += w * (ex * ex + ez * ez);
        }
        return Math.sqrt(var / W);     // 速度差的 RMS（量纲同速度）
    }

    /** 大陆度 c(x)：脊化噪声（与 v2 一致，保证海岸线风格不变）。 */
    public static double continentality(long ws, double x, double z) {
        double a = LandNoise.value(x / LandContract.C_WL0, z / LandContract.C_WL0, ws ^ 0xA0L);
        double b = LandNoise.value(x / LandContract.C_WL, z / LandContract.C_WL, ws ^ 0xA1L);
        double c = LandNoise.value(x / LandContract.C_WL2 + 2.7, z / LandContract.C_WL2 - 3.9, ws ^ 0xA2L);
        double r1 = 1.0 - Math.abs(2.0 * b - 1.0);
        double r2 = 1.0 - Math.abs(2.0 * c - 1.0);
        return 0.55 * a + 0.27 * r1 + 0.18 * r2;
    }

    /** 内部：高度 = 均衡 + 低地 + 细节。 */
    static double f(long ws, double x, double z, double[] outH) {
        // Hoisted above the land/sea decision so the fractal-coast amplitude can depend on
        // the uplift field (island belts must keep their coastline). uplift() does not
        // depend on su, so this is a pure reorder.
        double up = uplift(ws, x, z) * UPLIFT_GAIN;
        double c = continentality(ws, x, z);
        double cutV = cut(ws);
        // (A) fractal coastal detail: perturb the THRESHOLD, not the height
        double dC = 0.0;
        if (COAST_FBM_AMP > 0.0) {
            double wl = COAST_FBM_WL, amp = COAST_FBM_AMP;
            if (COAST_FBM_DTREF > 0.0) {
                amp /= (1.0 + Math.abs(up) / COAST_FBM_DTREF);
            }
            for (int i = 0; i < COAST_FBM_OCT; i++) {
                dC += amp * (LandNoise.value(x / wl + i * 2.7, z / wl - i * 1.9, ws ^ (0xC0L + i * 0x11L)) - 0.5) * 2.0;
                amp *= 0.55;
                wl *= 0.5;
            }
        }
        double su = smoothstep(cutV - 0.5 * COAST_GATE + dC, cutV + 0.5 * COAST_GATE + dC, c);
        double base = LandContract.T_OCEAN
                + (LandContract.T_CONT - LandContract.T_OCEAN) * su - LandContract.T_REF;
        // 低地/盆地：只在内陆生效，且不许把陆地压到海平面以下
        double lv = LandNoise.value(x / LOW_WL, z / LOW_WL, ws ^ 0xE7L);
        double low = smoothstep(0.62, 0.32, lv);
        double dt = up + CRATON_M * su - LOW_AMP * low * su;
        double hEst = base + dt;
        if (hEst > 2_000.0) dt *= 1.0;   // 预留：高段衰减
        // 细节
        double d1 = DET1 * (LandNoise.value(x / DWL1, z / DWL1, ws ^ 0xF1L) - 0.5) * 2.0;
        double d2 = DET2 * (LandNoise.value(x / DWL2 + 3.3, z / DWL2 - 2.7, ws ^ 0xF2L) - 0.5) * 2.0;
        double d3 = DET3 * (LandNoise.value(x / DWL3 + 1.1, z / DWL3 + 4.9, ws ^ 0xF3L) - 0.5) * 2.0;
        // MESOSCALE (20-80 km): the band that DECIDES DRAINAGE. Without it the base is nearly
        // a plane inside a 300 km window (relief ~600 m), so the LEM's D8 picks the same
        // steepest direction everywhere -> parallel herringbone rivers.
        double m1 = MESO_AMP * (LandNoise.value(x / MESO_WL + 7.7, z / MESO_WL - 5.3, ws ^ 0xF4L) - 0.5) * 2.0;
        double m2 = MESO_AMP * 0.5 * (LandNoise.value(x / (MESO_WL * 0.42) - 2.1, z / (MESO_WL * 0.42) + 8.8, ws ^ 0xF5L) - 0.5) * 2.0;
        double det = d1 + d2 + d3 + m1 + m2;
        // Detail attenuation used to start at 2000 m with slope 1.8: at 3500 m the factor was
        // 0.43, so every high area became a FLAT PLATEAU (observed: a 200 km window at 3.5 km
        // was featureless and the LEM just etched a uniform crackle into it).
        // Raised to 4000 m with slope 1.0 so mountains keep their texture.
        if (hEst > 4_000.0) det *= 4_000.0 / (4_000.0 + 1.0 * (hEst - 4_000.0));
        if (COAST_DET_HI > 0.0) {
            double coastRef = Math.abs(base + dt);
            det *= smoothstep(COAST_DET_LO, COAST_DET_HI, coastRef);
        }
        double fv = base + dt + det;
        // 软地板：低地不越海平面（连续，非 if 钳位）
        double wl = smoothstep(0.55, 0.80, su);
        if (wl > 0.0) {
            double e = fv - 1_200.0;
            double lifted = 1_200.0 + 0.5 * (e + Math.sqrt(e * e + 60_000.0));
            fv += wl * (lifted - fv);
        }
        double h = LandContract.K_ISO * fv;
        if (fv < 0.0) {
            double age = LandNoise.value(x / 8_000_000.0, z / 8_000_000.0, ws ^ 0xE3L);
            double gateD = -fv / 2_000.0;
            if (gateD > 1.0) gateD = 1.0;
            h -= LandContract.SEA_AGE_SUB * age * (1.0 - su) * gateD;
        }
        if (DEBUG_COMPONENTS) {
            double[] d = DBG.get();
            d[0] = c; d[1] = cutV; d[2] = su; d[3] = base;
            d[4] = dt; d[5] = det; d[6] = fv; d[7] = h;
            d[8] = d1; d[9] = d2; d[10] = d3; d[11] = m1; d[12] = m2;
        }
        outH[0] = h;
        return fv;
    }

    // ================= 内部工具 =================

    /** ψ 的无散梯度 → 细胞速度（常数）。 */
    private static void vel(long ws, Cell c, double[] out, int off) {
        double h = LandContract.VEL_FD_STEP;
        double dX = (psi(ws, c.sx, c.sz + h) - psi(ws, c.sx, c.sz - h)) / (2.0 * h);
        double dZ = (psi(ws, c.sx + h, c.sz) - psi(ws, c.sx - h, c.sz)) / (2.0 * h);
        double sc = LandContract.VEL_GAIN * LandContract.VEL_WL * 0.5;
        out[off] = dX * sc + LandContract.VEL_JITTER * (LandNoise.h01(0xB2L, c.id, 3L) - 0.5);
        out[off + 1] = -dZ * sc + LandContract.VEL_JITTER * (LandNoise.h01(0xC2L, c.id, 5L) - 0.5);
    }

    private static double psi(long ws, double x, double z) {
        return LandNoise.value(x / LandContract.VEL_WL, z / LandContract.VEL_WL, ws ^ 0xB1L) - 0.5;
    }

    private static void cells9(double qx, double qz, Cell[] out) {
        final double L = LandContract.PLATE_CELL;
        long ci = (long) Math.floor(qx / L), cj = (long) Math.floor(qz / L);
        int m = 0;
        for (int dj = -1; dj <= 1; dj++) {
            for (int di = -1; di <= 1; di++) {
                long i = ci + di, j = cj + dj;
                double sx = (i + 0.5 + LandContract.PLATE_JIT * (LandNoise.h01(0x111L, i, j) - 0.5)) * L;
                double sz = (j + 0.5 + LandContract.PLATE_JIT * (LandNoise.h01(0x222L, i, j) - 0.5)) * L;
                double dx = qx - sx, dz = qz - sz;
                out[m].sx = sx; out[m].sz = sz;
                out[m].d = Math.sqrt(dx * dx + dz * dz);
                out[m].id = LandNoise.mix(0x555L ^ (i * 0x2545F4914F6CDD1DL) ^ (j * 0x94D049BB133111EBL));
                m++;
            }
        }
    }

    private static double warpX(double x, double z) {
        return LandContract.PW_A1 * (LandNoise.value(x / LandContract.PW_WL1, z / LandContract.PW_WL1, 0x71L) - 0.5) * 2
             + LandContract.PW_A2 * (LandNoise.value(x / LandContract.PW_WL2 + 3.1, z / LandContract.PW_WL2 - 1.7, 0x72L) - 0.5) * 2
             + LandContract.PW_A3 * (LandNoise.value(x / LandContract.PW_WL3 - 2.3, z / LandContract.PW_WL3 + 4.1, 0x73L) - 0.5) * 2
             + LandContract.PW_A4 * (LandNoise.value(x / LandContract.PW_WL4 + 1.9, z / LandContract.PW_WL4 + 2.7, 0x74L) - 0.5) * 2;
    }

    private static double warpZ(double x, double z) {
        return LandContract.PW_A1 * (LandNoise.value(x / LandContract.PW_WL1 + 7.7, z / LandContract.PW_WL1 - 3.1, 0x75L) - 0.5) * 2
             + LandContract.PW_A2 * (LandNoise.value(x / LandContract.PW_WL2 - 5.3, z / LandContract.PW_WL2 + 1.9, 0x76L) - 0.5) * 2
             + LandContract.PW_A3 * (LandNoise.value(x / LandContract.PW_WL3 + 6.1, z / LandContract.PW_WL3 - 4.7, 0x77L) - 0.5) * 2
             + LandContract.PW_A4 * (LandNoise.value(x / LandContract.PW_WL4 - 3.3, z / LandContract.PW_WL4 + 5.9, 0x78L) - 0.5) * 2;
    }

    /** Ridged multifractal limited to octaves >= ~50 km (for coarse-sampled solvers). */
    static double ridgedMFCoarse(long ws, double x, double z) {
        double wl = RIDGE_WL, amp = 1.0, norm = 0.0, sum = 0.0, wgt = 1.0;
        int oct = Math.min(RIDGE_OCT, 2);
        for (int i = 0; i < oct; i++) {
            double v = LandNoise.value(x / wl + i * 7.3, z / wl - i * 3.1, ws ^ (0x91L + i * 0x11L));
            double rr = 1.0 - Math.abs(2.0 * v - 1.0);
            rr = rr * rr;
            sum += wgt * rr * amp; norm += amp; wgt *= (0.45 + 0.55 * rr); amp *= 0.5; wl *= 0.5;
        }
        return norm > 1e-12 ? sum / norm : 0.0;
    }

    /** Ridged multifractal: branching crest network (Musgrave). */
    /** Same as {@link #ridgedMF} but limited to 3 octaves (>= 55 km) for coarse modulation. */
    static double ridgedMF3(long ws, double x, double z) {
        double wl = RIDGE_WL, amp = 1.0, norm = 0.0, sum = 0.0, wgt = 1.0;
        for (int i = 0; i < 3; i++) {
            double v = LandNoise.value(x / wl + i * 7.3, z / wl - i * 3.1, ws ^ (0x91L + i * 0x11L));
            double rr = 1.0 - Math.abs(2.0 * v - 1.0);
            rr = rr * rr;
            sum += wgt * rr * amp;
            norm += amp;
            wgt *= (0.45 + 0.55 * rr);
            amp *= 0.5;
            wl *= 0.5;
        }
        return norm > 1e-12 ? sum / norm : 0.0;
    }

    static double ridgedMF(long ws, double x, double z) {
        double wl = RIDGE_WL, amp = 1.0, norm = 0.0, sum = 0.0, wgt = 1.0;
        for (int i = 0; i < RIDGE_OCT; i++) {
            double v = LandNoise.value(x / wl + i * 7.3, z / wl - i * 3.1, ws ^ (0x91L + i * 0x11L));
            double rr = 1.0 - Math.abs(2.0 * v - 1.0);
            rr = rr * rr;
            sum += wgt * rr * amp;
            norm += amp;
            wgt *= (0.45 + 0.55 * rr);
            amp *= 0.5;
            wl *= 0.5;
        }
        return norm > 1e-12 ? sum / norm : 0.0;
    }

    /** Public alias for probes. */
    public static double smoothstepPublic(double a, double b, double x) { return smoothstep(a, b, x); }

    private static double smoothstep(double e0, double e1, double x) {
        double t = (x - e0) / (e1 - e0);
        if (t < 0.0) t = 0.0; else if (t > 1.0) t = 1.0;
        return t * t * (3.0 - 2.0 * t);
    }
}
