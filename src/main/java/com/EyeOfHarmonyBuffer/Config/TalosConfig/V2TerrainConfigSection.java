package com.EyeOfHarmonyBuffer.Config.TalosConfig;

import net.minecraftforge.common.config.Configuration;

/**
 * X1 阶段2：V2 地形轨开关（默认关）。
 *
 * 开 = ChunkProviderTalos2 / WorldChunkManagerTalos2 / BiomeDecoratorTalos2 走 V2 轨：
 *   L1 新海陆（NoiseContinentGrid）+ L1b 地形骨架（OrographyField）直接出方块地形；
 * 关 = 原生产轨（TectonicWorld 海陆 + TalosMacroClimate 宏群系 + TerrainEngine 预设高度）零影响。
 *
 * 开关为静态字段：世界生成线程按需读取（每次读值 ~1ns，无同步问题）。
 */
public final class V2TerrainConfigSection {

    private static final String CATEGORY = "talos.v2";

    /** 是否启用 V2 地形轨（新海陆噪声场 + orography 骨架直接生成方块）。 */
    public static boolean terrainV2Enabled = false;

    /** 是否启用 V2 山层（过程驱动：抬升场 + 侵蚀 + 权威权重，替换 DLA）。 */
    public static boolean mountainV2Enabled = true;

    private V2TerrainConfigSection() {}

    public static void load(Configuration config) {
        terrainV2Enabled = config
            .get(CATEGORY, "terrainV2Enabled", terrainV2Enabled,
                "X1 阶段2 开关：V2 地形轨（NoiseContinentGrid/OrographyField 直接出方块）。"
                + "默认关（生产仍走 TectonicWorld 旧轨）；开 = 新旧两轨按同一 Provider 分叉。")
            .getBoolean(terrainV2Enabled);

        mountainV2Enabled = config
            .get(CATEGORY, "mountainV2Enabled", mountainV2Enabled,
                "V2 山层开关（默认开）：按种子离线求解抬升场 + 侵蚀（400k×200k 环面，400m 网格），"
                + "运行时双线性查询；与基础地形按权威权重 w 仲裁合成。关 = 只有基础地形自带山地。")
            .getBoolean(mountainV2Enabled);
    }
}
