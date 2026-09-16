package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanWiring;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P284：B3 的**可信采样**。
 *
 * <p>P260/P282 的 20~40 度陆地样本只有 250 点、平均 kappa=0.42 —— 全是近岸过渡点，
 * 不是大陆内部。本探针把经度跨度从 11,000 km 放大到 40,000 km，
 * 并按 kappa 分层（内陆 / 过渡 / 海岸 / 海洋），把"季风到底是哪一类点上的现象"分开看。
 */
public class P284 {

    static final int SEED = 1022228679;
    static final int XSTEP = 20_000, NX = 2001;      // x = 0 .. 40,000 km
    static final int ZSTEP = 200_000, R0 = 8, R1 = 25; // z = 1.6M .. 5.0M  => lat 14.4 .. 45.0
    static final int GRAD = 500_000;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    static final int V_AMP_SIN = 1, V_CELL_REF0 = 2, V_NO_CELL = 4, V_NO_UZM = 8, V_CELL_LOC2 = 16, V_CELL_LOC4 = 32;
    static final int[] VARS = {0, V_NO_UZM, V_CELL_REF0, V_CELL_LOC2, V_CELL_LOC4};
    static final String[] VNAME = {"基线", "无U_zm", "cell参考0", "cell局部2Mkm", "cell局部4Mkm"};
    static final String[] KCLS = {"内陆 k>=0.8", "过渡 0.5~0.8", "海岸 0.2~0.5", "海洋 k<0.2"};

    // ================= D76（2026-09-16）：分片模式 =================
    // 为什么要分片：本探针的 V0 是**冷 pass**（第一次把所有瓦片解出来，3115 s），
    // 而 V1~V4 只有 27~30 s（瓦片已缓存）⇒ 整个验收批次的墙钟 96.5% 花在这里。
    // 每个格点的计算**只依赖 (x, z, seed)** ⇒ 按 z 行切分不影响任何单点结果。
    //
    // 三条纪律：
    //   1. **默认路径（无参数）逐位不变** —— CLO=0 / CHI=NX-1 / XOFF=0 / NK=NX / ROFF=0 / NR=NX；
    //   2. 分片只写【原始整数计数】与【kappa 原值】，**不写任何格式化后的百分比** ——
    //      百分比一律由 merge 从【求和后的整数】用与默认路径相同的公式算出（整数求和 ⇒ 精确）；
    //   3. `merge` 必须能重建**任何顺序敏感的浮点聚合**（`kappa 均值`），所以 kappa 原值要落盘，
    //      由 merge 按全局行序重新求和 ⇒ 与单片跑**逐位相同**。
    // ⚠⚠ 分片方向是 **x（列）**，不是 z（行）。为什么（2026-09-16 实测 + 推导）：
    //   `grad()` 的两个差分探针在 z 与 x 上各伸出 GRAD = 500 km：
    //     · 按 z 分片：半宽 500 km / TILE_Z 50 km = **10 条瓦片行**；一片只有 3 行时，
    //       它仍要解 3 + 20 = 23 条瓦片行，而全量只有 88 条 ⇒ **每片都要干全量的约 1/3**。
    //       实测：低 z 的那片 986 s（已经接近全量 3313 s 的 30%），高 z 的那片 1790 s 还没完。
    //     · 按 x 分片：500 km / TILE_X 100 km = **5 条瓦片列**；x 总跨度 40,000 km = 400 列，
    //       8 片时每片 50 列 + 10 列探测 = 60 列 ⇒ **每片只占全量的 15%**，而且**负载天然均衡**
    //       （每片都覆盖全部 18 条纬度行，地形多样性一样）。
    //   ⇒ 预期墙钟 ≈ 0.15 x 3313 s ≈ 500 s。
    static int CLO = 0;             // 本片的第一列（默认 0）
    static int CHI = NX - 1;        // 本片的最后一列（默认 NX-1）
    static boolean SHARD = false;   // 分片模式：不打印任何 [P284] 行，只写 part 文件
    static PrintStream part = null;
    /** kappa 环带半宽（列）：要覆盖 ref 窗口 (200 列) 与 grad 步长 (GRAD/XSTEP 列)。 */
    static final int ST = GRAD / XSTEP;             // = 25 列
    static final int KHALO = 200 + ST;              // = 225 列
    static int XOFF = 0;            // kappa 数组的左边界（全局列号；默认 0）
    static int NK = NX;             // kappa 数组的列数（默认 NX）
    static int ROFF = 0;            // ref 数组的左边界（全局列号；默认 0）
    static int NR = NX;             // ref 数组的列数（默认 NX）

    public static void main(String[] args) throws Exception {
        // ---- D76：分片入口（默认无参数 ⇒ 下面一行都不走） ----
        if (args.length > 0 && args[0].equals("merge")) { merge(args); return; }
        if (args.length >= 3 && args[0].equals("profile")) {
            profile(Integer.parseInt(args[1]), Integer.parseInt(args[2])); return;
        }
        if (args.length >= 4 && args[0].equals("shard")) {
            SHARD = true;
            CLO = Integer.parseInt(args[1]);
            CHI = Integer.parseInt(args[2]);
            part = new PrintStream(new File(args[3]), "UTF-8");
            // kappa 环带按**全局**列号夹紧（第一片/最后一片要贴边），
            // ref 数组只需覆盖 [CLO-ST, CHI+ST]（grad 只读到这里）。
            XOFF = Math.max(0, CLO - KHALO);
            int xhi = Math.min(NX - 1, CHI + KHALO);
            NK = xhi - XOFF + 1;
            ROFF = CLO - ST;
            NR = (CHI - CLO + 1) + 2 * ST;
        }

        // ── 第三步接线（§162）：验收必须在**生产口径**下跑 ──
        // 生产由 WorldChunkManagerTalos2 -> OceanWiring.onWorld 装 SST'；这里调**同一个入口**，
        // 保证「验收测的」与「生产跑的」是同一段代码（否则就是口径偏移）。
        // 预热是后台线程，本探针不等待 —— 它自己在采样时按行懒解，代价一样。
        OceanWiring.onWorld(SEED);
        // 分片模式保持静默：这一行由 merge 统一打印一次（否则 6 个分片会打 6 遍）。
        if (!SHARD) System.out.println("[P284] 接线口径：installedSeed=" + com.EyeOfHarmonyBuffer.sim.ocean.OceanField.installedSeed()
            + "  ENABLED=" + com.EyeOfHarmonyBuffer.sim.ocean.OceanField.ENABLED);
        // 分片模式下 rep 保持 null：任何漏改的 say() 会**立刻 NPE**（响亮地失败，而不是静默错）。
        rep = SHARD ? null : new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p284_report.txt"), "UTF-8");
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0);
        double thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        int NZ = R1 - R0 + 1, nOwn = CHI - CLO + 1, nP = NZ * nOwn;   // D76：默认 nOwn=NX ⇒ 与改前相同

        if (!SHARD) {
        say("P284：B3 的可信采样（x 跨度 40,000 km，按 kappa 分层）");
        say(String.format(LF, "  dx=%d km x 0..%d km   dz=%d km  z=%d..%d km（lat %.1f~%.1f）  格点 %d  GRAD=%d km",
            XSTEP/1000, (NX-1)*XSTEP/1000, ZSTEP/1000, R0*ZSTEP/1000, R1*ZSTEP/1000,
            Math.toDegrees(WorldContract.latOf(R0*ZSTEP)), Math.toDegrees(WorldContract.latOf(R1*ZSTEP)), nP, GRAD/1000));
        say("");
        }

        // ---- kappa 环带：含自己那一段，外加 grad（±ST 列）与 ref 窗口（±200 列）需要的边界列 ----
        double[] kapH = new double[NZ * NK];
        for (int r = R0; r <= R1; r++) {
            int baseH = (r - R0) * NK;
            for (int c = XOFF; c < XOFF + NK; c++) {
                kapH[baseH + (c - XOFF)] = Atmosphere.kappaAt(c * XSTEP, r * ZSTEP, SEED, cell);
            }
        }
        int[] px_ = new int[nP], pz_ = new int[nP];
        double[] kap = new double[nP];
        boolean[] land = new boolean[nP];
        int k = 0;
        for (int r = R0; r <= R1; r++) {
            for (int c = CLO; c <= CHI; c++) {
                px_[k] = c * XSTEP; pz_[k] = r * ZSTEP;
                kap[k] = kapH[(r - R0) * NK + (c - XOFF)];
                land[k] = PlateField.isLandWithCell(c * XSTEP, r * ZSTEP, SEED, cell);
                k++;
            }
        }
        // 局部 <kappa> 参考值（用户裁决 3A 的候选）：沿同一条 z 线的**纬向窗口均值**。
        // 半宽 2000 km / 4000 km，采样步长 240 km（kappa 场在 800 km 尺度上光滑）。
        // 直接复用已算好的 kap[]，**不额外调用 kappaAt** ⇒ 预计算几乎免费。
        double[] ref2 = new double[nP], ref4 = new double[nP];
        for (int r = R0; r <= R1; r++) {
            int baseH = (r - R0) * NK;      // kappa 环带（含边界列）
            int baseO = (r - R0) * nOwn;    // 本片自己的点
            for (int c = CLO; c <= CHI; c++) {
                ref2[baseO + (c - CLO)] = windowMean(kapH, baseH, c, 100, 12, XOFF);
                ref4[baseO + (c - CLO)] = windowMean(kapH, baseH, c, 200, 24, XOFF);
            }
        }

        int nLand = 0, nInt = 0;
        for (int i = 0; i < nP; i++) {
            if (land[i]) nLand++;
            if (kap[i] >= 0.8) nInt++;
        }
        if (SHARD) {
            // 分片：只落**原始量**。kappa 原值必须落盘，因为 `mean(kap)` 是顺序敏感的浮点聚合，
            // 必须由 merge 按【全局行序】重新求和才能与单片跑逐位相同（浮点加法不满足结合律）。
            // ⚠ K 行必须带**全局列序下的下标**：x 分片时本片的点在全量数组里是**不连续**的，
            // 所以 merge 只能按下标摆放（z 分片时连续，但统一按下标更简单也更不容易错）。
            part.println("META " + nP + " " + nLand);
            for (int r = R0; r <= R1; r++) {
                for (int c = CLO; c <= CHI; c++) {
                    part.println("K " + ((r - R0) * NX + c) + " "
                        + Double.toString(kapH[(r - R0) * NK + (c - XOFF)]));
                }
            }
        } else {
            say(String.format(LF, "  采样：陆地 %d (%.1f%%)，其中内陆 k>=0.8 有 %d (%.1f%%)；kappa 均值 %.3f",
                nLand, nLand*100.0/nP, nInt, nInt*100.0/nP, mean(kap)));
            say("");
        }

        for (int vi = 0; vi < VARS.length; vi++) {
            int var = VARS[vi];
            double[] refLine = (var & V_CELL_LOC2) != 0 ? ref2 : ((var & V_CELL_LOC4) != 0 ? ref4 : null);
            long t0 = System.nanoTime();
            double[] us = new double[nP], vs = new double[nP], uw = new double[nP], vw = new double[nP];
            double[] pS = new double[nP], pW = new double[nP];
            int cap = 0;
            for (int i = 0; i < nP; i++) {
                int x = px_[i], z = pz_[i];
                double lat = WorldContract.latOf(z);
                int rr = R0 + i / nOwn, cc = CLO + i % nOwn;   // D76：默认 nOwn=NX, CLO=0
                double[] a = grad(cc, rr, SEED, cell, thS, var, refLine);
                double[] b2 = grad(cc, rr, SEED, cell, thW, var, refLine);
                double refC = refLine == null ? 0.0 : refLine[i];
                pS[i] = pAnom(x, z, SEED, cell, thS, var, refC);
                pW[i] = pAnom(x, z, SEED, cell, thW, var, refC);
                double[] w1 = Atmosphere.wind(a[0], a[1], kap[i], lat, thS);
                double[] w2 = Atmosphere.wind(b2[0], b2[1], kap[i], lat, thW);
                if ((var & V_NO_UZM) != 0) {
                    w1[0] -= ZonalTables.uZm(Math.toDegrees(lat) - zmShift(thS));
                    w2[0] -= ZonalTables.uZm(Math.toDegrees(lat) - zmShift(thW));
                }
                if (Math.hypot(w1[0],w1[1]) > Atmosphere.U_MAX-1e-9 || Math.hypot(w2[0],w2[1]) > Atmosphere.U_MAX-1e-9) cap++;
                us[i] = w1[0]; vs[i] = w1[1]; uw[i] = w2[0]; vw[i] = w2[1];
            }
            double ms = (System.nanoTime()-t0)/1e6;
            if (SHARD) {
                // 只落原始量：点数、撞上限计数（整数）、本片耗时。
                // 撞上限的【百分比】由 merge 用 (Σcap)/(ΣnP) 算 ⇒ 与单片跑逐位相同。
                part.println("V " + vi + " " + nP + " " + cap + " " + String.format(LF, "%.0f", ms));
            } else {
            say(String.format(LF, "V%d. %s   （%d 点 x 2 季，%.0f ms，撞上限 %.2f%%）", vi, VNAME[vi], nP, ms, cap*100.0/nP));
            say(String.format(LF, "    %-9s %-12s %7s %8s %9s %9s", "纬度", "kappa 层", "点数", "反相%", "du>0比例", "p反号%"));
            }
            for (int bi = 0; bi < 6; bi++) {
                double lo = 15 + bi * 5, hi = lo + 5;
                for (int ci = 0; ci < 4; ci++) {
                    int n = 0, rev = 0, duP = 0, pRev = 0;
                    for (int i = 0; i < nP; i++) {
                        double ld = Math.toDegrees(WorldContract.latOf(pz_[i]));
                        if (ld < lo || ld >= hi) continue;
                        int cl = kclass(kap[i]);
                        if (cl != ci) continue;
                        if (us[i]*uw[i] + vs[i]*vw[i] < 0) rev++;
                        if (us[i] - uw[i] > 0) duP++;
                        if (pS[i]*pW[i] < 0) pRev++;
                        n++;
                    }
                    if (SHARD) {
                        // ⚠ 分片【不能】套用 `n < 5` 过滤 —— 本片的 n 可以小于 5，但合并后可能 >= 5。
                        // 过滤只能发生在 merge 里（用合并后的 n）。
                        part.println("C " + vi + " " + (bi * 4 + ci) + " " + n + " " + rev + " " + duP + " " + pRev);
                    } else {
                    if (n < 5) continue;
                    say(String.format(LF, "    %-9s %-12s %7d %7.1f%% %8.1f%% %8.1f%%",
                        (int)lo + "~" + (int)hi, KCLS[ci], n, rev*100.0/n, duP*100.0/n, pRev*100.0/n));
                    }
                }
            }
            if (!SHARD) {
            say("");
            // 汇总：20~40 度
            say(String.format(LF, "    汇总 20~40 度："));
            }
            for (int ci = 0; ci < 4; ci++) {
                int n = 0, rev = 0, duP = 0, pRev = 0;
                for (int i = 0; i < nP; i++) {
                    double ld = Math.toDegrees(WorldContract.latOf(pz_[i]));
                    if (ld < 20 || ld >= 40) continue;
                    if (kclass(kap[i]) != ci) continue;
                    if (us[i]*uw[i] + vs[i]*vw[i] < 0) rev++;
                    if (us[i] - uw[i] > 0) duP++;
                    if (pS[i]*pW[i] < 0) pRev++;
                    n++;
                }
                if (SHARD) {
                    part.println("S " + vi + " " + ci + " " + n + " " + rev + " " + duP + " " + pRev);
                } else {
                if (n == 0) continue;
                say(String.format(LF, "      %-12s n=%6d  反相 %5.1f%%  du>0 %5.1f%%  p反号 %5.1f%%",
                    KCLS[ci], n, rev*100.0/n, duP*100.0/n, pRev*100.0/n));
                }
            }
            if (!SHARD) say("");
        }
        if (SHARD) { part.close(); return; }
        rep.close();
    }

    static int kclass(double kv) { return kv >= 0.8 ? 0 : (kv >= 0.5 ? 1 : (kv >= 0.2 ? 2 : 3)); }

    /** 第 base+c 列周围、半宽 halfCols 列、步长 step 列的窗口均值（越界钳位）。 */
    static double windowMean(double[] kap, int base, int c, int halfCols, int step, int xoff) {
        double s = 0; int n = 0;
        for (int j = -halfCols; j <= halfCols; j += step) {
            int q = c + j; if (q < 0) q = 0; if (q >= NX) q = NX - 1;   // 夹紧在**全局**列范围
            s += kap[base + (q - xoff)]; n++;
        }
        return s / n;
    }
    static double mean(double[] v) { double s = 0; for (double x : v) s += x; return s / v.length; }
    static double zmShift(double theta) { return Math.toDegrees(Atmosphere.DELTA_PHI0) * Math.cos(theta); }

    static double pAnom(int x, int z, long seed, int cell, double theta, int var, double refIn) {
        // ⚠ §94：基线路径（var==0）**必须直接走生产入口**。
        // 本探针原来手工重写了 cell 项，§86 给 cellPressure 加 tropicGate 之后它就**过期了**
        // —— 导致"同纬海洋 30.9%"是**无门控**读数（真值 25.8%）。手工版只留给反事实分支。
        if (var == 0) return Atmosphere.pressureAnomaly(x, z, seed, cell, theta);
        double lat = WorldContract.latOf(z);
        double k = Atmosphere.clamp01(Atmosphere.kappaAt(x, z, seed, cell));
        double s = Math.sin(lat);
        double psiDays = Atmosphere.PSI_SEA_DAYS + (Atmosphere.PSI_LAND_DAYS - Atmosphere.PSI_SEA_DAYS) * k;
        double psi = 2.0 * Math.PI * psiDays / WorldContract.DAYS_PER_YEAR;
        double hemi = lat >= 0.0 ? 0.0 : Math.PI;
        double latDeg = Math.toDegrees(lat);
        double aSea0 = ZonalTables.aSea(latDeg);
        double amp = aSea0 + (ZonalTables.aLand(latDeg) - aSea0) * k;
        double tAnom = amp * Math.cos(theta - psi - hemi);
        double tZm = Atmosphere.tZonalMean(lat);
        double elev = PlateField.elevationWithCell(x, z, seed, cell);
        // ⚠ D40：手工反事实分支原来**漏了 (1-k)*sstAnom 与 tropicGate**，与生产入口不同源。
        // 已补齐（SST_PROVIDER=null 时 sstAnom 恒为 0，但口径必须一致）。
        double tSfc = tZm + tAnom - Atmosphere.GAMMA * Math.max(0.0, elev) * k
                    + (1.0 - k) * Atmosphere.sstAnom(x, z);
        double h = Math.max(0.0, elev) * Atmosphere.PLATEAU_AMP * k;
        double pTherm = -Atmosphere.K_P * Atmosphere.CHI * (tSfc + Atmosphere.GAMMA * h - tZm);
        if ((var & V_NO_CELL) != 0) return pTherm;
        double shifted = Math.toDegrees(lat - Atmosphere.CELL_MIGRATION * Math.cos(theta - Atmosphere.CELL_LAG));
        double ref;
        if ((var & V_CELL_REF0) != 0) ref = 0.0;
        else if ((var & (V_CELL_LOC2 | V_CELL_LOC4)) != 0) ref = refIn;
        else ref = Atmosphere.KAPPA_MEAN;
        return pTherm + Atmosphere.CELL_GAIN * ZonalTables.carrier(shifted)
             * Atmosphere.tropicGate(lat) * (ref - k);   // ⚠ D40 修复：补回 tropicGate
    }

    static double[] grad(int c, int r, long seed, int cell, double theta, int var, double[] refLine) {
        int x = c * XSTEP, z = r * ZSTEP, base = (r - R0) * NR, st = ST;   // D76：默认 NR=NX
        double rc = 0, rm = 0, rp = 0;
        if (refLine != null) {
            int ci = c - ROFF;                                   // D76：默认 ROFF=0 ⇒ ci=c
            rc = refLine[base + ci];
            rm = refLine[base + Math.max(0, ci - st)];
            rp = refLine[base + Math.min(NR - 1, ci + st)];
        }
        double dx = (pAnom(x+GRAD, z, seed, cell, theta, var, rp) - pAnom(x-GRAD, z, seed, cell, theta, var, rm)) / (2.0*GRAD);
        double dz = (pAnom(x, z+GRAD, seed, cell, theta, var, rc) - pAnom(x, z-GRAD, seed, cell, theta, var, rc)) / (2.0*GRAD);
        return new double[]{dx, dz};
    }

    static void say(String s) { System.out.println("[P284] " + s); rep.println("[P284] " + s); }

    // ================= D76：merge =================
    /**
     * 把若干分片合并成**与单片跑逐位相同**的输出。
     *
     * <p>三条禁令（违反任何一条都会让读数悄悄漂掉）：
     * <ol>
     *   <li>**不许**在这里重算任何 `us/vs/uw/vw/pS/pW` —— 那些是昂贵的部分，也正是不该被复制的地方；
     *       表格只用分片交回来的**整数计数**求和（整数加法精确）；
     *   <li>**不许**用分片的 n 去套 `n < 5` / `n == 0` 过滤 —— 过滤只能用**合并后**的 n；
     *   <li>**不许**让分片计算任何百分比 —— 全部由这里从求和后的整数算，
     *       用的是**与默认路径逐字相同**的公式（下面两条 String.format 与 main 里那两条一致）。
     * </ol>
     *
     * <p>`kappa 均值` 是这里唯一顺序敏感的浮点聚合：分片把 kappa **原值**落盘，
     * 这里按【全局行序】重建数组后再左折叠求和 ⇒ 与单片跑逐位相同。
     */
    static void merge(String[] args) throws Exception {
        OceanWiring.onWorld(SEED);
        System.out.println("[P284] 接线口径：installedSeed=" + com.EyeOfHarmonyBuffer.sim.ocean.OceanField.installedSeed()
            + "  ENABLED=" + com.EyeOfHarmonyBuffer.sim.ocean.OceanField.ENABLED);
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p284_report.txt"), "UTF-8");

        final int nFull = (R1 - R0 + 1) * NX;
        double[] kapAll = new double[nFull];
        boolean[] seen = new boolean[nFull];
        int nLand = 0, nParts = 0, npSum = 0;
        int[] cellN = new int[VARS.length * 24], cellRev = new int[VARS.length * 24];
        int[] cellDuP = new int[VARS.length * 24], cellPR = new int[VARS.length * 24];
        int[] sumN = new int[VARS.length * 4], sumRev = new int[VARS.length * 4];
        int[] sumDuP = new int[VARS.length * 4], sumPR = new int[VARS.length * 4];
        double[] vMs = new double[VARS.length];
        int[] vCap = new int[VARS.length];

        for (int a = 1; a < args.length; a++) {
            BufferedReader br = new BufferedReader(new FileReader(args[a]));
            String ln;
            int np = -1;
            while ((ln = br.readLine()) != null) {
                if (ln.startsWith("META ")) {
                    String[] t = ln.split(" ");
                    np = Integer.parseInt(t[1]);
                    nLand += Integer.parseInt(t[2]);
                    npSum += np;
                    nParts++;
                } else if (ln.startsWith("K ")) {
                    // K 行自带**全局下标**（x 分片时本片的点在全量数组里不连续）
                    String[] t = ln.split(" ");
                    int idx = Integer.parseInt(t[1]);
                    kapAll[idx] = Double.parseDouble(t[2]);
                    seen[idx] = true;
                } else if (ln.startsWith("V ")) {
                    String[] t = ln.split(" ");
                    int vi = Integer.parseInt(t[1]);
                    vCap[vi] += Integer.parseInt(t[3]);
                    vMs[vi] += Double.parseDouble(t[4]);
                } else if (ln.startsWith("C ")) {
                    String[] t = ln.split(" ");
                    int q = Integer.parseInt(t[1]) * 24 + Integer.parseInt(t[2]);
                    cellN[q] += Integer.parseInt(t[3]);
                    cellRev[q] += Integer.parseInt(t[4]);
                    cellDuP[q] += Integer.parseInt(t[5]);
                    cellPR[q] += Integer.parseInt(t[6]);
                } else if (ln.startsWith("S ")) {
                    String[] t = ln.split(" ");
                    int q = Integer.parseInt(t[1]) * 4 + Integer.parseInt(t[2]);
                    sumN[q] += Integer.parseInt(t[3]);
                    sumRev[q] += Integer.parseInt(t[4]);
                    sumDuP[q] += Integer.parseInt(t[5]);
                    sumPR[q] += Integer.parseInt(t[6]);
                }
            }
            br.close();
        }

        int nP = nFull;
        int nInt = 0;
        double sumKap = 0.0;
        for (int i = 0; i < nFull; i++) {
            if (!seen[i]) throw new IllegalStateException("merge: 分片没有覆盖全部格点，缺 i=" + i);
            if (kapAll[i] >= 0.8) nInt++;
            sumKap += kapAll[i];        // 与 mean() 同一顺序 ⇒ 逐位相同
        }

        say("P284：B3 的可信采样（x 跨度 40,000 km，按 kappa 分层）");
        say(String.format(LF, "  dx=%d km x 0..%d km   dz=%d km  z=%d..%d km（lat %.1f~%.1f）  格点 %d  GRAD=%d km",
            XSTEP/1000, (NX-1)*XSTEP/1000, ZSTEP/1000, R0*ZSTEP/1000, R1*ZSTEP/1000,
            Math.toDegrees(WorldContract.latOf(R0*ZSTEP)), Math.toDegrees(WorldContract.latOf(R1*ZSTEP)), nP, GRAD/1000));
        say("");
        say(String.format(LF, "  采样：陆地 %d (%.1f%%)，其中内陆 k>=0.8 有 %d (%.1f%%)；kappa 均值 %.3f",
            nLand, nLand*100.0/nP, nInt, nInt*100.0/nP, sumKap/nP));
        say("");
        for (int vi = 0; vi < VARS.length; vi++) {
            say(String.format(LF, "V%d. %s   （%d 点 x 2 季，%.0f ms，撞上限 %.2f%%）",
                vi, VNAME[vi], nP, vMs[vi], vCap[vi]*100.0/nP));
            say(String.format(LF, "    %-9s %-12s %7s %8s %9s %9s", "纬度", "kappa 层", "点数", "反相%", "du>0比例", "p反号%"));
            for (int bi = 0; bi < 6; bi++) {
                double lo = 15 + bi * 5, hi = lo + 5;
                for (int ci = 0; ci < 4; ci++) {
                    int q = vi * 24 + bi * 4 + ci;
                    int n = cellN[q];
                    if (n < 5) continue;
                    say(String.format(LF, "    %-9s %-12s %7d %7.1f%% %8.1f%% %8.1f%%",
                        (int)lo + "~" + (int)hi, KCLS[ci], n, cellRev[q]*100.0/n, cellDuP[q]*100.0/n, cellPR[q]*100.0/n));
                }
            }
            say("");
            say(String.format(LF, "    汇总 20~40 度："));
            for (int ci = 0; ci < 4; ci++) {
                int q = vi * 4 + ci;
                int n = sumN[q];
                if (n == 0) continue;
                say(String.format(LF, "      %-12s n=%6d  反相 %5.1f%%  du>0 %5.1f%%  p反号 %5.1f%%",
                    KCLS[ci], n, sumRev[q]*100.0/n, sumDuP[q]*100.0/n, sumPR[q]*100.0/n));
            }
            say("");
        }
        // ⚠ 不要用 say()，也不要以 [P284] 开头：合并日志会被 diff_acceptance.ps1 逐行比对，
        // 多一行 [P284] 就会报一处差异（这一行是运维信息，不是读数）。
        System.out.println("MERGE_PARTS=" + nParts + "  SUM_NP=" + npSum + "  EXPECTED=" + nFull);
        rep.close();
    }

    /** 主表用的行数（R0..R1）。 */
    static final int NZ_FULL = R1 - R0 + 1;

    // ================= D77：分阶段剖析（只测量，不改任何东西） =================
    /**
     * profile <cLo> <cHi>：把一段列区间的**成本归到具体阶段**。
     *
     * <p>为什么需要它：§209.3 实测 P284 的成本极度不均（112 列 &gt;1530 s，而 333 列只花 130 s，
     * 差约 40 倍）。分片只能把这个事实摊到多个进程上，**真正的收益在于知道那几段为什么贵**。
     *
     * <p>⚠ 口径：只调 OceanField.install，**不启预热线程** —— 否则后台预热会污染 solveCount，
     * 成本就归不到这一段上。海洋场本身是种子纯函数，装机方式不影响数值。
     */
    static void profile(int cLo, int cHi) throws Exception {
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0);
        double thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        // ⚠ 必须先摆好与分片路径**同一套**静态量：grad() 用 NR/ROFF 定位 ref 数组，
        //   否则它按 NR=NX 去索引一个只有 nOwn 列的数组 ⇒ 越界（第一次跑就是这么挂的）。
        CLO = cLo; CHI = cHi;
        XOFF = Math.max(0, cLo - KHALO);
        NK = Math.min(NX - 1, cHi + KHALO) - XOFF + 1;
        ROFF = cLo - ST;
        NR = (cHi - cLo + 1) + 2 * ST;
        int nOwn = cHi - cLo + 1, nP = NZ_FULL * nOwn;
        int xoff = XOFF;
        int nk = NK;
        say2(String.format(LF, "profile  cols %d..%d  (x = %d..%d km)  本片点 %d  kappa 环带 %d 列",
            cLo, cHi, cLo * XSTEP / 1000, cHi * XSTEP / 1000, nP, nk));

        com.EyeOfHarmonyBuffer.sim.runtime.SimClimate.resetStats();
        long oc0 = com.EyeOfHarmonyBuffer.sim.ocean.OceanField.solveCount;
        long on0 = com.EyeOfHarmonyBuffer.sim.ocean.OceanField.solveNanos;
        long wall0 = System.nanoTime();

        long t0 = System.nanoTime();
        double[] kapH = new double[NZ_FULL * nk];
        for (int r = R0; r <= R1; r++) {
            int baseH = (r - R0) * nk;
            for (int c = xoff; c < xoff + nk; c++) {
                kapH[baseH + (c - xoff)] = Atmosphere.kappaAt(c * XSTEP, r * ZSTEP, SEED, cell);
            }
        }
        long tKappa = System.nanoTime() - t0;

        t0 = System.nanoTime();
        double[] ref2 = new double[NZ_FULL * NR];      // 与 grad 的索引同一套（含 ±ST 边界）
        for (int r = R0; r <= R1; r++) {
            int baseH = (r - R0) * nk, baseR = (r - R0) * NR;
            for (int c = ROFF; c < ROFF + NR; c++) {
                ref2[baseR + (c - ROFF)] = windowMean(kapH, baseH, c, 100, 12, xoff);
            }
        }
        long tRef = System.nanoTime() - t0;

        double[] kap = new double[nP];
        int[] px_ = new int[nP], pz_ = new int[nP];
        int k = 0;
        for (int r = R0; r <= R1; r++)
            for (int c = cLo; c <= cHi; c++) {
                px_[k] = c * XSTEP; pz_[k] = r * ZSTEP;
                kap[k] = kapH[(r - R0) * nk + (c - xoff)]; k++;
            }
        long tGrad = 0, tPAnom = 0, tWind = 0, tKappaMemo = 0, tSst = 0, tElev = 0;
        double sink = 0;
        for (int i = 0; i < nP; i++) {
            int x = px_[i], z = pz_[i];
            double lat = WorldContract.latOf(z);
            int rr = R0 + i / nOwn, cc = cLo + i % nOwn;
            long a = System.nanoTime();
            double[] g1 = grad(cc, rr, SEED, cell, thS, 0, ref2);
            double[] g2 = grad(cc, rr, SEED, cell, thW, 0, ref2);
            long b = System.nanoTime(); tGrad += b - a;
            double p1 = pAnom(x, z, SEED, cell, thS, 0, 0.0);
            double p2 = pAnom(x, z, SEED, cell, thW, 0, 0.0);
            long c2 = System.nanoTime(); tPAnom += c2 - b;
            double[] w1 = Atmosphere.wind(g1[0], g1[1], kap[i], lat, thS);
            double[] w2 = Atmosphere.wind(g2[0], g2[1], kap[i], lat, thW);
            long d = System.nanoTime(); tWind += d - c2;
            long e = System.nanoTime();
            double ke = Atmosphere.kappaMemo(x, z, SEED, cell);
            long f = System.nanoTime(); tKappaMemo += f - e;
            double sa = Atmosphere.sstAnom(x, z);
            long g2b = System.nanoTime(); tSst += g2b - f;
            double el = PlateField.elevationWithCell(x, z, SEED, cell);
            tElev += System.nanoTime() - g2b;
            sink += p1 + p2 + w1[0] + w2[0] + ke + sa + el;
        }
        long wall = System.nanoTime() - wall0;

        say2(String.format(LF, "  kappa 环带（kappaAt x %d）= %.1f s   ⇒ %.1f us/次", nk * NZ_FULL, tKappa / 1e9, tKappa / 1e3 / (nk * NZ_FULL)));
        say2(String.format(LF, "  ref 窗口（windowMean x %d）    = %.1f s", nP, tRef / 1e9));
        say2(String.format(LF, "  内层循环 %d 点（单变体 V0）：", nP));
        say2(String.format(LF, "     grad   x2/点 = %8.1f s   ⇒ %8.3f ms/点", tGrad / 1e9, tGrad / 1e6 / nP));
        say2(String.format(LF, "     pAnom  x2/点 = %8.1f s   ⇒ %8.3f ms/点", tPAnom / 1e9, tPAnom / 1e6 / nP));
        say2(String.format(LF, "     wind   x2/点 = %8.1f s   ⇒ %8.3f ms/点", tWind / 1e9, tWind / 1e6 / nP));
        say2(String.format(LF, "     [采样各一次] kappaMemo %.1f us/次   sstAnom %.1f us/次   elevationWithCell %.1f us/次",
            tKappaMemo / 1e3 / nP, tSst / 1e3 / nP, tElev / 1e3 / nP));
        say2(String.format(LF, "     合计内层 = %.1f s；整段墙钟 = %.1f s（差 = 装机 + 首次瓦片求解 + JIT）",
            (tGrad + tPAnom + tWind) / 1e9, wall / 1e9));
        say2("  --- 模型侧计数器（本段独占，未启预热线程）---");
        long sc = com.EyeOfHarmonyBuffer.sim.runtime.SimClimate.SOLVE_COUNT.get();
        long sn = com.EyeOfHarmonyBuffer.sim.runtime.SimClimate.SOLVE_NANOS.get();
        long ch = com.EyeOfHarmonyBuffer.sim.runtime.SimClimate.CACHE_HIT.get();
        long cm = com.EyeOfHarmonyBuffer.sim.runtime.SimClimate.CACHE_MISS.get();
        long smp = com.EyeOfHarmonyBuffer.sim.runtime.SimClimate.SAMPLE_COUNT.get();
        long oc = com.EyeOfHarmonyBuffer.sim.ocean.OceanField.solveCount - oc0;
        long on = com.EyeOfHarmonyBuffer.sim.ocean.OceanField.solveNanos - on0;
        say2(String.format(LF, "   SimClimate 瓦片求解 %d 次 = %.1f s（占墙钟 %.0f%%）⇒ %.1f ms/瓦片；命中 %d / 未命中 %d；sample %d",
            sc, sn / 1e9, 100.0 * sn / Math.max(1, wall), sn / 1e6 / Math.max(1, sc), ch, cm, smp));
        say2(String.format(LF, "   OceanField 行求解 %d 次 = %.1f s（占墙钟 %.0f%%）⇒ %.1f ms/行",
            oc, on / 1e9, 100.0 * on / Math.max(1, wall), on / 1e6 / Math.max(1, oc)));
        say2(String.format(LF, "   sink=%.6f（防优化删除）", sink));
        say2("");
    }

    static void say2(String s) { System.out.println("[P284P] " + s); System.out.flush(); }
}
