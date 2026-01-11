package com.EyeOfHarmonyBuffer.Config.TalosConfig;

import net.minecraftforge.common.config.Configuration;

public final class HydroConfigSection {

    private static final String CAT_HYDRO = "fieldmanager.hydro";
    private static final String CAT_HYDRO_RIVER = "fieldmanager.hydro.river";
    private static final String CAT_HYDRO_LAKE = "fieldmanager.hydro.lake";
    private static final String CAT_HYDRO_COAST = "fieldmanager.hydro.coastline";
    private static final String CAT_HYDRO_DIAG = "fieldmanager." +
        "hydro.diagnostics";

    // hydro
    public static double hydroSeaLevel = 64.0d;

    // hydro - groundwater
    public static double hydroBaseSaturation = 0.25d;
    public static double hydroSaturationVariance = 0.35d;
    public static double hydroBaseAquiferNormalized = 0.45d;
    public static double hydroAquiferVariance = 0.25d;
    public static double hydroMaxFlowRate = 0.8d;
    public static double hydroHeightFalloffBlocks = 64.0d;
    public static double hydroHeightWeight = 0.5d;

    public static double hydroSaturationNoiseFrequency = 1.0d / 256.0d;
    public static double hydroSaturationNoiseLacunarity = 2.0d;
    public static double hydroSaturationNoisePersistence = 0.5d;
    public static int    hydroSaturationNoiseOctaves = 4;

    public static double hydroFlowNoiseFrequency = 1.0d / 192.0d;
    public static double hydroFlowNoiseLacunarity = 2.0d;
    public static double hydroFlowNoisePersistence = 0.5d;
    public static int    hydroFlowNoiseOctaves = 4;

    public static double hydroWaterTableBufferBlocks = 6.0d;

    // hydro - river
    public static double hydroRiverFrequency = 0.0015d;
    public static double hydroRiverDetailFrequency = 0.006d;
    public static double hydroRiverStrength = 1.0d;
    public static double hydroRiverThreshold = 0.45d;
    public static int hydroRiverSeedOffset = 51;
    public static int hydroRiverSmoothRadius = 2;

    // hydro - lake
    public static boolean hydroLakeEnabled = false;
    public static double hydroLakeThreshold = 0.7d;
    public static int hydroLakeSeedOffset = 73;

    // hydro - coastline
    public static boolean hydroCoastSyncWithMacro = true;
    public static double hydroCoastFalloff = 16.0d;

    // hydro - diagnostics
    public static boolean hydroDiagLogSamples = false;
    public static int hydroDiagProbeInterval = 500;

    public HydroConfigSection() {}

    public static void load(Configuration config) {
        loadGroundwater(config);
        loadRiver(config);
        loadLake(config);
        loadCoast(config);
        loadDiagnostics(config);
    }

    private static void loadGroundwater(Configuration config) {
        hydroSeaLevel = config
            .get(CAT_HYDRO, "seaLevel", hydroSeaLevel,
                "海平面高度。")
            .getDouble(hydroSeaLevel);

        hydroBaseSaturation = clamp01(
            config.get(CAT_HYDRO, "baseSaturation", hydroBaseSaturation,
                    "陆地平均饱和度（0~1，越高越湿）。")
                .getDouble(hydroBaseSaturation)
        );

        hydroSaturationVariance = clamp01(
            config.get(CAT_HYDRO, "saturationVariance", hydroSaturationVariance,
                    "饱和度噪声波动幅度（0~1）。")
                .getDouble(hydroSaturationVariance)
        );

        hydroBaseAquiferNormalized = clamp01(
            config.get(CAT_HYDRO, "baseAquiferNormalized", hydroBaseAquiferNormalized,
                    "地下水位在世界高度中的归一化位置（0=世界底，1=世界顶）。")
                .getDouble(hydroBaseAquiferNormalized)
        );

        hydroAquiferVariance = clamp01(
            config.get(CAT_HYDRO, "aquiferVariance", hydroAquiferVariance,
                    "地下水位噪声幅度（0~1）。")
                .getDouble(hydroAquiferVariance)
        );

        hydroMaxFlowRate = clamp01(
            config.get(CAT_HYDRO, "maxFlowRate", hydroMaxFlowRate,
                    "地下水流速上限（0~1）。")
                .getDouble(hydroMaxFlowRate)
        );

        hydroHeightFalloffBlocks = Math.max(1.0d,
            config.get(CAT_HYDRO, "heightFalloffBlocks", hydroHeightFalloffBlocks,
                    "地形高于海平面多少方块后饱和度衰减到 0。")
                .getDouble(hydroHeightFalloffBlocks)
        );

        hydroHeightWeight = clamp01(
            config.get(CAT_HYDRO, "heightWeight", hydroHeightWeight,
                    "高度因子对饱和度的影响权重（0~1）。")
                .getDouble(hydroHeightWeight)
        );

        hydroSaturationNoiseFrequency = config
            .get(CAT_HYDRO, "saturationNoiseFrequency", hydroSaturationNoiseFrequency,
                "饱和度噪声频率（1/波长）。")
            .getDouble(hydroSaturationNoiseFrequency);

        hydroSaturationNoiseLacunarity = config
            .get(CAT_HYDRO, "saturationNoiseLacunarity", hydroSaturationNoiseLacunarity,
                "饱和度噪声每层频率倍增系数。")
            .getDouble(hydroSaturationNoiseLacunarity);

        hydroSaturationNoisePersistence = config
            .get(CAT_HYDRO, "saturationNoisePersistence", hydroSaturationNoisePersistence,
                "饱和度噪声每层振幅衰减系数。")
            .getDouble(hydroSaturationNoisePersistence);

        hydroSaturationNoiseOctaves = Math.max(1,
            config.get(CAT_HYDRO, "saturationNoiseOctaves", hydroSaturationNoiseOctaves,
                    "饱和度噪声叠加层数（>=1）。")
                .getInt(hydroSaturationNoiseOctaves)
        );

        hydroFlowNoiseFrequency = config
            .get(CAT_HYDRO, "flowNoiseFrequency", hydroFlowNoiseFrequency,
                "水流噪声频率。")
            .getDouble(hydroFlowNoiseFrequency);

        hydroFlowNoiseLacunarity = config
            .get(CAT_HYDRO, "flowNoiseLacunarity", hydroFlowNoiseLacunarity,
                "水流噪声每层频率倍增。")
            .getDouble(hydroFlowNoiseLacunarity);

        hydroFlowNoisePersistence = config
            .get(CAT_HYDRO, "flowNoisePersistence", hydroFlowNoisePersistence,
                "水流噪声每层振幅衰减。")
            .getDouble(hydroFlowNoisePersistence);

        hydroFlowNoiseOctaves = Math.max(1,
            config.get(CAT_HYDRO, "flowNoiseOctaves", hydroFlowNoiseOctaves,
                    "水流噪声叠加层数（>=1）。")
                .getInt(hydroFlowNoiseOctaves)
        );

        hydroWaterTableBufferBlocks = Math.max(0.0d,
            config.get(CAT_HYDRO, "waterTableBufferBlocks", hydroWaterTableBufferBlocks,
                    "水位距离地表的最小缓冲（方块数，>=0）。")
                .getDouble(hydroWaterTableBufferBlocks)
        );
    }

    private static void loadRiver(Configuration config) {
        hydroRiverFrequency = config
            .get(CAT_HYDRO_RIVER, "frequency", hydroRiverFrequency,
                "主河网噪声频率。")
            .getDouble(hydroRiverFrequency);

        hydroRiverDetailFrequency = config
            .get(CAT_HYDRO_RIVER, "detailFrequency", hydroRiverDetailFrequency,
                "细节噪声频率。")
            .getDouble(hydroRiverDetailFrequency);

        hydroRiverStrength = config
            .get(CAT_HYDRO_RIVER, "strength", hydroRiverStrength,
                "河流强度系数。")
            .getDouble(hydroRiverStrength);

        hydroRiverThreshold = config
            .get(CAT_HYDRO_RIVER, "threshold", hydroRiverThreshold,
                "河流判定阈值（0~1，越低河越多）。")
            .getDouble(hydroRiverThreshold);

        hydroRiverSeedOffset = config
            .get(CAT_HYDRO_RIVER, "seedOffset", hydroRiverSeedOffset,
                "河流噪声 seed 偏移。")
            .getInt(hydroRiverSeedOffset);

        hydroRiverSmoothRadius = Math.max(0,
            config.get(CAT_HYDRO_RIVER, "smoothRadius", hydroRiverSmoothRadius,
                    "河流平滑半径（>=0）。")
                .getInt(hydroRiverSmoothRadius)
        );
    }

    private static void loadLake(Configuration config) {
        hydroLakeEnabled = config
            .get(CAT_HYDRO_LAKE, "enabled", hydroLakeEnabled,
                "是否生成湖泊/内陆海字段。")
            .getBoolean(hydroLakeEnabled);

        hydroLakeThreshold = config
            .get(CAT_HYDRO_LAKE, "threshold", hydroLakeThreshold,
                "湖泊判定阈值。")
            .getDouble(hydroLakeThreshold);

        hydroLakeSeedOffset = config
            .get(CAT_HYDRO_LAKE, "seedOffset", hydroLakeSeedOffset,
                "湖泊噪声 seed 偏移。")
            .getInt(hydroLakeSeedOffset);
    }

    private static void loadCoast(Configuration config) {
        hydroCoastSyncWithMacro = config
            .get(CAT_HYDRO_COAST, "syncWithMacro", hydroCoastSyncWithMacro,
                "是否与宏格海岸线保持一致。")
            .getBoolean(hydroCoastSyncWithMacro);

        hydroCoastFalloff = config
            .get(CAT_HYDRO_COAST, "falloff", hydroCoastFalloff,
                "海岸平滑过渡宽度。")
            .getDouble(hydroCoastFalloff);
    }

    private static void loadDiagnostics(Configuration config) {
        hydroDiagLogSamples = config
            .get(CAT_HYDRO_DIAG, "logSamples", hydroDiagLogSamples,
                "是否额外输出 Hydro 样本日志。")
            .getBoolean(hydroDiagLogSamples);

        hydroDiagProbeInterval = Math.max(1,
            config.get(CAT_HYDRO_DIAG, "probeInterval", hydroDiagProbeInterval,
                    "诊断日志间隔（采样次数，>=1）。")
                .getInt(hydroDiagProbeInterval)
        );
    }

    private static double clamp01(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }
}
