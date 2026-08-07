package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.TectonicMath;

public final class TalosSeafloorShaper {

    private TalosSeafloorShaper() {}

    private static final double W_SHALLOW_START = 0.90;
    private static final double W_SLOPE_START = 0.85;
    private static final double W_SHELF_START = 0.35;
    private static final double W_BLEND_START = 0.348;
    private static final double W_BLEND_END = 0.350;

    private static final int DEPTH_SHALLOW_NEAR = 1;
    private static final int DEPTH_SHALLOW_FAR = 4;
    private static final int DEPTH_SHELF = 16;

    /** 海床表面材质种类。 */
    public enum SeafloorMaterial {
        /** 保持深层岩石变体（石头/花岗岩等），不铺表面层。 */
        ROCK,
        SAND,
        GRAVEL,
        CLAY
    }

    /** 某列海床的表面 / 填充层规格。 */
    public static final class SeafloorFill {
        public final SeafloorMaterial surface;
        public final int surfaceDepth;
        public final SeafloorMaterial filler;
        public final int fillerDepth;

        public SeafloorFill(SeafloorMaterial surface, int surfaceDepth,
                            SeafloorMaterial filler, int fillerDepth) {
            this.surface = surface;
            this.surfaceDepth = surfaceDepth;
            this.filler = filler;
            this.fillerDepth = fillerDepth;
        }
    }

    private static final int SALT_MATERIAL = 0x5EAF1001;
    private static final int SALT_DEPTH = 0x5EAF1002;

    /** 材质噪声的采样单元边长（blocks）：越大同一材质连片越大。 */
    private static final double MATERIAL_NOISE_SCALE = 384.0;

    // ===== 海底起伏：洞厅底部同风格的域扭曲 + 分层 Perlin =====
    /** 主起伏噪声尺度（blocks）。 */
    private static final double FLOOR_NOISE_SCALE = 560.0;
    /** 域扭曲尺度 / 幅度：让大块地形弯曲、更自然。 */
    private static final double FLOOR_WARP_SCALE = 900.0;
    private static final double FLOOR_WARP_AMP = 700.0;
    /** 高频细节尺度 / 幅度：小幅快速起伏。 */
    private static final double FLOOR_DETAIL_SCALE = 180.0;
    private static final double FLOOR_DETAIL_AMP = 3.5;

    private static final int FLOOR_SALT = 0x7EAF3001;
    private static final int FLOOR_DETAIL_SALT = 0x7EAF3002;

    /** 深海基准深度：架缘外 / 远洋。 */
    private static final double DEPTH_DEEP_EDGE = 34.0;
    private static final double DEPTH_DEEP_FAR = 44.0;

    /**
     * 按 shelfWeight 分段给海床配表面材质（与 computeSeabedY 同一套分段）：
     *   - 近岸浅滩：表层沙子（2 层）+ 沙子填充（2 层）；
     *   - 大陆坡上部：砂砾为主，少量沙子；
     *   - 平坦大陆架：沙子 / 砂砾 / 黏土按确定性噪声分区；
     *   - 架缘过渡：黏土或沙子；
     *   - 深海：保持岩石基底，少量砂砾点缀。
     * 所有选择都是 (worldX, worldZ, worldSeedInt) 的确定性函数，跨区块可复现。
     */
    public static SeafloorFill computeSeafloorFill(double shelfWeight,
                                                   int worldX, int worldZ,
                                                   int worldSeedInt) {
        double w = clamp01(shelfWeight);
        double rMat = materialNoise01(
            worldX, worldZ, worldSeedInt, SALT_MATERIAL);
        double rDep = materialNoise01(
            worldX, worldZ, worldSeedInt, SALT_DEPTH);

        if (w >= W_SHALLOW_START) {
            return new SeafloorFill(SeafloorMaterial.SAND, 2,
                SeafloorMaterial.SAND, 2);
        }
        if (w >= W_SLOPE_START) {
            SeafloorMaterial s = rMat < 0.20
                ? SeafloorMaterial.SAND : SeafloorMaterial.GRAVEL;
            return new SeafloorFill(s, 2, SeafloorMaterial.GRAVEL, 3);
        }
        if (w >= W_SHELF_START) {
            SeafloorMaterial s = rMat < 0.45
                ? SeafloorMaterial.SAND
                : (rMat < 0.80 ? SeafloorMaterial.GRAVEL
                               : SeafloorMaterial.CLAY);
            int sd = rDep < 0.5 ? 1 : 2;
            int fd = rDep < 0.5 ? 2 : 3;
            return new SeafloorFill(s, sd, s, fd);
        }
        if (w >= W_BLEND_START) {
            SeafloorMaterial s = rMat < 0.5
                ? SeafloorMaterial.CLAY : SeafloorMaterial.SAND;
            return new SeafloorFill(s, 1, s, 2);
        }
        // 深海：绝大多数保持岩石，约 1/10 的列铺一层砂砾
        if (rMat < 0.10) {
            return new SeafloorFill(SeafloorMaterial.GRAVEL, 1,
                SeafloorMaterial.ROCK, 0);
        }
        return new SeafloorFill(SeafloorMaterial.ROCK, 0,
            SeafloorMaterial.ROCK, 0);
    }

    /**
     * 低频 2D 值噪声 [0,1)：相邻方块平滑过渡，材质按阈值切出大块分区。
     * 确定性：只依赖 (worldX, worldZ, worldSeedInt, salt)。
     */
    private static double materialNoise01(int worldX, int worldZ,
                                          int seed, int salt) {
        return valueNoise01(worldX, worldZ, seed, salt, MATERIAL_NOISE_SCALE);
    }

    /** 通用低频值噪声 [0,1)。 */
    private static double valueNoise01(int worldX, int worldZ,
                                       int seed, int salt, double scale) {
        double sx = worldX / scale;
        double sz = worldZ / scale;
        int x0 = (int) Math.floor(sx);
        int z0 = (int) Math.floor(sz);
        double fx = sx - x0;
        double fz = sz - z0;
        double u = fx * fx * (3.0 - 2.0 * fx);
        double v = fz * fz * (3.0 - 2.0 * fz);

        double n00 = noiseCorner(x0, z0, seed, salt);
        double n10 = noiseCorner(x0 + 1, z0, seed, salt);
        double n01 = noiseCorner(x0, z0 + 1, seed, salt);
        double n11 = noiseCorner(x0 + 1, z0 + 1, seed, salt);
        double a = n00 + (n10 - n00) * u;
        double b = n01 + (n11 - n01) * u;
        return a + (b - a) * v;
    }

    /** 域扭曲：两个不同盐的 Perlin 把 (x,z) 弯折，让起伏呈大块有机形态。 */
    private static double[] floorWarp(int worldX, int worldZ, int seed) {
        double wx2 = worldX + FLOOR_WARP_AMP * perlin2(
            worldX / FLOOR_WARP_SCALE, worldZ / FLOOR_WARP_SCALE,
            seed, FLOOR_SALT);
        double wz2 = worldZ + FLOOR_WARP_AMP * perlin2(
            worldX / FLOOR_WARP_SCALE, worldZ / FLOOR_WARP_SCALE,
            seed, FLOOR_SALT + 1);
        return new double[]{wx2, wz2};
    }

    /** 2D Perlin（梯度噪声），输出约 [-1,1]。 */
    private static double perlin2(double x, double z, int seed, int salt) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        double fx = x - x0;
        double fz = z - z0;
        double u = fade(fx);
        double v = fade(fz);

        double n00 = grad2(x0, z0, fx, fz, seed, salt);
        double n10 = grad2(x0 + 1, z0, fx - 1, fz, seed, salt);
        double n01 = grad2(x0, z0 + 1, fx, fz - 1, seed, salt);
        double n11 = grad2(x0 + 1, z0 + 1, fx - 1, fz - 1, seed, salt);

        double a = n00 + (n10 - n00) * u;
        double b = n01 + (n11 - n01) * u;
        return a + (b - a) * v;
    }

    /** 分形柏林（fBm）：多层 Perlin 叠加，输出连续且分布更自然。 */
    private static double fbm2(double x, double z, int seed, int salt,
                               int octaves, double lacunarity, double gain) {
        double sum = 0.0;
        double amp = 1.0;
        double freq = 1.0;
        double norm = 0.0;
        for (int i = 0; i < octaves; i++) {
            sum += amp * perlin2(x * freq, z * freq, seed, salt + i);
            norm += amp;
            amp *= gain;
            freq *= lacunarity;
        }
        return norm > 0.0 ? sum / norm : 0.0;
    }

    private static double grad2(int ix, int iz, double dx, double dz,
                                int seed, int salt) {
        long h = TectonicMath.hashInts(ix, iz, seed, salt);
        switch ((int) (h & 7L)) {
            case 0:
                return dx + dz;
            case 1:
                return dx - dz;
            case 2:
                return -dx + dz;
            case 3:
                return -dx - dz;
            case 4:
                return dx;
            case 5:
                return -dx;
            case 6:
                return dz;
            default:
                return -dz;
        }
    }

    private static double fade(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    /** 网格角点值 [0,1)。 */
    private static double noiseCorner(int cx, int cz, int seed, int salt) {
        return TectonicMath.randUnitDouble(
            TectonicMath.hashInts(cx, cz, seed, salt));
    }

    /**
     * 海底高度塑形：按 shelfWeight 定基准深度，再叠加与洞厅底部同风格的
     * “域扭曲 + 3 层 fBm + 高频细节”，让整片海洋呈大块起伏的连续地形。
     *
     * 基准深度分段（与 computeSeafloorFill 同源）：
     *   - w >= 0.90：近岸浅滩，1~4 格深；
     *   - 0.85 ~ 0.90：大陆坡上部，4 → 16 格渐深；
     *   - 0.35 ~ 0.85：大陆架，基准 16 格深；
     *   - 0.348 ~ 0.35：架缘过渡；
     *   - w < 0.348：深海，34 → 44 格渐深。
     *
     * 起伏幅度靠岸小、深海大（3 ~ 15 格），浅海段保持浅水安全范围。
     *
     * 注意：
     *   - 本塑形仅作用在“海洋区域”（isLand == false），
     *     且最终海底高度会被限制在 [1, seaLevel - 1] 之间；
     *   - 对陆地区域（isLand == true）不会抬高，只会在海平面以下做截断。
     *
     * @param seaLevel         世界海平面 Y
     * @param isLand           是否为陆地（为 true 时不会做海底塑形，只做水下截断）
     * @param shelfWeight      海洋侧大陆架权重 [0,1]，从远洋向岸边递增
     * @param rawTerrainHeight 原始地形高度（陆地水下截断用；海洋段由本函数噪声生成）
     * @param worldX           世界方块 X（用于确定性起伏噪声）
     * @param worldZ           世界方块 Z
     * @param worldSeedInt     Talos 世界种子 int
     * @param worldHeight      世界高度上限（用于 clamp）
     * @return 调整后的海底 / 地表 Y 值
     */
    public static int computeSeabedY(
        int seaLevel,
        boolean isLand,
        double shelfWeight,
        double rawTerrainHeight,
        int worldX,
        int worldZ,
        int worldSeedInt,
        int worldHeight
    ) {
        if (isLand) {
            int h = (int) Math.round(rawTerrainHeight);
            if (h < 1) h = 1;
            if (h > worldHeight - 2) h = worldHeight - 2;
            return Math.min(h, seaLevel - 1);
        }

        double w = clamp01(shelfWeight);

        // 基准深度：近岸浅 → 大陆架 → 深海，分段连续
        double baseDepth;
        if (w >= W_SHALLOW_START) {
            double t = (1.0 - w) / (1.0 - W_SHALLOW_START);
            baseDepth = DEPTH_SHALLOW_NEAR
                + (DEPTH_SHALLOW_FAR - DEPTH_SHALLOW_NEAR) * t;
        } else if (w >= W_SLOPE_START) {
            double t = (w - W_SLOPE_START)
                / (W_SHALLOW_START - W_SLOPE_START);
            baseDepth = DEPTH_SHELF
                + (DEPTH_SHALLOW_FAR - DEPTH_SHELF) * t;
        } else if (w >= W_SHELF_START) {
            baseDepth = DEPTH_SHELF;
        } else if (w >= W_BLEND_START) {
            double t = (w - W_BLEND_START)
                / (W_BLEND_END - W_BLEND_START);
            baseDepth = DEPTH_DEEP_EDGE
                + (DEPTH_SHELF - DEPTH_DEEP_EDGE) * smoothstep01(t);
        } else {
            double t = w / W_BLEND_START;
            baseDepth = DEPTH_DEEP_FAR
                + (DEPTH_DEEP_EDGE - DEPTH_DEEP_FAR) * smoothstep01(t);
        }

        // 起伏幅度：靠岸小、深海大（4 ~ 30 格）
        double amp = 4.0 + 26.0
            * (1.0 - smoothstep01(w / W_SHALLOW_START));

        // 洞厅底部同风格：域扭曲 + 3 层 fBm + 高频细节
        double[] warp = floorWarp(worldX, worldZ, worldSeedInt);
        double n = fbm2(
            warp[0] / FLOOR_NOISE_SCALE, warp[1] / FLOOR_NOISE_SCALE,
            worldSeedInt, FLOOR_SALT, 3, 2.0, 0.5);
        double detail = perlin2(
            worldX / FLOOR_DETAIL_SCALE, worldZ / FLOOR_DETAIL_SCALE,
            worldSeedInt, FLOOR_DETAIL_SALT) * FLOOR_DETAIL_AMP;

        double depth = baseDepth + n * amp + detail;
        int seabedY = seaLevel - (int) Math.round(depth);

        if (w >= W_SHALLOW_START) {
            return clampY(seabedY, seaLevel - 8, seaLevel - 1);
        }
        if (w >= W_SLOPE_START) {
            return clampY(seabedY, seaLevel - 26, seaLevel - 2);
        }
        if (w >= W_SHELF_START) {
            return clampY(seabedY, seaLevel - 30, seaLevel - 4);
        }
        if (w >= W_BLEND_START) {
            return clampY(seabedY, seaLevel - 36, seaLevel - 4);
        }
        return clampY(seabedY, 2, seaLevel - 2);
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }

    private static double smoothstep01(double t) {
        t = clamp01(t);
        return t * t * (3.0 - 2.0 * t);
    }

    private static int clampY(int y, int lo, int hi) {
        if (y < lo) y = lo;
        if (y > hi) y = hi;
        return y;
    }
}
