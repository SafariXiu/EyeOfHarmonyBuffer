package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer;

import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.MacroPackageId;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 每个 MacroPackageId 对应的一组「宏群系配置」。
 *
 * 当前只包含：
 * - RiverStylePreset：河流风格预设（宽度 + 弯曲风格）
 *
 * 后续如果需要，可以在 MacroPackageSpec 里继续加：
 * - macroParams 预设
 * - biome 列表 / 权重
 * 等内容。
 */

public final class MacroPackageRegistry {

    private static final Map<MacroPackageId, MacroPackageSpec> SPECS;

    static {
        EnumMap<MacroPackageId, MacroPackageSpec> m =
            new EnumMap<>(MacroPackageId.class);

        // ===== 热带 / 亚热带 =====
        // 热带雨林 + 湿热盆地：大水量、宽谷、弯曲很强，波长中等偏长
        m.put(MacroPackageId.TROPICAL_HUMID,
            MacroPackageSpec.builder(MacroPackageId.TROPICAL_HUMID)
                .riverStyle(new RiverStylePreset(
                    56,
                    320,
                    460,
                    0.9,
                    1200
                ))
                .build()
        );

        // 热带干燥 / 草原 / 半干旱：河道略窄一点，弯曲中等偏强
        m.put(MacroPackageId.TROPICAL_DRY,
            MacroPackageSpec.builder(MacroPackageId.TROPICAL_DRY)
                .riverStyle(new RiverStylePreset(
                    40,
                    260,
                    380,
                    0.7,
                    1100
                ))
                .build()
        );

        // ===== 温带 =====
        // 温带低地 / 平原：经典“大河 + 冲积平原”，宽度和弯曲都中等
        m.put(MacroPackageId.TEMPERATE_LOWLAND,
            MacroPackageSpec.builder(MacroPackageId.TEMPERATE_LOWLAND)
                .riverStyle(new RiverStylePreset(
                    40,
                    240,
                    360,
                    0.6,
                    1000
                ))
                .build()
        );

        // 温带森林：和低地接近，但略多弯一点
        m.put(MacroPackageId.TEMPERATE_FORESTED,
            MacroPackageSpec.builder(MacroPackageId.TEMPERATE_FORESTED)
                .riverStyle(new RiverStylePreset(
                    36,
                    230,
                    340,
                    0.65,
                    950
                ))
                .build()
        );

        // 温带高原 / 中等山地：谷地更窄，弯曲中等，波长稍短（峡谷感更强）
        m.put(MacroPackageId.TEMPERATE_HIGHLAND,
            MacroPackageSpec.builder(MacroPackageId.TEMPERATE_HIGHLAND)
                .riverStyle(new RiverStylePreset(
                    28,
                    190,
                    300,
                    0.5,
                    800
                ))
                .build()
        );

        // ===== 凉爽 / 亚寒带 / 寒带 =====
        // 冷针叶林 / 过渡森林：河道整体偏窄，弯曲适中
        m.put(MacroPackageId.COOL_FORESTED,
            MacroPackageSpec.builder(MacroPackageId.COOL_FORESTED)
                .riverStyle(new RiverStylePreset(
                    28,
                    180,
                    280,
                    0.45,
                    850
                ))
                .build()
        );

        // 亚极地冻原：河更直一些，谷地不算很宽
        m.put(MacroPackageId.SUBPOLAR_TUNDRA,
            MacroPackageSpec.builder(MacroPackageId.SUBPOLAR_TUNDRA)
                .riverStyle(new RiverStylePreset(
                    24,
                    160,
                    260,
                    0.35,
                    900
                ))
                .build()
        );

        // 高寒山地 + 极地荒漠：峡谷窄、河很直，几乎只做大尺度方向变化
        m.put(MacroPackageId.POLAR_HIGHLAND,
            MacroPackageSpec.builder(MacroPackageId.POLAR_HIGHLAND)
                .riverStyle(new RiverStylePreset(
                    20,
                    140,
                    240,
                    0.2,
                    900
                ))
                .build()
        );

        SPECS = Collections.unmodifiableMap(m);
    }

    private MacroPackageRegistry() {}

    public static MacroPackageSpec get(MacroPackageId id) {
        MacroPackageSpec spec = SPECS.get(id);
        if (spec == null) {
            throw new IllegalArgumentException("No MacroPackageSpec for id: " + id);
        }
        return spec;
    }

    /**
     * 以后可以继续在这里挂更多宏观参数（macroParams、biome 列表等）。
     */
    public static final class MacroPackageSpec {

        private final MacroPackageId id;
        private final RiverStylePreset riverStyle;

        private MacroPackageSpec(Builder b) {
            this.id = b.id;
            this.riverStyle = b.riverStyle;
        }

        public MacroPackageId id() {
            return id;
        }

        public RiverStylePreset riverStyle() {
            return riverStyle;
        }

        public static Builder builder(MacroPackageId id) {
            return new Builder(id);
        }

        public static final class Builder {
            private final MacroPackageId id;
            private RiverStylePreset riverStyle;

            private Builder(MacroPackageId id) {
                this.id = id;
            }

            public Builder riverStyle(RiverStylePreset preset) {
                this.riverStyle = preset;
                return this;
            }

            public MacroPackageSpec build() {
                if (riverStyle == null) {
                    riverStyle = new RiverStylePreset(
                        32, 200, 300,
                        0.5, 900
                    );
                }
                return new MacroPackageSpec(this);
            }
        }
    }

    /**
     * 单个宏群系的河流风格预设。
     *
     * 注意：
     * - 这些是「宏观基值」，真正落地时可以在层2 再叠一点噪声，
     *   然后按 packageWeights(x,z) 做线性混合，得到最终的 RiverStyleParams(x,z)。
     */
    public static final class RiverStylePreset {

        /** 河道核心（真正装水的部分）目标宽度（blocks，整条河的大致标尺） */
        public final int coreWidthBlocks;

        /** 河谷总体宽度（从谷底到两侧山脚的半宽标尺） */
        public final int valleyWidthBlocks;

        /** 山体/洞穴等需要避让的宽度（比 valley 更宽） */
        public final int avoidWidthBlocks;

        /**
         * 弯曲强度：0 = 完全不 meander（除了功能性寻路），
         * 1 = 极强 meander。
         */
        public final double meanderStrength;

        /**
         * 典型弯曲波长（blocks），控制「S 型」弯道的大致尺度。
         */
        public final int meanderWavelengthBlocks;

        public RiverStylePreset(int coreWidthBlocks,
                                int valleyWidthBlocks,
                                int avoidWidthBlocks,
                                double meanderStrength,
                                int meanderWavelengthBlocks) {
            this.coreWidthBlocks = coreWidthBlocks;
            this.valleyWidthBlocks = valleyWidthBlocks;
            this.avoidWidthBlocks = avoidWidthBlocks;
            this.meanderStrength = meanderStrength;
            this.meanderWavelengthBlocks = meanderWavelengthBlocks;
        }

        @Override
        public String toString() {
            return "RiverStylePreset{" +
                "core=" + coreWidthBlocks +
                ", valley=" + valleyWidthBlocks +
                ", avoid=" + avoidWidthBlocks +
                ", meanderStrength=" + meanderStrength +
                ", wavelength=" + meanderWavelengthBlocks +
                '}';
        }
    }
}
