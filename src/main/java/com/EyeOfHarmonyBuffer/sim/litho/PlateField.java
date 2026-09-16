package com.EyeOfHarmonyBuffer.sim.litho;

/**
 * 板块构造大陆场 —— 新世界模拟器的 L1（地形/海陆 + 海岸）。
 *
 * <h3>为什么是板块，而不是噪声</h3>
 * 旧实现（{@code NoiseContinentGrid}）是**阈值化各向同性噪声** ⇒ 各向同性斑块 ⇒
 * 没有连贯的经向海岸 ⇒ 没有海盆。实测对照（同一套已标定的求解器）：
 * <pre>
 *   规则闭合盆 197 km 宽   ⇒ 西缘 |v| 均值 77 mm/s
 *   噪声大陆 ~300 km 宽    ⇒ 西缘 |v| 均值  2.4 mm/s      ← 更宽反而弱 32 倍
 * </pre>
 * 西边界流要求一条**连贯的经向海岸**（几百公里）。板块边界天然是**直线/弧线**，
 * 于是海岸天然连贯。
 *
 * <h3>模型（最小但自洽）</h3>
 * <ol>
 *   <li><b>站点</b>：抖动网格上的 Voronoi 站点（{@link #PLATE_CELL} 一格一个），
 *       3x3 邻域取最近与次近 ⇒ Voronoi 图，边界是**直线段**；</li>
 *   <li><b>陆洋属性</b>：站点上的低频噪声 {@code contScore} 决定该板块是陆壳还是洋壳
 *       —— 低频 ⇒ 相邻若干板块同属一块大陆（内部出现碰撞带）；</li>
 *   <li><b>边界类型</b>：每个板块一个漂移矢量（哈希给出），
 *       用「相对速度在边界法向上的投影」判**汇聚 / 离散**，再结合两侧壳类型给出
 *       碰撞 / 俯冲 / 洋脊 / 裂谷 / 转换；</li>
 *   <li><b>高程</b>：壳底高程 + 边界特征的指数衰减 + 分形噪声。</li>
 *   <li><b>造山标定</b>（§70 裁决 4）：双尺度造山带（宽<b>平顶</b>高原 exp(-(edge/175km)^4)
 *       + 窄山系 exp(-(edge/30km)^2)）+ <b>随抬升饱和</b>的山带起伏
 *       RELIEF_AMP*feat/(feat+RELIEF_H0)，把高尾压成「山峰」而不是「山墙」。</li>
 *       边界处两侧取**均值**，保证跨越边界时高程连续（不会出现 4600 m 的悬崖）。</li>
 * </ol>
 *
 * <h3>纯函数纪律（设计冻结 §4）</h3>
 * 本类是**无状态纯函数 O(1)**：没有静态可变字段，同 (seed, x, z) 永远同结果。
 * 这是无限世界的前提 —— 任何依赖全局求解的量都不能放在 L1。
 */
public final class PlateField {

    private PlateField() {}

    // ==================== 常量 ====================

    /**
     * 板块格边长（block）。Voronoi 边界段的长度与此同量级。
     *
     * <p>= 600 km 是 **P243 扫描出来的**（唯一变量就是它，其余参数不动）：
     * <pre>
     *   cell  G1b(西墙>=400km 面积比)  westRun中位  westRun最长  persist中位  陆地比
     *   300   0.058                     55 km       1105 km      420 km     0.184
     *   400   0.581                     95 km       1540 km      715 km     0.251   ← westRun 中位 < Munk 宽 122km
     *   600   0.589                    125 km       2255 km      595 km     0.268   ← 选中
     *   800   0.391(样本不足)            20 km       1675 km      240 km     0.231
     *  1200   0.383(样本不足)            15 km        475 km     2775 km     0.212
     * </pre>
     * 800/1200 km 两档在 4000 km 采样框里只剩 4~11 条盆链，**统计量本身不可信**；
     * 要定这两档需要放大采样框。
     */
    /**
     * 板块格边长（block/m）。
     *
     * <p><b>裁决：600,000 -> 2,400,000</b>。理由：A2 定为「75 mm/s @100km 全深均」，
     * 而实测（P257）带内均速中位：600k -> 43.7 mm/s（差 1.72x，**不达标**），
     * 2400k -> 71.0 mm/s（差 1.06x）。
     * 且 Z_CYCLE=20M 之后，2400k 给出「每极到赤道约 4.2 个板块格」，与地球同量级。
     *
     * <p>已知代价：板块大 ⇒ 板块边界少 ⇒ 造山少。**已按裁决 §70 第 4 条补偿**：
     * 双尺度造山带（宽平顶高原 + 窄山系）+ 随抬升饱和的山带起伏（见下方注释块与 elevationFull）。
     * 实测（20,000 x 20,000 km，dx=dz=20 km，面积加权）：max 4611 -> 8069 m、
     * >2000 m 占陆地 3.33 -> 6.42 %、>4000 m 0.135 -> 1.76 %、>5000 m 0 -> 0.952 %、
     * 海岸线翻转率 1.59 %、elevationWithCell 659 -> 712 ns（1.08x）。
     */
    public static final int PLATE_CELL = 2_400_000;
    /**
     * 站点抖动幅度（**单位为格边长**：`sx = (gx + 0.5 + JITTER*(2u-1)) * cell`，
     * 所以站点最多偏离格心 +/- JITTER 格）。
     *
     * <p>⚠⚠ <b>审计 D30（2026-09-14 复核）：原注释「必须 &lt; 0.5，否则 3x3 邻域不够」是【错的】。</b>
     * 0.5 不是充分条件。看一个显式的反例（格点取整数、查询点在 (0.5, 0.5)）：
     * <pre>
     *   把四个近邻**推离**查询点：(0,0)->(-J,-J)、(1,0)->(1+J,-J)、
     *                            (0,1)->(-J,1+J)、(1,1)->(1+J,1+J)
     *   ⇒ 最近邻距离 = sqrt(2)*(0.5+J)
     *   把 3x3 之外的 (2,0) **推向**查询点：(2-J, 0)
     *   ⇒ 距离 = sqrt((1.5-J)^2 + 0.25)
     *   令两者相等： (1.5-J)^2 + 0.25 = 2*(0.5+J)^2  =>  J^2 + 5J - 2 = 0
     *   => J = (sqrt(33)-5)/2 = **0.3723**
     * </pre>
     * <b>⇒ 超过 0.372，上面那种排布下最近的站点就落在 3x3 之外了 ⇒ 会取错站点。</b>
     *
     * <p>⚠ 这是**一个**排布的推导，不是全局最小上界；保守的充分条件是 **J &lt;= 1/3**
     * （审计线索里给的就是这个数，但没有推导；0.333 &lt; 0.372 所以它确实是安全的）。
     *
     * <p><b>当前值 0.34 落在 [1/3, 0.372] 之间 ⇒ 按本推导是安全的，但余量只有 8%。</b>
     * <b>本轮【不改值】</b>（改 JITTER 会改全世界的板块几何 ⇒ 全球重标定），
     * 只把注释改成可推导的界，并把「0.34 余量薄」这件事记账。
     */
    private static final double JITTER = 0.34;
    /** 陆坡（大陆边缘）半宽：边界两侧各这么宽做混合，中间穿过海平面。 */
    private static final double MARGIN_W = 120_000.0;
    /** 造山**山系**半宽（窄尺度：喜马拉雅/阿尔卑斯型山脊）。 */
    private static final double OROGEN_W = 30_000.0;
    /** 造山**高原**半宽（宽尺度：青藏/安第斯型**平顶**高原，中间平、边缘陡）。 */
    private static final double PLATEAU_W = 175_000.0;
    /** 碰撞抬升里分给**高原**的比例，其余给山系。 */
    private static final double PLATEAU_FRAC = 0.48;
    /** 俯冲山弧里分给高原的比例（安第斯型）。 */
    private static final double ARC_PLATEAU_FRAC = 0.40;

    /** 海平面（高程基准）。 */
    public static final double SEA_LEVEL = 0.0;
    /** 陆壳底高程 / 洋壳底高程（m）。 */
    private static final double ELEV_CONT = 620.0;
    private static final double ELEV_OCEAN = -4100.0;

    private static final double COLLIDE_H = 5600.0;   // 陆陆碰撞：总抬升（高原 2688 + 山系 2912）
    private static final double ARC_H     = 2600.0;   // 俯冲：陆侧山弧
    private static final double TRENCH_D  = -2400.0;  // 俯冲：洋侧海沟
    private static final double RIDGE_H   = 1600.0;   // 洋脊
    private static final double RIFT_D    = -900.0;   // 陆内裂谷
    private static final double TRANSFORM_H = 320.0;  // 转换断层（走滑，起伏小）

    /**
     * **山带中尺度起伏**：真实山带的局部起伏随抬升量增长并饱和（平原几十米，山带 1~2 km）。
     * relief = RELIEF_AMP * feat / (feat + RELIEF_H0)，处处连续、无阈值。
     *
     * <p>这是把 5000 m 以上的面积压成「山峰」而不是「山墙」的关键机制 ——
     * 也是唯一能让 7000~9000 m 的极值自然涌现、而不把 4000 m 以上面积堆爆的做法（§70 裁决 4）。
     */
    private static final double RELIEF_AMP = 1500.0;
    private static final double RELIEF_H0  = 1500.0;
    /** 起伏的水平尺度（m）。 */
    private static final double RELIEF_W   = 30_000.0;

    /** 陆壳判定阈值（contScore 归一化到约 ±1）。 */
    private static final double CONT_THRESHOLD = 0.10;

    private static final long SITE_SALT  = 0x5EED_0001L;
    private static final long CONT_SALT  = 0x5EED_0002L;
    private static final long NOISE_SALT = 0x5EED_0003L;

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
     * <p>做法：以固定图案（3 环 × 8 方向 + 中心）在半径 COAST_BLEND 内采样陆海掩膜，
     * 取加权陆地占比再映射到 [-1,1]。**O(1)**（固定 25 次 O(1) 查询），
     * 确定性的，不做 flood fill。
     *
     * <p>用途：让海陆的 A(phi)/psi(phi) 连续过渡 ⇒ p' 连续 ⇒ 跨岸气压梯度平滑
     * ⇒ 沿岸地转风 ⇒ 沿岸上升流层（东边界流）。见 §31.5 那条链。
     */
    public static double landScore(int x, int z, long seed) {
        return landScoreWithCell(x, z, seed, PLATE_CELL);
    }

    public static double landScoreWithCell(int x, int z, long seed, int cell) {
        return 2.0 * landFractionWithCell(x, z, seed, cell, COAST_BLEND) - 1.0;
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
     */
    // ==================== C：超宽大洋限制器（**连续岛弧**） ====================

    /**
     * **海盆宽度上界**（block，半宽）。0 = 关闭。
     *
     * <p>为什么需要（设计冻结 §48）：西边界流**正比于纬线上的海盆宽**，而东边界流要求那条纬线
     * **两端都是海岸**。PLATE_CELL 一个参数没法同时控制「中位宽度」和「上界」⇒ 实测无论取哪个值，
     * 总有一边不达标（2400 km 西 89/东 13；1000 km 西 31/东 101）。
     *
     * <p>**物理上真实的机制**：地球的大洋中间本来就有洋中脊、微大陆、岛弧，
     * 它们把超宽大洋切开（太平洋最宽也只有 15,000 km 量级）。
     * 这里就按这个做：**在「距任何陆地都超过 MAX_OCEAN_HALF」的位置插入一条岛弧**。
     */
    public static int MAX_OCEAN_HALF = 7_000_000;

    /** 岛弧**脊线**的峰值高程（m）：要高于海平面才算「有东岸」。 */
    public static double OCEAN_BREAK_H = 900.0;

    /**
     * 岛弧**覆盖率**（0~1）：越大 ⇒ 沿脊的抬升越强 ⇒ 成岛越多。
     *
     * <p>⚠ **语义已变**：旧实现是「40 km 方块的命中比」，新实现只把它当作一个**单调旋钮**
     * 进入 {@link #arcRidge}（bias = 2*(FRAC-0.35)）。0.35 是标定工作点，实测成岛率见
     * {@code build/eoh_scratch_arc/REPORT.md}。
     */
    public static double OCEAN_BREAK_FRAC = 0.35;

    /** 岛弧**沿脊**起伏的水平尺度（m）：决定岛链上「岛」的间距与大小。 */
    private static final double ARC_ALONG_W = 170_000.0;
    /** 沿脊起伏的增益：把 fbm(±0.6) 映射到抬升比例 [0,1] 并两端饱和。 */
    private static final double ARC_GAIN = 1.75;

    /**
     * 环采样点上「陆地程度」的过渡带（**contScore 单位**，不是米）：
     * cs ≤ CONT_THRESHOLD 记 0（洋壳），cs ≥ CONT_THRESHOLD + ARC_CS_W 记 1（陆壳）。
     *
     * <p>⚠ **为什么不直接用环采样点的高程**（第一版就是这么写的，实测有残余撕裂）：
     * {@code elevationFull} 的 {@code base} 项在「次近站点换人」处有最高 ~1,790 m 的**跳变**
     * （造山报告 §7 记账的既有缺陷，RAW 层 >3000 m 断崖 0.0038 %、最大 7,970 m 就来自它）。
     * 判陆若用高程，则「环上那一点正好压在 Voronoi 棱上」时 w 会跳 —— 实测
     * (6181000,4621000)：环上 k=3 的高程在 1 km 内 −3165.5 → −1968.9（跳 1,197 m），
     * 于是 w 0.983 → 0.534，抬升差 **1,930 m**。任何「高程 → [0,1] 判陆」的单调映射
     * 都不可能消除它（带宽受深海平原高程范围限制，最宽 ~3,000 m ⇒ 1,197 m 至少映射成 0.4）。
     *
     * <p>改用与陆壳判定**同一个场**（{@code contScore} 的底层 fbm）在**当前点**上求值：
     * 它对位置处处 C1，没有 Voronoi 量化带来的跳变。代价是它比「最近站点」的取值
     * 多了一个 ~半个板块格（几百 km）的偏差 —— 方向是**保守**的：
     * 代理说「是陆地」而实际是海 ⇒ 少插岛弧（海盆可能略宽）；
     * 代理说「是海」而实际是陆 ⇒ 多插岛弧（无害，depth 门会把它按下去）。
     */
    private static final double ARC_CS_W = 0.08;

    /**
     * 抬升的**本地深度门**（m）：raw 高程 ≥ DEPTH_HI 时不给抬升，≤ DEPTH_LO 时给满。
     *
     * <p>它保证 {@code elevationWithCell} 在 e = 0 处**连续**（depth(−0) = 0）：
     * 否则「e ≥ 海平面就早退」会与「e &lt; 0 就加抬升」在岸线上留下最多 900 m 的跳跃。
     */
    private static final double DEPTH_HI =  -250.0;
    private static final double DEPTH_LO = -2_600.0;

    private static final long ARC_SALT = 0x5EED_0004L;

    /** 平滑阶跃：≤0 恒 0、≥1 恒 1，中间 C1。**无阈值、无跳变**。 */
    static double smoothstep01(double t) {
        if (t <= 0.0) return 0.0;
        if (t >= 1.0) return 1.0;
        return t * t * (3.0 - 2.0 * t);
    }

    /**
     * **超宽大洋权重** w ∈ [0,1]：旧 {@code inWideOcean} 的连续版。
     *
     * <p>8 个方向在 MAX_OCEAN_HALF 处各取一点，把该点海拔过一遍 landness 再取 **max**：
     * <pre>
     *   w = 1     ⇔ 八个方向全是深海      （= 旧 inWideOcean == true）
     *   w = 0     ⇔ 至少一个方向踩到陆地  （= 旧 inWideOcean == false）
     *   0 &lt; w &lt; 1 ⇔ 某个方向的采样点正落在陆坡上 ⇒ **连续过渡带**（旧实现就是在这里硬切）
     * </pre>
     * 取 max（而不是均值/乘积）是**故意的**：只有「八个方向都没有陆地」才算超宽大洋，
     * 均值会让离岸 5,000 km 的普通洋底也被抬起。
     *
     * <p>⚠ **早退是精确的**：max 一旦到 1 就再也降不下来 ⇒ 提前 return 与跑完 8 次**逐位相同**。
     * 于是它保留了原实现的短路性能（绝大多数点在头一两个方向就出结果）。
     */
    static double wideOceanWeight(int x, int z, long seed, int cell) {
        double lm = 0.0;
        for (int k = 0; k < 8; k++) {
            double a = 2.0 * Math.PI * k / 8.0 + 0.39;
            int sx = x + (int) Math.round(MAX_OCEAN_HALF * Math.cos(a));
            int sz = z + (int) Math.round(MAX_OCEAN_HALF * Math.sin(a));
            double lk = smoothstep01((contScore(sx, sz, seed, cell) - CONT_THRESHOLD) / ARC_CS_W);
            if (lk > lm) lm = lk;
            if (lm >= 1.0) return 0.0;
        }
        return 1.0 - lm;
    }

    /**
     * **岛弧沿脊的抬升比例** a ∈ [0,1]：连续值噪声 ⇒ 岛链，而不是一堵墙。
     *
     * <p>旧实现用 {@code (int)(x/40_000)} 的整数方块哈希 ⇒ 40 km 见方、边缘垂直的平板。
     * 这里换成 3 八度值噪声（处处 C1，无方块边界），幅度两端由 smoothstep 饱和，
     * 于是脊线上出现「岛 — 海峡 — 岛」的天然分节。
     */
    static double arcRidge(int x, int z, long seed) {
        double n = fbm(x - 8_131, z + 4_577, seed ^ ARC_SALT, 3, 1.0 / ARC_ALONG_W, 1.0);
        double bias = 2.0 * (OCEAN_BREAK_FRAC - 0.35);
        return smoothstep01(0.5 + bias + ARC_GAIN * n);
    }

    /**
     * **连续岛弧剖面**：{@code e -> e + (OCEAN_BREAK_H - e) * w * depth * ridge}。
     *
     * <p>三件事同时成立：
     * <ol>
     *   <li>w = depth = ridge = 1 处高程**恰为** OCEAN_BREAK_H ⇒ 选项 C 的功能（超宽大洋里
     *       一定出现高于海平面的岛弧、把海盆切开）**逐点保留**；</li>
     *   <li>w 在环采样点落到陆坡上时连续地从 1 掉到 0 ⇒ 岛弧**从洋底斜着升起**：
     *       横向坡度 ≈ (OCEAN_BREAK_H + |e|) / 过渡带宽 ≈ 5,000 m / 76 km ≈ 66 m/km ≈ 3.8°，
     *       落在真实陆坡（3~6°，造山报告 §1.4）的量级内；</li>
     *   <li>ridge 沿脊起伏 ⇒ 有岛有海峡，是**岛弧链**。</li>
     * </ol>
     *
     * <p>横剖面**故意用线性的 w**（而不是再套一层 flatTop）：w 本身就是「到弧轴的横向坐标」，
     * 而 0→1 的单调剖面里**线性是最大坡度最小的那个**（max |dp/dw| = 1；
     * smoothstep 1.5、flatTop 更陡）⇒ 直接用它做横剖面，崖最缓。
     */
    public static double elevationWithCell(int x, int z, long seed, int cell) {
        double e = elevationFull(x, z, seed, cell, CONT_THRESHOLD);
        if (e >= SEA_LEVEL || MAX_OCEAN_HALF <= 0) return e;
        double w = wideOceanWeight(x, z, seed, cell);
        if (w <= 0.0) return e;
        double depth = smoothstep01((DEPTH_HI - e) / (DEPTH_HI - DEPTH_LO));
        double ridge = arcRidge(x, z, seed);
        return e + (OCEAN_BREAK_H - e) * (w * depth * ridge);
    }

    public static double elevationWithCellRaw(int x, int z, long seed, int cell) {
        return elevationFull(x, z, seed, cell, CONT_THRESHOLD);
    }

    /**
     * 标定入口：板块格边长**与陆壳阈值都可调**。生产恒走 {@link #elevation}。
     *
     * <p>阈值同时控制「陆地占比」与「盆宽分布」，是 M1 §10.6 指定的下一个杠杆。
     * 依然是**纯函数**（所有参数传入，无静态可变状态）。
     */
    public static double elevationFull(int x, int z, long seed, int cell, double contThreshold) {
        double px = x, pz = z;
        double d1 = Double.MAX_VALUE, d2 = Double.MAX_VALUE;
        double s1x = 0, s1z = 0, s2x = 0, s2z = 0;
        long h1 = 0, h2 = 0;
        int cx = (int) Math.floor(px / cell), cz = (int) Math.floor(pz / cell);
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int gx = cx + dx, gz = cz + dz;
                long h = hash2(seed + SITE_SALT, gx, gz);
                double sx = (gx + 0.5 + JITTER * (rnd01(h) * 2 - 1)) * cell;
                double sz = (gz + 0.5 + JITTER * (rnd01(hash2(h, 7, 3)) * 2 - 1)) * cell;
                double d = Math.hypot(sx - px, sz - pz);
                if (d < d1) {
                    d2 = d1; s2x = s1x; s2z = s1z; h2 = h1;
                    d1 = d; s1x = sx; s1z = sz; h1 = h;
                } else if (d < d2) {
                    d2 = d; s2x = sx; s2z = sz; h2 = h;
                }
            }
        }

        double edge = 0.5 * (d2 - d1);           // 到板块边界的距离，两侧都为正
        double cs1 = contScore(s1x, s1z, seed, cell);
        double cs2 = contScore(s2x, s2z, seed, cell);
        boolean c1 = cs1 > contThreshold, c2 = cs2 > contThreshold;
        double b1 = c1 ? ELEV_CONT : ELEV_OCEAN;
        double b2 = c2 ? ELEV_CONT : ELEV_OCEAN;

        // 陆坡：边界处取两侧均值 ⇒ 跨越边界高程连续
        double t = edge >= MARGIN_W ? 1.0 : edge / MARGIN_W;
        double base = (b1 + b2) * 0.5 * (1.0 - t) + b1 * t;

        // 边界特征：**双尺度**（宽平顶高原 + 窄山系），并按 MARGIN_W 做**两侧混合**。
        //   平顶高原 gw = exp(-(edge/PLATEAU_W)^4)：中间平坦、边缘陡（青藏型），处处 C∞；
        //   两侧混合与 base 用的是同一个 t ⇒ 洋陆边缘的 4600 m 断层消失（原来是 ARC_H 与
        //   TRENCH_D 在边界线上直接对切）。edge >= 4*PLATEAU_W 时两项都 < 1e-108 m，等于 0。
        double feat = 0.0;
        if (edge < PLATEAU_W * 4.0) {
            double g = Math.exp(-(edge * edge) / (OROGEN_W * OROGEN_W));
            double gw = flatTop(edge / PLATEAU_W);
            double gCol = (1.0 - PLATEAU_FRAC) * g + PLATEAU_FRAC * gw;
            double gArc = (1.0 - ARC_PLATEAU_FRAC) * g + ARC_PLATEAU_FRAC * gw;
            double nx = s2x - s1x, nz = s2z - s1z;
            double nl = Math.hypot(nx, nz) + 1e-9;
            // 相对速度在边界法向上的投影：> 0 = 汇聚
            double vrel = -(driftX(h2) - driftX(h1)) * (nx / nl) - (driftZ(h2) - driftZ(h1)) * (nz / nl);
            boolean conv = vrel > 0.10;
            boolean dive = vrel < -0.10;
            double f1 = featOf(c1, c2, conv, dive, g, gCol, gArc);
            double f2 = featOf(c2, c1, conv, dive, g, gCol, gArc);
            feat = (f1 + f2) * 0.5 * (1.0 - t) + f1 * t;
        }

        double n1 = fbm(px, pz, seed ^ NOISE_SALT, 5, 1.0 / 90_000.0, 700.0);
        double n2 = fbm(px + 13_337, pz - 7_919, seed ^ (NOISE_SALT + 1), 3, 1.0 / 22_000.0, 160.0);
        double n3 = 0.0;
        if (feat > 0.0) {
            n3 = fbm(px - 5_147, pz + 2_237, seed ^ (NOISE_SALT + 2), 3, 1.0 / RELIEF_W,
                     RELIEF_AMP * (feat / (feat + RELIEF_H0)));
        }
        return base + feat + n1 + n2 + n3;
    }

    /** 平顶高原剖面 exp(-u^4)：u=0 处平坦、u≈1 处陡（青藏高原型边缘），处处 C∞。 */
    private static double flatTop(double u) { double u2 = u * u; return Math.exp(-(u2 * u2)); }

    /** 单侧边界特征：near = 本侧(最近站)的壳类型，far = 另一侧。 */
    private static double featOf(boolean near, boolean far, boolean conv, boolean dive,
                                 double g, double gCol, double gArc) {
        if (near && far) return conv ? COLLIDE_H * gCol : (dive ? RIFT_D * g : TRANSFORM_H * g);
        if (near || far) {
            if (conv) return (near ? ARC_H * gArc : TRENCH_D * g);
            if (dive) return (near ? RIFT_D * 0.6 : RIDGE_H * 0.35) * g;
            return TRANSFORM_H * g;
        }
        return (conv ? ARC_H * 0.45 * g : (dive ? RIDGE_H : TRANSFORM_H * 0.5) * g);
    }

    /** 是否陆地。 */
    public static boolean isLand(int x, int z, long seed) {
        return elevation(x, z, seed) >= SEA_LEVEL;
    }

    /** 标定入口：与 {@link #isLand} 相同，板块格边长可调。 */
    public static boolean isLandWithCell(int x, int z, long seed, int cell) {
        return elevationWithCell(x, z, seed, cell) >= SEA_LEVEL;
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
    public static double coastDistanceNew(int x, int z, long seed, int cell, int maxSearch) {
        boolean land0 = isLandWithCell(x, z, seed, cell);
        double best = Double.MAX_VALUE;
        for (int k = 0; k < 8; k++) {
            double a = Math.PI * k / 4.0;
            double dx = Math.cos(a), dz = Math.sin(a);
            double found = -1;
            for (double d = 250; d <= maxSearch; d *= 2) {
                int sx = x + (int) Math.round(dx * d), sz = z + (int) Math.round(dz * d);
                if (isLandWithCell(sx, sz, seed, cell) != land0) { found = d; break; }
            }
            if (found < 0) continue;
            double lo = found * 0.5, hi = found;
            for (int it = 0; it < 12; it++) {
                double mid = 0.5 * (lo + hi);
                int sx = x + (int) Math.round(dx * mid), sz = z + (int) Math.round(dz * mid);
                if (isLandWithCell(sx, sz, seed, cell) != land0) hi = mid; else lo = mid;
            }
            if (hi < best) best = hi;
        }
        if (best == Double.MAX_VALUE) best = 2.0 * maxSearch;
        return land0 ? -best : best;
    }

    /** 标定入口：板块格边长与陆壳阈值都可调。 */
    public static boolean isLandFull(int x, int z, long seed, int cell, double contThreshold) {
        return elevationFull(x, z, seed, cell, contThreshold) >= SEA_LEVEL;
    }

    /** 到板块边界的距离（block，两侧都为正）。诊断用。 */
    public static double edgeDistance(int x, int z, long seed) {
        double px = x, pz = z;
        double d1 = Double.MAX_VALUE, d2 = Double.MAX_VALUE;
        int cx = (int) Math.floor(px / PLATE_CELL), cz = (int) Math.floor(pz / PLATE_CELL);
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                long h = hash2(seed + SITE_SALT, cx + dx, cz + dz);
                double sx = (cx + dx + 0.5 + JITTER * (rnd01(h) * 2 - 1)) * PLATE_CELL;
                double sz = (cz + dz + 0.5 + JITTER * (rnd01(hash2(h, 7, 3)) * 2 - 1)) * PLATE_CELL;
                double d = Math.hypot(sx - px, sz - pz);
                if (d < d1) { d2 = d1; d1 = d; } else if (d < d2) { d2 = d; }
            }
        }
        return 0.5 * (d2 - d1);
    }

    // ==================== 内部 ====================

    /** 陆壳倾向：站点上的低频噪声，归一化到约 ±1。低频 ⇒ 相邻板块常同属一块大陆。 */
    private static double contScore(double sx, double sz, long seed, int cell) {
        return fbm(sx, sz, seed ^ CONT_SALT, 3, 1.0 / cell, 1.0) / 1.75;
    }

    private static double driftX(long h) { return rnd01(hash2(h, 101, 202)) * 2 - 1; }
    private static double driftZ(long h) { return rnd01(hash2(h, 303, 404)) * 2 - 1; }

    /** 2D 整数哈希 -> [0,1)。 */
    private static double rnd01(long h) {
        long v = h;
        v ^= (v >>> 33); v *= 0xFF51AFD7ED558CCDL;
        v ^= (v >>> 33); v *= 0xC4CEB9FE1A85EC53L;
        v ^= (v >>> 33);
        return (v >>> 11) * 0x1.0p-53;
    }

    private static long hash2(long seed, int a, int b) {
        long h = seed;
        h = h * 0x9E3779B97F4A7C15L + a;
        h ^= (h >>> 29); h *= 0xBF58476D1CE4E5B9L;
        h = h * 0x9E3779B97F4A7C15L + b;
        h ^= (h >>> 32); h *= 0x94D049BB133111EBL;
        h ^= (h >>> 29);
        return h;
    }

    /** 2D 值噪声，返回 [-1,1)。 */
    private static double vnoise(double x, double z, long seed) {
        int xi = (int) Math.floor(x), zi = (int) Math.floor(z);
        double tx = x - xi, tz = z - zi;
        double u = tx * tx * (3 - 2 * tx), v = tz * tz * (3 - 2 * tz);
        double a = rnd01(hash2(seed, xi, zi)) * 2 - 1;
        double b = rnd01(hash2(seed, xi + 1, zi)) * 2 - 1;
        double c = rnd01(hash2(seed, xi, zi + 1)) * 2 - 1;
        double d = rnd01(hash2(seed, xi + 1, zi + 1)) * 2 - 1;
        return (a * (1 - u) + b * u) * (1 - v) + (c * (1 - u) + d * u) * v;
    }

    /** 分形叠加。amp 是第一八度的振幅。 */
    private static double fbm(double x, double z, long seed, int oct, double baseFreq, double amp) {
        double s = 0, a = amp, f = baseFreq;
        for (int i = 0; i < oct; i++) {
            s += a * vnoise(x * f, z * f, seed + i * 7919L);
            a *= 0.5; f *= 2.0;
        }
        return s;
    }
}
