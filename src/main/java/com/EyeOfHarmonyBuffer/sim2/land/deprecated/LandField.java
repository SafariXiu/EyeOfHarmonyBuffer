package com.EyeOfHarmonyBuffer.sim2.land.deprecated;

import com.EyeOfHarmonyBuffer.sim2.land.LandContract;
import com.EyeOfHarmonyBuffer.sim2.land.LandNoise;



import java.util.concurrent.ConcurrentHashMap;

/**
 * 新海陆分布 v3 的核心场：<b>一个标量 {@code F}（地壳厚度异常，m）同时定海陆/高度</b>。
 *
 * <p><b>2026-10-21 重写版</b>（原文件被 PowerShell 的编码转换损坏，按算法逐段重建）。结构：
 * <ul>
 *   <li><b>板块层</b>：Worley 细胞（6,000 km）+ 巨型核投票 + 两层扭曲；
 *       每块板块一个常速度（无散背景流 ψ + 板片拉力 + 逐板块抖动）。</li>
 *   <li><b>大陆层</b>：连续的 {@code c} 场（脊化活动带）定海陆，与板块解耦——
 *       海岸线不沿板块边界走。</li>
 *   <li><b>构造项</b>：按相对运动的法向/切向分解（汇聚/张裂/转换/斜向），
 *       质量守恒 + 临界楔给峰值与带宽，远场传递 + 褶皱列车给形态，海沟/岛弧按俯冲极性分侧。</li>
 *   <li><b>连续性</b>：陆/洋、极性、f/W0 这些分支都做了连续化；
 *       只有"最近细胞换人"这一层仍是离散的（见设计文档 §30）。</li>
 * </ul>
 *
 * <p>⚠ 铁律：本类不 import 任何旧海陆类；接线由 {@code PlateField} 门面负责。
 */
public final class LandField {

    private LandField() {}

    /** 平滑阶跃：x≤e0 → 0，x≥e1 → 1。 */
    private static double smoothstep(double e0, double e1, double x) {
        double t = (x - e0) / (e1 - e0);
        if (t < 0.0) t = 0.0; else if (t > 1.0) t = 1.0;
        return t * t * (3.0 - 2.0 * t);
    }

    // ================= 缓存 =================
    private static final ConcurrentHashMap<Long, Double> CUT_CACHE =
            new ConcurrentHashMap<Long, Double>();
    private static final ThreadLocal<double[]> HBUF = new ThreadLocal<double[]>() {
        @Override protected double[] initialValue() { return new double[1]; }
    };

    private static final class Cell {
        double sx, sz, d;
        long id;
    }
    private static final class Pl {
        long id;
        double tx, tz;
        double t;
        double vx, vz;
        boolean cont;
        /** 该板块类型点处的大陆性 c（越大越"陆"）；用于判定谁俯冲。 */
        double cc;
    }

    private static final ThreadLocal<Cell[]> CELLS = new ThreadLocal<Cell[]>() {
        @Override protected Cell[] initialValue() {
            return new Cell[]{new Cell(), new Cell(), new Cell(), new Cell(), new Cell()};
        }
    };
    private static final ThreadLocal<Pl[]> PLATES = new ThreadLocal<Pl[]>() {
        @Override protected Pl[] initialValue() {
            return new Pl[]{new Pl(), new Pl(), new Pl(), new Pl(), new Pl()};
        }
    };
    // 3x3 full neighbourhood for strain (9 cells, unsorted): no rank-swap discontinuity.
    private static final ThreadLocal<Cell[]> SG = new ThreadLocal<Cell[]>() {
        @Override protected Cell[] initialValue() {
            Cell[] a = new Cell[9];
            for (int i = 0; i < 9; i++) a[i] = new Cell();
            return a;
        }
    };
    private static final ThreadLocal<Pl[]> SPG = new ThreadLocal<Pl[]>() {
        @Override protected Pl[] initialValue() {
            Pl[] a = new Pl[9];
            for (int i = 0; i < 9; i++) a[i] = new Pl();
            return a;
        }
    };

    private static final ThreadLocal<double[]> TMP = new ThreadLocal<double[]>() {
        @Override protected double[] initialValue() { return new double[4]; }
    };
    private static final ThreadLocal<double[]> MT = new ThreadLocal<double[]>() {
        @Override protected double[] initialValue() { return new double[4]; }
    };
    private static final ThreadLocal<double[]> ST = new ThreadLocal<double[]>() {
        @Override protected double[] initialValue() { return new double[7]; }
    };
    /** strain() 专用缓冲：它会被 f() 重入调用，绝不能复用车队局部 CELLS/PLATES。 */
    private static final ThreadLocal<Cell[]> SC = new ThreadLocal<Cell[]>() {
        @Override protected Cell[] initialValue() {
            return new Cell[]{new Cell(), new Cell(), new Cell(), new Cell(), new Cell()};
        }
    };
    private static final ThreadLocal<Pl[]> SP = new ThreadLocal<Pl[]>() {
        @Override protected Pl[] initialValue() {
            return new Pl[]{new Pl(), new Pl(), new Pl(), new Pl(), new Pl()};
        }
    };

    // ================================================================================
    // 公开 API
    // ================================================================================

    /** 由每种子二分定标的大陆性阈值（目标陆占 {@link LandContract#LAND_TARGET}）。 */
    public static double cut(final long ws) {
        Double v = CUT_CACHE.get(ws);
        if (v != null) return v.doubleValue();
        double lo = 0.20, hi = 0.95;
        for (int i = 0; i < LandContract.CAL_ITERS; i++) {
            double mid = 0.5 * (lo + hi);
            double f = landFraction(ws, mid, LandContract.CAL_SPAN, LandContract.CAL_STEP);
            if (f > LandContract.LAND_TARGET) lo = mid; else hi = mid;
        }
        double c = 0.5 * (lo + hi);
        Double prev = CUT_CACHE.putIfAbsent(ws, c);
        return prev != null ? prev.doubleValue() : c;
    }

    public static boolean isLand(long ws, double x, double z) {
        return f(ws, cut(ws), x, z, null) > 0.0;
    }

    public static double height(long ws, double x, double z) {
        double[] h = HBUF.get();
        f(ws, cut(ws), x, z, h);
        return h[0];
    }

    public static double landFraction(long ws, double cut, double span, double step) {
        int n = (int) Math.round(span / step);
        int land = 0, tot = 0;
        for (int j = 0; j < n; j++) {
            double z = j * step;
            for (int i = 0; i < n; i++) {
                if (f(ws, cut, i * step, z, null) > 0.0) land++;
                tot++;
            }
        }
        return land / (double) tot;
    }

    /** 诊断：{tc, base, dt, conv, db, orog, det, F, conv, shear}；长度 &gt;11 时追加 cell/plate id。 */
    public static void debug(long ws, double x, double z, double[] out) {
        f(ws, cut(ws), x, z, null, out);
    }

    /** 该点所属板块的稳定 key（板块图用）。 */
    public static long plateKey(long ws, double px, double pz) {
        Cell[] cs = CELLS.get();
        cell2(px + warpX(px, pz), pz + warpZ(px, pz), cs);
        Pl[] ps = PLATES.get();
        plateOf(ws, cs[0], cut(ws), ps[0]);
        return ps[0].id;
    }

    /** 诊断：该点所属板块的速度（归一化单位；× {@link LandContract#V_SCALE} 得 m/yr）。 */
    public static void plateVelocity(long ws, double px, double pz, double[] out) {
        Cell[] cs = CELLS.get();
        cell2(px + warpX(px, pz), pz + warpZ(px, pz), cs);
        Pl[] ps = PLATES.get();
        plateOf(ws, cs[0], cut(ws), ps[0]);
        out[0] = ps[0].vx;
        out[1] = ps[0].vz;
    }

    /**
     * 原型：把速度场平滑成 C¹，再解析求应变率张量。
     *
     * <pre>
     *   v(x) = Σ wⱼ vⱼ / Σ wⱼ,     wⱼ = exp(−dⱼ²/(2·STRAIN_L²))   （最近 5 个细胞）
     *   ∇wⱼ = −(dⱼ/L²)·wⱼ·ûⱼ
     *   ∇v  = (Σ ∇wⱼ⊗vⱼ)/W − v⊗∇W/W
     *   ε   = ½(∇v + ∇vᵀ)
     * </pre>
     *
     * @param out {εxx, εxz, εzz, 散度, 最大主压应变, W}
     */
    public static void strain(long ws, double px, double pz, double[] out) {
        strainAt(ws, px, pz, cut(ws), out);
    }

    /** 内部版：cut 由调用方给（{@code f()} 会重入，不能再触发 cut() 的定标递归）。 */
    static void strainAt(long ws, double px, double pz, double cut, double[] out) {
        double qx = px + warpX(px, pz), qz = pz + warpZ(px, pz);
        // 3x3 full neighbourhood: the SUM never changes its member set inside a block,
        // so v(x) has no rank-swap jump (that was the source of the last creases).
        Cell[] cs = SC.get();
        cell2(qx, qz, cs);
        Pl[] ps = SP.get();
        for (int j = 0; j < 5; j++) plateOf(ws, cs[j], cut, ps[j]);
        double L2 = LandContract.STRAIN_L * LandContract.STRAIN_L;
        // ---- 第一遍：不加方向权重的平滑速度 v̄（它是连续的，可作第二遍的基准）
        double Ws = 0, mx = 0, mz = 0;
        for (int j = 0; j < 5; j++) {
            double dx = qx - cs[j].sx, dz = qz - cs[j].sz;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d < 1.0) d = 1.0;
            double w = Math.exp(-(d * d) / (2.0 * L2));
            Ws += w;
            mx += w * ps[j].vx; mz += w * ps[j].vz;
        }
        if (Ws < 1e-12) Ws = 1e-12;
        mx /= Ws; mz /= Ws;

        // ---- 第二遍：方向相似度加权
        // aⱼ 的基准是【v̄】（连续），不是 v₀（离散）——后者实测会把台阶从 453 推到 651。
        // 物理：只有同向推进的板块才共同缩短地壳；反向/斜向的板块（走滑、转换）不参与。
        // ∇(w·a) 只取 a·∇w（a 变化缓慢，忽略 ∇a 不影响连续性）。
        double W = 0, vx = 0, vz = 0, dWx = 0, dWz = 0;
        double nxx = 0, nxz = 0, nzx = 0, nzz = 0;
        double nb = Math.sqrt(mx * mx + mz * mz);
        if (nb < 1e-9) nb = 1e-9;
        for (int j = 0; j < 5; j++) {
            double dx = qx - cs[j].sx, dz = qz - cs[j].sz;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d < 1.0) d = 1.0;
            double w = Math.exp(-(d * d) / (2.0 * L2));
            double nj = Math.sqrt(ps[j].vx * ps[j].vx + ps[j].vz * ps[j].vz);
            if (nj < 1e-9) nj = 1e-9;
            double a = 0.5 + 0.5 * ((ps[j].vx * mx + ps[j].vz * mz) / (nj * nb));
            if (a < 0.0) a = 0.0; else if (a > 1.0) a = 1.0;
            a = a * a * (3.0 - 2.0 * a);
            w *= a;
            double dwx = -(d / L2) * w * (dx / d);
            double dwz = -(d / L2) * w * (dz / d);
            W += w;
            vx += w * ps[j].vx;  vz += w * ps[j].vz;
            dWx += dwx;          dWz += dwz;
            nxx += dwx * ps[j].vx; nxz += dwx * ps[j].vz;
            nzx += dwz * ps[j].vx; nzz += dwz * ps[j].vz;
        }
        if (W < 1e-12) W = 1e-12;
        double iw = 1.0 / W;
        vx *= iw; vz *= iw;
        double jxx = nxx * iw - vx * dWx * iw;
        double jxz = nxz * iw - vz * dWx * iw;
        double jzx = nzx * iw - vx * dWz * iw;
        double jzz = nzz * iw - vz * dWz * iw;
        double exx = jxx, exz = 0.5 * (jxz + jzx), ezz = jzz;
        double div = exx + ezz;
        double half = 0.5 * (exx - ezz);
        double dev = Math.sqrt(half * half + exz * exz);
        out[0] = exx; out[1] = exz; out[2] = ezz; out[3] = div;
        out[4] = -(0.5 * div - dev);   // 最大主压应变（正 = 挤压）
        out[6] = div * div + 4.0 * dev * dev;   // 应变能量（平方范数，零点平滑，无 V 形）
        out[5] = W;
    }

    /**
     * 边界类型：0 无、1 汇聚、2 张裂、3 转换、4 斜向。
     * <p>分类依据：{@code conv = −(v_rel·n̂)} 与 {@code shear = |v_rel·t̂|}。
     */
    public static int boundaryType(long ws, double px, double pz) {
        Cell[] cs = CELLS.get();
        cell2(px + warpX(px, pz), pz + warpZ(px, pz), cs);
        Pl[] ps = PLATES.get();
        double cut = cut(ws);
        plateOf(ws, cs[0], cut, ps[0]);
        plateOf(ws, cs[1], cut, ps[1]);
        if (ps[0].id == ps[1].id) return 0;
        double ex = cs[1].sx - cs[0].sx, ez = cs[1].sz - cs[0].sz;
        double len = Math.sqrt(ex * ex + ez * ez);
        if (len < 1.0) len = 1.0;
        double nx = ex / len, nz = ez / len;
        double dvx = ps[1].vx - ps[0].vx, dvz = ps[1].vz - ps[0].vz;
        double conv = -(dvx * nx + dvz * nz);
        double shear = Math.abs(dvx * (-nz) + dvz * nx);
        return cls(conv, shear);
    }

    /**
     * 边界分类（尺度无关）：看相对运动与法向的夹角。
     * <pre>
     *   tan = shear / |conv|
     *   tan &lt; 0.577 → 汇聚/张裂（30° 内）；tan &gt; 1.732 → 转换（60° 以上）；否则斜向
     * </pre>
     */
    private static int cls(double conv, double shear) {
        double ac = Math.abs(conv);
        if (ac > 1e-6) {
            double tan = shear / ac;
            if (tan < 0.577) return conv > 0 ? 1 : 2;
            if (tan > 1.732) return 3;
            return 4;
        }
        return shear > 1e-6 ? 3 : 0;
    }

    // ================================================================================
    // 核心求值
    // ================================================================================

    static double f(long ws, double cut, double px, double pz, double[] outH) {
        return f(ws, cut, px, pz, outH, null);
    }

    static double f(long ws, double cut, double px, double pz, double[] outH, double[] dbg) {
        double qx = px + warpX(px, pz);
        double qz = pz + warpZ(px, pz);

        Cell[] cs = CELLS.get();
        cell2(qx, qz, cs);
        Pl[] ps = PLATES.get();
        plateOf(ws, cs[0], cut, ps[0]);
        plateOf(ws, cs[1], cut, ps[1]);
        Pl p0 = ps[0], p1 = ps[1];

        // 到最近两块细胞中垂面的有符号距离（正 = 更靠近 c0）
        double db = 0.5 * (cs[1].d - cs[0].d);

        // ---- 大陆场：在【板块参考系】里取样 ----
        // 每块板块把同一张"原始大陆"沿运动方向搬 v·τ，于是跨过裂谷边界两侧是同一块
        // 大陆被搬开的两半（共轭边缘）。⚠ CRUST_DRIFT 默认 0：参考系切换会让取样坐标
        // 跳 1,000+ km（实测 tc 从 37,041 跳到 12,576 m），等"参考系本身连续"再开。
        double tau = LandContract.TAU_MIN + (LandContract.TAU_MAX - LandContract.TAU_MIN)
                * LandNoise.value(px / LandContract.TAU_WL, pz / LandContract.TAU_WL, ws ^ 0x71L);
        double disp = LandContract.V_SCALE * tau * LandContract.CRUST_DRIFT;

        // 海岸线域扭曲：位移【坐标】而不是叠高度（叠高度只有 ±380 m ⟹ 岸线只挪 6 km）
        double cwx = LandContract.CW_A1 * (LandNoise.value(qx / LandContract.CW_WL1,
                        qz / LandContract.CW_WL1, ws ^ 0x91L) - 0.5) * 2.0
                   + LandContract.CW_A2 * (LandNoise.value(qx / LandContract.CW_WL2 + 3.1,
                        qz / LandContract.CW_WL2 - 1.7, ws ^ 0x92L) - 0.5) * 2.0;
        double cwz = LandContract.CW_A1 * (LandNoise.value(qx / LandContract.CW_WL1 + 7.3,
                        qz / LandContract.CW_WL1 - 4.1, ws ^ 0x93L) - 0.5) * 2.0
                   + LandContract.CW_A2 * (LandNoise.value(qx / LandContract.CW_WL2 - 5.9,
                        qz / LandContract.CW_WL2 + 2.3, ws ^ 0x94L) - 0.5) * 2.0;
        double ccx = qx + cwx, ccz = qz + cwz;

        // ---- 大陆场取样 ----
        // ⚠ 这里曾有一段"跨板块边缘混合 cA/cB"的分支（用 db 判定 |db|<CRUST_BAND）。
        //   CRUST_DRIFT=0 时 cA≡cB，它什么也没做，却在地形里留下一条沿 Voronoi 中垂线的
        //   直线边界（用户放大截图的图2/图3）。地形里不该出现任何 |db| 几何。
        double c0 = cellC(ws, ccx, ccz);

        // ---- 地壳厚度 ----
        double su = (c0 - cut) / 0.03;
        if (su < 0) su = 0;
        if (su > 1) su = 1;
        su = su * su * (3 - 2 * su);
        double tc = LandContract.T_OCEAN + (LandContract.T_CONT - LandContract.T_OCEAN) * su;
        double b = (c0 - cut - 0.01) / 0.06;
        if (b > 0.0) {
            if (b > 1.0) b = 1.0;
            tc += 4_000.0 * (b * b * (3.0 - 2.0 * b));
        }
        double base = tc - LandContract.T_REF;

        // ---- 构造项：主对 + 一层连续混合 ----
        // 实测四种写法里只有这一种最好（判据 LandSeam）：
        //   单层混合 1,788 处 / 最差 1,234 · 三对相加 550 · 两层混合 1,987/5,645 · 核加权 1,994/1,267
        // ---- 构造项 ----
        // 两条路径（A/B，默认走旧的"选边界对"）：
        //   OROG_EPS=false：主对 + 一层连续混合（有 2,120 处换人台阶）
        //   OROG_EPS=true ：从平滑速度场的解析应变直接给（残差 46 处，但需要重新调幅）
        double dt, conv, orog, shearDbg;
        if (LandContract.OROG_EPS) {
            double[] st = ST.get();
            strainAt(ws, px, pz, cut, st);
            // Softplus instead of max(0, x): the clamp leaves a V-shaped kink on the
            // neutral (compression/extension) line, and dt is proportional to it,
            // so the terrain gets a V-shaped notch along that line.
            // With the old 2000 km kernel the V was smeared over 2000 km; at 350 km it is a cliff.
            double compRaw = st[4] / LandContract.EPS_REF;      // ~1 = strong compression
            double comp = 0.5 * (compRaw + Math.sqrt(compRaw * compRaw + 0.0025));
            // 主应变方向先取：下面要在 4 个偏移点上平均 comp，st 会被覆盖。
            double th = 0.5 * Math.atan2(2.0 * st[1], st[0] - st[2]);
            // 始终做 5 点平均（不再有开关）。
            // 只要保留"原始 vs 平均"的切换点，跨过它就是 1 km 内 3 倍的 comp 跳变
            // ⟹ 构造项跳 5 km。实测 35296/26436 就是这个，且 cellId/plateId 都没变。
            {
                double sum = comp;
                double cgPre = base / LandContract.COAST_GATE + 0.5;
                if (cgPre > 0.02 && comp > 0.02) {
                    for (int k = 0; k < 4; k++) {
                        double ox = (k == 0) ? 90_000.0 : (k == 1) ? -90_000.0 : 0.0;
                        double oz = (k == 2) ? 90_000.0 : (k == 3) ? -90_000.0 : 0.0;
                        strainAt(ws, px + ox, pz + oz, cut, st);
                        double c2 = st[4] / LandContract.EPS_REF;
                        if (c2 < 0.0) c2 = 0.0;
                        sum += c2;
                    }
                    comp = sum * 0.2;
                } else {
                    comp = sum;
                }
            }
            double tauE = LandContract.TAU_MIN + (LandContract.TAU_MAX - LandContract.TAU_MIN)
                    * LandNoise.value(px / LandContract.TAU_WL, pz / LandContract.TAU_WL, ws ^ 0x71L);
            // 幅度：0.5 → 0.25（上一版白色说明顶到 4 km 以上）
            double se = 5.0 * LandContract.V_SCALE * comp * tauE;   // sink-weighted velocity lowers comp; compensate
            // ---- 造山带厚度：按【均衡极限】饱和，而不是把缩短量摊到无上限的宽度上 ----
            // 观测：西藏 70 km 地壳/4.5–5 km/宽 ~1,000 km；安第斯 60–70 km/4–6.9 km/宽 250–300 km；
            //      阿尔卑斯 50–55 km/3–4.8 km/宽 150–200 km。
            // ⟹ 带【宽】由岩石圈强度定（几百 km），不是「缩短多少就宽多少」；多出来的缩短量只能进高度。
            //   ΔH → 0.798·H0 是均衡极限（H0 = EPS_T_COLL）。之前把 se 硬封顶，
            //   等于把这条物理上限当成了"防止过冲"的补丁，结果峰永远到不了观测值。
            double tg = 1.0 - Math.exp(-se / LandContract.OROG_SE_CAP);
            double ampE = 0.798 * LandContract.EPS_T_COLL * tg;
            double sigE = Math.min(0.5 * (300_000.0 + se), LandContract.OROG_SIG_MAX);
            // 陆/洋门：和旧路径同一套 COAST_GATE（否则洋里也长山）
            double cgE = base / LandContract.COAST_GATE + 0.5;
            if (cgE < 0.0) cgE = 0.0; else if (cgE > 1.0) cgE = 1.0;
            cgE = cgE * cgE * (3.0 - 2.0 * cgE);
            // ==== faithful port of TalosLandField.orogeny01 (the OLD, good-looking belts) ====
            // d = |r1 - r0| / |grad(r1 - r0)|  is the TRUE distance to the bisector
            //   (my earlier 0.5*|d_i - d_j| was un-normalised, hence wrong width).
            // band = BAND * (0.30 + 0.70*conv)  -> strong convergence = wide belt
            // shape = (t^2 * conv)^gamma        -> sharp crest, smooth foot
            double rr0 = Math.max(1.0, cs[0].d), rr1 = Math.max(1.0, cs[1].d);
            double u0x = (qx - cs[0].sx) / rr0, u0z = (qz - cs[0].sz) / rr0;
            double u1x = (qx - cs[1].sx) / rr1, u1z = (qz - cs[1].sz) / rr1;
            double gmx = u1x - u0x, gmz = u1z - u0z;
            double gm = Math.sqrt(gmx * gmx + gmz * gmz);
            double dEdge = gm > 1e-12 ? Math.abs(rr1 - rr0) / gm : 1e9;
            double c01 = 0.5 * (comp + Math.sqrt(comp * comp + 0.01));
            if (c01 > 1.0) c01 = 1.0;
            double bandW = 130_000.0 * (0.30 + 0.70 * c01);
            double tBelt = 1.0 - dEdge / bandW;
            double belt = tBelt > 0.0 ? tBelt * tBelt : 0.0;
            double shape = Math.pow(belt * c01, 1.3);
            double sharp = shape;
            double v1 = LandNoise.value(px / 110_000.0, pz / 110_000.0, ws ^ 0x86L);
            double v2 = LandNoise.value(px / 45_000.0 + 3.7, pz / 45_000.0 - 1.9, ws ^ 0x87L);
            double ridgeN = 0.66 * (1.0 - Math.abs(2.0 * v1 - 1.0))
                          + 0.34 * (1.0 - Math.abs(2.0 * v2 - 1.0));
            double foldE = 1.0 - 0.25 * (1.0 - Math.pow(ridgeN, 1.3));
            double amE = 0.55 + 0.45 * LandNoise.value(px / 180_000.0, pz / 180_000.0, ws ^ 0x88L);
            dt = cgE * 41_000.0 * shape * foldE * amE;
            // ★ 高原：在缝合带【后方】铺开的 hinterland，位置由 |db| 给，半径随缩短量增长。
            //   之前把"平顶"做在 comp 上 ⟹ 等于把山带整体抬高，不是高原（用户实测指正）。
            //   青藏：喜马拉雅山带在前，西藏高原在后方 300–1,000 km，高度 4.5–5 km。
            double rPlat = 260_000.0 + se * 0.55;
            if (rPlat > 1_000_000.0) rPlat = 1_000_000.0;
            // 边缘扰动：|db| 是 Voronoi 中垂线的距离，等值线是【直线】而且在顶点处折角
            // ⟹ 高原边界像用尺子画的（用户实测指正）。加两八度噪声把半径打乱。
            // ② 边界不能是直线：|db| 的等值线本质是 Voronoi 中垂线，只能靠更强的半径扰动打散。
            // ★ 高原范围改用【连续的 comp 场】，彻底不用 |db|。
            //   |db| 是 Voronoi 中垂线距离：等值线是直线、顶点处有折角 —— 实测放大图上
            //   留下一道道细直折痕。comp 处处连续。门槛比山带（0.25~0.55）更低 ⟹ 包围山带一圈。
            double plat = smoothstep(0.10, 0.30, comp);
            if (plat > 0.0) {
                // 取 max 而不是相加：高原是同一块增厚地壳【横向铺开】，不是再叠一层。
                // 相加会把山带+高原叠到 9.5 km（实测），比珠峰还高。
                // ① 高原面要平：fold 只该作用在山带上，权重 0.25 → 0.15
                double pv = cgE * (0.62 * ampE) * plat * (0.85 + 0.15 * foldE) * amE;
                // 平滑最大值（soft-max）：`max(a,b)` 在 a=b 处一阶不连续，
                // 阴影会把那条等值线画成一条细直折痕（用户放大截图里那些线）。
                // softmax 处处 C¹，与 max 的偏差 < 1 km 厚度。
                double d = dt - pv;
                dt = 0.5 * (dt + pv + Math.sqrt(d * d + 4.0e6));
            }
            // 高原：峰到顶后，剩下的挤压量横向铺开（青藏式）。
            // 用比山带宽得多的门（指数 0.55 vs 1.2），所以是台地形态而不是高墙。
            if (se >= LandContract.OROG_SE_CAP) {
                double over = se / LandContract.OROG_SE_CAP - 1.0;
                double pw = 1.0 - Math.exp(-over);
                double sharpP = Math.pow(comp / (0.30 + comp), 0.55);
                dt += cgE * LandContract.EPS_PLATEAU_AMP * pw * sharpP * foldE * amE;
            }
            // 局部尖峰：只在最强挤压处（comp > EPS_SPIKE_LO），并沿走向切成断续块。
            // 这是"高原面之上再叠逆冲片"的机制——质量守恒那条公式封顶在 ~4.2 km。
            if (comp > LandContract.EPS_SPIKE_LO) {
                double sg = (comp - LandContract.EPS_SPIKE_LO) / 0.35;
                if (sg > 1.0) sg = 1.0;
                // 峰的门直接用 ridgeN（与山带纹理同一个网络）——
                // 之前用另一个 500 km 噪声 seg，两个网络不同源 ⟹ 峰是散点不是脊。
                dt += cgE * LandContract.EPS_SPIKE_AMP * sg * ridgeN * sharp * sharp;
            }
            conv = comp;
            orog = dt;
            shearDbg = Math.abs(st[3]);
        } else {
            double[] mt = MT.get();
            tectonic(ws, p0, p1, cs[0], cs[1], db, px, pz, base, mt);
            dt = mt[0]; conv = mt[1]; orog = mt[2]; shearDbg = mt[3];
            double B = LandContract.OROG_BLEND;
            double gap = cs[2].d - cs[1].d;      // 第 2/3 近细胞的间隙：在这条线上换人
            if (gap < B) {
                double s = 0.5 * (1.0 - gap / B);
                plateOf(ws, cs[2], cut, ps[2]);
                tectonic(ws, p0, ps[2], cs[0], cs[2], 0.5 * (cs[2].d - cs[0].d), px, pz, base, mt);
                dt = (1.0 - s) * dt + s * mt[0];
                conv = (1.0 - s) * conv + s * mt[1];
                orog = (1.0 - s) * orog + s * mt[2];
                shearDbg = (1.0 - s) * shearDbg + s * mt[3];
            }
        }

        // ---- 低地 / 盆地：陆壳减薄（长波）----
        // 没有这一项时陆地基准恒为 651 m（T_CONT−T_REF 决定），实测 98.9% 的陆地 >200 m。
        // 只在陆侧生效（乘 su），幅度由长波场控制 ⟹ 平原、盆地、台地才分得开。
        double lv = LandNoise.value(px / LandContract.LOW_WL, pz / LandContract.LOW_WL, ws ^ 0xE7L);
        double low = (LandContract.LOW_HI - lv) / (LandContract.LOW_HI - LandContract.LOW_LO);
        if (low > 1.0) low = 1.0;
        if (low > 0.0) {
            low = low * low * (3.0 - 2.0 * low);
            // 门用 base 而不是 su：海岸处 su 仍有 ~0.85，抑制不住 ⟹ 盆地叠上海岸噪声会撒出小湖。
            // base 在海岸≈0、内陆 8,300 ⟹ 2,000–6,000 m 之间连续淡入，海边完全不受影响。
            double gb = (base - 2_000.0) / 4_000.0;
            if (gb < 0.0) gb = 0.0; else if (gb > 1.0) gb = 1.0;
            gb = gb * gb * (3.0 - 2.0 * gb);
            double sink = LandContract.LOW_AMP * low * su * gb;
            double floor = 1_000.0 - base;          // 保证 base+dt ≥ 1,000 m 厚度（≈150 m 高程）
            if (dt - sink < floor) sink = dt - floor;
            if (sink > 0.0) dt -= sink;
        }

        // ---- 带限细节 ----
        double det = LandContract.DET1 * (LandNoise.value(px / LandContract.DWL1,
                        pz / LandContract.DWL1, ws ^ 0xF1L) - 0.5) * 2.0
                   + LandContract.DET2 * (LandNoise.value(px / LandContract.DWL2 + 3.3,
                        pz / LandContract.DWL2 - 2.7, ws ^ 0xF2L) - 0.5) * 2.0
                   + LandContract.DET3 * (LandNoise.value(px / LandContract.DWL3 + 1.1,
                        pz / LandContract.DWL3 + 4.9, ws ^ 0xF3L) - 0.5) * 2.0
                   + LandContract.DET4 * (LandNoise.value(px / LandContract.DWL4 - 3.5,
                        pz / LandContract.DWL4 + 2.1, ws ^ 0xF4L) - 0.5) * 2.0
                   + LandContract.CRATON1_AMP * (LandNoise.value(px / LandContract.CRATON1_WL,
                        pz / LandContract.CRATON1_WL, ws ^ 0xE5L) - 0.5) * 2.0
                   + LandContract.CRATON2_AMP * (LandNoise.value(px / LandContract.CRATON2_WL + 2.7,
                        pz / LandContract.CRATON2_WL - 3.9, ws ^ 0xE6L) - 0.5) * 2.0;

        // 海岸带噪声（加在厚度上，闸门按 base）
        if (LandContract.COAST_NOISE) {
            double cg = base / LandContract.CN_GATE;
            double g = Math.exp(-cg * cg);
            if (g > 1e-3) {
                double cn =
                      LandContract.CN_AMP1 * (LandNoise.value(px / LandContract.CN_WL1,
                            pz / LandContract.CN_WL1, ws ^ 0x51L) - 0.5) * 2.0
                    + LandContract.CN_AMP2 * (LandNoise.value(px / LandContract.CN_WL2 + 3.1,
                            pz / LandContract.CN_WL2 - 1.7, ws ^ 0x52L) - 0.5) * 2.0
                    + LandContract.CN_AMP3 * (LandNoise.value(px / LandContract.CN_WL3 - 2.3,
                            pz / LandContract.CN_WL3 + 4.1, ws ^ 0x53L) - 0.5) * 2.0;
                det += cn * g;
            }
        }

        // ③ 高段衰减：实测最高峰一带 det 仍有 ±300 m（色标每档 600 m），
        // 于是 3,100 m 的等值线在 60 km 尺度上来回穿越阈值 ⟹ 画面上出现锯齿状"灰/褐硬边"。
        // 地形本身没问题（1.05 m/km），是噪声把色带边界切碎了。这里让高处更平滑。
        // 高原面上尤其是问题：4 km 的平坦台面 + ±300 m 噪声会被阴影放大成一片圆鼓包。
        double hEst = base + dt;
        if (hEst > 2_000.0) {
            det *= 2_000.0 / (2_000.0 + 1.8 * (hEst - 2_000.0));
        }

        double fv = base + dt + det;
        // 低地软地板：softplus 平滑抬起，但**权重必须连续**——
        // 之前写成 `if (su > 0.6)`，跨过这条线时 F 从 −9,703 直接跳到 +1,200（9,885 m 的崖）。
        double wl = (su - 0.55) / 0.25;
        if (wl > 0.0) {
            if (wl > 1.0) wl = 1.0;
            wl = wl * wl * (3.0 - 2.0 * wl);
            double e = fv - 1_200.0;
            double lifted = 1_200.0 + 0.5 * (e + Math.sqrt(e * e + 60_000.0));
            fv += wl * (lifted - fv);
        }
        if (outH != null) {
            double h = LandContract.K_ISO * fv;
            if (fv < 0.0) {
                // 深海热沉降：只作用在洋壳，且在 F=0 处连续归零。
                // ① (1−su) 洋壳比例 ② gate=−F/2000 深海闸门 ③ age 噪声。
                // 缺 ② 时岸线上会留 0–2,000 m 硬台阶（实测 100 m 步长下 h 从 +1 掉到 −1,195）。
                double age = LandNoise.value(px / 8_000_000.0, pz / 8_000_000.0, ws ^ 0xE3L);
                double gate = -fv / 2_000.0;
                if (gate > 1.0) gate = 1.0;
                h -= LandContract.SEA_AGE_SUB * age * (1.0 - su) * gate;
            }
            outH[0] = h;
        }
        if (dbg != null) {
            dbg[0] = tc; dbg[1] = base; dbg[2] = dt; dbg[3] = conv;
            dbg[4] = db; dbg[5] = orog; dbg[6] = det; dbg[7] = fv;
            if (dbg.length > 9) { dbg[8] = conv; dbg[9] = shearDbg; }
            if (dbg.length > 11) { dbg[10] = cs[0].id; dbg[11] = p0.id; }
        }
        return fv;
    }

    /**
     * 一对板块边界（A=c0, B=c1）对 {@code dt} 的贡献。
     *
     * <p>所有分支都做了连续化：极性用 tanh、海沟/岛弧相加、陆/洋按 {@code COAST_GATE} 权重混合。
     *
     * @param out {dt, conv, orog, shear}
     */
    private static void tectonic(long ws, Pl p0, Pl p1, Cell c0, Cell c1, double db,
                                 double px, double pz, double base, double[] out) {
        out[0] = 0.0; out[1] = 0.0; out[2] = 0.0; out[3] = 0.0;
        if (p0.id == p1.id) return;

        // f / W0 按【连续大陆性】加权（硬布尔会让幅度差 2.4×）
        double wA = contW(p0.cc), wB = contW(p1.cc);
        double both = wA * wB;
        double one = wA + wB - 2.0 * wA * wB;
        double none = 1.0 - wA - wB + wA * wB;
        double f = both * LandContract.F_COLLISION + one * LandContract.F_SUBDUCTION
                 + none * LandContract.F_ACCRETION;
        double w0 = both * LandContract.W0_COLLISION + one * LandContract.W0_SUBDUCTION
                  + none * LandContract.W0_ACCRETION;

        double ex = c1.sx - c0.sx, ez = c1.sz - c0.sz;
        double len = Math.sqrt(ex * ex + ez * ez);
        if (len < 1.0) len = 1.0;
        double nx = ex / len, nz = ez / len;
        double dvx = p1.vx - p0.vx, dvz = p1.vz - p0.vz;
        // 夹【矢量】而不是分量：conv 与 shear 必须始终是同一个速度差的两个投影
        double dvLen = Math.sqrt(dvx * dvx + dvz * dvz);
        double lim = LandContract.VEL_REL_MAX;
        if (dvLen > lim) { double s = lim / dvLen; dvx *= s; dvz *= s; }
        double conv = -(dvx * nx + dvz * nz);
        double shear = Math.abs(dvx * (-nz) + dvz * nx);
        double adb = Math.abs(db);
        int type = cls(conv, shear);

        // 陆/洋权重：陆侧 1、洋侧 0，±COAST_GATE/2 内连续过渡
        // ⚠ 硬切换会在海岸线留 2,673 m 高程的崖（实测 dt +4,697 → −12,500）
        double cg = base / LandContract.COAST_GATE + 0.5;
        if (cg < 0.0) cg = 0.0; else if (cg > 1.0) cg = 1.0;
        cg = cg * cg * (3.0 - 2.0 * cg);

        double dt = 0.0;
        double orog = 0.0;
        if (conv > LandContract.CONV_THRESH) {
            double scale = (type == 4) ? LandContract.OBLIQUE_SCALE
                         : (type == 3) ? 0.30 : 1.0;
            if (cg > 0.01) {
                orog = LandContract.OROG_BUDGET
                        ? orogenyBudget(ws, p0, p1, conv, adb, px, pz, f, w0)
                        : LandContract.A_OROG * conv
                          * Math.exp(-(adb / LandContract.S_OROG) * (adb / LandContract.S_OROG));
                orog *= scale;
                dt += cg * orog;
            }
            if (cg < 0.99) {
                // 洋壳一侧：按俯冲极性单侧开沟 + 上覆侧岛弧（极性必须连续，否则沿边界来回翻转）
                double pol = Math.tanh((p1.cc - p0.cc) / LandContract.POL_SMOOTH);
                double wa = 0.5 * (1.0 + pol);
                double sa = subShape(ws, db, conv, scale, px, pz);
                double sb = subShape(ws, -db, conv, scale, px, pz);
                double arc = wa * sa + (1.0 - wa) * sb;
                dt += (1.0 - cg) * arc;
                if (cg < 0.01) orog = arc;
            }
        } else if (conv < -LandContract.CONV_THRESH) {
            // 张裂：陆侧裂谷、洋侧洋脊，同样按 cg 连续过渡
            double qr = adb / LandContract.S_RIFT;
            double qg = adb / LandContract.S_RIDGE;
            double rift = -LandContract.A_RIFT * (-conv) * Math.exp(-qr * qr);
            double ridge = LandContract.A_RIDGE * (-conv) * Math.exp(-qg * qg);
            dt += cg * rift + (1.0 - cg) * ridge;
        } else if (type == 3) {
            double q = adb / LandContract.S_TRANS;
            double amp = LandContract.A_TRANS * Math.exp(-q * q);
            dt += cg * amp + (1.0 - cg) * (-0.6 * amp);
        }

        out[0] = dt; out[1] = conv; out[2] = orog; out[3] = shear;

        // 远场截断：在 OROG_REACH 处严格为 0
        double reach = LandContract.OROG_REACH;
        if (adb >= reach) {
            out[0] = 0.0; out[2] = 0.0;
        } else {
            double edge = 0.70 * reach;
            if (adb > edge) {
                double t = (adb - edge) / (reach - edge);
                double win = 1.0 - t * t * (3.0 - 2.0 * t);
                out[0] *= win;
                out[2] *= win;
            }
        }
    }

    /**
     * 俯冲侧剖面：海沟与岛弧各自一个高斯，<b>两个都算、直接相加</b>（不做 if 分支）。
     *
     * <p>写成 {@code d>=0 → 海沟, 否则 → 岛弧} 时，边界两侧相差
     * {@code A_TRENCH·conv + A_ARC·conv}——实测 20,857 m 厚度（3,163 m 高程）的硬台阶。
     */
    private static double subShape(long ws, double d, double conv, double scale, double px, double pz) {
        double qt = (d - LandContract.TRENCH_OFF) / LandContract.S_TRENCH;
        double qa = (d + LandContract.ARC_OFF) / LandContract.S_ARC;
        double g = (LandNoise.value(px / LandContract.ARC_SEG_WL, pz / LandContract.ARC_SEG_WL, ws ^ 0x8AL)
                    - LandContract.ARC_SEG_LO) / (LandContract.ARC_SEG_HI - LandContract.ARC_SEG_LO);
        if (g < 0.0) g = 0.0; else if (g > 1.0) g = 1.0;
        g = g * g * (3.0 - 2.0 * g);
        return (-LandContract.A_TRENCH * Math.exp(-qt * qt)
                + LandContract.A_ARC * g * Math.exp(-qa * qa)) * conv * scale;
    }

    /** 大陆性权重：cc 相对阈值 CUT_INIT 的连续 soft 化（0=洋壳，1=陆壳）。 */
    private static double contW(double cc) {
        double t = (cc - LandContract.CUT_INIT) / 0.05;
        if (t < 0.0) t = 0.0; else if (t > 1.0) t = 1.0;
        return t * t * (3.0 - 2.0 * t);
    }

    /**
     * 造山累积（质量守恒 + 临界楔 + 远场传递 + 褶皱列车）。
     *
     * <pre>
     *   S_eff = f·v_c·τ      W = W0 + S_eff      σ = W/2
     *   A     = H0·S_eff/(σ·√(2π))                       // 质量守恒，自带饱和
     *   ΔH    = A·act·ridge·(env·fold + sec)             // 包络 × 褶皱列车 + 次级褶皱
     * </pre>
     */
    private static double orogenyBudget(long ws, Pl p0, Pl p1, double conv, double db,
                                        double px, double pz, double f, double w0) {
        // 汇聚年龄 τ 必须是【成对】量（只取 p0 时，最近细胞一换人 τ 跳变，幅度可差 10×）
        double u0 = LandNoise.value(p0.tx / LandContract.TAU_WL, p0.tz / LandContract.TAU_WL, ws ^ 0x71L);
        double u1 = LandNoise.value(p1.tx / LandContract.TAU_WL, p1.tz / LandContract.TAU_WL, ws ^ 0x71L);
        double tau = LandContract.TAU_MIN
                + (LandContract.TAU_MAX - LandContract.TAU_MIN) * 0.5 * (u0 + u1);
        double vc = LandContract.V_SCALE * conv;
        double se = f * vc * tau;
        // 带宽增长封顶：临界楔的"随缩短增宽"是有上限的，不封顶会把峰值摊薄
        double w = w0 + Math.min(se, LandContract.OROG_SE_CAP);
        double sig = 0.5 * w;
        double amp = LandContract.T_CONT * se / (sig * LandContract.SQRT_2PI);
        double sigE = Math.min(sig, LandContract.OROG_SIG_MAX);   // 主带半宽封顶

        double n1 = LandNoise.value(px / LandContract.BELT_WL, pz / LandContract.BELT_WL, ws ^ 0x81L) - 0.5;
        double nA = LandNoise.value(px / (LandContract.BELT_WL * 1.1) - 2.3,
                pz / (LandContract.BELT_WL * 1.1) + 4.1, ws ^ 0x83L);
        double dbW = db + LandContract.BELT_SHIFT * n1 * 2.0;
        double adb = Math.abs(dbW);
        double env = Math.exp(-(adb * adb) / (2.0 * sigE * sigE));
        double act = 0.35 + 0.65 * nA;

        // 褶皱列车：相位由到边界的距离给；噪声用【世界坐标】（用 du/dv 会在细胞边界跳）
        double lam = LandContract.OROG_LAMBDA
                * (1.0 - (1.0 - LandContract.OROG_LAMBDA_MIN) * Math.min(1.0, conv));
        double w1 = LandNoise.value(px / 1_700_000.0 + 5.3, pz / 1_700_000.0 - 2.1, ws ^ 0x86L) - 0.5;
        double w2 = LandNoise.value(px / 700_000.0 - 3.7, pz / 700_000.0 + 1.3, ws ^ 0x87L) - 0.5;
        double ph = dbW / lam + 2.4 * w1 + 1.2 * w2;
        double f1 = 0.5 - 0.5 * Math.cos(Math.PI * 2.0 * ph);
        double f2 = 0.5 - 0.5 * Math.cos(Math.PI * 2.0 * (ph * 1.7 + 0.8 + 1.5 * w2));
        double fold = 1.0 - LandContract.OROG_FOLD * env * (0.65 * f1 + 0.35 * f2);

        // 次级褶皱：主带外缘之外再起一波（"推到顶后前面又起小的"）
        double sec = 0.0;
        double outw = adb - LandContract.OROG_SEC_START * sigE;
        if (outw > 0.0) {
            double dec = Math.exp(-outw / (LandContract.OROG_SEC_DECAY * sigE));
            double ph2 = outw / (lam * LandContract.OROG_SEC_MULT) + 1.9 + 1.5 * w1;
            sec = LandContract.OROG_SEC * dec * (0.5 - 0.5 * Math.cos(Math.PI * 2.0 * ph2));
        }

        double tex = LandNoise.value(px / LandContract.TEX_ACROSS, pz / LandContract.TEX_ALONG, ws ^ 0x84L)
                   + 0.5 * LandNoise.value(px / (LandContract.TEX_ACROSS * 0.5),
                                           pz / (LandContract.TEX_ALONG * 0.5), ws ^ 0x85L);
        double ridge = 1.0 + LandContract.OROG_TEX_W * (tex - 0.75) * 2.0;
        return amp * act * ridge * (env * fold + sec);
    }

    // ================================================================================
    // 板块层
    // ================================================================================

    private static double warpX(double x, double z) {
        return LandContract.PW_A1 * (LandNoise.value(x / LandContract.PW_WL1, z / LandContract.PW_WL1, 0x71L) - 0.5) * 2
             + LandContract.PW_A2 * (LandNoise.value(x / LandContract.PW_WL2 + 3.1, z / LandContract.PW_WL2 - 1.7, 0x72L) - 0.5) * 2
             + LandContract.PW_A3 * (LandNoise.value(x / LandContract.PW_WL3 - 2.3, z / LandContract.PW_WL3 + 4.1, 0x73L) - 0.5) * 2
             + LandContract.PW_A4 * (LandNoise.value(x / LandContract.PW_WL4 + 1.9, z / LandContract.PW_WL4 + 2.7, 0x74L) - 0.5) * 2;
    }

    private static double warpZ(double x, double z) {
        return LandContract.PW_A1 * (LandNoise.value(x / LandContract.PW_WL1 + 7.7, z / LandContract.PW_WL1 - 3.1, 0x75L) - 0.5) * 2
             + LandContract.PW_A2 * (LandNoise.value(x / LandContract.PW_WL2 - 5.3, z / LandContract.PW_WL2 + 1.9, 0x76L) - 0.5) * 2
             + LandContract.PW_A3 * (LandNoise.value(x / LandContract.PW_WL3 + 6.1, z / LandContract.PW_WL3 - 4.7, 0x77L) - 0.5) * 2
             + LandContract.PW_A4 * (LandNoise.value(x / LandContract.PW_WL4 - 3.3, z / LandContract.PW_WL4 + 5.9, 0x78L) - 0.5) * 2;
    }

    /** 最近的五个 Worley 细胞（6,000 km 格距，抖动 0.75）。后几个用于接缝混合与应变核。 */
    private static void cell2(double qx, double qz, Cell[] out) {
        final double L = LandContract.PLATE_CELL;
        final int K = 5;
        for (int m = 0; m < out.length; m++) if (out[m] == null) out[m] = new Cell();
        for (int m = 0; m < K; m++) { out[m].d = Double.MAX_VALUE; out[m].sx = 0; out[m].sz = 0; out[m].id = 0; }
        long ci = (long) Math.floor(qx / L), cj = (long) Math.floor(qz / L);
        long k1 = 0;
        for (int dj = -1; dj <= 1; dj++) {
            for (int di = -1; di <= 1; di++) {
                long i = ci + di, j = cj + dj;
                double sx = (i + 0.5 + LandContract.PLATE_JIT * (LandNoise.h01(0x111L, i, j) - 0.5)) * L;
                double sz = (j + 0.5 + LandContract.PLATE_JIT * (LandNoise.h01(0x222L, i, j) - 0.5)) * L;
                double d = Math.sqrt((qx - sx) * (qx - sx) + (qz - sz) * (qz - sz));
                long k = LandNoise.mix(0x555L ^ (i * 0x2545F4914F6CDD1DL) ^ (j * 0x94D049BB133111EBL));
                for (int m = 0; m < K; m++) {
                    if (d < out[m].d) {
                        for (int q = K - 1; q > m; q--) {
                            out[q].d = out[q - 1].d;
                            out[q].sx = out[q - 1].sx;
                            out[q].sz = out[q - 1].sz;
                            out[q].id = out[q - 1].id;
                        }
                        out[m].d = d; out[m].sx = sx; out[m].sz = sz; out[m].id = k;
                        if (m == 0) k1 = k;
                        break;
                    }
                }
            }
        }
        for (int m = 1; m < K; m++) out[m].id = 0;
        out[0].id = k1;
    }

    /**
     * 巨型核：归属度 g 由"到核边的距离"连续给出（内侧 1、外侧 0），
     * 类型点 = 细胞种子 → 核中心 的连续插值。
     *
     * <p>{@code GIANT_RIM} 默认 1 m（等于关闭软化）：软投票实测只把最差台阶从 986 降到 958，
     * 却让探针相关从 0.640 掉到 0.628、成本 +22%。
     */
    private static long giantAt(double x, double z, double[] seedOut) {
        final double L = LandContract.PLATE_GIANT_L;
        long ci = (long) Math.floor(x / L), cj = (long) Math.floor(z / L);
        double best = Double.MAX_VALUE;
        double sx0 = 0, sz0 = 0;
        long bk = 0;
        for (int dj = -1; dj <= 1; dj++) {
            for (int di = -1; di <= 1; di++) {
                long i = ci + di, j = cj + dj;
                double sx = (i + 0.5 + 0.6 * (LandNoise.h01(0x333L, i, j) - 0.5)) * L;
                double sz = (j + 0.5 + 0.6 * (LandNoise.h01(0x444L, i, j) - 0.5)) * L;
                double d = (x - sx) * (x - sx) + (z - sz) * (z - sz);
                if (d < best) {
                    best = d; sx0 = sx; sz0 = sz;
                    bk = LandNoise.mix(0x777L ^ (i * 0x9E3779B97F4A7C15L) ^ (j * 0xC2B2AE3D27D4EB4FL));
                }
            }
        }
        double rr = LandContract.PLATE_R * (0.88 + 0.24 * ((bk >>> 11) * 0x1.0p-53));
        double dist = Math.sqrt(best);
        double rim = LandContract.GIANT_RIM;
        double g = 0.5 + (rr - dist) / rim;
        if (g < 0.0) g = 0.0; else if (g > 1.0) g = 1.0;
        g = g * g * (3.0 - 2.0 * g);
        if (seedOut != null) {
            seedOut[0] = x + (sx0 - x) * g;
            seedOut[1] = z + (sz0 - z) * g;
            seedOut[2] = g;
        }
        return g > 0.5 ? bk : 0;
    }

    /** 由细胞得到板块属性（id / 类型点 / 厚度 / 速度）。 */
    private static void plateOf(long ws, Cell c, double cut, Pl out) {
        double[] gs = TMP.get();
        long g = giantAt(c.sx, c.sz, gs);
        double tx = gs[0], tz = gs[1];
        long id = (g != 0) ? g : c.id;
        out.id = id; out.tx = tx; out.tz = tz;
        double cc = cellC(ws, tx, tz);
        out.cont = cc > cut;
        out.cc = cc;
        double jit = LandNoise.h01(0xE1L, id, 7L) - 0.5;
        out.t = out.cont ? LandContract.T_CONT + 1200.0 * jit : LandContract.T_OCEAN;

        // 速度 = 无散背景流 ψ 的旋度（散度恒 0）+ 逐板块抖动 + 板片拉力
        double sc = (g != 0) ? LandContract.VEL_GIANT : LandContract.VEL_SMALL;
        double h = LandContract.VEL_FD_STEP;
        double dpsiX = (psi(ws, tx, tz + h) - psi(ws, tx, tz - h)) / (2.0 * h);
        double dpsiZ = (psi(ws, tx + h, tz) - psi(ws, tx - h, tz)) / (2.0 * h);
        out.vx = dpsiX * LandContract.VEL_WL * 0.5 * LandContract.VEL_GAIN * sc
               + LandContract.VEL_JITTER * (LandNoise.h01(0xB2L, id, 3L) - 0.5);
        out.vz = -dpsiZ * LandContract.VEL_WL * 0.5 * LandContract.VEL_GAIN * sc
               + LandContract.VEL_JITTER * (LandNoise.h01(0xC2L, id, 5L) - 0.5);
        // 俯冲汇拉力：**按距离加权求和**，不能用最近的那个。
        // 最近邻是 Voronoi 结构 ⟹ 它换人时速度跳、应变换、地形上留下一条直线
        // （实测：贯穿 4,000 km 的笔直细线，只在最近汇换人处出现）。
        double[] sk = TMP.get();
        sinkPull(tx, tz, sk);
        out.vx += LandContract.VEL_SINK * sk[0];
        out.vz += LandContract.VEL_SINK * sk[1];
    }

    /** 最近的"俯冲汇"位置（写入 out[0..1]）。 */
    private static void nearestSink(double x, double z, double[] out) {
        final double L = LandContract.SINK_L;
        long ci = (long) Math.floor(x / L), cj = (long) Math.floor(z / L);
        double best = Double.MAX_VALUE;
        double bx = 0, bz = 0;
        for (int dj = -1; dj <= 1; dj++) {
            for (int di = -1; di <= 1; di++) {
                long i = ci + di, j = cj + dj;
                double sx = (i + 0.5 + LandContract.SINK_JIT * (LandNoise.h01(0x911L, i, j) - 0.5)) * L;
                double sz = (j + 0.5 + LandContract.SINK_JIT * (LandNoise.h01(0x922L, i, j) - 0.5)) * L;
                double d = (x - sx) * (x - sx) + (z - sz) * (z - sz);
                if (d < best) { best = d; bx = sx; bz = sz; }
            }
        }
        out[0] = bx;
        out[1] = bz;
    }

    /** 俯冲汇的加权拉力方向（C¹）：Σ w·û / Σ w，w = exp(−d²/2σ²)。 */
    private static void sinkPull(double x, double z, double[] out) {
        final double L = LandContract.SINK_L;
        double L2 = 2.0 * (L * 0.55) * (L * 0.55);
        long ci = (long) Math.floor(x / L), cj = (long) Math.floor(z / L);
        double ax = 0, az = 0, W = 0;
        for (int dj = -1; dj <= 1; dj++) {
            for (int di = -1; di <= 1; di++) {
                long i = ci + di, j = cj + dj;
                double sx = (i + 0.5 + LandContract.SINK_JIT * (LandNoise.h01(0x911L, i, j) - 0.5)) * L;
                double sz = (j + 0.5 + LandContract.SINK_JIT * (LandNoise.h01(0x922L, i, j) - 0.5)) * L;
                double dx = sx - x, dz = sz - z;
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d < 1.0) d = 1.0;
                double w = Math.exp(-(d * d) / L2);
                ax += w * dx / d; az += w * dz / d; W += w;
            }
        }
        if (W < 1e-12) { out[0] = 0; out[1] = 0; } else { out[0] = ax / W; out[1] = az / W; }
    }

    // Fill all 9 cells of the 3x3 neighbourhood (unsorted).
    private static void cellGrid(double qx, double qz, Cell[] out) {
        final double L = LandContract.PLATE_CELL;
        long ci = (long) Math.floor(qx / L), cj = (long) Math.floor(qz / L);
        int m = 0;
        for (int dj = -1; dj <= 1; dj++) {
            for (int di = -1; di <= 1; di++) {
                long i = ci + di, j = cj + dj;
                double sx = (i + 0.5 + LandContract.PLATE_JIT * (LandNoise.h01(0x111L, i, j) - 0.5)) * L;
                double sz = (j + 0.5 + LandContract.PLATE_JIT * (LandNoise.h01(0x222L, i, j) - 0.5)) * L;
                double dx = qx - sx, dz = qz - sz;
                out[m].sx = sx; out[m].sz = sz;
                out[m].d = Math.sqrt(dx * dx + dz * dz);
                out[m].id = LandNoise.mix(0x555L ^ (i * 0x2545F4914F6CDD1DL) ^ (j * 0x94D049BB133111EBL));
                m++;
            }
        }
    }

    private static double psi(long ws, double x, double z) {
        return LandNoise.value(x / LandContract.VEL_WL, z / LandContract.VEL_WL, ws ^ 0xB1L) - 0.5;
    }

    /**
     * 大陆性场：<b>克拉通核 + 活动带</b>（不是平滑大团块）。
     *
     * <p>脊化项 {@code 1−|2b−1|} 的等值线是<b>长条</b>而不是圆弧 ⟹ 海岸线成折线、
     * 有自然延伸的半岛与海湾、陆架也自然延伸。实测明显优于各向同性紧凑陆块。
     */
    private static double cellC(long ws, double sx, double sz) {
        if (!LandContract.CELLC_RIDGE) {
            return 0.35 * LandNoise.value(sx / LandContract.C_WL0, sz / LandContract.C_WL0, ws ^ 0xA0L)
                 + 0.65 * LandNoise.value(sx / LandContract.C_WL, sz / LandContract.C_WL, ws ^ 0xA1L);
        }
        double a = LandNoise.value(sx / LandContract.C_WL0, sz / LandContract.C_WL0, ws ^ 0xA0L);
        double b = LandNoise.value(sx / LandContract.C_WL, sz / LandContract.C_WL, ws ^ 0xA1L);
        double c = LandNoise.value(sx / LandContract.C_WL2 + 2.7, sz / LandContract.C_WL2 - 3.9, ws ^ 0xA2L);
        double ridge = 1.0 - Math.abs(2.0 * b - 1.0);
        double ridge2 = 1.0 - Math.abs(2.0 * c - 1.0);
        return 0.55 * a + 0.27 * ridge + 0.18 * ridge2;
    }
}
