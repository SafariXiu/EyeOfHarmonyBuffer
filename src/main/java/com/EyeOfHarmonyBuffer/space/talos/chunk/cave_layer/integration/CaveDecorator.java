package com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.integration;

import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.format.CaveTag;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveChamber;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveChunkData;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveEntrance;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveFlavorRegistry;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveMath;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveMegaHall;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveSegment;
import ganymedes01.etfuturum.ModBlocks;
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
    private static final int SALT_PUDDLE = 0x26;
    private static final int SALT_RUBBLE = 0x33;
    private static final int SALT_ENTRANCE = 0x44;

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
            if (CaveFlavorRegistry.hasTag(
                    CaveTag.SPIKE_ZONE, cellX, cellZ, seed
                )) {
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

                    if (collapsed
                        && !insideAnyChamber(wx, lo + 0.5, wz, data)
                        && !insideAnyMegaHall(wx, lo + 0.5, wz, data)
                        && !(lo - 1 >= 1
                            && isWater(blocks[base + lo - 1]))) {
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
                    boolean spikeZone = CaveFlavorRegistry.hasTag(
                        CaveTag.SPIKE_ZONE, cellX, cellZ, seed
                    );

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
                    if (!puddle && lo > 1
                        && !insideAnyMegaHall(wx, lo + 0.5, wz, data)
                        && !isWater(blocks[base + lo - 1])) {
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
                            // 大厅内不用石笋区的小结构，改用大厅专属大石笋/钟乳石
                            && !insideAnyChamber(wx, lo + 0.5, wz, data)
                            && !insideAnyChamber(wx, hi + 0.5, wz, data)
                            && !insideAnyMegaHall(wx, lo + 0.5, wz, data)
                            && !insideAnyMegaHall(wx, hi + 0.5, wz, data)
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

                            // 附件校验：钟乳石必须挂在实际实体天花板下、
                            // 石笋必须立在实际实体地板上，避免出现悬浮柱。
                            if (stalactite
                                && hi + 1 <= top
                                && !isAir(blocks[base + hi + 1])) {
                                for (int k = 0; k < len; k++) {
                                    if (k == len - 1) {
                                        setBlock(blocks, meta,
                                            base + hi - k, Blocks.cobblestone);
                                    } else {
                                        setRockBlock(blocks, meta,
                                            base + hi - k,
                                            wx, hi - k, wz, seed);
                                    }
                                }
                            } else if (!stalactite
                                && !puddle
                                && lo - 1 >= 1
                                && !isAir(blocks[base + lo - 1])
                                && !isWater(blocks[base + lo - 1])) {
                                for (int k = 0; k < len; k++) {
                                    if (k == len - 1) {
                                        setBlock(blocks, meta,
                                            base + lo + k, Blocks.cobblestone);
                                    } else {
                                        setRockBlock(blocks, meta,
                                            base + lo + k,
                                            wx, lo + k, wz, seed);
                                    }
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
                        setRockBlock(blocks, meta, base + yBase,
                            x0 + lx, yBase, z0 + lz, seed);
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
                                setRockBlock(blocks, meta, nb + yWater,
                                    x0 + nx, yWater, z0 + nz, seed);
                            }
                            if (yBase >= 1) {
                                setRockBlock(blocks, meta, nb + yBase,
                                    x0 + nx, yBase, z0 + nz, seed);
                            }
                        }
                    }
                }
            }
        }

        // G. 大厅装饰：湖中石笋（有湖时）+ 干地大石笋 + 顶部大钟乳石。
        for (CaveChamber ch : data.chambers) {
            // 洞厅内部 / 边缘的大厅不再装饰，避免混进洞厅
            if (insideAnyMegaHall(
                    (int) Math.floor(ch.cx), ch.cy,
                    (int) Math.floor(ch.cz), data)) {
                continue;
            }
            decorateChamberStructures(
                ch, x0, z0, blocks, meta, worldHeight, seed
            );
        }

        // 水体支撑兜底：给水体底部和侧面包 3 层石头壳，防止悬空 / 侧漏。
        int cellCount = 16 * 16 * worldHeight;
        int[] queue = new int[cellCount];
        byte[] dist = new byte[cellCount];
        int head = 0;
        int tail = 0;
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int base = (lx * 16 + lz) * worldHeight;
                for (int y = 1; y < worldHeight; y++) {
                    if (isWater(blocks[base + y])) {
                        queue[tail++] = base + y;
                    }
                }
            }
        }
        while (head < tail) {
            int idx = queue[head++];
            int d = dist[idx];
            if (d >= 3) {
                continue;
            }
            int flat = idx / worldHeight;
            int lx = flat / 16;
            int lz = flat % 16;
            int y = idx % worldHeight;
            if (lx > 0) {
                tail = fillShellNeighbor(idx - 16 * worldHeight,
                    d + 1, queue, dist, tail, blocks, meta,
                    x0, z0, worldHeight, seed);
            }
            if (lx < 15) {
                tail = fillShellNeighbor(idx + 16 * worldHeight,
                    d + 1, queue, dist, tail, blocks, meta,
                    x0, z0, worldHeight, seed);
            }
            if (lz > 0) {
                tail = fillShellNeighbor(idx - worldHeight,
                    d + 1, queue, dist, tail, blocks, meta,
                    x0, z0, worldHeight, seed);
            }
            if (lz < 15) {
                tail = fillShellNeighbor(idx + worldHeight,
                    d + 1, queue, dist, tail, blocks, meta,
                    x0, z0, worldHeight, seed);
            }
            if (y > 1) {
                tail = fillShellNeighbor(idx - 1,
                    d + 1, queue, dist, tail, blocks, meta,
                    x0, z0, worldHeight, seed);
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
                    if (isWater(blocks[base + top])) {
                        continue; // 河/海表面不铺碎石环，避免把水面换成砂砾/原石
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

    private static boolean isWater(Block block) {
        return block == Blocks.water || block == Blocks.flowing_water;
    }

    /** 把水体外围一格空气变成大块岩性石头并入队，用于向外扩层。 */
    private static int fillShellNeighbor(int idx, int nd,
                                         int[] queue, byte[] dist, int tail,
                                         Block[] blocks, byte[] meta,
                                         int x0, int z0, int worldHeight,
                                         long seed) {
        if (dist[idx] != 0 || !isAir(blocks[idx])) {
            return tail;
        }
        int flat = idx / worldHeight;
        int lx = flat / 16;
        int lz = flat % 16;
        int wy = idx % worldHeight;
        setRockBlock(blocks, meta, idx, x0 + lx, wy, z0 + lz, seed);
        dist[idx] = (byte) nd;
        queue[tail++] = idx;
        return tail;
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
                // 湖面上方的空气段不算洞底，水洼不落在湖里
                if (lo - 1 >= 1 && isWater(blocks[base + lo - 1])) {
                    continue;
                }
                if (insideAnyMegaHall(
                    worldX, lo + 0.5, worldZ, data)) {
                    continue;
                }
                return lo;
            }
        }
        return -1;
    }

    /** 该点（方块中心坐标）是否位于某个大厅空腔内。 */
    private static boolean insideAnyChamber(int worldX, double worldY,
                                            int worldZ, CaveChunkData data) {
        for (CaveChamber ch : data.chambers) {
            if (ch.inside(
                worldX + 0.5, worldY, worldZ + 0.5, 0.0
            )) {
                return true;
            }
        }
        return false;
    }

    /** 该点是否位于洞厅内或洞厅边缘禁装饰带内（外扩 4 格）。 */
    private static boolean insideAnyMegaHall(int worldX, double worldY,
                                             int worldZ, CaveChunkData data) {
        for (CaveMegaHall hall : data.megaHalls) {
            if (hall.nearHorizontal(worldX + 0.5, worldZ + 0.5, 4.0)
                && worldY >= hall.minY - 4.0
                && worldY <= hall.maxY + 4.0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 大厅装饰：湖中 3~5 根大石笋（仅限有湖大厅）、干地 2~4 根大石笋、
     * 顶部 3~5 根大钟乳石。结构都是底部 3×3 逐渐收窄到 1×1 的锥形。
     */
    private static void decorateChamberStructures(CaveChamber ch,
                                                  int x0, int z0,
                                                  Block[] blocks, byte[] meta,
                                                  int worldHeight, long seed) {
        int lakeSurface = (int) Math.floor(ch.lakeSurfaceY);

        // 湖中大石笋：从湖床长出，可露出水面 1~4 格
        int spikeCount = 3 + (int) (CaveMath.hash01(
            (long) ch.cx, (long) ch.cy, (long) ch.cz,
            seed, 0x61) * 3.0);
        for (int i = 0; i < spikeCount; i++) {
            int wx = chamberSpireCenterX(ch, i, 0x62, seed);
            int wz = chamberSpireCenterZ(ch, i, 0x63, seed);
            if (!ch.inside(wx + 0.5, ch.lakeBedY + 0.5, wz + 0.5, 0.0)) {
                continue;
            }
            int len = 6 + (int) (CaveMath.hash01(
                (long) ch.cx, (long) ch.cz, i, seed, 0x64) * 7.0);
            int start = ch.lakeBedY;
            int end = Math.min(start + len - 1, lakeSurface + 4);
            placeChamberSpire(wx, wz, start, end, true,
                x0, z0, blocks, meta, worldHeight, seed,
                (long) ch.cx, (long) ch.cz, 0x70 + i);
        }

        // 干地大石笋：长在湖面以上的大厅坡地上
        int dryCount = 2 + (int) (CaveMath.hash01(
            (long) ch.cx, (long) ch.cy, (long) ch.cz,
            seed, 0x71) * 3.0);
        for (int i = 0; i < dryCount; i++) {
            int wx = chamberSpireCenterX(ch, i, 0x72, seed);
            int wz = chamberSpireCenterZ(ch, i, 0x73, seed);
            double nx = (wx + 0.5 - ch.cx) / ch.rx;
            double nz = (wz + 0.5 - ch.cz) / ch.rz;
            double d2 = nx * nx + nz * nz;
            if (d2 >= 1.0) {
                continue;
            }
            double ryLocal = ch.ry * Math.sqrt(Math.max(0.0, 1.0 - d2));
            double floorY = ch.cy - ryLocal;
            double ceilingY = ch.cy + ryLocal;
            if (floorY <= ch.lakeSurfaceY + 0.5) {
                continue; // 在水里或紧贴湖边
            }
            int len = 6 + (int) (CaveMath.hash01(
                (long) ch.cx, (long) ch.cz, i, seed, 0x74) * 7.0);
            int start = (int) Math.ceil(floorY - 0.5);
            int maxY = (int) Math.floor(ceilingY - 0.5);
            int end = Math.min(start + len - 1, maxY - 1);
            if (end < start) {
                continue;
            }
            placeChamberSpire(wx, wz, start, end, true,
                x0, z0, blocks, meta, worldHeight, seed,
                (long) ch.cx, (long) ch.cz, 0x75 + i);
        }

        // 顶部大钟乳石：从天花板垂下，底部停在湖面 / 地面之上
        int ceilingCount = 3 + (int) (CaveMath.hash01(
            (long) ch.cx, (long) ch.cy, (long) ch.cz,
            seed, 0x65) * 3.0);
        for (int i = 0; i < ceilingCount; i++) {
            int wx = chamberSpireCenterX(ch, i, 0x66, seed);
            int wz = chamberSpireCenterZ(ch, i, 0x67, seed);
            double nx = (wx + 0.5 - ch.cx) / ch.rx;
            double nz = (wz + 0.5 - ch.cz) / ch.rz;
            double d2 = nx * nx + nz * nz;
            if (d2 >= 1.0) {
                continue;
            }
            double ryLocal = ch.ry * Math.sqrt(Math.max(0.0, 1.0 - d2));
            double ceilingY = ch.cy + ryLocal;
            double floorY = ch.cy - ryLocal;
            int len = 6 + (int) (CaveMath.hash01(
                (long) ch.cx, (long) ch.cz, i, seed, 0x68) * 7.0);
            int start = (int) Math.floor(ceilingY - 0.5);
            int floorTop = (int) Math.ceil(floorY - 0.5);
            int minEnd = (floorY <= ch.lakeSurfaceY + 0.5)
                ? lakeSurface + 1 : floorTop + 1;
            int end = Math.max(start - len + 1, minEnd);
            if (end > start) {
                continue;
            }
            placeChamberSpire(wx, wz, start, end, false,
                x0, z0, blocks, meta, worldHeight, seed,
                (long) ch.cx, (long) ch.cz, 0x69 + i);
        }
    }

    /** 大厅石笋 / 钟乳石中心 X（确定性偏移）。 */
    private static int chamberSpireCenterX(CaveChamber ch, int i,
                                           int salt, long seed) {
        double dx = (CaveMath.hash01(
            (long) ch.cx, (long) ch.cy, i, seed, salt) - 0.5)
            * 2.0 * ch.rx * 0.55;
        return (int) Math.floor(ch.cx + dx + 0.5);
    }

    /** 大厅石笋 / 钟乳石中心 Z（确定性偏移）。 */
    private static int chamberSpireCenterZ(CaveChamber ch, int i,
                                           int salt, long seed) {
        double dz = (CaveMath.hash01(
            (long) ch.cx, (long) ch.cz, i, seed, salt) - 0.5)
            * 2.0 * ch.rz * 0.55;
        return (int) Math.floor(ch.cz + dz + 0.5);
    }

    /**
     * 放置一根锥形石柱：底部 3×3 → 中部 2×2 → 顶部 1×1，
     * 尖端用圆石。逐块判断是否在当前区块，跨区块也完整。
     */
    private static void placeChamberSpire(int centerX, int centerZ,
                                          int yStart, int yEnd,
                                          boolean upward,
                                          int x0, int z0,
                                          Block[] blocks, byte[] meta,
                                          int worldHeight, long seed,
                                          long sx, long sz, int salt) {
        int len = Math.abs(yEnd - yStart) + 1;
        for (int k = 0; k < len; k++) {
            int y = upward ? yStart + k : yStart - k;
            if (y <= 0 || y >= worldHeight) {
                continue;
            }
            double t = (double) k / (len - 1);
            int minDx, maxDx, minDz, maxDz;
            if (t < 0.30) {
                minDx = -1; maxDx = 1;
                minDz = -1; maxDz = 1;
            } else if (t < 0.60) {
                int off = CaveMath.hash01(
                    sx, sz, k, seed, salt) < 0.5 ? 0 : -1;
                minDx = off; maxDx = off + 1;
                minDz = off; maxDz = off + 1;
            } else {
                minDx = 0; maxDx = 0;
                minDz = 0; maxDz = 0;
            }
            boolean tipLayer = (k == len - 1);
            for (int dz = minDz; dz <= maxDz; dz++) {
                for (int dx = minDx; dx <= maxDx; dx++) {
                    int wx = centerX + dx;
                    int wz = centerZ + dz;
                    if (wx < x0 || wx >= x0 + 16
                        || wz < z0 || wz >= z0 + 16) {
                        continue;
                    }
                    int idx = ((wx & 15) * 16 + (wz & 15))
                        * worldHeight + y;
                    if (tipLayer && dx == 0 && dz == 0) {
                        blocks[idx] = Blocks.cobblestone;
                        meta[idx] = 0;
                    } else {
                        setRockBlock(blocks, meta, idx, wx, y, wz, seed);
                    }
                }
            }
        }
    }

    private static void setBlock(Block[] blocks, byte[] meta,
                                 int index, Block block) {
        blocks[index] = block;
        meta[index] = 0;
    }

    /** 放置大块岩性石头：普通石头为主，花岗岩/闪长岩/安山岩为辅。 */
    private static void setRockBlock(Block[] blocks, byte[] meta,
                                     int index,
                                     int wx, int wy, int wz, long seed) {
        byte v = CaveMath.rockVariant3D(wx, wy, wz, seed);
        if (v == 4) {
            Block b = ModBlocks.DEEPSLATE.get();
            blocks[index] = b != null ? b : Blocks.stone;
            meta[index] = 0;
        } else if (v == 5) {
            Block b = ModBlocks.TUFF.get();
            blocks[index] = b != null ? b : Blocks.stone;
            meta[index] = 0;
        } else if (v == 0) {
            blocks[index] = Blocks.stone;
            meta[index] = 0;
        } else {
            blocks[index] = ModBlocks.STONE.get();
            meta[index] = (byte) (v == 1 ? 1 : (v == 2 ? 3 : 5));
        }
    }
}
