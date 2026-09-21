package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P904 -- §571 海岸沙漠站收支分解 + §476 海陆声明。
//   目的：检验「冷海 ⇒ 抑制对流」这条链在模型里是否存在。
//   口径照抄 P712/P903；补上 P902 漏掉的 §476 声明。
public class P904 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s){ rep.println("[P904] "+s); System.out.println("[P904] "+s); rep.flush(); }
    static final int SEED=1022228679, GRAD=500_000;
    static final double CIRC=40_000_000.0;
    static int xOfLon(double lon){ return (int)Math.round(lon/360.0*CIRC); }
    static int zOfLat(double lat){ return WorldContract.zOfLat(lat); }
    // 站名 / 纬度 / 经度 / 观测年降水(mm/yr) / 观测雾(mm/yr)
    static final Object[][] ST={
        {"Atacama   (Peru-Humboldt)", -23.5, -70.4,  3.1, 0.0},
        {"Namib     (Benguela)",      -23.56, 15.04, 30.2, 103.4},
        {"Baja      (California)",     28.0,-114.0,  97.2, 0.0},
        {"W.Sahara  (Canary)",         23.7, -15.9,  39.7, 0.0},
        {"Nouakchott(Canary S.)",      18.1, -15.9, 140.0, 0.0},
    };
    static final String[] SLOT={"qLat","qSens","qRad","CWV","ts","ts-ta"};

    public static void main(String[] a) throws Exception {
        rep=new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p904_report.txt"),"UTF-8");
        say("P904: §571 海岸沙漠站收支分解（检验「冷海⇒抑制对流」这条链）");
        EarthRef.install(); OceanField.install(SEED); SimClimate.clearCache();
        long sd=SimTerrain.seedOf(SEED); int cell=PlateField.PLATE_CELL;
        double thJJA=Atmosphere.theta(0.0), thDJF=Atmosphere.theta(WorldContract.DAYS_PER_YEAR*0.5);
        say(String.format(LF,"  sd=%d cell=%d GRAD=%d", sd, cell, GRAD));
        say("");
        say("  站点                        | 本世界 陆/海 kappa | P(mm/d) JJA/DJF |   qa(g/kg)  |   ts K  |  ts-ta | qRad   | meanDh(J/kg)");
        for(int i=0;i<ST.length;i++){
            double latd=(Double)ST[i][1], lon=(Double)ST[i][2];
            int x=xOfLon(lon), z=zOfLat(latd);
            double k=Atmosphere.kappaMemo(x,z,sd,cell);
            double pJ=PrecipField.mmPerDay(x,z,sd,cell,thJJA,GRAD);
            double pD=PrecipField.mmPerDay(x,z,sd,cell,thDJF,GRAD);
            double[] h=new double[6];
            StationaryWave.DIAG_HEAT=new ThreadLocal<double[]>(){protected double[] initialValue(){return h;}};
            StationaryWave.netColumnHeating(x,z,sd,cell,thJJA,GRAD,pJ);
            StationaryWave.DIAG_HEAT=null;
            double qa_gkg = h[3]/(Atmosphere.RHO_AIR*PrecipField.H_MOIST)*1000.0;
            say(String.format(LF,"  %-27s | %-6s %.3f | %6.3f %6.3f | %10.2f | %7.2f | %+6.2f | %+6.1f | %+9.1f",
                ST[i][0], (k>0.5?"陆":"海"), k, pJ, pD, qa_gkg, h[4], h[5], h[2], PrecipField.blqLastDh));
            say(String.format(LF,"  %-27s | 观测年降水 %.1f mm/yr  观测雾 %.1f mm/yr", "", ST[i][3], ST[i][4]));
        }
        say("");
        say("  === 检验点 ===");
        say("  ① 三个「寒流海岸沙漠」在本世界是不是【陆地】（kappa>0.5）？");
        say("  ② 它们的 qa 是多少？真实寒流海岸沙漠的边界层比湿约 2-6 g/kg。");
        say("  ③ BLQ 的 meanDh = h_bl - h*_th：正 ⇒ 门开（会下雨）；负 ⇒ 门关。");
        say("  ④ 若「冷海」那侧 kappa=0（海），说明采样点落在海里，需换点到岸上。");
        say("");
        say("DONE");
        System.out.println("JAVA_EXIT=0");
    }
}
