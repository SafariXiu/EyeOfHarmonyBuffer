package probe;

import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.BarotropicGyre;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P242（2026-09 新增）：**求解器标定 —— 复现 MITgcm 的 `tutorial_barotropic_gyre`**。
 *
 * <p>目的只有一个：回答「我们的 BarotropicGyre 到底解得对不对」。
 * 在此之前我们所有的理想海盆实验（P200）都是**自己造的几何、没有外部参照** ——
 * 也就是说，从来没有任何一次测量能排除「求解器本身有偏差」。
 *
 * <h3>外部参照（MITgcm manual, verification/tutorial_barotropic_gyre）</h3>
 * <pre>
 *   闭合矩形方盆 1200 x 1200 km，单层 Δz = 5000 m
 *   f = f0 + βy,  f0 = 1e-4,  β = 1e-11 s^-1 m^-1
 *   τx(y) = τ0·sin(πy/Ly),  τ0 = 0.1 N/m²,  Ly = 1200 km
 *   Δx = Δy = 20 km（60x60），A_h = 400 m²/s，δt = 1200 s
 *   Munk 宽 M_w = π(A_h/β)^(1/3) ≈ 100 km（manual 原文）
 * </pre>
 *
 * <h3>为什么这个算例正好是我们需要的</h3>
 * τx = τ0·sin(πy/Ly) 给出 curl = −τ0(π/Ly)cos(πy/Ly)：**南半负、北半正** ——
 * 于是**同一个盆里同时有副热带涡旋（西边界向极＝暖流）和副极地涡旋（西边界向赤道＝冷流）**。
 * 这正是我们关心的双涡旋结构，而且有解析的 Sverdrup / Munk 参照可以对照。
 *
 * <h3>方程对应关系（这是本探针成立的前提）</h3>
 * MITgcm 解的是原始方程（自由面），我们对速度方程取旋度、取刚盖：
 * <pre>
 *   ∂ζ/∂t + βv = curl(τ)/(ρ0·Δz) + A_h∇²ζ      （ζ=∇²ψ, u=−∂ψ/∂y, v=∂ψ/∂x）
 * </pre>
 * ⇒ 两者的 **A_h ↔ A_H 直接对应**，且本求解器**不读 f**（源码里 fC 只赋值、从不读取），
 * 与刚盖正压涡度方程一致（f 只通过 β 进入）。
 *
 * <h3>解析参照（本探针自己算）</h3>
 * <pre>
 *   curl(y)   = −τ0·(π/Ly)·cos(πy/Ly)
 *   v_Sverdrup(y) = curl(y)/(ρ0·H·β)
 *   δ_M = (A_H/β)^(1/3),  M_w = π·δ_M
 *   强化比预估   ≈ Lx/M_w − 1
 * </pre>
 *
 * <h3>配置</h3>
 * <pre>
 *   A1  MITgcm 参照            闭合方盆, β=1e-11,  A_H=400,  H=5000, V_CYCLES=1
 *   A2  同上但 V_CYCLES=4      —— 检验内层泊松是否欠收敛（P200 的旧注释提示过这点）
 *   B   同几何 + 生产参数       闭合方盆, β=3.24e-10, A_H=1.9e4, H=100
 *   C   B 但去掉 X 墙          环形通道（对照：预期 v ≡ 0）
 *   D   MITgcm 参照 + 细网格    NCX=NCZ=120（δ_M 分辨率收敛性）
 * </pre>
 *
 * <p>只读诊断：直接调 {@link BarotropicGyre#solve}，通过公开静态字段配置，**不改任何源码**。
 */
public class P242 {

    static final int NXF = 62, NYF = 62;          // 细网格（外圈一圈陆）
    static final double DXF = 20_000.0, DZF = 20_000.0;
    static final double TAU0 = 0.1, LY = 1_200_000.0;
    static final double BETA_MIT = 1.0e-11;
    static final double BETA_OURS = 3.24e-10;
    static final double AH_MIT = 400.0, H_MIT = 5000.0;
    static final double AH_OURS = 1.9e4, H_OURS = 100.0;

    static final Locale L = Locale.ROOT;
    static final File ROOT = new File("K:\\moder\\EyeOfHarmonyBuffer");
    static PrintStream rep;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build\\eoh_probe\\mtn\\p242_report.txt"), "UTF-8");
        say("求解器标定：复现 MITgcm tutorial_barotropic_gyre（只读诊断，不改源码）");
        say("注意：本求解器不读 f（fC 只赋值），与刚盖正压涡度方程一致 —— 所以 f0 无关，只比 β。");
        say("本批 4 个配置；A1b 是 A1 的【步数加倍】收敛检验（P222 的判据：加倍后应逐位/近位不变）。");
        run("A1  MITgcm 参照 (15万步)",     true,  60, BETA_MIT,  AH_MIT,  H_MIT,  1, 150_000);
        run("B   生产参数 · 闭合盆 DT=140",  true,  60, BETA_OURS, AH_OURS, H_OURS, 1, 150_000);
        run("C   生产参数 · X 环形通道 DT=35", false, 60, BETA_OURS, AH_OURS, H_OURS, 1, 150_000, 35.0);
        dtSweep();
        say("");
        say("E/F：B 的配置改用小 DT 跑满 15 万步 —— 若收敛则发散是【DT 稳定裕度】问题，不是物理 bug。");
        run("E   生产参数 · 闭合盆 DT=35",  true,  60, BETA_OURS, AH_OURS, H_OURS, 1, 150_000, 35.0);
        run("F   生产参数 · 闭合盆 DT=14",  true,  60, BETA_OURS, AH_OURS, H_OURS, 1, 150_000, 14.0);
        rep.close();
    }

    static void say(String s) { System.out.println("[P242] " + s); rep.println("[P242] " + s); }
    static String f2(double x) { return Double.isNaN(x) ? "NaN" : String.format(L, "%.2f", x); }

    static boolean[] boxLand(int nc, boolean closeX) {
        boolean[] land = new boolean[NXF * NYF];
        for (int y = 0; y < NYF; y++) {
            for (int x = 0; x < NXF; x++) {
                boolean edge = (y == 0 || y == NYF - 1) || (closeX && (x == 0 || x == NXF - 1));
                land[y * NXF + x] = edge;
            }
        }
        return land;
    }

    /** τx = ρ_a·C_D·(WIND_MS·|U|)·U·WIND_MS ⇒ 反解出给定 τx 所需的 u（v=0）。 */
    static double[] windField() {
        double den = BarotropicGyre.RHO_AIR * BarotropicGyre.C_D * BarotropicGyre.WIND_MS * BarotropicGyre.WIND_MS;
        double[] u = new double[NXF * NYF];
        for (int y = 0; y < NYF; y++) {
            double yp = y * DZF - DZF;                       // 从南墙内缘量起
            double s = Math.sin(Math.PI * yp / LY);
            if (s < 0) s = 0;
            double uu = Math.sqrt(s * TAU0 / den);
            for (int x = 0; x < NXF; x++) u[y * NXF + x] = uu;
        }
        return u;
    }

    static void run(String tag, boolean closeX, int nc, double beta, double aH, double hMix, int vcy, int macro) {
        run(tag, closeX, nc, beta, aH, hMix, vcy, macro, 140.0);
    }

    static void run(String tag, boolean closeX, int nc, double beta, double aH, double hMix, int vcy, int macro, double dt) {
        say("");
        say("===== " + tag + " =====");
        say(String.format(L, "  β=%.3e  A_H=%.0f  H=%.0f  NCX=NCZ=%d  V_CYCLES=%d  闭合X=%s  MACRO=%d  DT=%.1f",
            beta, aH, hMix, nc, vcy, closeX ? "是" : "否（环形）", macro, dt));
        BarotropicGyre.NCX = nc; BarotropicGyre.NCZ = nc;
        BarotropicGyre.A_H = aH; BarotropicGyre.H_MIXED = hMix;
        BarotropicGyre.BETA_SCALE = 1.0;
        BarotropicGyre.V_CYCLES = vcy; BarotropicGyre.FINAL_CYCLES = 32;
        BarotropicGyre.DT = dt; BarotropicGyre.MACRO = macro;
        BarotropicGyre.CONV_TOL = 1e-3; BarotropicGyre.MIN_STEPS = 150;

        boolean[] land = boxLand(nc, closeX);
        double[] fRow = new double[NYF], betaRow = new double[NYF];
        for (int y = 0; y < NYF; y++) { fRow[y] = 0.0; betaRow[y] = beta; }
        double[] uW = windField(), vW = new double[NXF * NYF];
        double[] uo = new double[NXF * NYF], vo = new double[NXF * NYF];

        long st0 = BarotropicGyre.TOTAL_MACRO_STEPS.get();
        long t0 = System.nanoTime();
        BarotropicGyre.solve(NXF, NYF, DXF, DZF, land, null, uW, vW, fRow, betaRow, uo, vo, 0);
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        long steps = BarotropicGyre.TOTAL_MACRO_STEPS.get() - st0;
        say(String.format(L, "  用时 %d ms  steps=%d  ζ残差=%.3e  maxZeta=%.3e  泊松残差=%.3e",
            ms, steps, BarotropicGyre.lastResidual, BarotropicGyre.lastMaxZeta, BarotropicGyre.lastPoisRes));
        measure(land, uo, vo, beta, aH, hMix);
    }

    /**
     * [D] 生产参数配置的 **DT 扫描**：固定 2000 步，看 maxZeta 是否随 DT 爆炸。
     *
     * <p>动机：B/C 在 DT=140 下发散（maxZeta 1e9），而 A1 在同样 DT 下完全正常。
     * 假说 = **显式 Rossby 波 CFL**：c_R ≈ βL²/π²，L 越大波越快。
     * <pre>
     *   A1 : β=1e-11, L=1200km ⇒ c_R=1.46 m/s ⇒ CFL 上限 Δx/c = 14.2 ks
     *   B  : β=3.24e-10, L=1200km ⇒ c_R=47.3 m/s ⇒ CFL 上限 Δx/c =   437 s   ← DT=140 只剩 3.1 倍余量
     *   生产: β=3.24e-10, L= 300km ⇒ c_R= 2.95 m/s ⇒ CFL 上限 Δx/c =  3.2 ks  ← 所以生产从没碰到过
     * </pre>
     * B 的稳态 ζ 量级参照：v_Sverdrup/δ_M = 7.88e-3/38900 = 2.0e-7。
     */
    static void dtSweep() {
        say("");
        say("===== D  生产参数配置的 DT 扫描（固定 2000 步；maxZeta 参照值 ≈2.0e-7）=====");
        double[][] cfg = {{BETA_OURS, AH_OURS, H_OURS}, {BETA_MIT, AH_MIT, H_MIT}};
        String[] tag = {"生产参数(β=3.24e-10,A_H=1.9e4,H=100)", "MITgcm 参照(β=1e-11,A_H=400,H=5000)"};
        for (int c = 0; c < cfg.length; c++) {
            say("  --- " + tag[c] + " ---");
            double[] dts = {140.0, 70.0, 35.0, 14.0, 3.5};
            for (double dt : dts) {
                BarotropicGyre.NCX = 60; BarotropicGyre.NCZ = 60;
                BarotropicGyre.A_H = cfg[c][1]; BarotropicGyre.H_MIXED = cfg[c][2];
                BarotropicGyre.BETA_SCALE = 1.0;
                BarotropicGyre.V_CYCLES = 1; BarotropicGyre.FINAL_CYCLES = 32;
                BarotropicGyre.DT = dt; BarotropicGyre.MACRO = 2000;
                BarotropicGyre.CONV_TOL = 0.0; BarotropicGyre.MIN_STEPS = 1_000_000;
                boolean[] land = boxLand(60, true);
                double[] fRow = new double[NYF], betaRow = new double[NYF];
                for (int y = 0; y < NYF; y++) { fRow[y] = 0.0; betaRow[y] = cfg[c][0]; }
                double[] uW = windField(), vW = new double[NXF * NYF];
                double[] uo = new double[NXF * NYF], vo = new double[NXF * NYF];
                long st0 = BarotropicGyre.TOTAL_MACRO_STEPS.get();
                BarotropicGyre.solve(NXF, NYF, DXF, DZF, land, null, uW, vW, fRow, betaRow, uo, vo, 0);
                long steps = BarotropicGyre.TOTAL_MACRO_STEPS.get() - st0;
                say(String.format(L, "    DT=%7.1f  伪时间=%.2e s  steps=%d  ζ残差=%.3e  maxZeta=%.3e  泊松残差=%.3e",
                    dt, dt * steps, steps, BarotropicGyre.lastResidual, BarotropicGyre.lastMaxZeta, BarotropicGyre.lastPoisRes));
            }
        }
    }

    static int firstSea(boolean[] land, int y) { for (int x = 0; x < NXF; x++) if (!land[y * NXF + x]) return x; return -1; }
    static int lastSea(boolean[] land, int y) { for (int x = NXF - 1; x >= 0; x--) if (!land[y * NXF + x]) return x; return -1; }
    static int firstSeaRow(boolean[] land) { for (int y = 0; y < NYF; y++) if (firstSea(land, y) >= 0) return y; return -1; }
    static int lastSeaRow(boolean[] land) { for (int y = NYF - 1; y >= 0; y--) if (firstSea(land, y) >= 0) return y; return -1; }

    static void measure(boolean[] land, double[] uo, double[] vo, double beta, double aH, double hMix) {
        int y0 = firstSeaRow(land), y1 = lastSeaRow(land);
        if (y0 < 0) { say("  没有海格"); return; }
        int yMid = (y0 + y1) / 2;
        int xw = firstSea(land, yMid), xe = lastSea(land, yMid);
        double Lx = (xe - xw + 1) * DXF, LyB = (y1 - y0 + 1) * DZF;
        double dM = Math.cbrt(aH / beta), Mw = Math.PI * dM;
        double curlPeak = TAU0 * Math.PI / LY;
        double vSv = curlPeak / (BarotropicGyre.RHO_WATER * hMix * beta);
        say(String.format(L, "  盆：%d x %d 格 = %.0f x %.0f km   海格 %d", xe - xw + 1, y1 - y0 + 1, Lx / 1000, LyB / 1000, count(land)));
        say(String.format(L, "  解析参照：δ_M=(A_H/β)^(1/3)=%.1f km   M_w=πδ_M=%.1f km   curl峰=%.3e   v_Sverdrup峰=%.3f mm/s   强化比预估 Lx/M_w-1=%.1f",
            dM / 1000, Mw / 1000, curlPeak, vSv * 1000, Lx / Mw - 1));

        double su = 0, sv = 0; int ns = 0;
        for (int i = 0; i < NXF * NYF; i++) if (!land[i]) { su += uo[i] * uo[i]; sv += vo[i] * vo[i]; ns++; }
        double ru = Math.sqrt(su / ns), rv = Math.sqrt(sv / ns);
        say(String.format(L, "  全场 %d 海格：RMS u=%.5f  RMS v=%.5f  u/v=%.3f", ns, ru, rv, ru / Math.max(1e-12, rv)));

        int nMw = Math.max(1, (int) (Mw / DXF));
        for (int half = 0; half < 2; half++) {
            int yy = y0 + (int) (LyB * (half == 0 ? 0.25 : 0.75) / DZF);
            if (yy <= y0) yy = y0 + 1;
            if (yy >= y1) yy = y1 - 1;
            int xa = firstSea(land, yy), xb = lastSea(land, yy);
            if (xa < 0) { say("  行 " + yy + " 没有海格"); continue; }
            double yp = (yy - y0) * DZF;
            double curl = -TAU0 * (Math.PI / LY) * Math.cos(Math.PI * yp / LY);
            double vPred = curl / (BarotropicGyre.RHO_WATER * hMix * beta);
            say(String.format(L, "  --- 行 y'=%.0f km（%s）curl=%.3e  ⇒ Sverdrup v=%.3f mm/s ---",
                yp / 1000, half == 0 ? "南半：西边界应向北(暖流)" : "北半：西边界应向南(冷流)", curl, vPred * 1000));
            StringBuilder sx = new StringBuilder(), svv = new StringBuilder();
            int span = xb - xa;
            for (int k = 0; k <= 40; k++) {
                int x = xa + (int) Math.round(span * k / 40.0);
                sx.append(String.format(L, "%6.0f", x * DXF / 1000));
                svv.append(String.format(L, "%6.2f", vo[yy * NXF + x] * 1000));
            }
            say("    x km:" + sx);
            say("    v mm/s:" + svv);
            double wSum = 0, eSum = 0; int wN = 0, eN = 0;
            for (int x = xa; x < Math.min(xa + nMw, xb + 1); x++) { wSum += Math.abs(vo[yy * NXF + x]); wN++; }
            for (int x = Math.max(xa, xb - nMw + 1); x <= xb; x++) { eSum += Math.abs(vo[yy * NXF + x]); eN++; }
            int cLo = xa + (int) (span * 0.40), cHi = xa + (int) (span * 0.60);
            double cSum = 0; int cN = 0;
            for (int x = cLo; x <= cHi; x++) { cSum += Math.abs(vo[yy * NXF + x]); cN++; }
            double mW = wSum / Math.max(1, wN), mE = eSum / Math.max(1, eN), mC = cSum / Math.max(1, cN);
            say(String.format(L, "    西带|v|均=%.4f (%.2f mm/s)  东带=%.4f  内区=%.4f  ⇒ 西/东=%.2f  西/内=%.2f",
                mW, mW * 1000, mE, mC, mW / Math.max(1e-12, mE), mW / Math.max(1e-12, mC)));
            int xpk = xa; double vpk = 0;
            for (int x = xa; x <= xb; x++) if (Math.abs(vo[yy * NXF + x]) > vpk) { vpk = Math.abs(vo[yy * NXF + x]); xpk = x; }
            int xz = -1;
            for (int x = xpk; x <= xb; x++) if (vo[yy * NXF + x] * vo[yy * NXF + xpk] <= 0) { xz = x; break; }
            int xe1 = -1;
            for (int x = xpk; x <= xb; x++) if (Math.abs(vo[yy * NXF + x]) <= vpk / Math.E) { xe1 = x; break; }
            say(String.format(L, "    |v|峰=%.2f mm/s @ 离西墙 %.0f km   e折宽 %s km   首次过零 @ %s km",
                vpk * 1000, (xpk - xa) * DXF / 1000,
                xe1 < 0 ? "-" : f2((xe1 - xpk) * DXF / 1000), xz < 0 ? "-" : f2((xz - xpk) * DXF / 1000)));
            double psi = 0, psiMax = 0;
            for (int x = xa; x <= xb; x++) { psi += vo[yy * NXF + x] * DXF; if (Math.abs(psi) > Math.abs(psiMax)) psiMax = psi; }
            say(String.format(L, "    沿行积分 ψ_max=%.0f m²/s   解析预估 %.0f m²/s", psiMax, Lx * Math.abs(curl) / (BarotropicGyre.RHO_WATER * hMix * beta)));
            int xc = (xa + xb) / 2;
            double bv = beta * vo[yy * NXF + xc];
            double src = curl / (BarotropicGyre.RHO_WATER * hMix);
            say(String.format(L, "    内区 Sverdrup 残差 @x=%.0fkm：|βv−curl/(ρ0H)| / |curl/(ρ0H)| = %.4f",
                xc * DXF / 1000, Math.abs(bv - src) / Math.max(1e-30, Math.abs(src))));
        }
    }

    static int count(boolean[] land) { int k = 0; for (boolean b : land) if (!b) k++; return k; }
}
