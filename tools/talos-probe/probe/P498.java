package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P498：**湿润收支**（新主线工作项 1 的仪器）。
 *
 * <h3>v1 的两个错（自己抓到的，记账）</h3>
 * <ol>
 *   <li><b>单位错（E73）</b>：v1 把 {@code -div(qV)} 直接当 mm/day 打印。q 是**比湿（kg/kg）**，
 *       {@code div(qV)} 的单位是 1/s，要变成 kg/(m^2 s) 必须乘气柱质量
 *       {@code RHO_AIR * H_BL = 1225 kg/m^2}。v1 的数字因此小了 **1225 倍**，
 *       看上去「湿润辐合 ~0.01 mm/day 可忽略」—— 完全错的结论。v2 已修。</li>
 *   <li><b>自检 [1] 断言了一个连续统恒等式（E74）</b>：v1 用「中心点的 q、u」拆
 *       {@code -div(qV) = adv + conv}，但中心值拆分与乘积的中心差分**在离散下不恒等**
 *       （差一个 O(h^2) 截断项）。v1 于是报了 860 次 FAIL —— 是**仪器错，不是模型错**。
 *       v2 改用**代数上精确**的邻点平均拆分，恒等式在离散下逐位成立。</li>
 * </ol>
 *
 * <h3>v2 的口径</h3>
 * <pre>
 *   MFC_BL = -div(rho_a * q * V) * H_BL        kg/(m^2 s)  ->  x86400 = mm/day
 *   adv    = -(u * dq/dx + v * dq/dz) * rho_a*H_BL*86400    [精确拆分]
 *   conv   = -(q * du/dx + q * dv/dz) * rho_a*H_BL*86400    [精确拆分，q = 邻点均值]
 *   恒等式 adv + conv == MFC_BL 在离散下**逐位**成立（自检 1）
 * </pre>
 * 另给出「中心值」版 {@code advC/convC}（物理上更直观，也是模型 {@code wLoc} 实际用的那套），
 * 并把两者的差单独打印为**混叠/截断项**（不许当成误差藏起来）。
 *
 * <h3>推导出的恒等式（本探针的第二个标尺）</h3>
 * <pre>
 *   模型的辐合降水  P_conv = EPS_C * rho_a * q * (-H_BL*divU) / rho_w   [m/s]
 *   边界层水汽辐合 MFC_conv = rho_a * q * (-divU) * H_BL               [kg/(m^2 s)]
 *   => P_conv[mm/day] = EPS_C * MFC_conv[mm/day] = 0.75 * MFC_conv
 * </pre>
 * 即在 {@code wLoc} 的线性区里，**模型的辐合降水恰好是边界层水汽辐合的 EPS_C 倍**
 * （{@code EPS_C} 就是「降水效率」这个物理量的数值）。所以下面 D 段的 {@code conv}
 * 与 {@code P_mean} 是**可以直接比大小**的两个数。
 */
public class P498 {

    static final int SEED = 1022228679;
    static final long SD = SimTerrain.seedOf(SEED);
    static final int CELL = PlateField.PLATE_CELL;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P498] " + s); rep.flush(); System.out.println("[P498] " + s); System.out.flush(); }

    static final int NX = 120;
    static final int XSPAN = 60_000_000;
    static final int GS = 500_000;
    /** 比湿(kg/kg) x m/s 的散度 -> mm/day 的换算：rho_a * H_BL * 86400。 */
    static final double TO_MMDAY = PrecipField.RHO_AIR * PrecipField.H_BL * 86400.0;

    static double[] st(int x, int z, double th) {
        double lat = WorldContract.latOf(z);
        double k = Atmosphere.kappaAt(x, z, SD, CELL);
        double tSl = Atmosphere.annualSeaLevelTemp(lat, k, Atmosphere.sstAnom(x, z))
                   + Atmosphere.seasonalAnomaly(lat, k, th);
        double[] u = Atmosphere.windAt(x, z, SD, CELL, th, GS);
        double hUp = PrecipField.upwindElev(x, z, SD, CELL, u[0], u[1]);
        double depl = PrecipField.depletion(hUp, k);
        double q = PrecipField.moisture(tSl, k > 0.0 ? hUp : 0.0, k);
        return new double[]{tSl, k, hUp, depl, q, PrecipField.qSat(tSl), u[0], u[1]};
    }

    static final class Pt {
        double tSl, k, hUp, depl, q, qSat, u, v, divU, wZm, wLoc, wEff, aw;
        double pMean, pEddy, pTot, ePot;
        double mfcF, advX, convX, advC, convC, alias;
        double idErr, qErr, pmErr;
    }

    /** P_mean = K * q * max(0, w_eff) 里的 K（mm/day per (kg/kg * m/s)）。 */
    static final double K_PM = PrecipField.EPS_C * PrecipField.RHO_AIR / PrecipField.RHO_WATER * 8.64e7;

    static Pt sample(int x, int z, double th) {
        Pt p = new Pt();
        double[] c = st(x, z, th), e = st(x + GS, z, th), w = st(x - GS, z, th);
        double[] n = st(x, z + GS, th), s = st(x, z - GS, th);
        p.tSl = c[0]; p.k = c[1]; p.hUp = c[2]; p.depl = c[3]; p.q = c[4]; p.qSat = c[5];
        p.u = c[6]; p.v = c[7];
        double lat = WorldContract.latOf(z);
        double h2 = 2.0 * GS;

        p.divU = (e[6] - w[6]) / h2 + (n[7] - s[7]) / h2;
        p.wZm = ZonalTables.wZm(Math.toDegrees(lat - PrecipField.precipSubsolarLat(th)));
        p.wLoc = PrecipField.W_LOC_MAX * Math.tanh((-PrecipField.H_BL * p.divU) / PrecipField.W_LOC_MAX);
        p.wEff = PrecipField.wEff(lat, th, p.divU);
        p.aw = Math.max(0.0, p.wEff);

        p.pMean = PrecipField.precip(p.q, p.wEff) * 8.64e7;
        double m = PrecipField.eddyMfc(lat, th);
        p.pEddy = (m > 0.0 ? m * p.depl * PrecipField.depletionCol(p.hUp, p.k) / PrecipField.RHO_WATER : 0.0) * 8.64e7;
        p.pTot = p.pMean + p.pEddy;

        // ---- 平流/辐合：精确拆分（邻点平均）----
        double qx = 0.5 * (e[4] + w[4]), qz = 0.5 * (n[4] + s[4]);
        double ux = 0.5 * (e[6] + w[6]), vz = 0.5 * (n[7] + s[7]);
        double dqdx = (e[4] - w[4]) / h2, dqdz = (n[4] - s[4]) / h2;
        double dudx = (e[6] - w[6]) / h2, dvdz = (n[7] - s[7]) / h2;
        double divFlux = ((e[4] * e[6] - w[4] * w[6]) + (n[4] * n[7] - s[4] * s[7])) / h2;
        p.mfcF = -divFlux * TO_MMDAY;
        p.advX = -(ux * dqdx + vz * dqdz) * TO_MMDAY;
        p.convX = -(qx * dudx + qz * dvdz) * TO_MMDAY;
        p.idErr = Math.abs(p.advX + p.convX - p.mfcF);
        // ---- 中心值版（物理直观 / 模型 wLoc 用的那套）----
        p.advC = -(c[6] * dqdx + c[7] * dqdz) * TO_MMDAY;
        p.convC = -c[4] * (dudx + dvdz) * TO_MMDAY;
        p.alias = p.mfcF - (p.advC + p.convC);

        // ---- 潜在蒸发（beta=1；模型里**没有** E 这一项）----
        double tSfc = Atmosphere.surfaceTemp(x, z, SD, CELL, th);
        double sp = Math.hypot(c[6], c[7]);
        p.ePot = PrecipField.RHO_AIR * Atmosphere.cdOf(c[1]) * sp
               * Math.max(0.0, PrecipField.qSat(tSfc) - c[4]) * 86400.0;
        p.qErr = Math.abs(p.q - PrecipField.RH_SEA * p.qSat * p.depl);
        p.pmErr = Math.abs(p.pMean - K_PM * p.q * p.aw);
        return p;
    }

    static final class Acc {
        int n = 0, nZeroP = 0, nNegW = 0, nNegMfc = 0, nPosMfc = 0;
        double tSl, q, qSat, depl, divU, wZm, wLoc, wEff, aw, pMean, pEddy, pTot, ePot, hUp;
        double mfcF, advX, convX, advC, convC, alias;
        void add(Pt p) {
            n++;
            tSl += p.tSl; q += p.q; qSat += p.qSat; depl += p.depl; hUp += p.hUp;
            divU += p.divU; wZm += p.wZm; wLoc += p.wLoc; wEff += p.wEff; aw += p.aw;
            pMean += p.pMean; pEddy += p.pEddy; pTot += p.pTot; ePot += p.ePot;
            mfcF += p.mfcF; advX += p.advX; convX += p.convX;
            advC += p.advC; convC += p.convC; alias += p.alias;
            if (p.pTot == 0.0) nZeroP++;
            if (p.wEff <= 0.0) nNegW++;
            if (p.mfcF < 0) nNegMfc++; else nPosMfc++;
        }
        double m(double s) { return n == 0 ? Double.NaN : s / n; }
        double pct(int c) { return n == 0 ? Double.NaN : 100.0 * c / n; }
    }

    static int failId = 0, failQ = 0, failFact = 0, failProd = 0;
    static double worstId = 0.0, worstQ = 0.0, worstPm = 0.0;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p498_report.txt"), "UTF-8");
        long t0 = System.currentTimeMillis();
        say("P498 v2：湿润收支 —— 平流/辐合的分离 + P_陆/P_海 的精确分解 + 硬零普查");
        say(String.format(LF, "  接线口径：installedSeed=%d  EDDY_PLACEMENT_FROM_OBS=%s  EDDY_CLOSURE=%d  EDDY_MFC_REF=%.3e",
                SEED, PrecipField.EDDY_PLACEMENT_FROM_OBS, PrecipField.EDDY_CLOSURE, PrecipField.EDDY_MFC_REF));
        say(String.format(LF, "  RH_SEA=%.2f  EPS_C=%.2f  H_MOIST=%.0f  W_LOC_MAX=%.1e  H_BL=%.0f  GS=%d  换算=rho_a*H_BL*86400=%.1f",
                PrecipField.RH_SEA, PrecipField.EPS_C, PrecipField.H_MOIST, PrecipField.W_LOC_MAX,
                PrecipField.H_BL, GS, TO_MMDAY));
        say(String.format(LF, "  LANDS_ANNUAL_IN_PRESSURE=%s  SEASON_SHAPE_FROM_OBS=%s  COAST_WIND_ON=%s  KAPPA_MEAN=%.3f",
                Atmosphere.LANDS_ANNUAL_IN_PRESSURE, Atmosphere.SEASON_SHAPE_FROM_OBS,
                Atmosphere.COAST_WIND_ON, Atmosphere.KAPPA_MEAN));
        say("");

        Acc[][] k = new Acc[9][4];   // 0=陆夏 1=海夏 2=陆冬 3=海冬
        for (int i = 0; i < 9; i++) for (int j = 0; j < 4; j++) k[i][j] = new Acc();

        say("B. 通量表");
        say(String.format(LF, "  %-4s %-3s %7s %8s %8s %6s %8s %8s %8s %8s %8s %8s %8s",
                "lat", "类", "T_sl(K)", "q(g/kg)", "qs(g/kg)", "depl", "divU(1e-6)", "w_zm", "w_loc", "w_eff", "P_mean", "P_eddy", "P_tot"));
        say("       (w_* 单位 1e-3 m/s，P_* 单位 mm/day)");
        for (int li = 0; li < 9; li++) {
            int la = 5 + li * 5;
            int z = (int) Math.round(la / 90.0 * WorldContract.MAX_D);
            for (int i = 0; i < NX; i++) {
                int x = (int) ((long) i * XSPAN / NX);
                double kk = Atmosphere.kappaAt(x, z, SD, CELL);
                boolean land = kk > 0.8, sea = kk < 0.2;
                if (!land && !sea) continue;
                Pt ps = sample(x, z, 0.0), pw = sample(x, z, Math.PI);
                for (Pt p : new Pt[]{ps, pw}) {
                    worstId = Math.max(worstId, p.idErr);
                    worstQ = Math.max(worstQ, p.qErr);
                    if (p.idErr > 1e-9 * Math.max(1e-12, Math.abs(p.mfcF))) failId++;
                    if (p.qErr > 1e-15) failQ++;
                    // [3] 逐点恒等：P_mean == K*q*max(0,w_eff)。**逐点**成立；
                    //     带平均的「比 = 比 x 比」不成立（<qA> != <q><A>，差一个协方差项）——
                    //     那是 D 段要单独报告的物理量，不是自检失败。
                    worstPm = Math.max(worstPm, p.pmErr);
                    if (p.pmErr > 1e-12 * Math.max(1e-12, Math.abs(p.pMean))) failFact++;
                }
                double pr = PrecipField.mmPerDay(x, z, SD, CELL, 0.0, GS);
                if (Math.abs(pr - ps.pTot) > 1e-9 * Math.max(1e-12, Math.abs(pr))) failProd++;
                double prw = PrecipField.mmPerDay(x, z, SD, CELL, Math.PI, GS);
                if (Math.abs(prw - pw.pTot) > 1e-9 * Math.max(1e-12, Math.abs(prw))) failProd++;
                if (land) { k[li][0].add(ps); k[li][2].add(pw); }
                else { k[li][1].add(ps); k[li][3].add(pw); }
            }
            row(la, "陆", k[li][0]); row(la, "海", k[li][1]);
            row(la, "陆", k[li][2]); row(la, "海", k[li][3]);
            say("");
        }

        // ================= C =================
        say("C. 湿润通量散度（mm/day；正 = 该列从别处**得到**水汽）");
        say("   精确拆分: MFC = adv + conv（邻点平均）  中心值版: advC/convC + 混叠项 alias");
        say(String.format(LF, "  %-4s %-3s %-4s %10s %10s %10s %10s %10s %10s %10s",
                "lat", "类", "季", "MFC", "adv", "conv", "advC", "convC", "alias", "MFC>0%"));
        for (int li = 0; li < 9; li++) {
            int la = 5 + li * 5;
            Acc[] q = k[li];
            say(String.format(LF, "  %-4d %-3s %-4s %10.2f %10.2f %10.2f %10.2f %10.2f %10.2f %9.0f%%",
                    la, "陆", "夏", q[0].m(q[0].mfcF), q[0].m(q[0].advX), q[0].m(q[0].convX),
                    q[0].m(q[0].advC), q[0].m(q[0].convC), q[0].m(q[0].alias), q[0].pct(q[0].nPosMfc)));
            say(String.format(LF, "  %-4d %-3s %-4s %10.2f %10.2f %10.2f %10.2f %10.2f %10.2f %9.0f%%",
                    la, "海", "夏", q[1].m(q[1].mfcF), q[1].m(q[1].advX), q[1].m(q[1].convX),
                    q[1].m(q[1].advC), q[1].m(q[1].convC), q[1].m(q[1].alias), q[1].pct(q[1].nPosMfc)));
            say(String.format(LF, "  %-4d %-3s %-4s %10.2f %10.2f %10.2f %10.2f %10.2f %10.2f %9.0f%%",
                    la, "陆", "冬", q[2].m(q[2].mfcF), q[2].m(q[2].advX), q[2].m(q[2].convX),
                    q[2].m(q[2].advC), q[2].m(q[2].convC), q[2].m(q[2].alias), q[2].pct(q[2].nPosMfc)));
        }
        say("");

        // ================= D =================
        say("D. **判别式**：P_mean_陆/P_mean_海 精确等于 (q比) x (max(0,w_eff)比)");
        say(String.format(LF, "  %-4s %8s %8s %8s %8s %8s %8s %8s %10s %10s",
                "lat", "Pm_陆", "Pm_海", "Pm比", "q比", "w+比", "乘积", "残差", "Ptot比", "Ptot海=0?"));
        double[] agg = new double[8];
        for (int li = 0; li < 9; li++) {
            int la = 5 + li * 5;
            Acc L = k[li][0], S = k[li][1];
            double pmL = L.m(L.pMean), pmS = S.m(S.pMean);
            double qR = L.m(L.q) / S.m(S.q), wR = L.m(L.aw) / S.m(S.aw);
            double pR = pmL / pmS, prod = qR * wR;
            double res = Math.abs(pR - prod) / Math.max(1e-12, Math.abs(pR));
            say(String.format(LF, "  %-4d %8.2f %8.2f %8.3f %8.3f %8.3f %8.3f %8.1e %10.3f %10s",
                    la, pmL, pmS, pR, qR, wR, prod, res,
                    L.m(L.pTot) / Math.max(1e-12, S.m(S.pTot)),
                    S.m(S.pTot) == 0.0 ? "是(硬零)" : "no"));
            // 只统计**两侧都有正辐合降水**的纬度：海洋副热带夏季 P_mean 恰为 0
            // ⇒ 比值发散成 Infinity，把带平均污染成无意义的数（E75 的同族）。
            if (la >= 15 && la <= 30 && pmS > 1e-9 && pmL > 1e-9) {
                agg[0] += pR; agg[1] += qR; agg[2] += wR; agg[3] += L.m(L.pTot) / Math.max(1e-12, S.m(S.pTot));
                agg[7]++;
            }
        }
        say("");
        double n4 = agg[7];
        if (n4 <= 0) {
            say("  ⇒ 15~30 度**没有任何一个纬度两侧都有正辐合降水** ⇒ 判别式无定义（这本身就是结论）。");
        } else {
            say(String.format(LF, "  ⇒ 15~30 度夏季、**只统计两侧都有正 P_mean 的 %d 个纬度**：", (int) n4));
            say(String.format(LF, "       P_mean 比 = %.3f   = q比 %.3f  x  w+比 %.3f  x  协方差因子 %.3f",
                    agg[0] / n4, agg[1] / n4, agg[2] / n4, (agg[0] / n4) / ((agg[1] / n4) * (agg[2] / n4))));
            say(String.format(LF, "       P_total 比 = %.3f（含涡动项）", agg[3] / n4));
            say("       注意：被排除的纬度是**海洋 P_mean 恰为 0（硬零）**的那些 —— 那本身是最大的病，见 E 段。");
        }
        say("");

        // ================= E =================
        say("E. 硬零普查（模型的结构性截断：P 恰为 0.000000 的格点比例）");
        say(String.format(LF, "  %-4s %-3s %-4s %10s %10s %10s", "lat", "类", "季", "P=0 比例", "w_eff<=0 比例", "P_eddy/sum"));
        for (int li = 0; li < 9; li++) {
            int la = 5 + li * 5;
            for (int j = 0; j < 4; j++) {
                Acc a = k[li][j];
                say(String.format(LF, "  %-4d %-3s %-4s %9.0f%% %11.0f%% %10.2f",
                        la, (j % 2 == 0 ? "陆" : "海"), (j < 2 ? "夏" : "冬"),
                        a.pct(a.nZeroP), a.pct(a.nNegW), a.m(a.pEddy)));
            }
        }
        say("");

        // ================= F =================
        say("F. 潜在蒸发 E_pot（beta=1，诊断量；**模型里根本没有 E 这一项**）与收支残差");
        say(String.format(LF, "  %-4s %-3s %-4s %10s %10s %12s %12s", "lat", "类", "季", "E_pot", "P", "P - E_pot", "P-E_pot-MFC"));
        for (int li = 0; li < 9; li++) {
            int la = 5 + li * 5;
            for (int j = 0; j < 4; j++) {
                Acc a = k[li][j];
                say(String.format(LF, "  %-4d %-3s %-4s %10.2f %10.2f %12.2f %12.2f",
                        la, (j % 2 == 0 ? "陆" : "海"), (j < 2 ? "夏" : "冬"),
                        a.m(a.ePot), a.m(a.pTot), a.m(a.pTot) - a.m(a.ePot),
                        a.m(a.pTot) - a.m(a.ePot) - a.m(a.mfcF)));
            }
        }
        say("");

        // ================= G =================
        say("G. 自检（硬断言）");
        say(String.format(LF, "  [1] 离散恒等式 MFC == adv + conv（**求导之后**的层面）  最大残差 = %.3e mm/day   %s",
                worstId, failId == 0 ? "PASS" : ("FAIL n=" + failId)));
        say(String.format(LF, "  [2] q == RH_SEA*qSat*depl                              最大残差 = %.3e kg/kg    %s",
                worstQ, failQ == 0 ? "PASS" : ("FAIL n=" + failQ)));
        say(String.format(LF, "  [3] 逐点 P_mean == K*q*max(0,w_eff)                     最大残差 = %.3e mm/day     %s",
                worstPm, failFact == 0 ? "PASS" : ("FAIL n=" + failFact)));
        say(String.format(LF, "  [4] 本探针重算的 P_tot == 生产 mmPerDay（同口径同步长）  超限次数 = %d            %s",
                failProd, failProd == 0 ? "PASS" : "FAIL"));
        say(String.format(LF, "  SELFCHECK_FAILURES=%d", failId + failQ + failFact + failProd));
        say(String.format(LF, "  耗时 %.1f s", (System.currentTimeMillis() - t0) / 1000.0));
        rep.flush(); rep.close();
        System.out.println("JAVA_EXIT=0");
    }

    static void row(int la, String cls, Acc a) {
        say(String.format(LF, "  %-4d %-3s %7.1f %8.3f %8.3f %6.3f %8.2f %8.3f %8.3f %8.3f %8.2f %8.2f %8.2f",
                la, cls, a.m(a.tSl), a.m(a.q) * 1000.0, a.m(a.qSat) * 1000.0, a.m(a.depl),
                a.m(a.divU) * 1e6, a.m(a.wZm) * 1e3, a.m(a.wLoc) * 1e3, a.m(a.wEff) * 1e3,
                a.m(a.pMean), a.m(a.pEddy), a.m(a.pTot)));
    }
}
