package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P903 -- §570 柱加热收支分解：把 DIAG_HEAT 六个槽逐项打印，与 Cherchi et al. (2014) 的
//   文献值对照（东地中海 JJA: w.dm/dp -174.5 / 干焓平流 -224.0 / F_rad **-55.8** / 水汽平流 +34.3）。
//   目的：核实我方 qRad 的【符号与量级】。
public class P903 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s){ rep.println("[P903] "+s); System.out.println("[P903] "+s); rep.flush(); }
    static final int SEED=1022228679, GRAD=500_000;
    static final double CIRC=40_000_000.0;
    static int xOfLon(double lon){ return (int)Math.round(lon/360.0*CIRC); }
    static int zOfLat(double lat){ return WorldContract.zOfLat(lat); }
    // 0=ASIA 1=SAHARA 2=E.MED(28-42N,15-35E, 与 Cherchi 文献同域) 3=ATACAMA 4=NAMIB
    static final String[] NAME={"ASIA 70-120E/15-35N","SAHARA 0-30E/20-35N","E.MED 15-35E/28-42N"};
    static final double[][] BOX={{70,120,15,35},{0,30,20,35},{15,35,28,42}};
    static final String[] SLOT={"qLat","qSens","qRad","CWV","ts","ts-ta"};

    public static void main(String[] a) throws Exception {
        rep=new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p903_report.txt"),"UTF-8");
        say("P903: §570 柱加热收支分解（对照 Cherchi et al. 2014 Table 2）");
        EarthRef.install(); OceanField.install(SEED); SimClimate.clearCache();
        long sd=SimTerrain.seedOf(SEED); int cell=PlateField.PLATE_CELL; double th=Atmosphere.theta(0.0);
        say(String.format(LF,"  sd=%d cell=%d GRAD=%d  theta=JJA", sd, cell, GRAD));
        say("");
        say("  盒子                     n | " + String.format(LF,"%-9s %-9s %-9s %-9s %-8s %-8s","qLat","qSens","qRad","CWV","ts","ts-ta"));
        double[][] acc=new double[BOX.length][6]; int[] n=new int[BOX.length];
        for(int b=0;b<BOX.length;b++)
            for(double latd=BOX[b][2]+2.5; latd<=BOX[b][3]; latd+=5.0)
            for(double lon=BOX[b][0]+5.0; lon<=BOX[b][1]; lon+=10.0){
                int x=xOfLon(lon), z=zOfLat(latd);
                double pm=PrecipField.mmPerDay(x,z,sd,cell,th,GRAD);
                double[] h=new double[6];
                StationaryWave.DIAG_HEAT=new ThreadLocal<double[]>(){protected double[] initialValue(){return h;}};
                StationaryWave.netColumnHeating(x,z,sd,cell,th,GRAD,pm);
                StationaryWave.DIAG_HEAT=null;
                n[b]++; for(int s=0;s<6;s++) acc[b][s]+=h[s];
            }
        for(int b=0;b<BOX.length;b++){
            say(String.format(LF,"  %-24s %2d | %+9.3f %+9.3f %+9.3f %9.3f %8.2f %8.2f",
                NAME[b], n[b], acc[b][0]/n[b], acc[b][1]/n[b], acc[b][2]/n[b], acc[b][3]/n[b], acc[b][4]/n[b], acc[b][5]/n[b]));
        }
        say("");
        say("  === 与 Cherchi et al. (2014) Table 2 对照（东地中海 JJA, ERA-40, W/m2）===");
        say("    文献 F_rad = -55.8（冷却）   |   我方 qRad 见上表第 3 列");
        say("    文献 干焓平流 -224.0 / 水汽平流 +34.3 / LHF +57.6 / SHF +36.1");
        say("    ⇒ 若我方 qRad 为正，符号与文献相反 ⇒ 记为待查；若为负，看量级是否同阶。");
        say("");
        say("DONE");
        System.out.println("JAVA_EXIT=0");
    }
}
