package probe;

import com.EyeOfHarmonyBuffer.probeorig.OrigMountainV2;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.MountainLayerV2;

/**
 * P170：任务 C（MountainLayerV2 的 NZ 200_000/CELL=800 -> BELT_CELL_Z/CELL=200 + Z halo）
 * 的逐点等价性对照。
 *
 * 对照实现 OrigMountainV2 = 修复前的同一份代码（同包改名，放在 probeorig），
 * 两者在同一 JVM 里对同一批点求值，逐位比较 auth / uplift / slope01。
 * 分带统计：fz 是"距 belt 格 Z 边界的格数"，deep = 距上下边界都 ≥ 2.5 格。
 */
public class P170 {

    static final int SEED = 1022228679;
    static final int CELL = 250;
    static final int NX = 200, NZ = 200;

    static int[] n = new int[8];
    static int[] nd = new int[8];
    static double[] dAuth = new double[8];
    static double[] dUp = new double[8];
    static double[] dSlope = new double[8];
    static final String[] NAME = {"z.0", "z.1", "z.2", "z.deep", "xy.deep", "xy.deep(nonzero)", "all", "untouched?"};

    static int band(double f) {
        if (f < 0.5) return 0;
        if (f < 1.5) return 1;
        if (f < 2.5) return 2;
        return 3;
    }

    static void add(int b, double da, double du, double ds, boolean diff) {
        n[b]++;
        if (diff) nd[b]++;
        if (Math.abs(da) > dAuth[b]) dAuth[b] = Math.abs(da);
        if (Math.abs(du) > dUp[b]) dUp[b] = Math.abs(du);
        if (Math.abs(ds) > dSlope[b]) dSlope[b] = Math.abs(ds);
    }

    /** 关掉 carve 再比一次：用于确认"差异是否全部来自河道下切（D8 汇水域）"。 */
    static void scan(int cellX, int cellZ, boolean withCarve) {
        OrigMountainV2.Tune.carveEnabled = withCarve;
        MountainLayerV2.Tune.carveEnabled = withCarve;
        OrigMountainV2.clearCache();
        MountainLayerV2.clearCache();
        java.util.Arrays.fill(n, 0);
        java.util.Arrays.fill(nd, 0);
        java.util.Arrays.fill(dAuth, 0.0);
        java.util.Arrays.fill(dUp, 0.0);
        java.util.Arrays.fill(dSlope, 0.0);
        long diffs = 0;
        for (int j = -1; j <= NZ; j++) {
            double z = cellZ * 50000.0 + j * CELL + CELL * 0.5;
            for (int i = 0; i < NX; i++) {
                double x = cellX * 50000.0 + i * CELL + CELL * 0.5;
                int xi = (int) x, zi = (int) z;
                double oa = OrigMountainV2.auth(xi, zi, SEED);
                double ou = OrigMountainV2.uplift(xi, zi, SEED);
                double os = OrigMountainV2.slope01(xi, zi, SEED);
                double na = MountainLayerV2.auth(xi, zi, SEED);
                double nu = MountainLayerV2.uplift(xi, zi, SEED);
                double ns = MountainLayerV2.slope01(xi, zi, SEED);
                boolean diff = oa != na || ou != nu || os != ns;
                if (diff) diffs++;
                int b = band(j + 0.5);
                add(b, na - oa, nu - ou, ns - os, diff);
                add(6, na - oa, nu - ou, ns - os, diff);
                if (i >= 2 && i < NX - 2 && j >= 2 && j < NZ - 2) {
                    add(4, na - oa, nu - ou, ns - os, diff);
                    if (ou > 0.0 || oa > 0.0) {
                        add(5, na - oa, nu - ou, ns - os, diff);
                    }
                }
            }
        }
        System.out.println("[P170] carveEnabled=" + withCarve + " -> 差异点 " + diffs);
        for (int b = 0; b < 7; b++) {
            if (n[b] == 0) continue;
            System.out.printf("[P170]   %-18s n=%6d 差异=%6d (%.2f%%)  max|dAuth|=%.6f max|dUplift|=%.6f max|dSlope|=%.8f%n",
                NAME[b], n[b], nd[b], 100.0 * nd[b] / n[b], dAuth[b], dUp[b], dSlope[b]);
        }
    }

    public static void main(String[] args) {
        int cellX = 0, cellZ = 5;
        if (args.length >= 2) {
            cellX = Integer.parseInt(args[0]);
            cellZ = Integer.parseInt(args[1]);
        }
        System.out.println("=== P170 任务C 等价性对照  seed=" + SEED + "  belt 格=(" + cellX + "," + cellZ + ") ===");
        scan(cellX, cellZ, false);
        System.out.println("[P170] ---- 打开 carve（生产口径）----");
        scan(cellX, cellZ, true);
        OrigMountainV2.Tune.carveEnabled = true;
        MountainLayerV2.Tune.carveEnabled = true;
        OrigMountainV2.clearCache();
        MountainLayerV2.clearCache();

        double maxAuth = 0, maxUp = 0;
        long diffs = 0;
        // 覆盖查询会用到的整片 lattice（j 从 -1 到 NZ，i 从 0 到 NX-1）
        for (int j = -1; j <= NZ; j++) {
            double z = cellZ * 50000.0 + j * CELL + CELL * 0.5;
            for (int i = 0; i < NX; i++) {
                double x = cellX * 50000.0 + i * CELL + CELL * 0.5;
                int xi = (int) x, zi = (int) z;
                double oa = OrigMountainV2.auth(xi, zi, SEED);
                double ou = OrigMountainV2.uplift(xi, zi, SEED);
                double os = OrigMountainV2.slope01(xi, zi, SEED);
                double na = MountainLayerV2.auth(xi, zi, SEED);
                double nu = MountainLayerV2.uplift(xi, zi, SEED);
                double ns = MountainLayerV2.slope01(xi, zi, SEED);
                boolean diff = oa != na || ou != nu || os != ns;
                if (diff) diffs++;
                if (oa > maxAuth) maxAuth = oa;
                if (ou > maxUp) maxUp = ou;
                int b = band(j + 0.5);
                add(b, na - oa, nu - ou, ns - os, diff);
                add(6, na - oa, nu - ou, ns - os, diff);
                if (i >= 2 && i < NX - 2 && j >= 2 && j < NZ - 2) {
                    add(4, na - oa, nu - ou, ns - os, diff);
                    if (ou > 0.0 || oa > 0.0) {
                        add(5, na - oa, nu - ou, ns - os, diff);
                    }
                }
            }
        }
        System.out.printf("[P170] 网格 %dx%d 点，差异点 %d，maxAuth=%.3f maxUplift=%.3f%n", NX, NZ + 2, diffs, maxAuth, maxUp);
        for (int b = 0; b < 7; b++) {
            if (n[b] == 0) continue;
            System.out.printf("[P170] %-18s n=%6d 差异=%6d (%.2f%%)  max|dAuth|=%.6f max|dUplift|=%.6f max|dSlope|=%.8f%n",
                NAME[b], n[b], nd[b], 100.0 * nd[b] / n[b], dAuth[b], dUp[b], dSlope[b]);
        }

        // 耗时对比（各解一次同一格）
        OrigMountainV2.clearCache();
        long t0 = System.nanoTime();
        OrigMountainV2.ensure(SEED);
        long msOld = (System.nanoTime() - t0) / 1_000_000L;
        MountainLayerV2.clearCache();
        t0 = System.nanoTime();
        MountainLayerV2.ensure(SEED);
        long msNew = (System.nanoTime() - t0) / 1_000_000L;
        System.out.println("[P170] 同格首次求解耗时：旧(C前)=" + msOld + "ms  新(C后)=" + msNew + "ms");
    }
}
