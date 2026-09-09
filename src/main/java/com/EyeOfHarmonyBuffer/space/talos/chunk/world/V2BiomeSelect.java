package com.EyeOfHarmonyBuffer.space.talos.chunk.world;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.NoiseContinentGrid;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.OrographyField;

/**
 * V2 群系场（L1c）：**气候带（Tier-1）软分配 + 地形变体（Tier-2/3）**。
 *
 * 输入：{@link ClimateCoords}（气候坐标，由 M1–M6 环流解派生：风带/洋流/气团/地形）
 *       + L1b 类型场（relief01 / elevation01 / kind / coastDist）。
 * 输出：群系种类 {@link Kind} + 高度倾向（按候选权重插值 → 地形连续）。
 *
 * **不读最终高度** → 与高度层无循环依赖；本类不依赖 Minecraft 类型，便于离线探针。
 *
 * 层级：
 *   Tier-1 气候带：5 温度带 × 4 湿度带 = 20 格查表，帐篷隶属度取最多 4 个候选
 *   Tier-2 地形：山地 / 高山 / 高原 / 盆地（连续门控权重，与气候候选一起归一化）
 *   Tier-3 特殊变体：预留（沼泽/盐沼/绿洲…）——加一条 acc.add(...) 即可
 *
 * 扩展方式：新群系 = 在 {@link #CLIMATE} 表里占一格，或加一个带门控权重的候选；
 * 高度倾向随权重自动插值，无需改高度层。
 */
public final class V2BiomeSelect {

    private V2BiomeSelect() {}

    /** 浅岸陆架半宽（blocks）。 */
    public static final double SHELF_BLOCKS = 2400.0;

    // ===== Tier-1 气候带中心与隶属度宽度 =====
    /** 温度带中心：极地 / 寒温 / 温带 / 亚热带 / 热带。 */
    public static double[] TEMP_CENTER = {0.10, 0.30, 0.52, 0.72, 0.90};
    /** 湿度带中心：干旱 / 半干旱 / 湿润 / 过湿。 */
    public static double[] MOIST_CENTER = {0.30, 0.44, 0.60, 0.80};
    public static double TEMP_SPAN = 0.22, MOIST_SPAN = 0.16;

    // ===== Tier-2 地貌变体（山体强度来自 LandformField，与地形同源） =====
    /** 高山：温度越低权重越高（mtnAmt × 该门控）。 */
    public static double ALPINE_TEMP_HI = 0.45, ALPINE_TEMP_LO = 0.20;
    /** 盆地：内陆 + 湿润 + 低洼（低洼用档案低地权重）。 */
    public static double BASIN_INLAND_LO = 0.35, BASIN_INLAND_HI = 0.60;
    public static double BASIN_MOIST_LO = 0.40, BASIN_MOIST_HI = 0.60;

    /** Tier-1 查表：行 = 温度带（极地→热带），列 = 湿度带（干旱→过湿）。 */
    private static final Kind[][] CLIMATE = {
        {Kind.POLAR_DESERT, Kind.POLAR_DESERT, Kind.SUBPOLAR_TUNDRA, Kind.SUBPOLAR_TUNDRA},
        {Kind.POLAR_DESERT, Kind.SUBPOLAR_TUNDRA, Kind.COOL_FOREST, Kind.COOL_FOREST},
        {Kind.TEMPERATE_STEPPE, Kind.PLAINS, Kind.TEMPERATE_FOREST, Kind.COOL_FOREST},
        {Kind.DESERT, Kind.WARM_STEPPE, Kind.TEMPERATE_FOREST, Kind.TEMPERATE_FOREST},
        {Kind.DESERT, Kind.SAVANNA, Kind.TROPICAL_RAIN, Kind.TROPICAL_RAIN},
    };

    /**
     * 群系种类（16）。括号内为高度倾向 (bias, scale) —— V2 高度层权威值，
     * 与 BiomeGenTalos2* 里的 heightBias/heightScale 同值（后者供 V1 轨）。
     */
    public enum Kind {
        OCEAN(0.35, 0.60), SHELF(0.35, 0.60),
        PLAINS(0.45, 0.55), BASIN(0.30, 0.40), PLATEAU(0.62, 0.45),
        MOUNTAINS(0.72, 0.60), ALPINE(0.68, 0.50),
        DESERT(0.45, 0.40), SAVANNA(0.45, 0.50), TROPICAL_RAIN(0.42, 0.50),
        WARM_STEPPE(0.42, 0.45), TEMPERATE_FOREST(0.50, 0.55), TEMPERATE_STEPPE(0.40, 0.45),
        COOL_FOREST(0.52, 0.55), SUBPOLAR_TUNDRA(0.35, 0.40), POLAR_DESERT(0.15, 0.20);

        public final double heightBias;
        public final double heightScale;

        Kind(double bias, double scale) {
            this.heightBias = bias;
            this.heightScale = scale;
        }
    }

    /** 一次选择的结果。 */
    public static final class Sel {
        public Kind kind;
        public double heightBias = 0.5, heightScale = 0.5;
        public boolean isLand;
        // ---- 诊断 / 出图 ----
        public double temp, moist, continent;
        public double windX, windZ, sstAnom, up, lee;
        public double wMtn, wAlpine, wPlat, wBasin;
    }

    /** 群系种类数（候选权重数组长度）。 */
    public static final int KINDS = Kind.values().length;

    /**
     * 把所有候选权重**累加**到 out[KINDS]（调用方负责先清零）。
     * 这是 L1c 的规范化出口：单点选择（{@link #select}）与离线 LUT（V2BiomeField）
     * 都走这里，保证两者口径完全一致。
     */
    public static double accumulateWeights(int x, int z, int worldSeedInt,
                                            OrographyField.OroSample oro, double[] out) {
        return accumulateWeights(x, z, worldSeedInt, oro, oro.isLand, out);
    }

    /**
     * 同上，但可强制按"陆地"口径算（asLand=true）。
     * LUT 求解时对**每一格**都算陆地候选（用于海岸线精确取用），海格另存 OCEAN/SHELF。
     */
    public static double accumulateWeights(int x, int z, int worldSeedInt,
                                            OrographyField.OroSample oro, boolean asLand,
                                            double[] out) {
        if (!asLand) {
            double cd = NoiseContinentGrid.coastDistBlocks(x, z, worldSeedInt);
            out[(cd < SHELF_BLOCKS ? Kind.SHELF : Kind.OCEAN).ordinal()] += 1.0;
            return 1.0;
        }
        ClimateCoords.Coords c = ClimateCoords.sample(x, z, worldSeedInt, oro);

        // 坐标夹到带心覆盖范围内：否则极端值（如 moist=1.0 超出末端带心+跨度）会
        // 一个候选都匹配不到 → argmax 落到数组默认值 0（= OCEAN），陆地列被误判成海。
        double temp = clamp(c.temp, TEMP_CENTER[0], TEMP_CENTER[TEMP_CENTER.length - 1]);
        double moist = clamp(c.moist, MOIST_CENTER[0], MOIST_CENTER[MOIST_CENTER.length - 1]);

        // Tier-1：气候带帐篷隶属度（每轴最多 2 个非零 → 最多 4 个候选）
        // 先算总和并归一化到 1 —— 否则一个群系能从 2×2 个带各拿一份权重、总和 >1，
        // 会把地貌变体（上限 1）压过去（这正是"山区被判成沙漠"的直接原因）。
        double climSum = 0.0;
        for (int i = 0; i < TEMP_CENTER.length; i++) {
            double wt = tent(temp, TEMP_CENTER[i], TEMP_SPAN);
            if (wt <= 0.0) {
                continue;
            }
            for (int j = 0; j < MOIST_CENTER.length; j++) {
                double wm = tent(moist, MOIST_CENTER[j], MOIST_SPAN);
                if (wm <= 0.0) {
                    continue;
                }
                double w = wt * wm;
                out[CLIMATE[i][j].ordinal()] += w;
                climSum += w;
            }
        }
        if (climSum > 1e-9) {
            double inv = 1.0 / climSum;
            for (int q = 0; q < KINDS; q++) {
                if (out[q] != 0.0) {
                    out[q] *= inv;
                }
            }
        }

        // Tier-2：地貌变体 —— 唯一权威是 LandformField（地形同源，不再自己定阈值）
        LandformField.Sample lf = LandformField.sample(x, z, worldSeedInt);
        double mtnW = lf.mtnAmt;
        // 高山 = 真山 × 冷 × **雪线以上**（用中性 bias 的静态骨架高度，避免与地形循环依赖）
        double snow = ss(V2TerrainGen.snowLineY(z), V2TerrainGen.snowLineY(z) + 30.0, lf.h0);
        double alpineW = mtnW * ss(ALPINE_TEMP_HI, ALPINE_TEMP_LO, temp) * snow;
        double mtnOnlyW = mtnW - alpineW;
        double platW = lf.plat * (1.0 - mtnW);
        double basinW = ss(BASIN_INLAND_LO, BASIN_INLAND_HI, c.continent)
            * ss(BASIN_MOIST_LO, BASIN_MOIST_HI, c.moist)
            * (1.0 - mtnW) * lf.low;

        // 两个维度做**混合**而不是竞争：地貌置信度 d ∈ [0,1] 决定谁说话
        //   final = (1−d)·气候 + 地貌
        // d=1（真山）→ 完全由地貌决定；d=0（平地）→ 完全由气候决定。
        double d = mtnW + platW + basinW;
        if (d > 1.0) {
            d = 1.0;
        }
        double climateScale = 1.0 - d;
        for (int q = 0; q < KINDS; q++) {
            if (out[q] != 0.0) {
                out[q] *= climateScale;
            }
        }
        out[Kind.ALPINE.ordinal()] += alpineW;
        out[Kind.MOUNTAINS.ordinal()] += mtnOnlyW;
        out[Kind.PLATEAU.ordinal()] += platW;
        out[Kind.BASIN.ordinal()] += basinW;
        return d;   // 地貌置信度（供平滑层按置信度加权投票）
    }

    /** 单点选择（未平滑；LUT 求解与探针用）。 */
    public static Sel select(int x, int z, int worldSeedInt, OrographyField.OroSample oro) {
        Sel s = new Sel();
        s.isLand = oro.isLand;
        if (!oro.isLand) {
            double cd = NoiseContinentGrid.coastDistBlocks(x, z, worldSeedInt);
            set(s, cd < SHELF_BLOCKS ? Kind.SHELF : Kind.OCEAN);
            return s;
        }
        ClimateCoords.Coords c = ClimateCoords.sample(x, z, worldSeedInt, oro);
        s.temp = c.temp;
        s.moist = c.moist;
        s.continent = c.continent;
        s.windX = c.windX;
        s.windZ = c.windZ;
        s.sstAnom = c.sstAnom;
        s.up = c.up;
        s.lee = c.lee;

        double[] w = new double[KINDS];
        accumulateWeights(x, z, worldSeedInt, oro, w);
        // 返回值是地貌置信度，单点选择不需要
        double wSum = 0, biasSum = 0, scaleSum = 0;
        Kind best = Kind.PLAINS;
        double bestW = -1;
        for (int k = 0; k < KINDS; k++) {
            double wk = w[k];
            if (wk <= 0.0) {
                continue;
            }
            Kind kk = Kind.values()[k];
            wSum += wk;
            biasSum += wk * kk.heightBias;
            scaleSum += wk * kk.heightScale;
            if (wk > bestW) {
                bestW = wk;
                best = kk;
            }
        }
        s.heightBias = wSum > 1e-9 ? biasSum / wSum : 0.5;
        s.heightScale = wSum > 1e-9 ? scaleSum / wSum : 0.5;
        s.kind = best;
        return s;
    }

    /** 只取群系种类（不需要高度倾向时的快捷方式）。 */
    public static Kind kindAt(int x, int z, int worldSeedInt, OrographyField.OroSample oro) {
        return select(x, z, worldSeedInt, oro).kind;
    }

    /** 帐篷隶属度：|v − c| ≥ span → 0，中心 → 1。 */
    private static double tent(double v, double c, double span) {
        double d = Math.abs(v - c);
        return d >= span ? 0.0 : 1.0 - d / span;
    }

    /** smoothstep（hi 可以小于 lo，表示反向）。 */
    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static double ss(double lo, double hi, double v) {
        double t = (v - lo) / (hi - lo);
        t = t < 0.0 ? 0.0 : (t > 1.0 ? 1.0 : t);
        return t * t * (3.0 - 2.0 * t);
    }

    private static void set(Sel s, Kind k) {
        s.kind = k;
        s.heightBias = k.heightBias;
        s.heightScale = k.heightScale;
    }
}
