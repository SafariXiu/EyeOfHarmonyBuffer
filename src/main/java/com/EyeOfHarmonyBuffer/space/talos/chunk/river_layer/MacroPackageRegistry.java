package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer;

import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.api.MacroPackageId;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 每个 MacroPackageId 对应的一组「宏群系配置」。
 *
 * 当前只包含：
 * - RiverStylePreset：河流风格预设（深度 + 谷型）
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
        m.put(MacroPackageId.TROPICAL_HUMID,
            MacroPackageSpec.builder(MacroPackageId.TROPICAL_HUMID)
                .riverStyle(new RiverStylePreset(
                    22,
                    0.5,
                    RiverValleyType.U_SHAPED
                ))
                // 宽洪泛平原 + 坡很缓 → 强烈河岸压低
                .riverBank(new RiverBankPreset(
                    0.9   // bankIntensity
                ))
                .build()
        );

        // 热带干燥 / 草原 / 半干旱：洪泛平原略收、坡略陡
        m.put(MacroPackageId.TROPICAL_DRY,
            MacroPackageSpec.builder(MacroPackageId.TROPICAL_DRY)
                .riverStyle(new RiverStylePreset(
                    18,
                    0.5,
                    RiverValleyType.V_SHAPED
                ))
                .riverBank(new RiverBankPreset(
                    0.7
                ))
                .build()
        );

        // ===== 温带 =====
        // 温带低地 / 平原：典型大河 + 冲积平原
        m.put(MacroPackageId.TEMPERATE_LOWLAND,
            MacroPackageSpec.builder(MacroPackageId.TEMPERATE_LOWLAND)
                .riverStyle(new RiverStylePreset(
                    18,
                    0.5,
                    RiverValleyType.U_SHAPED
                ))
                .riverBank(new RiverBankPreset(
                    0.8   // 比 TROPICAL_DRY 略高一点，平原河谷更软
                ))
                .build()
        );

        // 温带森林：比低地略收、坡略陡
        m.put(MacroPackageId.TEMPERATE_FORESTED,
            MacroPackageSpec.builder(MacroPackageId.TEMPERATE_FORESTED)
                .riverStyle(new RiverStylePreset(
                    18,
                    0.5,
                    RiverValleyType.U_SHAPED
                ))
                .riverBank(new RiverBankPreset(
                    0.65
                ))
                .build()
        );

        // 温带高原 / 中等山地：洪泛平原很窄，多峡谷坡 / 崖
        m.put(MacroPackageId.TEMPERATE_HIGHLAND,
            MacroPackageSpec.builder(MacroPackageId.TEMPERATE_HIGHLAND)
                .riverStyle(new RiverStylePreset(
                    16,
                    0.55,
                    RiverValleyType.V_SHAPED
                ))
                .riverBank(new RiverBankPreset(
                    0.35  // 比上面明显更硬
                ))
                .build()
        );

        // ===== 凉爽 / 亚寒带 / 寒带 =====
        // 冷针叶林 / 过渡森林：比温带森林再“硬”一点
        m.put(MacroPackageId.COOL_FORESTED,
            MacroPackageSpec.builder(MacroPackageId.COOL_FORESTED)
                .riverStyle(new RiverStylePreset(
                    16,
                    0.55,
                    RiverValleyType.V_SHAPED
                ))
                .riverBank(new RiverBankPreset(
                    0.45
                ))
                .build()
        );

        // 亚极地冻原：平岸宽度一般，坡偏直上直下
        m.put(MacroPackageId.SUBPOLAR_TUNDRA,
            MacroPackageSpec.builder(MacroPackageId.SUBPOLAR_TUNDRA)
                .riverStyle(new RiverStylePreset(
                    16,
                    0.55,
                    RiverValleyType.V_SHAPED
                ))
                .riverBank(new RiverBankPreset(
                    0.4
                ))
                .build()
        );

        // 高寒山地 + 极地荒漠：洪泛平原极窄，坡很陡
        m.put(MacroPackageId.POLAR_HIGHLAND,
            MacroPackageSpec.builder(MacroPackageId.POLAR_HIGHLAND)
                .riverStyle(new RiverStylePreset(
                    14,
                    0.6,
                    RiverValleyType.V_SHAPED
                ))
                .riverBank(new RiverBankPreset(
                    0.2
                ))
                .build()
        );

        // ===== 裂谷 / 峡谷：窄 V 型浅河 + 干裂谷河岸泛洪平原 =====
        // 裂谷谷底现在是干燥的（高于海平面），给河道配上可见的泛洪平原，
        // 让河在谷底有自己的平缓河岸带；纬度越低泛洪平原越宽。
        m.put(MacroPackageId.RIFT_TROPICAL,
            MacroPackageSpec.builder(MacroPackageId.RIFT_TROPICAL)
                .riverStyle(new RiverStylePreset(
                    8,
                    0.6,
                    RiverValleyType.V_SHAPED
                ))
                .riverBank(new RiverBankPreset(
                    0.6
                ))
                .build()
        );

        m.put(MacroPackageId.RIFT_TEMPERATE,
            MacroPackageSpec.builder(MacroPackageId.RIFT_TEMPERATE)
                .riverStyle(new RiverStylePreset(
                    8,
                    0.6,
                    RiverValleyType.V_SHAPED
                ))
                .riverBank(new RiverBankPreset(
                    0.55
                ))
                .build()
        );

        m.put(MacroPackageId.RIFT_POLAR,
            MacroPackageSpec.builder(MacroPackageId.RIFT_POLAR)
                .riverStyle(new RiverStylePreset(
                    8,
                    0.6,
                    RiverValleyType.V_SHAPED
                ))
                .riverBank(new RiverBankPreset(
                    0.45
                ))
                .build()
        );

        // ===== 最高峰：几乎无河，极窄极陡 =====
        m.put(MacroPackageId.MOUNTAIN_PEAK,
            MacroPackageSpec.builder(MacroPackageId.MOUNTAIN_PEAK)
                .riverStyle(new RiverStylePreset(
                    10,
                    0.6,
                    RiverValleyType.V_SHAPED
                ))
                .riverBank(new RiverBankPreset(
                    0.15
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
                        16,
                        0.5,
                        RiverValleyType.U_SHAPED
                    );
                }
                if (riverBank == null) {
                    // 一个比较中性的默认河岸：在 0.7~0.8 区间做压低
                    riverBank = new RiverBankPreset(
                        0.5
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
     * 注意：河道宽度 / 弯曲由 .rvr 河网模板决定，宏包只控制深度与谷型。
     */
    public static final class RiverStylePreset {
        /** 主河典型最大下挖深度（blocks） */
        public final int baseDepthBlocks;

        /** 支流深度缩放（每升一级乘的系数，0.0~1.0） */
        public final double tributaryDepthScale;

        /** 河谷类型  */
        public final RiverValleyType riverValleyType;

        public RiverStylePreset(int baseDepthBlocks,
                                double tributaryDepthScale,
                                RiverValleyType riverValleyType) {
            this.baseDepthBlocks = baseDepthBlocks;
            this.tributaryDepthScale = tributaryDepthScale;
            this.riverValleyType = riverValleyType;
        }

        @Override
        public String toString() {
            return "RiverStylePreset{" +
                "baseDepth=" + baseDepthBlocks +
                ", tribScale=" + tributaryDepthScale +
                ", riverValleyTypes=" + riverValleyType +
                '}';
        }
    }

    /**
     * 河岸 / 河谷形态预设（单参数版）。
     *
     * 设计目标：
     *   - 对外只暴露一个连续参数 bankIntensity ∈ [0,1]：
     *       0   = 几乎不做河岸压低（源自老 preset 中「阈值靠近 1」的那类）
     *       1   = 河岸压低很强、洪泛平原很宽、斜坡过渡也比较宽
     *
     *   - 具体「平的这截有多宽」「坡有多宽」都在 TalosRiverTerrainModifier 里
     *     用统一的公式从 bankIntensity 推导出来；这样可以保证宏群系跨越时
     *     只要 bankIntensity 在空间是连续的，阈值和河岸形状也天然连续。
     */
    public record RiverBankPreset(
        double bankIntensity
    ) {}
}
