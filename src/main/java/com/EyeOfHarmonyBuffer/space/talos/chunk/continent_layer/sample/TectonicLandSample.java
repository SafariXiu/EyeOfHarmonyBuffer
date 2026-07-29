package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.sample;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.ids.PlateId;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.ids.SupercontinentId;

public final class TectonicLandSample {

    public final int blockX;
    public final int blockZ;

    public final LandType landType;

    public final SupercontinentId supercontinentId;
    public final PlateId plateId;

    /** 海岸线有向距离：正数 = 内陆，负数 = 向外到海 */
    public final double signedCoastDistance;

    /** 径向“向中心权重”，0 在外环 / 海上，1 在超大陆核附近 */
    public final double radialCenterward;

    /** 海岸带权重，0 在远离海岸的地方，1 在海岸附近（陆侧） */
    public final double coastBand;

    /** 陆架权重（海侧），0 深海，1 大陆架/近岸 */
    public final double shelfBand;

    /** 板块边界接近程度（0 = 板块内部，1 = 强烈边界上） */
    public final double plateBoundaryWeight;

    public TectonicLandSample(
        int blockX,
        int blockZ,
        LandType landType,
        SupercontinentId supercontinentId,
        PlateId plateId,
        double signedCoastDistance,
        double radialCenterward,
        double coastBand,
        double shelfBand,
        double plateBoundaryWeight
    ) {
        this.blockX = blockX;
        this.blockZ = blockZ;
        this.landType = landType;
        this.supercontinentId = supercontinentId;
        this.plateId = plateId;
        this.signedCoastDistance = signedCoastDistance;
        this.radialCenterward = radialCenterward;
        this.coastBand = coastBand;
        this.shelfBand = shelfBand;
        this.plateBoundaryWeight = plateBoundaryWeight;
    }
}
