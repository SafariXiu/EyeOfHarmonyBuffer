package com.EyeOfHarmonyBuffer.sim.ocean;

/**
 * 诊断式**表层流速** —— 玩家看到、感受到的那一层。
 *
 * <h3>为什么需要它</h3>
 * `GyreRow` 给出的是**正压（深度平均）**速度。实测（P252）深度平均的西边界带内均速只有
 * 11.6 ~ 33.9 mm/s，比真实海洋的深度平均（湾流 75 mm/s）低 2~4 倍。
 * **而玩家看到的是表层**，真实海洋的表层是深度平均的 5~10 倍（湾流表层 ~1 m/s vs 深度平均 0.075 m/s）。
 *
 * <h3>两个分量（都是 O(1)，都不碰动量方程）</h3>
 * <ol>
 *   <li><b>表层强化</b>：风生环流的输运主要集中在上层，所以
 *       `u_surface = u_barotropic * (H_total / H_thermocline)`。
 *       这是**一个物理参数**（温跃层深度），不是自由拟合；</li>
 *   <li><b>埃克曼漂流</b>：`u_e = tau / (rho * sqrt(A_z * |f|))`，方向为风应力**右转 45 度**（北半球）。
 *       tau=0.1 Pa 时量级约 0.1 m/s，与真实表层漂流一致。</li>
 * </ol>
 *
 * <p>赤道附近 `|f| -> 0` 会让埃克曼公式发散，所以对 `|f|` 与幅值都设了下限/上限（见常量）。
 * 这是**已知的近似**：真实赤道埃克曼动力学与本式不同。
 *
 * <h3>⚠ 裁决记录（用户裁决，第 20 轮）</h3>
 * <ul>
 *   <li>验收口径定为 **75 mm/s @ 100 km 带宽、全深平均**（目标 2 的 A2）⇒
 *       <b>表层强化层不在任何验收路径上</b>，深度平均解本身就是验收对象。</li>
 *   <li>**埃克曼项不接受**（裁决原话）。理由已被 §22 的实测支持：
 *       埃克曼在同一行内是常数 ⇒ 对西/东差异贡献恒为 0；
 *       开了它还会把东带符号从 6/6 打到 3/6、A7 从 6/6 打到 3/6，
 *       并且在副热带 0.25 带把西边界流从 65.9 抵消到 21.1 mm/s。</li>
 *   <li>⇒ EKMAN_ENABLED 默认 **false**；本类保留只为「玩家感知量级」这个独立话题，
 *       **不要在任何验收数字里使用它**。</li>
 * </ul>
 *
 * <h3>⚠⚠ 可达性横幅（审计 D51 的处置，2026-09-13）</h3>
 * <b>本类在 src 里是零引用</b>（grep 全树：除了本文件自身的声明，没有任何一处 new/调用/读字段）。
 * 它只被探针 P253/P254 用来说明「表层强化倍率」这个独立话题。
 * ⇒ <b>本类的任何常量都不在任何生产路径、也不在任何验收路径上。</b>
 * 之所以要写这一条：本类与 {@link CoastalLayer} 曾经共享两个常量名（H_THERMOCLINE / F_MIN），
 * 值却不同（800 vs 150、1.27e-5 vs 1.0e-5），而其中只有 CoastalLayer 那一份是活的 ——
 * 一个只看名字的审计者会以为「同一个量漂了 5.3 倍」。**现在名字已经分开**（见下）。
 */
public final class SurfaceLayer {

    private SurfaceLayer() {}

    /** ⚠ 2026-09-13（审计 D5 同族）：原来是第二份 7.2921e-5 字面量，现引用世界契约的单一来源。 */
    public static final double OMEGA = com.EyeOfHarmonyBuffer.sim.world.WorldContract.OMEGA;
    /**
     * **风生环流输运层厚度（m）** —— 风生输运集中在这一层里，
     * 所以 u_surface = u_barotropic x (H_total / 本值)。
     *
     * <p>⚠ <b>改名记录（审计 D51）</b>：原名 {@code H_THERMOCLINE}，与
     * {@link CoastalLayer#H_THERMOCLINE}（**150 m，沿岸上升流层厚度**）**同名但差 5.3 倍**。
     * 两者是**两个不同的物理量**：
     * <ul>
     *   <li>沿岸上升流层（CoastalLayer，150 m）：大陆架/沿岸上升流把温跃层顶到近表层；</li>
     *   <li>风生输运层（本类，800 m）：大洋内区风生环流的垂向衰减尺度。</li>
     * </ul>
     * 值本身都没错，错的是**共用一个名字** ⇒ 现在分开命名，口径各自写在名字里。
     */
    public static double H_TRANSPORT = 800.0;
    /** 埃克曼层涡黏性（m^2/s），真实海洋典型值 1e-2。 */
    public static double A_Z = 0.01;
    /**
     * |f| 下限（s^-1），防止赤道发散。锚点 = **约 5 度纬度**（2*Omega*sin(5 deg) = 1.27e-5）。
     *
     * <p>⚠ <b>与 {@link CoastalLayer#F_MIN}（1.0e-5）不是同一个量</b>（审计 D51）：
     * 那个是**沿岸层**的地转下限，锚点是 3.93 度；两者服务的层不同、锚点不同。
     * <b>不要「统一」成一个值</b> —— 那会改掉其中一条链的物理。
     */
    public static double F_MIN = 1.27e-5;
    /** 埃克曼流速上限（m/s）。 */
    public static double EKMAN_MAX = 0.35;
    /** 埃克曼项开关。**默认关闭**（用户裁决；理由见类注释）。 */
    public static boolean EKMAN_ENABLED = false;
    public static double RHO = 1025.0;

    /**
     * 表层速度 = 正压速度 x (hTotal/H_TRANSPORT) + 埃克曼漂流。
     *
     * @param uBar,vBar 正压（深度平均）速度 m/s
     * @param tauX,tauY 风应力 Pa
     * @param f         科氏参数（带符号）
     * @param hTotal    正压解使用的总深度 m
     * @return {u, v} m/s
     */
    public static double[] at(double uBar, double vBar, double tauX, double tauY, double f, double hTotal) {
        double s = hTotal / H_TRANSPORT;
        double u = uBar * s, v = vBar * s;
        double fa = Math.abs(f);
        if (fa < F_MIN) fa = F_MIN;
        double tau = Math.hypot(tauX, tauY);
        if (EKMAN_ENABLED && tau > 1e-12) {
            double ue = tau / (RHO * Math.sqrt(A_Z * fa));
            if (ue > EKMAN_MAX) ue = EKMAN_MAX;
            // 埃克曼漂流方向 = 风应力右转 45 度（f>0）；南半球左转
            double ang = Math.atan2(tauY, tauX) - Math.signum(f) * Math.PI / 4.0;
            u += ue * Math.cos(ang);
            v += ue * Math.sin(ang);
        }
        return new double[]{u, v};
    }
}
