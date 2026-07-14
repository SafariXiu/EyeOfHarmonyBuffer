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
 * - RiverBankPreset：河岸 / 河谷形态预设（洪泛平原宽度、坡度、崖感等）
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
        // -> 宽洪泛平原，坡比较缓，基本没有悬崖
        m.put(MacroPackageId.TROPICAL_HUMID,
            MacroPackageSpec.builder(MacroPackageId.TROPICAL_HUMID)
                .riverStyle(new RiverStylePreset(
                    56,
                    320,
                    460,
                    0.9,
                    1200,
                    22,
                    0.5,
                    RiverValleyType.U_SHAPED
                ))
                .riverBank(new RiverBankPreset(
                    0.3,
                    0.6
                ))
                .build()
        );

        // 热带干燥 / 草原 / 半干旱：河道略窄一点，弯曲中等偏强
        // -> 洪泛平原稍微收一点，坡略陡，偶尔有一点崖感
        m.put(MacroPackageId.TROPICAL_DRY,
            MacroPackageSpec.builder(MacroPackageId.TROPICAL_DRY)
                .riverStyle(new RiverStylePreset(
                    40,
                    260,
                    380,
                    0.7,
                    1100,
                    18,
                    0.5,
                    RiverValleyType.V_SHAPED
                ))
                .riverBank(new RiverBankPreset(
                    0.4,
                    0.7
                ))
                .build()
        );

        // ===== 温带 =====
        // 温带低地 / 平原：经典“大河 + 冲积平原”，宽度和弯曲都中等
        // -> 典型大洪泛平原，坡适中偏缓，崖感不强
        m.put(MacroPackageId.TEMPERATE_LOWLAND,
            MacroPackageSpec.builder(MacroPackageId.TEMPERATE_LOWLAND)
                .riverStyle(new RiverStylePreset(
                    40,
                    240,
                    360,
                    0.6,
                    1000,
                    18,
                    0.5,
                    RiverValleyType.U_SHAPED
                ))
                .riverBank(new RiverBankPreset(
                    0.35,
                    0.65
                ))
                .build()
        );

        // 温带森林：和低地接近，但略多弯一点
        // -> 河岸比低地略收、坡略陡，崖稍多一点，整体更“切”进森林
        m.put(MacroPackageId.TEMPERATE_FORESTED,
            MacroPackageSpec.builder(MacroPackageId.TEMPERATE_FORESTED)
                .riverStyle(new RiverStylePreset(
                    36,
                    230,
                    340,
                    0.65,
                    950,
                    18,
                    0.5,
                    RiverValleyType.U_SHAPED
                ))
                .riverBank(new RiverBankPreset(
                    0.45,
                    0.8
                ))
                .build()
        );

        // 温带高原 / 中等山地：谷地更窄，弯曲中等，波长稍短（峡谷感更强）
        // -> 洪泛平原很窄，多是峡谷坡 / 崖
        m.put(MacroPackageId.TEMPERATE_HIGHLAND,
            MacroPackageSpec.builder(MacroPackageId.TEMPERATE_HIGHLAND)
                .riverStyle(new RiverStylePreset(
                    28,
                    190,
                    300,
                    0.5,
                    800,
                    16,
                    0.55,
                    RiverValleyType.V_SHAPED
                ))
                .riverBank(new RiverBankPreset(
                    0.7,
                    0.9
                ))
                .build()
        );

        // ===== 凉爽 / 亚寒带 / 寒带 =====
        // 冷针叶林 / 过渡森林：河道整体偏窄，弯曲适中
        // -> 比温带森林再“硬”一点：洪泛平原不太宽，坡较陡，崖较多
        m.put(MacroPackageId.COOL_FORESTED,
            MacroPackageSpec.builder(MacroPackageId.COOL_FORESTED)
                .riverStyle(new RiverStylePreset(
                    28,
                    180,
                    280,
                    0.45,
                    850,
                    16,
                    0.55,
                    RiverValleyType.V_SHAPED
                ))
                .riverBank(new RiverBankPreset(
                    0.55,
                    0.85
                ))
                .build()
        );

        // 亚极地冻原：河更直一些，谷地不算很宽
        // -> 平岸宽度一般，坡偏直上直下，但整体 relief 没高寒山那么夸张
        m.put(MacroPackageId.SUBPOLAR_TUNDRA,
            MacroPackageSpec.builder(MacroPackageId.SUBPOLAR_TUNDRA)
                .riverStyle(new RiverStylePreset(
                    24,
                    160,
                    260,
                    0.35,
                    900,
                    16,
                    0.55,
                    RiverValleyType.V_SHAPED
                ))
                .riverBank(new RiverBankPreset(
                    0.6,
                    0.9
                ))
                .build()
        );

        // 高寒山地 + 极地荒漠：峡谷窄、河很直，几乎只做大尺度方向变化
        // -> 洪泛平原极窄，坡很陡，基本是大峡谷 / 冰川切割谷
        m.put(MacroPackageId.POLAR_HIGHLAND,
            MacroPackageSpec.builder(MacroPackageId.POLAR_HIGHLAND)
                .riverStyle(new RiverStylePreset(
                    20,
                    140,
                    240,
                    0.2,
                    900,
                    14,
                    0.6,
                    RiverValleyType.V_SHAPED
                ))
                .riverBank(new RiverBankPreset(
                    0.8,
                    0.95
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
        private final RiverBankPreset riverBank;

        private MacroPackageSpec(Builder b) {
            this.id = b.id;
            this.riverStyle = b.riverStyle;
            this.riverBank = b.riverBank;
        }

        public MacroPackageId id() {
            return id;
        }

        public RiverStylePreset riverStyle() {
            return riverStyle;
        }

        public RiverBankPreset riverBank() {
            return riverBank;
        }

        public static Builder builder(MacroPackageId id) {
            return new Builder(id);
        }

        public static final class Builder {
            private final MacroPackageId id;
            private RiverStylePreset riverStyle;
            private RiverBankPreset riverBank;

            private Builder(MacroPackageId id) {
                this.id = id;
            }

            public Builder riverStyle(RiverStylePreset preset) {
                this.riverStyle = preset;
                return this;
            }

            public Builder riverBank(RiverBankPreset preset) {
                this.riverBank = preset;
                return this;
            }

            public MacroPackageSpec build() {
                if (riverStyle == null) {
                    riverStyle = new RiverStylePreset(
                        32, 200, 300,
                        0.5, 900,
                        16,
                        0.5,
                        RiverValleyType.U_SHAPED
                    );
                }
                if (riverBank == null) {
                    // 一个比较中性的默认河岸：在 0.7~0.8 区间做压低
                    riverBank = new RiverBankPreset(
                        0.7,
                        0.8
                    );
                }
                return new MacroPackageSpec(this);
            }
        }
    }

    public enum RiverValleyType {
        U_SHAPED,
        V_SHAPED
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

        /** 主河典型最大下挖深度（blocks） */
        public final int baseDepthBlocks;

        /** 支流深度缩放（每升一级乘的系数，0.0~1.0） */
        public final double tributaryDepthScale;

        /** 河谷类型  */
        public final RiverValleyType riverValleyType;

        public RiverStylePreset(int coreWidthBlocks,
                                int valleyWidthBlocks,
                                int avoidWidthBlocks,
                                double meanderStrength,
                                int meanderWavelengthBlocks,
                                int baseDepthBlocks,
                                double tributaryDepthScale,
                                RiverValleyType riverValleyType) {
            this.coreWidthBlocks = coreWidthBlocks;
            this.valleyWidthBlocks = valleyWidthBlocks;
            this.avoidWidthBlocks = avoidWidthBlocks;
            this.meanderStrength = meanderStrength;
            this.meanderWavelengthBlocks = meanderWavelengthBlocks;
            this.baseDepthBlocks = baseDepthBlocks;
            this.tributaryDepthScale = tributaryDepthScale;
            this.riverValleyType = riverValleyType;
        }

        @Override
        public String toString() {
            return "RiverStylePreset{" +
                "core=" + coreWidthBlocks +
                ", valley=" + valleyWidthBlocks +
                ", avoid=" + avoidWidthBlocks +
                ", meanderStrength=" + meanderStrength +
                ", wavelength=" + meanderWavelengthBlocks +
                ", baseDepth=" + baseDepthBlocks +
                ", tribScale=" + tributaryDepthScale +
                ", riverValleyTypes=" + riverValleyType +
                '}';
        }
    }

    /**
     * 河岸 / 河谷形态预设（河岸压低阈值）。
     *
     * smoothThreshold：开始从 baseHeight 平滑过渡到河面高度的 riverMask；
     * flatThreshold：完全压到河面高度的 riverMask；
     * 约定 smoothThreshold < flatThreshold，riverMask 越靠近河心越大。
     */
    public record RiverBankPreset(
        double smoothThreshold,
        double flatThreshold
    ) {}
}
