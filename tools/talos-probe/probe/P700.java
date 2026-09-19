package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P700 -- §498 丙：把 M 里硬编码的 zFT=6000 换成【真抬升算出来的 LNB】。
//   A 算法自检（对文献值）  B 命名物理情形的量级  C 两盒实测  D 稳健性（不是旋钮控制的？）
public class P700 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P700] " + s); System.out.println("[P700] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }
    static final double[][] BOX = {{70,120,15,35},{0,30,20,35}};
    static final String[] BN = {"亚洲   70-120E/15-35N", "撒哈拉 0-30E /20-35N"};

    static double[] tS, qq, kk;
    static int NP;
    static int[] boxOf;

    static void cache(long sd, int cell, double th) {
        int cap = 4000; tS = new double[cap]; qq = new double[cap]; kk = new double[cap]; boxOf = new int[cap];
        NP = 0;
        for (int b = 0; b < 2; b++)
            for (double latd = BOX[b][2] + 2.5; latd <= BOX[b][3]; latd += 2.5)
                for (int c = 0; c < 72; c++) {
                    double lon = (c + 0.5) * 5.0;
                    if (lon < BOX[b][0] || lon > BOX[b][1]) continue;
                    int x = xOfLon(lon), z = zOfLat(latd);
                    PrecipField.mmPerDay(x, z, sd, PlateField.PLATE_CELL, th, GRAD);
                    double[] d = PrecipField.DIAG.get();
                    tS[NP] = Atmosphere.surfaceTemp(x, z, sd, PlateField.PLATE_CELL, th);
                    qq[NP] = d[5];
                    kk[NP] = Atmosphere.kappaMemo(x, z, sd, cell);
                    boxOf[NP] = b; NP++;
                }
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p700_report.txt"), "UTF-8");
        say("P700: §498 真抬升 —— LCL/LFC/LNB/CAPE 替换 zFT=6000 硬编码");

        // ================= A 算法自检 =================
        say("");
        say("=== A 算法自检（全部对文献值，与模型无关）===");
        say(String.format(LF, "干绝热递减率 GAMMA_D = %.6e K/m  (教科书 g/cp = 9.768e-3)", ParcelLift.GAMMA_D));
        say(String.format(LF, "多元大气指数 g/(R_d*GAMMA) = %.4f  (标准大气 5.2559)",
                ParcelLift.G / (ParcelLift.R_D * Atmosphere.GAMMA)));
        say("湿绝热递减率 GAMMA_s (K/m)：");
        double[][] tp = {{300.0, 100000.0, 0.020}, {273.15, 100000.0, 0.0038},
                         {290.0, 85000.0, 0.012}, {250.0, 50000.0, 0.003}, {210.0, 20000.0, 0.0004}};
        for (double[] a : tp) {
            double q = ParcelLift.qs(a[0], a[1]);
            say(String.format(LF, "   T=%6.2f K p=%7.0f Pa  q_s=%.6f  GAMMA_s=%.4e  (GAMMA_s/tdry=%.3f)",
                    a[0], a[1], q, ParcelLift.gammaMoist(a[0], a[1], q),
                    ParcelLift.gammaMoist(a[0], a[1], q) / ParcelLift.GAMMA_D));
        }
        say("LCL 两条独立公式的交叉校验（Bolton1980 vs Davies-Jones1983=IFS）：");
        double[][] lc = {{300.0, 0.020}, {305.0, 0.016}, {310.0, 0.005}, {288.0, 0.009}, {295.0, 0.002}};
        for (double[] a : lc) {
            ParcelLift.Result r = ParcelLift.lift(a[0], a[1]);
            say(String.format(LF, "   tS=%.1f q=%.4f td=%.2f | LCL Bolton=%.3f DJ=%.3f 差值=%.4f K | zLCL=%.0f m",
                    a[0], a[1], r.tdSfc, r.tLcl, r.tLclDJ, r.tLcl - r.tLclDJ, r.zLcl));
        }
        say("⟹ 环境递减率 6.5e-3 夹在 GAMMA_s(≈3.8e-3) 与 GAMMA_D(9.77e-3) 之间 ⇒ 机制成立：");
        say("   干段(9.77>6.5)气块【过冷】⇒ 负浮力；过 LCL 后(3.8<6.5)气块【过热】⇒ 正浮力。");

        // ================= B 命名物理情形 =================
        say("");
        say("=== B 命名物理情形（判据以物理正确为准）===");
        String[] nm = {"热带海洋 300K/20g/kg（应 CAPE 1000-3000, LNB 12-16km）",
                       "热带陆地 305K/16g/kg（应深对流）",
                       "副热带沙漠 310K/5g/kg（应 CAPE~0, 无 LNB）",
                       "中纬海洋 288K/9g/kg（应弱-中 CAPE）",
                       "极地 265K/2g/kg（应无对流）"};
        double[][] cs = {{300.0, 0.020}, {305.0, 0.016}, {310.0, 0.005}, {288.0, 0.009}, {265.0, 0.002}};
        for (int i = 0; i < cs.length; i++) {
            ParcelLift.Result r = ParcelLift.lift(cs[i][0], cs[i][1]);
            say("   " + nm[i]);
            say(String.format(LF, "      LCL z=%.0f m | LFC=%s LNB=%s | CAPE=%s J/kg CIN=%s | M_par=%.4e M_up=%.4e",
                    r.zLcl, f1(r.zLfc), f1(r.zLnb), f1(r.cape), f1(r.cin), r.mParcel, r.mUpdraft));
        }

        // ================= C 两盒实测 =================
        EarthRef.install();
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        // ★ §535（P1-5）：§476 要求【任何基于盒子的判据都必须先声明该盒在本世界的海陆构成】。
        //   ⚠ 本探针自身的循环起点仍是旧式 lo+half（见下方 for 行）—— 属 P1-6 待统一项；
        //     此处声明的陆占比用 Boxes 的【格心口径】，步长与本探针一致（dLon=5 dLat=2.5）。
        Boxes.declare(rep, "P700 ASIA   70-120E/15-35N", sd, PlateField.PLATE_CELL, 70,120,15,35, 5, 2.5);
        Boxes.declare(rep, "P700 SAHARA 0-30E /20-35N",  sd, PlateField.PLATE_CELL,  0, 30,20,35, 5, 2.5);
        int cell = PlateField.PLATE_CELL;
        double th = Atmosphere.theta(0.0);
        cache(sd, PlateField.PLATE_CELL, th);
        say("");
        say("=== C 两盒实测（JJA, theta(0)）样本数 " + NP + " ===");
        double[] sum = new double[12];
        int[] cnt = new int[2]; int[] convCnt = new int[2]; int[] capped = new int[2];
        for (int i = 0; i < NP; i++) {
            ParcelLift.Result r = ParcelLift.lift(tS[i], qq[i]);
            int b = boxOf[i]; cnt[b]++;
            if (r.convective) convCnt[b]++;
            if (!Double.isNaN(r.zLnb) && r.zLnb > 19000.0) capped[b]++;
            sum[b * 6 + 0] += Double.isNaN(r.cape) ? 0.0 : r.cape; sum[b * 6 + 1] += r.zLcl;
            sum[b * 6 + 2] += Double.isNaN(r.zLnb) ? 0.0 : r.zLnb;
            sum[b * 6 + 3] += Double.isNaN(r.mParcel) ? 0.0 : r.mParcel;
            sum[b * 6 + 4] += Double.isNaN(r.mUpdraft) ? 0.0 : r.mUpdraft;
            sum[b * 6 + 5] += Double.isNaN(r.cin) ? 0.0 : r.cin;
        }
        String[] rn = {"CAPE(J/kg)", "zLCL(m)", "zLNB(m)", "M_parcel(J/kg)", "M_updraft(J/kg)", "CIN(J/kg)"};
        say(String.format(LF, "  %-16s | %14s | %14s | %9s", "量", "亚洲", "撒哈拉", "contrast"));
        for (int j = 0; j < 6; j++) {
            double A = sum[j] / cnt[0], B = sum[6 + j] / cnt[1];
            double den = Math.abs(A) + Math.abs(B);
            double c = den < 1e-30 ? 0.0 : (A - B) / den;
            say(String.format(LF, "  %-16s | %+14.5e | %+14.5e | %+9.4f", rn[j], A, B, c));
        }
        say(String.format(LF, "  深对流点数占比    | %13.1f%% | %13.1f%% |  (n=%d/%d)",
                100.0 * convCnt[0] / cnt[0], 100.0 * convCnt[1] / cnt[1], convCnt[0], convCnt[1]));
        say(String.format(LF, "  【无对流】点数占比 | %13.1f%% | %13.1f%% |  <- 物理上应「沙漠更多」，方向待判",
                100.0 * (cnt[0] - convCnt[0]) / cnt[0], 100.0 * (cnt[1] - convCnt[1]) / cnt[1]));
        say("  ⚠ 上表把「无对流」编码为 0（中性）；M 只在【有对流】的点上有定义 —— 见 C2 的两种编码。");
        say(String.format(LF, "  LNB 被 zMax 截断  | %d/%d | %d/%d", capped[0], cnt[0], capped[1], cnt[1]));

        // ================= C2 旧 M（硬编码 zFT=6000）vs 新 M（抬升算出的 LNB） =================
        say("");
        say("=== C2 旧 M vs 新 M：判别力的【符号】才是关键 ===");
        double dhOldA = 0, dhOldB = 0;
        for (int i = 0; i < NP; i++) {
            double zFT = PrecipField.M_FT_FRAC * Atmosphere.H_EFF;
            double qftF = Math.exp(-zFT / PrecipField.H_MOIST);
            double dh = Radiation.CP * (Atmosphere.GAMMA * zFT) - PrecipField.G_ACC * zFT
                      + Radiation.LV * qq[i] * (1.0 - qftF);
            if (boxOf[i] == 0) dhOldA += dh; else dhOldB += dh;
        }
        dhOldA /= cnt[0]; dhOldB /= cnt[1];
        double cOld = (dhOldA - dhOldB) / (Math.abs(dhOldA) + Math.abs(dhOldB));
        double mA = 0, mB = 0; int na = 0, nb = 0;
        for (int i = 0; i < NP; i++) {
            ParcelLift.Result r = ParcelLift.lift(tS[i], qq[i]);
            if (Double.isNaN(r.mParcel)) continue;
            if (boxOf[i] == 0) { mA += r.mParcel; na++; } else { mB += r.mParcel; nb++; }
        }
        mA /= na; mB /= nb;
        double cNew = (mA - mB) / (Math.abs(mA) + Math.abs(mB));
        say(String.format(LF, "  旧 M ∝ dh(zFT=%.0f m 硬编码): 亚=%+.6e 撒=%+.6e  contrast=%+.4f  (%s)",
                PrecipField.M_FT_FRAC * Atmosphere.H_EFF, dhOldA, dhOldB, cOld,
                cOld > 0 ? "方向对" : "方向【反】"));
        say(String.format(LF, "  新 M = h_BL - h_env(zLNB 算出): 亚=%+.6e 撒=%+.6e  contrast=%+.4f  (%s)  n=%d/%d",
                mA, mB, cNew, cNew > 0 ? "方向对" : "方向【反】", na, nb));
        double tA = mA * na, tB = mB * nb;
        double mA2 = tA / cnt[0], mB2 = tB / cnt[1];
        double cNew2 = (mA2 - mB2) / (Math.abs(mA2) + Math.abs(mB2));
        say(String.format(LF, "    编码B（无对流记 M=0）      : 亚=%+.6e 撒=%+.6e  contrast=%+.4f", mA2, mB2, cNew2));
        say("    ⇒ 两种编码相差，因为【无对流】在亚洲占 " + (cnt[0] - na) + "/" + cnt[0]
            + " 而在撒哈拉占 " + (cnt[1] - nb) + "/" + cnt[1] + "（方向仍由错误的 q 主导，见 §497）");

        // ================= C3 缺陷定量：固定 6.5 K/km 没有对流层顶 =================
        say("");
        say("=== C3 根因定量：单元线性廓线【没有对流层顶】 ===");
        say(String.format(LF, "  %-6s | %-14s | %-14s | %s", "tS(K)", "T(12km) 本模型", "T(12km) 观测热带", "偏差"));
        double[] tss = {295.0, 300.0, 305.0};
        double[] obs = {200.0, 200.0, 200.0};
        for (int i = 0; i < tss.length; i++) {
            double tm = tss[i] - Atmosphere.GAMMA * 12000.0;
            say(String.format(LF, "  %-6.1f | %-14.2f | %-14.1f | %+.2f K",
                    tss[i], tm, obs[i], tm - obs[i]));
        }
        double zTrop = 300.0 / Atmosphere.GAMMA;
        say(String.format(LF, "  本模型 300 K 地面的「顶」= tS/GAMMA = %.0f m（观测热带对流层顶 16000-17000 m）", zTrop));
        say("  ⇒ 固定 6.5e-3 是【中纬标准大气(ISA, 288.15K 定义)】的递减率，套到 300+ K 热带地面后：");
        say("     12 km 处偏暖 ~26 K ⇒ 高层过暖 ⇒ M 的 g*z 项偏大 ⇒ M 被推向负值。");

        // ================= D 稳健性：M 的符号是不是被旋钮控制？ =================
        say("");
        say("=== D 稳健性：换环境递减率 / 水汽标高，判别力还在不在？ ===");
        double[] gs = {5.5e-3, 6.0e-3, 6.5e-3, 7.0e-3, 7.5e-3};
        double[] hs = {1500.0, 2000.0, 2500.0, 3000.0};
        say(String.format(LF, "  %-8s %-8s | %11s %11s %9s | %11s %11s %9s",
                "GAMMA", "H_MOIST", "CAPE亚", "CAPE撒", "contr", "M_par亚", "M_par撒", "contr"));
        for (double g : gs) for (double h : hs) {
            double sa = 0, sb = 0, ma = 0, mb = 0;
            for (int i = 0; i < NP; i++) {
                ParcelLift.Result r = ParcelLift.lift(tS[i], qq[i], g, h, 20000.0, 40.0);
                double ca = Double.isNaN(r.cape) ? 0.0 : r.cape;
                double mv = Double.isNaN(r.mParcel) ? 0.0 : r.mParcel;
                if (boxOf[i] == 0) { sa += ca; ma += mv; } else { sb += ca; mb += mv; }
            }
            sa /= cnt[0]; sb /= cnt[1]; ma /= cnt[0]; mb /= cnt[1];
            double cc = (Math.abs(sa) + Math.abs(sb)) < 1e-30 ? 0 : (sa - sb) / (Math.abs(sa) + Math.abs(sb));
            double cm = (Math.abs(ma) + Math.abs(mb)) < 1e-30 ? 0 : (ma - mb) / (Math.abs(ma) + Math.abs(mb));
            say(String.format(LF, "  %-8.4f %-8.0f | %11.1f %11.1f %+9.4f | %+11.4e %+11.4e %+9.4f",
                    g, h, sa, sb, cc, ma, mb, cm));
        }
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
    static String f1(double v) { return Double.isNaN(v) ? "none" : String.format(LF, "%.1f", v); }
}
