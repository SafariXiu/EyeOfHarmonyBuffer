package probe;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.litho.TalosField;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/** P536：TalosField（V8 Java 落地）vs PlateField 基线的逐列性能对照。设计冻结 §306/§311。 */
public class P536 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void S(String s) { rep.println("[P536] " + s); rep.flush(); System.out.println("[P536] " + s); System.out.flush(); }

    static final long SEED = 1022228679L;
    static final int CELL = 2_400_000;
    static final double THR = 0.10;
    static double sinkD = 0;

    static double rnd01(long h) { long v = h; v ^= (v >>> 33); v *= 0xFF51AFD7ED558CCDL; v ^= (v >>> 33);
        v *= 0xC4CEB9FE1A85EC53L; v ^= (v >>> 33); return (v >>> 11) * 0x1.0p-53; }
    static long mix(long s, long a, long b) { long h = s * 0x9E3779B97F4A7C15L + a; h ^= (h >>> 29);
        h *= 0xBF58476D1CE4E5B9L; h = h * 0x9E3779B97F4A7C15L + b; h ^= (h >>> 32); h *= 0x94D049BB133111EBL; h ^= (h >>> 29); return h; }
    static double vnoise(double x, double z, long s) {
        double xf = Math.floor(x), zf = Math.floor(z); long xi = (long) xf, zi = (long) zf;
        double tx = x - xf, tz = z - zf, u = tx * tx * (3 - 2 * tx), v = tz * tz * (3 - 2 * tz);
        double a = rnd01(mix(s, xi, zi)) * 2 - 1, b = rnd01(mix(s, xi + 1, zi)) * 2 - 1;
        double c = rnd01(mix(s, xi, zi + 1)) * 2 - 1, d = rnd01(mix(s, xi + 1, zi + 1)) * 2 - 1;
        return (a * (1 - u) + b * u) * (1 - v) + (c * (1 - u) + d * u) * v; }

    static final int CHUNKS = 1024;

    static double benchVnoise(int n) { long t0 = System.nanoTime(); double acc = 0;
        for (int i = 0; i < n; i++) acc += vnoise(i * 0.017, i * 0.031, SEED);
        long dt = System.nanoTime() - t0; sinkD += acc; return dt / (double) n; }

    static double benchPlate(long bx0, long bz0) { long t0 = System.nanoTime(); double acc = 0;
        for (int c = 0; c < CHUNKS; c++) { int bx = (int)(bx0 + (c % 64) * 16), bz = (int)(bz0 + (c / 64) * 16);
            for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++)
                acc += PlateField.elevationWithCellFull(bx + lx, bz + lz, SEED, CELL, THR); }
        long dt = System.nanoTime() - t0; sinkD += acc; return dt / (double) (CHUNKS * 256); }

    static double benchTalosElev(long bx0, long bz0) { long t0 = System.nanoTime(); double acc = 0;
        for (int c = 0; c < CHUNKS; c++) { long bx = bx0 + (long)(c % 64) * 16, bz = bz0 + (long)(c / 64) * 16;
            for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++)
                acc += TalosField.elevation(bx + lx, bz + lz, SEED); }
        long dt = System.nanoTime() - t0; sinkD += acc; return dt / (double) (CHUNKS * 256); }

    static double benchTalosLand(long bx0, long bz0) { long t0 = System.nanoTime(); int acc = 0;
        for (int c = 0; c < CHUNKS; c++) { long bx = bx0 + (long)(c % 64) * 16, bz = bz0 + (long)(c / 64) * 16;
            for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++)
                if (TalosField.isLand(bx + lx, bz + lz, SEED)) acc++; }
        long dt = System.nanoTime() - t0; sinkD += acc; return dt / (double) (CHUNKS * 256); }

    /** 最坏情况：区块乱序访问（格窗缓存被反复击穿）。 */
    static double benchTalosScatter() { long t0 = System.nanoTime(); double acc = 0;
        java.util.Random r = new java.util.Random(7);
        for (int c = 0; c < CHUNKS; c++) { long bx = (long)(r.nextDouble() * 2e9 - 1e9), bz = (long)(r.nextDouble() * 2e9 - 1e9);
            for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++)
                acc += TalosField.elevation(bx + lx, bz + lz, SEED); }
        long dt = System.nanoTime() - t0; sinkD += acc; return dt / (double) (CHUNKS * 256); }

    public static void main(String[] args) throws Exception {
        File dir = new File(ROOT, "build" + File.separator + "eoh_probe" + File.separator + "refs");
        dir.mkdirs();
        rep = new PrintStream(new File(dir, "P536_talos.txt"), "UTF-8");
        int REP = args.length > 0 ? Integer.parseInt(args[0]) : 9;
        S("=== P536 TalosField vs PlateField 逐列性能 ===");
        S(String.format(LF, "DCELL=%.0f block  LEVEL(seed)=%.6f  (自标定，一次性)", TalosField.DCELL, TalosField.level(SEED)));
        S("");
        for (int i = 0; i < 4; i++) { benchVnoise(2_000_000); benchPlate(1000, 2000); benchTalosElev(1000, 2000); benchTalosLand(1000, 2000); }
        S("预热完成");
        double[] nv = new double[REP], pl = new double[REP], te = new double[REP], tl = new double[REP], sc = new double[REP];
        for (int r = 0; r < REP; r++) {
            nv[r] = benchVnoise(4_000_000);
            pl[r] = benchPlate(1000 + r * 4096L, 2000 + r * 4096L);
            te[r] = benchTalosElev(1000 + r * 4096L, 2000 + r * 4096L);
            tl[r] = benchTalosLand(1000 + r * 4096L, 2000 + r * 4096L);
            sc[r] = benchTalosScatter();
        }
        java.util.Arrays.sort(nv); java.util.Arrays.sort(pl); java.util.Arrays.sort(te); java.util.Arrays.sort(tl); java.util.Arrays.sort(sc);
        double mnv = nv[REP/2], mpl = pl[REP/2], mte = te[REP/2], mtl = tl[REP/2], msc = sc[REP/2];
        S(String.format(LF, "--- 中位数（%d 趟）---", REP));
        S(String.format(LF, "vnoise(x1)               %8.2f ns =  1.00 当量", mnv));
        S(String.format(LF, "PlateField.elevFull      %8.2f ns = %5.2f 当量   <- 基线", mpl, mpl / mnv));
        S(String.format(LF, "TalosField.elevation     %8.2f ns = %5.2f 当量", mte, mte / mnv));
        S(String.format(LF, "TalosField.isLand        %8.2f ns = %5.2f 当量", mtl, mtl / mnv));
        S(String.format(LF, "TalosField 乱序访问      %8.2f ns = %5.2f 当量  (最坏情况)", msc, msc / mnv));
        S("");
        S(String.format(LF, "=== 验收（§306）===", 0));
        S(String.format(LF, "elevation / 基线 = %.3f x   目标 <= 1.00x，理想 <= 0.70x  => %s",
                mte / mpl, (mte / mpl <= 0.70 ? "达标(理想)" : (mte / mpl <= 1.0 ? "达标" : "未达标"))));
        S(String.format(LF, "isLand    / 基线 = %.3f x", mtl / mpl));
        S(String.format(LF, "乱序      / 基线 = %.3f x    (格窗缓存击穿的最坏情况)", msc / mpl));
        S(String.format(LF, "sink=%.6e", sinkD));
        rep.close();
    }
}