package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.*;

// P717 -- §548 (N-1 判决)：从数据反解 divAt 的插值权重，验证它就是结点网格上的双线性插值。
//   divAt(int x,int z) 取整数坐标；行几何 fj=(latOf(z)+pi/2)/pi*(rows-1) 可公开算出，
//   于是可解出 tx 并与「x 在两结点间的自然分数位置」对照。吻合 ⇒ 审计读的「最近结点值」是另一个量。
public class P717 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s){ rep.println("[P717] "+s); System.out.println("[P717] "+s); rep.flush(); }
    static double frac(double t){ return t - Math.floor(t); }

    static int rows, nx; static double[] dv; static double lx;
    static double A_, B_, tj_; static int j0_, tjok_;

    // 给定 (x,z)：算出 j0,tj, 以及 A=(1-tj)v00+tj v10, B=(1-tj)v01+tj v11
    static boolean bracket(int x, int z){
        int r = rows, n = nx;
        double lat = WorldContract.latOf(z);
        double fj = (lat + Math.PI/2) / Math.PI * (r - 1);
        int j0 = (int) Math.floor(fj); if (j0 < 0) j0 = 0; if (j0 > r-2) j0 = r-2;
        double tj = fj - j0; if (tj < 0) tj = 0; if (tj > 1) tj = 1;
        double fx = frac((double) x * n / lx) * n;      // 预测的连续列坐标
        int i0 = (int) Math.floor(fx); double tx = fx - i0;
        int i1 = (i0 + 1) % n; i0 = ((i0 % n) + n) % n;
        j0_ = j0; tj_ = tj;
        double v00 = dv[j0*n+i0], v01 = dv[j0*n+i1], v10 = dv[(j0+1)*n+i0], v11 = dv[(j0+1)*n+i1];
        A_ = (1-tj)*v00 + tj*v10; B_ = (1-tj)*v01 + tj*v11;
        double bil = (1-tx)*A_ + tx*B_;
        double at = StationaryWave.divAt(x, z);
        double scale = Math.max(1e-300, Math.abs(A_) + Math.abs(B_));
        return Math.abs(at - bil) / scale < 1e-7;
    }

    public static void main(String[] a) throws Exception {
        rep = new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p717_report.txt"),"UTF-8");
        say("P717: §548 N-1 判决 —— divAt 是不是结点网格上的双线性插值");
        StationaryWave.ENABLED = true; StationaryWave.Q_SCALE = 1e-3;
        StationaryWave.invalidate();
        StationaryWave.ensureSolved(SimTerrain.seedOf(1022228679), PlateField.PLATE_CELL, Atmosphere.theta(0.0), 500_000);
        rows = StationaryWave.gridRows(); nx = StationaryWave.NX;
        dv = StationaryWave.divGridCopy(); lx = WorldContract.Z_CYCLE;
        say(String.format(LF,"  rows=%d NX=%d  lx=%.0f 列距=%.0f", rows, nx, lx, lx/nx));
        say("");

        say("  [A] 审计 P585 的 5 个测试点 (lat,lon)：divAt / 双线性预测 / 最近结点值 三方对照");
        say("      lat  lon |    x        z    | j0   tj   | divAt            | 双线性预测        | 最近结点值        | 差/结点值");
        int[][] pts = {{25,95},{25,15},{30,100},{20,80},{-36,67}};
        for (int[] p : pts){
            int z = WorldContract.zOfLat(p[0]);
            int x = (int) Math.round(p[1]/360.0*lx);
            double at = StationaryWave.divAt(x, z);
            bracket(x, z);
            double fx = frac((double)x*nx/lx)*nx; int i0 = (int)Math.floor(fx); double tx = fx-i0;
            int i1 = (i0+1)%nx; int i0n = ((i0%nx)+nx)%nx;
            double bil = (1-tx)*A_ + tx*B_;
            int jn = (int) Math.round((WorldContract.latOf(z)+Math.PI/2)/Math.PI*(rows-1));
            int in = (int) Math.round((double)x/lx*nx);
            if (jn>rows-1) jn=rows-1; if (jn<0) jn=0;
            in = ((in%nx)+nx)%nx;
            double node = dv[jn*nx+in];
            double rel = Math.abs(at-node)/Math.max(1e-300,Math.abs(node));
            say(String.format(LF,"      %+4d %4d | %8d %8d | %2d %.3f | %+.6e | %+.6e | %+.6e | %.2f%%%s",
                p[0],p[1],x,z,j0_,tj_,at,bil,node,100*rel,
                Math.abs(at-bil)/Math.max(1e-300,Math.abs(A_)+Math.abs(B_))<1e-7 ? "  <= divAt==双线性" : "  <<< 双线性不符"));
            say(String.format(LF,"        (列方向: fx=%.3f tx=%.3f 节点 i=%d/%d  结点间距=%.0f)", fx, tx, i0n, i1, lx/nx));
        }
        say("");

        say("  [B] 200 个随机整点：divAt == 上述双线性的比例（相对误差<1e-7）");
        Random rnd = new Random(7L); int ok=0, N=200; double wrel=0;
        double[] txErr = new double[N]; int k=0;
        for (int s=0;s<N;s++){
            int x = rnd.nextInt((int)lx);
            int z = rnd.nextInt((int)WorldContract.Z_CYCLE);
            double at = StationaryWave.divAt(x, z);
            boolean good = bracket(x, z);
            if (good) ok++;
            double fx = frac((double)x*nx/lx)*nx; int i0=(int)Math.floor(fx); double tx=fx-i0;
            double bil = (1-tx)*A_ + tx*B_;
            double r = Math.abs(at-bil)/Math.max(1e-300, Math.abs(A_)+Math.abs(B_));
            if (r>wrel) wrel=r;
            txErr[k++] = 0;
        }
        say(String.format(LF,"      divAt 与【自然列映射双线性】一致的点：%d / %d   最坏相对差=%.3e", ok, N, wrel));
        say("");

        say("  [C] 判读");
        say("      (1) 行几何与列映射都是【可公开复算】的；若 [A][B] 全部吻合，");
        say("          则 StationaryWave.divAt 就是结点网格上的双线性插值，没有额外畸变。");
        say("      (2) 审计 P585 [D] 段读的是 round() 后的【最近结点值】，");
        say("          round 与双线性在 tx,tj 明显非零时必然不同，差值上界 = |v_i - v_{i+1}| 量级。");
        say("      (3) P715 已证：结点(x,z)上 divAt 与网格值逐位一致到 1.5e-5（纯取整噪声）。");
        say("DONE");
        System.out.println("JAVA_EXIT=0");
    }
}
