package com.EyeOfHarmonyBuffer.sim.ocean;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;

/**
 * 一维 Munk 行求解器 —— M2 海洋层的**物理内核**（设计冻结 §3.1'''' / §18）。
 *
 * <h3>为什么是「一条纬度行」</h3>
 * 前两轮连续否决了两个 2-D 求解域方案（连通海区 850 s、海岸定界 262 s），根因是
 * **泊松方程的解依赖整个定义域**，在无限世界上只能用窗口近似，窗口边缘就是接缝，
 * 而且 `cost ∝ L^4`（格数 x 步数，Rossby CFL）。
 *
 * ⇒ 沿一条纬度行解稳态 Munk 方程（**一维边值问题**）：
 * <pre>
 *   A_H * psi'''' - beta * psi' = -curl / (rho0*H)
 *   BC: psi = 0, psi'' = 0 于两端墙（自由滑移）
 * </pre>
 *
 * <h3>解法：解析基函数 + 4x4 线性方程组（精确，O(1)）</h3>
 * 齐次部分 `A_H psi'''' - beta psi' = 0` 的特征根是 `m(A_H m^3 - beta) = 0`：
 * <pre>
 *   m = 0,  1/delta,  delta^-1 * e^{+-2pi i/3}      delta = (A_H/beta)^(1/3)
 * </pre>
 * 所以通解 = Sverdrup 特解 + 四个基函数：
 * <pre>
 *   psi(x) = psi_S(x) + C0 + C1*e^((s-L)/delta) + e^(-s/2delta) * [C2 cos(w s) + C3 sin(w s)]
 *   s = x - x_west,  L = x_east - x_west,  w = sqrt(3)/(2 delta)
 *   ⚠ 第二项必须用**有界**形式 e^((s-L)/delta) 而不是 e^(s/delta)：L/delta 可达 35，
 *   后者在 4x4 里造成 1e15 的动态范围 ⇒ 灾难性抵消（实测 |v|峰 会只有真值的一半）。
 *   psi_S(x) = -(1/(rho0*H*beta)) * integral_x^{x_east} curl dx'
 * </pre>
 * 四个边界条件正好定四个常数 ⇒ **对任意 curl(x) 都精确**，不需要网格、不需要迭代。
 *
 * <h3>与 2-D 解的对照（P249）</h3>
 * 同参数下内区 v 与 Sverdrup 预测**完全一致**（-3.61 vs -3.612 mm/s），西/东 = 15.0（2-D 12.1），
 * 成本 1 ms/行（2-D 椭圆解 16~893 s）。
 *
 * <h3>已知限制（§18.5）</h3>
 * 一维降维丢掉 d^2/dy^2 ⇒ 一条西岸在经向上结束时，本模型会丢掉那条流；
 * 相邻行之间也不严格满足二维不可压。落地时用**相邻行平滑**处理。
 */
public final class GyreRow {

    private GyreRow() {}

    /** 风应力旋度提供者（**纯函数**）。M3 完成后由真实大气层实现。 */
    public interface WindCurl {
        double at(int x, int z);
    }
    
    /** 地球自转速率（世界契约的一部分）。
     *  ⚠ 2026-09-13（审计 D5 同族）：这里原来是**第三份** 7.2921e-5 字面量（且全仓库零引用）。
     *  现引用世界契约的单一来源；保留本字段只为兼容探针签名。 */
    public static final double OMEGA = com.EyeOfHarmonyBuffer.sim.world.WorldContract.OMEGA;

    /** 世界契约的纬度。**实现委托给 WorldContract，保持单一真相来源。** */
    public static double latOf(int z, int zCycle) {
        return com.EyeOfHarmonyBuffer.sim.world.WorldContract.latOf(z, zCycle);
    }

    /** beta(phi) = 2*Omega*pi/Z_CYCLE*cos(phi)。
     *  **不要再用常数 3.24e-10 —— 那是 45 度的值。** 见 §23.0 的仪器错误。 */
    public static double betaForLatitude(double latRad, int zCycle) {
        return com.EyeOfHarmonyBuffer.sim.world.WorldContract.betaForLatitude(latRad, zCycle);
    }

    /** 求解参数。 */
    public static final class Params {
        /** 科氏参数梯度。**默认 NaN = 由 solve() 按该行的纬度自动推导**（§23.0 的仪器错误：
         *  早先默认值是 45 度的 3.24e-10，而六个纬度带共用了它）。
         *  只有合成算例才手动设它；设了就以手动值为准。 */
        public double beta = Double.NaN;
        /** 世界契约的气候周期（block），用于推导纬度与 beta。 */
        public int zCycle = com.EyeOfHarmonyBuffer.sim.world.WorldContract.Z_CYCLE;
        /** 手动 beta 是否已设置。 */
        public boolean hasBeta() { return !Double.isNaN(beta) && beta > 0.0; }
        /** 某条纬度行的 beta（手动优先，否则按纬度推导）。 */
        public double betaAt(int z) {
            return hasBeta() ? beta : betaForLatitude(latOf(z, zCycle), zCycle);
        }
        /** 某条纬度行的 Munk 层宽（m）—— 盆宽门槛用它，**不要用常数 delta()**。 */
        public double deltaAt(int z) { return Math.cbrt(aH / betaAt(z)); }
        /** 复制并换一个 beta（solve() 内部用）。 */
        public Params withBeta(double b) {
            Params q = new Params();
            q.beta = b; q.aH = aH; q.rhoH = rhoH; q.h = h; q.maxRow = maxRow; q.zCycle = zCycle;
            return q;
        }
        public double aH = 1.9e4;
        /** rho0 * H。**H 必须是真实海洋深度（2000~4000 m）** —— §17.3：
         *  旧值 100 m 让速度放大 40 倍（输运与 H 无关、速度 ∝ 1/H）。 */
        /** ⚠ 2026-09-13（审计 D5 同族）：原来是字面量 1025.0，与 CoastalLayer.RHO 是两个独立常量。
         *  现引用 CoastalLayer.RHO（同包、final ⇒ 无漂移可能）。 */
        public double rhoH = CoastalLayer.RHO * 4000.0;
        /** 行网格间距（block）。要能分辨 delta_M，取 delta_M/8 左右。 */
        public double h = 5_000.0;
        /**
         * 行扫描上限（block），防止无限循环。
         *
         * <p>⚠ **必须大于最大海盆宽度**：PLATE_CELL=2400 km 下海盆宽可达 16,000 km，
         * 而查询点常常落在海盆东侧 ⇒ 12M 的上限会把东岸**截断**，
         * 于是「东边界」根本不存在（P273 实测：64 条纬线里只有 19 条有真海岸，
         * 且这些纬线的沿岸风剖面大面积为零）。已提到 24M。
         */
        public int maxRow = 24_000_000;
        /** 手动 beta 下的 delta_M。未设 beta 时返回 NaN —— 请改用 `deltaAt(z)`。 */
        public double delta() { return Math.cbrt(aH / beta); }
    }

    /**
     * 解析**三带**风（占位 + M3 的纬向骨架）：
     * <pre>
     *   tau_x(bandD) = -tau0 * cos(3 pi bandD)          —— 信风 / 西风 / 极地东风
     *   curl         = -3 pi tau0 * sin(3 pi bandD) / MAX_D
     *   ⇒ bandD 1/6 处 curl&lt;0（副热带）、1/2 处 curl&gt;0（副极地）、5/6 处 curl&lt;0（极地）
     * </pre>
     * ⚠ 早先写成 `sin(2 pi bandD)` 只有**两带**，与地球不符（地球是三条风带），会给出错误的涡旋分带。
     */
    public static final class BandedWind implements WindCurl {
        private final double tau0;
        private final int zc;
        public BandedWind(double tau0, int zCycle) { this.tau0 = tau0; this.zc = zCycle; }
        @Override public double at(int x, int z) {
            double b = bandD(z, zc);
            return -(3.0 * Math.PI * tau0 / (zc / 2.0)) * Math.sin(3.0 * Math.PI * b);
        }
        /** 风应力本身（Pa）。表层埃克曼漂流需要它，不只是 curl。 */
        public double tauX(int z) { return -tau0 * Math.cos(3.0 * Math.PI * bandD(z, zc)); }
        public double tauY(int z) { return 0.0; }
    }

    static double bandD(int z, int zc) {
        int zz = ((z % zc) + zc) % zc;
        int d = Math.min(zz, zc - zz);
        return d / (zc / 2.0);
    }

    /** 一行的解。 */
    public static final class Row {
        public boolean valid;
        public int westX, eastX;        // 两端墙的 block 坐标
        public double h;
        public int n;
        public double[] psi, v;         // 长度 n，i 对应 x = westX + i*h
        public double psiMax, vPeak, westBandMean, eastBandMean;
        /** 西边界流速度 = |psi_S(西岸)| / delta_M（诊断用）。 */
        public double wbcSpeed;
        public double xAt(int i) { return westX + i * h; }
    }

    /** 沿包含 (x,z) 的那条纬度行求解。**纯函数**（除了读写传入的 wind provider）。 */
    public static Row solve(int x, int z, long seed, int cell, WindCurl wind, Params p) {
        Row r = new Row();
        double h = p.h;
        // beta 未手动设置 ⇒ 按这条纬度行的纬度推导（§23.0：绝不能再共用 45 度的常数）
        if (!p.hasBeta()) p = p.withBeta(p.betaAt(z));
        // --- 1) 找行的两端 ---
        int xw = x;
        while (xw > -p.maxRow && !PlateField.isLandWithCell(xw - (int) h, z, seed, cell)) xw -= (int) h;
        int xe = x;
        while (xe < p.maxRow && !PlateField.isLandWithCell(xe + (int) h, z, seed, cell)) xe += (int) h;
        if (PlateField.isLandWithCell(x, z, seed, cell)) { r.valid = false; return r; }
        int n = (int) ((xe - xw) / h) + 1;
        if (n < 16) { r.valid = false; return r; }
        double[] curl = new double[n];
        for (int i = 0; i < n; i++) curl[i] = wind.at(xw + (int) (i * h), z);
        return solveSpan(xw, xe, curl, p);
    }

    /** 核心：给定 [xw, xe] 与逐点 curl，解一维 Munk 边值问题。**可用合成 curl 单独验证。** */
    public static Row solveSpan(int xw, int xe, double[] curl, Params p) {
        Row r = new Row();
        double h = p.h;
        if (!p.hasBeta()) {
            throw new IllegalStateException("GyreRow.solveSpan: Params.beta 未设置（NaN）。\n"
                + "  - 走 solve(x,z,...) 会自动按纬度推导，不需要手动设；\n"
                + "  - 直接调 solveSpan 的合成算例必须显式设 beta（见 P250/P251 的合成验证）。");
        }
        int n = curl.length;
        if (n < 16) { r.valid = false; return r; }
        r.valid = true; r.westX = xw; r.eastX = xe; r.h = h; r.n = n;

        // --- 2) Sverdrup 积分（从东岸往西累加，psi_S(东岸)=0） ---
        double kfb = 1.0 / (p.rhoH * p.beta);
        double[] psiS = new double[n];
        double acc = 0;
        for (int i = n - 1; i >= 0; i--) {
            if (i < n - 1) acc += 0.5 * (curl[i] + curl[i + 1]) * h;
            psiS[i] = -acc * kfb;
        }
        double psiSw = psiS[0];

        // --- 3) psi_S 的二阶导（中心差分；端点用单侧） ---
        double[] psiS2 = new double[n];
        for (int i = 0; i < n; i++) {
            if (i == 0) psiS2[i] = (psiS[2] - 2 * psiS[1] + psiS[0]) / (h * h);
            else if (i == n - 1) psiS2[i] = (psiS[n - 1] - 2 * psiS[n - 2] + psiS[n - 3]) / (h * h);
            else psiS2[i] = (psiS[i + 1] - 2 * psiS[i] + psiS[i - 1]) / (h * h);
        }

        // --- 4) 4x4：四个基函数在两端满足 psi=0 与 psi''=0 ---
        double d = p.delta();
        double kk = 1.0 / (2.0 * d);
        double w = Math.sqrt(3.0) / (2.0 * d);
        double L = (n - 1) * h;
        double[][] M = new double[4][4];
        double[] rhs = new double[4];
        fillBC(M, rhs, 0, 0.0, L, d, kk, w, psiS[0], psiS2[0]);          // 西端 x'=0
        fillBC(M, rhs, 2, L, L, d, kk, w, psiS[n - 1], psiS2[n - 1]);    // 东端 x'=L
        double[] C = gauss4(M, rhs);

        // --- 5) 求值 ---
        r.psi = new double[n]; r.v = new double[n];
        double vmax = 0; int vk = 0; double pmax = 0;
        for (int i = 0; i < n; i++) {
            double s = i * h;
            double e1 = Math.exp((s - L) / d);
            double ek = Math.exp(-kk * s);
            double cs = Math.cos(w * s), sn = Math.sin(w * s);
            r.psi[i] = psiS[i] + C[0] + C[1] * e1 + ek * (C[2] * cs + C[3] * sn);
            double d1 = C[1] * e1 / d;
            double d2 = ek * (-w * sn - kk * cs);
            double d3 = ek * (w * cs - kk * sn);
            r.v[i] = (psiS[i + 1 < n ? i + 1 : i] - psiS[i > 0 ? i - 1 : i]) / (2 * h)
                   + d1 + C[2] * d2 + C[3] * d3;
            if (Math.abs(r.psi[i]) > Math.abs(pmax)) pmax = r.psi[i];
            if (Math.abs(r.v[i]) > Math.abs(vmax)) { vmax = r.v[i]; vk = i; }
        }
        r.psiMax = pmax;
        r.vPeak = vmax;
        int nMw = (int) (Math.PI * d / h);
        double ws = 0, es = 0; int wn = 0, en = 0;
        for (int i = 0; i < Math.min(nMw, n); i++) { ws += Math.abs(r.v[i]); wn++; }
        for (int i = Math.max(0, n - nMw); i < n; i++) { es += Math.abs(r.v[i]); en++; }
        r.westBandMean = ws / Math.max(1, wn);
        r.eastBandMean = es / Math.max(1, en);
        r.wbcSpeed = Math.abs(psiSw) / d;
        return r;
    }

    /** 填两行 BC：psi(s)=0 与 psi''(s)=0。row0 = 目标行号。 */
    private static void fillBC(double[][] M, double[] rhs, int row0, double s, double L, double d, double kk, double w,
                               double psiS_s, double psiS2_s) {
        double e1 = Math.exp((s - L) / d);
        double ek = Math.exp(-kk * s);
        double cs = Math.cos(w * s), sn = Math.sin(w * s);
        // psi 行
        M[row0][0] = 1.0; M[row0][1] = e1; M[row0][2] = ek * cs; M[row0][3] = ek * sn;
        rhs[row0] = -psiS_s;
        // psi'' 行：f1''=e1/d^2; f2''=ek*(-2kk^2 cs + 2kk w sn); f3''=ek*(-2kk^2 sn - 2kk w cs)
        M[row0 + 1][0] = 0.0;
        M[row0 + 1][1] = e1 / (d * d);
        M[row0 + 1][2] = ek * (-2 * kk * kk * cs + 2 * kk * w * sn);
        M[row0 + 1][3] = ek * (-2 * kk * kk * sn - 2 * kk * w * cs);
        rhs[row0 + 1] = -psiS2_s;
    }

    static double[] gauss4(double[][] M, double[] b) {
        int n = 4;
        for (int k = 0; k < n; k++) {
            int piv = k;
            for (int i = k + 1; i < n; i++) if (Math.abs(M[i][k]) > Math.abs(M[piv][k])) piv = i;
            double[] t = M[k]; M[k] = M[piv]; M[piv] = t;
            double tt = b[k]; b[k] = b[piv]; b[piv] = tt;
            for (int i = k + 1; i < n; i++) {
                double f = M[i][k] / M[k][k];
                for (int j = k; j < n; j++) M[i][j] -= f * M[k][j];
                b[i] -= f * b[k];
            }
        }
        double[] x = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            double s = b[i];
            for (int j = i + 1; j < n; j++) s -= M[i][j] * x[j];
            x[i] = s / M[i][i];
        }
        return x;
    }
}
