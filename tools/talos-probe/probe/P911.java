package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.*;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P911 -- §581 I-5 前提验证：BasinFinder.find 在 spanOf=NULL 的那些站上给不给得出有效域。
public class P911 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s){ rep.println("[P911] "+s); System.out.println("[P911] "+s); rep.flush(); }
    static final int SEED=1022228679;
    static final double CIRC=40_000_000.0;
    static int xOfLon(double lon){ return (int)Math.round(lon/360.0*CIRC); }
    static int zOfLat(double lat){ return WorldContract.zOfLat(lat); }
    static final Object[][] PT={
        {"GulfStream 32N/83W",32.0,-83.0},{"Brazil 28S/49W",-28.0,-49.0},
        {"E.Australia 28S/152E",-28.0,152.0},{"Kuroshio 34N/140E",34.0,140.0},
        {"California 28N/114W",28.0,-114.0},{"Canary 24N/14W",24.0,-14.0},
        {"Peru 23S/70W",-23.0,-70.0},{"Benguela 23S/15E",-23.0,15.0}};

    public static void main(String[] a) throws Exception {
        rep=new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p911_report.txt"),"UTF-8");
        say("P911: §581 I-5 前提验证 —— BasinFinder.find vs OceanField.spanOf");
        EarthRef.install(); OceanField.install(SEED); SimClimate.clearCache();
        long sd=SimTerrain.seedOf(SEED); int cell=PlateField.PLATE_CELL;
        say("  cell=" + cell + "（板块格）");
        say("");
        say("  站点                    | spanOf | BasinFinder.valid | 域 km x km | westRun km | 海格 | trunc | 距平 K");
        int bfValid=0, spNull=0;
        long t0=System.currentTimeMillis();
        for(int i=0;i<PT.length;i++){
            double latd=(Double)PT[i][1], lon=(Double)PT[i][2];
            int x=xOfLon(lon), z=zOfLat(latd);
            int[] sp=null; try { sp=OceanField.spanOf(x,z,SEED); } catch(Throwable t){}
            if(sp==null) spNull++;
            BasinFinder.Basin b=null; String bs;
            try { b=BasinFinder.find(x,z,sd,cell); bs = (b!=null && b.valid) ? "YES" : "no"; if(b!=null && b.valid) bfValid++; }
            catch(Throwable t){ bs="EXC:"+t.getClass().getSimpleName(); }
            String dims = (b!=null && b.valid) ? String.format(LF,"%.0f x %.0f", b.widthKm(), b.heightKm()) : "-";
            String wr = (b!=null && b.valid) ? String.format(LF,"%.0f", b.westRunKm()) : "-";
            String sc = (b!=null && b.valid) ? (""+b.seaCells) : "-";
            String tr = (b!=null) ? (""+b.truncated) : "-";
            say(String.format(LF,"  %-22s | %-6s | %-17s | %-10s | %-10s | %-4s | %-5s | %+6.3f",
                PT[i][0], (sp==null?"NULL":"ok"), bs, dims, wr, sc, tr, OceanField.anomalyAt(x,z,SEED)));
        }
        long dt=System.currentTimeMillis()-t0;
        say("");
        say("  spanOf NULL = " + spNull + "/8     BasinFinder valid = " + bfValid + "/8");
        say(String.format(LF,"  八次 find() 总耗时 = %d ms（均 %.1f ms/次）", dt, dt/8.0));
        say("  earlyOutMismatch = " + BasinFinder.earlyOutMismatch);
        say("");
        say("  判读：若 BasinFinder 在有 spanOf=NULL 的站上给出 valid=true 且域尺寸合理 ⇒ I-5 前提成立。");
        say("        若耗时量级是几十 ms/次 ⇒ 证实审计前置 2「绝不能放进查询路径」。");
        say("DONE");
        System.out.println("JAVA_EXIT=0");
    }
}
