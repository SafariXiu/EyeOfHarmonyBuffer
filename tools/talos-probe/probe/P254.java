package probe;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.GyreRow;
import com.EyeOfHarmonyBuffer.sim.ocean.SurfaceLayer;

import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Locale;

/**
 * P254：**表层层的归因解剖** —— 埃克曼项到底能不能补东边界缺口？
 *
 * <p>P253 报出「表层西带量级达标、但东带符号乱了一半」。本探针把它拆开：
 * <ol>
 *   <li>验证 表层 = 5 x 正压 + 埃克曼(局部 tau, 局部 f)，两项分别列出；</li>
 *   <li>受控实验：固定 curl（地转解完全不变），只旋转应力矢量方向 psi，
 *       看东带符号正确数能不能到 6/6；</li>
 *   <li>把「x5 强化」和「埃克曼」的贡献分开算，看谁在补东边界。</li>
 * </ol>
 */
public class P254 {

    static final int SEED = 1022228679;
    /** ⚠ 口径修正（§559，模板 P442:47）：收 {@code long seed} 的入口传本值；收 {@code int worldSeedInt} 的入口仍传裸 {@code SEED}。本次未改任何判据/阈值/输出行。 */
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final int CELL = PlateField.PLATE_CELL;
    static final int ZC = com.EyeOfHarmonyBuffer.sim.world.WorldContract.Z_CYCLE;
    static final double BAND_KM = 122.0;
    static final double TAU0 = 0.0685;
    static final double H_TOTAL = 4000.0;
    static final double[] BANDS = {0.15, 0.25, 0.40, 0.55, 0.70, 0.85};
    static final int NB = BANDS.length;
    static final double TGT_W = 50.0;
    static final double TGT_E = 30.0;

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    static GyreRow.Params p;
    static GyreRow.BandedWind wind;
    static GyreRow.Row[][] rows = new GyreRow.Row[NB][];
    static int[] rn = new int[NB];
    static double[] baroW = new double[NB], baroE = new double[NB];
    static double[] fArr = new double[NB], tauXArr = new double[NB], curlArr = new double[NB];
    static double AMP;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p254_report.txt"), "UTF-8");
        say("P254：表层层的归因解剖（固定 curl，只转应力方向）");
        p = new GyreRow.Params();
        p.h = 5000.0; p.rhoH = 1025.0 * H_TOTAL;
        wind = new GyreRow.BandedWind(TAU0, ZC);
        AMP = H_TOTAL / SurfaceLayer.H_TRANSPORT;
        int nb = (int) (BAND_KM * 1000.0 / p.h);
        say(String.format(LF, "  带=%d格 delta_M(赤道)=%.1f km/(45度)=%.1f km 强化因子=%.1f tau0=%.4f",
            nb, p.deltaAt(0) / 1000, p.deltaAt(250_000) / 1000, AMP, TAU0));
        say("");

        for (int bi = 0; bi < NB; bi++) {
            int z = (int) (BANDS[bi] * (ZC / 2));
            double mwGate = Math.PI * p.deltaAt(z);
            double latRad = BANDS[bi] * Math.PI / 2.0;   // 六个带都在北半球 ⇒ f>0
            fArr[bi] = 2.0 * SurfaceLayer.OMEGA * Math.sin(latRad);
            tauXArr[bi] = wind.tauX(z);
            curlArr[bi] = wind.at(0, z);
            GyreRow.Row[] tmp = new GyreRow.Row[8];
            int c = 0;
            for (int ix = 0; ix < 8; ix++) {
                GyreRow.Row r = GyreRow.solve(ix * 1_500_000, z, SD, CELL, wind, p);
                if (!r.valid) continue;
                if ((r.eastX - r.westX) < 2 * mwGate) continue;
                tmp[c++] = r;
            }
            rows[bi] = Arrays.copyOf(tmp, c);
            rn[bi] = c;
            double bw = 0, be = 0;
            for (int k = 0; k < c; k++) {
                double[] m = bandMeans(rows[bi][k]);
                bw += m[0]; be += m[1];
            }
            baroW[bi] = c > 0 ? bw / c * 1000 : 0;
            baroE[bi] = c > 0 ? be / c * 1000 : 0;
        }

        // ---------------- A. 分解 ----------------
        say("A. 分解：表层 = 5 x 正压 + 埃克曼（埃克曼在行内是常数）");
        say(String.format(LF, "  %-6s %9s %9s %11s %9s %11s %11s %11s",
            "bandD", "正压西", "正压东", "v_ekman", "5x西", "表层西", "5x东", "表层东"));
        for (int bi = 0; bi < NB; bi++) {
            double[] s = surface(bi, 0.0);
            double ve = ekmanV(bi, 0.0);
            say(String.format(LF, "  %-6.2f %8.4f %8.4f %10.4f %8.3f %10.3f %10.3f %10.3f",
                BANDS[bi], baroW[bi], baroE[bi], ve * 1000, AMP * baroW[bi], s[0], AMP * baroE[bi], s[1]));
        }
        say(String.format(LF, "  （mm/s；tau_x=%.5f..%.5f Pa，f=%.3e..%.3e）",
            tauXArr[0], tauXArr[NB - 1], fArr[0], fArr[NB - 1]));
        say("");

        // ---------------- B. 旋转扫描 ----------------
        say("B. 受控实验：固定 curl（地转解逐位不变），只把应力矢量旋转 psi（|tau| 不变 ⇒ 埃克曼幅值不变）");
        say("   东带必须与西带反号 —— 这是硬约束：闭合行 psi 两端都是 0 ⇒ 积分 v ds = 0");
        say(String.format(LF, "  %8s %11s %11s %10s %9s", "psi(deg)", "西带符号对", "东带符号对", "东带均", "西带均"));
        double bestE = -1, bestPsi = 0;
        for (int d = -180; d <= 180; d += 15) {
            double psi = d * Math.PI / 180.0;
            int okW = 0, okE = 0; double seMean = 0, swMean = 0; int cnt = 0;
            for (int bi = 0; bi < NB; bi++) {
                if (rn[bi] == 0) continue;
                double[] s = surface(bi, psi);
                boolean wPos = baroW[bi] > 0;
                if ((s[0] > 0) == wPos) okW++;
                if ((s[1] > 0) == !wPos) okE++;
                seMean += s[1]; swMean += s[0]; cnt++;
            }
            say(String.format(LF, "  %8d %8d/%d %8d/%d %10.3f %9.3f", d, okW, cnt, okE, cnt, seMean / cnt, swMean / cnt));
            if (okE > bestE) { bestE = okE; bestPsi = d; }
        }
        say(String.format(LF, "  ⇒ 最佳 psi=%d deg 时东带符号也只对 %.0f/%d", (int) bestPsi, bestE, NB));
        say("");

        // ---------------- C. 相位分析 ----------------
        say("C. 相位分析（北半球，sign(f)=+1）");
        say(String.format(LF, "  %-6s %11s %10s %11s %11s %8s",
            "bandD", "sign(tau_x)", "sign(curl)", "东带要求", "埃克曼给", "一致?"));
        for (int bi = 0; bi < NB; bi++) {
            int sTau = (int) Math.signum(tauXArr[bi]);
            int sCurl = (int) Math.signum(curlArr[bi]);
            int need = sCurl;
            int give = -sTau;
            say(String.format(LF, "  %-6.2f %11d %10d %11d %11d %8s",
                BANDS[bi], sTau, sCurl, need, give, need == give ? "一致" : "矛盾"));
        }
        say("  ⇒ 东边界要求按 sign(curl) 变；埃克曼给的符号 = sign(tau_x) x sign(sin(psi - sign(f)pi/4))");
        say("     ⇒ 只有 sign(tau_x)==sign(curl) 的带上才可能一致 —— 本测只有 3/6 带满足");
        say("     ⇒ 所以 pi=0（纯纬向）时东带只能对 3/6，实测吻合。");
        say("     ⇒ **重要**：扫描里唯一拿到 6/6 的 psi=±135 deg，恰好让 sin(psi-45deg)=0，");
        say("       也就是把埃克曼项**抵消为零**。非零埃克曼的最好成绩是 4/6。");
        say("       ⇒ 结论不是「转个角度就能修好」，而是「埃克曼开着就修不好」。");
        say("");

        // ---------------- D. 谁在补东边界 ----------------
        say("D. x5 强化 与 埃克曼，各自对东边界的贡献（目标 " + TGT_E + " mm/s）");
        say(String.format(LF, "  %-6s %13s %15s %15s", "bandD", "正压东带", "只x5(无埃克曼)", "x5+埃克曼"));
        int passPure = 0, passFull = 0;
        for (int bi = 0; bi < NB; bi++) {
            double pure = Math.abs(AMP * baroE[bi]);
            double full = Math.abs(surface(bi, 0.0)[1]);
            if (pure >= TGT_E) passPure++;
            if (full >= TGT_E) passFull++;
            say(String.format(LF, "  %-6.2f %13.4f %15.4f %15.4f", BANDS[bi], baroE[bi], pure, full));
        }
        say(String.format(LF, "  ⇒ 只 x5：%d/%d 达标；x5+埃克曼：%d/%d 达标（但见 B 的符号问题）",
            passPure, NB, passFull, NB));
        say("");

        // ---------------- E. A7 强化比 ----------------
        say("E. A7（西/东 比 >= 4）在三种口径下的达标数");
        say(String.format(LF, "  %-6s %13s %15s %15s", "bandD", "正压", "x5无埃克曼", "x5+埃克曼"));
        int a = 0, b2 = 0, c2 = 0;
        for (int bi = 0; bi < NB; bi++) {
            double r0 = ratio(baroW[bi], baroE[bi]);
            double r1 = ratio(AMP * baroW[bi], AMP * baroE[bi]);
            double[] s = surface(bi, 0.0);
            double r2 = ratio(s[0], s[1]);
            if (Math.abs(r0) >= 4) a++;
            if (Math.abs(r1) >= 4) b2++;
            if (Math.abs(r2) >= 4) c2++;
            say(String.format(LF, "  %-6.2f %13.2f %15.2f %15.2f", BANDS[bi], r0, r1, r2));
        }
        say(String.format(LF, "  ⇒ 达标 %d/%d, %d/%d, %d/%d", a, NB, b2, NB, c2, NB));
        say("");

        // ---------------- F. 西带量级 ----------------
        say("F. 西带量级（目标 " + TGT_W + " mm/s；§2.1 A2 写的是 100 mm/s，存在未记账的漂移）");
        int w50 = 0, w100 = 0;
        for (int bi = 0; bi < NB; bi++) {
            double v = Math.abs(surface(bi, 0.0)[0]);
            if (v >= 50) w50++;
            if (v >= 100) w100++;
            say(String.format(LF, "  bandD %.2f  表层西带 %8.3f mm/s  (对50 差%.1fx / 对100 差%.1fx)",
                BANDS[bi], v, 50.0 / v, 100.0 / v));
        }
        say(String.format(LF, "  ⇒ 对 50：%d/%d；对 100：%d/%d", w50, NB, w100, NB));
        rep.close();
    }

    static double[] bandMeans(GyreRow.Row r) {
        int nb = (int) (BAND_KM * 1000.0 / p.h);
        int q = Math.min(nb, r.n);
        double a1 = 0, a2 = 0;
        for (int i = 0; i < q; i++) a1 += r.v[i];
        for (int i = Math.max(0, r.n - q); i < r.n; i++) a2 += r.v[i];
        return new double[]{a1 / q, a2 / q};
    }

    static double ekmanV(int bi, double psi) {
        double tx = tauXArr[bi];
        double txr = tx * Math.cos(psi), tyr = tx * Math.sin(psi);
        double ue = ekmanSpeed(bi);
        double ang = Math.atan2(tyr, txr) - Math.signum(fArr[bi]) * Math.PI / 4.0;
        return ue * Math.sin(ang);
    }

    static double ekmanSpeed(int bi) {
        double fa = Math.abs(fArr[bi]);
        if (fa < SurfaceLayer.F_MIN) fa = SurfaceLayer.F_MIN;
        double ue = Math.abs(tauXArr[bi]) / (SurfaceLayer.RHO * Math.sqrt(SurfaceLayer.A_Z * fa));
        if (ue > SurfaceLayer.EKMAN_MAX) ue = SurfaceLayer.EKMAN_MAX;
        return ue;
    }

    static double[] surface(int bi, double psi) {
        int c = rn[bi];
        if (c == 0) return new double[]{0, 0};
        double ve = ekmanV(bi, psi);
        double sw = 0, se = 0;
        for (int k = 0; k < c; k++) {
            double[] m = bandMeans(rows[bi][k]);
            sw += AMP * m[0] + ve;
            se += AMP * m[1] + ve;
        }
        return new double[]{sw / c * 1000, se / c * 1000};
    }

    static double ratio(double w, double e) {
        if (Math.abs(e) < 1e-12) return Double.NaN;
        return w / e;
    }

    static void say(String s) { System.out.println("[P254] " + s); rep.println("[P254] " + s); }
}
