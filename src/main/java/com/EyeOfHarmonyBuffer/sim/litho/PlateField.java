package com.EyeOfHarmonyBuffer.sim.litho;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.NoiseContinentGrid;

/**
 * ★ L1（地形/海陆 + 海岸）的**公开门面**与**度量钩子**。
 *
 * <h3>本类现在是什么（§567 之后）</h3>
 * 地形本体已唯一：{@link TalosField}（V8 生成器）。本类**不再生成任何地形**，只留三件事：
 * <ol>
 *   <li><b>委托</b>：{@link #elevation} / {@link #elevationWithCell} / {@link #isLand} /
 *       {@link #isLandWithCell} 全部转给 {@link TalosField}；</li>
 *   <li><b>度量钩子</b>：{@link LandMask} / {@link ElevSource}（地球验证层用，生产恒 {@code null}）；</li>
 *   <li><b>海岸度量的采样图案</b>：{@link #landScore} / {@link #landFractionWithCell}
 *       （建在 {@code isLandWithCell} 之上）与 {@link #coastDistanceNew}。</li>
 * </ol>
 *
 * <h3>§567：旧实现整支删除</h3>
 * 旧实现（Voronoi 板块场 + 造山边界特征 + 超宽大洋岛弧限制器 + 域扭曲 A1~A13 旋钮 +
 * {@code TALOS_TERRAIN} 开关）连同 86 个成员一起删除。施工图
 * {@code build/eoh_probe/mtn/delete_legacy_landsea_plan.md}，记账见设计冻结 §567。
 * 要看旧代码请用 git；删除前的工作树已整份备份到
 * {@code build/eoh_probe/mtn/_legacy_delete_backup/}（含 SHA256SUMS.txt）。
 *
 * <h3>纯函数纪律（设计冻结 §4）</h3>
 * 地形侧仍是**无状态纯函数 O(1)**：同 (seed, x, z) 永远同结果。本类里仅存的静态可变量是
 * 两个度量钩子（{@link #MASK} / {@link #ELEV}）与一个身份位（{@link #WORLD_IS_TALOS}），
 * 三者都**不参与地形生成**（前两者生产恒 null，后者是给测量溯源读的刻度）。
 */
public final class PlateField {

    private PlateField() {}

    // ==================== 常量 ====================

    /**
     * 板块格边长（block）——**只作为既有调用点的形参保留；新地形路（{@link TalosField}）不用这个值**。
     *
     * <p>§567 之后地形本体是 {@code TalosField}，它有**自己的**格边长
     * {@code TalosField.DCELL = L/9 = 2,222,222 m}（TalosField.java:25-27），
     * 与这里的 2,400,000 **不是同一个数**。本常量保留的原因：~200 支探针与 10 处 src 调用点
     * 仍把它当 {@code cell} 形参传（收下、已被忽略）。§7-2 裁决：本轮**不动值、不改名**。
     *
     * <p>历史（这个数字是怎么来的 —— 属于已删除的旧实现，保留只为解释来源）：
     * <pre>
     *   cell  G1b(西墙>=400km 面积比)  westRun中位  westRun最长  persist中位  陆地比
     *   300   0.058                     55 km       1105 km      420 km     0.184
     *   400   0.581                     95 km       1540 km      715 km     0.251
     *   600   0.589                    125 km       2255 km      595 km     0.268   ← P243 选中
     *   800   0.391(样本不足)            20 km       1675 km      240 km     0.231
     *  1200   0.383(样本不足)            15 km        475 km     2775 km     0.212
     * </pre>
     * 后来 §70 裁决 4 / A2 标定把它从 600,000 提到 2,400,000（P257 实测带内均速中位：
     * 600k → 43.7 mm/s 不达标，2400k → 71.0 mm/s），并补了双尺度造山带。
     */
    public static final int PLATE_CELL = 2_400_000;

    /**
     * ★ <b>世界身份位：本世界的地形实现是 {@link TalosField}（新路）。</b>
     *
     * <p><b>这是给测量溯源系统读世界身份用的位，不是开关。</b>
     * 它没有「另一半」——旧实现（本类自带的 Voronoi 板块地形）已按用户裁决「旧的完全删除」
     * 整支删除（§567），代码里**不存在**把它翻到 {@code false} 就能得到的世界。
     * 读它的地方全在测量侧：{@code tools/talos-probe/fingerprint_config.ps1} 的 WORLD 维度、
     * {@code rerun_acceptance.ps1} 的 WORLDPROD / PRODUCTION 判据，以及验收目录名
     * {@code <srcfp8>_<cfgfp8>_<WORLD>}。
     *
     * <p>⚠ <b>不要把它当旋钮</b>：改成 {@code false} 并不会切换到任何东西，只会让测量系统
     * 把这台机器登记成「LEGACY 世界」——而 LEGACY 世界在代码里已经不存在 ⇒ 目录名、WORLD 维度、
     * PRODUCTION 判据会**一致地说谎**（{@code PRODUCTION=YES} 却指向一个不存在的世界）。
     * 它随代码走，不是配置项。
     *
     * <p>形式是**故意**的：{@code public static boolean X = …;} —— 不能写成 {@code static final}，
     * 因为 {@code fingerprint_config.ps1:50-51} 用正则
     * {@code public\s+static\s+boolean\s+\w+\s*=} 生成 {@code cfg_classes.txt}，
     * 而 {@code P900.java:51-56} 用反射读这些 {@code public static boolean} 字段。
     * 写法一改，WORLD 维度整条消失（= 20 门验收 fail-closed 起不来）。
     */
    public static boolean WORLD_IS_TALOS = true;

    // ==================== 度量钩子（生产恒 null） ====================

    /**
     * ★★ <b>物理线验证用的外部陆海掩膜钩子</b>（默认 {@code null} ⇒ 行为逐位不变）。
     *
     * <p>为什么需要它：真正的判据是「给定边界条件，模型算得对不对」。在此之前我们是拿
     * <b>随机世界</b>的纬向平均去减<b>地球</b>的纬向平均，那个差里混了
     * 【边界条件差异】+【物理误差】两项，分不开。把地球真实的陆海掩膜喂进来，
     * 边界条件这一项就被钉死，剩下的是纯物理误差。
     *
     * <p>只给探针用，<b>不在生产路径上</b>；{@code null} 时下面的分支根本不进。
     */
    public interface LandMask { boolean isLand(int x, int z); }

    /**
     * ★★★ **已废弃并删除**（2026-10-08 接入新海陆）。
     *   新海陆（{@link NoiseContinentGrid} -> TalosLandField）是**唯一**来源，
     *   不再需要外部掩膜钩子。原字段保留为编译期常量 {@code null} 语义，
     *   以便仍在读它的探针（P597 等）不报错。
     */
    @Deprecated public static final LandMask MASK = null;

    /**
     * ★★ 地球验证层的【地形注入点】。**生产恒为 null。**
     *
     * <p>为什么需要它（审计 §400）：{@link #MASK} 只覆盖 {@code isLand}，
     * 而 {@link #elevationWithCell} 没有掩膜分支 ⇒ 装了地球掩膜的跑会得到
     * 「地球的海岸线 + 程序生成的随机地形」，而且是**自相矛盾**的：
     * {@code isLand} 说是陆地、{@code elevation} 给 -3000~-4600 m 的"海底"。
     * 实测（P596）：拉萨 386 m（真实 3650）、亚马逊 -4119 m，最大相对偏差 4219%。
     * 后果：**没有青藏高原 ⇒ 亚洲季风不可能形成**（Boos & Kuang 2010, Nature），
     * 那测出来的就不是物理误差，而是缺边界条件。
     *
     * <p><b>严格分层的设计（用户要求「别把他们和我们自己的混在一起」）</b>：
     * <ul>
     *   <li>这里**只有一个接口**，{@code src} 里**零地球知识**（没有任何文件路径、没有 ETOPO1、没有观测数据）；</li>
     *   <li>地球掩膜与高程的加载、以及全部地球观测锚，都住在 {@code tools/talos-probe/probe/EarthRef.java}
     *       这一个文件里；</li>
     *   <li>{@code null} 时下面只多一次空判断，**逐位不变**（P293 复验）；</li>
     *   <li>★ 2026-10-08：<b>本机制已整支作废并删除</b>。海陆层换成 {@link NoiseContinentGrid}
     *       （-> TalosLandField）后是唯一来源，不再需要外部掩膜/高程钩子。
     *       {@code MASK/ELEV} 现在是 {@code @Deprecated static final = null}，
     *       {@code SimClimate} 的指纹位改为折入 {@link #configStamp()}。</li>
     * </ul>
     */
    public interface ElevSource { double elevMeters(int x, int z); }

    /** ★★★ **已废弃并删除**（2026-10-08）。见 {@link #MASK}。 */
    @Deprecated public static final ElevSource ELEV = null;

    /**
     * 海平面（高程基准）。
     *
     * <p>§567 之后新地形路的海平面来自 {@code TalosField.level(seed)}（自标定，
     * 见 TalosField.java:253-271,315）；本常量是 {@link #isLand} 的判据基准
     * （{@code elevation >= 0}），并被既有探针（P505）与登记表引用。
     */
    public static final double SEA_LEVEL = 0.0;

    // ==================== 侵蚀参数常量（从已删除的 TalosField 迁来） ====================
    /**
     * ★ 高频外推额外 octave 的振幅（侵蚀用）。
     *   ★ 2026-10-08 从 `TalosField.ERO_EXTRA_AMP` **原值迁来**（= 0.25）。
     *   `TalosField` 已删除（旧地形生成器，生产零调用）。
     */
    public static final double ERO_EXTRA_AMP = 0.25;

    /**
     * ★ 最细波长 = HF_WL0 / 2^(HF_OCT-1) = 3,000,000 / 2^12 = **732.421875 m**。
     *   ★ 2026-10-08 从 `TalosField.HF_WL_MIN` **原值迁来**。
     */
    public static final double HF_WL_MIN = 3_000_000.0 / (double) (1L << 12);

    // ==================== 大陆度（连续、带符号、O(1)） ====================

    /**
     * 海岸过渡尺度（block/m）。M3 的需求：p' 必须在海岸处**连续**，
     * 否则跨岸差分会造出 25 m/s 的虚假沿岸风（设计冻结 §27.4 / §31.5）。
     * 取大气对海陆热力对比的响应尺度 L_R ≈ 800 km。
     */
    public static final int COAST_BLEND = 800_000;

    /**
     * 采样环半径（× COAST_BLEND）与权重。
     *
     * <p>⚠ **必须多环**：早先用 3 环（0.35/0.65/1.00R）时，κ(x) 在离岸方向上呈**阶梯状**，
     * 于是 dp/dx 在 240~360 km 与 480 km 处**符号相反**（P270 实测），
     * 沿岸风被平均掉、只剩 1/8。现在用 10 环 + 高斯权重 ⇒ κ(x) 单调平滑。
     */
    /**
     * 采样：**密集极坐标网格**（12 环 x 16 方向 = 192 点，加中心 1 点 ⇒ **N = 193**）。
     *
     * <p>⚠⚠ <b>审计 D65（2026-09-14 复核）：本注释原文写「环间无相位偏移」——【与代码不符】。</b>
     * {@code landFractionWithCell} 第 198 行是
     * <pre>
     *   double a = 2.0 * Math.PI * k / N_ANG + r * 0.37;   // r = 环号
     * </pre>
     * **每一环相对上一环额外转 0.37 弧度**（约 21.2 度）⇒ 环**不是**对齐的，是**错开的**。
     * 代码是对的（错开能减少环间的方向性伪影），**错的是这句话**。
     *
     * <p>⚠ 顺带确认 <b>D29 的量化</b>：`RING_W` 全为 1.0、中心点权重也是 1.0 ⇒
     * `wsum ≡ 1 + 12*16 = 193`，所以 kappa **只有 194 个离散值**、级距 **2/193 = 0.010363**。
     * P481 实测（§191）：跨一道岸 p' 的**最大单步 / 跨岸总变化 = 1.41 ~ 1.55%** ⇒ 噪声级，**不修**。
     *
     * <p>为什么必须密集：kappa 是「二值陆海掩膜」的点采样估计，本身带 ~1/sqrt(N) 的噪声；
     * 而跨岸气压梯度取的是 15 km 步长的差分 ⇒ **噪声盖过信号**（§43：3 环时 kappa(x) 呈阶梯状，
     * 梯度在 240~360 km 与 480 km 处符号相反）。密集网格把台阶间隔压到 ~70 km，差分才平均得掉。
     */
    private static final double[] RING_R = {
        0.0833, 0.1667, 0.25, 0.3333, 0.4167, 0.5, 0.5833, 0.6667, 0.75, 0.8333, 0.9167, 1.0
    };
    private static final double[] RING_W = {
        1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0
    };
    private static final int N_ANG = 16;   // **不可再降**：40 点采样会让 kappa 在离岸 50~400km 处塌到 0（§69 实测）

    /**
     * **大陆度**：连续、带符号。内陆 -> +1，深海 -> -1，直线海岸 -> 0。
     *
     * <p>做法：以固定图案（12 环 × 16 方向 + 中心）在半径 COAST_BLEND 内采样陆海掩膜，
     * 取加权陆地占比再映射到 [-1,1]。**O(1)**（固定 193 次 O(1) 查询），
     * 确定性的，不做 flood fill。
     *
     * <p>用途：让海陆的 A(phi)/psi(phi) 连续过渡 ⇒ p' 连续 ⇒ 跨岸气压梯度平滑
     * ⇒ 沿岸地转风 ⇒ 沿岸上升流层（东边界流）。见 §31.5 那条链。
     */
    public static double landScore(int x, int z, long seed) {
        return landScoreWithCell(x, z, seed, PLATE_CELL);
    }

    public static double landScoreWithCell(int x, int z, long seed, int cell) {
        // ★★★★★★ 2026-10-08：改由【大陆度】派生（连续、与 isLand 同源、且在本世界尺度下正确）
        //   ★ 原实现（半径 COAST_BLEND=800 km 的加权陆地占比）在本世界失效：
        //     特征只有 125 km、海洋无边 ⟹ 任何半径都采样到几乎全是海 ⟹ kappa 永不 > 0.5
        //   ★ 新：kappa 由高度派生（海平面 ⟹ 0.5，深海 ⟹ 0，内陆 ⟹ 1）
        return NoiseContinentGrid.landScore(x, z, (int) seed);
    }

    /** 半径 radius 内的加权陆地占比，[0,1]。 */
    public static double landFractionWithCell(int x, int z, long seed, int cell, int radius) {
        int s = isLandWithCell(x, z, seed, cell) ? 1 : 0;
        double wsum = 1.0;
        for (int r = 0; r < RING_R.length; r++) {
            int rr = (int) (RING_R[r] * radius);
            if (rr < 1) continue;
            double w = RING_W[r];
            for (int k = 0; k < N_ANG; k++) {
                double a = 2.0 * Math.PI * k / N_ANG + r * 0.37;   // 固定相位，确定性
                int sx = x + (int) Math.round(rr * Math.cos(a));
                int sz = z + (int) Math.round(rr * Math.sin(a));
                if (isLandWithCell(sx, sz, seed, cell)) s += w;
                wsum += w;
            }
        }
        return s / wsum;
    }

    // ==================== 公开查询 ====================

    /** 高程（m，相对 {@link #SEA_LEVEL}）。纯函数。 */
    public static double elevation(int x, int z, long seed) {
        return elevationWithCell(x, z, seed, PLATE_CELL);
    }

    /**
     * 标定入口：**板块格边长可调**。生产恒走 {@link #PLATE_CELL}（即 {@link #elevation}），
     * 本入口只给探针做尺度扫描 —— 依然是纯函数，没有静态可变状态。
     *
     * <p>§567 之后函数体只剩两条路：{@link ElevSource} 钩子（装了才走，生产恒 {@code null}）
     * 与 {@link TalosField}。{@code cell} 形参**已被忽略**（保留只为不改 ~200 支探针的调用点，
     * 理由见 {@link #PLATE_CELL}）。
     */
    public static double elevationWithCell(int x, int z, long seed, int cell) {
        // ★★★★★★ 2026-10-08 接入新海陆：直接委托 NoiseContinentGrid（-> TalosLandField）
        //   旧的 TalosField V8 生成器路径与 MASK/ELEV 钩子已删除（用户裁决：全接过去，更干净）
        return NoiseContinentGrid.height(x, z, (int) seed);
    }

    /**
     * ★ 2026-10-08 新增：**double 坐标**重载（供沿岸二分搜索用）。
     *   ★ 为什么需要：`CommandTalosCoast` 的二分把海岸线细化到约 4 block，
     *     若强制取整会失去精度。旧 `TalosField.isLand` 是 `(double,double,long)`。
     */
    public static boolean isLand(double x, double z, long seed) {
        return NoiseContinentGrid.isLand((int) Math.floor(x), (int) Math.floor(z), (int) seed);
    }

    /**
     * ★ 2026-10-08 新增：4 参数 `elevation` 重载（兼容旧的 `TalosField.elevation(x,z,seed,extraAmp)`）。
     *   ★ `extraAmp` 在新海陆里**无对应量**（旧 TalosField 的侵蚀外推振幅），故忽略。
     */
    public static double elevation(int x, int z, long seed, double extraAmp) {
        return elevation(x, z, seed);
    }

    /**
     * 是否陆地。
     *     *   ★★★★★★ 2026-10-08：改为直接委托（原来是 `elevation >= SEA_LEVEL`）。
     *   理由：新海陆的 `isLand` 是权威判定，而 `elevation` 经过增益/钳制后
     *   在海岸处可能与之不逐位等价 ⟹ 直接委托消除该风险。
     */
    public static boolean isLand(int x, int z, long seed) {
        return isLandWithCell(x, z, seed, PLATE_CELL);
    }

    /**
     * 标定入口：与 {@link #isLand} 相同，板块格边长可调（§567 之后该形参已被忽略）。
     *
     * <p>优先级：{@link #MASK}（装了才走）→ {@link TalosField#isLand}。
     * ⚠ 注意 {@link #isLand} 与这里**故意不对称**：{@code isLand} 走 {@code elevation}，
     * 因此**不查 MASK**。§567 明确**不在删除刀里修**这个不对称（改它 = 改行为，
     * 会让 MASK 影响 {@code isLand} 的读数）；要修就单独一刀 + 单独验收。
     */
    public static boolean isLandWithCell(int x, int z, long seed, int cell) {
        // ★★★★★★ 2026-10-08 接入新海陆：直接委托 NoiseContinentGrid（-> TalosLandField）
        return NoiseContinentGrid.isLand(x, z, (int) seed);
    }

    /**
     * **到海岸的距离**（block；陆点为负、海点为正、岸线为 0）—— **新场上的距离量**（审计 D18-(c2)）。
     *
     * <p>为什么必须有它：旧的 {@code ClimateCoords:94} 用的是
     * {@code continent = clamp01(-oro.coastDist / 40_000)}，那是**距离**型语义
     * （岸线 0、14 km 0.35、24 km 0.60）。而 **任何「陆地占比」型的量都做不到这件事** ——
     * 一个半径 R 的圆盘无论多大，在离岸 d 处的占比都是 {@code 0.5 + d/(pi*R/2)}，
     * 想让 24 km 处达到 0.60 就需要 R = 24/0.1/… 即 ~76 km，可那时**岸线处仍是 0.5**
     * ⇒ 端点对不上。（P454 实测：COAST_FINE=40 km 时 kapFine(24 km) 只有 0.038，
     * 而旧语义要 0.60 —— **量程差一个数量级**。）
     *
     * <p>也**不能**用旧场的 {@code OrographyField.coastDist}：那来自 NoiseContinentGrid，
     * 而 D16-a 已把海陆统一到本场 ⇒ 用它等于把旧场请回来。
     *
     * <p>实现：8 条射线，先按 250 m 起倍增找翻转、再二分到 ~100 m，取最小。约 160 次
     * {@link #isLandWithCell}（kappaAt 是 193 次，同量级）。
     * 搜索窗内没有任何海岸时返回 {@code ±2*maxSearch}（远处视为「极内陆 / 极深海」），不是 0 ——
     * 返回 0 会让深海与内陆都得到 continent = 0，那是错的。
     */
    /**
     * ★★★ 配置戳（供 SimClimate 的瓦片缓存失效用）。
     *   ★ 2026-10-08：海陆层已整支换成 {@link NoiseContinentGrid}（-> TalosLandField），
     *     所以戳也必须换 —— 否则旧瓦片缓存不会失效。
     */
    public static long configStamp() {
        long h = 0x9E3779B97F4A7C15L;
        h = h * 31 + NoiseContinentGrid.configStamp();
        h = h * 31 + 1L;   // 版本位：接入新海陆
        return h;
    }

    public static double coastDistanceNew(int x, int z, long seed, int cell, int maxSearch) {
        // ★★★★★★ 2026-10-08 接入新海陆：直接用新场的【有符号海岸距离】（比 8 射线二分更精确、更快）
        return NoiseContinentGrid.coastDistBlocks(x, z, (int) seed);
    }
}