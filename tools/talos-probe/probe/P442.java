package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanWiring;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P442 v2：**A1 重锚后的验收仪器**（父 agent 自写，只用生产类）。
 *
 * <h3>v1 -> v2 修的 bug（口径错误，父 agent 自己犯的）</h3>
 * v1 把**观测**算成 `curl( mean_theta(u) )` —— 先对 4 个相位平均风、再求旋度。
 * 但旋度是风的**非线性**函数（tau = rho*Cd*|u|*u）⇒ 两者不等，实测差 20 倍。
 * **v2 改成 `mean_theta( curl(u(theta)) )`**，与模型侧（也是先逐相位求 curl 再平均）同口径。
 *
 * <h3>判据（§92.1 + §99.1 + §100 裁决 1）</h3>
 * 逐海盆比较「面积加权 mean_curl 的符号」与「观测锚的符号」，**判年均**；
 * 观测锚 = {@link ZonalTables#uZmSea}（洋面 10 m ERA5 两表）逐相位求 curl 后平均。
 */
public class P442 {

    static final int SEED = 1022228679;
    /**
     * ⚠⚠ 2026-09-19 修正（D 类「仪器口径」，**E5a 类错误**；模板 = P292:38、冻结 §559）：
     * {@code PlateField}/{@code Atmosphere} 的入口收的是 <b>派生后</b> 的长种子，不是裸世界种子。
     *
     * <p>生产路径：{@code OceanField:205 long seed = SimTerrain.seedOf(worldSeedInt)}，随后
     * {@code GyreRow.solve}(:369) / {@code CoastalLayer.coastTangent}(:385) /
     * {@code Atmosphere.windStress}(:390) <b>全部</b>用它。本探针原来把裸世界种子 {@code SEED}
     * 直接传给了 11 处收 {@code long seed} 的入口 —— {@code isLandWithCell}(:84)、
     * {@code windStress}(:97/:98/:99/:100/:101)、{@code kappaAt}(:115)、
     * {@code pressureAnomaly}(:120/:121/:122/:123) ⇒ 陆地掩膜、κ 场、风应力全不同
     * ⇒ **量的不是同一个世界**。同型缺陷本仓已修三次：E5a（冻结 :6208）、
     * P539（:22955/:22962）、P292（§558）。
     *
     * <p>判据：**收 {@code long seed} 的入口必须传派生值 {@code SD}；收 {@code int worldSeedInt}
     * 的入口传裸值**。本文件的 {@code OceanWiring.onWorld(SEED)}（:45）按定义收 int 世界种子
     * ⇒ **保持裸 int**（生产同样如此）。
     * <p>本次改动**不动任何判据、判据域、阈值**：仍是 {@code |lat| < 65} 的判据域、
     * 17.5/40 度的分带、{@code 0.9 x 97.8% = 88.0%} 的阈值。
     */
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final int ZC = WorldContract.Z_CYCLE;
    static final int CELL = PlateField.PLATE_CELL;
    static final int GRAD = 500_000, DX = 100_000;
    static final int XMIN = -12_000_000, XMAX = 12_000_000;
    static final double LATSTEP = 2.5, RHO = 1.225, CD = 1.3e-3;
    static final double[] PH4 = {0.0, Math.PI / 2, Math.PI, 3.0 * Math.PI / 2.0};
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static final java.util.HashMap<Integer, Integer> fSeen = new java.util.HashMap<>();

    public static void main(String[] args) throws Exception {

        // ── 第三步接线（§162）：验收必须在**生产口径**下跑 ──
        // 生产由 WorldChunkManagerTalos2 -> OceanWiring.onWorld 装 SST'；这里调**同一个入口**，
        // 保证「验收测的」与「生产跑的」是同一段代码（否则就是口径偏移）。
        // 预热是后台线程，本探针不等待 —— 它自己在采样时按行懒解，代价一样。
        OceanWiring.onWorld(SEED);
        System.out.println("[P442] 接线口径：installedSeed=" + com.EyeOfHarmonyBuffer.sim.ocean.OceanField.installedSeed()
            + "  ENABLED=" + com.EyeOfHarmonyBuffer.sim.ocean.OceanField.ENABLED);
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p442_report.txt"), "UTF-8");
        say("P442 v3：A1 验收仪器（逐海盆 mean_curl 符号 vs 观测锚，判年均）");
        say("  v3 修正：obs 侧补上 sign(lat)（curl 关于赤道**反对称**；v2 漏了 ⇒ 南半球整片反号）");
        say("  v3 修正：判据域收紧到 |lat| < 65（>=65 按 §92.1 只报量级），并按观测零线分带");
        say(String.format(LF, "  SEED=%d  PLATE_CELL=%d km  GRAD=%d km  dx=%d km  x=%d..%d Mm  纬度步长 %.1f 度",
            SEED, CELL / 1000, GRAD / 1000, DX / 1000, XMIN / 1_000_000, XMAX / 1_000_000, LATSTEP));
        say(String.format(LF, "  SEA_ONLY_UZM=%s（落地配置）  COAST_WIND_ON=%s  CELL_GAIN=%.2f  KAPPA_MEAN=%.3f",
            ZonalTables.SEA_ONLY_UZM, Atmosphere.COAST_WIND_ON, Atmosphere.CELL_GAIN, Atmosphere.KAPPA_MEAN));
        say("");

        // ★ §703 仪器自检：把「标签纬度」与「旧式实际纬度」并排打出来，证据留在日志里。
        say("MAP. §703 纬度映射自检（旧式 = lat/90*(Z_CYCLE/2) = 两倍纬度）");
        for (double ld = -87.5; ld <= 87.5; ld += LATSTEP) {
            int zOld = (int) (ld / 90.0 * (ZC / 2));
            say(String.format(LF, "  MAP label %+6.1f -> latActual %+6.2f   (旧式 z=%+10d -> %+6.2f)",
                ld, Math.toDegrees(WorldContract.latOf(WorldContract.zOfLat(ld))), zOld,
                Math.toDegrees(WorldContract.latOf(zOld))));
        }
        say("");
        say("A. 逐纬度对照（每行 = 一条纬线上的所有活跃海盆）");
        // ★ §710：把 A 段数出的「逐纬度活跃海盆数」存下来，供 C4 做 §143 要求的海盆权重。
        int[] basinByLat = new int[71];   // latDeg = -87.5 + i*2.5, i = 0..70
        say(String.format(LF, "  %-7s %6s %7s %7s %13s %9s", "纬度", "海盆", "model+", "model-", "obs curl", "一致%"));
        int nB = 0, agree = 0, sN = 0, sAg = 0, subN = 0, subNeg = 0, polN = 0, polPos = 0;
        // 按**观测零线**分带（零线在 +-17.5 与 +-40 度）：
        //   带 0 赤道带 0-17.5（观测 curl > 0）  带 1 副热带 17.5-40（< 0）  带 2 中高纬 40-65（> 0）
        final double[] BAND_LO = {0.0, 17.5, 40.0}, BAND_HI = {17.5, 40.0, 65.0};
        final String[] BAND_NAME = {"赤道带 0~17.5 (obs>0)", "副热带 17.5~40 (obs<0)", "中高纬 40~65 (obs>0)"};
        int[] bN = new int[3], bAg = new int[3], bW = new int[3], bWAg = new int[3];
        say("");
        say("E. ⚠ **继承量**：模型算出来的纬向风到底还剩多少是 exogenous 观测表 uZmSea？");
        say("   （A1 有相当部分是「继承」的，不量出来这个数就是自证）");
        say(String.format(LF, "  %-7s %8s %14s %14s %10s", "纬度", "海盆", "模型 u 海盆均值", "观测 u 年均", "比值"));
        say("   ⚠ 观测列取 uZmSea(|lat|, pi/2) = 年均；取 0.0 会得到 7 月表（我第一版就写错了）");
        double inhNum = 0, inhDen = 0;
        final int NLF = 51;
        double[] fLat = new double[NLF], fL = new double[NLF], fZm = new double[NLF], fTot = new double[NLF],
                 fPert = new double[NLF], fPx = new double[NLF], fPz = new double[NLF];
        int nLatF = 0;
        double gZm = 0, gTot = 0, gPx = 0, gPz = 0, gPert = 0, gRes = 0, gL = 0;
        for (double latDeg = -87.5; latDeg <= 87.5; latDeg += LATSTEP) {
            // ★ §703 仪器修正：原为 `(int)(latDeg/90.0*(ZC/2))`；ZC = Z_CYCLE = 4*MAX_D
            //   ⇒ ZC/2 = 2*MAX_D ⇒ 模型列取的是【两倍纬度】处（|lat|>45 后还越过极点折返），
            //   而下一行 obsMeanCurl 用的是【标签】纬度 ⇒ 同一行两侧纬度【不同口径】，
            //   符号一致率因此失去意义。（§673/§674 已因同一个错作废过四节结论。）
            //   契约：zOfLat(lat) = lat/90*MAX_D。
            int z = WorldContract.zOfLat(latDeg);
            double obs = obsMeanCurl(latDeg);
            int n = 0, pos = 0, neg = 0, ag = 0;
            double basinU = 0, basinL = 0;
            int runStart = Integer.MIN_VALUE;
            for (int x = XMIN; x <= XMAX + DX; x += DX) {
                boolean land = (x > XMAX) || PlateField.isLandWithCell(x, z, SD, CELL);
                if (!land) { if (runStart == Integer.MIN_VALUE) runStart = x; continue; }
                if (runStart == Integer.MIN_VALUE) continue;
                int xw = runStart, xe = x - DX, L = xe - xw;
                runStart = Integer.MIN_VALUE;
                if (L < 8 * DX) continue;
                double dlt = Math.cbrt(1.9e4 / Math.max(1e-30,
                    WorldContract.betaForLatitude(Math.toRadians(latDeg), ZC)));
                if (L < 2 * Math.PI * dlt) continue;
                double acc = 0, accTx = 0; int m = 0;
                for (int xx = xw; xx <= xe; xx += DX) {
                    double c4 = 0, tx4 = 0;
                    for (double th : PH4) {
                        double tyE = Atmosphere.windStress(xx + GRAD, z, SD, CELL, th, GRAD)[1];
                        double tyW = Atmosphere.windStress(xx - GRAD, z, SD, CELL, th, GRAD)[1];
                        double txN = Atmosphere.windStress(xx, z + GRAD, SD, CELL, th, GRAD)[0];
                        double txS = Atmosphere.windStress(xx, z - GRAD, SD, CELL, th, GRAD)[0];
                        double tx0 = Atmosphere.windStress(xx, z, SD, CELL, th, GRAD)[0];
                        c4 += (tyE - tyW) / (2.0 * GRAD) - (txN - txS) / (2.0 * GRAD);
                        tx4 += tx0;
                    }
                    acc += c4 / 4.0; accTx += tx4 / 4.0; m++;
                }
                if (m == 0) continue;
                double meanCurl = acc / m;
                // 把海盆均值的 tau_x 反解成等价 u（同 RHO/CD），再按海盆长度加权汇入承接量
                double meanTx = accTx / m;
                double eqU = Math.signum(meanTx) * Math.sqrt(Math.abs(meanTx) / (RHO * CD));
                basinU += eqU * L; basinL += L;
                // ---- F 项分解（全部走生产 API；COAST_WIND_ON 只改 v，故 u 与 windAt 逐位相同）----
                double latR = WorldContract.latOf(z);
                double kap = Atmosphere.kappaAt((xw + xe) / 2, z, SD, CELL);
                double fZm1 = 0, fTot1 = 0, fPx1 = 0, fPz1 = 0;
                int mf = 0;
                for (int xx = xw; xx <= xe; xx += 4 * DX) {   // F 是诊断，1/4 抽样即可
                    for (double th : PH4) {
                        double px = (Atmosphere.pressureAnomaly(xx + GRAD, z, SD, CELL, th)
                                   - Atmosphere.pressureAnomaly(xx - GRAD, z, SD, CELL, th)) / (2.0 * GRAD);
                        double pz = (Atmosphere.pressureAnomaly(xx, z + GRAD, SD, CELL, th)
                                   - Atmosphere.pressureAnomaly(xx, z - GRAD, SD, CELL, th)) / (2.0 * GRAD);
                        double uZm = Atmosphere.wind(0, 0, kap, latR, th)[0];
                        double uT  = Atmosphere.wind(px, pz, kap, latR, th)[0];
                        double uX  = Atmosphere.wind(px, 0, kap, latR, th)[0] - uZm;
                        double uZ  = Atmosphere.wind(0, pz, kap, latR, th)[0] - uZm;
                        fZm1 += uZm; fTot1 += uT; fPx1 += uX; fPz1 += uZ;
                    }
                    mf++;
                }
                if (mf > 0 && Math.abs(latDeg) <= 62.5) {
                    double w = L / 4.0 / mf;
                    int li = nLatF < NLF ? nLatF : NLF - 1;
                    if (!fSeen.containsKey((int) (latDeg * 10))) {
                        fSeen.put((int) (latDeg * 10), li);
                        fLat[li] = latDeg; nLatF++;
                    } else {
                        li = fSeen.get((int) (latDeg * 10));
                    }
                    fL[li] += L; fZm[li] += fZm1 * w; fTot[li] += fTot1 * w;
                    fPx[li] += fPx1 * w; fPz[li] += fPz1 * w;
                    fPert[li] += (fTot1 - fZm1) * w;
                    gL += L; gZm += fZm1 * w; gTot += fTot1 * w; gPx += fPx1 * w; gPz += fPz1 * w;
                    gPert += (fTot1 - fZm1) * w;
                    gRes += (fTot1 - fZm1 - fPx1 - fPz1) * w;
                }
                n++;
                if (meanCurl > 0) pos++; else neg++;
                boolean ok = meanCurl * obs > 0;      // 用乘积判符号，避免 signum(0) 的坑
                if (ok) ag++;
                // §92.1/§99.1：**极地 >= 65 度只报量级、不判方向**（观测在那里接近零、符号逐月翻转）
                if (Math.abs(latDeg) < 65.0) { nB++; if (ok) agree++; }
                if (Math.abs(latDeg) < 65.0 && Math.abs(obs) > 2e-9) { sN++; if (ok) sAg++; }
                double a = Math.abs(latDeg);
                if (a >= 10 && a <= 30) { subN++; if (meanCurl < 0) subNeg++; }
                if (a >= 42 && a <= 58) { polN++; if (meanCurl > 0) polPos++; }
                if (a < 65.0) {
                    for (int b = 0; b < 3; b++) {
                        if (a > BAND_LO[b] && a <= BAND_HI[b]) {
                            bN[b]++;
                            if (ok) bAg[b]++;
                            if (Math.abs(obs) > 2e-9) { bW[b]++; if (ok) bWAg[b]++; }
                        }
                    }
                }
            }
            basinByLat[(int) Math.round((latDeg + 87.5) / LATSTEP)] = n;   // ★ §710 权重源
            if (n == 0) continue;
            say(String.format(LF, "  %-7.1f %6d %7d %7d %13.3e %8.0f%%", latDeg, n, pos, neg, obs, 100.0 * ag / n));
            if (basinL > 0 && Math.abs(latDeg) <= 62.5) {
                double mU = basinU / basinL;
                // ⚠ 年均 = uZmSea(a, pi/2) = 0.5*(1月表+7月表)；写 0.0 会拿到**7 月表**（口径错，我犯过一次）
                double oU = ZonalTables.uZmSea(Math.abs(latDeg), Math.PI / 2);
                say(String.format(LF, "  %-7s %8s %14.3f %14.3f %9.2f", "", "", mU, oU, mU / oU));
                inhNum += Math.abs(mU - oU) * basinL; inhDen += Math.abs(oU) * basinL;
            }
        }
        say("");
        say("B. 汇总");
        say("  （判据域 = 绝对纬度 < 65 度；>=65 度按 §92.1 只报量级）");
        say(String.format(LF, "  判据域内活跃海盆 n = %d", nB));
        say(String.format(LF, "  **逐海盆符号正确率（年均 vs 观测）：%d/%d = %.0f%%**", agree, nB, 100.0 * agree / Math.max(1, nB)));
        say(String.format(LF, "  **强信号子集（绝对观测 > 2e-9）：%d/%d = %.0f%%**", sAg, sN, 100.0 * sAg / Math.max(1, sN)));
        say(String.format(LF, "  A1.3 副热带 10~30 度：%d/%d = %.0f%% 的活跃海盆 mean_curl < 0",
            subNeg, subN, 100.0 * subNeg / Math.max(1, subN)));
        say(String.format(LF, "  A1.3 副极地 42~58 度：%d/%d = %.0f%% 的活跃海盆 mean_curl > 0",
            polPos, polN, 100.0 * polPos / Math.max(1, polN)));
        say("");
        say("D. **按观测零线分带的逐海盆符号一致率**（这才是 A1.3 的正确口径 ——");
        say("   §92.1 的「副热带 10~30 全 <0」横跨了 17.5 度那条零线，10~17.5 观测本来就是正的）");
        int tN = 0, tAg = 0, tW = 0, tWAg = 0;
        say("");
        say("F. ⚠⚠ **u 的三项分解** —— u = -(gam*px + f*pz)/(rho*(gam^2+f^2)) + U_zm");
        say("   假设：距平 p' 里占主导的是**纬向平均的环流胞**，它的经向梯度经 f 变成纬向风 ⇒");
        say("   与 U_zm **重复计数**（§62.3 修掉的是 p_ref 的那一份，这一份当时没查）。");
        say(String.format(LF, "  %-7s %10s %10s %10s %10s %10s %8s", "纬度", "U_zm", "u_tot", "u_pert", "  其中 u_px", "  其中 u_pz", "|pert/zm|"));
        for (int li = 0; li < nLatF; li++) {
            if (fL[li] <= 0) continue;
            say(String.format(LF, "  %-7.1f %10.3f %10.3f %10.3f %10.3f %10.3f %7.2f", fLat[li],
                fZm[li] / fL[li], fTot[li] / fL[li], fPert[li] / fL[li], fPx[li] / fL[li], fPz[li] / fL[li],
                Math.abs(fPert[li]) / Math.max(1e-9, Math.abs(fZm[li]))));
        }
        say(String.format(LF, "  F 汇总（长度加权、|lat| <= 62.5）：|u_pert|/|U_zm| = %.2f   |u_pz|/|u_px| = %.2f",
            Math.abs(gPert) / Math.max(1e-9, Math.abs(gZm)), Math.abs(gPz) / Math.max(1e-9, Math.abs(gPx))));
        say(String.format(LF, "    长度加权均值： U_zm = %+.3f   u_tot = %+.3f   u_px = %+.3f   u_pz = %+.3f",
            gZm / Math.max(1e-9, gL), gTot / Math.max(1e-9, gL), gPx / Math.max(1e-9, gL), gPz / Math.max(1e-9, gL)));
        say(String.format(LF, "    分解线性度自检 |u_tot-(U_zm+u_px+u_pz)| / |u_pert| = %.1f%% （迭代耦合 ⇒ 非零属正常，大了就说明分解不可用）",
            100.0 * Math.abs(gRes) / Math.max(1e-9, Math.abs(gPert))));
        say("");
        say(String.format(LF, "  E 汇总：长度加权 |u_model - u_obs| / |u_obs| = %.1f%%  （<=62.5 度、活跃海盆内）",
            100.0 * inhNum / Math.max(1e-30, inhDen)));
        say("  ⇒ 这个数越小，A1 越是「继承观测」；越大，越是模型自己长出来的。");
        say("");
        for (int b = 0; b < 3; b++) {
            tN += bN[b]; tAg += bAg[b]; tW += bW[b]; tWAg += bWAg[b];
            say(String.format(LF, "  %-26s 全部 %3d/%3d = %3.0f%%   强信号 %3d/%3d = %3.0f%%",
                BAND_NAME[b], bAg[b], bN[b], 100.0 * bAg[b] / Math.max(1, bN[b]),
                bWAg[b], bW[b], 100.0 * bWAg[b] / Math.max(1, bW[b])));
        }
        say(String.format(LF, "  %-26s 全部 %3d/%3d = %3.0f%%   强信号 %3d/%3d = %3.0f%%",
            "三带合计", tAg, tN, 100.0 * tAg / Math.max(1, tN), tWAg, tW, 100.0 * tWAg / Math.max(1, tW)));
        // A1 阈值（用户裁决 2026-09-13）：用**观测-观测上限**定，不拍脑袋。
        // 上限的算法：取两条**独立**再分析的洋面 10 m 纬向平均风
        //   ERA5 u10（0.25 度 -> 2.5 度）与 NCEP R1 uwnd.sig995（2.5 度），
        //   用**同一个** ERA5 sst 缺测掩膜取洋面纬向平均，
        //   逐月算 tau_x = rho*Cd*|u|u、curl = -d(tau_x)/dy，再按本项目**同一套海盆权重**比符号。
        //   数据通路（可复现）：
        //   https://apdrc.soest.hawaii.edu/dods/public_data/Reanalysis_Data/ERA5/monthly_2d/Surface.ascii
        //   https://apdrc.soest.hawaii.edu/dods/public_data/Reanalysis_Data/NCEP/NCEP/monthly/surface/uwnd.ascii
        //   结果：2015/2016/2017 年平 = 100.0 / 97.8 / 97.8%；**逐月 36 个月的中位数 = 97.8%**
        //        （min 91.2%、p25 96.3%、p75 99.3%、max 100.0%）；3 年平均 100.0%。
        //   ⇒ 取**逐月中位数 97.8%** 作上限（年平会退化成 100%，不具代表性）。
        //   ⚠ 记账：ERA5 与 NCEP R1 并非完全独立（同化相近的观测），所以 97.8% 是**上限的上限**。
        final double A1_CEILING_PCT = 97.8;
        double modelPct = 100.0 * tAg / Math.max(1, tN);
        say(String.format(LF, "  【A1 阈值】观测-观测上限 = %.1f%%（逐月中位数）⇒ 判据 = 0.9 x 上限 = %.1f%%", A1_CEILING_PCT, 0.9 * A1_CEILING_PCT));
        say(String.format(LF, "  【A1 判定】实测 %.1f%%（强信号 %.1f%%）  ⇒ %s", modelPct, 100.0 * tWAg / Math.max(1, tW),
            modelPct >= 0.9 * A1_CEILING_PCT ? "达标 ✓" : "**未达标**"));
        say(String.format(LF, "  [A1 旧口径·仅诊断] 逐海盆符号率 %.1f%%（含区域项）—— 不参与判定", modelPct));
        say("");
        say("C. 观测锚本身的形状（逐 5 度，年均 = 4 相位 curl 的平均）");
        say(String.format(LF, "  %-7s %13s %13s %13s", "纬度", "obs curl 年均", "obs 1 月", "obs 7 月"));
        for (int a = 0; a <= 90; a += 5) {
            say(String.format(LF, "  %-7d %13.3e %13.3e %13.3e", a,
                obsMeanCurl(a), obsCurlPhase(a, 0.0), obsCurlPhase(a, Math.PI)));
        }
        // ★ §705 纯新增量测（不进判据）：同一条锚在不同模板半宽 h 下的符号。
        //   输入表 uZmSea 间隔 = 5 度。若赤道带的符号结构随 h 翻号 ⇒ 那是插值折线的一阶导，不是物理。
        say("");
        say("C2. §705 差分尺度敏感度（同一条锚，不同 h；输入表间隔 5 度）");
        final double[] HS = {0.5, 1.25, 2.5, 5.0};
        say(String.format(LF, "  %-7s %13s %13s %13s %13s", "|lat|", "h=0.5", "h=1.25", "h=2.5", "h=5.0"));
        for (double a2 = 0.0; a2 <= 30.0; a2 += 2.5) {
            StringBuilder sb = new StringBuilder();
            for (double h : HS) {
                double c = 0; for (double th : PH4) c += obsCurlPhase(a2, th, h) / 4.0;
                sb.append(String.format(LF, " %13.3e", c));
            }
            say(String.format(LF, "  %-7.1f%s", a2, sb.toString()));
        }
        say("  —— 赤道带 (0,17.5] 内各 h 下的符号序列：");
        for (double h : HS) {
            StringBuilder sb = new StringBuilder(); int nPos = 0, n2 = 0;
            for (double a3 = 2.5; a3 <= 17.5; a3 += 2.5) {
                double c = 0; for (double th : PH4) c += obsCurlPhase(a3, th, h) / 4.0;
                sb.append(c > 0 ? "+" : "-"); n2++; if (c > 0) nPos++;
            }
            say(String.format(LF, "    h=%.2f  符号 %s   (+ 占比 %d/%d)", h, sb.toString(), nPos, n2));
        }
        // ★ §707 同口径量测（纯新增，不进判据）：
        //   锚是【纬向平均】量，其 d(tau_y)/dx 恒为 0；而 A 段是【逐经度求 curl 再在海盆内平均】，
        //   含区域项 d(tau_y)/dx（在单个海盆内平均并不归零）。这里按锚的口径算模型侧的
        //   【纬向平均 tau_x】，再以 h=2.5 度（5 度表的自洽尺度，§706）微分。
        say("");
        say("C4. §707 同口径：模型侧【纬向平均 tau_x】的 curl vs 锚（h=2.5 度）");
        final double H25 = 2.5;
        double[] latAxis = new double[61], txZm = new double[61];
        int[] cntZm = new int[61]; int nAx = 0;
        for (double ld = 0.0; ld <= 75.0; ld += H25) {
            int zz = WorldContract.zOfLat(ld);
            double acc = 0; int m = 0;
            // ★ §708：C4 必须扫【整个周长】。XMIN..XMAX 只是 24/40 Mm，
            //   北半球热带（0~22.5N）在那一段里全是陆地 ⇒ nPts=0 ⇒ 中心差分被零填充污染。
            //   锚是【全球纬向平均】，模型侧必须同样是全周期。
            for (int xx = -WorldContract.Z_CYCLE / 2; xx < WorldContract.Z_CYCLE / 2; xx += DX) {
                if (PlateField.isLandWithCell(xx, zz, SD, CELL)) continue;
                double t4 = 0;
                for (double th : PH4) t4 += Atmosphere.windStress(xx, zz, SD, CELL, th, GRAD)[0] / 4.0;
                acc += t4; m++;
            }
            latAxis[nAx] = ld; txZm[nAx] = (m > 0) ? acc / m : 0.0; cntZm[nAx] = m; nAx++;
        }
        say(String.format(LF, "  %-7s %6s %14s %14s %14s %6s", "|lat|", "nPts", "model <tx>", "model curl", "obs curl", "一致"));
        int c4n = 0, c4ag = 0;
        int c4w = 0, c4wag = 0;   // ★ §710 加权（海盆数）
        for (int i = 1; i + 1 < nAx; i++) {
            // ★ 守卫：任一邻元 nPts=0 时中心差分无意义，不拿它去比。
            if (cntZm[i] == 0 || cntZm[i - 1] == 0 || cntZm[i + 1] == 0) {
                say(String.format(LF, "  %-7.1f %6d %14s %14s %14s %6s", latAxis[i], cntZm[i], "-", "-", "-", "跳过"));
                continue;
            }
            double dTau = txZm[i + 1] - txZm[i - 1];
            double dLat = Math.toRadians(latAxis[i + 1] - latAxis[i - 1]);
            double curl = -dTau / dLat / WorldContract.R_EFF;
            double obs = 0; for (double th : PH4) obs += obsCurlPhase(latAxis[i], th, H25) / 4.0;
            boolean ok = curl * obs > 0;
            c4n++; if (ok) c4ag++;
            int bi = (int) Math.round((latAxis[i] + 87.5) / H25);
            int wgt = basinByLat[bi] + basinByLat[70 - bi];   // +|lat| 与 -|lat| 之和
            // ★ §711：域必须与 §710 预注册一致（|lat| <= 65）。
            //   第一版漏了这一步 ⇒ 分母把 67.5~72.5 也算进去（实测 79 而非 67）。
            if (latAxis[i] <= 65.0) { c4w += wgt; if (ok) c4wag += wgt; }
            say(String.format(LF, "  %-7.1f %6d %14.3e %14.3e %14.3e %6s",
                latAxis[i], cntZm[i], txZm[i], curl, obs, ok ? "OK" : "XX"));
        }
        say(String.format(LF, "  C4 等权  %d/%d = %.1f%%", c4ag, c4n, 100.0 * c4ag / Math.max(1, c4n)));
        // ★ §710 预注册判据：权重 = 海盆数（+/- 之和），上限 97.8% 与阈值 88.0% 不变。
        double c4pct = 100.0 * c4wag / Math.max(1, c4w);
        say(String.format(LF, "  C4 加权  %d/%d = %.1f%%   阈值 %.1f%%   ⇒ C4_WEIGHTED=%s",
            c4wag, c4w, c4pct, 0.9 * A1_CEILING_PCT, c4pct >= 0.9 * A1_CEILING_PCT ? "PASS" : "FAIL"));
        say(String.format(LF, "  [§710 预注册] 预测 58/64 = 90.6%% ⇒ PASS；实测 %d/%d = %.1f%%", c4wag, c4w, c4pct));
        // ★ §711：本门改用 §143 定义阈值时所用的【纬向平均】统计量（规格回归，阈值不变）。
        say(String.format(LF, "  GATE_A1_SIGN=%s", c4pct >= 0.9 * A1_CEILING_PCT ? "PASS" : "FAIL"));
        rep.close();
    }

    /** 观测锚：**逐相位求 curl，再对 4 个相位平均**（v1 在这里犯了"先平均风"的错）。 */
    static double obsMeanCurl(double latDeg) {
        double c = 0;
        for (double th : PH4) c += obsCurlPhase(latDeg, th) / 4.0;
        return c;
    }

    /**
     * 单个相位下、由洋面 10 m 纬向平均风给出的经向 curl。
     *
     * <p>⚠⚠ **v1/v2 在这里错了一个符号，v3 修正**（父 agent 自己犯的）：
     * 帐篷函数的**升支**上 d(latSigned)/dz = +1/R_EFF（两个半球相同），
     * 而 tau_x 是**带符号纬度**的偶函数 ⇒ d tau_x/d(latSigned) 是**奇函数** ⇒
     * `d tau_x/dz` 在两个半球**反号** ⇒ **curl 关于赤道是反对称的**。
     * v2 漏了 `sign(lat)`，于是南半球整片反号（实测 NH 100% 一致 / SH 0% 一致）。
     * （模型侧是对的：z 在南半球是「往赤道增大」，所以它的 d/dz 本来就带这个反号。）
     */
    static double obsCurlPhase(double latDeg, double theta) { return obsCurlPhase(latDeg, theta, 0.5); }

    /** §705 量测用重载：模板半宽 h 可变（默认 0.5 保持判据逐位不变）。 */
    static double obsCurlPhase(double latDeg, double theta, double h) {
        double a = Math.abs(latDeg);
        double uP = ZonalTables.uZmSea(Math.min(90.0, a + h), theta);
        double uM = ZonalTables.uZmSea(Math.max(0.0, a - h), theta);
        double txP = RHO * CD * Math.abs(uP) * uP;
        double txM = RHO * CD * Math.abs(uM) * uM;
        double sgn = latDeg >= 0.0 ? 1.0 : -1.0;
        return -sgn * ((txP - txM) / Math.toRadians(2 * h)) / WorldContract.R_EFF;
    }

    static void say(String s) { System.out.println("[P442] " + s); rep.println("[P442] " + s); }
}
