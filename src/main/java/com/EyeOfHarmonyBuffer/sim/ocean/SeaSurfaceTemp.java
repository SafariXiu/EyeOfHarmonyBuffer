package com.EyeOfHarmonyBuffer.sim.ocean;

/**
 * 海温异常（SST anomaly）—— **西边界暖舌 / 东边界冷舌**。
 *
 * <h3>为什么需要它（设计冻结 §33）</h3>
 * 东边界流的闭环是：
 * <pre>
 *   冷海温 -> 近海气压偏高 -> 向岸气压梯度 -> 沿岸地转风 -> 沿岸上升流 -> 更冷海温
 * </pre>
 * 而我们的 SST 原本只有纬度依赖（x 方向完全没结构）⇒ 开阔洋面上 p_x ≡ 0
 * ⇒ 沿岸风只有 0.014 Pa（需要 0.05~0.1）⇒ 沿岸层只到 1/4。
 *
 * <h3>公式（平流 + 松弛的一阶平衡）</h3>
 * 沿流线：<code>v*dT/dz = -lambda*(T - T_zm(z))</code>，令 T = T_zm + T'：
 * <pre>
 *   T' = -( v_surf / lambda ) * dT_zm/dz
 * </pre>
 * **不需要任何开关**：
 * <ul>
 *   <li>西边界流 v > 0（向极）且 dT_zm/dz < 0 ⇒ T' > 0（**暖舌**）✓</li>
 *   <li>东边界流 v < 0（向赤道）⇒ T' < 0（**冷舌**）✓</li>
 * </ul>
 *
 * <h3>标定（锚定观测，不是拍的）</h3>
 * lambda = 1/(45 天)：海洋混合层的表面热通量松弛时标（真实 30~60 天）。
 * 代入湾流表层速度 0.35 m/s 与 dT_zm/dz = -4.4e-6 K/m：
 * <pre>
 *   T' = 0.35 * 4.4e-6 / 2.572e-7 = **+6.0 K**（观测湾流暖舌 +4~8 K ✓）
 *   东边界 0.3 m/s  ⇒ **-5.1 K**（观测加州冷舌 -4~5 K ✓）
 * </pre>
 * ⇒ 两端都自然落在观测范围内，**不需要用上限去硬掰**。
 */
public final class SeaSurfaceTemp {

    private SeaSurfaceTemp() {}

    /** 表面热通量松弛速率（1/s）。45 天。 */
    public static double LAMBDA = 1.0 / (45.0 * 86400.0);
    /**
     * 异常上限（K）。
     *
     * <p>⚠ 2026-09-13（D54 重标）之后它**不再是「安全阀」**：SURF_FACTOR 从 27 降到 4.7 后，
     * 30N 带均 |T'| 已经到 +6.5 K，离 8.0 不远 ⇒ 它现在是**主动限幅器**。
     * 见 {@link #SURF_FACTOR} 的注释与 P467 判据②（饱和比例）。
     */
    public static double ANOM_MAX = 8.0;
    /**
     * 表层速度放大（**只用于海温平流**，与验收口径无关）。
     *
     * <p>锚点是**可观测的比值**：湾流表层漂流 ~0.35 m/s ÷ 全深平均 75 mm/s ≈ **4.7**。
     * （推导与「为什么不是 27」见下。）
     */
    // ⚠⚠ 2026-09-13 重标（审计 D54）：27 -> 4.7。
    // 原文同时写了两个互斥的锚，而它们指向同一个物理量：
    //   锚 A（本字段旧注释）：湾流表层 2 m/s ÷ 全深平均 75 mm/s = 27 ⇒ 隐含 v_surf = 27*0.075 = 2.0 m/s；
    //   锚 B（下面 anomaly() 的标定示例）：用 v_surf = 0.35 m/s 得到 +6.0 K。
    // 同一个公式、同一个锚、**差 5.7 倍**。0.35 / 0.075 = 4.67 ⇒ 与锚 B 自洽的值是 **4.7**，
    // 锚 B 才是那个真正复现观测（湾流暖舌 +4~8 K）的例子，所以取 4.7。
    // 为什么 27 当年「看起来对」：那时用的是**合成带风**，它的经向风极小
    // （P466：30N 的 vPeak = 0.0000、45N = 0.0088 m/s），0.01 m/s × 27 = 0.27 m/s ⇒ T' ≈ +4.3 K，正好。
    // **⇒ 27 是与「错的驱动源 + 小 20 倍的风速」一起标定出来的；换成模型自己的风必然失效。**
    // 算术核对（P466 实测的 v 与 dT_zm/dz；注意 P466 用的是**单相位**大气，OceanField 用 4 相位均，
    // 所以下面的数字是**量级锚**而不是逐位预测）：
    //   30N 带均 v=0.0866：SF=27 -> T'=+37.4 K（被 ANOM_MAX 截断）；SF=4.7 -> **+6.5 K** ✓
    //   45N 峰值 v=0.0388：SF=27 -> T'=+28.1 K（被 ANOM_MAX 截断）；SF=4.7 -> **+4.9 K** ✓（观测湾流 +4~8 K）
    //
    // ⚠ 由此产生的一个**口径后果**：ANOM_MAX = 8.0 原本是「防爆保险」（正常值远低于它），
    // 现在它落在正常值上方不多（30N 带均已 +6.5 K）⇒ **它从保险丝变成了主动限幅器**。
    // 任何对 SURF_FACTOR / 风场的后续改动都必须重新检查「饱和比例」（P467 判据②）。
    public static double SURF_FACTOR = 4.7;

    /**
     * 海温异常（K）。
     *
     * @param vSurf   表层经向速度（m/s，正 = 向极）
     * @param dTzmDz  纬向平均温度的经向梯度（K/m，正 = 向极变暖）
     */
    /**
     * 本类旋钮的**配置指纹**（纯函数，只读自己的 public static）。
     * 由 OceanField.configStamp() 折进去，最终进 SimClimate.configStamp()（审计 D58）。
     */
    public static long configStamp() {
        long h = 1125899906842597L;
        h = h * 31 + Double.doubleToLongBits(LAMBDA);
        h = h * 31 + Double.doubleToLongBits(ANOM_MAX);
        h = h * 31 + Double.doubleToLongBits(SURF_FACTOR);
        return h;
    }

    public static double anomaly(double vSurf, double dTzmDz) {
        double t = -(vSurf / LAMBDA) * dTzmDz;
        // **平滑饱和**而不是硬截断 —— 硬截断会在饱和边界上造出假的温度梯度，
        // 而 p' 的梯度直接变成风（第一跑就是这么把沿岸风毁掉的）。
        return ANOM_MAX * Math.tanh(t / ANOM_MAX);
    }
}
