package probe;

import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.GlobalCirculation;
import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.RelaxedClimate;
import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.ThermalForcing;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.NoiseContinentGrid;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.PolarZone;

import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Locale;

/**
 * P241（2026-09 新增，只读诊断）：**冷源可达性 + 温度签名深度**。
 *
 * <p>回答一个此前从没被量过的问题：沿真实稳态流场回溯，中纬度的水到底能来自多北（多高 bandD）？
 * 这份「来路」又能在温度场上刻出多深的异常？
 *
 * <h3>为什么是这两个量</h3>
 * 「为什么几乎没有冷流」在这套模型里拆成两个互相独立的量：
 * <ol>
 *   <li><b>来路</b>：水有没有经向搬运。纬向流沿等温线走 = 白搬；</li>
 *   <li><b>签名深度</b>：advectSst 把它记得多深。即使来路是一条完美的寒流，算子分辨率不够也画不出来。</li>
 * </ol>
 * 第 2 条可以直接算：sig = (teq_终点 − teq_起点) × (1 − exp(−(行程+dt)/relaxL))。
 * 现行算子在 0.30 m/s 下行程只有 18 km、f=0.268，而中纬纬向温度梯度约 4.4e-6/block
 * ⇒ 签名深度约 0.02（温标满量程 2.0 的 1%）—— 这就是「画不出来」的定量版本。
 *
 * <h3>三个算子（同一批起点、同一张采样场）</h3>
 * <pre>
 *   [1] 现行算子：与 RelaxedClimate.advectSst 同式（14 步 × dt=1500、REF=0.05）
 *   [2] 弧长算子：沿流线回溯固定弧长 L —— 这是「放大射程」改造的上限，与流速无关
 *   [3] 反向控制：从墙外副极地【正向】追踪，看冷水有没有路走向赤道
 * </pre>
 *
 * <h3>已知近似（写在前面，免得把近似当结论）</h3>
 * <ul>
 *   <li>[1] 没有乘 d.fade（近岸渐隐）—— 那个数组在包外拿不到，所以 [1] 的行程是**上界**；</li>
 *   <li>陆判据用 landResidual(x,z) 在 5 km 节点上采样，与求解器粗格同分辨率但非逐格相同；
 *   <li>z 在 [0,1M) 内环接（气候确实以 1M 为周期）。陆地在 z 上不周期、缝在赤道，
 *       起点在 bandD 0.55~0.75，只有极端向赤道的路径会碰到；碰到就计数并报告。</li>
 * </ul>
 */
public class P241 {

    static final int SEED = 1022228679;
    static final int ZC = GlobalCirculation.Z_CYCLE;

    /** 本地采样场：x∈[0,1000km)、z∈[0,1M)。5 km 与求解器粗格 CELL_Z 同分辨率。 */
    static final int GX = 5_000, GZ = 5_000, NX = 200, NZ = 200;

    // ---- 现行算子（与 RelaxedClimate.advectSst 对齐）----
    static final double ADV_DT = 1500.0, ADV_REF = 0.05, RELAX_L = 62_500.0;
    static final int ADV_STEPS = 14;

    // ---- 弧长算子 ----
    static final double DS = 2_500.0;
    static final double[] LS = {25_000.0, 50_000.0, 100_000.0, 160_000.0, 250_000.0, 400_000.0};

    // ---- 取样口径 ----
    static final double START_LO = 0.55, START_HI = 0.75;
    static final double NEAR_LO = 0.76, NEAR_HI = 0.82;
    static final double SIG_VISIBLE = 0.10;

    static final Locale L = Locale.ROOT;
    static final File ROOT = new File("K:\\moder\\EyeOfHarmonyBuffer");
    static PrintStream rep;
    static long T0;

    static float[] fu, fv, lnd;

    public static void main(String[] args) throws Exception {
        RelaxedClimate.PREHEAT = false;
        rep = new PrintStream(new File(ROOT, "build\\eoh_probe\\mtn\\p241_report.txt"), "UTF-8");
        T0 = System.nanoTime();
        say("口径：GX=" + GX + " GZ=" + GZ + " NX=" + NX + " NZ=" + NZ
            + " 冷带 COLD_BAND=" + f3(PolarZone.COLD_BAND)
            + " 墙=" + f3(PolarZone.WALL_OUTER) + "~" + f3(PolarZone.WALL_INNER)
            + " relaxL=" + (long) RELAX_L + " REF=" + ADV_REF + " 起点带=[" + START_LO + "," + START_HI + "]");
        sample();
        census();
        currentOperator();
        arcOperator();
        forwardControl();
        spatialAttribution();
        say("总耗时 " + sec() + "s");
        rep.close();
    }

    static void say(String s) { System.out.println("[P241] " + s); rep.println("[P241] " + s); }
    static long sec() { return (System.nanoTime() - T0) / 1_000_000_000L; }
    static String f3(double x) { return String.format(L, "%.3f", x); }

    // ------------------------------------------------ bandD / 采样

    /** 裸 bandD（对 double z）：0=赤道、1=极点，[0,1M) 内环接。 */
    static double bd(double z) {
        double zz = z % ZC;
        if (zz < 0) zz += ZC;
        double d = Math.min(zz, ZC - zz);
        return d / (ZC / 2.0);
    }

    static int idx(int ix, int iz) { return iz * NX + ix; }

    /** 双线性：x 夹紧、z 环接。 */
    static double bil(float[] a, double x, double z) {
        double gx = x / GX, gz = z / GZ;
        int ix = (int) Math.floor(gx), iz = (int) Math.floor(gz);
        double tx = gx - ix, tz = gz - iz;
        if (ix < 0) { ix = 0; tx = 0; }
        if (ix > NX - 2) { ix = NX - 2; tx = 1; }
        int iz0 = ((iz % NZ) + NZ) % NZ, iz1 = (iz0 + 1) % NZ;
        double s00 = a[idx(ix, iz0)], s10 = a[idx(ix + 1, iz0)];
        double s01 = a[idx(ix, iz1)], s11 = a[idx(ix + 1, iz1)];
        return s00 * (1 - tx) * (1 - tz) + s10 * tx * (1 - tz) + s01 * (1 - tx) * tz + s11 * tx * tz;
    }

    static void sample() {
        fu = new float[NX * NZ]; fv = new float[NX * NZ]; lnd = new float[NX * NZ];
        for (int iz = 0; iz < NZ; iz++) {
            int z = iz * GZ;
            for (int ix = 0; ix < NX; ix++) {
                int x = ix * GX;
                boolean land = NoiseContinentGrid.landResidual(x, z, SEED) >= 0.0;
                lnd[idx(ix, iz)] = land ? 1f : 0f;
                if (!land) {
                    double[] c = RelaxedClimate.sampleCurrent(x, z, SEED);
                    fu[idx(ix, iz)] = (float) c[0];
                    fv[idx(ix, iz)] = (float) c[1];
                }
            }
        }
        say("采样完成 " + NX * NZ + " 点  t=" + sec() + "s");
    }

    // ------------------------------------------------ 场普查

    static void census() {
        say("===== [0] 场普查（整周期，含两翼） =====");
        int[] sea = new int[10], tot = new int[10];
        for (int iz = 0; iz < NZ; iz++) {
            int z = iz * GZ;
            int b = (int) Math.min(9, bd(z) * 10);
            for (int ix = 0; ix < NX; ix++) { tot[b]++; if (lnd[idx(ix, iz)] < 0.5) sea[b]++; }
        }
        say(String.format(L, "%-10s %8s %8s %8s", "bandD", "节点", "海节点", "海占比"));
        for (int b = 0; b < 10; b++) {
            say(String.format(L, "%-10s %8d %8d %7.1f%%",
                String.format(L, "%.1f-%.1f", b * 0.1, (b + 1) * 0.1), tot[b], sea[b],
                100.0 * sea[b] / Math.max(1, tot[b])));
        }
        // 若把冷带起点从现行值外推到 0.70：多少海节点会开始变冷
        int seaCold = 0, seaAll = 0; double wsum = 0; double wOld = 0;
        for (int iz = 0; iz < NZ; iz++) {
            int z = iz * GZ;
            double b = bd(z);
            if (b < 0.70 || b >= PolarZone.COLD_BAND) continue;
            for (int ix = 0; ix < NX; ix++) {
                if (lnd[idx(ix, iz)] >= 0.5) continue;
                int x = ix * GX;
                seaAll++;
                double be = PolarZone.band(x, z, SEED);
                wOld += PolarZone.coldWeight(be);
                double t = (be - 0.70) / (PolarZone.FLOE_BAND - 0.70);
                t = t < 0 ? 0 : (t > 1 ? 1 : t);
                wsum += t * t * (3 - 2 * t);
                seaCold++;
            }
        }
        say("bandD∈[0.70," + f3(PolarZone.COLD_BAND) + ") 的海节点 " + seaCold
            + "（占全表 " + String.format(L, "%.1f%%", 100.0 * seaCold / Math.max(1, NX * NZ)) + "）"
            + "；这些点现行平均 coldWeight=" + f3(seaCold > 0 ? wOld / seaCold : 0)
            + "，若把 COLD_BAND 推到 0.70 则为 " + f3(seaCold > 0 ? wsum / seaCold : 0));
    }

    // ------------------------------------------------ 起点集合

    static int[] starts(double lo, double hi) {
        int n = 0;
        for (int iz = 0; iz < NZ; iz++) {
            double b = bd(iz * GZ);
            if (b < lo || b > hi) continue;
            for (int ix = 0; ix < NX; ix++) if (lnd[idx(ix, iz)] < 0.5) n++;
        }
        int[] out = new int[n]; int k = 0;
        for (int iz = 0; iz < NZ; iz++) {
            double b = bd(iz * GZ);
            if (b < lo || b > hi) continue;
            for (int ix = 0; ix < NX; ix++) if (lnd[idx(ix, iz)] < 0.5) out[k++] = idx(ix, iz);
        }
        return out;
    }

    static int xOf(int i) { return (i % NX) * GX; }
    static int zOf(int i) { return (i / NX) * GZ; }

    static double median(double[] a, int n) {
        if (n <= 0) return Double.NaN;
        double[] c = Arrays.copyOf(a, n);
        Arrays.sort(c);
        return c[n / 2];
    }

    static double frac(boolean[] a, int n) {
        if (n <= 0) return Double.NaN;
        int k = 0; for (int i = 0; i < n; i++) if (a[i]) k++;
        return k / (double) n;
    }

    /** advectSst 里那个 f：**当地平衡温度**的权重（f→0 取上游、f→1 取当地）。 */
    static double fOf(double trav) { return 1.0 - Math.exp(-(trav + ADV_DT) / RELAX_L); }

    /**
     * 上游记忆权重 = exp(−(行程+dt)/relaxL) = 1 − f。
     *
     * <p>⚠ 2026-09 修正：本探针最初把 sig 乘成了 **f**。但 advectSst 的式子是
     * {@code sstNew = tAd + (teqSea − tAd)·f} ⇒ 相对当地平衡的异常是
     * {@code (tAd − teqSea)·(1 − f)}，系数是 **(1−f)**。用 f 会在 L=100km 处把签名放大 **4.1×**、
     * L=160km 处放大 **12.3×**（而 L=25km 处反而缩小 1.9×）。首次运行的 [2]/[5] 因此偏乐观。
     */
    static double wUp(double trav) { return Math.exp(-(trav + ADV_DT) / RELAX_L); }

    /** 一处终点相对起点的两个签名深度：meri 只取纬向平均那部分（真正的冷暖舌），full 还含摆动。 */
    static double sigMeri(double z0, double z1, double w) {
        return (ThermalForcing.zonalMeanSeaTeq(bd(z1)) - ThermalForcing.zonalMeanSeaTeq(bd(z0))) * w;
    }

    static double sigFull(int x0, int z0, double x1, double z1, double w) {
        double t0 = ThermalForcing.seaTeq(x0, z0, SEED);
        double t1 = ThermalForcing.seaTeq((int) Math.round(x1), (int) Math.round(z1) % ZC, SEED);
        return (t1 - t0) * w;
    }

    // ------------------------------------------------ [1] 现行算子

    static void currentOperator() {
        say("===== [1] 现行算子（与 advectSst 同式：14 步 × dt=1500、REF=" + ADV_REF + "） =====");
        int[] st = starts(START_LO, START_HI);
        int n = st.length;
        if (n == 0) { say("起点为空"); return; }
        double[] trav = new double[n], fA = new double[n], wA = new double[n], bdE = new double[n], sm = new double[n], sf = new double[n];
        double[] bd0 = new double[n];
        boolean[] stalled = new boolean[n], hitLand = new boolean[n], leftX = new boolean[n];
        boolean[] wrapped = new boolean[n], hitWall = new boolean[n], coldSrc = new boolean[n];
        boolean[] coldVis = new boolean[n], warmVis = new boolean[n], poleward = new boolean[n];
        for (int i = 0; i < n; i++) {
            int x0 = xOf(st[i]), z0 = zOf(st[i]);
            double px = x0, pz = z0, t = 0;
            boolean sd = false, hl = false, lx = false, wr = false, hw = false;
            for (int k = 0; k < ADV_STEPS; k++) {
                double u = bil(fu, px, pz), w = bil(fv, px, pz);
                double sp = Math.hypot(u, w);
                if (sp < 1e-9) { sd = true; break; }
                double rate = ADV_DT / (sp + ADV_REF);
                double qx = px - u * rate, qz = pz - w * rate;
                t += Math.hypot(qx - px, qz - pz);
                px = qx; pz = qz;
                // 判定必须用**未归一化**的 pz：((pz%ZC)+ZC)%ZC 在 pz 有小数部分时会因为
                // +ZC 再 %ZC 的浮点往返丢掉 1 ULP，于是 zz != pz 恒真（第一次跑实测 0.9972，是仪器 bug）。
                if (pz < 0 || pz >= ZC) wr = true;
                double zz = ((pz % ZC) + ZC) % ZC;
                if (PolarZone.isWallCell(PolarZone.rawBand((int) Math.floor(zz)))) hw = true;
                if (px < 0 || px > (NX - 1) * GX) { lx = true; break; }
                if (bil(lnd, px, zz) > 0.5) { hl = true; break; }
            }
            double zz = ((pz % ZC) + ZC) % ZC;
            bd0[i] = bd(z0); bdE[i] = bd(zz); trav[i] = t; fA[i] = fOf(t); wA[i] = wUp(t);
            sm[i] = sigMeri(z0, zz, wA[i]);
            sf[i] = sigFull(x0, z0, px, zz, wA[i]);
            stalled[i] = sd; hitLand[i] = hl; leftX[i] = lx; wrapped[i] = wr; hitWall[i] = hw;
            poleward[i] = bdE[i] > bd0[i];
            coldSrc[i] = PolarZone.band((int) Math.round(px), (int) Math.round(zz), SEED) >= PolarZone.COLD_BAND;
            coldVis[i] = sm[i] <= -SIG_VISIBLE;
            warmVis[i] = sm[i] >= SIG_VISIBLE;
        }
        say("起点海格 n=" + n + "（bandD∈[" + START_LO + "," + START_HI + "]）");
        say(String.format(L, "行程 km      : 中位 %.2f   均值 %.2f   P90 %.2f",
            median(trav, n) / 1000.0, mean(trav, n) / 1000.0, p90(trav, n) / 1000.0));
        say(String.format(L, "权重         : f(当地) 中位 %.4f   上游记忆(1-f) 中位 %.4f   relaxL=%.0f", median(fA, n), median(wA, n), RELAX_L));
        say(String.format(L, "终点 bandD   : 中位 %.4f   Δ中位 %+.4f   Δ>0 比例 %.3f",
            median(bdE, n), median(diff(bdE, bd0, n), n), frac(poleward, n)));
        say(String.format(L, "冷源(>=%.2f)比例: %.4f", PolarZone.COLD_BAND, frac(coldSrc, n)));
        say(String.format(L, "签名深度 meri: 中位 %+.4f   P(<=-%.2f)=%.4f   P(>=+%.2f)=%.4f",
            median(sm, n), SIG_VISIBLE, frac(coldVis, n), SIG_VISIBLE, frac(warmVis, n)));
        say(String.format(L, "签名深度 full: 中位 %+.4f（含 wobble 平流）", median(sf, n)));
        say(String.format(L, "终止原因     : 停滞 %.4f  撞陆 %.4f  出x界 %.4f  环接 %.4f  触墙 %.4f",
            frac(stalled, n), frac(hitLand, n), frac(leftX, n), frac(wrapped, n), frac(hitWall, n)));
        say(String.format(L, "速度尺度     : 起点|F|中位 %.4f m/s", medianAbs(st, n)));
    }

    // ------------------------------------------------ [2] 弧长算子

    static void arcOperator() {
        say("===== [2] 弧长算子（沿流线回溯固定弧长 L；与流速无关 = 放大射程的上限） =====");
        int[] st = starts(START_LO, START_HI);
        int n = st.length, M = LS.length;
        if (n == 0) { say("起点为空"); return; }
        double[][] bdE = new double[M][n], sm = new double[M][n], tr = new double[M][n];
        boolean[][] term = new boolean[M][n];
        boolean[][] wallAt = new boolean[M][n];
        double[] bd0 = new double[n];
        boolean[][] coldSrc = new boolean[M][n];
        int maxSteps = (int) (LS[M - 1] / DS);
        for (int i = 0; i < n; i++) {
            int x0 = xOf(st[i]), z0 = zOf(st[i]);
            bd0[i] = bd(z0);
            double px = x0, pz = z0, t = 0;
            int k = 0; boolean dead = false; boolean wallHit = false;
            while (true) {
                while (k < M && t >= LS[k]) {
                    double zz = ((pz % ZC) + ZC) % ZC;
                    bdE[k][i] = bd(zz); tr[k][i] = t; term[k][i] = dead;
                    sm[k][i] = sigMeri(z0, zz, wUp(t));
                    coldSrc[k][i] = PolarZone.band((int) Math.round(px), (int) Math.round(zz), SEED) >= PolarZone.COLD_BAND;
                    wallAt[k][i] = wallHit;
                    k++;
                }
                if (k >= M || dead) break;
                double u = bil(fu, px, pz), w = bil(fv, px, pz);
                double sp = Math.hypot(u, w);
                if (sp < 1e-4) { dead = true; continue; }
                px -= u / sp * DS; pz -= w / sp * DS; t += DS;
                double zz = ((pz % ZC) + ZC) % ZC;
                if (PolarZone.isWallCell(PolarZone.rawBand((int) Math.floor(zz)))) wallHit = true;
                if (px < 0 || px > (NX - 1) * GX) { dead = true; continue; }
                if (bil(lnd, px, zz) > 0.5) { dead = true; continue; }
            }
        }
        say(String.format(L, "%-8s %10s %10s %10s %10s %11s %11s %9s",
            "L km", "Δ中位", "Δ>0比例", "冷源比例", "sig中位", "P(sig<=-0.1)", "P(sig>=+0.1)", "未走完", "穿墙"));
        for (int k = 0; k < M; k++) {
            boolean[] p = new boolean[n], cs = new boolean[n], cv = new boolean[n], wv = new boolean[n], wh = new boolean[n];
            double[] d = new double[n], s = new double[n];
            for (int i = 0; i < n; i++) {
                d[i] = bdE[k][i] - bd0[i]; s[i] = sm[k][i];
                p[i] = d[i] > 0; cs[i] = coldSrc[k][i];
                cv[i] = s[i] <= -SIG_VISIBLE; wv[i] = s[i] >= SIG_VISIBLE; wh[i] = wallAt[k][i];
            }
            say(String.format(L, "%-8.0f %10.4f %10.3f %10.3f %+10.4f %11.3f %11.3f %9.3f %9.3f",
                LS[k] / 1000.0, median(d, n), frac(p, n), frac(cs, n), median(s, n),
                frac(cv, n), frac(wv, n), frac(term[k], n), frac(wh, n)));
        }
        int kk = 3;
        say("--- L=160km 按起点 bandD 细分 ---");
        say(String.format(L, "%-12s %8s %10s %10s %10s %11s", "起点bandD", "n", "Δ中位", "Δ>0比例", "sig中位", "P(sig<=-0.1)"));
        double[] lo = {0.55, 0.60, 0.65, 0.70};
        for (int q = 0; q < lo.length; q++) {
            double a = lo[q], b = q == lo.length - 1 ? START_HI + 1e-9 : lo[q + 1];
            int m = 0; double[] d2 = new double[n], s2 = new double[n]; boolean[] p2 = new boolean[n], c2 = new boolean[n];
            for (int i = 0; i < n; i++) {
                if (bd0[i] < a || bd0[i] >= b) continue;
                d2[m] = bdE[kk][i] - bd0[i]; s2[m] = sm[kk][i];
                p2[m] = d2[m] > 0; c2[m] = s2[m] <= -SIG_VISIBLE; m++;
            }
            say(String.format(L, "%-12s %8d %10.4f %10.3f %+10.4f %11.3f",
                String.format(L, "%.2f-%.2f", a, b), m, median(d2, m), frac(p2, m), median(s2, m), frac(c2, m)));
        }
    }

    // ------------------------------------------------ [3] 反向控制

    static void forwardControl() {
        say("===== [3] 反向控制：从墙外副极地【正向】追踪 160km（冷水有没有路走向赤道） =====");
        int[] st = starts(NEAR_LO, NEAR_HI);
        int n = st.length;
        if (n == 0) { say("起点为空"); return; }
        double[] bd0 = new double[n], bdE = new double[n], tr = new double[n], spd = new double[n];
        boolean[] eq = new boolean[n], reach60 = new boolean[n], deadA = new boolean[n];
        boolean[] wallHitA = new boolean[n];
        int steps = 64;
        for (int i = 0; i < n; i++) {
            int x0 = xOf(st[i]), z0 = zOf(st[i]);
            bd0[i] = bd(z0);
            double px = x0, pz = z0, t = 0; boolean dead = false;
            double spSum = 0; int spN = 0; boolean wh = false;
            for (int s = 0; s < steps; s++) {
                double u = bil(fu, px, pz), w = bil(fv, px, pz);
                double sp = Math.hypot(u, w);
                if (sp < 1e-4) { dead = true; break; }
                spSum += sp; spN++;
                px += u / sp * DS; pz += w / sp * DS; t += DS;
                double zz = ((pz % ZC) + ZC) % ZC;
                if (PolarZone.isWallCell(PolarZone.rawBand((int) Math.floor(zz)))) wh = true;
                if (px < 0 || px > (NX - 1) * GX) { dead = true; break; }
                if (bil(lnd, px, zz) > 0.5) { dead = true; break; }
            }
            double zz = ((pz % ZC) + ZC) % ZC;
            bdE[i] = bd(zz); tr[i] = t; deadA[i] = dead;
            spd[i] = spN > 0 ? spSum / spN : 0;
            wallHitA[i] = wh;
            eq[i] = bdE[i] < bd0[i]; reach60[i] = bdE[i] <= 0.60;
        }
        say("起点海格 n=" + n + "（bandD∈[" + NEAR_LO + "," + NEAR_HI + "]，紧贴墙外缘）");
        say(String.format(L, "行程 km   : 中位 %.2f（预算 160）", median(tr, n) / 1000.0));
        say(String.format(L, "ΔbandD    : 中位 %+.4f   向赤道比例 %.3f", median(diff(bdE, bd0, n), n), frac(eq, n)));
        say(String.format(L, "到达 <=0.60 比例: %.4f（0.60 是中纬带上界）", frac(reach60, n)));
        say(String.format(L, "提前终止比例: %.3f   路径触墙比例: %.4f（墙是否真的封住）", frac(deadA, n), frac(wallHitA, n)));
        double[] tm = new double[n], lam = new double[n], lmeri = new double[n];
        double sumDy = 0, sumDs = 0;
        double tau = 45 * 86400.0;
        for (int i = 0; i < n; i++) {
            tm[i] = spd[i] > 1e-9 ? tr[i] / spd[i] : 0;
            lam[i] = spd[i] * tau;
            double dy = Math.abs(bdE[i] - bd0[i]) * (ZC / 2.0);
            lmeri[i] = tr[i] > 1e-9 ? lam[i] * (dy / tr[i]) : 0;
            sumDy += dy; sumDs += tr[i];
        }
        double ratio = sumDs > 0 ? sumDy / sumDs : 0;
        say(String.format(L, "沿线均速中位: %.4f m/s   走完 160km 需要时间中位 %.1f d", median(spd, n), median(tm, n) / 86400.0));
        say(String.format(L, "λ=U·τ(τ=45d) 中位: %.1f km", median(lam, n) / 1000.0));
        say(String.format(L, "纬度/弧长 汇总比: %.4f   ⇒ 纬度衰减长度 L_meri=λ·比 中位 %.2f km", ratio, median(lmeri, n) / 1000.0));
        // 口径修正（2026-09，第一次跑到这里时印错了）：50000.0/ratio 与 median(lam) 的单位都是**block**，
        // 旧写法把一个 block 值当 km 打印、又用它除以 km 值，于是同时错了两处**同一个 1000 倍**。
        // 本次报告里记的实测值是「172 km 弧长、衰减 e^-4.86」，由同一批数据换算得到。
        double needM = 50000.0 / Math.max(1e-9, ratio);
        say(String.format(L, "要产生 50km 的纬度位移需要弧长 %.1f km；按 λ=%.1f km 衰减 e^-%.2f", needM / 1000.0, median(lam, n) / 1000.0, needM / median(lam, n)));
    }

    // ------------------------------------------------ 小工具

    static double mean(double[] a, int n) {
        if (n <= 0) return Double.NaN;
        double s = 0; for (int i = 0; i < n; i++) s += a[i];
        return s / n;
    }

    static double p90(double[] a, int n) {
        if (n <= 0) return Double.NaN;
        double[] c = Arrays.copyOf(a, n); Arrays.sort(c);
        return c[(int) (n * 0.9)];
    }

    static double[] diff(double[] a, double[] b, int n) {
        double[] r = new double[n];
        for (int i = 0; i < n; i++) r[i] = a[i] - b[i];
        return r;
    }

    static double medianAbs(int[] st, int n) {
        if (n <= 0) return Double.NaN;
        double[] a = new double[n];
        for (int i = 0; i < n; i++) a[i] = Math.hypot(fu[st[i]], fv[st[i]]);
        return median(a, n);
    }

    // ------------------------------------------------ [5] 冷签名的空间归属

    /** 到岸距离上限：与求解器窗口半宽（HALO_X=100km）同量级，取 200km 以判定「东/西哪一侧是岸」。 */
    static final double EDGE_CAP = 200_000.0;
    /** 判「贴岸」的距离，与 P195 的 EDGE_KM=50 保持一致。 */
    static final double D_EDGE = 50_000.0;
    static final String[] CN = {"东边界EB", "西边界WB", "两岸(窄)", "内区"};

    static int[] startsIn(double lo, double hi, int ixLo, int ixHi) {
        int n = 0;
        for (int iz = 0; iz < NZ; iz++) {
            double b = bd(iz * GZ);
            if (b < lo || b > hi) continue;
            for (int ix = ixLo; ix <= ixHi; ix++) if (lnd[idx(ix, iz)] < 0.5) n++;
        }
        int[] out = new int[n]; int k = 0;
        for (int iz = 0; iz < NZ; iz++) {
            double b = bd(iz * GZ);
            if (b < lo || b > hi) continue;
            for (int ix = ixLo; ix <= ixHi; ix++) if (lnd[idx(ix, iz)] < 0.5) out[k++] = idx(ix, iz);
        }
        return out;
    }

    /** 从 (ix,iz) 向东找第一块陆的距离（block）；到 EDGE_CAP 或网格边仍无 ⇒ EDGE_CAP。 */
    static double coastEast(int ix, int iz) {
        for (int k = 1; k * GX <= EDGE_CAP; k++) {
            int j = ix + k;
            if (j >= NX) return EDGE_CAP;
            if (lnd[idx(j, iz)] > 0.5) return k * (double) GX;
        }
        return EDGE_CAP;
    }

    static double coastWest(int ix, int iz) {
        for (int k = 1; k * GX <= EDGE_CAP; k++) {
            int j = ix - k;
            if (j < 0) return EDGE_CAP;
            if (lnd[idx(j, iz)] > 0.5) return k * (double) GX;
        }
        return EDGE_CAP;
    }

    static int cls(double dE, double dW) {
        boolean e = dE <= D_EDGE, w = dW <= D_EDGE;
        if (e && w) return 2;
        if (e) return 0;
        if (w) return 1;
        return 3;
    }

    /** 沿流线回溯至多 L 的弧长；返回终点（环接后）的 z，并把「走过的弧长 / 是否提前终止」写进 out。 */
    static double traceBack(int x0, int z0, double L, double[] out) {
        double px = x0, pz = z0, t = 0; boolean dead = false;
        int maxSteps = (int) (L / DS);
        for (int s = 0; s < maxSteps; s++) {
            double u = bil(fu, px, pz), w = bil(fv, px, pz);
            double sp = Math.hypot(u, w);
            if (sp < 1e-4) { dead = true; break; }
            px -= u / sp * DS; pz -= w / sp * DS; t += DS;
            double zz = ((pz % ZC) + ZC) % ZC;
            if (px < 0 || px > (NX - 1) * GX) { dead = true; break; }
            if (bil(lnd, px, zz) > 0.5) { dead = true; break; }
        }
        out[0] = t; out[1] = dead ? 1 : 0;
        return ((pz % ZC) + ZC) % ZC;
    }

    /**
     * [5] 把 [2] 里那 16.7% 的「可见冷签名」按**海岸朝向**归类，回答：
     * 「这些冷点是不是聚在东边界（加州流/加那利流式）上？」—— 这决定「只做 L1 够不够」。
     *
     * <p>仪器自带对照：同一张表也印暖签名的同类占比。若冷/暖两列的分类占比与基准占比一致，
     * 说明签名只是噪声、与海岸无关；若冷签名在 EB 上显著富集，才是真的东边界寒流。
     */
    static void spatialAttribution() {
        say("===== [5] 冷签名的空间归属：冷点到底长在哪（副热带带 vs 副极地带） =====");
        int ixLo = (int) (EDGE_CAP / GX), ixHi = NX - 1 - ixLo;
        say("口径：起点 bandD∈[" + START_LO + "," + START_HI + "]、且 x∈[" + (ixLo * GX / 1000)
            + "," + (ixHi * GX / 1000) + "]km（保证 ±" + (int) (EDGE_CAP / 1000) + "km 扫描不触网格边）。");
        say("分类：EB=东侧≤" + (int) (D_EDGE / 1000) + "km 有陆且西侧无；WB=反之；两岸=两侧都有；内区=两侧都无。");
        say("签名系数已修正为上游记忆 (1-f)=exp(-(行程+dt)/relaxL)（见 wUp 的说明）。");
        attribution(0.25, 0.45, "副热带带");
        attribution(0.55, 0.75, "副极地带");
    }

    /** 单个纬度带的空间归属表（EB / WB / 两岸 / 内区 × 冷 / 暖签名 + 富集倍数）。 */
    static void attribution(double bLo, double bHi, String tag) {
        int ixLo = (int) (EDGE_CAP / GX), ixHi = NX - 1 - ixLo;
        say("----- " + tag + "：bandD∈[" + bLo + "," + bHi + "] -----");
        int[] st = startsIn(bLo, bHi, ixLo, ixHi);
        int n = st.length;
        if (n == 0) { say("起点为空"); return; }
        int[] cOf = new int[n];
        double[] dE = new double[n], dW = new double[n];
        int[] base = new int[4];
        for (int i = 0; i < n; i++) {
            int ix = st[i] % NX, iz = st[i] / NX;
            dE[i] = coastEast(ix, iz); dW[i] = coastWest(ix, iz);
            cOf[i] = cls(dE[i], dW[i]);
            base[cOf[i]]++;
        }
        for (double Lv : new double[]{100_000.0, 160_000.0}) {
            int[] nn = new int[4], nc = new int[4], nw = new int[4];
            double[] dS = new double[4];
            int coldTot = 0, warmTot = 0;
            int[] coldByCls = new int[4], warmByCls = new int[4];
            double[] coldDE = new double[n], coldDW = new double[n];
            int coldM = 0;
            for (int i = 0; i < n; i++) {
                int ix = st[i] % NX, iz = st[i] / NX;
                double[] out = new double[2];
                double zz = traceBack(ix * GX, iz * GZ, Lv, out);
                double sg = sigMeri(iz * GZ, zz, wUp(out[0]));
                int c = cOf[i];
                nn[c]++; dS[c] += bd(zz) - bd(iz * GZ);
                if (sg <= -SIG_VISIBLE) { nc[c]++; coldTot++; coldByCls[c]++; coldDE[coldM] = dE[i]; coldDW[coldM] = dW[i]; coldM++; }
                if (sg >= SIG_VISIBLE) { nw[c]++; warmTot++; warmByCls[c]++; }
            }
            double pAll = coldTot / (double) n;
            say("----- L=" + (int) (Lv / 1000) + "km（n=" + n + "）-----");
            say(String.format(L, "%-9s %6s %7s %8s %8s %8s %8s %11s",
                "类别", "n", "基准占比", "P(冷|类)", "P(暖|类)", "冷占比", "富集×", "ΔbandD中位"));
            for (int c = 0; c < 4; c++) {
                double pc = nn[c] > 0 ? nc[c] / (double) nn[c] : Double.NaN;
                double pw = nn[c] > 0 ? nw[c] / (double) nn[c] : Double.NaN;
                double share = coldTot > 0 ? coldByCls[c] / (double) coldTot : Double.NaN;
                double enr = (nn[c] > 0 && pAll > 0) ? (nc[c] / (double) nn[c]) / pAll : Double.NaN;
                say(String.format(L, "%-9s %6d %7.3f %8.4f %8.4f %8.3f %8.2f %11.4f",
                    CN[c], nn[c], base[c] / (double) n, pc, pw, share, enr,
                    nn[c] > 0 ? dS[c] / nn[c] : Double.NaN));
            }
            say(String.format(L, "合计：冷签名 %d（%.4f）暖签名 %d（%.4f）", coldTot, pAll, warmTot, warmTot / (double) n));
            StringBuilder s1 = new StringBuilder("冷签名的类别分布：");
            StringBuilder s2 = new StringBuilder("暖签名的类别分布：");
            for (int c = 0; c < 4; c++) {
                s1.append(CN[c]).append(' ').append(coldTot > 0 ? String.format(L, "%.3f", coldByCls[c] / (double) coldTot) : "-").append("  ");
                s2.append(CN[c]).append(' ').append(warmTot > 0 ? String.format(L, "%.3f", warmByCls[c] / (double) warmTot) : "-").append("  ");
            }
            say(s1.toString());
            say(s2.toString());
            say(String.format(L, "冷签名格点到岸距离中位：东 %.1f km / 西 %.1f km",
                median(coldDE, coldM) / 1000.0, median(coldDW, coldM) / 1000.0));
        }
    }
}

