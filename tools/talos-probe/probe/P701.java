package probe;
import com.EyeOfHarmonyBuffer.sim.atmos.*;
import java.io.*; import java.util.Locale;
// P701 -- §500 完整垂直维度：先把【能量收支闭合】测掉（不守恒的 SCM 后面全是假的）
public class P701 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s){ rep.println("[P701] "+s); System.out.println("[P701] "+s); }
    public static void main(String[] a) throws Exception {
        rep = new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p701_report.txt"),"UTF-8");
        say("P701: §500 完整垂直维度 —— 第一步：能量收支闭合");
        say(String.format(LF,"N=%d  P_TOP=%.0f Pa  层质量 Σ=%.4f kg/m2 (应 = (P_SURF-P_TOP)/g = %.4f)",
            VerticalColumn.N, VerticalColumn.P_TOP,
            sum(VerticalColumn.MASS), (PrecipField.P_SURF-VerticalColumn.P_TOP)/PrecipField.G_ACC));
        say("");
        double[][] cs = {{300.0,0.020},{305.0,0.016},{310.0,0.005},{288.0,0.009}};
        String[] nm = {"热带海洋 300K/20g/kg","热带陆地 305K/16g/kg","副热带沙漠 310K/5g/kg","中纬海洋 288K/9g/kg"};
        double tTop = 200.0;
        say("=== A0 top-layer / bottom-layer step-by-step ===");
        {
            VerticalColumn c = new VerticalColumn();
            c.init(300.0, 0.020, 1.0, 6.0);
            say(String.format(LF,"  init t0=%.2f q0=%.3e t59=%.2f q59=%.5f MASS0=%.2f",
                c.t[0], c.q[0], c.t[VerticalColumn.N-1], c.q[VerticalColumn.N-1], VerticalColumn.MASS[0]));
            for (int n=1;n<=6;n++){
                double dh = c.step(120.0, 0.6, 200.0);
                say(String.format(LF,"  step%d t0=%10.4f t59=%10.4f olr=%10.4f ra=%11.4f sh=%9.4f lh=%9.4f dHdt=%11.4e res=%10.3e",
                    n, c.t[0], c.t[VerticalColumn.N-1], c.olr, c.ra, c.sh, c.lh, dh, c.budgetResidual()));
            }
        }
        say("");
        say("=== A 每一步的收支残差（这是命门）===");
        say(String.format(LF,"  %-22s | %10s | %10s | %10s | %10s | %12s | %s","情形","dH/dt","Ra","SH+LH","L*P","残差","收敛"));
        for(int i=0;i<cs.length;i++){
            VerticalColumn c = new VerticalColumn();
            c.init(cs[i][0], cs[i][1], 1.0, 6.0);
            double worst=0.0; int steps=0;
            double dt=120.0, mu=0.6;
            for(int n=1;n<=60000;n++){
                c.step(dt, mu, tTop);
                double r=Math.abs(c.budgetResidual());
                double scale=Math.abs(c.ra)+Math.abs(c.sh)+Math.abs(c.lh)+Radiation.LV*(c.precipConv+c.precipLs)/c.lastDt+1.0;   // §531: precipConv/precipLs 是【每步质量】，必须除 lastDt 才能与 W/m2 的 ra/sh/lh 同量纲
                if(r/scale>worst) worst=r/scale;
                steps=n;
                c.markBad(n);
                if(c.firstBadStep>0) break;
                if(n>30 && Math.abs(c.dHdtMeasured)<0.05) break;
            }
            double[] b=c.budgetTerms();
            say(String.format(LF,"  %-22s | %+10.4f | %+10.4f | %+10.4f | %+10.4f | %+12.3e | %d 步",
                nm[i], b[0], b[1], b[2], b[3], b[4], steps));
            say(String.format(LF,"      ↑ 全程最坏相对残差 = %.3e   (判据 <1e-6)   %s", worst, worst<1e-6?"*** 通过 ***":"!!! 不通过 !!!"));
            say(String.format(LF,"      首个非有限值: 步=%d 层=%d (层0=柱顶, 层%d=地面) ; 收敛判据 |dH/dt|<0.05", c.firstBadStep, c.firstBadLayer, VerticalColumn.N-1));
        }
        rep.flush(); System.out.println("JAVA_EXIT=0");
    }
    static double sum(double[] x){ double s=0; for(double v:x) s+=v; return s; }
}
