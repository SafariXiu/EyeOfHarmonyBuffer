package com.EyeOfHarmonyBuffer.sim.erosion;

/**
 * <b>Runevision 侵蚀滤镜（Advanced Terrain Erosion Filter）的 Java 移植。</b>
 *
 * <p><b>许可证</b>：算法与原始 GLSL/C# 版权 (c) 2025 Rune Skovbo Johansen，
 * MPL 2.0；本移植同属 MPL 2.0。参考实现（逐行对照过）：
 * <ul>
 *   <li>{@code K:/moder/TT/analysis/refs/catto-ErosionFilterOCL.java}（GLSL 原版，115 行）</li>
 *   <li>{@code K:/moder/TT/analysis/refs/catto-ErosionFilter.java}（CATTO 的 Java 翻译，135 行）</li>
 *   <li>{@code K:/moder/TT/analysis/refs/lpmitchell-AdvancedTerrainErosion.cs}（C# 参考，698 行）</li>
 * </ul>
 *
 * <p><b>§7698 找到并修掉的七个偏差（全部已在本实现中修正）</b>：
 * <ol>
 *   <li>地形必须用<b>连续</b>高度（{@code c.hCapped}），不是整数 {@code c.h} —— 否则梯度被量化</li>
 *   <li>{@code hash2} 值域必须是 <b>[-1,1]</b>，不是 [0,1)</li>
 *   <li>必须保留 {@code Erosion.Scale}：{@code freq = 1/(scale*cellScale)}</li>
 *   <li>调用处不能写死 {@code cellScale}</li>
 *   <li>{@code gullySlope} 不能双重缩放；{@code roundingForOctave} 用递增的 {@code roundingMult}</li>
 *   <li>{@code smoothStart(t, s)} 返回<b>平滑斜坡的值</b>（{@code t-0.5s} 或 {@code 0.5t^2/s}），
 *       <b>不是</b> {@code clamp(t/s,0,1)}</li>
 *   <li>{@code rounding.z} 必须匹配<b>本仓地形的坡度量级</b>（见 {@link #ROUNDING_SCALE}）</li>
 * </ol>
 *
 * <p><b>§7712 ETOPO 标定</b>：本仓地形的功率谱斜率已按 ETOPO 实测标定
 * （{@code TalosField.HH = 0.4097}、{@code HH_HIGH = 0.3549}，对应
 * {@code beta = 2H+2 = 2.82 / 2.71}，实测脚本见
 * {@code K:/moder/TT/etopo_spectrum/}）。
 *
 * <p><b>§7714 细节层各向同性</b>：细节层<b>必须用</b> {@link DetailNoise}
 * （Simplex + 每 octave 旋转黄金角）。用 {@code TerrainNoise.fbm2D}（双线性
 * value noise）会留下 45 度晶格偏置（实测 aniso = 1.327），在侵蚀图上表现为
 * <b>斜向条带</b>。
 */
public final class ErosionFilter {

    private ErosionFilter() {}

    // ==================== 参数（默认值全部来自 GLSL 原版） ====================

    /** {@code Erosion.Scale}：cell 的世界尺度 = SCALE * CELLSCALE（单位：世界单位）。 */
    public static final double SCALE = 0.15;

    /** {@code Erosion.CellScale}：每 cell 的条纹数约 1.5*CELLSCALE。 */
    public static final double CELLSCALE = 0.7;

    /** 八度数（GLSL 原版默认 5）。 */
    public static final int OCTAVES = 5;

    /** 每 octave 的强度衰减。 */
    public static final double GAIN = 0.5;

    /** 每 octave 的频率倍率。 */
    public static final double LACUNARITY = 2.0;

    /** 归一化阈值：只归一化长度 >= 0.5 的（GLSL 原版 {@code k=2}）。 */
    public static final double NORMALIZATION = 0.5;

    /** 脊/沟的圆化（{@code Rounding.x / .y}）。 */
    public static final double RIDGE_ROUNDING = 0.1;
    public static final double CREASE_ROUNDING = 0.0;

    /**
     * ★ <b>§7698 修正：{@code Rounding.z} 的放大倍数。</b>
     *
     * <p>GLSL 原版用 {@code rounding.z = 0.1}，但那要求输入地形的
     * {@code slopeLength} 与 {@code rfi} 同量级。本仓实测 {@code slopeLength} 约 0.22，
     * 而 {@code rfi} 只有 [0, 0.01] —— 差 20-40 倍，会导致 {@code combiMask} 恒为常数、
     * fade 完全不工作（表现为峰谷「点扭在一起」）。放大 10 倍后 {@code rfi} 落在 [0, 1.0]，
     * 与 slope 匹配。<b>这是一个标定值，不是原版常数</b>。
     */
    public static final double ROUNDING_SCALE = 10.0;

    /**
     * ★ <b>输出的 DC（零频）分量 —— 必须减掉。</b>
     *
     * <p><b>§7722 实测</b>：{@code erosion()} 的原始输出不是零均值的。
     * 在 256x256（步长 16）样本上实测（{@code ERO_WL0=50000, ERO_OCT=8,
     * ERO_STRENGTH=0.267}）：
     * <pre>
     *   dh  mean = -0.44878   sd = 0.03920
     *   ⇒ 乘 ERO_AMP 后 mean = -179.5 格（!!）, sd = 15.7 格
     * </pre>
     * 也就是说 <b>DC 是起伏的 11 倍</b>。不减掉它，地形会被整体压低 ~180 格
     * ⇒ 全部沉到海平面以下（{@code §7722} 实测：整张图变成纯海洋）。
     *
     * <p><b>为什么会有这个 DC</b>：{@code fgx = mix(fadeTarget, phx*gullyWeight, combiMask)}
     * 在 {@code combiMask} 大时趋近 {@code phx*0.5}，而 {@code phx}（归一化后的
     * 相位方向 x 分量）在多个 octave 上累加后有一个稳定的负偏置。
     *
     * <p><b>口径</b>：本常数 = 上述实测均值，<b>是一个标定值</b>。
     * 改 {@code ERO_WL0} / {@code ERO_OCT} / {@code ERO_STRENGTH} / {@code ROUNDING_SCALE}
     * 都必须重新标定它（用 {@code P1363}）。
     */
    public static final double DC = -0.44878;

    /** {@code Rounding.w}：每 octave 的圆化倍率。 */
    public static final double ROUNDING_PER_OCTAVE = 2.0;

    /** {@code Onset.x / .y}。 */
    public static final double ONSET_INIT = 1.25;
    public static final double ONSET_OCTAVE = 1.25;

    /** {@code AssumedSlope}：假定的输入坡度与权重（权重 1.0 = 完全用假定坡度）。 */
    public static final double ASSUMED_SLOPE = 0.7;
    public static final double ASSUMED_SLOPE_WEIGHT = 1.0;

    // ==================== shader 风格辅助函数（逐字照搬） ====================

    static double clamp01(double v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }
    static double mix(double x, double y, double a) { return x * (1 - a) + y * a; }

    /** {@code PowInv(t,p) = 1 - (1-clamp01(t))^p}。 */
    static double powInv(double t, double p) {
        double v = 1.0 - clamp01(t);
        return 1.0 - Math.pow(v, p);
    }

    /** {@code EaseOut(t) = 1 - (1-clamp01(t))^2}。 */
    static double easeOut(double t) {
        double v = 1.0 - clamp01(t);
        return 1.0 - v * v;
    }

    /**
     * ★ <b>{@code SmoothStart} —— 最容易写错的一个。</b>
     *
     * <p>它返回<b>一个平滑斜坡的值</b>（量纲 = t），<b>不是</b>归一化到 [0,1]。
     * 照搬 GLSL/C# 逐字实现。
     */
    static double smoothStart(double t, double smoothing) {
        if (smoothing <= 1e-12) return t;
        if (t >= smoothing) return t - 0.5 * smoothing;
        return 0.5 * t * t / smoothing;
    }

    /** {@code fract(v) = v - floor(v)}。 */
    static double fract(double v) { return v - Math.floor(v); }

    // ==================== Hash2（逐字照搬 C#） ====================

    private static final double HKX = 1.0 / Math.PI;
    private static final double HKY = Math.exp(-1.0);
    private static final double SSX = 0.06711056;
    private static final double SSY = 0.00583715;

    /** 输出两个分量，各在 <b>[-1, 1]</b>（不是 [0,1)）。 */
    static double[] hash2(double x, double y, int seed) {
        double ox = seed * SSX, oy = seed * SSY;
        double ax = (x + ox) * HKX + HKY, ay = (y + oy) * HKY + HKX;
        double t = fract(ax * ay * (ax + ay));
        double ix = 16.0 * HKX * t, iy = 16.0 * HKY * t;
        return new double[] { -1.0 + 2.0 * fract(ix), -1.0 + 2.0 * fract(iy) };
    }

    // ==================== PhacelleNoise（逐字照搬，4x4 cell 混合） ====================

    /**
     * @param px,py   已乘过 freq 的坐标
     * @param ndx,ndy 归一化的条纹方向
     * @param freq    条纹频率（= cellScale）
     * @return {interpX, interpY, sideDirX, sideDirY}
     */
    static double[] phacelleNoise(double px, double py, double ndx, double ndy,
                                  double freq, double offset, double normalization) {
        double sdx = -ndy * freq * (Math.PI * 2.0);
        double sdy = ndx * freq * (Math.PI * 2.0);
        double no = offset * (Math.PI * 2.0);
        int pix = (int) Math.floor(px), piy = (int) Math.floor(py);
        double pfx = px - pix, pfy = py - piy;
        double pdx = 0, pdy = 0, wsum = 0;
        for (int i = -1; i <= 2; i++) {
            for (int j = -1; j <= 2; j++) {
                double[] hh = hash2(pix + i, piy + j, 1337);
                double rox = hh[0] * 0.5, roy = hh[1] * 0.5;
                double vx = pfx - i - rox, vy = pfy - j - roy;
                double w = Math.max(0.0, Math.exp(-(vx * vx + vy * vy) * 2.0) - 0.01111);
                wsum += w;
                double wi = vx * sdx + vy * sdy + no;
                pdx += Math.cos(wi) * w;
                pdy += Math.sin(wi) * w;
            }
        }
        double ix = pdx / Math.max(wsum, 1e-10), iy = pdy / Math.max(wsum, 1e-10);
        double mag = Math.max(1.0 - normalization, Math.sqrt(ix * ix + iy * iy));
        return new double[] { ix / mag, iy / mag, sdx, sdy };
    }

    // ==================== 主函数 ====================

    /**
     * 计算一个点的侵蚀高度偏移。
     *
     * @param px,py      世界位置（<b>世界单位</b>，见 {@link #SCALE}）
     * @param slopeX,slopeY 输入高度场的梯度 <b>d(height)/d(worldUnit)</b>，
     *                   而 height 必须归一化到 [-1,1]
     * @param fadeTarget 归一化海拔，<b>[-1, 1]</b>（谷 -1 / 峰 +1）
     * @param strength   侵蚀强度
     * @param gullyWeight 沟壑权重（GLSL 默认 0.5）
     * @param detail     细节指数（GLSL 默认 1.5）
     * @return 高度偏移，单位与 {@code height} 相同（即 [-1,1] 空间）
     */
    public static double erosion(double px, double py, double slopeX, double slopeY,
                                 double fadeTarget, double strength, double gullyWeight,
                                 double detail) {
        double strengthLocal = strength;
        fadeTarget = Math.max(-1.0, Math.min(1.0, fadeTarget));
        double freq = 1.0 / (SCALE * CELLSCALE);
        double slopeLength = Math.max(Math.sqrt(slopeX * slopeX + slopeY * slopeY), 1e-10);
        double roundingMult = 1.0;

        double rfi = mix(CREASE_ROUNDING, RIDGE_ROUNDING,
                         clamp01(fadeTarget + 0.5)) * (0.1 * ROUNDING_SCALE);
        double combiMask = easeOut(smoothStart(slopeLength * ONSET_INIT, rfi * ONSET_INIT));

        // 假定的输入坡度（GLSL：lerp(yz, yz/slopeLength*assumedSlope.x, assumedSlope.y)）
        double gsx = mix(slopeX, slopeX / slopeLength * ASSUMED_SLOPE, ASSUMED_SLOPE_WEIGHT);
        double gsy = mix(slopeY, slopeY / slopeLength * ASSUMED_SLOPE, ASSUMED_SLOPE_WEIGHT);

        double hAcc = 0.0;
        for (int i = 0; i < OCTAVES; i++) {
            double gl = Math.max(Math.sqrt(gsx * gsx + gsy * gsy), 1e-10);
            double[] ph = phacelleNoise(px * freq, py * freq, gsx / gl, gsy / gl,
                                        CELLSCALE, 0.25, NORMALIZATION);
            double phx = ph[0], phy = ph[1];
            double phz = -ph[2] * freq, phw = -ph[3] * freq;

            double sloping = Math.abs(phy);
            double sgn = phy >= 0 ? 1.0 : -1.0;
            // gullySlope 累加（【不被 mask 淡化】—— GLSL 原版如此）
            gsx += phz * sgn * strengthLocal * gullyWeight;
            gsy += phw * sgn * strengthLocal * gullyWeight;

            double fgx = mix(fadeTarget, phx * gullyWeight, combiMask);
            hAcc += fgx * strengthLocal;
            fadeTarget = fgx;

            double rfo = mix(CREASE_ROUNDING, RIDGE_ROUNDING,
                             clamp01(phx + 0.5)) * roundingMult * ROUNDING_SCALE;
            double newMask = easeOut(smoothStart(sloping * ONSET_OCTAVE, rfo * ONSET_OCTAVE));
            combiMask = powInv(combiMask, detail) * newMask;

            strengthLocal *= GAIN;
            freq *= LACUNARITY;
            roundingMult *= ROUNDING_PER_OCTAVE;
        }
        // ★ §7722：减掉 DC（见 DC 常数的 javadoc）。不减会把地形整体压低 ~180 格。
        return hAcc - DC;
    }
}
