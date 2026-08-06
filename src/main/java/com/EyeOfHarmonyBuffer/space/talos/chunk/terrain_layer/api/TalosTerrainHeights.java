package com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer.api;

import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.api.MacroPackageId;
import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.api.TalosMacroClimate;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.PlateBoundaryState;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.TalosCoastlineShaper;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.TalosLandMask;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.TalosPlateBoundaryShaper;
import com.EyeOfHarmonyBuffer.space.talos.chunk.mountain_layer.api.TalosMountainSystem;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.api.TalosRiverChannelShaper;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.api.TalosRiverSystem;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.api.TalosRiverTerrainModifier;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverBodyData;
import com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer.TerrainMath;

/**
 * 最终高度场（地形链统一出口）。
 *
 * 完整高度链：基础高度 → 海岸塑形 → 裂谷塑形 → 山脉抬升 →
 * 河岸 / 泛洪平原 → 河谷下切。这是全项目唯一的实现，
 * 区块生成、洞穴层、指令、水体过滤等一律走这里，避免口径漂移。
 *
 * 两种查询：
 *   - {@link #sample(int, int, int, int, int)}：稀疏逐点重算（洞穴层 / 指令用）；
 *   - {@link #sampleColumn(TerrainColumnInputs)}：给定一列全部输入（世界层用
 *     TalosChunkContext 组装输入，复用 chunk 级缓存）。
 * 两者结果完全一致（同一确定性函数）。
 *
 * 调用限制（务必遵守，否则结果错误或直接抛异常）：
 *   1. 必须在 FML preInit 之后调用：完整链（sample / sampleColumn）依赖河流模板
 *      （RiverRegistry 由 TalosRiverSystem.onPreInit 加载）。preInit 之前调用
 *      不会报错，但河流影响会全部缺失，高度与真实世界不一致。
 *   2. 禁止在「河流系统构建过程」中调用完整链：SupercontinentRiverSystemRegistry
 *      构建时会回调本类做水体过滤，此时再走 sample / sampleColumn 会触发
 *      sampleHydroField → 再次进入河流构建 → 无限递归（栈溢出）。
 *      这类场景必须使用 samplePreRiverHeight（不含水文，无递归）。
 *   3. 山脉抬升依赖 Talos 世界的山地状态（WorldEvent.Load 之后才创建）；
 *      在此之前 / 非 Talos 世界 / 山地系统被禁用时，elevation 与 mask 为 0，
 *      高度等于「无山脉」版本——这是设计内的降级，不是报错。
 *   4. seaLevel 与 worldHeight 必须与目标世界一致（Talos 当前为 64 /
 *      world.getActualHeight()），传错会得到另一套高度。
 *   5. 完整链的首次调用可能触发河流系统 / 山地预构建的惰性初始化，
 *      单次成本较高，不要在热路径里逐块调用（区块生成请走 sampleColumn）。
 */
public final class TalosTerrainHeights {

    private TalosTerrainHeights() {}

    /** 某一列高度链的全部输入（世界层用 TalosChunkContext 组装，稀疏查询自行采样）。 */
    public static final class TerrainColumnInputs {
        public final int worldX;
        public final int worldZ;
        public final int worldSeedInt;
        public final int seaLevel;
        public final int worldHeight;
        public final boolean isLand;
        public final TalosLandMask.Sample landSample;
        public final double baseHeightD;
        public final double bankIntensity;
        /** 每列缓存的构造风格平滑 DIVERGENT 强度（基础岩面淡出与裂谷塑形共用）。 */
        public final double smoothedDivergence;
        /** 水文采样；sampleColumn 要求非 null（samplePreRiverHeight 可传 null）。 */
        public final TalosRiverSystem.HydroSample hydro;
        public final MacroPackageId macroId;
        public final double mountainElevation01;
        public final double mountainMask01;
        public final int mountainKind;

        public TerrainColumnInputs(int worldX,
                                   int worldZ,
                                   int worldSeedInt,
                                   int seaLevel,
                                   int worldHeight,
                                   boolean isLand,
                                   TalosLandMask.Sample landSample,
                                   double baseHeightD,
                                   double bankIntensity,
                                   double smoothedDivergence,
                                   TalosRiverSystem.HydroSample hydro,
                                   MacroPackageId macroId,
                                   double mountainElevation01,
                                   double mountainMask01,
                                   int mountainKind) {
            this.worldX = worldX;
            this.worldZ = worldZ;
            this.worldSeedInt = worldSeedInt;
            this.seaLevel = seaLevel;
            this.worldHeight = worldHeight;
            this.isLand = isLand;
            this.landSample = landSample;
            this.baseHeightD = baseHeightD;
            this.bankIntensity = bankIntensity;
            this.smoothedDivergence = smoothedDivergence;
            this.hydro = hydro;
            this.macroId = macroId;
            this.mountainElevation01 = mountainElevation01;
            this.mountainMask01 = mountainMask01;
            this.mountainKind = mountainKind;
        }
    }

    /** 最终高度采样结果。 */
    public static final class TerrainHeightSample {
        public final boolean isLand;
        /** 海岸塑形后高度（海洋列海床塑形用）。 */
        public final double coastD;
        /** 河岸塑形后、河谷下切前的高度。 */
        public final double preRiverD;
        /** 最终高度场（double，与区块填充 round 前的值一致）。 */
        public final double surfaceD;
        /** 河流影响掩码（非陆地列恒为 0）。 */
        public final double riverMask;
        /** 命中的水体（湖 / 湿地 / 穿河湖 / 牛轭湖），无则 null。 */
        public final RiverBodyData body;
        /** 水面高度（海平面 + 水体水位偏移；无水体时 = 海平面）。 */
        public final double waterLevel;

        public TerrainHeightSample(boolean isLand,
                                   double coastD,
                                   double preRiverD,
                                   double surfaceD,
                                   double riverMask,
                                   RiverBodyData body,
                                   double waterLevel) {
            this.isLand = isLand;
            this.coastD = coastD;
            this.preRiverD = preRiverD;
            this.surfaceD = surfaceD;
            this.riverMask = riverMask;
            this.body = body;
            this.waterLevel = waterLevel;
        }
    }

    /**
     * 稀疏查询：完整重算某一列的最终高度。
     * 每列成本较高（海陆 / 群系 / 水文 / 山地逐点采样），只用于低频场景。
     *
     * 调用限制：见类注释 1~5；尤其禁止在河流系统构建期间调用（会递归）。
     */
    public static TerrainHeightSample sample(int worldX, int worldZ,
                                             int worldSeedInt,
                                             int seaLevel,
                                             int worldHeight) {
        TalosLandMask.Sample land =
            TalosLandMask.sampleFull(worldX, worldZ, worldSeedInt);
        boolean isLand = land != null && land.isLand;

        MacroPackageId macro = TalosMacroClimate.getMacroPackageId(
            worldX, worldZ, worldSeedInt, isLand
        );
        TalosMacroClimate.HeightModulation mod =
            TalosMacroClimate.getHeightModulationAt(
                worldX, worldZ, worldSeedInt, isLand
            );
        double smoothedDivergence = TalosMacroClimate
            .getTectonicStyleSample(worldX, worldZ, worldSeedInt)
            .smoothedDivergence;
        double base = TalosBaseTerrain.sampleBaseHeight(
            worldX, worldZ, worldSeedInt, seaLevel, land,
            mod.bias, mod.scale, smoothedDivergence
        );
        double bank = TalosRiverTerrainModifier.smoothedBankIntensityAt(
            worldX, worldZ, worldSeedInt
        );
        TalosRiverSystem.HydroSample hydro =
            TalosRiverSystem.sampleHydroField(worldX, worldZ, worldSeedInt);
        TalosMountainSystem.MountainSample mountain =
            TalosMountainSystem.sampleMountain(worldX, worldZ, worldSeedInt);

        return sampleColumn(new TerrainColumnInputs(
            worldX, worldZ, worldSeedInt, seaLevel, worldHeight,
            isLand, land, base, bank, smoothedDivergence, hydro, macro,
            mountain.elevation01, mountain.mask01, mountain.kind
        ));
    }

    /**
     * 不含水文的高度：基础 → 海岸 → 裂谷 → 山脉抬升。
     * 用于水体过滤等「不能触发河流查询」的场景
     * （河流系统构建时查询最终高度会与水文查询互相递归）。
     *
     * 调用限制：
     *   - 可在河流系统构建期间安全调用（不依赖模板 / 水文，无递归）；
     *   - 山脉抬升仍依赖山地状态（见类注释 3）；
     *   - seaLevel / worldHeight 仍需与目标世界一致（见类注释 4）。
     */
    public static double samplePreRiverHeight(int worldX, int worldZ,
                                              int worldSeedInt,
                                              int seaLevel,
                                              int worldHeight) {
        TalosLandMask.Sample land =
            TalosLandMask.sampleFull(worldX, worldZ, worldSeedInt);
        boolean isLand = land != null && land.isLand;

        MacroPackageId macro = TalosMacroClimate.getMacroPackageId(
            worldX, worldZ, worldSeedInt, isLand
        );
        TalosMacroClimate.HeightModulation mod =
            TalosMacroClimate.getHeightModulationAt(
                worldX, worldZ, worldSeedInt, isLand
            );
        double smoothedDivergence = TalosMacroClimate
            .getTectonicStyleSample(worldX, worldZ, worldSeedInt)
            .smoothedDivergence;
        double base = TalosBaseTerrain.sampleBaseHeight(
            worldX, worldZ, worldSeedInt, seaLevel, land,
            mod.bias, mod.scale, smoothedDivergence
        );
        TalosMountainSystem.MountainSample mountain =
            TalosMountainSystem.sampleMountain(worldX, worldZ, worldSeedInt);

        return preRiverShaped(new TerrainColumnInputs(
            worldX, worldZ, worldSeedInt, seaLevel, worldHeight,
            isLand, land, base, 0.5, smoothedDivergence, null, macro,
            mountain.elevation01, mountain.mask01, mountain.kind
        ));
    }

    /**
     * 给定一列全部输入的完整高度链（唯一实现）。
     *
     * 调用限制：
     *   - 要求 TalosChunkContext 已完整构建（含水文采样），
     *     inputs.hydro 必须非 null（陆地列尤其如此），否则 NPE；
     *   - 必须在河流模板加载完成（FML preInit）后调用；
     *   - 禁止在河流系统构建期间调用（会递归），改用 samplePreRiverHeight；
     *   - 山地抬升依赖山地状态（见类注释 3）。
     */
    public static TerrainHeightSample sampleColumn(TerrainColumnInputs in) {
        double coast = coastShaped(in);
        double mountain = preRiverShaped(in);

        double riverMask = (in.landSample != null && in.landSample.isLand)
            ? in.hydro.mask
            : 0.0;
        double bank = TalosRiverTerrainModifier.applyRiverBankShaping(
            in.worldX, in.worldZ, in.worldSeedInt,
            mountain, in.seaLevel,
            TalosRiverTerrainModifier.bankPreset(in.bankIntensity),
            in.isLand, riverMask
        );

        double channel = in.isLand
            ? TalosRiverChannelShaper.applyRiverChannelShaping(
                in.worldX, in.worldZ, in.worldSeedInt,
                bank, in.seaLevel, in.hydro, in.macroId)
            : bank;

        double waterLevel = in.seaLevel;
        if (in.hydro.body != null) {
            waterLevel = in.seaLevel + in.hydro.body.getWaterLevelOffset();
        }

        return new TerrainHeightSample(
            in.isLand, coast, bank, channel, riverMask,
            in.hydro.body, waterLevel
        );
    }

    /** 基础 → 海岸 → 裂谷 → 山脉抬升（河岸/河谷之前，不依赖水文）。 */
    private static double preRiverShaped(TerrainColumnInputs in) {
        double coast = coastShaped(in);

        double riftStrength = in.smoothedDivergence;
        PlateBoundaryState riftState = (riftStrength > 0.0)
            ? PlateBoundaryState.DIVERGENT
            : (in.landSample != null
                ? in.landSample.plateBoundaryState : null);
        double riftWeight = (riftState == PlateBoundaryState.DIVERGENT)
            ? riftStrength
            : (in.landSample != null
                ? in.landSample.plateBoundaryWeight : 0.0);
        // 海岸衰减：分离带与山脉抬升在靠海一侧逐渐收束，避免板块边界把海岸线抬成“墙”。
        // coastWeight 1=贴海、0.5≈128 格内、0=内陆；平滑带与海岸塑形同源。
        double coastWeight = (in.landSample != null)
            ? in.landSample.coastWeight : 0.0;
        double coastFade = 1.0 - TerrainMath.smoothstep(0.5, 1.0, coastWeight);
        riftWeight *= coastFade;
        double rift = TalosPlateBoundaryShaper.applyRiftShaping(
            coast, in.seaLevel, in.isLand, riftState, riftWeight,
            in.worldX, in.worldZ, in.worldSeedInt
        );

        double uplifted = TalosMountainSystem.applyMountainUplift(
            rift, in.seaLevel,
            in.mountainElevation01, in.mountainMask01, in.mountainKind,
            in.worldHeight
        );
        return TerrainMath.lerp(rift, uplifted, coastFade);
    }

    private static double coastShaped(TerrainColumnInputs in) {
        return TalosCoastlineShaper.applyCoastlineShaping(
            in.baseHeightD, in.seaLevel, in.isLand,
            in.landSample != null ? in.landSample.coastWeight : 0.0
        );
    }
}
