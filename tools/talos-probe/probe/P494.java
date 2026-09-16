package probe;

import com.EyeOfHarmonyBuffer.sim.ocean.CoastalLayer;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P494：**D80 的自检** —— 海岸埃克曼响应在赤道带必须连续、奇、且在 f=0 处为 0。
 *
 * <p>被修的口径：{@code |f| < F_MIN} 时原来把 {@code |f|} <b>夹到 F_MIN 并保留符号</b>
 * ⇒ {@code f = 0} 落进「{@code f < 0.0} 为假」的分支，赤道被判成北半球。
 *
 * <p>本探针按纪律要求<b>覆盖求导/变换之后的量</b>：不只查 {@code hcLocalRaw} 的代数式，
 * 还要查**它在世界坐标 z 上的实际序列**（那才是消费者看到的东西），
 * 并逐位对照「旧公式」在同一批入参下的读数。
 */
public class P494 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static int fails = 0;
    static void say(String s) { rep.println("[P494] " + s); rep.flush(); System.out.println("[P494] " + s); System.out.flush(); }
    static void chk(boolean ok, String m) { if (!ok) fails++; say("   " + (ok ? "OK  " : "**错**") + " " + m); }

    static final double F_MIN = 1.0e-5;      // 与 CoastalLayer.F_MIN 同值（断言在 A3 里核）
    static final double RHO = 1025.0, W = 100_000.0, DAYS = 90.0;

    /** **旧公式**（改前的生产行为），逐字复刻，只用于对照。 */
    static double oldRaw(double tau, double f) {
        double fa = Math.abs(f);
        if (fa < F_MIN) fa = F_MIN;
        double fs = f < 0.0 ? -fa : fa;
        return tau * (DAYS * 86400.0) / (RHO * fs * W);
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p494_report.txt"), "UTF-8");
        say("P494：D80 自检 —— 赤道带的海岸埃克曼响应");
        say(String.format(LF, "  F_MIN(生产)=%.3e   UPWELL_SEASON_DAYS=%.0f   UPWELL_WIDTH=%.0f   RHO=%.0f",
                CoastalLayer.F_MIN, CoastalLayer.UPWELL_SEASON_DAYS, CoastalLayer.UPWELL_WIDTH, CoastalLayer.RHO));
        say(String.format(LF, "  本探针对照用的 F_MIN=%.1e 与生产相同？ %s", F_MIN,
                (F_MIN == CoastalLayer.F_MIN ? "是" : "**否**")));
        say("");

        // ---- A. 代数性质 ----
        say("A. 代数性质（τ=0.06 Pa）");
        final double tau = 0.06;
        double at0 = CoastalLayer.hcLocalRaw(tau, 0.0);
        chk(at0 == 0.0, String.format(LF, "hcLocalRaw(τ, f=0) == 0       实测 %.6e m", at0));
        double a = CoastalLayer.hcLocalRaw(tau, +F_MIN * 0.5), b = CoastalLayer.hcLocalRaw(tau, -F_MIN * 0.5);
        chk(a == -b, String.format(LF, "奇函数：raw(+F_MIN/2) = -raw(-F_MIN/2)   实测 %.6f vs %.6f", a, b));
        double a2 = CoastalLayer.hcLocalRaw(tau, +F_MIN * 0.25), b2 = CoastalLayer.hcLocalRaw(tau, -F_MIN * 0.25);
        chk(a2 == -b2, String.format(LF, "奇函数：raw(+F_MIN/4) = -raw(-F_MIN/4)   实测 %.6f vs %.6f", a2, b2));
        chk(Math.abs(a2) < Math.abs(a), "单调：|raw| 随 |f| 减小而减小（赤道处响应消失）");
        say("");

        // ---- B. 远场逐位不变 ----
        say("B. 远场（|f| >= F_MIN）必须与旧公式**逐位**相同");
        int nBit = 0, nTot = 0;
        for (double f = -1.0e-4; f <= 1.0e-4; f += 1.0e-6) {
            if (Math.abs(f) < F_MIN) continue;
            nTot++;
            if (Double.doubleToLongBits(CoastalLayer.hcLocalRaw(tau, f)) != Double.doubleToLongBits(oldRaw(tau, f))) nBit++;
        }
        chk(nBit == 0, String.format(LF, "|f| >= F_MIN 的 %d 个采样点：与旧公式不同的有 %d 个", nTot, nBit));
        say("");

        // ---- C. 世界坐标上的**实际序列**（消费者看到的东西）----
        say("C. 世界坐标 z 上的实际序列（消费者看到的就是它；τ=0.06 Pa）");
        say(String.format(LF, "   %10s %12s %14s %14s %12s", "z", "lat(deg)", "h_c 新(m)", "h_c 旧(m)", "差(m)"));
        double maxJumpNew = 0, maxJumpOld = 0, prevN = 0, prevO = 0;
        boolean first = true;
        for (int z = -6_000_000; z <= 6_000_000; z += 500_000) {
            double lat = WorldContract.latOf(z);
            double f = WorldContract.coriolis(lat);
            double hn = CoastalLayer.hcLocal(tau, f);
            double ho = 150.0 * Math.tanh(oldRaw(tau, f) / 150.0);
            if (!first) { maxJumpNew = Math.max(maxJumpNew, Math.abs(hn - prevN)); maxJumpOld = Math.max(maxJumpOld, Math.abs(ho - prevO)); }
            prevN = hn; prevO = ho; first = false;
            if (Math.abs(z) <= 1_500_000 || z % 3_000_000 == 0)
                say(String.format(LF, "   %10d %12.3f %14.4f %14.4f %12.4f", z, Math.toDegrees(lat), hn, ho, hn - ho));
        }
        say("");
        // ⚠ 断言写法修正（第一版写错了）：500 km 采样下赤道带本来就有 148 m 的**真实**梯度，
        //   不能断言「跳变 < 1 m」。要断言的是「赤道**不比别处更特殊**」，所以做一次 5 km 细扫。
        double fine = 0, prevF = 0; boolean firstF = true;
        for (int z = -200_000; z <= 200_000; z += 5_000) {
            double hv = CoastalLayer.hcLocal(tau, WorldContract.coriolis(WorldContract.latOf(z)));
            if (!firstF) fine = Math.max(fine, Math.abs(hv - prevF));
            prevF = hv; firstF = false;
        }
        // ⚠ 断言写法第二次修正：赤道带里 h_c 是**一条线性斜坡**（raw 正比于 |f| 正比于 |lat|），
        //   ±200 km 内从 0 长到 ~139 m ⇒ 每 5 km 本来就有 ~5 m 的**真实**增量。所以不能断言「跳变小」，
        //   要断言的是「**没有反号**」+「比旧口径小一个量级」。
        chk(fine < 10.0, String.format(LF, "新口径：赤道 ±200 km 的 5 km 细扫最大增量 = %.4f m（线性斜坡，应 <= 10 m）", fine));
        boolean signOk = true;
        for (int z = -2_000_000; z <= 2_000_000; z += 50_000) {
            double fv = WorldContract.coriolis(WorldContract.latOf(z));
            double hv = CoastalLayer.hcLocal(tau, fv);
            if (fv > 0 && hv < 0) signOk = false;
            if (fv < 0 && hv > 0) signOk = false;
        }
        chk(signOk, "h_c 的符号与 f 的符号处处一致（**没有反号**）");
        double fineOld = 0, prevFO = 0; boolean firstO = true;
        for (int z = -200_000; z <= 200_000; z += 5_000) {
            double hv = 150.0 * Math.tanh(oldRaw(tau, WorldContract.coriolis(WorldContract.latOf(z))) / 150.0);
            if (!firstO) fineOld = Math.max(fineOld, Math.abs(hv - prevFO));
            prevFO = hv; firstO = false;
        }
        say(String.format(LF, "   对照（旧口径）同一细扫 = %.4f m", fineOld));
        say(String.format(LF, "   500 km 粗采样：新 %.4f m / 旧 %.4f m（粗采样里 148 m 是**真实梯度**，不是跳变）",
                maxJumpNew, maxJumpOld));
        say("");

        // ---- D. 赤道那一对（z=0 与 z=-1）----
        say("D. 赤道那一对（生产里 WorldContract.latOf(0) 精确为 0）");
        double f0 = WorldContract.coriolis(WorldContract.latOf(0));
        double fm1 = WorldContract.coriolis(WorldContract.latOf(-1));
        double hn0 = CoastalLayer.hcLocal(tau, f0), hnm1 = CoastalLayer.hcLocal(tau, fm1);
        double ho0 = 150.0 * Math.tanh(oldRaw(tau, f0) / 150.0), hom1 = 150.0 * Math.tanh(oldRaw(tau, fm1) / 150.0);
        say(String.format(LF, "   f(z=0)  = %.6e    f(z=-1) = %.6e", f0, fm1));
        say(String.format(LF, "   新： h_c(0) = %+.4f m   h_c(-1) = %+.4f m   跳变 = %.4f m", hn0, hnm1, Math.abs(hn0 - hnm1)));
        say(String.format(LF, "   旧： h_c(0) = %+.4f m   h_c(-1) = %+.4f m   跳变 = %.4f m", ho0, hom1, Math.abs(ho0 - hom1)));
        chk(Math.abs(hn0 - hnm1) < 1.0, "新口径在 z=0 / z=-1 上的跳变 < 1 m");
        say("");
        say(String.format(LF, "A~D 硬断言失败数 = %d", fails));
        rep.flush(); rep.close();
        System.out.println("JAVA_EXIT=" + (fails == 0 ? 0 : 5));
    }
}
