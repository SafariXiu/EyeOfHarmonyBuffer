package com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer;

import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.MacroPackageId;

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
        // 深海 + 陆架：baseHeight 略低于 seaLevel，低频 1 层 + 中频很弱，高频关掉。
        m.put(MacroPackageId.OCEANIC,
            new BaseTerrainPreset(
                30.0,           // baseHeight
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
                64.0            // oceanDepthMax：最深约 seaLevel - 64
            )
        );

        // ===== 热带 / 亚热带 =====

        // 热带湿润盆地：整体抬高一些，低频振幅略减，减少大面积深盆地。
        m.put(MacroPackageId.TROPICAL_HUMID,
            new BaseTerrainPreset(
                82.0,           // baseHeight: 70 -> 82  （+12）
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
                0.0             // oceanDepthMax（陆地不用）
            )
        );

        // 热带干燥 / 草原 / 半干旱：整体略抬，低频稍减。
        m.put(MacroPackageId.TROPICAL_DRY,
            new BaseTerrainPreset(
                82.0,           // baseHeight: 76 -> 82  （+6）
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
                0.0
            )
        );

        // ===== 温带 =====

        // 温带低地平原：整体抬高，低频振幅略减，减少大面积低洼；保持中高频不变，保留细节。
        m.put(MacroPackageId.TEMPERATE_LOWLAND,
            new BaseTerrainPreset(
                80.0,           // baseHeight: 72 -> 80  （+8）
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
                0.0
            )
        );

        // 温带森林：整体略抬，减小一点低频深度。
        m.put(MacroPackageId.TEMPERATE_FORESTED,
            new BaseTerrainPreset(
                82.0,           // baseHeight: 76 -> 82  （+6）
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
                0.0
            )
        );

        // 温带高原 / 中山地：原本就比较高，只需略抬和略减低频。
        m.put(MacroPackageId.TEMPERATE_HIGHLAND,
            new BaseTerrainPreset(
                88.0,           // baseHeight: 84 -> 88  （+4）
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
                0.0
            )
        );

        // ===== 凉爽 / 亚寒带 / 寒带 =====

        // 冷针叶林 / 过渡森林：参数和热带湿润接近，同样整体抬高、减弱低频深盆地。
        m.put(MacroPackageId.COOL_FORESTED,
            new BaseTerrainPreset(
                82.0,           // baseHeight: 76 -> 82  （+6）
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
                0.0
            )
        );

        // 亚极地冻原：原来整体偏低，这里抬得更多一点，仍保持起伏不大。
        m.put(MacroPackageId.SUBPOLAR_TUNDRA,
            new BaseTerrainPreset(
                78.0,           // baseHeight: 68 -> 78  （+10）
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
                0.0
            )
        );

        // 高寒山地 + 极地荒漠：基本已满足不跌破海平面，这里只做轻微上调。
        m.put(MacroPackageId.POLAR_HIGHLAND,
            new BaseTerrainPreset(
                94.0,           // baseHeight: 92 -> 94  （+2）
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
                0.0
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
                    80.0,
                    1.0 / 1600.0, 14.0, 2,
                    1.0 / 320.0,  8.0,  2,
                    1.0 / 96.0,   3.0,  1,
                    0.15,
                    0.0
                )
            );
        }
        return p;
    }
}
