package com.EyeOfHarmonyBuffer.sim2.land;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 地形标签：由同一套插值场（RegionField 权重）+ 基础高度导出。
 * id 稳定、分组预留（0–15 海洋，16–31 陆地，32–47 特殊）。
 */
public final class TerrainClass {
    public static final int DEEP_OCEAN = 0;
    public static final int SHELF = 1;
    public static final int TRENCH = 2;
    public static final int MID_OCEAN_RIDGE = 3;
    public static final int ISLAND_ARC = 4;
    public static final int PLAIN = 5;
    public static final int HILL = 6;
    public static final int MOUNTAIN = 7;
    public static final int RIFT = 8;
    public static final int BEACH = 9;
    /** 断崖（tier2 的另一种海岸风格）：离岸近 + 背景高度高。 */
    public static final int CLIFF = 10;
    /** 高原（大陆碰撞的广泛增厚）：高 + 平。 */
    public static final int PLATEAU = 11;
    /** 盆地（Voronoi 多边形内部的浅碗）。 */
    public static final int BASIN = 12;
    /** id 11 预留给岩性（tier3），暂不放进本枚举。 */

    public static final String[] NAMES = {
        "DEEP_OCEAN", "SHELF", "TRENCH", "MID_OCEAN_RIDGE", "ISLAND_ARC",
        "PLAIN", "HILL", "MOUNTAIN", "RIFT", "BEACH", "CLIFF", "PLATEAU", "BASIN"
    };

    /** 细节配方键（后续给每类接自己的细节用）。 */
    public static final String[] RECIPE = {
        "flat", "ripple", "incise", "swell", "volcanic",
        "smooth", "rough", "ridged", "valley", "sand"
    };

    private TerrainClass() {}

    /**
     * 陆架判定深度（m）：海里比它浅的就算 SHELF。
     *
     * <p>不需要专门造陆架——现有的海洋深度剖面（海岸 220 m → 深海 4,600 m）本来就
     * 在岸边形成了"相对高"的浅水带；这里只是把它**标定**出来。
     */
    public static double SHELF_MAX_DEPTH = 1_500.0;

    private static final ThreadLocal<double[]> W5 = ThreadLocal.withInitial(() -> new double[16]);

    /** 沙滩标签的半宽（m）：只看离岸距离，**不压制高度**。 */
    public static double BEACH_LABEL_W = 150.0;
    /** 断崖标签：离岸半宽（m）与最低背景高度（m）。 */
    public static double CLIFF_LABEL_W = 250.0;
    public static double CLIFF_MIN_H = 120.0;
    /** 裂谷标签的最高高程（m）：裂谷是低地，不是高山。 */
    public static double RIFT_MAX_H = 1_200.0;
    /** 裂谷权重阈值与轴半宽（1e6 m 为单位；0.10 ≈ 100 km）。 */
    public static double RIFT_W = 0.5;
    public static double RIFT_AXIS_KM = 0.10;
    /** 噪声裂谷的标签阈值。 */
    public static double RIFT_NOISE_W = 0.50;
    /** 裂谷标签：实际下切超过这个深度（m）就算裂谷。 */
    public static double RIFT_CARVE_LABEL_M = 30.0;
    /** 盆地标签：下切超过这个深度（m）。 */
    public static double BASIN_LABEL_M = 15.0;
    /** 盆地标签的最高高程（m）：再高就算高原/山地，不叫盆地。 */
    public static double BASIN_MAX_H = 2_500.0;
    /** 高原的最低高程（m）与权重阈值。 */
    public static double PLATEAU_MIN_H = 1_500.0;
    public static double PLATEAU_W = 0.75;
    /** 山脉的权重阈值（w[0]；0.40 ≈ 陆地 6.3%，约 2.1% 的世界）。 */
    public static double MOUNTAIN_W = 0.30;
    /** 起伏判据（m）：±40 km 内的 max−min。 */
    public static double RELIEF_MOUNTAIN = 350.0;
    public static double RELIEF_HILL = 120.0;
    /** 岩性权重（tier3，连续）：rock 1 = 岩岸，0 = 沙岸。只读，不影响几何。 */
    public static double rockyWeight(long ws, double x, double z) {
        double n = 0.6 * com.EyeOfHarmonyBuffer.sim2.land.LandNoise.value(x / 2_500_000.0, z / 2_500_000.0, ws ^ 0x7E1L)
                 + 0.4 * com.EyeOfHarmonyBuffer.sim2.land.LandNoise.value(x / 900_000.0 + 3.1, z / 900_000.0 - 1.7, ws ^ 0x7E2L);
        return n;
    }

    /** 局部起伏（m）：±40 km 四点的 max−min。这是"平原/丘陵/山地"的物理判据。 */
    public static double relief(long ws, double x, double z) {
        final double R = 40_000.0;
        double a = LandV3.height(ws, x + R, z), b = LandV3.height(ws, x - R, z);
        double c = LandV3.height(ws, x, z + R), d = LandV3.height(ws, x, z - R);
        double hi = Math.max(Math.max(a, b), Math.max(c, d));
        double lo = Math.min(Math.min(a, b), Math.min(c, d));
        return hi - lo;
    }

    public static int classify(long ws, double x, double z) {
        double h = LandV3.height(ws, x, z);
        double s = LandV3.coastS(ws, x, z);
        boolean land = s > 0.0;
        double d = Math.abs(s) < 1.2e-3 ? LandV3.coastDistM(ws, x, z) : Double.NaN;
        double[] w = W5.get();
        RegionField.weights(ws, x, z, w);
        // 裂谷权重 × 谷形：只有"真的在谷里"才算
        // 直接传"实际下切深度"：标签与几何用同一个量
        double carve = land ? LandV3.riftCarve(ws, x, z, s, w, h) : 0.0;
        double basinD = land ? LandV3.basinCarve(ws, x, z, s, w) : 0.0;
        return from(land, h, d, w, carve, relief(ws, x, z), basinD);
    }

    /** 兼容旧签名（无噪声裂谷权重）。 */
    public static int from(boolean land, double h, double d, double[] w) {
        return from(land, h, d, w, 0.0, Double.NaN);
    }

    public static int from(boolean land, double h, double d, double[] w, double riftW) {
        return from(land, h, d, w, riftW, Double.NaN);
    }

    /** 已有 h、海陆掩膜、离岸距离、区域权重、裂谷权重与局部起伏时用这个（渲染器批量用）。
     *  d = NaN 表示不在海岸带内（不判沙滩）；rel = NaN 表示未提供起伏（退回高程判据）。 */
    public static int from(boolean land, double h, double d, double[] w, double riftW, double rel) {
        return from(land, h, d, w, riftW, rel, 0.0);
    }

    /** 完整版：带盆地下切深度（m）。 */
    public static int from(boolean land, double h, double d, double[] w, double riftW, double rel, double basinD) {
        // —— 海侧 ——
        if (!land) {
            if (w[1] > 0.5) return TRENCH;
            if (w[2] > 0.5) return MID_OCEAN_RIDGE;
            if (w[3] > 0.5) return ISLAND_ARC;
            return h > -SHELF_MAX_DEPTH ? SHELF : DEEP_OCEAN;
        }
        // —— 陆侧（h 可以是负的：海岸洼地，仍算陆地）——
        // 海岸风格只看【离岸距离 + 背景高度】，不压制高度
        if (!Double.isNaN(d)) {
            double ad = Math.abs(d);
            if (ad < CLIFF_LABEL_W && h > CLIFF_MIN_H) return CLIFF;   // 高背景 + 近岸 ⟹ 断崖
            if (ad < BEACH_LABEL_W) return BEACH;
        }
        // 裂谷：判定与几何一致 —— 实际下切深度超过阈值就算（riftW 传的是深度，单位 m）
        if (riftW > RIFT_CARVE_LABEL_M) return RIFT;
        // 盆地：基础地形的一种（低地），按【区域】判，不看高程
        if (basinD > BASIN_LABEL_M) return BASIN;
        // 高原优先于山脉：强碰撞 + 宽范围 ⟹ 又高又平
        // 二级：山脉 = 任意汇聚边上的带（不依附于省）；高原 = 强碰撞的省
        if (w.length > 11 && w[11] > 0.5) return MOUNTAIN;
        if (w.length > 9 && w[9] > 0.45) return PLATEAU;
        // 裂谷按【轴距离】判（高原上的裂谷地板也有 2,000–3,000 m，不能只看 h）
        // 板块边界版裂谷已回退（RIFT_GEOM_ON=false ⟹ 标签也不再触发）
        if (LandV3.RIFT_GEOM_ON && w[4] > RIFT_W && (w.length > 10 ? w[10] < RIFT_AXIS_KM : h < RIFT_MAX_H)) return RIFT;
        // 丘陵 = 山麓带（crest 0.2–0.5）或 中等地形起伏；否则平原
        double cw = w.length > 11 ? w[11] : 0.0;
        if (cw > 0.2 && cw <= 0.5) return HILL;
        if (!Double.isNaN(rel)) return rel > RELIEF_HILL ? HILL : PLAIN;
        return h >= 200.0 ? HILL : PLAIN;
    }
}
