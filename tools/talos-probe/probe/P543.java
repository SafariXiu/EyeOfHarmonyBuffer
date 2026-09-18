package probe;

import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P543 —— D1 契约守卫。
 *
 * <p>第一步（口径解耦）时本探针证明的是「什么都没动」（逐位 0）；
 * <b>现在 D1 已落地，它证明的是「动对了」</b>：纬度全程连续、f 不再跳号、行星尺度不变。
 */
public class P543 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P543] " + s); System.out.println("[P543] " + s); }

    static double mx = 0;
    static String mxTag = "(无)";
    static void cmp(String tag, double got, double want) {
        double d = Math.abs(got - want);
        if (d > mx) { mx = d; mxTag = tag; }
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p543_report.txt"), "UTF-8");
        final int C = WorldContract.Z_CYCLE;
        final int D = WorldContract.MAX_D;
        say("P543：D1 契约守卫（极点已分离）");
        say("");
        say("=== A. 常量 ===");
        say(String.format(LF, "  Z_CYCLE = %d   （期望 40000000 = 4 x MAX_D）   %s", C, C == 40_000_000 ? "OK" : "**FAIL**"));
        say(String.format(LF, "  MAX_D   = %d   （期望 10000000）               %s", D, D == 10_000_000 ? "OK" : "**FAIL**"));
        double rWant = D / (Math.PI / 2.0);
        say(String.format(LF, "  R_EFF   = %.3f m   （期望 %.3f，= MAX_D/(pi/2)）   差 = %.3e",
            WorldContract.R_EFF, rWant, Math.abs(WorldContract.R_EFF - rWant)));
        double rFromCycle = C / (2.0 * Math.PI);
        say(String.format(LF, "  R = Z_CYCLE/(2pi) = %.3f m   ⇒ 与 R_EFF 差 = %.3e m   %s",
            rFromCycle, Math.abs(rFromCycle - WorldContract.R_EFF),
            Math.abs(rFromCycle - WorldContract.R_EFF) < 1.0 ? "行星尺度一致 OK" : "**不一致 FAIL**"));
        say("");
        say("=== B. 函数逐点对照**新**显式公式（最大绝对差必须恰好 0）===");
        int np = 0;
        for (int z = -3 * C; z <= 3 * C; z += 617_003) {
            int u = ((z % C) + C) % C;
            double q = (double) u / (C / 4);
            double t;
            if (q < 1.0) t = q; else if (q < 3.0) t = 2.0 - q; else t = q - 4.0;
            cmp("latOf", WorldContract.latOf(z), t * Math.PI / 2.0);
            cmp("bandD", WorldContract.bandD(z), Math.abs(t));
            cmp("hemisphereSign", WorldContract.hemisphereSign(z, C), t >= 0 ? 1.0 : -1.0);
            np++;
        }
        for (double lat = -Math.PI / 2; lat <= Math.PI / 2; lat += 0.0173) {
            cmp("betaForLatitude", WorldContract.betaForLatitude(lat),
                2.0 * WorldContract.OMEGA * (2.0 * Math.PI) / C * Math.cos(lat));
            cmp("coriolis", WorldContract.coriolis(lat), 2.0 * WorldContract.OMEGA * Math.sin(lat));
            np++;
        }
        say(String.format(LF, "  采样 %d 点   最大绝对差 = %.3e   （在 %s）   ⇒ %s", np, mx, mxTag,
            mx < 1e-12 ? "实现与公式一致 OK（<1e-12 = 浮点 1ULP 级）" : "**有偏差 FAIL**"));
        say("");
        say("=== C. ★ D1 的**全部意义**：f 不再跳号 ===");
        double j1 = Math.abs(WorldContract.coriolis(WorldContract.latOf(D - 1)) - WorldContract.coriolis(WorldContract.latOf(D + 1)));
        double j2 = Math.abs(WorldContract.coriolis(WorldContract.latOf(2 * D - 1)) - WorldContract.coriolis(WorldContract.latOf(2 * D + 1)));
        double j3 = Math.abs(WorldContract.coriolis(WorldContract.latOf(-1)) - WorldContract.coriolis(WorldContract.latOf(1)));
        say(String.format(LF, "  |delta f| 跨 z=MAX_D   (北极, +-1 格) = %.6e   %s", j1, j1 < 1e-6 ? "OK" : "**FAIL**"));
        say(String.format(LF, "  |delta f| 跨 z=2*MAX_D (赤道, +-1 格) = %.6e   %s", j2, j2 < 1e-6 ? "OK" : "**FAIL**"));
        say(String.format(LF, "  |delta f| 跨 z=0       (赤道, +-1 格) = %.6e   %s", j3, j3 < 1e-6 ? "OK" : "**FAIL**"));
        say("  （D1 之前：跨 z=MAX_D 的 |delta f| = 2.9e-04，即 f 硬翻号）");
        say("");
        say("=== D. zOfLat 往返 ===");
        say("     latDeg      zOfLat        latOf(zOfLat)      误差(度)");
        boolean zok = true;
        for (double lat : new double[]{0, 5, 23.5, 45, 62.5, 85, 90, -5, -23.5, -45, -62.5, -85, -90}) {
            int z = WorldContract.zOfLat(lat);
            double back = Math.toDegrees(WorldContract.latOf(z));
            double e = Math.abs(back - lat);
            if (e > 0.001) zok = false;
            say(String.format(LF, "  %+8.2f   %10d   %+13.5f   %.2e", lat, z, back, e));
        }
        say(String.format(LF, "  ⇒ 往返判定 = %s", zok ? "OK" : "**FAIL**"));
        say("");
        say("=== E. 采样值存档 ===");
        say("        z          bandD      latOf(deg)     sin(lat)        f(1/s)");
        int[] zs = {0, D / 2, D, D + D / 2, 2 * D, 2 * D + D / 2, 3 * D, 3 * D + D / 2, 4 * D};
        for (int z : zs) {
            double lat = WorldContract.latOf(z);
            say(String.format(LF, "  %9d   %11.7f   %+11.5f   %+10.6f   %+.6e",
                z, WorldContract.bandD(z), Math.toDegrees(lat), Math.sin(lat), WorldContract.coriolis(lat)));
        }
        say("");
        say("=== F. WorldContract 自带自检 ===");
        say(WorldContract.d1SelfCheck());
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
