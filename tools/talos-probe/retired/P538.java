package probe;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/** P538：**地形实现切换验证**（设计冻结 §315）。
 *
 * 顺序很重要：**先证明「开关关闭 => 逐位不变」，再谈切换后的结果。**
 * 不先证明这一点，后面所有对照都可能是「改坏了」而不是「切过去了」。
 */
public class P538 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void S(String s) { rep.println("[P538] " + s); rep.flush(); System.out.println("[P538] " + s); System.out.flush(); }

    static final int SEED = 1022228679;
    /** ⚠ 口径修正（§559，模板 P442:47）：收 {@code long seed} 的入口传本值；收 {@code int worldSeedInt} 的入口仍传裸 {@code SEED}。本次未改任何判据/阈值/输出行。 */
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final int CELL = PlateField.PLATE_CELL;
    static double sink = 0;

    static double bench(int chunks) {
        long t0 = System.nanoTime(); double acc = 0;
        for (int c = 0; c < chunks; c++) { int bx = 1000 + (c % 64) * 16, bz = 2000 + (c / 64) * 16;
            for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++)
                acc += PlateField.elevationWithCell(bx + lx, bz + lz, SD, CELL); }
        long dt = System.nanoTime() - t0; sink += acc; return dt / (double) (chunks * 256); }

    static double landPct(int n) {
        int c = 0;
        for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) {
            long bx = (long) ((x + 0.5) * 20_000_000.0 / n), bz = (long) ((y + 0.5) * 20_000_000.0 / n);
            if (PlateField.isLandWithCell((int) bx, (int) bz, SD, CELL)) c++; }
        return 100.0 * c / (n * n); }

    public static void main(String[] args) throws Exception {
        File dir = new File(ROOT, "build" + File.separator + "eoh_probe" + File.separator + "refs");
        dir.mkdirs();
        rep = new PrintStream(new File(dir, "P538_switch.txt"), "UTF-8");
        S("=== P538 地形实现切换验证（§315）===");
        S("");

        // ---------- A. 开关关闭时是否逐位不变 ----------
        PlateField.TALOS_TERRAIN = false;
        double maxAbs = 0; int nCmp = 0;
        for (int c = 0; c < 64; c++) { int bx = 1000 + (c % 8) * 16, bz = 2000 + (c / 8) * 16;
            for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
                double a = PlateField.elevationWithCell(bx + lx, bz + lz, SD, CELL);
                double b = PlateField.elevationWithCellFull(bx + lx, bz + lz, SD, CELL, 0.10);
                double d = Math.abs(a - b); if (d > maxAbs) maxAbs = d; nCmp++; } }
        S(String.format(LF, "A) 开关关闭: elevationWithCell vs elevationWithCellFull  max|diff| = %.1f m  (n=%d)", maxAbs, nCmp));
        S(String.format(LF, "   判定: %s", maxAbs == 0.0 ? "逐位不变 ✓" : "**变了 —— 不许继续**"));
        S("");

        // ---------- B. configStamp 是否随开关变化 ----------
        long sOff = SimClimate.configStamp();
        PlateField.TALOS_TERRAIN = true;
        long sOn = SimClimate.configStamp();
        S(String.format(LF, "B) configStamp: 关=0x%016X  开=0x%016X  %s", sOff, sOn, sOff != sOn ? "不同 ✓ (瓦片会失效)" : "**相同 —— 缓存会返回旧结果**"));
        S("");

        // ---------- C. 两种状态的地形对照 ----------
        PlateField.TALOS_TERRAIN = false;
        for (int i = 0; i < 3; i++) bench(512);
        double nsOff = bench(2048); double lpOff = landPct(200);
        PlateField.TALOS_TERRAIN = true;
        for (int i = 0; i < 3; i++) bench(512);
        double nsOn = bench(2048); double lpOn = landPct(200);
        S(String.format(LF, "C) 地形对照（同坐标、同种子）"));
        S(String.format(LF, "   %-22s %10s %10s", "量", "开关关", "开关开"));
        S(String.format(LF, "   %-22s %9.2f%% %9.2f%%", "陆地占比(200x200)", lpOff, lpOn));
        S(String.format(LF, "   %-22s %8.1f ns %8.1f ns", "elevationWithCell", nsOff, nsOn));
        S(String.format(LF, "   %-22s %9.3f x %9.3f x", "相对关闭态", 1.0, nsOn / nsOff));
        S("");
        S(String.format(LF, "sink=%.6e", sink));
        rep.close();
    }
}