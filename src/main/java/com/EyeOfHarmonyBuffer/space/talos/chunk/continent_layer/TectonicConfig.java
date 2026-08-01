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

    public static final double SHELF_MAX_DISTANCE = 12000.0;

    private TectonicConfig() {}
}
