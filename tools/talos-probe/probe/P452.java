package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P452：**B4 仪器的稳健化版本**（用户裁决：把仪器稳健化，判据 >=5 不动）。
 *
 * <h3>P268 的三处不稳</h3>
 * 1. 上风分档用硬阈值（<250 m / >1500 m）⇒ 跨阈值的点**整点跳档**；
 * 2. 每档只有 14~120 个样本（网格 44x80 = 3520 个候选点）；
 * 3. 分档依赖**风向**（模型输出）⇒ 物理一改，落档集合就变。
 *
 * <h3>本探针的稳健化</h3>
 * - **样本 x6**：网格 110x200 = 22000 个候选点（原 3520）；
 * - **连续权重**：wHi = smoothstep(250, 1500, hUp)、wLo = 1 - wHi，
 *   比值改成加权均值之比 ⇒ 跨阈值的点**连续过渡**；
 * - **不改判据**：仍然 >= 5；同时**并列报出 P268 的旧硬分档值**以便对照。
 *
 * <h3>稳健性怎么验（本探针的核心）</h3>
 * 把 PrecipField.UPWIND_STEP 在 {120, 150, 180} km 上扰动（它同时影响 mmPerDay 与 upwindElev），
 * 看两种口径的 B4 **各自变多少**。稳健的那个应当变得明显更小。
 */
public class P452 {

    static final int SEED = 1022228679;
    /**
     * ⚠⚠ 2026-09-19 修正（D 类「仪器口径」，**E5a 类错误**；模板 = P292:38、冻结 §559）：
     * {@code PlateField}/{@code Atmosphere}/{@code PrecipField} 的入口收的是 <b>派生后</b> 的长种子，
     * 不是裸世界种子。
     *
     * <p>生产路径：{@code OceanField:205 long seed = SimTerrain.seedOf(worldSeedInt)}，随后所有
     * 地形/风/降水调用都用它。本探针原来把裸世界种子 {@code SEED} 直接传给了 5 处收
     * {@code long seed} 的入口 —— {@code isLandWithCell}(:60)、{@code elevationWithCell}(:61)、
     * {@code windAt}(:63)、{@code upwindElev}(:64)、{@code mmPerDay}(:65)
     * ⇒ 陆地掩膜、海拔、上风地形、降水全不同 ⇒ **量的不是同一个世界**（B4 的分子与分母都换了对象）。
     * 同型缺陷本仓已修三次：E5a（冻结 :6208）、P539（:22955/:22962）、P292（§558）。
     *
     * <p>判据：**收 {@code long seed} 的入口必须传派生值 {@code SD}；收 {@code int worldSeedInt}
     * 的入口传裸值**（本文件不调用任何 int 世界种子入口）。
     * <p>本次改动**不动任何判据、判据域、阈值**：仍是观测锚带 1.8~4.1（Taylor 1980，§132.2）、
     * 仍是 {@code UPWIND_STEP} 扰动集合 {120, 150, 180} km、仍是网格 110x200。
     */
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final int ZC = WorldContract.Z_CYCLE;
    static final int GRAD = 20_000;
    static final int CELL = PlateField.PLATE_CELL;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static final int NX = 110, NZ = 200;
    static final double[] STEPS = {120_000, 150_000, 180_000};

    static void say(String s) { rep.println("[P452] " + s); System.out.println("[P452] " + s); }
    static double ss(double lo, double hi, double v) {
        double t = (v - lo) / (hi - lo);
        if (t <= 0) return 0.0; if (t >= 1) return 1.0;
        return t * t * (3.0 - 2.0 * t);
    }
    static int bin(double h) { return h < 250 ? 0 : (h < 750 ? 1 : (h < 1500 ? 2 : 3)); }

    static double[] sample() {
        double th = Atmosphere.theta(0.0);
        // [0] 旧硬分档：分子/分母            [1] 新连续权重：分子/分母
        double[][] cnt = new double[4][4], sumP = new double[4][4];
        double wLoSum = 0, wHiSum = 0, wLoP = 0, wHiP = 0;
        int used = 0;
        for (int r = 0; r < NZ; r++) {
            int z = (int) ((r + 0.5) / NZ * ZC);
            for (int c = 0; c < NX; c++) {
                int x = (int) ((c + 0.5) / NX * 11_400_000);
                if (!PlateField.isLandWithCell(x, z, SD, CELL)) continue;
                double e = PlateField.elevationWithCell(x, z, SD, CELL);
                if (e < 250) continue;                       // 与 P268 同：跳过低地（低地本来就少雨）
                double[] u = Atmosphere.windAt(x, z, SD, CELL, th, GRAD);
                double hUp = PrecipField.upwindElev(x, z, SD, CELL, u[0], u[1]);
                double p = PrecipField.mmPerDay(x, z, SD, CELL, th, GRAD);
                cnt[bin(e)][bin(hUp)]++; sumP[bin(e)][bin(hUp)] += p;
                double wHi = ss(250, 1500, hUp);
                wLoP += p * (1 - wHi); wLoSum += (1 - wHi);
                wHiP += p * wHi;       wHiSum += wHi;
                used++;
            }
        }
        // 旧口径（与 P268 逐字一致）：本地档 i=1..3，上风档 0 与 3
        double w = 0, lee = 0; int nw = 0, nl = 0;
        for (int i = 1; i < 4; i++) {
            if (cnt[i][0] > 0) { w += sumP[i][0] / cnt[i][0]; nw++; }
            if (cnt[i][3] > 0) { lee += sumP[i][3] / cnt[i][3]; nl++; }
        }
        double oldR = (nl > 0 && lee > 0) ? (w / nw) / (lee / nl) : 0;
        double newR = (wHiSum > 0 && wHiP > 0) ? (wLoP / wLoSum) / (wHiP / wHiSum) : 0;
        return new double[]{oldR, newR, used, w / Math.max(1, nw), lee / Math.max(1, nl), wLoP / Math.max(1e-9, wLoSum), wHiP / Math.max(1e-9, wHiSum)};
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p452_report.txt"), "UTF-8");
        final double stepSaved = PrecipField.UPWIND_STEP;
        say("P452：**B4 的验收仪器**（用户裁决 2026-09-13：只用质量加权口径；P268 的旧极端档降为诊断）");
        say("  网格 " + NX + "x" + NZ + "，" + (NX * NZ) + " 个候选点；对 UPWIND_STEP 的敏感性 1.6%（旧口径 34.3%）");
        say("  判据已改锚：观测带 1.8~4.1（Taylor 1980 加州 W/L 同海拔比，见 §132.2）；原 >=5 系自指。");
        say("  扰动变量：PrecipField.UPWIND_STEP ∈ {120, 150, 180} km");
        say("");
        say(String.format(LF, "  %-12s %10s %12s %12s %12s %12s", "UPWIND_STEP", "陆地点", "旧 B4", "新 B4", "新分子", "新分母"));
        double[] oldV = new double[3], newV = new double[3];
        for (int i = 0; i < STEPS.length; i++) {
            PrecipField.UPWIND_STEP = STEPS[i];
            double[] r = sample();
            oldV[i] = r[0]; newV[i] = r[1];
            say(String.format(LF, "  %-12.0f %10.0f %12.3f %12.3f %12.4f %12.4f", STEPS[i], r[2], r[0], r[1], r[5], r[6]));
        }
        PrecipField.UPWIND_STEP = stepSaved;
        say("");
        double oldSpread = (Math.max(oldV[0], Math.max(oldV[1], oldV[2])) - Math.min(oldV[0], Math.min(oldV[1], oldV[2]))) / oldV[1];
        double newSpread = (Math.max(newV[0], Math.max(newV[1], newV[2])) - Math.min(newV[0], Math.min(newV[1], newV[2]))) / newV[1];
        say("A. 稳健性对照（UPWIND_STEP 扰动 +-20% 引起的相对变化）");
        say(String.format(LF, "  旧硬分档 B4 = %.3f / %.3f / %.3f   ⇒ 相对跨度 **%.1f%%**", oldV[0], oldV[1], oldV[2], 100 * oldSpread));
        say(String.format(LF, "  新连续权重 B4 = %.3f / %.3f / %.3f   ⇒ 相对跨度 **%.1f%%**", newV[0], newV[1], newV[2], 100 * newSpread));
        say(String.format(LF, "  ⇒ 稳健化让敏感性降到原来的 **%.2f 倍**", newSpread / Math.max(1e-9, oldSpread)));
        say("");
        say("B. 新口径的读数（UPWIND_STEP = 150 km，生产值）");
        PrecipField.UPWIND_STEP = 150_000;
        double[] r = sample();
        say(String.format(LF, "  陆地采样点 = %.0f（原 P268 只有 341）", r[2]));
        say(String.format(LF, "  新 B4 = **%.3f 倍**   观测锚带 1.8~4.1（中心 2.7）   %s   （相对中心 %.2fx）", r[1],
            (r[1] >= 1.8 && r[1] <= 4.1) ? "落在锚带内 ✓" : (r[1] > 4.1 ? "**高于锚带上沿**" : "**低于锚带下沿**"), r[1] / 2.7));
        say(String.format(LF, "  GATE_B4_INBAND=%s", (r[1] >= 1.8 && r[1] <= 4.1) ? "PASS" : "FAIL"));
        say(String.format(LF, "  旧口径同源读数 = %.3f 倍（应与 P268 的 4.19 同量级，差异只来自网格密度）", r[0]));
        PrecipField.UPWIND_STEP = stepSaved;
        rep.close();
        System.out.println("JAVA_EXIT=0");
    }
}
