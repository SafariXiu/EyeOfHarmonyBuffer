package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer;

import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.api.MacroPackageId;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.api.TalosRiverSystem;
import micdoodle8.mods.galacticraft.api.prefab.core.BlockMetaPair;

public final class TalosRiverProfile {

    private TalosRiverProfile() {}

    /** 河床微起伏：低频噪声尺度与幅度（格）。 */
    private static final double RIVERBED_RELIEF_SCALE = 24.0;
    private static final double RIVERBED_RELIEF_AMP = 1.5;

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

        // 源头湖：按宏群系预设计算湖盆 + 岸边 + 滩涂 + 外坡
        MacroPackageRegistry.SourceLakePreset lakePreset = null;
        boolean inLakeZone = false;
        double sx = hydro.sourceX;
        double sz = hydro.sourceZ;
        double sampleX = worldX + 0.5;
        double sampleZ = worldZ + 0.5;
        double radialFromSource = 0.0;

        if (hydro.hasSource && !Double.isNaN(sx) && !Double.isNaN(sz)) {
            double dxs = sampleX - sx;
            double dzs = sampleZ - sz;
            radialFromSource = Math.sqrt(dxs * dxs + dzs * dzs);

            if (macroId != null && macroId != MacroPackageId.OCEANIC) {
                lakePreset = MacroPackageRegistry.get(macroId).sourceLake();
                double maxInfluenceR = lakePreset.baseRadius * 1.30
                    + lakePreset.beachWidth + lakePreset.outerSlopeWidth;
                if (radialFromSource <= maxInfluenceR) {
                    inLakeZone = true;
                }
            }
        }

        // 湖区独立于河流 mask 雕刻；非湖区才要求河流影响
        if (!inLakeZone && (mask <= 0.0 || dist == Double.MAX_VALUE
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
        if (riverBedYd >= seaLevel) {
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

        if (inLakeZone && lakePreset != null) {
            double theta = Math.atan2(sampleZ - sz, sampleX - sx);

            // 确定性角噪声：有机不规则，但每个方向都有完整湖体（无缩瓣盲区）
            double ampScale = lakePreset.irregularityAmp / 0.18;
            double radiusFactor = 1.0
                + ampScale * 0.12 * angularPerturbation(
                    theta, (long) sx, (long) sz);
            double minFactor = 1.0 - 0.12 * ampScale;
            double maxFactor = 1.0 + 0.12 * ampScale;
            if (radiusFactor < minFactor) radiusFactor = minFactor;
            if (radiusFactor > maxFactor) radiusFactor = maxFactor;

            double waterR = lakePreset.baseRadius * radiusFactor;
            double beachR = waterR + lakePreset.beachWidth;
            double slopeR = beachR + lakePreset.outerSlopeWidth;

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
                // 岸边（干岸）：水面 → 高出水面 beachHeight（至少 sea+1，避免干坑）。
                // 河道核心保持下切（河流穿岸入湖），湖岸只作用于非河道列。
                double t = (radialFromSource - waterR) / lakePreset.beachWidth;
                double shoreBed = seaLevel + Math.max(1.0,
                    lakePreset.beachHeight * smoothstep01(t));
                riverBedYd = (dist <= hydro.widthCore)
                    ? Math.min(riverBedYd, shoreBed)
                    : shoreBed;
            } else if (radialFromSource <= slopeR) {
                // 外坡：干岸高度 → 平滑回到原地形；河道核心同样保持下切。
                double t = (radialFromSource - beachR) / lakePreset.outerSlopeWidth;
                double shoreY = seaLevel + Math.max(1.0, lakePreset.beachHeight);
                double slopeBed = shoreY + (baseHeightD - shoreY) * smoothstep01(t);
                riverBedYd = (dist <= hydro.widthCore)
                    ? Math.min(riverBedYd, slopeBed)
                    : slopeBed;
            }
        }

        int riverBedY = (int) Math.floor(riverBedYd);
        if (riverBedY < 1) {
            riverBedY = 1;
        }
        if (riverBedY >= seaLevel) {
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
            || hydro == null || !hydro.hasSource
            || Double.isNaN(hydro.sourceX) || Double.isNaN(hydro.sourceZ)) {
            return null;
        }

        MacroPackageRegistry.SourceLakePreset lake =
            MacroPackageRegistry.get(macroId).sourceLake();

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

    private static double smoothstep01(double t) {
        if (t < 0.0) {
            t = 0.0;
        } else if (t > 1.0) {
            t = 1.0;
        }
        return t * t * (3.0 - 2.0 * t);
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
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }
}
