package com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.integration;

import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveChamber;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveChunkData;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveEntrance;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveMegaHall;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveMath;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveSegment;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverBodyData;
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
    /** 洞厅底部湖/地中频噪声尺度与盐。 */
    private static final double MEGA_HALL_LAKE_SCALE = 48.0;
    private static final int MEGA_HALL_LAKE_SALT = 0xB2;

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
            if (hall.isPillarColumn(worldX, worldZ)) {
                return; // 巨型石柱整列保留
            }
            int[] span = new int[2];
            if (!hall.verticalSpan(worldX, worldZ, maxY, span)) {
                continue;
            }
            carveMegaHallColumn(hall, worldX, worldZ, span[0], span[1],
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
                                            int yMin, int yMax,
                                            int localX, int localZ,
                                            net.minecraft.block.Block[] blocks,
                                            byte[] meta,
                                            int worldHeight, long seed) {
        double n = CaveMath.valueNoise3D(
            worldX, 0, worldZ, seed,
            MEGA_HALL_LAKE_SCALE, MEGA_HALL_LAKE_SALT);
        int raised = (int) (n * 5.0);
        // 洞厅底平面 + 中频噪声；只在靠近椭球边缘时被 yMin 约束。
        int baseFloor = (int) Math.floor(hall.cy - hall.ry) + 1;
        int floorY = Math.max(baseFloor + raised, yMin);
        if (floorY > yMax) {
            floorY = yMax;
        }

        // 先不填湖：只保留噪声抬升的地面，等噪声和原始地形调好再加水。
        for (int y = floorY; y <= yMax; y++) {
            setAir(blocks, meta, localX, localZ, y, worldHeight);
        }
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
