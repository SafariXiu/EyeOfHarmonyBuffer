package probe;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.V2BiomeField;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P455：**验收项 ④ 群系分布**（用户裁决「补上 做」）。
 *
 * <h3>为什么必须新增这一项</h3>
 * 验收表的 ①地形 / ②洋流 / ③气候 三块**全都不覆盖「群系」** —— 群系只作为 ③B3 的间接产物被碰到。
 * 而 D18（continent 换量）这类修复**改的正是群系分布**，却在验收表上是隐形的。
 *
 * <h3>成本约束（诚实说明）</h3>
 * 每次 V2BiomeField.sample 会触发一个瓦片（100x50 km）的群系+气候求解（冷 ~8.7 s / 热 ~2 s）
 * ⇒ 普查只能用**粗网格**。本探针用 1000 km 间距（24 x 20 = 480 点），
 * 并把**实际解了多少个瓦片**打印出来（纪律：扫描型探针必须报样本数）。
 *
 * <h3>三条读数</h3>
 * ④-1 各 Kind 的面积占比（按纬度带分层）
 * ④-2 岸线带（离岸 0~30 km）内各 Kind 的占比 —— D18 的靶心
 * ④-3 相邻格点的 Kind 变化率（粗网格上只能做量级对照；细网格版记为待办）
 */
public class P455 {

    static final int SEED = 1022228679;
    static final long SD = SimTerrain.seedOf(SEED);
    static final int CELL = PlateField.PLATE_CELL;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    static void say(String s) { rep.println("[P455] " + s); System.out.println("[P455] " + s); }
    static int coastDist(int x, int z, int maxWalk) {
        boolean land0 = PlateField.isLandWithCell(x, z, SD, CELL);
        for (int d = 250; d <= maxWalk; d += 250) {
            if (PlateField.isLandWithCell(x + d, z, SD, CELL) != land0) return land0 ? -d : d;
            if (PlateField.isLandWithCell(x - d, z, SD, CELL) != land0) return land0 ? -d : d;
        }
        return Integer.MIN_VALUE;
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p455_report.txt"), "UTF-8");
        say("P455：验收项 ④ 群系分布");
        say("");

        V2BiomeSelectKind.init();
        int NK = V2BiomeSelectKind.n;

        // ---------- ④-1 面积占比（1000 km 网格，按纬度带分层） ----------
        say("④-1 各群系的面积占比（全世界 1000 km 网格，按纬度带分层）");
        // ⚠ 成本实测（2026-09-13）：每个点要走一次「群系 LUT（81,204 格）+ 气候瓦片」求解，
        // 实测 **> 18 s/点** ⇒ 24x20 = 480 点需要 ~2 小时，**不可行**。
        // 降到 12x8 = 96 点 ⇒ ~25 分钟，并且**每档的样本数会被打印出来**（纪律：样本太少要能看出来）。
        int STEP = 1_000_000;
        int NXB = 12, NZB = 8;
        int[][] band = new int[9][NK];      // 纬度带 0..8（每 10 度）
        int[] tot = new int[NK];
        int n = 0, nLand = 0;
        for (int iz = 0; iz < NZB; iz++) {
            int z = (int) ((iz + 0.5) / NZB * 20_000_000);
            for (int ix = 0; ix < NXB; ix++) {
                int x = (int) ((ix + 0.5) / NXB * 24_000_000) - 12_000_000;
                boolean land = PlateField.isLandWithCell(x, z, SD, CELL);
                V2BiomeField.Sample s = V2BiomeField.sample(x, z, SEED, land);
                int k = s.kind.ordinal();
                if (k < 0 || k >= NK) continue;
                tot[k]++; n++;
                // 纪律：扫描型探针必须能看到进展（stdout 重定向时会缓冲，所以写进 rep 并 flush）
                if (n % 50 == 0) { say(String.format(LF, "    进度 ④-1: %d / %d 点", n, NXB * NZB)); rep.flush(); }
                if (land) nLand++;
                int b = (int) (Math.toDegrees(Math.abs(com.EyeOfHarmonyBuffer.sim.world.WorldContract.latOf(z))) / 10.0);
                if (b > 8) b = 8;
                band[b][k]++;
            }
        }
        say(String.format(LF, "  样本 n = %d（其中陆地 %d = %.1f%%）", n, nLand, n > 0 ? 100.0 * nLand / n : 0));
        if (n == 0) { say("  **样本为 0 —— 探针无效（纪律：扫描型探针必须在样本为 0 时报 FAIL）**"); rep.close(); return; }
        say("");
        StringBuilder hdr = new StringBuilder(String.format(LF, "  %-14s %8s", "Kind", "占比%"));
        for (int b = 0; b < 9; b++) hdr.append(String.format(LF, "%8s", (b * 10) + "-" + (b * 10 + 10)));
        say(hdr.toString());
        int shown = 0;
        for (int k = 0; k < NK; k++) {
            if (tot[k] == 0) continue;
            StringBuilder sb = new StringBuilder(String.format(LF, "  %-14s %8.2f", V2BiomeSelectKind.name(k), 100.0 * tot[k] / n));
            for (int b = 0; b < 9; b++) {
                int bt = 0; for (int kk = 0; kk < NK; kk++) bt += band[b][kk];
                sb.append(String.format(LF, "%8.1f", bt > 0 ? 100.0 * band[b][k] / bt : 0.0));
            }
            say(sb.toString());
            shown++;
        }
        say(String.format(LF, "  （非零群系 %d / %d 类）", shown, NK));

        // ---------- ④-2 岸线带 ----------
        say("");
        say("④-2 岸线带（离岸 0~30 km）各群系占比 —— D18 的靶心");
        java.util.ArrayList<int[]> pts = new java.util.ArrayList<>();
        for (int z = -8_000_000; z <= 8_000_000 && pts.size() < 120; z += 613_000) {
            for (int x = -12_000_000; x <= 12_000_000 && pts.size() < 120; x += 3_000) {
                if (!PlateField.isLandWithCell(x, z, SD, CELL)) continue;
                int cd = coastDist(x, z, 30_000);
                if (cd != Integer.MIN_VALUE && -cd <= 30_000) pts.add(new int[]{x, z, cd});
            }
        }
        say(String.format(LF, "  岸线带样本 = %d（陆点且离岸 <= 30 km）   （样本为 0 则本段无效）", pts.size()));
        if (pts.size() > 0) {
            int[] ct = new int[NK];
            for (int[] p : pts) ct[V2BiomeField.sample(p[0], p[1], SEED, true).kind.ordinal()]++;
            for (int k = 0; k < NK; k++) {
                if (ct[k] == 0) continue;
                say(String.format(LF, "    %-14s %6.1f%%  (n=%d)", V2BiomeSelectKind.name(k), 100.0 * ct[k] / pts.size(), ct[k]));
            }
        }

        // ---------- ④-3 相邻格点 Kind 变化率（粗网格，只作量级对照） ----------
        say("");
        say("④-3 相邻 1000 km 格点的 Kind 变化率（粗网格量级对照；细网格版记为待办）");
        int chg = 0, pair = 0;
        for (int iz = 0; iz < NZB; iz++) {
            int z = (int) ((iz + 0.5) / NZB * 20_000_000);
            int prev = -1;
            for (int ix = 0; ix < NXB; ix++) {
                int x = (int) ((ix + 0.5) / NXB * 24_000_000) - 12_000_000;
                int k = V2BiomeField.sample(x, z, SEED, PlateField.isLandWithCell(x, z, SD, CELL)).kind.ordinal();
                if (prev >= 0) { pair++; if (k != prev) chg++; }
                prev = k;
            }
        }
        say(String.format(LF, "  相邻对 %d，其中 Kind 变化 %d = %.1f%%（1000 km 尺度上，这个数应当不低）", pair, chg, pair > 0 ? 100.0 * chg / pair : 0));
        say("");
        say("  ⚠ 本探针**没有**覆盖「群系硬边」的细尺度普查（需要 250 m 级网格，代价是数百倍）—— 记为待办。");
        rep.close();
        System.out.println("JAVA_EXIT=0");
    }

    /** 只为把 Kind 的名字与数量取出来，避免在探针里硬编码枚举顺序。 */
    static final class V2BiomeSelectKind {
        static int n;
        static String[] names;
        static void init() {
            com.EyeOfHarmonyBuffer.space.talos.chunk.world.V2BiomeSelect.Kind[] vs =
                com.EyeOfHarmonyBuffer.space.talos.chunk.world.V2BiomeSelect.Kind.values();
            n = vs.length; names = new String[n];
            for (int i = 0; i < n; i++) names[i] = vs[i].name();
        }
        static String name(int k) { return k >= 0 && k < n ? names[k] : "?"; }
    }
}
