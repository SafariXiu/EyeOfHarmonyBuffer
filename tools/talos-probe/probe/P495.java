package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P495：**D79 的判别实验** —— 把**真实 ERA5 月平均纬向剖面**直接喂进涡动替身，峰位会不会迁移？
 *
 * <p>为什么必须用生产代码做这件事：§226.3 的 E69 —— 我写的 Python 代理给出模型迁移 −21.75 度，
 * 而生产 P493 实测 −2.50 度，**差 9 倍**。关于模型行为的判断只认生产代码里的探针。
 *
 * <p>预登记判读（§226.4，跑之前写死）：
 * <ul>
 *   <li>喂真剖面后迁移 <b>&gt;= 5 度</b> ⇒ 替身**能**迁移，问题在温度场的构造（回到路线 C）；</li>
 *   <li>喂真剖面后迁移 <b>&lt; 3 度</b> ⇒ 问题在**替身的放置**（水汽曲率 d2W/dphi2 而不是斜压性），
 *       D79 要重做替身。</li>
 * </ul>
 *
 * <p>A 段是钩子的**中性自检**：ZONAL_PROFILE_OVERRIDE = null 时必须与 P493 逐位相同。
 */
public class P495 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P495] " + s); rep.flush(); System.out.println("[P495] " + s); System.out.flush(); }

    static final int NLAT = 19, NMON = 12;
    static final double[][] PROF = new double[NMON][NLAT];      // 真实 ERA5 月平均纬向 t2m（K）
    static final double[][] BR_SEA = new double[NMON][NLAT];    // 洋面分支月值（K）
    static final double[][] BR_LAND = new double[NMON][NLAT];   // 陆面分支月值（**海平面**，K）
    static final double PHI0 = 171.0 / 365.25;

    static void loadBranches() throws Exception {
        BufferedReader br = new BufferedReader(new FileReader(new File(ROOT, "build/eoh_probe/refs/zonal_month_branches.txt")));
        String ln; int ns = 0, nl = 0;
        while ((ln = br.readLine()) != null) {
            ln = ln.trim();
            if (ln.isEmpty() || ln.startsWith("#") || ln.startsWith("lat")) continue;
            String[] p = ln.split("[\\s,]+");                       // p[0]=SEA/LAND, p[1]=mNN
            double[] dst = p[0].equals("SEA") ? BR_SEA[ns++] : BR_LAND[nl++];
            for (int k = 0; k < NLAT; k++) dst[k] = Double.parseDouble(p[k + 2]);
        }
        br.close();
        if (ns != NMON || nl != NMON) throw new IllegalStateException("branches " + ns + "/" + nl);
    }

    /** 以 5 度节点表（12 月 x 19 纬）为准的插值：|lat| 线性、月**循环**线性、半球 = 半个周期。 */
    static double interp(double[][] tab, double latRad, double theta) {
        double a = Math.abs(Math.toDegrees(latRad));
        int k = (int) (a / 5.0); if (k > NLAT - 2) k = NLAT - 2;
        double tl = a / 5.0 - k;
        double p = theta / (2.0 * Math.PI) + PHI0 + (latRad >= 0 ? 0.0 : 0.5);
        p = p - Math.floor(p);
        double f = p * 12.0;
        int mm = (int) f; if (mm > 11) mm = 11;
        double tm = f - mm; int m2 = (mm + 1) % 12;
        double r0 = tab[mm][k] * (1 - tm) + tab[m2][k] * tm;
        double r1 = tab[mm][k + 1] * (1 - tm) + tab[m2][k + 1] * tm;
        return r0 * (1 - tl) + r1 * tl;
    }

    static final double[][] PROF_D = new double[NMON][NLAT];    // 路线 D：按本世界 kappa_bar(phi) 混合
    static final double[][] PROF_E = new double[NMON][NLAT];    // 路线 E（对照）：按**地球** ZF(phi) 混合
    static final double[][] PROF_SL = new double[NMON][NLAT];   // 观测剖面（**折算到海平面**，与模型同口径）

    /** 读 12 x 19 的表（首列是 mNN 之类的标签，其后 19 个数）。 */
    static void loadTable(String rel, double[][] dst) throws Exception {
        BufferedReader br = new BufferedReader(new FileReader(new File(ROOT, rel)));
        String ln; int m = 0;
        while ((ln = br.readLine()) != null) {
            ln = ln.trim();
            if (ln.isEmpty() || ln.startsWith("#") || ln.startsWith("lat")) continue;
            String[] p = ln.split("[\\s,]+");
            for (int k = 0; k < NLAT; k++) dst[m][k] = Double.parseDouble(p[k + 1]);
            m++;
        }
        br.close();
        if (m != NMON) throw new IllegalStateException(rel + " rows = " + m);
    }

    /** 与 ZonalTables.seasonShape 同一套口径：|lat| 线性、月**循环**线性、半球 = 半个周期。 */
    static double realZonal(double latRad, double theta) {
        double a = Math.abs(Math.toDegrees(latRad));
        int k = (int) (a / 5.0); if (k > NLAT - 2) k = NLAT - 2;
        double tl = a / 5.0 - k;
        double p = theta / (2.0 * Math.PI) + PHI0 + (latRad >= 0 ? 0.0 : 0.5);
        p = p - Math.floor(p);
        double f = p * 12.0;
        int mm = (int) f; if (mm > 11) mm = 11;
        double tm = f - mm; int m2 = (mm + 1) % 12;
        double r0 = PROF[mm][k] * (1 - tm) + PROF[m2][k] * tm;
        double r1 = PROF[mm][k + 1] * (1 - tm) + PROF[m2][k + 1] * tm;
        return r0 * (1 - tl) + r1 * tl;
    }

    /** 观测剖面的**年平均**（19 纬）。 */
    static double realSlAnnual(double latRad) {
        double a = Math.abs(Math.toDegrees(latRad));
        int k = (int) (a / 5.0); if (k > NLAT - 2) k = NLAT - 2;
        double tl = a / 5.0 - k, s0 = 0, s1 = 0;
        for (int m = 0; m < NMON; m++) { s0 += PROF_SL[m][k]; s1 += PROF_SL[m][k + 1]; }
        return (s0 / NMON) * (1 - tl) + (s1 / NMON) * tl;
    }

    /**
     * **路线 C′**：年度基座用**本世界自己的**（zonalMeanSeaLevelK），季节异常**直接取观测的差值**
     * —— 不做任何归一化、不乘任何本世界振幅。
     *
     * <p>与 §225 被否掉那版的关键区别：那版是「归一化形状 × 本世界局部振幅」，
     * 是那个**组合**才做出非单调；这里用的是观测剖面自己的差值，观测剖面本身单调。
     */
    static double routeCPrime(double latRad, double theta) {
        return Atmosphere.zonalMeanSeaLevelK(latRad)
             + (interp(PROF_SL, latRad, theta) - realSlAnnual(latRad));
    }

    /** 单调性检查：返回 {上升点数, 最差上升量, 该处纬度}。 */
    static double[] monotonic(double[][] tab, boolean cp) {
        int n = 0; double worst = 0, at = 0;
        for (double th : new double[]{0.0, Math.PI}) {
            double prev = Double.NaN;
            for (double la = 0.0; la <= 90.001; la += 1.0) {
                double v = cp ? routeCPrime(Math.toRadians(la), th) : interp(tab, Math.toRadians(la), th);
                if (!Double.isNaN(prev) && v > prev) { n++; if (v - prev > worst) { worst = v - prev; at = la; } }
                prev = v;
            }
        }
        return new double[]{n, worst, at};
    }

    static double peak(double theta) {
        double best = -1e30, bestLat = 0;
        for (double la = 20.0; la <= 72.001; la += 0.25) {
            double v = PrecipField.eddyMfc(Math.toRadians(la), theta);
            if (v > best) { best = v; bestLat = la; }
        }
        return bestLat;
    }

    /** 真实剖面自己的 |dT/dy| 峰位（靶子），同一套正负 5 度中心差。 */
    static double gradPeak(double theta) {
        double best = -1, bestLat = 0;
        for (double la = 20.0; la <= 72.001; la += 0.25) {
            double g = Math.abs(realZonal(Math.toRadians(la + 5), theta) - realZonal(Math.toRadians(la - 5), theta));
            if (g > best) { best = g; bestLat = la; }
        }
        return bestLat;
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p495_report.txt"), "UTF-8");
        say("P495：把真实 ERA5 月平均纬向剖面喂进涡动替身（D79 的判别实验）");
        say("");
        loadTable("build/eoh_probe/refs/zonal_month_profile.txt", PROF);
        loadTable("build/eoh_probe/refs/zonal_month_routeD.txt", PROF_D);
        loadTable("build/eoh_probe/refs/zonal_month_routeE.txt", PROF_E);
        loadTable("build/eoh_probe/refs/zonal_month_realSL.txt", PROF_SL);
        say("A. 钩子中性自检（OVERRIDE = null，应与 P493 逐位相同：夏 45.00 / 冬 42.50）");
        PrecipField.ZONAL_PROFILE_OVERRIDE = null;
        double c0 = peak(0.0), c1 = peak(Math.PI);
        say(String.format(LF, "   现状（null）      : 夏 %.2f   冬 %.2f   迁移 %+.2f 度", c0, c1, c1 - c0));
        say("");
        say("B. 真实剖面自己的 |dT/dy| 峰位（靶子，同一套口径）");
        double g0 = gradPeak(0.0), g1 = gradPeak(Math.PI);
        say(String.format(LF, "   ERA5 真剖面      : 夏 %.2f   冬 %.2f   迁移 %+.2f 度", g0, g1, g1 - g0));
        say("");
        say("C. 把真剖面喂进替身（OVERRIDE = 真剖面）");
        PrecipField.ZONAL_PROFILE_OVERRIDE = new PrecipField.ZonalProfile() {
            @Override public double tempAt(double latRad, double theta) { return realZonal(latRad, theta); }
        };
        double r0 = peak(0.0), r1 = peak(Math.PI);
        say(String.format(LF, "   喂真剖面          : 夏 %.2f   冬 %.2f   迁移 %+.2f 度", r0, r1, r1 - r0));
        PrecipField.ZONAL_PROFILE_OVERRIDE = null;
        say("");
        say("=============== 汇总 ===============");
        say(String.format(LF, "   %-20s %8s %8s %10s", "口径", "夏", "冬", "迁移"));
        say(String.format(LF, "   %-20s %8.2f %8.2f %+10.2f", "现状（单一谐波）", c0, c1, c1 - c0));
        say(String.format(LF, "   %-20s %8.2f %8.2f %+10.2f", "喂真实 ERA5 剖面", r0, r1, r1 - r0));
        say(String.format(LF, "   %-20s %8.2f %8.2f %+10.2f", "真剖面自己的梯度峰", g0, g1, g1 - g0));
        say("");
        say("D. 路线 A（形状与振幅同源：两侧分支月值按 kappa 混合）—— **用生产链重测**");
        loadBranches();
        final double KM = Atmosphere.KAPPA_MEAN;
        PrecipField.ZONAL_PROFILE_OVERRIDE = new PrecipField.ZonalProfile() {
            @Override public double tempAt(double latRad, double theta) {
                return (1.0 - KM) * interp(BR_SEA, latRad, theta) + KM * interp(BR_LAND, latRad, theta);
            }
        };
        double a0 = peak(0.0), a1 = peak(Math.PI);
        say(String.format(LF, "   路线 A（kappa=%.3f）: 夏 %.2f   冬 %.2f   迁移 %+.2f 度", KM, a0, a1, a1 - a0));
        PrecipField.ZONAL_PROFILE_OVERRIDE = null;
        say(String.format(LF, "   %-20s %8.2f %8.2f %+10.2f", "路线 A", a0, a1, a1 - a0));
        say("");
        say("E. 路线 D（按**本世界实测的** kappa_bar(phi) 混合两侧分支月值，剖面来自 P491）");
        PrecipField.ZONAL_PROFILE_OVERRIDE = new PrecipField.ZonalProfile() {
            @Override public double tempAt(double latRad, double theta) { return interp(PROF_D, latRad, theta); }
        };
        double d0 = peak(0.0), d1 = peak(Math.PI);
        say(String.format(LF, "   路线 D              : 夏 %.2f   冬 %.2f   迁移 %+.2f 度", d0, d1, d1 - d0));
        PrecipField.ZONAL_PROFILE_OVERRIDE = null;
        say(String.format(LF, "   %-20s %8.2f %8.2f %+10.2f", "路线 D（kappa_bar 剖面）", d0, d1, d1 - d0));
        say("");
        say(String.format(LF, "   %-20s %8.2f %8.2f %+10.2f", "路线 A（常数 kappa）", a0, a1, a1 - a0));
        say("");
        say("F. **对照实验**路线 E：同样两分支、但按**地球自己的** ZF(phi)（0.22~0.60）混合");
        say("   —— 若 E 复现真剖面的迁移而 D 不能，则「迁移来自陆地占比的纬度对比」成立；");
        say("      若 E 也给 ~0，则该假设被**否证**，不许据此撤 B2.b。");
        PrecipField.ZONAL_PROFILE_OVERRIDE = new PrecipField.ZonalProfile() {
            @Override public double tempAt(double latRad, double theta) { return interp(PROF_E, latRad, theta); }
        };
        double e0 = peak(0.0), e1 = peak(Math.PI);
        say(String.format(LF, "   路线 E（地球 ZF）    : 夏 %.2f   冬 %.2f   迁移 %+.2f 度", e0, e1, e1 - e0));
        PrecipField.ZONAL_PROFILE_OVERRIDE = null;
        say(String.format(LF, "   %-20s %8.2f %8.2f %+10.2f", "路线 E（地球 ZF 剖面）", e0, e1, e1 - e0));
        say("");
        say("G. **E70 修正后**：观测剖面**折算到海平面**（+ZF(phi)*GAMMA*z_bar）再喂 —— 这才是与模型同口径的比较");
        say("   （C 段喂的是**绝对**剖面，含海拔，在 60~75 度多带了约 4 K；那是口径错配，E70。）");
        PrecipField.ZONAL_PROFILE_OVERRIDE = new PrecipField.ZonalProfile() {
            @Override public double tempAt(double latRad, double theta) { return interp(PROF_SL, latRad, theta); }
        };
        double g0b = peak(0.0), g1b = peak(Math.PI);
        say(String.format(LF, "   观测剖面（海平面）: 夏 %.2f   冬 %.2f   迁移 %+.2f 度", g0b, g1b, g1b - g0b));
        PrecipField.ZONAL_PROFILE_OVERRIDE = null;
        say(String.format(LF, "   %-20s %8.2f %8.2f %+10.2f", "观测剖面（海平面口径）", g0b, g1b, g1b - g0b));
        say("");
        say("H. **路线 C′**（§228.4）：年度基座 = 本世界 zonalMeanSeaLevelK，季节异常 = **观测剖面的差值**（不归一化）");
        PrecipField.ZONAL_PROFILE_OVERRIDE = new PrecipField.ZonalProfile() {
            @Override public double tempAt(double latRad, double theta) { return routeCPrime(latRad, theta); }
        };
        double p0 = peak(0.0), p1 = peak(Math.PI);
        double[] mc = monotonic(null, true);
        say(String.format(LF, "   路线 C′             : 夏 %.2f   冬 %.2f   迁移 %+.2f 度", p0, p1, p1 - p0));
        say(String.format(LF, "   单调性（0~90 度，两季合并）：上升点 %d 个，最差 +%.3f K @%.0f 度", (int) mc[0], mc[1], mc[2]));
        PrecipField.ZONAL_PROFILE_OVERRIDE = null;
        say(String.format(LF, "   %-20s %8.2f %8.2f %+10.2f", "路线 C′", p0, p1, p1 - p0));
        say("");
        // 参照：同一条单调性检查，打在「现状」与「§225 被否掉那版」的口径上
        double[] mNow = new double[]{0, 0, 0};
        {   // 现状 = Atmosphere 自己的合成
            java.util.List<Double> vals = new java.util.ArrayList<>();
            int n = 0; double worst = 0, at = 0;
            for (double th : new double[]{0.0, Math.PI}) {
                double prev = Double.NaN;
                for (double la = 0.0; la <= 90.001; la += 1.0) {
                    double v = Atmosphere.zonalMeanSeaLevelK(Math.toRadians(la))
                             + Atmosphere.seasonalAnomaly(Math.toRadians(la), Atmosphere.KAPPA_MEAN, th);
                    if (!Double.isNaN(prev) && v > prev) { n++; if (v - prev > worst) { worst = v - prev; at = la; } }
                    prev = v;
                }
            }
            mNow = new double[]{n, worst, at};
        }
        say(String.format(LF, "   对照·现状（单一谐波）：上升点 %d 个，最差 +%.3f K @%.0f 度", (int) mNow[0], mNow[1], mNow[2]));
        say("");
        say("★ 判读（§228.4 预登记，**三条必须同时满足**）：");
        say("   ① 单调递减（上升点 = 0）；② P296 的独立 GPCP 三条带不变差；③ B2.b 迁移进入 5~15 度。");
        say("");
        say("★ 判读（§226.4 跑之前写死）：");
        say("   · 喂真剖面后迁移 >= 5 度 ⇒ 替身**能**迁移 ⇒ 问题在温度场的构造（路线 C）；");
        say("   · < 3 度 ⇒ 问题在**替身的放置**（水汽曲率而不是斜压性）⇒ D79 要重做替身。");
        rep.flush(); rep.close();
        System.out.println("JAVA_EXIT=0");
    }
}
