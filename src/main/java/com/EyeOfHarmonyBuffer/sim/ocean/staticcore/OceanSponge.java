package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

/**
 * patch 外圈的 sponge 边界（无限世界架构 §2/§4）。
 *
 * <p>物理身份：这是<b>数值边界</b>，不是物理参数。做法是把外圈 {@code band} 个格点
 * 在每一步之后向父状态做平滑混合：最外一圈权重 1（等价 Dirichlet），向内按
 * smoothstep 衰减到 0。没有时间尺度常量、没有可调系数——衰减形状由带内距离给出。
 *
 * <p>x 方向：当前 patch 的动力核心（BarotropicSolver / VelocityProjection）是
 * x 周期的，所以暂时只在 y 两端加 sponge（{@code periodicX=true}）。等阶段 2c
 * 打开 x 开边界后再用 {@code periodicX=false}。
 */
public final class OceanSponge {

    private OceanSponge() {}

    /** 已应用的次数（诊断用）。 */
    public static long APPLY_COUNT = 0;
    /** 最近一次的最大松弛权重（诊断用；应约为 1）。 */
    public static double LAST_MAX_W = 0.0;

    /**
     * @param band       sponge 带宽度（格点数），必须 > 0
     * @param periodicX  true = x 方向周期（不加 x 侧 sponge）
     */
    public static void apply(OceanGrid g, OceanState st, OceanParent parent,
                             int band, boolean periodicX) {
        if (parent == null || band <= 0) return;
        APPLY_COUNT++;
        double maxW = 0.0;
        for (int j = 1; j < g.ny - 1; j++) {
            int dy = Math.min(j, g.ny - 1 - j);
            if (dy >= band) continue;
            for (int i = 0; i < g.nx; i++) {
                if (!g.isWet(i, j)) continue;
                int dx = periodicX ? Integer.MAX_VALUE : Math.min(i, g.nx - 1 - i);
                int d = Math.min(dx, dy);
                if (d >= band) continue;
                double w = 1.0 - (double) d / (double) band;
                w = w * w * (3.0 - 2.0 * w);          // smoothstep，C1
                if (w > maxW) maxW = w;
                double x = g.xOf(i), z = g.zOf(j);
                int wl = g.wetLayers[g.idx(i, j)];
                for (int k = 0; k < wl; k++) {
                    int q = st.idx(i, j, k);
                    double pt = parent.temperatureK(x, z, k);
                    if (Double.isFinite(pt)) st.t[q] = (1.0 - w) * st.t[q] + w * pt;
                    double ps = parent.salinityPsu(x, z, k);
                    if (Double.isFinite(ps)) st.s[q] = (1.0 - w) * st.s[q] + w * ps;
                    double pu = parent.uMs(x, z, k);
                    if (Double.isFinite(pu)) st.u[q] = (1.0 - w) * st.u[q] + w * pu;
                    double pv = parent.vMs(x, z, k);
                    if (Double.isFinite(pv)) st.v[q] = (1.0 - w) * st.v[q] + w * pv;
                }
            }
        }
        LAST_MAX_W = maxW;
    }
}
