package com.EyeOfHarmonyBuffer.Config.TalosConfig;

import net.minecraftforge.common.config.Configuration;

public final class TerrainConfigSection {

    private static final String CATEGORY = "fieldmanager.terrain";

    public static int terrainNoiseSeedOffset = 0;
    public static double terrainFrequency = 0.003d;
    public static double terrainAmplitude = 32.0d;
    public static double terrainBaseHeight = 64.0d;
    public static int terrainOctaves = 4;
    public static double terrainPersistence = 0.5d;
    public static double terrainLacunarity = 2.0d;
    public static int terrainSlopeSampleStep = 4;
    public static boolean terrainCacheEnabled = false;
    public static int terrainCacheSize = 256;

    public static int terrainWorldFloor = 0;
    public static int terrainWorldCeiling = 255;
    public static double terrainFloorY = MacroSelectorConfigSection.selectorElevationMin;
    public static double terrainCeilingY = MacroSelectorConfigSection.selectorElevationMax;
    public static double terrainSeaLevel = HydroConfigSection.hydroSeaLevel;

    public TerrainConfigSection() {}

    public static void load(Configuration config) {
        terrainNoiseSeedOffset = config
            .get(CATEGORY, "noiseSeedOffset", terrainNoiseSeedOffset,
                "噪声 seed 偏移，避免与其他字段冲突。")
            .getInt(terrainNoiseSeedOffset);

        terrainFrequency = config
            .get(CATEGORY, "frequency", terrainFrequency,
                "基础噪声频率。")
            .getDouble(terrainFrequency);

        terrainAmplitude = config
            .get(CATEGORY, "amplitude", terrainAmplitude,
                "高度振幅。")
            .getDouble(terrainAmplitude);

        terrainBaseHeight = config
            .get(CATEGORY, "baseHeight", terrainBaseHeight,
                "平均地面高度。")
            .getDouble(terrainBaseHeight);

        terrainOctaves = config
            .get(CATEGORY, "octaves", terrainOctaves,
                "噪声叠加层数。")
            .getInt(terrainOctaves);

        terrainPersistence = config
            .get(CATEGORY, "persistence", terrainPersistence,
                "每层振幅衰减系数。")
            .getDouble(terrainPersistence);

        terrainLacunarity = config
            .get(CATEGORY, "lacunarity", terrainLacunarity,
                "每层频率放大倍数。")
            .getDouble(terrainLacunarity);

        terrainSlopeSampleStep = Math.max(1,
            config.get(CATEGORY, "slopeSampleStep", terrainSlopeSampleStep,
                    "坡度采样间隔（方块数，>=1）。")
                .getInt(terrainSlopeSampleStep)
        );

        terrainCacheEnabled = config
            .get(CATEGORY, "cacheEnabled", terrainCacheEnabled,
                "是否启用 Terrain 局部缓存。")
            .getBoolean(terrainCacheEnabled);

        terrainCacheSize = Math.max(32,
            config.get(CATEGORY, "cacheSize", terrainCacheSize,
                    "Terrain 缓存条目数（>=32，仅 cacheEnabled=true 时有效）。")
                .getInt(terrainCacheSize)
        );

        terrainWorldFloor = config
            .get(CATEGORY, "worldFloor", terrainWorldFloor,
                "绝对世界底部（方块高度，通常 0）。")
            .getInt(terrainWorldFloor);

        terrainWorldCeiling = config
            .get(CATEGORY, "worldCeiling", terrainWorldCeiling,
                "绝对世界顶部（方块高度，通常 255）。")
            .getInt(terrainWorldCeiling);

        terrainFloorY = config
            .get(CATEGORY, "terrainFloorY", terrainFloorY,
                "可用地形高度区间下限（与 Stage 6.2 的海陆判定保持一致）。")
            .getDouble(terrainFloorY);

        terrainCeilingY = config
            .get(CATEGORY, "terrainCeilingY", terrainCeilingY,
                "可用地形高度区间上限。")
            .getDouble(terrainCeilingY);

        terrainSeaLevel = config
            .get(CATEGORY, "terrainSeaLevel", terrainSeaLevel,
                "用于地形生成的海平面高度。")
            .getDouble(terrainSeaLevel);
    }
}
