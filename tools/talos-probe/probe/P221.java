package probe;

import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.BarotropicGyre;
import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.GlobalCirculation;
import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.RelaxedClimate;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.NoiseContinentGrid;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.PolarZone;

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
    // 2026-09 第四次更新（**浮冰带成海强度 1.5 → 0.20**：用户看 /talosmap land 发现 1.5 会压出一条
    //   横贯全图、0% 陆地的笔直"护城河"，拍板改成偏置。见 PolarZone.FLOE_SEA_FORCE / P240）：
    //   u 0.29645 → 0.29733、v 0.14620 → 0.14662；海格 38682 → 38445（−237，正是浮冰带里多出来的陆地）。
    //   归因：闭合盆 [2] 的 0.01882/0.03691 **仍然逐位不变** ⇒ 求解器核心一个字节没动，
    //   差异全部来自海陆掩码（+ 它带动的风场）。P169 的形变也只在 biome.* 13 条上，
    //   mountain.* 12 条与 landform.* 2 条逐位不变 —— 位移半径已被证明是"那一条纬带"。
    // 2026-09 第五次更新（**彻底停止极地地形强制**：ICE_FORCE / FLOE_SEA_FORCE / floeSeaWeight 全删，
    //   landResidual 变成纯噪声阈值判定。见 PolarZone 类注释、P240、P220 的 U13）：
    //   u 0.29733 → 0.29852、v 0.14662 → 0.14721；海格 38445 → 38167。
    //   归因：闭合盆 [2] 的 0.01882/0.03691 **第四次逐位不变** ⇒ 求解器核心一个字节没动；
    //   差异全部来自海陆掩码（极地带恢复自然：陆占比 32.3% vs 外侧 29.3%，只差 3.0pp）。
    //   P169 形变仍是 biome.* 13 条，mountain.* / landform.* 14 条逐位不变。
    // 2026-09 第六次更新（**加回一条极地水道**：20km 基准宽、向赤道单侧摆 40km，墙外移到
    //   (0.72,0.76] 为它让位。见 PolarZone 的水道一节、P240 的三条断言、P220 的 U13）：
    //   u 0.29852 → 0.28806、v 0.14721 → 0.14205；海格 38167 → 40972（水道 + 墙外移带来的水）。
    //   归因：闭合盆 [2] 的 0.01882/0.03691 **第五次逐位不变** ⇒ 求解器核心仍然一个字节没动。
    //   P169 形变仍是 biome.* 13 条，mountain.*/landform.* 14 条逐位不变。
    //   附带收益：P235 的负对照从 0/0 变成 3089/3089 —— 地形上的水现在每一列都能绕进极地海，
    //   于是"墙是唯一在挡水的东西"从推测变成了实测。
    // 2026-09 第七次更新（当时把规定急流从浮冰带搬进水道；**该急流后来连同沿岸环流一起删除**，见第十次），
    //   需要 x 才能算，剖面跟着水道一起扭动 ⇒ 沿 X 处处非零 = 真正连续的环极流）：
    //   u 0.28806 → 0.28804、v 0.14205 → 0.14202（只动了第 5 位）；海格 40972 不变（**没改地形**）。
    //   闭合盆 [2] 的 0.01882/0.03691 **第六次逐位不变**。
    //   P169 形变仍是 biome.* 13 条 —— 注意这次**陆海一个字节都没改**，biome 动是因为
    //   急流搬了海温（advectSst），群系读的是气候。mountain.*/landform.* 14 条不动。
    // 2026-09 第八次更新（**加沿岸环流**：墙内、海岸距离 30~∞km 上叠一层
    //   F = f(d)·(d_z, −d_x)，严格无散度（P235 的 [3d] 实测 max|∇·F|=1.6e-10））：
    //   u 0.28804 → 0.28802、v 0.14202 不变；风 0.894579 → 0.894672。
    //   注意 [1]/[1b] 量的是**求解器自己的解**（P221 直接调 BarotropicGyre.solve），
    //   所以规定的叠加场不直接进这两行 —— 它们动是因为叠加场搬了海温 ⇒ 气压 ⇒ 风。
    //   闭合盆 [2] 的 0.01882/0.03691 **第七次逐位不变**。
    // 2026-09 第九次更新（**水道收窄 + 墙归位**：20km/单侧40km → **10km/单侧5km**，
    //   墙 (0.72,0.76] → **(0.82,0.86]**。理由是"被隔离的极地海盆"从 14% 涨到 24%、
    //   且墙与冷带错开 50km 把一圈不冷的洋面也封了进去）：
    //   u 0.28802 → 0.29326、v 0.14202 → 0.14460；海格 40972 → 39522（水道窄了 ⇒ 凿得少）。
    //   [1c] 墙内海格 8112 → **4362**（海盆缩回）、墙内 RMS u 0.00162 → **0.00063**（更静）。
    //   闭合盆 [2] 的 0.01882/0.03691 **第八次逐位不变**。
    // 2026-09 第十次更新（**最终定案：删水道 + 删全部规定洋流**）：
    //   地形恢复纯噪声（landResidual 就是阈值判定），PolarZone 只剩墙/冷带/海冰。
    //   u 0.29326 → 0.29842、v 0.14460 → 0.14715；海格 39522 → 38167（不再凿水道）。
    //   闭合盆 [2] 的 0.01882/0.03691 **第九次逐位不变**。
    //   新增 [1d]：墙内风应力旋度 + Sverdrup 预期 + δ_M ⇒ **归因「有力但被摩擦吃掉」**。
    static final double GOLD_PROD_U = 0.29842, GOLD_PROD_V = 0.14715;
    /** 风场指纹（同一批海格上的 RMS，用于把"海洋变了"与"风也变了"分开归因）。 */
    static final double GOLD_WIND_U = 0.923688, GOLD_WIND_V = 0.234731;
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

        // ---- 1c) 墙内求解器流速：决定"沿岸方向场"能不能叠上去而不打架 ----
        // 若墙内已经有 ~0.2 m/s 的算出来的流，再叠一条 0.35 的规定流会得到方向混乱的和；
        // 若只有 ~0.002，规定流就占绝对主导，可以放心叠。
        double wu2 = 0, wv2 = 0; int wn2 = 0; double wmax = 0;
        for (int iy = 0; iy < NY; iy++) {
            int z = (iy - HALO_Z) * CELL_Z;
            if (PolarZone.rawBand(z) <= PolarZone.WALL_INNER) continue;
            for (int ix = 0; ix < NX; ix++) {
                int i = iy * NX + ix;
                if (box.land[i]) continue;
                wu2 += uo[i] * uo[i]; wv2 += vo[i] * vo[i]; wn2++;
                double a = Math.abs(uo[i]);
                if (a > wmax) wmax = a;
            }
        }
        say(String.format("  [1c] 墙内(>%.2f)海格 %6d | RMS u=%.5f v=%.5f | max|u|=%.5f",
            PolarZone.WALL_INNER, wn2, Math.sqrt(wu2 / Math.max(1, wn2)),
            Math.sqrt(wv2 / Math.max(1, wn2)), wmax));
        say("      用途: ①>0.15 ⇒ 规定流会与算出来的流打架，沿岸方案要重新评估；"
            + "②<0.01 ⇒ 极地海盆在求解器里近乎静水，规定流占绝对主导");
        boolean okQuiet = Math.sqrt(wu2 / Math.max(1, wn2)) < 0.05;
        say("      用途: 极地洋流现在**完全由求解器算出**（规定流已全删），所以这一行就是极地洋流的全部");
        say("      判据: 墙内 RMS u < 0.05（记录用；规定流删掉之后这里没有对照物了） -> "
            + (okQuiet ? "PASS" : "FAIL"));
        ok &= okQuiet;

        // ---- 1d) 归因：墙内是"没力"还是"被摩擦吃掉"？ ----
        // 量两件事：① 墙内的风应力旋度（驱动力）；② 按 Sverdrup 平衡反推它"应该"驱动出多大的流。
        //   v_sverdrup = curl(τ) / (ρ·H·β)
        // 若 ② 远大于实测 ⇒ 是摩擦（δ_M=(A_H/β)^(1/3)≈64km ≈ 海盆宽度）把它掐死了；
        // 若 ② 本身就很小 ⇒ 是压根没有驱动力。这两者的结论完全不同。
        double curlMax = 0, curlSum = 0;
        int curlN = 0;
        for (int iy = 1; iy + 1 < NY; iy++) {
            int z = (iy - HALO_Z) * CELL_Z;
            if (PolarZone.rawBand(z) <= PolarZone.WALL_INNER) continue;
            for (int ix = 0; ix < NX; ix++) {
                int i = iy * NX + ix;
                if (box.land[i]) continue;
                // 纬向风为主 ⇒ curl(τ) ≈ −∂τ_x/∂z；τ_x = ρ_a·C_D·|W|·W_x（W 为归一化风速×WIND_MS）
                double curl = -(tauXAt(box.uW, box.vW, ix, iy + 1, NX)
                              - tauXAt(box.uW, box.vW, ix, iy - 1, NX)) / (2.0 * CELL_Z);
                double a = Math.abs(curl);
                curlSum += a;
                curlN++;
                if (a > curlMax) curlMax = a;
            }
        }
        double curlMean = curlN > 0 ? curlSum / curlN : 0;
        // β 取极区代表纬度（bandD 0.93 ≈ 83.7°）：β = 2Ω·cos φ·(π/2)·(2/Z_CYCLE)
        double betaRef = 2.0 * BarotropicGyre.OMEGA * Math.cos(0.93 * Math.PI / 2.0)
            * (Math.PI / 2.0) * 2.0 / GlobalCirculation.Z_CYCLE;
        double vSverdrup = curlMean / (BarotropicGyre.RHO_WATER * BarotropicGyre.H_MIXED * betaRef);
        // δ_M = (A_H/β)^(1/3)：摩擦边界层厚度。海盆经向宽度 ~140km ⇒ 若 δ_M 与它同量级，整盆都在摩擦层里。
        double deltaM = Math.cbrt(BarotropicGyre.A_H / betaRef);
        say(String.format("  [1d] 墙内风应力旋度：均值 %.3e、峰值 %.3e Pa/m（%d 个海格）",
            curlMean, curlMax, curlN));
        say(String.format("       Sverdrup 预期 v = curl/(ρHβ) = %.5f m/s（β≈%.2e）；实测 RMS u=%.5f",
            vSverdrup, betaRef, Math.sqrt(wu2 / Math.max(1, wn2))));
        say(String.format("       摩擦层 δ_M = (A_H/β)^(1/3) = %.0f km；极地海盆经向宽度 ≈ 140 km ⇒ %.1f 个 δ_M",
            deltaM / 1000.0, 140_000.0 / deltaM));
        say(String.format("       ⇒ 判定：%s",
            vSverdrup > 10 * Math.sqrt(wu2 / Math.max(1, wn2))
                ? "**有力但被摩擦吃掉**（Sverdrup 预期比实测大一个量级以上 ⇒ 是 δ_M 问题，不是没风）"
                : "驱动力本身就弱（Sverdrup 预期与实测同量级 ⇒ 极地风应力旋度太小）"));

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

    /** 风应力 τ_x = ρ_a·C_D·|W|·W_x，W 为归一化风速 × WIND_MS。 */
    static double tauXAt(double[] uW, double[] vW, int ix, int iy, int nx) {
        int i = iy * nx + ix;
        double u = uW[i], v = vW[i];
        double sp = Math.hypot(u, v) * BarotropicGyre.WIND_MS;
        return BarotropicGyre.RHO_AIR * BarotropicGyre.C_D * sp * u * BarotropicGyre.WIND_MS;
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
