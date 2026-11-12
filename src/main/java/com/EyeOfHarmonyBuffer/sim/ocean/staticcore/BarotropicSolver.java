package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

import com.EyeOfHarmonyBuffer.sim.ocean.SeaSurfaceForcing;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

/**
 * F5 最小正压风生环流 solver（J1 起步）。
 *
 * <p>方程：curl(tau)/(rho0*H) = del2(psi)，u=-dpsi/dy，v=dpsi/dx。
 * x 周期、y 墙、LandV3 mask；psi=0 于陆地/墙。
 * rho0=1027.8 kg/m3（Stocker 1992 / ClimberOcean）；H=sum(dz)。
 */
public final class BarotropicSolver {

    public static final double RHO0 = 1027.8;
    /** 底摩擦 r=1/adrag，adrag=4 days（MITgcm ocn_par.nml 参考值）。 */
    public static final double R_FRICTION = 1.0 / (4.0 * 86400.0);
    public static final double SOR_OMEGA = 0.2;

    /**
     * ★ 2026-11-03：风应力旋度的差分配对修复。
     * <p>旧式把「经向差分」除 dx、「纬向差分」除 dy，算出的既不是 curl 也不是 divergence；
     * 对纬向均匀风（curl 主项 = -d(tau_x)/dz）恒为 0。scratch 探针 NCurl 实测：修正前后 corr = -0.008。
     * <p>true = 物理正确（生产）；false = 复现 v7 的错配（仅 A/B 用）。
     */
    public static boolean CURL_PAIRING_FIXED = true;

    /**
     * ★ 2026-11-10（seed 0 NaN）：B6 球面度规开关。
     * <p>A/B：极点行（|lat|≈87.5°）dxc=dx·cosφ≈0.044·dx，正压 Laplacian 系数放大 ~500×；
     * 实测 seed 0 的南极点行 (j=1) 先出 NaN。false = 退回平面 dx（旧行为，仅 A/B 用）。
     */
    public static boolean USE_COS_METRIC = true;

    /**
     * ★ 2026-11-05（A1 的 3D 判据）：最近一次 update 的风应力旋度快照 [N/m^3]（nx*ny，干格 0）。
     *
     * <p>为什么要暴露它：旧门 `GATE_A1_SIGN` 比的是「模型纬向平均风应力旋度 vs 地球观测锚」，
     * 那是地球锚，3D 场换了之后必然漂移。3D 场自洽的判据应该是**模型自己的**
     * 「风旋度 ⟶ 正压流函数」这条链（符号 + 接线都在里面）。
     */
    public static double[] LAST_CURL;

    private BarotropicSolver() {}

    /** +j 方向是否指北（球面 +j 指南 ⟹ -1；笛卡尔按 lat 随 z 变化）。 */
    static double northSign(OceanGrid g, int j) {
        int jp = Math.min(g.ny - 1, j + 1);
        int jm = Math.max(0, j - 1);
        double d = g.latAt(jp) - g.latAt(jm);
        return d >= 0.0 ? 1.0 : -1.0;
    }

    public static final class Result {
        public final OceanState state;
        public final int iterations;
        public final double residual;
        Result(OceanState state, int iterations, double residual) {
            this.state = state; this.iterations = iterations; this.residual = residual;
        }
    }

    /** 冷启动版本：新建状态。 */
    public static Result solve(OceanGrid g, SeaSurfaceForcing f, double theta) {
        return update(g, new OceanState(g), f, theta);
    }

    /**
     * ★ 2026-11-02（季节循环）：在【已有状态】上重算风生 psi 与 u/v，**保留 T/S**。
     * 供 4 季/12 月循环使用：每月换一次风场，但不重置海洋的记忆。
     */
    public static Result update(OceanGrid g, OceanState st, SeaSurfaceForcing f, double theta) {
        return update(g, st, f, theta, null);
    }

    /**
     * ★ 2026-11-04（阶段 2c）：带父状态的更新。
     * <p>openY patch 的 ghost 行（j=0 / ny-1）是湿的：psi 取父值（Dirichlet），等于
     * 规定穿过该边界的水体输运；u/v 直接取父（不参与 psi 差分，避免越界）。
     */
    public static Result update(OceanGrid g, OceanState st, SeaSurfaceForcing f, double theta,
                                OceanParent parent) {
        int nx = g.nx, ny = g.ny;
        int n2 = nx * ny;
        double dx = g.dx, dy = g.dy;
        double h = 0.0;
        for (double v : g.dz) h += v;

        double[] rhs = new double[n2];
        double[] curlSnap = new double[n2];
        for (int j = 0; j < ny; j++) {
            // ★ 2026-11-09（阶段 4 / B6+B7）：
            //   B7：windStressZ 是 world +z，只有北支指北；正压解算要的是地理 τ_y，
            //       乘 northSign（球面 +j 指南 ⟹ -1）。旧式漏乘，NH 的 τ_y 项多 +2∂τ_N/∂x。
            //   B6：球面 zonal 度规 dxc = dx·cosφ，旧式按平面 dx。
            double latJ = g.latAt(j);
            double nsJ = northSign(g, j);
            double dxc = g.dx * (USE_COS_METRIC ? Math.max(0.05, Math.cos(latJ)) : 1.0);
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int iE = (i + 1) % nx, iW = (i - 1 + nx) % nx;
                int xiI = (int) Math.round(g.xOf(i));
                int zjI = (int) Math.round(g.zOf(j));
                int zN = (int) Math.round(g.zOf(j + 1));
                int zS = (int) Math.round(g.zOf(j - 1));
                int xE = (int) Math.round(g.xOf(iE));
                int xW = (int) Math.round(g.xOf(iW));
                double curl;
                if (CURL_PAIRING_FIXED) {
                    // curl_z(tau) = d(tau_y)/dx - d(tau_x)/dy，tau_y = northSign·tau_z
                    double tzE = nsJ * f.windStressZ(xE, zjI, theta);
                    double tzW = nsJ * f.windStressZ(xW, zjI, theta);
                    double txN = f.windStressX(xiI, zN, theta);
                    double txS = f.windStressX(xiI, zS, theta);
                    curl = (tzE - tzW) / (2.0 * dxc) - (txN - txS) / (2.0 * dy);
                } else {
                    // v7 旧式（错配；A/B 用，勿用于生产）
                    double txE = f.windStressX(xE, zjI, theta);
                    double txW = f.windStressX(xW, zjI, theta);
                    double tzN = f.windStressZ(xiI, zN, theta);
                    double tzS = f.windStressZ(xiI, zS, theta);
                    curl = (tzN - tzS) / (2.0 * dx) - (txE - txW) / (2.0 * dy);
                }
                curlSnap[j * nx + i] = curl;
                rhs[j * nx + i] = curl / (RHO0 * h);
            }
        }

        double[] psi = new double[n2];
        // 2c：ghost 行的 psi = 父值（穿过边界的输运）
        if (parent != null) {
            for (int j : new int[]{0, ny - 1}) {
                for (int i = 0; i < nx; i++) {
                    if (!g.isWet(i, j)) continue;
                    double p = parent.psi(g.xOf(i), g.zOf(j));
                    if (Double.isFinite(p)) psi[j * nx + i] = p;
                }
            }
        }
        double residual = 0.0;
        int iter = 0;
        int maxIter = 50000;
        for (; iter < maxIter; iter++) {
            residual = 0.0;
            for (int j = 1; j < ny - 1; j++) {
                double beta = WorldContract.betaForLatitude(g.latAt(j));
                double dxc = g.dx * (USE_COS_METRIC ? Math.max(0.05, Math.cos(g.latAt(j))) : 1.0);
                for (int i = 0; i < nx; i++) {
                    if (!g.isWet(i, j)) { psi[j * nx + i] = 0.0; continue; }
                    double w = g.isWet(i - 1, j) ? psi[j * nx + ((i - 1 + nx) % nx)] : 0.0;
                    double e = g.isWet(i + 1, j) ? psi[j * nx + ((i + 1) % nx)] : 0.0;
                    double s = g.isWet(i, j - 1) ? psi[(j - 1) * nx + i] : 0.0;
                    double nn = g.isWet(i, j + 1) ? psi[(j + 1) * nx + i] : 0.0;
                    // Stommel: central beta + bottom friction, Jacobi under-relaxation.
                    double rr = R_FRICTION;
                    double target = (rr / (dxc * dxc) * (w + e + s + nn)
                                   + beta / (2.0 * dxc) * (e - w)
                                   - rhs[j * nx + i]) / (4.0 * rr / (dxc * dxc));
                    double old = psi[j * nx + i];
                    double nv = old + SOR_OMEGA * (target - old);
                    psi[j * nx + i] = nv;
                    double d = Math.abs(nv - old);
                    if (d > residual) residual = d;
                }
            }
            if (residual < 1.0e-12) break;
        }

        st.psi = psi;
        LAST_CURL = curlSnap;   // ★ 2026-11-05：供 A1 的 3D 判据读（符号自洽：风旋度 ⟶ 正压 ψ）
        for (int j = 0; j < ny; j++) {
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                if (j == 0 || j == ny - 1) {
                    // ★ 2c：ghost 行直接用父的 u/v（psi 差分在边界会越界）
                    double x = g.xOf(i), z = g.zOf(j);
                    for (int k = 0; k < g.nz; k++) {
                        int q = st.idx(i, j, k);
                        double pu = parent == null ? Double.NaN : parent.uMs(x, z, k);
                        double pv = parent == null ? Double.NaN : parent.vMs(x, z, k);
                        st.u[q] = Double.isFinite(pu) ? pu : 0.0;
                        st.v[q] = Double.isFinite(pv) ? pv : 0.0;
                    }
                    continue;
                }
                double dxc = g.dx * (USE_COS_METRIC ? Math.max(0.05, Math.cos(g.latAt(j))) : 1.0);
                double u = -(psi[g.idx(i, j + 1)] - psi[g.idx(i, j - 1)]) / (2.0 * dy);
                double v = (psi[g.idx(i + 1, j)] - psi[g.idx(i - 1, j)]) / (2.0 * dxc);
                for (int k = 0; k < g.nz; k++) {
                    int q = st.idx(i, j, k);
                    st.u[q] = u; st.v[q] = v;
                }
            }
        }
        return new Result(st, iter, residual);
    }
}
