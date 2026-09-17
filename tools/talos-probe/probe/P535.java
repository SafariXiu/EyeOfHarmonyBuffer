package probe;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P535：**性能基线 + 机器无关标尺**（设计冻结 §306）。
 *
 * 只用裸 ns 跨机器不可比，且无法与 §306 的代价模型（vnoise 次数）对接。
 * 所以本探针同时测【一次 vnoise 调用的 ns】，把一切都换算成 **vnoise 当量**：
 *
 *   当量 = ns/列 / ns(一次 vnoise)
 *
 * 于是「V8 生成器必须 <= N 当量」成为与机器无关的验收条件。
 */
public class P535 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void S(String s) { rep.println("[P535] " + s); rep.flush(); System.out.println("[P535] " + s); System.out.flush(); }

    static final long SEED = 1022228679L;
    static final int  CELL = 2_400_000;
    static final double THR = 0.10;
    static double sinkD = 0; static boolean sinkB = false;

    // ---- 与 PlateField 同风格的噪声，用作「一次 vnoise」的标尺 ----
    static double rnd01(long h) { long v = h; v ^= (v >>> 33); v *= 0xFF51AFD7ED558CCDL; v ^= (v >>> 33);
        v *= 0xC4CEB9FE1A85EC53L; v ^= (v >>> 33); return (v >>> 11) * 0x1.0p-53; }
    static long hash2(long s, int a, int b) { long h = s; h = h * 0x9E3779B97F4A7C15L + a; h ^= (h >>> 29);
        h *= 0xBF58476D1CE4E5B9L; h = h * 0x9E3779B97F4A7C15L + b; h ^= (h >>> 32); h *= 0x94D049BB133111EBL; h ^= (h >>> 29); return h; }
    static double vnoise(double x, double z, long s) {
        int xi = (int) Math.floor(x), zi = (int) Math.floor(z);
        double tx = x - xi, tz = z - zi;
        double u = tx * tx * (3 - 2 * tx), v = tz * tz * (3 - 2 * tz);
        double a = rnd01(hash2(s, xi, zi)) * 2 - 1, b = rnd01(hash2(s, xi + 1, zi)) * 2 - 1;
        double c = rnd01(hash2(s, xi, zi + 1)) * 2 - 1, d = rnd01(hash2(s, xi + 1, zi + 1)) * 2 - 1;
        return (a * (1 - u) + b * u) * (1 - v) + (c * (1 - u) + d * u) * v; }

    static final int CHUNKS = 1024;

    static double benchVnoise(int n) {
        long t0 = System.nanoTime(); double acc = 0;
        for (int i = 0; i < n; i++) acc += vnoise(i * 0.017, i * 0.031, SEED);
        long dt = System.nanoTime() - t0; sinkD += acc; return dt / (double) n; }

    static double benchElevFull(int baseX, int baseZ) {
        long t0 = System.nanoTime(); double acc = 0;
        for (int c = 0; c < CHUNKS; c++) {
            int bx = baseX + (c % 64) * 16, bz = baseZ + (c / 64) * 16;
            for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++)
                acc += PlateField.elevationWithCellFull(bx + lx, bz + lz, SEED, CELL, THR); }
        long dt = System.nanoTime() - t0; sinkD += acc; return dt / (double) (CHUNKS * 256); }

    static double benchLandFull(int baseX, int baseZ) {
        long t0 = System.nanoTime(); int acc = 0;
        for (int c = 0; c < CHUNKS; c++) {
            int bx = baseX + (c % 64) * 16, bz = baseZ + (c / 64) * 16;
            for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++)
                if (PlateField.isLandFullWithArc(bx + lx, bz + lz, SEED, CELL, THR)) acc++; }
        long dt = System.nanoTime() - t0; sinkD += acc; return dt / (double) (CHUNKS * 256); }

    static double benchElevLight(int baseX, int baseZ) {
        long t0 = System.nanoTime(); double acc = 0;
        for (int c = 0; c < CHUNKS; c++) {
            int bx = baseX + (c % 64) * 16, bz = baseZ + (c / 64) * 16;
            for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++)
                acc += PlateField.elevationWithCell(bx + lx, bz + lz, SEED, CELL); }
        long dt = System.nanoTime() - t0; sinkD += acc; return dt / (double) (CHUNKS * 256); }

    static double benchFbm7(int baseX, int baseZ) {   // 7 倍频 fbm，当「10 当量」的参照物
        long t0 = System.nanoTime(); double acc = 0;
        for (int c = 0; c < CHUNKS; c++) {
            int bx = baseX + (c % 64) * 16, bz = baseZ + (c / 64) * 16;
            for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
                double x = bx + lx, z = bz + lz, s = 0, a = 1, f = 1.0 / 3000.0;
                for (int o = 0; o < 7; o++) { s += a * vnoise(x * f, z * f, SEED + o * 7919L); a *= 0.5; f *= 2; }
                acc += s; } }
        long dt = System.nanoTime() - t0; sinkD += acc; return dt / (double) (CHUNKS * 256); }

    public static void main(String[] args) throws Exception {
        File dir = new File(ROOT, "build" + File.separator + "eoh_probe" + File.separator + "refs");
        dir.mkdirs();
        rep = new PrintStream(new File(dir, "P535_perf.txt"), "UTF-8");
        int REP = args.length > 0 ? Integer.parseInt(args[0]) : 9;
        S("=== P535 性能基线 + vnoise 当量标尺 ===");
        S("SEED=" + SEED + " CELL=" + CELL + " THR=" + THR + " CHUNKS/pass=" + CHUNKS + " (= " + (CHUNKS*256) + " 列/趟) REP=" + REP);
        S("");

        S("--- 预热 ---");
        for (int i = 0; i < 4; i++) { benchVnoise(2_000_000); benchElevFull(1000, 2000); benchLandFull(1000, 2000); benchElevLight(1000, 2000); benchFbm7(1000, 2000); }
        S("预热完成");
        S("");

        double[] nv = new double[REP], ef = new double[REP], lf = new double[REP], el = new double[REP], f7 = new double[REP];
        for (int r = 0; r < REP; r++) {
            nv[r] = benchVnoise(4_000_000);
            ef[r] = benchElevFull(1000 + r * 4096, 2000 + r * 4096);
            lf[r] = benchLandFull(1000 + r * 4096, 2000 + r * 4096);
            el[r] = benchElevLight(1000 + r * 4096, 2000 + r * 4096);
            f7[r] = benchFbm7(1000 + r * 4096, 2000 + r * 4096);
        }
        java.util.Arrays.sort(nv); java.util.Arrays.sort(ef); java.util.Arrays.sort(lf); java.util.Arrays.sort(el); java.util.Arrays.sort(f7);
        double medNv = nv[REP/2], medEf = ef[REP/2], medLf = lf[REP/2], medEl = el[REP/2], medF7 = f7[REP/2];

        S(String.format(LF, "--- 中位数（%d 趟）---", REP));
        S(String.format(LF, "vnoise(x1)             %8.2f ns", medNv));
        S(String.format(LF, "fbm 7 倍频             %8.2f ns  = %6.2f 当量", medF7, medF7 / medNv));
        S(String.format(LF, "elevationWithCell      %8.2f ns  = %6.2f 当量", medEl, medEl / medNv));
        S(String.format(LF, "isLandFullWithArc      %8.2f ns  = %6.2f 当量", medLf, medLf / medNv));
        S(String.format(LF, "elevationWithCellFull  %8.2f ns  = %6.2f 当量   <= 基线", medEf, medEf / medNv));
        S("");
        S(String.format(LF, "min/max 离散度: elevFull %.2f..%.2f ns (%.1f%%)", ef[0], ef[REP-1], 100.0*(ef[REP-1]-ef[0])/ef[0]));
        S("");
        S(String.format(LF, "=== 验收门槛（设计冻结 §306）==="));
        S(String.format(LF, "V8 生成器目标  <= %.2f 当量（= 1.00x 基线）", medEf / medNv));
        S(String.format(LF, "V8 生成器理想  <= %.2f 当量（= 0.70x 基线）", 0.70 * medEf / medNv));
        S(String.format(LF, "§306 代价模型预测：朴素移植 360 vnoise -> O1 后 10 -> O2 后 4"));
        S(String.format(LF, "sink=%.6e/%b", sinkD, sinkB));
        rep.close();
    }
}