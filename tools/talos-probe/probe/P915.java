package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanWiring;

import java.io.File; import java.io.PrintStream; import java.util.Locale;

// P915 -- §586 追因：中纬雨带季节迁移被【哪一项分量】吃掉了？
//   已证：模型 NH 46.8(JJA) -> 39.6(DJF) = 7.2 度；GPCP 同带观测 12.5 度。SH 完全镜像。
//   EDDY_MFC_OBS_MONTH 自述「只取北半球（真实观测）」⇒ NH 的 7.2 度与镜像无关，
//   衰减必在【表 -> 消费者】这一段。本探针把每个分量单独拎出来各自求峰。
public class P915 {

    static final int SEED = 1022228679;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    static void say(String s){ rep.println("[P915] "+s); System.out.println("[P915] "+s); rep.flush(); }

    public static void main(String[] args) throws Exception {
        OceanWiring.onWorld(SEED);
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p915_report.txt"), "UTF-8");
        long sd = SimTerrain.seedOf(SEED);
        double thS = Atmosphere.theta(0.0);                                  // NH 夏至 (JJA)
        double thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);    // NH 冬至 (DJF)
        say("P915: 中纬雨带季节迁移的分量分解");
        say("  WORLD_IS_TALOS=" + PlateField.WORLD_IS_TALOS + "  sd=" + sd);
        say("  JJA theta=" + String.format(LF,"%.6f",thS) + "   DJF theta=" + String.format(LF,"%.6f",thW));
        say("");

        double[] pS = Zonal.profile(sd, thS), pW = Zonal.profile(sd, thW);

        say("=== A. 各分量在【北半球 35~70】带内的峰值纬度 ===");
        say(String.format(LF, "  %-26s %10s %10s %9s", "分量", "JJA峰@lat", "DJF峰@lat", "迁移(度)"));
        row("总降水 Zonal.profile",  pk(arr(pS,Zonal.NZ), Zonal.NZ, 35,70), pk(arr(pW,Zonal.NZ), Zonal.NZ, 35,70));
        row("EDDY表 eddyMfcObsMonth", pkf(true , 35,70,thS), pkf(true , 35,70,thW));
        row("模型 eddyMfc (算出来的)", pkf(false, 35,70,thS), pkf(false, 35,70,thW));
        row("W_ZM_MONTH 观测omega500", pkw(35,70,thS), pkw(35,70,thW));
        row("SST seaSurfaceTemp",      pks(35,70,thS), pks(35,70,thW));
        say("");
        say("=== B. 同带，南半球（-70~-35）===");
        say(String.format(LF, "  %-26s %10s %10s %9s", "分量", "DJF峰@lat", "JJA峰@lat", "迁移(度)"));
        row("总降水 Zonal.profile",  pk(arr(pW,Zonal.NZ), Zonal.NZ, -70,-35), pk(arr(pS,Zonal.NZ), Zonal.NZ, -70,-35));
        row("EDDY表 eddyMfcObsMonth", pkf(true , -70,-35,thW), pkf(true , -70,-35,thS));
        row("模型 eddyMfc (算出来的)", pkf(false, -70,-35,thW), pkf(false, -70,-35,thS));
        row("W_ZM_MONTH 观测omega500", pkw(-70,-35,thW), pkw(-70,-35,thS));
        row("SST seaSurfaceTemp",      pks(-70,-35,thW), pks(-70,-35,thS));
        say("");
        say("=== C. 逐度剖面（NH 30~72），JJA / DJF ===");
        say(String.format(LF, "  %-6s %10s %10s %11s %11s %9s %9s", "lat", "总P夏", "总P冬", "EDDY表夏", "EDDY表冬", "wZm夏", "wZm冬"));
        for (int d = 30; d <= 72; d += 2) {
            double lr = Math.toRadians(d);
            int r = rowOf(d, Zonal.NZ);
            say(String.format(LF, "  %-6d %10.3f %10.3f %11.4f %11.4f %9.3f %9.3f",
                d, val(pS,r), val(pW,r), tabT(lr,thS), tabT(lr,thW), wZm(d,thS)*1e3, wZm(d,thW)*1e3));
        }
        say("");
        say("  判读：若「EDDY表」自身的迁移就已远小于 12.5 度 ⇒ 表/月份相位口径错；");
        say("        若表迁移够而「模型 eddyMfc」或「总降水」变小 ⇒ 消费者（纬度/相位/混合）在稀释。");
        say("DONE");
        System.out.println("JAVA_EXIT=0");
    }

    static void row(String name, double[] a, double[] b){
        double sh = Math.abs(a[1]-b[1]);
        say(String.format(LF, "  %-26s %10.1f %10.1f %9.1f", name, a[1], b[1], sh));
    }
    static double[] arr(double[] p, int n){ return p; }
    static double val(double[] p, int r){ return (r<0||r>=p.length)?0:p[r]; }
    static int rowOf(int latDeg, int NZ){
        int z = WorldContract.zOfLat(latDeg);
        for (int r=0;r<NZ;r++){ int zz=(int)((r+0.5)/NZ*WorldContract.Z_CYCLE);
            if (zz>=z) return r; }
        return NZ-1;
    }
    // 表值：eddyMfcObsMonth
    static double tabT(double latRad, double th){
        try { return ZonalTables.eddyMfcObsMonth(latRad, th); } catch(Throwable t){ return 0; }
    }
    static double wZm(double latDeg, double th){
        try { return ZonalTables.wZmMonth(latDeg, th); } catch(Throwable t){ return 0; }
    }
    static double sst(double latRad, double th){
        try { return Atmosphere.seaSurfaceTemp(latRad, th); } catch(Throwable t){ return 0; }
    }
    static double modM(double latRad, double th){
        try { return PrecipField.eddyMfc(latRad, th); } catch(Throwable t){ return 0; }
    }
    static double[] pk(double[] v, int NZ, double lo, double hi){
        double best=-Double.MAX_VALUE, blat=0;
        for (int r=0;r<NZ;r++){
            int z=(int)((r+0.5)/NZ*WorldContract.Z_CYCLE);
            double lat=Math.toDegrees(WorldContract.latOf(z, WorldContract.Z_CYCLE));
            if (lat<lo||lat>=hi) continue;
            if (v[r]>best){ best=v[r]; blat=lat; }
        }
        return new double[]{best,blat};
    }
    static double[] pkf(boolean table, double lo, double hi, double th){
        double best=-Double.MAX_VALUE, blat=0;
        for (double d=lo; d<hi; d+=0.25){
            double lr=Math.toRadians(d);
            double x = table ? tabT(lr,th) : modM(lr,th);
            if (x>best){ best=x; blat=d; }
        }
        return new double[]{best,blat};
    }
    static double[] pkw(double lo, double hi, double th){
        double best=-Double.MAX_VALUE, blat=0;
        for (double d=lo; d<hi; d+=0.25){
            double x=wZm(d,th);
            if (x>best){ best=x; blat=d; }
        }
        return new double[]{best,blat};
    }
    static double[] pks(double lo, double hi, double th){
        double best=-Double.MAX_VALUE, blat=0;
        for (double d=lo; d<hi; d+=0.25){
            double x=sst(Math.toRadians(d),th);
            if (x>best){ best=x; blat=d; }
        }
        return new double[]{best,blat};
    }
}
