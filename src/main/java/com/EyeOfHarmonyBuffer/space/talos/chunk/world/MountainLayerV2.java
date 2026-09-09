package com.EyeOfHarmonyBuffer.space.talos.chunk.world;

import com.EyeOfHarmonyBuffer.Config.TalosConfig.V2TerrainConfigSection;
import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.GlobalCirculation;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.OrographyField;
import com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer.TerrainNoise;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * V2 山层（过程驱动，替换 DLA）：抬升场 + 河道下切（流水侵蚀）+ 权威权重。
 *
 * 架构与 RelaxedClimate 同款：按种子离线求解一次（后台线程，~1-3s），缓存粗网格，
 * 运行时双线性查询。域 = 400k(X) x 200k(Z) 环面（与气候层一致，查询前折叠坐标）。
 *
 * 输出：
 *   uplift(x,z) —— 山带抬升量（blocks，已侵蚀；叠加在 plain 之上）
 *   auth(x,z)   —— 山层权威权重 w [0,1]（山带核心 1 → 边界 0）
 *   slope01     —— |∇uplift| / 0.10，供块级细节与雪-岩判定
 *
 * 合成（由 ChunkProviderTalos2 完成）：
 *   h = plain + (1-w)*mtnComp + w*uplift        // plain/mtnComp 来自 V2TerrainGen 分解
 *
 * 侵蚀模型：D8 汇水面积 + 河道下切 h -= K·A^0.55·S。
 *   关键：下切按汇水面积开闸（aGate0..aGate1）——山脊/峰顶（A≈1）几乎不削，
 *   所以峰高 ≈ 抬升场设计值，可被 axialAmp 直接控制；谷地则被下切压低 → 底盘更低。
 */
public final class MountainLayerV2 {

    private MountainLayerV2() {}

    /** 粗网格分辨率（blocks，250m → 1600x800）。 */
    public static final int CELL = 250;
    /** 网格尺寸：400k x 200k。 */
    public static final int NX = 400_000 / CELL;
    public static final int NZ = 200_000 / CELL;

    /** 每世界山带数量（种子决定位置/走向/尺寸）。 */
    private static final int BELTS = 5;

    /**
     * 调参入口：默认值即生产参数。
     * 探针可改写后调用 {@link #clearCache()} 重新求解，用于快速扫参。
     */
    public static final class Tune {
        /** 下切预算（blocks）：河道满额时的下切量。 */
        public static double carve = 50.0;
        /** 下切闸门（汇水面积，单位 250m 格）：A ≤ aGateLo 完全不切，A ≥ aGateHi 满额。 */
        public static double aGateLo = 100.0, aGateHi = 4000.0;
        /** 下切不得超过本格抬升的比例（谷底不穿底盘）。 */
        public static double carveMaxFrac = 0.60;
        /** 下切后的热力平滑轮数 / 系数。 */
        public static int smoothPass = 2;
        public static double thermal = 0.35;
        /** 峰化曲线指数：u' = umax·(u/umax)^γ，γ&gt;1 压中段、保顶部。 */
        public static double peakifyGamma = 1.35;
        /** 山带包络基础抬升范围（底盘）。 */
        public static double ampLo = 4.0, ampHi = 10.0;
        /** 中尺度脊线纹理振幅（blocks）：0.5~3km ridged 噪声，山带内部起伏的主来源。 */
        public static double midAmp = 450.0;
        /** 纹理锐化指数：tex^p，p&gt;1 → 大部分区域压低、山脊变窄变尖。 */
        public static double texPow = 2.5;
        /** 主脊轴额外抬升范围（峰顶高度）。 */
        public static double axialLo = 60.0, axialHi = 85.0;
        /** 下切用的包络下限：env ≥ envFloor 时满额生效（env 在坡面上衰减很快）。 */
        public static double envFloor = 0.25;
        /** 底盘下压量（× belt 包络，可为负值以压低山带底盘）。 */
        public static double floorDrop = 18.0;
        /** 打印求解诊断。 */
        public static boolean debug = false;
        /** 下切总开关（诊断用）。 */
        public static boolean carveEnabled = true;
    }

    private static final ConcurrentHashMap<Integer, Layer> CACHE =
        new ConcurrentHashMap<Integer, Layer>();

    /** 当前求解的种子（供纹理与世界种子对齐；仅在 solve() 期间使用）。 */
    private static int SOLVE_SEED = 0;

    /** 一条山带。 */
    private static final class Belt {
        double cx, cz;          // 中心（域内绝对坐标）
        double ca, sa;          // 走向单位向量
        double halfL, halfW;    // 半长 / 半宽
        double amp;             // 山带包络基础抬升
        double axialAmp;        // 主脊轴额外抬升
        double meanderAmp;      // 主脊蜿蜒幅度
        double meanderFreq;
        double peakPhase1, peakPhase2;
    }

    /** 一个种子的解。 */
    private static final class Layer {
        final float[] uplift = new float[NX * NZ];
        final float[] auth = new float[NX * NZ];
        /** 坡度场（|∇uplift| / 0.10，0..1），供块级细节与雪-岩判定。 */
        final float[] slope = new float[NX * NZ];
    }

    public static boolean isEnabled() {
        return V2TerrainConfigSection.mountainV2Enabled;
    }

    /** 清空解缓存（探针扫参用）。 */
    public static void clearCache() {
        CACHE.clear();
    }

    /** 后台预热：确保该种子的山层已求解（重复调用直接返回）。 */
    public static void ensure(int worldSeedInt) {
        if (!isEnabled()) {
            return;
        }
        if (CACHE.containsKey(worldSeedInt)) {
            return;
        }
        CACHE.computeIfAbsent(worldSeedInt, MountainLayerV2::solve);
    }

    private static Layer layer(int worldSeedInt) {
        if (!isEnabled()) {
            return null;
        }
        Layer l = CACHE.get(worldSeedInt);
        if (l != null) {
            return l;
        }
        if (CACHE.size() > 2) {
            CACHE.clear();   // 多世界保护：最多缓存 3 个种子
        }
        return CACHE.computeIfAbsent(worldSeedInt, MountainLayerV2::solve);
    }

    // ==================== 查询 ====================

    /** 山带抬升（blocks，已侵蚀；山带外 0）。 */
    public static double uplift(int x, int z, int worldSeedInt) {
        Layer l = layer(worldSeedInt);
        if (l == null) {
            return 0.0;
        }
        return bilinear(l.uplift, x, z);
    }

    /** 山层权威权重 w [0,1]。 */
    public static double auth(int x, int z, int worldSeedInt) {
        Layer l = layer(worldSeedInt);
        if (l == null) {
            return 0.0;
        }
        return bilinear(l.auth, x, z);
    }

    /** 坡度 [0,1]（0=平，1≈陡坡）。 */
    public static double slope01(int x, int z, int worldSeedInt) {
        Layer l = layer(worldSeedInt);
        if (l == null) {
            return 0.0;
        }
        return bilinear(l.slope, x, z);
    }

    private static double bilinear(float[] g, int wx, int wz) {
        double fx = GlobalCirculation.foldX(wx) / (double) CELL - 0.5;
        double fz = GlobalCirculation.foldZ(wz) / (double) CELL - 0.5;
        int i = (int) Math.floor(fx), j = (int) Math.floor(fz);
        double tx = fx - i, tz = fz - j;
        i = ((i % NX) + NX) % NX;
        j = ((j % NZ) + NZ) % NZ;
        int i1 = (i + 1) % NX, j1 = (j + 1) % NZ;
        double v00 = g[j * NX + i], v10 = g[j * NX + i1];
        double v01 = g[j1 * NX + i], v11 = g[j1 * NX + i1];
        return (v00 * (1 - tx) + v10 * tx) * (1 - tz) + (v01 * (1 - tx) + v11 * tx) * tz;
    }

    // ==================== 求解 ====================

    private static Layer solve(int seed) {
        long t0 = System.nanoTime();
        SOLVE_SEED = seed;
        Belt[] belts = layout(seed);

        Layer layer = new Layer();
        float[] env = new float[NX * NZ];       // 山带包络（0..1），下切只在包络内生效
        // 1) 抬升场 + 权威场
        for (int j = 0; j < NZ; j++) {
            double z = j * CELL + CELL * 0.5;
            for (int i = 0; i < NX; i++) {
                double x = i * CELL + CELL * 0.5;
                double sumW = 0.0, sumUp = 0.0, sumEnv = 0.0, maxW = 0.0;
                for (Belt b : belts) {
                    double w = beltWeight(b, x, z);
                    if (w <= 0.0) {
                        continue;
                    }
                    double up = beltUplift(b, x, z, w);
                    sumW += w;
                    sumUp += w * up;
                    sumEnv += w * beltEnvelope(b, x, z);
                    if (w > maxW) {
                        maxW = w;
                    }
                }
                int k = j * NX + i;
                layer.auth[k] = (float) maxW;
                layer.uplift[k] = (float) (sumW > 0 ? sumUp / Math.max(1.0, sumW) : 0.0);
                env[k] = (float) (sumW > 0 ? sumEnv / Math.max(1.0, sumW) : 0.0);
            }
        }
        if (Tune.debug) {
            double mn = 1e9, mx = -1e9, s1 = 0, s2 = 0;
            int c = 0;
            for (int j = 0; j < NZ; j += 2) {
                for (int i = 0; i < NX; i += 2) {
                    double x = i * CELL + CELL * 0.5, z = j * CELL + CELL * 0.5;
                    for (Belt b : belts) {
                        if (beltEnvelope(b, x, z) < 0.5) continue;
                        double t = ridgeTexture(b, x, z);
                        if (t < mn) mn = t;
                        if (t > mx) mx = t;
                        s1 += t; s2 += t * t; c++;
                    }
                }
            }
            double mm = s1 / c;
            System.out.printf("[tex] n=%d min=%.3f max=%.3f mean=%.3f sd=%.3f  midAmp=%.0f%n",
                c, mn, mx, mm, Math.sqrt(Math.max(0, s2 / c - mm * mm)), Tune.midAmp);
            double vmn = 1e9, vmx = -1e9, v1 = 0, v2 = 0;
            int vc = 0;
            for (int k = 0; k < layer.uplift.length; k++) {
                if (layer.auth[k] < 0.6f) continue;
                double v = layer.uplift[k];
                if (v < vmn) vmn = v;
                if (v > vmx) vmx = v;
                v1 += v; v2 += v * v; vc++;
            }
            double vm = v1 / vc;
            System.out.printf("[pre-carve uplift] n=%d min=%.0f max=%.0f mean=%.0f sd=%.1f%n",
                vc, vmn, vmx, vm, Math.sqrt(Math.max(0, v2 / vc - vm * vm)));
        }
        // 2) 河道下切（汇水面积分档；山脊/峰顶 A≈1 不切 → 峰高 = 抬升场设计值）
        if (Tune.carveEnabled) {
            carve(layer.uplift, env);
        }
        // 2b) 峰化曲线：u' = uMax·(u/uMax)^γ —— 压中段底盘、保顶部，峰更突出
        float umax = 0f;
        for (float v : layer.uplift) {
            if (v > umax) umax = v;
        }
        if (umax > 1f) {
            for (int k = 0; k < layer.uplift.length; k++) {
                float v = layer.uplift[k];
                if (v <= 0f) continue;
                layer.uplift[k] = (float) (umax * Math.pow(v / umax, Tune.peakifyGamma));
            }
        }
        // 3) 坡度场（|∇uplift| / 0.10，0..1）
        for (int j = 0; j < NZ; j++) {
            for (int i = 0; i < NX; i++) {
                int im = ((i - 1) % NX + NX) % NX, ip = (i + 1) % NX;
                int jm = ((j - 1) % NZ + NZ) % NZ, jp = (j + 1) % NZ;
                double gx = (layer.uplift[j * NX + ip] - layer.uplift[j * NX + im]) / (2.0 * CELL);
                double gz = (layer.uplift[jp * NX + i] - layer.uplift[jm * NX + i]) / (2.0 * CELL);
                double s = Math.sqrt(gx * gx + gz * gz) / 0.10;   // 10% 坡度 → 1
                layer.slope[j * NX + i] = (float) (s > 1.0 ? 1.0 : s);
            }
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        System.out.println("[MountainV2] seed=" + seed + " solved in " + ms + "ms  belts=" + belts.length
            + "  upMax=" + String.format("%.0f", umax));
        return layer;
    }

    /** 山带布局：中心尽量落在内陆，沿轴裁剪保证主要在山地上。 */
    private static Belt[] layout(int seed) {
        Random rng = new Random(seed * 6364136223846793005L + 1442695040888963407L);
        Belt[] out = new Belt[BELTS];
        int made = 0;
        for (int attempt = 0; attempt < 400 && made < BELTS; attempt++) {
            double cx = rng.nextDouble() * 400_000.0;
            double cz = rng.nextDouble() * 200_000.0;
            OrographyField.OroSample o = OrographyField.sample((int) cx, (int) cz, seed);
            if (!o.isLand || o.coastDist > -20_000.0) {
                continue;
            }
            Belt b = new Belt();
            b.cx = cx;
            b.cz = cz;
            double ang = rng.nextDouble() * Math.PI * 2.0;
            b.ca = Math.cos(ang);
            b.sa = Math.sin(ang);
            b.halfL = 45_000.0 + rng.nextDouble() * 55_000.0;
            b.halfW = 8_000.0 + rng.nextDouble() * 7_000.0;      // 略窄 → 更陡
            b.amp = Tune.ampLo + rng.nextDouble() * (Tune.ampHi - Tune.ampLo);
            b.axialAmp = Tune.axialLo + rng.nextDouble() * (Tune.axialHi - Tune.axialLo);
            b.meanderAmp = 5_000.0 + rng.nextDouble() * 4_000.0;
            b.meanderFreq = 1.0 / (45_000.0 + rng.nextDouble() * 30_000.0);
            b.peakPhase1 = rng.nextDouble() * Math.PI * 2.0;
            b.peakPhase2 = rng.nextDouble() * Math.PI * 2.0;
            // 与已有山带保持距离，避免重叠成一片
            boolean ok = true;
            for (int i = 0; i < made; i++) {
                double dx = out[i].cx - b.cx, dz = out[i].cz - b.cz;
                if (dx * dx + dz * dz < 90_000.0 * 90_000.0) {
                    ok = false;
                    break;
                }
            }
            if (!ok) {
                continue;
            }
            out[made++] = b;
        }
        if (made < BELTS) {
            Belt[] trimmed = new Belt[made];
            System.arraycopy(out, 0, trimmed, 0, made);
            return trimmed;
        }
        return out;
    }

    /** 山带权威权重（含端部 taper、边界过渡）。 */
    private static double beltWeight(Belt b, double x, double z) {
        double dx = x - b.cx, dz = z - b.cz;
        double along = dx * b.ca + dz * b.sa;
        double lat = -dx * b.sa + dz * b.ca;
        double taper = 1.0 - Math.abs(along) / b.halfL;
        if (taper <= 0.0) {
            return 0.0;
        }
        taper = Math.pow(taper, 0.30);
        double belt = Math.exp(-Math.pow(lat / (0.40 * b.halfW), 2.0)) * taper;
        double meander = b.meanderAmp * TerrainNoise.fbm2DS(seedSalt(b, 0xE31L), along * b.meanderFreq, 0.0, 1.0, 1.0, 2);
        double latA = lat - meander;
        double axial = Math.exp(-Math.pow(latA / (0.10 * b.halfW), 2.0)) * taper;   // 主脊更窄
        double w = belt * 1.6 + axial * 0.8;
        return w > 1.0 ? 1.0 : w;
    }

    /**
     * 中尺度脊线纹理：与世界种子同一套 ridged 场（V2TerrainGen.mountainTexture），
     * 使山层与基础山地的脊线对齐、过渡自然。返回 0..1（谷=0，脊=1）。
     * 这是山带内部 1~4km 尺度起伏的唯一来源——只有它才能造出支脊/次级峰。
     */
    private static double ridgeTexture(Belt b, double x, double z) {
        return V2TerrainGen.mountainTexture(SOLVE_SEED, x, z);
    }

    /** 山带包络（横向高斯 × 端部 taper，0..1）。 */
    private static double beltEnvelope(Belt b, double x, double z) {
        double dx = x - b.cx, dz = z - b.cz;
        double along = dx * b.ca + dz * b.sa;
        double lat = -dx * b.sa + dz * b.ca;
        double taper = 1.0 - Math.abs(along) / b.halfL;
        if (taper <= 0.0) {
            return 0.0;
        }
        return Math.exp(-Math.pow(lat / (0.40 * b.halfW), 2.0)) * Math.pow(taper, 0.30);
    }

    /** 山带抬升（含沿脊峰/鞍 + 多尺度噪声）。 */
    private static double beltUplift(Belt b, double x, double z, double w) {
        double dx = x - b.cx, dz = z - b.cz;
        double along = dx * b.ca + dz * b.sa;
        double lat = -dx * b.sa + dz * b.ca;
        double taper = Math.pow(Math.max(0.0, 1.0 - Math.abs(along) / b.halfL), 0.30);
        double belt = Math.exp(-Math.pow(lat / (0.40 * b.halfW), 2.0)) * taper;
        double meander = b.meanderAmp * TerrainNoise.fbm2DS(seedSalt(b, 0xE31L), along * b.meanderFreq, 0.0, 1.0, 1.0, 2);
        double latA = lat - meander;
        double axial = Math.exp(-Math.pow(latA / (0.10 * b.halfW), 2.0)) * taper;
        double tex = ridgeTexture(b, x, z);
        if (Tune.texPow != 1.0) {
            tex = Math.pow(tex, Tune.texPow);
        }
        double mid = Tune.midAmp * tex;
        double n1 = TerrainNoise.fbm2DS(seedSalt(b, 0xE11L), x, z, 1.0 / 45000.0, 1.0, 3) * 8.0;
        double n2 = TerrainNoise.fbm2DS(seedSalt(b, 0xE12L), x, z, 1.0 / 13000.0, 1.0, 3) * 5.0;
        double n3 = TerrainNoise.fbm2DS(seedSalt(b, 0xE13L), x, z, 1.0 / 4200.0, 1.0, 2) * 3.0;
        // 沿脊改为"离散峰列"：周期 ≈8k 的尖峰，每峰高度不同，峰间鞍部接近 0
        double s = along / 4000.0 + b.peakPhase1;
        double bump = Math.pow(Math.max(0.0, Math.cos(s * Math.PI)), 2.0);
        double hvar = 0.55 + 0.45 * Math.sin(along / 19000.0 + b.peakPhase2);
        double mod = 0.10 + 1.70 * bump * hvar;
        double axialAmp = b.axialAmp * mod;
        double beltMod = 1.0 + 0.28 * Math.sin(along / 31000.0 + b.peakPhase2);
        double up = belt * beltMod * (b.amp + n1 * 0.7 + mid)
            + (n1 + n2 + n3) * belt * beltMod
            + axial * axialAmp
            - Tune.floorDrop * belt * beltMod;
        return Math.max(-30.0, up);
    }

    private static long seedSalt(Belt b, long salt) {
        return (long) Double.doubleToLongBits(b.cx * 31.0 + b.cz * 17.0) ^ salt;
    }

    // ==================== 河道下切 ====================

    /**
     * 汇水面积 → 静态下切。
     * 山脊/峰顶 A≈1 → 不切（峰高 = 抬升场设计值）；河道 A 大 → 切到 carve 预算。
     * 下切量再受 belt 包络与本地抬升比例约束，谷底不会穿到 plain 之下。
     */
    private static void carve(float[] h, float[] env) {
        int n = h.length;
        float[] acc = new float[n];
        int[] order = new int[n];
        float[] tmp = new float[n];
        // 1) 填洼（Priority-Flood）：噪声会造出大量小洼地，不填则汇水面积碎片化
        float[] fill = fillSinks(h);
        // 2) 高度降序排序（在填洼面上排，保证排水自高向低单向流动）
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        for (float v : fill) { if (v < lo) lo = v; if (v > hi) hi = v; }
        float scale = (hi - lo) < 1e-3f ? 1f : 4095f / (hi - lo);
        int[] cnt = new int[4097];
        for (int k = 0; k < n; k++) cnt[(int) ((fill[k] - lo) * scale)]++;
        int a = 0;
        for (int b = 4095; b >= 0; b--) { int c = cnt[b]; cnt[b] = a; a += c; }
        for (int k = 0; k < n; k++) order[cnt[(int) ((fill[k] - lo) * scale)]++] = k;
        // 3) D8 汇水面积（自高向低累积）
        Arrays.fill(acc, 0f);
        for (int oi = 0; oi < n; oi++) {
            int k = order[oi];
            int lo2 = lowest(k, fill);
            acc[k] += 1f;
            if (lo2 >= 0) acc[lo2] += acc[k];
        }
        // 4) 分档下切：A 小（山脊/峰顶）不切，A 大（河道）切满 carve 预算
        double accMax = 0, cutMax = 0, cutSum = 0;
        int pos = 0;
        for (int k = 0; k < n; k++) {
            double v = h[k];
            if (v <= 0.0) { tmp[k] = (float) v; continue; }
            double g = (acc[k] - Tune.aGateLo) / Math.max(1e-6, Tune.aGateHi - Tune.aGateLo);
            if (g < 0.0) g = 0.0;
            else if (g > 1.0) g = 1.0;
            g = g * g * (3.0 - 2.0 * g);          // smoothstep：只有真河道才切
            double envC = env[k] / Tune.envFloor;
            if (envC > 1.0) envC = 1.0;
            double cut = Tune.carve * g * envC;
            double lim = v * Tune.carveMaxFrac;
            if (cut > lim) cut = lim;
            tmp[k] = (float) (v - cut);
            pos++;
            if (acc[k] > accMax) accMax = acc[k];
            if (cut > cutMax) cutMax = cut;
            cutSum += cut;
        }
        if (Tune.debug) {
            System.out.println("[carve] pos=" + pos + " accMax=" + String.format("%.0f", accMax)
                + " cutMax=" + String.format("%.1f", cutMax)
                + " cutMean=" + String.format("%.1f", cutSum / Math.max(1, pos)));
        }
        // 5) 热力平滑（圆化脊线/坡面，只在包络内）
        for (int p = 0; p < Tune.smoothPass; p++) {
            for (int j = 1; j < NZ - 1; j++) {
                for (int i = 1; i < NX - 1; i++) {
                    int k = j * NX + i;
                    if (env[k] <= 0f) continue;
                    float s = 0.25f * (tmp[k - 1] + tmp[k + 1] + tmp[k - NX] + tmp[k + NX]);
                    tmp[k] = tmp[k] + (float) Tune.thermal * (s - tmp[k]);
                }
            }
        }
        System.arraycopy(tmp, 0, h, 0, n);
    }

    /** 填洼 ε（blocks）：足够小不影响高度，但能消除平地排水死区。 */
    private static final float FILL_EPS = 1e-3f;

    /**
     * Priority-Flood+ε 填洼（Barnes 2014）：从全局最低格起，按高度优先队列外扩，
     * 每格水位 = max(自身高度, 上游水位) → 结果无洼地，D8 排水全程连通。
     */
    private static float[] fillSinks(float[] h) {
        int n = h.length;
        float[] fill = new float[n];
        boolean[] done = new boolean[n];
        int[] heap = new int[n];
        int size = 0;
        int seed = 0;
        float lo = Float.MAX_VALUE;
        for (int k = 0; k < n; k++) {
            if (h[k] < lo) { lo = h[k]; seed = k; }
        }
        fill[seed] = lo;
        done[seed] = true;
        heapPush(heap, size++, seed, fill);
        while (size > 0) {
            int c = heapPop(heap, size--, fill);
            int j = c / NX, i = c % NX;
            for (int dj = -1; dj <= 1; dj++) {
                for (int di = -1; di <= 1; di++) {
                    if (di == 0 && dj == 0) continue;
                    int jj = ((j + dj) % NZ + NZ) % NZ;
                    int ii = ((i + di) % NX + NX) % NX;
                    int kk = jj * NX + ii;
                    if (done[kk]) continue;
                    done[kk] = true;
                    // +ε：填洼区每远离溢流点升高 ε，保证 D8 排水无平地死区
                    float lvl = fill[c] + FILL_EPS;
                    fill[kk] = h[kk] > lvl ? h[kk] : lvl;
                    heapPush(heap, size++, kk, fill);
                }
            }
        }
        return fill;
    }

    private static void heapPush(int[] heap, int size, int v, float[] key) {
        heap[size] = v;
        int i = size;
        while (i > 0) {
            int p = (i - 1) >> 1;
            if (key[heap[p]] <= key[heap[i]]) {
                break;
            }
            int t = heap[p]; heap[p] = heap[i]; heap[i] = t;
            i = p;
        }
    }

    private static int heapPop(int[] heap, int size, float[] key) {
        int top = heap[0];
        heap[0] = heap[size - 1];
        int i = 0, m = size - 1;
        while (true) {
            int l = 2 * i + 1, r = l + 1, s = i;
            if (l < m && key[heap[l]] < key[heap[s]]) s = l;
            if (r < m && key[heap[r]] < key[heap[s]]) s = r;
            if (s == i) {
                break;
            }
            int t = heap[i]; heap[i] = heap[s]; heap[s] = t;
            i = s;
        }
        return top;
    }

    private static int lowest(int k, float[] h) {
        int j = k / NX, i = k % NX;
        int best = -1;
        float bh = h[k];
        for (int dj = -1; dj <= 1; dj++) {
            for (int di = -1; di <= 1; di++) {
                if (di == 0 && dj == 0) continue;
                int jj = ((j + dj) % NZ + NZ) % NZ;
                int ii = ((i + di) % NX + NX) % NX;
                int kk = jj * NX + ii;
                if (h[kk] < bh) { bh = h[kk]; best = kk; }
            }
        }
        return best;
    }
}
