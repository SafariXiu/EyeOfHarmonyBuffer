package com.EyeOfHarmonyBuffer.sim.ocean;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.util.HashMap;

/**
 * <b>二维闭合环流（路线 B）—— 把"冷暖交汇"所需的东西补上。</b>
 *
 * <h3>为什么需要它（问题的陈述）</h3>
 * <p>{@link GyreRow} 是【逐纬度行】的一维求解器：每行只解 v(x)（经向速度），
 * <b>全系统没有任何 u（纬向速度）</b> ⇒ 环流【永不闭合】：一股西边界流从南流到北之后无处可去
 * ⇒ 冷暖两股流即使相邻也只是【并排摆着】，没有交汇、没有锋面、没有混合。
 * <p>这是【结构性缺失】而非数值缺陷。本类在真实二维海盆域上解出 {@code psi(x,z)}，
 * 由 {@code u = -dpsi/dz}、{@code v = +dpsi/dx} 同时给出两个分量 ⇒ <b>环流闭合</b>。
 *
 * <h3>★ 它【不】修量级（B-1 已实测证明，勿再期待）</h3>
 * <p>1-D 行求解器实测 = Munk 理论极限 {@code psi_max/delta_M} 的 <b>0.68~0.72</b> ⟹ 已经到位。
 * <p>{@code psi ∝ curl*L}（带状旋度下只积一次，实测 L^1.03）⟹ 量级由【域宽 L 与 curl（纬度）】
 * 决定，与"是否二维"无关。本类给的是【闭合】。
 * <p>（javadoc 历史读数「闭合盆 153 mm/s」经 B-1 实测<b>不可复现</b>：需 L≈26 万 km ⟹ 不可当依据。）
 *
 * <h3>方程与离散（B-2/B-2b 已验证）</h3>
 * <pre>
 *   d(zeta)/dt = -beta*v + curl(tau)/(rho*H) + A_H*laplacian(zeta),   zeta = laplacian(psi)
 *   v = +d(psi)/dx,   u = -d(psi)/dz         墙：自由滑移 psi = zeta = 0
 * </pre>
 * <p>其【线性稳态】就是 Munk 问题（{@code curl(zeta)=curl(curl(psi))} 是 4 阶算子）。
 * <p><b>zeta 用隐式扩散</b>（{@code (1 - dt*A_H*lap) zeta^{n+1} = zeta^n + dt*(-beta*v + F)}），
 * 这样 dt 不再被 {@code h^2/(4*A_H)} 卡住，而由 β 波 CFL 决定
 * （{@link BasinFinder#safeDt}）。实测：收敛步数 24,000 -> <b>250</b>、耗时 300 s -> <b>2.4 s</b>，
 * 稳态与之<b>逐位吻合</b>（psi_max 0.1753 vs 0.1754）。
 *
 * <h3>正确性自检（可复现）</h3>
 * <p>示例域（63x60 km、lat 30、curl=-4.9e-7 Pa/m）的 Sverdrup 估计
 * {@code psi = curl*L/(rho*H*beta) = 0.238 m^2/s}，本类稳态给 <b>0.1753</b> ⟹ 吻合到 30% 以内
 * （差值来自二维几何与边界）✓
 *
 * <h3>开关与指纹</h3>
 * <p>{@link #ENABLED} 默认 <b>false</b> ⟹ 本类不参与任何计算，全链与开启前<b>逐位一致</b> ✓
 * <p>开启后 {@link #configStamp()} 的值会变 ⇒ 必须重跑完整验收。
 */
public final class BasinGyre2D {

    private BasinGyre2D() {}

    /**
     * 总开关。默认 <b>false</b>（不改变任何现有输出）。
     *
     * <p>★ 纪律：接进消费方（异常链）是【独立的一步】，本开关只控制"是否算这个场"。
     */
    public static volatile boolean ENABLED = false;   // ★ 2026-10-14 最终：保持关闭 ✓（域数 ~10000 是硬门槛）
    //   为什么关回去（实测结论，见工作目录 §十四）：
    //     开关态下每个域要算 0.26 s；而探针套件会触及成百上千个域
    //     ⟹ 完整验收跑到【小时级】都完不成 ✗ ⟹ 【不能进生产】✓
    //   开关保留的价值：① 可随时做定向实验 ② 打开时 configStamp 变化 ⟹ 缓存自动失效 ✓

    /**
     * <b>是否把【距平链】也换成二维平流-松弛</b>（B-4c）。默认 <b>false</b>。
     *
     * <p>关着时：距平仍走 {@link SeaSurfaceTemp#anomaly} 的一维局地平衡
     * {@code v*dTdz = -lambda*T'}，全链逐位不变 ✓。
     * <p>开着时：在本域上额外解一次
     * <pre>
     *   u*dT'/dx + v*(dT_zm/dz) = -lambda*T'        （迎风差分，二维）
     * </pre>
     * 再按点取用。物理依据见 B-3 的实测：加了纬向平流后<b>距平场出现锋面</b>
     * （最大梯度 = 背景梯度的 50 倍），而一维版没有任何纬向结构。
     *
     * <p>⚠ 打开会改 SST' ⟹ 必须重跑完整验收（{@link #configStamp()} 已含它 ✓）。
     */
    public static volatile boolean T2D_ENABLED = false;  // ★ 同上 ✓
    /** 二维距平场的伪时间步数。 */
    /**
     * 二维距平场的伪时间步数。
     *
     * <p>⚠ 2026-10-14（B-2d 实测）：20000 步 ⟹ **2.69 s/域** ✗ —— 探针/生产会触及很多域
     * ⟹ 总时间爆炸（打开开关后验收直接卡住 ✗）。降到 2000 步 + 抽稀 8 倍 ⟹ 约 16 倍加速 ✓
     */
    public static int STEPS_T = 2000;   // 旧的伪时间步数（保留只为对照；已不再使用 ✓）

    /**
     * 稳态迎风 Gauss-Seidel 的扫描次数（B-2f）。
     *
     * <p>稳态方程 {@code u*dT'/dx + v*dT'/dz + lambda*T' = v*(-dT_zm/dz)} 是一阶线性 PDE，
     * 按流向做迎风 Gauss-Seidel ⟹ 信息沿特征线单向下传 ⟹ 几次扫描即收敛 ✓
     * <p>实测：20 次扫描的运算量比 2000 步伪时间少 ~500 倍 ✓✓ —— 这是能否接入的门槛 ✓
     */
    public static int SWEEPS_T = 20;

    /**
     * <b>求解网格</b>（世界单位）。500 m ⟹ Munk 层 delta_M = 8.4 km 有 17 格 ✓。
     *
     * <p>⚠ 必须与 {@link BasinFinder#SAMPLE} <b>分开</b>：后者是【域构造】的粗网格（20 km ✓），
     * 用它当求解网格会完全分辨不出边界层 ✗；反之把域构造的网格缩到 500 m 会把搜索窗毁掉 ✗
     * （2026-10-14 实测：域被截断成 81 km ✗）。
     */
    public static int CELL = 1000;

    /**
     * 伪时间步数。<b>2026-10-14（B-2c）1500 -> 400</b>：实测 250 步即到平台 ✓，取 400 留余量 ✓。
     *
     * <p>两个加速旋钮的实测依据（探针 B2GRID，同一域只改网格）：
     * <pre>
     *   网格m | |v|max(mm/s) | 耗时s | 相对 500m 偏差
     *     500 |   0.05904    |  3.2  |  +0.0%
     *    1000 |   0.06055    |  0.8  |  +2.6%   &lt;- 采用
     *    2000 |   0.05117    |  0.2  | -13.3%
     *    4000 |   0.04459    |  0.0  | -24.5%
     * </pre>
     * <p>⟹ 1 km 网格几乎与 500 m 等价（+2.6%）而快 4 倍 ✓；再降到 2 km 就掉 13% ✗
     * <p>⚠ 大域（400~660 km）在 500 m + 1500 步下【算不动】（1.1M 格 × 4e10 次运算 ✗）；
     * 这两项合起来给约 15 倍加速 ✓ —— 是"域修对之后还能不能跑"的关键 ✓
     */
    public static int STEPS = 400;
    /** 每步的 SOR 扫描次数（隐式 zeta / 泊松）。 */
    public static int SOR_ZETA = 15, SOR_POIS = 10;

    /**
     * 域级缓存：<b>一个域的解被域内所有查询点共享</b> ✓（{@link BasinFinder} 的 javadoc 也是这么建议的）。
     *
     * <p>⚠ 键必须是【域】，不是【查询点】✗ —— 初版按 (x,z) 做键，
     * 结果"换一个点再查"又重解了一遍（实测 2.67 s ✗），缓存形同虚设 ✓。
     */
    /** 诊断：最近一次 solve 的关键事实（探针可读；不参与计算 ✓）。 */
    public static volatile String LAST_DIAG = "(未解过)";

    /** 逐行一维解的【行数】（实测 B2SEP 用 41 行：0.02 s/域 ✓，且 |u|/|v| = 2.09 ✓）。 */
    public static int ROWS = 41;

    /** 复用的 RHS 缓冲（避免每步分配；不影响结果 ✓）。 */
    private static double[] RHS = new double[0];

    private static final HashMap<Long, double[]> DOMAINS = new HashMap<>();
    private static long cachedSeed = Long.MIN_VALUE;
    /**
     * 缓存当时的【求解模式签名】。
     *
     * <p>⚠ 2026-10-14（B-4c 实测抓到的生产级缺陷）：域是【按需求解】并缓存的 ✓，
     * 若第一次求解发生在 {@link #T2D_ENABLED}=false 时，缓存里就<b>没有</b>距平场 ✗，
     * 之后把开关打开也读不到（{@link #tprimeAt} 恒返回 NaN ✗）。
     * <p>⟹ 任何会改变"解里含什么"的旋钮一变，就必须<b>作废整个域缓存</b> ✓✓。
     */
    private static long cachedMode = Long.MIN_VALUE;

    private static long modeSig() {
        long h = 1125899906842597L;
        h = h * 31 + (T2D_ENABLED ? 1 : 0);
        h = h * 31 + STEPS; h = h * 31 + STEPS_T;
        h = h * 31 + SOR_ZETA; h = h * 31 + SOR_POIS;
        return h;
    }

    private static long domainKey(int minX, int minZ) { return ((long) minX << 32) ^ (minZ & 0xFFFFFFFFL); }

    /** 域索引：20 km 粗格 -> 该格所在域（O(1) 查找 ✓）。 */
    private static final HashMap<Long, double[]> INDEX = new HashMap<>();

    /** canonical 的记忆化表（量化格 -> 域）；换种子时清空 ✓。 */
    private static final HashMap<Long, BasinFinder.Basin> CANON = new HashMap<>();

    /** 该域的解：{psi[], nx, nz, minX, minZ, h} —— 用 double[] 装以便统一缓存。 */
    /**
     * <b>规范化域的选择</b>（B-4b）。
     *
     * <p>⚠ 问题：{@link BasinFinder#find} 的结果<b>依赖扫描起点</b>（它与 {@code GyreRow.solve} 的
     * 端点扫描起点不同 ⟹ 同一个海岸会检出不同的边界，见工作目录 N3 的记账）。
     * 若直接用【查询点】调 find，则同一个海盆会被解出<b>不同的域</b> ✗
     * （实测表现：某域中心点的 |u| = 0.041 竟大于该域内的 max 0.0199 ✗）。
     *
     * <p>★ 修法：把 find 迭代到<b>不动点</b> —— 用域自己的中心再 find，直到域不再变 ✓。
     * 这样【不论从哪个查询点进来】，只要在同一海盆内，都会收敛到同一个域 ✓✓。
     */
    private static BasinFinder.Basin canonical(int x, int z, long seed, int cell) {
        // ★★★★★ 2026-10-14（B-2h）：**记忆化** ✗->✓
        //   实测：开关态下 P477 跑 9 分钟都没完 ✗ —— 瓶颈【不在求解器】（0.052 s/域 ✓），
        //   而在 ¤canonical¤：它每次要建 BasinFinder 的 161x161 = **25,921 格**陆地掩膜 ✗，
        //   最多迭代 6 次 ✗，而 P477 要扫 9 纬度 x 2,401 个 x 采样 ✗✗ ⟹ 掩膜被反复重建 ✓
        //   canonical 是【确定性】的 ✓（同 (seed, x, z) 恒同结果 ✓）⟹ 按【采样格量化】做记忆化 ✓
        final int Q = Math.max(BasinFinder.SAMPLE, 20_000);          // 量化到 20 km 的格 ✓
        long qk = ((long) (x / Q) << 32) ^ ((z / Q) & 0xFFFFFFFFL) ^ (seed * 31);
        BasinFinder.Basin hit = CANON.get(qk);
        if (hit != null) return hit;
        BasinFinder.Basin b = BasinFinder.find(x, z, seed, cell);
        for (int it = 0; it < 3 && b != null && b.valid; it++) {
            // ⚠⚠ 2026-10-14（B-4e 后续实测抓到）：代表点【必须是海】✗
            //   域 = "各行海段的并集 + 一圈墙环" ⟹ 它的矩形 bbox 中心【很可能是陆地】✗
            //   （实测：lat +60/+70 的域给 660x280 / 600x460 km，但 canonical 用中心再 find
            //     拿到 invalid ⟹ solve 返回 null ⟹ uvAt 与 tprimeAt 全是 null/NaN ✗）
            //   修法：在域的中纬行上，从中心向两侧找一个【确定是海】的点 ✓（确定性 ✓）
            // ★ 2026-10-14（B-4e 续）：改为【逐行扫描，取最长海段的中点】✓
            //   初版只在"中纬行"上从中心向两侧找 ✓ —— 若那一行几乎全是陆就会失败 ✗
            //   （实测 lat +60 的域仍返回 null ✗）。逐行找最长的海段最稳 ✓ 且是确定性的 ✓
            int cx = -1, cz = -1, bestLen = 0;
            int dzStep = Math.max(CELL, 2_000);
            for (int zz = b.minZ; zz <= b.maxZ; zz += dzStep) {
                int runStart = -1;
                for (int xx = b.minX; xx <= b.maxX + dzStep; xx += dzStep) {
                    boolean land = (xx > b.maxX) || PlateField.isLandWithCell(xx, zz, seed, cell);
                    if (!land) { if (runStart < 0) runStart = xx; }
                    else {
                        if (runStart >= 0) {
                            int len = xx - runStart;
                            if (len > bestLen) { bestLen = len; cx = runStart + len / 2; cz = zz; }
                            runStart = -1;
                        }
                    }
                }
            }
            if (cx < 0) break;                            // 域里找不到海（不该发生）
            BasinFinder.Basin b2 = BasinFinder.find(cx, cz, seed, cell);
            if (b2 == null || !b2.valid) break;
            if (b2.minX == b.minX && b2.maxX == b.maxX && b2.minZ == b.minZ && b2.maxZ == b.maxZ) { CANON.put(qk, b2); return b2; }
            b = b2;
        }
        if (b != null && b.valid) CANON.put(qk, b);           // ★ 记忆化 ✓（确定性 ✓）
        return b;
    }

    /**
     * ★★★★★ 2026-10-14（B-2d，候选 ③ 评估通过后落地）：**用"逐行一维解 + 行间差分"取代二维求解**。
     *
     * <h3>为什么可以（解析依据）</h3>
     * <pre>
     *   稳态：A_H*lap^2(psi) - beta*dpsi/dx = -curl/(rho*H)
     *   展开：A_H*(d4/dx4 + 2*d4/dx2dz2 + d4/dz4) - beta*d/dx = -F
     *   ★ 唯一耦合 x 与 z 的是混合项 2*d4/dx2dz2；
     *     WBC 层厚 ~8 km（x 尺度）vs 域的经向尺度 180~460 km（z 尺度）
     *     ⟹ d/dz << d/dx（20~50 倍）⟹ 混合项可忽略 ⟹ 算符【近似可分离】✓
     *   ⟹ psi(x,z) ≈ 每行的一维 Munk 解（正是 GyreRow 已经在做的、且已验证的 ✓）
     *   ⟹ u = -dpsi/dz 只要【相邻纬度行的一维 psi 做差分】即可 ✓✓
     * </pre>
     *
     * <h3>实测（探针 B2SEP，域 400x160 km、41 行）</h3>
     * <pre>
     *   逐行一维解：耗时 0.02 s（每行 0.5 ms）⟹ 每域毫秒级 ✓✓
     *   |v|max = 24.28 mm/s，|u|max = 50.84 mm/s ⟹ |u|/|v| = 2.09 ✓（闭合真实存在 ✓）
     *   对照：二维伪时间步进求解器（B-2/B-2b/B-2c）要数分钟/域，且严重欠收敛（psi 只有一维的 1/10 ✗）
     *   ⟹ 本路线快约 10,000 倍 ✓✓✓
     * </pre>
     *
     * <h3>前提</h3>
     * <p>相邻行必须在<b>同一个域</b>上求解（否则行间差分毫无意义 ✗）—— 由 {@link BasinFinder} 提供 ✓
     * <p>每行用 {@link GyreRow#solveSpan} 在域的 x 范围上解（与生产同源的、已验证的一维求解器 ✓）
     */
    private static double[] solveRows(int x, int z, long seed) {
        int cell = PlateField.PLATE_CELL;
        BasinFinder.Basin b = canonical(x, z, seed, cell);
        if (b == null || !b.valid) return null;
        final double h = OceanField.ROW_H;                       // 一维求解器的格距（125 m ✓ 已验证）
        final int nx = (int) ((b.maxX - b.minX) / h) + 1;
        int nRows = ROWS;
        if (nx < 16 || nRows < 3) return null;
        if ((long) nx * nRows > 8_000_000L) return null;         // 上限护栏（≈ 8M doubles）
        double[][] psi = new double[nRows][];
        double dzKm = b.heightKm() / (nRows - 1);
        long t0 = System.nanoTime();
        int nGood = 0;
        for (int r = 0; r < nRows; r++) {
            int zz = b.minZ + (int) (r * dzKm * 1000);
            GyreRow.Params p = new GyreRow.Params();
            p.h = h; p.rhoH = CoastalLayer.RHO * OceanField.H_TOTAL;
            p.aH = OceanField.A_H; p.zCycle = WorldContract.Z_CYCLE;
            p = p.withBeta(p.betaAt(zz));
            // ★★★★★ 2026-10-14（B-2d 实测抓到）：**每行必须用它【自己的海段】求解** ✗
            //   初版直接用域的 bbox [minX, maxX] ✗ —— 而 bbox 里【包含陆地】✗
            //   ⟹ 等于在"跨陆的假海盆"上解 Munk ⟹ psi_max 暴涨 30~100 倍 ✗
            //     （实测：lat −60 一维 15.18 vs 逐行 485.98；lat −70 一维 59.41 vs 5789.71 ✗✗）
            //   修法：在该行找【最长的连续海段】，只用它求解 ✓，ψ 只填在该段内 ✓
            int seaA = -1, seaB = -1, bestLen = 0;
            int runA = -1;
            for (int i = 0; i <= nx; i++) {
                boolean land = (i == nx) || PlateField.isLandWithCell(b.minX + (int) (i * h), zz, seed, cell);
                if (!land) { if (runA < 0) runA = i; }
                else if (runA >= 0) {
                    if (i - runA > bestLen) { bestLen = i - runA; seaA = runA; seaB = i - 1; }
                    runA = -1;
                }
            }
            if (seaA < 0 || seaB <= seaA) continue;              // 该行没有海
            int xa = b.minX + (int) (seaA * h), xb = b.minX + (int) (seaB * h);
            int nSeg = seaB - seaA + 1;
            double curl = OceanField.curlAt(xa, zz, (int) seed);
            double[] c = new double[nSeg];
            for (int i = 0; i < nSeg; i++) c[i] = curl;
            GyreRow.Row rr = GyreRow.solveSpan(xa, xb, c, p);
            if (rr != null && rr.valid && rr.psi != null) {
                psi[r] = new double[nx];
                // ψ 只填在该海段内 ✓（段外保持 0 —— 那是陆地/域外，语义正确 ✓）
                for (int i = 0; i < Math.min(nSeg, rr.n); i++) psi[r][seaA + i] = rr.psi[i];
                nGood++;
            }
        }
        if (nGood < 3) return null;
        // ★ 输出布局（元数据最前 ✓）：[nx, nRows, minX, minZ, dz] + psi 场（行优先）
        //   注意：这里 nx 是"列数"、nRows 是"行数"，与旧的二维布局语义一致 ✓
        final int N = nx * nRows;
        double[] out = new double[N + 5];
        out[0] = nx; out[1] = nRows; out[2] = b.minX; out[3] = b.minZ; out[4] = dzKm * 1000;
        for (int r = 0; r < nRows; r++) if (psi[r] != null)
            System.arraycopy(psi[r], 0, out, 5 + r * nx, nx);
        // ---- ★ B-4c'：二维距平场（冷暖交汇）—— 在逐行路线给出的 (u,v) 上解稳态平流-松弛 ----
        //   方程： u*dT'/dx + v*(dT_zm/dz) = -lambda*T'
        //   做法：伪时间步进（只对【标量】T'，很便宜 ✓）
        //   ⚠ 两条本会话踩过的教训都必须遵守：
        //     ① CFL 要用【实际】速度（v_gyre*SURF_FACTOR + **急流** ✓），否则会发散 ✗
        //     2 T' 用【自己的较粗网格】（x 方向抽稀 TSTEP 倍 ✓）以控制成本 ✓
        if (T2D_ENABLED) {
            final int TSTEP = 8;                                  // x 方向抽稀（8*125 m = 1 km ✓；4 太贵 ✗）
            int tx = (nx + TSTEP - 1) / TSTEP;
            double hx = h * TSTEP;
            double[] T = new double[tx * nRows];
            double[] T2 = new double[tx * nRows];
            double vmaxEff = 0;
            for (int r = 1; r < nRows - 1; r++) {
                if (psi[r] == null) continue;
                int zz = b.minZ + (int) (r * dzKm * 1000);
                double fj = WorldContract.coriolis(WorldContract.latOf(zz));
                double[] tg = CoastalLayer.coastTangent(b.maxX, zz, seed, cell);
                double tauS = 0;
                for (double th : OceanField.PH4) {
                    double[] ts = com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere.windStress(
                        b.maxX - 50_000, zz, seed, cell, th, OceanField.GRAD);
                    tauS += (ts[0] * tg[0] + ts[1] * tg[1]) / OceanField.PH4.length;
                }
                double vJet = CoastalLayer.jetPeak(CoastalLayer.hcLocal(tauS, fj), fj);
                for (int i = 1; i < nx - 1; i++) {
                    double v = (psi[r][i + 1] - psi[r][i - 1]) / (2 * h) * SeaSurfaceTemp.SURF_FACTOR;
                    vmaxEff = Math.max(vmaxEff, Math.abs(v));
                }
                vmaxEff = Math.max(vmaxEff, Math.abs(vJet));
            }
            double dzM = dzKm * 1000;
            double dtT = 0.2 * Math.min(Math.min(hx, dzM) / Math.max(1e-12, vmaxEff), 1.0 / SeaSurfaceTemp.LAMBDA);
            if (!(dtT > 0) || Double.isInfinite(dtT)) dtT = 0.2 / SeaSurfaceTemp.LAMBDA;
            // ★★★★★ 2026-10-14（B-2f，性能门槛）：**换成【稳态迎风 Gauss-Seidel】** ✓✓
            //   为什么可行：稳态方程是一阶线性 PDE
            //       u*dT'/dx + v*dT'/dz + lambda*T' = v*(-dT_zm/dz)
            //     按【流向】用迎风差分做 Gauss-Seidel ⟹ 信息沿特征线单向下传 ⟹ 几次扫描即收敛 ✓
            //   相比 2000 步伪时间：运算量少 ~500 倍 ✓✓（这正是"能不能接入"的那个门槛 ✓）
            //   ⚠ 迎风系数必须非负；λ 项保证对角占优 ⟹ 扫描稳定 ✓（不会像显式格式那样发散 ✗）
            int sweepsT = SWEEPS_T;
            for (int s = 0; s < sweepsT; s++) {
                boolean fwd = (s % 2 == 0);                      // 交替方向 ⟹ 两个流向都能快速传播 ✓
                for (int rr = 0; rr < nRows; rr++) {
                    int r = fwd ? rr : (nRows - 1 - rr);
                    if (r < 1 || r >= nRows - 1 || psi[r] == null) continue;
                    if (psi[r - 1] == null || psi[r + 1] == null) continue;
                    int zz = b.minZ + (int) (r * dzKm * 1000);
                    double dTdz = (com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere.zonalMeanSeaLevelK(WorldContract.latOf(zz + 20_000))
                                 - com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere.zonalMeanSeaLevelK(WorldContract.latOf(zz - 20_000))) / 40_000.0;
                    for (int tt = 0; tt < tx; tt++) {
                        int ti = fwd ? tt : (tx - 1 - tt);
                        if (ti < 1 || ti >= tx - 1) continue;
                        int i = ti * TSTEP;
                        if (i + 1 >= nx || i - 1 < 0) continue;
                        int kT = r * tx + ti, kP = 5 + r * nx + i;
                        double v = (out[kP + 1] - out[kP - 1]) / (2 * h) * SeaSurfaceTemp.SURF_FACTOR;
                        double u = -(psi[r + 1][i] - psi[r - 1][i]) / (2 * dzM) * SeaSurfaceTemp.SURF_FACTOR;
                        double ax = Math.abs(u) / hx, az = Math.abs(v) / dzM;      // 迎风系数（≥0 ✓）
                        double nxT = u > 0 ? T[kT - 1] : T[kT + 1];                // x 方向迎风邻居 ✓
                        double nzT = v > 0 ? T[kT - tx] : T[kT + tx];              // z 方向迎风邻居 ✓
                        double rhs = ax * nxT + az * nzT + v * (-dTdz);
                        T[kT] = rhs / (ax + az + SeaSurfaceTemp.LAMBDA);
                    }
                }
            }
            T2 = null;
            double tmin = 0, tmax = 0; int nBad = 0;
            for (int k = 0; k < T.length; k++) {
                if (Double.isNaN(T[k])) nBad++;
                if (T[k] < tmin) tmin = T[k];
                if (T[k] > tmax) tmax = T[k];
            }
            double[] out2 = new double[out.length + T.length + 1];
            System.arraycopy(out, 0, out2, 0, out.length);
            System.arraycopy(T, 0, out2, out.length, T.length);
            out2[out.length + T.length] = tx;                     // 末尾存 T' 网格的 tx ✓
            LAST_DIAG += String.format("；T' 场 %dx%d（抽稀 %d 倍），dtT=%.1f s，范围 %+.4f~%+.4f K，NaN %d",
                tx, nRows, TSTEP, dtT, tmin, tmax, nBad);
            return out2;
        }
        LAST_DIAG = String.format("逐行一维解：%d 行（%d 有效）x %d 格，dz=%.1f km，耗时 %.2f s，psi_max=%.4f m^2/s",
            nRows, nGood, nx, dzKm, (System.nanoTime() - t0) / 1e9, maxAbs(out, 5, N));
        return out;
    }

    private static double maxAbs(double[] a, int from, int n) {
        double m = 0;
        for (int i = 0; i < n; i++) if (Math.abs(a[from + i]) > m) m = Math.abs(a[from + i]);
        return m;
    }

    private static double[] solveLegacy2D(int x, int z, long seed) {
        int cell = PlateField.PLATE_CELL;
        BasinFinder.Basin b = canonical(x, z, seed, cell);
        if (b == null || !b.valid) return null;
        final double h = CELL;
        final int nx = (int) ((b.maxX - b.minX) / h) + 1;
        final int nz = (int) ((b.maxZ - b.minZ) / h) + 1;
        if (nx < 5 || nz < 5 || (long) nx * nz > 4_000_000L) return null;
        boolean[] sea = new boolean[nx * nz];
        for (int j = 0; j < nz; j++) for (int i = 0; i < nx; i++)
            sea[j * nx + i] = !PlateField.isLandWithCell(b.minX + (int) (i * h), b.minZ + (int) (j * h), seed, cell);
        double[] F = new double[nz], betaRow = new double[nz];
        for (int j = 0; j < nz; j++) {
            int zz = b.minZ + (int) (j * h);
            F[j] = OceanField.curlAt(b.minX, zz, (int) seed) / (CoastalLayer.RHO * OceanField.H_TOTAL);
            betaRow[j] = WorldContract.betaForLatitude(WorldContract.latOf(zz), WorldContract.Z_CYCLE);
        }
        double A_H = OceanField.A_H;
        double span = Math.max(b.maxX - b.minX, b.maxZ - b.minZ);
        double betaAvg = 0; for (double v : betaRow) betaAvg += v; betaAvg /= nz;
        double dt = Math.min(BasinFinder.safeDt(h, betaAvg, span), 0.5 / Math.max(1e-12, betaAvg * span));
        double[] psi = new double[nx * nz], zeta = new double[nx * nz], vv = new double[nx * nz];
        double a = dt * A_H / (h * h), diag = 1 + 4 * a;
        for (int s = 0; s < STEPS; s++) {
            for (int j = 1; j < nz - 1; j++) for (int i = 1; i < nx - 1; i++) {
                int k = j * nx + i; vv[k] = sea[k] ? (psi[k + 1] - psi[k - 1]) / (2 * h) : 0;
            }
            // ★★★★★ 2026-10-14（B-4g 实测抓到，**生产级 bug**）：RHS 必须【先冻结】再 SOR ✗
            //   隐式方程：(1 - dt*A_H*lap) zeta^{n+1} = RHS，   RHS = zeta^n + dt*(-beta*v^n + F)
            //   初版把 RHS 写在 SOR 循环【里面】✗ ⟹ 每次扫描都拿【已经更新过的 zeta】重算源项
            //   ⟹ 源项被乘了扫描次数倍 ⟹ 解随扫描次数线性膨胀 ✗
            //   实测症状（合成 400 km 矩形盆，1000 m 网格）：
            //     steps=300 -> psi_max=1.45（−90%，像"没收敛"）；steps=3000 -> 22.77（+55% 越过一维 ✗）；
            //     steps=12000 -> 55.16（+275%，仍在涨 ✗）⟹ 不是收敛不足，是【缓慢发散】✓✓
            if (RHS.length < nx * nz) RHS = new double[nx * nz];   // ⚠ N 在下面才声明，这里用表达式 ✓
            for (int j = 1; j < nz - 1; j++) for (int i = 1; i < nx - 1; i++) {
                int k = j * nx + i;
                RHS[k] = sea[k] ? (zeta[k] + dt * (-betaRow[j] * vv[k] + F[j])) : 0;
            }
            for (int it = 0; it < SOR_ZETA; it++)
                for (int j = 1; j < nz - 1; j++) for (int i = 1; i < nx - 1; i++) {
                    int k = j * nx + i; if (!sea[k]) { zeta[k] = 0; continue; }
                    double g = (RHS[k] + a * (zeta[k + 1] + zeta[k - 1] + zeta[k + nx] + zeta[k - nx])) / diag;
                    zeta[k] += 1.6 * (g - zeta[k]);
                }
            for (int it = 0; it < SOR_POIS; it++)
                for (int j = 1; j < nz - 1; j++) for (int i = 1; i < nx - 1; i++) {
                    int k = j * nx + i; if (!sea[k]) continue;
                    double g = (psi[k + 1] + psi[k - 1] + psi[k + nx] + psi[k - nx] - h * h * zeta[k]) * 0.25;
                    psi[k] += 1.7 * (g - psi[k]);
                }
        }
        // ★ 布局【元数据放最前】（固定偏移）✓ —— 初版把 meta 放在尾部，加了距平场之后
        //   ¤f.length-4¤ 就指向 T 的尾巴了 ✗ ⟹ 元数据读错 ⟹ 访问器全返回 NaN ✗（实测抓到 ✓）
        //   现在： [0]=nx [1]=nz [2]=minX [3]=minZ [4..4+n)=psi [4+n..4+2n)=T'（若算）
        final int N = nx * nz;
        double[] out = new double[N + 4];
        out[0] = nx; out[1] = nz; out[2] = b.minX; out[3] = b.minZ;
        System.arraycopy(psi, 0, out, 4, N);
        if (!T2D_ENABLED) {
            double pMax = 0;
            for (int k = 0; k < N; k++) if (Math.abs(out[4 + k]) > pMax) pMax = Math.abs(out[4 + k]);
            LAST_DIAG = String.format("T2D 关闭：网格 %dx%d，psi_max=%.4f m^2/s（无距平段 ✓）", nx, nz, pMax);
        }
        // ---- B-4c：二维距平场（只在开关打开时算；关着时 out 里没有它 ⟹ 行为逐位不变 ✓）----
        if (T2D_ENABLED) {
            double[] uu = new double[nx * nz], vv2 = new double[nx * nz];
            double vmax = 0, umax = 0;
            for (int j = 1; j < nz - 1; j++) for (int i = 1; i < nx - 1; i++) {
                int k = j * nx + i; if (!sea[k]) continue;
                vv2[k] = (psi[k + 1] - psi[k - 1]) / (2 * h);
                uu[k] = -(psi[k + nx] - psi[k - nx]) / (2 * h);
                if (Math.abs(vv2[k]) > vmax) vmax = Math.abs(vv2[k]);
                if (Math.abs(uu[k]) > umax) umax = Math.abs(uu[k]);
            }
            double[] dTdz = new double[nz];
            for (int j = 0; j < nz; j++) {
                int zz = b.minZ + (int) (j * h);
                dTdz[j] = (com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere.zonalMeanSeaLevelK(WorldContract.latOf(zz + 20_000))
                         - com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere.zonalMeanSeaLevelK(WorldContract.latOf(zz - 20_000))) / 40_000.0;
            }
            // ★★ 2026-10-14（B-4d）：把【沿岸急流项】带进二维场 ✓
            //   否则打开开关等于【丢掉急流那一份】✗（一维口径里它是 K 级距平的来源 ✓）
            //   这里逐字复刻 OceanField.solveRow 的算法（同一批公开 API ✓）：
            //     tauS = 沿【海岸切向】投影的风应力（4 相位平均 = 年平 ✓）
            //     hc = CoastalLayer.hcLocal(tauS, f);  vJet = CoastalLayer.jetPeak(hc, f)
            //     v += vJet * exp(-(eastX - x)/R_d)   当 0 < d < JET_RANGE_RD*R_d
            //   与一维的差别只有一个：东边缘取【本域该行最东的海格】✓（一维用的是 g.eastX ✓）
            double[] eastX = new double[nz], rdArr = new double[nz], vJetArr = new double[nz];
            for (int j = 0; j < nz; j++) {
                int ex = -1;
                for (int i = nx - 1; i >= 0; i--) if (sea[j * nx + i]) { ex = b.minX + (int) (i * h); break; }
                eastX[j] = ex;
                int zz = b.minZ + (int) (j * h);
                if (ex < 0) { rdArr[j] = 0; vJetArr[j] = 0; continue; }
                double fj = WorldContract.coriolis(WorldContract.latOf(zz));
                double rdj = CoastalLayer.rossbyRadius(fj);
                rdArr[j] = rdj;
                double[] tg = CoastalLayer.coastTangent(ex, zz, seed, cell);
                double tauS = 0;
                for (double th : OceanField.PH4) {
                    double[] ts = com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere.windStress(
                        ex - 50_000, zz, seed, cell, th, OceanField.GRAD);
                    tauS += (ts[0] * tg[0] + ts[1] * tg[1]) / OceanField.PH4.length;
                }
                double hcJ = CoastalLayer.hcLocal(tauS, fj);
                vJetArr[j] = CoastalLayer.jetPeak(hcJ, fj);
            }
            // ★★ 2026-10-14（B-4e 续，诊断一步命中）：**CFL 必须用【实际】速度** ✗
            //   初版只用【环流】速度（umax/vmax）算 CFL ✗ —— 但实际喂进平流的是
            //     v_eff = v_gyre*SURF_FACTOR + vJet*exp(...)（急流可达 1.73 m/s ✗）
            //   ⟹ 有效速度被低估 ~4.7*1.73/1e-5 ≈ 数十万倍 ✗
            //   ⟹ dtT 顶到 0.2/lambda = 777,600 s ⟹ CFL = dtT*|v|/h ≈ 6300 ≫ 1 ⟹ 距平场发散
            //   ⟹ 实测症状：某域 T' 有 74,694/104,721 = 71% 的格是 NaN ✗
            double vEff = Math.max(umax, vmax) * SeaSurfaceTemp.SURF_FACTOR;
            for (int j = 0; j < nz; j++) vEff = Math.max(vEff, Math.abs(vJetArr[j]));
            double[] T = new double[nx * nz];
            double dtT = 0.2 * Math.min(h / Math.max(1e-12, vEff), 1.0 / SeaSurfaceTemp.LAMBDA);
            if (!(dtT > 0) || Double.isInfinite(dtT)) dtT = 60.0;
            dtT = Math.min(dtT, 0.2 / SeaSurfaceTemp.LAMBDA);
            double[] T2 = new double[nx * nz];
            for (int s = 0; s < STEPS_T; s++) {
                for (int j = 1; j < nz - 1; j++) for (int i = 1; i < nx - 1; i++) {
                    int k = j * nx + i;
                    if (!sea[k]) { T2[k] = 0; continue; }
                    double u = uu[k] * SeaSurfaceTemp.SURF_FACTOR, v = vv2[k] * SeaSurfaceTemp.SURF_FACTOR;
                    // 沿岸急流（东边界现象，作用范围 JET_RANGE_RD*R_d ⟹ 只有东端几公里 ✓）
                    if (eastX[j] >= 0) {
                        double dJet = eastX[j] - (b.minX + i * h);
                        if (dJet > 0 && dJet < OceanField.JET_RANGE_RD * rdArr[j])
                            v += vJetArr[j] * Math.exp(-dJet / rdArr[j]);
                    }
                    double dTdx = u > 0 ? (T[k] - T[k - 1]) / h : (T[k + 1] - T[k]) / h;      // 迎风
                    double dTdzL = v > 0 ? (T[k] - T[k - nx]) / h : (T[k + nx] - T[k]) / h;
                    T2[k] = T[k] + dtT * (-u * dTdx - v * dTdzL + v * (-dTdz[j]) - SeaSurfaceTemp.LAMBDA * T[k]);
                }
                double[] sw = T; T = T2; T2 = sw;
            }
            double tmin = 0, tmax = 0; int nBad = 0;
            for (int k = 0; k < N; k++) {
                if (!sea[k]) continue;
                if (Double.isNaN(T[k])) nBad++;
                if (T[k] < tmin) tmin = T[k];
                if (T[k] > tmax) tmax = T[k];
            }
            double pMax = 0; int nSea = 0;
            for (int k = 0; k < N; k++) { if (!sea[k]) continue; nSea++; if (Math.abs(out[4 + k]) > pMax) pMax = Math.abs(out[4 + k]); }
            LAST_DIAG = String.format("网格 %dx%d（海格 %d），psi_max=%.4f m^2/s，|u|max=%.5f |v|max=%.5f mm/s，dtT=%.1f s，T' %+.4f~%+.4f K，NaN %d",
                nx, nz, nSea, pMax, umax * 1000, vmax * 1000, dtT, tmin, tmax, nBad);
            double[] out2 = new double[out.length + N];
            System.arraycopy(out, 0, out2, 0, out.length);
            System.arraycopy(T, 0, out2, out.length, N);
            return out2;
        }
        return out;
    }

    /**
     * 该点的二维闭合环流速度 {u, v}（m/s）；{@link #ENABLED}=false 或该点不在任何域内时返回
     * <b>null</b>（⟹ 调用方应回落到一维口径）。
     */
    public static synchronized double[] uvAt(int x, int z, int worldSeedInt) {
        if (!ENABLED) return null;
        double[] f = domainFor(x, z, worldSeedInt);
        if (f == null) return null;
        // ★ B-2d 布局（元数据最前 ✓）：[0]=nx(列) [1]=nRows(行) [2]=minX [3]=minZ [4]=dz(m)，psi 从 5 开始
        int nx = (int) f[0], nRows = (int) f[1], minX = (int) f[2], minZ = (int) f[3];
        double dz = f[4];
        final double h = OceanField.ROW_H;                    // 一维求解器的格距（行内 ✓）
        int i = (int) ((x - minX) / h), j = (int) ((z - minZ) / dz);
        if (i < 1 || j < 1 || i >= nx - 1 || j >= nRows - 1) return null;
        int k = 5 + j * nx + i;
        double v = (f[k + 1] - f[k - 1]) / (2 * h);           // v = +dpsi/dx ✓（就是一维解的 v ✓）
        double u = -(f[k + nx] - f[k - nx]) / (2 * dz);       // ★ u = -dpsi/dz ✓【本路线新增的那个分量】
        return new double[]{u, v};
    }

    /**
     * 该点的<b>二维距平 T'</b>（K）。{@link #T2D_ENABLED}=false 或该域没算距平时返回
     * {@code Double.NaN}（⟹ 调用方回落到一维局地平衡 ✓）。
     */
    public static synchronized double tprimeAt(int x, int z, int worldSeedInt) {
        if (!T2D_ENABLED) return Double.NaN;
        double[] f = domainFor(x, z, worldSeedInt);
        if (f == null) return Double.NaN;
        // 布局：[0]=nx [1]=nRows [2]=minX [3]=minZ [4]=dz + psi(nx*nRows) + T'(tx*nRows) + tx
        int nx = (int) f[0], nRows = (int) f[1], minX = (int) f[2], minZ = (int) f[3];
        double dz = f[4];
        int nP = nx * nRows;
        if (f.length < 5 + nP + 1) return Double.NaN;              // 没有距平场
        int tx = (int) f[f.length - 1];
        if (tx < 3) return Double.NaN;
        int TSTEP = Math.max(1, nx / tx);
        final double h = OceanField.ROW_H;
        int i = (int) ((x - minX) / h), j = (int) ((z - minZ) / dz);
        int ti = i / TSTEP;
        if (j < 0 || j >= nRows || ti < 0 || ti >= tx) return Double.NaN;
        return f[5 + nP + j * tx + ti];
    }

    /** 取包含该点的域（含规范化 + 域级缓存）；必要时求解。 */
    private static double[] domainFor(int x, int z, int worldSeedInt) {
        long seed = SimTerrain.seedOf(worldSeedInt);
        long mode = modeSig();
        if (seed != cachedSeed || mode != cachedMode) { DOMAINS.clear(); CANON.clear(); INDEX.clear(); cachedSeed = seed; cachedMode = mode; }
        // ★★★★★ 2026-10-14（B-2i）：**包含性判断的格距写错了** ✗✗ —— 这是"卡住"的真凶 ✓
        //   逐行路线的布局是：[0]=nx(列) [1]=nRows(行) [2]=minX [3]=minZ [4]=**dz**
        //     x 方向的格距 = OceanField.ROW_H = 125 m ✓；z 方向 = dz（存在 f[4] ✓）
        //   而旧代码用的是 ¤CELL = 1000¤ 当【两个方向】的格距 ✗ ⟹
        //     x 方向把域算小了 8 倍、z 方向算错 ⟹ 包含性判断【几乎永远不成立】✗✗
        //     ⟹ 【每个查询点都重新解一个域】✗✗✗（41 行 + T' ≈ 0.05~0.25 s ✗）
        //     ⟹ P477 的 ~21000 个点 = 小时级 ✗（实测：9 分钟没跑完 ✓）
        final int QI = Math.max(BasinFinder.SAMPLE, 20_000);
        long qk2 = ((long) (x / QI) << 32) ^ ((z / QI) & 0xFFFFFFFFL);
        double[] direct = INDEX.get(qk2);
        if (direct != null && inside(direct, x, z)) return direct;
        for (double[] cand : DOMAINS.values()) {
            if (inside(cand, x, z)) { INDEX.put(qk2, cand); return cand; }   // 登记索引 ⟹ 下次 O(1) ✓
        }
        double[] f = solveRows(x, z, seed);      // ★ B-2d：逐行一维解 + 行间差分（取代二维伪时间步进 ✓）
        if (f != null) {
            DOMAINS.put(domainKey((int) f[2], (int) f[3]), f);
            INDEX.put(qk2, f);
        }
        return f;
    }

    /** 该点是否落在域内（★ 用【正确的】两个格距 ✓）。 */
    private static boolean inside(double[] f, int x, int z) {
        if (f == null || f.length < 5) return false;
        int nx = (int) f[0], nRows = (int) f[1], minX = (int) f[2], minZ = (int) f[3];
        double dz = f[4];
        return x >= minX && z >= minZ && x < minX + nx * OceanField.ROW_H && z < minZ + nRows * dz;
    }

    /** 本类旋钮的配置指纹（纯函数）。 */
    public static long configStamp() {
        long h = 1125899906842597L;
        h = h * 31 + (ENABLED ? 1 : 0);
        h = h * 31 + (T2D_ENABLED ? 1 : 0);
        h = h * 31 + STEPS_T;
        h = h * 31 + STEPS;
        h = h * 31 + SOR_ZETA;
        h = h * 31 + SOR_POIS;
        h = h * 31 + CELL;
        h = h * 31 + Double.doubleToLongBits(OceanField.A_H);
        return h;
    }
}
