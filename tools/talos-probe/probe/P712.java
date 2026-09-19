package probe;
import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;
// P712 -- §518 ★★ 常驻物理门：区域 MSE 平流收支的【符号】
//   文献判据(§516)：季风【输出】MSE => Qdiv>0 ；沙漠【输入】MSE => Qdiv<0
//   ★ 这是一条【符号】判据 —— 不依赖任何地球数值拟合，因此不受
//     「判据以物理正确为准还是地球正确为准」的困扰。
public class P712 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s){ rep.println("[P712] "+s); System.out.println("[P712] "+s); }
    static final int SEED=1022228679, GRAD=500_000;
    static final double CIRC=40_000_000.0;
    static int xOfLon(double lon){ return (int)Math.round(lon/360.0*CIRC); }
    static int zOfLat(double lat){ return WorldContract.zOfLat(lat); }
    static final double[][] BOX={{70,120,15,35},{0,30,20,35}};
    public static void main(String[] a) throws Exception {
        rep=new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p712_report.txt"),"UTF-8");
        say("P712: §518 GATE_QDIV_SIGN —— 区域 MSE 平流收支符号门（§516 文献判据）");
        EarthRef.install(); com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED); SimClimate.clearCache();
        long sd=SimTerrain.seedOf(SEED); int cell=PlateField.PLATE_CELL; double th=Atmosphere.theta(0.0);
        // ★ §535（P1-5）：§476 要求【任何基于盒子的判据都必须先声明该盒在本世界的海陆构成】。
        //   本探针此前完全未声明 ⇒ 读数可解释性被限定（不知道「亚洲盒」有多少是海）。
        Boxes.declare(rep, "P712 ASIA 70-120E/15-35N", sd, cell, 70,120,15,35, 10.0, 5.0);
        Boxes.declare(rep, "P712 SAHARA 0-30E/20-35N",  sd, cell,  0, 30,20,35, 10.0, 5.0);
        double[] sQ=new double[2], sP=new double[2]; int[] n=new int[2];
        for(int b=0;b<2;b++)
            for(double latd=BOX[b][2]+2.5; latd<=BOX[b][3]; latd+=5.0)
            for(double lon=BOX[b][0]+5.0; lon<=BOX[b][1]; lon+=10.0){
                int x=xOfLon(lon), z=zOfLat(latd);
                double pm=PrecipField.mmPerDay(x,z,sd,cell,th,GRAD);
                double[] h=new double[6];
                StationaryWave.DIAG_HEAT=new ThreadLocal<double[]>(){protected double[] initialValue(){return h;}};
                double qd=StationaryWave.netColumnHeating(x,z,sd,cell,th,GRAD,pm);
                StationaryWave.DIAG_HEAT=null;
                n[b]++; sQ[b]+=qd; sP[b]+=pm;
            }
        double QA=sQ[0]/n[0], QS=sQ[1]/n[1], PA=sP[0]/n[0], PS=sP[1]/n[1];
        say("");
        say(String.format(LF,"  ASIA   n=%d  <Qdiv> = %+9.3f W/m2   <P> = %7.3f mm/d",n[0],QA,PA));
        say(String.format(LF,"  SAHARA n=%d  <Qdiv> = %+9.3f W/m2   <P> = %7.3f mm/d",n[1],QS,PS));
        say("");
        boolean g1 = QA > 0.0;        // 季风应输出 MSE
        boolean g2 = QS < 0.0;        // 沙漠应输入 MSE
        boolean g3 = PS < PA;         // 沙漠降水应少于季风区
        say(String.format(LF,"  [1] 季风 Qdiv>0        : %s  (%+.3f)", g1?"PASS":"FAIL", QA));
        say(String.format(LF,"  [2] 沙漠 Qdiv<0        : %s  (%+.3f)", g2?"PASS":"FAIL", QS));
        say(String.format(LF,"  [3] 沙漠 P < 季风 P    : %s  (%.3f vs %.3f)", g3?"PASS":"FAIL", PS, PA));
        say("");
        say(String.format(LF,"  GATE_QDIV_SIGN = %s   (%d/3)", (g1&&g2&&g3)?"PASS":(g1?"PARTIAL":"FAIL"),
            (g1?1:0)+(g2?1:0)+(g3?1:0)));
        say("  基线：本次实测 ASIA Qdiv=+144.787 P=3.525 ; SAHARA Qdiv=+202.371 P=4.029");
        say("  ⇒ 现状 = 模型的撒哈拉在能量收支上是【季风区】，不是沙漠。");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
