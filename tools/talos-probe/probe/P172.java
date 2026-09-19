package probe;

import com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer.PeriodicNoise;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.V2TerrainGen;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.OrographyField;

/**
 * P172：任务 F（塑形噪声在 Z 上不再每 1M 复读）的实测证据。
 *
 * 1) 直接对照：同一 (x, z) 与 (x, z+1M) 的梯度噪声
 *      - 旧口径 nz = +格数（lattice 取模）→ 两处【逐位相同】
 *      - 新口径 nz = -格数（不折叠）    → 两处【不同】
 * 2) 端到端：V2TerrainGen 的生产入口（基础高度 / 山体纹理 / 山体细节 / 海床深度）
 *      在 (x, z) 与 (x, z+1M) 的取值对比（多组采样统计差异比例与平均 |Δ|）
 */
public class P172 {

    static final int SEED = 1022228679;
    static final int ZC = 1_000_000;

    public static void main(String[] args) {
        System.out.println("=== P172 任务F：Z 方向不再周期复读  seed=" + SEED + " ===");
        System.out.println("[P172] INFINITE_X=" + PeriodicNoise.INFINITE_X + "  INFINITE_Z=" + PeriodicNoise.INFINITE_Z);

        // ---------- 1) 格数口径 ----------
        double[] freqs = {1.0 / 8000, 1.0 / 3000, 1.0 / 1200, 1.0 / 1100, 1.0 / 420, 1.0 / 260};
        String[] names = {"大陆 8k", "低 3k", "海床 1.2k", "中 1.1k", "细节 420", "高 260"};
        System.out.println("[P172] 频段格数（cellsXFromFreq / cellsZFromFreq，负 = 该轴不折叠）：");
        for (int i = 0; i < freqs.length; i++) {
            System.out.printf("[P172]   %-9s X=%5d  Z=%5d%n", names[i],
                PeriodicNoise.cellsXFromFreq(freqs[i]), PeriodicNoise.cellsZFromFreq(freqs[i]));
        }

        // ---------- 2) 直接 A/B ----------
        System.out.println("[P172] 同一 (x,z) 与 (x,z+1M) 的噪声值：");
        int[] xs = {12_345, 300_777, 1_234_567};
        int[] zs = {250_000, 3_700_000};
        for (int i = 0; i < freqs.length; i++) {
            double fq = freqs[i];
            int nx = PeriodicNoise.cellsXFromFreq(fq);
            int nzOld = Math.abs(PeriodicNoise.cellsZFromFreq(fq));   // 旧口径：折叠
            int nzNew = PeriodicNoise.cellsZFromFreq(fq);             // 新口径：不折叠
            double maxOld = 0, maxNew = 0, sumNew = 0;
            int n = 0;
            for (int x : xs) {
                for (int z : zs) {
                    double o0 = PeriodicNoise.gradientFbm2(101L, x, z, nx, nzOld, 3);
                    double o1 = PeriodicNoise.gradientFbm2(101L, x, z + ZC, nx, nzOld, 3);
                    double n0 = PeriodicNoise.gradientFbm2(101L, x, z, nx, nzNew, 3);
                    double n1 = PeriodicNoise.gradientFbm2(101L, x, z + ZC, nx, nzNew, 3);
                    maxOld = Math.max(maxOld, Math.abs(o1 - o0));
                    maxNew = Math.max(maxNew, Math.abs(n1 - n0));
                    sumNew += Math.abs(n1 - n0);
                    n++;
                }
            }
            System.out.printf("[P172]   %-9s 旧(折叠) max|f(z+1M)-f(z)|=%.8f   新(不折叠) max=%.6f  mean=%.6f%n",
                names[i], maxOld, maxNew, sumNew / n);
        }

        // ---------- 3) 端到端（V2TerrainGen 生产入口） ----------
        System.out.println("[P172] 端到端：V2TerrainGen 在 (x,z) 与 (x,z+1M) 的取值：");
        double[] bp = new double[2];
        int sameH = 0, nH = 0, sameT = 0, nT = 0, sameD = 0, nD = 0, sameS = 0, nS = 0;
        double sumH = 0, sumT = 0, sumD = 0, sumS = 0, mxH = 0;
        for (int x = 20_000; x <= 380_000; x += 40_000) {
            for (int z = 150_000; z <= 750_000; z += 120_000) {
                OrographyField.OroSample o0 = OrographyField.sample(x, z, SEED);
                OrographyField.OroSample o1 = OrographyField.sample(x, z + ZC, SEED);
                V2TerrainGen.baseAndPlain(x, z, SEED, 64, o0, 0.5, 0.5, bp);
                double h0 = bp[0];
                V2TerrainGen.baseAndPlain(x, z + ZC, SEED, 64, o1, 0.5, 0.5, bp);
                double h1 = bp[0];
                double dH = Math.abs(h1 - h0);
                nH++;
                if (dH < 1e-9) sameH++;
                sumH += dH;
                if (dH > mxH) mxH = dH;

                double t0 = V2TerrainGen.mountainTexture(V2TerrainGen.textureSeed(SEED), x, z);
                double t1 = V2TerrainGen.mountainTexture(V2TerrainGen.textureSeed(SEED), x, z + ZC);
                nT++;
                sumT += Math.abs(t1 - t0);
                if (Math.abs(t1 - t0) < 1e-12) sameT++;

                double d0 = V2TerrainGen.mountainDetail(x, z, SEED, 1.0);
                double d1 = V2TerrainGen.mountainDetail(x, z + ZC, SEED, 1.0);
                nD++;
                sumD += Math.abs(d1 - d0);
                if (Math.abs(d1 - d0) < 1e-12) sameD++;

                double s0 = V2TerrainGen.seaDepthBlocks(x, z, SEED);
                double s1 = V2TerrainGen.seaDepthBlocks(x, z + ZC, SEED);
                nS++;
                sumS += Math.abs(s1 - s0);
                if (Math.abs(s1 - s0) < 1e-12) sameS++;
            }
        }
        System.out.printf("[P172] 基础高度  baseAndPlain: 逐位相同 %d/%d，平均|Δh|=%.4f 块，最大|Δh|=%.4f 块%n",
            sameH, nH, sumH / nH, mxH);
        System.out.printf("[P172] 山体纹理  mountainTexture: 逐位相同 %d/%d，平均|Δ|=%.6f%n", sameT, nT, sumT / nT);
        System.out.printf("[P172] 山体细节  mountainDetail:  逐位相同 %d/%d，平均|Δ|=%.6f 块%n", sameD, nD, sumD / nD);
        System.out.printf("[P172] 海床深度  seaDepthBlocks:  逐位相同 %d/%d，平均|Δ|=%.6f 块%n", sameS, nS, sumS / nS);
        System.out.println("[P172] （旧的折叠口径下，这四项都会是 100%% 逐位相同）");
    }
}
