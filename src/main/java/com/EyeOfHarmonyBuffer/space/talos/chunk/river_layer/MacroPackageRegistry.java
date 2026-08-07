package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer;

import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.api.MacroPackageId;
import micdoodle8.mods.galacticraft.api.prefab.core.BlockMetaPair;
import net.minecraft.init.Blocks;

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

    private static final BlockMetaPair SAND = new BlockMetaPair(Blocks.sand, (byte) 0);
    private static final BlockMetaPair DIRT = new BlockMetaPair(Blocks.dirt, (byte) 0);
    private static final BlockMetaPair GRAVEL = new BlockMetaPair(Blocks.gravel, (byte) 0);
    private static final BlockMetaPair CLAY = new BlockMetaPair(Blocks.clay, (byte) 0);
    private static final BlockMetaPair PACKED_ICE =
        new BlockMetaPair(Blocks.packed_ice, (byte) 0);

    /** 默认源头湖预设（未配置时使用，保持旧版湖盆观感并新增岸边/滩涂）。 */
    private static final SourceLakePreset DEFAULT_SOURCE_LAKE = new SourceLakePreset(
        48.0, 16.0, 32.0, 0.18, 0.18,
        24.0, 1.5, 48.0,
        SAND, DIRT
    );

    /** 默认河床底料：砂砾 50% / 沙子 30% / 黏土 20%，深 1~3 格，斑块尺度 48 格。 */
    private static final RiverbedPreset DEFAULT_RIVERBED = new RiverbedPreset(
        new BlockMetaPair[] { GRAVEL, SAND, CLAY },
        new double[] { 0.5, 0.3, 0.2 },
        1, 3, 48.0
    );

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
                // 湿润热带：更宽的湖岸滩涂
                .sourceLake(new SourceLakePreset(
                    48.0, 16.0, 32.0, 0.18, 0.18,
                    32.0, 1.5, 48.0,
                    SAND, DIRT
                ))
                // 宽洪泛平原 + 坡很缓 → 强烈河岸压低
                .riverBank(new RiverBankPreset(
                    0.9   // bankIntensity
                ))
                // 湿润热带：沙/砂砾混合河底
                .riverbed(new RiverbedPreset(
                    new BlockMetaPair[] { SAND, GRAVEL, CLAY },
                    new double[] { 0.4, 0.4, 0.2 },
                    1, 3, 48.0
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
                // 干燥带：沙质河底
                .riverbed(new RiverbedPreset(
                    new BlockMetaPair[] { SAND, GRAVEL },
                    new double[] { 0.7, 0.3 },
                    1, 3, 48.0
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
                // 山地：砂砾为主的河底
                .riverbed(new RiverbedPreset(
                    new BlockMetaPair[] { GRAVEL, CLAY },
                    new double[] { 0.7, 0.3 },
                    1, 3, 48.0
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
                // 高寒：砾石岸 + 窄滩
                .sourceLake(new SourceLakePreset(
                    40.0, 12.0, 28.0, 0.18, 0.15,
                    16.0, 1.5, 40.0,
                    GRAVEL, GRAVEL
                ))
                .riverBank(new RiverBankPreset(
                    0.2
                ))
                // 高寒：砂砾 + 浮冰河底
                .riverbed(new RiverbedPreset(
                    new BlockMetaPair[] { GRAVEL, PACKED_ICE },
                    new double[] { 0.7, 0.3 },
                    1, 3, 48.0
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
                // 裂谷：湖更小更浅
                .sourceLake(new SourceLakePreset(
                    28.0, 10.0, 20.0, 0.18, 0.18,
                    14.0, 1.0, 28.0,
                    SAND, DIRT
                ))
                .riverBank(new RiverBankPreset(
                    0.6
                ))
                // 裂谷：砂砾河底
                .riverbed(new RiverbedPreset(
                    new BlockMetaPair[] { GRAVEL, SAND },
                    new double[] { 0.6, 0.4 },
                    1, 3, 48.0
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
                .sourceLake(new SourceLakePreset(
                    28.0, 10.0, 20.0, 0.18, 0.18,
                    14.0, 1.0, 28.0,
                    SAND, DIRT
                ))
                .riverBank(new RiverBankPreset(
                    0.55
                ))
                .riverbed(new RiverbedPreset(
                    new BlockMetaPair[] { GRAVEL, SAND },
                    new double[] { 0.6, 0.4 },
                    1, 3, 48.0
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
                .sourceLake(new SourceLakePreset(
                    28.0, 10.0, 20.0, 0.18, 0.18,
                    14.0, 1.0, 28.0,
                    GRAVEL, GRAVEL
                ))
                .riverBank(new RiverBankPreset(
                    0.45
                ))
                // 极地裂谷：砂砾 + 浮冰
                .riverbed(new RiverbedPreset(
                    new BlockMetaPair[] { GRAVEL, PACKED_ICE },
                    new double[] { 0.8, 0.2 },
                    1, 3, 48.0
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
        private final SourceLakePreset sourceLake;
        private final RiverbedPreset riverbed;

        private MacroPackageSpec(Builder b) {
            this.id = b.id;
            this.riverStyle = b.riverStyle;
            this.riverBank = b.riverBank;
            this.sourceLake = b.sourceLake;
            this.riverbed = b.riverbed;
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

        public SourceLakePreset sourceLake() {
            return sourceLake;
        }

        public RiverbedPreset riverbed() {
            return riverbed;
        }

        public static Builder builder(MacroPackageId id) {
            return new Builder(id);
        }

        public static final class Builder {
            private final MacroPackageId id;
            private RiverStylePreset riverStyle;
            private RiverBankPreset riverBank;
            private SourceLakePreset sourceLake;
            private RiverbedPreset riverbed;

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

            public Builder sourceLake(SourceLakePreset preset) {
                this.sourceLake = preset;
                return this;
            }

            public Builder riverbed(RiverbedPreset preset) {
                this.riverbed = preset;
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
                if (sourceLake == null) {
                    sourceLake = DEFAULT_SOURCE_LAKE;
                }
                if (riverbed == null) {
                    riverbed = DEFAULT_RIVERBED;
                }
                return new MacroPackageSpec(this);
            }
        }
    }

    /**
     * 河床底料预设：用低频确定性噪声把河床分成大块材料斑块
     * （砂砾 / 沙子 / 黏土等），斑块内同一种方块，铺床顶向下 depth 格。
     */
    public static final class RiverbedPreset {
        /** 候选方块（与 weights 一一对应）。 */
        public final BlockMetaPair[] blocks;
        /** 材料权重（和 blocks 等长，不需要归一化）。 */
        public final double[] weights;
        /** 底料最小深度（格）。 */
        public final int depthMin;
        /** 底料最大深度（格）。 */
        public final int depthMax;
        /** 斑块尺度（blocks，越大块越完整）。 */
        public final double patchScale;

        public RiverbedPreset(BlockMetaPair[] blocks, double[] weights,
                              int depthMin, int depthMax, double patchScale) {
            this.blocks = blocks;
            this.weights = weights;
            this.depthMin = depthMin;
            this.depthMax = depthMax;
            this.patchScale = patchScale;
        }
    }

    /**
     * 源头湖预设：湖盆 + 岸边（干岸）+ 滩涂（浅水底）+ 外坡。
     * 参数可按宏群系调；未配置时使用 DEFAULT_SOURCE_LAKE。
     */
    public static final class SourceLakePreset {
        /** 湖盆基准半径（blocks）。 */
        public final double baseRadius;
        /** 湖心最深深度（blocks，水面以下）。 */
        public final double centerDepth;
        /** 暗河井深度（blocks，湖心再向下）。 */
        public final double undergroundExtraDepth;
        /** 暗河井半径因子（相对 baseRadius）。 */
        public final double shaftRadiusFactor;
        /** 湖岸不规则扰动幅度（0~1，0.18 ≈ 旧版观感）。 */
        public final double irregularityAmp;
        /** 干岸宽度（blocks，从水边向外）。 */
        public final double beachWidth;
        /** 干岸高出水面的高度（blocks）。 */
        public final double beachHeight;
        /** 干岸外缘回到原地形的过渡宽度（blocks）。 */
        public final double outerSlopeWidth;
        /** 干岸方块（默认沙）。 */
        public final BlockMetaPair shoreBlock;
        /** 滩涂 / 浅水底方块（默认泥土）。 */
        public final BlockMetaPair mudBlock;

        public SourceLakePreset(double baseRadius, double centerDepth,
                                double undergroundExtraDepth,
                                double shaftRadiusFactor,
                                double irregularityAmp,
                                double beachWidth, double beachHeight,
                                double outerSlopeWidth,
                                BlockMetaPair shoreBlock,
                                BlockMetaPair mudBlock) {
            this.baseRadius = baseRadius;
            this.centerDepth = centerDepth;
            this.undergroundExtraDepth = undergroundExtraDepth;
            this.shaftRadiusFactor = shaftRadiusFactor;
            this.irregularityAmp = irregularityAmp;
            this.beachWidth = beachWidth;
            this.beachHeight = beachHeight;
            this.outerSlopeWidth = outerSlopeWidth;
            this.shoreBlock = shoreBlock;
            this.mudBlock = mudBlock;
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
