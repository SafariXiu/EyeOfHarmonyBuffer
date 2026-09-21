package probe;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P914 -- §586 判决：新海陆生成器的陆地分布还【南北对称】吗？
//   ZonalTables 的 |lat| 对称设计自述理由是「本世界的陆地分布是南北对称的」。
//   海陆生成器已重写 ⇒ 必须重测这个前提。
public class P914 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s){ rep.println("[P914] "+s); System.out.println("[P914] "+s); rep.flush(); }
    static final int SEED=1022228679;
    static final double XSPAN=40_000_000.0, ZCYCLE=WorldContract.Z_CYCLE;
    static final int NX=120;

    public static void main(String[] a) throws Exception {
        rep=new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p914_report.txt"),"UTF-8");
        say("P914: §586 判决 —— 新世界陆地分布是否南北对称");
        long sd=SimTerrain.seedOf(SEED); int cell=PlateField.PLATE_CELL;
        say("  sd=" + sd + "  cell=" + cell + "  NX=" + NX);
        say("");
        say("  纬度    | 陆地占比 | 直接镜像差 | 说明");
        double sumAbs=0; int n=0;
        for(int band=0; band<9; band++){
            double lo=band*10.0, hi=lo+10.0;         // 0-10,10-20,...,80-90
            double frN=frac(lo,hi,sd,cell), frS=frac(-hi,-lo,sd,cell);
            double d=frN-frS;
            sumAbs+=Math.abs(d); n++;
            say(String.format(LF,"  %2.0f~%2.0f | %7.1f%% | %+8.1f pp | (对照南半球 %2.0f~%2.0f = %.1f%%)",
                lo,hi,100*frN,100*d,-hi,-lo,100*frS));
        }
        say("");
        say(String.format(LF,"  九对纬度带的平均镜像差 = %.1f 个百分点", 100*sumAbs/n));
        say("");
        say("  判读：若平均镜像差接近 0（几 pp 内）⇒ 南北方对称 ⇒ ZonalTables 的 |lat| 对称设计仍自洽。");
        say("        若差异显著（十几 pp 以上）⇒ 那条自述理由【已被海陆重写推翻】，表应改为不对称。");
        say("DONE");
        System.out.println("JAVA_EXIT=0");
    }
    /** 纬度带 [lo,hi]（可带符号）的面积加权陆地占比 */
    static double frac(double lo, double hi, long sd, int cell){
        long land=0, tot=0;
        int NLAT=(int)Math.max(2, Math.round((hi-lo)));
        for(int i=0;i<NLAT;i++){
            double lat=lo+(i+0.5)*(hi-lo)/NLAT;
            int z=WorldContract.zOfLat(lat);
            for(int j=0;j<NX;j++){
                int x=(int)Math.round((j+0.5)*XSPAN/NX);
                if(PlateField.isLandWithCell(x,z,sd,cell)) land++;
                tot++;
            }
        }
        return (double)land/tot;
    }
}
