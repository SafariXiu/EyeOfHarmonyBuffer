package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P634 -- 观测剖面的形状与哪个模型因子最相关？（决定 eddyMfc 该由什么驱动）
public class P634 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P634] " + s); System.out.println("[P634] " + s); }
    static final double R = WorldContract.R_EFF;
    static final double D5 = Math.toRadians(5.0);

    static double corr(double[] a, double[] b) {
        int n = a.length; double sa = 0, sb = 0, sab = 0, sa2 = 0, sb2 = 0;
        for (int i = 0; i < n; i++) { sa += a[i]; sb += b[i]; sab += a[i] * b[i]; sa2 += a[i] * a[i]; sb2 += b[i] * b[i]; }
        double ca = sab / n - (sa / n) * (sb / n);
        double va = sa2 / n - (sa / n) * (sa / n), vb = sb2 / n - (sb / n) * (sb / n);
        return (va > 0 && vb > 0) ? ca / Math.sqrt(va * vb) : 0;
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p634_report.txt"), "UTF-8");
        say("P634: 观测剖面 vs 各候选因子的形状相关（19 个 5 度节点，0~90N）");
        double thS = Atmosphere.theta(0.0);
        double thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        String[] NM = {"W", "W' (dW/dy)", "W'' (curv)", "sigma", "L_d", "L_d^2", "gate", "K=tau*sig^2*Ld^2", "sigma*Ld^2"};
        for (int pass = 0; pass < 2; pass++) {
            double th = (pass == 0) ? thS : thW;
            int N = 19;
            double[][] f = new double[NM.length][N];
            double[] obs = new double[N];
            for (int k = 0; k < N; k++) {
                double lat = Math.toRadians(k * 5.0);
                double w0 = PrecipField.columnWater(lat, th);
                double wp = PrecipField.columnWater(lat + D5, th);
                double wm = PrecipField.columnWater(lat - D5, th);
                double d1 = (wp - wm) / (2 * D5 * R);
                double d2 = (wp - 2 * w0 + wm) / (D5 * R * D5 * R);
                double sg = PrecipField.eadyGrowth(lat, th);
                double ld = PrecipField.deformRadius(lat, th);
                double g = PrecipField.stormGate(lat, th);
                f[0][k] = w0; f[1][k] = d1; f[2][k] = d2; f[3][k] = sg; f[4][k] = ld; f[5][k] = ld * ld;
                f[6][k] = g;
                f[7][k] = PrecipField.EDDY_PHYS_GAIN * PrecipField.EDDY_TAU * sg * sg * ld * ld;
                f[8][k] = sg * ld * ld;
                obs[k] = ZonalTables.eddyMfcObsMonth(lat, th);
            }
            say("");
            say(String.format(LF, "  [%s] 与观测剖面的相关：", (pass == 0) ? "JJA" : "DJF"));
            for (int q = 0; q < NM.length; q++)
                say(String.format(LF, "      %-18s corr %+.4f", NM[q], corr(f[q], obs)));
            say(String.format(LF, "      %-18s corr %+.4f", "观测自相关(对照)", corr(obs, obs)));
        }
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}