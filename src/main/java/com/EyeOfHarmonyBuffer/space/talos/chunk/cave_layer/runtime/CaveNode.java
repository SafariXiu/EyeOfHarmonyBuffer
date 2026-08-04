package com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime;

/**
 * 洞穴网络节点（不可变，确定性生成）。
 *
 * 类型：
 *   - BACKBONE  骨干节点（512/768 格晶格，保证网络全局连通）
 *   - NORMAL    普通分支节点
 *   - CHAMBER   大厅节点（附带椭球空腔）
 *   - ENTRANCE  主入口节点（地表竖井）
 *   - SINKHOLE  天坑节点（小入口）
 *   - SHAFT     竖井节点（与同格 MID 节点垂直连接）
 */
public final class CaveNode {

    public static final int KIND_BACKBONE = 1;
    public static final int KIND_NORMAL = 0;
    public static final int KIND_CHAMBER = 2;
    public static final int KIND_ENTRANCE = 3;
    public static final int KIND_SINKHOLE = 4;
    public static final int KIND_SHAFT = 5;

    /** 深度带：UPPER / MID / DEEP。 */
    public static final int BAND_UPPER = 0;
    public static final int BAND_MID = 1;
    public static final int BAND_DEEP = 2;

    public final long id;
    public final int cellX;
    public final int cellZ;
    public final float x;
    public final float y;
    public final float z;
    public final int kind;
    public final int band;

    /** 大厅椭球半轴（kind == CHAMBER 时有效）。 */
    public final float chamberRx;
    public final float chamberRy;
    public final float chamberRz;

    /** 入口竖井半径（kind == ENTRANCE / SINKHOLE 时有效）。 */
    public final int shaftRadius;

    /** 竖井孪生节点 id（kind == SHAFT 时有效）。 */
    public final long twinId;

    public CaveNode(long id, int cellX, int cellZ,
                    float x, float y, float z,
                    int kind, int band,
                    float chamberRx, float chamberRy, float chamberRz,
                    int shaftRadius, long twinId) {
        this.id = id;
        this.cellX = cellX;
        this.cellZ = cellZ;
        this.x = x;
        this.y = y;
        this.z = z;
        this.kind = kind;
        this.band = band;
        this.chamberRx = chamberRx;
        this.chamberRy = chamberRy;
        this.chamberRz = chamberRz;
        this.shaftRadius = shaftRadius;
        this.twinId = twinId;
    }

    public boolean isEntranceLike() {
        return kind == KIND_ENTRANCE || kind == KIND_SINKHOLE;
    }
}
