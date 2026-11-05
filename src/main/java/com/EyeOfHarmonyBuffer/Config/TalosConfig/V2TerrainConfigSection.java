package com.EyeOfHarmonyBuffer.Config.TalosConfig;

import net.minecraftforge.common.config.Configuration;

/**
 * V2 世界层开关。
 *
 * **这里曾经有 {@code terrainV2Enabled}（默认 false）**：它决定整条地形链走 V2 还是走旧轨
 * （TectonicWorld 海陆 + TalosMacroClimate 宏群系 + TerrainEngine 预设高度 + RVR2 河网 +
 * 旧洞穴），并且被下游 8 处各自再读一次 —— 同一份代码能生成两个完全不同的世界。
 * 该开关与旧轨已整体删除：**地形链唯一**（见 {@code ChunkProviderTalos2.onChunkProvider}）。
 * 剩下这个开关只控制山层**内部**用不用 V2 抬升场，不影响"走哪条链"。
 */
public final class V2TerrainConfigSection {

    private static final String CATEGORY = "talos.v2";

    /** 是否启用 V2 山层（过程驱动：抬升场 + 侵蚀 + 权威权重，替换 DLA）。 */
    public static boolean mountainV2Enabled = true;

    private V2TerrainConfigSection() {}

    public static void load(Configuration config) {
        mountainV2Enabled = config
            .get(CATEGORY, "mountainV2Enabled", mountainV2Enabled,
                "V2 山层开关（默认开）：按种子离线求解抬升场 + 侵蚀（每格 50km×200km，250m 网格），"
                + "运行时双线性查询；与基础地形按权威权重 w 仲裁合成。关 = 只有基础地形自带山地。")
            .getBoolean(mountainV2Enabled);
    }
}
