package com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer;

/**
 * 宏群系级的基础地形 preset。
 * 每个 MacroPackageId（包括 OCEANIC）对应一份。
 *
 * 高度控制模型：噪声只负责「形状」，最终高度被锁定在
 * [minHeight, maxHeight] 高度带内（宏包做大限制，群系级微调留待后续）。
 */

public final class BaseTerrainPreset {

    /** 高度带下限（陆地不应低于海平面时，至少给 64）。 */
    public final double minHeight;

    /** 高度带上限（山地可直接顶到 256）。 */
    public final double maxHeight;

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

    /** 岩面噪声主幅度（blocks）：0 = 不叠加。 */
    public final double cliffAmp;

    /** 岩面噪声主波长（blocks）：越大起伏越舒缓。 */
    public final double cliffScale;

    /** 台阶化强度 [0,1]：0 = 连续起伏，1 = 明显台地/岩架。 */
    public final double terrace;

    /** 岩面细节噪声幅度（blocks）。 */
    public final double detailAmp;

    public BaseTerrainPreset(double minHeight,
                             double maxHeight,
                             double lowFreq,  double lowAmp,  int lowOctaves,
                             double midFreq,  double midAmp,  int midOctaves,
                             double highFreq, double highAmp, int highOctaves,
                             double plateauStrength,
                             double oceanDepthMax,
                             double cliffAmp,
                             double cliffScale,
                             double terrace,
                             double detailAmp) {

        this.minHeight       = minHeight;
        this.maxHeight       = maxHeight;
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
        this.cliffAmp        = cliffAmp;
        this.cliffScale      = cliffScale;
        this.terrace         = terrace;
        this.detailAmp       = detailAmp;
    }
}
