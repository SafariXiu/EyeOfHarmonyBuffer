package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.StationaryWave;

import java.io.*;
import java.util.Locale;

// P583 -- 符号定案靶子：注入单个高斯【热源】，看中心点的 div(V) 符号。
// 物理不可争议：加热 => 上升 => 低层辐合 => div < 0。
// 用自造强迫把「符号约定」和「真实降水场形状」两件事分开 —— 这是 391 自证缺的一环。
public class P583 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P583] " + s); System.out.println("[P583] " + s); }
    static final double C2 = 70.0 * 70.0;

    static double[][] bump(int rows, int nx, int i0, int j0, double si, double sj, double q0) {
        double[][] q = new double[rows][nx];
        for (int j = 1; j < rows - 1; j++)
            for (int i = 0; i < nx; i++) {
                double di = i - i0;
                if (di > nx / 2.0) di -= nx;
                if (di < -nx / 2.0) di += nx;
                double dj = j - j0;
                q[j][i] = q0 * Math.exp(-(di * di / (2 * si * si) + dj * dj / (2 * sj * sj)));
            }
        return q;
    }

    static void run(String tag, int rows, int nx, int i0, int j0, double q0, boolean zeroF) {
        StationaryWave.Q_OVERRIDE = bump(rows, nx, i0, j0, 5.0, 7.0, q0);
        StationaryWave.ZERO_F = zeroF;
        StationaryWave.ENABLED = true;
        StationaryWave.invalidate();
        StationaryWave.ensureSolved(12345L, 0, 0.0, 500_000);
        double[] dv = StationaryWave.divGridCopy();
        double[] qg = StationaryWave.qGridCopy();
        double dc = dv[j0 * nx + i0], qc = qg[j0 * nx + i0];
        say(String.format(LF, "  %-34s Q0=%+.2f f=%s  Q(中心)=%+.4e  div(中心)=%+.4e  -Q/c^2=%+.4e  => %s",
            tag, q0, zeroF ? "0  " : "全 ", qc, dc, -qc / C2, dc < 0 ? "辐合 ✓" : "辐散 ✗"));
        // 沿经度剖面（中心行）
        StringBuilder sb = new StringBuilder("     沿中心行: ");
        for (int i = i0 - 4; i <= i0 + 4; i++) {
            int ii = ((i % nx) + nx) % nx;
            sb.append(String.format(LF, "%+.2e ", dv[j0 * nx + ii]));
        }
        say(sb.toString());
        // 沿纬度剖面（中心列）
        StringBuilder sb2 = new StringBuilder("     沿中心列: ");
        for (int j = j0 - 4; j <= j0 + 4; j++) sb2.append(String.format(LF, "%+.2e ", dv[j * nx + i0]));
        say(sb2.toString());
        say(String.format(LF, "     残差=%.3e  forcing_max=%.3e", StationaryWave.lastResidual, StationaryWave.lastForcing));
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p583_report.txt"), "UTF-8");
        int rows = StationaryWave.NPHI + 2, nx = StationaryWave.NX;
        say("P583: 高斯热源靶子 —— 中心 div(V) 必须是【负】（加热=>上升=>低层辐合）");
        say(String.format(LF, "  网格 rows=%d NX=%d  dy_=%.1f km  dx=%.1f km  c^2=%.0f",
            rows, nx, 1e7 / (rows - 1) / 1e3, 4e7 / nx / 1e3, C2));
        say("");
        // 用赤道附近做基准（f 小），再到中纬
        run("赤道 j=24 零 f", rows, nx, 32, 24, +1.0, true);
        run("赤道 j=24 真 f", rows, nx, 32, 24, +1.0, false);
        run("25N j=38 零 f", rows, nx, 32, 38, +1.0, true);
        run("25N j=38 真 f", rows, nx, 32, 38, +1.0, false);
        say("");
        say("  反向对照（冷源，应为 div > 0）：");
        run("25N j=38 真 f 冷源", rows, nx, 32, 38, -1.0, false);
        StationaryWave.ENABLED = false;
        StationaryWave.Q_OVERRIDE = null;
        StationaryWave.ZERO_F = false;
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
