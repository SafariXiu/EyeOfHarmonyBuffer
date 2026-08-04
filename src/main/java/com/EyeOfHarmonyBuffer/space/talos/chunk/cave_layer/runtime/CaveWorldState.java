package com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单个世界的洞穴状态：单元节点缓存（惰性、线程安全、纯函数构建）。
 *
 * 生成本身无状态；这里只缓存已算过的 256 格单元节点，
 * 避免每个区块重复哈希。缓存可被并发清空（世界卸载时）。
 */
public final class CaveWorldState {

    /** 节点缓存上限（单元数）；超过后整体清空重算（确定性结果不受影响）。 */
    private static final int NODE_CACHE_LIMIT = 200_000;

    public final int worldSeedInt;

    private final ConcurrentHashMap<Long, List<CaveNode>> nodeCache =
        new ConcurrentHashMap<Long, List<CaveNode>>();

    /** 单元 → 从该单元出发的线段（避免每个区块重建边）。 */
    private final ConcurrentHashMap<Long, List<CaveSegment>> edgeCache =
        new ConcurrentHashMap<Long, List<CaveSegment>>();

    public CaveWorldState(int worldSeedInt) {
        this.worldSeedInt = worldSeedInt;
    }

    /** 取某区块的洞穴数据（线段 / 大厅 / 入口）。 */
    public CaveChunkData dataForChunk(int chunkX, int chunkZ) {
        trimCache();
        return CaveGenerator.buildChunkData(
            chunkX, chunkZ, worldSeedInt, nodeCache, edgeCache
        );
    }

    /** 调试：取某单元的节点列表（不缓存）。 */
    public List<CaveNode> nodesForCell(int cellX, int cellZ) {
        return CaveGenerator.nodesForCell(cellX, cellZ, worldSeedInt);
    }

    public int cachedCellCount() {
        return nodeCache.size();
    }

    public void clear() {
        nodeCache.clear();
        edgeCache.clear();
    }

    private void trimCache() {
        if (nodeCache.size() > NODE_CACHE_LIMIT) {
            nodeCache.clear();
            edgeCache.clear();
        }
    }
}
