package com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.integration;

import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveChamber;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveChunkData;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveEntrance;
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
        for (CaveChamber ch : data.chambers) {
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

        boolean waterProtected = riverMask > 0.7 || body != null;
        int maxY = waterProtected
            ? topSolidY - 2
            : topSolidY - SURFACE_CAP;
        if (maxY < 1) {
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
            if (worldX + 0.5 >= ch.minX && worldX + 0.5 <= ch.maxX
                && worldZ + 0.5 >= ch.minZ && worldZ + 0.5 <= ch.maxZ) {
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
            if (chamberNear) {
                for (CaveChamber ch : data.chambers) {
                    if (ch.inside(worldX + 0.5, y + 0.5, worldZ + 0.5, wall)) {
                        excess = 1.0;
                        break;
                    }
                }
            }

            if (excess > 0.0) {
                setAir(blocks, meta, localX, localZ, y, worldHeight);
            }
        }
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
}
