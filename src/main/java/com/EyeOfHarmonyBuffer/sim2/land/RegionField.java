package com.EyeOfHarmonyBuffer.sim2.land;

import java.util.concurrent.ConcurrentHashMap;
import com.EyeOfHarmonyBuffer.sim2.land.LandNoise;

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
    public static double MOUNTAIN_RANGE = 340_000.0;   // 真实造山带 100–500 km 宽
    /** 海沟带宽度（m）。 */
    public static double TRENCH_RANGE = 250_000.0;
    /** 洋中脊半宽（m）。 */
    public static double RIDGE_RANGE = 320_000.0;
    /** 裂谷半宽（m）。 */
    public static double RIFT_RANGE = 150_000.0;
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
        // 分类距离：山/沟/弧用最近的【汇聚】边界，洋脊/裂谷用最近的【离散】边界
        double Dc = bf.length > 13 && bf[13] < 1e8 ? bf[13] : D;
        double Dd = bf.length > 14 && bf[14] < 1e8 ? bf[14] : D;
        double conv = bf[4];
        // 分类速率（主导对·按功能）：汇聚侧 / 离散侧各自的平均速率
        // 最近的汇聚/离散边界的【速率】（不随距离衰减；弧带在 400–900 km 外）
        double convC = bf.length > 17 ? bf[17] : Math.max(0.0, conv);
        double divC = bf.length > 18 ? bf[18] : Math.max(0.0, -conv);
        double sConv = LandV3.smoothstepPublic(CONV_LO, CONV_HI, convC);
        double sDiv = LandV3.smoothstepPublic(CONV_LO, CONV_HI, divC);
        // 距离带的局部化：速率本身只说明"这条边界在动"，带子的宽窄由 D 的 smoothstep 管
        double convEff = convC, divEff = divC;
        // 山带允许“没入陆架”：用更宽的 ctype（向洋侧延伸约一个陆架宽度）
        double ctypeM = LandV3.smoothstepPublic(cut - 0.10, cut + 0.03, c);
        // 带用【低阈值汇聚门】保证沿走向连续；幅度单独用汇聚强度（第 9 项）
        double bandGate = LandV3.smoothstepPublic(0.02, 0.10, convC);
        // 山带宽度沿走向变化（±35%）：避免"等宽长条"
        double rangeM = MOUNTAIN_RANGE * (1.0 + 0.65 * LandNoise.perlin(x / 2_400_000.0, z / 2_400_000.0, ws ^ 0x71L));
        // 沿走向的强度调制：山带不是连续等强的，而是"一段强、一段弱"（像真实造山带的段）
        double along = LandNoise.perlin(x / 900_000.0, z / 900_000.0, ws ^ 0x73L)
                     + 0.5 * LandNoise.perlin(x / 380_000.0 + 2.3, z / 380_000.0 - 1.1, ws ^ 0x74L);
        double seg = 0.92 + 0.08 * LandV3.smoothstepPublic(-0.55, 0.55, along);   // 只做轻微起伏，不断开
        double sM = bandGate * ctypeM * (1.0 - LandV3.smoothstepPublic(0.0, rangeM, Dc)) * seg;
        double mtnAmp = Math.min(1.0, Math.max(0.0, convC) / 0.6);
        double sT = sConv * (1.0 - ctype) * (1.0 - LandV3.smoothstepPublic(0.0, TRENCH_RANGE, Dc));
        double sR = sDiv * (1.0 - ctype) * (1.0 - LandV3.smoothstepPublic(0.0, RIDGE_RANGE, Dd));
        // 岛弧：汇聚 + 洋壳 + 400–900 km 的带
        // 岛弧：俯冲带外侧 200–600 km（真实弧后盆地 + 火山弧的位置）
        double arcBand = LandV3.smoothstepPublic(120_000.0, 220_000.0, Dc)
                       * (1.0 - LandV3.smoothstepPublic(250_000.0, 320_000.0, Dc));
        // 强度缩放（不再是符号门）：汇聚越强弧越明显，弱汇聚几乎没有弧
        double arcGate = LandV3.smoothstepPublic(0.15, 0.55, convC);
        double sA = arcGate * (1.0 - ctype) * arcBand;
        // 裂谷：离散 + 陆壳（等 RIFT_ON 打开后才有实际地形）
        // 裂谷带用【真实轴距离】（Dd 被 softmin 的 clamp 抹平了，会把整片区域都算成裂谷）
        double dDivRaw = bf.length > 19 ? bf[19] : Dd;
        double sF = sDiv * ctype * (1.0 - LandV3.smoothstepPublic(0.0, RIFT_RANGE, dDivRaw));
        // 第 6 项：到边界的距离（km）；第 7/8 项：边界法向的【双角表示】(cos2θ, sin2θ)
        // 双角表示对 ±n 不敏感，且插值天然连续 —— 山带方向用它。
        double nvx = bf[5], nvz = bf[6];
        double nn = Math.sqrt(nvx * nvx + nvz * nvz);
        if (nn < 1e-9) { nvx = 1.0; nvz = 0.0; nn = 1.0; }
        double c2 = (nvx * nvx - nvz * nvz) / (nn * nn);
        double s2 = 2.0 * nvx * nvz / (nn * nn);
        // 第 10 项：高原权重 —— 大陆碰撞的【广泛增厚】：强汇聚 + 宽范围（D < 1,500 km）
        // 高原半径随【汇聚强度】增大：碰撞越强、地壳越厚 ⟹ 高原扩散越远（500 → 2,600 km）
        // 高原用【区域汇聚】（同一条造山带的两条边取最大），不受"最近边是离散"影响
        double convPlateau = bf.length > 20 && bf[20] > 0.0 ? bf[20] : convC;
        double plateauRange = 500_000.0 + 2_100_000.0 * Math.min(1.0, Math.max(0.0, convPlateau) / 0.55);
        // 二级互斥：高原是【山带后方的环带】，山带处权重归零
        // 环带：山带外缘 350 km 起，到 2,400 km 止（只在 Dconv 有意义时成立）
        double ringIn = LandV3.smoothstepPublic(350_000.0, 600_000.0, Dc);
        double ringOut = 1.0 - LandV3.smoothstepPublic(900_000.0, 1_400_000.0, Dc);
        // 嵌套：山带 = 所有汇聚带；高原 = 只有"极强汇聚"才有，从缝合带开始（无内缝）
        double Ireg = bf.length > 20 && bf[20] > 0.0 ? bf[20] : Math.max(0.0, convC);
        // 连续汇聚场（ψ 的负拉普拉斯）：成片、不在边上翻转 ⟹ 山带能连成长链
        double Cnorm = Math.min(1.0, Math.max(0.0, LandV3.convergField(ws, x, z)) / 8e-15);
        double Inorm = Math.min(1.0, Math.max(Cnorm, Ireg / 3.0) * 0.5 + 0.5 * Cnorm);
        // 山带空间项用【最近边的距离 D】（任何类型都连续），汇聚门用【区域汇聚】
        // —— 否则到了多边形顶点，最近边换成另一条，conv 翻符号 ⟹ 山带被切断
        // 构造省：一块 1,600 km 的区域；主脊是省内 D<280 km 的一条
        // 省与主脊都用【到最近汇聚边的垂距 Dconv】⟹ 沿整条缝合带成带，不会在顶点塌成圆
        double province = LandV3.smoothstepPublic(0.25, 0.55, Inorm)
                        * (1.0 - LandV3.smoothstepPublic(0.55 * 1_600_000.0, 1_600_000.0, Dc));
        // 山带 = 连续汇聚场 × 边界附近窗口（不再依赖"某条边" ⟹ 可以连成长链）
        // 顶点归一：附近汇聚边越多，单条带的权重越低（避免三叉点加宽）
        double nConv = bf.length > 21 ? Math.max(1.0, bf[21]) : 1.0;
        double crest = LandV3.smoothstepPublic(0.15, 0.55, Cnorm)
                     * Math.exp(-(D / 450_000.0) * (D / 450_000.0)) * ctypeM
                     / (1.0 + 0.5 * (nConv - 1.0));
        double pw = province;   // w[9] 现在是"省"
        v = new float[]{(float) sM, (float) sT, (float) sR, (float) sA, (float) sF,
                        (float) (D / 1e6), (float) c2, (float) s2, (float) mtnAmp, (float) pw,
                        (float) (dDivRaw / 1e6), (float) crest};
        if (nodeWs != ws) { NODES.clear(); nodeWs = ws; }
        NODES.put(key, v);
        return v;
    }

    private static final ThreadLocal<double[]> BF = ThreadLocal.withInitial(() -> new double[24]);

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
        for (int k = 0; k < 12; k++) {
            double top = a[k] + (b[k] - a[k]) * su;
            double bot = c[k] + (d[k] - c[k]) * su;
            out[k] = top + (bot - top) * sv;
        }
    }

    /** 缓存节点数（监控用）。 */
    public static int cachedNodes() { return NODES.size(); }

    /**
     * 区域节点算法的配置指纹（2026-10-25 Phase 0）。
     *
     * <p>这些常量参与 {@link #node(long,long,long)}，改任何一个都会改变高度；
     * 之前它们既不在 {@code LandV3.configStamp()} 里，也没有清缓存入口。
     */
    public static long configStamp() {
        long h = 0x52A7F13DL;
        h = h * 31 + Double.doubleToLongBits(LATTICE);
        h = h * 31 + Double.doubleToLongBits(MOUNTAIN_RANGE);
        h = h * 31 + Double.doubleToLongBits(TRENCH_RANGE);
        h = h * 31 + Double.doubleToLongBits(RIDGE_RANGE);
        h = h * 31 + Double.doubleToLongBits(RIFT_RANGE);
        h = h * 31 + Double.doubleToLongBits(CONV_LO);
        h = h * 31 + Double.doubleToLongBits(CONV_HI);
        return h;
    }

    /** 清空节点缓存（A/B 或改常量后必须调用）。 */
    public static void clearCache() {
        NODES.clear();
        nodeWs = Long.MIN_VALUE;
    }
}
