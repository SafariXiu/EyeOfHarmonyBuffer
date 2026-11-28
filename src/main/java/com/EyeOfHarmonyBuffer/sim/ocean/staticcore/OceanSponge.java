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

    /** True = x boundaries are also sponged (infinite-world patch); default false = legacy periodic-x. */
    public static boolean OPEN_X = true;

    /** 已应用的次数（诊断用）。 */
    public static long APPLY_COUNT = 0;
    /** 最近一次的最大松弛权重（诊断用；应约为 1）。 */
    public static double LAST_MAX_W = 0.0;

    // ── S0.4 诊断（2026-11-24）───────────────────────────────────────────────
    /** 本方法调用里：父解在该格点有值 / 需要外推 / 外推也找不到 的 (i,j,k) 计数。 */
    public static long LAST_BC_DIRECT = 0, LAST_BC_EXTRAP = 0, LAST_BC_MISSING = 0;
    /** 外推搜索半径（格）。只影响"父解陆地"的边界格，不影响有父解值的地方。 */
    public static final int EXTRAP_R = 4;

    /**
     * 找 (i,j) 附近第一个有有限父值的格点（S0.4）。
     *
     * <p>为什么需要：父解在**自己的陆地**上返回 NaN，而父/子海陆并不一致 ⟹
     * 实测一条边界上 550 个 (j,k) 采样里 350/200 是 NaN。原先"NaN 就跳过"，
     * 等于**多数边界格没有边界条件**（漏海绵）。这里按环向外推找一个**海洋**父值。
     */
    private static double[] donorXZ(OceanGrid g, OceanParent parent, int i, int j, int k) {
        for (int r = 1; r <= EXTRAP_R; r++) {
            for (int s = -1; s <= 1; s += 2) {
                int ii = i + s * r;
                if (ii >= 0 && ii < g.nx) {
                    double x = g.xOf(ii), z = g.zOf(j);
                    if (Double.isFinite(parent.temperatureK(x, z, k))) return new double[]{x, z};
                }
            }
            for (int s = -1; s <= 1; s += 2) {
                int jj = j + s * r;
                if (jj >= 0 && jj < g.ny) {
                    double x = g.xOf(i), z = g.zOf(jj);
                    if (Double.isFinite(parent.temperatureK(x, z, k))) return new double[]{x, z};
                }
            }
        }
        return null;
    }

    /**
     * @param band       sponge 带宽度（格点数），必须 > 0
     * @param periodicX  true = x 方向周期（不加 x 侧 sponge）
     */
    public static void apply(OceanGrid g, OceanState st, OceanParent parent,
                             int band, boolean periodicX) {
        if (parent == null || band <= 0) return;
        APPLY_COUNT++;
        LAST_BC_DIRECT = 0; LAST_BC_EXTRAP = 0; LAST_BC_MISSING = 0;
        double maxW = 0.0;
        for (int j = 0; j < g.ny; j++) {
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
                    double px = x, pz = z;
                    double pt = parent.temperatureK(px, pz, k);
                    if (Double.isFinite(pt)) {
                        LAST_BC_DIRECT++;
                    } else {
                        // S0.4：父解陆地（NaN）→ 环向外推一个"海洋父值"，而不是静默跳过。
                        double[] donor = donorXZ(g, parent, i, j, k);
                        if (donor == null) { LAST_BC_MISSING++; continue; }
                        px = donor[0]; pz = donor[1];
                        pt = parent.temperatureK(px, pz, k);
                        if (!Double.isFinite(pt)) { LAST_BC_MISSING++; continue; }
                        LAST_BC_EXTRAP++;
                    }
                    st.t[q] = (1.0 - w) * st.t[q] + w * pt;
                    double ps = parent.salinityPsu(px, pz, k);
                    if (Double.isFinite(ps)) st.s[q] = (1.0 - w) * st.s[q] + w * ps;
                    double pu = parent.uMs(px, pz, k);
                    if (Double.isFinite(pu)) st.u[q] = (1.0 - w) * st.u[q] + w * pu;
                    double pv = parent.vMs(px, pz, k);
                    if (Double.isFinite(pv)) st.v[q] = (1.0 - w) * st.v[q] + w * pv;
                }
            }
        }
        LAST_MAX_W = maxW;
    }
}
