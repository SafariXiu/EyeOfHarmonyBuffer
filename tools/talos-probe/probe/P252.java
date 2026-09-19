package probe;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.GyreRow;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P252：**关掉目标 2 的西边界量级缺口** —— 扫 A_H 与风强。
 *
 * <p>U ∝ L·curl/(rho0·H·beta·delta_M)。盆宽已是真实海洋量级、H 已换成真实深度 4000 m，
 * 剩下的两个杠杆：
 * <ol>
 *   <li><b>curl</b>：P246 实测的生产风场连贯 curl 上限是 MIT 参照的 4.9 倍，P251 只用了 3.6 倍；</li>
 *   <li><b>A_H</b>：现行 1.9e4 是源码自注地球值 1.2e3 的 16 倍 ⇒ **往下调更物理**，
 *       而且 U ∝ A_H^(-1/3)、delta_M ∝ A_H^(1/3)（边界层更薄，h=5 km 仍能分辨到 3.9 格）。</li>
 * </ol>
 *
 * <p>带内均值一律用**固定的** pi*delta_M(A_H=1.9e4) = 122 km 作宽度，保证各档可比。
 */
public class P252 {

    static final int SEED = 1022228679;
    /** ⚠ 口径修正（§559，模板 P442:47）：收 {@code long seed} 的入口传本值；收 {@code int worldSeedInt} 的入口仍传裸 {@code SEED}。本次未改任何判据/阈值/输出行。 */
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final int CELL = PlateField.PLATE_CELL;
    static final int ZC = com.EyeOfHarmonyBuffer.sim.world.WorldContract.Z_CYCLE;
    static final double BAND_KM = 122.0;          // 固定带宽（= pi*delta_M @ A_H=1.9e4）
    static final double[] BANDS = {0.15, 0.25, 0.40, 0.55, 0.70, 0.85};
    static final double[] AHS = {1.9e4, 4.75e3, 2.4e3};
    static final double[] TAUS = {0.05, 0.0685};   // 0.0685 ≈ 0.05 * (4.9/3.6)
    static final double TARGET_MM = 50.0;

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:\\moder\\EyeOfHarmonyBuffer");
    static PrintStream rep;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build\\eoh_probe\\mtn\\p252_report.txt"), "UTF-8");
        say("关掉西边界量级缺口：扫 A_H 与风强（固定带 122 km；H=4000 m；h=5 km）");
        for (double ah : AHS) {
            for (double tau : TAUS) {
                one(ah, tau);
            }
        }
        rep.close();
    }

    static void say(String s) { System.out.println("[P252] " + s); rep.println("[P252] " + s); }

    static void one(double ah, double tau0) {
        GyreRow.Params p = new GyreRow.Params();
        p.h = 5000.0; p.aH = ah;
        GyreRow.WindCurl wind = new GyreRow.BandedWind(tau0, ZC);
        int nb = (int) (BAND_KM * 1000.0 / p.h);
        say("");
        say(String.format(LF, "  ===== A_H = %.0f（delta_M 赤道 %.1f km / 45度 %.1f km，门槛按纬度行算）  风 tau0 = %.4f  =====",
            ah, p.deltaAt(0) / 1000, p.deltaAt(250_000) / 1000, tau0));
        say(String.format(LF, "      %-9s %5s %13s %13s %9s %9s  %s",
            "bandD", "n", "西带均mm/s", "东带均mm/s", "西/东", "符号", "量级"));
        double worstW = 1e9;
        boolean allSign = true;
        for (double b : BANDS) {
            int z = (int) (b * (ZC / 2));
            double mwGate = Math.PI * p.deltaAt(z);
            int cnt = 0; double wSum = 0, eSum = 0, wA = 0, eA = 0;
            for (int ix = 0; ix < 8; ix++) {
                GyreRow.Row r = GyreRow.solve(ix * 1_500_000, z, SD, CELL, wind, p);
                if (!r.valid) continue;
                if ((r.eastX - r.westX) < 2 * mwGate) continue;
                double ws = 0, es = 0, wa = 0, ea = 0;
                int q = Math.min(nb, r.n);
                for (int i = 0; i < q; i++) { ws += r.v[i]; wa += Math.abs(r.v[i]); }
                for (int i = Math.max(0, r.n - q); i < r.n; i++) { es += r.v[i]; ea += Math.abs(r.v[i]); }
                cnt++; wSum += ws / q; eSum += es / q; wA += wa / q; eA += ea / q;
            }
            if (cnt == 0) { say(String.format(LF, "      %-9.2f   （无合格盆）", b)); continue; }
            double wm = wSum / cnt * 1000, em = eSum / cnt * 1000;
            double curlSign = -Math.sin(3 * Math.PI * b);
            boolean expWpos = curlSign < 0;
            boolean ok = (expWpos && wm > 0) || (!expWpos && wm < 0);
            if (!ok) allSign = false;
            double mag = Math.abs(wm);
            if (mag < worstW) worstW = mag;
            say(String.format(LF, "      %-9.2f %5d %13.4f %13.4f %9.2f %9s  %s",
                b, cnt, wm, em, wA / Math.max(1e-12, eA), ok ? "正确" : "错误",
                mag >= TARGET_MM ? "达标" : String.format(LF, "差%.1f倍", TARGET_MM / mag)));
        }
        say(String.format(LF, "      ⇒ 最弱带的西边界 = %.4f mm/s（目标 %.0f mm/s，%s）；符号 %s",
            worstW, TARGET_MM, worstW >= TARGET_MM ? "达标" : String.format(LF, "差 %.1f 倍", TARGET_MM / worstW),
            allSign ? "全对" : "有错"));
    }
}
