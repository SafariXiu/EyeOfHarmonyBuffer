package probe;

import com.EyeOfHarmonyBuffer.sim.export.MapWriter;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P505: 单纯的海陆分布图。不改任何生产代码，只读 PlateField。
 *
 * <p>口径（必须与生产逐字一致）:
 *   land = PlateField.isLandWithCell(x, z, seed, PLATE_CELL)
 *   seed = SimTerrain.seedOf(TalosContract.DEFAULT_SEED = 1022228679)
 *   PLATE_CELL = 2,400,000  (ChunkProviderTalos2:214-217 / V2BiomeField:333 用的就是它)
 *
 * <p>几何：z 覆盖 [z0, z0+span)，默认 z0 = -MAX_D = -10,000,000、span = Z_CYCLE
 *   = 20,000,000 ⇒ 一个完整纬度循环（南极点 -> 赤道 -> 北极点），
 *   z=0 赤道、z=+10,000,000 北极、z=-10,000,000 南极。x 覆盖 [x0, x0+span)，
 *   X 是**非周期**的 ⇒ 左右两边没有接缝，取哪一段都是"一个 2000 万格的窗口"。
 *
 * <p>每像素 1 个样本（像素中心），不做多数投票 —— 这是采样声明，不是平滑。
 *
 * <p>用法: P505 [W] [x0] [z0] [span]   默认 2000 0 -10000000 20000000
 */
public class P505 {

    static final int SEED = 1022228679;              // TalosContract.DEFAULT_SEED
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static final File MAPDIR = new File(ROOT, "run" + File.separator + "talos_maps");
    static PrintStream rep;

    static void say(String s) { rep.println("[P505] " + s); rep.flush(); System.out.println("[P505] " + s); System.out.flush(); }

    /** t < 0.5 = 海（深蓝），否则陆（浅褐）。两色，无第三色。 */
    static final MapWriter.ColorMap SEALAND = new MapWriter.ColorMap() {
        @Override public int rgb(double t) { return t < 0.5 ? 0x1B3B6F : 0xE0D5B0; }
    };

    public static void main(String[] args) throws Exception {
        int w = args.length > 0 ? Integer.parseInt(args[0]) : 2000;
        long x0 = args.length > 1 ? Long.parseLong(args[1]) : 0L;
        long z0 = args.length > 2 ? Long.parseLong(args[2]) : -(long) WorldContract.MAX_D;
        long span = args.length > 3 ? Long.parseLong(args[3]) : (long) WorldContract.Z_CYCLE;
        int h = w;
        int nWin = args.length > 4 ? Integer.parseInt(args[4]) : 1;
        double step = span / (double) h;

        File dir = new File(ROOT, "build" + File.separator + "eoh_probe" + File.separator + "refs");
        dir.mkdirs();
        rep = new PrintStream(new File(dir, "P505_sealand.txt"), "UTF-8");

        long seed = SimTerrain.seedOf(SEED);
        say("=== P505 海陆分布图 ===");
        say(String.format(LF, "worldSeedInt=%d  seedOf()=%d  PLATE_CELL=%d", SEED, seed, PlateField.PLATE_CELL));
        say(String.format(LF, "seaLevel=%.1f m  Z_CYCLE=%d  MAX_D=%d", PlateField.SEA_LEVEL, WorldContract.Z_CYCLE, WorldContract.MAX_D));
        say(String.format(LF, "图幅 %d x %d px，每像素 %.1f 格 (%.3f km)；x0=%d z0=%d span=%d 格",
            w, h, step, step / 1000.0, x0, z0, span));
        say("");

        double[] v = new double[w * h];
        long land = 0;
        int[] bandLand = new int[19];      // |lat| 0..90, 5 度一档
        int[] bandTot = new int[19];
        // 面积权重：本仓 z 线性于纬度 ⇒ 均匀 (x,z) 采样是均匀纬度采样，
        // 而地球的 29.2% 是球面面积加权口径。两个数必须分开报。
        double wSum = 0, landW = 0;
        double[] bandW = new double[19], bandLandW = new double[19];
        long t0 = System.nanoTime();
        for (int r = 0; r < h; r++) {
            int z = (int) Math.round(z0 + r * step);
            double latDeg = Math.toDegrees(WorldContract.latOf(z));
            int b = (int) Math.min(18.0, Math.abs(latDeg) / 5.0);
            for (int c = 0; c < w; c++) {
                int x = (int) Math.round(x0 + c * step);
                boolean isl = PlateField.isLandWithCell(x, z, seed, PlateField.PLATE_CELL);
                v[r * w + c] = isl ? 1.0 : 0.0;
                double cw = Math.cos(Math.toRadians(latDeg));
                wSum += cw; bandW[b] += cw;
                if (isl) { land++; bandLand[b]++; landW += cw; bandLandW[b] += cw; }
                bandTot[b]++;
            }
            if (r % Math.max(1, h / 8) == 0) say(String.format(LF, "  进度 %3d%%  z=%9d lat=%+7.2f", 100 * r / h, z, latDeg));
        }
        long t1 = System.nanoTime();
        say(String.format(LF, "采样完成：%d 点，%.1f s，%.2f us/点", w * h, (t1 - t0) / 1e9, (t1 - t0) / 1000.0 / (w * h)));
        say(String.format(LF, "陆地占比 A（(x,纬度) 均匀网格，无面积权重） = %d/%d = %.2f%%", land, w * h, 100.0 * land / (w * h)));
        say(String.format(LF, "陆地占比 B（球面面积加权 cos(lat)）        = %.2f%%   <== 与地球 29.2%% 同口径", 100.0 * landW / wSum));
        say("  为什么必须分开报：本仓 z 线性于纬度 ⇒ 均匀 (x,z) 采样 = 均匀纬度采样，高纬每像素代表的球面面积更小。");
        say("  地球 29.2%% 是面积加权口径，只能与 B 比。");
        say("");

        String png = "sealand_" + w + ".png";
        File out = new File(MAPDIR, png);
        MapWriter.writePng(out, w, h, v, 0.0, 1.0, SEALAND);
        say("PNG = " + out.getAbsolutePath());

        say("");
        say("|lat| 陆地占比（本窗口，5 度一档）:");
        say(String.format(LF, "  %6s %9s %9s %10s", "lat", "landUnif", "landAreaWt", "n"));
        for (int i = 0; i < 19; i++) {
            say(String.format(LF, "  %3d-%3d %8.2f%% %8.2f%% %10d", i * 5, i * 5 + 5,
                100.0 * bandLand[i] / Math.max(1, bandTot[i]),
                100.0 * bandLandW[i] / Math.max(1e-9, bandW[i]), bandTot[i]));
        }
        say("  land%A = (x,纬度) 均匀；land%B = 面积加权 cos(lat)（与地球同口径）。");

        // ---- X 非周期性检查：X 不是周期的 ⇒ "全球陆地占比"必须给出它对 X 窗口的依赖 ----
        if (nWin > 1) {
            say("");
            say("--- X 非周期性检查（同一纬度循环，nWin 个互不重叠的 X 窗口，每窗 span 格）---");
            say(String.format(LF, "  %14s %10s %10s %12s", "x0", "landUnif", "landAreaWt", "landPx"));
            double[] aArr = new double[nWin], bArr = new double[nWin];
            double aMin = 1e9, aMax = -1e9, bMin = 1e9, bMax = -1e9;
            double[][] wBW = new double[nWin][19], wBL = new double[nWin][19];
            for (int k = 0; k < nWin; k++) {
                long kx0 = x0 + (long) k * span;
                long L = 0; double tWS = 0, tLW = 0;
                for (int r = 0; r < h; r++) {
                    int z = (int) Math.round(z0 + r * step);
                    double latDeg = Math.toDegrees(WorldContract.latOf(z));
                    double cw = Math.cos(Math.toRadians(latDeg));
                    int b = (int) Math.min(18.0, Math.abs(latDeg) / 5.0);
                    for (int c = 0; c < w; c++) {
                        int x = (int) Math.round(kx0 + c * step);
                        boolean isl = PlateField.isLandWithCell(x, z, seed, PlateField.PLATE_CELL);
                        tWS += cw; wBW[k][b] += cw;
                        if (isl) { L++; tLW += cw; wBL[k][b] += cw; }
                    }
                }
                aArr[k] = 100.0 * L / (w * h);
                bArr[k] = 100.0 * tLW / tWS;
                if (aArr[k] < aMin) aMin = aArr[k];
                if (aArr[k] > aMax) aMax = aArr[k];
                if (bArr[k] < bMin) bMin = bArr[k];
                if (bArr[k] > bMax) bMax = bArr[k];
                say(String.format(LF, "  %14d %9.2f%% %9.2f%% %11d", kx0, aArr[k], bArr[k], L));
            }
            // E94: 这里原来写的是 "land%A 极差 = %.2f ... land%B 极差 = %.2f ..."。
            // %A 与 %B 是**合法的 Java 格式转换符**（十六进制浮点 / boolean），
            // 它们会各自吃掉一个参数 ⇒ 转换符从 4 个变成 6 个，参数序列整体错位。
            // 标签里绝不允许出现 %。<b>改名前这一行是运行时异常的直接来源。</b>
            say(String.format(LF, "  landUniform 极差 = %.2f pt (%.2f~%.2f)    landAreaWt 极差 = %.2f pt (%.2f~%.2f)",
                aMax - aMin, aMin, aMax, bMax - bMin, bMin, bMax));
            double aMean = 0, bMean = 0;
            for (int k = 0; k < nWin; k++) { aMean += aArr[k]; bMean += bArr[k]; }
            aMean /= nWin; bMean /= nWin;
            // E94（就是这一行）：标签里写了 land%A / land%B，而 %A/%B 是合法的转换符。
            //   转换符序列 = %d, %A, %.2f, (%% 不吃参数), %B, %.2f, (%% 不吃参数) = 5 个吃参数的
            //   而实参只有 3 个 ⇒ 第 4 个吃参数的转换符 %B 处抛 MissingFormatArgumentException。
            //   与实测栈完全一致。**格式串里的字面标签绝不允许含 %。**
            say(String.format(LF, "  %d 窗均值：landUniform = %.2f%%   landAreaWt(面积加权) = %.2f%%", nWin, aMean, bMean));
            say("  判读：极差几个 pt ⇒ X 方向统计均匀（非周期但无大尺度漂移）；");
            say("        极差十几 pt   ⇒ 不同 X 窗口的气候背景不同，任何'全球'统计量都必须声明窗口。");

            // 多窗平均的 5 度带剖面（单一窗口的剖面会带窗口偏差）—— 写成机器可读 TSV。
            double[] bMeanBand = new double[19], bMinBand = new double[19], bMaxBand = new double[19];
            for (int i = 0; i < 19; i++) {
                double s = 0, lo2 = 1e9, hi2 = -1e9;
                for (int k = 0; k < nWin; k++) {
                    double x2 = 100.0 * wBL[k][i] / Math.max(1e-9, wBW[k][i]);
                    s += x2; if (x2 < lo2) lo2 = x2; if (x2 > hi2) hi2 = x2;
                }
                bMeanBand[i] = s / nWin; bMinBand[i] = lo2; bMaxBand[i] = hi2;
            }
            say("");
            say(String.format(LF, "  %d 窗平均的 |lat| 带剖面: %6s %9s %9s %9s", nWin, "lat", "meanB", "minB", "maxB"));
            for (int i = 0; i < 18; i++) {
                say(String.format(LF, "    %3d-%3d %8.2f%% %8.2f%% %8.2f%%", i * 5, i * 5 + 5,
                    bMeanBand[i], bMinBand[i], bMaxBand[i]));
            }
            File tsv = new File(dir, "P505_bandmean.tsv");
            PrintStream tf = new PrintStream(tsv, "UTF-8");
            tf.println("# P505 land fraction by |lat| band, AREA-WEIGHTED (cos lat), averaged over " + nWin + " disjoint X windows");
            tf.println("# columns: lat_lo lat_hi mean_land_pct min_land_pct max_land_pct");
            tf.println("# global: landA_uniform=" + String.format(LF, "%.4f", aMean) + " landB_areawt=" + String.format(LF, "%.4f", bMean));
            tf.println("# per-window (x0, landA, landB):");
            for (int k = 0; k < nWin; k++) {
                tf.println("#   " + (x0 + (long) k * span) + " " + String.format(LF, "%.4f", aArr[k]) + " " + String.format(LF, "%.4f", bArr[k]));
            }
            for (int i = 0; i < 18; i++) {
                tf.println((i * 5) + " " + (i * 5 + 5) + " " + String.format(LF, "%.4f", bMeanBand[i])
                    + " " + String.format(LF, "%.4f", bMinBand[i]) + " " + String.format(LF, "%.4f", bMaxBand[i]));
            }
            tf.flush(); tf.close();
            say("  TSV = " + tsv.getAbsolutePath() + "   （多窗平均剖面，供脚本拼接，不手抄）");
        }


        int zRow0 = (int) Math.round(z0);
        int zRowL = (int) Math.round(z0 + (h - 1) * step);
        StringBuilder hdr = new StringBuilder();
        hdr.append("P505 sealand map\n")
           .append("worldSeedInt=").append(SEED).append("\n")
           .append("seedOf=").append(seed).append("\n")
           .append("PLATE_CELL=").append(PlateField.PLATE_CELL).append("\n")
           .append("landField=PlateField.isLandWithCell(x,z,seed,PLATE_CELL)  (>= SEA_LEVEL=").append(PlateField.SEA_LEVEL).append(")\n")
           .append("width_px=").append(w).append("\nheight_px=").append(h).append("\n")
           .append("x0=").append(x0).append("\nz0=").append(z0).append("\nspan=").append(span).append("\n")
           .append("blocks_per_px=").append(step).append("\n")
           // ⚠ latOf(+0) 的半球符号在 z=Z_CYCLE/2=10,000,000 处翻转 ⇒ latOf(10,000,000) = -90 度，
           //   那是**南极点**，不是北极点。采样行只有 h 行（r=0..h-1）⇒ 最高一行是 z0+(h-1)*step，
           //   不是 z0+span。用错的那个数写 manifest 会让元数据自相矛盾。
           .append("row0_is=image bottom  z=").append(zRow0).append("  lat=")
           .append(String.format(LF, "%.4f", Math.toDegrees(WorldContract.latOf(zRow0)))).append("\n")
           .append("rowLast_is=image top  z=").append(zRowL).append("  lat=")
           .append(String.format(LF, "%.4f", Math.toDegrees(WorldContract.latOf(zRowL)))).append("\n")
           .append("z0=").append(z0).append(" span=").append(span).append("  (z0+span is NOT sampled: rows are r=0..h-1)\n")
           .append("hemisphere_seam: latOf() sign flips at z=Z_CYCLE/2=+10000000, so latOf(+10000000)=-90 (SOUTH pole)\n")
           .append("col0_is=x0, colLast_is=x0+span; X is APERIODIC -- no wrap\n")
           .append("sampling=1 sample per pixel at pixel centre, NO majority vote\n")
           .append("colors=sea 0x1B3B6F / land 0xE0D5B0\n")
           .append("land_fraction_uniform_xlat=").append(100.0 * land / (w * h)).append(" %\n")
           .append("land_fraction_area_weighted=").append(100.0 * landW / wSum).append(" %  (compare with Earth 29.2%)\n");
        PrintStream mf = new PrintStream(new File(MAPDIR, "sealand_" + w + ".manifest.txt"), "UTF-8");
        mf.print(hdr);
        mf.flush(); mf.close();
        say("MANIFEST = " + new File(MAPDIR, "sealand_" + w + ".manifest.txt").getAbsolutePath());
        rep.flush(); rep.close();
        System.out.println("JAVA_EXIT=0");
    }
}
