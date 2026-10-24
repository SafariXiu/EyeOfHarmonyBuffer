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
    /**
     * Speed reference for subduction uplift, in NORMALIZED velocity units (1.0 = median
     * plate relative speed = 50 mm/yr, see LandContract.V_SCALE).
     *
     * <p>PTP 2019 §4.1: {@code u_j = u0 f(d) g(v) h(z)}, {@code g(v) = v/v0} (linear,
     * saturating at 1). {@code v0} is the speed at which the subduction uplift reaches its
     * full calibrated amplitude, so ARC_AMP is only reached at {@code v >= V_REF}.
     *
     * <p>0.60 is measured, not guessed: the median POSITIVE normal convergence at the arc
     * ring (side gate > 0.8, oceanic) is 0.59 (p60 0.79, p90 1.31). Taking the median keeps
     * the typical arc at today's amplitude, while slow (near-transform) margins fade out.
     */
    public static double V_REF = 0.60;
    public static double Z_TRENCH_N = -1.0, Z_CONT_N = 1.0;
    /** Radial profile of the arc band around ARC_DIST (km). Wider = softer fan at vertices. */
    public static double ARC_WIDTH_K = 1.0;
    // ARC_WIDTH narrowed 160 km -> 90 km: the 320 km band was a solid continent-scale mass
// rather than a chain. Amplitude must stay high (the abyssal floor is -4 km, so ~27,000
// thickness is needed just to emerge) -- the width was what made it a block.
    // 弧环必须与主造山峰（fd 峰在 R_S/3 = 600 km）重合，否则同一侧会出现两条平行岛链：
    // 窄环 320 km + 主峰 600 km。这里把弧环移到 600 km 并加宽，两者合并成一条带。
    public static double ARC_AMP = 32_000.0, ARC_DIST = 600_000.0, ARC_WIDTH = 180_000.0;
    /** 旧弧环参数（320 km / 90 km）。关掉 ARC_ALIGN_ON 就回到旧世界（用于逐位对照）。 */
    public static boolean ARC_ALIGN_ON = true;
    public static double ARC_DIST_OLD = 320_000.0, ARC_WIDTH_OLD = 90_000.0;
    public static double ARC_MASK_WL = 140_000.0, ARC_MASK_LO = 0.32, ARC_MASK_HI = 0.68;

    /**
     * Along-strike segmentation of the arc belt (world space, NOT a tangent projection --
     * that jumped whenever the nearest boundary point switched, see §53.3).
     *
     * <p>Real arc systems are 1,000-3,000 km long and are broken by slab tears / transform
     * offsets; one 6,000 km plate boundary never carries one unbroken chain. The wavelength
     * is tied to the model's own plate scale (PLATE_CELL / 3 = 2,000 km) instead of being a
     * free number, and the edge is deliberately SHARP: segmentation decides WHERE a belt
     * exists, it must not dim an existing belt into nothing.
     */
    public static double ARC_SEG_WL = LandContract.PLATE_CELL / 3.0;
    // 0.455/0.465 = the 60% quantile of the 2-octave fBm (p40 = 0.456, p60 = 0.539):
    // a 6,000 km boundary then carries roughly 60% arc belt, which is the density the user
    // picked after seeing the 50% version (2019 arc systems are 1,000-3,000 km with gaps).
    public static double ARC_SEG_LO = 0.455, ARC_SEG_HI = 0.465;
    /** Speed scaling of the arc (PTP 2019 g(v)); false = old constant-amplitude behaviour. */
    public static boolean ARC_SPEED_ON = true;
    /** Along-strike segmentation of the arc belt; false = belt runs the whole boundary. */
    public static boolean ARC_SEG_ON = true;
    /**
     * Numerical half-width of the "which way are the plates moving" gate, in normalized
     * velocity units (1.0 = median relative speed = 50 mm/yr, LandContract.V_SCALE).
     * 0.10 = 5 mm/yr.
     *
     * <p>NOT a physical threshold: it is the width over which the SIGN of bf[4] is smoothed.
     * bf[4] is a weighted average over pairs, so it oscillates through zero near a triple
     * junction; a hard sign test would flicker there and print radial stripes.
     */
    public static double PLATE_SIGN_W = 0.10;
    /**
     * Gate ridges / trenches / collisions on the plates' ACTUAL relative motion.
     *
     * <p>Same defect class as the island arc (§58): all three were driven by quantities that
     * cannot tell convergence from divergence (a softplus `force`, or the unsigned distance
     * `D`), so a feature appeared merely because two plates touch. false = old behaviour,
     * bit for bit (for A/B).
     */
    public static boolean PLATE_STATE_ON = true;
    /**
     * Which plate subducts: use the plates' ACTUAL type + age (PTP 2019 §4.1) instead of the
     * continentality contrast alone.
     *
     * <blockquote>"Two converging oceanic plates always lead to the subduction of the older
     * plate. An oceanic plate always plunges beneath a continental plate."</blockquote>
     *
     * <p>The old side gate compared continentality of the two seeds, which is right for
     * ocean-continent but for ocean-ocean is just noise: the same margin could pick a side
     * from continentality and flip where the noise crossed zero. false = old behaviour.
     */
    public static boolean POLARITY_FROM_AGE = true;
    /**
     * 两层板块（B′）：粗晶格的巨型板块 + 只在粗边界带出生的中小板块。
     *
     * <p>默认 **false**（关掉逐位回到单层 6,000 km 晶格）。打开后仍是**无权最近种子 Voronoi**：
     * 每块板都含自己的种子、边界是两个最近种子的中垂线、不嵌套、无空单元。
     */
    public static boolean PLATE_TWO_TIER_ON = false;
    /**
     * 大陆-板块耦合（稳态版）：把**汇聚带的稳态地壳增厚**加进地壳厚度场，
     * 于是陆/海与海岸线开始响应板块（不再只是山带）。
     *
     * <p>依据：稳态造山楔理论（Whipple &amp; Meade 2004, JGR Earth Surface 109, F01011）——
     * 地壳厚度由**当前汇聚速率**决定，不需要对时间积分。实现见 {@link #boundaryField} 的 out[11]。
     *
     * <p>⚠ 与既有 {@code uplift} 的造山存在**部分重复**：两者都源自汇聚。这里刻意取较小的幅度，
     * 只让海岸线/陆块轮廓响应，山峰仍由 uplift 负责。
     */
    public static boolean STEADY_OROG_ON = true;
    /**
     * 稳态增厚的最大幅度（m 厚度）。取 **35,000** = 青藏高原的地壳增厚量
     * （35 km → 70 km，CRUST1.0 量级），K_ISO·35 km ≈ 5,303 m 的额外高程；
     * 实测全球最高 4,078 → 8,386 m（仍在 MAX_PEAK_M=10,240 之内）。
     */
    public static double OROG_STEADY_AMP = 35_000.0;
    /** 造山带半宽（m）：观测 300–700 km，取 500 km。 */
    public static double OROG_STEADY_LAMBDA = 500_000.0;
    // ================= 大陆成因：克拉通核 + 古缝合带（幽灵板块） =================
    /**
     * 大陆模板模式：
     * <ul>
     *   <li>0 = 纯噪声（旧世界）</li>
     *   <li>1 = 每块当前板块一个 τ（“印章感”，保留做 A/B）</li>
     *   <li>2 = **克拉通核（低频噪声）+ 古缝合带**：幽灵板块的边界 ⟹ 增生带，默认</li>
     * </ul>
     * 文献依据：大陆是克拉通 + 增生造山带的拼贴体（Cawood et al. 2009；Hawkesworth &amp; Kemp 2006）。
     */
    public static int TEMPLATE_MODE = 0;   // 纯噪声大陆（用户 2026-10-24 提议；1=板块τ, 2=幽灵带, 3=克拉通穹顶）
    /** 幽灵板块的晶格间距（m）。 */
    public static double GHOST_CELL = 9_000_000.0;
    /** 幽灵板块相对真实板块的旋转角（弧度）。 */
    public static double GHOST_ANGLE = 0.4363;      // 25°
    /** 平移（m），避免与真实板块重合。 */
    public static double GHOST_OFFSET = 5_000_000.0;
    /** 增生带半宽（m）。 */
    public static double GHOST_LAMBDA = 700_000.0;
    /** 增生带对大陆度的最大贡献。 */
    public static double GHOST_AMP = 0.45;
    /** 增生带的地壳增厚（m）：让古缝合带读起来像**山脉**，而不只是多出一块陆地。 */
    public static double GHOST_UP_AMP = 4_000.0;

    // ---- 克拉通穹顶（大陆基底）----
    /** 克拉通种子的晶格间距（m）。 */
    public static double CRATON_CELL = 7_000_000.0;
    /** 大陆基底自己的域扭曲（与板块的 warp 独立）：把圆形穹顶改成有机形状。 */
    public static double CRATON_WARP_A = 4_000_000.0;
    public static double CRATON_WARP_WL = 12_000_000.0;
    /** 单个穹顶半径（m）。R/晶格 ≈ 0.8 ⟹ 相邻穹顶会连成大陆。 */
    public static double CRATON_R = 7_000_000.0;
    /** 低频“大陆遮罩”的波长（m）。点阵遮罩开启时不再使用。 */
    public static double CRATON_WL = 32_000_000.0;
    // ---- 大陆簇点阵（无限世界里的“两边大陆”）：点阵间距 ~35,000 km ----
    /** 是否用簇点阵代替噪声遮罩。 */
    public static boolean CLUSTER_MASK_ON = true;
    public static double CLUSTER_CELL = 35_000_000.0;
    /** 簇核心半径（m）：里面必定是大陆。 */
    public static double CLUSTER_RIN = 14_000_000.0;
    /** 簇外缘半径（m）：外面退化成海。 */
    public static double CLUSTER_ROUT = 21_000_000.0;
    // 海岸线细节：把噪声加到【距离】上，再进 smoothstep ⟹ 核内(d≪R)饱和为 1、深海为 0，
    // 只有海岸带被扰动。既不扭曲内部，也不改陆地总量（噪声均值 0）。
    public static boolean COAST_RADIAL_ON = false;
    public static double COAST_RADIAL_AMP = 1_500_000.0;
    public static double COAST_RADIAL_WL = 3_000_000.0;
    /** 遮罩的软阈值：低于 LO 的地方穹顶被压到海里，高于 HI 的地方成陆。 */
    public static double CRATON_MASK_LO = 0.45;
    public static double CRATON_MASK_HI = 0.62;
    /** 噪声纹理幅度（只做细节，不能把穹顶挖穿）。 */
    public static double CRATON_NOISE = 0.15;
    /** 缝合带“造陆”的闸门：c_base 超过它才开始加陆地（只在陆缘/陆内，不在洋中架桥）。 */
    public static double CRATON_GATE_LO = 0.20;
    public static double CRATON_GATE_HI = 0.45;

    /** 大陆簇点阵遮罩：每个簇一个软圆盘，并集 = 成片的大陆；世界里周期性重现。 */
    private static double clusterMask(double x, double z) {
        final double L = CLUSTER_CELL;
        long ci = (long) Math.floor(x / L), cj = (long) Math.floor(z / L);
        double best = 0.0;
        for (int dj = -1; dj <= 1; dj++) {
            for (int di = -1; di <= 1; di++) {
                long i = ci + di, j = cj + dj;
                double sx = (i + 0.5 + LandContract.PLATE_JIT * (LandNoise.h01(0xD11L, i, j) - 0.5)) * L;
                double sz = (j + 0.5 + LandContract.PLATE_JIT * (LandNoise.h01(0xD22L, i, j) - 0.5)) * L;
                double r = CLUSTER_RIN + (CLUSTER_ROUT - CLUSTER_RIN) * LandNoise.h01(0xD33L, i, j);
                double dx = x - sx, dz = z - sz;
                double d = Math.sqrt(dx * dx + dz * dz);
                // 海岸线细节：噪声加到【距离】上，再进 smoothstep ⟹ 核内饱和为 1、深海为 0，
                // 只有海岸带被扰动。既不扭曲内部，也不改陆地总量（噪声均值 0）。
                if (COAST_RADIAL_ON && COAST_RADIAL_AMP > 0.0) {
                    double wl = COAST_RADIAL_WL, amp = COAST_RADIAL_AMP, sum = 0.0;
                    for (int o = 0; o < 3; o++) {
                        sum += amp * (LandNoise.value(x / wl + o * 1.7, z / wl - o * 2.3, 0xE44L + o * 0x11L) - 0.5) * 2.0;
                        amp *= 0.55;
                        wl *= 0.5;
                    }
                    d += sum;
                }
                // 核内平坦(=1)、核外平滑降到 0 ⟹ 大陆是整块而不是一个尖峰。
                double m = 1.0 - smoothstep(CLUSTER_RIN, r, d);
                if (m > best) best = m;
            }
        }
        return best;
    }

    /** 克拉通穹顶场：并集（max）的一组抛物面 ⟹ 大陆是凸的宽块，不会围出大洋。 */
    private static double cratonField(double x, double z) {
        // 大陆基底专用域扭曲：两倍频、幅度 CRATON_WARP_A。
        double wx = x + CRATON_WARP_A * (LandNoise.value(x / CRATON_WARP_WL, z / CRATON_WARP_WL, 0xE11L) - 0.5) * 2.0;
        double wz = z + CRATON_WARP_A * (LandNoise.value(x / CRATON_WARP_WL + 5.3, z / CRATON_WARP_WL - 2.7, 0xE22L) - 0.5) * 2.0;
        x = wx; z = wz;
        final double L = CRATON_CELL;
        long ci = (long) Math.floor(x / L), cj = (long) Math.floor(z / L);
        // 大陆遮罩：簇点阵（默认）或旧的低频噪声。
        double mask;
        if (CLUSTER_MASK_ON) {
            mask = clusterMask(x, z);
        } else {
            double m01 = LandNoise.value(x / CRATON_WL + 11.3, z / CRATON_WL - 7.1, 0x77L);
            mask = smoothstep(CRATON_MASK_LO, CRATON_MASK_HI, m01);
        }
        double best = 0.0;
        for (int dj = -1; dj <= 1; dj++) {
            for (int di = -1; di <= 1; di++) {
                long i = ci + di, j = cj + dj;
                double sx = (i + 0.5 + LandContract.PLATE_JIT * (LandNoise.h01(0xC11L, i, j) - 0.5)) * L;
                double sz = (j + 0.5 + LandContract.PLATE_JIT * (LandNoise.h01(0xC22L, i, j) - 0.5)) * L;
                double r = CRATON_R * (0.70 + 0.60 * LandNoise.h01(0xC33L, i, j));
                double dx = x - sx, dz = z - sz;
                double q = (dx * dx + dz * dz) / (r * r);
                if (q < 1.0) {
                    double v = 1.0 - q;
                    if (v > best) best = v;
                }
            }
        }
        // 遮罩为主、穹顶只做内部纹理 ⟹ 大陆是一整块（不会碎成许多穹顶岛）。
        // 穹顶只做纹理：抬高基底地板，避免穹顶之间的鞍部被切穿成大内海。
        return mask * (0.85 + 0.15 * best);
    }

    /**
     * 大陆模板与板块挂钩：每块板一个“陆壳倾向” τ（由板块 id 哈希），
     * 有效大陆度 = (1−W)·噪声 + W·τ(点处按种子权重平滑)。
     *
     * <p>⟹ **换板块 = 换大陆位置**（这正是“大陆跟着板块走”的那一半）。
     * W=0：纯噪声（旧世界）；W=1：纯板块。
     */
    public static boolean PLATE_TEMPLATE_ON = true;
    /** 保留给旧 A/B：纯线性混合系数（新公式不再用它）。 */
    public static double TAU_W = 0.5;
    /**
     * 噪声在“海岸带”的幅度：c_eff = τ + (c_noise−0.5)·(MIN + MAX·4τ(1−τ))。
     * τ→0/1（纯洋/纯陆）时噪声被压到 MIN ⟹ **板内不会被挖出大洋**；
     * τ≈0.5（板块边界带）时噪声拉满 ⟹ 海岸线仍是分形的。
     */
    public static double TAU_NOISE_MIN = 0.06;
    public static double TAU_NOISE_MAX = 0.50;
    /** 跨板块 τ 的平滑半宽（m）。 */
    public static double TAU_BLEND = 800_000.0;
    /**
     * 陆架/内陆海：大陆板块内部被淹没的洼地不应该有 5–6 km 的洋底深度。
     * 用 τ 高的地方把洋底抬高 ⟹ 内陆海变成 1–3 km 的浅海（地中海/黑海量级）。
     */
    public static boolean SHELF_ON = true;
    /** 抬高幅度（m 厚度；16,000 ≈ 2,424 m 高程）。 */
    public static double SHELF_AMP = 16_000.0;
    /** 陆/海阈值的整体平移（标定用，使陆占比保持 ~32%）。 */
    public static double TAU_CUT_1 = 0.0;
    /** 两层（12 板）标定：τ 的方差大得多，阈值要下移。 */
    public static double TAU_CUT_TT = 0.0;

    /** 板块的“陆壳倾向” τ ∈ [0,1]，只由板块 id 决定。 */
    private static double tauSeed(long id) {
        return LandNoise.h01(0x7A1L, id, 0L);
    }
    /**
     * Sea-floor age from the distance to the RIDGE (PTP 2019 §4.3), not from a noise field.
     *
     * <p>The paper blends the interpolated plate elevation with a ridge template by
     * {@code α = dΓ/(dΓ+dP)} (dΓ = distance to the ridge, dP = distance to the closest
     * plate): at the ridge α=0 and the floor is the ridge profile, at the plate interior
     * α=1 and it is abyssal. We compute α inside {@link #boundaryField} (out[9]) and use it
     * for BOTH the ridge swell (weight 1-α) and the ageing subsidence (weight α).
     *
     * <p>false = old behaviour (350 km Gaussian ridge + 8,000 km noise age), bit for bit.
     */
    public static boolean SEA_AGE_FROM_RIDGE = true;
    /**
     * Parsons &amp; Sclater (1977), J. Geophys. Res. 82(5): 803-827: ocean-floor depth follows
     * {@code d(t) = 2500 + 350·sqrt(t)} metres for {@code t <= 70 Ma} and flattens after.
     * 70 Ma is their validity limit (the plate model departs from half-space cooling there).
     *
     * <p>Used for the SHAPE only: the 350 m/sqrt(Ma) coefficient cancels when the subsidence
     * is normalised to 1 at 70 Ma, so the existing amplitude calibration (SEA_AGE_SUB) is
     * unchanged and only the depth-vs-age profile becomes square-root.
     */
    public static double PS_AGE_MAX_MA = 70.0;
    /**
     * Derive the sea-floor age from the SPREADING RATE ({@code t = dΓ / v_half}) instead of
     * from distance alone. Fast ridges then get narrow flanks and slow ridges wide ones.
     * false = geometric normalisation (dΓ / (PLATE_CELL/3)), for A/B.
     */
    public static boolean AGE_FROM_RATE = true;
    /**
     * Continental rifts: a divergent boundary between two CONTINENTAL plates thins the crust
     * instead of building a range. Written as the mirror of the orogeny and applied to the
     * final height (after the land soft-floor), because a rift valley must be allowed to drop
     * BELOW sea level (rift lakes / Red Sea), which the land soft-floor would otherwise clamp.
     *
     * <p>Amplitude/width reuse {@code LandContract.A_RIFT / S_RIFT}. ⚠ Their provenance is
     * NOT FOUND (they are v2 leftovers, "模型自己的选择"); flagged in the design doc as P3c.
     */
    // 2026-10-24 第二轮：**默认关**。连续的裂谷项把每条陆-陆离散边界都挖成一条海（用户实测：
    // 大陆上出现沿板块边界的海）。物理原因见设计文档 §62.4：10 km 地壳减薄按 Airy 必然掉
    // ~1.5 km，而本世界的陆地中位数只有 ~0.6 km；地球上裂谷坐在 1–2 km 的高原穹隆上，
    // 本模型没有那个穹隆。重开后必须先解决"穹隆/肩部"或改成"早期浅裂谷"，见 §62.5。
    public static boolean RIFT_ON = false;
    /** Along-strike segmentation of rifts (same structural scale as the arc segmentation). */
    public static boolean RIFT_SEG_ON = true;
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
    public static boolean COAST_FBM2_ON = true;
    /** 海岸细节（作为阈值扰动，远离海岸时 smoothstep 饱和 ⟹ 内部不被改动）。 */
    public static double COAST_FBM2_AMP = 0.025;
    public static double COAST_FBM2_WL = 2_000_000.0;
    // ---- 海岸细节（干净版）：只动【海岸带】的高度，不动内部/深洋 ----
    // base += COAST_DETAIL_AMP · exp(−((c−cut)/W)²) · fbm(x)  ⟹ 海岸线局部起伏，内部/深海为 0。
    public static boolean COAST_DETAIL_ON = false;
    public static double COAST_DETAIL_AMP = 2_500.0;    // 厚度（m）：2,500 → 379 m 高程
    public static double COAST_DETAIL_W = 0.05;         // 海岸带的宽度（大陆度单位）
    public static double COAST_DETAIL_WL = 1_500_000.0;  // 最粗波长
    public static int COAST_DETAIL_OCT = 4;
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
    /** 局部地壳类型耦合（P1）：用当地大陆度决定边缘形态（山在陆、沟在洋、碰撞只在陆上）。 */
    public static boolean LOCAL_CRUST_ON = true;
    /** 陆壳造山增益（P1 之后陆地造山需要重新标定）。 */
    public static double MOUNTAIN_GAIN = 3.0;
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
    /** 标定中的候选阈值：非 NaN 时 cut() 直接返回它（打断 cut→height→cut 递归）。 */
    private static final ThreadLocal<double[]> CALIB = ThreadLocal.withInitial(() -> new double[]{Double.NaN});

    /** 真实陆地占比（按最终高度 h>0，而不是大陆度代理）。 */
    private static double landFracActual(long ws) {
        int n = 64;
        double span = 40_000_000.0;
        int land = 0;
        for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) {
            if (height(ws, (i + .5) * span / n, (j + .5) * span / n) > 0) land++;
        }
        return land / ((double) n * n);
    }

    public static double cut(long ws) {
        double[] cal = CALIB.get();
        if (!Double.isNaN(cal[0])) return cal[0];
        // 模板/板块集合会改变“有效大陆度”的分布 ⟹ 阈值必须按模式分别缓存，
        // 否则 A/B 切模式时会复用另一套的 cut（实测两层世界会错成 64% 陆地）。
        long k = ws;
        k = k * 31 + (PLATE_TEMPLATE_ON ? 1 : 0);
        k = k * 31 + (PLATE_TWO_TIER_ON ? 1 : 0);
        k = k * 31 + Double.doubleToLongBits(TAU_NOISE_MIN);
        k = k * 31 + Double.doubleToLongBits(TAU_NOISE_MAX);
        k = k * 31 + Double.doubleToLongBits(SHELF_AMP);
        k = k * 31 + TEMPLATE_MODE;
        k = k * 31 + Double.doubleToLongBits(GHOST_AMP);
        k = k * 31 + Double.doubleToLongBits(GHOST_LAMBDA);
        k = k * 31 + Double.doubleToLongBits(GHOST_CELL);
        k = k * 31 + Double.doubleToLongBits(CRATON_CELL);
        k = k * 31 + Double.doubleToLongBits(CRATON_R);
        k = k * 31 + Double.doubleToLongBits(CRATON_NOISE);
        k = k * 31 + Double.doubleToLongBits(CRATON_WARP_A);
        k = k * 31 + (COAST_FBM2_ON ? 1 : 0);
        k = k * 31 + Double.doubleToLongBits(COAST_FBM2_AMP);
        k = k * 31 + Double.doubleToLongBits(CRATON_WL);
        k = k * 31 + Double.doubleToLongBits(CRATON_MASK_LO);
        k = k * 31 + Double.doubleToLongBits(CRATON_MASK_HI);
        k = k * 31 + (CLUSTER_MASK_ON ? 1 : 0);
        k = k * 31 + Double.doubleToLongBits(CLUSTER_CELL);
        k = k * 31 + Double.doubleToLongBits(CLUSTER_RIN);
        k = k * 31 + Double.doubleToLongBits(CLUSTER_ROUT);
        // Single-entry volatile cache: the ConcurrentHashMap lookup boxed the long and
        // showed up as 3.7% of samples in the load profile.
        if (k == CUT_LAST_WS) return CUT_LAST_V;
        Double v = CUTS.get(k);
        if (v != null) { CUT_LAST_WS = ws; CUT_LAST_V = v.doubleValue(); return v.doubleValue(); }
        double lo = 0.20, hi = 0.95;
        for (int it = 0; it < 14; it++) {
            double mid = 0.5 * (lo + hi);
            if (landFrac(ws, mid) > LandContract.LAND_TARGET) lo = mid; else hi = mid;
        }
        double c = 0.5 * (lo + hi);
        CUTS.put(k, c);
        CUT_LAST_WS = k;
        CUT_LAST_V = c;
        return c;
    }

    /**
     * 点处的板块“陆壳倾向” τ（种子加权平均）。只用 cells9，**不碰 cut()**——
     * 否则 cut→landFrac→boundaryField→overrideScore→cut 会无限递归。
     */
    private static double plateTauAt(long ws, double x, double z) {
        Cell[] cs = SC.get();
        cells9(x + warpX(x, z), z + warpZ(x, z), cs);
        double dminC = 1e18;
        for (int j = 0; j < 9; j++) if (cs[j].d < dminC) dminC = cs[j].d;
        double pNum = 0, pDen = 0;
        for (int j = 0; j < 9; j++) {
            double e = cs[j].d - dminC;
            double wj = Math.exp(-(e * e) / (2.0 * TAU_BLEND * TAU_BLEND));
            pNum += wj * tauSeed(cs[j].id);
            pDen += wj;
        }
        return pDen > 1e-12 ? pNum / pDen : 0.5;
    }

    private static final ThreadLocal<Cell[]> GHOST = new ThreadLocal<Cell[]>() {
        @Override protected Cell[] initialValue() {
            Cell[] a = new Cell[9];
            for (int i = 0; i < 9; i++) a[i] = new Cell();
            return a;
        }
    };

    /** 幽灵板块（古格局）的 3×3 种子：晶格与 hash 都与真实板块独立。 */
    private static void ghostCells9(double qx, double qz, Cell[] out) {
        final double L = GHOST_CELL;
        long ci = (long) Math.floor(qx / L), cj = (long) Math.floor(qz / L);
        int m = 0;
        for (int dj = -1; dj <= 1; dj++) {
            for (int di = -1; di <= 1; di++) {
                long i = ci + di, j = cj + dj;
                double sx = (i + 0.5 + LandContract.PLATE_JIT * (LandNoise.h01(0x811L, i, j) - 0.5)) * L;
                double sz = (j + 0.5 + LandContract.PLATE_JIT * (LandNoise.h01(0x822L, i, j) - 0.5)) * L;
                double dx = qx - sx, dz = qz - sz;
                out[m].sx = sx; out[m].sz = sz;
                out[m].d = Math.sqrt(dx * dx + dz * dz);
                out[m].id = LandNoise.mix(0x855L ^ (i * 0x2545F4914F6CDD1DL) ^ (j * 0x94D049BB133111EBL));
                m++;
            }
        }
    }

    /**
     * 古缝合带强度（0..1）：幽灵板块格局**旋转+平移**后，其边界的汇聚段就是增生带。
     * 只用最近两粒幽灵种子（一次 cells9 + 两次 vel），不建对表。
     */
    private static double ghostBelts(long ws, double x, double z) {
        final double ca = Math.cos(GHOST_ANGLE), sa = Math.sin(GHOST_ANGLE);
        double gx = x * ca - z * sa + GHOST_OFFSET;
        double gz = x * sa + z * ca + GHOST_OFFSET;
        Cell[] gs = GHOST.get();
        ghostCells9(gx, gz, gs);
        int i1 = 0;
        for (int j = 1; j < 9; j++) if (gs[j].d < gs[i1].d) i1 = j;
        int i2 = -1;
        double best = 1e18;
        for (int j = 0; j < 9; j++) {
            if (j == i1) continue;
            if (gs[j].d < best) { best = gs[j].d; i2 = j; }
        }
        if (i2 < 0) return 0.0;
        double abx = gs[i2].sx - gs[i1].sx, abz = gs[i2].sz - gs[i1].sz;
        double ab = Math.sqrt(abx * abx + abz * abz);
        if (ab < 1.0) return 0.0;
        double dEdge = (gs[i2].d * gs[i2].d - gs[i1].d * gs[i1].d) / (2.0 * ab);
        if (dEdge > 4.0 * GHOST_LAMBDA) return 0.0;
        // 只有“汇聚型”的幽灵边界才增生。用两粒幽灵种子的 id 哈希决定（世界设定，
        // 与 τ 同性质）——省掉两次 vel（8 次噪声），单层世界 height() 才能守在预算内。
        if (LandNoise.h01(0x9AAL, Math.min(gs[i1].id, gs[i2].id), Math.max(gs[i1].id, gs[i2].id)) > 0.55) {
            return 0.0;
        }
        return Math.exp(-Math.max(0.0, dEdge) / GHOST_LAMBDA);
    }

    private static final ThreadLocal<double[]> VP = new ThreadLocal<double[]>() {
        @Override protected double[] initialValue() { return new double[2]; }
    };
    private static final ThreadLocal<double[]> VP2 = new ThreadLocal<double[]>() {
        @Override protected double[] initialValue() { return new double[2]; }
    };

    /** 板块模板下的有效大陆度：噪声只在海岸带（τ≈0.5）放大，板内由 τ 主导。 */
    private static double effectiveC(long ws, double x, double z, double c, double tp, double gb) {
        if (TEMPLATE_MODE == 3) {
            // 克拉通穹顶 + 少量噪声纹理 + 只在陆缘/陆内生效的缝合带
            double base = cratonField(x + warpX(x, z), z + warpZ(x, z));
            double gate = smoothstep(CRATON_GATE_LO, CRATON_GATE_HI, base);
            return base + CRATON_NOISE * (c - 0.5) + GHOST_AMP * gb * gate;
        }
        if (TEMPLATE_MODE == 2) {
            // 克拉通核（噪声）+ 古缝合带（幽灵板块的汇聚边界）
            return c + GHOST_AMP * gb;
        }
        if (TEMPLATE_MODE == 1) {
            double s = 4.0 * tp * (1.0 - tp);
            return tp + (c - 0.5) * (TAU_NOISE_MIN + TAU_NOISE_MAX * s);
        }
        return c;
    }

    private static double landFrac(long ws, double c0) {
        int n = 96; double span = 40_000_000.0; int land = 0, tot = 0;
        for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) {
            double x = i * span / n, z = j * span / n;
            double c = continentality(ws, x, z);
            if (PLATE_TEMPLATE_ON) {
                c = effectiveC(ws, x, z, c, plateTauAt(ws, x, z), ghostBelts(ws, x, z));
            }
            if (c > c0) land++;
            tot++;
        }
        return land / (double) tot;
    }

    /** 地形高度（米）。不含 LEM：这是演化前的 uplift 结果。 */
    /**
     * 海陆权威判定（用户 2026-10-25 裁决）：**只看掩膜**，不看 h 的符号。
     *
     * <p>契约变化：负高度**仍然是陆地**（海岸洼地）；湿地/湖泊由后续的 LEM + 水系层解出来，
     * 本层不负责。因此下游**不得**再用 {@code h > 0} 判海陆。
     *
     * <p>⚠ 已知后果：MC 的水位线仍然是 Y=海平面 ⟹ 这些负高度陆地点在游戏里会先显示成浅水，
     * 直到水系层给出"不填水"的覆盖。这是刻意接受的过渡状态。
     */
    public static boolean isLand(long ws, double x, double z) {
        return coastS(ws, x, z) > 0.0;
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
        h = h * 31 + LandContract.VERSION;
        h = h * 31 + (USE_PAPER ? 1 : 0);
        h = h * 31 + Double.doubleToLongBits(U0);
        h = h * 31 + Double.doubleToLongBits(R_S);
        h = h * 31 + Double.doubleToLongBits(SOFTMIN_L);
        h = h * 31 + Double.doubleToLongBits(VEL_L);
        h = h * 31 + Double.doubleToLongBits(LandContract.LAND_TARGET);
        // ---- island arc / ridge ----
        // Rule ⑤: every quantity that can change the output must be in the stamp, otherwise
        // a tuning pass silently reuses terrain-dependent climate tiles built with the old
        // values. That is exactly what happened when ARC_WIDTH/mask were retuned on 10-24.
        h = h * 31 + (ARC_ALIGN_ON ? 1 : 0);
        h = h * 31 + (COAST_FBM2_ON ? 1 : 0);
        h = h * 31 + (C_FINE_ON ? 1 : 0);
        h = h * 31 + (LOCAL_CRUST_ON ? 1 : 0);
        h = h * 31 + (HEIGHT_V2 ? 1 : 0);
        h = h * 31 + (COAST_TIER_ON ? 1 : 0);
        h = h * 31 + Double.doubleToLongBits(V2_BEACH_W);
        h = h * 31 + Double.doubleToLongBits(V2_BEACH_H);
        h = h * 31 + (COAST_DETAIL_ON ? 1 : 0);
        h = h * 31 + (COAST_RADIAL_ON ? 1 : 0);
        h = h * 31 + Double.doubleToLongBits(COAST_RADIAL_AMP);
        h = h * 31 + Double.doubleToLongBits(COAST_RADIAL_WL);
        h = h * 31 + Double.doubleToLongBits(COAST_DETAIL_AMP);
        h = h * 31 + Double.doubleToLongBits(COAST_DETAIL_W);
        h = h * 31 + Double.doubleToLongBits(COAST_FBM2_AMP);
        h = h * 31 + Double.doubleToLongBits(COAST_FBM2_WL);
        h = h * 31 + Double.doubleToLongBits(ARC_AMP);
        h = h * 31 + Double.doubleToLongBits(ARC_DIST);
        h = h * 31 + Double.doubleToLongBits(ARC_WIDTH);
        h = h * 31 + Double.doubleToLongBits(ARC_MASK_WL);
        h = h * 31 + Double.doubleToLongBits(ARC_MASK_LO);
        h = h * 31 + Double.doubleToLongBits(ARC_MASK_HI);
        h = h * 31 + Double.doubleToLongBits(V_REF);
        h = h * 31 + (ARC_SPEED_ON ? 1 : 0);
        h = h * 31 + (ARC_SEG_ON ? 1 : 0);
        h = h * 31 + Double.doubleToLongBits(ARC_SEG_WL);
        h = h * 31 + Double.doubleToLongBits(ARC_SEG_LO);
        h = h * 31 + Double.doubleToLongBits(ARC_SEG_HI);
        h = h * 31 + Double.doubleToLongBits(RIDGE_OC_AMP);
        h = h * 31 + Double.doubleToLongBits(RIDGE_OC_W);
        // ---- trench / single-sided orogeny ----
        h = h * 31 + (OROG_SIDE_ON ? 1 : 0);
        h = h * 31 + Double.doubleToLongBits(OROG_SIDE_T);
        h = h * 31 + Double.doubleToLongBits(OROG_COLLIDE_LO);
        h = h * 31 + Double.doubleToLongBits(OROG_COLLIDE_HI);
        h = h * 31 + Double.doubleToLongBits(OROG_COLLIDE_W);
        h = h * 31 + Double.doubleToLongBits(TRENCH_AMP);
        h = h * 31 + Double.doubleToLongBits(TRENCH_OFF);
        h = h * 31 + Double.doubleToLongBits(TRENCH_W);
        h = h * 31 + (PLATE_STATE_ON ? 1 : 0);
        h = h * 31 + Double.doubleToLongBits(PLATE_SIGN_W);
        h = h * 31 + (SEA_AGE_FROM_RIDGE ? 1 : 0);
        h = h * 31 + (AGE_FROM_RATE ? 1 : 0);
        h = h * 31 + (POLARITY_FROM_AGE ? 1 : 0);
        h = h * 31 + (PLATE_TWO_TIER_ON ? 1 : 0);
        h = h * 31 + (STEADY_OROG_ON ? 1 : 0);
        h = h * 31 + (PLATE_TEMPLATE_ON ? 1 : 0);
        h = h * 31 + Double.doubleToLongBits(TAU_W);
        h = h * 31 + Double.doubleToLongBits(TAU_NOISE_MIN);
        h = h * 31 + Double.doubleToLongBits(TAU_NOISE_MAX);
        h = h * 31 + TEMPLATE_MODE;
        h = h * 31 + Double.doubleToLongBits(GHOST_AMP);
        h = h * 31 + Double.doubleToLongBits(GHOST_LAMBDA);
        h = h * 31 + Double.doubleToLongBits(GHOST_CELL);
        h = h * 31 + Double.doubleToLongBits(GHOST_ANGLE);
        h = h * 31 + Double.doubleToLongBits(CRATON_CELL);
        h = h * 31 + Double.doubleToLongBits(CRATON_R);
        h = h * 31 + Double.doubleToLongBits(CRATON_NOISE);
        h = h * 31 + Double.doubleToLongBits(CRATON_WL);
        h = h * 31 + Double.doubleToLongBits(CRATON_MASK_LO);
        h = h * 31 + Double.doubleToLongBits(CRATON_MASK_HI);
        h = h * 31 + (CLUSTER_MASK_ON ? 1 : 0);
        h = h * 31 + Double.doubleToLongBits(CLUSTER_CELL);
        h = h * 31 + Double.doubleToLongBits(CLUSTER_RIN);
        h = h * 31 + Double.doubleToLongBits(CLUSTER_ROUT);
        h = h * 31 + Double.doubleToLongBits(TAU_BLEND);
        h = h * 31 + (SHELF_ON ? 1 : 0);
        h = h * 31 + Double.doubleToLongBits(SHELF_AMP);
        h = h * 31 + Double.doubleToLongBits(TAU_CUT_1);
        h = h * 31 + Double.doubleToLongBits(TAU_CUT_TT);
        h = h * 31 + Double.doubleToLongBits(OROG_STEADY_AMP);
        h = h * 31 + Double.doubleToLongBits(OROG_STEADY_LAMBDA);
        h = h * 31 + Double.doubleToLongBits(LandContract.TT_L1);
        h = h * 31 + Double.doubleToLongBits(LandContract.TT_L2);
        h = h * 31 + Double.doubleToLongBits(LandContract.TT_BAND);
        h = h * 31 + (RIFT_ON ? 1 : 0);
        h = h * 31 + Double.doubleToLongBits(LandContract.MAX_PEAK_M);
        h = h * 31 + (RIFT_SEG_ON ? 1 : 0);
        h = h * 31 + Double.doubleToLongBits(LandContract.A_RIFT);
        h = h * 31 + Double.doubleToLongBits(LandContract.S_RIFT);
        h = h * 31 + Double.doubleToLongBits(PS_AGE_MAX_MA);
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

    /** 新高度场（掩膜 + 浮雕）开关；关掉回到旧的混合场（用于逐位对照）。 */
    public static boolean HEIGHT_V2 = true;

    // ---- 新高度场参数（掩膜 + 浮雕）----
    public static double V2_LAND_BASE = 250.0;     // 陆地基底（m）
    public static double V2_HILL_AMP = 360.0;      // 丘陵起伏
    public static double V2_MTN_AMP = 6_200.0;     // 山脉起伏（× wMountain）
    /** 稀有高峰项（× wMountain² × ridged³）：只有最强山带的最强脊线才够高 ⟹ 9000–10000 m 的极少数峰。 */
    public static double V2_PEAK_AMP = 1_880.0;   // 稀有高峰：重标到 max≈7,980 m ⟹ Y≈254.5（留 1 格余量）
    /** 山带没入陆架时的抬升（m，负深度）：让山带"沉入海中"而不是硬切在岸线。 */
    public static double V2_MTN_SHELF = 1_200.0;
    /** 山脉靠海的 s 门宽：1e-4 ≈ 1 km。让山能走到海边（形成断崖），而不是在几十公里外消失。 */
    public static double V2_MTN_COAST_S = 1e-4;
    /** 山脊噪声的各向异性：沿带 / 横带 的波长比。 */
    public static double V2_BELT_ANISO = 4.0;
    public static double V2_PLAIN_AMP = 60.0;      // 平原微起伏
    public static double V2_SHELF_DEPTH = 220.0;   // 海岸处水深
    public static double V2_ABYSS = 4_600.0;       // 深海
    public static double V2_TRENCH = 1_800.0;      // 海沟下切（× wTrench）
    public static double V2_RIDGE = 700.0;         // 洋中脊隆起（× wRidge）
    public static double V2_ARC = 1_200.0;         // 岛弧隆起（× wArc）
    public static double V2_COAST_W = 0.05;        // 海岸→内陆/深海 的过渡宽度（大陆度单位）
    // ---- tier2 海岸层（沙滩 / 断崖）----
    public static boolean COAST_TIER_ON = true;
    /** 沙滩带宽（m）：岸内侧 0..V2_BEACH_W 内压平到 V2_BEACH_H。 */
    public static double V2_BEACH_W = 900.0;
    /** 沙滩高度（m）。 */
    public static double V2_BEACH_H = 7.0;
    /** 判定为“断崖”的内陆高度阈值（m）：1 km 内就很高 ⟹ 不做沙滩，保留陡壁。 */
    public static double V2_CLIFF_HI = 220.0;
    // ---- 公共基底噪声：陆/洋共用同一套，海岸处幅度相等 ⟹ 天然连续 ----
    /** 海岸处的公共起伏幅度（m）——两侧相等，保证接缝为零。 */
    public static double V2_COAST_AMP = 60.0;
    /** 内陆的公共起伏幅度（m）。 */
    public static double V2_LAND_AMP = 320.0;
    /** 深海的公共起伏幅度（m）（深海丘陵）。 */
    public static double V2_SEA_AMP = 220.0;
    /** 第二组（细尺度）公共起伏。 */
    public static double V2_COAST_AMP2 = 15.0;
    public static double V2_LAND_AMP2 = 45.0;
    public static double V2_SEA_AMP2 = 25.0;

    private static final ThreadLocal<double[]> W5V2 = ThreadLocal.withInitial(() -> new double[9]);
    private static final ThreadLocal<double[]> W5V2B = ThreadLocal.withInitial(() -> new double[9]);

    /** 沿带拉长的山脊噪声：u = 横带，v = 沿带。 */
    private static double ridgedBelt(double x, double z, double nx, double nz, double wl, long seed) {
        double u = (x * nx + z * nz) / wl;
        double v = (-x * nz + z * nx) / (wl * V2_BELT_ANISO);
        double sum = 0.0, a = 1.0, s = 0.0;
        for (int o = 0; o < 3; o++) {
            double n = 1.0 - Math.abs(LandNoise.perlin(u, v, seed + o * 0x31L));
            sum += a * n; s += a; a *= 0.5; u *= 2.0; v *= 2.0;
        }
        return sum / s;
    }

    private static double fbm3(double x, double z, double wl, long seed) {
        return 0.60 * LandNoise.perlin(x / wl, z / wl, seed)
             + 0.30 * LandNoise.perlin(x / (wl * 0.45), z / (wl * 0.45), seed ^ 0x11L)
             + 0.15 * LandNoise.perlin(x / (wl * 0.20), z / (wl * 0.20), seed ^ 0x22L);
    }

    private static double ridged3(double x, double z, double wl, long seed) {
        double v = 0.0, a = 1.0, w = wl, sum = 0.0;
        for (int o = 0; o < 3; o++) {
            double n = 1.0 - Math.abs(LandNoise.perlin(x / w, z / w, seed + o * 0x31L));
            v += a * n; sum += a; a *= 0.5; w *= 0.5;
        }
        return v / sum;
    }

    /** 新高度场：land 由掩膜唯一决定，h 只是浮雕（§77 铁律）。 */
    /** 有符号到岸距离（米，陆正海负）：d ≈ s/|∇s|。只在海岸带内调用（5 次 coastS）。
     *  注意：与历史方法 {@link #coastDist}（用 height 求梯度、陆负海正）是两套，别混用。 */
    public static double coastDistM(long ws, double x, double z) {
        double e = 200.0;
        double s = coastS(ws, x, z);
        double gx = (coastS(ws, x + e, z) - coastS(ws, x - e, z)) / (2 * e);
        double gz = (coastS(ws, x, z + e) - coastS(ws, x, z - e)) / (2 * e);
        double gl = Math.sqrt(gx * gx + gz * gz);
        return gl > 1e-15 ? s / gl : 0.0;
    }

    /** 有符号海岸场（>0 陆，<0 海，0 = 海岸线）：与 heightV2 的 s 完全相同。 */
    public static double coastS(long ws, double x, double z) {
        double c = continentality(ws, x, z);
        double dC = 0.0;
        {
            double wl = COAST_FBM2_ON ? COAST_FBM2_WL : COAST_FBM_WL;
            double amp = COAST_FBM2_ON ? COAST_FBM2_AMP : COAST_FBM_AMP;
            for (int i = 0; i < COAST_FBM_OCT; i++) {
                dC += amp * (LandNoise.value(x / wl + i * 2.7, z / wl - i * 1.9, ws ^ (0xC0L + i * 0x11L)) - 0.5) * 2.0;
                amp *= 0.55; wl *= 0.5;
            }
        }
        return c + dC - cut(ws);
    }

    public static double heightV2(long ws, double x, double z) {
        double c = continentality(ws, x, z);
        double cut = cut(ws);
        // 海岸阈值扰动（与旧路径同源：只扰动阈值，不动内部）
        double dC = 0.0;
        {
            double wl = COAST_FBM2_ON ? COAST_FBM2_WL : COAST_FBM_WL;
            double amp = COAST_FBM2_ON ? COAST_FBM2_AMP : COAST_FBM_AMP;
            for (int i = 0; i < COAST_FBM_OCT; i++) {
                dC += amp * (LandNoise.value(x / wl + i * 2.7, z / wl - i * 1.9, ws ^ (0xC0L + i * 0x11L)) - 0.5) * 2.0;
                amp *= 0.55; wl *= 0.5;
            }
        }
        double s = c + dC - cut;                         // >0 陆，<0 海
        double[] w = W5V2.get();
        RegionField.weights(ws, x, z, w);
        // 不再用各向异性坐标（交汇点会产生扇形伪影）；带由 weight 给出，噪声各向同性。
        double bnx = 1.0, bnz = 0.0;
        // 公共基底（陆/洋共用，海岸处幅度相等 ⟹ 接缝为零）
        double n1 = fbm3(x, z, 220_000.0, ws ^ 0x6B1L);
        double n2 = fbm3(x, z, 40_000.0, ws ^ 0x6B2L);
        if (s > 0) {
            double lf = smoothstep(0.0, V2_COAST_W, s);  // 0 海岸 → 1 内陆
            double a1 = V2_COAST_AMP + (V2_LAND_AMP - V2_COAST_AMP) * lf;
            double a2 = V2_COAST_AMP2 + (V2_LAND_AMP2 - V2_COAST_AMP2) * lf;
            double h = V2_LAND_BASE * lf;
            h += a1 * n1 + a2 * n2;
            // 带（w[0]）连续，幅度按汇聚强度（w[8]）变化，但不为零 ⟹ 山脉沿带连绵
            double mAmp = 0.35 + 0.65 * (w.length > 8 ? w[8] : 1.0);
            double rf = ridged3(x, z, 300_000.0, ws ^ 0x6A2L);
            // 山带可以一直到海边：用很窄的 s 门（≈1 km），而不是 lf（≈500 km）
            double mtnGate = smoothstep(0.0, V2_MTN_COAST_S, s);
            h += V2_MTN_AMP * w[0] * mAmp * rf * mtnGate;
            if (V2_PEAK_AMP > 0.0) {
                double wm = w[0];
                h += V2_PEAK_AMP * wm * wm * rf * rf * rf * lf;   // 稀有高峰
            }
            return Math.min(h, LandContract.MAX_PEAK_M);
        }
        double sf = smoothstep(0.0, V2_COAST_W, -s);     // 0 海岸 → 1 深海
        double b1 = V2_COAST_AMP + (V2_SEA_AMP - V2_COAST_AMP) * sf;
        double b2 = V2_COAST_AMP2 + (V2_SEA_AMP2 - V2_COAST_AMP2) * sf;
        double depth = V2_SHELF_DEPTH + (V2_ABYSS - V2_SHELF_DEPTH) * sf;
        depth += V2_TRENCH * w[1];
        depth -= V2_RIDGE * w[2];
        depth -= V2_ARC * w[3];
        depth -= V2_MTN_SHELF * w[0];      // 山带没入陆架（水下延续）
        double h = -Math.max(120.0, depth) + b1 * n1 + b2 * n2;
        return Math.min(h, -1.0);                        // 掩膜权威：洋面点永不浮出
    }

    public static double height(long ws, double x, double z) {
        if (HEIGHT_V2) return heightV2(ws, x, z);
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
    /**
     * Island-arc strength in [0,1]: EXACTLY the product {@link #landformExtra} multiplies by
     * ARC_AMP, without the amplitude.
     *
     * <p>⚠ It used to be a stale copy (single-octave mask, {@code smoothstep(±ARC_GATE_W,
     * bf[4])} side gate, {@code min(1,|signed|)} factor). A locator built on it then points at
     * places the production field does not build an arc, which is worse than no locator at
     * all. Keep this in lock-step with landformExtra.
     */
    public static double arcRaw(long ws, double x, double z) {
        double[] bf = BF.get();
        boundaryField(ws, x, z, bf);
        if (bf[3] <= 0.0) return 0.0;
        double c = continentality(ws, x, z);
        double gate = smoothstep(LAND_GATE_LO, LAND_GATE_HI, c);
        double wArc = smoothstep(-OROG_SIDE_T, OROG_SIDE_T, bf[7]);
        if (ARC_SPEED_ON) wArc *= Math.min(1.0, Math.max(0.0, bf[4]) / V_REF);
        if (ARC_SEG_ON) wArc *= arcSegmentMask(ws, x, z);
        double ad = (bf[0] - ARC_DIST) / ARC_WIDTH;
        double arc = Math.exp(-ad * ad);
        if (arc <= 1e-4) return 0.0;
        double mw = ARC_MASK_WL;
        double m = 0.60 * LandNoise.value(x / mw, z / mw, ws ^ 0x7C1L)
                 + 0.32 * LandNoise.value(x / (mw * 0.45) + 3.1, z / (mw * 0.45) - 1.7, ws ^ 0x7C2L)
                 + 0.24 * LandNoise.value(x / (mw * 0.20) - 2.3, z / (mw * 0.20) + 5.9, ws ^ 0x7C3L);
        double mask = smoothstep(ARC_MASK_LO, ARC_MASK_HI, m / 1.06);
        return wArc * arc * mask * (1.0 - gate);
    }

    static double landformExtra(long ws, double x, double z, double[] bf, double D, double gate) {
        // (A) An island arc belongs to the OVERRIDING side of a CONVERGENT boundary; an
        // oceanic ridge belongs to a DIVERGENT one. Previously the ridge used (1-wArc),
        // i.e. the opposite side of ANY boundary, which is why every boundary showed two
        // parallel chains (arc on one side, ridge on the other).
        double sideArc = smoothstep(-OROG_SIDE_T, OROG_SIDE_T, bf[7]);
        // A mid-ocean ridge exists where the plates MOVE APART. The old gate used bf[1]
        // (force, a softplus): it is ~0 for divergence but also small for weak convergence,
        // so slow convergent and transform boundaries grew a ridge dome too.
        double div = PLATE_STATE_ON
                ? smoothstep(0.0, -PLATE_SIGN_W, bf[4])
                : smoothstep(0.15, 0.0, bf[1]);
        // AN ISLAND ARC IS AN OROGENIC BELT: it is the oceanic expression of the same
        // subduction thickening that builds a mountain range on land, so it must be driven by
        // COMPRESSION, not by "being next to a boundary".
        //
        // The earlier "conv" gate used bf[1] (force, a softplus) and was removed in §56.2
        // because it appeared to kill every arc: bf[1] is ~0 both for divergence AND for weak
        // convergence, so it could not tell the two apart. bf[4] is the SIGNED convergence --
        // < 0 diverging (ridge), > 0 converging (subduction) -- and it is the right signal.
        // PTP 2019 §4.1: u_j = u0 f(d) g(v) h(z) with g(v) = v/v0, i.e. the uplift is
        // proportional to the convergence speed and saturates at v0 = V_REF.
        double wArc = sideArc;
        if (ARC_SPEED_ON) {
            wArc *= Math.min(1.0, Math.max(0.0, bf[4]) / V_REF);
        }
        double extra = 0.0;
        // MONOTONIC profile from the boundary. The ring exp(-((D-320)/160)^2) wrapped around
        // every Voronoi vertex as many concentric arcs -- that IS the "fan"/terracing. A
        // monotone decay has no ring to wrap, so the artifact cannot form.
        // Ring at ARC_DIST from the boundary (same profile as arcRaw). Using D directly
        // put the whole ARC_AMP on the boundary itself.
        double ad = (D - (ARC_ALIGN_ON ? ARC_DIST : ARC_DIST_OLD))
                / (ARC_ALIGN_ON ? ARC_WIDTH : ARC_WIDTH_OLD);
        double arc = Math.exp(-ad * ad);
        if (arc > 1e-4 && ARC_AMP > 0.0) {
            // Segmentation is evaluated ONLY inside the 90 km ring: it needs two noise
            // octaves, and points outside the ring can never receive an arc anyway.
            if (ARC_SEG_ON) wArc *= arcSegmentMask(ws, x, z);
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
        // Mid-ocean ridge. OLD: a 350 km Gaussian keyed to D (distance to ANY boundary),
        // gated by the local sign. NEW (PTP 2019 §4.3): the young end of the alpha blend,
        // weight (1-alpha), where alpha is built from the distance to the nearest DIVERGING
        // boundary. The old gate must go: mid-plate the nearest boundary can be a trench,
        // and multiplying the swell by that sign would tear a step across the plate.
        double dome, ridgeGate;
        if (SEA_AGE_FROM_RIDGE) {
            dome = (1.0 - bf[9]) * bf[10];
            ridgeGate = 1.0;
        } else {
            double rd = D / RIDGE_OC_W;
            dome = Math.exp(-rd * rd);
            ridgeGate = div;
        }
        if (dome > 1e-4 && RIDGE_OC_AMP > 0.0) {
            double oceanic = 1.0 - gate;
            extra += ridgeGate * RIDGE_OC_AMP * dome * oceanic;
        }
        return extra;
    }

    /**
     * Along-strike segmentation mask for the arc belt, in WORLD space (0..1).
     *
     * <p>Why a separate, coarse mask: the fine {@code ARC_MASK_WL} (140 km) carves one belt
     * into islands, but it does nothing about the belt's LENGTH. Real arc systems are
     * 1,000-3,000 km long and end at slab tears / transform offsets; without this, one
     * 6,000 km plate boundary carries a continuous dotted chain from end to end.
     *
     * <p>A 2-D world-space fBm is used instead of a tangent projection: the projection
     * jumped whenever the nearest boundary point switched and painted sunburst fans (§53.3).
     */
    static double arcSegmentMask(long ws, double x, double z) {
        double wl = ARC_SEG_WL;
        double m = 0.62 * LandNoise.value(x / wl, z / wl, ws ^ 0x7D1L)
                 + 0.38 * LandNoise.value(x / (wl * 0.5) + 1.7, z / (wl * 0.5) - 4.3, ws ^ 0x7D2L);
        return smoothstep(ARC_SEG_LO, ARC_SEG_HI, m);
    }

    public static double upliftPaper(long ws, double x, double z) {
        double[] bf = BF.get();
        boundaryField(ws, x, z, bf);
        return upliftPaperFrom(ws, x, z, bf);
    }

    /**
     * Same as {@link #upliftPaper} but with the boundary field already computed.
     *
     * <p>{@code f()} needs bf[9] (the sea-floor age blend) as well as the uplift, and
     * {@code boundaryField} is the expensive part of {@code height()}; calling it twice
     * would nearly double terrain cost. Keeping the arithmetic in this method is
     * bit-identical to the old single-call form.
     */
    static double upliftPaperFrom(long ws, double x, double z, double[] bf) {
        double c = continentality(ws, x, z);
        double gate = smoothstep(LAND_GATE_LO, LAND_GATE_HI, c);
        double detail = 0.0;
        if (RIDGE_AMP > 0.0) detail += RIDGE_AMP * ridgedMF(ws, x, z) * gate * 0.30;
        if (FINE_AMP > 0.0) {
            detail += FINE_AMP * gate * ((LandNoise.value(x / FINE_WL, z / FINE_WL, ws ^ 0x9AL) - 0.5) * 2.0
                    + 0.5 * (LandNoise.value(x / (FINE_WL * 0.4), z / (FINE_WL * 0.4), ws ^ 0x9BL) - 0.5) * 2.0);
        }
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
            // 局部地壳类型：以 cut 为界的锐利陆/洋判定（而不是 LAND_GATE 那种很缓的坡）
            double ctype = smoothstep(cut(ws) - 0.06, cut(ws) + 0.06, c);
            if (LOCAL_CRUST_ON) {
                // 碰撞带只应出现在陆壳上，否则就是“海底碰撞山脊”
                bothO *= ctype;
            }
            double cd = D / OROG_COLLIDE_W;
            double profM = (1.0 - bothO) * wSideO * fd + bothO * Math.exp(-cd * cd);
            // Subducting side gets the trench (negative), NOT a mirror-image mountain belt.
            double profT = 0.0;
            if (TRENCH_AMP > 0.0) {
                double subSide = smoothstep(OROG_SIDE_T, -OROG_SIDE_T, bf[7]);
                double td = (D - TRENCH_OFF) / TRENCH_W;
                profT = -TRENCH_AMP * subSide * (1.0 - bothO) * Math.exp(-td * td);
            }
            // 山只在陆壳上、海沟只在洋壳侧
            // 陆壳造山增益：P1 把“海里的假山”关掉后，陆地造山需要单独标定（U0 原本是按混合世界定的）。
            double mgain = LOCAL_CRUST_ON ? MOUNTAIN_GAIN : 1.0;
            profile = LOCAL_CRUST_ON ? (profM * mgain * ctype + profT * (1.0 - ctype)) : (profM + profT);
        }
        // profile can now be NEGATIVE (trench). strength modulates the ridge detail and
        // must stay in [0,1]: an unclamped lower bound sent detail*(0.35+0.65*strength)
        // to -3e8 thickness (observed h = -46,504 km).
        // Mountains, trenches and collisions are all products of CONVERGENCE. Until now the
        // only suppression at a divergent boundary was `force` (a softplus that sits at
        // ~0.03, not 0), so rifts still got a low range and a phantom trench on one side.
        if (PLATE_STATE_ON) {
            profile *= smoothstep(0.0, PLATE_SIGN_W, bf[4]);
        }
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

    /** {D, force, plunge, valid, signedConv, nx, nz, side, contMin, alpha, ridgeFrac, steadyOrog}. */
    private static final ThreadLocal<double[]> BF = new ThreadLocal<double[]>() {
        @Override protected double[] initialValue() { return new double[13]; }
    };

    /**
     * Normalised plate age in [0,1] (0 = young, 1 = old). A world parameter (P1): each plate
     * draws a fixed age from its cell id, so the value is constant over the whole plate and
     * identical across runs. It is an ORDERING, not a date -- the model has no time
     * integration, so this is the plate's "birth age" setting, not something derived from
     * its motion history.
     */
    static double plateAge01(long cellId) {
        return LandNoise.h01(0xA5A5L, cellId, 0L);
    }

    /**
     * Override propensity of a plate (higher = stays on top, lower = subducts).
     *
     * <p>Continental crust never subducts under oceanic (PTP 2019 §4.1), so the crust TYPE is
     * lexicographically dominant: continental -> +1, oceanic -> -age. Between two oceanic
     * plates the older one is denser and therefore has the lower score, i.e. it subducts.
     */
    static double overrideScore(double continentalityAtSeed, double age01, long ws) {
        return continentalityAtSeed > cut(ws) ? 1.0 : -age01;
    }

    /** Plate age at the nearest seed (probe/diagnostic entry point). */
    public static double plateAgeAt(long ws, double x, double z) {
        Cell[] cs = SC.get();
        cells9(x + warpX(x, z), z + warpZ(x, z), cs);
        int k = 0;
        for (int j = 1; j < 9; j++) if (cs[j].d < cs[k].d) k = j;
        return plateAge01(cs[k].id);
    }

    /** Debug: record the highest-weight pair of the last boundaryField call. */
    public static volatile boolean DEBUG_PAIR = false;
    private static final ThreadLocal<double[]> DPAIR = new ThreadLocal<double[]>() {
        @Override protected double[] initialValue() { return new double[6]; }
    };

    /** {ageA, ageB, conv, sideSign, weight, pSide} of the dominant pair (debug). */
    public static double[] lastPair() { return DPAIR.get(); }

    /** {seedX, seedZ, age} of the nearest plate seed (probe/diagnostic entry point). */
    public static double[] plateSeedAt(long ws, double x, double z) {
        Cell[] cs = SC.get();
        cells9(x + warpX(x, z), z + warpZ(x, z), cs);
        int k = 0;
        for (int j = 1; j < 9; j++) if (cs[j].d < cs[k].d) k = j;
        return new double[]{cs[k].sx, cs[k].sz, plateAge01(cs[k].id)};
    }

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
        // B': the selected 9 seeds change WITHIN a cell (a fine seed can be much closer than
        // the coarse ones), so the pair table must be rebuilt per point. The band-based cache
        // was tried and REJECTED: it changed the sea-floor age (D_ridge is a softmin, so even
        // a far candidate shifts it) and produced 3.1 km steps. See 板块规模-44到10.md §8.3.
        if (PLATE_TWO_TIER_ON || key[0] != ci || key[1] != cj || key[2] != ws) {
            cells9(qx, qz, cs);
            SeedProps sp = SPC.get();
            if (sp.ws != ws) { sp.ws = ws; java.util.Arrays.fill(sp.key, Long.MIN_VALUE); }
            double[] vtmp = VELTMP.get();
            for (int j = 0; j < 9; j++) {
                long kb = Double.doubleToLongBits(cs[j].sx) * 0x9E3779B97F4A7C15L
                        ^ Double.doubleToLongBits(cs[j].sz);
                int h = (int) (LandNoise.mix(kb) & (SPC_SIZE - 1));
                if (sp.key[h] == kb) {
                    vb[j * 2] = sp.vx[h]; vb[j * 2 + 1] = sp.vz[h];
                    st.cc[j] = sp.c[h];
                } else {
                    vel(ws, cs[j], vtmp, 0);
                    double cseed = continentality(ws, cs[j].sx, cs[j].sz);
                    if (PLATE_TEMPLATE_ON) {
                        if (TEMPLATE_MODE == 3) {
                            // 与地图上的陆地模板一致：用克拉通穹顶场，而不是 τ 哈希。
                            // 否则“哪块板是陆壳”和“哪里是陆地”会脱节 ⟹ 假碰撞(对称山脊)/错极性。
                            double sx = cs[j].sx, sz = cs[j].sz;
                            cseed = cratonField(sx + warpX(sx, sz), sz + warpZ(sx, sz));
                        } else {
                            double tj = tauSeed(cs[j].id);
                            double sj = 4.0 * tj * (1.0 - tj);
                            cseed = tj + (cseed - 0.5) * (TAU_NOISE_MIN + TAU_NOISE_MAX * sj);
                        }
                    }
                    sp.key[h] = kb; sp.vx[h] = vtmp[0]; sp.vz[h] = vtmp[1]; sp.c[h] = cseed;
                    vb[j * 2] = vtmp[0]; vb[j * 2 + 1] = vtmp[1];
                    st.cc[j] = cseed;
                }
            }
            key[0] = ci; key[1] = cj; key[2] = ws;
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
                        double cA = st.cc[a], cB = st.cc[b];
                        if (POLARITY_FROM_AGE) {
                            // Higher score = OVERRIDES (stays up); lower = subducts.
                            // Continental crust always overrides an oceanic one; between two
                            // oceanic plates the older (denser) one subducts, so age LOWERS
                            // the score.
                            st.pSide[np] = overrideScore(cA, plateAge01(cs[a].id), ws)
                                         - overrideScore(cB, plateAge01(cs[b].id), ws);
                        } else {
                            st.pSide[np] = cA - cB;
                        }
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
        // 板块模板：τ 的种子加权平均（核宽 TAU_BLEND）。
        // ⚠ 必须在下面的 sw<1e-9 提前返回【之前】写，否则板内(无边界)的点会读到上一次的旧值。
        if (out.length > 12) {
            double pNum = 0, pDen = 0;
            for (int j = 0; j < 9; j++) {
                double e = cs[j].d - dminC;
                double wj = Math.exp(-(e * e) / (2.0 * TAU_BLEND * TAU_BLEND));
                pNum += wj * tauSeed(cs[j].id);
                pDen += wj;
            }
            out[12] = pDen > 1e-12 ? pNum / pDen : 0.5;
        }
        double sw = 0, sconv = 0, splunge = 0, ssign = 0, snx = 0, snz = 0;
        double sside = 0, scontMin = 0;
        double sOroNum = 0, sOroDen = 0;   // 稳态造山：汇聚速率的加权平均（核 = exp(-d/λ)）
        // PTP 2019 §4.3: soft-min distance over the DIVERGING pairs only (= distance to the
        // ridge, dΓ), plus the weighted mean divergence rate of those pairs (the spreading
        // rate that formed the crust).
        double swRidge = 0, sRate = 0;
        final int np = st.nPair;
        for (int q = 0; q < np; q++) {
            final int a = st.pa[q], b = st.pb[q];
            double ra = Math.max(1.0, cs[a].d), rb = Math.max(1.0, cs[b].d);
            double d = Math.abs(ra * ra - rb * rb) / (2.0 * st.pAb[q]);
            double eC = cs[a].d - dminC;
            double w = Math.exp(-d / Lm) * Math.exp(-(eC * eC) / (2.0 * VEL_L * VEL_L));
            snx += w * st.pNx[q]; snz += w * st.pNz[q];
            if (st.pConv[q] < 0.0) { swRidge += w; sRate += w * (-st.pConv[q]); }
            {
                // 稳态增厚的核与 boundary field 的 locality 核分开：λ=500 km 是造山带宽度，
                // 而 w 里的 SOFTMIN_L=120 km 只是边界场的局地化。
                double kOro = Math.exp(-d / OROG_STEADY_LAMBDA);
                sOroDen += kOro;
                if (st.pConv[q] > 0.0) sOroNum += kOro * st.pConv[q];
            }
            sw += w;
            sconv += w * st.pConvS[q];
            splunge += w * st.pCcMin[q];
            ssign += w * st.pConv[q];
            sside += w * st.pSide[q] * (cs[a].d < cs[b].d ? 1.0 : -1.0);
            scontMin += w * st.pContMin[q];
            if (DEBUG_PAIR && w > DPAIR.get()[4]) {
                double[] dp = DPAIR.get();
                double sideSgn = cs[a].d < cs[b].d ? 1.0 : -1.0;
                dp[0] = plateAge01(cs[a].id);
                dp[1] = plateAge01(cs[b].id);
                dp[2] = st.pConv[q];
                dp[3] = sideSgn;
                dp[4] = w;
                dp[5] = st.pSide[q];
            }
        }
        out[7] = 0.0; out[8] = 1.0;
        if (sw >= 1e-9) { out[7] = sside / sw; out[8] = scontMin / sw; }
        if (sw < 1e-9) {
            out[0] = 1e9; out[1] = 0; out[2] = 0; out[3] = 0; out[4] = 0; out[5] = 0; out[6] = 0;
            if (out.length > 9) out[9] = 1.0;    // no boundary in reach: old crust
            if (out.length > 10) out[10] = 0.0;  // ... and no ridge
            if (out.length > 11) out[11] = 0.0;  // ... and no convergence
            return;
        }
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
        // 稳态造山强度（0..1）：近边界汇聚速率的加权平均 / V_REF，再乘 exp(-D/λ)。
        if (out.length > 11) {
            double convAvg = sOroDen > 1e-9 ? sOroNum / sOroDen : 0.0;
            double o01 = Math.min(1.0, convAvg / V_REF) * Math.exp(-out[0] / OROG_STEADY_LAMBDA);
            out[11] = o01;
        }
        // ---- sea-floor age blend (PTP 2019 §4.3) ----
        // alpha = dΓ/(dΓ+dP): 0 at the ridge, ->1 towards the plate interior. dP is the
        // distance to the nearest plate seed, so dΓ and dP are anti-correlated along a
        // ridge->centre transect and alpha is a model-free normalised age.
        if (out.length > 9) {
            // Age proxy: normalised distance from the RIDGE, saturating at the plate scale.
            //
            // PTP 2019 §4.3 uses alpha = dΓ/(dΓ+dP) with dP = distance to the closest plate,
            // which in their dynamic model is the half-width of the freshly generated crust.
            // A static model has no such gap: taking dP = distance to the nearest plate SEED
            // turns every plate into a bowl and makes a TRENCH margin look young (both edges
            // far from the seed). dΓ alone, saturated at PLATE_CELL/3, is the honest
            // simplification: it gives ridge-parallel isobaths and reaches "old crust" well
            // before the boundary-free interior (where boundaryField short-circuits at ~2,500 km).
            double swR = Math.max(swRidge, 1e-300);
            double dRidge = Math.max(0.0, -Lm * Math.log(swR));
            double age01;
            if (AGE_FROM_RATE) {
                // t = dΓ / v_half. bf[4]-style conv is the FULL relative rate; each plate
                // moves half of it, and V_SCALE maps normalised units to m/yr (median
                // relative speed = 50 mm/yr, LandContract.V_SCALE).
                double rate = swRidge > 1e-12 ? sRate / swRidge : 0.0;
                double vHalf = 0.5 * rate * LandContract.V_SCALE;          // m/yr
                double ageMa = vHalf > 1e-12 ? dRidge / vHalf / 1.0e6 : 1.0e6;
                double u = ageMa / PS_AGE_MAX_MA;
                age01 = u >= 1.0 ? 1.0 : Math.sqrt(u);                    // P&S: depth ∝ sqrt(t)
            } else {
                age01 = dRidge / LandContract.PLATE_CELL * 3.0;           // geometric
                if (age01 > 1.0) age01 = 1.0;
            }
            out[9] = age01;
            // Share of the boundary weight that is DIVERGING. At a pure ridge it is 1, at a
            // transform it is 0, at a ridge/transform junction 0.5 -- without it a full ridge
            // dome appeared on a transform boundary that merely sat next to a diverging pair.
            if (out.length > 10) out[10] = sw > 1e-12 ? Math.min(1.0, swRidge / sw) : 0.0;
        }
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

    /** 大陆度的细倍频开关（海岸线 100–900 km 细节）。 */
    public static boolean C_FINE_ON = true;

    /** 大陆度 c(x)：脊化噪声（与 v2 一致，保证海岸线风格不变）。 */
    public static double continentality(long ws, double x, double z) {
        double a = LandNoise.value(x / LandContract.C_WL0, z / LandContract.C_WL0, ws ^ 0xA0L);
        double b = LandNoise.value(x / LandContract.C_WL, z / LandContract.C_WL, ws ^ 0xA1L);
        double c = LandNoise.value(x / LandContract.C_WL2 + 2.7, z / LandContract.C_WL2 - 3.9, ws ^ 0xA2L);
        double r1 = 1.0 - Math.abs(2.0 * b - 1.0);
        double r2 = 1.0 - Math.abs(2.0 * c - 1.0);
        double v = 0.55 * a + 0.27 * r1 + 0.18 * r2;
        if (C_FINE_ON) {
            // 更细的 2–3 个倍频：给海岸线 100–900 km 尺度的海湾/半岛/小岛。
            // 零均值 ⟹ 不改变陆占比的期望，只增加海岸长度；cut 会照常自标定。
            v += 0.050 * LandNoise.perlin(x / 900_000.0 + 7.1, z / 900_000.0 - 3.3, ws ^ 0xA7L);
            v += 0.030 * LandNoise.perlin(x / 400_000.0 - 2.2, z / 400_000.0 + 5.7, ws ^ 0xA8L);
            v += 0.020 * LandNoise.perlin(x / 180_000.0 + 1.4, z / 180_000.0 - 6.1, ws ^ 0xA9L);
        }
        return v;
    }

    /** 内部：高度 = 均衡 + 低地 + 细节。 */
    static double f(long ws, double x, double z, double[] outH) {
        // Hoisted above the land/sea decision so the fractal-coast amplitude can depend on
        // the uplift field (island belts must keep their coastline). uplift() does not
        // depend on su, so this is a pure reorder.
        //
        // boundaryField is hoisted OUT of upliftPaper here so f() can also read bf[9]
        // (the sea-floor age blend) without paying for a second boundary-field evaluation.
        double[] bf = BF.get();
        double up;
        if (USE_PAPER) {
            boundaryField(ws, x, z, bf);
            up = upliftPaperFrom(ws, x, z, bf) * UPLIFT_GAIN;
        } else {
            up = uplift(ws, x, z) * UPLIFT_GAIN;
        }
        double c = continentality(ws, x, z);
        double gb = (PLATE_TEMPLATE_ON && TEMPLATE_MODE >= 2) ? ghostBelts(ws, x, z) : 0.0;
        if (PLATE_TEMPLATE_ON && bf.length > 12) {
            // 阈值 cut 现在按**有效大陆度**二分（见 landFrac）⟹ 不再需要手工平移。
            c = effectiveC(ws, x, z, c, bf[12], gb) + (PLATE_TWO_TIER_ON ? TAU_CUT_TT : TAU_CUT_1);
        }
        double cutV = cut(ws);
        // (A) fractal coastal detail: perturb the THRESHOLD, not the height
        double dC = 0.0;
        if (COAST_FBM_AMP > 0.0) {
            double wl = COAST_FBM2_ON ? COAST_FBM2_WL : COAST_FBM_WL;
            double amp = COAST_FBM2_ON ? COAST_FBM2_AMP : COAST_FBM_AMP;
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
        // 稳态造山增厚：只在陆壳上生效（洋壳侧由岛弧/海沟负责），与 su 相乘 ⟹ 海岸线随汇聚带移动。
        // 完全剥离：造山/缝合带只在大陆【内部】加厚，海岸线附近为 0 ⟹ 板块不再移动海岸线。
        // 用比 su 更陡、且中心更高的闸门（cut+0.02 .. cut+0.06），海岸带内一点点加厚都不给。
        double cGate = smoothstep(cutV + 0.02, cutV + 0.06, c);
        double tadd = 0.0;
        if (STEADY_OROG_ON) {
            double o01 = bf.length > 11 ? bf[11] : 0.0;
            tadd = OROG_STEADY_AMP * o01 * su * cGate;
        }
        double base = LandContract.T_OCEAN
                + (LandContract.T_CONT - LandContract.T_OCEAN) * su - LandContract.T_REF + tadd;
        // 海岸细节（干净版）：只在 |c−cut| 很小时生效 ⟹ 内部与深洋完全不受影响（没有域扭曲）。
        if (COAST_DETAIL_ON && COAST_DETAIL_AMP > 0.0) {
            double dc = (c - cutV) / COAST_DETAIL_W;
            double band = Math.exp(-dc * dc);
            if (band > 1e-3) {
                double wl = COAST_DETAIL_WL, amp = COAST_DETAIL_AMP, sum = 0.0;
                for (int o = 0; o < COAST_DETAIL_OCT; o++) {
                    sum += amp * (LandNoise.value(x / wl + o * 2.1, z / wl - o * 1.3, ws ^ (0xE0L + o * 0x13L)) - 0.5) * 2.0;
                    amp *= 0.55;
                    wl *= 0.5;
                }
                base += band * sum;
            }
        }
        // 内陆海/陆架：τ 高的地方（大陆板块内部）把洋底抬高，避免“陆中大洋”有 5–6 km 深。
        if (SHELF_ON && PLATE_TEMPLATE_ON && bf.length > 12) {
            base += SHELF_AMP * bf[12] * (1.0 - su);
        }
        // 古缝合带的地壳增厚：让增生带是山脉，而不只是陆块边缘。
        if (PLATE_TEMPLATE_ON && TEMPLATE_MODE >= 2 && GHOST_UP_AMP > 0.0) {
            base += GHOST_UP_AMP * gb * su * cGate;
        }
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
        // Continental rift: divergence + continental crust -> crustal thinning (a valley).
        // 全球最高峰上限（用户 2026-10-24 裁决）：只削掉超过它的峰，低于它逐位不变。
        if (h > LandContract.MAX_PEAK_M) h = LandContract.MAX_PEAK_M;
        if (RIFT_ON && bf[3] > 0.0 && bf[4] < 0.0) {
            double divW = smoothstep(0.0, -PLATE_SIGN_W, bf[4]);
            if (divW > 0.0) {
                // Segment the rift along strike: one 6,000 km boundary does not open as one
                // continuous canal -- real rift systems are 1,000-2,000 km, broken at
                // transfer zones. Same wavelength as the arc segmentation, different seed.
                double seg = 1.0;
                if (RIFT_SEG_ON) {
                    double segWl = ARC_SEG_WL;
                    double m = 0.62 * LandNoise.value(x / segWl, z / segWl, ws ^ 0x7E1L)
                             + 0.38 * LandNoise.value(x / (segWl * 0.5) + 2.9, z / (segWl * 0.5) - 1.3, ws ^ 0x7E2L);
                    seg = smoothstep(ARC_SEG_LO, ARC_SEG_HI, m);
                }
                double rd = bf[0] / LandContract.S_RIFT;
                h -= LandContract.A_RIFT * LandContract.K_ISO * divW * seg * su * Math.exp(-rd * rd);
            }
        }
        if (fv < 0.0) {
            // Sea-floor age: PTP 2019 §4.3 uses the distance to the RIDGE, not a noise field.
            // alpha = 0 at the ridge (young), ->1 in the plate interior (old, abyssal).
            double age;
            if (SEA_AGE_FROM_RIDGE && USE_PAPER) {
                age = bf[9];
            } else {
                age = LandNoise.value(x / 8_000_000.0, z / 8_000_000.0, ws ^ 0xE3L);
            }
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

    private static final ThreadLocal<Cell[]> CAND = new ThreadLocal<Cell[]>() {
        @Override protected Cell[] initialValue() {
            Cell[] a = new Cell[18];
            for (int i = 0; i < 18; i++) a[i] = new Cell();
            return a;
        }
    };

    private static void cells9(double qx, double qz, Cell[] out) {
        if (PLATE_TWO_TIER_ON) { cells9TwoTier(qx, qz, out); return; }
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

    // -------- per-thread seed property cache（只缓存种子自己的量，几何完全不变） --------
    /**
     * 种子的速度与大陆度是 **(ws, 种子位置)** 的纯函数，与查询点无关；B′ 打开时种子集合
     * 逐点变化 ⟹ 每个点都要重算 9 份 vel（36 次噪声）+ 9 份 continentality（27 次噪声）。
     * 这里按 seed id 做一个小型开放寻址缓存，命中即零噪声。
     */
    private static final int SPC_SIZE = 4096;
    private static final class SeedProps {
        // ⚠ key = 种子的【位置】位模式，不是 cell id：id 由 i*C1 ^ j*C2 再 mix 得来，
        // 不同的 (i,j) 可以撞同一个 id（XOR 不单射），用它做缓存键会把两粒种子的速度/大陆度串掉。
        final long[] key = new long[SPC_SIZE];
        final double[] vx = new double[SPC_SIZE];
        final double[] vz = new double[SPC_SIZE];
        final double[] c = new double[SPC_SIZE];
        long ws = Long.MIN_VALUE;
    }
    private static final ThreadLocal<SeedProps> SPC = new ThreadLocal<SeedProps>() {
        @Override protected SeedProps initialValue() {
            SeedProps s = new SeedProps();
            java.util.Arrays.fill(s.key, Long.MIN_VALUE);
            return s;
        }
    };
    private static final ThreadLocal<double[]> VELTMP = new ThreadLocal<double[]>() {
        @Override protected double[] initialValue() { return new double[2]; }
    };

    /** 距离粗 Voronoi 边界多远就完全看不到细层种子：8×SOFTMIN_L ⟹ 权重 e^-8 ≈ 3e-4。 */
    private static final double TT_FAR = 8.0 * SOFTMIN_L;

    /** 只用粗层 9 个种子（带外路径；可整格缓存）。 */
    private static void cells9Coarse(double qx, double qz, Cell[] out) {
        final double L1 = LandContract.TT_L1;
        long ci = (long) Math.floor(qx / L1), cj = (long) Math.floor(qz / L1);
        int m = 0;
        for (int dj = -1; dj <= 1; dj++) {
            for (int di = -1; di <= 1; di++) {
                long i = ci + di, j = cj + dj;
                double sx = (i + 0.5 + LandContract.PLATE_JIT * (LandNoise.h01(0x311L, i, j) - 0.5)) * L1;
                double sz = (j + 0.5 + LandContract.PLATE_JIT * (LandNoise.h01(0x322L, i, j) - 0.5)) * L1;
                double dx = qx - sx, dz = qz - sz;
                out[m].sx = sx; out[m].sz = sz;
                out[m].d = Math.sqrt(dx * dx + dz * dz);
                out[m].id = LandNoise.mix(0x5A5AL ^ (i * 0x2545F4914F6CDD1DL) ^ (j * 0x94D049BB133111EBL));
                m++;
            }
        }
    }

    /** 查询点到【粗 Voronoi 边界】的距离（只用 9 个粗种子）。 */
    private static double coarseEdgeDist(double qx, double qz) {
        final double L1 = LandContract.TT_L1;
        long ci = (long) Math.floor(qx / L1), cj = (long) Math.floor(qz / L1);
        double[] sx = new double[9];
        double[] sz = new double[9];
        int k = 0;
        for (int dj = -1; dj <= 1; dj++) {
            for (int di = -1; di <= 1; di++) {
                long i = ci + di, j = cj + dj;
                sx[k] = (i + 0.5 + LandContract.PLATE_JIT * (LandNoise.h01(0x311L, i, j) - 0.5)) * L1;
                sz[k] = (j + 0.5 + LandContract.PLATE_JIT * (LandNoise.h01(0x322L, i, j) - 0.5)) * L1;
                k++;
            }
        }
        double r1 = 1e18, r2 = 1e18;
        int k1 = 0, k2 = 0;
        for (int m = 0; m < 9; m++) {
            double dx = qx - sx[m], dz = qz - sz[m];
            double dd = dx * dx + dz * dz;
            if (dd < r1) { r2 = r1; k2 = k1; r1 = dd; k1 = m; }
            else if (dd < r2) { r2 = dd; k2 = m; }
        }
        double ab = Math.sqrt((sx[k1] - sx[k2]) * (sx[k1] - sx[k2]) + (sz[k1] - sz[k2]) * (sz[k1] - sz[k2]));
        return (r2 - r1) / (2.0 * Math.max(1.0, ab));
    }

    /**
     * 两层种子（B′）：粗层 9 个巨型种子 + 细层最多 9 个候选（只在粗边界带内出生），
     * 取**最近 9 个**交回给调用方。之后完全是**无权 Voronoi**——每块板含自己的种子、
     * 边界是两个最近种子的中垂线、不嵌套、无空单元。
     *
     * <p>细种子是否出生只取决于**它自己到粗边界的距离**（与查询点无关）⟹ 全局一致、确定。
     */
    private static void cells9TwoTier(double qx, double qz, Cell[] out) {
        final double L1 = LandContract.TT_L1;
        final double L2 = LandContract.TT_L2;
        final double BAND = LandContract.TT_BAND;
        Cell[] cand = CAND.get();
        int n = 0;
        long ci = (long) Math.floor(qx / L1), cj = (long) Math.floor(qz / L1);
        double[] csx = new double[9];
        double[] csz = new double[9];
        for (int dj = -1; dj <= 1; dj++) {
            for (int di = -1; di <= 1; di++) {
                long i = ci + di, j = cj + dj;
                double sx = (i + 0.5 + LandContract.PLATE_JIT * (LandNoise.h01(0x311L, i, j) - 0.5)) * L1;
                double sz = (j + 0.5 + LandContract.PLATE_JIT * (LandNoise.h01(0x322L, i, j) - 0.5)) * L1;
                csx[(dj + 1) * 3 + (di + 1)] = sx;
                csz[(dj + 1) * 3 + (di + 1)] = sz;
                cand[n].sx = sx; cand[n].sz = sz;
                double dx = qx - sx, dz = qz - sz;
                cand[n].d = Math.sqrt(dx * dx + dz * dz);
                cand[n].id = LandNoise.mix(0x5A5AL ^ (i * 0x2545F4914F6CDD1DL) ^ (j * 0x94D049BB133111EBL));
                n++;
            }
        }
        long fi = (long) Math.floor(qx / L2), fj = (long) Math.floor(qz / L2);
        for (int dj = -1; dj <= 1; dj++) {
            for (int di = -1; di <= 1; di++) {
                long i = fi + di, j = fj + dj;
                double sx = (i + 0.5 + LandContract.PLATE_JIT * (LandNoise.h01(0x411L, i, j) - 0.5)) * L2;
                double sz = (j + 0.5 + LandContract.PLATE_JIT * (LandNoise.h01(0x422L, i, j) - 0.5)) * L2;
                double r1 = 1e18, r2 = 1e18;
                int k1 = 0, k2 = 0;
                for (int k = 0; k < 9; k++) {
                    double ddx = sx - csx[k], ddz = sz - csz[k];
                    double dd = ddx * ddx + ddz * ddz;
                    if (dd < r1) { r2 = r1; k2 = k1; r1 = dd; k1 = k; }
                    else if (dd < r2) { r2 = dd; k2 = k; }
                }
                double ab = Math.sqrt((csx[k1] - csx[k2]) * (csx[k1] - csx[k2])
                                    + (csz[k1] - csz[k2]) * (csz[k1] - csz[k2]));
                if ((r2 - r1) / (2.0 * Math.max(1.0, ab)) < BAND) {
                    cand[n].sx = sx; cand[n].sz = sz;
                    double dx = qx - sx, dz = qz - sz;
                    cand[n].d = Math.sqrt(dx * dx + dz * dz);
                    cand[n].id = LandNoise.mix(0x5B5BL ^ (i * 0x2545F4914F6CDD1DL) ^ (j * 0x94D049BB133111EBL));
                    n++;
                }
            }
        }
        for (int k = 0; k < 9; k++) {
            int best = k;
            for (int m = k + 1; m < n; m++) if (cand[m].d < cand[best].d) best = m;
            if (best != k) { Cell t = cand[k]; cand[k] = cand[best]; cand[best] = t; }
        }
        for (int k = 0; k < 9; k++) {
            out[k].sx = cand[k].sx; out[k].sz = cand[k].sz;
            out[k].d = cand[k].d; out[k].id = cand[k].id;
        }
    }

    /** 最近板块 id（探针/诊断入口；两张种子表都支持）。 */
    public static long plateIdAt(long ws, double x, double z) {
        Cell[] cs = SC.get();
        cells9(x + warpX(x, z), z + warpZ(x, z), cs);
        int k = 0;
        for (int j = 1; j < 9; j++) if (cs[j].d < cs[k].d) k = j;
        return cs[k].id;
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
