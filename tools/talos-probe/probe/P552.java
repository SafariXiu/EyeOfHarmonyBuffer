package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

/**
 * P552 —— S3 第一步：**逐框直接测带符号的 divU 与 w_loc**，把 §349 的反解结论钉死。
 *
 * <p>§349 是用 P548 的框降水 **反解** 出「隐含的 w_eff」，结论是：
 * 撒哈拉与亚洲都需要正的（辐合的）w_loc，且**撒哈拉需要的更多**（86% vs 23% 的 cap）。
 * 那是**间接**证据。本探针直接测量：
 * <ol>
 *   <li>框内 divU 的**带符号**均值 + **辐合点占比**（避开带平均的 11 倍符号相消陷阱）</li>
 *   <li>w_loc 与 w_zm 的分解值（用 wEff(lat,theta,0) 精确取出 w_zm，无需知道 shifted）</li>
 *   <li>**cellPressure** 的带符号均值 —— §349 指认的手术靶点，直接量它</li>
 *   <li>用 P548 的框降水与框 q 反解隐含 w_eff，与**直接测得**的 max(0,w_eff) 对比</li>
 * </ol>
 *
 * <p><b>仪器自证（报告第 1 行）</b>：三条恒等式 —— ① w_loc 往返、② w_zm 用 divU=0 提取、
 * ③ 恒有 wEff(lat,theta,divU) == wZm + wLoc（SPLIT_ASCENT=false 时）。
 */
public class P552 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P552] " + s); System.out.println("[P552] " + s); }

    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static final int SEED = 1022228679;
    /** DIV0 = W_LOC_MAX / H_BL：tanh 的线性区尺度，用于报「深入到非线性多深」。 */
    static final double DIV0 = PrecipField.W_LOC_MAX / PrecipField.H_BL;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p552_report.txt"), "UTF-8");
        P546.loadMask();
        PlateField.MASK = new PlateField.LandMask() {
            public boolean isLand(int x, int z) { return P546.earthIsLand(x, z); }
        };
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double th = Atmosphere.theta(0.0);            // JJA
        double lat0 = 22.5;                            // 取样纬度

        // ---- 仪器自证（报告第 1 行）----
        double maxRt = 0, maxId = 0, maxZm = 0;
        for (double la = -60; la <= 60; la += 7.5) {
            double lr = Math.toRadians(la);
            double d = 3.0e-6 * Math.sin(la * 0.7);
            double wT = PrecipField.wEff(lr, th, d);
            double wZ = PrecipField.wEff(lr, th, 0.0);
            double wL = PrecipField.W_LOC_MAX * Math.tanh(-PrecipField.H_BL * d / PrecipField.W_LOC_MAX);
            maxId = Math.max(maxId, Math.abs(wT - (wZ + wL)));
            // 从 wL 反解 divU
            double r = wL / PrecipField.W_LOC_MAX;                     // java.lang.Math 没有 atanh
            double inv = -PrecipField.W_LOC_MAX * 0.5 * Math.log((1.0 + r) / (1.0 - r)) / PrecipField.H_BL;
            maxRt = Math.max(maxRt, Math.abs(inv - d));
            // divU=0 必须恰好取到 w_zm：与 zonal 表独立求值对照
            double shifted = la - Math.toDegrees(PrecipField.precipSubsolarLat(th));
            maxZm = Math.max(maxZm, Math.abs(wZ - ZonalTables.wZm(shifted)));
        }
        say("自证 SELF-PROOF：max|wEff-(wZm+wLoc)|=" + String.format(LF, "%.3e", maxId)
            + "  max|divU_反解-divU|=" + String.format(LF, "%.3e", maxRt)
            + "  max|wEff(divU=0)-wZm(shifted)|=" + String.format(LF, "%.3e", maxZm));
        say("  契约：Z_CYCLE=" + WorldContract.Z_CYCLE + "  W_LOC_MAX=" + PrecipField.W_LOC_MAX
            + "  H_BL=" + PrecipField.H_BL + "  DIV0=" + String.format(LF, "%.3e", DIV0)
            + "  theta=" + String.format(LF, "%.4f", th) + "  RH_SEA=" + PrecipField.RH_SEA);

        // ---- 五个框（与 P548 / earth_monsoon_boxes.py 完全同口径）----
        Object[][] boxes = {
            {70.0, 120.0, 15.0, 35.0, "亚洲季风区 70-120E", 2.911},
            {0.0,  30.0,  20.0, 35.0, "撒哈拉 0-30E",      4.013},
            {250.0, 285.0, 25.0, 35.0, "美国南部 110-75W", 1.181},
            {150.0, 210.0, 25.0, 35.0, "北太平洋 150E-150W", 0.880},
            {300.0, 350.0, 25.0, 35.0, "北大西洋 60-10W",  0.885},
        };

        say("");
        say("=== 框平均（直接测量，JJA，地球掩膜）===");
        say("  框                  divU(1e-6/s)  辐合点%   kappa   cellP(Pa)  w_zm(1e-3)  w_loc(1e-3)  max(0,w_loc)均值  w_eff(1e-3)  max(0,w_eff)均值   q        P_mmday  隐含w_eff(1e-3)  比");
        for (Object[] bx : boxes) {
            double lo = (double) bx[0], hi = (double) bx[1];
            double la = (double) bx[2], hb = (double) bx[3];
            long n = 0, nConv = 0;
            double sDiv = 0, sKap = 0, sCell = 0, sWzm = 0, sWloc = 0, sWlocP = 0, sWeff = 0, sWeffP = 0, sQ = 0, sP = 0;
            for (double latd = la + 2.5; latd <= hb; latd += 2.5) {
                int z = WorldContract.zOfLat(latd);
                for (int c = 0; c < 72; c++) {
                    double lon = (c + 0.5) * 360.0 / 72;
                    if (lon < lo || lon > hi) continue;
                    int x = (int) Math.round((c + 0.5) * CIRC / 72);
                    double lat = WorldContract.latOf(z);
                    double k = Atmosphere.kappaAt(x, z, sd, cell);
                    double[] ux = Atmosphere.windAt(x + GRAD, z, sd, cell, th, GRAD);
                    double[] uw = Atmosphere.windAt(x - GRAD, z, sd, cell, th, GRAD);
                    double[] un = Atmosphere.windAt(x, z + GRAD, sd, cell, th, GRAD);
                    double[] us = Atmosphere.windAt(x, z - GRAD, sd, cell, th, GRAD);
                    double divU = (ux[0] - uw[0]) / (2.0 * GRAD) + (un[1] - us[1]) / (2.0 * GRAD);
                    double wT = PrecipField.wEff(lat, th, divU);
                    double wZ = PrecipField.wEff(lat, th, 0.0);
                    double wL = wT - wZ;
                    double[] dg = new double[8];
                    P546.decompose(x, z, sd, cell, th, dg);
                    double q = dg[1];
                    double pm = PrecipField.mmPerDay(x, z, sd, cell, th, GRAD);
                    sDiv += divU; sKap += k; sWzm += wZ; sWloc += wL;
                    sWlocP += Math.max(0, wL); sWeff += wT; sWeffP += Math.max(0, wT);
                    sQ += q; sP += pm;
                    sCell += Atmosphere.cellPressure(lat, k, th);
                    if (divU < 0) nConv++;
                    n++;
                }
            }
            double q = sQ / n, Pm = sP / n;
            // 由框 P 与框 q 反解隐含 w_eff（§349 的算法，独立于直接测量）
            double wImp = (Pm / (86400.0 * 1000.0)) * PrecipField.RHO_WATER
                         / (PrecipField.EPS_C * PrecipField.RHO_AIR * q);
            double wMeas = sWeffP / n;
            say(String.format(LF, "  %-18s %10.3f %8.1f %7.3f %10.1f %11.3f %12.3f %14.3f %12.3f %14.3f %9.5f %8.3f %12.3f %6.2f",
                bx[4], sDiv / n * 1e6, 100.0 * nConv / n, sKap / n, sCell / n,
                sWzm / n * 1e3, sWloc / n * 1e3, sWlocP / n * 1e3, sWeff / n * 1e3, sWeffP / n * 1e3,
                q, Pm, wImp * 1e3, wMeas > 1e-9 ? wImp / wMeas : -1));
        }

        // ---- 逐纬度：只看两个关键框（框平均会掩盖纬度结构，§349 的归因是逐纬度的）----
        say("");
        say("=== 逐纬度分解（1e-3 m/s；divU 单位 1e-6/s）===");
        say("  框        lat  divU   辐合?  kappa  cellP(Pa)  w_zm   w_loc  w_eff  P588(mm/d)");
        for (Object[] bx : new Object[][]{ boxes[0], boxes[1] }) {
            double lo = (double) bx[0], hi = (double) bx[1];
            double la = (double) bx[2], hb = (double) bx[3];
            for (double latd = la + 2.5; latd <= hb; latd += 5.0) {
                int z = WorldContract.zOfLat(latd);
                long n = 0, nConv = 0; double sDiv = 0, sKap = 0, sCell = 0, sWz = 0, sWl = 0, sWe = 0, sP = 0;
                for (int c = 0; c < 72; c++) {
                    double lon = (c + 0.5) * 360.0 / 72;
                    if (lon < lo || lon > hi) continue;
                    int x = (int) Math.round((c + 0.5) * CIRC / 72);
                    double lat = WorldContract.latOf(z);
                    double k = Atmosphere.kappaAt(x, z, sd, cell);
                    double[] ux = Atmosphere.windAt(x + GRAD, z, sd, cell, th, GRAD);
                    double[] uw = Atmosphere.windAt(x - GRAD, z, sd, cell, th, GRAD);
                    double[] un = Atmosphere.windAt(x, z + GRAD, sd, cell, th, GRAD);
                    double[] us = Atmosphere.windAt(x, z - GRAD, sd, cell, th, GRAD);
                    double divU = (ux[0] - uw[0]) / (2.0 * GRAD) + (un[1] - us[1]) / (2.0 * GRAD);
                    double wT = PrecipField.wEff(lat, th, divU);
                    double wZ = PrecipField.wEff(lat, th, 0.0);
                    sDiv += divU; sKap += k; sCell += Atmosphere.cellPressure(lat, k, th);
                    sWz += wZ; sWl += wT - wZ; sWe += wT;
                    sP += PrecipField.mmPerDay(x, z, sd, cell, th, GRAD);
                    if (divU < 0) nConv++;
                    n++;
                }
                say(String.format(LF, "  %-8s %4.1f %7.3f %5s %6.3f %10.1f %6.3f %6.3f %6.3f %10.3f",
                    ((String) bx[4]).substring(0, 4), latd, sDiv / n * 1e6, (nConv * 2 > n ? "是" : "否"),
                    sKap / n, sCell / n, sWz / n * 1e3, sWl / n * 1e3, sWe / n * 1e3, sP / n));
            }
        }

        say("");
        say("  判据 A（§349 直接确证）：撒哈拉的 max(0,w_loc) 均值 应 >= 亚洲的 —— 若成立，"+"模型确实把更强的低层辐合给了沙漠");
        say("  判据 B（手术靶点确证）：cellPressure 在撒哈拉与亚洲**都应为负**（热低压）且量级相近");
        say("  判据 C（反解一致性）：隐含w_eff/实测max(0,w_eff) 应接近 1（两者独立路径，一致即互证）");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
