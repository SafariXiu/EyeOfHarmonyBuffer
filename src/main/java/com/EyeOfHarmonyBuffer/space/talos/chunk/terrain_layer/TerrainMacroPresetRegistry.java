package com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer;

import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.api.MacroPackageId;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 第四层专用：每个 MacroPackageId 对应一份 BaseTerrainPreset。
 *
 * 不影响现有的 MacroPackageRegistry（那是给河流系统用的）。
 */

public final class TerrainMacroPresetRegistry {

    private static final Map<MacroPackageId, BaseTerrainPreset> PRESETS;

    static {
        EnumMap<MacroPackageId, BaseTerrainPreset> m =
            new EnumMap<>(MacroPackageId.class);

        // ===== 海洋 OCEANIC =====
        // 深海 + 陆架：高度带 [0, 56]，低频 1 层 + 中频很弱，高频关掉。
        m.put(MacroPackageId.OCEANIC,
            new BaseTerrainPreset(
                0.0,            // minHeight（最深处约海平面下 64）
                56.0,           // maxHeight（保持低于海平面）
                1.0 / 2000.0,   // lowFreq   大尺度海盆
                14.0,           // lowAmp
                1,              // lowOctaves
                1.0 / 600.0,    // midFreq   近岸/陆架起伏
                6.0,            // midAmp
                1,              // midOctaves
                0.0,            // highFreq  高频关掉
                0.0,            // highAmp
                0,              // highOctaves
                0.0,            // plateauStrength
                64.0,            // oceanDepthMax：最深约 seaLevel - 64
                0.0,            // cliffAmp
                1.0,            // cliffScale
                 0.0,            // terrace
                 0.0             // detailAmp
            )
        );

        // ===== 热带 / 亚热带 =====

        // 热带湿润盆地：整体抬高一些，低频振幅略减，减少大面积深盆地。
        m.put(MacroPackageId.TROPICAL_HUMID,
            new BaseTerrainPreset(
                64.0,           // minHeight
                116.0,          // maxHeight
                1.0 / 1600.0,   // lowFreq   很大尺度盆地
                16.0,           // lowAmp:   20 -> 16    （-20%）
                2,              // lowOctaves
                1.0 / 400.0,    // midFreq
                10.0,           // midAmp
                2,              // midOctaves
                1.0 / 96.0,     // highFreq
                3.0,            // highAmp
                1,              // highOctaves
                0.1,            // plateauStrength
                0.0,             // oceanDepthMax（陆地不用）
                2.5,            // cliffAmp
                48.0,            // cliffScale
                 0.3,            // terrace
                 0.8             // detailAmp
            )
        );

        // 热带干燥 / 草原 / 半干旱：整体略抬，低频稍减。
        m.put(MacroPackageId.TROPICAL_DRY,
            new BaseTerrainPreset(
                64.0,           // minHeight
                106.0,          // maxHeight
                1.0 / 1400.0,   // lowFreq
                16.0,           // lowAmp:    18 -> 16
                2,
                1.0 / 350.0,    // midFreq
                12.0,           // midAmp
                2,
                1.0 / 96.0,
                4.0,
                1,
                0.35,           // plateau 较明显（台地感）
                0.0,
                3.5,            // cliffAmp
                56.0,            // cliffScale
                 0.5,            // terrace
                 1.0             // detailAmp
            )
        );

        // ===== 温带 =====

        // 温带低地平原：整体抬高，低频振幅略减，减少大面积低洼；保持中高频不变，保留细节。
        m.put(MacroPackageId.TEMPERATE_LOWLAND,
            new BaseTerrainPreset(
                64.0,           // minHeight（平原谷底贴海平面但不淹没）
                94.0,           // maxHeight（平原不再上天）
                1.0 / 1600.0,
                14.0,           // lowAmp:    16 -> 14
                2,
                1.0 / 320.0,
                8.0,
                2,
                1.0 / 96.0,
                3.0,
                1,
                0.15,
                0.0,
                1.5,            // cliffAmp
                64.0,            // cliffScale
                 0.2,            // terrace
                 0.5             // detailAmp
            )
        );

        // 温带森林：整体略抬，减小一点低频深度。
        m.put(MacroPackageId.TEMPERATE_FORESTED,
            new BaseTerrainPreset(
                64.0,           // minHeight
                110.0,          // maxHeight
                1.0 / 1400.0,
                16.0,           // lowAmp:    18 -> 16
                2,
                1.0 / 260.0,
                11.0,
                2,
                1.0 / 80.0,
                4.0,
                1,
                0.2,
                0.0,
                3.0,            // cliffAmp
                48.0,            // cliffScale
                 0.35,            // terrace
                 0.8             // detailAmp
            )
        );

        // 温带高原 / 中山地：原本就比较高，只需略抬和略减低频。
        m.put(MacroPackageId.TEMPERATE_HIGHLAND,
            new BaseTerrainPreset(
                78.0,           // minHeight（高原谷底也保持较高）
                168.0,          // maxHeight（拉高）
                1.0 / 1200.0,
                22.0,           // lowAmp:    24 -> 22
                2,
                1.0 / 260.0,
                14.0,
                2,
                1.0 / 72.0,
                5.0,
                1,
                0.35,
                0.0,
                5.0,            // cliffAmp
                48.0,            // cliffScale
                 0.5,            // terrace
                 1.5             // detailAmp
            )
        );

        // ===== 凉爽 / 亚寒带 / 寒带 =====

        // 冷针叶林 / 过渡森林：参数和热带湿润接近，同样整体抬高、减弱低频深盆地。
        m.put(MacroPackageId.COOL_FORESTED,
            new BaseTerrainPreset(
                64.0,           // minHeight
                110.0,          // maxHeight
                1.0 / 1500.0,
                16.0,           // lowAmp:    20 -> 16
                2,
                1.0 / 280.0,
                10.0,
                2,
                1.0 / 80.0,
                3.0,
                1,
                0.2,
                0.0,
                3.5,            // cliffAmp
                56.0,            // cliffScale
                 0.4,            // terrace
                 1.0             // detailAmp
            )
        );

        // 亚极地冻原：原来整体偏低，这里抬得更多一点，仍保持起伏不大。
        m.put(MacroPackageId.SUBPOLAR_TUNDRA,
            new BaseTerrainPreset(
                64.0,           // minHeight
                88.0,           // maxHeight（低平冻原）
                1.0 / 1800.0,
                14.0,           // lowAmp:    16 -> 14
                1,
                1.0 / 360.0,
                7.0,
                1,
                1.0 / 96.0,
                2.0,
                1,
                0.05,
                0.0,
                2.0,            // cliffAmp
                72.0,            // cliffScale
                 0.25,            // terrace
                 0.5             // detailAmp
            )
        );

        // 高寒山地 + 极地荒漠：基本已满足不跌破海平面，这里只做轻微上调。
        m.put(MacroPackageId.POLAR_HIGHLAND,
            new BaseTerrainPreset(
                96.0,           // minHeight（山地谷底不再破 64）
                256.0,          // maxHeight（山地直接顶到世界高度）
                1.0 / 1300.0,
                28.0,
                2,
                1.0 / 220.0,
                18.0,
                2,
                1.0 / 64.0,
                5.0,
                1,
                0.4,
                0.0,
                6.0,            // cliffAmp
                40.0,            // cliffScale
                 0.5,            // terrace
                 1.5             // detailAmp
            )
        );

        // ===== 裂谷 / 峡谷（板块分离带注入，干裂谷：不低于海平面） =====
        m.put(MacroPackageId.RIFT_TROPICAL,
            new BaseTerrainPreset(
                64.0,           // minHeight（不再低于海平面）
                92.0,           // maxHeight
                1.0 / 1400.0,
                10.0,
                2,
                1.0 / 400.0,
                6.0,
                2,
                1.0 / 96.0,
                2.0,
                1,
                0.1,
                0.0,
                2.0,            // cliffAmp
                48.0,            // cliffScale
                 0.4,            // terrace
                 1.0             // detailAmp
            )
        );

        m.put(MacroPackageId.RIFT_TEMPERATE,
            new BaseTerrainPreset(
                64.0,
                88.0,
                1.0 / 1500.0,
                10.0,
                2,
                1.0 / 350.0,
                6.0,
                2,
                1.0 / 96.0,
                2.0,
                1,
                0.1,
                0.0,
                2.0,            // cliffAmp
                48.0,            // cliffScale
                 0.4,            // terrace
                 1.0             // detailAmp
            )
        );

        m.put(MacroPackageId.RIFT_POLAR,
            new BaseTerrainPreset(
                64.0,
                84.0,
                1.0 / 1700.0,
                9.0,
                2,
                1.0 / 400.0,
                5.0,
                2,
                1.0 / 96.0,
                2.0,
                1,
                0.05,
                0.0,
                2.0,            // cliffAmp
                48.0,            // cliffScale
                 0.4,            // terrace
                 1.0             // detailAmp
            )
        );

        // ===== 最高峰（挤压带核心注入）：顶到世界高度，高频细节强调山脊破碎感 =====
        m.put(MacroPackageId.MOUNTAIN_PEAK,
            new BaseTerrainPreset(
                110.0,          // minHeight（峰带谷底也远高于普通山脉）
                256.0,          // maxHeight（顶到世界高度）
                1.0 / 1400.0,
                16.0,
                2,
                1.0 / 260.0,
                12.0,
                2,
                1.0 / 72.0,
                6.0,
                1,
                0.15,
                0.0,
                0.0,            // cliffAmp
                1.0,            // cliffScale
                 0.0,            // terrace
                 0.0             // detailAmp
            )
        );

        PRESETS = Collections.unmodifiableMap(m);
    }

    private TerrainMacroPresetRegistry() {}

    public static BaseTerrainPreset get(MacroPackageId id) {
        BaseTerrainPreset p = PRESETS.get(id);
        if (p == null) {
            // fallback：用温带平原则的 preset，避免 NPE
            return PRESETS.getOrDefault(MacroPackageId.TEMPERATE_LOWLAND,
                new BaseTerrainPreset(
                    64.0,
                    94.0,
                    1.0 / 1600.0, 14.0, 2,
                    1.0 / 320.0,  8.0,  2,
                    1.0 / 96.0,   3.0,  1,
                    0.15,
                    0.0,
                    1.5,            // cliffAmp
                    64.0,            // cliffScale
                 0.2,            // terrace
                 0.5             // detailAmp
                )
            );
        }
        return p;
    }
}
