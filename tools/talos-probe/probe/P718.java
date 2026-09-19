package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.*;

// P718 -- §548：不去猜 fxOf/fjOf，直接【暴力反演】divAt 用的结点索引与权重。
public class P718 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s){ rep.println("[P718] "+s); System.out.println("[P718] "+s); rep.flush(); }
    static double frac(double t){ return t - Math.floor(t); }

    static int rows, nx; static double[] dv; static double lx;

    static double V(int j, int i){ return dv[((j%rows)+rows)%rows*nx + ((i%nx)+nx)%nx]; }

    public static void main(String[] a) throws Exception {
        rep = new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p718_report.txt"),"UTF-8");
        say("P718: §548 暴力反演 divAt 的结点索引/权重");
        StationaryWave.ENABLED = true; StationaryWave.Q_SCALE = 1e-3;
        StationaryWave.invalidate();
        StationaryWave.ensureSolved(SimTerrain.seedOf(1022228679), PlateField.PLATE_CELL, Atmosphere.theta(0.0), 500_000);
        rows = StationaryWave.gridRows(); nx = StationaryWave.NX;
        dv = StationaryWave.divGridCopy(); lx = WorldContract.Z_CYCLE;
        say(String.format(LF,"  rows=%d NX=%d lx=%.0f", rows, nx, lx));

        // 对给定点，暴力搜索 (dj,di,tx,tj)：tx,tj 由 best (di,dj) 下的最优权重决定
        // 更直接：对每个 (dj,di) 组合，解 tx,tj 使双线性等于 divAt（超定，用最小二乘），再看残差
        int[][] pts = {{25,95},{25,15},{30,100},{20,80},{-36,67}};
        say("");
        say("  [A] 对每个测试点，搜索使双线性最接近 divAt 的 (dj,di)：");
        for (int[] p : pts){
            int z = WorldContract.zOfLat(p[0]);
            int x = (int) Math.round(p[1]/360.0*lx);
            double at = StationaryWave.divAt(x, z);
            double lat = WorldContract.latOf(z);
            double fj = (lat + Math.PI/2)/Math.PI*(rows-1);
            double fx = frac((double)x*nx/lx)*nx;
            int jb = (int)Math.floor(fj), ib = (int)Math.floor(fx);
            double tj0 = fj - jb, tx0 = fx - ib;
            String best=""; double bestr=Double.MAX_VALUE;
            for (int dj=-2; dj<=2; dj++) for (int di=-2; di<=2; di++){
                int j0=jb+dj, i0=ib+di;
                double v00=V(j0,i0), v01=V(j0,i0+1), v10=V(j0+1,i0), v11=V(j0+1,i0+1);
                // 用名义权重 (tj0,tx0)（tx 随 di 平移不变）
                double bil = (1-tj0)*((1-tx0)*v00+tx0*v01) + tj0*((1-tx0)*v10+tx0*v11);
                double r = Math.abs(bil-at)/Math.max(1e-300,Math.abs(at));
                if (r<bestr){ bestr=r; best=String.format(LF,"dj=%+d di=%+d", dj, di); }
            }
            say(String.format(LF,"    (%+4dN,%4dE) at=%+.6e  fj=%.3f(基%2d tj=%.3f)  fx=%.3f(基%2d tx=%.3f)  最佳 %s  残差=%.3f",
                p[0],p[1],at,fj,jb,tj0,fx,ib,tx0,best,bestr));
        }

        say("");
        say("  [B] 换一种假设：网格是【转置】存的 dv[i*rows+j]（行=j 纬向，列=i 经向）");
        for (int[] p : pts){
            int z = WorldContract.zOfLat(p[0]);
            int x = (int) Math.round(p[1]/360.0*lx);
            double at = StationaryWave.divAt(x, z);
            double lat = WorldContract.latOf(z);
            double fj = (lat + Math.PI/2)/Math.PI*(rows-1);
            double fx = frac((double)x*nx/lx)*nx;
            int jb=(int)Math.floor(fj), ib=(int)Math.floor(fx);
            double tj0=fj-jb, tx0=fx-ib;
            String best=""; double bestr=Double.MAX_VALUE;
            for (int dj=-2; dj<=2; dj++) for (int di=-2; di<=2; di++){
                int j0=jb+dj, i0=ib+di;
                double v00=dv[(((i0%nx)+nx)%nx)*rows + ((j0%rows)+rows)%rows];
                double v01=dv[(((i0+1)%nx)+nx)%nx*rows + ((j0%rows)+rows)%rows];
                double v10=dv[(((i0)%nx)+nx)%nx*rows + (((j0+1)%rows)+rows)%rows];
                double v11=dv[(((i0+1)%nx)+nx)%nx*rows + (((j0+1)%rows)+rows)%rows];
                double bil=(1-tj0)*((1-tx0)*v00+tx0*v01)+tj0*((1-tx0)*v10+tx0*v11);
                double r=Math.abs(bil-at)/Math.max(1e-300,Math.abs(at));
                if (r<bestr){ bestr=r; best=String.format(LF,"dj=%+d di=%+d",dj,di); }
            }
            say(String.format(LF,"    (%+4dN,%4dE) 最佳 %s  残差=%.3f", p[0],p[1],best,bestr));
        }

        say("");
        say("  [C] 假设三：divAt 直接就是【最近结点值】round(fj),round(fx)");
        double worst=0;
        Random rnd=new Random(11L);
        for (int s=0;s<200;s++){
            int x=rnd.nextInt((int)lx), z=rnd.nextInt((int)WorldContract.Z_CYCLE);
            double at=StationaryWave.divAt(x,z);
            double fj=(WorldContract.latOf(z)+Math.PI/2)/Math.PI*(rows-1);
            double fx=frac((double)x*nx/lx)*nx;
            int j=(int)Math.round(fj), i=((int)Math.round(fx)%nx+nx)%nx;
            if (j>rows-1) j=rows-1; if (j<0) j=0;
            double node=V(j,i);
            double r=Math.abs(at-node)/Math.max(1e-300,Math.abs(at));
            if (r>worst) worst=r;
        }
        say(String.format(LF,"    200 随机点：|divAt-最近结点|/|divAt| 最坏 = %.3f", worst));
        say("    （若只有几% ⇒ 说明 divAt 与最近结点几乎同值，列向插值权重极小 ⇒ 我的 fx 映射虽位置对但 tx 不对）");
        say("DONE");
        System.out.println("JAVA_EXIT=0");
    }
}
