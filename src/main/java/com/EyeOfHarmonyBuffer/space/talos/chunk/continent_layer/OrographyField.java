package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 大陆内部结构场（L1b 地形骨架 · 直接从海陆高度场派生，无板块假设）。
 *
 * 双轨输出：
 *   A. **orography 级连续场**（供气候/气团/风等消费——它们只需要"多高/哪里是山"）：
 *      - elevation01 [0,1]：内陆海拔 = 陆地残差 r=h'-T(&gt;=0) 用每种子 q93 标尺归一化。
 *        海岸≈0 → 深内陆≈1（大陆中心天然是"最深"处 → 内陆抬升/高原腹地）；
 *      - relief01   [0,1]：山地强度 = 1 - dev/q95（dev=|2m-1|，m=λ20k 中频场，0=脊线零交叉；
 *        per-seed dev q95 归一，避免窄动态范围饱和）。脊线与海拔场独立 → 山链随机蜿蜒穿过
 *        内陆与高原（无等距山环、无"高原同心圈"）；
 *   B. **离散地形分档 kind**（供下游地形/宏群系/山生成等层用）：
 *      LOWLAND / HILL / PLATEAU / MOUNTAIN / PEAK。占比按种子 dev 分位标定保证
 *      （见 KIND_* 常量注释），四种子实测：低地+丘陵 51~52%、高原 17%、山 28~29%、峰 ~1%。
 *
 * 已知教训（design.md D28）：① 不能用固定阈值切 ridged（动态范围窄 → 饱和或 0 值巨量
 * 并列、分位塌陷）② 分位前必须排序 ③ 山链判定不得掺"离岸距离"等高程相关门（等距山环）。
 * 未来 PlateField 只需替换 ridgeDev 来源，下游 API 不变。纯函数 O(1)：每点 ≈ 残差1 +
 * medNoise1 + coastDistBlocks5 次 height（首次标定 ~1250 点几十 ms，缓存）。
 */
public final class OrographyField {

    private OrographyField() {}

    /** 地形种类（数值稳定，供映射表 / 群系 / 山生成使用）。 */
    public static final int KIND_LOWLAND = 0;
    public static final int KIND_HILL = 1;
    public static final int KIND_PLATEAU = 2;
    public static final int KIND_MOUNTAIN = 3;
    public static final int KIND_PEAK = 4;

    // ---- 地形构成目标（占陆地比例，dev 分位法保证） ----
    /** 峰 = dev 最小（最贴脊线）的陆地份额（再要求海拔≥PEAK_MIN_ELEV）。 */
    // ★★★★★★★ 2026-10-08 修复：kind 与地形脱钩（实测 MOUNTAIN 占 34.44%，而其列顶均值
    //   只有 y=80.3，与 LOWLAND 的 69.0 几乎一样 ⟹ 34% 的陆地挂着「山地」群系
    //   却坐在平坦平原上 ⟹ 游戏里满大陆是石质/裸露的棕灰地表）。
    //   根因：kind 原来按 dev=|velField(..,0)| 的分位数切，而实际地形来自
    //   coastProfileCF + orogeny —— 两个场完全不同。
    //   修法：kind/relief 改由【实际造山场 orogeny01】派生，份额按地球标定。
    /** 峰 = 造山最强的陆地份额。地球「高峰」远小于 1% ⟹ 取 0.8%。 */
    private static final double KIND_PK_TOP = 0.008;
    /** 山+峰 = dev 最小的陆地份额（峰先取走，其余为山）。 */
    /** 山 = 造山最强的陆地份额。地球视觉上的山地约 10~15% ⟹ 取 12%。 */
    private static final double KIND_MTN_TOP = 0.12;
    /** 高原 = 剩余（非山地）陆地里海拔最高者的份额 → 低地+丘陵 ≈ 1-0.30-0.17 ≈ 53%。 */
    private static final double KIND_PLATEAU_TARGET = 0.12;   // ★ 0.17->0.12（与山的 12% 合计约 24%，接近地球的台地+山地）
    /** 丘陵门槛（展示量 relief/海拔的固定切分，只影响低地与丘陵的比例）。 */
    private static final double KIND_HILL_RELIEF = 0.30;
    private static final double KIND_HILL_ELEV = 0.20;

    // ---- 海拔 ----
    /** 海拔 smoothstep 沿（×q93）。 */
    private static final double ELEV_EDGE_LO = 0.10;
    private static final double ELEV_EDGE_HI = 1.25;
    /** 峰所需的最低海拔。 */
    private static final double PEAK_MIN_ELEV = 0.30;
    // ---- 贴岸淡化（只消最贴岸 ~3k，防贴水线伪影；不做等距山环） ----
    private static final double SHORE_START = 200.0;
    private static final double SHORE_FULL = 875.0;
    // ---- 标定 ----
    private static final int CALIBRATE_STRIDE = 2000;   // ★ 2026-10-08 回退：地形层与行星尺度无关（TILE/CELL 服务 MC 分辨率与地形质量）
    /**
     * 标定扫描域（blocks）。**这是旧环面世界留下的常量，不是从任何现值派生的** —— 提成具名
     * 常量只是为了让"这两个 400k/200k 从哪来"可检索。
     *
     * ⚠️ 契约变更后 X 无限、Z 不重复，这个扫描域只覆盖世界的一小块角落；标定结果会进 CUTOFF_CACHE，
     * 进而影响地形。**改扫描域 = 改世界生成**，所以本轮只做"值不变、改成具名常量"，是否要换域
     * 需要单独拍板（见交付报告里的 flag）。
     */
    private static final int CALIBRATE_X_SPAN = 400_000;   // ★ 2026-10-08 回退：地形层与行星尺度无关（TILE/CELL 服务 MC 分辨率与地形质量）
    private static final int CALIBRATE_Z_SPAN = 200_000;   // ★ 2026-10-08 回退：地形层与行星尺度无关（TILE/CELL 服务 MC 分辨率与地形质量）

    /** 每种子标定结果缓存。 */
    private static final ConcurrentHashMap<Integer, Cutoffs> CUTOFF_CACHE =
        new ConcurrentHashMap<Integer, Cutoffs>();

    /** 分位阈值组（dev 升序 + 条件海拔，避免 0 值并列塌陷）。 */
    private static final class Cutoffs {
        /** ★ 2026-10-08：字段改为【造山场】的分位（原来是 dev 的）。 */
        final double orogPeakQ;    // 峰：orogeny >= orogPeakQ（且海拔达标）
        final double orogMtnQ;     // 山：orogeny >= orogMtnQ
        final double plateauElevQ; // 高原：非山地中 elevation >= plateauElevQ

        Cutoffs(double orogPeakQ, double orogMtnQ, double plateauElevQ) {
            this.orogPeakQ = orogPeakQ;
            this.orogMtnQ = orogMtnQ;
            this.plateauElevQ = plateauElevQ;
        }
    }

    /** 采样结果（连续场 + 离散分档并存）。 */
    public static final class OroSample {
        public final boolean isLand;
        /** 内陆海拔 [0,1]：海岸≈0、深内陆≈1（orography 级，气候用）。 */
        public final double elevation01;
        /** 山地强度 [0,1]：1 - dev/q95，越大越贴山脊（orography 级，气候用）。 */
        public final double relief01;
        /** 山地覆盖度 [0,1]（relief01 的平滑版，可直接当遮罩相乘）。 */
        public final double beltMask01;
        /** 地形种类（KIND_*；供地形/宏群系/山生成层用）。海洋点恒为 LOWLAND，消费方先查 isLand。 */
        public final int kind;
        /** 原始陆地残差（r = h' - T ≥ 0）。 */
        public final double residual;
        /** 有符号海岸距离（block：&lt;0 内陆、0 岸线、&gt;0 海上；陆地采样与 relief 同源同值，免二次采样）。 */
        public final double coastDist;
        /**
         * ★★★★★★★ 2026-10-08 新增：<b>造山强度 [0,1]</b>（从海陆分布层派生）。
         *
         * <p>= <b>窄脊线带</b> × <b>板块汇聚度</b>：
         * <pre>
         *   beltNarrow = clamp01(1 − dev/OROG_BELT_W)^2      // dev = |velField(..,0)| 的零集 = 脊线
         *   orogeny01  = beltNarrow × TalosLandField.convergence01(..)   // −div v 归一
         * </pre>
         * <p>两者都来自海陆分布层**自己的**连续场（速度场与其散度），不依赖任何外挂山脉层。
         */
        public final double orogeny01;

        OroSample(boolean isLand, double elevation01, double relief01,
                  double beltMask01, int kind, double residual, double coastDist, double orogeny01) {
            this.isLand = isLand;
            this.elevation01 = elevation01;
            this.relief01 = relief01;
            this.beltMask01 = beltMask01;
            this.kind = kind;
            this.residual = residual;
            this.coastDist = coastDist;
            this.orogeny01 = orogeny01;
        }

        @Override
        public String toString() {
            return String.format("Oro[%s elev=%.2f belt=%.2f kind=%d]",
                isLand ? "LAND" : "SEA", elevation01, relief01, kind);
        }
    }

    /** 中频偏差 dev（0=脊线零交叉，越大离脊越远；λ20k 场）。 */
    public static double ridgeDev(int x, int z, int worldSeedInt) {
        double med = NoiseContinentGrid.medNoise(x, z, worldSeedInt);
        return Math.abs(2.0 * med - 1.0);
    }

    // ================= ★★★★★★★★ 采样级记忆化 =================
    //
    // 【为什么必须有】
    //   sample() 每点调用：landResidual + ridgeDev(->medNoise) + coastDistBlocks + reliefFromDev
    //   其中 coastDistBlocks 内部是 625 细胞的扫描 ⟹ 实测约 400 us/点。
    //   LandformField.solve 有 400x200 = 80,000 点 ⟹ 32 秒 ⟹ 与实测 27~44 秒吻合。
    //
    // 【关键：多处重复计算同一坐标】
    //   relieveFromDev 需要 coastDist，而 sample 也算一次 ⟹ 同一坐标被重复调用。
    //   且 V2BiomeField.solve / LandformField.solve 会在相邻瓦片重复采样同一区域。
    //   ⟹ ThreadLocal 记忆化（多线程各自一份，无锁）
    private static final ThreadLocal<java.util.HashMap<Long, OroSample>> SAMPLE_MEMO =
        ThreadLocal.withInitial(java.util.HashMap::new);
    public static boolean SAMPLE_MEMO_ON = true;

    public static void clearSampleMemo() { SAMPLE_MEMO.get().clear(); }

    /** 单点采样（世界 block 坐标，任意范围）。 */
    public static OroSample sample(int x, int z, int worldSeedInt) {
        // ★ 记忆化：同一坐标只算一次（键 = x,z 打包）
        long mkey = 0;
        java.util.HashMap<Long, OroSample> memo = null;
        if (SAMPLE_MEMO_ON) {
            memo = SAMPLE_MEMO.get();
            mkey = (((long) x) << 32) ^ (z & 0xFFFFFFFFL);
            OroSample hit = memo.get(mkey);
            if (hit != null) return hit;
        }
        OroSample res = sampleRaw(x, z, worldSeedInt);
        if (memo != null) { if (memo.size() > 65536) memo.clear(); memo.put(mkey, res); }
        return res; }

    private static OroSample sampleRaw(int x, int z, int worldSeedInt) {
        // ★★★★★★★ 2026-10-08 修复【两套口径】：陆海判定必须用【同一个函数】
        //
        // 【原 bug】这里用 `landResidual > 0`（= 高度符号）判陆海，
        //   而 `SimTerrain.compose` 用 `PlateField.isLand`（B 方案后 = landScore ≥ 0.5）。
        //   后果：`/talos_tp hill` 说「陆」，传送过去却是**深海**（实测）。
        // 【为什么不能用高度符号】新架构里 height = (base+hfeat)×gain（连续混合），
        //   高度符号**不再等价于**陆海。
        // 【正解】直接调 `TalosLandField.isLand` ⟹ 与 compose 逐位一致。
        if (!TalosLandField.isLand(NoiseContinentGrid.wsOf(worldSeedInt), x, z)) {
            return new OroSample(false, 0.0, 0.0, 0.0, KIND_LOWLAND, 0.0, 0.0, 0.0); }
        double r = NoiseContinentGrid.landResidual(x, z, worldSeedInt);
        Cutoffs c = cutoffsFor(worldSeedInt);
        double elevation = elevation01(r, worldSeedInt);
        double dev = ridgeDev(x, z, worldSeedInt);
        double d = NoiseContinentGrid.coastDistBlocks(x, z, worldSeedInt);   // 陆上 <0
        double relief = reliefFromDev(x, z, worldSeedInt, dev, d);   // 含贴岸淡化，与 relief01() 同口径

        // ★★★★★★★ 2026-10-08 修复：kind 改由【实际造山】判，不再用 dev。
        //   原实现使 34.44% 的陆地挂 MOUNTAIN 却坐在 y≈80 的平原上（实测）。
        double orog = orogenyFromDev(dev, x, z, worldSeedInt);
        int kind;
        if (orog >= c.orogPeakQ && elevation >= PEAK_MIN_ELEV) {
            kind = KIND_PEAK;
        } else if (orog >= c.orogMtnQ) {
            kind = KIND_MOUNTAIN;
        } else if (elevation >= c.plateauElevQ) {
            kind = KIND_PLATEAU;
        } else if (relief >= KIND_HILL_RELIEF || elevation >= KIND_HILL_ELEV) {
            kind = KIND_HILL;
        } else {
            kind = KIND_LOWLAND;
        }

        // ★ beltMask01 的输入从「dev 型 relief」换成了「造山型 relief」⟹ 边界重新标定：
        //   实测 orogeny > 0.05 覆盖陆地约 20%（山体走廊）、> 0.35 覆盖约 5%（脊线）
        return new OroSample(true, elevation, relief, smoothstep(0.05, 0.35, relief), kind, r, d,
            orog);
    }

    // ================= ★★★★★★★ 造山场（从海陆分布派生） =================
    /**
     * 造山带的【窄带宽度】（dev 单位，dev ∈ [0,1]）。
     *
     * <p>{@code dev = ridgeDev = |velField(..,0)|}，它的零集是一条条**平滑的曲线网络**
     * ⟹ 「dev 小」= 脊线走廊。{@code relief01} 用的是 {@code devQ95} 归一（很宽，
     * 实测 beltMask01&gt;0.5 覆盖陆地 24.79%）；造山要的是**窄而清晰的山脉**，
     * 所以这里另取一个窄尺度并平方锐化。
     */
    // ★★★★★★★ 2026-10-08 标定（实测扫描，见 待裁决/085）：
    //   dev 的 mean|grad| = 2.917e-06 /block ⟹ 带宽 ≈ 2*W/|grad dev|
    //     W=0.30 -> 带宽 76,000 格，陆地覆盖 8.41%   （太宽，成团块不成山脉）
    //     W=0.15 -> 带宽 44,000 格，陆地覆盖 3.99%
    //     W=0.08 -> 带宽 25,000 格，陆地覆盖 2.05%
    //   ⟹ 取 0.20：山脉宽约 50 km、覆盖约 5%，既线性又有存在感。
    // ★★★★★★★ 2026-10-08 标定：0.20 -> 0.55
    //   （0.20 是「窄带」，但在收敛门槛 2.5e-6 + 造山 130 格下覆盖率只有约 3%）
    //   实测组合（C1=2.5e-6, UPLIFT=180）：W=0.35 -> 4.51%，W=0.50 -> 9.72%，W=0.70 -> 16.78%
    public static double OROG_BELT_W = 0.55;

    /** 造山强度 [0,1] = 窄脊线带 × 板块汇聚度。 */
    public static double orogenyFromDev(double dev, int x, int z, int worldSeedInt) {
        // ★★★★★★★ 2026-10-08（用户裁决 c 方案）：带改用【Voronoi 细胞边界】，
        //   强度仍用【板块汇聚度】。两者都在 TalosLandField 里（本层只做转发）。
        //   实测：速度零集网络 与 细胞边界网络 的相关系数只有 0.0031 ⟹ 必须换成细胞边界。
        //   （dev 参数保留是为了不改签名；已不再参与计算。）
        return TalosLandField.orogeny01(NoiseContinentGrid.wsOf(worldSeedInt), x, z); }

    /** 造山强度（按坐标；供无 OroSample 的调用方）。 */
    public static double orogeny01(int x, int z, int worldSeedInt) {
        return orogenyFromDev(ridgeDev(x, z, worldSeedInt), x, z, worldSeedInt); }

    /** 内陆海拔（陆地残差 → [0,1]）。 */
    public static double elevation01(double residual, int worldSeedInt) {
        double rQ = NoiseContinentGrid.residualScale(worldSeedInt);
        if (rQ <= 0.0) {
            rQ = 1.0;
        }
        return smoothstep(ELEV_EDGE_LO, ELEV_EDGE_HI, residual / rQ);
    }

    /** 山地强度（dev q95 归一 × 贴岸淡化）。 */
    public static double relief01(int x, int z, int worldSeedInt) {
        return reliefFromDev(x, z, worldSeedInt, ridgeDev(x, z, worldSeedInt));
    }

    /** 山地强度核心（调用方已有 dev 时用，避免重复算 medNoise）。 */
    private static double reliefFromDev(int x, int z, int worldSeedInt, double dev) {
        double d = NoiseContinentGrid.coastDistBlocks(x, z, worldSeedInt);   // 陆上 <0
        return reliefFromDev(x, z, worldSeedInt, dev, d);
    }

    /** 山地强度核心（调用方已有 dev 与 coastDist 时用，避免重复算 medNoise + 海岸梯度）。 */
    private static double reliefFromDev(int x, int z, int worldSeedInt, double dev, double d) {
        // ★★★★★★★ 2026-10-08 修复：relief01 改为【实际造山强度】，不再用 dev 的分位。
        //   原实现 micro = 1 - dev/devQ95 在 dev 小于 q95 处恒为正 ⟹ 95% 的陆地都有
        //   relief > 0，实测 mean = 0.4814、>0.5 占 50.24% ⟹ 与真实地形无关。
        //   现在 relief01 = orogeny01（造山场：板块边界核 x 汇聚度），
        //   与实际抬升【同源】⟹ relief 高 ⟺ 真的是山。
        return TalosLandField.orogeny01(NoiseContinentGrid.wsOf(worldSeedInt), x, z);
    }

    // ======== 按种子标定（dev 升序分位 + 条件海拔分位） ========

    private static Cutoffs cutoffsFor(int worldSeedInt) {
        Cutoffs c = CUTOFF_CACHE.get(worldSeedInt);
        if (c != null) {
            return c;
        }
        return CUTOFF_CACHE.computeIfAbsent(worldSeedInt, OrographyField::calibrate);
    }

    private static Cutoffs calibrate(int worldSeedInt) {
        int nx = CALIBRATE_X_SPAN / CALIBRATE_STRIDE;
        int nz = CALIBRATE_Z_SPAN / CALIBRATE_STRIDE;
        int max = nx * nz;
        double[] orogs = new double[max];
        double[] elevs = new double[max];
        int m = 0;
        for (int z = 0; z < CALIBRATE_Z_SPAN; z += CALIBRATE_STRIDE) {
            for (int x = 0; x < CALIBRATE_X_SPAN; x += CALIBRATE_STRIDE) {
                double r = NoiseContinentGrid.landResidual(x, z, worldSeedInt);
                if (!NoiseContinentGrid.isLandResidual(r)) {
                    continue;
                }
                orogs[m] = TalosLandField.orogeny01(NoiseContinentGrid.wsOf(worldSeedInt), x, z);
                elevs[m] = elevation01(r, worldSeedInt);
                m++;
            }
        }
        if (m == 0) {
            return new Cutoffs(1.0, 0.0, 1.0);
        }
        double[] orog = Arrays.copyOf(orogs, m);
        Arrays.sort(orog);   // 升序
        // ★ 造山是【越大越山】⟹ 取上分位
        double orogPeakQ = quantile(orog, 1.0 - KIND_PK_TOP);
        double orogMtnQ = quantile(orog, 1.0 - KIND_MTN_TOP);

        // 高原阈值：**非山地**（orogeny < orogMtnQ）中的海拔分位，
        //   使高原占全部陆地 KIND_PLATEAU_TARGET。
        double remain = 1.0 - KIND_MTN_TOP;   // 非山地占陆地比（≈0.88）
        double need = KIND_PLATEAU_TARGET / remain;
        double[] es = new double[m];
        int e = 0;
        for (int i = 0; i < m; i++) {
            if (orogs[i] < orogMtnQ) {        // ★ 非山地 = 造山小于门槛
                es[e++] = elevs[i];
            }
        }
        Arrays.sort(es, 0, e);   // quantile 需要升序！
        double plateauElevQ = (e > 0) ? quantile(Arrays.copyOf(es, e), 1.0 - need) : 1.0;
        return new Cutoffs(orogPeakQ, orogMtnQ, plateauElevQ);
    }

    private static double quantile(double[] sorted, double p) {
        if (sorted.length == 0) {
            return 1.0;
        }
        int idx = Math.min(sorted.length - 1, (int) Math.floor(p * sorted.length));
        if (idx < 0) {
            idx = 0;
        }
        return sorted[idx];
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
