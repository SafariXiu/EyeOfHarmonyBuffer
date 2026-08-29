package com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer;

/**
 * 把世界坐标折算成宏细胞坐标。
 */

public final class MacroCells {

    /** 每个宏细胞的大小（以 block 为单位）。 */
    public static final int MACRO_CELL_SIZE = 4096;

    private MacroCells() {}

    /** 世界坐标 -> 宏 cell 坐标（对负数稳定）。 */
    public static int worldToMacroCell(int coord) {
        return Math.floorDiv(coord, MACRO_CELL_SIZE);
    }

    /** 宏 cell 中心的世界坐标（double）。 */
    public static double cellCenter(int c) {
        return c * (double) MACRO_CELL_SIZE + MACRO_CELL_SIZE * 0.5;
    }
}
