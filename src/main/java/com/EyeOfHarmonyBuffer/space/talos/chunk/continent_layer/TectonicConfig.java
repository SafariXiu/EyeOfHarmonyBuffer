package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer;

public final class TectonicConfig {

    /** 布点格网边长（主 / 次级大陆共用）：主大陆占 (奇,奇) 格，次级占其余格。 */
    public static final int PLACEMENT_CELL_SIZE = 40000;

    /** 超级格边长 = 2 × 布点格：一个超级格包含 1 个主大陆 + 最多 3 个次级大陆。 */
    public static final int SUPER_CELL_SIZE = 80000;

    /** 主大陆中心抖动上限（相对布点格中心）。 */
    public static final int CENTER_JITTER_MAX = 12000;

    /** 次级大陆中心抖动上限。 */
    public static final int SUB_JITTER_MAX = 8000;

    /** 次级大陆出现概率（分离检查通过后才真正存在，实际密度略低于该值）。 */
    public static final double SUB_PRESENCE_PROB = 0.7;

    /** 两个大陆实体之间的最小安全间距（blocks）。 */
    public static final double SAFETY_GAP = 3000.0;

    /** 形状振幅等比缩放的基准半径（取主大陆中值），次级大陆形状观感与主大陆统一。 */
    public static final double MAIN_REFERENCE_RADIUS = 22000.0;

    public static final int MIN_RADIUS = 17000;
    public static final int BASE_RADIUS_MIN = 21000;
    public static final int BASE_RADIUS_MAX = 23000;
    public static final int MAX_RADIUS = 25000;

    /** 次级大陆半径范围（比主大陆小一号）。 */
    public static final int SUB_MIN_RADIUS = 12000;
    public static final int SUB_BASE_RADIUS_MIN = 13000;
    public static final int SUB_BASE_RADIUS_MAX = 16000;
    public static final int SUB_MAX_RADIUS = 17000;

    public static final int COAST_VERTEX_COUNT = 4096;

    public static final double WCOAST_DEFAULT = 256.0;
    public static final double WSHELF_DEFAULT = 2048.0;
    public static final double WPLATE_DEFAULT = 3000.0;

    public static final int   MIN_PLATE_PER_SUPER = 4;
    public static final int   MAX_PLATE_PER_SUPER = 6;
    public static final double PLATE_SEED_RING_MIN = 0.25;
    public static final double PLATE_SEED_RING_MAX = 0.95;

    public static final double PLATE_BOUNDARY_THRESHOLD = 0.22;

    /** 板块相对运动速度低于该值判定为静止（板块运动向量速度范围 0.3~1.0）。 */
    public static final double PLATE_INACTIVE_RELATIVE_SPEED = 0.25;

    /** 切向分量超过法向分量的该倍数时判定为走滑。 */
    public static final double PLATE_TRANSFORM_TANGENT_RATIO = 2.0;

    /** 板块混合数量：采样时考虑最近 N 块板块（含最近者），产生 N-1 条缝合线影响。 */
    public static final int PLATE_BLEND_COUNT = 3;

    /** 板块边界风格化 / 覆盖的最小强度（0.2 以外不生效，宏包覆盖与裂谷塑形共用）。 */
    public static final double PLATE_BOUNDARY_MIN_STRENGTH = 0.2;

    /** 裂谷边缘平台高于海平面的格数（硬切后的台地高度）。 */
    public static final int RIFT_EDGE_ABOVE_SEA = 1;

    /**
     * 裂谷谷底相对海平面的格数（负数 = 高于海平面）。
     * 干裂谷：谷底保持在水面之上，避免整个裂谷被淹成湖。
     */
    public static final int RIFT_FLOOR_BELOW_SEA = -3;

    /** 裂谷塑形混合系数（1 = 完全按裂谷剖面，越小保留越多原始地形）。 */
    public static final double RIFT_BLEND = 0.85;

    /**
     * 裂谷悬崖风格化：外崖面开始受塑形的强度。
     * 强度每 0.01 ≈ 0.22×超级大陆直径 × 0.01 格（半径 1.4w 的大陆约为 62 格），
     * 0.197~0.2 即约 18 格宽的悬崖面（悬崖顶部到底部的横向距离）。
     * 注意：宽度随大陆半径缩放，大/小大陆上会略有出入。
     */
    public static final double RIFT_CLIFF_START_STRENGTH = 0.197;

    /** 裂谷崖缘平台结束 / 内缘缓坡开始强度。 */
    public static final double RIFT_TALUS_START_STRENGTH = 0.45;

    /** 裂谷倒石堆结束 / 谷底开始强度。 */
    public static final double RIFT_FLOOR_START_STRENGTH = 0.75;

    /** 崖缘平台上卷格数（崖唇）。 */
    public static final double RIFT_RIM_UPLIFT = 7.0;

    /** 倒石堆顶部高于谷底的格数。 */
    public static final double RIFT_TALUS_HEIGHT = 4.0;

    /** 崖顶锯齿噪声幅度（格）。 */
    public static final double RIFT_RIM_NOISE_AMP = 4.0;

    /** 崖面凹凸噪声幅度（格）。 */
    public static final double RIFT_CLIFF_NOISE_AMP = 5.0;

    /** 倒石堆碎石起伏幅度（格）。 */
    public static final double RIFT_TALUS_NOISE_AMP = 2.5;

    /** 谷底起伏幅度（格）。 */
    public static final double RIFT_FLOOR_NOISE_AMP = 1.5;

    /** 岩架高度（格）：崖面下落途中的台阶。 */
    public static final double RIFT_LEDGE_HEIGHT = 2.0;

    /** 崖线主噪声尺度（格）。 */
    public static final double RIFT_NOISE_SCALE_RIM = 96.0;

    /** 崖面噪声尺度（格）。 */
    public static final double RIFT_NOISE_SCALE_FACE = 28.0;

    /** 细节噪声尺度（格）。 */
    public static final double RIFT_NOISE_SCALE_DETAIL = 12.0;

    public static final double SHELF_MAX_DISTANCE = 12000.0;

    private TectonicConfig() {}
}
