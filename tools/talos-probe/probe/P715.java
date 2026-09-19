package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P715 -- §548 (N-1 判决)：divAt 与网格值在【结点】上是否逐位相同？
//   若相同 ⇒ N-1 不是缺陷，而是「拿插值值比结点值」的比较错误；
//   若不同 ⇒ divAt 的坐标映射确有缺陷。
public class P715 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s){ rep.println("[P715] "+s); System.out.println("[P715] "+s); rep.flush(); }

    public static void main(String[] a) throws Exception {
        rep = new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p715_report.txt"),"UTF-8");
        say("P715: §548 N-1 判决 —— 结点上 divAt vs 网格值");
        // 与 P585 同配置地把求解器解出来
        StationaryWave.ENABLED = true;
        StationaryWave.Q_SCALE = 1e-3;
        StationaryWave.invalidate();
        StationaryWave.ensureSolved(SimTerrain.seedOf(1022228679), PlateField.PLATE_CELL, Atmosphere.theta(0.0), 500_000);
        int rows = StationaryWave.gridRows(), nx = StationaryWave.NX;
        double[] dv = StationaryWave.divGridCopy();
        say(String.format(LF,"  网格 rows=%d NX=%d；预期（P585 的配置）", rows, nx));
        say("");
        double lx = WorldContract.Z_CYCLE;      // 全周长 = 4*MAX_D（P585 里就是这个量）
        int tested = 0, exact = 0;
        double worst = 0.0; int wj = -1, wi = -1;
        say("  抽样结点（每 5 行 x 每 7 列）：j i  | x z | divAt | 网格 | 相对差");
        for (int j = 0; j < rows; j += 5) {
            double lat = -90.0 + 180.0 * j / (rows - 1);
            int z = WorldContract.zOfLat(lat);
            for (int i = 0; i < nx; i += 7) {
                int x = (int) Math.round(lx * i / nx);
                double at = StationaryWave.divAt(x, z);
                double grid = dv[j * nx + i];
                double rel = (Math.abs(grid) < 1e-30) ? Math.abs(at - grid)
                                                      : Math.abs(at - grid) / Math.abs(grid);
                tested++;
                if (at == grid) exact++;
                if (rel > worst) { worst = rel; wj = j; wi = i; }
                if (rel > 1e-6)
                    say(String.format(LF,"    j=%2d i=%2d | x=%8d z=%8d | %+.6e | %+.6e | %.2f%%",
                        j, i, x, z, at, grid, 100 * rel));
            }
        }
        say("");
        say(String.format(LF,"  抽样结点数 = %d ；【逐位相同】= %d ；最坏相对差 = %.3e (j=%d i=%d)",
            tested, exact, worst, wj, wi));
        say("");
        say(String.format(LF,"  判读：%s", (exact == tested)
            ? "结点上【全部逐位相同】⇒ N-1 不是缺陷；先前看到的 1%~35% 是【拿插值值比结点值】造成的"
            : "结点上仍有差异 ⇒ divAt 的坐标映射确有缺陷"));
        say("DONE");
        System.out.println("JAVA_EXIT=0");
    }
}
