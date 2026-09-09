package com.EyeOfHarmonyBuffer.space.talos.chunk.world;

import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.GlobalCirculation;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.NoiseContinentGrid;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.OrographyField;
import com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer.BaseTerrainProfile;
import com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer.TerrainBaseHeight;
import com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer.TerrainNoise;

/**
 * V2 轨 · 块级高度层（design.md D30/D43）。
 *
 * 职责拆分（用户拍板 2026-09）：L1/L1b 只输出类型（isLand/kind/belt/elevation01 仅供
 * 类型分档与气候慢场），**块级高度完全由本层提供**：
 *   1. 五档地貌权重来自 {@link LandformField}（**唯一权威**，地形与群系共用）；
 *   2. 按权重线性插值五份「高度档案」（高度带 + 低/中/高三层噪声幅度）；
 *   3. 单次 TerrainBaseHeight 三层分解出列高（共享大陆骨架 λ8k + 低 λ3k +
 *      中 λ1.1k 起伏 + 高 λ260 细节），base 与 plain **共享同一份噪声采样**；
 *   4. 山层抬升由 Provider 在此高度之上按权威权重仲裁（MountainLayerV2）。
 *
 * 海洋同层：残差深度带（D30 公式作"海盆带"）+ 中频海床起伏（±3）。
 * 纯函数、无 Minecraft 依赖。
 */
public final class V2TerrainGen {

    private V2TerrainGen() {}

    // ===== 高度档案五档（全部共享同一组频率 → 权重插值无接缝；仅 带限/三层幅度/台地强度 不同） =====

    private static final double LOW_FREQ = 1.0 / 3000.0;
    private static final double MID_FREQ = 1.0 / 1100.0;
    private static final double HIGH_FREQ = 1.0 / 260.0;

    /** 列序: [min, max, lowAmp, midAmp, plateauStrength]。 */
    private static final double[] LOW     = { 68, 82, 18, 8, 0.10 };
    private static final double[] HILL    = { 72, 96, 22, 12, 0.15 };
    private static final double[] PLATEAU = { 86, 110, 24, 10, 0.62 };
    private static final double[] MOUNTAIN = { 92, 126, 28, 13, 0.28 };
    private static final double[] PEAK    = { 106, 150, 30, 15, 0.0 };

    // ===== 海床 / 滩带 / 雪线 =====

    /** 岸边最小水深（近海第一格即下沉 2）。 */
    public static final double OCEAN_MIN_DEPTH = 2.0;
    /** 典型大洋最大深度增量（smoothstep 上界 ≈ +32，总深 ≤34）。 */
    public static final double OCEAN_MAX_DEPTH = 32.0;
    /** 深度 smoothstep 的 |r|/seaQ93 区间。 */
    public static final double DEPTH_EDGE_LO = 0.30;
    public static final double DEPTH_EDGE_HI = 1.20;
    /** 深盆附加（超出 1.25×seaQ93 后每单位 +14，封顶 9）。 */
    public static final double BASIN_EXTRA_AFTER = 1.25;
    public static final double BASIN_EXTRA_PER_UNIT = 14.0;
    public static final double BASIN_EXTRA_CAP = 9.0;
    /** 海床中频起伏幅度上限（blocks）。 */
    public static final double SEABED_RELIEF = 3.0;
    /** 陆侧沙滩半宽（blocks，配合最终地表高度 ≤ 海面+5 判定）。 */
    public static final double BEACH_LAND_BLOCKS = 10.0;
    /** 浅海沙底水深上限 / 砂砾海底水深上限（blocks）。 */
    public static final double SAND_SEA_DEPTH = 6.0;
    public static final double GRAVEL_SEA_DEPTH = 16.0;
    /** 赤道雪线高度 / 极地雪线高度（纬度间线性插值）。 */
    public static final double SNOW_EQUATOR_Y = 185.0;
    public static final double SNOW_POLE_Y = 128.0;

    /** 基础山地中尺度纹理振幅（blocks）与锐化指数（探针可调）。 */
    public static double mtnTexAmp = 200.0;
    public static double mtnTexPow = 2.0;
    /** 纹理基准波长倒数（1/blocks）：三个八度 = 1x / 2.5x / 6.25x。 */
    public static double mtnTexFreq = 1.0 / 3000.0;

    // ===== 查询 =====

    /**
     * 陆地列基础高度（不含山层抬升；Provider 在其上按权威权重叠加 MountainLayerV2）。
     * 权重全部来自类型场连续量 → 类型边界高度连续、无断崖。
     */
    public static double landBaseHeight(int x, int z, int worldSeedInt, int seaLevel,
                                        OrographyField.OroSample o) {
        return landBaseHeight(x, z, worldSeedInt, seaLevel, o, 0.5, 0.5);
    }

    /**
     * 陆地列基础高度（含群系高度倾向，D38）。
     *
     * @param biomeBias  群系高度倾向 [0,1]（0=带底，1=带顶，0.5=中性）
     * @param biomeScale 群系起伏散布比例（0.5=中性）
     */
    public static double landBaseHeight(int x, int z, int worldSeedInt, int seaLevel,
                                        OrographyField.OroSample o,
                                        double biomeBias, double biomeScale) {
        LandformField.Sample lf = LandformField.sample(x, z, worldSeedInt);
        double[] w = W.get();
        w[0] = lf.low;
        w[1] = lf.hill;
        w[2] = lf.plat;
        w[3] = lf.mtn;
        w[4] = lf.peak;
        BaseTerrainProfile profile = new BaseTerrainProfile();
        buildProfileFromWeights(w, false, seaLevel, profile);
        double h = TerrainBaseHeight.computeBaseHeightCore(x, z, worldSeedInt, profile,
            biomeBias, biomeScale);
        // 中尺度脊线纹理：基础山地/峰档也要有 0.5~3km 的脊谷起伏，
        // 否则"山"只是一块光滑的高地（Alpine 群系观感即来源于此）。
        double mtnW = w[3] + w[4];
        if (mtnW > 0.01) {
            double tex = mountainTexture(worldSeedInt * 0x9E3779B97F4A7C15L, x, z);
            h += mtnTexAmp * mtnW * Math.pow(tex, mtnTexPow);
        }
        // 贴岸低地保险：陆地列不下探到海面以下（干盆地处理留待水系阶段）
        return h < seaLevel + 1 ? seaLevel + 1 : h;
    }

    /**
     * 一次算 base + plain（**共享三层噪声**，省约一半列耗时）。
     * out[0] = base，out[1] = plain。调用方复用同一数组（每区块分配一次即可）。
     */
    public static void baseAndPlain(int x, int z, int worldSeedInt, int seaLevel,
                                    OrographyField.OroSample o, double biomeBias, double biomeScale,
                                    double[] out) {
        // 地貌场（唯一权威）→ 五档权重 → 档案 → 高度
        LandformField.Sample lf = LandformField.sample(x, z, worldSeedInt);
        double[] w = W.get();
        w[0] = lf.low;
        w[1] = lf.hill;
        w[2] = lf.plat;
        w[3] = lf.mtn;
        w[4] = lf.peak;
        basePlainFromWeights(w, x, z, worldSeedInt, seaLevel, biomeBias, biomeScale, out);
    }

    /** 由五档权重直接算 base/plain（地貌场求解与运行期共用，无 LUT 依赖）。 */
    public static void basePlainFromWeights(double[] w, int x, int z, int worldSeedInt,
                                            int seaLevel, double biomeBias, double biomeScale,
                                            double[] out) {
        Scratch sc = TL.get();
        buildProfileFromWeights(w, false, seaLevel, sc.base);
        buildProfileFromWeights(w, true, seaLevel, sc.plain);
        TerrainBaseHeight.sampleNoise(x, z, worldSeedInt, sc.base, sc.noise);
        double hb = TerrainBaseHeight.fromNoise(sc.base, sc.noise, biomeBias, biomeScale,
            TerrainBaseHeight.BIOME_BIAS_GAIN);
        double hp = TerrainBaseHeight.fromNoise(sc.plain, sc.noise, biomeBias, biomeScale,
            TerrainBaseHeight.BIOME_BIAS_GAIN);
        double mtnW = w[3] + w[4];
        if (mtnW > 0.01) {
            double tex = mountainTexture(worldSeedInt * 0x9E3779B97F4A7C15L, x, z);
            hb += mtnTexAmp * mtnW * Math.pow(tex, mtnTexPow);
        }
        out[0] = hb < seaLevel + 1 ? seaLevel + 1 : hb;
        out[1] = hp < seaLevel + 1 ? seaLevel + 1 : hp;
    }

    private static final ThreadLocal<double[]> W = new ThreadLocal<double[]>() {
        @Override
        protected double[] initialValue() {
            return new double[5];
        }
    };

    /** 热路径复用容器（线程本地，避免逐列分配）。 */
    private static final class Scratch {
        final BaseTerrainProfile base = new BaseTerrainProfile();
        final BaseTerrainProfile plain = new BaseTerrainProfile();
        final TerrainBaseHeight.Noise noise = new TerrainBaseHeight.Noise();
    }

    private static final ThreadLocal<Scratch> TL = new ThreadLocal<Scratch>() {
        @Override
        protected Scratch initialValue() {
            return new Scratch();
        }
    };

    /**
     * 五档档案权重 → 档案参数。
     * slim=true 时把 MOUNTAIN/PEAK 权重并入丘陵（瘦身版 plain 用）。
     *
     * 关键：山带走廊（relief/belt）覆盖面达陆地 5~6 成，若走廊整体抬到山地带，
     * 低海拔走廊也会变成 110+ 的大山 → 中纬大面积雪白。故"走廊×高海拔"才成山
     * （elevation01 只在此处作为"是否真山"的门槛连续量，不直接定高）；
     * 低海拔走廊并入丘陵档（起伏放大但不长高）。
     *
     * @return 山地+峰权重（供中尺度纹理使用）
     */
    public static void buildProfileFromWeights(double[] w, boolean slim, int seaLevel,
                                               BaseTerrainProfile profile) {
        double wLow = w[0];
        double wHill = w[1];
        double wPlat = w[2];
        double wMtn = w[3];
        double wPeak = w[4];
        if (slim) {
            // 瘦身版：山地/峰权重并入丘陵（与 sum 完全等价）
            wHill = wHill + wMtn + wPeak;
            wMtn = 0.0;
            wPeak = 0.0;
        }

        double minH = wLow * LOW[0] + wHill * HILL[0] + wPlat * PLATEAU[0]
            + wMtn * MOUNTAIN[0] + wPeak * PEAK[0];
        double maxH = wLow * LOW[1] + wHill * HILL[1] + wPlat * PLATEAU[1]
            + wMtn * MOUNTAIN[1] + wPeak * PEAK[1];
        double lowAmp = wLow * LOW[2] + wHill * HILL[2] + wPlat * PLATEAU[2]
            + wMtn * MOUNTAIN[2] + wPeak * PEAK[2];
        double midAmp = wLow * LOW[3] + wHill * HILL[3] + wPlat * PLATEAU[3]
            + wMtn * MOUNTAIN[3] + wPeak * PEAK[3];
        double plateauStrength = wLow * LOW[4] + wHill * HILL[4] + wPlat * PLATEAU[4]
            + wMtn * MOUNTAIN[4] + wPeak * PEAK[4];

        profile.minHeight = Math.max(seaLevel + 1, minH);
        profile.maxHeight = Math.max(profile.minHeight + 4, maxH);
        profile.lowFreq = LOW_FREQ;
        profile.lowAmp = lowAmp;
        profile.lowOctaves = 3;
        profile.midFreq = MID_FREQ;
        profile.midAmp = midAmp;
        profile.midOctaves = 3;
        profile.highFreq = HIGH_FREQ;
        profile.highAmp = 5.0;
        profile.highOctaves = 2;
        profile.plateauStrength = plateauStrength;
        profile.oceanDepthMax = 0.0;
    }

    /**
     * 瘦身版基础地形（山层仲裁用，D34）：把 MOUNTAIN/PEAK 两档权重并入丘陵，
     * 只保留 低地/丘陵/高原（上限约 122）。与 landBaseHeight 之差即基础山地贡献 mtnComp。
     */
    public static double landPlainHeight(int x, int z, int worldSeedInt, int seaLevel,
                                         OrographyField.OroSample o) {
        return landPlainHeight(x, z, worldSeedInt, seaLevel, o, 0.5, 0.5);
    }

    /** 瘦身版基础地形（含同一份群系高度倾向，保证 mtnComp = base − plain 干净）。 */
    public static double landPlainHeight(int x, int z, int worldSeedInt, int seaLevel,
                                         OrographyField.OroSample o,
                                         double biomeBias, double biomeScale) {
        LandformField.Sample lf = LandformField.sample(x, z, worldSeedInt);
        double[] w = W.get();
        w[0] = lf.low;
        w[1] = lf.hill;
        w[2] = lf.plat;
        w[3] = lf.mtn;
        w[4] = lf.peak;
        BaseTerrainProfile profile = new BaseTerrainProfile();
        buildProfileFromWeights(w, true, seaLevel, profile);
        double h = TerrainBaseHeight.computeBaseHeightCore(x, z, worldSeedInt, profile,
            biomeBias, biomeScale);
        return h < seaLevel + 1 ? seaLevel + 1 : h;
    }

    /**
     * 山体块级细节噪声（λ≈1.1k/550，振幅随山体强度缩放）：
     * 山层抬升是 400m 粗网格，块级细节由这里补上。
     */
    public static double mountainDetail(int x, int z, int worldSeedInt, double strength01) {
        double s = clamp01(strength01);
        if (s <= 0.01) {
            return 0.0;
        }
        double r1 = ridged(0x9E37L ^ worldSeedInt, x, z, 1.0 / 1100.0, 3);
        double r2 = ridged(0xC2B2L ^ worldSeedInt, x, z, 1.0 / 420.0, 2);
        return (r1 * 24.0 + r2 * 10.0) * (0.15 + 0.85 * s);
    }

    /**
     * 中尺度山体纹理（λ ≈ 3k / 1.2k / 480m，ridged，返回 0..1）：
     * 山层（MountainLayerV2）与基础山地共用同一套频谱，保证"只要成山就有脊谷"。
     */
    public static double mountainTexture(long seed, double x, double z) {
        double sum = 0.0, norm = 0.0, amp = 1.0, f = mtnTexFreq;
        for (int o = 0; o < 3; o++) {
            double n = TerrainNoise.fbm2DS(seed + o * 0x51ED270BL, x, z, f, 1.0, 2);
            double r = 1.0 - Math.abs(n * 0.7);
            if (r < 0.0) r = 0.0;
            sum += amp * r * r;
            norm += amp;
            amp *= o == 0 ? 1.0 : 0.70;
            f *= 2.5;
        }
        return sum / norm;
    }

    /** ridged 噪声 [0,1]。 */
    private static double ridged(long seed, double x, double z, double freq, int octaves) {
        double sum = 0.0, amp = 1.0, norm = 0.0, f = freq;
        for (int i = 0; i < octaves; i++) {
            double n = TerrainNoise.noise2S(seed + i * 0x9E3779B9L, x * f, z * f);
            double v = 1.0 - Math.abs(n);
            v = v * v;
            sum += v * amp;
            norm += amp;
            f *= 2.0;
            amp *= 0.5;
        }
        return sum / norm;
    }

    /**
     * 海洋列水深（blocks，>0）：海盆带（残差 q93 归一，D30 公式）+ 中频海床起伏。
     * 近岸浅坡由残差连续斜坡给出，无需额外 shelf 项。
     */
    public static double seaDepthBlocks(int x, int z, int worldSeedInt) {
        double r = NoiseContinentGrid.landResidual(x, z, worldSeedInt);   // r<0 = 海上
        double a = -r / NoiseContinentGrid.seaResidualScale(worldSeedInt);
        double d = OCEAN_MIN_DEPTH + OCEAN_MAX_DEPTH * smoothstep(DEPTH_EDGE_LO, DEPTH_EDGE_HI, a);
        if (a > BASIN_EXTRA_AFTER) {
            double extra = (a - BASIN_EXTRA_AFTER) * BASIN_EXTRA_PER_UNIT;
            d += Math.min(BASIN_EXTRA_CAP, extra);
        }
        // 海床中频起伏（λ≈1.2k 波纹，±SEABED_RELIEF；1.75 = 3 octave 归一和）
        double mid = TerrainNoise.warpedFbm2D(0x51E5A2D9L ^ worldSeedInt, x, z,
            1.0 / 1200.0, 1.0, 3, 1.0 / 2400.0, 900.0);
        d += SEABED_RELIEF * mid / 1.75;
        return d;
    }

    /** 陆侧贴岸低地是否为沙滩带（surfaceY ≤ 海面+5 才铺沙，防止山岸变沙）。 */
    public static boolean isBeachLand(OrographyField.OroSample o, double surfaceY, int seaLevel) {
        return o.coastDist > -BEACH_LAND_BLOCKS && surfaceY <= seaLevel + 5.0;
    }

    /** 该纬度雪线高度（赤道 {@link #SNOW_EQUATOR_Y} / 极地 {@link #SNOW_POLE_Y} 线性插值；bandD：0=赤道 1=极地）。 */
    public static double snowLineY(int worldZ) {
        double b = clamp01(GlobalCirculation.bandD(worldZ));
        return SNOW_POLE_Y + (SNOW_EQUATOR_Y - SNOW_POLE_Y) * (1.0 - b);
    }

    private static double smoothstep(double e0, double e1, double x) {
        if (e1 <= e0) {
            return x < e0 ? 0.0 : 1.0;
        }
        double t = clamp01((x - e0) / (e1 - e0));
        return t * t * (3.0 - 2.0 * t);
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}
