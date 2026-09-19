package probe;
import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;
// P708 -- §512 ★★★ 决定性问题：用【已验证的完整气柱】去量目标量 —— 亚洲 vs 撒哈拉的 M / zLNB / P
public class P708 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s){ rep.println("[P708] "+s); System.out.println("[P708] "+s); rep.flush(); }
    static final int SEED=1022228679, GRAD=500_000;
    static final double CIRC=40_000_000.0;
    static int xOfLon(double lon){ return (int)Math.round(lon/360.0*CIRC); }
    static int zOfLat(double lat){ return WorldContract.zOfLat(lat); }
    static final double[][] BOX={{70,120,15,35},{0,30,20,35}};
    static final String[] BN={"ASIA","SAHARA"};
    static final double TTOP=200.0, DT=120.0, MU=372.0/1361.0;
    public static void main(String[] a) throws Exception {
        rep=new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p708_report.txt"),"UTF-8");
        say("P708: §512 用已验证气柱量目标量（FIXED_TS=true，地面状态取自模型自己）");
        VerticalColumn.FIXED_TS=true;
        EarthRef.install();
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        SimClimate.clearCache();
        long sd=SimTerrain.seedOf(SEED); int cell=PlateField.PLATE_CELL;
        // ★ §535（P1-5）：§476 要求【任何基于盒子的判据都必须先声明该盒在本世界的海陆构成】。
        //   ⚠ 本探针自身的循环起点仍是旧式 lo+half（见下方 for 行）—— 属 P1-6 待统一项；
        //     此处声明的陆占比用 Boxes 的【格心口径】，步长与本探针一致（dLon=10 dLat=5）。
        Boxes.declare(rep, "P708 ASIA   70-120E/15-35N", sd, cell, 70,120,15,35, 10, 5);
        Boxes.declare(rep, "P708 SAHARA 0-30E /20-35N",  sd, cell,  0, 30,20,35, 10, 5);
        double th=Atmosphere.theta(0.0);
        say("");
        say("  box    | lon  lat  |  tS(K)  |   q      | zLNB(m) |   M(J/kg) |  P(mm/d) | CWV");
        double[] sm=new double[2], sz=new double[2], sp=new double[2], st=new double[2], sq=new double[2];
        int[] cnt=new int[2]; int[] posM=new int[2];
        for(int b=0;b<2;b++){
            for(double latd=BOX[b][2]+2.5; latd<=BOX[b][3]; latd+=5.0)
            for(double lon=BOX[b][0]+5.0; lon<=BOX[b][1]; lon+=10.0){
                int x=xOfLon(lon), z=zOfLat(latd);
                PrecipField.mmPerDay(x,z,sd,cell,th,GRAD);
                double[] d=PrecipField.DIAG.get();
                double tS=Atmosphere.surfaceTemp(x,z,sd,cell,th);
                double qq=d[5], beta=d[13];
                ParcelLift.Result pr=ParcelLift.lift(tS,qq);
                VerticalColumn c=new VerticalColumn(); c.init(tS,qq,beta,6.0);
                for(int n=1;n<=16000;n++){ c.step(DT,MU,TTOP); if(n>200&&Math.abs(c.dHdtMeasured)<3.0) break; }
                // ★★★★★★★ §514 修正：h 是【湿静力能】，必须含位势项 g*z。
                //   原式漏了 g*z ⇒ M ≈ cp*ΔT ≈ 1.0e5，量的其实是「柱子多深」，不是对流不稳定度。
                //   正确形式下 cp*ΔT 与 -g*z 近乎相消（§498 的 P700 得 +4141/+2030，§470 得 ±2万）。
                double hBl=Radiation.CP*c.t[VerticalColumn.N-1]+PrecipField.G_ACC*c.z[VerticalColumn.N-1]
                          +Radiation.LV*c.q[VerticalColumn.N-1];
                double M=Double.NaN, zl=Double.NaN, pp=(c.precipConv+c.precipLs)/c.lastDt*86400.0*1000.0;
                if(!Double.isNaN(pr.pLnb)){
                    int k=0; double bd=1e18;
                    for(int j=0;j<VerticalColumn.N;j++){ double dd=Math.abs(VerticalColumn.PC[j]-pr.pLnb); if(dd<bd){bd=dd;k=j;} }
                    M=hBl-(Radiation.CP*c.t[k]+PrecipField.G_ACC*c.z[k]+Radiation.LV*c.q[k]); zl=c.z[k];
                }
                say(String.format(LF,"  %-6s | %4.0f %4.0f | %7.2f | %7.5f | %7.0f | %+9.1f | %8.3f | %6.2f",
                    BN[b],lon,latd,tS,qq,zl,M,pp,c.cwv()));
                cnt[b]++; st[b]+=tS; sq[b]+=qq;
                if(!Double.isNaN(M)){ sm[b]+=M; sz[b]+=zl; posM[b]++; }
                sp[b]+=pp;
            }
        }
        say("");
        say(String.format(LF,"  %-8s | %8s | %10s | %8s | %10s | %9s | %s","box","n","<tS>(K)","<q>","<M>(J/kg)","<P>(mm/d)","M>0 比例"));
        for(int b=0;b<2;b++)
            say(String.format(LF,"  %-8s | %8d | %10.2f | %8.5f | %10.1f | %9.3f | %d/%d",
                BN[b],cnt[b],st[b]/cnt[b],sq[b]/cnt[b],sm[b]/Math.max(1,posM[b]),sp[b]/cnt[b],posM[b],cnt[b]));
        double cM=(sm[0]/posM[0]-sm[1]/posM[1])/(Math.abs(sm[0]/posM[0])+Math.abs(sm[1]/posM[1]));
        double cP=(sp[0]/cnt[0]-sp[1]/cnt[1])/(Math.abs(sp[0]/cnt[0])+Math.abs(sp[1]/cnt[1]));
        double cT=(st[0]/cnt[0]-st[1]/cnt[1])/(Math.abs(st[0]/cnt[0])+Math.abs(st[1]/cnt[1]));
        say("");
        say(String.format(LF,"  contrast M = %+.4f   (方向%s)",cM,cM>0?"对（亚洲更湿不稳）":"【反】"));
        say(String.format(LF,"  contrast P = %+.4f   (方向%s)",cP,cP>0?"对":"【反】"));
        say(String.format(LF,"  contrast tS= %+.4f",cT));
        say("  §498 参照：简化版 M（ParcelLift + 线性环境）contrast = +0.2440；旧硬编码层 = -0.0702");
        say("DONE");
        System.out.println("JAVA_EXIT=0");
    }
}
