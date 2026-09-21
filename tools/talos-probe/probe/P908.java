package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P908 -- §578 P692 八个站点逐站诊断：kappa / SST / theta_e,conv / theta_e(BL) / P。
//   口径：本半球夏季（与 P692 §488 一致）。
public class P908 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s){ rep.println("[P908] "+s); System.out.println("[P908] "+s); rep.flush(); }
    static final int SEED=1022228679, GRAD=500_000;
    static final double CIRC=40_000_000.0;
    static int xOfLon(double lon){ return (int)Math.round(lon/360.0*CIRC); }
    static int zOfLat(double lat){ return WorldContract.zOfLat(lat); }
    static final Object[][] ST={
        {24.0,-14.0,"西撒哈拉 (加那利)",0},{-23.0,15.0,"纳米布 (本格拉)",0},
        {-23.0,-70.0,"阿塔卡马 (秘鲁)",0},{28.0,-114.0,"下加利福尼亚 (加州)",0},
        {32.0,-83.0,"美国东南 (湾流)",1},{-28.0,-49.0,"巴西南部",1},
        {-28.0,152.0,"东澳",1},{34.0,140.0,"日本",1}};

    public static void main(String[] a) throws Exception {
        rep=new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p908_report.txt"),"UTF-8");
        say("P908: §578 P692 八站逐站诊断（本半球夏季口径）");
        EarthRef.install(); OceanField.install(SEED); SimClimate.clearCache();
        long sd=SimTerrain.seedOf(SEED); int cell=PlateField.PLATE_CELL;
        double thN=Atmosphere.theta(0.0), thS=Atmosphere.theta(WorldContract.DAYS_PER_YEAR*0.5);
        say("  观测锚：沙漠组应远低于暖流组；四寒流海岸的 EIS 结构应为 6.9/6.6/6.9/2.5");
        say("");
        say("  站点                    | 组 | 陆/海 kappa | 本半球夏季 |  SST C | th_e,conv | th_e(BL) | 余量 K |  P(mm/d)");
        double sD=0,sW=0; int nD=0,nW=0, landN=0;
        for(int i=0;i<ST.length;i++){
            double latd=(Double)ST[i][0], lon=(Double)ST[i][1];
            int grp=(Integer)ST[i][3];
            int x=xOfLon(lon), z=zOfLat(latd);
            double th=(latd>=0)?thN:thS;
            double k=Atmosphere.kappaMemo(x,z,sd,cell);
            boolean land=k>0.5; if(land) landN++;
            double sstK=Atmosphere.seaSurfaceTemp(WorldContract.latOf(z), th)+Atmosphere.sstAnom(x,z,th);
            double sstC=sstK-273.15;
            double teConv=PrecipField.thetaEConv(sstC);
            double p=PrecipField.mmPerDay(x,z,sd,cell,th,GRAD);
            double margin=PrecipField.blqLastDh;
            double teBl=teConv+margin;
            say(String.format(LF,"  %-22s | %s | %-4s %5.3f | %s | %6.2f | %9.2f | %8.2f | %+7.2f | %7.3f",
                ST[i][2], (grp==0?"沙漠":"暖流"), (land?"陆":"海"), k,
                (latd>=0?"JJA":"DJF"), sstC, teConv, teBl, margin, p));
            if(grp==0){ sD+=p; nD++; } else { sW+=p; nW++; }
        }
        say("");
        say(String.format(LF,"  沙漠组均值 = %.4f (n=%d)   暖流组均值 = %.4f (n=%d)   比值 = %.4f   （判据 < 1）",
            sD/nD, nD, sW/nW, nW, (sD/nD)/(sW/nW)));
        say(String.format(LF,"  八站里 kappa>=0.5 的 = %d / 8", landN));
        say("");
        say("  判读要点：");
        say("   ① 暖流组若是【陆】站却 P=0，看它的【余量 K】—— 负得越多说明 theta_e(BL) 越低于阈值。");
        say("   ② 暖流海岸本该湿。若它 SST 很低（冷异常）⇒ theta_e,conv 低 ⇒ 门槛低 ⇒ 反而该容易触发。");
        say("   ③ 若暖流组陆站的 theta_e(BL) 也极低，说明【海温异常 sstAnom 在陆地上被误用】。");
        say("DONE");
        System.out.println("JAVA_EXIT=0");
    }
}
