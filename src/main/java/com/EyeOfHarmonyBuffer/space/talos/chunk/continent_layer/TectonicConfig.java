package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer;

public final class TectonicConfig {

    public static final int SUPER_CELL_SIZE = 80000;
    public static final int CENTER_JITTER_MAX = 4000;

    public static final int MIN_RADIUS = 17000;
    public static final int BASE_RADIUS_MIN = 21000;
    public static final int BASE_RADIUS_MAX = 23000;
    public static final int MAX_RADIUS = 25000;

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
