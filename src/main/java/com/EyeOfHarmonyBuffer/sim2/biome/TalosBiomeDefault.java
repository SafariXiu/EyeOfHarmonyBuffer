package com.EyeOfHarmonyBuffer.sim2.biome;

/**
 * 生物群系层 —— <b>占位实现</b>（2026-10-23）。
 *
 * <p><b>背景</b>：V3 海陆只负责地形/海岸/气候，没有群系。旧的 {@code V2BiomeField}
 * （250 m LUT）曾是唯一群系来源，因此无法随 V2 地形一起废弃。
 *
 * <p><b>决定</b>：群系系统将作为<b>独立项目</b>实现（后续会有大量逻辑，不适合塞进
 * {@code sim2.land}）。在它落地之前，本类恒定返回<b>塔罗斯平原</b>，让旧的群系依赖
 * 可以先从海陆流程里摘出去。
 *
 * <h3>当前行为</h3>
 * <ul>
 *   <li>{@link #sample} 恒定返回 {@code PLAINS}，{@code bias=0.45 / scale=0.55}
 *       （即 {@code Kind.PLAINS} 的默认高度参数），{@code land} 透传调用方给的判定；</li>
 *   <li>无任何空间变化 —— 全图同一种群系；</li>
 *   <li>线程本地复用对象，无分配（与旧实现同款约定）。</li>
 * </ul>
 *
 * <h3>迁移到真实群系层时要做的事</h3>
 * <ol>
 *   <li>把本类换成真实采样（温度/湿度/海拔/洋流 → 群系），保持 {@link #sample} 签名；</li>
 *   <li>{@link Kind} 目前仍借用 {@code V2BiomeSelect.Kind}，真实实现落地时应换成
 *       群系层自己的枚举，并改造消费方（表面材质档案等）；</li>
 *   <li>届时 {@code V2BiomeField} / {@code V2BiomeSelect} / {@code V2BiomePicker}
 *       才能整体移入 {@code sim2.land.deprecated}。</li>
 * </ol>
 */
public final class TalosBiomeDefault {

    /** 群系格距（米）。沿用旧 LUT 的 250 m，供命令与调试工具使用。 */
    public static final int CELL = 250;
    /** 群系平滑参数（暂沿用旧值，真实群系层落地时另行定义）。 */
    public static int BLUR_R = 1;
    public static int BLUR_PASSES = 2;
    public static double JITTER_AMP = 50.0;


    /** 群系种类：暂用 V2 的枚举，避免这一步就牵动所有消费方。 */
    public enum Kind {
        OCEAN, SHELF, PLAINS, BASIN, PLATEAU, MOUNTAINS, ALPINE,
        DESERT, SAVANNA, TROPICAL_RAIN, WARM_STEPPE, TEMPERATE_FOREST,
        TEMPERATE_STEPPE, COOL_FOREST, SUBPOLAR_TUNDRA, POLAR_DESERT
    }

    /** 采样结果（线程复用，不要跨调用持有）。 */
    public static final class Sample {
        /** 群系种类。 */
        public com.EyeOfHarmonyBuffer.space.talos.chunk.world.V2BiomeSelect.Kind kind;
        /** 高度偏移（供 V2 高度链使用；TERRAIN_ON 时该链被 SimTerrain 接管）。 */
        public double bias, scale;
        /** 该点是否陆地（由调用方传入，本类不改写判定）。 */
        public boolean land;
    }

    private static final ThreadLocal<Sample> TL = new ThreadLocal<Sample>() {
        @Override protected Sample initialValue() { return new Sample(); }
    };

    private TalosBiomeDefault() {}

    /**
     * 恒定返回塔罗斯平原。
     *
     * @param x     世界 X（方块）
     * @param z     世界 Z（方块）
     * @param seed  世界种子（当前不使用）
     * @param land  调用方自己的海陆判定（原样回传）
     */
    public static Sample sample(double x, double z, long seed, boolean land) {
        Sample s = TL.get();
        s.kind = com.EyeOfHarmonyBuffer.space.talos.chunk.world.V2BiomeSelect.Kind.PLAINS;
        s.bias = 0.45;
        s.scale = 0.55;
        s.land = land;
        return s;
    }

    /** 整数坐标重载，便于方块级调用。 */
    public static Sample sample(int x, int z, long seed, boolean land) {
        return sample((double) x, (double) z, seed, land);
    }
}
