package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.nio.file.Files;
import java.util.Locale;

/**
 * P548 —— **撒哈拉 40x 异常定位**：把 P546 的降水分解套到 5 个框上。
 *
 * <p>复用 {@link P546#decompose}（同包可见），所以**分解口径与 P546 逐位相同**
 * —— 不会出现「另一支探针另一套采样」（E12）。
 *
 * <p>地球参照从 {@code refs/earth_monsoon_boxes.tsv} 读（由 {@code earth_monsoon_boxes.py}
 * 从 gpcp_ltm.nc 算出并落盘）—— **不许手抄**（M14）。
 */
public class P548 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static final File TSV = new File(ROOT, "build/eoh_probe/refs/earth_monsoon_boxes.tsv");
    static PrintStream rep;
    static void say(String s) { rep.println("[P548] " + s); System.out.println("[P548] " + s); }

    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static final int SEED = 1022228679;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p548_report.txt"), "UTF-8");
        say("P548：撒哈拉异常定位 —— 5 个框的降水分解（地球掩膜驱动）");
        say("  契约自证：Z_CYCLE=" + WorldContract.Z_CYCLE + "  gain=" + com.EyeOfHarmonyBuffer.sim.atmos.PrecipField.EDDY_PHYS_GAIN
            + "  closure=" + com.EyeOfHarmonyBuffer.sim.atmos.PrecipField.EDDY_CLOSURE);
        P546.loadMask();
        PlateField.MASK = new PlateField.LandMask() {
            public boolean isLand(int x, int z) { return P546.earthIsLand(x, z); }
        };
        // 地球参照（从脚本落盘的 TSV 读）
        double[] eJ = new double[8], eW = new double[8];
        int[] bl = new int[8];
        String[] bn = new String[8];
        int nb = 0;
        for (String ln : Files.readAllLines(TSV.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
            if (ln.startsWith("#") || ln.trim().isEmpty()) continue;
            String[] q = ln.split("\t");
            if (nb >= 8) break;
            bn[nb] = q[0]; bl[nb] = Integer.parseInt(q[1]);
            int lo = Integer.parseInt(q[1]), hi = Integer.parseInt(q[2]);
            eJ[nb] = Double.parseDouble(q[5]); eW[nb] = Double.parseDouble(q[7]);
            // 经度区间在下面按 bl 复用，这里只存地球值
            bl[nb] = lo; if (hi < lo) bl[nb] = hi;
            nb++;
        }
        say("  ✔ 地球参照已读入（" + nb + " 个框）");
        say("");

        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0);

        // 与 P547 相同的 5 个框：{lonLo, lonHi, latLo, latHi, 名字}
        Object[][] boxes = {
            {70.0, 120.0, 15.0, 35.0, "亚洲季风区 70-120E"},
            {0.0, 30.0, 20.0, 35.0, "撒哈拉 0-30E"},
            {250.0, 285.0, 25.0, 35.0, "美国南部 110-75W"},
            {150.0, 210.0, 25.0, 35.0, "北太平洋 150E-150W"},
            {300.0, 350.0, 25.0, 35.0, "北大西洋 60-10W"},
        };

        say("=== JJA 分解（mm/day）===");
        say("  框                     term1(w_zm+wLoc)  term2(风暴轴)  term3(浅对流地板)  地板胜   合计    地球JJA   比值");
        for (int bi = 0; bi < boxes.length; bi++) {
            double lo = (double) boxes[bi][0], hi = (double) boxes[bi][1];
            double la = (double) boxes[bi][2], hb = (double) boxes[bi][3];
            double s1 = 0, s2 = 0, s3 = 0, sfin = 0; long n = 0; int fw = 0;
            double dq = 0, dk = 0, dwzm = 0, dwloc = 0, dmfc = 0, dt = 0;
            double dts = 0, dqs = 0;   // ★ A1 诊断：地表温度 / 该温度下的饱和比湿
            for (double latd = la + 2.5; latd <= hb; latd += 2.5) {
                int z = (int) Math.round(latd / 90.0 * WorldContract.MAX_D);
                for (int c = 0; c < 72; c++) {
                    int x = (int) Math.round((c + 0.5) * CIRC / 72);
                    double lon = (c + 0.5) * 360.0 / 72;
                    if (lon < lo || lon > hi) continue;
                    double[] dg = new double[8];
                    double[] r = P546.decompose(x, z, sd, cell, thS, dg);
                    s1 += r[0]; s2 += r[1]; s3 += r[2]; sfin += r[4];
                    if (r[3] < r[2]) fw++;
                    dq += dg[1]; dk += dg[0]; dwzm += dg[3]; dwloc += dg[4]; dmfc += dg[5];
                    // ★ A1 诊断：气柱饱和度 RH = q / qSat(T_sfc) —— 这是「对流触发」的候选判据
                    double tsF = Atmosphere.surfaceTemp(x, z, sd, cell, thS);
                    dts += tsF; dqs += com.EyeOfHarmonyBuffer.sim.atmos.PrecipField.qSat(tsF);
                    n++;
                }
            }
            if (n == 0) { say("  " + boxes[bi][4] + "  （无点）"); continue; }
            double my = sfin / n, ey = eJ[bi];
            say(String.format(LF, "  %-20s %12.3f %14.3f %16.3f  %5.1f%% %9.3f %9.3f %7.2fx",
                boxes[bi][4], s1 / n, s2 / n, s3 / n, 100.0 * fw / n, my, ey, ey > 0 ? my / ey : -1));
            say(String.format(LF, "      （诊断）kappa=%.3f  q=%.5f  w_zm=%+.3e  wLoc=%+.3e  mfc=%+.3e   n=%d",
                dk / n, dq / n, dwzm / n, dwloc / n, dmfc / n, n));
            say(String.format(LF, "      ★A1  tSfc=%.2f K (%.1f C)   qSat(tSfc)=%.5f   RH=q/qSat=%.3f   q/RH_SEA=%.5f",
                dts / n, dts / n - 273.15, dqs / n, (dq / n) / (dqs / n), (dq / n) / com.EyeOfHarmonyBuffer.sim.atmos.PrecipField.RH_SEA));
        }
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
