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
    /** id 11 预留给岩性（tier3），暂不放进本枚举。 */

    public static final String[] NAMES = {
        "DEEP_OCEAN", "SHELF", "TRENCH", "MID_OCEAN_RIDGE", "ISLAND_ARC",
        "PLAIN", "HILL", "MOUNTAIN", "RIFT", "BEACH", "CLIFF", "PLATEAU"
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

    private static final ThreadLocal<double[]> W5 = ThreadLocal.withInitial(() -> new double[12]);

    /** 沙滩标签的半宽（m）：只看离岸距离，**不压制高度**。 */
    public static double BEACH_LABEL_W = 150.0;
    /** 断崖标签：离岸半宽（m）与最低背景高度（m）。 */
    public static double CLIFF_LABEL_W = 250.0;
    public static double CLIFF_MIN_H = 120.0;
    /** 裂谷标签的最高高程（m）：裂谷是低地，不是高山。 */
    public static double RIFT_MAX_H = 1_200.0;
    /** 高原的最低高程（m）与权重阈值。 */
    public static double PLATEAU_MIN_H = 1_500.0;
    public static double PLATEAU_W = 0.75;
    /** 山脉的权重阈值（w[0]；0.40 ≈ 陆地 6.3%，约 2.1% 的世界）。 */
    public static double MOUNTAIN_W = 0.30;
    /** 岩性权重（tier3，连续）：rock 1 = 岩岸，0 = 沙岸。只读，不影响几何。 */
    public static double rockyWeight(long ws, double x, double z) {
        double n = 0.6 * com.EyeOfHarmonyBuffer.sim2.land.LandNoise.value(x / 2_500_000.0, z / 2_500_000.0, ws ^ 0x7E1L)
                 + 0.4 * com.EyeOfHarmonyBuffer.sim2.land.LandNoise.value(x / 900_000.0 + 3.1, z / 900_000.0 - 1.7, ws ^ 0x7E2L);
        return n;
    }

    public static int classify(long ws, double x, double z) {
        double h = LandV3.height(ws, x, z);
        double s = LandV3.coastS(ws, x, z);
        boolean land = s > 0.0;
        double d = Math.abs(s) < 1.2e-3 ? LandV3.coastDistM(ws, x, z) : Double.NaN;
        double[] w = W5.get();
        RegionField.weights(ws, x, z, w);
        return from(land, h, d, w);
    }

    /** 已有 h、海陆掩膜、离岸距离与权重时用这个（渲染器批量用）。海陆由 land 决定，**不看 h 的符号**。
     *  d = NaN 表示不在海岸带内（不判沙滩）。 */
    public static int from(boolean land, double h, double d, double[] w) {
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
        // 高原优先于山脉：强碰撞 + 宽范围 ⟹ 又高又平
        if (w.length > 9 && w[9] > PLATEAU_W && h > PLATEAU_MIN_H) return PLATEAU;
        // 山脉只看【山带权重】：不再用 h>=800（那会把高原边缘/抬升平原都算成山，视觉上"太肥"）
        if (w[0] > MOUNTAIN_W) return MOUNTAIN;
        if (w[4] > 0.5 && h < RIFT_MAX_H) return RIFT;
        // 山麓（w[0] 0.2–0.5）与 200 m 以上的陆地都算丘陵
        if (w[0] > 0.2 || h >= 200.0) return HILL;
        return PLAIN;
    }
}
