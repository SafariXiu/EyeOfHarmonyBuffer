package com.EyeOfHarmonyBuffer.sim.ocean;

import com.EyeOfHarmonyBuffer.sim.Frozen;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.ClimlabEBM;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

/**
 * ★★★★★ **2026-10-20：内生海洋经向热输运（OHT）子模型——Stocker et al. (1992) 纬向平均多盆地海洋。**
 *
 * <p>母本：Stocker, Wright &amp; Mysak (1992), J. Climate 5, 773-797（CLIMBER-2 海洋的基础）；
 * 方程与闭合逐字取自 Wright &amp; Stocker (1992), JGR 97, 12,709（第 3 页原图核对）：
 * <pre>
 *   (1) -2Ω v = -(1/(ρ* a c)) (Δp/ΔΛ) + τs/(ρ* H_M) - X(z+H_M)
 *   (2)  2Ω u = -(1/(ρ* a)) ∂p/∂φ
 *   (3)  ∂p/∂z = -ρ g
 *   (4)  (1/(a c)) ∂(c v)/∂φ + ∂w/∂z = 0
 *   (5)  ∂T/∂t + (1/(a c))∂(c v T)/∂φ + ∂(w T)/∂z
 *          = (1/(a c))∂/∂φ(c K_H ∂T/∂φ) + ∂/∂z(K_V ∂T/∂z) + q_conv
 *   (10) Δp = -ε ΔΛ sin(2φ) ∂p/∂φ
 *   (11) ε = ε₀ π/(3ΔΛ)，ε₀ = 0.3
 *   (13) -K_V ∂T/∂z = (H_M/τ_H)(T* - T)，τ_H = 50 d
 * </pre>
 *
 * <p><b>本版（R8）范围（诚实边界）</b>：T-only；S 固定 35 psu；风应力 = 0；
 * 表面 T* 暂用当前 {@link ClimlabEBM#tOceanK} 的 SST（脚手架，用于验证求解器能收敛）；
 * 盐度、E-P、风应力、Semtner 海冰、OHT 接 EBM 依次在后续轮次接。
 * 本类当前<b>不被生产代码消费</b>，所以对输出零影响。
 *
 * <p><b>网格</b>：10° × 18 纬带（中心 -85..85）、15 层（Stocker 1992 Table 1 层底）、
 * 3 盆地（大西洋 80N/60°、太平洋 55N/120°、印度洋 20N/65°）+ 南大洋（40S..70S，全经度混合）。
 *
 * <p><b>性能</b>：一次 spin-up O(NLAT×NBASIN×NDEPTH) 每步；按 (seed, config) 缓存；
 * 不参与逐点气候查询。
 */
@Frozen(reason = "重构冻结：2.5D 海洋等待 3D 海洋替换", until = "refactor: 3D ocean")
public final class ClimberOcean {

    private ClimberOcean() {}

    /** 用户 2026-10-20 裁决：默认开；当前尚未接生产消费点（零输出影响）。 */
    public static boolean CLIMBER_OCEAN_ON = true;

    // ==================== 盆地 ====================
    public static final int BASIN_ATL = 0, BASIN_PAC = 1, BASIN_IND = 2, BASIN_SO = 3;
    public static final int NBASIN = 4;

    // ==================== 网格 ====================
    public static final int NLAT = 18;
    public static final double DLAT_DEG = 10.0;
    public static final int NDEPTH = 15;
    /** Stocker 1992 Table 1：层底深度 [m]。 */
    public static final double[] Z_EDGE = {
        0.0, 50.0, 100.0, 150.0, 250.0, 500.0, 750.0, 1000.0,
        1500.0, 2000.0, 2500.0, 3000.0, 3500.0, 4000.0, 4500.0, 5000.0
    };

    // ==================== Stocker 1992 Table 1 ====================
    public static final double RHO_REF = 1027.8;
    public static final double G = 9.81;
    public static final double A_MOM = 1.0e-4;
    public static final double K_H = 1.0e3;
    public static final double K_V = 1.0e-4;

    /**
     * ★ 2026-10-20：是否用 **Bryan & Lewis (1979)** 的空间变化扩散（默认 true；false = 常数 Table 1）。
     * <p>逐字来源：MOM `blmixi.F`（GEOMAR 存档）——
     * <pre>
     *   data afkph, dfkph, sfkph, zfkph /0.8, 1.05, 4.5e-5, 2500.0e2/
     *   Ahv(k) = afkph + (dfkph/pi) * atan(sfkph*(zw(k) - zfkph))     [cm²/s]
     *   Ahh(k) = (1000 + (5000-1000)*exp(-zt/50000)) * 1.0e4         [cm²/s]
     * </pre>
     * ⟹ K_V: 0.3→1.3 cm²/s（2500 m 交叉）；K_H: 5000→1000 m²/s（500 m 尺度）。
     * <p>⚠ 当前常数 K_H=1e3 是 Table 1 的**深层值**，与论文“vary following Bryan-Lewis”有偏差。
     */
    public static boolean BL_DIFFUSION = false;   // 2026-10-20 A/B：BL 剖面开 ⟹ 雪球（表面 K_V 3e-5 脱耦），回退常数 Table 1
    /** 诊断用：K_H 的乘数（默认 1；只用于机制实验，不是生产参数）。 */
    public static double K_H_MUL = 1.0;

    /** Bryan-Lewis 垂直扩散（m²/s），z 为深度（m，正=向下）。 */
    public static double blKv(double zM) {
        return (0.8 + (1.05 / Math.PI) * Math.atan(4.5e-5 * (zM * 100.0 - 2500.0e2))) * 1.0e-4;
    }

    /** Bryan-Lewis 水平扩散（m²/s），z 为深度（m，正=向下）。 */
    public static double blKh(double zM) {
        return 1000.0 + 4000.0 * Math.exp(-zM / 500.0);
    }
    public static final double EPS0 = 0.3;
    public static final double H_OCEAN = 5000.0;
    public static final double H_MIXED = 50.0;
    public static final double TAU_SURF = 50.0 * 86400.0;
    public static final double C_OCEAN = 3900.0;
    public static final double EMISS_OCEAN = 0.96;

    /** 线性状态方程系数：α=1.67e-4 K⁻¹、β=7.80e-4 psu⁻¹（Vallis 2017 Table 1.2；本仓 Thermohaline 同源）。 */
    public static final double ALPHA = 1.67e-4;
    public static final double BETA = 7.80e-4;
    public static final double T_REF = 288.0;
    public static final double S_REF = 35.0;
    public static final double S_CONST = 35.0;

    /** 海冰：0 °C 出现、−1.8 °C 全覆盖（Petoukhov et al. 2000 §5），冰反照率 0.62（Table 1 AI）。 */
    public static final double T_ICE0 = 273.15;
    public static final double T_FREEZE = 271.35;
    public static final double ALB_ICE = 0.62;

    /** 世界半径与角速度（Table 1 的 a、Ω 换成本世界）。 */
    public static final double R = WorldContract.R_EFF;
    public static final double OMEGA = WorldContract.OMEGA;

    /** 时间步长（秒）；默认 1 天。窄盆地 ε 大，显式平流需要更小步长（见 spin-up 诊断）。 */
    public static double DT_SEC = 86400.0;

    // ==================== 状态 ====================
    /** 温度 T[basin][lat][depth]（K）。 */
    static double[][][] T;
    /** 盐度（psu），本版固定。 */
    static double[][][] S;
    /** 诊断速度（m/s）。 */
    static double[][][] U, V, W;
    /** 缓存的海表温度（K）[lat]。 */
    static double[] sstCache;

    // 复用缓冲（避免每步分配）
    private static final double[][][] P = new double[NBASIN][NLAT][NDEPTH];
    private static final double[][] DT = new double[NLAT][NDEPTH];
    private static final double[][] DS = new double[NLAT][NDEPTH];
    private static final double[] DZ = layerThickness();
    /** 各纬带 E-P（m/s，正=蒸发）；null = 无淡水通量。 */
    private static double[] EMP = null;
    /**
     * ★ 2026-10-20（MOC 实验）：海表盐度恢复（论文 eq.14 的形式）——`dS += (S_REF − S)/τ_S`。
     * 论文的 S* 是 Levitus 观测；本世界用**世界常数 S_REF=35 psu**（海洋平均盐度），
     * 目的是让高纬冷水变密、形成深水。默认 false（用模型自己的 E−P）。
     */
    public static boolean S_RESTORE = false;
    /**
     * ★ 2026-10-20（深水形成）：海冰厚度与**盐析**（Semtner 1976 的 0 层热力冰）。
     * 生长：dh = −Q_ice·dt/(ρ_i·L_f)；盐析：dS = S·(ρ_i/ρ_w)·dh/H_M。
     * 常数：ρ_i=917 kg/m³、L_f=3.34e5 J/kg（标准值）。
     */
    public static final double RHO_ICE = 917.0;
    public static final double L_FUSION = 3.34e5;
    /** 各纬带冰厚（m）。 */
    static double[] ICE_H = null;
    /** 冰厚上限（m）—— 防止盐析无界增长（物理海冰 ~3–5 m）。 */
    public static final double ICE_MAX = 3.0;
    /** 冰的热传导率（W/m/K）—— Semtner (1976) 的标准值。 */
    public static final double K_ICE = 2.0;
    /** 旧的逐脚步进盐析是否启用（false = 用 updateFlux 的年步长 Semtner 冰）。 */
    public static boolean ICE_STEP_GROWTH = false;
    /** 诊断：盐析块被执行的次数与累计冰增长（m）。 */
    public static long brineCalls = 0;
    public static double brineAccum = 0.0;
    /**
     * ★ 2026-10-20（MOC 实验）：**整柱对流调整**（Marotzke 1991 式：不稳定段整段混合，而非逐层两两）。
     * false = 原来的逐层两两混合。默认 true（海洋诊断路径，不影响生产）。
     */
    public static boolean CONV_FULL_COLUMN = true;
    /** 各纬带纬向风应力 τ_x（Pa，正=向东）；null = 无风。 */
    private static double[] TAU_X = null;
    /** 各纬带海表净热通量（W/m²，正=进入海洋）；null = 用 50 天松弛（脚手架）。 */
    private static double[] QNET = null;
    /** 各纬带**冰面**净热通量（W/m²，T=T_FREEZE、反照率=冰）；用于冰厚生长/融化。 */
    private static double[] QNET_ICE = null;
    /** 季节循环：4 季通量 + 当前相位插值结果。 */
    private static double[][] QNET_S = null, QICE_S = null;
    private static double[] QNET_CUR = null, QICE_CUR = null;
    /** 交互式通量的世界参数。 */
    private static long FLUX_SEED = 0;
    private static int FLUX_CELL = 0, FLUX_GRAD = 0;
    private static double FLUX_XSPAN = 0.0;

    private static boolean inited = false;
    // ==================== 几何 ====================
    /** 纬度带中心（弧度）。 */
    public static double latRad(int i) { return Math.toRadians(-85.0 + i * DLAT_DEG); }

    /** 盆地北界（度）—— Stocker 1992：ATL 80N、PAC 55N、IND 20N。 */
    public static double basinNorthDeg(int b) {
        switch (b) {
            case BASIN_ATL: return 80.0;
            case BASIN_PAC: return 55.0;
            case BASIN_IND: return 20.0;
            default: return -40.0;   // SO 南界
        }
    }

    /** 盆地角宽（弧度）—— ATL 60°、PAC 120°、IND 65°；SO 全经度。 */
    public static double basinWidthRad(int b) {
        switch (b) {
            case BASIN_ATL: return Math.toRadians(60.0);
            case BASIN_PAC: return Math.toRadians(120.0);
            case BASIN_IND: return Math.toRadians(65.0);
            default: return 2.0 * Math.PI;
        }
    }

    /**
     * 该纬度带是否属于该盆地（含陆地掩膜）。
     * <p>★ 2026-10-20（论文几何）：三个盆地在 40S **连通南大洋**，不再有独立 SO 盆地；
     * 三个盆地都延伸到 70S（70S 以南为南极大陆），40S 以南由 {@link #mixSouthernOcean()}
     * 做瞬时纬向混合（Stocker 1992：“In the Southern Ocean, temperature and salt are
     * instantly mixed longitudinally”）。
     */
    public static boolean hasOcean(int b, int i) {
        double lat = -85.0 + i * DLAT_DEG;
        if (b == BASIN_SO) return false;      // 独立 SO 盆地已取消
        if (lat <= -70.0) return false;       // 南极大陆
        return lat <= basinNorthDeg(b);
    }

    public static double[] layerThickness() {
        double[] dz = new double[NDEPTH];
        for (int k = 0; k < NDEPTH; k++) dz[k] = Z_EDGE[k + 1] - Z_EDGE[k];
        return dz;
    }

    // ==================== 初始化 ====================
    static synchronized void init() {
        if (inited) return;
        ICE_H = new double[NLAT];
        T = new double[NBASIN][NLAT][NDEPTH];
        S = new double[NBASIN][NLAT][NDEPTH];
        U = new double[NBASIN][NLAT][NDEPTH];
        V = new double[NBASIN][NLAT][NDEPTH];
        W = new double[NBASIN][NLAT][NDEPTH];
        sstCache = new double[NLAT];
        for (int b = 0; b < NBASIN; b++) {
            for (int i = 0; i < NLAT; i++) {
                double tSfc = ClimlabEBM.tOceanK(latRad(i));
                for (int k = 0; k < NDEPTH; k++) {
                    double z = 0.5 * (Z_EDGE[k] + Z_EDGE[k + 1]);
                    // 初值：表层取当前 SST，按 6.5 K/km 递减到 2 C（只影响 spin-up 速度，不影响平衡解）
                    T[b][i][k] = Math.max(275.0, tSfc - 6.5e-3 * z);
                    S[b][i][k] = S_CONST;
                }
            }
        }
        inited = true;
    }

    /** 密度（线性 EOS；Stocker 1992 eq.(7)）。 */
    static double rho(double t, double s) {
        return RHO_REF * (1.0 - ALPHA * (t - T_REF) + BETA * (s - S_REF));
    }

    // ==================== 动量诊断 ====================
    /** 由 T/S 诊断 u、v、w（eq. 2/1/10/4）；风应力本版为 0。 */
    static void diagnose() {
        double[] dz = DZ;
        // 先算全场压力（复用 P）
        for (int b = 0; b < NBASIN; b++) {
            for (int i = 0; i < NLAT; i++) {
                if (!hasOcean(b, i)) continue;
                double acc = 0.0;
                for (int k = 0; k < NDEPTH; k++) {
                    P[b][i][k] = acc;
                    acc += rho(T[b][i][k], S[b][i][k]) * G * dz[k];
                }
            }
        }
        for (int b = 0; b < NBASIN; b++) {
            double dlam = basinWidthRad(b);
            double eps = EPS0 * Math.PI / (3.0 * dlam);   // eq.(11)
            for (int i = 0; i < NLAT; i++) {
                if (!hasOcean(b, i)) continue;
                double phi = latRad(i);
                double c = Math.max(0.05, Math.cos(phi));
                double s2 = Math.sin(2.0 * phi);
                // 3) 纬向地转 u（eq.2）：2Ω u = -(1/(ρ* a)) ∂p/∂φ
                for (int k = 0; k < NDEPTH; k++) {
                    double dpdphi = dPdPhi(P, b, i, k);
                    U[b][i][k] = -dpdphi / (2.0 * OMEGA * RHO_REF * R);
                }
                // 4) 经向：eq.(1)+(10)：v = -(ε sin2φ/(2Ω ρ* a c)) ∂p/∂φ
                for (int k = 0; k < NDEPTH; k++) {
                    double dpdphi = dPdPhi(P, b, i, k);
                    V[b][i][k] = -(eps * s2 / (2.0 * OMEGA * RHO_REF * R * c)) * dpdphi;
                }
                // 4b) 风应力（eq.1 的 τs/(ρ* H_M)）：只作用于混合层（顶层），
                //     Ekman：v_w = -τ_x/(ρ* H_M f)，f = 2Ω sinφ
                if (TAU_X != null) {
                    double f = 2.0 * OMEGA * Math.sin(phi);
                    if (Math.abs(f) > 1e-6) {
                        V[b][i][0] += -TAU_X[i] / (RHO_REF * H_MIXED * f);
                    }
                }
                // 4c) ★ eq.(12) 刚盖闭合：净北向输运为零 ⟹ 减掉深度平均的斜压修正
                //     （Wright & Stocker 1992 第 3 页：“the net north-south transport vanishes
                //      at all latitudes … determines the depth-independent contribution”）
                double qNet = 0.0, hTot = 0.0;
                for (int k = 0; k < NDEPTH; k++) { qNet += V[b][i][k] * dz[k]; hTot += dz[k]; }
                double v0 = qNet / hTot;
                for (int k = 0; k < NDEPTH; k++) V[b][i][k] -= v0;
            }
            // 5) 连续方程积分出 w（底 w=0）
            for (int i = 0; i < NLAT; i++) {
                if (!hasOcean(b, i)) continue;
                double phi = latRad(i);
                double c = Math.max(0.05, Math.cos(phi));
                double wAcc = 0.0;
                for (int k = NDEPTH - 1; k >= 0; k--) {
                    double div = (cV(b, i + 1, k) - cV(b, i - 1, k)) / (2.0 * Math.toRadians(DLAT_DEG) * R * c);
                    wAcc -= dz[k] * div;
                    W[b][i][k] = wAcc;
                }
            }
        }
        // 数值护栏：速度上限（防止冰/盐析耦合下的 CFL 爆炸；物理速度远低于此）
        for (int b = 0; b < NBASIN; b++) {
            for (int i = 0; i < NLAT; i++) {
                if (!hasOcean(b, i)) continue;
                for (int k = 0; k < NDEPTH; k++) {
                    V[b][i][k] = Math.max(-0.5, Math.min(0.5, V[b][i][k]));
                    W[b][i][k] = Math.max(-0.01, Math.min(0.01, W[b][i][k]));
                }
            }
        }
    }

    /** 压力对纬度的中心差分（边界单侧）；单位 **Pa/rad**（闭合式 (10) 要求 ∂p/∂φ，不是 Pa/m）。 */
    static double dPdPhi(double[][][] p, int b, int i, int k) {
        int im = Math.max(0, i - 1), ip = Math.min(NLAT - 1, i + 1);
        if (im == ip) return 0.0;
        double pm = hasOcean(b, im) ? p[b][im][k] : p[b][i][k];
        double pp = hasOcean(b, ip) ? p[b][ip][k] : p[b][i][k];
        return (pp - pm) / ((ip - im) * Math.toRadians(DLAT_DEG));
    }

    static double cV(int b, int i, int k) {
        int ii = Math.max(0, Math.min(NLAT - 1, i));
        if (!hasOcean(b, ii)) return 0.0;
        return Math.max(0.05, Math.cos(latRad(ii))) * V[b][ii][k];
    }

    // ==================== 时间步 ====================
    /** 一步 T/S 的平流-扩散-表面通量-对流调整。dt 秒。 */
    static void stepTS(double dt) {
        double[] dz = DZ;
        double dphi = Math.toRadians(DLAT_DEG);
        double[][] dT = DT, dS = DS;
        for (int b = 0; b < NBASIN; b++) {
            for (int i = 0; i < NLAT; i++) {
                if (!hasOcean(b, i)) continue;
                double phi = latRad(i);
                for (int k = 0; k < NDEPTH; k++) {
                    double v = V[b][i][k], w = W[b][i][k];
                    // ★ Bryan-Lewis 空间变化扩散（BL_DIFFUSION=false 时退回常数 Table 1）
                    double zc = 0.5 * (Z_EDGE[k] + Z_EDGE[k + 1]);
                    double kh = (BL_DIFFUSION ? blKh(zc) : K_H) * K_H_MUL;
                    double kv = BL_DIFFUSION ? blKv(zc) : K_V;
                    int im = Math.max(0, i - 1), ip = Math.min(NLAT - 1, i + 1);
                    int km = Math.max(0, k - 1), kp = Math.min(NDEPTH - 1, k + 1);
                    // 水平平流（迎风）
                    int iu = v > 0 ? im : ip;
                    double advHT = 0.0, advHS = 0.0;
                    if (Math.abs(v) > 1e-12 && hasOcean(b, iu)) {
                        advHT = -v * (T[b][i][k] - T[b][iu][k]) / (dphi * R);
                        advHS = -v * (S[b][i][k] - S[b][iu][k]) / (dphi * R);
                    }
                    // 水平扩散
                    double tmT = hasOcean(b, im) ? T[b][im][k] : T[b][i][k];
                    double tpT = hasOcean(b, ip) ? T[b][ip][k] : T[b][i][k];
                    double tmS = hasOcean(b, im) ? S[b][im][k] : S[b][i][k];
                    double tpS = hasOcean(b, ip) ? S[b][ip][k] : S[b][i][k];
                    double difHT = kh * (tmT - 2.0 * T[b][i][k] + tpT) / (dphi * R * dphi * R);
                    double difHS = kh * (tmS - 2.0 * S[b][i][k] + tpS) / (dphi * R * dphi * R);
                    // 垂直平流（迎风）
                    int ku = w > 0 ? kp : km;
                    double advVT = 0.0, advVS = 0.0;
                    if (Math.abs(w) > 1e-14 && ku != k) {
                        advVT = -w * (T[b][i][k] - T[b][i][ku]) / dz[k];
                        advVS = -w * (S[b][i][k] - S[b][i][ku]) / dz[k];
                    }
                    // 垂直扩散
                    double difVT = 0.0, difVS = 0.0;
                    if (k > 0) {
                        double hh = 0.5 * (dz[k - 1] + dz[k]);
                        difVT += kv * (T[b][i][k - 1] - T[b][i][k]) / hh / dz[k];
                        difVS += kv * (S[b][i][k - 1] - S[b][i][k]) / hh / dz[k];
                    }
                    if (k < NDEPTH - 1) {
                        double hh = 0.5 * (dz[k] + dz[k + 1]);
                        difVT += kv * (T[b][i][k + 1] - T[b][i][k]) / hh / dz[k];
                        difVS += kv * (S[b][i][k + 1] - S[b][i][k]) / hh / dz[k];
                    }
                    dT[i][k] = advHT + advVT + difHT + difVT;
                    dS[i][k] = advHS + advVS + difHS + difVS;
                }
                // 表面：T 用净热通量（正=进入海洋）或 50 天松弛（脚手架）；S 用模型 E-P
                double[] qUse = (QNET_CUR != null) ? QNET_CUR : QNET;
                if (qUse != null) {
                    dT[i][0] += qUse[i] / (RHO_REF * C_OCEAN * H_MIXED);
                } else {
                    double tStar = ClimlabEBM.tOceanK(phi);
                    dT[i][0] += (tStar - T[b][i][0]) / TAU_SURF;
                }
                if (S_RESTORE) {
                    dS[i][0] += (S_REF - S[b][i][0]) / TAU_SURF;
                } else if (EMP != null) {
                    dS[i][0] += S[b][i][0] * EMP[i] / H_MIXED;
                }
                // ★ 盐析：到冰点就允许结冰（不要求已有冰厚），只在一个盆地上结算避免重复
                if (ICE_STEP_GROWTH && ICE_H != null && QNET_ICE != null && b == BASIN_ATL && T[b][i][0] <= T_FREEZE + 0.05) {
                    brineCalls++;
                    double qIce = (QICE_CUR != null) ? QICE_CUR[i] : QNET_ICE[i];   // W/m²，负=冷却 ⟹ 结冰
                    double dh = -qIce * dt / (RHO_ICE * L_FUSION);
                    double hNew = Math.max(0.0, Math.min(ICE_MAX, ICE_H[i] + dh));
                    double dH = hNew - ICE_H[i];
                    ICE_H[i] = hNew;
                    if (dH > 0) brineAccum += dH;
                    dS[i][0] += S[b][i][0] * (RHO_ICE / RHO_REF) * dH / H_MIXED;
                }
            }
            // 更新 + 对流调整（T、S 一起按层厚混合）
            for (int i = 0; i < NLAT; i++) {
                if (!hasOcean(b, i)) continue;
                for (int k = 0; k < NDEPTH; k++) {
                    T[b][i][k] += dt * dT[i][k];
                    S[b][i][k] = Math.max(0.0, Math.min(50.0, S[b][i][k] + dt * dS[i][k]));
                    T[b][i][k] = Math.max(250.0, Math.min(320.0, T[b][i][k]));
                }
                // 海冰：SST 不得低于冰点（结冰潜热/冰盖把表面钉在 T_FREEZE）
                if (T[b][i][0] < T_FREEZE) T[b][i][0] = T_FREEZE;
                if (CONV_FULL_COLUMN) {
                    // 整柱对流调整：找出不稳定段 [k0,k1]，按层厚混合整段，重复到稳定
                    for (int it = 0; it < 12; it++) {
                        boolean changed = false;
                        int k0 = 0;
                        while (k0 < NDEPTH - 1) {
                            int k1 = k0;
                            while (k1 < NDEPTH - 1
                                    && rho(T[b][i][k1], S[b][i][k1]) > rho(T[b][i][k1 + 1], S[b][i][k1 + 1]) + 1e-9) {
                                k1++;
                            }
                            if (k1 > k0) {
                                double mt = 0.0, ht = 0.0, st = 0.0;
                                for (int k = k0; k <= k1; k++) {
                                    mt += dz[k];
                                    ht += dz[k] * T[b][i][k];
                                    st += dz[k] * S[b][i][k];
                                }
                                double ta = ht / mt, sa = st / mt;
                                for (int k = k0; k <= k1; k++) {
                                    T[b][i][k] = ta;
                                    S[b][i][k] = sa;
                                }
                                changed = true;
                            }
                            k0 = k1 + 1;
                        }
                        if (!changed) break;
                    }
                } else {
                    for (int it = 0; it < 4; it++) {
                        for (int k = 0; k < NDEPTH - 1; k++) {
                            double r1 = rho(T[b][i][k], S[b][i][k]);
                            double r2 = rho(T[b][i][k + 1], S[b][i][k + 1]);
                            if (r1 > r2 + 1e-9) {
                                double m1 = dz[k], m2 = dz[k + 1], mt = m1 + m2;
                                double tw = (m1 * T[b][i][k] + m2 * T[b][i][k + 1]) / mt;
                                double sw = (m1 * S[b][i][k] + m2 * S[b][i][k + 1]) / mt;
                                T[b][i][k] = tw; T[b][i][k + 1] = tw;
                                S[b][i][k] = sw; S[b][i][k + 1] = sw;
                            }
                        }
                    }
                }
            }
        }
        mixSouthernOcean();
    }

    /**
     * 40S 以南三个盆地共享 T/S（瞬时的纬向混合，Stocker 1992 的南大洋处理）。
     * 每层对三个盆地取平均再写回，使大西洋/太平洋/印度洋的暖水能在 40S 以南汇入同一条通道。
     */
    private static void mixSouthernOcean() {
        for (int i = 0; i < NLAT; i++) {
            double lat = -85.0 + i * DLAT_DEG;
            if (lat >= -40.0) continue;
            for (int k = 0; k < NDEPTH; k++) {
                double ts = 0.0, ss = 0.0;
                int n = 0;
                for (int b = 0; b < BASIN_SO; b++) {
                    if (hasOcean(b, i)) { ts += T[b][i][k]; ss += S[b][i][k]; n++; }
                }
                if (n == 0) continue;
                double ta = ts / n, sa = ss / n;
                for (int b = 0; b < BASIN_SO; b++) {
                    if (hasOcean(b, i)) { T[b][i][k] = ta; S[b][i][k] = sa; }
                }
            }
        }
    }

    /** spin-up：years 年，dt 由 {@link #DT_SEC} 控制；emp = E-P（m/s，正=蒸发）。 */
    public static synchronized void spinUp(int years, double[] emp) { spinUp(years, emp, null); }

    /** spin-up：带纬向风应力 tauX（Pa，正=向东）。 */
    public static synchronized void spinUp(int years, double[] emp, double[] tauX) {
        spinUp(years, emp, tauX, null);
    }

    /** spin-up：带表面净热通量 qnet（W/m²，正=进入海洋）；null = 用 50 天松弛脚手架。 */
    public static synchronized void spinUp(int years, double[] emp, double[] tauX, double[] qnet) {
        init();
        invalidateOht37();
        EMP = emp;
        TAU_X = tauX;
        QNET = qnet;
        double dt = DT_SEC;
        long n = (long) years * 365;
        for (long s = 0; s < n; s++) {
            diagnose();
            stepTS(dt);
        }
        diagnose();
        for (int i = 0; i < NLAT; i++) sstCache[i] = hasOcean(BASIN_SO, i) ? T[BASIN_SO][i][0]
                : (hasOcean(BASIN_ATL, i) ? T[BASIN_ATL][i][0] : Double.NaN);
        SPUN_UP = true;
    }

    /** 兼容：无淡水通量、无风的 spin-up。 */
    public static synchronized void spinUp(int years) { spinUp(years, null, null); }

    /**
     * ★ 交互式通量 spin-up：每年用当前 SST 重算一次 {@code Q_net}（四季平均），
     * 让 {@code Radiation.residual} 的 SST 负反馈生效。
     */
    public static synchronized void spinUpInteractive(int years, double[] emp, double[] tauX,
                                                      long seed, int cell, int grad, double xspan) {
        init();
        invalidateOht37();
        EMP = emp;
        TAU_X = tauX;
        QNET = new double[NLAT];
        QNET_ICE = new double[NLAT];
        QNET_S = new double[4][NLAT];
        QICE_S = new double[4][NLAT];
        QNET_CUR = new double[NLAT];
        QICE_CUR = new double[NLAT];
        FLUX_SEED = seed;
        FLUX_CELL = cell;
        FLUX_GRAD = grad;
        FLUX_XSPAN = xspan;
        double dt = DT_SEC;
        // ★ 步数必须按 dt 换算：years 年 = years*365/dtDays 步（此前漏了 /dtDays ⟹ 实际只跑了 dtDays 倍的年数）
        double dtDays = Math.max(1e-6, DT_SEC / 86400.0);
        long n = (long) Math.round(years * 365.0 / dtDays);
        long fluxEvery = Math.max(1L, Math.round(365.0 / dtDays));   // 每年更新一次通量
        double tDay = 0.0;
        for (long s = 0; s < n; s++) {
            if (s % fluxEvery == 0L) updateFlux();
            tDay += dtDays;
            applySeason(2.0 * Math.PI * (tDay % 365.25) / 365.25);
            diagnose();
            stepTS(dt);
        }
        diagnose();
        for (int i = 0; i < NLAT; i++) sstCache[i] = hasOcean(BASIN_SO, i) ? T[BASIN_SO][i][0]
                : (hasOcean(BASIN_ATL, i) ? T[BASIN_ATL][i][0] : Double.NaN);
        SPUN_UP = true;
    }

    /** 海冰密集度（0..1）：0 °C 出现、−1.8 °C 全覆盖，线性。 */
    public static double iceFraction(double t) {
        if (t >= T_ICE0) return 0.0;
        if (t <= T_FREEZE) return 1.0;
        return (T_ICE0 - t) / (T_ICE0 - T_FREEZE);
    }

    /** 诊断出口：某纬带的海冰密集度（取 SO/ATL 表层温度）。 */
    public static double iceFrac(int i) {
        init();
        double sst = hasOcean(BASIN_SO, i) ? T[BASIN_SO][i][0]
                : (hasOcean(BASIN_ATL, i) ? T[BASIN_ATL][i][0] : T_ICE0);
        return iceFraction(sst);
    }

    /**
     * ★ 2026-10-20（Semtner 1976 0 层冰）：解冰面温度 T_s，使
     * <pre>  q_surf(T_s) + k_i(T_f − T_s)/h = 0 </pre>
     * 返回 T_s；若 T_f 处 q_surf > 0（融化），返回 T_f。
     */
    private static double iceSurfaceT(int i, double h, double theta) {
        double tf = T_FREEZE;
        double qf = OceanForcing.surfaceFluxWm2(FLUX_SEED, FLUX_CELL, FLUX_GRAD, FLUX_XSPAN, i, theta, tf, 1.0);
        if (qf > 0.0) return tf;
        double lo = tf - 40.0, hi = tf;
        for (int it = 0; it < 24; it++) {
            double mid = 0.5 * (lo + hi);
            double q = OceanForcing.surfaceFluxWm2(FLUX_SEED, FLUX_CELL, FLUX_GRAD, FLUX_XSPAN, i, theta, mid, 1.0);
            double f = q + K_ICE * (tf - mid) / Math.max(0.05, h);
            if (f > 0.0) lo = mid; else hi = mid;
        }
        return 0.5 * (lo + hi);
    }

    /** 年步长的冰生长/融化 + 盐析（4 季累加）。 */
    private static void updateIce() {
        if (ICE_H == null) return;
        double seasonSec = 365.25 / 4.0 * 86400.0;
        for (int i = 0; i < NLAT; i++) {
            if (!hasOcean(BASIN_ATL, i)) continue;
            double h = ICE_H[i];
            double annual = 0.0;
            for (int s = 0; s < 4; s++) {
                double theta = s * Math.PI / 2.0;
                double ts, q;
                double qf = OceanForcing.surfaceFluxWm2(FLUX_SEED, FLUX_CELL, FLUX_GRAD, FLUX_XSPAN, i, theta, T_FREEZE, 1.0);
                if (qf > 0.0) {
                    ts = T_FREEZE;
                    q = qf;
                } else {
                    ts = iceSurfaceT(i, h, theta);
                    q = OceanForcing.surfaceFluxWm2(FLUX_SEED, FLUX_CELL, FLUX_GRAD, FLUX_XSPAN, i, theta, ts, 1.0);
                }
                annual += -q * seasonSec / (RHO_ICE * L_FUSION);
            }
            double hNew = Math.max(0.0, Math.min(ICE_MAX, h + annual));
            double dH = hNew - h;
            if (dH != 0.0) {
                ICE_H[i] = hNew;
                double s0 = S[BASIN_ATL][i][0];
                double dS = s0 * (RHO_ICE / RHO_REF) * dH / H_MIXED;
                S[BASIN_ATL][i][0] = Math.max(0.0, Math.min(50.0, s0 + dS));
            }
        }
    }
    private static void updateFlux() {
        double[] th = {0.0, Math.PI / 2.0, Math.PI, 3.0 * Math.PI / 2.0};
        for (int i = 0; i < NLAT; i++) {
            double sst = hasOcean(BASIN_SO, i) ? T[BASIN_SO][i][0]
                    : (hasOcean(BASIN_ATL, i) ? T[BASIN_ATL][i][0] : ClimlabEBM.tOceanK(latRad(i)));
            double f = iceFraction(sst);
            double q = 0.0, qi = 0.0;
            for (int s = 0; s < 4; s++) {
                double qs = OceanForcing.surfaceFluxWm2(FLUX_SEED, FLUX_CELL, FLUX_GRAD, FLUX_XSPAN, i, th[s], sst, f);
                double qis = OceanForcing.surfaceFluxWm2(FLUX_SEED, FLUX_CELL, FLUX_GRAD, FLUX_XSPAN, i, th[s], T_FREEZE, 1.0);
                if (QNET_S != null) QNET_S[s][i] = qs;
                if (QICE_S != null) QICE_S[s][i] = qis;
                q += qs;
                qi += qis;
            }
            QNET[i] = q / 4.0;
            QNET_ICE[i] = qi / 4.0;
        }
        updateIce();
    }

    /**
     * ★ 2026-10-20（季节循环）：按相位把 4 季通量插值到当前时刻（一阶谐波）。
     * 冰用当季 Q_ice（冬负→生长、夏正→融化），解决"年均通量导致冰单调增长"的问题。
     */
    private static void applySeason(double theta) {
        if (QNET_S == null || QNET_CUR == null) return;
        double c = Math.cos(theta), s = Math.sin(theta);
        for (int i = 0; i < NLAT; i++) {
            double q0 = QNET_S[0][i], q1 = QNET_S[1][i], q2 = QNET_S[2][i], q3 = QNET_S[3][i];
            QNET_CUR[i] = 0.25 * (q0 + q1 + q2 + q3) + 0.5 * (q0 - q2) * c + 0.5 * (q1 - q3) * s;
            if (QICE_S != null && QICE_CUR != null) {
                double i0 = QICE_S[0][i], i1 = QICE_S[1][i], i2 = QICE_S[2][i], i3 = QICE_S[3][i];
                QICE_CUR[i] = 0.25 * (i0 + i1 + i2 + i3) + 0.5 * (i0 - i2) * c + 0.5 * (i1 - i3) * s;
            }
        }
    }

    /** 纬向平均 SST（K）—— 诊断出口。 */
    public static double sst(int i) { init(); return sstCache == null ? Double.NaN : sstCache[i]; }

    /**
     * 经向热输运 OHT(φ) [W]，正 = 向北：
     * OHT = Σ_basin (width*a) Σ_k ρ cp v T dz（Stocker 1992 的 zonal integral）。
     */
    public static double oht(int i) { return ohtAdv(i) + ohtDif(i); }

    /** 平流部分：Σ_basin width Σ_k ρ cp v (T−273.15) dz。 */
    public static double ohtAdv(int i) {
        init();
        double[] dz = DZ;
        double sum = 0.0;
        for (int b = 0; b < NBASIN; b++) {
            if (!hasOcean(b, i)) continue;
            double width = basinWidthRad(b) * R;
            for (int k = 0; k < NDEPTH; k++) {
                double r = rho(T[b][i][k], S[b][i][k]);
                sum += width * r * C_OCEAN * V[b][i][k] * (T[b][i][k] - 273.15) * dz[k];
            }
        }
        return sum;
    }

    /** 扩散部分：Σ_basin width Σ_k (−ρ cp K_H ∂T/∂y) dz（eq.5 的水平扩散项）。 */
    public static double ohtDif(int i) {
        init();
        double[] dz = DZ;
        double dphi = Math.toRadians(DLAT_DEG);
        double sum = 0.0;
        int im = Math.max(0, i - 1), ip = Math.min(NLAT - 1, i + 1);
        if (im == ip) return 0.0;
        for (int b = 0; b < NBASIN; b++) {
            if (!hasOcean(b, i)) continue;
            double width = basinWidthRad(b) * R;
            for (int k = 0; k < NDEPTH; k++) {
                double r = rho(T[b][i][k], S[b][i][k]);
                double tm = hasOcean(b, im) ? T[b][im][k] : T[b][i][k];
                double tp = hasOcean(b, ip) ? T[b][ip][k] : T[b][i][k];
                double dTdy = (tp - tm) / ((ip - im) * dphi * R);
                double zc = 0.5 * (Z_EDGE[k] + Z_EDGE[k + 1]);
                double kh = (BL_DIFFUSION ? blKh(zc) : K_H) * K_H_MUL;
                sum += width * (-r * C_OCEAN * kh * dTdy) * dz[k];
            }
        }
        return sum;
    }

    /** 诊断出口：最大 |v|（m/s）。 */
    public static double maxAbsV() {
        init();
        double m = 0.0;
        for (int b = 0; b < NBASIN; b++) for (int i = 0; i < NLAT; i++) for (int k = 0; k < NDEPTH; k++)
            if (hasOcean(b, i)) m = Math.max(m, Math.abs(V[b][i][k]));
        return m;
    }

    /** 诊断出口：最大 |w|（m/s）。 */
    public static double maxAbsW() {
        init();
        double m = 0.0;
        for (int b = 0; b < NBASIN; b++) for (int i = 0; i < NLAT; i++) for (int k = 0; k < NDEPTH; k++)
            if (hasOcean(b, i)) m = Math.max(m, Math.abs(W[b][i][k]));
        return m;
    }

    /** 诊断出口：当前纬带的 Q_net（W/m²，正=进入海洋）。 */
    public static double qnet(int i) { return QNET == null ? Double.NaN : QNET[i]; }

    /** 37 点（-90..90，5°）的 OHT 缓存；由 {@link #cachedOht37()} 懒构建，spin-up 时失效。 */
    private static double[] OHT37 = null;
    /** 是否已完成至少一次 spin-up；false 时 {@link #cachedOht37()} 显式抛错（不再静默给初始态）。 */
    private static volatile boolean SPUN_UP = false;

    /** 让 OHT37 缓存失效（每次 spin-up 开始时调用）。 */
    static void invalidateOht37() { OHT37 = null; SPUN_UP = false; }

    /**
     * 37 点 OHT 剖面（W，正=向北），由 18 带（10°）线性插值到 5° 节点。
     * <p>给 {@code ClimlabEBM} 的 {@code OHT_FROM_OCEAN} 开关用；未 spin-up 时返回当前状态
     * （可能很粗），调用方必须自己保证已 spin-up。
     */
    public static synchronized double[] cachedOht37() {
        init();
        if (!SPUN_UP) {
            throw new IllegalStateException("ClimberOcean.cachedOht37: 海洋尚未 spin-up"
                + "（请先 ensureSpunUp / spinUpInteractive；OHT_FROM_OCEAN=true 时禁止用初始态）");
        }
        if (OHT37 == null) {
            OHT37 = new double[37];
            for (int k = 0; k < 37; k++) {
                double lat = -90.0 + 5.0 * k;
                double fi = (lat + 85.0) / DLAT_DEG;
                int i0 = (int) Math.floor(fi);
                double frac = fi - i0;
                if (i0 < 0) { i0 = 0; frac = 0.0; }
                if (i0 >= NLAT - 1) { i0 = NLAT - 2; frac = 1.0; }
                int i1 = i0 + 1;
                OHT37[k] = oht(i0) * (1.0 - frac) + oht(i1) * frac;
            }
        }
        return OHT37;
    }

    /**
     * ★ 2026-10-20：**世界初始化用的一次性 spin-up 入口**。
     * <p>从模型自己的大气场算 E−P、τx（四季平均），再调 {@link #spinUpInteractive}；
     * spin-up 期间临时关闭 {@code ClimlabEBM.OHT_FROM_OCEAN}（防止递归），结束后恢复并清 EBM 缓存。
     * <p>前提：世界已 {@code OceanWiring.onWorld} 并 warm；否则抛错。
     * <p>成本：300 年 @ dt=0.05 天 ≈ 50 s（一次，之后 cachedOht37 直接读缓存）。
     */
    public static synchronized void ensureSpunUp(long seed, int cell, int grad, double xspan) {
        if (SPUN_UP) return;
        boolean save = ClimlabEBM.OHT_FROM_OCEAN;
        ClimlabEBM.OHT_FROM_OCEAN = false;   // spin-up 的强迫调用 EBM 时不得递归读本海洋 OHT
        try {
            double[] seasons = {0.0, Math.PI / 2.0, Math.PI, 3.0 * Math.PI / 2.0};
            double[] emp = new double[NLAT];
            double[] tauX = new double[NLAT];
            for (int i = 0; i < NLAT; i++) {
                double latDeg = -85.0 + i * DLAT_DEG;
                int z = WorldContract.zOfLat(latDeg);
                double sp = 0, se = 0, st = 0;
                int n = 0;
                for (double th : seasons) {
                    for (int c = 0; c < 4; c++) {
                        int x = (int) Math.round((c + 0.5) * xspan / 4.0);
                        if (PlateField.isLandWithCell(x, z, seed, cell)) continue;
                        double p = PrecipField.mmPerDay(x, z, seed, cell, th, grad);
                        double e = PrecipField.DIAG.get()[16] * 86400.0;
                        double tx = Atmosphere.windStress(x, z, seed, cell, th, grad)[0];
                        sp += p; se += e; st += tx; n++;
                    }
                }
                emp[i] = n > 0 ? (se / n - sp / n) / 1000.0 / 86400.0 : 0.0;
                tauX[i] = n > 0 ? st / n : 0.0;
            }
            spinUpInteractive(300, emp, tauX, seed, cell, grad, xspan);
            ClimlabEBM.resetOhtCache();
        } finally {
            ClimlabEBM.OHT_FROM_OCEAN = save;
        }
    }

    /**
     * ★ 2026-10-20（海洋偏差诊断）：某（盆地, 纬度带）的**柱积分热收支** [W/m²]。
     * <pre>
     *   adv = Σ_k ρ cp (advH + advV) dz      （经向+垂直平流）
     *   dif = Σ_k ρ cp (difH + difV) dz      （Bryan-Lewis 或常数扩散）
     *   surf= 顶层 Q_net（W/m²）
     *   resid = -(adv + dif + surf)          （稳态应≈0；非零 = 对流/存储）
     * </pre>
     * 只读，不改变任何状态。
     */
    public static double[] heatBudgetColumn(int b, int i) {
        init();
        double[] dz = DZ;
        double dphi = Math.toRadians(DLAT_DEG);
        double adv = 0.0, dif = 0.0;
        if (!hasOcean(b, i)) return new double[]{0, 0, 0, 0};
        for (int k = 0; k < NDEPTH; k++) {
            double zc = 0.5 * (Z_EDGE[k] + Z_EDGE[k + 1]);
            double kh = BL_DIFFUSION ? blKh(zc) : K_H;
            double kv = BL_DIFFUSION ? blKv(zc) : K_V;
            double v = V[b][i][k], w = W[b][i][k];
            int im = Math.max(0, i - 1), ip = Math.min(NLAT - 1, i + 1);
            int km = Math.max(0, k - 1), kp = Math.min(NDEPTH - 1, k + 1);
            int iu = v > 0 ? im : ip;
            double advH = 0.0, advV = 0.0;
            if (Math.abs(v) > 1e-12 && hasOcean(b, iu)) advH = -v * (T[b][i][k] - T[b][iu][k]) / (dphi * R);
            int ku = w > 0 ? kp : km;
            if (Math.abs(w) > 1e-14 && ku != k) advV = -w * (T[b][i][k] - T[b][i][ku]) / dz[k];
            double tmT = hasOcean(b, im) ? T[b][im][k] : T[b][i][k];
            double tpT = hasOcean(b, ip) ? T[b][ip][k] : T[b][i][k];
            double difH = kh * (tmT - 2.0 * T[b][i][k] + tpT) / (dphi * R * dphi * R);
            double difV = 0.0;
            if (k > 0) {
                double hh = 0.5 * (dz[k - 1] + dz[k]);
                difV += kv * (T[b][i][k - 1] - T[b][i][k]) / hh / dz[k];
            }
            if (k < NDEPTH - 1) {
                double hh = 0.5 * (dz[k] + dz[k + 1]);
                difV += kv * (T[b][i][k + 1] - T[b][i][k]) / hh / dz[k];
            }
            double r = rho(T[b][i][k], S[b][i][k]);
            adv += (advH + advV) * r * C_OCEAN * dz[k];
            dif += (difH + difV) * r * C_OCEAN * dz[k];
        }
        double surf = (QNET != null) ? QNET[i] : 0.0;
        return new double[]{adv, dif, surf, -(adv + dif + surf)};
    }

    /**
     * ★ 2026-10-20（海洋偏差诊断）：某纬带的**经向翻转强度**（Sv）。
     * <p>不能用 Σ_k v dz（刚盖修正让它恒为 0）；应取**从底向上的累积输运**的最大值：
     * Ψ(φ,z) = Σ_{k'=bottom..k} v dz'，MOC = max_z |Σ_basin width·a·Ψ|。
     */
    public static double mocSv(int i) {
        init();
        double best = 0.0;
        for (int b = 0; b < BASIN_SO; b++) {
            if (!hasOcean(b, i)) continue;
            double width = basinWidthRad(b) * R;
            double psi = 0.0;
            for (int k = NDEPTH - 1; k >= 0; k--) {
                psi += V[b][i][k] * DZ[k];
                best = Math.max(best, Math.abs(psi) * width);
            }
        }
        return best / 1e6;
    }

    /**
     * ★ 2026-10-20（海洋偏差诊断）：相邻纬带在给定层的 {ΔT(K), Δρ(kg/m³)}（用正中间的盆地）。
     */
    public static double[] gradAt(int i, int k) {
        init();
        int im = Math.max(0, i - 1), ip = Math.min(NLAT - 1, i + 1);
        for (int b = 0; b < BASIN_SO; b++) {
            if (hasOcean(b, i) && hasOcean(b, im) && hasOcean(b, ip)) {
                double tm = T[b][im][k], tp = T[b][ip][k];
                double rm = rho(T[b][im][k], S[b][im][k]);
                double rp = rho(T[b][ip][k], S[b][ip][k]);
                return new double[]{tp - tm, rp - rm};
            }
        }
        return new double[]{Double.NaN, Double.NaN};
    }

    /** 诊断出口：某纬带冰面净通量（W/m²）。 */
    public static double qnetIce(int i) { return QNET_ICE == null ? Double.NaN : QNET_ICE[i]; }

    /** 诊断出口：某纬带的冰厚（m）。 */
    public static double iceThickness(int i) { init(); return ICE_H == null ? 0.0 : ICE_H[i]; }

    /** 诊断出口：某纬带表层盐度（psu，取第一个有海洋的盆地）。 */
    public static double surfaceSalinity(int i) {
        init();
        for (int b = 0; b < BASIN_SO; b++) if (hasOcean(b, i)) return S[b][i][0];
        return Double.NaN;
    }

    /** 配置指纹。 */
    public static long configStamp() {
        long h = 0x0CEA11L;
        h = h * 31 + (CLIMBER_OCEAN_ON ? 1 : 0);
        h = h * 31 + NLAT;
        h = h * 31 + NDEPTH;
        h = h * 31 + NBASIN;
        h = h * 31 + Double.doubleToLongBits(K_H);
        h = h * 31 + Double.doubleToLongBits(K_V);
        h = h * 31 + Double.doubleToLongBits(EPS0);
        return h;
    }
}
