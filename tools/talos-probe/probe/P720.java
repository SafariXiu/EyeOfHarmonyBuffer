package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.ParcelLift;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.StationaryWave;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P720 —— 【文献给定的加热】单独检验「季风加热 -> 定波 -> 撒哈拉下沉」这个机制
 * 在我们的求解器里到底成不成立。
 *
 * <p><b>为什么做这个</b>：StationaryWave 求解器已存在、已挂在 PrecipField 上，但 ENABLED=false。
 * 真正的缺口不是「有没有求解器」，而是「亚洲季风加热激发的定波遥相关能不能产生撒哈拉夏季下沉」。
 * 本探针【不动任何基线、不进生产路径】，只用 {@link StationaryWave#Q_OVERRIDE} 注入一个
 * 文献形状的加热，把机制单独证真/证伪。
 *
 * <p><b>文献（R&amp;H 1996/2001，本仓 research/pdfs/_report_linear_swm.md 有逐字摘录）</b>：
 * <ol>
 *   <li>理想化加热 = <b>elliptical deep-convective monsoon heating centered at 25N, 90E and
 *       400 hPa and maximizing at 5 K day^-1</b>（RH2001 §3b 逐字）。</li>
 *   <li>自带对照组：<b>下沉对加热纬度极其敏感，加热中心移到赤道附近的 10N 时可忽略</b>
 *       （"The descent was highly sensitive to the latitude of the heating, being negligible for
 *       pre-Asian monsoon heating that is centered nearer the equator at 10N."）。</li>
 *   <li>量级锚：观测下沉 0.25 hPa/h @674 hPa；R&amp;H 的季风+地形只解释<b>一半</b>。</li>
 * </ol>
 *
 * <p><b>★ 单位换算全过程（本探针的核心，必须能被别人独立复算）</b>
 * <pre>
 *  第一步：K/day -> K/s
 *     5 K/day = 5 / 86400 = 5.7870370e-5 K/s            （86400 = 1 天秒数，定义值）
 *
 *  第二步：K/s -> 求解器要的 Q（m^2/s^3）。求解器解的是【第一斜压模】：
 *     eps*Phi1 + c^2*div(V) = -Q1        （StationaryWave.resid 的实部/虚部就是这一式）
 *     把热力学方程 D(theta)'/Dt = Q_theta 投影到第一斜压模：
 *       Phi' = Phi1*cos(pi*z/H),  theta' = -(pi*theta0/(gH))*Phi1*sin(pi*z/H),
 *       w    = w1*sin(pi*z/H),    div_low = -(pi/H)*w1
 *     乘 sin(pi*z/H) 并对 z 积分（H/2 归一）得
 *       d(theta1)/dt + S*w1 = Q_hat   =>   d(Phi1)/dt + c^2*div = -Q1,
 *       c^2 = H^2*N^2/pi^2  （与 c = C_GRAV = 70 m/s 一致 => N = pi*c/H = 0.01833 1/s，是合理的对流层 N）
 *     ==>  Q = (g*H/(pi*theta0)) * Q_hat        【这就是 StationaryWave:48-53 写的那条桥，
 *                                               P720 在这里重新独立推导了一遍，逐字一致】
 *     其中 Q_hat = 加热率【峰值】(K/s)，要求垂直廓线是 sin(pi*z/H)（400 hPa 峰值 ~ 对流层中层 ✓）。
 *     取模型自己的常数：g = PrecipField.G_ACC = 9.807 m/s^2、H = Atmosphere.H_EFF = 12,000 m、
 *     theta0 = 300 K（StationaryWave javadoc 写死的参考位温；换 Atmosphere.T0=288.15 只让 Q 大 4%）
 *     => 系数 g*H/(pi*theta0) = 124.867 m^2/(s^3*K)  =>  Q_peak = 7.2262e-3 m^2/s^3
 *
 *  第三步：任务要求的交叉核对（K/day -> W/m^2 需要 cp 与气柱质量）
 *     F = cp * M * Q_hat   [W/m^2]，M = 被加热的气柱质量 [kg/m^2]
 *     (a) 走生产桥 Q = Q_WM2_TO_SW * F（3.5e-5，StationaryWave:126/541）反推 M：
 *         M = Q_peak / (Q_WM2_TO_SW * cp * Q_hat) = 7.2262e-3 / (3.5e-5 * 1004 * 5.787e-5)
 *           = 3554 kg/m^2   <=> 一层 349 hPa 厚的气柱 = 全气柱 10194 kg/m^2 的 34.9%
 *     (b) 独立路线：质量加权的 sin 廓线，rho = rho0*exp(-z/Hs)、rho0 = Atmosphere.RHO_AIR = 1.225、
 *         Hs = R_d*T0/g = 287.05*288.15/9.807 = 8432 m（R_d = ParcelLift.R_D、T0 = Atmosphere.T0）：
 *         M_sin = 4808 kg/m^2 => F = 279.3 W/m^2 => Q = 9.776e-3 m^2/s^3（比主值大 35%）
 *     两条路线相差 1.35 倍；全气柱上限 M = 10194 给 2.07e-2（2.9 倍）。
 *     ★ 求解器对 Q 严格线性（DFT -> 逐波数解 -> 逆 DFT，没有非线性项），
 *       所以这个幅度不确定度【不改变任何符号、也不改变 25N/10N 的比值】—— 下面用 ×2 实测核对。
 * </pre>
 *
 * <p><b>加热几何（哪些是文献给的、哪些是我们选的 —— 必须分清）</b>
 * <pre>
 *   文献给的：中心 (25N, 90E)、垂直峰值 400 hPa、峰值 5 K/day、形状 = 椭圆(elliptical)。
 *   我们选的（R&amp;H 原文未给尺度）：sigma_x = 3000 km（模型 x 坐标上的距离 = 27.0 度经度；
 *     亚洲季风 60-120E 的东西半宽约 30 度）、sigma_y = 1200 km（= 10.8 度纬度；
 *     季风 15-35N 的南北半宽约 10 度）。形状取椭圆高斯（"elliptical" 的最简光滑实现）。
 *   我们无法选的：垂直廓线 —— 求解器只有【一个】第一斜压模，没有独立垂直层。
 *     垂直结构由 Phi'=Phi1*cos(pi*z/H) 固定；"400 hPa 峰值" 唯一的作用是告诉我们
 *     这是【深对流加热】，因而投影到第一斜压模（这是 R&amp;H 自己的用词 deep-convective）。
 *   稳健性：sigma 与 Q 幅度各做一次扫描（sigma 是唯一没有文献依据的量）。
 * </pre>
 *
 * <p><b>★ 预登记判据（写死在代码里，不许事后改）</b>
 * 量：撒哈拉盒 (0-30E, 20-35N) 的 div(V) 盒平均（下层；div(V) = 低层散度，Randall Ch.8）。
 * 符号：下沉 <=> w &lt; 0 <=> 低层辐散 <=> div(V) &gt; 0。
 * <pre>
 *   GATE_A 符号: 25N 算例撒哈拉 div(V) &gt; 0
 *   GATE_B 对照: |A_25| / |A_10| &gt;= 3.0       （R&amp;H：10N 时"可忽略"）
 *   GATE_C 量级: A_25 &gt;= 1.0e-7 1/s
 *               （=> w_mid = -(H/pi)*div &lt;= -0.38 mm/s；锚点：R&amp;H 说他们的下沉约为观测的一半，
 *                 观测 0.25 hPa/h @674 hPa <=> 约 0.84 mm/s => 一半 = 0.42 mm/s）
 *   三条全过 => 成立；A、B 过而 C 不过 => 部分成立；否则 => 不成立。
 * </pre>
 */
public class P720 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P720] " + s); System.out.println("[P720] " + s); }

    static final double CIRC = 40_000_000.0;   // 赤道周长（= WorldContract.Z_CYCLE）
    static final double POLE = 20_000_000.0;   // 极到极（= 2*MAX_D）
    static final double TH0 = 300.0;           // 参考位温，StationaryWave javadoc
    static final double PEAK_KDAY = 5.0;       // R&H 原文
    static final double QHAT = PEAK_KDAY / 86400.0;

    static int ROWS, NX;
    static double DXM, DYM;

    // ---- 加热尺度（我们选的，见类注释） ----
    static double SIG_X_KM = 3000.0;
    static double SIG_Y_KM = 1200.0;

    // ---- 盒子（lonLo, lonHi, latLo, latHi；上端开区间，格心口径） ----
    static final double[][] BOX = {
        {   0,  30, 20, 35},   // 0 撒哈拉（任务指定）
        {  20,  40, 30, 45},   // 1 东地中海
        {  45,  75, 15, 35},   // 2 阿拉伯（加热区与撒哈拉之间，同纬度带）
        {  75, 105, 15, 35},   // 3 亚洲季风区（= 加热中心所在）
        {   0,  60,-10, 10},   // 4 加热西侧的赤道带
        {   0,  30,  5, 15},   // 5 非洲 10N 带
        { 330, 360, 20, 35},   // 6 撒哈拉以西（大西洋东岸）
        {-180, 180, 15, 35},   // 7 北半球副热带整圈（同纬度带）
        { 150, 270, 20, 35},   // 8 ★事后诊断（首轮跑完后追加，不参与预登记判定）：同纬度带的【背面】远场
        {  30,  60, 20, 35},   // 9 ★事后诊断：埃及/红海（紧邻撒哈拉以东）
    };
    static final String[] BOXN = {"撒哈拉 0-30E/20-35N", "东地中海 20-40E/30-45N", "阿拉伯 45-75E/15-35N",
                                  "季风区 75-105E/15-35N", "赤道带 0-60E/10S-10N", "非洲10N 0-30E/5-15N",
                                  "大西洋东 330-360E/20-35N", "副热带整圈 15-35N",
                                  "远场(背面) 150-270E/20-35N", "埃及红海 30-60E/20-35N"};

    // ---- 预登记判据 ----
    static final double RATIO_MIN = 3.0;
    static final double DIV_FLOOR = 1.0e-7;

    static double latOfRow(int j) { return -90.0 + 180.0 * j / (ROWS - 1); }
    static double lonOfCol(int i) { return 360.0 * i / NX; }
    static int rowOfLat(double lat) { return (int) Math.round((lat + 90.0) / 180.0 * (ROWS - 1)); }

    /** 椭圆高斯加热（R&H 只说 elliptical；尺度见类注释）。单位 m^2/s^3。 */
    static double[][] heater(double latC, double lonC, double qPeak, double sxKm, double syKm) {
        double[][] q = new double[ROWS][NX];
        int j0 = rowOfLat(latC);
        int i0 = (int) Math.round(lonC / 360.0 * NX);
        for (int j = 1; j < ROWS - 1; j++) {
            double dy = (j - j0) * DYM;
            for (int i = 0; i < NX; i++) {
                double di = i - i0;
                if (di > NX / 2.0) di -= NX;
                if (di < -NX / 2.0) di += NX;
                double dx = di * DXM;
                q[j][i] = qPeak * Math.exp(-0.5 * (dx * dx / (sxKm * 1000.0 * sxKm * 1000.0)
                                                 + dy * dy / (syKm * 1000.0 * syKm * 1000.0)));
            }
        }
        return q;
    }

    static void setDampingDays(double days) { StationaryWave.DAMPING = 1.0 / (days * 86400.0); }

    /** 解一次并取场。返回 {div[], q[], p[]}。 */
    static double[][] solve(double[][] q) {
        StationaryWave.Q_OVERRIDE = q;
        StationaryWave.ZERO_F = false;          // f = 2*Omega*sin(phi)：beta 效应是机制本身，必须开
        StationaryWave.ENABLED = true;
        StationaryWave.invalidate();            // ★ Q_OVERRIDE 不在 stamp() 里 => 必须手动作废缓存
        StationaryWave.ensureSolved(12345L, 0, 0.0, 500_000);
        return new double[][]{ StationaryWave.divGridCopy(), StationaryWave.qGridCopy(), StationaryWave.pGridCopy() };
    }

    /** 盒统计：{divAt均值, 网格原值均值, min, max, 点数, |div|最大}。 */
    static double[] boxStat(double[] div, double lonLo, double lonHi, double latLo, double latHi) {
        double s1 = 0, s2 = 0, mn = Double.MAX_VALUE, mx = -Double.MAX_VALUE, amx = 0;
        int n = 0;
        for (int j = 1; j < ROWS - 1; j++) {
            double lat = latOfRow(j);
            if (lat < latLo || lat >= latHi) continue;
            int z = WorldContract.zOfLat(lat);
            for (int i = 0; i < NX; i++) {
                double lon = lonOfCol(i);
                if (lon < lonLo || lon >= lonHi) continue;
                int x = (int) Math.round(CIRC * i / NX);
                double viaAt = StationaryWave.divAt(x, z);
                double raw = div[j * NX + i];
                s1 += viaAt; s2 += raw;
                if (raw < mn) mn = raw;
                if (raw > mx) mx = raw;
                if (Math.abs(raw) > amx) amx = Math.abs(raw);
                n++;
            }
        }
        if (n == 0) return new double[]{0, 0, 0, 0, 0, 0};
        return new double[]{s1 / n, s2 / n, mn, mx, n, amx};
    }

    static double wMid(double div) { return -(Atmosphere.H_EFF / Math.PI) * div; }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p720_report.txt"), "UTF-8");
        ROWS = StationaryWave.NPHI + 2;
        NX = StationaryWave.NX;
        DXM = CIRC / NX;
        DYM = POLE / (ROWS - 1);
        double dampSaved = StationaryWave.DAMPING;
        boolean fSaved = StationaryWave.ZERO_F;
        boolean eSaved = StationaryWave.ENABLED;
        double[][] qSaved = StationaryWave.Q_OVERRIDE;

        say("P720: 文献加热单独检验「季风加热 -> 定波 -> 撒哈拉下沉」");
        say("");
        say("[A] 求解器与加热几何");
        say(String.format(LF, "  网格 rows=%d NX=%d  dx=%.1f km  dy=%.1f km  c=C_GRAV=%.0f m/s  N=pi*c/H=%.5f 1/s",
            ROWS, NX, DXM / 1e3, DYM / 1e3, StationaryWave.C_GRAV,
            Math.PI * StationaryWave.C_GRAV / Atmosphere.H_EFF));
        say(String.format(LF, "  阻尼 DAMPING=1/(%.2f day)=%.4e 1/s（模型默认 1.5 天；R&H 用牛顿冷却 25 天、边界层 5 天）",
            1.0 / (StationaryWave.DAMPING * 86400.0), StationaryWave.DAMPING));
        say(String.format(LF, "  基本态纬向风 Uy = 0（求解器写死：ensureSolved 里 Uy[j]=0.0）"
            + " —— R&H 的机制描述依赖\"中纬西风的相互作用\"，我们没有那支西风，这是本实验的结构性前提"));
        int j25 = rowOfLat(25.0), j10 = rowOfLat(10.0), jq = 16;
        say(String.format(LF, "  行->纬度：j=%d => %.3fN（25N 目标）  j=%d => %.3fN（10N 目标）",
            j25, latOfRow(j25), j10, latOfRow(j10)));
        int iFirst = -1, iLast = -1;
        for (int i = 0; i < NX; i++) {
            double lo = lonOfCol(i);
            if (lo >= 0.0 && lo < 30.0) { if (iFirst < 0) iFirst = i; iLast = i; }
        }
        say(String.format(LF, "  列->经度：i=%d => %.3fE（90E 目标）；撒哈拉 0-30E = i %d..%d（%d 列，格心 %.3f..%.3fE）",
            jq, lonOfCol(jq), iFirst, iLast, iLast - iFirst + 1, lonOfCol(iFirst), lonOfCol(iLast)));
        say(String.format(LF, "  加热 sigma_x=%.0f km（%.1f 度经度 = %.1f 列）  sigma_y=%.0f km（%.1f 度纬度 = %.1f 行）",
            SIG_X_KM, SIG_X_KM / (CIRC / 1e3) * 360.0, SIG_X_KM * 1e3 / DXM,
            SIG_Y_KM, SIG_Y_KM / (POLE / 1e3) * 180.0, SIG_Y_KM * 1e3 / DYM));
        say("");

        // ---------------- [B] 单位换算 ----------------
        double coeff = PrecipField.G_ACC * Atmosphere.H_EFF / (Math.PI * TH0);
        double qPeak = coeff * QHAT;
        double fWm2 = qPeak / StationaryWave.Q_WM2_TO_SW;
        double mImplied = qPeak / (StationaryWave.Q_WM2_TO_SW * RadiationCP() * QHAT);
        double mFull = 101325.0 / PrecipField.G_ACC;
        double hs = ParcelLift.R_D * Atmosphere.T0 / PrecipField.G_ACC;
        double mSin = 0;
        for (int k = 0; k < 20000; k++) {
            double z = (k + 0.5) * (Atmosphere.H_EFF / 20000.0);
            mSin += Atmosphere.RHO_AIR * Math.exp(-z / hs) * Math.sin(Math.PI * z / Atmosphere.H_EFF)
                    * (Atmosphere.H_EFF / 20000.0);
        }
        double qAlt = StationaryWave.Q_WM2_TO_SW * RadiationCP() * mSin * QHAT;
        say("[B] 单位换算（K/day -> 求解器的 Q，单位 m^2/s^3）");
        say(String.format(LF, "  5 K/day = %.6e K/s", QHAT));
        say(String.format(LF, "  Q = (g*H/(pi*theta0))*Qhat    系数 = %.3f   （g=%.3f, H=%.0f, theta0=%.1f）",
            coeff, PrecipField.G_ACC, Atmosphere.H_EFF, TH0));
        say(String.format(LF, "  ==> Q_peak = %.6e m^2/s^3   （这是本实验注入的峰值强迫）", qPeak));
        say(String.format(LF, "  交叉核对 A（走生产桥 Q_WM2_TO_SW=%.2e）：等效柱加热 F = %.1f W/m^2", 
            StationaryWave.Q_WM2_TO_SW, fWm2));
        say(String.format(LF, "     => 隐含气柱质量 M = Q/(Q_WM2_TO_SW*cp*Qhat) = %.0f kg/m^2 (= %.0f hPa 厚的气层)",
            mImplied, mImplied * PrecipField.G_ACC / 100.0));
        say(String.format(LF, "     全气柱 M = %.0f kg/m^2  => 隐含 M 占全气柱 %.1f%%", mFull, 100.0 * mImplied / mFull));
        say(String.format(LF, "  交叉核对 B（cp + 质量加权 sin 廓线）：rho0=%.3f, Hs=R_d*T0/g=%.0f m => M_sin=%.0f kg/m^2",
            Atmosphere.RHO_AIR, hs, mSin));
        say(String.format(LF, "     => Q_alt = %.6e m^2/s^3 （是主值的 %.3f 倍；两条路线差 %.0f%%）",
            qAlt, qAlt / qPeak, 100.0 * Math.abs(qAlt / qPeak - 1.0)));
        say(String.format(LF, "     全气柱上限 => Q_max = %.6e（%.2f 倍）。求解器对 Q 严格线性 => 只缩放、不改符号/比值。",
            StationaryWave.Q_WM2_TO_SW * RadiationCP() * mFull * QHAT,
            StationaryWave.Q_WM2_TO_SW * RadiationCP() * mFull * QHAT / qPeak));
        say("");

        // ---------------- 三个算例 ----------------
        say("[C] 三个算例：25N 加热 / 10N 加热 / 无加热（同一求解器、同一网格、同一阻尼）");
        double[][] q25 = heater(25.0, 90.0, qPeak, SIG_X_KM, SIG_Y_KM);
        double[][] q10 = heater(10.0, 90.0, qPeak, SIG_X_KM, SIG_Y_KM);
        double[][] q00 = new double[ROWS][NX];
        double[][] r25 = solve(q25);
        double f25 = checkForcing(q25, r25[1], "25N");
        double[][] o25 = measure(r25[0], "A 25N 5K/day");
        double[][] r10 = solve(q10);
        double f10 = checkForcing(q10, r10[1], "10N");
        double[][] o10 = measure(r10[0], "B 10N 5K/day");
        double[][] r00 = solve(q00);
        double f00 = checkForcing(q00, r00[1], "无加热");
        double[][] o00 = measure(r00[0], "C 无加热(全零强迫)");
        say(String.format(LF, "  强迫场核对（注入 Q 与求解器实际吃的 qGrid 的最大差）：25N %.3e  10N %.3e  无加热 %.3e",
            f25, f10, f00));
        say(String.format(LF, "  求解健康：残差=%.3e  强迫峰值=%.3e  solveCount=%d",
            StationaryWave.lastResidual, StationaryWave.lastForcing, StationaryWave.solveCount));
        say("");

        // ---------------- [D] 对照表 ----------------
        say("[D] 盒平均 div(V)［1/s］（下层；>0 = 低层辐散 = 下沉）。中括号是 w_mid = -(H/pi)*div［mm/s］");
        say(String.format(LF, "  %-24s %18s %18s %18s %10s %5s", "盒子", "25N 加热", "10N 加热", "无加热", "25N/10N", "点数"));
        for (int b = 0; b < BOX.length; b++) {
            double a = o25[b][1], bv = o10[b][1], c = o00[b][1];
            String ratio = Math.abs(bv) > 1e-300 ? String.format(LF, "%+.2f", a / bv) : "  n/a";
            say(String.format(LF, "  %-24s %+11.4e[%+6.3f] %+11.4e[%+6.3f] %11.4e %10s %5.0f",
                BOXN[b], a, wMid(a) * 1e3, bv, wMid(bv) * 1e3, c, ratio, o25[b][4]));
        }
        say("");
        say("  （同上，改用生产读路径 divAt(x,z) 采样，应与网格原值逐位一致 —— §548 的结论）");
        double worstDelta = 0;
        for (int b = 0; b < BOX.length; b++)
            worstDelta = Math.max(worstDelta, Math.abs(o25[b][0] - o25[b][1]));
        say(String.format(LF, "    25N 算例上两者最大差 = %.3e （判据 <1e-18）", worstDelta));
        say("");

        // ---------------- [E] 垂直结构 ----------------
        say("[E] 垂直结构：求解器只有【一个】第一斜压模，没有独立垂直层");
        say("    下层 div(V) = 求解器直接输出（Randall Ch.8：u,v 是 lower-tropospheric variables）");
        say("    中层 w_mid = -(H_EFF/pi)*div(V) = 求解器的垂直速度诊断（与 PrecipField DIAG[14] 同一式）");
        say("    上层 div_upper = -div_lower 【推导量，不是求解器输出】由 Phi'=Phi1*cos(pi*z/H) 得出");
        say(String.format(LF, "  %-24s %14s %14s %14s", "盒子", "下层 div[s^-1]", "中层 w[mm/s]", "上层 div[s^-1]"));
        for (int b : new int[]{0, 1, 2, 3, 4, 5}) {
            double a = o25[b][1];
            say(String.format(LF, "  %-24s %+14.4e %+14.4f %+14.4e", BOXN[b], a, wMid(a) * 1e3, -a));
        }
        say(String.format(LF, "  观测锚（R&H 引文）：下沉 0.25 hPa/h @674 hPa <=> w 约 -0.84 mm/s；R&H 模型只给一半 => -0.42 mm/s"));
        say("");

        // ---------------- [F] 纬度扫描 ----------------
        say("[F] 加热纬度扫描（同幅度同形状，只移纬度）—— R&H 的对照维度");
        say(String.format(LF, "  %6s %16s %16s %16s %16s", "latC", "撒哈拉", "东地中海", "阿拉伯", "季风区盒"));
        double[] latList = {10, 15, 20, 25, 30, 35};
        double sah25 = 0, sah10 = 0;
        for (double lc : latList) {
            double[][] q = heater(lc, 90.0, qPeak, SIG_X_KM, SIG_Y_KM);
            double[][] r = solve(q);
            double[] s = boxStat(r[0], BOX[0][0], BOX[0][1], BOX[0][2], BOX[0][3]);
            double[] m = boxStat(r[0], BOX[1][0], BOX[1][1], BOX[1][2], BOX[1][3]);
            double[] ar = boxStat(r[0], BOX[2][0], BOX[2][1], BOX[2][2], BOX[2][3]);
            double[] mo = boxStat(r[0], BOX[3][0], BOX[3][1], BOX[3][2], BOX[3][3]);
            if (lc == 25) sah25 = s[1];
            if (lc == 10) sah10 = s[1];
            say(String.format(LF, "  %5.0fN %+16.4e %+16.4e %+16.4e %+16.4e", lc, s[1], m[1], ar[1], mo[1]));
        }
        say("");

        // ---------------- [G] 线性核对（幅度不确定度不影响结论） ----------------
        say("[G] 线性核对：同一形状、幅度 ×2（若严格线性，所有数恰好翻倍）");
        double[][] q2 = heater(25.0, 90.0, 2.0 * qPeak, SIG_X_KM, SIG_Y_KM);
        double[][] r2 = solve(q2);
        double[] s2v = boxStat(r2[0], BOX[0][0], BOX[0][1], BOX[0][2], BOX[0][3]);
        double[] m2v = boxStat(r2[0], BOX[3][0], BOX[3][1], BOX[3][2], BOX[3][3]);
        say(String.format(LF, "  撒哈拉：1x %+.6e  ->  2x %+.6e   比值 = %.10f", sah25, s2v[1], s2v[1] / sah25));
        say(String.format(LF, "  季风区：1x %+.6e  ->  2x %+.6e", o25[3][1], m2v[1]));
        double[][] rAlt = solve(heater(25.0, 90.0, qAlt, SIG_X_KM, SIG_Y_KM));
        double[] sAlt = boxStat(rAlt[0], BOX[0][0], BOX[0][1], BOX[0][2], BOX[0][3]);
        say(String.format(LF, "  换算路线 B（Q x %.3f）的撒哈拉：%+.6e （= 主值 x %.3f）", qAlt / qPeak, sAlt[1], sAlt[1] / sah25));
        say("");

        // ---------------- [H] sigma 扫描（唯一无文献依据的量） ----------------
        say("[H] 水平尺度扫描（sigma 是【我们】选的，R&H 未给）—— 25N 加热");
        say(String.format(LF, "  %-14s %16s %16s %16s", "sigma_x/sigma_y", "撒哈拉", "东地中海", "季风区盒"));
        double[][] sigSets = {{1500, 600}, {3000, 1200}, {6000, 2400}};
        for (double[] ss : sigSets) {
            double[][] r = solve(heater(25.0, 90.0, qPeak, ss[0], ss[1]));
            double[] s = boxStat(r[0], BOX[0][0], BOX[0][1], BOX[0][2], BOX[0][3]);
            double[] m = boxStat(r[0], BOX[1][0], BOX[1][1], BOX[1][2], BOX[1][3]);
            double[] mo = boxStat(r[0], BOX[3][0], BOX[3][1], BOX[3][2], BOX[3][3]);
            say(String.format(LF, "  %5.0f/%-8.0f %+16.4e %+16.4e %+16.4e", ss[0], ss[1], s[1], m[1], mo[1]));
        }
        say("");

        // ---------------- [I] 阻尼扫描（诊断：机制传得到多远） ----------------
        say("[I] 阻尼扫描（模型默认 1.5 天；R&H 的牛顿冷却 25 天 / 边界层 5 天）—— 25N 加热");
        say(String.format(LF, "  %-10s %16s %16s %16s %16s", "DAMPING", "撒哈拉", "东地中海", "阿拉伯", "季风区盒"));
        for (double dd : new double[]{1.5, 5.0, 25.0}) {
            setDampingDays(dd);
            double[][] r = solve(heater(25.0, 90.0, qPeak, SIG_X_KM, SIG_Y_KM));
            double[] s = boxStat(r[0], BOX[0][0], BOX[0][1], BOX[0][2], BOX[0][3]);
            double[] m = boxStat(r[0], BOX[1][0], BOX[1][1], BOX[1][2], BOX[1][3]);
            double[] ar = boxStat(r[0], BOX[2][0], BOX[2][1], BOX[2][2], BOX[2][3]);
            double[] mo = boxStat(r[0], BOX[3][0], BOX[3][1], BOX[3][2], BOX[3][3]);
            say(String.format(LF, "  %5.1f day %+16.4e %+16.4e %+16.4e %+16.4e", dd, s[1], m[1], ar[1], mo[1]));
        }
        setDampingDays(1.5);
        say("");

        // ---------------- [J] 经向/纬向剖面（能量去哪了） ----------------
        solve(q25);
        double[] div25 = StationaryWave.divGridCopy();
        say("[J] 25N 算例：沿 25N 的纬向剖面（每 4 列）div(V)［1e-7 s^-1］");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < NX; i += 4)
            sb.append(String.format(LF, "%4.0fE:%+7.2f  ", lonOfCol(i), div25[j25 * NX + i] * 1e7));
        say("  " + sb);
        say("[J] 25N 算例：沿 90E（加热中心经度）的经向剖面 div(V)［1e-7 s^-1］");
        StringBuilder sb2 = new StringBuilder();
        for (int j = 1; j < ROWS - 1; j += 2)
            sb2.append(String.format(LF, "%+3.0f:%+7.2f ", latOfRow(j), div25[j * NX + jq] * 1e7));
        say("  " + sb2);
        solve(q10);
        double[] div10 = StationaryWave.divGridCopy();
        say("[J] 10N 算例：同一纬向剖面（25N 行）div(V)［1e-7 s^-1］");
        StringBuilder sb3 = new StringBuilder();
        for (int i = 0; i < NX; i += 4)
            sb3.append(String.format(LF, "%4.0fE:%+7.2f  ", lonOfCol(i), div10[j25 * NX + i] * 1e7));
        say("  " + sb3);
        say("");

        // ---------------- [L] ★事后诊断（首轮跑完后追加，不参与预登记判定） ----------------
        say("[L] ★事后诊断（首轮跑完后追加；[K] 的判据与门槛一个字都没改）—— 局地化检验");
        say("    质量守恒 => 全球 div 的面积分必须为 0 => 局地辐合【必然】在别处产生辐散。");
        say("    若撒哈拉的下沉是 R&H 的【局地 Rossby 遥相关】，它必须显著强于同纬度带【背面】的远场；");
        say("    若它只是全球补偿，则撒哈拉 ≈ 远场，且 25N/10N 只是整体缩放、图案相同。");
        say(String.format(LF, "  %-10s %16s %16s %10s %16s %16s", "算例", "撒哈拉盒", "远场盒(背面)", "撒/远", "埃及红海", "副热带整圈"));
        for (int c = 0; c < 3; c++) {
            double[][] oo = (c == 0) ? o25 : (c == 1 ? o10 : o00);
            String tg = (c == 0) ? "25N" : (c == 1 ? "10N" : "无加热");
            double sv = oo[0][1], fv = oo[8][1];
            say(String.format(LF, "  %-10s %+16.4e %+16.4e %10s %+16.4e %+16.4e", tg, sv, fv,
                Math.abs(fv) > 0 ? String.format(LF, "%.2f", sv / fv) : "n/a", oo[9][1], oo[7][1]));
        }
        say("  全局极值位置（25N 算例）：");
        say("    下沉最强 " + extreme(div25, true));
        say("    上升最强 " + extreme(div25, false));
        say("");
        say("[M] ★事后诊断：25N 与 10N 两个解【是不是同一个图案乘一个系数】？");
        double mxx = 0, myy = 0, sxy = 0, sxx = 0, syy = 0;
        int nn = 0;
        for (int j = 1; j < ROWS - 1; j++)
            for (int i = 0; i < NX; i++) { mxx += div25[j * NX + i]; myy += div10[j * NX + i]; nn++; }
        mxx /= nn; myy /= nn;
        for (int j = 1; j < ROWS - 1; j++)
            for (int i = 0; i < NX; i++) {
                double a = div25[j * NX + i] - mxx, b = div10[j * NX + i] - myy;
                sxy += a * b; sxx += a * a; syy += b * b;
            }
        double corr = sxy / Math.sqrt(sxx * syy);
        double scale = sxy / syy;
        double resid = 0;
        for (int j = 1; j < ROWS - 1; j++)
            for (int i = 0; i < NX; i++) {
                double a = div25[j * NX + i] - mxx, b = div10[j * NX + i] - myy;
                double dd = a - scale * b; resid += dd * dd;
            }
        say(String.format(LF, "  图案相关系数 corr(div25, div10) = %.6f   （1.000000 = 完全同形）", corr));
        say(String.format(LF, "  最佳比例 25N = %.6f x 10N；去掉该比例后的相对残差 = %.4f", scale, Math.sqrt(resid / sxx)));
        say(String.format(LF, "  逐盒比值（25N/10N）：撒哈拉 %.2f  东地中海 %.2f  阿拉伯 %.2f  季风区 %.2f  远场 %.2f",
            o25[0][1] / o10[0][1], o25[1][1] / o10[1][1], o25[2][1] / o10[2][1], o25[3][1] / o10[3][1], o25[8][1] / o10[8][1]));
        say("");

        // ---------------- [N] ★事后稳健性（首轮跑完后追加，不参与预登记判定） ----------------
        say("[N] ★事后稳健性（首轮跑完后追加）：GATE_B 那个 2.870 的比值有多稳？");
        say("    两个自由参数都不是文献给的：sigma（R&H 只说 elliptical）与 DAMPING（R&H 用 25 天）。");
        say("    若比值在合理范围内跨过 3.0，则【我那个门槛本身】是读数的一部分，必须报出来。");
        say(String.format(LF, "  %-14s %-10s %16s %16s %10s", "sigma_x/sigma_y", "DAMPING", "撒哈拉 25N", "撒哈拉 10N", "比值"));
        double rMin = Double.MAX_VALUE, rMax = -Double.MAX_VALUE;
        double[][] sigScan = {{1500, 600}, {3000, 1200}, {6000, 2400}, {9000, 3600}};
        for (double[] ss : sigScan) {
            for (double dd : new double[]{1.5, 5.0, 25.0}) {
                setDampingDays(dd);
                double[][] ra = solve(heater(25.0, 90.0, qPeak, ss[0], ss[1]));
                double[] sa = boxStat(ra[0], BOX[0][0], BOX[0][1], BOX[0][2], BOX[0][3]);
                double[][] rb = solve(heater(10.0, 90.0, qPeak, ss[0], ss[1]));
                double[] s10 = boxStat(rb[0], BOX[0][0], BOX[0][1], BOX[0][2], BOX[0][3]);
                double rr = sa[1] / s10[1];
                if (rr < rMin) rMin = rr;
                if (rr > rMax) rMax = rr;
                say(String.format(LF, "  %5.0f/%-8.0f %5.1f day  %+16.4e %+16.4e %10.3f", ss[0], ss[1], dd, sa[1], s10[1], rr));
            }
        }
        setDampingDays(1.5);
        say(String.format(LF, "  => 比值区间 [%.3f, %.3f]；预登记门槛 3.0 %s 该区间", rMin, rMax,
            (rMin <= 3.0 && 3.0 <= rMax) ? "落在" : "不在"));
        say(String.format(LF, "  求解成本（本轮 %d 次解）：合计 %.2f s，平均 %.0f ms/次  （生产路径按 (seed,cell,theta) 缓存）",
            StationaryWave.solveCount, StationaryWave.SOLVE_NANOS / 1e9,
            StationaryWave.SOLVE_NANOS / 1e6 / Math.max(1, StationaryWave.solveCount)));
        say("");

        // ---------------- [K] 判定（预登记） ----------------
        double a25 = o25[0][1], a10 = o10[0][1], a00 = o00[0][1];
        double e25 = o25[1][1], e10 = o10[1][1];
        boolean gA = a25 > 0;
        boolean gB = Math.abs(a10) > 0 ? Math.abs(a25) / Math.abs(a10) >= RATIO_MIN : true;
        boolean gC = a25 >= DIV_FLOOR;
        say("[K] 判定（预登记在类 javadoc 里，代码执行前已写死）");
        say(String.format(LF, "  撒哈拉盒：25N %+.6e  10N %+.6e  无加热 %+.6e", a25, a10, a00));
        say(String.format(LF, "  w_mid  ：25N %+.4f mm/s  10N %+.4f mm/s  （>0 = 上升，<0 = 下沉）", wMid(a25) * 1e3, wMid(a10) * 1e3));
        say(String.format(LF, "  GATE_A 符号（25N 撒哈拉 div>0 = 下沉）：%s", gA ? "PASS" : "FAIL"));
        say(String.format(LF, "  GATE_B 对照（|25N|/|10N| >= %.1f）：%.3f 倍 => %s", RATIO_MIN,
            Math.abs(a10) > 0 ? Math.abs(a25) / Math.abs(a10) : Double.POSITIVE_INFINITY, gB ? "PASS" : "FAIL"));
        say(String.format(LF, "  GATE_C 量级（25N 撒哈拉 div >= %.1e）：%s", DIV_FLOOR, gC ? "PASS" : "FAIL"));
        String verdict = (gA && gB && gC) ? "成立" : ((gA && gB) ? "部分成立（符号与对照都对，量级不足）" : "不成立");
        say(String.format(LF, "  VERDICT = %s", verdict));
        say(String.format(LF, "  东地中海（次判据）：25N %+.6e  10N %+.6e  w_mid 25N %+.4f mm/s  => %s",
            e25, e10, wMid(e25) * 1e3, (e25 > 0 && Math.abs(e10) > 0 && Math.abs(e25) / Math.abs(e10) >= RATIO_MIN) ? "同向成立" : "不满足"));
        say(String.format(LF, "  无加热算例的最大 |div| = %.3e（必须为 0 => 一切响应都来自注入的加热）", o00[7][5]));

        // 还原现场
        StationaryWave.DAMPING = dampSaved;
        StationaryWave.ZERO_F = fSaved;
        StationaryWave.ENABLED = eSaved;
        StationaryWave.Q_OVERRIDE = qSaved;
        StationaryWave.invalidate();
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }

    static double RadiationCP() { return 1004.0; }   // Radiation.CP

    /** 全局极值（max=true 取最大 div = 最强下沉；false 取最小 = 最强上升）及其位置。 */
    static String extreme(double[] div, boolean max) {
        int best = -1;
        double bv = max ? -Double.MAX_VALUE : Double.MAX_VALUE;
        for (int j = 1; j < ROWS - 1; j++)
            for (int i = 0; i < NX; i++) {
                double v = div[j * NX + i];
                if (max ? v > bv : v < bv) { bv = v; best = j * NX + i; }
            }
        return String.format(LF, "%+.4e 1/s @ %.1fN/%.0fE", bv, latOfRow(best / NX), lonOfCol(best % NX));
    }

    /** 核对注入的 Q 与求解器实际吃的网格（防止"探针以为自己注入了"这类假绿）。 */
    static double checkForcing(double[][] q, double[] qg, String tag) {
        double worst = 0;
        for (int j = 0; j < ROWS; j++)
            for (int i = 0; i < NX; i++) worst = Math.max(worst, Math.abs(q[j][i] - qg[j * NX + i]));
        say(String.format(LF, "  [强迫核对 %s] max|Q注入 - qGrid| = %.3e   Q中心=%.4e  Q峰值=%.4e",
            tag, worst, q[rowOfLat(25.0)][16], StationaryWave.lastForcing));
        return worst;
    }

    /** 量所有盒，返回 [box][{divAt, raw, min, max, n, |max|}]。 */
    static double[][] measure(double[] div, String tag) {
        double[][] out = new double[BOX.length][];
        for (int b = 0; b < BOX.length; b++)
            out[b] = boxStat(div, BOX[b][0], BOX[b][1], BOX[b][2], BOX[b][3]);
        return out;
    }
}
