package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P699 -- §497: 统一诊断 —— 哪些状态量真有【O(1) 判别力】？（§496 的统一诊断要的读数）
//   度量：contrast = (A-B)/(|A|+|B|) ∈ [-1,1]；|contrast| 大 = 判别力强；接近 0 = 1% 量级（相消残差）。
public class P699 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P699] " + s); System.out.println("[P699] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }
    static final double[][] BOX = {{70,120,15,35},{0,30,20,35}};
    static final int NQ = 15;

    static double[] bm(long sd, int cell, double th, int b) {
        double[] a = new double[NQ]; long n = 0;
        for (double latd = BOX[b][2] + 2.5; latd <= BOX[b][3]; latd += 2.5)
            for (int c = 0; c < 72; c++) {
                double lon = (c + 0.5) * 5.0;
                if (lon < BOX[b][0] || lon > BOX[b][1]) continue;
                int x = xOfLon(lon), z = zOfLat(latd);
                double p = PrecipField.mmPerDay(x, z, sd, PlateField.PLATE_CELL, th, GRAD);
                double[] d = PrecipField.DIAG.get();
                double[] u = Atmosphere.windAt(x, z, sd, PlateField.PLATE_CELL, th, GRAD);
                double k = Atmosphere.kappaMemo(x, z, sd, cell);
                double tS = Atmosphere.surfaceTemp(x, z, sd, PlateField.PLATE_CELL, th);
                double tTh = tS - Atmosphere.GAMMA * Atmosphere.H_BL;
                double dh = (Radiation.CP * tS + Radiation.LV * d[5])
                          - (Radiation.CP * tTh + PrecipField.G_ACC * Atmosphere.H_BL + Radiation.LV * PrecipField.qSat(tTh));
                a[0] += k; a[1] += d[6]; a[2] += tS; a[3] += d[13]; a[4] += d[5];
                a[5] += d[11]; a[6] += d[0]; a[7] += d[3]; a[8] += d[4];
                a[9] += Atmosphere.sstAnom(x, z, th); a[10] += PlateField.elevationWithCell(x, z, sd, cell);
                a[11] += p; a[12] += dh; a[13] += PrecipField.eddyMfc(WorldContract.latOf(z), th);
                a[14] += u[0]; n++;
            }
        for (int i = 0; i < NQ; i++) a[i] /= n;
        return a;
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p699_report.txt"), "UTF-8");
        say("P699: 状态量判别力总表（§497）—— contrast = (亚-撒)/(|亚|+|撒|)");
        EarthRef.install();
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        // ★ §535（P1-5）：§476 要求【任何基于盒子的判据都必须先声明该盒在本世界的海陆构成】。
        //   ⚠ 本探针自身的循环起点仍是旧式 lo+half（见下方 for 行）—— 属 P1-6 待统一项；
        //     此处声明的陆占比用 Boxes 的【格心口径】，步长与本探针一致（dLon=5 dLat=2.5）。
        Boxes.declare(rep, "P699 ASIA   70-120E/15-35N", sd, PlateField.PLATE_CELL, 70,120,15,35, 5, 2.5);
        Boxes.declare(rep, "P699 SAHARA 0-30E /20-35N",  sd, PlateField.PLATE_CELL,  0, 30,20,35, 5, 2.5);
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0);
        String[] qn = {"kappa","tSl(K)","tSfc(K)","beta","q(kg/kg)","depl","divU(1/s)","wEff(m/s)",
                        "wBase(w_zm)","sstAnom(K)","elev(m)","P(mm/day)","dh(J/kg)","eddyMfc","u(m/s)"};
        double[] A_ = bm(sd, PlateField.PLATE_CELL, thS, 0), B_ = bm(sd, PlateField.PLATE_CELL, thS, 1);
        say("");
        say("     量            |      亚洲        |      撒哈拉      |  contrast  | 判别力");
        for (int i = 0; i < NQ; i++) {
            double den = Math.abs(A_[i]) + Math.abs(B_[i]);
            double c = den < 1e-30 ? 0.0 : (A_[i] - B_[i]) / den;
            String pw = Math.abs(c) > 0.5 ? "**强**" : (Math.abs(c) > 0.2 ? "中" : "弱(相消)");
            say(String.format(LF, "     %-13s | %+14.5e | %+14.5e | %+9.4f | %s", qn[i], A_[i], B_[i], c, pw));
        }
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}