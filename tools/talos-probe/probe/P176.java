package probe;

import com.EyeOfHarmonyBuffer.space.talos.chunk.world.LandformField;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.MountainLayerV2;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.V2BiomeField;

/**
 * P176：H-1 / H-2 —— 查明并量化"belt 格首半格重复带"。
 *
 *  H-1：MountainLayerV2.bilinear 在 fx = -0.5 时把 i 从 -1 夹到 0 却保留 tx = 0.5
 *       → 算成 (列0,列1) 的 50/50 平均 → 与 fx = +0.5 逐位同值 → 每 50km 一条 250 格宽重复带。
 *  H-2：LandformField / V2BiomeField 的 halo+clamp 写法是否也有同类重复带。
 *
 * 输出：
 *  [A] 山层：x 与 x+250 逐位相同的比例（X 重复带）、z 与 z+250（Z 重复带）
 *  [B] 山层：细采样（步长 50）跨 belt 格首的序列 —— 看是否"单调变化、不再折回"
 *  [C] 山层：belt 格内部（离边界 ≥2 格）指纹（改动前后必须逐位一致）
 *  [D] 受影响面积实测比例（x 与 x+250 相同的采样点占比）
 *  [E] LandformField / V2BiomeField：同样的"x 与 x+250 是否逐位相同"检查
 */
public class P176 {

    static final int SEED = 1022228679;
    static final int MCELL = 50_000, CELL = 250;
    static final int TILE_X = 100_000;

    public static void main(String[] args) {
        int z = 250_250;                     // belt 格 (·,5) 内部：有山带
        // ---------- [A] 山层 X/Z 重复带比例 ----------
        int nX = 0, sameX = 0, nzX = 0, sameNzX = 0, nZ = 0, sameZ = 0, nzZ = 0, sameNzZ = 0;
        for (int x = 0; x <= 200_000; x += 50) {
            double[] a = f(x, z), b = f(x + CELL, z);
            nX++;
            boolean nz = a[0] != 0 || a[1] != 0 || b[0] != 0 || b[1] != 0;
            if (eq(a, b)) {
                sameX++;
                if (nz) sameNzX++;
            }
            if (nz) nzX++;
        }
        for (int zz = 250_000; zz <= 250_500; zz += 50) {
            double[] a = f(25_000, zz), b = f(25_000, zz + CELL);
            nZ++;
            boolean nz = a[0] != 0 || a[1] != 0 || b[0] != 0 || b[1] != 0;
            if (eq(a, b)) {
                sameZ++;
                if (nz) sameNzZ++;
            }
            if (nz) nzZ++;
        }
        System.out.printf("[P176-A] 山层 x 与 x+250 逐位相同：%d/%d (%.2f%%)；其中非零点 %d/%d (%.2f%%)%n",
            sameX, nX, 100.0 * sameX / nX, sameNzX, nzX, nzX > 0 ? 100.0 * sameNzX / nzX : 0.0);
        System.out.printf("[P176-A] 山层 z 与 z+250 逐位相同：%d/%d (%.2f%%)；其中非零点 %d/%d (%.2f%%)%n",
            sameZ, nZ, 100.0 * sameZ / nZ, sameNzZ, nzZ, nzZ > 0 ? 100.0 * sameNzZ / nzZ : 0.0);

        // ---------- [B] 跨 belt 格首的细采样 ----------
        System.out.println("[P176-B] 跨 x=0（belt 格首）步长 50 的 uplift：");
        StringBuilder sb = new StringBuilder();
        for (int x = -100; x <= 600; x += 50) {
            sb.append(String.format("%d:%.6f ", x, f(x, z)[1]));
        }
        System.out.println("[P176-B]   " + sb);
        System.out.println("[P176-B] x=0 与 x=250 是否逐位相同：" + eq(f(0, z), f(250, z))
            + "；x=0 与 x=50 相同：" + eq(f(0, z), f(50, z)));

        // ---------- [C] belt 格内部指纹（离边界 ≥2 格） ----------
        long hInt = 0xCBF29CE484222325L, hBand = 0xCBF29CE484222325L;
        int nInt = 0, nBand = 0;
        for (int zz = 250_000; zz < 300_000; zz += 250) {
            for (int x = 0; x < 50_000; x += 250) {
                int cx = MountainLayerV2.cellOfX(x), cz = MountainLayerV2.cellOfZ(zz);
                double fx = (x - cx * (double) MCELL) / CELL - 0.5;
                double fz = (zz - cz * (double) MCELL) / CELL - 0.5;
                double[] v = f(x, zz);
                long h = mix(mix(mix(0xCBF29CE484222325L, v[0]), v[1]), v[2]);
                if (fx >= 2.0 && fz >= 2.0) {
                    hInt = mixh(hInt, h);
                    nInt++;
                }
                if (fx < 0.5 || fz < 0.5) {
                    hBand = mixh(hBand, h);
                    nBand++;
                }
            }
        }
        System.out.printf("[P176-C] 内部(离边界≥2格) n=%d hash=%016X | 边界带(fx或fz<0.5) n=%d hash=%016X%n",
            nInt, hInt, nBand, hBand);

        // ---------- [D] 受影响面积 ----------
        System.out.printf("[P176-D] 理论上受 x-clamp 影响的面积 = 250/50000 = %.3f%%（X 首半格）%n",
            100.0 * CELL / MCELL);

        // ---------- [E] LandformField / V2BiomeField 的同类检查 ----------
        int nL = 0, sameL = 0, nB = 0, sameB = 0;
        for (int x = 0; x <= 200_000; x += 50) {
            // 注意：Sample 是 ThreadLocal 复用的同一对象 → 必须先把值抄出来再采第二个点
            double[] l0 = lf(x);
            double[] l1 = lf(x + CELL);
            nL++;
            boolean same = true;
            for (int q = 0; q < l0.length; q++) {
                if (l0[q] != l1[q]) {
                    same = false;
                    break;
                }
            }
            if (same) sameL++;
            Object[] b0 = bf(x);
            Object[] b1 = bf(x + CELL);
            nB++;
            if (b0[0] == b1[0] && (Double) b0[1] == (Double) b1[1] && (Double) b0[2] == (Double) b1[2]) sameB++;
        }
        System.out.printf("[P176-E] LandformField x 与 x+250 逐位相同：%d/%d (%.2f%%) | V2BiomeField：%d/%d (%.2f%%)%n",
            sameL, nL, 100.0 * sameL / nL, sameB, nB, 100.0 * sameB / nB);
        System.out.println("[P176-E] （LandformField 的 halo 是 i<-1 才夹取且同时置 tx=0；V2BiomeField 同款 —— 预期没有重复带）");
    }

    static double[] lf(int x) {
        LandformField.Sample s = LandformField.sample(x, 275_000, SEED);
        return new double[]{s.low, s.hill, s.plat, s.mtn, s.peak, s.mtnAmt, s.h0};
    }

    static Object[] bf(int x) {
        V2BiomeField.Sample s = V2BiomeField.sample(x, 275_000, SEED);
        return new Object[]{s.kind, s.bias, s.scale};
    }

    static double[] f(int x, int z) {
        return new double[]{
            MountainLayerV2.auth(x, z, SEED),
            MountainLayerV2.uplift(x, z, SEED),
            MountainLayerV2.slope01(x, z, SEED)};
    }

    static boolean eq(double[] a, double[] b) {
        return a[0] == b[0] && a[1] == b[1] && a[2] == b[2];
    }

    static long mix(long h, double v) {
        h ^= Double.doubleToRawLongBits(v);
        h *= 0x100000001B3L;
        h ^= h >>> 29;
        return h;
    }

    static long mixh(long h, long v) {
        h ^= v;
        h *= 0x100000001B3L;
        h ^= h >>> 29;
        return h;
    }
}
