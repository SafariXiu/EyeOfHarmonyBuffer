package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

// P614 -- 常驻物理门（目标第 4 项）：判据以【物理正确】为准，不引用地球数值。
// 每一条都是「任何自转行星都必须成立的关系」，失败即报告，不做调参。
public class P614 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P614] " + s); System.out.println("[P614] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }

    static int nPass = 0, nTotal = 0;
    static void gate(String name, boolean ok, String detail) {
        nTotal++; if (ok) nPass++;
        say(String.format(LF, "  [%s] %-34s %s", ok ? "通过" : "不通过", name, detail));
    }

    // ---------- A. 质量守恒 ----------
    static void gateMassConservation() {
        double s = 0, a = 0;
        for (int i = 0; i < 1800; i++) {
            double w = ZonalTables.wZm((i + 0.5) * 0.05);
            s += w * 0.05; a += Math.abs(w) * 0.05;
        }
        double imb = s / a;
        gate("胞内质量守恒 |Integral w/Integral|w||", Math.abs(imb) < 1e-3,
             String.format(LF, "= %+.4f（W_ZM 表；它是降水指标不是质量流函数，已知）", imb));
    }

    // ---------- B. 副热带沙漠必须有季节循环 ----------
    static void gateDesertSeasonality(long sd, int cell) {
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        double[][] B = {{0, 30, 20, 35}};
        double[] r = new double[2];
        for (int p = 0; p < 2; p++) {
            double th = p == 0 ? thS : thW;
            double s = 0; long n = 0;
            for (double latd = B[0][2] + 2.5; latd <= B[0][3]; latd += 2.5)
                for (int c = 0; c < 72; c++) {
                    double lon = (c + 0.5) * 5.0;
                    if (lon < B[0][0] || lon > B[0][1]) continue;
                    s += PrecipField.mmPerDay(xOfLon(lon), zOfLat(latd), sd, cell, th, GRAD);
                    n++;
                }
            r[p] = s / n;
        }
        double ratio = Math.max(r[0], r[1]) / Math.max(1e-9, Math.min(r[0], r[1]));
        gate("副热带沙漠季节循环幅度 >= 2", ratio >= 2.0,
             String.format(LF, "撒哈拉 JJA %.3f / DJF %.3f = %.2f", r[0], r[1], ratio));
    }

    // ---------- C. 指名站点：上升流区必须比暖流区干 ----------
    static final Object[][] SITES = {
        {24.0, -14.0, "西撒哈拉", 1}, {-23.0, 15.0, "纳米布", 1},
        {-23.0, -70.0, "阿塔卡马", 1}, {28.0, -114.0, "下加利福尼亚", 1},
        {32.0, -83.0, "美国东南", 0}, {-28.0, -49.0, "巴西南部", 0},
        {-28.0, 152.0, "东澳", 0}, {34.0, 140.0, "日本", 0},
    };
    static void gateNamedSites(long sd, int cell) {
        double th = Atmosphere.theta(0.0);
        double sd1 = 0, sw = 0; int nd = 0, nw = 0;
        StringBuilder sb = new StringBuilder();
        for (Object[] s : SITES) {
            int x = xOfLon((double) s[1]), z = zOfLat((double) s[0]);
            double p = PrecipField.mmPerDay(x, z, sd, cell, th, GRAD);
            if ((int) s[3] == 1) { sd1 += p; nd++; } else { sw += p; nw++; }
            sb.append(String.format(LF, "%s=%.2f ", (String) s[2], p));
        }
        double md = sd1 / nd, mw = sw / nw;
        gate("上升流区(大陆西岸) < 暖流区(大陆东岸)", md < mw,
             String.format(LF, "%.3f vs %.3f | %s", md, mw, sb.toString()));
    }

    // ---------- D. 海岸判据的符号必须随纬度翻转 ----------
    static double[] coast(long sd, int cell, double th, double lo, double hi) {
        long nE = 0, nW = 0; double sE = 0, sW = 0;
        for (double latd = lo; latd <= hi; latd += 2.5)
            for (int hemi = -1; hemi <= 1; hemi += 2) {
                int z = zOfLat(latd * hemi);
                for (int c = 0; c < 144; c++) {
                    int x = xOfLon((c + 0.5) * 2.5);
                    if (Atmosphere.kappaMemo(x, z, sd, cell) < 0.7) continue;
                    double e = Atmosphere.eastness(x, z, sd, cell);
                    if (Math.abs(e) < 0.3) continue;
                    double p = PrecipField.mmPerDay(x, z, sd, cell, th, GRAD);
                    if (e > 0) { sE += p; nE++; } else { sW += p; nW++; }
                }
            }
        return new double[]{nE == 0 ? Double.NaN : sE / nE, nW == 0 ? Double.NaN : sW / nW};
    }
    static void gateCoastFlip(long sd, int cell) {
        double th = Atmosphere.theta(0.0);
        double[] sub = coast(sd, cell, th, 15, 35);
        double[] mid = coast(sd, cell, th, 40, 60);
        gate("副热带：大陆西岸 < 大陆东岸", sub[0] < sub[1],
             String.format(LF, "西 %.3f vs 东 %.3f", sub[0], sub[1]));
        gate("中纬：大陆西岸 > 大陆东岸", mid[0] > mid[1],
             String.format(LF, "西 %.3f vs 东 %.3f", mid[0], mid[1]));
    }

    // ---------- E. beta 的空间型（§387）----------
    static void gateBetaPattern(long sd, int cell) {
        double th = Atmosphere.theta(0.0);
        double[][] BOX = {{0, 30, 20, 35}, {70, 120, 15, 35}};
        String[] NM = {"沙漠", "季风"};
        double[] b = new double[2];
        for (int q = 0; q < 2; q++) {
            double s = 0; long n = 0;
            for (double latd = BOX[q][2] + 2.5; latd <= BOX[q][3]; latd += 2.5)
                for (int c = 0; c < 72; c++) {
                    double lon = (c + 0.5) * 5.0;
                    if (lon < BOX[q][0] || lon > BOX[q][1]) continue;
                    PrecipField.mmPerDay(xOfLon(lon), zOfLat(latd), sd, cell, th, GRAD);
                    s += PrecipField.DIAG.get()[13]; n++;
                }
            b[q] = s / n;
        }
        gate("beta 沙漠 <= 0.1 且 季风 >= 0.8（§387）", b[0] <= 0.1 && b[1] >= 0.8,
             String.format(LF, "沙漠 %.3f  季风 %.3f（S3 %s）", b[0], b[1], SoilMoisture.ENABLED ? "开" : "关"));
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p614_report.txt"), "UTF-8");
        say("P614: 常驻物理门（地球掩膜 + ETOPO1；判据不引用地球数值）");
        EarthRef.install();
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        say("");
        say("  --- 生产默认态 ---");
        gateMassConservation();
        gateDesertSeasonality(sd, cell);
        gateNamedSites(sd, cell);
        gateCoastFlip(sd, cell);
        gateBetaPattern(sd, cell);
        say("");
        say(String.format(LF, "  === 生产默认态：%d/%d 通过 ===", nPass, nTotal));
        say("");
        say("  --- 全栈组合（§409 最佳：种子+干暖）---");
        nPass = 0; nTotal = 0;
        SoilMoisture.ENABLED = true; SoilMoisture.clearMemo();
        Atmosphere.PA_DRY_WARMTH = true;
        PrecipField.WZM_ITCZ_SHIFT = false;
        SimClimate.clearCache();
        gateMassConservation();
        gateDesertSeasonality(sd, cell);
        gateNamedSites(sd, cell);
        gateCoastFlip(sd, cell);
        gateBetaPattern(sd, cell);
        say("");
        say(String.format(LF, "  === 全栈组合：%d/%d 通过 ===", nPass, nTotal));
        SoilMoisture.ENABLED = false; Atmosphere.PA_DRY_WARMTH = false;
        PrecipField.WZM_ITCZ_SHIFT = true; SoilMoisture.clearMemo();
        EarthRef.uninstall();
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}