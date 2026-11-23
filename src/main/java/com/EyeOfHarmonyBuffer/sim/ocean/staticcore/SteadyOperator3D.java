package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

import com.EyeOfHarmonyBuffer.sim.ocean.SeaSurfaceForcing;

/**
 * ★ 2026-11-23（项目 B / D1）：**3D 定常算子**（矩阵-free）。
 *
 * <h3>它是什么</h3>
 * 把 PrognosticT 的倾向 `dT/dt = −adv + lapH + lapZ (+src+redi)` 在给定状态处**线性化**：
 * 对任意扰动 δ（只取 T），`L δ` 就是该倾向的一阶变化。于是
 * <pre>
 *   M(x+δ) ≈ x + δ + dt·L δ        ⟹        J − I ≈ dt·L
 * </pre>
 * Newton 预条件要的 `(I−J)⁻¹ ≈ −(dt·L)⁻¹` 就以此为骨架。
 *
 * <h3>离散：与 PrognosticT **逐字同构**，不是"相似"</h3>
 * <ul>
 *   <li>平流：直接用 {@link AdvectionDiffusionSolver#divFlux}（迎风、面速度=本格速度），
 *       并保留 {@code ADVECTIVE_FORM} 的 `−δ·divU` 修正；</li>
 *   <li>水平扩散：`khAt(k)·((δw+δe−2δ)/dx² + (δs+δn−2δ)/dy²)`；</li>
 *   <li>垂直扩散：`kvAt/(dz²)·(δup+δdn−2δ)`，海底零梯度。</li>
 * </ul>
 *
 * <h3>边界（与 map 一致）</h3>
 * <ul>
 *   <li><b>表层 k=0</b>：map 每步把 T 设成由表面能量平衡解出的值 ⟹ 对 δ 是**强约束**，
 *       本算子把它记为 `Lδ = −δ`（"一步内被重置"的快模态行为）；需要时调用方可跳过该行；</li>
 *   <li>海底：零梯度（`δdn = δ`）；</li>
 *   <li>侧边界：与 `divFlux` 的干格处理一致（干格不参与）。</li>
 * </ul>
 *
 * <h3>接线状态</h3>
 * ★ **未接线**：生产路径不调用，默认逐位不变，因此**不进 configStamp**。
 *
 * <h3>验收（DE 报告，尚未通过）</h3>
 * ① cos(dt·Lδ, 真实 dR) > 0.5（三个模态）；② 单次 apply < 0.2 s；③ 0–200 m 带 Newton ≤3 步 D<1e−2。
 */
public final class SteadyOperator3D {
    private SteadyOperator3D() {}

    /**
     * L δ 在格点 (i,j,k) 的值 [K/s]。
     * k=0 返回 `−δ`（表层强约束的线性化）。
     */
    public static double applyAt(OceanGrid g, OceanState st, double[] d, int i, int j, int k) {
        int q = st.idx(i, j, k);
        if (k == 0) return -d[q];
        // ★ D2'（2026-11-23）：表层是【诊断量】—— SurfaceTemperatureSolver 直接由 bulk 净热通量=0 求 SST，
        //   与海洋顶层 T 无关 ⟹ 对任何 T 扰动，δ(surface) = 0。算子必须按这个边界条件取顶层值，
        //   否则 k=1 的顶面会把一个"根本不存在的表面扰动"当成真的（实测 band0 cos 0.299 → 见 DM）。
        int qs = st.idx(i, j, 0);
        double saved = d[qs];
        d[qs] = 0.0;
        double adv = AdvectionDiffusionSolver.divFlux(g, st, d, i, j, k);
        if (AdvectionDiffusionSolver.ADVECTIVE_FORM) {
            adv -= d[q] * AdvectionDiffusionSolver.divU(g, st, i, j, k);
        }
        int wl = g.wetLayers[g.idx(i, j)];
        double dz = g.dz[k];
        double dw = g.isWet(i - 1, j, k) ? d[st.idx(i - 1, j, k)] : d[q];
        double de = g.isWet(i + 1, j, k) ? d[st.idx(i + 1, j, k)] : d[q];
        double ds = g.isWet(i, j - 1, k) ? d[st.idx(i, j - 1, k)] : d[q];
        double dn = g.isWet(i, j + 1, k) ? d[st.idx(i, j + 1, k)] : d[q];
        double dup = (k == 1) ? 0.0 : d[st.idx(i, j, k - 1)];   // 表层诊断 ⟹ δ=0
        double ddn = (k + 1 < wl) ? d[st.idx(i, j, k + 1)] : d[q];
        double lapH = AdvectionDiffusionSolver.khAt(g, k)
                * ((dw + de - 2.0 * d[q]) / (g.dx * g.dx) + (ds + dn - 2.0 * d[q]) / (g.dy * g.dy));
        double lapZ = AdvectionDiffusionSolver.kvAt(g, st, i, j, k) / (dz * dz) * (dup + ddn - 2.0 * d[q]);
        d[qs] = saved;
        double out = -adv + lapH + lapZ;
        if (ML_TERM_ON && inML(g, st, i, j, k)) {
            out += (mlMean(g, st, d, i, j) - d[q]) / YEAR_S;
        }
        return out;
    }

    /** 整场 apply：out = L·delta（n3 长度，T 分量）。 */
    public static void apply(OceanGrid g, OceanState st, double[] delta, double[] out) {
        int nx = g.nx, ny = g.ny, nz = g.nz;
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int wl = Math.min(nz, g.wetLayers[g.idx(i, j)]);
                for (int k = 0; k < wl; k++) {
                    int q = st.idx(i, j, k);
                    out[q] = applyAt(g, st, delta, i, j, k);
                }
            }
    }

    /**
     * L 的对角元（用于红黑 Gauss-Seidel 平滑）。与 {@link #applyAt} 逐项对应：
     * <pre>
     *   diag = −[流出面速度/Δ 之和]
     *        + kh·((−2+dryW+dryE)/dx² + (−2+dryS+dryN)/dy²)
     *        + kv/dz²·(−2 + [海底])
     * </pre>
     * dryX = 1 表示该侧是干格（零梯度 ⟹ 邻格值等于本格值，对角多 +1）。
     */
    public static double diagAt(OceanGrid g, OceanState st, int i, int j, int k) {
        if (k == 0) return -1.0;                 // 表层强约束：Lδ = −δ
        int q = st.idx(i, j, k);
        double diag = 0.0;
        double dz = g.dz[k];
        if (VelocityProjection.FACES_READY) {
            double uE = st.uF[q], uW = st.uF[st.idx(i - 1, j, k)];
            double vN = st.vF[q], vS = st.vF[st.idx(i, j - 1, k)];
            double wT = st.wF[st.idx(i, j, k - 1)];
            double wB = st.wF[q];
            diag -= (Math.max(uE, 0.0) + Math.max(-uW, 0.0)) / g.dx;
            diag -= (Math.max(vN, 0.0) + Math.max(-vS, 0.0)) / g.dy;
            diag -= (Math.max(wT, 0.0) + Math.max(-wB, 0.0)) / dz;
        } else {
            double u = st.u[q], v = st.v[q];
            double uW = st.u[st.idx(i - 1, j, k)];
            double vS = st.v[st.idx(i, j - 1, k)];
            diag -= (Math.max(u, 0.0) + Math.max(-uW, 0.0)) / g.dx;
            diag -= (Math.max(v, 0.0) + Math.max(-vS, 0.0)) / g.dy;
            if (k > 0) diag -= Math.max(st.w[st.idx(i, j, k - 1)], 0.0) / dz;
            if (k + 1 < g.nz) diag -= Math.max(-st.w[st.idx(i, j, k + 1)], 0.0) / dz;
        }
        int dryW = g.isWet(i - 1, j, k) ? 0 : 1;
        int dryE = g.isWet(i + 1, j, k) ? 0 : 1;
        int dryS = g.isWet(i, j - 1, k) ? 0 : 1;
        int dryN = g.isWet(i, j + 1, k) ? 0 : 1;
        diag += AdvectionDiffusionSolver.khAt(g, k)
                * ((-2.0 + dryW + dryE) / (g.dx * g.dx) + (-2.0 + dryS + dryN) / (g.dy * g.dy));
        int wl = g.wetLayers[g.idx(i, j)];
        int bottom = (k + 1 < wl) ? 0 : 1;
        diag += AdvectionDiffusionSolver.kvAt(g, st, i, j, k) / (dz * dz) * (-2.0 + bottom);
        return diag;
    }

    /**
     * 红黑 Gauss-Seidel 平滑：解 `L·x = rhs` 的若干轮（近似逆的内层）。
     * 每次更新用一次 {@link #applyAt}（矩阵-free），收敛判据由调用方看残差。
     * @return 最后一轮的相对残差 ‖rhs − Lx‖∞ / ‖rhs‖∞
     */
    public static double solveRBGS(OceanGrid g, OceanState st, double[] rhs, double[] x, int sweeps) {
        return solveRBGS(g, st, rhs, x, sweeps, 1.0);
    }

    /** 同上，带松弛因子 ω（ω<1 阻尼；本算子非对角占优，ω=1 在 10 轮后会发散）。 */
    public static double solveRBGS(OceanGrid g, OceanState st, double[] rhs, double[] x, int sweeps, double omega) {
        int nx = g.nx, ny = g.ny, nz = g.nz;
        double norm = 0.0;
        for (int i = 0; i < rhs.length; i++) norm = Math.max(norm, Math.abs(rhs[i]));
        if (!(norm > 0)) return 0.0;
        for (int s = 0; s < sweeps; s++) {
            for (int color = 0; color < 2; color++) {
                for (int j = 1; j < ny - 1; j++)
                    for (int i = 0; i < nx; i++) {
                        if (!g.isWet(i, j)) continue;
                        int wl = Math.min(nz, g.wetLayers[g.idx(i, j)]);
                        for (int k = 1; k < wl; k++) {
                            if (((i + j + k) & 1) != color) continue;
                            int q = st.idx(i, j, k);
                            double dg = diagAt(g, st, i, j, k);
                            if (!(Math.abs(dg) > 1e-30)) continue;
                            double lx = applyAt(g, st, x, i, j, k);
                            x[q] += omega * (rhs[q] - lx) / dg;
                        }
                    }
            }
        }
        return residualInf(g, st, rhs, x) / norm;
    }

    /** ‖rhs − Lx‖∞（跳过表层行）。 */
    public static double residualInf(OceanGrid g, OceanState st, double[] rhs, double[] x) {
        int nx = g.nx, ny = g.ny, nz = g.nz;
        double m = 0.0;
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int wl = Math.min(nz, g.wetLayers[g.idx(i, j)]);
                for (int k = 1; k < wl; k++) {
                    int q = st.idx(i, j, k);
                    m = Math.max(m, Math.abs(rhs[q] - applyAt(g, st, x, i, j, k)));
                }
            }
        return m;
    }

    /**
     * ★ D2b v1：**季节平均算子** `L̄ = (1/N)·Σ_s L(state_s)`。
     *
     * <p>动机（DJ 报告）：无冰上层的年响应由**季节循环**（表面通量 + 混合层卷夹）控制，
     * 只用年均状态建的定常算子表达不了。这里用同一年内各季节末的状态（`MAP_SEASON_CAPTURE`）
     * 各建一次算子再平均 —— **不新建常数**，只是把已有系数在季节上平均。
     */
    public static void applySeasonal(OceanGrid g, OceanState[] states, double[] delta, double[] out) {
        int n = Math.max(1, states.length);
        java.util.Arrays.fill(out, 0.0);
        double[] tmp = new double[out.length];
        for (OceanState s : states) {
            java.util.Arrays.fill(tmp, 0.0);
            apply(g, s, delta, tmp);
            for (int i = 0; i < out.length; i++) out[i] += tmp[i] / n;
        }
    }

    /** ★ D2-prime 2.2（默认关）：混合层重写的投影项（幂等投影的年化率）。private ⟹ 不进 A7 开关表。 */
    private static boolean ML_TERM_ON = true;

    /** 诊断探针专用入口（生产路径不调用）。 */
    public static void setMLTerm(boolean on) { ML_TERM_ON = on; }
    private static final double YEAR_S = 365.25 * 86400.0;

    /** 0..h 的厚度加权平均（h = 状态里的 mixed layer depth）。 */
    public static double mlMean(OceanGrid g, OceanState st, double[] d, int i, int j) {
        int wl = Math.min(g.nz, g.wetLayers[g.idx(i, j)]);
        double h = st.mixedLayerDepth[j * g.nx + i];
        double sum = 0, wsum = 0, dep = 0;
        for (int k = 0; k < wl; k++) {
            double w = Math.min(g.dz[k], h - dep);
            if (!(w > 0)) break;
            sum += d[st.idx(i, j, k)] * w;
            wsum += w;
            dep += w;
        }
        return wsum > 0 ? sum / wsum : d[st.idx(i, j, Math.min(1, wl - 1))];
    }

    /** 该格是否在混合层内（k < h）。 */
    public static boolean inML(OceanGrid g, OceanState st, int i, int j, int k) {
        double zTop = 0;
        for (int kk = 0; kk < k; kk++) zTop += g.dz[kk];
        return zTop < st.mixedLayerDepth[j * g.nx + i];
    }

    /** 诊断转发（kvAt 是包内可见；探针在包 probe 里）。只读，不改任何状态。 */
    public static double kvAtDiag(OceanGrid g, OceanState st, int i, int j, int k) {
        return AdvectionDiffusionSolver.kvAt(g, st, i, j, k);
    }

    /** 深层带的下界 [m]：与报告/探针里 1000–1800 m 的定义一致（几何分带，不是物理常数）。 */
    public static final double DEEP_Z = 1000.0;

    /** ★ 2026-11-23（D1 验收③）：深层平衡求解开关。默认开（世界生成时解一次）。 */
    public static boolean DEEP_EQ_ON = true;

    /** 在 packed 状态上给深层（z ≥ DEEP_Z）均匀加 dT [K]。 */
    public static double[] withDeepOffset(OceanGrid g, double[] packed, double dT) {
        double[] y = packed.clone();
        for (int j = 1; j < g.ny - 1; j++)
            for (int i = 0; i < g.nx; i++) {
                if (!g.isWet(i, j)) continue;
                int wl = Math.min(g.nz, g.wetLayers[g.idx(i, j)]);
                for (int k = 0; k < wl; k++) {
                    if (g.zCenter[k] < DEEP_Z) continue;
                    y[(k * g.ny + j) * g.nx + i] += dT;
                }
            }
        return y;
    }

    /** 深层平均漂移 [K/yr]：只用 M(x) − x，面积×厚度加权。 */
    public static double deepMeanDrift(OceanGrid g, double[] packed, int worldSeed, SeaSurfaceForcing f) {
        double[] mx = AnnualMap.apply(packed, g, worldSeed, f);
        double s = 0, w = 0;
        for (int j = 1; j < g.ny - 1; j++) {
            double wj = Math.max(0.05, g.cosLat(j));
            for (int i = 0; i < g.nx; i++) {
                if (!g.isWet(i, j)) continue;
                int wl = Math.min(g.nz, g.wetLayers[g.idx(i, j)]);
                for (int k = 0; k < wl; k++) {
                    if (g.zCenter[k] < DEEP_Z) continue;
                    int q = (k * g.ny + j) * g.nx + i;
                    s += wj * g.dz[k] * (mx[q] - packed[q]);
                    w += wj * g.dz[k];
                }
            }
        }
        return s / Math.max(1e-30, w);
    }

    /**
     * 深层平衡：五点粗扫 + 二次求根，返回均匀偏移 S* [K]。
     * 只解「模型自己的深层平均漂移 = 0」，不新建常数、不调参。
     */
    public static double solveDeepEquilibrium(OceanGrid g, double[] packed, int worldSeed, SeaSurfaceForcing f) {
        double[] S = {0.0, 0.25, 0.5, 0.75, 1.0, 1.25, 1.5};
        double[] d = new double[S.length];
        for (int i = 0; i < S.length; i++) d[i] = deepMeanDrift(g, withDeepOffset(g, packed, S[i]), worldSeed, f);
        int b = -1;
        for (int i = 0; i + 1 < S.length; i++) if (d[i] > 0.0 && d[i + 1] <= 0.0) { b = i; break; }
        if (b < 0) return d[0] > 0.0 ? S[S.length - 1] : 0.0;
        double s0 = S[b], s1 = S[b + 1];
        double d0 = d[b], d1 = d[b + 1];
        double s2 = (b + 2 < S.length) ? S[b + 2] : s1 + (s1 - s0);
        double d2 = (b + 2 < S.length) ? d[b + 2] : d1;
        // 三点二次插值（Lagrange），取 [s0,s2] 内的根；退化时退回线性。
        double den = (s0 - s1) * (s0 - s2) * (s1 - s2);
        if (Math.abs(den) < 1e-30 || d2 == d1) {
            return s0 - d0 * (s1 - s0) / Math.max(1e-300, (d1 - d0));
        }
        double a = d0 * (s1 - s2) / den - d1 * (s0 - s2) / den + d2 * (s0 - s1) / den;
        double bb = -d0 * (s1 * s1 - s2 * s2) / den + d1 * (s0 * s0 - s2 * s2) / den - d2 * (s0 * s0 - s1 * s1) / den;
        double c = d0 * (s1 * s1 * s2 - s2 * s2 * s1) / den - d1 * (s0 * s0 * s2 - s2 * s2 * s0) / den + d2 * (s0 * s0 * s1 - s1 * s1 * s0) / den;
        if (Math.abs(a) < 1e-30) return -c / Math.max(1e-300, bb);
        double disc = bb * bb - 4.0 * a * c;
        if (disc < 0.0) return s1;
        double r1 = (-bb + Math.sqrt(disc)) / (2.0 * a);
        double r2 = (-bb - Math.sqrt(disc)) / (2.0 * a);
        double lo = Math.min(s0, s2), hi = Math.max(s0, s2);
        boolean i1 = r1 >= lo - 0.25 && r1 <= hi + 0.25;
        boolean i2 = r2 >= lo - 0.25 && r2 <= hi + 0.25;
        if (i1 && !i2) return r1;
        if (i2 && !i1) return r2;
        return (i1 ? r1 : (i2 ? r2 : s1));
    }
}