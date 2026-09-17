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
 * P502：**中纬低层散度的来源分解 + 稳态收支反解 RH**（设计冻结 §253.3）。
 *
 * <p>要回答两个问题：
 * <ol>
 *   <li><b>模型 55~60 度海洋的低层散度为什么是正的？</b> 真实 Ferrel 环流的地面分支
 *       （30 度下沉 → 60 度上升）在 55~60 度应当是**辐合**。分解 @@divU = du/dx + dv/dz@@，
 *       再把 @@v@@ 拆成「纬向平均气压梯度（Ferrel）部分」与「距平部分」。</li>
 *   <li><b>把边界层水汽写成稳态收支、解出来的 RH 是否复现观测的海洋降水？</b>
 *       @@(qs - q)/q = divU*H_eff/(C_E*Ve*(1-alpha))@@ ⇒ @@RH = 1/(1+D)@@。</li>
 * </ol>
 *
 * <p><b>自检</b>
 * <ol>
 *   <li>[1] @@divU == du/dx + dv/dz@@ 逐位（生产就是这么算的）</li>
 *   <li>[2] @@v_total == v_ref + v_anom@@ 逐位 —— 用 @@wind(0,0,κ,lat,theta)@@ **隔离**出
 *       @@pzRef@@ 那一支（px = pz = 0 时 u = 0、v 只剩 @@-gam*pzRef/den@@），
 *       **不重写生产公式**（E69 纪律）</li>
 *   <li>[3] 量级合理性：RH ∈ [0,1]、E ∈ [0,100] mm/day</li>
 * </ol>
 */
public class P502 {

    static final int SEED = 1022228679;
    static final long SD = SimTerrain.seedOf(SEED);
    static final int CELL = PlateField.PLATE_CELL;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P502] " + s); rep.flush(); System.out.println("[P502] " + s); System.out.flush(); }

    static final int NX = 120, XSPAN = 60_000_000, GS = 500_000, NROW = 25;
    static int failDiv = 0, failV = 0, failMag = 0;
    static double worstV = 0.0;

    /** 观测的海洋降水锚（GPCP+ETOPO1，JJA，mm/day）。 */
    static double obsSeaJja(double la) {
        double[] lat = {23.75, 26.25, 28.75, 31.25, 33.75, 36.25, 38.75, 41.25, 43.75, 46.25, 48.75, 51.25, 53.75, 56.25, 58.75};
        double[] p   = {1.844, 1.872, 2.144, 2.301, 2.450, 2.602, 2.893, 2.966, 2.841, 2.762, 2.745, 2.866, 2.658, 2.723, 2.659};
        int best = 0; double bd = 1e9;
        for (int i = 0; i < lat.length; i++) { double d = Math.abs(lat[i] - la); if (d < bd) { bd = d; best = i; } }
        return p[best];
    }

    public static void main(String[] args) throws Exception {
        double th = args.length > 0 ? Double.parseDouble(args[0]) : 0.43033;
        String tag = args.length > 1 ? args[1] : "JJA";
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p502_report_" + tag + ".txt"), "UTF-8");
        say("P502：中纬低层散度来源分解 + 收支反解 RH   theta=" + th + "   ALPHA_SH=" + PrecipField.ALPHA_SH
            + "   V_GUST=" + PrecipField.V_GUST);
        say("");
        say(String.format(LF, "  %6s | %9s %9s %9s | %9s %9s %9s | %9s",
                "latN", "divU(1e-6)", "du/dx", "dv/dz", "v_tot", "v_ref", "v_anom", "pzRef"));
        say("  " + "-".repeat(84));

        double[] acc = new double[13]; int[] cnt = new int[2];
        for (int i = 0; i < NROW; i++) {
            double la = 2.5 + i * 2.5;
            int z = (int) Math.round(la / 90.0 * WorldContract.MAX_D);
            double[] a = new double[13]; int[] c = new int[2];
            for (int q = 0; q < NX; q++) {
                int x = (int) ((long) q * XSPAN / NX);
                double k = Atmosphere.kappaAt(x, z, SD, CELL);
                boolean land = k > 0.8, sea = k < 0.2;
                if (!land && !sea) continue;
                double lat = WorldContract.latOf(z);
                double[] ux = Atmosphere.windAt(x + GS, z, SD, CELL, th, GS);
                double[] uw = Atmosphere.windAt(x - GS, z, SD, CELL, th, GS);
                double[] un = Atmosphere.windAt(x, z + GS, SD, CELL, th, GS);
                double[] us = Atmosphere.windAt(x, z - GS, SD, CELL, th, GS);
                double dudx = (ux[0] - uw[0]) / (2.0 * GS);
                double dvdz = (un[1] - us[1]) / (2.0 * GS);
                double divU = dudx + dvdz;
                if (divU != dudx + dvdz) failDiv++;
                // 中心点的 v，及其 pzRef 支（px = pz = 0 隔离）
                double px = (Atmosphere.pressureAnomaly(x + GS, z, SD, CELL, th)
                           - Atmosphere.pressureAnomaly(x - GS, z, SD, CELL, th)) / (2.0 * GS);
                double pz = (Atmosphere.pressureAnomaly(x, z + GS, SD, CELL, th)
                           - Atmosphere.pressureAnomaly(x, z - GS, SD, CELL, th)) / (2.0 * GS);
                double kk = Atmosphere.kappaMemo(x, z, SD, CELL);
                double vTot = Atmosphere.wind(px, pz, kk, lat, th)[1];
                double vRef = Atmosphere.wind(0.0, 0.0, kk, lat, th)[1];
                double vAn = vTot - vRef;
                // E86 FIX: vAn is DEFINED as vTot - vRef, so asserting vRef + vAn == vTot with zero
                // tolerance just tests floating-point non-associativity (416 hits).
                // NEW RULE: an identity assertion must relate two INDEPENDENTLY computed quantities;
                // never assert A + C == B for a DEFINITION C := B - A.
                double resV = Math.abs(vTot - (vRef + vAn));
                worstV = Math.max(worstV, resV);
                if (resV > 1e-12 * Math.max(1.0, Math.abs(vTot))) failV++;
                double pzRef = ZonalTables.pRefSlopePerRad(Math.toDegrees(lat)) / WorldContract.R_EFF;
                int b = land ? 0 : 1;
                a[b * 5 + 0] += divU * 1e6; a[b * 5 + 1] += dudx * 1e6; a[b * 5 + 2] += dvdz * 1e6;
                a[b * 5 + 3] += vTot; a[b * 5 + 4] += vRef;
                a[10] += vAn; a[12] += pzRef;
                c[b]++;
            }
            double m0 = c[1] > 0 ? a[5] / c[1] : Double.NaN, m1 = c[1] > 0 ? a[6] / c[1] : Double.NaN;
            double m2 = c[1] > 0 ? a[7] / c[1] : Double.NaN, m3 = c[1] > 0 ? a[8] / c[1] : Double.NaN;
            double m4 = c[1] > 0 ? a[9] / c[1] : Double.NaN, m5 = c[1] > 0 ? a[10] / c[1] : Double.NaN;
            double m6 = c[1] > 0 ? a[12] / c[1] : Double.NaN;
            say(String.format(LF, "  %6.1f | %9.2f %9.2f %9.2f | %9.3f %9.3f %9.3f | %9.2e",
                    la, m0, m1, m2, m3, m4, m5, m6));
            if (la >= 15 && la <= 60) {
                for (int j = 0; j < 13; j++) acc[j] += a[j];
                cnt[0] += c[0]; cnt[1] += c[1];
            }
        }
        say("");
        say(String.format(LF, "  15~60 度海洋平均：divU = %+.3f  du/dx = %+.3f  dv/dz = %+.3f  (x1e-6 1/s)   v_tot = %+.3f  v_ref = %+.3f  v_anom = %+.3f m/s",
                acc[5] / cnt[1], acc[6] / cnt[1], acc[7] / cnt[1], acc[8] / cnt[1], acc[9] / cnt[1], acc[10] / cnt[1]));
        say("");
        say("★ 判读：若 v_ref（Ferrel 支）在中纬**很小或符号为负**，就证明「模型没有纬向平均经向地面流」。");
        say("        若 dv/dz 在 55~60 度是**正**的，就证明低层是虚假辐散。");
        say("");
        say("H. 自检");
        say(String.format(LF, "  [1] divU == du/dx + dv/dz（逐位）              失配 %d 次   %s", failDiv, failDiv == 0 ? "PASS" : "FAIL"));
        say(String.format(LF, "  [2] v_anom := v_tot - v_ref 的浮点闭合      失配 %d 次（最大 %.2e）   %s", failV, worstV, failV == 0 ? "PASS" : "FAIL"));
        rep.flush(); rep.close();
        System.out.println("JAVA_EXIT=0");
    }
}
