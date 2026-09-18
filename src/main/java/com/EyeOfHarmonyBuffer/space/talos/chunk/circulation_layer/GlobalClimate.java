package com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.AirMassType;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.NoiseContinentGrid;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.OrographyField;

/**
 * V2 统一气候采样门面（docs/TerrainV2/design.md 二c：对外唯一 API）。
 *
 * 数据源 = M5/M6 RelaxedClimate（按种子离线松弛求解 + 双线性查表），叠加：
 *   - L1 海陆（NoiseContinentGrid，块级海岸距离）
 *   - L1b 地形算子（P1b）：迎风坡抬升 → 额外降水；背风坡下沉 → 焚风减雨；
 *     辐合（-∇·v）→ 对流性降水；气压异常 → 干湿（高压=干）
 *
 * 生产消费方（L5 群系 / 水体 / 地形增强）与 /talosmap 统一走本门面。
 */
public final class GlobalClimate {

    private GlobalClimate() {}

    // ---- P1b 地形算子参数（尺度归一，出图后微调） ----

    /** 辐合归一标尺（实测 |div| p50≈5e-5/p90≈1.6e-4 → 取 1.0e-4 使 p90≈饱和 1.0）。 */
    private static final double CONV_SCALE = 1.0e-4;
    /**
     * 地形抬升坡度归一标尺（实测坡向点积 p90≈1.4e-5 → 取 1.8e-5）。
     *
     * **这是"风×海拔梯度"算子唯一的归一标尺**：{@code ClimateCoords}（群系湿度）与
     * 本类（降水）用的是同一个算子，必须共用这一个数。原先 ClimateCoords 自己又写了一份
     * 1.8e-5，靠注释宣称"与 GlobalClimate.UPLIFT_SCALE 一致" —— 注释不是约束。
     */
    public static final double UPLIFT_SCALE = 1.8e-5;
    /**
     * 海拔梯度采样步长（block）。**同样是那个算子的一部分**：
     * ClimateCoords 原先用 1500、本类用 2000，同一个"迎风抬升"在群系与降水里
     * 用的是两套梯度 ⇒ 两边的迎风/背风强度系统性差 33%。现在统一为 2000
     * （本值有上面标尺的实测注释作依据；1500 那份没有）。
     */
    public static final int ELEV_STEP = 2000;
    /** 风场辐合差分步长（block）。辐合**只**在降水里用，故不跨层共用。 */
    public static final int CONV_STEP = 1500;

    /** 降水归一标尺（mm/day -> [0,1]）：20 mm/day 视为饱和。**只影响出图配色，不参与任何物理量。** */
    public static final double RAIN_FULL_MM = 20.0;

    /**
     * 岸距搜索半径（block）。**口径依据**：{@code ClimateSample.coastDist} 自己的 javadoc 写的是
     * 「远场截断 ±200k」⇒ 本值复现该口径。
     *
     * <p>⚠ {@code PlateField.coastDistanceNew(x,z,seed,cell,maxSearch)} 在搜索半径内找不到海岸时
     * 返回**哨兵 ±2*maxSearch** ⇒ 本值给出的哨兵是 ±400k。
     * <p>参照系：{@code SimClimate.COAST_FINE = 40_000}（哨兵 ±80 km，便宜但 >40 km 即饱和，
     * 出图无用）；{@code SimClimate.COAST_FAR = 1_600_000}（哨兵 ±3200 km，量程足但搜索昂贵）。
     * 本层要的是「一张图上的岸距观感」，取中间量程。
     */
    private static final int COAST_MAX = 200_000;

    /**
     * ★★ 2026-09-18（顶死一套 · 第 4 段）：本方法已改成**新气候链的适配器**。
     *
     * <p><b>为什么改这里而不是改 `CommandTalosMap`</b>：整个旧栈（`RelaxedClimate` /
     * `GlobalCirculation` / `ClimateLatitudes` / `ThermalForcing` / `PolarZone` / `BarotropicGyre`）
     * 挂在唯一一个根上 —— 地图命令。保持签名与 {@code ClimateSample} 形状不变，
     * 命令就**一行都不用改**，而旧栈失去了最后一个消费者 ⇒ 第 6 段才能整簇删除。
     *
     * <p><b>取数口径（全部来自 sim/ 新栈，与群系链同一个源）</b>：
     * <ul>
     *   <li>海陆 / 岸距 ← {@code PlateField.isLandWithCell} / {@code coastDistanceNew}（新场）</li>
     *   <li>纬度带 ← {@code WorldContract.bandD}（D1 后：0=赤道、1=极点）</li>
     *   <li>风矢 ← {@code SimClimate.windAt}（与气候瓦片缓存同源）</li>
     *   <li>干湿 / 气团 / 气温 / 湿度 ← {@code ClimateCoords.sample}（= 新气候）</li>
     *   <li>降水 ← {@code PrecipField.mmPerDay}（气候层唯一降水口径），按 RAIN_FULL_MM 归一</li>
     *   <li>洋流 ← {@code OceanField.bandMeansAt} 的西/东带均速度（**真求解器**的带均值）</li>
     *   <li>海温 ← {@code OceanField.anomalyAt} 归一到 [-1,1]（口径见下）</li>
     *   <li>{@code gyreWarmth} ← 保留原式 {@code 0.5 - bandD}（它**本来就是占位符**，
     *       见 {@code ClimateSample} 里该字段的 javadoc）</li>
     * </ul>
     *
     * <p><b>⚠ 两处口径变更（诚实记账，都是「出图语义」不是物理量）</b>：
     * <ol>
     *   <li>{@code seaTemperature} 由「耦合输运后的海温」改为「海温距平 / 10 K」——
     *       新链建模的就是距平（`OceanField.anomalyAt`），绝对海温另有来源。</li>
     *   <li>{@code currentZ} 恒为 0：{@code bandMeansAt} 给的是**沿盆**带均速度（x 方向），
     *       丢掉了经向分量。旧实现是 O(1) 查表，这里是 O(1) **缓存命中**
     *       （首查会解那一行，代价见 `OceanField.solveRow`）。</li>
     * </ol>
     */
    public static ClimateSample sample(int x, int z, int worldSeedInt) {
        long seed = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(worldSeedInt);
        int cell = com.EyeOfHarmonyBuffer.sim.litho.PlateField.PLATE_CELL;

        boolean isLand = com.EyeOfHarmonyBuffer.sim.litho.PlateField.isLandWithCell(x, z, seed, cell);
        double coastDist = com.EyeOfHarmonyBuffer.sim.litho.PlateField.coastDistanceNew(x, z, seed, cell, COAST_MAX);
        double bandD = com.EyeOfHarmonyBuffer.sim.world.WorldContract.bandD(z);

        double[] wind = new double[2];
        com.EyeOfHarmonyBuffer.sim.runtime.SimClimate.windAt(x, z, worldSeedInt, wind);

        com.EyeOfHarmonyBuffer.space.talos.chunk.world.ClimateCoords.Coords c =
            com.EyeOfHarmonyBuffer.space.talos.chunk.world.ClimateCoords.sample(x, z, worldSeedInt, null);

        double rainMm = com.EyeOfHarmonyBuffer.sim.atmos.PrecipField.mmPerDay(
            x, z, seed, cell, com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere.theta(0.0), 500_000);
        double rain = clamp01(rainMm / RAIN_FULL_MM);

        // ClimateCoords.Coords.airMass: 0=mT 1=cT 2=mP 3=cP（见其字段注释）
        AirMassType type;
        switch (c.airMass) {
            case 0:  type = AirMassType.MARITIME_TROPICAL; break;
            case 1:  type = AirMassType.CONTINENTAL_TROPICAL; break;
            case 2:  type = AirMassType.MARITIME_POLAR; break;
            default: type = AirMassType.CONTINENTAL_POLAR; break;
        }

        double gb = 0.5 - bandD;
        double gyre = gb < -1 ? -1 : (gb > 1 ? 1 : gb);

        double curX = 0.0, curZ = 0.0, sst = Double.NaN, spd = 0.0;
        if (!isLand) {
            double[] bm = com.EyeOfHarmonyBuffer.sim.ocean.OceanField.bandMeansAt(x, z, worldSeedInt);
            if (bm != null) {
                double v = 0.5 * (bm[0] + bm[1]);
                curX = v; curZ = 0.0; spd = Math.abs(v);
            }
            sst = com.EyeOfHarmonyBuffer.sim.ocean.OceanField.anomalyAt(x, z, worldSeedInt) / 10.0;
        }

        return new ClimateSample(isLand, coastDist,
            bandD, wind[0], wind[1],
            c.dry, rain, gyre,
            type, c.airT, c.q,
            curX, curZ, sst, spd);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // ★★ 2026-09-18 顶死一套 · 第 6 段：**旧栈实现已删除**
    //
    // 删掉的内容：`sampleLegacy(x,z,worldSeedInt)` 与其三个私有帮手
    //   （`divergence` / `slopeAlongWind` / `elevOf`）。
    //
    // 它们的数据源是整套 1M 旧契约：`RelaxedClimate`（LAT_CYCLE=1M、赤道→极点 500 km）、
    // `GlobalCirculation`、`NoiseContinentGrid`、`OrographyField`。
    // 第 4 段把 `sample` 改成新链适配器之后，它们**再无调用者**。
    //
    // 随之可删（本段的其余部分）：`RelaxedClimate` / `GlobalCirculation` / `ClimateLatitudes`
    // / `ThermalForcing` / `BarotropicGyre` / `PolarZone`（已改基到世界契约）。
    //
    // ⚠ `clamp01` 必须保留 —— 新适配器还在用它。
    // ══════════════════════════════════════════════════════════════════════════════

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}
