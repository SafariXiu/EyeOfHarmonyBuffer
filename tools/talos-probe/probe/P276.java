package probe;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.GyreRow;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Locale;

/** P276：西边界分布的**可信测量**（400 条纬线，两种口径）。 */
public class P276 {

    static final int SEED = 1022228679;
    static final int ZC = WorldContract.Z_CYCLE;
    static final double TAU0 = 0.0685, H_T = 4000.0, A_H = 1.9e4, H = 5000.0, W = 100_000.0;
    static final int N = 400;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");

    public static void main(String[] args) throws Exception {
        PrintStream rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p276_report.txt"), "UTF-8");
        int cell = PlateField.PLATE_CELL;
        GyreRow.BandedWind wind = new GyreRow.BandedWind(TAU0, ZC);
        double[] v = new double[N], wd = new double[N];
        int n = 0;
        for (int k = 0; k < N; k++) {
            int z = (int) ((k + 0.5) / N * ZC);
            GyreRow.Params p = new GyreRow.Params();
            p.h = H; p.rhoH = 1025.0 * H_T; p.aH = A_H; p.zCycle = ZC;
            GyreRow.Row r = GyreRow.solve((int) ((long) k * 7919 % 9_000_000), z, SEED, cell, wind, p);
            if (!r.valid) continue;
            if ((r.eastX - r.westX) < 2 * Math.PI * p.deltaAt(z)) continue;
            int q = Math.min(r.n, (int) (W / H));
            double s = 0;
            for (int i = 0; i < q; i++) s += r.v[i];
            v[n] = Math.abs(s / q * 1000);
            wd[n] = (r.eastX - r.westX) / 1000.0;
            n++;
        }
        say(rep, String.format(LF, "P276：西边界分布 N=%d 条纬线，合格 %d 条（PLATE_CELL=%d, Z_CYCLE=%d）", N, n, cell, ZC));
        double[] a = Arrays.copyOf(v, n); Arrays.sort(a);
        double[] b = Arrays.copyOf(wd, n); Arrays.sort(b);
        int p75 = 0; double sum = 0;
        for (double x : a) { sum += x; if (x >= 75) p75++; }
        say(rep, String.format(LF, "  口径 A（按纬线均匀，= 玩家随机落点）:"));
        say(rep, String.format(LF, "     p10 %.1f  p25 %.1f  p50 %.1f  p75 %.1f  p90 %.1f  max %.1f  均值 %.1f mm/s",
            q(a,.10), q(a,.25), q(a,.50), q(a,.75), q(a,.90), a[n-1], sum/n));
        say(rep, String.format(LF, "     >=75 占比 %d/%d = %.0f%%   中位对 75：%s",
            p75, n, p75 * 100.0 / n, q(a,.50) >= 75 ? "达标" : String.format(LF, "差 %.2fx", 75/q(a,.50))));
        // 口径 B：按海盆宽度加权（等价于「每单位海盆面积上随机取一点」）
        double num = 0, den = 0;
        for (int i = 0; i < n; i++) { num += v[i] * wd[i]; den += wd[i]; }
        say(rep, String.format(LF, "  口径 B（按海盆宽度加权 = 按面积）: 加权均值 %.1f mm/s", num / den));
        say(rep, String.format(LF, "  盆宽: p10 %.0f  p50 %.0f  p90 %.0f  max %.0f km（n=%d）", q(b,.10), q(b,.50), q(b,.90), b[n-1], n));
        say(rep, String.format(LF, "  相关：带内均速 / 盆宽 比值 p50 = %.3e（应近似为 curl/(rho0*H*beta*W) 的常数）",
            q(a,.50) / Math.max(1, q(b,.50)) * 1000 / 1000));
        say(rep, "");
        say(rep, "  结论：口径 A 是验收口径（玩家在世界上随机落点会看到的量级）。");
        rep.close();
    }

    static double q(double[] s, double p) { return s[Math.min(s.length - 1, (int) (p * s.length))]; }

    static void say(PrintStream rep, String s) { System.out.println("[P276] " + s); rep.println("[P276] " + s); }
}
