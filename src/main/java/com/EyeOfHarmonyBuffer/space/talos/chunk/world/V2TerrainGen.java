package com.EyeOfHarmonyBuffer.space.talos.chunk.world;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.NoiseContinentGrid;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.OrographyField;
import com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer.BaseTerrainProfile;
import com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer.PeriodicNoise;
import com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer.TerrainBaseHeight;

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

    private static final double LOW_FREQ = 1.0 / 3000.0;   // ★ 2026-10-08 回退：地形层与行星尺度无关（TILE/CELL 服务 MC 分辨率与地形质量）
    private static final double MID_FREQ = 1.0 / 1100.0;   // ★ 2026-10-08 回退：地形层与行星尺度无关（TILE/CELL 服务 MC 分辨率与地形质量）
    private static final double HIGH_FREQ = 1.0 / 260.0;   // ★ 2026-10-08 回退：地形层与行星尺度无关（TILE/CELL 服务 MC 分辨率与地形质量）

    // 可选周期噪声的格数（波长 → 格数，见 PeriodicNoise；当前 INFINITE_X/Z=true ⇒ 取负号、不折叠）
    private static final int LOW_NX = PeriodicNoise.cellsXFromFreq(LOW_FREQ);
    private static final int LOW_NZ = PeriodicNoise.cellsZFromFreq(LOW_FREQ);
    private static final int LOW_WNX = PeriodicNoise.cellsXFromFreq(LOW_FREQ * 0.5);
    private static final int LOW_WNZ = PeriodicNoise.cellsZFromFreq(LOW_FREQ * 0.5);
    private static final int MID_NX = PeriodicNoise.cellsXFromFreq(MID_FREQ);
    private static final int MID_NZ = PeriodicNoise.cellsZFromFreq(MID_FREQ);
    private static final int MID_WNX = PeriodicNoise.cellsXFromFreq(MID_FREQ * 0.5);
    private static final int MID_WNZ = PeriodicNoise.cellsZFromFreq(MID_FREQ * 0.5);
    private static final int HIGH_NX = PeriodicNoise.cellsXFromFreq(HIGH_FREQ);
    private static final int HIGH_NZ = PeriodicNoise.cellsZFromFreq(HIGH_FREQ);
    private static final int HIGH_WNX = PeriodicNoise.cellsXFromFreq(HIGH_FREQ * 0.5);
    private static final int HIGH_WNZ = PeriodicNoise.cellsZFromFreq(HIGH_FREQ * 0.5);
    /** 山体细节 / 海床起伏的格数（固定频率）。 */
    private static final int DET1_NX = PeriodicNoise.cellsXFromFreq(1.0 / 1100.0);
    private static final int DET1_NZ = PeriodicNoise.cellsZFromFreq(1.0 / 1100.0);
    private static final int DET2_NX = PeriodicNoise.cellsXFromFreq(1.0 / 420.0);
    private static final int DET2_NZ = PeriodicNoise.cellsZFromFreq(1.0 / 420.0);
    private static final int SEABED_NX = PeriodicNoise.cellsXFromFreq(1.0 / 1200.0);
    private static final int SEABED_NZ = PeriodicNoise.cellsZFromFreq(1.0 / 1200.0);
    private static final int SEABED_WNX = PeriodicNoise.cellsXFromFreq(1.0 / 2400.0);
    private static final int SEABED_WNZ = PeriodicNoise.cellsZFromFreq(1.0 / 2400.0);

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
    public static double mtnTexFreq = 1.0 / 3000.0;   // ★ 2026-10-08 回退：地形层与行星尺度无关（TILE/CELL 服务 MC 分辨率与地形质量）

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
            double tex = mountainTexture(textureSeed(worldSeedInt), x, z);
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
            double tex = mountainTexture(textureSeed(worldSeedInt), x, z);
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
        profile.lowNX = LOW_NX; profile.lowNZ = LOW_NZ;
        profile.lowWNX = LOW_WNX; profile.lowWNZ = LOW_WNZ;
        profile.midNX = MID_NX; profile.midNZ = MID_NZ;
        profile.midWNX = MID_WNX; profile.midWNZ = MID_WNZ;
        profile.hiNX = HIGH_NX; profile.hiNZ = HIGH_NZ;
        profile.hiWNX = HIGH_WNX; profile.hiWNZ = HIGH_WNZ;
        // 大陆骨架频率**唯一来源**是 TerrainBaseHeight.CONTINENTAL_FREQ
        // （这里原先又硬编码了一遍 1.0/8000.0 —— 两处各自漂移就会让"档案里预热的格数"
        //   与"采样时用的格数"不一致，而且不会有任何报错）。
        profile.contNX = PeriodicNoise.cellsXFromFreq(TerrainBaseHeight.CONTINENTAL_FREQ);
        profile.contNZ = PeriodicNoise.cellsZFromFreq(TerrainBaseHeight.CONTINENTAL_FREQ);
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

    // ==================== 块级合成高度：**唯一入口** ====================

    /**
     * Minecraft 1.7.10 的世界高度（= Provider 的 {@code getActualHeight()}）。
     * 命令类（{@code /talosmap}、{@code /talos_here}）手里没有 Provider，只能用这个常量；
     * 世界内的调用方请传自己的 {@code worldHeight}。
     */
    public static final int MC_WORLD_HEIGHT = 256;

    /**
     * 软封顶（blocks）：接近世界高度上限时平滑压缩，不硬截出平台。生产现行值 252 / 6。
     * **final**：本系统刚清掉一个"跨线程共享的可变静态"（见 BarotropicGyre 顶部），
     * 新引入的世界常量一律用 final，避免又长出一个可以从前台/预热线程同时写的手柄。
     */
    public static final double SOFT_CAP_H = 252.0;
    public static final double SOFT_CAP_K = 6.0;

    /** {@code mtnComp}（blocks）→ 山体细节强度的标尺。生产现行值 90。 */
    public static final double DETAIL_MTNCOMP_SCALE = 90.0;

    /** 细节强度里的坡度调制：{@code 1.00 + 0.80·slope01}。生产现行值 0.80。 */
    public static final double DETAIL_SLOPE_GAIN = 0.80;

    // ═══════════ §7716~§7730：Runevision 侵蚀滤镜的接入参数 ═══════════
    //
    // ⚠⚠ 【注入点不在本文件】：composeColumn 在 SimTerrain.ENABLED=true 时会
    //    直接 return SimTerrain.compose(...)，所以本文件里【没有】侵蚀代码。
    //    真正的注入点 = sim/runtime/SimTerrain.compose 的陆地分支。
    //    这里只放【参数】，供 SimTerrain 引用。

    /** 侵蚀的总开关。{@code false} 则完全跳过（A/B 对比与回归排查用）。 */
    // ★★★★★★★ 2026-10-08：**用户要求先关掉侵蚀**，以便确认普通地形（海陆分布层）的样子。
    //
    // 【为什么关】用户在【海边平原】也看到了侵蚀效果，而设计意图是「只在山地侵蚀」。
    //   根因在 SimTerrain.java:317 的 ERO_GATE_FLOOR = 0.5：
    //     gateEff = ERO_GATE_FLOOR + (1 - ERO_GATE_FLOOR) * gate = 0.5 + 0.5*gate
    //     ⟹ 平原（gate=0）仍然有 gateEff = ★ 0.5 的侵蚀
    //   而 SimTerrain.java:664 的注释写的是「平原 gate≈0（完全不侵蚀）」⟹ **注释与常量矛盾**。
    //
    // ★ 后续计划（用户）：侵蚀只用在【海陆分布输出为山地】的位置。
    //   届时需要：① 修正 ERO_GATE_FLOOR（或让门控真正门控）
    //             ② 门控信号来自「海陆层输出的山地标记」
    //
    // ★ 关闭方式：这是【总开关】，false 时整段侵蚀代码不执行（含 ERO_GATE_FLOOR）
    public static boolean EROSION_ENABLED = false;

    /**
     * <b>侵蚀输入高度场的最粗波长（block）。</b>
     *
     * <p>{@code §7722}：本仓地形基础高度最短波长 {@code TalosField.HF_WL_MIN = 46,875 m}，
     * 所以侵蚀必须在 50 km 以下自己提供全部结构。
     */
    public static final double ERO_WL0 = 50000.0;   // ★ 2026-10-08 回退：地形层与行星尺度无关（TILE/CELL 服务 MC 分辨率与地形质量）

    /**
     * <b>侵蚀输入高度场的八度数 —— §7730 的关键标定。</b>
     *
     * <p><b>为什么是 12</b>：runevision 博客逐字给出前提
     * 「the gradient of the height function <b>doesn't change too drastically within a
     * single cell</b>」。而 cell = {@code SCALE·CELLSCALE·ERO_UNIT} = 0.105 × 300 = <b>31.5 格</b>。
     * <ul>
     *   <li>最细波长 <b>远大于</b> cell（旧值 OCT=8 ⇒ 390 格）⟹ 一个 cell 内梯度几乎不变
     *       ⟹ cell 边界【可见】（用户放大图确认的多边形线）</li>
     *   <li>最细波长 <b>远小于</b> cell（OCT=13 ⇒ 12.2 格）⟹ 文章说的 grainy noise</li>
     * </ul>
     * 取 OCT=12 ⟹ 最细波长 = 50000/2^11 = <b>24.4 格 ≈ cell</b> ⟹ 甜点。
     */
    public static final int ERO_OCT = 10;

    /**
     * <b>1 个世界单位的长度（block）。</b>
     * {@code cell = SCALE·CELLSCALE·UNIT = 0.105·UNIT}；取 300 ⟹ cell = 31.5 格。
     */
    public static double ERO_UNIT = 1000.0;   // ★ 2026-10-08 回退：地形层与行星尺度无关（TILE/CELL 服务 MC 分辨率与地形质量）   // non-final: scale sweeps (P1394) + ledger

    /** <b>侵蚀强度</b>（{@code ErosionFilter.erosion} 的 {@code strength}）。 */
    public static final double ERO_STRENGTH = 0.267;

    /**
     * <b>侵蚀输出的缩放（block）。§7736 标定：400 -> 21。</b>
     *
     * <p><b>根因</b>（P1379~P1386 实测，报告 {@code K:/moder/Talos2/侵蚀线条-根因链.md}）：
     * 图上那些「多边形/直边接缝」<b>不是</b> cell 边界的折角 —— P1380 实测边界处方向变化
     * 0.55 度 vs cell 内部 0.46 度（比 1.19，<b>没有折角</b>）；P1384 把输入梯度方向场的波长
     * 从 1e9 扫到 13 格，<b>格子纹丝不动</b>（gridScore 恒 1.0）。
     *
     * <p>真因是<b>幅值尺度</b>：侵蚀在 105 格上的起伏 rms = <b>59.30 格</b>，
     * 而基础地形同一尺度的 rms 只有 <b>0.158 格</b> ⟹ <b>375 倍</b>。
     * 也就是说侵蚀不是「地形上的扰动」，它<b>就是</b>地形。
     * 参考实现（{@code lpmitchell.cs:311-312}）的用法是
     * {@code eroded = n.x + h.x + offset}，侵蚀是归一化高度场上的<b>小扰动</b>。
     *
     * <p><b>标定依据</b>（P1386，生产公式含 gate 与 ERO_SINK）：接缝幅度与 ERO_AMP
     * <b>严格线性</b>（8 个取样点，相邻比值恒为 2.00，{@code seam/ERO_AMP = 0.0395}）：
     * <pre>
     *   ERO_AMP  400 -> seam 15.81 格（多边形清晰可见）
     *   ERO_AMP   21 -> seam  0.83 格（低于方块分辨率 ==> 不可见）
     * </pre>
     * 阈值：要 seam &lt; 1 格（= 1 方块，物理上不可见）需 {@code ERO_AMP <= 25}。
     *
     * <p>⚠ <b>代价（必须记账）</b>：侵蚀对地形总起伏的贡献随之同比例下降
     * ⟹ 沟壑变成细纹理而不是地形特征。这是「沟壑清晰」与「接缝不可见」不可兼得的
     * 定量形式，用户裁决为<b>取后者</b>。
     */
    public static double ERO_AMP = 150.0;

    /**
     * <b>§7727：侵蚀输出的【下移量（block）】—— 让侵蚀只往下挖，不往上堆。</b>
     *
     * <p>原始输出零均值，正偏移会把地形抬到 {@code SOFT_CAP_H = 252} 以上被压成平台，
     * 平台边缘在图上就是平滑长曲线。{@code P1363} 实测（AMP=400）输出范围
     * {@code -27.33 .. +77.39} ⟹ 取 80 ⟹ 输出落 {@code [-107, -3]}，恒为负。
     *
     * <p>⚠⚠ <b>§7736：它是 DC 中和量 ⟹ 必须与 {@link #ERO_AMP} <b>同比例</b>缩放。</b>
     * 实测输出均值 {@code mean(dh) = 0.447} ⟹ 施加量
     * {@code (dh - 0.447) * ERO_AMP} 才是不抬高地形的净效果 ⟹ 需要
     * <b>{@code ERO_SINK = 0.447 * ERO_AMP}</b>。旧值 80 = 0.447 × 179 ⟹
     * 它当时中和的是 <b>ERR_AMP=400 下 dh 的均值</b>（0.20 × 400 = 80）。
     *
     * <p><b>§7737 实测比例</b>（P1390，20 km 陆地窗口 40000 点，<b>不是猜的</b>）：
     * {@code mean(dh*gate)/mean(gate) = 0.2760} ⟹ <b>{@code ERO_SINK = 0.276 * ERO_AMP}</b>。
     * <pre>
     *   ERO_AMP 400 -> 110.4（旧值 80 偏小，地形被净抬高 30 格）
     *   ERO_AMP  21 ->   5.8
     * </pre>
     * <b>不同比例改它会把海陆比搞坏</b>：整片地形被净抬高或压低 ⟹ 低地跌破海平面或海岸线消失。
     *
     * <p>★ 注意 {@code ERO_SINK} 是<b>纯偏移</b>（对整片地形加常数）⟹ <b>不影响接缝</b>
     * （接缝由 {@code ERO_AMP} 决定）。这一条已由 P1386 的线性关系佐证。
     */
    public static double ERO_SINK = 41.4;   // non-final: follows ERO_AMP

    /**
     * <b>§7732（甲）：把【真实地形梯度】加进侵蚀输入的比例（0 = 不混）。</b>
     *
     * <p>依据（runevision 博客逐字）：「the pivot point is never too far away.
     * <b>At least as long as the gradient of the height function doesn't change too
     * drastically within a single cell.</b>」
     *
     * <p>纯均匀 fBm 的梯度方向处处一致 ⟹ cell 边界可见（§7728 的 P1342 二分实测）。
     * 叠加真实地形（米制海拔，4 km 基线）的梯度后，方向随位置剧变 ⟹ 打散 cell。
     */
    public static double ERO_REAL_GRAD = 1.0;   // ★ §7793：曾被置 0（补救块从未执行）=> 墙 27 格。P1544 实测开启后 max 台阶 25.71 -> 7.72

    /** 侵蚀细节层的种子扰动（与山地细节层解耦）。 */
    public static final long ERO_SEED_XOR = 0x5A5AL;

    /** 一列的合成高度及其全部中间量（**复用容器**，见 {@link #composeColumn}）。 */
    public static final class Column {
        /** 该列是否陆地（与传入的 {@code o.isLand} 一致）。 */
        public boolean land;
        /** 海洋列水深（blocks，>0）；陆地列恒为 0。 */
        public double seaDepth;
        /** 基础地形分解：base = 含山地/峰档的全量，plain = 瘦身版（山地档并入丘陵）。 */
        public double base, plain;
        /** 基础山地贡献 = max(0, base − plain)。 */
        public double mtnComp;
        /** 山层权威权重 w（0=基础地形全权，1=山层全权）与山层抬升量（blocks）。 */
        public double auth, uplift;
        /** 传给 {@link #mountainDetail} 的强度（已含坡度调制，已 clamp 到 [0,1]）。 */
        public double detailStrength;
        /** 基础地形 + 山层仲裁，**未加块级细节、未软封顶**。 */
        public double hNoDetail;
        /** 加上块级细节后、**未软封顶** —— 这是算坡度的口径（见 {@link #composeColumn}）。 */
        public double hDetail;
        /** 软封顶后的连续高度 —— 这是生产判定雪线/沙滩、以及取整铺方块的口径。 */
        public double hCapped;
        /** {@code round(hCapped)} 再 clamp 到 [1, maxY] 的方块高度（= 生产铺方块的列顶）。 */
        public int h;
        /** 是否在雪线以上 / 是否贴岸沙滩（口径与生产逐位一致）。 */
        public boolean snow, beach;
    }

    private static final ThreadLocal<Column> COLUMN = ThreadLocal.withInitial(Column::new);
    private static final ThreadLocal<double[]> COMPOSE_BP = ThreadLocal.withInitial(() -> new double[2]);
    /** §7716：侵蚀的梯度 scratch（[0]=dx, [1]=dz），避免每列分配。 */
    private static final ThreadLocal<double[]> ERO_GRAD = ThreadLocal.withInitial(() -> new double[2]);

    /**
     * **块级合成高度的唯一入口**：生产（{@code ChunkProviderTalos2.fillLandColumnV2}）、
     * {@code /talosmap} 的 terrain 图层、{@code /talos_here} 的面板，三处都必须走这里。
     *
     * <h3>为什么必须唯一</h3>
     * 这条链原先在 4 处各写了一遍，口径**互不相同**，而且没有任何机制能发现：
     * <pre>
     *   生产 fillLandColumnV2   : 群系 bias/scale + slope01 调制 + 软封顶        ← 权威口径
     *   生产 columnHeight       : 同上但**不软封顶**（坡度估计刻意用未封顶高度）
     *   /talosmap  terrain 图层 : landBaseHeight/landPlainHeight（**丢了群系 bias/scale**）
     *                             + 细节**无 slope01 调制** + 无软封顶
     *   /talos_here 面板        : 同上，且**连 mountainDetail 都没有** —— 却打印
     *                             "→ 合成高度"和"(雪线以上)"
     * </pre>
     * 后果：地图与面板上的"高度"不是世界里的高度，而"雪线以上"在被显示的那个口径上
     * 几乎永远为假（少了细节噪声，高度偏低 10~40 blocks）。
     *
     * <h3>复用容器的使用纪律</h3>
     * 返回值是**每线程复用**的对象。调用方必须**立刻**把需要的字段拷进局部变量；
     * 之后的任何一次 {@code composeColumn} 都会覆盖它 —— 尤其是 {@code columnSlope}
     * 那种连续调用 4 次的场景。
     *
     * @param maxY 列顶方块上限（生产传 {@code worldHeight - 2}，命令类传
     *             {@link #MC_WORLD_HEIGHT} {@code - 2}）
     */
    public static Column composeColumn(int x, int z, int worldSeedInt, int seaLevel,
                                       OrographyField.OroSample o,
                                       double biomeBias, double biomeScale, int maxY) {
        Column c = COLUMN.get();
        // ---- 新模拟器的运行时分派（§83）--------------------------------------------
        // 这是本文件里**唯一**为接线而加的东西：一行分派。ENABLED=false 时，
        // 下面每一行都与接线前**逐位相同**；旧实现一个方法、一个常量都没有改动或删除。
        if (com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.ENABLED) {
            return com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.compose(c, x, z, worldSeedInt, seaLevel, maxY);
        }
        // --------------------------------------------------------------------------
        if (!o.isLand) {
            c.land = false;
            c.seaDepth = seaDepthBlocks(x, z, worldSeedInt);
            c.base = 0.0;
            c.plain = 0.0;
            c.mtnComp = 0.0;
            c.auth = 0.0;
            c.uplift = 0.0;
            c.detailStrength = 0.0;
            c.hNoDetail = seaLevel - c.seaDepth;
            c.hDetail = c.hNoDetail;
            c.hCapped = c.hNoDetail;
            int hs = (int) Math.round(c.hCapped);
            c.h = hs < 1 ? 1 : (hs > maxY ? maxY : hs);
            c.snow = false;
            c.beach = false;
            return c;
        }
        double[] bp = COMPOSE_BP.get();
        baseAndPlain(x, z, worldSeedInt, seaLevel, o, biomeBias, biomeScale, bp);
        c.land = true;
        c.seaDepth = 0.0;
        c.base = bp[0];
        c.plain = bp[1];
        c.mtnComp = c.base > c.plain ? c.base - c.plain : 0.0;
        c.auth = MountainLayerV2.auth(x, z, worldSeedInt);
        c.uplift = MountainLayerV2.uplift(x, z, worldSeedInt);
        c.hNoDetail = c.plain + (1.0 - c.auth) * c.mtnComp + c.auth * c.uplift;
        double mtnAmt = Math.max(c.auth, Math.min(1.0, c.mtnComp / DETAIL_MTNCOMP_SCALE));
        double slope01 = MountainLayerV2.slope01(x, z, worldSeedInt);
        c.detailStrength = Math.min(1.0, mtnAmt * (1.00 + DETAIL_SLOPE_GAIN * slope01));
        c.hDetail = c.hNoDetail + mountainDetail(x, z, worldSeedInt, c.detailStrength);
        c.hCapped = c.hDetail;
        if (c.hCapped > SOFT_CAP_H - 6.0 * SOFT_CAP_K) {
            c.hCapped = SOFT_CAP_H - SOFT_CAP_K
                * Math.log1p(Math.exp((SOFT_CAP_H - c.hCapped) / SOFT_CAP_K));
        }
        int h = (int) Math.round(c.hCapped);
        c.h = h < 1 ? 1 : (h > maxY ? maxY : h);
        c.snow = c.hCapped >= snowLineY(z);
        c.beach = !c.snow && isBeachLand(o, c.hCapped, seaLevel);
        return c;
    }

    /** 同上，但群系高度倾向由本方法自己采（{@link V2BiomeField#sample}）。 */
    public static Column composeColumn(int x, int z, int worldSeedInt, int seaLevel,
                                       OrographyField.OroSample o, int maxY) {
        V2BiomeField.Sample bs = V2BiomeField.sample(x, z, worldSeedInt, true);
        return composeColumn(x, z, worldSeedInt, seaLevel, o, bs.bias, bs.scale, maxY);
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
        double r1 = PeriodicNoise.ridged2(0x9E37L ^ worldSeedInt, x, z, DET1_NX, DET1_NZ, 3);
        double r2 = PeriodicNoise.ridged2(0xC2B2L ^ worldSeedInt, x, z, DET2_NX, DET2_NZ, 2);
        return (r1 * 24.0 + r2 * 10.0) * (0.15 + 0.85 * s);
    }

    /**
     * 中尺度脊线纹理的**唯一**种子来源。
     *
     * 这里曾经是两套：{@code V2TerrainGen} 自己乘 {@code 0x9E3779B97F4A7C15L}，
     * 而 {@code MountainLayerV2.ridgeTexture} 传的是**裸种子** —— 于是同一句注释
     * （"山层与基础山地共用同一套频谱、脊线对齐"）在两边其实是**两个不同的噪声实现**，
     * 山带的脊线和它所在那块高地的脊线根本对不上，过渡处自然也就谈不上自然。
     * 现在乘法常量只活在这一个方法里，两处调用点都从这里取，物理上不可能再漂。
     */
    public static long textureSeed(int worldSeedInt) {
        return worldSeedInt * 0x9E3779B97F4A7C15L;
    }

    /**
     * 中尺度山体纹理（λ ≈ 3k / 1.2k / 480m，ridged，返回 0..1）：
     * 山层（MountainLayerV2）与基础山地共用同一套频谱，保证"只要成山就有脊谷"。
     *
     * @param seed 必须来自 {@link #textureSeed(int)} —— 直接传裸种子会拿到另一套噪声。
     */
    public static double mountainTexture(long seed, double x, double z) {
        double sum = 0.0, norm = 0.0, amp = 1.0, f = mtnTexFreq;
        for (int o = 0; o < 3; o++) {
            double n = PeriodicNoise.gradientFbm2(seed + o * 0x51ED270BL, x, z,
                PeriodicNoise.cellsXFromFreq(f), PeriodicNoise.cellsZFromFreq(f), 2);
            double r = 1.0 - Math.abs(n * 0.7);
            if (r < 0.0) r = 0.0;
            sum += amp * r * r;
            norm += amp;
            amp *= o == 0 ? 1.0 : 0.70;
            f *= 2.5;
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
        double mid = PeriodicNoise.warpedFbm2(0x51E5A2D9L ^ worldSeedInt, x, z,
            SEABED_NX, SEABED_NZ, 3, SEABED_WNX, SEABED_WNZ, 900.0);
        d += SEABED_RELIEF * mid / 1.75;
        return d;
    }

    /** 陆侧贴岸低地是否为沙滩带（surfaceY ≤ 海面+5 才铺沙，防止山岸变沙）。 */
    public static boolean isBeachLand(OrographyField.OroSample o, double surfaceY, int seaLevel) {
        return o.coastDist > -BEACH_LAND_BLOCKS && surfaceY <= seaLevel + 5.0;
    }

    /** 该纬度雪线高度（赤道 {@link #SNOW_EQUATOR_Y} / 极地 {@link #SNOW_POLE_Y} 线性插值；bandD：0=赤道 1=极地）。 */
    public static double snowLineY(int worldZ) {
        // ⚠ 2026-09-13 修正（审计 D17）：原来用 GlobalCirculation.bandD —— 那条链的纬度周期是
        // ClimateLatitudes.LAT_CYCLE = 1_000_000，而世界契约 WorldContract.Z_CYCLE = 20_000_000，
        // **差 20 倍**。后果：群系的 ALPINE 雪线门在新世界里每 500 km 来回摆一次 185<->128 格，
        // 与真实地形高度无关；而方块的雪用的是新温度口径（SimTerrain.SNOW_FROM_TEMP）
        // ⇒ **同一列两套判据**，CommandTalosHere 还把两者并排打印、自相矛盾。
        // 现改用 WorldContract.bandD（与方块、与气候同一个纬度周期）。
        // ⚠ 仍未统一的是**判据本身**（这里几何、方块温度）—— 那要把 SimClimate 引进群系 LUT 求解，
        //   代价高，单独一批。本修正只消掉 20 倍的周期错。
        double b = clamp01(com.EyeOfHarmonyBuffer.sim.world.WorldContract.bandD(worldZ));
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
