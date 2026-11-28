package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

import com.EyeOfHarmonyBuffer.sim.ocean.Thermohaline;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

/**
 * ★ 2026-11-18（S4）：密度水沿坡下沉 —— Campin &amp; Goosse (1999)。
 *
 * <p>出处：MOM5 ocean_overflow.F90（2026-11-18 抓取），三个常数均为其注释明写的 C&amp;G 建议值；
 * 闭合式逐行对应交接文档 §31.2：
 * <pre>
 *   speed = g*delta/(rho0*mu) * slope * (rho_so - rho_do)   ,  speed <= umax
 *   flux  = speed * 面宽 ; flux <= 0.5*dx*dy/dt
 *   mass  = flux * min(层厚 over [k_so, k_dw])
 *   k_dw  = 下游柱中密度达到 rho_so 的层（中性浮力深度）
 * </pre>
 *
 * <p><b>为什么需要它</b>：常规对流只在 N²<=0 时发生；实测本模型极区柱是**稳定**的
 * （表层比深层轻 0.76 kg/m³），所以深水永远下不去。溢流不要求柱失稳——只要地形台阶两侧
 * 有密度差，密度水就沿坡下沉（交接 §26/§27/§31）。
 *
 * <p><b>本实现的近似</b>：以"两格之间按体积 vol 交换 T/S"实现（等量性质下送、等量原位水上返），
 * 因此**热量与盐量严格守恒**（单元测试到机器精度）；MOM5 用的是质量通量 + 连续性，
 * 本仓没有可用的厚度演化，故用守恒交换近似，已在此声明。
 *
 * <p>默认 false ⟹ 生产路径一个字节都不动（apply 第一行就 return）。
 */
public final class DenseOverflow {
    private DenseOverflow() {}

    /** 总开关。默认 false。 */
    public static boolean ON = false;

    /** C&amp;G (1999) 底部摩擦耗散率 [s^-1]；MOM5 注释 "suggest overflow_mu=10^-4"。 */
    static final double MU = 1.0e-4;
    /** 参与溢流的网格比例；MOM5 注释 "suggest overflow_delta=1/3"。 */
    static final double DELTA = 1.0 / 3.0;
    /** 最大下坡速度 [m/s]；MOM5 默认 0.01。 */
    static final double UMAX = 0.01;
    static final double RHO0 = 1027.8;

    /** 诊断：最近一次 apply 的质量通量合计 [kg/s]、触发的面数、最大速度 [m/s]。 */
    public static double LAST_FLUX_KGS = 0.0;
    public static int LAST_FACES = 0;
    public static double LAST_MAX_SPEED = 0.0;

    /** 模型自己的线性 EOS（与 Thermohaline 一致，不引新常数）。 */
    static double rho(double t, double s) {
        return RHO0 * (1.0 - Thermohaline.ALPHA * (t - 285.0) + Thermohaline.BETA * (s - 35.0));
    }

    /** 在给定时间步内施加一次溢流。ON=false 时立即返回（零副作用）。 */
    public static void apply(OceanGrid g, OceanState st, double dt) {
        LAST_FLUX_KGS = 0.0;
        LAST_FACES = 0;
        LAST_MAX_SPEED = 0.0;
        if (!ON || !(dt > 0.0)) return;
        int nx = g.nx, ny = g.ny;
        int n3 = nx * ny * g.nz;
        double[] dT = new double[n3];
        double[] dS = new double[n3];
        double factor = WorldContract.GRAVITY * DELTA / (RHO0 * MU);
        int[] di = {-1, 1, 0, 0};
        int[] dj = {0, 0, -1, 1};
        for (int j = 1; j < ny - 1; j++)
            for (int i = 1; i < nx - 1; i++) {
                if (!g.isWet(i, j)) continue;
                int wl = Math.min(g.nz, g.wetLayers[j * nx + i]);
                if (wl < 2) continue;
                int kso = wl - 1;
                int q1 = st.idx(i, j, kso);
                double tSo = st.t[q1], sSo = st.s[q1];
                double rhoSo = rho(tSo, sSo);
                double hSo = 0.0;
                for (int k = 0; k < wl; k++) hSo += g.dz[k];
                for (int m = 0; m < 4; m++) {
                    int i2 = i + di[m], j2 = j + dj[m];
                    if (i2 < 0 || i2 >= nx || j2 < 1 || j2 >= ny - 1) continue;
                    if (!g.isWet(i2, j2)) continue;
                    int wl2 = Math.min(g.nz, g.wetLayers[j2 * nx + i2]);
                    if (wl2 <= wl) continue;
                    double rhoDo = rho(st.t[st.idx(i2, j2, kso)], st.s[st.idx(i2, j2, kso)]);
                    if (!(rhoSo > rhoDo)) continue;
                    double hDo = 0.0;
                    for (int k = 0; k < wl2; k++) hDo += g.dz[k];
                    double run = (m < 2) ? g.dx : g.dy;
                    double slope = Math.abs(hDo - hSo) / run;
                    if (!(slope > 0.0)) continue;
                    double speed = Math.min(factor * slope * (rhoSo - rhoDo), UMAX);
                    double faceW = (m < 2) ? g.dy : g.dx;
                    double flux = Math.min(speed * faceW, 0.5 * g.dx * g.dy / dt);
                    int kdw = kso + 1;
                    while (kdw + 1 < wl2
                            && rho(st.t[st.idx(i2, j2, kdw)], st.s[st.idx(i2, j2, kdw)]) < rhoSo) kdw++;
                    double thick = g.dz[kso];
                    for (int k = kso; k <= kdw; k++) thick = Math.min(thick, g.dz[k]);
                    double mass = flux * thick;
                    double vol = mass * dt / RHO0;
                    double cellA = g.dx * g.dy;
                    double fSo = vol / (cellA * g.dz[kso]);
                    double fDo = vol / (cellA * g.dz[kdw]);
                    int q2 = st.idx(i2, j2, kdw);
                    double dTv = st.t[q1] - st.t[q2];
                    double dSv = st.s[q1] - st.s[q2];
                    dT[q1] -= fSo * dTv;  dS[q1] -= fSo * dSv;
                    dT[q2] += fDo * dTv;  dS[q2] += fDo * dSv;
                    LAST_FLUX_KGS += mass;
                    LAST_FACES++;
                    LAST_MAX_SPEED = Math.max(LAST_MAX_SPEED, speed);
                }
            }
        for (int q = 0; q < n3; q++)
            if (dT[q] != 0.0 || dS[q] != 0.0) { st.t[q] += dT[q]; st.s[q] += dS[q]; }
    }
}
