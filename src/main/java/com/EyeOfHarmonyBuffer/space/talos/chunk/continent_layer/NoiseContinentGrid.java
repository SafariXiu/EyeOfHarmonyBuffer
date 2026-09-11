package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer;

import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.ClimateLatitudes;
import com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer.PeriodicNoise;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 噪声大陆高度场（V2 海陆分布核心 · X1 阶段1）。
 *
 * 单一分形高度场（fbm：λ=80k 主波 ×3 层）+ 低频域扭曲 + 中频棱角（λ/4 ×2 层，振幅 0.10），
 * 等值线切割出大陆；鞍部抬升把等值线附近（LIFT_WINDOW 内）的高度 ×2 拉伸，
 * 把紧邻的小块合并成片（不吞孤立小岛）。
 *
 * **自适应阈值**：陆地占比按种子标定——首次使用时在 400k×200k 主域按 4k 步长采样 5000 点，
 * 取 q67 分位数 + LIFT 偏移作阈值并缓存。原因：固定阈值对低频噪声的种子间均值漂移极敏感
 * （实测 0.607 下陆地占比在 22%~54% 间摆动），自适应后各种子稳定 ≈33%（D10 目标 30~35%）。
 * 注意：大陆**块数与均衡度仍随种子变化**（实测 3~7 块，部分种子出现一块 80%+ 的泛大陆）；
 * 如需强制 4~6 个均衡大洲，需上"布点格+多极点大陆"方案（design.md 四节 / X1 后续），本场维持纯噪声。
 *
 * 纯函数（同一 worldSeedInt 结果一致）、O(1)、无连通域 / 洪水填充，提供：
 *   - {@link #isLand(int,int,int)}          : 是否陆地
 *   - {@link #height(int,int,int)}          : 原始高程场（供河流 WatershedBuilder / 山带 / 地形 / 出图）
 *   - {@link #coastDistBlocks(int,int,int)} : 有符号海岸距离（block：&lt;0 内陆、0 岸线、&gt;0 海上）
 *
 * 说明：本场不提供 superId——旧 superId 只服务于 RVR2 河流模板，新水系将改为 WatershedBuilder
 * （直接消费 height）。生产世界生成尚未接入本场（TalosLandMask/WorldgenAPI 仍走旧 TectonicWorld），
 * 接入 = X1 阶段2（design.md）；当前只有气团/洋流原型与 /talosmap / GlobalClimate 以本场为 L1 来源。
 */
public final class NoiseContinentGrid {

    // --- 高度场参数（形态搜索标定：λ=80k/3层 + 中频0.10 → 3~7 块大陆、海 100% 连通性最佳组合） ---
    /** 低频主波长（block）。 */
    private static final double LOW_WAV = 20_000.0;
    /**
     * 大陆尺度倍率（2026-09 加入）。
     *
     * 实测（P135）：沿东西方向的连续海段中位数只有 **690 km**，而真实地球大西洋约 6,000 km、
     * 太平洋约 15,000 km。海盆太小 → 风生环流没有空间成形，西边界流占盆宽比例过大，
     * 视觉上就是"横贯全图的水平带 + 陆地只是被挖掉的障碍物"。
     * 提高本倍率会把大陆和海洋一起放大（X_STRETCH=5 保持不变，仍沿 X 细长）。
     */
    public static double CONTINENT_SCALE = 2.5;
    /** 低频 fbm 层数（λ, λ/2, λ/4）。 */
    private static final int LOW_OCTAVES = 3;
    /** 中频棱角振幅（叠加在低频之上）。 */
    private static final double MED_AMP = 0.10;
    /** 中频二次扭曲盐（medNoise 专用，异于 warp 的 1000/2000 盐）。 */
    private static final long MED_WARP_SALT = 0x3B9ACA07L;
    /** 鞍部抬升窗口宽度（高度单位）：等值线两侧 ±LIFT_WINDOW/2 内高度 ×2 拉伸。 */
    private static final double LIFT_WINDOW = 0.06;
    /** 海岸距离梯度估计步长（block）。需明显小于低频波长且远大于高频毛刺。 */
    private static final double GRAD_STEP = 1500.0;
    /** 海岸距离输出上限（block）。 */
    private static final double DIST_CAP = 50_000.0;

    // （这里曾有一个零引用的 CALIBRATE_STRIDE 常量 + 一句与现值不符的点数注释，已删除：
    //   真正在跑的标定在 calibrateStats()，用 nx=409 / nz=101、步长 49_000 / 40_001。）

    /**
     * 每种子标定结果缓存（自适应阈值 + 陆地残差标尺，一次扫描算齐；见类注释）。
     */
    private static final ConcurrentHashMap<Integer, LandStats> STATS_CACHE =
        new ConcurrentHashMap<Integer, LandStats>();

    /** 每种子标定结果。threshold = q67(h)+LIFT/2；landQ93 = 陆上残差 r=h'-T 的 q93（地貌标尺）；
     *  seaQ93 = 海上 |r| 的 q93（海床深度标尺）。 */
    private static final class LandStats {
        final double threshold;
        final double landQ93;
        final double seaQ93;

        LandStats(double threshold, double landQ93, double seaQ93) {
            this.threshold = threshold;
            this.landQ93 = landQ93;
            this.seaQ93 = seaQ93;
        }
    }

    private NoiseContinentGrid() {}

    // ======== 高程场（纯函数，全局单一场） ========

    /**
     * 标准 fbm（归一化到 [0,1]）—— **两轴都不折叠**（走 PeriodicNoise.value2XZ，
     * 而 PeriodicNoise.INFINITE_X / INFINITE_Z 默认都是 true）→ 大陆/海洋不受任何周期约束。
     *
     * 历史：这里曾是"环面周期版"（f(x+400k,z) ≡ f(x,z)、格数取模）。那段描述已失效，
     * 现行行为见本节末的 warp 注释与 PeriodicNoise 的 INFINITE_* 开关。
     */
    private static double fbm(double x, double z, long seed, int octaves, double lacunarity, double gain, double baseFreq) {
        double sum = 0.0, amp = 1.0, total = 0.0;
        int lac = Math.max(2, (int) Math.round(lacunarity));
        // 两轴都不折叠（无限平面）：大陆/海洋不再受任何周期约束。
        // X 方向仍按 X_STRETCH 拉长 → 大陆呈"沿 X 延展的条带"。
        double wavZ = 1.0 / baseFreq;
        double wavX = X_STRETCH * wavZ;
        for (int i = 0; i < octaves; i++) {
            sum += amp * PeriodicNoise.value2XZ(seed + i, x, z, wavX, wavZ);
            total += amp;
            amp *= gain;
            wavX /= lac;
            wavZ /= lac;
        }
        return sum / total;
    }

    /** X/Z 波长拉伸比（1 = 各向同性；越大 → 大陆沿 X 越细长）。 */
    public static double X_STRETCH = 5.0;



    // ======== 低频域扭曲（大陆轮廓弯曲，消除网格 / 平铺感） ========
    //
    // C1 世界 = **无限平面**：X 无限、Z 也不折叠（Z_CYCLE 只管气候）→ 域扭曲在两个方向上都不折叠。
    // （历史：曾按"圆柱"处理，Z 方向按 200k 折叠；那段与现值不符。）
    // 权衡量级：扭曲幅度 WARP_AMP = 1250 blocks；两个波长见下面的 WARP_WAV_X / WARP_WAV_Z。

    /** 扭曲幅度（blocks）。 */
    private static final double WARP_AMP = 1_250.0;
    /** 扭曲波长（blocks），沿 X 拉长（与 X_STRETCH 同一取向）。
     *  实际生效的 lattice 格数 = round(PERIOD_轴 / 波长)，而 INFINITE_X/Z=true 使其取负号 ⇒ **不折叠**：
     *  X: round(100_000/125_000) = 1 → -1；Z: round(1_000_000/25_000) = 40 → -40（有效 λz = 25k）。 */
    private static double WARP_WAV_X = 125_000.0;   // 沿 X 拉长（与 X_STRETCH 同一取向）
    private static double WARP_WAV_Z = 25_000.0;

    private static double warpOffset(long seed, double x, double z) {
        return WARP_AMP * CONTINENT_SCALE * (PeriodicNoise.value2XZ(seed, x, z, WARP_WAV_X * CONTINENT_SCALE, WARP_WAV_Z * CONTINENT_SCALE) * 2.0 - 1.0);
    }

    private static double[] warp(double x, double z, long seed) {
        return new double[] {
            x + warpOffset(seed + 1000L, x, z),
            z + warpOffset(seed + 2000L, x, z)
        };
    }

    /** 原始高程场 h（约 [0, 1.10]，均值 ≈0.5）。 */
    public static double height(int x, int z, int worldSeedInt) {
        double[] w = warp(x, z, worldSeedInt);
        double wav = LOW_WAV * CONTINENT_SCALE;
        double low = fbm(w[0], w[1], worldSeedInt, LOW_OCTAVES, 2.0, 0.5, 1.0 / wav);
        double med = fbm(w[0], w[1], worldSeedInt + 500, 2, 2.0, 0.5, 4.0 / wav);
        return low + med * MED_AMP;
    }

    /**
     * 中频带通场（λ=20k/10k/5k/2.5k 四级 + 双重域扭曲，值域约 [0,1]）。
     * 供 OrographyField 做带状山链检测（ridged 零交叉脊线网络）。
     *
     * 2026-09 修订：原 2 级中频的 0.5 等值线在平滑场上必成闭合环（每片局部极值一
     * 个环），出图呈"迷宫项链"。加细层到 4 级 + 二次域扭曲后，脊线网络渗透化：
     * 大尺度成为蜿蜒山带，闭合环缩小到细尺度（λ/8）在地图上不可辨。分档仍按
     * 每种子 dev 分位自适应（D28），各类占比目标不变。
     */
    public static double medNoise(int x, int z, int worldSeedInt) {
        double[] w = warp(x, z, worldSeedInt);
        double[] w2 = warp(w[0], w[1], worldSeedInt + MED_WARP_SALT);   // 二次扭曲（异盐）
        return fbm(w2[0], w2[1], worldSeedInt + 500, 4, 2.0, 0.5, 4.0 / (LOW_WAV * CONTINENT_SCALE));
    }

    /**
     * 通用带通场（与海陆同域扭曲，seed+salt 去相关；值域约 [0,1]）。
     * 供 OrographyField 做大尺度山系包络等结构层采样。
     */
    public static double bandNoise(int x, int z, int worldSeedInt, long salt, double baseFreq, int octaves) {
        double[] w = warp(x, z, worldSeedInt);
        return fbm(w[0], w[1], worldSeedInt + salt, octaves, 2.0, 0.5, baseFreq);
    }

    // ======== 自适应阈值（按种子标定陆地占比） ========

    /**
     * 取某世界种子的标定结果（缓存，见 LandStats）。
     * isLand ⇔ h >= T - LIFT_WINDOW/2（抬升后 ≥ T），故取 T = q67(h) + LIFT_WINDOW/2
     * 可使陆地占比 ≈33%。标定在主域 4k 网格上采样两次（≈10000 次 height，毫秒级），
     * 多线程首次访问由 computeIfAbsent 保证只算一次。
     */
    public static void clearStats() {
        STATS_CACHE.clear();
    }

    private static LandStats statsFor(int worldSeedInt) {
        LandStats s = STATS_CACHE.get(worldSeedInt);
        if (s != null) {
            return s;
        }
        return STATS_CACHE.computeIfAbsent(worldSeedInt, NoiseContinentGrid::calibrateStats);
    }

    /** 标定窗口数（沿 X 铺开；世界沿 X 无限 → 单窗口采样会让远处陆地占比严重漂移）。 */
    private static final int CALIBRATE_WINDOWS = 1;   // 见 calibrateStats：改用单张大网格采样

    /** 标定目标：陆地占比（阈值二分对准这个数）。 */
    public static double TARGET_LAND = 0.33;

    private static LandStats calibrateStats(int worldSeedInt) {
        // 采样：**大范围、非整除步长**的单张网格（而不是 9 个小窗口）。
        //
        // 旧写法是 9 个 400k×200k 的窗口。A 项把大陆波长放大到 λx=1M 后，一个 400k 宽的窗口
        // 只覆盖 0.4 个最长波长 → 窗口内方差远小于全局方差，池化后仍复现不出全局分布，
        // 于是阈值在局部样本上对准了 33%、全局只有 24%（P138 实测，沿 X 各分箱均匀）。
        // 现在跨 ±10M（约 20 个 λx）× ±2M，步长取 49k / 40_001（与噪声格点非整除，避免锁相）。
        // CALIBRATE_WINDOWS 置 1 后，第二遍的索引 hs[w*per + j*nx + i] 自动退化成 hs[j*nx+i]，
        // 正好等于本网格的排布，第二遍无需改动。
        final int nx = 409;
        final int nz = 101;
        final int per = nx * nz;
        final int sampleN = per * CALIBRATE_WINDOWS;
        double[] hs = new double[sampleN];
        int k = 0;
        for (int j = 0; j < nz; j++) {
            int z = -2_000_000 + j * 40_001;
            for (int i = 0; i < nx; i++) {
                hs[k++] = height(-10_000_000 + i * 49_000, z, worldSeedInt);
            }
        }
        double[] sorted = hs.clone();
        Arrays.sort(sorted);
        double q67 = sorted[Math.min(sorted.length - 1, (int) (0.67 * sorted.length))];

        // 阈值：对**真正的陆地判据**做二分，直接对准目标陆地占比。
        //
        // 陆地判据（本文件 line 322）：residual(h,t) = lifted(h,t) − t ≥ 0
        //                              ⟺ h ≥ t − LIFT_WINDOW/2
        // 旧写法 t = q67(h) + LIFT_WINDOW/2 在数学上等价于"使该判据成立的比例 = 33%"，
        // **公式本身没错**；错的是 q67 的采样——9 个 400k×200k 小窗口在 λx 放大到 1M 后
        // 只覆盖 0.4 个最长波长，q67 系统性偏高 → 陆地掉到 18.3%（P138 实测，沿 X 均匀）。
        // 现在采样换成单张大网格（见上），这里再二分一遍以去掉"目标分位 vs 目标占比"的
        // 分布假设，两者都对之后陆地占比落在 33%。
        double lo = sorted[0], hi = sorted[sorted.length - 1];
        for (int it = 0; it < 30; it++) {
            double mid = 0.5 * (lo + hi);
            int cnt = 0;
            for (int q = 0; q < sampleN; q++) {
                // 必须用 lifted 判据：residual(h,t) = lifted(h,t) − t ≥ 0 ⟺ h ≥ t − LIFT_WINDOW/2。
                // 直接用 h ≥ t 会把阈值定低 ~LIFT/2 → 陆地偏多（实测 39.7% vs 目标 33%）。
                if (lifted(hs[q], mid) - mid >= 0.0) {
                    cnt++;
                }
            }
            if (cnt / (double) sampleN > TARGET_LAND) {
                lo = mid;      // 陆地太多 → 抬高阈值
            } else {
                hi = mid;
            }
        }
        double threshold = 0.5 * (lo + hi);

        // 第二遍：收集陆上残差（h' - T >= 0）q93（内陆标尺）与海上 |r| q93（海床标尺）
        double[] rs = new double[sampleN];
        double[] ss = new double[sampleN];
        int m = 0;
        int ns = 0;
        for (int w = 0; w < CALIBRATE_WINDOWS; w++) {
            for (int j = 0; j < nz; j++) {
                for (int i = 0; i < nx; i++) {
                    double r = lifted(hs[w * per + j * nx + i], threshold) - threshold;
                    if (r >= 0.0) {
                        rs[m++] = r;
                    } else {
                        ss[ns++] = -r;
                    }
                }
            }
        }
        double landQ93 = 1.0;
        if (m > 0) {
            Arrays.sort(rs, 0, m);
            landQ93 = rs[Math.min(m - 1, (int) (0.93 * m))];
            if (landQ93 < 1.0e-6) {
                landQ93 = 1.0e-6;
            }
        }
        double seaQ93 = 1.0;
        if (ns > 0) {
            Arrays.sort(ss, 0, ns);
            seaQ93 = ss[Math.min(ns - 1, (int) (0.93 * ns))];
            if (seaQ93 < 1.0e-6) {
                seaQ93 = 1.0e-6;
            }
        }
        return new LandStats(threshold, landQ93, seaQ93);
    }

    /**
     * 陆地残差（抬升后相对阈值的超出量，&gt;=0 为陆）：越深内陆越大。
     * 供 OrographyField 等"大陆内部结构"层做海拔/山脊推导。
     */
    /** 极地冰盖起始纬度带（bandD：0=赤道 1=极地）。0.82 ≈ 74°。 */
    public static double ICE_BAND = 0.82;
    /** 冰盖强制成陆的强度（残差量级约 0.3，1.5 足够压过任何噪声起伏）。 */
    public static double ICE_FORCE = 1.5;

    public static double landResidual(int x, int z, int worldSeedInt) {
        double t = statsFor(worldSeedInt).threshold;
        double r = residual(height(x, z, worldSeedInt), t);
        // 极地永久冰盖：bandD 超过 ICE_BAND 后把残差整体抬高 → 变成陆地。
        // **它不是装饰**：冰盖是"陆"，把海盆朝极地那一端封住 —— 这与地球的
        // 南极洲 / 北冰洋封住大西洋是同一个机制，是风生环流圈能形成的前提。
        // 而且因为纬度在极点饱和，z→±∞ 都是冰盖，所以既不用墙也不会折返。
        double b = ClimateLatitudes.getDistanceToCenter(z) / (double) ClimateLatitudes.MAX_D;
        if (b > ICE_BAND) {
            double k = (b - ICE_BAND) / (1.0 - ICE_BAND);
            r += ICE_FORCE * k * k;
        }
        return r;
    }

    /**
     * 该种子陆上残差的 q93 标尺（&gt;0），地貌层用它归一化海拔；见 LandStats。
     */
    public static double residualScale(int worldSeedInt) {
        return statsFor(worldSeedInt).landQ93;
    }

    /**
     * 该种子海上 |残差| 的 q93 标尺（&gt;0），海床深度层用它归一化水深；见 LandStats。
     */
    public static double seaResidualScale(int worldSeedInt) {
        return statsFor(worldSeedInt).seaQ93;
    }

    // ======== 鞍部抬升 + 海陆判定 ========

    /**
     * 鞍部抬升：等值线两侧 LIFT_WINDOW 宽的带内做 ×2 拉伸（h' = 2h + LIFT - 2T，连续、斜率翻倍），
     * 把只差一点点的近邻小块"吸"进同一片大陆，而不吞真正的孤岛。
     */
    private static double lifted(double h, double threshold) {
        if (h > threshold - LIFT_WINDOW) {
            return h + LIFT_WINDOW - (threshold - h);
        }
        return h;
    }

    /** 抬升后相对阈值的残差 r = h' - T：&gt;0 陆、&lt;0 海。 */
    private static double residual(double h, double threshold) {
        return lifted(h, threshold) - threshold;
    }

    /**
     * 是否陆地。**整个 V2 地形/气候层唯一的海陆判定**。
     *
     * 曾经这条判据被逐字抄在 6 处以上（{@code OrographyField}、{@code GlobalClimate}、
     * {@code ThermalForcing}、{@code RelaxedClimate}、{@code V2BiomeField}、{@code ClimateCoords}），
     * 每处都写 {@code landResidual(...) >= 0.0} —— 只要残差函数将来多一个项
     * （例如新增一种成陆机制），就有 6 个地方要同时改，漏一处就出现"同一列在 A 层是陆、
     * 在 B 层是海"的口径偏移。现在只有一个定义，第二处写法由 P220 的源码扫描器盯着。
     */
    public static boolean isLand(int x, int z, int worldSeedInt) {
        return isLandResidual(landResidual(x, z, worldSeedInt));
    }

    /**
     * 海陆判定（**已有残差值**时用，省一次噪声采样）。
     * 与 {@link #isLand} 共用同一个比较式，不允许在别处重写 {@code r >= 0.0}。
     */
    public static boolean isLandResidual(double residual) {
        return residual >= 0.0;
    }

    // ======== 有符号海岸距离（block 级） ========

    /**
     * 有符号海岸距离（block）：&lt;0 内陆、≈0 岸线、&gt;0 海上。
     *
     * 近似：把海岸线附近的高度场当局部斜坡，d ≈ -残差 / |∇残差|（梯度用 GRAD_STEP 中央差分，
     * 额外 4 次 height 采样；残差 &gt;0 为陆，故取负号使 d&lt;0 内陆、d&gt;0 海上）。
     * 与 isLand 由同一残差函数导出（符号互补），海岸带 / 洋流沿岸等需要"离岸多远"的
     * 消费方才能得到真实块距离；旧实现直接把残差当距离用（单位是噪声高度而非 block），
     * 会导致近岸判定在全图恒真，特此修正。
     *
     * 精度随 |d| 增大而下降，远场截断在 ±DIST_CAP。
     */
    public static double coastDistBlocks(int x, int z, int worldSeedInt) {
        double t = statsFor(worldSeedInt).threshold;
        int k = (int) GRAD_STEP;
        double h = height(x, z, worldSeedInt);
        double r = residual(h, t);
        double hxm = height(x - k, z, worldSeedInt);
        double hxp = height(x + k, z, worldSeedInt);
        double hzm = height(x, z - k, worldSeedInt);
        double hzp = height(x, z + k, worldSeedInt);
        double gx = (residual(hxp, t) - residual(hxm, t)) / (2.0 * k);
        double gz = (residual(hzp, t) - residual(hzm, t)) / (2.0 * k);
        double grad = Math.sqrt(gx * gx + gz * gz);
        // 平滑饱和：dist = -DIST_CAP·tanh(r / (grad·DIST_CAP))。
        // 原实现是 if (grad < 1e-12) dist = (r<0 ? +CAP : -CAP) 的**硬分支**，
        // 在平坦远场（grad≈0）上 dist 会在 ±CAP 之间跳 2·CAP，而 dist 下游连着
        // 岸墙折射与近岸流速阻尼——流场方向因此出现不可导的跳变。
        // tanh 形式在 grad 大时退化成 -r/grad（与旧式一致），grad→0 时连续饱和到 ∓CAP。
        return -DIST_CAP * Math.tanh(r / (grad * DIST_CAP + 1.0e-12));
    }
}
