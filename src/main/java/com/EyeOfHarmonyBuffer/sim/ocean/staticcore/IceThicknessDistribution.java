package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

import com.EyeOfHarmonyBuffer.sim.ocean.SeaSurfaceForcing;

/**
 * Standard 5-category ice thickness distribution (ITD) skeleton.
 *
 * <p>This is the Phase 2c structure: area/volume/snow per thickness category, instead
 * of forcing frazil into one bulk ice state. It is deliberately not wired into the
 * production path yet. The category bounds are a numerical discretization (CICE-like
 * 0.1/0.3/0.7/1.5 m); they are not physical constants and can be revisited.
 *
 * <p>Units: area is grid-cell fraction; volume is grid-cell mean ice volume (m);
 * snow is grid-cell mean snow volume (m snow). Ice thickness in a category is
 * h = volume / max(area, eps).
 */
public final class IceThicknessDistribution {

    public static final int NCAT = 5;
    public static final double[] BOUNDS = {0.0, 0.1, 0.3, 0.7, 1.5, Double.POSITIVE_INFINITY};
    public static final double EPS = 1.0e-12;

    public final double[][] area;    // [cat][js]
    public final double[][] volume;  // [cat][js]
    public final double[][] snow;    // [cat][js]
    private final double[] remapA = new double[NCAT];
    private final double[] remapV = new double[NCAT];
    private final double[] remapS = new double[NCAT];
    private final int n2;
    /** True once categories have been populated from the bulk state. */
    public boolean initialized = false;

    public IceThicknessDistribution(OceanGrid g) {
        n2 = g.nx * g.ny;
        area = new double[NCAT][n2];
        volume = new double[NCAT][n2];
        snow = new double[NCAT][n2];
    }

    /** Rebuild categories from the existing bulk state. */
    public void initFromState(OceanState st) {
        clear();
        OceanGrid g = st.grid;
        for (int j = 0; j < g.ny; j++) {
            for (int i = 0; i < g.nx; i++) {
                if (!g.isWet(i, j)) continue;
                int js = j * g.nx + i;
                double a = Math.max(0.0, Math.min(1.0, st.iceFraction[js]));
                double v = Math.max(0.0, st.iceThickness[js]);
                if (a <= EPS || v <= EPS) continue;
                double h = v / a;
                int c = category(h);
                area[c][js] += a;
                volume[c][js] += v;
                snow[c][js] += Math.max(0.0, st.snowThickness[js]);
            }
        }
        initialized = true;
    }

    public void clear() {
        initialized = false;
        for (int c = 0; c < NCAT; c++) {
            java.util.Arrays.fill(area[c], 0.0);
            java.util.Arrays.fill(volume[c], 0.0);
            java.util.Arrays.fill(snow[c], 0.0);
        }
    }

    public int category(double h) {
        if (!(h > 0.0)) return 0;
        for (int c = 0; c < NCAT; c++) {
            if (h < BOUNDS[c + 1]) return c;
        }
        return NCAT - 1;
    }

    public double totalArea(int js) {
        double s = 0.0;
        for (int c = 0; c < NCAT; c++) s += area[c][js];
        return s;
    }

    public double totalVolume(int js) {
        double s = 0.0;
        for (int c = 0; c < NCAT; c++) s += volume[c][js];
        return s;
    }

    public double totalSnow(int js) {
        double s = 0.0;
        for (int c = 0; c < NCAT; c++) s += snow[c][js];
        return s;
    }

    /** Write totals back to the bulk state (single-category coupling). */
    public void syncToState(OceanState st) {
        OceanGrid g = st.grid;
        for (int j = 0; j < g.ny; j++) {
            for (int i = 0; i < g.nx; i++) {
                if (!g.isWet(i, j)) continue;
                int js = j * g.nx + i;
                double a = totalArea(js);
                if (a > 1.0) {
                    // ★ 2026-11-21（#4 底层审查）：面积是几何量；体积/雪是质量/能量。
                    // 面积 >1 时只能把面积归一到 1（cap area + retain mass），
                    // 绝不能把 volume/snow 一起缩放，否则静默丢质量。
                    // 完整的机械重分布 ψ 在后续步骤补；这里只修质量守恒 bug。
                    double inv = 1.0 / a;
                    for (int c = 0; c < NCAT; c++) {
                        area[c][js] *= inv;
                    }
                    a = 1.0;
                }
                st.iceFraction[js] = Math.max(0.0, Math.min(1.0, a));
                st.iceThickness[js] = Math.max(0.0, totalVolume(js));
                st.snowThickness[js] = Math.max(0.0, totalSnow(js));
            }
        }
    }

    /**
     * Add frazil volume to the thinnest category. The new area is the remaining
     * open-water fraction; no reference thickness is used. Returns the volume created.
     */
    public double addFrazil(int js, double dm) {
        if (!(dm > 0.0)) return 0.0;
        double open = Math.max(0.0, 1.0 - totalArea(js));
        if (open <= EPS) return 0.0;
        // Route B: frazil fills the open-water area; no H_ICE_REF reference thickness.
        area[0][js] += open;
        volume[0][js] += dm;
        return dm;
    }

    /** Largest absolute difference between category totals and the bulk state. */
    public double conservationError(OceanState st) {
        double err = 0.0;
        OceanGrid g = st.grid;
        for (int j = 0; j < g.ny; j++) {
            for (int i = 0; i < g.nx; i++) {
                if (!g.isWet(i, j)) continue;
                int js = j * g.nx + i;
                err = Math.max(err, Math.abs(totalArea(js) - st.iceFraction[js]));
                err = Math.max(err, Math.abs(totalVolume(js) - st.iceThickness[js]));
                err = Math.max(err, Math.abs(totalSnow(js) - st.snowThickness[js]));
            }
        }
        return err;
    }

    /**
     * Standard per-category zero-layer thermodynamics for one grid cell.
     * The caller owns the open-water/ML part and the salt/freshwater totals.
     */
    public void thermoStep(OceanState st, SeaSurfaceForcing f, double theta,
                           double dt, double tf, int i, int j, int js) {
        OceanGrid g = st.grid;
        int x = (int) Math.round(g.xOf(i));
        int z = (int) Math.round(g.zOf(j));
        for (int c = 0; c < NCAT; c++) {
            double a = area[c][js];
            if (a <= EPS) continue;
            double v = volume[c][js];
            double h = v / a;
            double hs = snow[c][js] / a;
            double qAtTf = f.netHeatFluxWm2(x, z, theta, tf, 1.0, h);
            double dV;
            if (qAtTf > 0.0) {
                double qIce = qAtTf;
                if (snow[c][js] > 0.0 && hs > 0.0) {
                    double dSnow = Math.min(snow[c][js],
                            qAtTf * dt * a / (OceanConstants.RHO_SNOW * OceanConstants.L_FUSION));
                    snow[c][js] -= dSnow;
                    if (dSnow > 0.0) {
                        st.snowMeltFlux[js] += dSnow * OceanConstants.RHO_SNOW / dt;
                        AdvectionDiffusionSolver.LAST_SNOW_MELT_KG += dSnow * OceanConstants.RHO_SNOW;
                        qIce -= dSnow * OceanConstants.RHO_SNOW * OceanConstants.L_FUSION / (dt * a);
                    }
                }
                dV = -Math.max(0.0, qIce) * dt
                        / (OceanConstants.RHO_ICE * OceanConstants.L_FUSION) * a;
            } else {
                double pond = AdvectionDiffusionSolver.MELT_POND_CESM_ON
                        ? st.meltPondArea[js] / Math.max(a, 1.0e-9) : 0.0;
                double qs = AdvectionDiffusionSolver.iceSurfaceFlux(f, theta, x, z,
                        tf, h, 1.0, hs, pond);
                if (AdvectionDiffusionSolver.D3_ICE_ATMOS_ON) {
                    st.iceSurfaceTemp[js] = AdvectionDiffusionSolver.LAST_ICE_SURFACE_TEMP;
                }
                dV = qs * dt
                        / (OceanConstants.RHO_ICE * OceanConstants.L_FUSION) * a;
            }
            if (i == TRACE_I && j == TRACE_J) {
                TRACE_QSUM += qAtTf * dt;
                TRACE_DVSUM += dV;
                TRACE_MAXH = Math.max(TRACE_MAXH, v / Math.max(EPS, a));
                TRACE_SNOWMAX = Math.max(TRACE_SNOWMAX, snow[c][js] / Math.max(EPS, a));
                TRACE_N++;
            }
            v += dV;
            if (v <= 0.0 || a <= EPS) {
                area[c][js] = 0.0;
                volume[c][js] = 0.0;
                if (snow[c][js] > 0.0) {
                    st.snowMeltFlux[js] += snow[c][js] * OceanConstants.RHO_SNOW / dt;
                    AdvectionDiffusionSolver.LAST_SNOW_MELT_KG += snow[c][js] * OceanConstants.RHO_SNOW;
                }
                snow[c][js] = 0.0;
            } else {
                volume[c][js] = v;
            }
            // snow -> ice conversion when snow load exceeds freeboard (mass-conserving)
            if (v > 0.0 && a > EPS && snow[c][js] > 0.0) {
                double hh = v / a;
                double freeboard = Math.max(0.0,
                        hh * (OceanConstants.RHO_REF - OceanConstants.RHO_ICE)
                                / OceanConstants.RHO_REF);
                double hsNow = snow[c][js] / a;
                if (hsNow > freeboard) {
                    double excess = hsNow - freeboard;
                    double snowVol = excess * a;
                    snow[c][js] -= snowVol;
                    volume[c][js] += snowVol * OceanConstants.RHO_SNOW / OceanConstants.RHO_ICE;
                    AdvectionDiffusionSolver.LAST_SNOW_ICE_KG += snowVol * OceanConstants.RHO_SNOW;
                }
            }
        }
        // Lateral melt is applied by FloeSizeDistribution after thermoStep.
        if (ITD_REMAP_ON) remapCategories(js);
    }

    /**
     * 一阶守恒厚度重映射：每个类别的 mean h = V/A 落到新的厚度 bin；
     * area/volume/snow 一起搬，总量严格守恒。默认不调用（ITD_REMAP_ON=false）。
     */
    public void remapCategories(int js) {
        for (int c = 0; c < NCAT; c++) { remapA[c] = 0.0; remapV[c] = 0.0; remapS[c] = 0.0; }
        for (int c = 0; c < NCAT; c++) {
            double a = area[c][js];
            if (a <= EPS) continue;
            double v = volume[c][js];
            double h = v / a;
            int t = category(h);
            remapA[t] += a;
            remapV[t] += v;
            remapS[t] += snow[c][js];
        }
        for (int c = 0; c < NCAT; c++) {
            area[c][js] = remapA[c];
            volume[c][js] = remapV[c];
            snow[c][js] = remapS[c];
        }
    }

    /** Remap all wet cells (used after transport when ITD_REMAP_ON). */
    public void remapAll(OceanState st) {
        OceanGrid g = st.grid;
        for (int j = 1; j < g.ny - 1; j++) {
            for (int i = 0; i < g.nx; i++) {
                if (!g.isWet(i, j)) continue;
                remapCategories(j * g.nx + i);
            }
        }
    }

    /**
     * Transport each category with the existing single-category upwind transport.
     * This is a transition implementation: it reuses transportIce per category and
     * then syncs totals; it is not a full ridging/remapping scheme.
     */
    public void transport(OceanState st, SeaSurfaceForcing f, double theta, double dt) {
        LAST_AREA_BEFORE = 0.0; LAST_AREA_AFTER = 0.0;
        LAST_RIDGE_LOST = 0.0; LAST_RIDGE_VOL = 0.0; LAST_RIDGE_COUNT = 0L;
        int n = st.iceFraction.length;
        double[] areaBefore = null;
        if (RIDGING_ON) {
            areaBefore = new double[n];
            for (int js = 0; js < n; js++) areaBefore[js] = totalArea(js);
        }
        if (!st.fsd.initialized) st.fsd.initFromState(st);
        for (int c = 0; c < NCAT; c++) {
            for (int js = 0; js < n; js++) {
                st.iceFraction[js] = area[c][js];
                st.iceThickness[js] = volume[c][js];
                st.snowThickness[js] = snow[c][js];
            }
            AdvectionDiffusionSolver.transportIce(st.grid, st, f, theta, dt);
            for (int js = 0; js < n; js++) {
                area[c][js] = st.iceFraction[js];
                volume[c][js] = st.iceThickness[js];
                snow[c][js] = st.snowThickness[js];
            }
        }
        for (int js = 0; js < n; js++) LAST_AREA_BEFORE += areaBefore == null ? 0.0 : areaBefore[js];
        for (int js = 0; js < n; js++) LAST_AREA_AFTER += totalArea(js);
        // Ridging must use the area loss *before* syncToState caps total area;
        // otherwise the cap loss and the ridging loss double-count.
        if (RIDGING_ON && areaBefore != null) {
            for (int js = 0; js < n; js++) {
                double lost = areaBefore[js] - totalArea(js);
                if (lost > EPS) {
                    redistribute(js, lost);
                    st.fsd.reconcile(st, js);
                    st.fsd.breakup(js, lost);
                }
            }
        }
        syncToState(st);
        for (int js = 0; js < n; js++) st.fsd.reconcile(st, js);
    }

    /**
     * ★ 2026-11-20（P3）：机械重分布（ridging）—— Thorndike et al. (1975) 的 ψ(g) 的**体块实现**。
     *
     * <p>物理：冰盖**辐聚**时面积减少而体积保留 ⟹ 剩下的冰必须变厚。本方法把损失的面积
     * {@code dArea} 从**最薄的类别**里扣掉（这些冰被压碎、并入厚冰），把对应的体积
     * **全部并入最厚的非空类别**（不增加它的面积）⟹ 该类别厚度上升。
     *
     * <p>守恒：面积恰好减少 {@code dArea}；**体积严格守恒**（扣除的体积 = 加入的体积）。
     * 这与 Thorndike 1975 的 ψ 在「只保留 ridging 模态、无热力源汇」时一致；
     * 与 Lipscomb (2001) 的 remapping 相比，这里不做跨类别的重映射（5 类粗分档下重映射的
     * 数值扩散大于物理增益），只做「薄 → 厚」的体积转移。
     *
     * <p>默认不启用（由 {@link #RIDGING_ON} 控制）；关闭时本方法不被调用 ⟹ 逐位不变。
     *
     * @param js    网格单元
     * @param dArea 因辐聚损失的冰面积（>0，单位：格子比例）
     * @return 被转移的体积（m，诊断用）
     */
    public double redistribute(int js, double dArea) {
        if (!(dArea > 0.0)) return 0.0;
        double aTot = totalArea(js);
        if (aTot <= EPS) return 0.0;
        double take = Math.min(dArea, aTot);
        LAST_RIDGE_LOST += take;
        LAST_RIDGE_COUNT++;
        double removedVol = 0.0;
        double removedSnow = 0.0;
        double removedArea = 0.0;
        double left = take;
        for (int c = 0; c < NCAT && left > EPS; c++) {
            double a = area[c][js];
            if (a <= EPS) continue;
            double da = Math.min(a, left);
            double h = volume[c][js] / a;
            double ds = da * (snow[c][js] / a);
            area[c][js] = a - da;
            double dv = da * h;
            volume[c][js] -= dv;
            snow[c][js] -= ds;
            removedVol += dv;
            removedSnow += ds;
            removedArea += da;
            left -= da;
            if (area[c][js] <= EPS) {
                removedSnow += snow[c][js];
                area[c][js] = 0.0;
                volume[c][js] = 0.0;
                snow[c][js] = 0.0;
            }
        }
        // 并入最厚的非空类别；若全空，则把面积与体积放回第 0 类（保守退化）
        int top = -1;
        for (int c = NCAT - 1; c >= 0; c--) if (area[c][js] > EPS) { top = c; break; }
        if (top < 0) {
            area[0][js] += removedArea;
            volume[0][js] += removedVol;
            snow[0][js] += removedSnow;
        } else {
            volume[top][js] += removedVol;
            snow[top][js] += removedSnow;
        }
        LAST_RIDGE_VOL += removedVol;
        return removedVol;
    }

    /** 是否启用 ridging（默认 false ⟹ 不改变任何现有行为）。 */
    public static boolean RIDGING_ON = false;
    public static double LAST_AREA_BEFORE = 0.0, LAST_AREA_AFTER = 0.0;
    public static double LAST_RIDGE_LOST = 0.0, LAST_RIDGE_VOL = 0.0;
    public static long LAST_RIDGE_COUNT = 0L;
    public static int TRACE_JS = -1;
    public static int TRACE_I = -1, TRACE_J = -1;
    public static double TRACE_QSUM = 0.0, TRACE_DVSUM = 0.0, TRACE_MAXH = 0.0, TRACE_SNOWMAX = 0.0;
    public static long TRACE_N = 0L;

    /**
     * ★ 2026-11-21（#4 步骤②）：热力改变厚度后做**厚度空间守恒重映射**
     * （一阶 donor-cell；CICE/Lipscomb 的线性 remap 后续可替换）。
     * 默认 false ⟹ Phase2 路径逐位不变。
     */
    public static boolean ITD_REMAP_ON = false;

    /** Apply an area loss to all categories proportionally (FSD lateral melt). */
    public void applyLateralLoss(int js, double lossArea) {
        if (!(lossArea > 0.0)) return;
        double a = totalArea(js);
        if (a <= EPS) return;
        double f = Math.max(0.0, 1.0 - lossArea / a);
        for (int c = 0; c < NCAT; c++) {
            area[c][js] *= f;
            volume[c][js] *= f;
            snow[c][js] *= f;
        }
    }

    /** Remove up to dm of ice volume, starting at the thinnest category. */
    public double meltVolume(int js, double dm) {
        double left = Math.max(0.0, dm);
        double removed = 0.0;
        for (int c = 0; c < NCAT && left > 0.0; c++) {
            double v = volume[c][js];
            if (v <= 0.0) continue;
            double take = Math.min(v, left);
            volume[c][js] = v - take;
            left -= take;
            removed += take;
            if (volume[c][js] <= EPS) {
                area[c][js] = 0.0;
                volume[c][js] = 0.0;
                snow[c][js] = 0.0;
            }
        }
        return removed;
    }
}
