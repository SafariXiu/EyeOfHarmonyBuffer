package com.EyeOfHarmonyBuffer.sim.ocean;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;

/**
 * 自适应求解域 —— 新架构海洋层的**架构核心**（设计冻结 §3.1'）。
 *
 * <h3>为什么不能用固定窗口</h3>
 * 旧实现用「300 km 固定窗口 + X 环绕」近似无限域。`v = ∂ψ/∂x`，周期域上 ψ 单值
 * ⇒ **∮v dx 绕一圈 = 0**；实测同一套参数下闭合盆 153 mm/s、环绕 **0.00 mm/s**（P242 E vs C）。
 *
 * <h3>为什么也不能用「连通海区」（§15 的教训）</h3>
 * P247 实测：**97.6% 的查询点，其连通海区撑满 1600 km 搜索半径** —— 海洋是连通的，
 * 「连通分量」返回的是整个世界的海，成本估算 850 s，直接爆预算。
 * 真实地球的海洋同样连通，但北大西洋与北太平洋各自长自己的涡旋。
 *
 * ⇒ 涡旋的「盆」是**在一个纬度范围内被海岸夹住的那一片**，应该由**海岸**定界。
 *
 * <h3>本类的口径（§3.1'）</h3>
 * <pre>
 *   1. 在查询行找到包含查询点的海区间 [w, e]；
 *   2. 向南/向北走，只要【西墙是同一道岸】(相邻行位移 <= WALL_TOL) 就继续；
 *   3. 域 = 这些区间并集的包围盒 + 一圈墙环；
 *   4. 北/南边缘是「海岸结束的地方」，求解时视作 psi=0 的墙。
 * </pre>
 *
 * <p>P243 实测（板块格 600 km）西墙连续长度：中位 125 km、P90 765 km、最大 2255 km。
 * 按此估算求解成本：中位量级几秒、P90 数十秒、最大约 200 s ⇒ **可负担**。
 *
 * <h3>纯函数纪律</h3>
 * 无状态纯函数：同 (seed, x, z, cell) 永远同结果。
 * 生产环境应按「域键」缓存（一个域的解可被域内所有查询点共享），但缓存不是本类的职责。
 */
public final class BasinFinder {

    private BasinFinder() {}

    /** 粗采样间距（block）。20 km 与求解器粗格同量级。 */
    public static final int SAMPLE = 20_000;
    /** 单方向搜索半径（格）=> 1600 km。超过则标记 truncated。 */
    public static final int MAX_R = 80;
    /** 诊断：D57 的早退与掩膜中心格**不一致**的次数。正常恒为 0（不一致就是等价性论证有洞）。 */
    public static long earlyOutMismatch = 0;
    /** 西墙相邻行允许的位移（block）。与 P243 的 WALL_TOL 一致。 */
    public static final int WALL_TOL = 60_000;
    /** CFL 安全系数（§11：余量 ~3 必发散，取 10）。 */
    public static final double CFL_SAFETY = 10.0;

    /** 一个「海岸定界」的求解域。 */
    public static final class Basin {
        public boolean valid;        // 查询点是海
        /**
     * 搜索半径被撑满 ⇒ 真实域更大，需要开边界回退。
     *
     * <p>⚠⚠ <b>审计 D33（2026-09-13 复核）：本字段目前【零消费者】，而且它在 z 方向上【永远为 false】。</b>
     * <ul>
     *   <li><b>零消费者</b>：grep 全 src 树，除本文件自身的赋值与 {@code toString()} 之外没有任何一处读它 ⇒
     *       它是一个**死输出**，不要拿它做任何判断；</li>
     *   <li><b>z 方向恒 false</b>：南北扩展循环是 {@code for (step = 1; step <= MAX_R; step++)}，
     *       行号 {@code r = cz + dir*step}，而 {@code cz = MAX_R}、{@code W = 2*MAX_R+1}
     *       ⇒ {@code r ∈ [0, 2*MAX_R]} = [0, W-1] **恒在界内** ⇒
     *       那句 {@code if (r < 0 || r >= W) { trunc = true; break; }} **不可达**。
     *       所以只有「西墙/东墙撞到 W 的边界」这一路会把 trunc 置真。</li>
     * </ul>
     * <b>结论：D33 是「潜在陷阱」而不是「在跑的 bug」</b>（因为没人读它）。
     * 修它要么给 z 方向也扩一圈窗口、要么删掉这个分支 —— 但**在有人真正需要 truncated 之前不该动**，
     * 因为任何改动都要重跑验收。
     *
     * <p>⚠ 同一条 D33 里的另一半主张「12 个探针各自用魔数 20_000 打补丁」——
     * <b>我没有复现</b>（grep 探针树的 62 处 20_000 绝大多数是各自独立的采样步长，
     * 只有 P248 自己声明了一个同名常量）。按项目纪律，那一半标为 <b>未对账</b>，不进缺陷账。
     */
    public boolean truncated;
        public int minX, maxX, minZ, maxZ;   // **求解域** bbox（含一圈墙环）
        public int rows;             // 接受到的纬向行数
        public int maxWidthCells;    // 最宽那一行的海格数
        public int seaCells;         // 域内海格数（粗）

        public double widthKm()  { return (maxX - minX) / 1000.0; }
        public double heightKm() { return (maxZ - minZ) / 1000.0; }
        /** 域尺度：取包围盒长边。Rossby CFL 用它。 */
        public double spanKm()   { return Math.max(widthKm(), heightKm()); }
        /** 西墙连续长度（= 域高）。这是「西边界流有多少跑道」。 */
        public double westRunKm() { return heightKm(); }

        @Override public String toString() {
            if (!valid) return "Basin(非海格)";
            return String.format(java.util.Locale.ROOT,
                "Basin(%.0f x %.0f km, 海格 %d, 最宽 %d 格%s)",
                widthKm(), heightKm(), seaCells, maxWidthCells, truncated ? ", 截断" : "");
        }
    }

    /** 找出查询点所在的「海岸定界」域。纯函数。 */
    public static Basin find(int x, int z, long seed, int cell) {
        final int W = 2 * MAX_R + 1;
        final int cx = MAX_R, cz = MAX_R;
        Basin b = new Basin();
        // ⚠⚠ 审计 D57（2026-09-13，接线后跑验收时抓到的**生产级**性能缺陷）：
        // 「查询点本身是陆地 ⇒ 不属于任何海盆」这个早退**必须放在最前面**。
        // 原写法是先把这个 161x161 = **25,921 格**的陆地掩膜**全部算出来**，
        // 才在第 84 行检查中心格 —— 而中心格 land[cz*W+cx] 恰好就等于
        // PlateField.isLandWithCell(x, z, seed, cell)（因为 jz=cz=MAX_R ⇒ wz=z、jx=cx ⇒ 该 x）。
        // 代价：陆地查询要白算 25,921 次 isLandWithCell（每次还会走 elevationFull 的 Math.hypot）
        // ⇒ 实测 7~50 ms/次。而**每一次 sstAnom(x,z) 都会走这里**（pressureAnomaly -> sstAnom），
        // 陆地格点占三成以上 ⇒ 区块生成与一切大范围导出都被这一条拖住。
        // 等价性：掩膜构造**没有任何副作用**，早退条件与第 84 行**逐字相同** ⇒
        // 对任意 (x,z) 返回值恒等（P474 会逐点断言 + P467/P470/P472 的读数逐字回归）。
        if (PlateField.isLandWithCell(x, z, seed, cell)) { b.valid = false; return b; }
        boolean[] land = new boolean[W * W];
        for (int jz = 0; jz < W; jz++) {
            int wz = z + (jz - MAX_R) * SAMPLE;
            int row = jz * W;
            for (int jx = 0; jx < W; jx++) {
                land[row + jx] = PlateField.isLandWithCell(x + (jx - MAX_R) * SAMPLE, wz, seed, cell);
            }
        }
        // 保留原检查作为**自洽断言**：如果早退与它不一致，说明上面的等价性论证有洞。
        if (land[cz * W + cx]) { earlyOutMismatch++; b.valid = false; return b; }

        int tolSteps = WALL_TOL / SAMPLE;
        // --- 初始行区间 ---
        int w = cx; while (w > 0 && !land[cz * W + w - 1]) w--;
        int e = cx; while (e < W - 1 && !land[cz * W + e + 1]) e++;
        boolean trunc = (w == 0 || e == W - 1);
        int unionW = w, unionE = e, minRow = cz, maxRow = cz;
        int rows = 1, maxW = e - w + 1;
        long seaCells = e - w + 1;

        // --- 向北 / 向南扩展 ---
        for (int dir = -1; dir <= 1; dir += 2) {
            int pw = w, pe = e;
            int curW = w, curE = e;
            for (int step = 1; step <= MAX_R; step++) {
                int r = cz + dir * step;
                if (r < 0 || r >= W) { trunc = true; break; }
                int row = r * W;
                // 在新区间里找与 [pw, pe] 重叠的那一段：从 pw 起向东找第一个海格
                int s = -1;
                for (int q = pw; q <= pe; q++) if (!land[row + q]) { s = q; break; }
                if (s < 0) break;                       // 该行没有与之重叠的海 ⇒ 岸在这里结束
                int nw = s; while (nw > 0 && !land[row + nw - 1]) nw--;
                int ne = s; while (ne < W - 1 && !land[row + ne + 1]) ne++;
                if (Math.abs(nw - pw) > tolSteps) break; // 西墙跳变 ⇒ 换了一道岸
                if (nw == 0) trunc = true;
                if (ne == W - 1) trunc = true;
                pw = nw; pe = ne;
                curW = Math.min(curW, nw); curE = Math.max(curE, ne);
                if (r < minRow) minRow = r;
                if (r > maxRow) maxRow = r;
                rows++;
                int wdt = ne - nw + 1;
                if (wdt > maxW) maxW = wdt;
                seaCells += wdt;
            }
            unionW = Math.min(unionW, curW); unionE = Math.max(unionE, curE);
        }

        b.valid = true; b.truncated = trunc;
        b.rows = rows; b.maxWidthCells = maxW; b.seaCells = (int) seaCells;
        // 求解域 = 并集 + 一圈墙环
        b.minX = x + (unionW - 1 - MAX_R) * SAMPLE;
        b.maxX = x + (unionE + 1 - MAX_R) * SAMPLE;
        b.minZ = z + (minRow - 1 - MAX_R) * SAMPLE;
        b.maxZ = z + (maxRow + 1 - MAX_R) * SAMPLE;
        return b;
    }

    /**
     * 该域允许的最大伪时间步（设计冻结 §5 的 R5，§11 实测）。
     *
     * <p>`c_R ≈ βL²/π²`，实测 **CFL 余量 ~3 必发散**，所以取安全系数 10。
     */
    public static double safeDt(double dx, double beta, double spanMeters) {
        return dx * Math.PI * Math.PI / (CFL_SAFETY * beta * spanMeters * spanMeters);
    }
}
