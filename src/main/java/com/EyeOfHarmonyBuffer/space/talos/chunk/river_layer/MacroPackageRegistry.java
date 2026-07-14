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
                    1.4,  // flatWidthFactor：洪泛平原半宽 ≈ 1.4 * coreWidth/2
                    0.7,  // slopeSteepness：整体坡比较缓
                    0.1,  // cliffRetainFactor：几乎不保留原始悬崖
                    0.6,  // noiseRoughness：岸线起伏比较丰富
                    0.2   // terraceStrength：台地感弱一点
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
                    1.0,  // 郊外冲沟 / 河谷，洪泛平原和 core 差不多宽
                    0.9,  // 坡比湿热盆地略陡
                    0.3,  // 保留一点崖感
                    0.7,  // 草原 / 半干旱，岸线可以更破碎一点
                    0.3   // 有一点点阶地
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
                    1.2,  // 比 core 再宽一圈的洪泛平原
                    0.8,  // 坡偏缓
                    0.2,  // 只在个别地方有一点崖
                    0.5,  // 岸线起伏中等
                    0.3   // 冲积平原里会有一点阶地 / 老河道痕迹
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
                    1.0,  // 洪泛平原刚好包 core，一圈不算很宽
                    0.9,  // 坡略陡一点
                    0.35, // 森林峡谷感稍强，崖感多一点
                    0.6,  // 岸线起伏略强
                    0.35  // 阶地感稍明显
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
                    0.6,  // 河边只有一小圈相对平的岸
                    1.3,  // 坡明显更陡
                    0.8,  // 多数地方保留原始峭壁
                    0.8,  // 高原河谷，岸线起伏强
                    0.4   // 峡谷两侧可以有一点阶地
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
                    0.8,  // 稍窄的平岸
                    1.1,  // 坡比温带森林更陡一些
                    0.65, // 明显保留崖感
                    0.7,  // 冷凉地区谷坡起伏明显
                    0.4   // 有一定阶地 / 冰缘地貌感
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
                    0.7,  // 不太宽的平岸
                    1.0,  // 坡中等偏直
                    0.5,  // 崖感中等
                    0.6,  // 岸线起伏一般
                    0.25  // 阶地感略有，但不抢戏
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
                    0.4,  // 非常窄的平岸
                    1.5,  // 坡很陡
                    0.9,  // 大部分保持悬崖 / 峡谷感
                    0.9,  // 起伏剧烈
                    0.3   // 偶尔有冰缘阶地
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
                    // 一个比较中性的默认河岸：适中的洪泛平原、坡度、少量崖感
                    riverBank = new RiverBankPreset(
                        1.0,  // flatWidthFactor
                        1.0,  // slopeSteepness
                        0.4,  // cliffRetainFactor
                        0.6,  // noiseRoughness
                        0.3   // terraceStrength
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
     * 河岸 / 河谷形态预设。
     *
     * 这些参数一般在「宏群系层」定义，真正落地时可以：
     * - 先按 packageWeights(x,z) 做线性混合，得到宏观 bank 参数；
     * - 然后在 chunk 层叠局部噪声，做出更细节的河岸形状。
     */
    public record RiverBankPreset(
        double flatWidthFactor,   // 洪泛平原半宽 / coreWidthBlocks
        double slopeSteepness,    // 坡度强度 0.0~2.0，越大越陡
        double cliffRetainFactor, // 保留原始崖高比例 0.0~1.0
        double noiseRoughness,    // 岸线起伏粗糙度
        double terraceStrength    // 河岸台地/阶地感
    ) {}
}
