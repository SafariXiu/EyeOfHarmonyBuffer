package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P492：**「洋面基线 + 海陆年均对比」新温度基线的自检与影响普查**（§216.7）。
 *
 * <p>A 段是**非循环**的独立核对：{@code ZonalTables} 里只存 T_ZM / ZF / DELTA / ELEV 四列，
 * 而 {@code ls_anchor_table.txt}（由 {@code gen_zonal_tables.py} 从 ERA5 npz 直接写出）
 * 还带 **T_SEA / T_LAND / DSL** 三列 —— 它们是**推论**，不是被存进 Java 的量。
 * 检查「模型的洋面支是否等于观测的 T_SEA」「模型的陆地海平面支是否等于观测的 T_LAND」
 * 就同时验了：对称化、5 度插值、Δ_SL 剥海拔、以及 interpolate 的口径。
 *
 * <p>C 段量的是**相对旧基线**的温度变化：旧基线是三点勒让德
 * {@code T0 + T2*p2(s) + T4*p4(s)}（这里按老常量复算，不改生产代码）。
 */
public class P492 {

    static final int SEED = 1022228679;
    static final long SD = SimTerrain.seedOf(SEED);
    static final int CELL = PlateField.PLATE_CELL;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static int fails = 0;

    static void say(String s) { rep.println("[P492] " + s); rep.flush(); System.out.println("[P492] " + s); System.out.flush(); }
    static void chk(boolean ok, String msg) { if (!ok) fails++; say("   " + (ok ? "OK  " : "**错**") + " " + msg); }

    // ---- 旧基线（只为对照；生产已不再使用）----
    static final double OLD_T0 = 288.15, OLD_T2 = -26.71, OLD_T4 = -6.29;
    static double oldTzm(double latRad) {
        double s = Math.sin(latRad);
        return OLD_T0 + OLD_T2 * Atmosphere.p2(s) + OLD_T4 * Atmosphere.p4(s);
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p492_report.txt"), "UTF-8");
        say("P492：新温度基线（洋面基线 + 海陆年均对比）的自检与影响普查");
        say("");

        // ================= A. 与 ERA5 锚点表逐点核对 =================
        say("A. 与 ERA5 锚点表核对（表里没存的推论列 T_SEA / T_LAND 才是真正的检验）");
        double[][] tb = new double[19][8];
        BufferedReader br = new BufferedReader(new FileReader(new File(ROOT, "build/eoh_probe/refs/ls_anchor_table.txt")));
        String ln;
        int n = 0;
        while ((ln = br.readLine()) != null) {
            ln = ln.trim();
            if (ln.isEmpty() || ln.startsWith("#")) continue;
            String[] p = ln.split("[\\s,]+");
            for (int i = 0; i < 8; i++) tb[n][i] = Double.parseDouble(p[i]);
            n++;
        }
        br.close();
        say(String.format(LF, "   读入 %d 行（期望 19）", n));
        chk(n == 19, "锚点表行数 = 19");
        say(String.format(LF, "   %5s %10s %10s %10s %10s %10s %10s %10s", "lat", "T_ZM", "T_OCEAN", "T_LAND", "DELTA", "ZF", "ELEV", "D_SL"));
        double maxSea = 0, maxLand = 0, maxZf = 0, maxDe = 0, maxEv = 0, maxTz = 0, maxDsl = 0;
        for (int k = 0; k < n; k++) {
            double la = tb[k][0];
            double latRad = Math.toRadians(la);
            double tz = Atmosphere.tZonalMean(latRad);
            double sea = Atmosphere.oceanBaseK(latRad);
            double zf = ZonalTables.zfEarth(latRad);
            double ev = ZonalTables.landMeanElev(latRad);
            double dsl = Atmosphere.landMinusOceanSLK(latRad);
            double land = sea + dsl - Atmosphere.GAMMA * ev;        // 折回「含海拔」的陆地支
            maxTz = Math.max(maxTz, Math.abs(tz - tb[k][1]));
            maxSea = Math.max(maxSea, Math.abs(sea - tb[k][2]));
            maxLand = Math.max(maxLand, Math.abs(land - tb[k][3]));
            maxDe = Math.max(maxDe, Math.abs((land - sea) - tb[k][4]));
            maxZf = Math.max(maxZf, Math.abs(zf - tb[k][5]));
            maxEv = Math.max(maxEv, Math.abs(ev - tb[k][6]));
            maxDsl = Math.max(maxDsl, Math.abs(dsl - tb[k][7]));
            say(String.format(LF, "   %+5.0f %10.2f %10.2f %10.2f %10.2f %10.4f %10.0f %10.2f", la, tz, sea, land, land - sea, zf, ev, dsl));
        }
        say("");
        chk(maxSea < 0.01, String.format(LF, "★ **洋面支** vs 观测 T_OCEAN        max|差| = %.4f K", maxSea));
        chk(maxLand < 0.01, String.format(LF, "★ **陆地支** vs 观测 T_LAND         max|差| = %.4f K", maxLand));
        chk(maxDe < 0.01, String.format(LF, "Δ = T_LAND − T_OCEAN vs 表          max|差| = %.4f K", maxDe));
        chk(maxDsl < 0.01, String.format(LF, "Δ_SL = Δ + Γ*z_bar vs 表            max|差| = %.4f K", maxDsl));
        chk(maxEv < 0.5, String.format(LF, "z_bar_land 表 vs ETOPO1             max|差| = %.4f m", maxEv));
        chk(maxZf < 1e-4, String.format(LF, "ZF_earth 表（参照量）               max|差| = %.6f", maxZf));
        chk(maxTz < 0.01, String.format(LF, "T_zm 表（参照量）                   max|差| = %.4f K", maxTz));
        say("");

        // ================= B. 为什么一个 T_zm 不够 =================
        say("B. 一个 T_zm 为什么不够：对称化后的**协方差**项");
        say("   cov(|phi|) = ZF_sym*T_LAND + (1-ZF_sym)*T_OCEAN - T_ZM_sym");
        say("   （逐行它精确为 0；对称化之后不为 0，因为两个半球的 ZF 与支温度不同）");
        double cMax = 0, cSs = 0; int cn = 0;
        for (double la = 0; la <= 90; la += 1.0) {
            double lr = Math.toRadians(la);
            double cov = ZonalTables.zfEarth(lr) * ZonalTables.tLandK(lr)
                       + (1 - ZonalTables.zfEarth(lr)) * ZonalTables.tOceanK(lr)
                       - Atmosphere.tZonalMean(lr);
            cMax = Math.max(cMax, Math.abs(cov)); cSs += cov * cov; cn++;
        }
        say(String.format(LF, "   rms = %.2f K   max = %.2f K   （这就是被一个 T_zm 悄悄吃掉的那部分）",
                Math.sqrt(cSs / cn), cMax));
        double mId = 0;
        for (double la = -90; la <= 90; la += 0.5) {
            double lr = Math.toRadians(la);
            double lhs = Atmosphere.annualSeaLevelTemp(lr, 0.0, 0.0);
            double rhs = Atmosphere.oceanBaseK(lr);
            mId = Math.max(mId, Math.abs(lhs - rhs));
        }
        chk(mId < 1e-12, String.format(LF, "κ=0 时 annualSeaLevelTemp == oceanBaseK（max|差| = %.2e K）", mId));
        say("");

        // ================= C. 相对旧基线的变化 =================
        say("C. 相对**旧**基线（三点勒让德）的温度变化普查");
        say("   ΔT(lat, land) = [annualSeaLevelTemp(1) - Γ*z_bar] - oldTzm");
        say("   ΔT(lat, sea ) =  annualSeaLevelTemp(0)            - oldTzm");
        say(String.format(LF, "   %5s %12s %12s %12s %12s", "lat", "ΔT_sea(K)", "ΔT_land(K)", "旧T_zm", "新T_ocean"));
        for (double la = 0; la <= 90; la += 15) {
            double lr = Math.toRadians(la);
            double zbar = ZonalTables.landMeanElev(lr);
            double old = oldTzm(lr);
            double ds = Atmosphere.annualSeaLevelTemp(lr, 0.0, 0.0) - old;
            double dl = Atmosphere.annualSeaLevelTemp(lr, 1.0, 0.0) - Atmosphere.GAMMA * zbar - old;
            say(String.format(LF, "   %+5.0f %+12.2f %+12.2f %12.2f %12.2f", la, ds, dl, old, Atmosphere.oceanBaseK(lr)));
        }
        say("");
        say("   （南半球同值 —— 新基线对 |lat| 精确对称，见 D 段）");
        say("");

        // ================= D. 南北对称性（**函数级**，不是逐点） =================
        //  逐点比较没有意义：PlateField 在 z 上非周期，北极点可能是 2000 m 的山、南极点可能是海。
        //  要验的是「**温度基线的每一块**都是 |lat| 的偶函数」——那才是 T_zm 层的性质。
        say("D. 南北对称性（函数级）：新构造的每一个分量对 |lat| 都必须是偶函数");
        double dToc = 0, dTl = 0, dLms = 0, dSeas = 0, dAnn = 0;
        for (double la = 0; la <= 90; la += 0.25) {
            double lp = Math.toRadians(la), lm = Math.toRadians(-la);
            dToc = Math.max(dToc, Math.abs(Atmosphere.oceanBaseK(lp) - Atmosphere.oceanBaseK(lm)));
            dTl = Math.max(dTl, Math.abs(ZonalTables.tLandK(lp) - ZonalTables.tLandK(lm)));
            dLms = Math.max(dLms, Math.abs(Atmosphere.landMinusOceanSLK(lp) - Atmosphere.landMinusOceanSLK(lm)));
            dAnn = Math.max(dAnn, Math.abs(Atmosphere.annualSeaLevelTemp(lp, 0.37, 0) - Atmosphere.annualSeaLevelTemp(lm, 0.37, 0)));
            for (int q = 0; q < 4; q++) {
                double th = q * Math.PI / 2;
                dSeas = Math.max(dSeas, Math.abs(Atmosphere.seasonalAnomaly(lp, 0.37, th) + Atmosphere.seasonalAnomaly(lm, 0.37, th)));
            }
        }
        chk(dToc == 0, String.format(LF, "T_OCEAN(|lat|) 偶函数      max|T(+)-T(-)| = %.1e K", dToc));
        chk(dTl == 0, String.format(LF, "T_LAND(|lat|) 偶函数       max|T(+)-T(-)| = %.1e K", dTl));
        chk(dLms == 0, String.format(LF, "Δ_SL(|lat|) 偶函数         max|T(+)-T(-)| = %.1e K", dLms));
        chk(dAnn == 0, String.format(LF, "年均场偶函数               max|T(+)-T(-)| = %.1e K", dAnn));
        chk(dSeas < 1e-12, String.format(LF, "季节项**反相**（南北半球相反季节）max|s(+)+s(-)| = %.1e K", dSeas));
        say("");

        // ================= E. airT / airMass 四类普查（D8-b） =================
        say("E. airT 与 airMass 四类普查（D8-b 的证据）");
        say("   airT = (tSea - tzm)/AIRT_SCALE，tzm = 本世界自己的纬向平均（κ=⟨κ⟩）");
        int[] cls = new int[4];
        double aMin = 1e9, aMax = -1e9, aAbs = 0; int nA = 0;
        for (int zi = 4; zi <= 176; zi++) {
            int z = zi * 100_000;
            for (int xi = 0; xi < 40; xi++) {
                int x = xi * 137_000;
                double kap = Atmosphere.kappaAt(x, z, SD, CELL);
                double tSea = Atmosphere.annualSeaLevelTemp(WorldContract.latOf(z), kap, Atmosphere.sstAnom(x, z));
                double tzm = Atmosphere.zonalMeanSeaLevelK(WorldContract.latOf(z));
                double airT = Math.max(-1, Math.min(1, (tSea - tzm) / 15.0));
                double mar = Math.max(0, Math.min(1, 1.0 - kap));
                int c = mar >= 0.5 ? (airT >= 0.0 ? 0 : 2) : (airT >= 0.0 ? 1 : 3);
                cls[c]++; nA++;
                aMin = Math.min(aMin, airT); aMax = Math.max(aMax, airT); aAbs += Math.abs(airT);
            }
        }
        say(String.format(LF, "   样本 %d：airT 范围 [%.3f, %.3f]，平均 |airT| = %.4f", nA, aMin, aMax, aAbs / nA));
        say(String.format(LF, "   四类占比： 陆+暖(0) %.1f%%   海+暖(1) %.1f%%   陆+冷(2) %.1f%%   海+冷(3) %.1f%%",
                100.0 * cls[0] / nA, 100.0 * cls[1] / nA, 100.0 * cls[2] / nA, 100.0 * cls[3] / nA));
        say("   （P458 在旧基线 + 无 SST 时报过 1.3% 的「陆+暖」；有 SST 后是 21/19/35/25）");
        say("");

        // ================= F. temp 归一化的截断 =================
        say("F. 温度归一化 c.temp = 0.10 + (tSfc - 255.15)/55 的截断普查");
        final double T_LO = 255.15, T_HI = 299.15, C_LO = 0.10, C_HI = 0.90;
        double slope = (C_HI - C_LO) / (T_HI - T_LO);
        int nLow = 0, nHigh = 0, nTot = 0; double sMin = 1e9, sMax = -1e9;
        for (int zi = 2; zi <= 178; zi++) {
            int z = zi * 100_000;
            for (int xi = 0; xi < 30; xi++) {
                int x = xi * 181_000;
                double tSfc = Atmosphere.surfaceTemp(x, z, SD, CELL, 0.0);
                sMin = Math.min(sMin, tSfc); sMax = Math.max(sMax, tSfc);
                double c = C_LO + (tSfc - T_LO) * slope;
                if (c < C_LO) nLow++;
                if (c > C_HI) nHigh++;
                nTot++;
            }
        }
        say(String.format(LF, "   样本 %d：T_sfc 范围 [%.1f, %.1f] K", nTot, sMin, sMax));
        say(String.format(LF, "   低于 255.15 K 被截到 0.10 的占 %.2f%%，高于 299.15 K 被截到 0.90 的占 %.2f%%",
                100.0 * nLow / nTot, 100.0 * nHigh / nTot));
        say("");
        say("F2. **同一批点**在**旧**基线下的截断率（对照 —— 新基线让高纬陆地变冷，冷端截断必然增加）");
        int oLow = 0, oHigh = 0;
        for (int zi = 2; zi <= 178; zi++) {
            int z = zi * 100_000;
            for (int xi = 0; xi < 30; xi++) {
                int x = xi * 181_000;
                double kap = Atmosphere.kappaAt(x, z, SD, CELL);
                double elev = PlateField.elevationWithCell(x, z, SD, CELL);
                double tOld = oldTzm(WorldContract.latOf(z)) + Atmosphere.seasonalAnomaly(WorldContract.latOf(z), kap, 0.0)
                            - Atmosphere.GAMMA * Math.max(0.0, elev) * kap + (1.0 - kap) * Atmosphere.sstAnom(x, z);
                double c = C_LO + (tOld - T_LO) * slope;
                if (c < C_LO) oLow++;
                if (c > C_HI) oHigh++;
            }
        }
        say(String.format(LF, "   旧基线：冷端截断 %.2f%%   热端截断 %.2f%%   ⇒ 新基线分别 %+.2f / %+.2f 个百分点",
                100.0 * oLow / nTot, 100.0 * oHigh / nTot,
                100.0 * (nLow - oLow) / nTot, 100.0 * (nHigh - oHigh) / nTot));
        say("");

        say("========================================================================");
        say(String.format(LF, "A 段硬断言失败数 = %d", fails));
        say("========================================================================");
        rep.flush(); rep.close();
        System.out.println("JAVA_EXIT=" + (fails == 0 ? 0 : 5));
    }
}
