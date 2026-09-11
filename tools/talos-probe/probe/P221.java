package probe;

import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.BarotropicGyre;
import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.GlobalCirculation;
import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.RelaxedClimate;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.NoiseContinentGrid;

import java.io.File;
import java.io.PrintStream;

/**
 * P221：**海洋层的常态指纹**（Z 环绕定案后的版本；替代已归档的 P210）。
 *
 * <h3>为什么需要它</h3>
 * P210 是一次性的"只改一个变量"受控实验：它进程内切 {@code BarotropicGyre.WRAP_Y} 的 true/false。
 * 那次定案把开关删了（T4a 现在断言"该字段不得存在"）⇒ P210 无法再编译，已归档为
 * {@code _OBSOLETE_P210.java.txt}。但它测出来的数字**必须继续被盯着**，否则以后没人知道
 * 洋流是不是又漂回去了。本探针就是那批数字的**常驻**版本。
 *
 * <h3>三条判据</h3>
 * <ol>
 *   <li><b>生产窗口</b>（tileX=0，真实海陆 + 真实风/f/β）：RMS u/v 必须与归档实验的
 *       {@code false} 那一行一致 —— 那是"clamp 实现"的定义值。
 *       归档值：{@code u=0.25279 v=0.12310 u/v=2.05}（开关时代 {@code true} 是 {@code 0.22087/0.05545/3.98}）。</li>
 *   <li><b>闭合盆负对照</b>（海区上下被陆封死、碰不到 Z 接缝）：必须与归档值**逐位一致**
 *       （{@code u=0.01882 v=0.03691}）。这一条同时干两件事：证明 clamp 路径与实验测的那份代码
 *       是同一份；证明这次改动的**影响只落在会碰到接缝的海区**（封闭海盆两边当年就逐位相同）。</li>
 *   <li><b>确定性</b>：同一输入连续解两次必须逐位相同（守批次 1 修掉的"跨调用热启动"类回归）。</li>
 * </ol>
 *
 * 用法：runprobe4.bat P221   输出：p221_report.txt；退出码 0/1。预算约 1 分钟。
 */
public class P221 {

    static final int SEED = 1022228679;
    static final int TILE_X = 100_000, ZC = GlobalCirculation.Z_CYCLE;
    static final int CELL_X = 1250, CELL_Z = 5000, HALO_Z = 20, NY = 240, NX = 240;

    // ---- 定义值（%.5f 精度）----
    //
    // [1] 生产窗口：**不是**归档 P210 的 false 行，两者不是同一个东西，别混：
    //   · P210 的 false 行（0.25279/0.12310）= 在【旧风场】上只切求解器的 Z 模板 —— 中间态。
    //   · 本值（0.27766/0.14153）= 定案后的**真实生产状态**：连气候窗口自己的流场
    //     （RelaxedClimate.updateFlow 也调这个求解器）一起换成了 clamp ⇒ 风场本身也变了。
    //   排除法证据：两次测量的 land 完全相同（海格 35042）、f/β 由本探针用同一段代码现算，
    //   唯一剩下的自由输入就是 sampleWind 给出的 uW/vW ⇒ 差异只能来自风场。
    //   另一条独立证据：P169 的 biome.* 13 行（读的是 ClimateCoords←风/海温）全部改变，
    //   而 mountain.*/landform.* 14 行逐位不变、P168/P176 逐位不变。
    // 2026-09 第二次更新（MACRO 5000 → 10000，让自旋真正收敛，见 P222）：
    //   u 0.27766 → 0.31143、v 0.14153 → 0.15358（+8.5%）、u/v 1.96 → 2.03。
    //   变化来源是"自旋不再被 5000 步截断"，不是求解器换了实现 —— 闭合盆 [2] 的
    //   0.01882/0.03691 在两次改动后都**逐位未变**就是证据。
    // 2026-09 第三次更新（**极地定案**：冰盖 0.82→0.96 + 浮冰带强制成海 + 极地冷带 + 固定急流）：
    //   u 0.31143 → 0.29645、v 0.15358 → 0.14620（−4.8% / −4.8%）；海格 35042 → 38682。
    //   归因链（这条改动**故意**动了输入，不是求解器漂移）：
    //     · 闭合盆 [2] 的 0.01882/0.03691 **逐位未变** ⇒ clamp 路径与求解器核心一个字节没动；
    //     · 风场指纹同时从 0.950836/0.241717 掉到 0.915341/0.232663 ⇒ 差异来自**输入场**：
    //       极地冷带经 ThermalForcing 进 seaTeq → 进 updateP 的 SST_P_GAIN → 进风场；
    //     · 海格数 +3640 ⇒ 冰盖缩小 + 浮冰带强制成海直接改了海陆掩码。
    //   逐位对照见 P235 [5]（那里还量了虚拟墙单独的贡献：ΔRMS ≈ −3.4e-6，远小于本容差）。
    static final double GOLD_PROD_U = 0.29645, GOLD_PROD_V = 0.14620;
    /** 风场指纹（同一批海格上的 RMS，用于把"海洋变了"与"风也变了"分开归因）。 */
    static final double GOLD_WIND_U = 0.915341, GOLD_WIND_V = 0.232663;
    static final double GOLD_BOX_U = 0.01882, GOLD_BOX_V = 0.03691;
    static final double TOL = 1e-4;

    static File ROOT = new File("K:\\moder\\EyeOfHarmonyBuffer");
    static PrintStream rep;

    public static void main(String[] args) throws Exception {
        RelaxedClimate.PREHEAT = false;
        rep = new PrintStream(new File(ROOT, "build\\eoh_probe\\mtn\\p221_report.txt"), "UTF-8");
        boolean ok = true;

        say("===== P221：海洋层常态指纹（Z 不环绕已定案）=====");
        say("  SEED=" + SEED + "  网格 " + NX + "x" + NY + " @ " + CELL_X + "/" + CELL_Z
            + "  haloZ=" + HALO_Z + "  Z_CYCLE=" + ZC);
        say("  归档对照（P210，已归档）：生产 true=0.22087/0.05545(3.98) → false=0.25279/0.12310(2.05)；"
            + "闭合盆 两侧均 0.01882/0.03691");

        // ---- 1) 生产窗口 ----
        say("");
        RealBox box = new RealBox(0);
        box.load();
        double[] uo = new double[NX * NY], vo = new double[NX * NY];
        BarotropicGyre.solve(NX, NY, CELL_X, CELL_Z, box.land, box.uW, box.vW,
            box.fRow, box.betaRow, uo, vo);
        double[] s = stats(box.land, uo, vo);
        say(String.format("  [1] 生产窗口 tileX=0（clamp 实现）  海格%6d | RMS u=%.5f v=%.5f u/v=%6.2f",
            (int) s[3], s[0], s[1], s[0] / s[1]));
        boolean ok1 = Math.abs(s[0] - GOLD_PROD_U) <= TOL && Math.abs(s[1] - GOLD_PROD_V) <= TOL;
        say("      判据: 与定义值一致（u=" + GOLD_PROD_U + " v=" + GOLD_PROD_V + " ±" + TOL + "；MACRO=10000 收敛态） -> "
            + (ok1 ? "PASS" : "FAIL"));
        if (!ok1) {
            say(String.format("      偏差: Δu=%+.5f Δv=%+.5f", s[0] - GOLD_PROD_U, s[1] - GOLD_PROD_V));
        }
        ok &= ok1;

        // ---- 1b) 风场指纹（归因用：海洋变了还是风也变了）----
        double wu = 0, wv = 0;
        int wn = 0;
        for (int i = 0; i < box.land.length; i++) {
            if (box.land[i]) continue;
            wu += box.uW[i] * box.uW[i];
            wv += box.vW[i] * box.vW[i];
            wn++;
        }
        double wru = Math.sqrt(wu / wn), wrv = Math.sqrt(wv / wn);
        say(String.format("  [1b] 风场指纹（同批海格）           海格%6d | RMS windU=%.6f windV=%.6f",
            wn, wru, wrv));
        boolean okWind = Math.abs(wru - GOLD_WIND_U) <= TOL && Math.abs(wrv - GOLD_WIND_V) <= TOL;
        say("      判据: 与定义值一致（windU=" + GOLD_WIND_U + " windV=" + GOLD_WIND_V + " ±" + TOL + "） -> "
            + (okWind ? "PASS" : "FAIL"));
        say("      用途: 下次海洋数字变了，先用这一行判断是风场动了还是求解器动了");
        ok &= okWind;

        // ---- 2) 闭合盆负对照 ----
        say("");
        boolean[] basin = new boolean[NX * NY];
        for (int y = 0; y < NY; y++) {
            for (int x = 0; x < NX; x++) {
                basin[y * NX + x] = !(x >= 40 && x < 200 && y >= 20 && y < 220);
            }
        }
        double[] bu = new double[NX * NY], bv = new double[NX * NY];
        double[] fRow = new double[NY], betaRow = new double[NY];
        double[] uw = new double[NX * NY], vw = new double[NX * NY];
        for (int y = 0; y < NY; y++) {
            int z = (y - HALO_Z) * CELL_Z;
            double b = GlobalCirculation.bandD(z);
            double uu = Math.cos(Math.PI * b);
            for (int x = 0; x < NX; x++) { uw[y * NX + x] = uu; vw[y * NX + x] = 0; }
        }
        fillRow(fRow, betaRow);
        BarotropicGyre.solve(NX, NY, CELL_X, CELL_Z, basin, uw, vw, fRow, betaRow, bu, bv);
        double[] sb = stats(basin, bu, bv);
        say(String.format("  [2] 闭合盆 列[40,200) x 行[20,220)      海格%6d | RMS u=%.5f v=%.5f",
            (int) sb[3], sb[0], sb[1]));
        boolean ok2 = Math.abs(sb[0] - GOLD_BOX_U) <= TOL && Math.abs(sb[1] - GOLD_BOX_V) <= TOL;
        say("      判据: 与归档值逐位一致（0.01882/0.03691 ±" + TOL + "）—— 证明 clamp 路径")
        ;
        say("            与受控实验测的是同一份代码，且改动只落在会碰接缝的海区 -> "
            + (ok2 ? "PASS" : "FAIL"));
        if (!ok2) {
            say(String.format("      偏差: Δu=%+.5f Δv=%+.5f", sb[0] - GOLD_BOX_U, sb[1] - GOLD_BOX_V));
        }
        ok &= ok2;

        // ---- 3) 确定性 ----
        say("");
        double[] u2 = new double[NX * NY], v2 = new double[NX * NY];
        BarotropicGyre.solve(NX, NY, CELL_X, CELL_Z, basin, uw, vw, fRow, betaRow, u2, v2);
        int diff = 0;
        for (int i = 0; i < u2.length; i++) {
            if (Double.doubleToRawLongBits(bu[i]) != Double.doubleToRawLongBits(u2[i])
                || Double.doubleToRawLongBits(bv[i]) != Double.doubleToRawLongBits(v2[i])) {
                diff++;
            }
        }
        say("  [3] 确定性：闭合盆连解两次，逐位不同的格 = " + diff + "/" + u2.length
            + " -> " + (diff == 0 ? "PASS" : "FAIL"));
        ok &= diff == 0;

        say("");
        say("OCEAN_FINGERPRINT_STATUS=" + (ok ? "PASS" : "FAIL"));
        rep.flush();
        rep.close();
        System.exit(ok ? 0 : 1);
    }

    static void fillRow(double[] fRow, double[] betaRow) {
        int nyLat = ZC / CELL_Z;
        for (int iy = 0; iy < NY; iy++) {
            int v = (iy - HALO_Z) % nyLat;
            if (v < 0) v += nyLat;
            int z = v * CELL_Z;
            double bb = GlobalCirculation.bandD(z);
            double ff = Math.sin(GlobalCirculation.latRad(z));
            fRow[iy] = Math.abs(ff) < 1e-4 ? 0.0 : ff;
            betaRow[iy] = 2.0 * BarotropicGyre.OMEGA * Math.cos(bb * Math.PI / 2.0)
                * (Math.PI / 2.0) * 2.0 / ZC;
        }
    }

    /** 返回 {RMS u, RMS v, u/v, 海格数}。 */
    static double[] stats(boolean[] land, double[] uo, double[] vo) {
        double su = 0, sv = 0;
        int n = 0;
        for (int i = 0; i < land.length; i++) {
            if (land[i]) continue;
            su += uo[i] * uo[i];
            sv += vo[i] * vo[i];
            n++;
        }
        double ru = Math.sqrt(su / n), rv = Math.sqrt(sv / n);
        return new double[] { ru, rv, rv > 0 ? ru / rv : -1, n };
    }

    static void say(String s) {
        System.out.println("[P221] " + s);
        rep.println("[P221] " + s);
    }

    /** 生产窗口的 land/风/f/β（风按 (tileZ,tileX) 分组扫描，避免缓存抖动）。 */
    static class RealBox {
        final int originX;
        boolean[] land;
        double[] uW, vW, fRow, betaRow;

        RealBox(int tileX) {
            this.originX = tileX * TILE_X - 100_000;
        }

        void load() {
            land = new boolean[NX * NY];
            uW = new double[NX * NY];
            vW = new double[NX * NY];
            for (int iy = 0; iy < NY; iy++) {
                int z = (iy - HALO_Z) * CELL_Z;
                for (int ix = 0; ix < NX; ix++) {
                    land[iy * NX + ix] = NoiseContinentGrid.isLand(originX + ix * CELL_X, z, SEED);
                }
            }
            for (int tz = -1; tz <= 1; tz++) {
                for (int iy = 0; iy < NY; iy++) {
                    int z = (iy - HALO_Z) * CELL_Z;
                    if (Math.floorDiv(z, ZC) != tz) continue;
                    for (int tx = -1; tx <= 1; tx++) {
                        for (int ix = 0; ix < NX; ix++) {
                            int x = originX + ix * CELL_X;
                            if (Math.floorDiv(x, TILE_X) != tx) continue;
                            double[] w = RelaxedClimate.sampleWind(x, z, SEED);
                            uW[iy * NX + ix] = w[0];
                            vW[iy * NX + ix] = w[1];
                        }
                    }
                }
            }
            fRow = new double[NY];
            betaRow = new double[NY];
            fillRow(fRow, betaRow);
        }
    }
}
