package probe;

import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.BarotropicGyre;
import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.GlobalCirculation;
import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.RelaxedClimate;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P246：**生产风场的 curl 剖面** —— 分析报告里那把「一直没量的尺子」。
 *
 * <h3>为什么现在必须量它</h3>
 * 洋流的唯一驱动就是 curl(τ)。P245 的 G4 只差 16%（0.0422 vs 0.05），而 P245 用的是
 * **MITgcm 算例的风**（τ0 = 0.1 N/m²，curl 峰 = 1.33e-7）。
 * 而 P221 [1d] 在极区实测的 curl 是 **3.1e-6** —— 是它的 **23 倍**。
 *
 * ⇒ 如果生产风场的 curl 在中纬度也远大于 1.33e-7，那么 G4 会同比上升，M1 可能直接通过。
 * ⇒ 如果它反而更小，那么「风」就是下一个要动的设计参数（而不是几何）。
 *
 * <h3>口径</h3>
 * <pre>
 *   τ = ρ_a·C_D·(WIND_MS·|U|)·U·WIND_MS        （与 BarotropicGyre.tauX/tauY 逐字同式）
 *   curl_z(τ) = ∂τy/∂x − ∂τx/∂y               （中心差分）
 *   v_Sverdrup = curl/(ρ0·H·β)                （H=100, β 用求解器同一公式）
 *   ψ_max(盆宽 L) = L·|curl|/(ρ0·H·β)
 * </pre>
 * 采样：x ∈ [0,1000km) @10km x z ∈ [0,1M) @5km，覆盖整个气候周期。
 */
public class P246 {

    static final int SEED = 1022228679;
    static final int ZC = 1_000_000;
    static final int NX = 100, NZ = 200;
    static final int DX = 10_000, DZ = 5_000;
    static final int NB = 10;

    static final Locale L = Locale.ROOT;
    static final File ROOT = new File("K:\\moder\\EyeOfHarmonyBuffer");
    static PrintStream rep;

    public static void main(String[] args) throws Exception {
        RelaxedClimate.PREHEAT = false;
        rep = new PrintStream(new File(ROOT, "build\\eoh_probe\\mtn\\p246_report.txt"), "UTF-8");
        say("生产风场 curl 剖面（洋流的唯一驱动）");
        say(String.format(L, "采样 %d x %d：x∈[0,%dkm) @%dkm，z∈[0,1M) @%dkm", NX, NZ, NX * DX / 1000, DX / 1000, DZ / 1000));

        double[] tx = new double[NX * NZ], ty = new double[NX * NZ], sp = new double[NX * NZ];
        for (int iy = 0; iy < NZ; iy++) {
            int z = iy * DZ;
            for (int ix = 0; ix < NX; ix++) {
                int x = ix * DX;
                double[] w = RelaxedClimate.sampleWind(x, z, SEED);
                double s = Math.hypot(w[0], w[1]) * BarotropicGyre.WIND_MS;
                int i = iy * NX + ix;
                sp[i] = s;
                tx[i] = BarotropicGyre.RHO_AIR * BarotropicGyre.C_D * s * w[0] * BarotropicGyre.WIND_MS;
                ty[i] = BarotropicGyre.RHO_AIR * BarotropicGyre.C_D * s * w[1] * BarotropicGyre.WIND_MS;
            }
        }
        say(String.format(L, "采样完成  t=%.1fs", 0.0));

        // 分箱累计
        double[] cc = new double[NX * NZ];
        double[] n = new double[NB], cSum = new double[NB], cAbs = new double[NB], cSq = new double[NB], spSum = new double[NB];
        double globAbs = 0; long globN = 0; double globMax = 0;
        for (int iy = 1; iy < NZ - 1; iy++) {
            int z = iy * DZ;
            double b = GlobalCirculation.bandD(z);
            int bin = Math.min(NB - 1, (int) (b * NB));
            for (int ix = 1; ix < NX - 1; ix++) {
                int i = iy * NX + ix;
                double dtydx = (ty[i + 1] - ty[i - 1]) / (2.0 * DX);
                double dtxdy = (tx[i + NX] - tx[i - NX]) / (2.0 * DZ);
                double c = dtydx - dtxdy;
                cc[i] = c;
                n[bin]++; cSum[bin] += c; cAbs[bin] += Math.abs(c); cSq[bin] += c * c; spSum[bin] += sp[i];
                globAbs += Math.abs(c); globN++;
                if (Math.abs(c) > globMax) globMax = Math.abs(c);
            }
        }

        double rhoH = BarotropicGyre.RHO_WATER * BarotropicGyre.H_MIXED;
        // ---- 第二遍：块平均，把「海盆尺度的连贯 curl」从「小尺度噪声」里分离出来 ----
        int BX = 20, BZ = 20;                    // 200 km x 100 km 的块
        double[] bn = new double[NB], bAbs = new double[NB], bSum = new double[NB];
        for (int by = 1; by + BZ <= NZ - 1; by += BZ) {
            int zm = (by + BZ / 2) * DZ;
            double b = GlobalCirculation.bandD(zm);
            int bin = Math.min(NB - 1, (int) (b * NB));
            for (int bx = 1; bx + BX <= NX - 1; bx += BX) {
                double s = 0;
                for (int j = 0; j < BZ; j++) for (int i2 = 0; i2 < BX; i2++) s += cc[(by + j) * NX + bx + i2];
                s /= (BX * BZ);
                bn[bin]++; bSum[bin] += s; bAbs[bin] += Math.abs(s);
            }
        }
        say("");
        say("  参照（P245 用的 MITgcm 风）：τ0=0.1 N/m²，curl 峰 = 1.33e-7 Pa/m，v_Sverdrup ≈ 4.0 mm/s");
        say(String.format(L, "  全场合计：平均 |curl| = %.3e Pa/m   最大 |curl| = %.3e Pa/m", globAbs / globN, globMax));
        say("");
        say(String.format(L, "  %-9s %8s %12s %12s %12s %11s %12s %11s",
            "bandD", "风速m/s", "curl均值", "|curl|均值", "|curl|RMS", "v_Sverdrup", "psi(L=1200km)", "相对MIT"));
        double mit = 1.331e-7;
        for (int k = 0; k < NB; k++) {
            if (n[k] == 0) continue;
            double bMid = (k + 0.5) / NB;
            double beta = 2.0 * BarotropicGyre.OMEGA * Math.cos(bMid * Math.PI / 2.0) * (Math.PI / 2.0) * 2.0 / ZC;
            double cAbsM = cAbs[k] / n[k];
            double vSv = cAbsM / (rhoH * beta);
            double psi = 1.2e6 * cAbsM / (rhoH * beta);
            say(String.format(L, "  %.1f-%.1f  %8.2f %12.3e %12.3e %12.3e %8.2f mm/s %12.0f %10.2fx",
                k * 0.1, (k + 1) * 0.1, spSum[k] / n[k], cSum[k] / n[k], cAbsM,
                Math.sqrt(cSq[k] / n[k]), vSv * 1000, psi, cAbsM / mit));
        }
        say("");
        say(String.format(L, "  ==== 200x100 km 块平均后的【连贯 curl】（这才驱动得了海盆涡旋）===="));
        say(String.format(L, "  %-9s %8s %14s %14s %14s %11s", "bandD", "块数", "连贯curl均值", "|连贯curl|均值", "相对MIT", "Sverdrup"));
        for (int k = 0; k < NB; k++) {
            if (bn[k] == 0) continue;
            double bMid = (k + 0.5) / NB;
            double beta = 2.0 * BarotropicGyre.OMEGA * Math.cos(bMid * Math.PI / 2.0) * (Math.PI / 2.0) * 2.0 / ZC;
            double ba = bAbs[k] / bn[k];
            say(String.format(L, "  %.1f-%.1f  %8.0f %14.3e %14.3e %12.2fx %8.2f mm/s",
                k * 0.1, (k + 1) * 0.1, bn[k], bSum[k] / bn[k], ba, ba / mit, ba / (rhoH * beta) * 1000));
        }
        say("");
        say("  读法：相对MIT 那一列 = 该带 |curl| 均值 / P245 用的 1.33e-7。");
        say("        若普遍 >1，则 P245 的 G4 是被低估的，乘上该倍数就是新世界的预期值。");
        rep.close();
    }

    static void say(String s) { System.out.println("[P246] " + s); rep.println("[P246] " + s); }
}
