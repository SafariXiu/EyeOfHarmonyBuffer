package com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.integration;

import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveChamber;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveChunkData;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveEntrance;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveMegaHall;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveMath;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveSegment;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverBodyData;
import ganymedes01.etfuturum.ModBlocks;
import net.minecraft.init.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * 洞穴雕刻 pass（世界层方块填充阶段调用）。
 *
 * 规则：
 *   - 只作用于陆地列；地表顶部保留 SURFACE_CAP 格（入口竖井除外）；
 *   - 河流 / 水体列只允许在床底以下 2 格雕刻（不挖穿河床 / 湖床）；
 *   - y = 0 的基岩永不雕刻；
 *   - 入口竖井从井底一直开到地表（含顶部方块），形成真实洞口。
 *
 * 性能：
 *   - 逐列先做线段 / 大厅的快速拒绝（大部分列零成本跳过）；
 *   - 只有靠近洞穴的列才逐块计算点到折线距离 + 洞壁噪声。
 */
public final class CaveCarver {

    /** 洞壁噪声尺度（blocks）。 */
    public static final double NOISE_SCALE = 12.0;
    /** 洞壁噪声半幅（blocks）。 */
    public static final double WALL_AMP = 1.3;
    /** 地表保留厚度（blocks）：默认不挖穿顶部。 */
    public static final int SURFACE_CAP = 3;
    /** 地下湖石头壳厚度：水体外面再包几层石头。 */
    private static final int LAKE_SHELL_THICKNESS = 2;
    /** 水体保护厚度：河床 / 海床下方至少保留的实心层（防止沙层悬空）。 */
    private static final int WATER_PROTECT_BUFFER = 10;
    /** 河流保护掩码阈值：放宽后河岸两侧也会被包住。 */
    private static final double RIVER_PROTECT_MASK = 0.35;
    /** 洞厅底部地形噪声：单层大尺度、大幅度，靠幅度自然拔高高区。 */
    private static final double FLOOR_NOISE_SCALE = 140.0;
    private static final double FLOOR_NOISE_AMP = 30.0;
    private static final int FLOOR_NOISE_SALT = 0xC3;
    /** 域扭曲：把噪声坐标揉弯，消除直线切线。 */
    private static final double FLOOR_WARP_AMP = 60.0;
    private static final double FLOOR_WARP_SCALE = 300.0;
    private static final int FLOOR_WARP_SALT = 0xD1;
    /** 水面基准：低处灌水到 y=15。 */
    private static final int FLOOR_WATER_LEVEL = 15;
    /** 平台阈值：噪声超过该值直接切高台（约覆盖 1/4 区域）。 */
    private static final double PLATEAU_THRESHOLD = 0.15;
    /** 平台过渡带宽：阈值前这段做陡坡，而不是硬切。 */
    private static final double PLATEAU_BLEND = 0.12;
    /** 噪声梯度超过该值时保留硬崖，不铺陡坡。 */
    private static final double PLATEAU_HARD_GRADIENT = 0.06;
    private static final int PLATEAU_GRADIENT_STEP = 4;
    /** 高平台基准高度与起伏。 */
    private static final int PLATEAU_BASE = 32;
    private static final double PLATEAU_AMP = 6.0;
    private static final double PLATEAU_SCALE = 600.0;
    private static final int PLATEAU_SALT = 0xC6;
    /** 底部高频风格化噪声：小幅、较缓起伏，只留自然粗糙感。 */
    private static final double FLOOR_DETAIL_SCALE = 32.0;
    private static final double FLOOR_DETAIL_AMP = 1.5;
    private static final int FLOOR_DETAIL_SALT = 0xC7;

    private static final int NOISE_SALT = 0xC0FFEE;

    private CaveCarver() {}

    /**
     * 几何层挖空余量：> 0 表示该方块应挖空（不含地表 / 水体保护规则）。
     * 大厅命中直接返回正数。
     */
    public static double sampleExcess(CaveChunkData data,
                                      int worldX, int worldY, int worldZ,
                                      long seed) {
        double wall = (CaveMath.valueNoise3D(
            worldX, worldY, worldZ, seed, NOISE_SCALE, NOISE_SALT) - 0.5)
            * 2.0 * WALL_AMP;

        double best = Double.NEGATIVE_INFINITY;
        for (CaveSegment seg : data.segments) {
            if (worldX < seg.minX || worldX > seg.maxX
                || worldY < seg.minY || worldY > seg.maxY
                || worldZ < seg.minZ || worldZ > seg.maxZ) {
                continue;
            }
            double e = seg.sampleExcess(worldX, worldY, worldZ, wall);
            if (e > best) {
                best = e;
            }
        }
        for (CaveMegaHall hall : data.megaHalls) {
            int[] span = new int[2];
            if (hall.verticalSpan(
                    worldX, worldZ, (int) Math.floor(hall.maxY), span)
                && worldY >= span[0] && worldY <= span[1]) {
                return 1.0;
            }
        }
        for (CaveChamber ch : data.chambers) {
            // 带湖大厅的湖床以下是实体，不算空腔
            if (ch.hasLake && worldY < ch.lakeBedY) {
                continue;
            }
            if (ch.inside(worldX + 0.5, worldY + 0.5, worldZ + 0.5, wall)) {
                return 1.0;
            }
        }
        return best;
    }

    /**
     * 雕刻某一列（在方块填充完成后调用）。
     *
     * @param topSolidY  该列顶层实体方块 Y（与 ChunkProviderTalos2 一致）
     * @param seaLevel   海平面（入口开口要求地表高于海平面）
     * @param riverMask  河流影响掩码（0~1）
     * @param body       命中的水体（无则 null）
     * @param worldHeight 世界高度（方块数组索引用）
     */
    public static void carveColumn(int worldX, int worldZ,
                                   int localX, int localZ,
                                   int topSolidY,
                                   int seaLevel,
                                   double riverMask, RiverBodyData body,
                                   CaveChunkData data,
                                   net.minecraft.block.Block[] blocks,
                                   byte[] meta,
                                   int worldHeight,
                                   long seed) {
        if (data == null || topSolidY <= 1) {
            return;
        }

        boolean waterProtected =
            riverMask > RIVER_PROTECT_MASK || body != null;
        int maxY = waterProtected
            ? topSolidY - WATER_PROTECT_BUFFER
            : topSolidY - SURFACE_CAP;
        if (maxY < 1) {
            return;
        }

        // 洞厅快速雕刻：整列按几何直接填，不走逐格噪声
        for (CaveMegaHall hall : data.megaHalls) {
            int[] span = new int[2];
            if (!hall.verticalSpan(worldX, worldZ, maxY, span)) {
                continue;
            }
            carveMegaHallColumn(hall, worldX, worldZ, span[1],
                localX, localZ, blocks, meta, worldHeight, seed);
            return;
        }

        // 入口竖井：挖穿地表封层（水体列不开入口）
        for (CaveEntrance e : data.entrances) {
            int dx = worldX - e.x;
            int dz = worldZ - e.z;
            if (dx * dx + dz * dz <= e.radius * e.radius) {
                // 井口必须高于海平面，否则会开在水线 / 水下，形成"挖到水地表"。
                if (!waterProtected && topSolidY >= seaLevel + 1) {
                    int start = Math.max(1, e.y);
                    for (int y = start; y <= topSolidY; y++) {
                        setAir(blocks, meta, localX, localZ, y, worldHeight);
                    }
                    maxY = Math.min(maxY, e.y - 1);
                }
                break;
            }
        }
        if (maxY < 1) {
            return;
        }

        // 快速拒绝：列附近没有线段 / 大厅则整列跳过
        List<CaveSegment> near = null;
        boolean chamberNear = false;
        for (CaveSegment seg : data.segments) {
            if (worldX >= seg.minX && worldX <= seg.maxX
                && worldZ >= seg.minZ && worldZ <= seg.maxZ) {
                if (near == null) {
                    near = new ArrayList<CaveSegment>(4);
                }
                near.add(seg);
            }
        }
        for (CaveChamber ch : data.chambers) {
            // 带湖大厅向外多扩几格，方便给湖边铺厚石头壳
            double pad = ch.hasLake ? LAKE_SHELL_THICKNESS : 0.0;
            if (worldX + 0.5 >= ch.minX - pad
                && worldX + 0.5 <= ch.maxX + pad
                && worldZ + 0.5 >= ch.minZ - pad
                && worldZ + 0.5 <= ch.maxZ + pad) {
                chamberNear = true;
                break;
            }
        }
        if ((near == null || near.isEmpty()) && !chamberNear) {
            return;
        }

        for (int y = 1; y <= maxY; y++) {
            double wall = (CaveMath.valueNoise3D(
                worldX, y, worldZ, seed, NOISE_SCALE, NOISE_SALT) - 0.5)
                * 2.0 * WALL_AMP;
            double excess = Double.NEGATIVE_INFINITY;
            boolean lakeWater = false;
            boolean lakeSealed = false;
            boolean lakeHandled = false;

            if (near != null) {
                for (CaveSegment seg : near) {
                    if (y < seg.minY || y > seg.maxY) {
                        continue;
                    }
                    double e = seg.sampleExcess(worldX, y, worldZ, wall);
                    if (e > excess) {
                        excess = e;
                    }
                }
            }

            // 地下湖：先铺石头壳（湖底 + 同一层四邻），再放水。
            for (CaveChamber ch : data.chambers) {
                if (!ch.hasLake || y < ch.lakeBedY
                    || y + 0.5 > ch.lakeSurfaceY) {
                    continue;
                }
                if (ch.inside(
                    worldX + 0.5, y + 0.5, worldZ + 0.5, wall
                )) {
                    // 湖心：湖床下方若是空的，先补几层石头封底，再放水
                    for (int d = 1; d <= LAKE_SHELL_THICKNESS; d++) {
                        int by = y - d;
                        if (by < 1) {
                            break;
                        }
                        int byIdx = (localX * 16 + localZ)
                            * worldHeight + by;
                        if (isAir(blocks[byIdx])) {
                            setStone(blocks, meta, localX, localZ,
                                by, worldHeight);
                        } else {
                            break;
                        }
                    }
                    setWater(blocks, meta, localX, localZ, y, worldHeight);
                    lakeWater = true;
                    lakeHandled = true;
                    break;
                }
                // 不在水里：同一层四邻有水的就铺石头封边
                if (isNearLakeWater(ch, worldX, y, worldZ, wall)) {
                    setStone(blocks, meta, localX, localZ, y, worldHeight);
                    lakeSealed = true;
                    lakeHandled = true;
                    break;
                }
            }

            if (!lakeHandled && chamberNear) {
                for (CaveChamber ch : data.chambers) {
                    // 湖床以下保留实体，不参与普通挖空
                    if (ch.hasLake && y < ch.lakeBedY) {
                        continue;
                    }
                    if (ch.inside(worldX + 0.5, y + 0.5, worldZ + 0.5, wall)) {
                        excess = 1.0;
                        break;
                    }
                }
            }

            if (lakeSealed) {
                // 湖边石头壳已经放好
            } else if (excess > 0.0 && !lakeWater) {
                setAir(blocks, meta, localX, localZ, y, worldHeight);
            }
        }
    }

    /** 洞厅整列快速填充：底部中频噪声分出干地与湖泊。 */
    private static void carveMegaHallColumn(CaveMegaHall hall,
                                            int worldX, int worldZ,
                                            int yMax,
                                            int localX, int localZ,
                                            net.minecraft.block.Block[] blocks,
                                            byte[] meta,
                                            int worldHeight, long seed) {
        if (yMax < 2) {
            return;
        }
        // 强制 y=1 保留石头层，避免基岩漏出。
        setStone(blocks, meta, localX, localZ, 1, worldHeight);

        // 底部地形：分层柏林（3 层 fBm），水面基准 y=15。
        double wx2 = worldX + FLOOR_WARP_AMP * CaveMath.perlin3D(
            worldX / FLOOR_WARP_SCALE, 0.4, worldZ / FLOOR_WARP_SCALE,
            seed, FLOOR_WARP_SALT);
        double wz2 = worldZ + FLOOR_WARP_AMP * CaveMath.perlin3D(
            worldX / FLOOR_WARP_SCALE, 0.5, worldZ / FLOOR_WARP_SCALE,
            seed, FLOOR_WARP_SALT + 1);
        double n = CaveMath.fbm3D(
            wx2 / FLOOR_NOISE_SCALE, 0.1, wz2 / FLOOR_NOISE_SCALE,
            seed, FLOOR_NOISE_SALT, 3, 2.0, 0.5) * 2.0;
        int offset = (int) Math.round(n * FLOOR_NOISE_AMP);
        int lowY = FLOOR_WATER_LEVEL + offset;
        int floorY = lowY;
        // 阈值前做一段 smoothstep 陡坡，越过阈值后进入高平台。
        if (n >= PLATEAU_THRESHOLD - PLATEAU_BLEND) {
            double pn = CaveMath.perlin3D(
                worldX / PLATEAU_SCALE, 0.2, worldZ / PLATEAU_SCALE,
                seed, PLATEAU_SALT) * 2.0;
            int plateauY = PLATEAU_BASE + (int) Math.round(pn * PLATEAU_AMP);
            if (n >= PLATEAU_THRESHOLD) {
                floorY = plateauY;
            } else {
                double nRight = CaveMath.fbm3D(
                    (wx2 + PLATEAU_GRADIENT_STEP) / FLOOR_NOISE_SCALE,
                    0.1, wz2 / FLOOR_NOISE_SCALE,
                    seed, FLOOR_NOISE_SALT, 3, 2.0, 0.5) * 2.0;
                if (Math.abs(nRight - n) > PLATEAU_HARD_GRADIENT) {
                    // 噪声跳变剧烈：保留硬崖。
                    floorY = lowY;
                } else {
                    double t = (n - (PLATEAU_THRESHOLD - PLATEAU_BLEND))
                        / PLATEAU_BLEND;
                    double s = t * t * (3.0 - 2.0 * t);
                    floorY = (int) Math.round(lowY + (plateauY - lowY) * s);
                }
            }
        }
        // 高频风格化：小幅快速起伏，作用在整个底部（含湖盆与平台）。
        double dn = CaveMath.perlin3D(
            worldX / FLOOR_DETAIL_SCALE, 0.3, worldZ / FLOOR_DETAIL_SCALE,
            seed, FLOOR_DETAIL_SALT) * 2.0;
        floorY += (int) Math.round(dn * FLOOR_DETAIL_AMP);
        if (floorY < 2) {
            floorY = 2;
        }
        if (floorY > yMax) {
            floorY = Math.max(2, yMax - 1);
        }

        int pillarIdx = hall.pillarIndex(worldX, worldZ);
        if (pillarIdx >= 0) {
            carveMegaHallPillar(hall, pillarIdx, worldX, worldZ,
                floorY, yMax,
                localX, localZ, blocks, meta, worldHeight, seed);
            return;
        }

        if (floorY < FLOOR_WATER_LEVEL) {
            // 压到 15 以下的区域挖成盆地并灌水，水面统一在 y=15。
            int surface = Math.min(FLOOR_WATER_LEVEL, yMax);
            for (int y = floorY; y <= surface; y++) {
                setWater(blocks, meta, localX, localZ, y, worldHeight);
            }
            for (int y = surface + 1; y <= yMax; y++) {
                setAir(blocks, meta, localX, localZ, y, worldHeight);
            }
        } else {
            // 干地：floorY 是地面实体层，空气从 floorY+1 开始，
            // 避免岸边 y=15 被挖成空气缝。
            for (int y = floorY + 1; y <= yMax; y++) {
                setAir(blocks, meta, localX, localZ, y, worldHeight);
            }
        }
    }

    /** 巨型石柱：柱顶/柱脚外扩融合，柱身做侵蚀。 */
    private static void carveMegaHallPillar(CaveMegaHall hall, int pillarIdx,
                                            int worldX, int worldZ,
                                            int floorY, int yMax,
                                            int localX, int localZ,
                                            net.minecraft.block.Block[] blocks,
                                            byte[] meta,
                                            int worldHeight, long seed) {
        double px = hall.pillarX[pillarIdx];
        double pz = hall.pillarZ[pillarIdx];
        double dx = worldX + 0.5 - px;
        double dz = worldZ + 0.5 - pz;
        int span = Math.max(1, yMax - floorY + 1);
        // 半径纵向每 4 格采样一次再插值，减少 Perlin 调用。
        int step = 4;
        int sampleCount = span / step + 2;
        double[] radii = new double[sampleCount];
        for (int k = 0; k < sampleCount; k++) {
            int sy = Math.min(yMax, floorY + k * step);
            radii[k] = pillarRadiusAt(
                hall, pillarIdx, worldX, sy, worldZ, seed);
        }
        for (int y = floorY; y <= yMax; y++) {
            double scale = pillarScale(y, floorY, span);
            int k = (y - floorY) / step;
            if (k >= sampleCount - 1) {
                k = sampleCount - 2;
            }
            double f = (double) ((y - floorY) - k * step) / step;
            double r = (radii[k]
                + (radii[k + 1] - radii[k]) * f) * scale;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist >= r) {
                // 柱体之外：恢复该列的正常洞厅地形（噪声地面/水面）。
                if (floorY < FLOOR_WATER_LEVEL) {
                    int surface = Math.min(FLOOR_WATER_LEVEL, yMax);
                    if (y <= surface) {
                        setWater(blocks, meta, localX, localZ, y, worldHeight);
                    } else {
                        setAir(blocks, meta, localX, localZ, y, worldHeight);
                    }
                } else if (y > floorY) {
                    setAir(blocks, meta, localX, localZ, y, worldHeight);
                } else {
                    // y == floorY：保留实体地面
                }
            } else {
                int idx = (localX * 16 + localZ) * worldHeight + y;
                byte v = CaveMath.rockVariant3D(worldX, y, worldZ, seed);
                if (v == 0) {
                    blocks[idx] = Blocks.stone;
                    meta[idx] = 0;
                } else {
                    blocks[idx] = ModBlocks.STONE.get();
                    meta[idx] = (byte) (v == 1 ? 1 : (v == 2 ? 3 : 5));
                }
            }
        }
    }

    /** 柱体径向半径：用 Perlin 沿圆周和高度做平滑起伏，形成不规则多边柱。 */
    private static double pillarRadiusAt(CaveMegaHall hall, int idx,
                                         int wx, int wy, int wz, long seed) {
        double dx = wx + 0.5 - hall.pillarX[idx];
        double dz = wz + 0.5 - hall.pillarZ[idx];
        double angle = Math.atan2(dz, dx);
        double ca = Math.cos(angle) * 3.0;
        double sa = Math.sin(angle) * 3.0;
        double r1 = CaveMath.perlin3D(
            ca, wy / 9.0, sa, seed, 0xE1 + idx);
        double r2 = CaveMath.perlin3D(
            ca * 2.0, wy / 13.0, sa * 2.0, seed, 0xE2 + idx);
        return hall.pillarHalf * (1.0 + 0.22 * r1 + 0.12 * r2);
    }

    /** 柱顶/柱脚 8 格内向外扩 50%，与天花板/地面融合。 */
    private static double pillarScale(int y, int floorY, int span) {
        int zone = 8;
        double t = (double) (y - floorY) / span;
        double flare = 0.0;
        double e = (double) zone / span;
        if (t < e) {
            flare = 0.5 * (1.0 - t / e);
        } else if (t > 1.0 - e) {
            flare = 0.5 * ((t - (1.0 - e)) / e);
        }
        return 1.0 + flare;
    }

    /** 当前方块同一层是否在湖心水体的石头壳范围内。 */
    private static boolean isNearLakeWater(CaveChamber ch,
                                           int worldX, int worldY, int worldZ,
                                           double wall) {
        for (int dz = -LAKE_SHELL_THICKNESS;
             dz <= LAKE_SHELL_THICKNESS; dz++) {
            for (int dx = -LAKE_SHELL_THICKNESS;
                 dx <= LAKE_SHELL_THICKNESS; dx++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                if (Math.abs(dx) + Math.abs(dz) > LAKE_SHELL_THICKNESS) {
                    continue;
                }
                if (ch.inside(
                    worldX + dx + 0.5, worldY + 0.5,
                    worldZ + dz + 0.5, wall
                )) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void setAir(net.minecraft.block.Block[] blocks, byte[] meta,
                               int localX, int localZ, int y, int worldHeight) {
        if (y <= 0 || y >= worldHeight) {
            return;
        }
        int idx = (localX * 16 + localZ) * worldHeight + y;
        blocks[idx] = Blocks.air;
        meta[idx] = 0;
    }

    private static void setWater(net.minecraft.block.Block[] blocks,
                                 byte[] meta,
                                 int localX, int localZ, int y,
                                 int worldHeight) {
        if (y <= 0 || y >= worldHeight) {
            return;
        }
        int idx = (localX * 16 + localZ) * worldHeight + y;
        blocks[idx] = Blocks.water;
        meta[idx] = 0;
    }

    private static void setStone(net.minecraft.block.Block[] blocks,
                                 byte[] meta,
                                 int localX, int localZ, int y,
                                 int worldHeight) {
        if (y <= 0 || y >= worldHeight) {
            return;
        }
        int idx = (localX * 16 + localZ) * worldHeight + y;
        blocks[idx] = Blocks.stone;
        meta[idx] = 0;
    }

    private static boolean isAir(net.minecraft.block.Block block) {
        return block == null || block == Blocks.air;
    }
}
