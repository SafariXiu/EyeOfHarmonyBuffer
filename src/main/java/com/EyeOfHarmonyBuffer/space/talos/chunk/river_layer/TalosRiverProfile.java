package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer;

import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.api.MacroPackageId;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.api.TalosRiverSystem;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverBodyData;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverBodyType;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverPoint;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.smoothing.WaterBodySmoothing;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.smoothing.WaterBodySmoothingContext;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.smoothing.WaterBodySmoothings;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.smoothing.SmoothingMath;
import micdoodle8.mods.galacticraft.api.prefab.core.BlockMetaPair;

import java.util.List;

public final class TalosRiverProfile {

    private TalosRiverProfile() {}

    /** 河床微起伏：低频噪声尺度与幅度（格）。 */
    private static final double RIVERBED_RELIEF_SCALE = 24.0;
    private static final double RIVERBED_RELIEF_AMP = 1.5;

    /** 湿地边缘的过渡带宽（blocks）。 */
    private static final double WETLAND_TRANSITION_BLOCKS = 10.0;

    /**
     * 高度场雕刻用的完整河床目标高度（含源头湖 / 暗河井）。
     *
     * 与 computeRiverBedY 的区别：
     *   - 在 computeRiverBedY 基础上叠加源头湖的湖盆与暗河井深度；
     *   - 雕刻门槛：只在河谷内或源头湖范围内下挖，
     *     其它位置返回 baseHeightD（原高度）；
     *   - 返回值为"河床目标 Y"，调用方用 min(height, bedY) 落地，
     *     从而让河谷两侧自然成坡，而不是事后垂直切方块。
     */
    public static double computeChannelBedY(int worldX, int worldZ,
                                            int worldSeedInt,
                                            double baseHeightD,
                                            int seaLevel,
                                            TalosRiverSystem.HydroSample hydro,
                                            MacroPackageId macroId) {
        double dist = hydro.distance;
        double valleyWidth = hydro.widthValley;
        double mask = hydro.mask;

        // 宏群系水体预设：源头湖与通用水体（湖/湿地/穿河湖/牛轭湖）共用。
        // 注意必须在 hasSource 之外也初始化，否则独立水体永远拿不到预设。
        MacroPackageRegistry.SourceLakePreset lakePreset = null;
        boolean inLakeZone = false;
        if (macroId != null && macroId != MacroPackageId.OCEANIC) {
            lakePreset = MacroPackageRegistry.get(macroId).sourceLake();
        }

        double sx = hydro.sourceX;
        double sz = hydro.sourceZ;
        double sampleX = worldX + 0.5;
        double sampleZ = worldZ + 0.5;
        double radialFromSource = 0.0;
        double waterR = 0.0;
        double beachR = 0.0;
        double slopeR = 0.0;

        if (hydro.hasSource && !Double.isNaN(sx) && !Double.isNaN(sz)) {
            double dxs = sampleX - sx;
            double dzs = sampleZ - sz;
            radialFromSource = Math.sqrt(dxs * dxs + dzs * dzs);

            if (lakePreset != null) {
                // 湖域判定与湖盆剖面共用同一个角噪声半径，
                // 避免“固定完美圆 > 实际不规则外坡”留下一圈无人管辖的环形水道。
                double theta = Math.atan2(sampleZ - sz, sampleX - sx);
                double ampScale = lakePreset.irregularityAmp / 0.18;
                double radiusFactor = 1.0
                    + ampScale * 0.12 * angularPerturbation(
                        theta, (long) sx, (long) sz);
                double minFactor = 1.0 - 0.12 * ampScale;
                double maxFactor = 1.0 + 0.12 * ampScale;
                if (radiusFactor < minFactor) radiusFactor = minFactor;
                if (radiusFactor > maxFactor) radiusFactor = maxFactor;

                waterR = lakePreset.baseRadius * radiusFactor;
                beachR = waterR + lakePreset.beachWidth;
                slopeR = beachR + lakePreset.outerSlopeWidth;
                if (radialFromSource <= slopeR) {
                    inLakeZone = true;
                }
            }
        }

        // 通用水体（独立湖 / 湿地 / 终端湖 / 穿河湖 / 牛轭湖）
        boolean inBodyZone = false;
        if (hydro.body != null
            && macroId != null
            && macroId != MacroPackageId.OCEANIC) {
            inBodyZone = inBodyZone(hydro, lakePreset, sampleX, sampleZ);
        }

        // 湖区 / 水体独立于河流 mask 雕刻；两者都不在才要求河流影响
        if (!inBodyZone && !inLakeZone && (mask <= 0.0 || dist == Double.MAX_VALUE
            || valleyWidth <= 0.0 || dist > valleyWidth)) {
            return baseHeightD;
        }

        if (macroId == null || macroId == MacroPackageId.OCEANIC) {
            return baseHeightD;
        }

        double riverBedYd = computeRiverBedY(baseHeightD, seaLevel, hydro, macroId);

        // 纵向深度倍率：湖泊段 / 入海口 / 源头按 progress 平滑缩放河床深度
        double depthScale = hydro.depthScale;
        if (depthScale < 0.0) {
            depthScale = 0.0;
        } else if (depthScale > 1.0) {
            depthScale = 1.0;
        }
        double depthAbove = seaLevel - riverBedYd;
        if (depthAbove > 0.0) {
            riverBedYd = seaLevel - depthAbove * depthScale;
        }

        // 原语义：床面不低于海平面则该列不雕刻（河谷最外圈 / 入海口边缘）。
        // 必须先于微起伏判断，否则起伏 + 下方钳制会把原本不挖的列也挖成 1 格深。
        // 注意：水体/源头湖区域不受此限制——湖心列在河床计算里没有河道影响，
        // riverBedYd 会等于（或高于）海平面，必须在下面由水体挖坑逻辑接管。
        if (!inBodyZone && !inLakeZone && riverBedYd >= seaLevel) {
            return baseHeightD;
        }

        // 河床微起伏：±1.5 格低频噪声。
        // 只作用于全深河段（depthScale≈1）；入海口 / 源头抬升 ramp 内完全归零，
        // 避免 floor 取整把平滑斜坡打成台阶（入海口硬切）。
        double relief = (valueNoise(
            worldX, worldZ, worldSeedInt,
            RIVERBED_RELIEF_SCALE, 0x3C1A9E77) - 0.5)
            * 2.0 * RIVERBED_RELIEF_AMP;
        double reliefScale = smoothstep01((depthScale - 0.92) / 0.08);
        riverBedYd += relief * reliefScale;
        if (riverBedYd > seaLevel - 1.0) {
            riverBedYd = seaLevel - 1.0;
        }
        if (riverBedYd < 1.0) {
            riverBedYd = 1.0;
        }

        if (inBodyZone && hydro.body != null && lakePreset != null) {
            riverBedYd = applyBodyBasin(
                worldX, worldZ, worldSeedInt,
                sampleX, sampleZ,
                baseHeightD, seaLevel,
                hydro, hydro.body, lakePreset,
                riverBedYd,
                relief
            );
        }

        // 源头湖的河道开口统一用河谷宽度（与水体结构一致），
        // 否则入湖口会明显比河道窄。
        double riverCutWidth = Math.max(hydro.widthCore, hydro.widthValley);

        if (inLakeZone && lakePreset != null) {
            if (radialFromSource <= waterR) {
                // 湖盆：抛物线深挖（最深 centerDepth）+ 暗河井
                double rNorm = radialFromSource / waterR;
                if (rNorm > 1.0) rNorm = 1.0;

                double u = 1.0 - rNorm;
                if (u < 0.0) u = 0.0;
                double lakeShape = u * u;

                double baseDepth = seaLevel - riverBedYd;
                if (baseDepth < 0.0) baseDepth = 0.0;

                double lakeDepth = lakePreset.centerDepth * lakeShape;
                double finalDepth = Math.max(baseDepth, lakeDepth);

                double shaftRadius = lakePreset.baseRadius * lakePreset.shaftRadiusFactor;
                if (radialFromSource <= shaftRadius) {
                    double rCoreNorm = radialFromSource / shaftRadius;
                    if (rCoreNorm > 1.0) rCoreNorm = 1.0;

                    double shaftShape = 1.0 - rCoreNorm;
                    if (shaftShape < 0.0) shaftShape = 0.0;

                    double targetShaftCenterDepth =
                        lakePreset.centerDepth + lakePreset.undergroundExtraDepth;
                    double shaftDepthSmooth = targetShaftCenterDepth * shaftShape;

                    int shaftSteps = 4;
                    double stepSize = targetShaftCenterDepth / shaftSteps;
                    int stepIndex = (int) Math.floor(shaftDepthSmooth / stepSize);
                    if (stepIndex < 0) stepIndex = 0;
                    if (stepIndex >= shaftSteps) stepIndex = shaftSteps - 1;

                    double shaftDepth = (stepIndex + 1) * stepSize;
                    finalDepth = Math.max(finalDepth, shaftDepth);
                }

                riverBedYd = seaLevel - finalDepth;
                // 湖盆边缘至少留 1 格浅水，避免干坑
                if (riverBedYd > seaLevel - 1.0) {
                    riverBedYd = seaLevel - 1.0;
                }
            } else if (radialFromSource <= beachR) {
                // 岸边（干岸）：平于水面（默认 y=64），不再抬高。
                // 河道核心保持下切（河流穿岸入湖），湖岸只作用于非河道列。
                double shoreBed = seaLevel;
                riverBedYd = (dist <= riverCutWidth)
                    ? Math.min(riverBedYd, shoreBed)
                    : shoreBed;
            } else if (radialFromSource <= slopeR) {
                // 外坡：干岸高度 → 平滑回到原地形；河道核心同样保持下切。
                double t = (radialFromSource - beachR) / lakePreset.outerSlopeWidth;
                double shoreY = seaLevel;
                double slopeBed = shoreY + (baseHeightD - shoreY) * smoothstep01(t);
                riverBedYd = (dist <= riverCutWidth)
                    ? Math.min(riverBedYd, slopeBed)
                    : slopeBed;
            }
        }

        int riverBedY = (int) Math.floor(riverBedYd);
        if (riverBedY < 1) {
            riverBedY = 1;
        }
        // 非水体区域：床面不低于海平面就不雕刻。
        // 水体 / 源头湖的岸滩（高于水面）和湿地干丘（轻压后仍高于水面）
        // 必须保留，否则沙环 / 沼泽地形会被还原成原地形。
        if (riverBedY >= seaLevel && !inBodyZone && !inLakeZone) {
            return baseHeightD;
        }
        return riverBedY;
    }

    /**
     * 源头湖岸 / 滩涂方块：
     *   - 湖区（水边 + 干岸）内、地表高度在浅水带 → 滩涂方块；
     *   - 地表高度在干岸带 → 干岸方块；
     *   - 其余返回 null（保持原地表）。
     *
     * @param surfaceY 该列顶部实体方块 Y（河床顶或地表）
     */
    public static BlockMetaPair lakeSurfaceMaterial(
        int surfaceY, int seaLevel, int worldX, int worldZ,
        TalosRiverSystem.HydroSample hydro, MacroPackageId macroId
    ) {
        if (macroId == null || macroId == MacroPackageId.OCEANIC
            || hydro == null) {
            return null;
        }

        MacroPackageRegistry.SourceLakePreset lake =
            MacroPackageRegistry.get(macroId).sourceLake();

        // 通用水体（湖 / 湿地 / 穿河湖 / 牛轭湖）优先于源头湖
        if (hydro.body != null) {
            return bodyLakeSurfaceMaterial(
                surfaceY, seaLevel, worldX, worldZ, hydro, lake
            );
        }

        if (!hydro.hasSource
            || Double.isNaN(hydro.sourceX) || Double.isNaN(hydro.sourceZ)) {
            return null;
        }

        // 河道核心不换方块：保持河床深层，避免湖岸材料把河切断
        if (hydro.distance <= hydro.widthCore) {
            return null;
        }

        double dx = worldX + 0.5 - hydro.sourceX;
        double dz = worldZ + 0.5 - hydro.sourceZ;
        double radial = Math.sqrt(dx * dx + dz * dz);

        // 与湖盆剖面一致的角度相关半径（确定性角噪声）
        double theta = Math.atan2(dz, dx);
        double ampScale = lake.irregularityAmp / 0.18;
        double radiusFactor = 1.0
            + ampScale * 0.12 * angularPerturbation(
                theta, (long) hydro.sourceX, (long) hydro.sourceZ);
        double minFactor = 1.0 - 0.12 * ampScale;
        double maxFactor = 1.0 + 0.12 * ampScale;
        if (radiusFactor < minFactor) radiusFactor = minFactor;
        if (radiusFactor > maxFactor) radiusFactor = maxFactor;

        double waterR = lake.baseRadius * radiusFactor;
        double beachR = waterR + lake.beachWidth;
        if (radial > beachR) {
            return null;
        }

        if (surfaceY <= seaLevel - 1 && surfaceY >= seaLevel - 4) {
            return lake.mudBlock;   // 滩涂 / 浅水底
        }
        if (surfaceY <= seaLevel + 2 && surfaceY >= seaLevel) {
            return lake.shoreBlock; // 干岸
        }
        return null;
    }

    /**
     * 判断某列是否落在通用水体的雕刻范围内：
     * 湖盆内部，或岸滩 + 外坡过渡带。
     */
    private static boolean inBodyZone(TalosRiverSystem.HydroSample hydro,
                                      MacroPackageRegistry.SourceLakePreset preset,
                                      double sampleX, double sampleZ) {
        RiverBodyData body = hydro.body;
        if (body == null || preset == null) {
            return false;
        }

        if (pointInPolygon(sampleX, sampleZ, body.getOutline())) {
            return true;
        }

        double edgeDist = distanceToPolygonOutline(
            sampleX, sampleZ, body.getOutline()
        );
        return edgeDist <= preset.beachWidth + preset.outerSlopeWidth;
    }

    /**
     * 通用水体的高度场雕刻（按类型区分）：
     *   - 湖 / 终端湖 / 牛轭湖：抛物面湖盆，湖心最深压到 y=50，
     *     岸滩 + 外坡按真实轮廓完整包围；
     *   - 穿河湖：内部平底成湖、不保留河道，避免湖中看到河槽与进出收窄；
     *   - 湿地：轻压地形，最低压到 y=62，保留基础地形起伏形成沼泽；
     *     只做 10 格外缘过渡，不做深湖盆和沙岸。
     */
    private static double applyBodyBasin(int worldX, int worldZ,
                                         int worldSeedInt,
                                         double sampleX, double sampleZ,
                                         double baseHeightD,
                                         int seaLevel,
                                         TalosRiverSystem.HydroSample hydro,
                                         RiverBodyData body,
                                         MacroPackageRegistry.SourceLakePreset preset,
                                         double riverBedYd,
                                         double relief) {
        double[] l = bodyLocal(sampleX, sampleZ, body);
        double rx = Math.max(1.0, body.getRadiusX());
        double rz = Math.max(1.0, body.getRadiusZ());
        double r = Math.sqrt(
            (l[0] / rx) * (l[0] / rx) + (l[1] / rz) * (l[1] / rz)
        );
        boolean inside = pointInPolygon(sampleX, sampleZ, body.getOutline());

        double waterLevel = seaLevel + body.getWaterLevelOffset();
        double distToEdge = distanceToPolygonOutline(
            sampleX, sampleZ, body.getOutline()
        );
        double edgeDist = inside ? 0.0 : distToEdge;

        // 湖的河道开口 / 过渡半径：用河谷宽度（和河道雕刻一致），
        // 保证湖与河交界处的口子和河道一样宽。
        double cutWidth = Math.max(hydro.widthCore, hydro.widthValley);

        WaterBodySmoothingContext ctx = new WaterBodySmoothingContext(
            body, rx, rz, r,
            worldX, worldZ, worldSeedInt,
            distToEdge,
            waterLevel, seaLevel, baseHeightD, hydro,
            riverBedYd, relief, cutWidth
        );
        WaterBodySmoothing smoothing =
            WaterBodySmoothings.forType(body.getType());

        double result;
        if (inside) {
            result = smoothing.interiorBedY(ctx);
            // 湖类最外圈至少留 1 格浅水，避免干坑；湿地不强制
            if (body.getType() != RiverBodyType.WETLAND) {
                if (result > waterLevel - 1.0) {
                    result = waterLevel - 1.0;
                }
                if (result < 1.0) {
                    result = 1.0;
                }
            }
        } else if (body.getType() == RiverBodyType.WETLAND) {
            // 湿地外缘过渡：边缘处 = 轻压值，向外平滑回到原地形
            double pressed = smoothing.interiorBedY(ctx);
            double t = 1.0 - clamp01(
                edgeDist / WETLAND_TRANSITION_BLOCKS
            );
            result = baseHeightD
                + (pressed - baseHeightD) * smoothstep01(t);
        } else {
            // 岸滩 / 外坡（湖类共用）：沙圈平于水面，外坡平滑回到原地形
            if (edgeDist <= preset.beachWidth) {
                result = waterLevel;
            } else {
                double t = (preset.outerSlopeWidth > 1.0e-6)
                    ? (edgeDist - preset.beachWidth) / preset.outerSlopeWidth
                    : 1.0;
                double shoreY = waterLevel;
                result = shoreY + (baseHeightD - shoreY) * smoothstep01(t);
            }
        }

        // 河道保持下切（河流穿湖 / 穿岸）。
        // 穿河湖 / 终端湖内部由湖面覆盖河道；
        // 但穿河湖的岸坡带（进出口附近）必须保留河道切口穿过沙圈，
        // 否则深水在岸线处直接顶到沙圈，形成交界立面。
        // 牛轭湖的轮廓在接口附近与河道重叠，湖内也必须保留河道本身，
        // 否则湖岸会把原来的河道挤掉。
        boolean preserveChannel;
        if (body.getType() == RiverBodyType.OXBOW_LAKE) {
            preserveChannel = true;
        } else if (body.getType() == RiverBodyType.THROUGH_LAKE) {
            double entranceBand = Math.max(
                8.0, Math.min(rx, rz) * 0.12
            );
            preserveChannel = !inside || distToEdge <= entranceBand;
        } else {
            preserveChannel = !inside;
        }
        if (preserveChannel
            && hydro.distance <= cutWidth
            && hydro.mask > 0.0) {
            result = Math.min(result, riverBedYd);
        }

        if (result < 1.0) {
            result = 1.0;
        }
        return result;
    }

    /**
     * 通用水体的岸滩 / 湖底方块（按类型区分）：
     *   - 湖 / 穿河湖 / 牛轭湖：水底铺湖泥，岸滩按真实轮廓铺干岸方块；
     *   - 湿地：只铺水下泥底，不做沙环；
     *   - 穿河湖内部不保留河道材料（整个湖底都是湖泥）。
     */
    private static BlockMetaPair bodyLakeSurfaceMaterial(
        int surfaceY, int seaLevel, int worldX, int worldZ,
        TalosRiverSystem.HydroSample hydro,
        MacroPackageRegistry.SourceLakePreset preset
    ) {
        RiverBodyData body = hydro.body;
        if (body == null || preset == null) {
            return null;
        }

        // 河道核心不换方块：保持河床深层，避免湖岸材料把河切断。
        // 穿河湖除外：湖内不保留河道。
        if (body.getType() != RiverBodyType.THROUGH_LAKE
            && hydro.distance <= hydro.widthCore) {
            return null;
        }

        double sampleX = worldX + 0.5;
        double sampleZ = worldZ + 0.5;
        boolean inside = pointInPolygon(sampleX, sampleZ, body.getOutline());

        double waterLevel = seaLevel + body.getWaterLevelOffset();
        double edgeDist = inside ? 0.0
            : distanceToPolygonOutline(sampleX, sampleZ, body.getOutline());

        if (body.getType() == RiverBodyType.WETLAND) {
            // 沼泽：只铺水下泥底，不做沙环
            if (inside && surfaceY <= waterLevel - 1) {
                return preset.mudBlock;
            }
            return null;
        }

        if (inside) {
            // 湖 / 穿河湖 / 牛轭湖：湖底不铺统一泥土，
            // 返回 null 让区块填充走河床底料逻辑（砂砾 / 沙子 / 黏土斑块），
            // 与河道观感一致。湿地仍铺泥底。
            return null;
        }

        if (edgeDist <= preset.beachWidth) {
            if (surfaceY >= waterLevel && surfaceY <= waterLevel + 2) {
                return preset.shoreBlock;
            }
        }
        return null;
    }

    private static double[] bodyLocal(double sampleX, double sampleZ,
                                      RiverBodyData body) {
        double dx = sampleX - body.getCenterX();
        double dz = sampleZ - body.getCenterZ();
        double cos = Math.cos(-body.getRotation());
        double sin = Math.sin(-body.getRotation());
        return new double[] {
            dx * cos - dz * sin,
            dx * sin + dz * cos
        };
    }

    /**
     * 点到多边形轮廓（闭合折线）的最短距离。
     * 岸滩 / 外坡 / 过渡带都按这个真实距离计算，
     * 保证沙环完整包围水体、不会在某些方向变成硬切。
     */
    private static double distanceToPolygonOutline(
        double px, double pz, List<RiverPoint> polygon
    ) {
        int n = polygon.size();
        double best = Double.POSITIVE_INFINITY;

        for (int i = 0, j = n - 1; i < n; j = i++) {
            RiverPoint a = polygon.get(j);
            RiverPoint b = polygon.get(i);
            double abx = b.getX() - a.getX();
            double abz = b.getZ() - a.getZ();
            double lenSq = abx * abx + abz * abz;

            double t;
            if (lenSq <= 1.0e-12) {
                t = 0.0;
            } else {
                t = ((px - a.getX()) * abx + (pz - a.getZ()) * abz) / lenSq;
                if (t < 0.0) t = 0.0;
                else if (t > 1.0) t = 1.0;
            }

            double cx = a.getX() + abx * t;
            double cz = a.getZ() + abz * t;
            double dx = px - cx;
            double dz = pz - cz;
            double d2 = dx * dx + dz * dz;
            if (d2 < best) {
                best = d2;
            }
        }

        return Math.sqrt(best);
    }

    private static boolean pointInPolygon(double px, double pz,
                                          List<RiverPoint> polygon) {
        boolean inside = false;
        int n = polygon.size();

        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = polygon.get(i).getX();
            double yi = polygon.get(i).getZ();
            double xj = polygon.get(j).getX();
            double yj = polygon.get(j).getZ();

            if ((yi > pz) != (yj > pz)) {
                double xIntersect =
                    (xj - xi) * (pz - yi) / (yj - yi) + xi;
                if (px < xIntersect) {
                    inside = !inside;
                }
            }
        }

        return inside;
    }

    private static double smoothstep01(double t) {
        return SmoothingMath.smoothstep01(t);
    }

    /**
     * 角度扰动：以源头坐标为种子、在圆周上做确定性值噪声，
     * 输出约 [-1,1]。6 个采样角平滑插值，形状有机但不会有深缩瓣。
     */
    private static double angularPerturbation(double theta, long sx, long sz) {
        final int N = 6;
        double t = theta / (2.0 * Math.PI) * N;
        int i0 = (int) Math.floor(t);
        double f = t - i0;
        double u = f * f * (3.0 - 2.0 * f);
        double a = angleHash(i0, sx, sz);
        double b = angleHash(i0 + 1, sx, sz);
        return a + (b - a) * u;
    }

    private static double angleHash(int i, long sx, long sz) {
        long h = 0x9E3779B97F4A7C15L;
        h ^= mix64(i + 0x9E3779B97F4A7C15L);
        h ^= mix64(sx + 0xBF58476D1CE4E5B9L);
        h ^= mix64(sz + 0x94D049BB133111EBL);
        h = mix64(h);
        return (h >>> 11) / (double) (1L << 53) * 2.0 - 1.0;
    }

    private static long mix64(long x) {
        x = (x ^ (x >>> 30)) * 0xbf58476d1ce4e5b9L;
        x = (x ^ (x >>> 27)) * 0x94d049bb133111ebL;
        return x ^ (x >>> 31);
    }

    /**
     * 河床底料：按宏群系预设 + 低频确定性噪声选择大块材料斑块，
     * 返回方块与铺设深度；非河流宏包返回 null。
     */
    public static TalosRiverSystem.RiverbedMaterial riverbedMaterialAt(
        int worldX, int worldZ, int worldSeedInt, MacroPackageId macroId
    ) {
        if (macroId == null || macroId == MacroPackageId.OCEANIC) {
            return null;
        }

        MacroPackageRegistry.RiverbedPreset preset =
            MacroPackageRegistry.get(macroId).riverbed();
        if (preset == null || preset.blocks == null || preset.blocks.length == 0
            || preset.weights == null || preset.weights.length != preset.blocks.length) {
            return null;
        }

        double n = valueNoise(
            worldX, worldZ, worldSeedInt, preset.patchScale, 0x51AB3C2D);

        double total = 0.0;
        for (double w : preset.weights) {
            total += Math.max(0.0, w);
        }
        if (total <= 0.0) {
            return null;
        }

        double pick = n * total;
        double acc = 0.0;
        BlockMetaPair block = preset.blocks[preset.blocks.length - 1];
        for (int i = 0; i < preset.blocks.length; i++) {
            acc += Math.max(0.0, preset.weights[i]);
            if (pick < acc) {
                block = preset.blocks[i];
                break;
            }
        }

        int span = preset.depthMax - preset.depthMin + 1;
        int depth = preset.depthMin
            + (int) (hash01(worldX, worldZ, worldSeedInt, 0x77AA55EE) * span);
        if (depth > preset.depthMax) {
            depth = preset.depthMax;
        }

        return new TalosRiverSystem.RiverbedMaterial(block, depth);
    }

    /** 确定性低频值噪声，返回 [0,1]，用于材料斑块选择。 */
    private static double valueNoise(int x, int z, int seed,
                                     double scale, int salt) {
        double sx = x / scale;
        double sz = z / scale;
        int x0 = (int) Math.floor(sx);
        int z0 = (int) Math.floor(sz);
        double fx = sx - x0;
        double fz = sz - z0;
        double u = fx * fx * (3.0 - 2.0 * fx);
        double v = fz * fz * (3.0 - 2.0 * fz);

        double a = hash01(x0, z0, seed, salt);
        double b = hash01(x0 + 1, z0, seed, salt);
        double c = hash01(x0, z0 + 1, seed, salt);
        double d = hash01(x0 + 1, z0 + 1, seed, salt);
        double e = a + (b - a) * u;
        double f = c + (d - c) * u;
        return e + (f - e) * v;
    }

    private static double hash01(int x, int z, int seed, int salt) {
        long h = 0x9E3779B97F4A7C15L;
        h ^= mix64(x + 0x9E3779B97F4A7C15L);
        h ^= mix64(z + 0xBF58476D1CE4E5B9L);
        h ^= mix64(seed + 0x94D049BB133111EBL);
        h ^= mix64(salt);
        h = mix64(h);
        return (h >>> 11) / (double) (1L << 53);
    }

    public static double computeRiverBedY(double baseHeight,
                                          int seaLevel,
                                          TalosRiverSystem.HydroSample hydro,
                                          MacroPackageId macroId) {

        double dist        = hydro.distance;
        double coreWidth   = hydro.widthCore;
        double valleyWidth = hydro.widthValley;
        double mask        = hydro.mask;
        int    riverLevel  = hydro.riverLevel;

        if (valleyWidth <= 0.0 || mask <= 0.0 ||
            dist == Double.MAX_VALUE) {
            return baseHeight;
        }

        MacroPackageRegistry.RiverStylePreset style =
            MacroPackageRegistry.get(macroId).riverStyle();

        double depthMain = style.baseDepthBlocks;

        double scale = 1.0;
        if (riverLevel > 0) {
            scale = Math.pow(
                clamp01(style.tributaryDepthScale),
                riverLevel
            );
        }
        double depthMax = depthMain * scale;

        double depthFactor = computeDepthFactorByValleyType(
            dist, coreWidth, valleyWidth,
            style.riverValleyType
        );

        depthFactor *= mask;

        if (depthFactor <= 0.0) {
            return baseHeight;
        }

        double depthBlocks = depthMax * depthFactor;

        double target = seaLevel - depthBlocks;
        if (target < 1.0) target = 1.0;
        return target;
    }

    /**
     * 根据横向距离 + 河谷半宽 + 类型，计算 0..1 的深度因子：
     *   - 1 ≈ 谷底（最深）
     *   - 0 ≈ 谷缘（无挖掘）
     */
    private static double computeDepthFactorByValleyType(double dist,
                                                         double coreWidth,
                                                         double valleyWidth,
                                                         MacroPackageRegistry.RiverValleyType type) {
        if (dist <= coreWidth) {
            return 1.0;
        }

        double denom = Math.max(1.0, valleyWidth - coreWidth);
        double t = (dist - coreWidth) / denom;
        t = clamp01(t);

        double u = 1.0 - t;

        switch (type) {
            case V_SHAPED:
                return Math.sqrt(u);

            case U_SHAPED:
            default:
                return u * u;
        }
    }

    private static double clamp01(double v) {
        return SmoothingMath.clamp01(v);
    }
}
