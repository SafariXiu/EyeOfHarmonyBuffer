package com.EyeOfHarmonyBuffer.sim2.land;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 区域强度场（地形标签的连续版本）：世界对齐粗格 + 双线性插值 + 缓存。
 *
 * <p>设计见 工作目录/短期/区域连续性-平滑过渡方案.md §8：
 * 权重在**世界对齐**的 LATTICE 格点上算（与区块无关），采样点只做插值 ⟹
 * 相邻区块共享同一条边界、用同一批节点 ⟹ 不可能出现"这个区块开始了、隔壁晚几个 chunk"。
 *
 * <p>本类只做**基础设施 + 验证**，暂不接任何侵蚀/细节逻辑。
 */
public final class RegionField {
    /** 粗格边长（m）。1 km 与世界尺度（40,000 km）相比是 1/40,000。 */
    public static double LATTICE = 1_000_000.0;
    /** 山脉带宽度（m）：D 小于它才可能是山脉。 */
    public static double MOUNTAIN_RANGE = 1_500_000.0;
    /** 海沟带宽度（m）。 */
    public static double TRENCH_RANGE = 800_000.0;
    /** 洋中脊半宽（m）。 */
    public static double RIDGE_RANGE = 600_000.0;
    /** 裂谷半宽（m）。 */
    public static double RIFT_RANGE = 600_000.0;
    /** 权重过渡的坡度（汇聚强度）。 */
    public static double CONV_LO = 0.0, CONV_HI = 0.20;

    private RegionField() {}

    private static final ConcurrentHashMap<Long, float[]> NODES = new ConcurrentHashMap<>();
    private static volatile long nodeWs = Long.MIN_VALUE;

    /** 节点值：{mountain, trench, ridge, arc, rift}。 */
    private static float[] node(long ws, long i, long j) {
        Long key = (((long) i) << 32) ^ (j & 0xFFFFFFFFL);
        float[] v = NODES.get(key);
        if (v != null) return v;
        double x = i * LATTICE, z = j * LATTICE;
        double[] bf = BF.get();
        LandV3.boundaryField(ws, x, z, bf);
        double c = LandV3.continentality(ws, x, z);
        double cut = LandV3.cut(ws);
        double ctype = LandV3.smoothstepPublic(cut - 0.06, cut + 0.06, c);
        double D = bf[0];
        double conv = bf[4];
        double sConv = LandV3.smoothstepPublic(CONV_LO, CONV_HI, conv);
        double sDiv = LandV3.smoothstepPublic(-CONV_LO, -CONV_HI, conv);
        // 山带允许“没入陆架”：用更宽的 ctype（向洋侧延伸约一个陆架宽度）
        double ctypeM = LandV3.smoothstepPublic(cut - 0.10, cut + 0.03, c);
        // 带用【低阈值汇聚门】保证沿走向连续；幅度单独用汇聚强度（第 9 项）
        double bandGate = LandV3.smoothstepPublic(0.02, 0.10, conv);
        double sM = bandGate * ctypeM * (1.0 - LandV3.smoothstepPublic(0.0, MOUNTAIN_RANGE, D));
        double mtnAmp = Math.min(1.0, Math.max(0.0, conv) / 0.6);
        double sT = sConv * (1.0 - ctype) * (1.0 - LandV3.smoothstepPublic(0.0, TRENCH_RANGE, D));
        double sR = sDiv * (1.0 - ctype) * (1.0 - LandV3.smoothstepPublic(0.0, RIDGE_RANGE, D));
        // 岛弧：汇聚 + 洋壳 + 400–900 km 的带
        double arcBand = LandV3.smoothstepPublic(400_000.0, 500_000.0, D)
                       * (1.0 - LandV3.smoothstepPublic(800_000.0, 900_000.0, D));
        double sA = sConv * (1.0 - ctype) * arcBand;
        // 裂谷：离散 + 陆壳（等 RIFT_ON 打开后才有实际地形）
        double sF = sDiv * ctype * (1.0 - LandV3.smoothstepPublic(0.0, RIFT_RANGE, D));
        // 第 6 项：到边界的距离（km）；第 7/8 项：边界法向的【双角表示】(cos2θ, sin2θ)
        // 双角表示对 ±n 不敏感，且插值天然连续 —— 山带方向用它。
        double nvx = bf[5], nvz = bf[6];
        double nn = Math.sqrt(nvx * nvx + nvz * nvz);
        if (nn < 1e-9) { nvx = 1.0; nvz = 0.0; nn = 1.0; }
        double c2 = (nvx * nvx - nvz * nvz) / (nn * nn);
        double s2 = 2.0 * nvx * nvz / (nn * nn);
        v = new float[]{(float) sM, (float) sT, (float) sR, (float) sA, (float) sF,
                        (float) (D / 1e6), (float) c2, (float) s2, (float) mtnAmp};
        if (nodeWs != ws) { NODES.clear(); nodeWs = ws; }
        NODES.put(key, v);
        return v;
    }

    private static final ThreadLocal<double[]> BF = ThreadLocal.withInitial(() -> new double[16]);

    /** 采样点权重：双线性（C0）；sampledChunkIndependent 验证用。 */
    public static void weights(long ws, double x, double z, double[] out) {
        if (nodeWs != ws) { NODES.clear(); nodeWs = ws; }
        double fx = x / LATTICE, fz = z / LATTICE;
        long i = (long) Math.floor(fx), j = (long) Math.floor(fz);
        double u = fx - i, v = fz - j;
        // smoothstep 加权 ⟹ C1（消除 1 km 格上的折线）
        double su = u * u * (3 - 2 * u), sv = v * v * (3 - 2 * v);
        float[] a = node(ws, i, j), b = node(ws, i + 1, j);
        float[] c = node(ws, i, j + 1), d = node(ws, i + 1, j + 1);
        for (int k = 0; k < 9; k++) {
            double top = a[k] + (b[k] - a[k]) * su;
            double bot = c[k] + (d[k] - c[k]) * su;
            out[k] = top + (bot - top) * sv;
        }
    }

    /** 缓存节点数（监控用）。 */
    public static int cachedNodes() { return NODES.size(); }
}
