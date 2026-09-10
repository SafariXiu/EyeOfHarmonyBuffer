package com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer;

/**
 * 稳态**风生正压环流**求解器（路线 B · 第二版）。
 *
 * <pre>
 *   ∂ζ/∂t = curl_z(τ)/(ρ₀·H) − β·v + A_h·∇²ζ
 *   ζ = ∇²ψ ,  u = −∂ψ/∂y ,  v = ∂ψ/∂x ,  τ = ρ_a·C_D·|U|·U
 *   curl_z(τ) = ∂τy/∂x − ∂τx/∂y
 * </pre>
 *
 * <b>为什么在粗网格上解</b>：Munk 层 δ_M=(A_h/β)^(1/3)。本世界 β≈1.15e-10（地球 5 倍）
 * 而海盆中位数只有 1510 km —— 用地球的 A_h≈1.2e3 得 δ_M=22 km、西向强化倍数 L/δ_M≈69，
 * 算出来是几 m/s 的假洋流。取 A_h=1.8e6 ⇒ δ_M≈250 km、L/δ_M≈6 ⇒ 真实的 0.7~1.2 m/s。
 * δ_M=250 km 意味着粗网格 35 km 完全够（7 格），于是整场在 32×128 上解。
 *
 * <b>为什么必须多重网格</b>：第一版用高斯-赛德尔解泊松，大尺度模式收敛率只有 1−O(1/N²)，
 * ψ 在盆尺度上滞后于 ζ → β 项错 → ζ 持续积分 → maxV 跑到 973 m/s。加扫描次数无效。
 *
 * 粗/细网格用**采样**解耦（不做代数多重网格），避免整除问题；粗网格自成一套离散。
 */
public final class BarotropicGyre {

    private BarotropicGyre() {}

    public static final double OMEGA = 7.2921e-5;
    public static double RHO_AIR = 1.2;
    public static double C_D = 1.3e-3;
    public static double RHO_WATER = 1025.0;
    public static double H_MIXED = 100.0;
    public static double A_H = 1.8e6;
    public static double WIND_MS = 10.0;
    public static double BETA_SCALE = 1.0;

    public static int NCX = 32, NCZ = 128;
    /** 伪时间步长（s）：显式扩散上限 ≈ 1/(2A_h(1/hx²+1/hz²)) ≈ 155 s。 */
    public static double DT = 140.0;
    /** 步数：DT×MACRO 需 ≥ 环流建立时间（≈8 天 = 7e5 s）。 */
    public static int MACRO = 5000;
    public static int V_CYCLES = 1;

    public static double lastResidual, lastMaxU, lastMaxV, lastMaxZeta, lastMaxSrc, lastMs;

    private static final class Lv {
        final int nx, ny, n;
        final boolean[] land;
        final double[] psi, rhs, res;
        Lv(int nx, int ny, boolean[] land) {
            this.nx = nx; this.ny = ny; this.n = nx * ny; this.land = land;
            psi = new double[n]; rhs = new double[n]; res = new double[n];
        }
        int id(int x, int y) {
            return (((y % ny) + ny) % ny) * nx + (((x % nx) + nx) % nx);
        }
    }

    private static Lv[] buildLevels(Lv fine) {
        java.util.List<Lv> ls = new java.util.ArrayList<>();
        ls.add(fine);
        Lv cur = fine;
        while (cur.nx >= 8 && cur.ny >= 16) {
            int nx = cur.nx / 2, ny = cur.ny / 2;
            boolean[] land = new boolean[nx * ny];
            for (int y = 0; y < ny; y++) {
                for (int x = 0; x < nx; x++) {
                    int c = 0;
                    if (cur.land[cur.id(2 * x, 2 * y)]) c++;
                    if (cur.land[cur.id(2 * x + 1, 2 * y)]) c++;
                    if (cur.land[cur.id(2 * x, 2 * y + 1)]) c++;
                    if (cur.land[cur.id(2 * x + 1, 2 * y + 1)]) c++;
                    land[y * nx + x] = c >= 2;
                }
            }
            cur = new Lv(nx, ny, land);
            ls.add(cur);
        }
        return ls.toArray(new Lv[0]);
    }

    private static void smooth(Lv l, int iters) {
        for (int it = 0; it < iters; it++) {
            for (int y = 0; y < l.ny; y++) {
                for (int x = 0; x < l.nx; x++) {
                    int i = y * l.nx + x;
                    if (l.land[i]) { l.psi[i] = 0; continue; }
                    double s = l.psi[l.id(x + 1, y)] + l.psi[l.id(x - 1, y)]
                             + l.psi[l.id(x, y + 1)] + l.psi[l.id(x, y - 1)];
                    l.psi[i] = 0.25 * (s - l.rhs[i]);
                }
            }
        }
    }

    private static void residual(Lv l) {
        for (int y = 0; y < l.ny; y++) {
            for (int x = 0; x < l.nx; x++) {
                int i = y * l.nx + x;
                if (l.land[i]) { l.res[i] = 0; continue; }
                double s = l.psi[l.id(x + 1, y)] + l.psi[l.id(x - 1, y)]
                         + l.psi[l.id(x, y + 1)] + l.psi[l.id(x, y - 1)] - 4.0 * l.psi[i];
                l.res[i] = l.rhs[i] - s;
            }
        }
    }

    private static void restrict(Lv f, Lv c) {
        for (int y = 0; y < c.ny; y++) {
            for (int x = 0; x < c.nx; x++) {
                double v = 4.0 * f.res[f.id(2 * x, 2 * y)]
                    + 2.0 * (f.res[f.id(2 * x + 1, 2 * y)] + f.res[f.id(2 * x - 1, 2 * y)]
                           + f.res[f.id(2 * x, 2 * y + 1)] + f.res[f.id(2 * x, 2 * y - 1)])
                    + (f.res[f.id(2 * x + 1, 2 * y + 1)] + f.res[f.id(2 * x - 1, 2 * y + 1)]
                     + f.res[f.id(2 * x + 1, 2 * y - 1)] + f.res[f.id(2 * x - 1, 2 * y - 1)]);
                c.rhs[y * c.nx + x] = v / 16.0;
            }
        }
        java.util.Arrays.fill(c.psi, 0.0);
    }

    private static void prolongAdd(Lv c, Lv f) {
        for (int y = 0; y < c.ny; y++) {
            for (int x = 0; x < c.nx; x++) {
                double v = c.psi[y * c.nx + x];
                int fx = 2 * x, fy = 2 * y;
                f.psi[f.id(fx, fy)] += v;
                f.psi[f.id(fx + 1, fy)] += 0.5 * v;
                f.psi[f.id(fx - 1, fy)] += 0.5 * v;
                f.psi[f.id(fx, fy + 1)] += 0.5 * v;
                f.psi[f.id(fx, fy - 1)] += 0.5 * v;
                f.psi[f.id(fx + 1, fy + 1)] += 0.25 * v;
                f.psi[f.id(fx - 1, fy + 1)] += 0.25 * v;
                f.psi[f.id(fx + 1, fy - 1)] += 0.25 * v;
                f.psi[f.id(fx - 1, fy - 1)] += 0.25 * v;
            }
        }
    }

    private static void vcycle(Lv[] ls, int lvl) {
        Lv l = ls[lvl];
        if (lvl == ls.length - 1) { smooth(l, 60); return; }
        smooth(l, 2);
        residual(l);
        Lv c = ls[lvl + 1];
        restrict(l, c);
        vcycle(ls, lvl + 1);
        prolongAdd(c, l);
        smooth(l, 2);
    }

    private static double tauX(double[] u, double[] v, int i) {
        double sp = Math.hypot(u[i], v[i]) * WIND_MS;
        return RHO_AIR * C_D * sp * u[i] * WIND_MS;
    }

    private static double tauY(double[] u, double[] v, int i) {
        double sp = Math.hypot(u[i], v[i]) * WIND_MS;
        return RHO_AIR * C_D * sp * v[i] * WIND_MS;
    }

    public static void solve(int nxF, int nyF, double dxF, double dzF, boolean[] landF,
                             double[] uW, double[] vW, double[] fRowF, double[] betaRowF,
                             double[] uOutF, double[] vOutF) {
        long t0 = System.nanoTime();
        int ncx = Math.max(4, Math.min(NCX, nxF)), ncz = Math.max(4, Math.min(NCZ, nyF));
        double hx = nxF * dxF / ncx, hz = nyF * dzF / ncz;
        int nc = ncx * ncz;

        // ---- 粗网格采样（最近邻；粗细网格用采样解耦，不需要整除） ----
        boolean[] land = new boolean[nc];
        double[] uC = new double[nc], vC = new double[nc];
        double[] fC = new double[ncz], bC = new double[ncz];
        for (int y = 0; y < ncz; y++) {
            int fy = (int) Math.min(nyF - 1, Math.floor((y + 0.5) * hz / dzF));
            fC[y] = fRowF[fy];
            bC[y] = betaRowF[fy];
            for (int x = 0; x < ncx; x++) {
                int fx = (int) Math.min(nxF - 1, Math.floor((x + 0.5) * hx / dxF));
                int fi = fy * nxF + fx, i = y * ncx + x;
                land[i] = landF[fi];
                uC[i] = land[i] ? 0 : uW[fi];
                vC[i] = land[i] ? 0 : vW[fi];
            }
        }

        // ---- 风应力平方律 + 旋度 → 源项 ----
        double[] src = new double[nc];
        double maxSrc = 0;
        for (int y = 0; y < ncz; y++) {
            for (int x = 0; x < ncx; x++) {
                int i = y * ncx + x;
                if (land[i]) continue;
                int ip = y * ncx + (x + 1) % ncx, im = y * ncx + (x - 1 + ncx) % ncx;
                int jp = ((y + 1) % ncz) * ncx + x, jm = ((y - 1 + ncz) % ncz) * ncx + x;
                double dTauYdx = (tauY(uC, vC, ip) - tauY(uC, vC, im)) / (2.0 * hx);
                double dTauXdy = (tauX(uC, vC, jp) - tauX(uC, vC, jm)) / (2.0 * hz);
                src[i] = (dTauYdx - dTauXdy) / (RHO_WATER * H_MIXED);
                if (Math.abs(src[i]) > maxSrc) maxSrc = Math.abs(src[i]);
            }
        }
        lastMaxSrc = maxSrc;

        Lv fine = new Lv(ncx, ncz, land);
        Lv[] ls = buildLevels(fine);
        double[] zeta = new double[nc];
        double hbar2 = hx * hz;

        // ---- 伪时间步进到稳态 ----
        double maxRes = 0;
        for (int it = 0; it < MACRO; it++) {
            for (int i = 0; i < nc; i++) fine.rhs[i] = land[i] ? 0 : hbar2 * zeta[i];
            for (int v = 0; v < V_CYCLES; v++) vcycle(ls, 0);
            maxRes = 0;
            for (int y = 0; y < ncz; y++) {
                double beta = bC[y] * BETA_SCALE;
                for (int x = 0; x < ncx; x++) {
                    int i = y * ncx + x;
                    if (land[i]) continue;
                    int ip = y * ncx + (x + 1) % ncx, im = y * ncx + (x - 1 + ncx) % ncx;
                    int jp = ((y + 1) % ncz) * ncx + x, jm = ((y - 1 + ncz) % ncz) * ncx + x;
                    double v = (fine.psi[ip] - fine.psi[im]) / (2.0 * hx);
                    double lap = (zeta[ip] - 2 * zeta[i] + zeta[im]) / (hx * hx)
                               + (zeta[jp] - 2 * zeta[i] + zeta[jm]) / (hz * hz);
                    double d = DT * (src[i] - beta * v + A_H * lap);
                    zeta[i] += d;
                    double a = Math.abs(d);
                    if (a > maxRes) maxRes = a;
                    if (a != a) { lastResidual = Double.NaN; return; }   // NaN 早退
                }
            }
        }
        lastResidual = maxRes;
        double maxZ = 0;
        for (int i = 0; i < nc; i++) if (!land[i] && Math.abs(zeta[i]) > maxZ) maxZ = Math.abs(zeta[i]);
        lastMaxZeta = maxZ;

        // ---- 最后一次泊松 → 粗格中心梯度 → 双线性插值回细网格 ----
        for (int i = 0; i < nc; i++) fine.rhs[i] = land[i] ? 0 : hbar2 * zeta[i];
        for (int v = 0; v < V_CYCLES + 1; v++) vcycle(ls, 0);
        double[] uG = new double[nc], vG = new double[nc];
        for (int y = 0; y < ncz; y++) {
            for (int x = 0; x < ncx; x++) {
                int i = y * ncx + x;
                if (land[i]) continue;
                int ip = y * ncx + (x + 1) % ncx, im = y * ncx + (x - 1 + ncx) % ncx;
                int jp = ((y + 1) % ncz) * ncx + x, jm = ((y - 1 + ncz) % ncz) * ncx + x;
                uG[i] = -(fine.psi[jp] - fine.psi[jm]) / (2.0 * hz);
                vG[i] = (fine.psi[ip] - fine.psi[im]) / (2.0 * hx);
            }
        }
        double mu = 0, mv = 0;
        for (int y = 0; y < nyF; y++) {
            double gy = (y + 0.5) * dzF / hz - 0.5;
            int y0 = (int) Math.floor(gy);
            double ty = gy - y0;
            for (int x = 0; x < nxF; x++) {
                int fi = y * nxF + x;
                if (landF[fi]) { uOutF[fi] = 0; vOutF[fi] = 0; continue; }
                double gx = (x + 0.5) * dxF / hx - 0.5;
                int x0 = (int) Math.floor(gx);
                double tx = gx - x0;
                double u = bl(uG, ncx, ncz, x0, y0, tx, ty);
                double v = bl(vG, ncx, ncz, x0, y0, tx, ty);
                uOutF[fi] = u;
                vOutF[fi] = v;
                if (Math.abs(u) > mu) mu = Math.abs(u);
                if (Math.abs(v) > mv) mv = Math.abs(v);
            }
        }
        lastMaxU = mu;
        lastMaxV = mv;
        lastMs = (System.nanoTime() - t0) / 1e6;
    }

    private static int idw(int y, int x, int nx, int ny) {
        return (((y % ny) + ny) % ny) * nx + (((x % nx) + nx) % nx);
    }

    private static double bl(double[] f, int nx, int ny, int x0, int y0, double tx, double ty) {
        double a = f[idw(y0, x0, nx, ny)] * (1 - tx) + f[idw(y0, x0 + 1, nx, ny)] * tx;
        double b = f[idw(y0 + 1, x0, nx, ny)] * (1 - tx) + f[idw(y0 + 1, x0 + 1, nx, ny)] * tx;
        return a * (1 - ty) + b * ty;
    }
}
