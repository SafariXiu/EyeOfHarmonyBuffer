package probe;

import com.EyeOfHarmonyBuffer.sim.ocean.CoastalLayer;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P450：验证 P0-b 的两条修复（D33 最小二乘斜率、D34 采样步长不变性）。
 *
 * <h3>为什么要「反向对照」</h3>
 * 一条断言如果永远为绿，它就没有证明任何事情。所以两条检查都同时跑**改前的公式**：
 *   - D33：改前应给出 slope_code / slope_true == 1/3（红），改后应给出 1（绿）；
 *   - D34：改前的定点应随 ds 单调上升（红），改后应三者相等（绿）。
 * 反向对照是在本文件里**逐字复刻改前那两行**，不是调用生产代码（生产已经改好了）。
 */
public class P450 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static int nPass = 0, nFail = 0;

    static void say(String s) { rep.println("[P450] " + s); System.out.println("[P450] " + s); }
    static void chk(boolean ok, String what, String detail) {
        if (ok) nPass++; else nFail++;
        say(String.format(LF, "  [%s] %-46s %s", ok ? "PASS" : "**FAIL**", what, detail));
    }

    /** 复刻 CoastalLayer.coastTangent 的最小二乘（fixed = 改后公式，否则改前公式）。 */
    static double[] slope(double[] xs, int DZ, int N, boolean fixed) {
        int n = 2 * N + 1;
        double sx = 0, sz = 0;
        for (int m = -N; m <= N; m++) { sx += xs[m + N]; sz += m * (double) DZ; }
        sx /= n; sz /= n;
        double sxy = 0, szz = 0;
        for (int k = 0; k < n; k++) {
            double dz = fixed ? ((k - N) * (double) DZ - sz) : (k * (double) DZ - sz);
            sxy += (xs[k] - sx) * dz;
            szz += dz * dz;
        }
        return new double[]{sxy / szz, szz};
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p450_report.txt"), "UTF-8");
        say("P450：P0-b 修复验证（D33 / D34）");
        say("");

        say("A. D33 最小二乘斜率：合成海岸 xs[k] = X0 + c*(k-N)*DZ，应解出 slope == c");
        int DZ = CoastalLayer.COAST_TAN_DZ, N = CoastalLayer.COAST_TAN_N;
        double[] cs = {0.5, 1.0, Math.sqrt(3.0), 3.0, 10.0};
        say(String.format(LF, "  DZ=%d  N=%d  n=%d   分母应为 10*DZ^2 = %.6e", DZ, N, 2 * N + 1, 10.0 * DZ * DZ));
        say(String.format(LF, "  %-10s %14s %14s %14s %14s", "c(真)", "slope 改前", "slope 改后", "改前/真", "改后/真"));
        boolean allFixed = true, allOldThird = true;
        for (double c : cs) {
            double[] xs = new double[2 * N + 1];
            for (int k = 0; k < 2 * N + 1; k++) xs[k] = 1_000_000.0 + c * (k - N) * DZ;
            double sOld = slope(xs, DZ, N, false)[0], sNew = slope(xs, DZ, N, true)[0];
            say(String.format(LF, "  %-10.4f %14.6f %14.6f %14.6f %14.6f", c, sOld, sNew, sOld / c, sNew / c));
            if (Math.abs(sNew / c - 1.0) > 1e-9) allFixed = false;
            if (Math.abs(sOld / c - 1.0 / 3.0) > 1e-9) allOldThird = false;
        }
        chk(allFixed, "D33 改后 slope 精确等于真值（5 个 c）", "");
        chk(allOldThird, "反向对照：改前 slope 恒为真值的 1/3", "（这条能红 ⇒ 上面那条不是空断言）");
        double[] xs0 = new double[2 * N + 1];
        for (int k = 0; k < 2 * N + 1; k++) xs0[k] = k * (double) DZ;
        say(String.format(LF, "  分母实测：改前 szz = %.6e   改后 szz = %.6e", slope(xs0, DZ, N, false)[1], slope(xs0, DZ, N, true)[1]));
        say("");

        say("B. D34 steadyPeriodic 的采样步长不变性（常值 tau）");
        double tau = 0.1;
        double analytic = tau * CoastalLayer.L_RELAX / (CoastalLayer.RHO * CoastalLayer.G_PRIME * CoastalLayer.H_THERMOCLINE);
        say(String.format(LF, "  解析定点 tau*L/(rho*g'*H1) = %.4f m", analytic));
        say(String.format(LF, "  %-12s %16s %16s %12s", "ds (m)", "改前定点 (m)", "改后定点 (m)", "改后/解析"));
        double[] dss = {20_000, 100_000, 500_000, 1_000_000, 2_000_000};
        double minNew = 1e30, maxNew = -1e30;
        for (double ds : dss) {
            int n = 400;
            double[] t = new double[n];
            for (int i = 0; i < n; i++) t[i] = tau;
            // 改后的生产函数
            double hNew = CoastalLayer.steadyPeriodic(t, ds)[n - 1];
            // 逐字复刻改前的一步递推（k = ds/(rho g' H1)）
            double a = Math.exp(-ds / CoastalLayer.L_RELAX);
            double kOld = ds / (CoastalLayer.RHO * CoastalLayer.G_PRIME * CoastalLayer.H_THERMOCLINE);
            double acc = 0;
            for (int p = 0; p < 4; p++) for (int i = 0; i < n; i++) { acc = acc * a + t[i] * kOld; }
            double hOld = acc;
            say(String.format(LF, "  %-12.0f %16.4f %16.4f %12.5f", ds, hOld, hNew, hNew / analytic));
            minNew = Math.min(minNew, hNew); maxNew = Math.max(maxNew, hNew);
        }
        // ⚠ 判据修正（我第一次把它写成「完全相同」，那是错的）：
        // 积分器的收敛残差 = a^(passes*n)，a = exp(-ds/L_RELAX)。
        // ds=20 km、n=400、passes=4 时 a^1600 = e^{-10.67} = 2.3e-5 —— 正好是量到的那点差。
        // 所以正确的断言是「ds 不变性达到收敛残差以内」，不是「逐位相同」。
        double worstResid = 0;
        for (double ds : dss) worstResid = Math.max(worstResid, Math.pow(Math.exp(-ds / CoastalLayer.L_RELAX), 4 * 400));
        chk((maxNew - minNew) / analytic < 3.0 * worstResid,
            "D34 改后：5 个 ds 的定点在**收敛残差内**相同",
            String.format(LF, "极差 %.3e m（相对 %.2e）；残差上界 %.2e", maxNew - minNew, (maxNew - minNew) / analytic, worstResid));
        chk(Math.abs(maxNew / analytic - 1.0) < 1e-4, "D34 改后：定点 == 解析值（1e-4 内）", String.format(LF, "比值 %.9f", maxNew / analytic));
        say("");
        say("D. **D40（本轮新发现）**：passes = 4 的收敛性取决于 n·ds/L_RELAX，不是只看 passes");
        say("  rossbyRadius 那段注释写的「e^{-2*4} ≈ 0.03% ⇒ 收敛」等价于假设 a = e^{-2}（即 ds = 2*L_RELAX）。");
        say("  ds 更小时 a 更大、残差更大 ⇒ **只有当总路径 n·ds >> L_RELAX 时才真的到定点**。");
        say("  P292 的实际沿路径长度如果是上万 km（= 数倍 L_RELAX），残差 ~1e-6 级，可忽略；");
        say("  但**任何 n·ds 与 L_RELAX 同量级的用法都不收敛**，探针必须自己断言这一点。");
        say(String.format(LF, "  本跑的残差 = a^(4n)：ds=20 km 时 %.2e、ds=2000 km 时 %.2e",
            Math.pow(Math.exp(-20_000 / CoastalLayer.L_RELAX), 1600), Math.pow(Math.exp(-2_000_000 / CoastalLayer.L_RELAX), 1600)));
        say("");

        say("C. D35-b：实际平滑窗口 vs 标称");
        say(String.format(LF, "  标称 SMOOTH_KM = %.0f km", CoastalLayer.SMOOTH_KM));
        for (double ds : new double[]{20_000, 166_667, 500_000, 750_000, 1_000_000, 2_000_000}) {
            double w = CoastalLayer.smoothedWindowM(ds);
            say(String.format(LF, "    ds=%9.0f m  ⇒  实际窗口 %9.0f m  = 标称的 %.2f 倍", ds, w, w / (CoastalLayer.SMOOTH_KM * 1000.0)));
        }
        say("");
        say(String.format(LF, "  汇总：PASS %d / FAIL %d", nPass, nFail));
        rep.close();
        System.out.println("JAVA_EXIT=0");
    }
}
