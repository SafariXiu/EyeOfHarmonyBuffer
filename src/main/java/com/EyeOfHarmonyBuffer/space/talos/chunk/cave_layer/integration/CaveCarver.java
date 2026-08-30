package com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.integration;

import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveChamber;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveChunkData;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveEntrance;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveGenerator;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveMegaHall;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveMath;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveSegment;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverBodyData;
import ganymedes01.etfuturum.ModBlocks;
import gregtech.api.GregTechAPI;
import net.minecraft.block.Block;
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
    /** 入口竖井向下延伸量（blocks）：挖到节点下方数格，保证钻入连接段，
     *  消除「竖井底与隧道顶之间的石头缝隙」。 */
    private static final int ENTRANCE_SHAFT_EXTEND = 4;
    /** 水体保护厚度：河床 / 海床下方至少保留的实心层（防止沙层悬空）。 */
    private static final int WATER_PROTECT_BUFFER = 10;
    /** 河流保护掩码阈值：放宽后河岸两侧也会被包住。 */
    private static final double RIVER_PROTECT_MASK = 0.35;

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
     * @param waterSurfaceY 该列水面高度 Y（水场输出；无水列传
     *                    Integer.MIN_VALUE，入口开口不受水面限制）
     * @param riverMask  河流影响掩码（0~1）
     * @param body       命中的水体（无则 null）
     * @param worldHeight 世界高度（方块数组索引用）
     */
    public static void carveColumn(int worldX, int worldZ,
                                   int localX, int localZ,
                                   int topSolidY,
                                   int waterSurfaceY,
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

        // 入口：先雕刻（入口优先于洞厅）。
        // 入口就是要挖穿地表开口，所以只挡「列顶低于水面」（会开在水下 / 水线）；
        // 不再因 riverMask > 0.35 的河岸带跳过——否则 usableLandmarkColumn 放行、
        // 装饰器也铺了碎石环，但洞根本没刻出来（用户看到装饰物却没有洞口）。
        // 真正的水体列（body != null）其列顶低于水面，会被 topSolidY < waterSurfaceY+1 拦住。
        // 注意：入口可能落在洞厅上方（洞厅顶 ≤64，入口贴近地表），
        // 若洞厅循环在前并 return，入口会被跳过 → 有装饰物却没洞口。
        boolean entranceCarved = false;
        for (CaveEntrance e : data.entrances) {
            if (topSolidY < waterSurfaceY + 1) {
                continue;
            }
            if (carveEntranceAt(e, worldX, worldZ, localX, localZ,
                topSolidY, blocks, meta, worldHeight)
                && topSolidY > e.y) {
                // 入口通道是真正的 CaveSegment（buildEntrancePassage），
                // 由主雕刻循环用 sampleExcess 雕刻（洞壁噪声 + 半径变化，
                // 与内部洞穴完全一致）。这里只挖地表开口，不再钳制 maxY，
                // 否则主循环不雕刻基座以上（→ 地表）的通道段。
                entranceCarved = true;
                break;
            }
        }

        // 洞厅快速雕刻：整列按几何直接填，不走逐格噪声。
        // 若入口已刻，洞厅继续填下层（洞厅顶 ≤64，入口在下层上方，互不重叠），
        // 且不 return——让下方普通雕刻继续刻入口到洞厅网络的连接段。
        for (CaveMegaHall hall : data.megaHalls) {
            int[] span = new int[2];
            if (!hall.verticalSpan(worldX, worldZ, maxY, span)) {
                continue;
            }
            carveMegaHallColumn(hall, worldX, worldZ, span[1],
                localX, localZ, blocks, meta, worldHeight, seed);
            // 湖连接管：允许在洞厅内继续雕刻，从湖底/侧壁穿进湖体
            if (!hall.isPillarColumn(worldX, worldZ)) {
                carveLakePipesThroughMegaHall(
                    worldX, worldZ, maxY,
                    localX, localZ, blocks, meta, worldHeight, seed, data);
            }
            if (entranceCarved) {
                break; // 入口已刻：洞厅填完，继续下方普通雕刻
            }
            return;
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

        // 干湿隔离兜底：盆地 / 禁干带的列，干洞段只能在上层带（46+）雕刻。
        // 洞厅专属区例外：该区域内干洞可全深度雕刻。
        int shallowCellX = Math.floorDiv(worldX, 256);
        int shallowCellZ = Math.floorDiv(worldZ, 256);
        boolean columnShallow = !CaveGenerator.isHallZoneCell(
            shallowCellX, shallowCellZ, seed)
            && CaveGenerator.isShallowOnlyCell(
                shallowCellX, shallowCellZ, seed);

        for (int y = 1; y <= maxY; y++) {
            double wall = (CaveMath.valueNoise3D(
                worldX, y, worldZ, seed, NOISE_SCALE, NOISE_SALT) - 0.5)
                * 2.0 * WALL_AMP;
            double excess = Double.NEGATIVE_INFINITY;
            boolean lakeWater = false;
            boolean lakeSealed = false;
            boolean lakeHandled = false;
            CaveSegment bestSeg = null;
            CaveSegment bestAquiferSeg = null;
            double bestAquiferExcess = Double.NEGATIVE_INFINITY;
            CaveSegment bestPierceSeg = null;
            double bestPierceExcess = Double.NEGATIVE_INFINITY;

            if (near != null) {
                for (CaveSegment seg : near) {
                    if (y < seg.minY || y > seg.maxY) {
                        continue;
                    }
                    double e = seg.sampleExcess(worldX, y, worldZ, wall);
                    if (e > excess) {
                        excess = e;
                        bestSeg = seg;
                    }
                    if (seg.aquifer && e > bestAquiferExcess) {
                        bestAquiferSeg = seg;
                        bestAquiferExcess = e;
                    }
                    if (seg.piercesLakeShell && e > bestPierceExcess) {
                        bestPierceSeg = seg;
                        bestPierceExcess = e;
                    }
                }
            }

            // 地下湖：先铺石头壳（湖底 + 同一层四邻），再放水。
            for (CaveChamber ch : data.chambers) {
                if (columnShallow) {
                    break;
                }
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
                            setRock(blocks, meta, localX, localZ,
                                by, worldHeight, worldX, by, worldZ, seed);
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
                    // 含水连接管从这列穿过时不再封边，让管道凿穿外壳接进湖里。
                    if (bestPierceSeg != null
                        && bestPierceSeg == bestSeg
                        && bestPierceExcess > 0.0) {
                        lakeHandled = true;
                    } else {
                        setRock(blocks, meta, localX, localZ, y,
                            worldHeight, worldX, y, worldZ, seed);
                        lakeSealed = true;
                    }
                    lakeHandled = true;
                    break;
                }
            }

            if (!lakeHandled && chamberNear && !columnShallow) {
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
                CaveSegment waterSeg = bestAquiferSeg != null
                    && bestAquiferExcess > 0.0
                    ? bestAquiferSeg : bestSeg;
                if (waterSeg != null && waterSeg.aquifer) {
                    int waterLevel = waterSeg.waterLevelY;
                    if (waterSeg.piercesLakeShell) {
                        // 湖连接管：远离接全水节点的那头时，水线压到目标湖面，
                        // 避免管道半径把水带高；远端点附近保持满水接暗河。
                        double dxp = worldX - waterSeg.pipeFarX;
                        double dzp = worldZ - waterSeg.pipeFarZ;
                        if (dxp * dxp + dzp * dzp > 80.0 * 80.0) {
                            waterLevel = Math.min(
                                waterLevel, waterSeg.lakeSurfaceY);
                        }
                    }
                    if (waterSeg.fullySubmerged
                        || y <= waterLevel) {
                        setWater(blocks, meta, localX, localZ, y,
                            worldHeight);
                    } else {
                        setAir(blocks, meta, localX, localZ, y,
                            worldHeight);
                    }
                } else if (!columnShallow
                    || y >= CaveGenerator.DRY_UPPER_MIN_Y) {
                    setAir(blocks, meta, localX, localZ, y, worldHeight);
                }
            }
        }
    }

    /**
     * 雕刻入口的地表开口：返回该列是否属于入口开口范围（是则已雕刻）。
     *
     * 新架构：入口通道是一条真正的 CaveSegment（buildEntrancePassage），
     * 由主雕刻循环用 sampleExcess 雕刻——带洞壁噪声、半径变化，与内部洞穴
     * 完全一致。这里只负责「地表开口」：在开口列挖一个可走进的漏斗/坡道坑，
     * 深度到通道顶部（surfaceY - 3 附近），让玩家能从地表看到并走进通道。
     */
    private static boolean carveEntranceAt(CaveEntrance e,
                                           int worldX, int worldZ,
                                           int localX, int localZ,
                                           int topSolidY,
                                           net.minecraft.block.Block[] blocks,
                                           byte[] meta,
                                           int worldHeight) {
        double dx = worldX - e.x;
        double dz = worldZ - e.z;
        double d = Math.sqrt(dx * dx + dz * dz);
        // 地表开口半径：漏斗 / 坡道更宽（可走进），竖井 / 天坑较窄。
        double mouthR = (e.type == CaveEntrance.TYPE_FUNNEL
            || e.type == CaveEntrance.TYPE_RAMP)
            ? e.radius + 3.0 : e.radius + 1.0;
        if (d > mouthR) {
            return false;
        }
        // 开口坑深度：中心挖到通道顶部附近（surfaceY - 3），边缘浅（漏斗坑）。
        // 通道段顶部也在 surfaceY - 3，两者重叠衔接。
        double topOfPassage = e.surfaceY - 3.0;
        double centerDepth = topSolidY - topOfPassage;
        if (centerDepth < 2.0) {
            centerDepth = 2.0;
        }
        // 锥形：中心深、边缘浅
        double t = mouthR > 0.0 ? Math.max(0.0, 1.0 - d / mouthR) : 1.0;
        int bottomY = topSolidY - (int) Math.ceil(centerDepth * t);
        if (bottomY < 1) {
            bottomY = 1;
        }
        for (int y = bottomY; y <= topSolidY; y++) {
            setAir(blocks, meta, localX, localZ, y, worldHeight);
        }
        return true;
    }



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
        setRock(blocks, meta, localX, localZ, 1,
            worldHeight, worldX, 1, worldZ, seed);

        // 1) 主厅地板（柱子/墙的基座高度）。
        int floorY = hall.floorY(worldX, worldZ);
        if (floorY > yMax) {
            floorY = Math.max(2, yMax - 1);
        }

        // 显式湖环（岛体 + 实心湖面 + 过渡带）：岛顶高于水位、中心平地；
        // 湖环内强制湖床灌水 → 岛必然在湖中央；过渡带向周围地形平滑融合。
        if (hall.isInIslandLake(worldX, worldZ)) {
            floorY = hall.islandFloorY(worldX, worldZ);
        }

        // 2) 结构候选：最近柱子 + 所有墙（各自按真实雕刻半径判定归属）。
        //    —— 用「柱体 ∪ 墙体」的并集判实心：谁覆盖该列就是石头，
        //    柱子不会被墙挖空（柱子体心始终在柱半径内），多墙交汇也能全部连通。
        int pillarIdx = -1;
        double pillarDist = Double.MAX_VALUE;
        for (int i = 0; i < hall.pillarCount; i++) {
            double d = Math.hypot(
                worldX + 0.5 - hall.pillarX[i],
                worldZ + 0.5 - hall.pillarZ[i]);
            if (d < pillarDist) {
                pillarDist = d;
                pillarIdx = i;
            }
        }
        if (pillarDist > hall.pillarHalf * 2.0) {
            pillarIdx = -1; // 超出柱子最大雕刻半径（噪声 1.34 × 顶/脚外扩 1.5 ≈ 2.0）
        }

        // 墙候选：所有到墙段距离 <= wallHalf*2（覆盖雕刻半径含顶/脚外扩）的墙
        java.util.List<Integer> wallCands =
            new java.util.ArrayList<Integer>();
        for (int i = 0; i < hall.wallCount; i++) {
            double d = ptSegDist(worldX + 0.5, worldZ + 0.5,
                hall.pillarX[hall.wallA[i]], hall.pillarZ[hall.wallA[i]],
                hall.pillarX[hall.wallB[i]], hall.pillarZ[hall.wallB[i]]);
            if (d <= hall.wallHalf * 2.0) {
                wallCands.add(i);
            }
        }

        if (pillarIdx < 0 && wallCands.isEmpty()) {
            // 3) 无结构：湖 / 干地。
            if (floorY < CaveMegaHall.LAKE_WATER_LEVEL) {
                int surface = Math.min(CaveMegaHall.LAKE_WATER_LEVEL, yMax);
                for (int y = floorY; y <= surface; y++) {
                    setWater(blocks, meta, localX, localZ, y, worldHeight);
                }
                for (int y = surface + 1; y <= yMax; y++) {
                    setAir(blocks, meta, localX, localZ, y, worldHeight);
                }
            } else {
                for (int y = floorY + 1; y <= yMax; y++) {
                    setAir(blocks, meta, localX, localZ, y, worldHeight);
                }
            }
            return;
        }

        // 4) 结构列：逐 y 按并集雕刻（柱 ∪ 墙）。半径沿高度每 4 格采样再插值。
        int span = Math.max(1, yMax - floorY + 1);
        int step = 4;
        int sampleCount = span / step + 2;
        double[] pillarR = pillarIdx >= 0 ? new double[sampleCount] : null;
        double[][] wallR = wallCands.isEmpty() ? null
            : new double[wallCands.size()][sampleCount];
        for (int k = 0; k < sampleCount; k++) {
            int sy = Math.min(yMax, floorY + k * step);
            if (pillarIdx >= 0) {
                pillarR[k] = pillarRadiusAt(hall, pillarIdx,
                    worldX, sy, worldZ, seed);
            }
            if (wallR != null) {
                for (int w = 0; w < wallCands.size(); w++) {
                    wallR[w][k] = wallRadiusAt(hall, wallCands.get(w),
                        worldX, sy, worldZ, floorY, yMax, seed);
                }
            }
        }
        for (int y = floorY; y <= yMax; y++) {
            double scale = pillarScale(y, floorY, span);
            int k = (y - floorY) / step;
            if (k >= sampleCount - 1) {
                k = sampleCount - 2;
            }
            double f = (double) ((y - floorY) - k * step) / step;
            boolean rock = false;
            if (pillarIdx >= 0) {
                double r = (pillarR[k] + (pillarR[k + 1] - pillarR[k]) * f)
                    * scale;
                if (pillarDist < r) {
                    rock = true;
                }
            }
            if (!rock && wallR != null) {
                for (int w = 0; w < wallCands.size(); w++) {
                    int wi = wallCands.get(w);
                    double dw = ptSegDist(worldX + 0.5, worldZ + 0.5,
                        hall.pillarX[hall.wallA[wi]], hall.pillarZ[hall.wallA[wi]],
                        hall.pillarX[hall.wallB[wi]], hall.pillarZ[hall.wallB[wi]]);
                    double r = (wallR[w][k] + (wallR[w][k + 1] - wallR[w][k]) * f)
                        * scale;
                    if (dw < r) {
                        rock = true;
                        break;
                    }
                }
            }
            if (rock) {
                int idx = (localX * 16 + localZ) * worldHeight + y;
                byte rockV = CaveMath.rockVariant3D(worldX, y, worldZ, seed);
                putRockVariant(blocks, meta, idx, rockV);
            } else if (y > floorY) {
                if (floorY < CaveMegaHall.LAKE_WATER_LEVEL) {
                    int surface = Math.min(CaveMegaHall.LAKE_WATER_LEVEL, yMax);
                    if (y <= surface) {
                        setWater(blocks, meta, localX, localZ, y, worldHeight);
                    } else {
                        setAir(blocks, meta, localX, localZ, y, worldHeight);
                    }
                } else {
                    setAir(blocks, meta, localX, localZ, y, worldHeight);
                }
            }
        }
    }

    /**
     * 墙在 (wx, wy, wz) 处的雕刻半径：沿墙 A→B 走向 + 垂直 + 高度做双层
     * Perlin 起伏（振幅 0.22 / 0.12，与石柱 pillarRadiusAt 一致），
     * 让墙边缘像天然岩脊一样不规则。调用方再乘 pillarScale 做顶/脚平滑。
     */
    private static double wallRadiusAt(CaveMegaHall hall, int wallIdx,
                                       int wx, int wy, int wz,
                                       int floorY, int yMax, long seed) {
        int a = hall.wallA[wallIdx];
        int b = hall.wallB[wallIdx];
        double ax = hall.pillarX[a];
        double az = hall.pillarZ[a];
        double bx = hall.pillarX[b];
        double bz = hall.pillarZ[b];
        double vx = bx - ax, vz = bz - az;
        double len = Math.hypot(vx, vz);
        if (len < 1e-9) {
            return hall.wallHalf;
        }
        double dxU = vx / len, dzU = vz / len;   // 沿墙方向
        double dxV = -dzU, dzV = dxU;            // 垂直墙方向
        double rx = wx + 0.5 - ax;
        double rz = wz + 0.5 - az;
        double u = Math.max(0.0, Math.min(len, rx * dxU + rz * dzU));
        double v = rx * dxV + rz * dzV;
        double r1 = CaveMath.perlin3D(
            u / 24.0, wy / 9.0, v / 24.0, seed, 0xE7 + wallIdx);
        double r2 = CaveMath.perlin3D(
            u / 14.0, wy / 13.0, v / 14.0, seed, 0xE8 + wallIdx);
        // 门洞开度（拱门形）：门洞处半径乘 0（墙体挖空成拱门缺口），
        // 拱门轮廓沿墙×高度，边缘 smoothstep+Perlin 渐变
        double door = hall.wallDoorFactor(
            wallIdx, u, wy, floorY, yMax, seed);
        return hall.wallHalf * (1.0 + 0.22 * r1 + 0.12 * r2) * door;
    }
    /** 洞厅列内补刻含水-湖连接管：穿过洞厅湖底/侧壁进入湖体。 */
    private static void carveLakePipesThroughMegaHall(
        int worldX, int worldZ, int maxY,
        int localX, int localZ,
        net.minecraft.block.Block[] blocks, byte[] meta,
        int worldHeight, long seed, CaveChunkData data
    ) {
        for (CaveSegment seg : data.segments) {
            if (!seg.piercesLakeShell) {
                continue;
            }
            if (worldX < seg.minX || worldX > seg.maxX
                || worldZ < seg.minZ || worldZ > seg.maxZ) {
                continue;
            }
            int yMin = Math.max(1, (int) Math.ceil(seg.minY));
            int yMax = Math.min(maxY, (int) Math.floor(seg.maxY));
            for (int y = yMin; y <= yMax; y++) {
                double wall = (CaveMath.valueNoise3D(
                    worldX, y, worldZ, seed, NOISE_SCALE, NOISE_SALT) - 0.5)
                    * 2.0 * WALL_AMP;
                double e = seg.sampleExcess(worldX, y, worldZ, wall);
                if (e > 0.0) {
                    // 洞厅内湖连接管水线强制压到洞厅湖面，避免水漫过湖。
                    int wl = Math.min(
                        seg.waterLevelY, seg.lakeSurfaceY);
                    if (seg.fullySubmerged || y <= wl) {
                        setWater(blocks, meta, localX, localZ, y,
                            worldHeight);
                    } else {
                        setAir(blocks, meta, localX, localZ, y,
                            worldHeight);
                    }
                }
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
                if (floorY < CaveMegaHall.LAKE_WATER_LEVEL) {
                    int surface = Math.min(CaveMegaHall.LAKE_WATER_LEVEL, yMax);
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
                putRockVariant(blocks, meta, idx, v);
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

    /** 点到线段 (ax,az)-(bx,bz) 的水平距离。 */
    private static double ptSegDist(double px, double pz,
                                    double ax, double az,
                                    double bx, double bz) {
        double vx = bx - ax, vz = bz - az;
        double l2 = vx * vx + vz * vz;
        if (l2 < 1e-9) {
            return Math.hypot(px - ax, pz - az);
        }
        double t = Math.max(0.0, Math.min(1.0,
            ((px - ax) * vx + (pz - az) * vz) / l2));
        return Math.hypot(px - (ax + t * vx), pz - (az + t * vz));
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

    /** 填充大块岩性石头：普通石头为主，花岗岩/闪长岩/安山岩为辅。 */
    private static void setRock(net.minecraft.block.Block[] blocks,
                                byte[] meta,
                                int localX, int localZ, int y,
                                int worldHeight,
                                int wx, int wy, int wz, long seed) {
        if (y <= 0 || y >= worldHeight) {
            return;
        }
        int idx = (localX * 16 + localZ) * worldHeight + y;
        byte v = CaveMath.rockVariant3D(wx, wy, wz, seed);
        putRockVariant(blocks, meta, idx, v);
    }

    /** 按岩性编码写方块：浅层四种 / 深层深板岩、凝灰岩。 */
    private static void putRockVariant(net.minecraft.block.Block[] blocks,
                                       byte[] meta, int idx, byte v) {
        if (v == 4) {
            Block b = ModBlocks.DEEPSLATE.get();
            blocks[idx] = b != null ? b : Blocks.stone;
            meta[idx] = 0;
        } else if (v == 5) {
            Block b = ModBlocks.TUFF.get();
            blocks[idx] = b != null ? b : Blocks.stone;
            meta[idx] = 0;
        } else if (v == 0) {
            // 玄武岩
            blocks[idx] = GregTechAPI.sBlockStones;
            meta[idx] = 8;
        } else if (v == 1) {
            // 黑花岗岩
            blocks[idx] = GregTechAPI.sBlockGranites;
            meta[idx] = 0;
        } else if (v == 2) {
            // 红花岗岩
            blocks[idx] = GregTechAPI.sBlockGranites;
            meta[idx] = 8;
        } else {
            // 大理石
            blocks[idx] = GregTechAPI.sBlockStones;
            meta[idx] = 0;
        }
    }

    private static boolean isAir(net.minecraft.block.Block block) {
        return block == null || block == Blocks.air;
    }
}
