package com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime;

import java.util.List;

/** 单个区块相关的全部洞穴数据（线段 / 大厅 / 入口）。 */
public final class CaveChunkData {

    public final List<CaveSegment> segments;
    public final List<CaveChamber> chambers;
    public final List<CaveEntrance> entrances;

    public CaveChunkData(List<CaveSegment> segments,
                         List<CaveChamber> chambers,
                         List<CaveEntrance> entrances) {
        this.segments = segments;
        this.chambers = chambers;
        this.entrances = entrances;
    }
}
