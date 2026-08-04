package com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.integration;

import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveChunkData;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveEntrance;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveMath;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveSegment;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

/**
 * 洞穴风格化装饰（雕刻后的整块 pass，确定性、零随机状态）：
 *   A. 洞底碎石 / 砂砾铺层；
 *   B. 钟乳石 / 石笋（石头 / 苔石）；
 *   C. 塌方段：通道下半部填碎石，只留顶部爬行缝；
 *   F. 入口 / 天坑井口周围的碎石环。
 *
 * 入口竖井列不参与 A/B/C（防止把井口堵住 / 在井里长石笋）。
 */
public final class CaveDecorator {

    private static final int SALT_FLOOR = 0x11;
    private static final int SALT_SPIKE = 0x22;
    private static final int SALT_SPIKE_ZONE = 0x25;
    private static final int SALT_PUDDLE = 0x26;
    private static final int SALT_RUBBLE = 0x33;
    private static final int SALT_ENTRANCE = 0x44;

    /** 石笋集中带：约 10% 的 256 格单元会成为石笋区。 */
    private static final double SPIKE_ZONE_CHANCE = 0.10;
    /** 石笋区内单列生成概率。 */
    private static final double SPIKE_IN_ZONE_CHANCE = 0.18;

    /** 小水洼：每个 16×16 区块出现水洼候选的概率（还需落在石笋区）。 */
    private static final double PUDDLE_CHUNK_CHANCE = 0.25;
    /** 小水洼半径范围：更小、更集中，只覆盖最低处一小片。 */
    private static final int PUDDLE_RADIUS_MIN = 2;
    private static final int PUDDLE_RADIUS_MAX = 3;

    private CaveDecorator() {}

    /** 雕刻完成后对整个区块做风格化装饰。 */
    public static void decorateChunk(int chunkX, int chunkZ, long seed,
                                     Block[] blocks, byte[] meta,
                                     int worldHeight,
                                     CaveChunkData data) {
        if (data == null) {
            return;
        }

        int x0 = chunkX * 16;
        int z0 = chunkZ * 16;

        // 小水洼：每个区块独立生成一个候选，中心落在区块内部，
        // 由单个区块完整负责，不再跨区块拼半个水洼。
        boolean[] puddleMask = null;
        int puddleCx = 0;
        int puddleCz = 0;
        int puddleR = 0;
        int puddleFloor = Integer.MAX_VALUE;
        if (CaveMath.hash01(
                chunkX, chunkZ, 0, seed, SALT_PUDDLE
            ) < PUDDLE_CHUNK_CHANCE) {
            puddleCx = 5 + (int) (CaveMath.hash01(
                chunkX, chunkZ, 1, seed, SALT_PUDDLE) * 6.0);
            puddleCz = 5 + (int) (CaveMath.hash01(
                chunkX, chunkZ, 2, seed, SALT_PUDDLE) * 6.0);
            puddleR = CaveMath.hash01(
                chunkX, chunkZ, 3, seed, SALT_PUDDLE) < 0.5
                ? PUDDLE_RADIUS_MIN : PUDDLE_RADIUS_MAX;

            // 整个水洼必须落在同一个石笋区，避免只填一半。
            int cellX = Math.floorDiv(chunkX * 16 + puddleCx, 256);
            int cellZ = Math.floorDiv(chunkZ * 16 + puddleCz, 256);
            if (CaveMath.hash01(
                    cellX, cellZ, 0, seed, SALT_SPIKE_ZONE
                ) < SPIKE_ZONE_CHANCE) {
                // 在候选范围内找真正最低的洞底，水只出现在这里。
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        int dx = lx - puddleCx;
                        int dz = lz - puddleCz;
                        if (dx * dx + dz * dz > puddleR * puddleR) {
                            continue;
                        }
                        int floor = lowestCaveFloor(
                            lx, lz, x0 + lx, z0 + lz,
                            blocks, worldHeight, data);
                        // 最低也要留出 1 格基岩，水底石头不能挖到 y=0。
                        if (floor >= 3 && floor < puddleFloor) {
                            puddleFloor = floor;
                        }
                    }
                }
                if (puddleFloor != Integer.MAX_VALUE) {
                    puddleMask = new boolean[256];
                    for (int lz = 0; lz < 16; lz++) {
                        for (int lx = 0; lx < 16; lx++) {
                            int dx = lx - puddleCx;
                            int dz = lz - puddleCz;
                            if (dx * dx + dz * dz > puddleR * puddleR) {
                                continue;
                            }
                            int floor = lowestCaveFloor(
                                lx, lz, x0 + lx, z0 + lz,
                                blocks, worldHeight, data);
                            if (floor == puddleFloor) {
                                puddleMask[lz * 16 + lx] = true;
                            }
                        }
                    }
                }
            }
        }

        for (int lz = 0; lz < 16; lz++) {
            int wz = z0 + lz;
            for (int lx = 0; lx < 16; lx++) {
                int wx = x0 + lx;
                int base = (lx * 16 + lz) * worldHeight;
                int top = worldHeight - 1;
                while (top > 0 && isAir(blocks[base + top])) {
                    top--;
                }
                if (top <= 1) {
                    continue;
                }

                boolean entranceCol = isEntranceColumn(wx, wz, data);
                boolean collapsed = !entranceCol && isCollapsedAt(wx, wz, data);

                // 扫描洞穴空气段（雕刻只产生空气，河道是水不在此列）
                int y = 1;
                while (y <= top) {
                    if (!isAir(blocks[base + y])) {
                        y++;
                        continue;
                    }
                    int lo = y;
                    while (y <= top && isAir(blocks[base + y])) {
                        y++;
                    }
                    int hi = y - 1;
                    int h = hi - lo + 1;
                    if (h < 3 || entranceCol) {
                        continue;
                    }

                    if (collapsed) {
                        // C. 塌方：下半部填碎石，顶部至少留 1 格
                        int fill = lo + Math.max(1, (h * 2) / 5);
                        if (fill >= hi) {
                            fill = hi - 1;
                        }
                        Block rub = CaveMath.hash01(
                            wx, wz, 0, seed, SALT_RUBBLE) < 0.5
                            ? Blocks.gravel : Blocks.cobblestone;
                        for (int yy = lo; yy <= fill; yy++) {
                            setBlock(blocks, meta, base + yy, rub);
                        }
                        continue;
                    }

                    // 石笋区（256 格单元级），带外不生成石笋 / 水洼
                    int cellX = Math.floorDiv(wx, 256);
                    int cellZ = Math.floorDiv(wz, 256);
                    boolean spikeZone = CaveMath.hash01(
                        cellX, cellZ, 0, seed, SALT_SPIKE_ZONE
                    ) < SPIKE_ZONE_CHANCE;

                    // 小水洼：只在整片区域最低的洞底替换地板为水，
                    // 水面统一在同一高度，不会在墙壁 / 高台上挖水。
                    boolean puddle = false;
                    if (puddleMask != null
                        && puddleMask[lz * 16 + lx]
                        && lo == puddleFloor) {
                        puddle = true;
                        setBlock(blocks, meta, base + lo - 1, Blocks.water);
                    }

                    // A. 洞底材质替换：不在地板上垫新方块（会凸起显乱），
                    // 而是替换地板下方一格的原生方块，表面保持平整。
                    double fr = CaveMath.hash01(wx, wz, 1, seed, SALT_FLOOR);
                    if (!puddle && lo > 1) {
                        if (fr < 0.08) {
                            setBlock(blocks, meta, base + lo - 1, Blocks.gravel);
                        } else if (fr < 0.12) {
                            setBlock(blocks, meta, base + lo - 1,
                                Blocks.cobblestone);
                        }
                    }

                    // B. 钟乳石 / 石笋：只在「石笋集中带」内高概率成片出现，
                    // 形成 3×3 ~ 5×5 的大结构；带外完全不生成。
                    if (h >= 4) {
                        if (spikeZone
                            && CaveMath.hash01(
                                wx, wz, 2, seed, SALT_SPIKE
                            ) < SPIKE_IN_ZONE_CHANCE) {
                            boolean stalactite = CaveMath.hash01(
                                wx, wz, 3, seed, SALT_SPIKE) < 0.55;
                            double size = CaveMath.hash01(
                                wx, wz, 4, seed, SALT_SPIKE);
                            int len;
                            if (size < 0.60) {
                                len = 2 + (int) (CaveMath.hash01(
                                    wx, wz, 5, seed, SALT_SPIKE) * 2.0); // 2~3
                            } else if (size < 0.85) {
                                len = 3 + (int) (CaveMath.hash01(
                                    wx, wz, 6, seed, SALT_SPIKE) * 2.0); // 3~4
                            } else {
                                len = 4 + (int) (CaveMath.hash01(
                                    wx, wz, 7, seed, SALT_SPIKE) * 2.0); // 4~5
                            }
                            len = Math.min(len, h - 2);
                            Block sb = CaveMath.hash01(
                                wx, wz, 8, seed, SALT_SPIKE) < 0.25
                                ? Blocks.mossy_cobblestone : Blocks.stone;

                            // 附件校验：钟乳石必须挂在实际实体天花板下、
                            // 石笋必须立在实际实体地板上，避免出现悬浮柱。
                            if (stalactite
                                && hi + 1 <= top
                                && !isAir(blocks[base + hi + 1])) {
                                for (int k = 0; k < len; k++) {
                                    setBlock(blocks, meta, base + hi - k,
                                        (k == len - 1) ? Blocks.cobblestone : sb);
                                }
                            } else if (!stalactite
                                && !puddle
                                && lo - 1 >= 1
                                && !isAir(blocks[base + lo - 1])) {
                                for (int k = 0; k < len; k++) {
                                    setBlock(blocks, meta, base + lo + k,
                                        (k == len - 1) ? Blocks.cobblestone : sb);
                                }
                            }
                        }
                    }
                }
            }
        }

        // 给水洼包一层石头：水底铺石头，外圈也补一圈，
        // 避免水洼旁边直接是空洞 / 断崖。
        if (puddleMask != null) {
            int yWater = puddleFloor - 1;
            int yBase = puddleFloor - 2;
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    if (!puddleMask[lz * 16 + lx]) {
                        continue;
                    }
                    int base = (lx * 16 + lz) * worldHeight;
                    if (yBase >= 1) {
                        setBlock(blocks, meta, base + yBase, Blocks.stone);
                    }
                    for (int dz = -1; dz <= 1; dz++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            if (dx == 0 && dz == 0) {
                                continue;
                            }
                            int nx = lx + dx;
                            int nz = lz + dz;
                            if (nx < 0 || nx >= 16 || nz < 0 || nz >= 16) {
                                continue;
                            }
                            if (puddleMask[nz * 16 + nx]) {
                                continue;
                            }
                            // 入口竖井不能堵
                            if (isEntranceColumn(x0 + nx, z0 + nz, data)) {
                                continue;
                            }
                            int nb = (nx * 16 + nz) * worldHeight;
                            if (yWater >= 1) {
                                setBlock(blocks, meta, nb + yWater,
                                    Blocks.stone);
                            }
                            if (yBase >= 1) {
                                setBlock(blocks, meta, nb + yBase,
                                    Blocks.stone);
                            }
                        }
                    }
                }
            }
        }

        // F. 入口井口碎石环
        for (CaveEntrance e : data.entrances) {
            int rOuter = e.radius + 1;
            for (int dz = -rOuter - 2; dz <= rOuter + 2; dz++) {
                for (int dx = -rOuter - 2; dx <= rOuter + 2; dx++) {
                    int wx = e.x + dx;
                    int wz = e.z + dz;
                    if (wx < x0 || wx >= x0 + 16 || wz < z0 || wz >= z0 + 16) {
                        continue;
                    }
                    int d2 = dx * dx + dz * dz;
                    if (d2 <= e.radius * e.radius) {
                        continue; // 井口本身
                    }
                    if (d2 > rOuter * rOuter
                        && CaveMath.hash01(wx, wz, 9, seed, SALT_ENTRANCE) >= 0.30) {
                        continue; // 外圈只随机撒一部分
                    }
                    int lx = wx & 15;
                    int lz2 = wz & 15;
                    int base = (lx * 16 + lz2) * worldHeight;
                    int top = worldHeight - 1;
                    while (top > 0 && isAir(blocks[base + top])) {
                        top--;
                    }
                    if (top <= 0) {
                        continue;
                    }
                    Block mat = CaveMath.hash01(
                        wx, wz, 10, seed, SALT_ENTRANCE) < 0.5
                        ? Blocks.gravel : Blocks.cobblestone;
                    setBlock(blocks, meta, base + top, mat);
                }
            }
        }
    }

    /** 未初始化（null）与空气都视为空气：区块填充后地表以上方块是 null。 */
    private static boolean isAir(Block block) {
        return block == null || block == Blocks.air;
    }

    private static boolean isEntranceColumn(int worldX, int worldZ,
                                            CaveChunkData data) {
        for (CaveEntrance e : data.entrances) {
            int dx = worldX - e.x;
            int dz = worldZ - e.z;
            if (dx * dx + dz * dz <= e.radius * e.radius) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCollapsedAt(int worldX, int worldZ,
                                         CaveChunkData data) {
        for (CaveSegment seg : data.segments) {
            if (seg.collapsed
                && worldX >= seg.minX && worldX <= seg.maxX
                && worldZ >= seg.minZ && worldZ <= seg.maxZ) {
                return true;
            }
        }
        return false;
    }

    /**
     * 返回该列可生成水洼的最低洞底 Y；入口 / 塌方列不参与，没有则返回 -1。
     */
    private static int lowestCaveFloor(int localX, int localZ,
                                       int worldX, int worldZ,
                                       Block[] blocks, int worldHeight,
                                       CaveChunkData data) {
        int base = (localX * 16 + localZ) * worldHeight;
        int top = worldHeight - 1;
        while (top > 0 && isAir(blocks[base + top])) {
            top--;
        }
        if (top <= 1 || isEntranceColumn(worldX, worldZ, data)) {
            return -1;
        }
        boolean collapsed = isCollapsedAt(worldX, worldZ, data);
        int y = 1;
        while (y <= top) {
            if (!isAir(blocks[base + y])) {
                y++;
                continue;
            }
            int lo = y;
            while (y <= top && isAir(blocks[base + y])) {
                y++;
            }
            int hi = y - 1;
            if (hi - lo + 1 >= 3 && !collapsed) {
                return lo;
            }
        }
        return -1;
    }

    private static void setBlock(Block[] blocks, byte[] meta,
                                 int index, Block block) {
        blocks[index] = block;
        meta[index] = 0;
    }
}
