package probe;

import java.io.*;
import java.util.Locale;

/**
 * P572 -- S2 定常波求解器的【数值验证】（先在探针里对解析解，验证后才进 src）。
 *
 * 方程（Gill 1980 型线性浅水稳态，等时标阻尼 eps）：
 *   eps*u - f*v = -p_x
 *   eps*v + f*u = -p_y
 *   eps*p + c^2*(u_x + v_y) = -Q
 * 消去 u,v 得（f 只随 y 变 => 交叉项恰好抵消）：
 *   L[p] = eps*p + c^2*[ -g*(p_xx+p_yy) - g_y*p_y + h_y*p_x ] = -Q
 *   其中 g = eps/D, h = f/D, D = eps^2 + f^2
 *
 * 自证（f = 0 时解析可对）：
 *   g = 1/eps, g_y = h_y = 0  =>  eps*p - (c^2/eps)*lap(p) = -Q
 *   p = P*sin(kx*x)*sin(ky*y)  =>  P = -Q0/(eps + (c^2/eps)*(kx^2+ky^2))
 *
 * 网格：周期 x（nx），通道 y（ny），p=0 于 y 的两壁。
 */
public class P572 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P572] " + s); System.out.println("[P572] " + s); }

    static int nx, ny;
    static double dx, dy;
    static double eps, cc, betaPlane, yRef;
    static double[] fOf, gOf, gyOf, hyOf;
    static double[] p, q;

    static void build(double eps0, double c0, double beta0, double yRef0) {
        eps = eps0;
        cc = c0 * c0;        // ★ 方程里是 c^2（不是 c）—— 这里曾经写成 c0，导致波速项小 70 倍（= c），
                             //   而解算器与残差检查用的是同一个（错的）算子，所以残差全绿、解析对不上。
                             //   这正是「先对解析解」抓出来的。
        betaPlane = beta0; yRef = yRef0;
        fOf = new double[ny]; gOf = new double[ny]; gyOf = new double[ny]; hyOf = new double[ny];
        for (int j = 0; j < ny; j++) {
            double y = (j - (ny - 1) / 2.0) * dy;          // 以通道中心为 0
            double f = betaPlane * (y + yRef0);
            double D = eps * eps + f * f;
            fOf[j] = f; gOf[j] = eps / D; hyOf[j] = f / D;
        }
        // g_y 与 h_y 用中心差分（端点单侧）
        for (int j = 0; j < ny; j++) {
            int jm = Math.max(0, j - 1), jp = Math.min(ny - 1, j + 1);
            gyOf[j] = (gOf[jp] - gOf[jm]) / ((jp - jm) * dy);
            hyOf[j] = (hyOf[jp] - hyOf[jm]) / ((jp - jm) * dy);
        }
    }

    /** SOR 解 L[p] = -q，返回最大残差。 */
    static double solve(double omega, int iters) {
        for (int it = 0; it < iters; it++) {
            double maxr = 0;
            for (int j = 1; j < ny - 1; j++) {                     // y 两壁 p = 0
                double g = gOf[j], gy = gyOf[j], hy = hyOf[j];
                double axp = -cc * g / (dx * dx) - cc * hy / (2 * dx);   // p_{i+1,j} 系数
                double axm = -cc * g / (dx * dx) + cc * hy / (2 * dx);   // p_{i-1,j}
                double ayp = -cc * g / (dy * dy) - cc * gy / (2 * dy);   // p_{i,j+1}
                double aym = -cc * g / (dy * dy) + cc * gy / (2 * dy);   // p_{i,j-1}
                double ad  = eps + cc * g * (2.0 / (dx * dx) + 2.0 / (dy * dy));
                for (int i = 0; i < nx; i++) {
                    int im = (i - 1 + nx) % nx, ip = (i + 1) % nx;       // x 周期
                    int k = j * nx + i;
                    double rhs = -q[k] - axp * p[j * nx + ip] - axm * p[j * nx + im]
                                       - ayp * p[(j + 1) * nx + i] - aym * p[(j - 1) * nx + i];
                    double pn = rhs / ad;
                    double d = omega * (pn - p[k]);
                    p[k] += d;
                    if (Math.abs(d) > maxr) maxr = Math.abs(d);
                }
            }
            if (it % 2000 == 1999) { /* 静默 */ }
            if (maxr < 1e-12) return maxr;
        }
        return -1;
    }

    /** 残差范数（用于自证）。 */
    static double residual() {
        double mx = 0;
        for (int j = 1; j < ny - 1; j++) {
            double g = gOf[j], gy = gyOf[j], hy = hyOf[j];
            for (int i = 0; i < nx; i++) {
                int im = (i - 1 + nx) % nx, ip = (i + 1) % nx;
                double pxx = (p[j * nx + ip] - 2 * p[j * nx + i] + p[j * nx + im]) / (dx * dx);
                double pyy = (p[(j + 1) * nx + i] - 2 * p[j * nx + i] + p[(j - 1) * nx + i]) / (dy * dy);
                double px = (p[j * nx + ip] - p[j * nx + im]) / (2 * dx);
                double py = (p[(j + 1) * nx + i] - p[(j - 1) * nx + i]) / (2 * dy);
                double L = eps * p[j * nx + i] + cc * (-g * (pxx + pyy) - gy * py + hy * px);
                mx = Math.max(mx, Math.abs(L + q[j * nx + i]));
            }
        }
        return mx;
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p572_report.txt"), "UTF-8");
        say("P572: S2 定常波求解器 —— 数值对解析（f = 0 的精确解）");

        nx = 64; ny = 64;
        double Lx = 40_000_000.0, Ly = 10_000_000.0;
        dx = Lx / nx; dy = Ly / (ny - 1);
        double c0 = 70.0;                  // Gill 的规范值 c = 70 m/s
        double epsDamp = 1.0 / (1.5 * 86400.0);   // 1/1.5 天（Gill 规范）

        // ---- 自证 1：f = 0（beta = 0）时对解析解 ----
        build(epsDamp, c0, 0.0, 0.0);
        p = new double[nx * ny]; q = new double[nx * ny];
        int kx = 2, ky = 2;
        double q0 = 1.0e-6;
        for (int j = 0; j < ny; j++) {
            double y = j * dy;
            for (int i = 0; i < nx; i++) {
                double x = i * dx;
                q[j * nx + i] = q0 * Math.sin(2 * Math.PI * kx * x / Lx) * Math.sin(Math.PI * ky * y / Ly);
            }
        }
        // 注意：sin(pi*ky*y/Ly) 在 y=0 与 y=Ly 都为 0 => 与 p=0 的壁一致
        double lam = Math.pow(2 * Math.PI * kx / Lx, 2) + Math.pow(Math.PI * ky / Ly, 2);
        double Pana = -q0 / (epsDamp + (c0 * c0 / epsDamp) * lam);

        double r = solve(1.5, 40000);
        double err = 0, amp = 0;
        for (int j = 1; j < ny - 1; j++) {
            double y = j * dy;
            for (int i = 0; i < nx; i++) {
                double x = i * dx;
                double ana = Pana * Math.sin(2 * Math.PI * kx * x / Lx) * Math.sin(Math.PI * ky * y / Ly);
                err = Math.max(err, Math.abs(p[j * nx + i] - ana));
                amp = Math.max(amp, Math.abs(ana));
            }
        }
        say("");
        say("  自证 1（f = 0，解析可对）");
        say(String.format(LF, "    eps = %.4e 1/s (1/1.5 天)   c = %.1f m/s   kx = %d, ky = %d", epsDamp, c0, kx, ky));
        say(String.format(LF, "    解析振幅 P = %.6e      数值最大偏差 = %.3e      相对 = %.3e",
            Pana, err, amp > 0 ? err / amp : 0));
        // ---- 诊断：把数值解与解析解在同一批点上并排打出，并报离散有效波数 ----
        double kxe2 = (4.0 * Math.pow(Math.sin(2 * Math.PI * kx / Lx * dx / 2), 2)) / (dx * dx);
        double kye2 = (4.0 * Math.pow(Math.sin(Math.PI * ky / Ly * dy / 2), 2)) / (dy * dy);
        say(String.format(LF, "    [诊断] lam(连续) = %.6e   k_eff^2(离散) = %.6e   比 = %.6f",
            lam, kxe2 + kye2, (kxe2 + kye2) / lam));
        say(String.format(LF, "    [诊断] 分母(连续) = %.6e   分母(离散) = %.6e",
            epsDamp + (c0 * c0 / epsDamp) * lam, epsDamp + (c0 * c0 / epsDamp) * (kxe2 + kye2)));
        say(String.format(LF, "    [诊断] ad(对角) = %.6e   axp(邻) = %.6e",
            epsDamp + (c0 * c0 / epsDamp) * (2.0 / (dx * dx) + 2.0 / (dy * dy)),
            -(c0 * c0 / epsDamp) / (dx * dx)));
        // ---- 决定性诊断：把【解析解】代进 L，看 L[ana] 是否 == -q ----
        double[] save = p.clone();
        for (int j = 0; j < ny; j++) {
            double y = j * dy;
            for (int i = 0; i < nx; i++) {
                double x = i * dx;
                p[j * nx + i] = Pana * Math.sin(2 * Math.PI * kx * x / Lx) * Math.sin(Math.PI * ky * y / Ly);
            }
        }
        double rAna = residual();
        say(String.format(LF, "    [决定性] 把解析解代进 L:  max|L[ana]+q| = %.3e   （若 ~0 => 算子对，SOR 收敛到了别处）", rAna));
        // 逐项拆开看：L[ana] 的各分量（取一个内点）
        {
            int j = 16, i = 8;
            double g = gOf[j], gy = gyOf[j], hy = hyOf[j];
            int im = (i - 1 + nx) % nx, ip = (i + 1) % nx;
            double pxx = (p[j * nx + ip] - 2 * p[j * nx + i] + p[j * nx + im]) / (dx * dx);
            double pyy = (p[(j + 1) * nx + i] - 2 * p[j * nx + i] + p[(j - 1) * nx + i]) / (dy * dy);
            double px = (p[j * nx + ip] - p[j * nx + im]) / (2 * dx);
            double py = (p[(j + 1) * nx + i] - p[(j - 1) * nx + i]) / (2 * dy);
            double term1 = epsDamp * p[j * nx + i];
            double term2 = -cc * g * (pxx + pyy);
            double term3 = -cc * gy * py;
            double term4 = cc * hy * px;
            say(String.format(LF, "    [决定性] 内点(%d,%d): eps*p=%.3e  -c2*g*lap=%.3e  -c2*gy*py=%.3e  +c2*hy*px=%.3e  L=%.3e  -q=%.3e",
                i, j, term1, term2, term3, term4, term1 + term2 + term3 + term4, -q[j * nx + i]));
        }
        p = save;
        say("    [诊断] 三个点的 数值 vs 解析：");
        int[] di = {8, 16, 24}, dj = {8, 16, 32};
        for (int t = 0; t < 3; t++) {
            double x = di[t] * dx, y = dj[t] * dy;
            double ana = Pana * Math.sin(2 * Math.PI * kx * x / Lx) * Math.sin(Math.PI * ky * y / Ly);
            say(String.format(LF, "      i=%2d j=%2d  num=% .6e  ana=% .6e  比=%.4f",
                di[t], dj[t], p[dj[t] * nx + di[t]], ana, Math.abs(ana) > 0 ? p[dj[t] * nx + di[t]] / ana : 0));
        }
        say(String.format(LF, "    残差 max|L[p]+q| = %.3e        SOR 返回 = %.3e", residual(), r));
        say(String.format(LF, "    判据 A（对连续解析，含中心差分固有误差）：相对 < 2e-3"));
        // ★ 严格自证：对【离散解析】——用离散有效波数，应当到机器精度
        double Pdisc = -q0 / (epsDamp + (c0 * c0 / epsDamp) * (kxe2 + kye2));
        double errD = 0, ampD = 0;
        for (int j = 1; j < ny - 1; j++) {
            double y = j * dy;
            for (int i = 0; i < nx; i++) {
                double x = i * dx;
                double anaD = Pdisc * Math.sin(2 * Math.PI * kx * x / Lx) * Math.sin(Math.PI * ky * y / Ly);
                errD = Math.max(errD, Math.abs(p[j * nx + i] - anaD));
                ampD = Math.max(ampD, Math.abs(anaD));
            }
        }
        say(String.format(LF, "    判据 B（对【离散解析】：用 k_eff^2）：P_disc = %.9e   最大偏差 = %.3e   相对 = %.3e",
            Pdisc, errD, ampD > 0 ? errD / ampD : 0));
        say("      => 判据 B 到机器精度才说明【求解器本身】无误；判据 A 的超差是格式固有。");
        say(String.format(LF, "      P_disc/P_cont = %.6f   （应 = 分母(连续)/分母(离散) = %.6f）",
            Pdisc / Pana, (epsDamp + (c0 * c0 / epsDamp) * lam) / (epsDamp + (c0 * c0 / epsDamp) * (kxe2 + kye2))));
        say("");
        say("  （下一步：接真实 Q（由 beta 与地表能量平衡给出），并打印谱半径与临界线行为）");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
