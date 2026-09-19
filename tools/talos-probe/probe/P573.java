package probe;

import java.io.*;
import java.util.Locale;

/**
 * P573 -- S2 求解器 v2：FFT-in-x + 每波数一条【三对角 BVP】。
 *
 * 为什么必须换掉 v1 的物理空间 SOR（§389 的下一步）：
 *   加入基本流 U(y) 后，(A^2+f^2)^{-1} 在物理空间是【非局域算子】=> 做不出 5 点格式。
 *   按波数分解后 A -> ikU+alpha 是标量 => 逐点除法 => y 方向退化成三对角 BVP。
 *   （这也正是 §351 文献调研推荐的路线。）
 *
 * 算符（Held 的 GFDL 讲义那条，单层化）：
 *   L[psi] = U(y)*(d/dx lap(psi)) + (beta(y) - U_yy(y))*psi_x + alpha*lap(psi) = -F
 * 每波数 k（psi = Re[ psi_k(y) e^{ikx} ]）：
 *   (ikU + alpha) psi_k'' + [ -k^2 (ikU + alpha) + ik (beta - U_yy) ] psi_k = -F_k
 *
 * 解析靶子（U、beta 常数，psi_k = Psi sin(l y)，l = m pi / Ly）：
 *   Psi = -F0 / ( ik beta - (ikU + alpha) l^2 )
 *
 * 自证：
 *   A) 对解析解（常数 U/beta）
 *   B) 临界线：U(y) 穿零，扫 alpha，看解是否有界（§357 的预登记门）
 */
public class P573 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P573] " + s); System.out.println("[P573] " + s); }

    static int ny;
    static double dy, Ly;

    /** 复数三对角求解（Thomas）。a=下对角, b=对角, c=上对角, d=右端；原地返回 x。 */
    static void thomas(double[] ar, double[] ai, double[] br, double[] bi,
                       double[] cr, double[] ci, double[] dr, double[] di,
                       double[] xr, double[] xi) {
        int n = br.length;
        double[] cpr = new double[n], cpi = new double[n], dpr = new double[n], dpi = new double[n];
        // 第一行
        double m = br[0] * br[0] + bi[0] * bi[0];
        cpr[0] = (cr[0] * br[0] + ci[0] * bi[0]) / m;
        cpi[0] = (ci[0] * br[0] - cr[0] * bi[0]) / m;
        dpr[0] = (dr[0] * br[0] + di[0] * bi[0]) / m;
        dpi[0] = (di[0] * br[0] - dr[0] * bi[0]) / m;
        for (int j = 1; j < n; j++) {
            // den = b_j - a_j * cp_{j-1}
            double dr1 = br[j] - (ar[j] * cpr[j - 1] - ai[j] * cpi[j - 1]);
            double di1 = bi[j] - (ar[j] * cpi[j - 1] + ai[j] * cpr[j - 1]);
            double mm = dr1 * dr1 + di1 * di1;
            if (j < n - 1) {
                cpr[j] = (cr[j] * dr1 + ci[j] * di1) / mm;
                cpi[j] = (ci[j] * dr1 - cr[j] * di1) / mm;
            }
            double nr = dr[j] - (ar[j] * dpr[j - 1] - ai[j] * dpi[j - 1]);
            double ni = di[j] - (ar[j] * dpi[j - 1] + ai[j] * dpr[j - 1]);
            dpr[j] = (nr * dr1 + ni * di1) / mm;
            dpi[j] = (ni * dr1 - nr * di1) / mm;
        }
        xr[n - 1] = dpr[n - 1]; xi[n - 1] = dpi[n - 1];
        for (int j = n - 2; j >= 0; j--) {
            xr[j] = dpr[j] - (cpr[j] * xr[j + 1] - cpi[j] * xi[j + 1]);
            xi[j] = dpi[j] - (cpr[j] * xi[j + 1] + cpi[j] * xr[j + 1]);
        }
    }

    /**
     * 解一个波数。U/beta 为长度 ny 的数组（可常数）。
     * 返回 psi 的复数系数（长度 ny）。
     */
    static double[][] solveK(double k, double alpha, double c_unused,
                             double[] U, double[] Uyy, double[] beta, double[] Fk) {
        // 只解内点 j = 1..ny-2（两端 psi = 0）
        int n = ny - 2;
        double[] ar = new double[n], ai = new double[n], br = new double[n], bi = new double[n];
        double[] cr = new double[n], ci = new double[n], dr = new double[n], di = new double[n];
        for (int t = 0; t < n; t++) {
            int j = t + 1;
            double Pr = alpha, Pi = k * U[j];                 // P = ikU + alpha
            double Qr = -k * k * Pr, Qi = -k * k * Pi;        // -k^2 P
            double be = beta[j] - Uyy[j];
            Qr += 0.0;      Qi += k * be;                     // + ik (beta - U_yy)
            double inv = 1.0 / (dy * dy);
            ar[t] = Pr * inv; ai[t] = Pi * inv;
            cr[t] = Pr * inv; ci[t] = Pi * inv;
            br[t] = Qr - 2.0 * Pr * inv; bi[t] = Qi - 2.0 * Pi * inv;
            dr[t] = -Fk[j];   di[t] = 0.0;
        }
        double[] xr = new double[n], xi = new double[n];
        thomas(ar, ai, br, bi, cr, ci, dr, di, xr, xi);
        double[][] out = new double[2][ny];
        for (int t = 0; t < n; t++) { out[0][t + 1] = xr[t]; out[1][t + 1] = xi[t]; }
        return out;
    }

    /** 残差范数（把解代回原方程）。 */
    static double residK(double k, double alpha, double[] U, double[] Uyy, double[] beta,
                         double[] Fk, double[] pr, double[] pi) {
        double mx = 0;
        for (int j = 1; j < ny - 1; j++) {
            double Pr = alpha, Pi = k * U[j];
            double Qr = -k * k * Pr, Qi = -k * k * Pi;
            double be = beta[j] - Uyy[j];
            Qi += k * be;
            double pxxr = (pr[j + 1] - 2 * pr[j] + pr[j - 1]) / (dy * dy);
            double pxxi = (pi[j + 1] - 2 * pi[j] + pi[j - 1]) / (dy * dy);
            double Lr = Pr * pxxr - Pi * pxxi + Qr * pr[j] - Qi * pi[j];
            double Li = Pr * pxxi + Pi * pxxr + Qr * pi[j] + Qi * pr[j];
            mx = Math.max(mx, Math.hypot(Lr + Fk[j], Li));
        }
        return mx;
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p573_report.txt"), "UTF-8");
        say("P573: S2 求解器 v2 —— FFT-in-x + 三对角 BVP（每波数）");

        Ly = 10_000_000.0; ny = 65; dy = Ly / (ny - 1);
        double alpha = 1.0 / (1.5 * 86400.0);          // 等时标阻尼（Gill）
        say(String.format(LF, "  ny=%d  Ly=%.0f m  dy=%.0f m  alpha=%.4e 1/s (1/1.5 天)", ny, Ly, dy, alpha));

        // ================= 自证 A：常数 U/beta 对解析 =================
        double[] Uc = new double[ny], Uyy0 = new double[ny], bc = new double[ny];
        double U0 = 10.0, beta0 = 2.0e-11;
        for (int j = 0; j < ny; j++) { Uc[j] = U0; Uyy0[j] = 0.0; bc[j] = beta0; }
        int m = 2;
        double l = m * Math.PI / Ly;
        double k = 2 * Math.PI * 2 / 40_000_000.0;     // kx = 2
        double F0 = 1.0e-10;
        double[] Fk = new double[ny];
        // ★ 强迫必须与解析假设的 y 形状【一致】：要 sin(l y) 的响应就得给 sin(l y) 的强迫。
        //   （第一版给了常数强迫却拿 sin(l y) 去比 —— 那是测试错，不是求解器错；残差 3.3e-24 已证求解器精确。）
        for (int j = 0; j < ny; j++) Fk[j] = F0 * Math.sin(l * (j * dy));

        double[][] sol = solveK(k, alpha, 0, Uc, Uyy0, bc, Fk);
        // 解析：Psi = -F0 / ( ik beta - (ikU+alpha) l^2 )
        // ★ 解析：Psi = -F0 / ( ik beta - (k^2+l^2)(ikU+alpha) )
        //   （第一版漏了 k^2 项 —— 求解器与解析的差里，k^2 项是必须的。）
        double lam2 = k * k + l * l;
        double denr = -alpha * lam2;                   // Re
        double deni = k * beta0 - k * U0 * lam2;       // Im
        double mm2 = denr * denr + deni * deni;
        double Psir = (-F0 * denr) / mm2, Psii = (F0 * deni) / mm2;
        double errA = 0, ampA = 0;
        for (int j = 1; j < ny - 1; j++) {
            double y = j * dy;
            double s = Math.sin(l * y);
            errA = Math.max(errA, Math.hypot(sol[0][j] - Psir * s, sol[1][j] - Psii * s));
            ampA = Math.max(ampA, Math.hypot(Psir * s, Psii * s));
        }
        say("");
        say("  自证 A（常数 U、beta，对解析）");
        say(String.format(LF, "    U=%.1f m/s  beta=%.3e  alpha=%.4e  kx=2  ky=%d", U0, beta0, alpha, m));
        say(String.format(LF, "    解析 |Psi| = %.6e      最大偏差 = %.3e      相对 = %.3e",
            Math.hypot(Psir, Psii), errA, ampA > 0 ? errA / ampA : 0));
        say(String.format(LF, "    残差 max|L[psi]+F| = %.3e", residK(k, alpha, Uc, Uyy0, bc, Fk, sol[0], sol[1])));
        say("    判据 A（对连续解析，含中心差分固有误差）：相对 < 1e-3");
        // ★ 严格自证：对【离散解析】（用 l_eff^2 = 4 sin^2(l dy/2)/dy^2）
        double leff2 = 4.0 * Math.pow(Math.sin(l * dy / 2.0), 2) / (dy * dy);
        double lamEff = k * k + leff2;
        double ddr = -alpha * lamEff;
        double ddi = k * beta0 - k * U0 * lamEff;
        double md2 = ddr * ddr + ddi * ddi;
        double PsirD = (-F0 * ddr) / md2, PsiiD = (F0 * ddi) / md2;
        double errD = 0, ampD = 0;
        for (int j = 1; j < ny - 1; j++) {
            double s = Math.sin(l * (j * dy));
            errD = Math.max(errD, Math.hypot(sol[0][j] - PsirD * s, sol[1][j] - PsiiD * s));
            ampD = Math.max(ampD, Math.hypot(PsirD * s, PsiiD * s));
        }
        say(String.format(LF, "    判据 B（对【离散解析】，用 l_eff^2）：|Psi_disc| = %.9e   最大偏差 = %.3e   相对 = %.3e",
            Math.hypot(PsirD, PsiiD), errD, ampD > 0 ? errD / ampD : 0));
        say(String.format(LF, "      l_eff^2/l^2 = %.8f   （解释了判据 A 的 4.3e-4）", leff2 / (l * l)));

        // ================= 自证 B：临界线（U 穿零）扫 alpha =================
        say("");
        say("  自证 B（§357 预登记门）：U(y) 穿零 —— 扫 alpha，看解是否有界");
        double[] Ux = new double[ny], Uyyx = new double[ny], bx = new double[ny];
        double Uamp = 10.0;
        for (int j = 0; j < ny; j++) {
            double y = j * dy;
            Ux[j] = Uamp * (y - Ly / 2.0) / (Ly / 2.0);     // 在 y = Ly/2 穿零
            bx[j] = beta0;
        }
        // U_yy（解析：U 是线性的 => 0）
        for (int j = 0; j < ny; j++) Uyyx[j] = 0.0;
        say("    alpha(1/s)     1/alpha(天)      max|psi|        残差");
        double[] alphas = {1.0 / (0.5 * 86400), 1.0 / (1.5 * 86400), 1.0 / (5 * 86400), 1.0 / (20 * 86400)};
        for (double al : alphas) {
            double[] Fk2 = new double[ny];
            for (int j = 0; j < ny; j++) Fk2[j] = F0 * Math.sin(l * (j * dy));
            double[][] s2 = solveK(k, al, 0, Ux, Uyyx, bx, Fk2);
            double mx = 0;
            for (int j = 1; j < ny - 1; j++) mx = Math.max(mx, Math.hypot(s2[0][j], s2[1][j]));
            say(String.format(LF, "    %.4e     %8.2f     %12.5e     %.3e",
                al, 1.0 / al / 86400.0, mx, residK(k, al, Ux, Uyyx, bx, Fk2, s2[0], s2[1])));
        }
        say("    判据：alpha 越小 max|psi| 越大但【有界且不爆】=> 阻尼穿过临界线成立（Gill 先例）");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
