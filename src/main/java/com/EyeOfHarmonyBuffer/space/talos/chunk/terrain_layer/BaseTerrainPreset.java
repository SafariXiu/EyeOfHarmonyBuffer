package com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer;

/**
 * 宏群系级的基础地形 preset。
 * 每个 MacroPackageId（包括 OCEANIC）对应一份。
 */

public final class BaseTerrainPreset {

    /** 平均高度（不含河谷/高山），例如陆地 60~90，海洋略低于 seaLevel。 */
    public final double baseHeight;

    /** 低频：大陆级 / 盆地级起伏（1~2 层）。 */
    public final double lowFreq;
    public final double lowAmp;
    public final int    lowOctaves;

    /** 中频：丘陵 / 台地级起伏（1~2 层）。 */
    public final double midFreq;
    public final double midAmp;
    public final int    midOctaves;

    /** 高频：小起伏 / 岩面细节（0~2 层，海洋一般 0）。 */
    public final double highFreq;
    public final double highAmp;
    public final int    highOctaves;

    /** 台地 / 高原倾向 [0,1]：0=无台地，1=强台地。 */
    public final double plateauStrength;

    /** 海床最大下切深度控制（只对 OCEANIC 有意义，其它包可为 0）。 */
    public final double oceanDepthMax;

    public BaseTerrainPreset(double baseHeight,
                             double lowFreq,  double lowAmp,  int lowOctaves,
                             double midFreq,  double midAmp,  int midOctaves,
                             double highFreq, double highAmp, int highOctaves,
                             double plateauStrength,
                             double oceanDepthMax) {

        this.baseHeight      = baseHeight;
        this.lowFreq         = lowFreq;
        this.lowAmp          = lowAmp;
        this.lowOctaves      = lowOctaves;
        this.midFreq         = midFreq;
        this.midAmp          = midAmp;
        this.midOctaves      = midOctaves;
        this.highFreq        = highFreq;
        this.highAmp         = highAmp;
        this.highOctaves     = highOctaves;
        this.plateauStrength = plateauStrength;
        this.oceanDepthMax   = oceanDepthMax;
    }
}
