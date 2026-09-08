package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer;

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
    private static final double LOW_WAV = 80_000.0;
    /** 低频 fbm 层数（λ, λ/2, λ/4）。 */
    private static final int LOW_OCTAVES = 3;
    /** 中频棱角振幅（叠加在低频之上）。 */
    private static final double MED_AMP = 0.10;
    /** 鞍部抬升窗口宽度（高度单位）：等值线两侧 ±LIFT_WINDOW/2 内高度 ×2 拉伸。 */
    private static final double LIFT_WINDOW = 0.06;
    /** 海岸距离梯度估计步长（block）。需明显小于低频波长且远大于高频毛刺。 */
    private static final double GRAD_STEP = 6000.0;
    /** 海岸距离输出上限（block）。 */
    private static final double DIST_CAP = 200_000.0;

    /** 阈值标定采样步长 / 每轴点数（400k/4k=100，200k/4k=50 → 5000 点）。 */
    private static final int CALIBRATE_STRIDE = 4000;

    /**
     * 每种子标定结果缓存（自适应阈值 + 陆地残差标尺，一次扫描算齐；见类注释）。
     */
    private static final ConcurrentHashMap<Integer, LandStats> STATS_CACHE =
        new ConcurrentHashMap<Integer, LandStats>();

    /** 每种子标定结果。threshold = q67(h)+LIFT/2；landQ93 = 陆地上残差 r=h'-T 的 q93（地貌标尺）。 */
    private static final class LandStats {
        final double threshold;
        final double landQ93;

        LandStats(double threshold, double landQ93) {
            this.threshold = threshold;
            this.landQ93 = landQ93;
        }
    }

    private NoiseContinentGrid() {}

    // ======== 高程场（纯函数，全局单一场） ========

    private static double hashUnit(long seed, int gx, int gz) {
        long h = TectonicMath.hashLongs(seed, (gx & 0xFFFFFFFFL), (gz & 0xFFFFFFFFL));
        long m = (h & 0xFFFFFFFFFFFFFFFFL) >>> (64 - 23);
        return m / (double) (1L << 23);
    }

    private static double valueNoise2D(double x, double z, long seed) {
        int xi = (int) Math.floor(x), zi = (int) Math.floor(z);
        double xf = x - xi, zf = z - zi;
        double u = xf * xf * (3.0 - 2.0 * xf);
        double v = zf * zf * (3.0 - 2.0 * zf);
        double a = hashUnit(seed, xi, zi), b = hashUnit(seed, xi + 1, zi);
        double c = hashUnit(seed, xi, zi + 1), d = hashUnit(seed, xi + 1, zi + 1);
        double ab = a + (b - a) * u, cd = c + (d - c) * u;
        return ab + (cd - ab) * v;
    }

    /** 标准 fbm（归一化到 [0,1]）。 */
    private static double fbm(double x, double z, long seed, int octaves, double lacunarity, double gain, double baseFreq) {
        double sum = 0.0, amp = 1.0, freq = baseFreq, total = 0.0;
        for (int i = 0; i < octaves; i++) {
            sum += amp * valueNoise2D(x * freq, z * freq, seed + i);
            total += amp;
            amp *= gain;
            freq *= lacunarity;
        }
        return sum / total;
    }

    /** 低频域扭曲（大陆轮廓弯曲，消除网格 / 平铺感）。 */
    private static double[] warp(double x, double z, long seed) {
        double freq = 1.0 / 1_000_000.0, amp = 50_000.0;
        double ox = amp * valueNoise2D(x * freq, z * freq, seed + 1000);
        double oz = amp * valueNoise2D(x * freq, z * freq, seed + 2000);
        return new double[] { x + ox, z + oz };
    }

    /** 原始高程场 h（约 [0, 1.10]，均值 ≈0.5）。 */
    public static double height(int x, int z, int worldSeedInt) {
        double[] w = warp(x, z, worldSeedInt);
        double low = fbm(w[0], w[1], worldSeedInt, LOW_OCTAVES, 2.0, 0.5, 1.0 / LOW_WAV);
        double med = fbm(w[0], w[1], worldSeedInt + 500, 2, 2.0, 0.5, 4.0 / LOW_WAV);
        return low + med * MED_AMP;
    }

    /**
     * 中频带通场（λ=20k/10k，未乘 MED_AMP，值域约 [0,1]）。
     * 供 OrographyField 做带状山链检测（ridged 零交叉脊线网络）。
     */
    public static double medNoise(int x, int z, int worldSeedInt) {
        double[] w = warp(x, z, worldSeedInt);
        return fbm(w[0], w[1], worldSeedInt + 500, 2, 2.0, 0.5, 4.0 / LOW_WAV);
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
    private static LandStats statsFor(int worldSeedInt) {
        LandStats s = STATS_CACHE.get(worldSeedInt);
        if (s != null) {
            return s;
        }
        return STATS_CACHE.computeIfAbsent(worldSeedInt, NoiseContinentGrid::calibrateStats);
    }

    private static LandStats calibrateStats(int worldSeedInt) {
        int nx = 400_000 / CALIBRATE_STRIDE;
        int nz = 200_000 / CALIBRATE_STRIDE;
        double[] hs = new double[nx * nz];
        int k = 0;
        for (int z = 0; z < 200_000; z += CALIBRATE_STRIDE) {
            for (int x = 0; x < 400_000; x += CALIBRATE_STRIDE) {
                hs[k++] = height(x, z, worldSeedInt);
            }
        }
        double[] sorted = hs.clone();
        Arrays.sort(sorted);
        double q67 = sorted[Math.min(sorted.length - 1, (int) (0.67 * sorted.length))];
        double threshold = q67 + LIFT_WINDOW / 2.0;   // +0.03（抬升把等效阈值降到 T - 0.03）

        // 第二遍：收集陆上残差（h' - T >= 0）的 q93，作为地貌层的内陆标尺
        double[] rs = new double[nx * nz];
        int m = 0;
        for (int z = 0; z < 200_000; z += CALIBRATE_STRIDE) {
            for (int x = 0; x < 400_000; x += CALIBRATE_STRIDE) {
                double r = lifted(hs[((z / CALIBRATE_STRIDE) * nx) + (x / CALIBRATE_STRIDE)], threshold) - threshold;
                if (r >= 0.0) {
                    rs[m++] = r;
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
        return new LandStats(threshold, landQ93);
    }

    /**
     * 陆地残差（抬升后相对阈值的超出量，&gt;=0 为陆）：越深内陆越大。
     * 供 OrographyField 等"大陆内部结构"层做海拔/山脊推导。
     */
    public static double landResidual(int x, int z, int worldSeedInt) {
        double t = statsFor(worldSeedInt).threshold;
        return residual(height(x, z, worldSeedInt), t);
    }

    /**
     * 该种子陆上残差的 q93 标尺（&gt;0），地貌层用它归一化海拔；见 LandStats。
     */
    public static double residualScale(int worldSeedInt) {
        return statsFor(worldSeedInt).landQ93;
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

    /** 是否陆地（残差 &gt;= 0）。 */
    public static boolean isLand(int x, int z, int worldSeedInt) {
        return landResidual(x, z, worldSeedInt) >= 0.0;
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
        double dist;
        if (grad < 1.0e-12) {
            dist = r < 0.0 ? DIST_CAP : -DIST_CAP;   // 平坦远场：按侧别给饱和值
        } else {
            dist = -r / grad;
            if (dist > DIST_CAP) dist = DIST_CAP;
            if (dist < -DIST_CAP) dist = -DIST_CAP;
        }
        return dist;
    }
}
