package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P499：**降水场的「形状」与预登记判据**（每 2.5 度一条，陆/海分面，两个至日）。
 *
 * <p>为什么需要它：P497/P498 只给 5 度一条的带平均，**带平均会掩盖互相抵消的误差**
 * （§244.1 的表看起来「陆地全在 ±17%」，逐纬度一摊开，30~45° 陆夏偏干 33~56%、
 * 陆冬偏湿 118~165%）。本探针把观测剖面（@@obs_lat_profile.txt@@，GPCP v2.3 LTM + ETOPO1）
 * 摆在旁边，逐纬度对形状。
 *
 * <p>用法（构建一次，跑多次；开关从命令行给）：
 * <pre>
 *   runprobe4.bat P499 --build-only
 *   java -Xmx6g -cp out probe.P499 0 A0      # SPLIT_ASCENT=false
 *   java -Xmx6g -cp out probe.P499 1 A1      # SPLIT_ASCENT=true
 * </pre>
 *
 * <p><b>判据（§244.5 预登记，跑之前写死）</b>：
 * <ol>
 *   <li>① 25~40°N 夏 **海洋** @@P >= 0.3@@ 的格点比例 >= 90%（观测带平均 2.356）</li>
 *   <li>② 15~30°N **陆地**季风指数落在观测 ±35% 内（@@2.871 x (1±0.35)@@ = +1.87 ~ +3.88）</li>
 *   <li>③ 30~40°N **陆地**冬季 P 落在观测 ±35% 内（@@1.378 x (1±0.35)@@ = 0.90 ~ 1.86）</li>
 * </ol>
 * 另打「观测海洋夏季最小值必须被复现」这条**新的**判别量：观测在 23.75~61.25°N 的
 * 海洋 JJA **最小值是 1.84 mm/day**（不是 0）。
 */
public class P499 {

    static final int SEED = 1022228679;
    static final long SD = SimTerrain.seedOf(SEED);
    static final int CELL = PlateField.PLATE_CELL;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P499] " + s); rep.flush(); System.out.println("[P499] " + s); System.out.flush(); }

    static final int NX = 120;
    // ★★ §779/§780 口径记录（**已量测，暂未落地**）：本常量 60_000_000 = 540 度当量，
    //   而 Zonal.java:32 = P683:21 = P914:17 = P296 都是 40_000_000 = 360 度，锚 GPCP 也是 360 度。
    //   WorldContract:6-11 权威陈述：「X 无限、无周期」⇒ 纬向平均**必须显式选窗口**。
    //   §780 实测 A/B（改 40e6）：idx25 +0.312 -> +0.334（Δ0.022）、① 带平均 4.985 -> 5.090、
    //   ③ 1.156 -> 1.196，**三门一个都没翻**。⇒ 口径不一致是真的，但量化上是**小效应**，
    //   不是任何门红的原因。**落地它必须与一次完整 22 支 A/B 同批进行**（它是验收探针，
    //   改它会变 PROBEFP）—— 因此这里暂时保持与已验证的 A/B 一致，留待同批落地。
    static final int XSPAN = 60_000_000;
    static final int GS = 500_000;
    static final int NROW = 25;           // 2.5 .. 62.5 步长 2.5（与 P296 的 band 上沿对齐）

    // 观测锚（GPCP v2.3 LTM 1991-2020 x ETOPO1，闭区间口径；见 refs/gen_land_anchors*.py）
    static final double OBS_IDX_1530_LAND = 2.871;
    static final double OBS_DJF_3040_LAND = 1.378;
    static final double OBS_ANN_2540_OCEAN = 2.356;
    static final double TOL = 0.35;

    public static void main(String[] args) throws Exception {
        boolean split = args.length > 0 && args[0].equals("1");
        String tag = args.length > 1 ? args[1] : (split ? "A1" : "A0");
        PrecipField.SPLIT_ASCENT = split;
        if (args.length > 2) PrecipField.U0_STORM = Double.parseDouble(args[2]);
        if (args.length > 3) PrecipField.EDDY_CLOSURE = Integer.parseInt(args[3]);
        // theta=0 是**夏至**；季节项还有 30 天（陆）/ 60 天（海）的滞后 ⇒ 「夏」的后继相位才是暖季。
        // 观测锚 GPCP 的 JJA 是 6/7/8 月，中心 7 月 16 日 = 夏至后 25 天 ⇒ theta = 25/365*2pi。
        // args[4] 给「夏」的相位（弧度），冬 = 夏 + pi。默认 0.0 = 夏至口径（旧）。
        double thS = args.length > 4 ? Double.parseDouble(args[4]) : 0.0;
        if (args.length > 5) PrecipField.Q_AT_SURFACE_TEMP = args[5].equals("1");
        if (args.length > 6) { PrecipField.SHALLOW_FLOOR = true; PrecipField.ALPHA_SH = Double.parseDouble(args[6]); }
        if (args.length > 7) PrecipField.EDDY_PHYS_GAIN = Double.parseDouble(args[7]);
        if (args.length > 8) PrecipField.ZONAL_SL_FROM_TABLE = args[8].equals("1");
        if (args.length > 9) PrecipField.COL_WATER_FROM_TABLE = args[9].equals("1");
        if (args.length > 10) PrecipField.EDDY_FULL_DIVERGENCE = args[10].equals("1");
        if (args.length > 11) PrecipField.EDDY_MASK_OUTSIDE = args[11].equals("1");
        double thW = thS + Math.PI;
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p499_report_" + tag + ".txt"), "UTF-8");
        say("P499：" + tag + "   夏相位 theta=" + String.format(LF, "%.5f", thS) + " (" + (thS == 0.0 ? "夏至" : "夏至后 " + Math.round(thS / (2 * Math.PI) * 365) + " 天") + ")   SPLIT_ASCENT=" + PrecipField.SPLIT_ASCENT
            + "   EDDY_PLACEMENT_FROM_OBS=" + PrecipField.EDDY_PLACEMENT_FROM_OBS);
        say(String.format(LF, "  观测剖面：refs/obs_lat_profile.txt（GPCP v2.3 LTM 1991-2020 x ETOPO1，cos-lat x 陆面分数）"));
        say("");
        say(String.format(LF, "  %6s | %8s %8s | %8s %8s   %s", "latN", "陆JJA", "陆DJF", "海JJA", "海DJF", "n陆/海"));
        say("  " + "-".repeat(74));
        say("  （下行是**观测**，上行是**模型**，逐纬度对照）");

        double[][] mLand = new double[NROW][2], mSea = new double[NROW][2];
        int[] nL = new int[NROW], nS = new int[NROW];
        // P296 口径的三条带（**全部下垫面**，陆海不分）—— 让 α 扫描不必每次跑 8.5 分钟的全量验收
        double[] mAll = new double[NROW * 2]; int[] nAll = new int[NROW];
        // 判据 ① 的逐点计数
        int nSea2540 = 0, nSea2540Ge = 0; double sumSea2540 = 0;
        double minSeaJja = Double.MAX_VALUE; double minSeaDjf = Double.MAX_VALUE;

        for (int i = 0; i < NROW; i++) {
            double la = 2.5 + i * 2.5;
            int z = WorldContract.zOfLat(la);
            double sL = 0, wL = 0, sS = 0, wS = 0;
            for (int q = 0; q < NX; q++) {
                int x = (int) ((long) q * XSPAN / NX);
                double k = Atmosphere.kappaAt(x, z, SD, CELL);
                boolean land = k > 0.8, sea = k < 0.2;
                if (!land && !sea) continue;
                double pj = PrecipField.mmPerDay(x, z, SD, CELL, thS, GS);
                double pd = PrecipField.mmPerDay(x, z, SD, CELL, thW, GS);
                mAll[i * 2] += pj; mAll[i * 2 + 1] += pd; nAll[i]++;
                if (land) { sL += pj; wL += pd; nL[i]++; }
                else {
                    sS += pj; wS += pd; nS[i]++;
                    if (la >= 25.0 - 1e-9 && la <= 40.0 + 1e-9) {
                        nSea2540++; sumSea2540 += pj; if (pj >= 0.3) nSea2540Ge++;
                    }
                }
            }
            mLand[i][0] = nL[i] > 0 ? sL / nL[i] : Double.NaN;
            mLand[i][1] = nL[i] > 0 ? wL / nL[i] : Double.NaN;
            mSea[i][0] = nS[i] > 0 ? sS / nS[i] : Double.NaN;
            mSea[i][1] = nS[i] > 0 ? wS / nS[i] : Double.NaN;
            if (la >= 20.0 && nS[i] > 0) {
                if (mSea[i][0] < minSeaJja) minSeaJja = mSea[i][0];
                if (mSea[i][1] < minSeaDjf) minSeaDjf = mSea[i][1];
            }
            say(String.format(LF, "  %6.2f | %8.2f %8.2f | %8.2f %8.2f   %d/%d",
                    la, mLand[i][0], mLand[i][1], mSea[i][0], mSea[i][1], nL[i], nS[i]));
            say(String.format(LF, "  %6s | %8s %8s | %8s %8s", "obs", obs(la, 0), obs(la, 1), obs(la, 2), obs(la, 3)));
        }

        // ---- 判据 ----
        double frac1 = nSea2540 > 0 ? 100.0 * nSea2540Ge / nSea2540 : Double.NaN;
        double mean1 = nSea2540 > 0 ? sumSea2540 / nSea2540 : Double.NaN;
        double idx25 = idx(15.0, 30.0, 2.5, mLand, true);
        double idx5 = idx(15.0, 30.0, 5.0, mLand, true);
        double djf3040 = idx(30.0, 40.0, 5.0, mLand, false);

        say("");
        say("G. 预登记判据（§244.5）");
        double sea40 = at(40.0, mSea, 0), sea45 = at(45.0, mSea, 0);
        double sea50 = at(50.0, mSea, 0), sea55 = at(55.0, mSea, 0), sea60 = at(60.0, mSea, 0);
        double rModel = sea45 > 1e-9 ? sea55 / sea45 : Double.NaN;
        say(String.format(LF, "  ④ 中纬雨带形状（**振幅无关**）：海洋 JJA 的 40/45/50/55/60 = %.2f / %.2f / %.2f / %.2f / %.2f",
                sea40, sea45, sea50, sea55, sea60));
        say("     观测 = 2.89 / 2.84 / 2.75 / 2.66 / 2.66   ⇒ r_obs = P(55)/P(45) = 0.937");
        say(String.format(LF, "     模型 r = %.3f（观测的 %.2f 倍 ⇒ 峰太尖）   %s", rModel, rModel / 0.937,
                (rModel >= 0.937 / 1.35 && rModel <= 0.937 * 1.35) ? "达标" : "**未达标**"));
        say(String.format(LF, "  [P296 口径] 赤道(2.5~12.5) 夏 %.2f / 冬 %.2f   副热带(27.5~37.5) 夏 %.2f / 冬 %.2f   中纬(47.5~62.5) 夏 %.2f / 冬 %.2f",
                band(mAll, nAll, 2.5, 12.5, 0), band(mAll, nAll, 2.5, 12.5, 1),
                band(mAll, nAll, 27.5, 37.5, 0), band(mAll, nAll, 27.5, 37.5, 1),
                band(mAll, nAll, 47.5, 62.5, 0), band(mAll, nAll, 47.5, 62.5, 1)));
        say("     GPCP 观测:  赤道 6.585 / 3.580   副热带 2.301 / 2.391   中纬 2.534 / 2.432   （独立锚，不许拟合）");
        say(String.format(LF, "  ① 25~40N 夏 海洋 P>=0.3 的格点比例 = %.1f%%  （要求 >= 90%%）   %s   [带平均 %.3f，观测 %.3f]",
                frac1, frac1 >= 90.0 ? "达标" : "**未达标**", mean1, OBS_ANN_2540_OCEAN));
        say(String.format(LF, "  ② 15~30N 陆 季风指数（2.5 度网格）= %+.3f   观测 %+.3f   允许 %.2f~%.2f   %s",
                idx25, OBS_IDX_1530_LAND, OBS_IDX_1530_LAND * (1 - TOL), OBS_IDX_1530_LAND * (1 + TOL),
                inBand(idx25, OBS_IDX_1530_LAND) ? "达标" : "**未达标**"));
        say(String.format(LF, "  ③ 30~40N 陆 冬 P = %.3f   观测 %.3f   允许 %.2f~%.2f   %s",
                djf3040, OBS_DJF_3040_LAND, OBS_DJF_3040_LAND * (1 - TOL), OBS_DJF_3040_LAND * (1 + TOL),
                inBand(djf3040, OBS_DJF_3040_LAND) ? "达标" : "**未达标**"));
        // ★ §739: expose the THREE criteria that P499 already carries (javadoc :30-32)
        //   as machine-readable GATE tokens. PURE ADDITION: no computation is changed,
        //   the pre-existing verdict is merely printed a second time in PASS/FAIL form.
        say(String.format(LF, "  GATE_SUBTROP_ZERO=%s    (25~40N summer ocean frac(P>=0.3) %.1f%% vs required 90%%)",
                frac1 >= 90.0 ? "PASS" : "FAIL", frac1));
        say(String.format(LF, "  GATE_MONSOON_IDX=%s     (15~30N land monsoon idx %+.3f in %.2f~%.2f)",
                inBand(idx25, OBS_IDX_1530_LAND) ? "PASS" : "FAIL", idx25,
                OBS_IDX_1530_LAND * (1 - TOL), OBS_IDX_1530_LAND * (1 + TOL)));
        say(String.format(LF, "  GATE_DJF_3040=%s        (30~40N land DJF P %.3f in %.2f~%.2f)",
                inBand(djf3040, OBS_DJF_3040_LAND) ? "PASS" : "FAIL", djf3040,
                OBS_DJF_3040_LAND * (1 - TOL), OBS_DJF_3040_LAND * (1 + TOL)));
        // ★ §747 PURE ADDITION (placed AFTER the gate prints so it cannot affect them):
        //   measure the SS251 floor P_sh = ALPHA_SH*E_sh/rho_w profile in P499's OWN caliber,
        //   so ALPHA_SH can be REVERSE-SOLVED.  SS251.2 forbids guessing a number first.
        //   Method: turn SHALLOW_CONDENSATE off -> DIAG[10] IS that form. No new formula.
        boolean scSaved = PrecipField.SHALLOW_CONDENSATE;
        PrecipField.SHALLOW_CONDENSATE = false;
        try { com.EyeOfHarmonyBuffer.sim.runtime.SimClimate.clearCache(); } catch (Throwable ignore) { }
        double shSum = 0; int shN = 0;
        for (int i2 = 0; i2 < NROW; i2++) {
            double la2 = 2.5 + i2 * 2.5;
            if (la2 < 25.0 - 1e-9 || la2 > 40.0 + 1e-9) continue;
            int z2 = WorldContract.zOfLat(la2);
            double s2 = 0; int n2 = 0;
            double sTs = 0, sQs = 0, sQb = 0, sVeff = 0, sK = 0;
            for (int q2 = 0; q2 < NX; q2++) {
                int x2 = (int) ((long) q2 * XSPAN / NX);
                int cell2 = CELL;
                double kk = Atmosphere.kappaAt(x2, z2, SD, cell2);
                if (kk >= 0.2) continue;
                double Ts2 = Atmosphere.surfaceTemp(x2, z2, SD, cell2, thS);
                double qs2 = PrecipField.qSat(Ts2);
                double qb2 = PrecipField.moisture(Ts2, 0.0, kk);
                double[] uu2 = Atmosphere.windAt(x2, z2, SD, cell2, thS, GS);
                double ve2 = Math.sqrt(uu2[0]*uu2[0] + uu2[1]*uu2[1] + PrecipField.V_GUST*PrecipField.V_GUST);
                PrecipField.mmPerDay(x2, z2, SD, CELL, thS, GS, true);
                s2 += PrecipField.DIAG.get()[10] * 86400.0 * 1000.0;
                sTs += Ts2; sQs += qs2; sQb += qb2; sVeff += ve2; sK += kk;
                n2++;
            }
            if (n2 > 0) { shSum += s2; shN += n2;
                double mTs = sTs/n2, mQs = sQs/n2, mQb = sQb/n2, mV = sVeff/n2, mK = sK/n2;
                say(String.format(LF, "  %6.2f | n%4d | P_sh %.4f | Ts %.2fK | qSat %.5f | q_BL %.5f | q/qSat %.3f | (1-k)qSat-q %+.5f | Veff %.2f | k %.3f",
                        la2, n2, s2 / n2, mTs, mQs, mQb, mQb / mQs, (1.0 - mK) * mQs - mQb, mV, mK)); }
        }
        PrecipField.SHALLOW_CONDENSATE = scSaved;
        say("");
        say("J. 751 blqG over 25-40N, LAND vs SEA (g = smoothstep01b(blqLastDh))");
        for (int i3 = 0; i3 < NROW; i3++) {
            double la3 = 2.5 + i3 * 2.5;
            if (la3 < 24.9 || la3 > 40.1) continue;
            int z3 = WorldContract.zOfLat(la3);
            double sg = 0, lg = 0; int sn = 0, ln = 0, sz = 0, lz = 0;
            for (int q3 = 0; q3 < NX; q3++) {
                int x3 = (int) ((long) q3 * XSPAN / NX);
                double k3 = Atmosphere.kappaAt(x3, z3, SD, CELL);
                if (k3 >= 0.2 && k3 <= 0.8) continue;
                PrecipField.mmPerDay(x3, z3, SD, CELL, thS, GS, true);
                double g3 = PrecipField.smoothstep01b(PrecipField.blqLastDh);
                if (k3 < 0.2) { sg += g3; sn++; if (g3 < 0.01) sz++; } else { lg += g3; ln++; if (g3 < 0.01) lz++; }
            }
            say(String.format(LF, "  lat %5.1f  sea n=%3d g=%6.3f zero=%5.2f  |  land n=%3d g=%6.3f zero=%5.2f",
                la3, sn, sg / Math.max(1, sn), (double) sz / Math.max(1, sn), ln, lg / Math.max(1, ln), (double) lz / Math.max(1, ln)));
        }
        // J2. 755: Sahara box (0-30E, 20-35N) -- k histogram and P_sh (SS251 form) by k bin.
        say("");
        say("J2. 755 Sahara box: k histogram + P_sh(ALPHA_SH) by k bin  [SHALLOW_CONDENSATE off = SS251 form]");
        PrecipField.SHALLOW_CONDENSATE = false;
        try { com.EyeOfHarmonyBuffer.sim.runtime.SimClimate.clearCache(); } catch (Throwable ig3) { }
        int[] hb = new int[5];
        double[] hs = new double[5];
        for (int zd = 20; zd <= 35; zd += 5) {
            int zz = WorldContract.zOfLat(zd);
            for (int xd = 0; xd <= 30; xd += 2) {
                int xx = (int) ((long) xd * 40_000_000L / 360L);
                double kk = Atmosphere.kappaAt(xx, zz, SD, CELL);
                PrecipField.mmPerDay(xx, zz, SD, CELL, thS, GS, true);
                double pf = PrecipField.DIAG.get()[10] * 86400.0 * 1000.0;
                int bi = kk < 0.2 ? 0 : (kk < 0.5 ? 1 : (kk < 0.8 ? 2 : (kk < 0.95 ? 3 : 4)));
                hb[bi]++; hs[bi] += pf;
            }
        }
        PrecipField.SHALLOW_CONDENSATE = scSaved;
        try { com.EyeOfHarmonyBuffer.sim.runtime.SimClimate.clearCache(); } catch (Throwable ig4) { }
        for (int b = 0; b < 5; b++)
            say(String.format(LF, "  kbin%d n=%4d  P_sh mean=%8.4f mm/day", b, hb[b], hs[b] / Math.max(1, hb[b])));
        try { com.EyeOfHarmonyBuffer.sim.runtime.SimClimate.clearCache(); } catch (Throwable ignore2) { }
        double shMean = shN > 0 ? shSum / shN : Double.NaN;
        say(String.format(LF, "  BAND 25~40N ocean  nPts=%d   P_sh mean = %.4f   anchor = %.3f",
                shN, shMean, OBS_ANN_2540_OCEAN));
        say(String.format(LF, "  ratio P_sh/anchor = %.3f   =>  REVERSE-SOLVED ALPHA_SH = %.3f   (fitted %.2f)",
                shMean / OBS_ANN_2540_OCEAN,
                PrecipField.ALPHA_SH * OBS_ANN_2540_OCEAN / shMean, PrecipField.ALPHA_SH));
        say("");
        say("H. 新的判别量：**观测的「海洋降水地板」**");
        say(String.format(LF, "  观测 20~62.5N 海洋 JJA 最小值 = 1.84 mm/day（23.75N）；DJF 最小值 = 1.17（18.75N）"));
        say(String.format(LF, "  模型 20~62.5N 海洋 JJA 最小值 = %.2f mm/day；DJF 最小值 = %.2f", minSeaJja, minSeaDjf));
        say(String.format(LF, "  （P497 口径校验：15/20/25/30 四点的陆地季风指数 = %+.3f，基线应为 +3.36）", idx5));
        rep.flush(); rep.close();
        System.out.println("JAVA_EXIT=0");
    }

    /** P296 口径的纬度带均值（**全部下垫面**，等纬距）。 */
    static double band(double[] mAll, int[] nAll, double lo, double hi, int col) {
        double s = 0; int n = 0;
        for (int i = 0; i < nAll.length; i++) {
            double la = 2.5 + i * 2.5;
            if (la < lo - 1e-9 || la > hi + 1e-9 || nAll[i] == 0) continue;
            s += mAll[i * 2 + col] / nAll[i]; n++;
        }
        return n > 0 ? s / n : Double.NaN;
    }

    static boolean inBand(double v, double obs) { return v >= obs * (1 - TOL) && v <= obs * (1 + TOL); }

    /** 取某纬度那一行的陆/海值（col 0=夏 1=冬）。 */
    static double at(double la, double[][] m, int col) {
        int i = (int) Math.round((la - 2.5) / 2.5);
        return (i < 0 || i >= m.length) ? Double.NaN : m[i][col];
    }

    /** 纬度带上的陆/海平均（把落在带内的行取等权平均，与 P497 的「等纬距」同口径）。 */
    static double idx(double lo, double hi, double step, double[][] m, boolean summerMinusWinter) {
        double s = 0; int n = 0;
        for (double la = lo; la <= hi + 1e-9; la += step) {
            int i = (int) Math.round((la - 2.5) / 2.5);
            if (i < 0 || i >= m.length) continue;
            s += summerMinusWinter ? (m[i][0] - m[i][1]) : m[i][1];
            n++;
        }
        return n > 0 ? s / n : Double.NaN;
    }

    /** 观测剖面：从 refs/obs_lat_profile.txt 抄进来的 24 行（脚本生成，见 §245）。 */
    static final double[][] OBS = {
        //   lat,  LAND_JJA, LAND_DJF, OCEAN_JJA, OCEAN_DJF
        {  1.25,  5.357, 4.737, 2.954, 4.114 },
        {  3.75,  5.992, 3.047, 4.712, 5.266 },
        {  6.25,  6.501, 1.729, 7.603, 5.497 },
        {  8.75,  6.435, 0.907, 8.193, 3.848 },
        { 11.25,  6.445, 0.558, 6.210, 2.156 },
        { 13.75,  5.234, 0.360, 4.401, 1.448 },
        { 16.25,  3.920, 0.419, 3.525, 1.206 },
        { 18.75,  3.268, 0.364, 3.094, 1.173 },
        { 21.25,  3.358, 0.309, 2.680, 1.176 },
        { 23.75,  3.736, 0.446, 1.844, 1.338 },
        { 26.25,  3.372, 0.547, 1.872, 1.770 },
        { 28.75,  2.614, 0.722, 2.144, 2.333 },
        { 31.25,  2.347, 1.171, 2.301, 2.947 },
        { 33.75,  2.071, 1.380, 2.450, 3.524 },
        { 36.25,  1.774, 1.545, 2.602, 4.193 },
        { 38.75,  1.584, 1.437, 2.893, 4.612 },
        { 41.25,  1.779, 1.400, 2.966, 4.756 },
        { 43.75,  1.937, 1.480, 2.841, 4.541 },
        { 46.25,  2.194, 1.479, 2.762, 4.147 },
        { 48.75,  2.365, 1.541, 2.745, 4.016 },
        { 51.25,  2.513, 1.505, 2.866, 4.058 },
        { 53.75,  2.565, 1.554, 2.658, 3.821 },
        { 56.25,  2.526, 1.555, 2.723, 3.603 },
        { 58.75,  2.383, 1.536, 2.659, 3.431 },
    };

    /** 取最接近的观测行（模型 2.5/5 度网格落到观测格点中心之间时按最近取）。 */
    static String obs(double la, int col) {
        int best = 0; double bd = 1e9;
        for (int i = 0; i < OBS.length; i++) {
            double d = Math.abs(OBS[i][0] - la);
            if (d < bd) { bd = d; best = i; }
        }
        if (bd > 2.0) return "-";
        return String.format(LF, "%.3f", OBS[best][col + 1]);
    }
}
