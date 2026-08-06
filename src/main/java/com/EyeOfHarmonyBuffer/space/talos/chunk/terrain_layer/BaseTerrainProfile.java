package com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer;

/**
 * 可变 profile：在 BaseTerrainPreset 基础上附加子群系 / mountainWeight 等调制。
 */

public final class BaseTerrainProfile {

    public double minHeight;
    public double maxHeight;

    public double lowFreq;
    public double lowAmp;
    public int    lowOctaves;

    public double midFreq;
    public double midAmp;
    public int    midOctaves;

    public double highFreq;
    public double highAmp;
    public int    highOctaves;

    public double plateauStrength;

    public double oceanDepthMax;

    public double cliffAmp;
    public double cliffScale;
    public double terrace;
    public double detailAmp;

    public static BaseTerrainProfile fromPreset(BaseTerrainPreset p) {
        BaseTerrainProfile r = new BaseTerrainProfile();
        r.minHeight       = p.minHeight;
        r.maxHeight       = p.maxHeight;
        r.lowFreq         = p.lowFreq;
        r.lowAmp          = p.lowAmp;
        r.lowOctaves      = p.lowOctaves;
        r.midFreq         = p.midFreq;
        r.midAmp          = p.midAmp;
        r.midOctaves      = p.midOctaves;
        r.highFreq        = p.highFreq;
        r.highAmp         = p.highAmp;
        r.highOctaves     = p.highOctaves;
        r.plateauStrength = p.plateauStrength;
        r.oceanDepthMax   = p.oceanDepthMax;
        r.cliffAmp        = p.cliffAmp;
        r.cliffScale      = p.cliffScale;
        r.terrace         = p.terrace;
        r.detailAmp       = p.detailAmp;
        return r;
    }
}
