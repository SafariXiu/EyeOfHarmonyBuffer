package probe;

import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.GlobalCirculation;
import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.RelaxedClimate;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.NoiseContinentGrid;

import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * P195：任务二 —— 用【稳健判据】把「双涡旋到底存不存在」一次判清楚。
 *
 *  主判据：以【海盆】为样本单元，符号检验比例 p = P(西缘|v| > 东缘|v|) + Wilson 二项 95% 区间。
 *  三个【互不重叠】的 x 窗各算一遍；三窗不一致 => 宣布【不可判定】。
 *  辅助判据：每盆比值中位数 + bootstrap 95% 区间（明确知道它低估不确定性，因为不含"换窗"这一层）。
 *  第三个视角：以【整行】为单元的行级符号比例（对行内空间相关更稳健的一把尺子）。
 *
 *  窗口：W1 x[0,1200km)  W2 x[2000,3200km)  W3 x[4000,5200km)  每带每半球 20 行
 *        旧口径 L x[0,800km) 每带每半球 8 行（在 W1 的 tile 循环里顺带采样，不额外建窗）
 *  采样 tile 主序，PREHEAT=false；只读诊断，不改任何源码。
 */
public class P195 {

    static final int SEED = 1022228679;
    static final int TILE_X = 100_000;
    static final int ZC = GlobalCirculation.Z_CYCLE;
    static final int STEP = 1000;
    static final int EDGE_KM = 50, MIN_W_KM = 100;
    static final String[] BN = {"赤道附近", "副热带", "中纬", "副极地", "极地"};

    static File ROOT = new File("K:\\moder\\EyeOfHarmonyBuffer");
    static File DIR = new File(ROOT, "run\\talos_maps");
    static File STATS = new File(DIR, "signstats.txt");
    static PrintStream rep;
    static long T0;

    static class Win {
        int id, x0, x1, rpb;
        String label;
        boolean legacy;
        double[][][] S = new double[5][2][7];      // n k med wLo wHi bLo bHi
        double[][][] M = new double[5][2][2];      // meanWest meanEast
        int[][] nRow = new int[5][2], kRow = new int[5][2];
        List<Double>[][] ratios = new List[5][2];

        Win(int id, int x0, int x1, int rpb, boolean legacy, String label) {
            this.id = id; this.x0 = x0; this.x1 = x1; this.rpb = rpb;
            this.legacy = legacy; this.label = label;
            for (int b = 0; b < 5; b++) for (int h = 0; h < 2; h++) ratios[b][h] = new ArrayList<>();
        }
    }

    public static void main(String[] args) throws Exception {
        RelaxedClimate.PREHEAT = false;
        DIR.mkdirs();
        rep = new PrintStream(new File(ROOT, "build\\eoh_probe\\mtn\\p195_report.txt"), "UTF-8");
        T0 = System.nanoTime();
        say("预算：3 个窗 x 3 个 (tileX,tileZ) 组合以外的开销主要在窗口求解；本探针串行，估计 12-18 分钟。");

        Win w1 = new Win(1, 0, 1_200_000, 20, false, "窗口1 x 0–1200km（20 行/带/半球）");
        Win w2 = new Win(2, 2_000_000, 3_200_000, 20, false, "窗口2 x 2000–3200km（20 行/带/半球）");
        Win w3 = new Win(3, 4_000_000, 5_200_000, 20, false, "窗口3 x 4000–5200km（20 行/带/半球）");
        Win lg = new Win(0, 0, 800_000, 8, true, "旧口径 800km（8 行/带/半球）");

        run(w1, lg);
        run(w2, null);
        run(w3, null);

        List<Win> all = new ArrayList<>();
        all.add(w1); all.add(w2); all.add(w3); all.add(lg);
        writeStats(all);
        for (Win w : all) dump(w);
        verdict(w1, w2, w3);
        say("总耗时 " + sec() + "s");
        rep.close();
    }

    static void say(String s) {
        System.out.println("[P195] " + s);
        rep.println("[P195] " + s);
    }

    static long sec() {
        return (System.nanoTime() - T0) / 1_000_000_000L;
    }

    // ------------------------------------------------ 采样 + 聚合

    static void run(Win w, Win lg) {
        say("--- 开始 " + w.label + "  t=" + sec() + "s");
        int nx = (w.x1 - w.x0) / STEP;
        int rows = 5 * 2 * w.rpb;
        int[] rowZ = new int[rows], band = new int[rows], hem = new int[rows];
        int k = 0;
        for (int b = 0; b < 5; b++) {
            for (int h = 0; h < 2; h++) {
                for (int r = 0; r < w.rpb; r++) {
                    band[k] = b; hem[k] = h;
                    int zz = b * 100_000 + (int) ((r + 0.5) * 100_000.0 / w.rpb);
                    rowZ[k] = h == 0 ? zz : ZC - zz;
                    k++;
                }
            }
        }
        boolean[][] sea = new boolean[rows][nx];
        float[][] v = new float[rows][nx];
        int lgNx = 0, lgRows = 0;
        boolean[][] lsea = null;
        float[][] lv = null;
        int[] lgRowZ = null, lgBand = null, lgHem = null;
        if (lg != null) {
            lgNx = (lg.x1 - lg.x0) / STEP;
            lgRows = 5 * 2 * lg.rpb;
            lsea = new boolean[lgRows][lgNx];
            lv = new float[lgRows][lgNx];
            lgRowZ = new int[lgRows]; lgBand = new int[lgRows]; lgHem = new int[lgRows];
            int q = 0;
            for (int b = 0; b < 5; b++) {
                for (int h = 0; h < 2; h++) {
                    for (int r = 0; r < lg.rpb; r++) {
                        lgBand[q] = b; lgHem[q] = h;
                        int zz = b * 100_000 + (int) ((r + 0.5) * 100_000.0 / lg.rpb);
                        lgRowZ[q] = h == 0 ? zz : ZC - zz;
                        q++;
                    }
                }
            }
        }
        int tx0 = w.x0 / TILE_X, tx1 = (w.x1 - 1) / TILE_X;
        for (int tx = tx0; tx <= tx1; tx++) {
            int i0 = Math.max(0, (tx * TILE_X - w.x0) / STEP);
            int i1 = Math.min(nx, ((tx + 1) * TILE_X - w.x0) / STEP);
            for (int i = i0; i < i1; i++) {
                int x = w.x0 + i * STEP;
                for (int r = 0; r < rows; r++) {
                    int z = rowZ[r];
                    boolean land = NoiseContinentGrid.landResidual(x, z, SEED) >= 0.0;
                    sea[r][i] = !land;
                    if (!land) v[r][i] = (float) Math.abs(RelaxedClimate.sampleCurrent(x, z, SEED)[1]);
                }
                if (lg != null) {
                    int li = (x - lg.x0) / STEP;
                    if (li >= 0 && li < lgNx) {
                        for (int r = 0; r < lgRows; r++) {
                            int z = lgRowZ[r];
                            boolean land = NoiseContinentGrid.landResidual(x, z, SEED) >= 0.0;
                            lsea[r][li] = !land;
                            if (!land) lv[r][li] = (float) Math.abs(RelaxedClimate.sampleCurrent(x, z, SEED)[1]);
                        }
                    }
                }
            }
            say("  tile " + tx + " 完成 t=" + sec() + "s");
        }
        aggregate(w, sea, v, nx, rows, band, hem);
        if (lg != null) aggregate(lg, lsea, lv, lgNx, lgRows, lgBand, lgHem);
    }

    static void aggregate(Win w, boolean[][] sea, float[][] v, int nx, int rows,
                          int[] band, int[] hem) {
        double[][] sw = new double[5][2], se = new double[5][2];
        int[][] nb = new int[5][2], nk = new int[5][2];
        for (int r = 0; r < rows; r++) {
            int b = band[r], h = hem[r], i = 0;
            while (i < nx) {
                if (!sea[r][i]) { i++; continue; }
                int e = i;
                while (e + 1 < nx && sea[r][e + 1]) e++;
                int wkm = (e - i + 1) * STEP / 1000;
                if (wkm >= MIN_W_KM) {
                    int edge = Math.min(EDGE_KM, (e - i + 1) / 3);
                    if (edge < 1) edge = 1;
                    double a = 0, a2 = 0;
                    for (int q = i; q < i + edge; q++) a += v[r][q];
                    for (int q = e - edge + 1; q <= e; q++) a2 += v[r][q];
                    double mw = a / edge, me = a2 / edge;
                    nb[b][h]++;
                    sw[b][h] += mw;
                    se[b][h] += me;
                    if (mw > me) nk[b][h]++;
                    if (me > 1e-6) w.ratios[b][h].add(mw / me);
                }
                i = e + 1;
            }
        }
        // 行级（整行海盆平均谁强）
        for (int r = 0; r < rows; r++) {
            int b = band[r], h = hem[r], i = 0;
            double aw = 0, ae = 0; int cw = 0, ce = 0;
            while (i < nx) {
                if (!sea[r][i]) { i++; continue; }
                int e = i;
                while (e + 1 < nx && sea[r][e + 1]) e++;
                if ((e - i + 1) * STEP / 1000 >= MIN_W_KM) {
                    int edge = Math.min(EDGE_KM, (e - i + 1) / 3);
                    if (edge < 1) edge = 1;
                    double a = 0, a2 = 0;
                    for (int q = i; q < i + edge; q++) a += v[r][q];
                    for (int q = e - edge + 1; q <= e; q++) a2 += v[r][q];
                    aw += a / edge; ae += a2 / edge; cw++; ce++;
                }
                i = e + 1;
            }
            if (cw > 0) {
                w.nRow[b][h]++;
                if (aw / cw > ae / ce) w.kRow[b][h]++;
            }
        }
        for (int b = 0; b < 5; b++) {
            for (int h = 0; h < 2; h++) {
                int n = nb[b][h];
                double[] ci = wilson(nk[b][h], n);
                double[] q = quant(w.ratios[b][h]);
                double[] bc = bootCI(w.ratios[b][h], 2000);
                w.S[b][h][0] = n;
                w.S[b][h][1] = nk[b][h];
                w.S[b][h][2] = q[1];
                w.S[b][h][3] = ci[0];
                w.S[b][h][4] = ci[1];
                w.S[b][h][5] = bc[0];
                w.S[b][h][6] = bc[1];
                w.M[b][h][0] = n > 0 ? sw[b][h] / n : Double.NaN;
                w.M[b][h][1] = n > 0 ? se[b][h] / n : Double.NaN;
            }
        }
    }

    static double[] wilson(int k, int n) {
        if (n <= 0) return new double[]{Double.NaN, Double.NaN};
        double z = 1.959964, ph = k / (double) n;
        double den = 1 + z * z / n;
        double c = (ph + z * z / (2 * n)) / den;
        double hw = z * Math.sqrt(ph * (1 - ph) / n + z * z / (4.0 * n * n)) / den;
        return new double[]{Math.max(0, c - hw), Math.min(1, c + hw)};
    }

    static double[] quant(List<Double> rs) {
        if (rs.isEmpty()) return new double[]{Double.NaN, Double.NaN, Double.NaN};
        double[] c = new double[rs.size()];
        for (int i = 0; i < c.length; i++) c[i] = rs.get(i);
        java.util.Arrays.sort(c);
        return new double[]{c[c.length / 4], c[c.length / 2], c[c.length * 3 / 4]};
    }

    static double[] bootCI(List<Double> rs, int iters) {
        if (rs.size() < 5) return new double[]{Double.NaN, Double.NaN};
        java.util.Random rnd = new java.util.Random(20240607);
        double[] src = new double[rs.size()];
        for (int i = 0; i < src.length; i++) src[i] = rs.get(i);
        double[] tmp = new double[src.length];
        double[] med = new double[iters];
        for (int it = 0; it < iters; it++) {
            for (int i = 0; i < src.length; i++) tmp[i] = src[rnd.nextInt(src.length)];
            java.util.Arrays.sort(tmp);
            med[it] = tmp[tmp.length / 2];
        }
        java.util.Arrays.sort(med);
        return new double[]{med[(int) (iters * 0.025)], med[(int) (iters * 0.975)]};
    }

    // ------------------------------------------------ 输出

    static void writeStats(List<Win> all) {
        StringBuilder sb = new StringBuilder();
        sb.append("#EOH SIGNSTATS v1\n");
        for (Win w : all) {
            sb.append("W ").append(w.id).append(' ').append(w.x0).append(' ').append(w.x1).append(' ')
              .append(w.rpb).append(' ').append(w.legacy ? 1 : 0).append(' ').append(w.label).append('\n');
            for (int b = 0; b < 5; b++) {
                for (int h = 0; h < 2; h++) {
                    sb.append("B ").append(b).append(' ').append(h);
                    for (int q = 0; q < 7; q++) sb.append(' ').append(fmt(w.S[b][h][q]));
                    sb.append('\n');
                    sb.append("M ").append(b).append(' ').append(h).append(' ')
                      .append(fmt(w.M[b][h][0])).append(' ').append(fmt(w.M[b][h][1])).append('\n');
                }
            }
        }
        try {
            Files.write(STATS.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
            say("写出 " + STATS.getAbsolutePath());
        } catch (Exception e) {
            say("写文件失败 " + e);
        }
    }

    static String fmt(double x) {
        return Double.isNaN(x) ? "NaN" : String.format("%.6f", x);
    }

    static String n2(double x) {
        return Double.isNaN(x) ? "  -  " : String.format("%.2f", x);
    }

    static String n3(double x) {
        return Double.isNaN(x) ? "  -  " : String.format("%.3f", x);
    }

    static void dump(Win w) {
        say("===== " + w.label + " =====");
        say(String.format("%-8s %-4s %6s %5s %7s %19s %8s %17s", "带", "半球", "海盆n", "西>东", "p_hat",
            "Wilson95%CI", "中位比", "boot95%CI"));
        for (int b = 0; b < 5; b++) {
            for (int h = 0; h < 2; h++) {
                double n = w.S[b][h][0];
                if (n <= 0) continue;
                say(String.format("%-8s %-4s %6d %5d %7.3f [%6.3f,%6.3f] %8s [%6s,%6s]",
                    BN[b], h == 0 ? "北" : "南", (int) n, (int) w.S[b][h][1], w.S[b][h][1] / n,
                    w.S[b][h][3], w.S[b][h][4], n2(w.S[b][h][2]),
                    n2(w.S[b][h][5]), n2(w.S[b][h][6])));
            }
        }
        say(String.format("%-8s %-4s %6s %5s %7s %19s", "带", "半球(合并行)", "行数", "西>东", "行级p", "Wilson95%CI"));
        for (int b = 0; b < 5; b++) {
            for (int h = 0; h < 2; h++) {
                int n = w.nRow[b][h];
                if (n <= 0) continue;
                double[] ci = wilson(w.kRow[b][h], n);
                say(String.format("%-8s %-4s %6d %5d %7.3f [%6.3f,%6.3f]",
                    BN[b], h == 0 ? "北" : "南", n, w.kRow[b][h], w.kRow[b][h] / (double) n, ci[0], ci[1]));
            }
        }
    }

    /** 主判据：三窗一致性。 */
    static void verdict(Win w1, Win w2, Win w3) {
        Win[] ws = {w1, w2, w3};
        say("===== 判定（主判据：三窗互不重叠，任一不一致即【不可判定】）=====");
        say(String.format("%-8s %-28s %-28s %-28s %s", "带", "窗口1 p̂[95%CI] n", "窗口2 p̂[95%CI] n",
            "窗口3 p̂[95%CI] n", "判定"));
        int nWest = 0, nEast = 0, nUnd = 0;
        for (int b = 0; b < 5; b++) {
            StringBuilder sb = new StringBuilder();
            boolean allUp = true, allDn = true;
            for (Win w : ws) {
                int n = (int) (w.S[b][0][0] + w.S[b][1][0]);
                int kk = (int) (w.S[b][0][1] + w.S[b][1][1]);
                double[] ci = wilson(kk, n);
                sb.append(String.format("%-28s", n3(kk / (double) n) + " [" + n3(ci[0]) + ","
                    + n3(ci[1]) + "] n=" + n));
                if (!(ci[0] > 0.5)) allUp = false;
                if (!(ci[1] < 0.5)) allDn = false;
            }
            String v;
            if (allUp) { v = "西岸强（3/3 窗显著）"; nWest++; }
            else if (allDn) { v = "东岸强（3/3 窗显著）"; nEast++; }
            else { v = "不可判定"; nUnd++; }
            say(String.format("%-8s %s %s", BN[b], sb, v));
        }
        say("汇总：西岸强 " + nWest + " 带，东岸强 " + nEast + " 带，不可判定 " + nUnd + " 带（共 5 带）。");
        if (nUnd == 5) {
            say("总判定：现有采样量下【无法判定双涡旋是否存在】——5 个纬度带的三窗区间全部或部分跨过 50%。");
        } else {
            say("总判定：部分带可判（见上表）；仍需注意这三个窗只覆盖 x=0.80/5.60 Mm 两段之间的三段。");
        }
        say("辅助判据（中位数比 + bootstrap）：bootstrap 只重采样海盆，不含『换窗』这一层不确定性，");
        say("  因此它的区间必然比真实不确定性窄；凡 bootstrap 显著但换窗翻转的带，一律按【不可判定】处理。");
    }
}
