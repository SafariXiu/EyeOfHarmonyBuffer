package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.*;

// P912 -- §582 ★ P692 判据重做：站点由【本世界自己的地理】选出，不用地球地名。
//   步骤：① 快速扫 kappa + sstAnom（都不含桶自旋）找出「冷海邻接陆点」/「暖海邻接陆点」
//         ② 只对选中的点调 mmPerDay（桶自旋慢，必须限量）
//   判据不变（文献）：冷海邻接组的 P 应 < 暖海邻接组。
public class P912 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s){ rep.println("[P912] "+s); System.out.println("[P912] "+s); rep.flush(); }
    static final int SEED=1022228679, GRAD=500_000;
    static final double CIRC=40_000_000.0;
    static int xOfLon(double lon){ return (int)Math.round(((lon%360.0)+360.0)%360.0/360.0*CIRC); }
    static int zOfLat(double lat){ return WorldContract.zOfLat(lat); }

    public static void main(String[] a) throws Exception {
        rep=new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p912_report.txt"),"UTF-8");
        say("P912: §582 P692 判据重做 —— 站点由本世界地理选出");
        EarthRef.install(); OceanField.install(SEED); SimClimate.clearCache();
        long sd=SimTerrain.seedOf(SEED); int cell=PlateField.PLATE_CELL;
        // ---- ① 快扫：只做 kappa + sstAnom（无桶自旋） ----
        int NLAT=25, NLON=36;                    // 纬度 -60..60 步 5 ；经度 0..350 步 10
        double[][] kap=new double[NLAT][NLON], an=new double[NLAT][NLON];
        int nSea=0, nAnom=0; double maxA=0, minA=0;
        for(int i=0;i<NLAT;i++){
            double latd=-60.0+i*5.0; int z=zOfLat(latd);
            for(int j=0;j<NLON;j++){
                double lon=j*10.0; int x=xOfLon(lon);
                kap[i][j]=Atmosphere.kappaMemo(x,z,sd,cell);
                an[i][j]=Math.abs(kap[i][j]-0.5)<0.5 ? Atmosphere.sstAnom(x,z,Atmosphere.theta(0.0)) : 0.0;
                if(kap[i][j]<=0.5){ nSea++; if(Math.abs(an[i][j])>0.5) nAnom++; if(an[i][j]>maxA) maxA=an[i][j]; if(an[i][j]<minA) minA=an[i][j]; }
            }
        }
        say(String.format(LF,"  快扫 %dx%d=%d 点；海点 %d；其中 |距平|>0.5 的 %d；距平范围 [%.2f, %.2f] K",
            NLAT,NLON,NLAT*NLON,nSea,nAnom,minA,maxA));
        // ---- ② 给每个陆点算「邻接海温距平」= 周围 3x3 内海点的均值 ----
        List<double[]> cand=new ArrayList<>();   // {lat, lon, adjAnom, kappa}
        for(int i=1;i<NLAT-1;i++) for(int j=0;j<NLON;j++){
            if(kap[i][j]<=0.5) continue;         // 只要陆点
            double s=0; int n=0;
            for(int di=-1;di<=1;di++) for(int dj=-1;dj<=1;dj++){
                int ii=i+di, jj=((j+dj)%NLON+NLON)%NLON;
                if(kap[ii][jj]<=0.5){ s+=an[ii][jj]; n++; }
            }
            if(n>0) cand.add(new double[]{-60.0+i*5.0, j*10.0, s/n, kap[i][j]});
        }
        say("  候选陆点（有邻接海）=" + cand.size());
        cand.sort((p,q)->Double.compare(p[2],q[2]));
        // ---- ③ 取最冷邻接 6 个与最暖邻接 6 个 ----
        int K=Math.min(6,cand.size()/2);
        List<double[]> cold=cand.subList(0,K), warm=cand.subList(cand.size()-K,cand.size());
        Map<String,Double> grpN=new LinkedHashMap<>();
        say("");
        say("  【冷海邻接陆点】(本半球夏季)");
        double sC=0; int nC=0;
        for(double[] p:cold){
            int x=xOfLon(p[1]), z=zOfLat(p[0]);
            double th=(p[0]>=0)?Atmosphere.theta(0.0):Atmosphere.theta(WorldContract.DAYS_PER_YEAR*0.5);
            double pp=PrecipField.mmPerDay(x,z,sd,cell,th,GRAD);
            say(String.format(LF,"    lat=%+6.1f lon=%5.1f  邻接海距平 %+6.2f K  kappa %.3f  P=%.4f mm/d", p[0],p[1],p[2],p[3],pp));
            sC+=pp; nC++;
        }
        say("  【暖海邻接陆点】");
        double sW=0; int nW=0;
        for(double[] p:warm){
            int x=xOfLon(p[1]), z=zOfLat(p[0]);
            double th=(p[0]>=0)?Atmosphere.theta(0.0):Atmosphere.theta(WorldContract.DAYS_PER_YEAR*0.5);
            double pp=PrecipField.mmPerDay(x,z,sd,cell,th,GRAD);
            say(String.format(LF,"    lat=%+6.1f lon=%5.1f  邻接海距平 %+6.2f K  kappa %.3f  P=%.4f mm/d", p[0],p[1],p[2],p[3],pp));
            sW+=pp; nW++;
        }
        say("");
        say(String.format(LF,"  冷海邻接组均值 = %.4f (n=%d)   暖海邻接组均值 = %.4f (n=%d)", sC/nC, nC, sW/nW, nW));
        say(String.format(LF,"  比值(冷/暖) = %.4f     判据：应 < 1", (sC/nC)/(sW/nW)));
        say("DONE");
        System.out.println("JAVA_EXIT=0");
    }
}
