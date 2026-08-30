package com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.api;

import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.format.CaveTag;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.integration.CaveCarver;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveChamber;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveChunkData;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveEntrance;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveFlavorRegistry;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveGenerator;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveMegaHall;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveNode;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveWorldState;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.TalosLandMask;
import com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer.api.TalosTerrainHeights;
import net.minecraft.world.World;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 洞穴层统一入口（地形生成只通过这里查询）。
 *
 * 地形生成（区块雕刻）用法：
 *   1. 每区块取一次 {@link #dataForChunk(int, int, int)}（空时表示无洞穴）；
 *   2. 逐列调用 CaveCarver.carveColumn(...) 完成雕刻。
 *
 * 调试 / 探针可直接用 {@link #sampleCaveDensity(int, int, int, int)}
 * 或 {@link #isCaveBlock(int, int, int, int)}（低频场景）。
 *
 * 调用限制：
 *   - 只对陆地列生效；海洋 / 河流 / 水体列由 CaveCarver 的保护规则决定；
 *   - 入口竖井依赖最终地表高度（地形链产物），必须在地形填充阶段之后雕刻；
 *   - 世界卸载时应调用 onWorldUnload 释放缓存。
 */
public final class TalosCaveSystem {

    private static final ConcurrentHashMap<Integer, CaveWorldState> STATES =
        new ConcurrentHashMap<Integer, CaveWorldState>();

    private TalosCaveSystem() {}

    /** 调试开关：-Dtalos.cave.enabled=false 可整体关闭洞穴系统。 */
    public static boolean isEnabled() {
        return System.getProperty(
            "talos.cave.enabled", "true"
        ).equalsIgnoreCase("true");
    }

    /**
     * 取某区块的洞穴数据；系统未启用或状态不存在时返回 null。
     * 状态会在首次调用时惰性创建（世界卸载时由 onWorldUnload 清掉）。
     */
    public static CaveChunkData dataForChunk(int chunkX, int chunkZ,
                                             int worldSeedInt) {
        if (!isEnabled()) {
            return null;
        }
        CaveWorldState state = stateFor(worldSeedInt);
        return state != null ? state.dataForChunk(chunkX, chunkZ) : null;
    }

    /**
     * 查询某坐标所在的洞穴区域标签（256 格单元级，纯函数，不依赖缓存）。
     * 没有特殊风味时返回 [DEFAULT]；后续新风味洞穴会出现在这个列表里。
     * 外部层联动请使用本方法，不要直接调用内部注册表。
     */
    public static java.util.List<CaveTag> tagsAt(
        int worldX, int worldZ, int worldSeedInt
    ) {
        if (!isEnabled()) {
            java.util.ArrayList<CaveTag> disabled =
                new java.util.ArrayList<CaveTag>(1);
            disabled.add(CaveTag.DEFAULT);
            return disabled;
        }
        java.util.List<CaveTag> tags = CaveFlavorRegistry.tagsForCell(
            Math.floorDiv(worldX, 256),
            Math.floorDiv(worldZ, 256),
            worldSeedInt
        );
        if (CaveGenerator.megaHallAt(
                worldX, worldZ, worldSeedInt) != null
            && !tags.contains(CaveTag.MEGA_HALL)) {
            tags.add(CaveTag.MEGA_HALL);
        }
        return tags;
    }

    /**
     * 某方块是否为洞穴空气（低频调试用；热路径请走区块数据 + CaveCarver）。
     * 只判断几何，不含地表保护（顶部封层 / 水体避让）规则。
     */
    public static boolean isCaveBlock(int worldX, int worldY, int worldZ,
                                      int worldSeedInt) {
        return sampleCaveDensity(worldX, worldY, worldZ, worldSeedInt) > 0.0;
    }

    /** 洞穴挖空余量：> 0 表示应挖空（几何层面）。 */
    public static double sampleCaveDensity(int worldX, int worldY, int worldZ,
                                           int worldSeedInt) {
        if (!isEnabled()) {
            return -1.0;
        }
        CaveWorldState state = STATES.get(worldSeedInt);
        if (state == null) {
            return -1.0;
        }
        CaveChunkData data = state.dataForChunk(worldX >> 4, worldZ >> 4);
        if (data == null) {
            return -1.0;
        }
        return CaveCarver.sampleExcess(data, worldX, worldY, worldZ,
            worldSeedInt);
    }

    /** 世界加载：预热状态（惰性创建）。 */
    public static void onWorldLoad(World world) {
        if (!isEnabled()) {
            return;
        }
        int seed = TalosLandMask.getWorldSeedInt(world);
        stateFor(seed);
    }

    /** 世界卸载：释放缓存。 */
    public static void onWorldUnload(World world) {
        int seed = TalosLandMask.getWorldSeedInt(world);
        CaveWorldState state = STATES.remove(seed);
        if (state != null) {
            state.clear();
        }
    }

    /**
     * 调试 / 探针用：取指定 256 单元内的入口（不做 usableLandmarkColumn 过滤——
     * 探针需要看到「不可用」入口来诊断为什么入口没雕刻 / 落海上）。
     */
    public static CaveEntrance debugEntranceAt(
        int cellX, int cellZ, int worldSeedInt
    ) {
        java.util.Map<Long, java.util.List<CaveNode>> nodeCache =
            new java.util.HashMap<Long, java.util.List<CaveNode>>();
        return CaveGenerator.entranceForCell(
            cellX, cellZ, worldSeedInt, nodeCache);
    }

    /**
     * 调试 / 传送用：列出玩家周围 radiusCells×radiusCells 个单元内的所有入口。
     * 入口是稀疏地标（约每 256 格单元一个），只查当前区块基本找不到。
     */
    public static java.util.List<CaveEntrance> debugEntrancesNear(
        int worldX, int worldZ, int worldSeedInt, int radiusCells
    ) {
        java.util.ArrayList<CaveEntrance> out =
            new java.util.ArrayList<CaveEntrance>();
        CaveWorldState state = stateFor(worldSeedInt);
        int ccx = Math.floorDiv(worldX, 256);
        int ccz = Math.floorDiv(worldZ, 256);
        java.util.Map<Long, java.util.List<CaveNode>> nodeCache =
            new java.util.HashMap<Long, java.util.List<CaveNode>>();
        for (int dz = -radiusCells; dz <= radiusCells; dz++) {
            for (int dx = -radiusCells; dx <= radiusCells; dx++) {
                CaveEntrance e = CaveGenerator.entranceForCell(
                    ccx + dx, ccz + dz, worldSeedInt, nodeCache);
                if (e == null) {
                    continue;
                }
                // 入口开口列必须能用：陆地且不在河道 / 湖体内，
                // 否则通道不会被雕刻，TP 过去只会落在海上。
                if (!usableLandmarkColumn(e.x, e.z, worldSeedInt)) {
                    continue;
                }
                out.add(e);
            }
        }
        return out;
    }

    /** 调试 / 传送用：列出玩家周围 radiusCells×radiusCells 个单元内的所有大厅。 */
    public static java.util.List<CaveChamber> debugChambersNear(
        int worldX, int worldZ, int worldSeedInt, int radiusCells
    ) {
        java.util.ArrayList<CaveChamber> out =
            new java.util.ArrayList<CaveChamber>();
        CaveWorldState state = stateFor(worldSeedInt);
        int ccx = Math.floorDiv(worldX, 256);
        int ccz = Math.floorDiv(worldZ, 256);
        for (int dz = -radiusCells; dz <= radiusCells; dz++) {
            for (int dx = -radiusCells; dx <= radiusCells; dx++) {
                for (CaveNode n : state.nodesForCell(ccx + dx, ccz + dz)) {
                    if (n.kind != CaveNode.KIND_CHAMBER) {
                        continue;
                    }
                    // 海底 / 水体列的大厅不会被雕刻，跳过
                    if (!usableLandmarkColumn(
                        (int) Math.floor(n.x), (int) Math.floor(n.z),
                        worldSeedInt
                    )) {
                        continue;
                    }
                    out.add(new CaveChamber(
                        n.x, n.y, n.z,
                        n.chamberRx, n.chamberRy, n.chamberRz,
                        n.id
                    ));
                }
            }
        }
        return out;
    }

    /**
     * 向外逐圈扫描 maxSuperCells 个 4096 超级格，返回命中的所有洞厅。
     * 洞厅极稀有，默认给 64 格（约 26 万格范围）足够覆盖大片大陆。
     */
    public static java.util.List<CaveMegaHall> findMegaHallsNear(
        int worldX, int worldZ, int worldSeedInt, int maxSuperCells
    ) {
        java.util.ArrayList<CaveMegaHall> out =
            new java.util.ArrayList<CaveMegaHall>();
        int superX = Math.floorDiv(
            worldX, CaveGenerator.MEGA_HALL_CELL_BLOCKS);
        int superZ = Math.floorDiv(
            worldZ, CaveGenerator.MEGA_HALL_CELL_BLOCKS);
        for (int r = 0; r <= maxSuperCells; r++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    CaveMegaHall hall = CaveGenerator.megaHallForSupercell(
                        superX + dx, superZ + dz, worldSeedInt);
                    if (hall != null) {
                        out.add(hall);
                    }
                }
            }
        }
        return out;
    }

    /**
     * 向外逐圈扫描 radiusCells 个 256 格单元，返回指定类型的节点。
     * kinds 为 null 表示全部节点。
     */
    public static java.util.List<CaveNode> debugNodesNear(
        int worldX, int worldZ, int worldSeedInt, int radiusCells,
        java.util.Set<Integer> kinds
    ) {
        java.util.ArrayList<CaveNode> out =
            new java.util.ArrayList<CaveNode>();
        CaveWorldState state = stateFor(worldSeedInt);
        int ccx = Math.floorDiv(worldX, 256);
        int ccz = Math.floorDiv(worldZ, 256);
        for (int r = 0; r <= radiusCells; r++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    for (CaveNode n : state.nodesForCell(
                        ccx + dx, ccz + dz)) {
                        if (kinds == null || kinds.contains(n.kind)) {
                            out.add(n);
                        }
                    }
                }
            }
        }
        if (kinds == null
            || kinds.contains(CaveNode.KIND_AQUIFER_FULL)
            || kinds.contains(CaveNode.KIND_AQUIFER_HALF)
            || kinds.contains(CaveNode.KIND_AQUIFER_DEAD)) {
            for (CaveNode n : CaveGenerator.debugAquiferNodesNear(
                worldX, worldZ, worldSeedInt, radiusCells)) {
                if (kinds == null || kinds.contains(n.kind)) {
                    out.add(n);
                }
            }
        }
        return out;
    }

    /**
     * 地标（入口 / 大厅）所在列是否真的会被雕刻：
     * 陆地列，且不在河道 / 湖体内。海平面与高度按 Talos 当前世界参数。
     */
    private static boolean usableLandmarkColumn(int worldX, int worldZ,
                                                int worldSeedInt) {
        TalosTerrainHeights.TerrainHeightSample ts =
            TalosTerrainHeights.sample(worldX, worldZ, worldSeedInt, 64, 256);
        return ts.isLand
            && ts.riverMask <= 0.7
            && ts.body == null
            // 地表必须高于海平面：否则入口井口会开在水线 / 水下
            && Math.round(ts.surfaceD) >= 65;
    }

    /** 调试汇总（/talcave 用，只经 api 暴露）。 */
    public static java.util.List<String> debugSummary(int worldX, int worldZ,
                                                      int worldSeedInt) {
        java.util.ArrayList<String> lines = new java.util.ArrayList<String>();
        if (!isEnabled()) {
            lines.add("[TALCAVE] 洞穴系统已禁用 (talos.cave.enabled=false)");
            return lines;
        }
        CaveWorldState state = STATES.get(worldSeedInt);
        if (state == null) {
            lines.add("[TALCAVE] 洞穴状态不存在（worldSeedInt="
                + worldSeedInt + "）");
            return lines;
        }

        int cellX = Math.floorDiv(worldX, 256);
        int cellZ = Math.floorDiv(worldZ, 256);
        CaveChunkData data = state.dataForChunk(worldX >> 4, worldZ >> 4);
        lines.add(String.format(
            "[TALCAVE] pos=(%d,%d) seed=%d cell=(%d,%d) cachedCells=%d",
            worldX, worldZ, worldSeedInt, cellX, cellZ,
            state.cachedCellCount()
        ));
        lines.add(String.format(
            "  当前区块: 通道=%d 大厅=%d 入口=%d",
            data.segments.size(), data.chambers.size(), data.entrances.size()
        ));
        lines.add("  区域标签: " + data.tags);
        if (!data.megaHalls.isEmpty()) {
            CaveMegaHall mh = data.megaHalls.get(0);
            lines.add(String.format(
                "  洞厅: %d 个，中心=(%.0f,%.0f) 半径=%.0f×%.0f×%.0f",
                data.megaHalls.size(), mh.cx, mh.cz, mh.rx, mh.ry, mh.rz
            ));
        }

        List<CaveNode> nodes = state.nodesForCell(cellX, cellZ);
        int chamber = 0;
        for (CaveNode n : nodes) {
            if (n.kind == CaveNode.KIND_CHAMBER) {
                chamber++;
            }
        }
        // 入口 = 从真实节点延伸的通道（entranceForCell 按单元哈希决定）
        java.util.Map<Long, List<CaveNode>> tmpCache =
            new java.util.HashMap<Long, List<CaveNode>>();
        CaveEntrance cellEnt = CaveGenerator.entranceForCell(
            cellX, cellZ, worldSeedInt, tmpCache);
        lines.add("  本单元: 节点=" + nodes.size()
            + " 入口=" + (cellEnt != null ? 1 : 0)
            + " 大厅=" + chamber);

        java.util.List<CaveEntrance> ents = debugEntrancesNear(
            worldX, worldZ, worldSeedInt, 2
        );
        java.util.List<CaveChamber> chams = debugChambersNear(
            worldX, worldZ, worldSeedInt, 2
        );
        double nearestEnt = Double.POSITIVE_INFINITY;
        for (CaveEntrance e : ents) {
            double d = Math.hypot(e.x - worldX, e.z - worldZ);
            if (d < nearestEnt) {
                nearestEnt = d;
            }
        }
        double nearestCham = Double.POSITIVE_INFINITY;
        for (CaveChamber c : chams) {
            double d = Math.hypot(c.cx - worldX, c.cz - worldZ);
            if (d < nearestCham) {
                nearestCham = d;
            }
        }
        lines.add(String.format(
            "  附近(5×5单元): 入口=%d 大厅=%d 最近入口≈%.0f 最近大厅≈%.0f",
            ents.size(), chams.size(),
            nearestEnt == Double.POSITIVE_INFINITY ? -1 : nearestEnt,
            nearestCham == Double.POSITIVE_INFINITY ? -1 : nearestCham
        ));
        return lines;
    }

    private static CaveWorldState stateFor(int worldSeedInt) {
        CaveWorldState state = STATES.get(worldSeedInt);
        if (state != null) {
            return state;
        }
        CaveWorldState created = new CaveWorldState(worldSeedInt);
        CaveWorldState prev = STATES.putIfAbsent(worldSeedInt, created);
        return prev != null ? prev : created;
    }
}
