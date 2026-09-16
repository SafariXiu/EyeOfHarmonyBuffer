package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Locale;

/**
 * P459（v2）：**p' 的 cell 项 —— 局部 &lt;kappa&gt; 参考值能不能既消掉海盆净偏压、
 * 又保住沿岸气压梯度？**（用户裁决 3A 的落地前量化；纪律：先记录再改代码）
 *
 * <p>关键认识：cell = G*carrier*gate*(KREF - kappa)。**跨岸线的差值恒等于 1**（kappa 0-&gt;1），
 * 与 KREF 无关 ⇒ 换 KREF 只改**海盆整体偏压**，理论上传岸梯度不该被动。
 * 但局部均值 KREF(x) 自己带梯度 ⇒ 它会在岸线附近**抵消一部分** kappa 的梯度。
 * 所以必须直接量：① 海盆净偏压降了多少；② **岸线附近 |d(cell)/dx| 掉了多少**。
 */
public class P459 {

    static final int SEED = 1022228679;
    static final long SD = SimTerrain.seedOf(SEED);
    static final int CELL = PlateField.PLATE_CELL;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    static final int DX = 250_000;
    static final int NX = 97;
    static final int X0 = -12_000_000;
    static final int NZ = 16;
    static final double[] PH = {0.0, Math.PI / 2, Math.PI, 3 * Math.PI / 2};
    /** 窗口半宽（采样点数）：1 点 = 250 km。null 表示生产口径（全球常数）。 */
    static final int[] HW = {-1, 8, 16, 32, 48};
    static final String[] HWN = {"c0_GLOBAL", "c1_2Mkm", "c2_4Mkm", "c3_8Mkm", "c4_12Mkm"};

    static void say(String s) { rep.println("[P459] " + s); System.out.println("[P459] " + s); }

    static double[] boxcar(double[] a, int hw) {
        int n = a.length; double[] o = new double[n];
        for (int i = 0; i < n; i++) {
            int lo = Math.max(0, i - hw), hi = Math.min(n - 1, i + hw);
            double s = 0; for (int j = lo; j <= hi; j++) s += a[j];
            o[i] = s / (hi - lo + 1);
        }
        return o;
    }

    static double cell4(double lat, double kRef, double k) {
        double s = 0;
        for (double t : PH) {
            double shifted = Math.toDegrees(lat - Atmosphere.CELL_MIGRATION * Math.cos(t - Atmosphere.CELL_LAG));
            s += Atmosphere.CELL_GAIN * ZonalTables.carrier(shifted) * Atmosphere.tropicGate(lat)
               * (kRef - Atmosphere.clamp01(k));
        }
        return s / PH.length;
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p459_report.txt"), "UTF-8");
        say("P459 v2: can a LOCAL <kappa> reference remove the basin bias WITHOUT losing the coastal gradient?");
        say(String.format(LF, "  KAPPA_MEAN=%.4f CELL_GAIN=%.3f migration=%.1fdeg gate=%.1fdeg  grid: %d lines x %d pts (dx=%d km)",
            Atmosphere.KAPPA_MEAN, Atmosphere.CELL_GAIN, Math.toDegrees(Atmosphere.CELL_MIGRATION),
            Atmosphere.CELL_TROPIC_GATE_DEG, NZ, NX, DX / 1000));
        double[] basinAbs = new double[5]; long[] nBasin = new long[5];
        double[] worst = new double[5]; double[] worstLat = new double[5];
        double[] gradCoast = new double[5]; long[] nCoast = new long[5];
        double[] gradAll = new double[5]; long[] nAll = new long[5];
        double[] ampO = new double[5]; double[] ampL = new double[5]; long nO = 0, nL = 0;

        for (int iz = 0; iz < NZ; iz++) {
            int z = (int) ((iz + 0.5) / NZ * WorldContract.Z_CYCLE);
            double lat = WorldContract.latOf(z);
            double[] k = new double[NX];
            for (int i = 0; i < NX; i++) k[i] = Atmosphere.kappaAt(X0 + i * DX, z, SD, CELL);
            double[][] c = new double[HW.length][NX];
            for (int v = 0; v < HW.length; v++) {
                double[] ref = HW[v] < 0 ? null : boxcar(k, HW[v]);
                for (int i = 0; i < NX; i++) {
                    double kr = HW[v] < 0 ? Atmosphere.KAPPA_MEAN : ref[i];
                    c[v][i] = cell4(lat, kr, k[i]);
                }
            }
            // 海盆段
            int i = 0;
            while (i < NX) {
                if (k[i] < 0.5) {
                    int j = i; while (j < NX && k[j] < 0.5) j++;
                    if ((j - i) * DX >= 3_000_000) {
                        for (int v = 0; v < HW.length; v++) {
                            double b = 0; for (int q = i; q < j; q++) b += c[v][q];
                            b /= (j - i);
                            basinAbs[v] += Math.abs(b); nBasin[v]++;
                            if (Math.abs(b) > worst[v]) { worst[v] = Math.abs(b); worstLat[v] = Math.toDegrees(lat); }
                        }
                    }
                    i = j;
                } else i++;
            }
            // 岸线点：±500 km 内 kappa 变化 > 0.3
            for (int q = 2; q < NX - 2; q++) {
                boolean coast = Math.abs(k[q + 2] - k[q - 2]) > 0.3;
                for (int v = 0; v < HW.length; v++) {
                    double g = Math.abs(c[v][q + 1] - c[v][q - 1]) / (2.0 * DX);
                    gradAll[v] += g; nAll[v]++;
                    if (coast) { gradCoast[v] += g; nCoast[v]++; }
                }
                if (k[q] < 0.1) { for (int v = 0; v < HW.length; v++) ampO[v] += Math.abs(c[v][q]); nO++; }
                if (k[q] > 0.9) { for (int v = 0; v < HW.length; v++) ampL[v] += Math.abs(c[v][q]); nL++; }
            }
        }
        say("");
        say("A. 海盆净 cell 偏压（kappa<0.5 且长度>=3000 km 的连续段）");
        say(String.format(LF, "  %-12s %-14s %-14s", "reference", "mean|net| Pa", "worst Pa @lat"));
        for (int v = 0; v < HW.length; v++)
            say(String.format(LF, "  %-12s %-14.1f %-14.1f   (n=%d)", HWN[v], basinAbs[v] / nBasin[v], worst[v], nBasin[v]));
        say("");
        say("B. 岸线附近 |d(cell)/dx| —— 这是真正驱动沿岸风的量");
        say(String.format(LF, "  %-12s %-16s %-16s %-10s", "reference", "coast Pa/m", "all pts Pa/m", "coast/all"));
        for (int v = 0; v < HW.length; v++)
            say(String.format(LF, "  %-12s %-16.4e %-16.4e %-10.3f", HWN[v], gradCoast[v] / nCoast[v], gradAll[v] / nAll[v],
                (gradCoast[v] / nCoast[v]) / (gradAll[v] / nAll[v])));
        say("");
        say("C. 相对生产口径（c0）的保留率");
        say(String.format(LF, "  %-12s %-14s %-14s %-14s %-14s", "reference", "basinBias", "coastGrad", "oceanAmp", "landAmp"));
        for (int v = 0; v < HW.length; v++)
            say(String.format(LF, "  %-12s %-14.1f%% %-14.1f%% %-14.1f%% %-14.1f%%", HWN[v],
                100 * (basinAbs[v] / nBasin[v]) / (basinAbs[0] / nBasin[0]),
                100 * (gradCoast[v] / nCoast[v]) / (gradCoast[0] / nCoast[0]),
                100 * (ampO[v] / nO) / (ampO[0] / nO),
                100 * (ampL[v] / nL) / (ampL[0] / nL)));
        say("");
        say(String.format(LF, "  nCoast=%d  nAll=%d  nOcean(pure)=%d  nLand(pure)=%d", nCoast[0], nAll[0], nO, nL));
        rep.flush();
        say("DONE");
    }
}
