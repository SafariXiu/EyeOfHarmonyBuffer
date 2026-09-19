package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.*;

// P719 -- §548：逐字内联复刻 divAt 的源码，与 divAt 的输出直接对撞。找出到底哪一环不等。
public class P719 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s){ rep.println("[P719] "+s); System.out.println("[P719] "+s); rep.flush(); }
    static int rows, NX; static double[] src;

    // === 与 StationaryWave.divAt 第 696-711 行逐字相同（只把 fxOf/fjOf 抄进来）===
    static double replica(int x, int z){
        double fx = x / (2 * Math.PI * REFF) * NX;
        double latRad = WorldContract.latOf(z);
        double fj = (latRad + Math.PI / 2) / Math.PI * (rows - 1);
        int i0 = (int) Math.floor(fx), j0 = (int) Math.floor(fj);
        double tx = fx - i0, tj = fj - j0;
        int i0m = ((i0 % NX) + NX) % NX, i1m = (((i0 + 1) % NX) + NX) % NX;
        int j0m = j0 < 0 ? 0 : (j0 > rows - 2 ? rows - 2 : j0);
        int j1m = j0m + 1;
        double v00 = src[j0m * NX + i0m], v10 = src[j0m * NX + i1m];
        double v01 = src[j1m * NX + i0m], v11 = src[j1m * NX + i1m];
        return (v00 * (1 - tx) + v10 * tx) * (1 - tj) + (v01 * (1 - tx) + v11 * tx) * tj;
    }

    static double REFF = 6366197.724;   // WorldContract.R_EFF（先按此值算，再反推校准）

    public static void main(String[] a) throws Exception {
        rep = new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p719_report.txt"),"UTF-8");
        say("P719: §548 内联复刻 divAt 对撞");
        StationaryWave.ENABLED = true; StationaryWave.Q_SCALE = 1e-3;
        StationaryWave.invalidate();
        StationaryWave.ensureSolved(SimTerrain.seedOf(1022228679), PlateField.PLATE_CELL, Atmosphere.theta(0.0), 500_000);
        rows = StationaryWave.gridRows(); NX = StationaryWave.NX;
        double[] d = StationaryWave.divGridCopy();
        say(String.format(LF,"  rows=%d NX=%d  dv.length=%d  rows*NX=%d  %s",
            rows, NX, (d==null?-1:d.length), rows*NX, (d!=null && d.length==rows*NX)?"长度一致":"<<< 长度不一致！"));
        say(String.format(LF,"  selfCheckMapping()=%.6e （健康 <0.01）", StationaryWave.selfCheckMapping()));
        src = d;

        say("");
        say("  用 WorldContract.R_EFF=6366197.724 复刻：");
        int[][] pts = {{25,95},{25,15},{30,100},{20,80},{-36,67}};
        double worstD = 0;
        for (int[] p : pts){
            int z = WorldContract.zOfLat(p[0]);
            int x = (int) Math.round(p[1]/360.0 * WorldContract.Z_CYCLE);
            double at = StationaryWave.divAt(x, z);
            double rp = replica(x, z);
            double rel = Math.abs(at-rp)/Math.max(1e-300, Math.abs(at));
            worstD = Math.max(worstD, rel);
            say(String.format(LF,"    (%+4dN,%4dE) divAt=%+.6e  复刻=%+.6e  相对差=%.3e %s",
                p[0],p[1],at,rp,rel, rel<1e-12?"== 逐位一致":"<<< 不一致"));
        }
        say(String.format(LF,"  最坏相对差=%.3e", worstD));

        say("");
        say("  若上面全部『逐位一致』⇒ divAt 的实现与我的理解完全相同，");
        say("  则 P717/P718 的『不符』只能来自我的探针在【取 dv 与调 divAt 之间场被换掉了】。");
        say("  检验：取 dv 后【再调一次 ensureSolved】（同参数），看 divAt 是否跟着变。");
        double before = StationaryWave.divAt(WorldContract.zOfLat(25), 10555556);
        StationaryWave.ensureSolved(SimTerrain.seedOf(1022228679), PlateField.PLATE_CELL, Atmosphere.theta(0.0), 500_000);
        double after = StationaryWave.divAt(WorldContract.zOfLat(25), 10555556);
        double[] d2 = StationaryWave.divGridCopy();
        boolean same = true; if (d2 != null && d != null && d2.length == d.length)
            for (int k=0;k<d.length;k++) if (d[k]!=d2[k]) { same=false; break; }
        say(String.format(LF,"    divAt before=%+.6e after=%+.6e  场数组相同=%s", before, after, same?"是":"否 <<<"));
        say("    （若 after!=before 或数组不同 ⇒ ensureSolved 每次都会重解并换场，");
        say("      我的探针拿到的 dv 与随后 divAt 读的不是同一份 ⇒ N-1 是探针口径问题）");
        say("DONE");
        System.out.println("JAVA_EXIT=0");
    }
}
