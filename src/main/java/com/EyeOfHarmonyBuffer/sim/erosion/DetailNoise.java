package com.EyeOfHarmonyBuffer.sim.erosion;

import com.EyeOfHarmonyBuffer.space.talos.chunk.util.SimplexNoise2D;

/**
 * <b>侵蚀滤镜的细节层噪声 —— Simplex + 每 octave 旋转黄金角。</b>
 *
 * <p><b>为什么不能用 value noise（§7714 实测）</b>：
 * {@code TerrainNoise.fbm2D} 走的是 {@code NoiseUtil.coreNoise2D}，那是
 * <b>双线性插值的 value noise</b>。它的等值线在格内是双曲线，功率谱在
 * <b>45 度方向有晶格偏置</b>。实测角向各向异性（中频环 0.05-0.35 cycles/px）：
 *
 * <pre>
 *   TerrainNoise.fbm2D  (value, 5 oct)  aniso(45/0) = 1.327   max/min = 1.802
 *   SimplexNoise2D fBm  (grad,  5 oct)  aniso(45/0) = 1.170   max/min = 2.184
 *   SimplexNoise2D fBm  + 黄金角旋转     aniso(45/0) = 1.108   max/min = 1.720
 *   SimplexNoise2D fBm  + 旋转 + 8 seed  aniso(45/0) = 1.007   max/min = 1.714
 * </pre>
 *
 * <p>value noise 的 5 个 octave <b>共用同一个晶格朝向</b>（{@code x*freq} 只缩放不旋转），
 * 所以偏置不会随 octave 抵消。每 octave 旋转晶格后，各 octave 的偏置方向不同，
 * 叠加时互相抵消。
 *
 * <p><b>为什么是黄金角</b>：{@code 2*pi*(1 - 1/phi) = 2.399963229728653 rad = 137.507 度}。
 * 它使连续旋转的朝向在圆上<b>最均匀</b>（向日葵排列）。实测在扫描的 6 个角度里
 * 给出最小的 {@code max/min}（1.720）。
 */
public final class DetailNoise {

    private DetailNoise() {}

    /** 黄金角（弧度）：{@code 2*pi*(1 - 1/phi)}，phi = 1.6180339887。 */
    public static final double GOLDEN_ANGLE = 2.399963229728653;

    /**
     * §7758: 逐 octave 差分步长 = wl * STEP_SCALE。
     *
     * 为什么可以调：参考库（lpmitchell.Noised）用的是【解析导数】，根本没有步长这个量；
     * 我们用中心差分，所以步长是【我们的选择】，不是移植来的常数。
     *
     * 原来的 0.5 有什么问题（P1468 实测）：step = wl*0.5 = 半个波长
     * ==> 每个 octave 只有【2 个采样/周期】
     * ==> 最粗的 octave（wl=50000，step=25000）在很大空间范围内几乎不变
     * ==> 它主导了局部梯度方向 ==> 方向被锁死。
     *
     * STEP_SCALE | 方向在 73 格内的平均变化 | 16 格内
     *      0.5    |        10.16 度          |  6.79 度   <== 原来
     *      0.25   |        16.78 度          | 11.46 度   <== +65%
     *      0.125  |        13.64 度          |  9.40 度
     *      0.0625 |        13.45 度          |  9.28 度
     *
     * 而「方向变化」正是侵蚀条纹能否转弯/分叉、从而不连成直线的唯一来源
     * （§16/§17：形态由方向结构决定，与振幅无关）。
     */
    public static double STEP_SCALE = 0.25;

    /**
     * ★ <b>§7731：每 octave 的振幅衰减 —— 由 ETOPO 标定的 beta 导出。</b>
     *
     * <p><b>出处</b>：{@code §7712} 的 ETOPO 功率谱标定
     * （{@code etopo_spectrum/fit_summary.txt}：10 arc-min {@code beta=2.8193, R²=0.9983}；
     * 2022 15 arc-sec {@code beta=2.7100, R²=0.9960}；两者在重叠带一致 ⟹ 地球是单一幂律）。
     * 取均值 {@code beta = 2.7647}。
     *
     * <p><b>推导</b>：对 {@code P(f) ~ f^-beta} 的二维分形面，振幅谱是
     * {@code A(f) ~ f^(-beta/2)}。octave 每级频率翻倍 ⟹
     * {@code amp *= 2^(-beta/2) = 2^(-1.3824) = } {@link #AMP_GAIN}。
     *
     * <p><b>为什么重要</b>：原来的 {@code amp *= 0.5}（即 beta=2）在【高频过强】，
     * 于是「加 octave 打散 cell 边界」与「不产生砂纸纹理」互相冲突。
     * 用 ETOPO 的 beta 之后高频自然衰减，两个目标可以同时满足。
     * <b>这不是新旋钮 —— 它由 §7712 的实测 beta 导出。</b>
     */
    public static final double AMP_GAIN = Math.pow(2.0, -2.7647 / 2.0);   // = 0.3825

    /**
     * 单次 fBm 采样（无每 octave 旋转）—— 保留用于对照。
     *
     * @param seed 噪声种子
     * @param x,z  世界坐标（block）
     * @param oct  八度数
     * @param wl0  最粗波长（block）
     * @return 大致 [-1, 1]
     */
    public static double fbm(long seed, double x, double z, int oct, double wl0) {
        return fbmRot(seed, x, z, oct, wl0, 0.0);
    }

    /**
     * fBm + 每 octave 旋转晶格。
     *
     * @param rot 每 octave 的旋转角（弧度）；{@link #GOLDEN_ANGLE} 是推荐值
     */
    public static double fbmRot(long seed, double x, double z, int oct, double wl0, double rot) {
        SimplexNoise2D sn = new SimplexNoise2D(seed);
        double s = 0.0, amp = 1.0, fq = 1.0 / wl0;
        double ca = 1.0, sa = 0.0;
        double cR = Math.cos(rot), sR = Math.sin(rot);
        for (int o = 0; o < oct; o++) {
            double xx = x * ca - z * sa;
            double zz = x * sa + z * ca;
            s += amp * sn.noise2(xx * fq, zz * fq);
            amp *= 0.5;
            fq *= 2.0;
            if (rot != 0.0) {
                double nc = ca * cR - sa * sR;
                double ns = ca * sR + sa * cR;
                ca = nc; sa = ns;
            }
        }
        return s;
    }

    /** 推荐的细节层（ridge 化），返回一个加性高度偏移（block）。 */
    public static double ridged(long seed, double x, double z, double amp, double wl0, int oct) {
        double r = fbmRot(seed, x, z, oct, wl0, GOLDEN_ANGLE);
        return amp * (1.0 - Math.abs(r));
    }

    // ==================== 梯度（侵蚀滤镜的输入） ====================

    /**
     * 细节层的<b>世界单位梯度</b> {@code d(h)/d(worldUnit)}，写入 {@code out[0]=dx, out[1]=dz}。
     *
     * <p><b>为什么[可以]用差分</b>：侵蚀的输入高度场<b>只是这个细节层</b>
     * （λ 210-550 m），<b>不是</b>整条地形链。所以差分只需 2 次额外
     * {@link #fbmRot}（约 100 ns 各），<b>不是</b> 2 次 {@code composeColumn}
     * （6,130 ns 各）。实测总增量见 §7716。
     *
     * <p><b>步长的选择</b>：取细节层<b>最细波长的一半</b>，即
     * {@code wl0 / 2^oct}，这样差分能分辨最细的 octave 而不会过度平滑。
     *
     * @param unit 世界单位的长度（block/worldUnit）；{@code p = x/unit}
     * @param out  长度 >= 2 的输出数组
     */
    public static void grad(long seed, double x, double z, int oct, double wl0,
                            double unit, double[] out) {
        // ★ §7722：**逐 octave 用各自的步长**。
        //   曾经这里只算一次中心差分，步长 = wl0/2^oct（= 最细波长）。
        //   ERO_OCT=8 时那是 wl0/256 —— 对最粗的 octave 太短（噪声），
        //   对较细的 octave 又太长（**被完全平滑掉**）。实测后果：频谱中间
        //   一大段没有梯度贡献，侵蚀只剩最粗的一层 ⟹ 山脊消失。
        //   现在每个 octave 用 step_o = wl_o/2（其自身波长的一半）做中心差分，
        //   再按该 octave 的振幅加权求和 —— 这才是 fBm 的正确谱梯度。
        SimplexNoise2D sn = new SimplexNoise2D(seed);
        double gx = 0.0, gz = 0.0;
        double amp = 1.0, wl = wl0;
        double ca = 1.0, sa = 0.0;
        double cR = Math.cos(GOLDEN_ANGLE), sR = Math.sin(GOLDEN_ANGLE);
        for (int o = 0; o < oct; o++) {
            double step = wl * STEP_SCALE;
            if (step < 1.0) step = 1.0;
            double fq = 1.0 / wl;
            // 旋转后的基坐标
            double bx = x * ca - z * sa, bz = x * sa + z * ca;
            // 该 octave 在旋转后空间里的方向导数（链式法则带上旋转矩阵）
            double dx = (sn.noise2((bx + step) * fq, bz * fq) - sn.noise2((bx - step) * fq, bz * fq))
                      / (2.0 * step);
            double dz = (sn.noise2(bx * fq, (bz + step) * fq) - sn.noise2(bx * fq, (bz - step) * fq))
                      / (2.0 * step);
            // 旋转回去：R^T * (dx,dz)
            gx += amp * (dx * ca + dz * sa);
            gz += amp * (-dx * sa + dz * ca);
            // ★ §7731：同上
            amp *= AMP_GAIN; wl *= 0.5;
            double nc = ca * cR - sa * sR, ns = ca * sR + sa * cR;
            ca = nc; sa = ns;
        }
        // d(h)/d(x) 单位 1/block；乘 unit 得 d(h)/d(worldUnit)
        out[0] = gx * unit;
        out[1] = gz * unit;
    }

    /**
     * ridge 化的梯度（{@code h = amp*(1-|r|)}）。
     *
     * <p>⚠ 在 {@code r = 0} 处 {@code |r|} 不可导。实践中用<b>中心差分直接对
     * ridged 值求导</b>即可 —— 差分会自动平滑掉那个折点，而侵蚀的
     * {@code combiMask} 本来就会在坡度为 0 处淡出。
     */
    /**
     * §7790: ridge 化梯度的符号函数光滑化宽度。0 = 硬符号（逐位回滚点）。
     *
     * 为什么需要（P1523/P1526/P1529 实测）：
     *   gradRidged 对每个 octave 用 sgn = (r >= 0 ? -1 : 1)  —— 一个【硬符号翻转】。
     *   当 ridge 值 r 跨过 0 时，符号【瞬时跳变】=> 梯度方向【瞬时反转 180 度】。
     *   生产端把该梯度当侵蚀的输入方向 => 滤镜在那里被瞬间翻转
     *   => 地形出现 27.21 格的【单格断崖】（world -1999901,-2000076 实测）。
     *
     * 修法：sgn(r) 用 tanh(r / RIDGE_SIGN_EPS) 代替硬符号 —— 处处光滑，
     *   且 |r| 远离 0 时与原式【逐位接近】（tanh 饱和到 ±1）。
     *
     * ⚠ 与 gradRidged 的旧注释（:177-179「差分会自动平滑掉那个折点」）不冲突：
     *   差分确实平滑了 |r| 的【折点】，但 sign(r) 的【跳变】不是折点，是不连续。
     */
    public static double RIDGE_SIGN_EPS = 0.0;

    public static void gradRidged(long seed, double x, double z, double amp,
                                  int oct, double wl0, double unit, double[] out) {
        // ★ §7722：与 grad 同样的【逐 octave 步长】，但每个 octave 走 ridge 化 h = amp*(1-|r|)。
        //   那才是造【山脊】的输入 —— raw fBm 的梯度只造圆丘（实测对照见 §7722）。
        SimplexNoise2D sn = new SimplexNoise2D(seed);
        double gx = 0.0, gz = 0.0, a = amp, wl = wl0;
        double ca = 1.0, sa = 0.0;
        double cR = Math.cos(GOLDEN_ANGLE), sR = Math.sin(GOLDEN_ANGLE);
        for (int o = 0; o < oct; o++) {
            double step = wl * STEP_SCALE;
            if (step < 1.0) step = 1.0;
            double fq = 1.0 / wl;
            double bx = x * ca - z * sa, bz = x * sa + z * ca;
            double r  = sn.noise2(bx * fq, bz * fq);
            // d/dx of (1-|r|) = -sign(r) * dr/dx
            // §7790：RIDGE_SIGN_EPS > 0 时用光滑饱和代替硬符号（0 = 逐位不变）
            double sgn = (RIDGE_SIGN_EPS > 0.0)
                       ? -Math.tanh(r / RIDGE_SIGN_EPS)
                       : (r >= 0.0 ? -1.0 : 1.0);
            double drx = (sn.noise2((bx + step) * fq, bz * fq)
                        - sn.noise2((bx - step) * fq, bz * fq)) / (2.0 * step);
            double drz = (sn.noise2(bx * fq, (bz + step) * fq)
                        - sn.noise2(bx * fq, (bz - step) * fq)) / (2.0 * step);
            gx += a * sgn * drx;
            gz += a * sgn * drz;
            // ★ §7731：用 ETOPO 标定的 beta 导出的衰减（0.3825），而不是 0.5
            a *= AMP_GAIN; wl *= 0.5;
            double nc = ca * cR - sa * sR, ns = ca * sR + sa * cR;
            ca = nc; sa = ns;
        }
        out[0] = gx * unit;
        out[1] = gz * unit;
    }

    /**
     * ★ 与 {@link #gradRidged} <b>同源</b>的 ridge 化高度值 —— 供 {@code fadeTarget}
     * 或任何需要"侵蚀输入高度"的地方使用（同一组 octave、同一组振幅）。
     */
    public static double ridgedValue(long seed, double x, double z, int oct, double wl0) {
        SimplexNoise2D sn = new SimplexNoise2D(seed);
        double s = 0.0, a = 1.0, wl = wl0;
        double ca = 1.0, sa = 0.0;
        double cR = Math.cos(GOLDEN_ANGLE), sR = Math.sin(GOLDEN_ANGLE);
        for (int o = 0; o < oct; o++) {
            double fq = 1.0 / wl;
            double bx = x * ca - z * sa, bz = x * sa + z * ca;
            s += a * (1.0 - Math.abs(sn.noise2(bx * fq, bz * fq)));
            // ★ §7731：同上
            a *= AMP_GAIN; wl *= 0.5;
            double nc = ca * cR - sa * sR, ns = ca * sR + sa * cR;
            ca = nc; sa = ns;
        }
        return s;
    }
}
