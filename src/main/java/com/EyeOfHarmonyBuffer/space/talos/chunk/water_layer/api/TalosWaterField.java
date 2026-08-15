package com.EyeOfHarmonyBuffer.space.talos.chunk.water_layer.api;

import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.api.MacroPackageId;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.api.TalosRiverSystem;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverBodyData;

/**
 * 水场（独立层）：全项目「水面高度」的唯一权威。
 *
 * 职责：
 *   - 逐列决定水面高度 waterSurfaceY（Double.NEGATIVE_INFINITY = 无水）；
 *   - 区块填充、洞穴入口、调试查询一律以本层输出为准，
 *     禁止再出现「h < seaLevel 就灌水」之类的散落判断。
 *
 * 规则（激进版：陆地默认无水，水是显式授权的）：
 *   1. 海洋列（isLand == false）：恒有水，水面 = seaLevel；
 *   2. 干盆地（basinMask01 > 0.5）：永不灌水（一票否决）；
 *   3. 水体（hydro.body != null：湖 / 湿地 / 穿河湖 / 牛轭湖）：
 *      水面 = seaLevel + body.getWaterLevelOffset()；
 *   4. 河道 / 源头湖区（hydro.mask > 0）：水面 = seaLevel。
 *      这里刻意「宽松发水」：河谷外圈虽然 mask > 0 但地表高于水面，
 *      填充端用 wl > h 收敛，不会多淹一格；
 *   5. 近海浅水带（coastWeight > 0.5 且最终地表低于海平面）：
 *      水面 = seaLevel，保住现有海岸浅滩观感（海岸塑形把近海洼地
 *      抬到 seaLevel-1，靠这条规则维持 1 格浅水）；
 *   6. 其余陆地：永不自动灌水（盆地、干裂谷、盐沼等新地形都走这里）。
 *
 * 调用限制（与 TalosTerrainHeights 一致）：
 *   - 输入依赖水文采样（hydro），禁止在河流系统构建期间调用
 *     （构建期只允许 samplePreRiverHeight，不会触达本层）；
 *   - 本层不依赖高度链实现，只消费调用方传入的 surfaceD（最终地表高度），
 *     因此不会与高度链 / 河流系统产生递归。
 *
 * 稀疏查询：请走 TalosTerrainHeights.sample(...).waterLevel（与本层同口径）。
 */
public final class TalosWaterField {

    private TalosWaterField() {}

    /** 每一列水场输入（全部来自调用方既有采样，本层不做任何采样）。 */
    public static final class WaterColumnInputs {
        public final int worldX;
        public final int worldZ;
        public final int worldSeedInt;
        public final int seaLevel;
        public final boolean isLand;
        /** 海岸带权重 [0,1]（来自 TalosLandMask.Sample.coastWeight）。 */
        public final double coastWeight;
        /** 最终地表高度（TalosTerrainHeights.surfaceD，未 round 的 double）。 */
        public final double surfaceD;
        public final TalosRiverSystem.HydroSample hydro;
        public final MacroPackageId macroId;
        /** 盆地掩码 [0,1]：> 0.5 视为干盆地（当前恒 0，盆地场落地后接入）。 */
        public final double basinMask01;

        public WaterColumnInputs(int worldX,
                                 int worldZ,
                                 int worldSeedInt,
                                 int seaLevel,
                                 boolean isLand,
                                 double coastWeight,
                                 double surfaceD,
                                 TalosRiverSystem.HydroSample hydro,
                                 MacroPackageId macroId,
                                 double basinMask01) {
            this.worldX = worldX;
            this.worldZ = worldZ;
            this.worldSeedInt = worldSeedInt;
            this.seaLevel = seaLevel;
            this.isLand = isLand;
            this.coastWeight = coastWeight;
            this.surfaceD = surfaceD;
            this.hydro = hydro;
            this.macroId = macroId;
            this.basinMask01 = basinMask01;
        }
    }

    /** 每一列水场输出。 */
    public static final class WaterColumnSample {
        /**
         * 该列水面高度 Y（double）。
         * Double.NEGATIVE_INFINITY = 该列无水（永不灌水）。
         * 填充端语义：仅当 waterSurfaceY > 地表高度时才灌水。
         */
        public final double waterSurfaceY;
        /** 是否为海洋列（水面恒 = seaLevel）。 */
        public final boolean isOcean;
        /** 命中的水体（湖 / 湿地 / 穿河湖 / 牛轭湖），无则 null。 */
        public final RiverBodyData body;

        public WaterColumnSample(double waterSurfaceY,
                                 boolean isOcean,
                                 RiverBodyData body) {
            this.waterSurfaceY = waterSurfaceY;
            this.isOcean = isOcean;
            this.body = body;
        }
    }

    /** 无水列（普通干陆地 / 干盆地共用；填充端不区分）。 */
    private static final WaterColumnSample DRY =
        new WaterColumnSample(Double.NEGATIVE_INFINITY, false, null);

    /** 逐列水面决策（唯一实现）。 */
    public static WaterColumnSample sampleColumn(WaterColumnInputs in) {
        // 1. 海洋列：恒有水
        if (!in.isLand) {
            return new WaterColumnSample(in.seaLevel, true, null);
        }

        // 2. 干盆地一票否决
        if (in.basinMask01 > 0.5) {
            return DRY;
        }

        // 3. 水体（湖 / 湿地 / 穿河湖 / 牛轭湖）：水面带水位偏移
        if (in.hydro != null && in.hydro.body != null) {
            return new WaterColumnSample(
                in.seaLevel + in.hydro.body.getWaterLevelOffset(),
                false,
                in.hydro.body
            );
        }

        // 4. 河道 / 源头湖区：水面 = seaLevel（宽松发水，填充端 wl > h 收敛）
        if (in.hydro != null && in.hydro.mask > 0.0) {
            return new WaterColumnSample(in.seaLevel, false, null);
        }

        // 5. 近海浅水带：coastWeight > 0.5 且最终地表低于海平面 → 保水
        //    （Math.round 与区块填充的 h 取整口径一致，避免 0.5 误差多淹一格）
        if (in.coastWeight > 0.5 && Math.round(in.surfaceD) < in.seaLevel) {
            return new WaterColumnSample(in.seaLevel, false, null);
        }

        // 6. 其余陆地：永不自动灌水
        return DRY;
    }
}
