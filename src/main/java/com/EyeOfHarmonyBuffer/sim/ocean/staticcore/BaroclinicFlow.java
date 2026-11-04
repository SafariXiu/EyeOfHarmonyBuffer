package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

import com.EyeOfHarmonyBuffer.sim.ocean.Thermohaline;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

/** 3D 密度驱动流：由 T/S 热成风求行平均 v，再积分 MOC 流函数 ψ(y,z)。
 *  v = -dψ/dz, w = dψ/dy；质量守恒由 ψ 保证。 */
public final class BaroclinicFlow {
    private BaroclinicFlow() {}
    public static double LAST_MOC = 0.0;
    /**
     * ★ 2026-11-04（A2）：{@link #LAST_MOC} 现在是【最宽盆地】的翻转（cos 加权）。
     * 本字段是"所有盆地同相"的上界（整行湿格相加），仅用于记账/A-B。
     */
    public static double LAST_MOC_ZONAL = 0.0;
    /** ★ A2：盆地无关的量 —— ψ 峰值 [m²/s]，数值上等于 Sv / 1,000 km 宽。 */
    public static double LAST_PSI_MAX = 0.0;
    /** ★ A2：MOC 峰值所在纬度 [deg] 与深度 [m]（诊断用）。 */
    public static double LAST_MOC_LAT_DEG = Double.NaN;
    public static double LAST_MOC_DEPTH_M = Double.NaN;
    public static int APPLY_COUNT = 0;
    public static double MAX_VBC = 0.0;
    public static boolean QOHT_FEEDBACK_ON = false;
    /**
     * ★ 2026-11-03：w 用局地连续积分（底 w=0，w_top = w_bot − dz·divH），替代行平均。
     * 默认关；A/B 用 NLW=1。W_MAX 是数值限幅（CFL 保护），不是物理参数。
     */
    public static boolean LOCAL_W_ON = false;
    /** ★ 2026-11-03：apply 末尾做 C 网格无散投影（默认关，A/B 用 NPROJ=1）。 */
    public static boolean PROJECT_ON = true;
    public static double W_MAX = 1.0e-4;
    public static boolean OHT_ON = true;

    /**
     * ★ 2026-11-04（A2）：深层静止掩码。-1 = 不启用；≥0 时 k ≥ DEEP_KMAX 的 u/v/w 置零，
     * MOC 只统计其上方。
     *
     * <p>物理依据：A′ 把深层当作**固定的水库边界**（T/S 是 Dirichlet），水库内部不参与环流；
     * 之前深层热成风的常数 v 在 4 km 上积分，把 MOC 放到了 5,777 Sv（物理上不成立）。
     */
    public static int DEEP_KMAX = -1;

    /** 最近一次 apply 的 OHT 峰值 [W]（正=向北；取 |F| 的最大值）。 */
    public static double LAST_OHT_PEAK_W = 0.0;
    /** 最近一次 apply 的 Q_OHT 峰值 [W/m2]。 */
    public static double LAST_QOHT_MAX = 0.0;
    /** 最近一次 apply 的【总】OHT 峰值 [W]（含正压风生 v，用参考温度）。 */
    public static double LAST_OHT_FULL_PEAK_W = 0.0;

    /**
     * 【总】经向热输运 OHT_full(y) = rho cp ∫∫ v (T − T_ref) dx dz，正 = 向北。
     * <p>v 用 {@code st.v}（正压风生 + 斜压热成风），不是只取斜压分量。
     * T_ref 取该行湿区的体积平均温度，把净质量输运项 ∫∫v 在数值上减掉
     * （本模型每列 v 的深度积分已为 0，但正压流存在数值残差）。
     * <p>★ 2026-11-02（步骤 3）：与只含 vBaroclinic 的 {@link #ohtProfile} 并列输出，
     * 避免把"斜压分量"当成"总热输运"来判符号。
     */
    public static double[] ohtProfileFull(OceanGrid g, OceanState st) {
        double[] flux = new double[g.ny];
        for (int j = 1; j < g.ny - 1; j++) {
            double sumVT = 0.0, sumV = 0.0, sumT = 0.0, vol = 0.0;
            for (int i = 0; i < g.nx; i++) {
                if (!g.isWet(i, j)) continue;
                for (int k = 0; k < g.nz; k++) {
                    int q = st.idx(i, j, k);
                    // ★ 2026-11-04（A2）：格子的纬向物理宽度 = dx·cosφ（与 MOC 同一处理）。
                    double w = g.dz[k] * g.dx * g.cosLat(j);
                    sumVT += st.v[q] * st.t[q] * w;
                    sumV += st.v[q] * w;
                    sumT += st.t[q] * w;
                    vol += w;
                }
            }
            if (vol <= 0.0) continue;
            double tRef = sumT / vol;
            flux[j] = 1027.8 * 3985.0 * (sumVT - tRef * sumV);
        }
        return flux;
    }

    /**
     * OHT(y) = ∫∫ rho cp v T dx dz  [W]，正 = 向北。
     * <p>★ 2026-11-02：原来只有 qOht 且它**从未被调用**（全仓无读者）⟹ "OHT 只诊断"
     * 这句话在代码里其实不存在。现在把剖面单独抽出来，供探针/诊断直接读。
     */
    public static double[] ohtProfile(OceanGrid g, OceanState st) {
        double[] flux = new double[g.ny];
        for (int j = 1; j < g.ny - 1; j++)
            for (int i = 0; i < g.nx; i++) {
                if (!g.isWet(i, j)) continue;
                for (int k = 0; k < g.nz; k++) {
                    int q = st.idx(i, j, k);
                    // ★ 2026-11-04（A2）：dx -> dx·cosφ（球面纬向长度）。
                    flux[j] += 1027.8 * 3985.0 * st.vBaroclinic[q] * st.t[q] * g.dz[k] * g.dx * g.cosLat(j);
                }
            }
        return flux;
    }

    /**
     * 纬向平均 OHT 辐合 [W/m2]，正 = 海洋增热。
     * <p>★ 2026-11-02 修复两处口径：
     * <ol>
     *   <li>原来除以 {@code nx*dx}（整圈，含陆地）⟹ 低估 {@code wet/nx} 倍；改为湿格宽度；</li>
     *   <li>原来对陆地格也填值（qOht 是格点场）⟹ 陆地上出现"海洋增热"；现在只填湿格。</li>
     * </ol>
     */
    public static double[] qOht(OceanGrid g, OceanState st) {
        double[] flux = ohtProfile(g, st);
        double[] qo = new double[g.nx * g.ny];
        for (int j = 1; j < g.ny - 1; j++) {
            int wet = 0;
            for (int i = 0; i < g.nx; i++) if (g.isWet(i, j)) wet++;
            if (wet == 0) continue;
            double div = -(flux[j + 1] - flux[j - 1]) / (2.0 * g.dy);
            double q = div / (wet * g.dx * g.cosLat(j));
            for (int i = 0; i < g.nx; i++) if (g.isWet(i, j)) qo[j * g.nx + i] = q;
        }
        return qo;
    }

    /** 刷新 OHT 峰值诊断；在 {@link #apply} 末尾调用（O(nx*ny*nz)，相对求解可忽略）。 */
    private static void updateOhtDiagnostics(OceanGrid g, OceanState st) {
        double[] flux = ohtProfile(g, st);
        double pk = 0.0;
        for (double f : flux) pk = Math.max(pk, Math.abs(f));
        LAST_OHT_PEAK_W = pk;
        double[] full = ohtProfileFull(g, st);
        double pkF = 0.0;
        for (double f2 : full) pkF = Math.max(pkF, Math.abs(f2));
        LAST_OHT_FULL_PEAK_W = pkF;
        double[] q = qOht(g, st);
        double qm = 0.0;
        for (double v : q) qm = Math.max(qm, Math.abs(v));
        LAST_QOHT_MAX = qm;
    }

    /**
     * 纬向梯度（K/m 或 psu/m）：中心差分；岸格改为单侧差分；陆地格不参与。
     * <p>★ 2026-11-02：这是"陆地 T=0 悬崖"的修复点。只要两侧都是湿格，结果与原式逐位相同。
     * <p>★ 2026-11-04（A2）：湿判据由【列】改为【层】（{@code isWet(i,j,k)}）。
     * 原式只看 2D 湿判，会把"该层已在该列海底以下"的邻格当成有效邻点，读到 0
     * （未初始化）或海底拷贝值 ⟹ 假的 ΔT。与 {@code advZ} 的干格修复是同一类错误，
     * 这里是它的第二处。
     * <p><b>实测（NMOC3D 的 ideal 模式：球面 144×72×25 + 地形，NCIRC 的理想 T/S）</b>：
     * 这类边有 7,689 条，最大假 ΔT = <b>297.8 K</b>；修复前 MOC 2,929.6 Sv /
     * max|v_bc| 0.289 m/s，修复后 <b>MOC 190.1 Sv / 0.0194 m/s</b>（15 倍）。
     * <p>BATHY_ON=false 时 wetLayers≡nz ⟹ 3D 判据与 2D 判据逐位等价，默认路径不变。
     */
    private static double zonalGrad(OceanGrid g, OceanState st, double[] v, int i, int j, int k) {
        boolean west = g.isWet(i - 1, j, k), east = g.isWet(i + 1, j, k);
        double c = v[st.idx(i, j, k)];
        if (west && east) return (v[st.idx(i + 1, j, k)] - v[st.idx(i - 1, j, k)]) / (2.0 * g.dx);
        if (east) return (v[st.idx(i + 1, j, k)] - c) / g.dx;
        if (west) return (c - v[st.idx(i - 1, j, k)]) / g.dx;
        return 0.0;
    }

    /** 行 j 上最宽【连续湿段】（x 周期）的格数；全湿时 = nx。 */
    private static int maxWetRun(OceanGrid g, int j) {
        int nx = g.nx, best = 0, cur = 0;
        for (int i = 0; i < nx; i++) {
            if (g.isWet(i, j)) { cur++; if (cur > best) best = cur; } else cur = 0;
        }
        if (g.isWet(0, j) && g.isWet(nx - 1, j)) {
            int a = 0;
            while (a < nx && g.isWet(a, j)) a++;
            int b = nx - 1;
            while (b >= 0 && g.isWet(b, j)) b--;
            int w = a + (nx - 1 - b);
            if (w > best) best = w;
        }
        return best;
    }

    public static double apply(OceanGrid g, OceanState st) {
        APPLY_COUNT++;
        MAX_VBC = 0.0;   // ★ 2026-11-02：原来从不清零，诊断读到的是"历史最大值"。
        if (!OHT_ON) {
            // ★ 2026-11-02：原来只清 vBaroclinic，却把已经加进 st.v 的斜压分量**留在场里**
            // ⟹ 同一 JVM 里先开再关，"OHT off" 的 A/B 仍然带着旧斜压流。这里先把 st.v 还原
            // 成正压部分，再清斜压场与 w，开关才是幂等的。
            for (int q = 0; q < st.v.length; q++) st.v[q] -= st.vBaroclinic[q];
            java.util.Arrays.fill(st.vBaroclinic, 0.0);
            java.util.Arrays.fill(st.w, 0.0);
            LAST_MOC = 0.0; LAST_MOC_ZONAL = 0.0; LAST_PSI_MAX = 0.0;
            LAST_MOC_LAT_DEG = Double.NaN; LAST_MOC_DEPTH_M = Double.NaN;
            LAST_OHT_PEAK_W = 0.0; LAST_QOHT_MAX = 0.0;
            return 0.0;
        }
        int nx = g.nx, ny = g.ny, nz = g.nz;
        double[] baro = new double[st.v.length];
        for (int q = 0; q < baro.length; q++) baro[q] = st.v[q] - st.vBaroclinic[q];
        double[][] psi = new double[ny][nz + 1];
        double[] vbar = new double[nz];
        for (int j = 1; j < ny - 1; j++) {
            double lat = g.latAt(j);
            // ★ 2026-11-02：原来的硬门 30° 会在那一行把 v 从满值直接切到 0，而 ψ 的 30°
            // 两侧一个为 0、一个有值 ⟹ w=Δψ/2dy 在门口得到"半个梯度"（不连续）。
            // 现在 25°→30° 线性 taper：ψ 连续地趋于 0，w 也不再跳变。25° 以内仍不做
            // 热成风（那里 f→0，地转平衡本身不成立），这一点是**明写的模型边界**。
            double alatDeg = Math.abs(Math.toDegrees(lat));
            if (alatDeg < 25.0) continue;
            double gate = Math.min(1.0, (alatDeg - 25.0) / 5.0);
            double f = 2.0 * WorldContract.OMEGA * Math.sin(lat);
            // 只为防除零：25° 处 |f| = 5.9e-5，远大于 1e-5 ⟹ 门内不再有任何钳位失真。
            // （旧值 1.0e-4 比 30° 的 7.3e-5 还大，等于把 30°~43° 的热成风额外压掉 27%~40%。）
            if (Math.abs(f) < 1.0e-5) f = Math.signum(f) * 1.0e-5;
            java.util.Arrays.fill(vbar, 0.0);
            double[] vbarInt = new double[nz];
            int cnt = 0, cntInt = 0;
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int wl = g.wetLayers[g.idx(i, j)];
                if (wl <= 0) continue;
                double[] vbc = new double[nz];
                // ★ 2026-11-04（A2）：热成风只在【本列的湿层】内递推；海底（wl-1）v=0，
                //   干层不再参与（旧式从 nz-2 往下，干层 T=0 的虚假梯度会进入 vbc）。
                for (int k = wl - 2; k >= 0; k--) {
                    // 陆地不参与差分；岸格用单侧差分；两侧都是陆地则梯度为 0。
                    double dTdx = zonalGrad(g, st, st.t, i, j, k);
                    double dSdx = zonalGrad(g, st, st.s, i, j, k);
                    double drhoDx = 1027.8 * (-Thermohaline.ALPHA * dTdx + Thermohaline.BETA * dSdx);
                    double dvdz = (9.81 / (1027.8 * f)) * drhoDx;
                    // ★ 2026-11-02 实测否决：Richardson 切变限制（|dv/dz|≤2N, N²≤0→0）在
                    // prognostic S 上把 MOC 从 2093 压到 457 Sv，但在稳态路径上 T 冲到 8475 K、
                    // MOC 24756 Sv、h 塌到 93 m。它与热成风/输运的稳态闭合不兼容，已回退。
                    vbc[k] = vbc[k + 1] - dvdz * g.dz[k];
                }
                for (int k = 0; k < nz; k++) vbc[k] *= gate;
                // ★ A2：深度平均只对本列的湿层（旧式用全 nz=4,525 m）——浅柱（如 930 m）
                //   的“零深度积分”必须用 930 m 作分母，否则大 vbc 保留 ⟹ MOC 4,396 Sv。
                double mean = 0.0, hsum = 0.0;
                for (int k = 0; k < wl; k++) { mean += vbc[k] * g.dz[k]; hsum += g.dz[k]; }
                mean /= Math.max(1.0, hsum);
                // ★ 2026-11-02：热成风是【内区】地转平衡；海岸格子的单侧密度梯度是边界层
                // 前沿，不是盆地尺度 MOC。实测（seed 0）：-67.5 行 MOC_all=1642 Sv vs
                // MOC_int=373 Sv；海岸 14/121 格却贡献 4.4 倍；maxVbc 达 4.3 m/s。
                // ⟹ 纬向平均只取两侧都湿的内区格；整行无内区时退回全部湿格。
                boolean interior = g.isWet(i - 1, j) && g.isWet(i + 1, j);
                for (int k = 0; k < wl; k++) {
                    double dv = vbc[k] - mean;
                    vbar[k] += dv;
                    if (interior) vbarInt[k] += dv;
                }
                cnt++;
                if (interior) cntInt++;
            }
            if (cnt == 0) continue;
            if (cntInt > 0) {
                for (int k = 0; k < nz; k++) vbar[k] = vbarInt[k] / cntInt;
            } else {
                for (int k = 0; k < nz; k++) vbar[k] /= cnt;
            }
            double[] p = psi[j];
            p[nz] = 0.0;
            for (int k = nz - 1; k >= 0; k--) p[k] = p[k + 1] + vbar[k] * g.dz[k];
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                for (int k = 0; k < nz; k++) {
                    int q = st.idx(i, j, k);
                    if (DEEP_KMAX >= 0 && k >= DEEP_KMAX) {
                        st.vBaroclinic[q] = 0.0;
                        st.v[q] = 0.0;          // 深层静止（水库不参与环流）
                        continue;
                    }
                    double v = -(p[k] - p[k + 1]) / g.dz[k];
                    st.vBaroclinic[q] = v;
                    MAX_VBC = Math.max(MAX_VBC, Math.abs(v));
                    st.v[q] = baro[q] + v;
                }
            }
        }
        // w = dψ/dy（行平均）；LOCAL_W_ON 时改用局地连续积分。
        for (int j = 1; j < ny - 1; j++) {
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                if (LOCAL_W_ON) {
                    double wAcc = 0.0;   // 底面 w=0，向上积分
                    for (int k = nz - 1; k >= 0; k--) {
                        int q = st.idx(i, j, k);
                        double uE = g.isWet(i + 1, j) ? st.u[st.idx(i + 1, j, k)] : st.u[q];
                        double uW = g.isWet(i - 1, j) ? st.u[st.idx(i - 1, j, k)] : st.u[q];
                        double vN = g.isWet(i, j + 1) ? st.v[st.idx(i, j + 1, k)] : st.v[q];
                        double vS = g.isWet(i, j - 1) ? st.v[st.idx(i, j - 1, k)] : st.v[q];
                        double divH = (uE - uW) / (2.0 * g.dx) + (vN - vS) / (2.0 * g.dy);
                        double wBot = wAcc;
                        double wTop = wBot - g.dz[k] * divH;
                        double wc = 0.5 * (wBot + wTop);
                        if (wc > W_MAX) wc = W_MAX;
                        if (wc < -W_MAX) wc = -W_MAX;
                        st.w[q] = (DEEP_KMAX >= 0 && k >= DEEP_KMAX) ? 0.0 : wc;
                        wAcc = wTop;
                    }
                } else {
                    for (int k = 0; k < nz; k++) {
                        double w = (psi[j + 1][k] - psi[j - 1][k]) / (2.0 * g.dy);
                        st.w[st.idx(i, j, k)] = (DEEP_KMAX >= 0 && k >= DEEP_KMAX) ? 0.0 : w;
                    }
                }
            }
        }
        if (PROJECT_ON) {
            VelocityProjection.project(g, st, 3000);
        } else {
            VelocityProjection.FACES_READY = false;
        }
        // ★ 2026-11-02 修复（量级错 100 倍）：vbar 是【纬向平均】v，所以 ψ 的单位是 m²/s；
        // 跨一个纬圈的体积输运 = ψ × 【湿段总宽度】，不是 ψ × 单个格宽 dx。
        // 原式乘 g.dx（555 km）而漏掉了 cnt（~100 个湿格）⟹ 报告的 MOC 小约 100 倍。
        // 例：旧值 0.45 "Sv" 实际约 45~65 Sv（取决于该行湿格数）。
        //
        // ★ 2026-11-04（A2）：口径重定，两处：
        //   (1) **乘 cos(lat)**。球面上一行的每格物理宽度是 dx·cosφ，不是赤道值 dx。
        //       漏掉它的后果：MOC 最大值跑到高纬（φ=82.5° 时虚高 1/cos = 7.7 倍），
        //       而高纬恰好是 MOC 最该小的地方（NOHT3D bathy：ψ_max 出现在 +82.5°）。
        //   (2) **按盆地**。v 是行均匀的，把整行湿格相加等价于"所有盆地同相"，
        //       只是上界；单个盆地的翻转 = ψ × 该行【最宽连续湿段】的宽度。
        //       LAST_MOC 取盆地值；同相上界保留为 LAST_MOC_ZONAL 供记账。
        //   (3) 盆地无关的量是 ψ 本身（m²/s = Sv / 1,000 km），保留为 LAST_PSI_MAX。
        double mocZonal = 0.0, mocBasin = 0.0, psiPeak = 0.0;
        int bj = -1, bk = -1;
        int kCap = (DEEP_KMAX >= 0) ? Math.min(nz, DEEP_KMAX) : nz;
        for (int j = 1; j < ny - 1; j++) {
            int wet = 0;
            for (int i = 0; i < nx; i++) if (g.isWet(i, j)) wet++;
            if (wet == 0) continue;
            double wCell = g.dx * g.cosLat(j);
            int seg = maxWetRun(g, j);
            for (int k = 0; k <= kCap; k++) {
                double p = Math.abs(psi[j][k]);
                mocZonal = Math.max(mocZonal, p * wet * wCell);
                double b = p * seg * wCell;
                if (b > mocBasin) { mocBasin = b; bj = j; bk = k; psiPeak = p; }
            }
        }
        LAST_MOC_ZONAL = mocZonal / 1.0e6;
        LAST_MOC = mocBasin / 1.0e6;
        LAST_PSI_MAX = psiPeak;
        LAST_MOC_LAT_DEG = (bj >= 0) ? Math.toDegrees(g.latAt(bj)) : Double.NaN;
        LAST_MOC_DEPTH_M = (bk < 0) ? Double.NaN : (bk >= nz ? 0.0 : g.zCenter[bk]);
        updateOhtDiagnostics(g, st);
        return LAST_MOC;
    }
}
