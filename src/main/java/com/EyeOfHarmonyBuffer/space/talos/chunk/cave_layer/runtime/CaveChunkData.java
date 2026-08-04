package com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime;

import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.format.CaveTag;

import java.util.List;

/** 单个区块相关的全部洞穴数据（线段 / 大厅 / 入口）。 */
public final class CaveChunkData {

    public final List<CaveSegment> segments;
    public final List<CaveChamber> chambers;
    public final List<CaveEntrance> entrances;
    /** 本区块所在 256 格单元命中的区域风格标签。 */
    public final List<CaveTag> tags;

    public CaveChunkData(List<CaveSegment> segments,
                         List<CaveChamber> chambers,
                         List<CaveEntrance> entrances,
                         List<CaveTag> tags) {
        this.segments = segments;
        this.chambers = chambers;
        this.entrances = entrances;
        this.tags = tags;
    }
}
