package probe;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P249：**一维 Munk 行求解器** —— §3.1''' 的核心，拿 P242 已知的 2-D 解验证它。
 *
 * <h3>思路</h3>
 * 沿一条纬度行（西岸到东岸）解稳态 Munk 方程：
 * <pre>
 *   A_H * psi'''' - beta * psi' = -curl / (rho0*H)
 *   BC: psi = 0, psi'' = 0 在两端的墙上（自由滑移）
 * </pre>
 * 这是**一维边值问题** —— 不是时间推进，所以：
 * <ul>
 *   <li>没有 CFL、没有 DT、没有伪时间（§11/§16 的两个死结都没了）；</li>
 *   <li>O(行长)，且行长由海岸界定（不是搜索半径）；</li>
 *   <li>西边界层与东边界层**自动**从方程里长出来，不需要解析公式。</li>
 * </ul>
 *
 * <h3>验证口径</h3>
 * 用与 P242 A1 **完全相同**的参数（MITgcm 参照）：闭合盆 1200 km、β=1e-11、A_H=400、
 * H=5000、τx=τ0·sin(πy/Ly)、τ0=0.1，取 y'=300 km 那一行（curl = -1.851e-07 常量）。
 *
 * 2-D 实测（P242 A1）作为参照：psi_max = 4542（解析 4335）、|v|峰 = 109.5 mm/s、
 * e折宽 40 km、西/东 = 12.09。
 */
public class P249 {

    static final Locale L = Locale.ROOT;
    static final File ROOT = new File("K:\\moder\\EyeOfHarmonyBuffer");
    static PrintStream rep;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build\\eoh_probe\\mtn\\p249_report.txt"), "UTF-8");
        say("一维 Munk 行求解器 vs P242 的 2-D 解（同参数）");

        double beta = 1.0e-11, aH = 400.0, rhoH = 1025.0 * 5000.0;
        double Lb = 1_200_000.0;   // 盆宽（不要叫 L，会遮蔽 Locale L）
        double curl = -1.851e-07;
        double dm = Math.cbrt(aH / beta);
        double psiAnalytic = Lb * Math.abs(curl) / (rhoH * beta);
        say(String.format(L, "参数：L=%.0f km  beta=%.1e  A_H=%.0f  rho0*H=%.3e  curl=%.3e",
            Lb / 1000, beta, aH, rhoH, curl));
        say(String.format(L, "      delta_M=%.1f km   Munk 宽 pi*delta=%.1f km", dm / 1000, Math.PI * dm / 1000));
        say(String.format(L, "解析 Sverdrup：psi_max = L*|curl|/(rho0*H*beta) = %.0f m^2/s", psiAnalytic));
        say("");
        say("  参照（P242 A1 二维解）：psi_max=4542  |v|峰=109.5 mm/s  e折宽 40 km  西/东=12.09");

        double[] hs = {10_000.0, 5_000.0, 2_500.0};
        for (double h : hs) {
            int n = (int) (Lb / h);
            double[] curlArr = new double[n];
            for (int i = 0; i < n; i++) curlArr[i] = curl;
            long t0 = System.nanoTime();
            double[] psi = solveRow(curlArr, h, beta, aH, rhoH);
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            double[] v = new double[n];
            for (int i = 1; i < n - 1; i++) v[i] = (psi[i + 1] - psi[i - 1]) / (2 * h);
            v[0] = (psi[1] - 0) / h; v[n - 1] = (0 - psi[n - 2]) / h;
            double pmax = 0; int pk = 0;
            for (int i = 0; i < n; i++) if (Math.abs(psi[i]) > Math.abs(pmax)) { pmax = psi[i]; pk = i; }
            double vpeak = 0; int vk = 0;
            for (int i = 0; i < n; i++) if (Math.abs(v[i]) > Math.abs(vpeak)) { vpeak = v[i]; vk = i; }
            int nMw = (int) (Math.PI * dm / h);
            double wSum = 0, eSum = 0; int wN = 0, eN = 0;
            for (int i = 0; i < Math.min(nMw, n); i++) { wSum += Math.abs(v[i]); wN++; }
            for (int i = Math.max(0, n - nMw); i < n; i++) { eSum += Math.abs(v[i]); eN++; }
            double mW = wSum / Math.max(1, wN), mE = eSum / Math.max(1, eN);
            int xe1 = -1;
            for (int i = vk; i < n; i++) if (Math.abs(v[i]) <= Math.abs(vpeak) / Math.E) { xe1 = i; break; }
            say("");
            say(String.format(L, "  --- h = %.1f km（n=%d，用时 %d ms）---", h / 1000, n, ms));
            say(String.format(L, "      psi_max = %.0f  （解析 %.0f，比 %.3f）   位置 x = %.0f km（离西墙）",
                Math.abs(pmax), psiAnalytic, Math.abs(pmax) / psiAnalytic, pk * h / 1000));
            say(String.format(L, "      |v|峰 = %.2f mm/s @ 离西墙 %.0f km   （二维实测 109.5 mm/s @ 0 km）",
                Math.abs(vpeak) * 1000, vk * h / 1000));
            say(String.format(L, "      e折宽 = %d km   （二维实测 40 km）", xe1 < 0 ? -1 : (int) ((xe1 - vk) * h / 1000)));
            say(String.format(L, "      西带|v|均 = %.4f  东带 = %.4f  ⇒ 西/东 = %.2f   （二维实测 12.09）",
                mW, mE, mW / Math.max(1e-15, mE)));
            StringBuilder sb = new StringBuilder("      v(mm/s) x40 从西墙:");
            for (int k = 0; k <= 40; k++) {
                int i = (int) (k * (n - 1) / 40.0);
                sb.append(String.format(L, " %7.2f", v[i] * 1000));
            }
            say(sb.toString());
        }
        rep.close();
    }

    static void say(String s) { System.out.println("[P249] " + s); rep.println("[P249] " + s); }

    /** 解 4 阶 BVP：A_H*psi'''' - beta*psi' = -curl/(rhoH)，BC psi=0 且 psi''=0 于两端。 */
    static double[] solveRow(double[] curl, double h, double beta, double aH, double rhoH) {
        int n = curl.length;
        double[][] M = new double[n][n];
        double[] b = new double[n];
        double c4 = aH / (h * h * h * h);
        double c1 = beta / (2.0 * h);
        for (int i = 0; i < n; i++) {
            M[i][i] += 6.0 * c4;
            if (i - 1 >= 0) M[i][i - 1] += -4.0 * c4;
            if (i + 1 < n)  M[i][i + 1] += -4.0 * c4;
            if (i - 2 >= 0) M[i][i - 2] += 1.0 * c4;
            if (i + 2 < n)  M[i][i + 2] += 1.0 * c4;
            // 西端 ghost：psi_{-1}=0, psi''=0 ⇒ psi_{-2} = -psi_0
            if (i == 0) M[i][i] += -1.0 * c4;
            // 东端 ghost：psi_n=0, psi''=0 ⇒ psi_{n+1} = -psi_{n-1}
            if (i == n - 1) M[i][i] += -1.0 * c4;
            // beta 项：-beta*psi'
            if (i - 1 >= 0) M[i][i - 1] += c1;
            if (i + 1 < n)  M[i][i + 1] += -c1;
            b[i] = -curl[i] / rhoH;
        }
        return gauss(M, b);
    }

    static double[] gauss(double[][] M, double[] b) {
        int n = b.length;
        for (int k = 0; k < n; k++) {
            int piv = k;
            for (int i = k + 1; i < n; i++) if (Math.abs(M[i][k]) > Math.abs(M[piv][k])) piv = i;
            if (piv != k) { double[] t = M[k]; M[k] = M[piv]; M[piv] = t; double tt = b[k]; b[k] = b[piv]; b[piv] = tt; }
            double d = M[k][k];
            for (int i = k + 1; i < n; i++) {
                double f = M[i][k] / d;
                if (f == 0) continue;
                for (int j = k; j < n; j++) M[i][j] -= f * M[k][j];
                b[i] -= f * b[k];
            }
        }
        double[] x = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            double s = b[i];
            for (int j = i + 1; j < n; j++) s -= M[i][j] * x[j];
            x[i] = s / M[i][i];
        }
        return x;
    }
}
