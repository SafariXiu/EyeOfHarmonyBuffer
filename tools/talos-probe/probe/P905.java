package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P905 -- §572 土壤桶自旋诊断：开 SoilMoisture 后逐点量 beta、自旋年数、残差。
//   检验假说：P902 的「全球干 50-90%」是不是因为桶【没收敛】。
public class P905 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s){ rep.println("[P905] "+s); System.out.println("[P905] "+s); rep.flush(); }
    static final int SEED=1022228679, GRAD=500_000;
    static final double CIRC=40_000_000.0;
    static int xOfLon(double lon){ return (int)Math.round(lon/360.0*CIRC); }
    static int zOfLat(double lat){ return WorldContract.zOfLat(lat); }
    static final String[] NAME={"ASIA 70-120E/15-35N","SAHARA 0-30E/20-35N","Namib(陆)","Nouakchott(陆)"};
    static final double[][] PT={{95,25},{15,27.5},{15.04,-23.56},{-15.9,18.1}};

    public static void main(String[] a) throws Exception {
        rep=new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p905_report.txt"),"UTF-8");
        say("P905: §572 土壤桶自旋诊断（MAX_YEARS=" + SoilMoisture.MAX_YEARS + "  SPIN_TOL=" + SoilMoisture.SPIN_TOL + "）");
        EarthRef.install(); OceanField.install(SEED); SimClimate.clearCache();
        long sd=SimTerrain.seedOf(SEED); int cell=PlateField.PLATE_CELL; double th=Atmosphere.theta(0.0);
        SoilMoisture.ENABLED = true; SimClimate.clearCache();
        say("");
        say("  测点                      | kappa |  beta  | spinYears | spinResid | 收敛? | P(mm/d)");
        for(int i=0;i<PT.length;i++){
            int x=xOfLon(PT[i][0]), z=zOfLat(PT[i][1]);
            double k=Atmosphere.kappaMemo(x,z,sd,cell);
            SoilMoisture.lastSpinYears=-1; SoilMoisture.lastSpinResid=-1;
            double beta=SoilMoisture.betaAt(x,z,sd,cell,th,GRAD);
            double p=PrecipField.mmPerDay(x,z,sd,cell,th,GRAD);
            boolean conv = (SoilMoisture.lastSpinResid>=0 && SoilMoisture.lastSpinResid < SoilMoisture.SPIN_TOL);
            say(String.format(LF,"  %-25s | %5.3f | %6.4f | %9.1f | %9.2e | %-5s | %7.3f",
                NAME[i], k, beta, SoilMoisture.lastSpinYears, SoilMoisture.lastSpinResid,
                conv?"是":"**否**", p));
        }
        say("");
        say("  === 对照：beta=1（生产态回落）的同一批点 ===");
        SoilMoisture.ENABLED = false; SimClimate.clearCache();
        for(int i=0;i<PT.length;i++){
            int x=xOfLon(PT[i][0]), z=zOfLat(PT[i][1]);
            double p=PrecipField.mmPerDay(x,z,sd,cell,th,GRAD);
            say(String.format(LF,"  %-25s | beta=1 | P = %7.3f mm/d", NAME[i], p));
        }
        SoilMoisture.ENABLED = false; SimClimate.clearCache();
        say("");
        say("  判读：若 spinYears 撞到 " + SoilMoisture.MAX_YEARS + " 且 resid > SPIN_TOL ⇒ 桶【没收敛】⇒ P902 的全球变干不可信。");
        say("DONE");
        System.out.println("JAVA_EXIT=0");
    }
}
