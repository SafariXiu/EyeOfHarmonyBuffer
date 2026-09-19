package com.EyeOfHarmonyBuffer.sim.world;

/**
 * 世界契约（决定 1 = B）的**唯一真相来源**。
 *
 * <pre>
 *   X      无限、无周期
 *   Z      无限
 *   气候   在 Z 上以 Z_CYCLE 周期重复
 *   地形   在 Z 上不重复
 * </pre>
 *
 * <p>纬度用**帐篷函数**：z=0 是赤道，z=MAX_D 是极点，z=Z_CYCLE 又回到赤道。
 * ⚠ 极点在 z=MAX_D 处是 N/S 焊死的折返点 —— 这就是裁决项 D1（极点分离）要改的东西。
 */
public final class WorldContract {

    private WorldContract() {}

    /**
     * <b>极到赤道的弧长</b>（block；1 格 = 1 米锁死）—— **本文件的基本尺度**。
     *
     * <p>R_EFF、beta、风带宽度、沿岸上升流层全部由它派生，**与气候在 z 上的周期无关**。
     * <p>2026-09-18 口径解耦（D1 第一步）：原来是 {@code Z_CYCLE / 2} 的派生量，
     * 现在独立成常量，**值不变（10,000,000）⇒ 行为逐位不变**。
     */
    public static final int MAX_D = 10_000_000;

    /**
     * 气候在 z 上的周期（block）。
     *
     * <p><b>裁决：1,000,000 -> 20,000,000</b>（设计冻结 §29）。理由：
     * <ul>
     *   <li>极到赤道 = MAX_D = 10,000 km ⇒ 行星半径 6,366 km，**与地球 6,371 km 差 0.1%**；</li>
     *   <li>东边界沿岸上升流层 T_E = tau*L_along/(2*rho*f) **正比于 L_along** ⇒ x20；</li>
     *   <li>风带宽度/L_R 从 0.20 升到 4.07 ⇒ **斜压不稳定（风暴轴）从「不存在」变成「与地球同级」**；</li>
     *   <li>**西边界流完全不受影响**：curl ∝ 1/MAX_D 且 beta ∝ 1/MAX_D ⇒ curl/beta 不变。</li>
     * </ul>
     * 唯一代价是玩家从赤道走到极点要 10,000 km（1 格 ≈ 1 米是锁死的）—— 这是玩法选择。
     *
     * <p>⚠ <b>2026-09-18（D1 第一步）</b>：本常量现在是 {@code 2 * MAX_D}，值仍是 20,000,000，
     * **逐位不变**。D1（极点分离）落地时本行改成 <b>{@code 4 * MAX_D}</b>（= 40,000,000），
     * 届时极点在 {@code Z_CYCLE / 4}、赤道在 {@code 0} 与 {@code 2 * MAX_D}。见下方 D1 暂存段。
     */
    public static final int Z_CYCLE = 4 * MAX_D;   // ★ D1（2026-09-18）：一条完整子午圈
    public static final double OMEGA = 7.2921e-5;
    /** 有效行星半径（m）：R_eff = MAX_D/(pi/2) = 6366 km —— 与地球 6371 km 差 0.1%。 */
    public static final double R_EFF = MAX_D / (Math.PI / 2.0);
    /** 一年多少「天」（季节相位的时间单位，纯约定）。 */
    public static final double DAYS_PER_YEAR = 365.25;

    /**
     * ★ D1（2026-09-18）：**带符号**纬度（弧度）—— 沿子午圈匀速行走的**线性三角波**。
     * <p>z=0 赤道 → MAX_D 北极(+90°) → 2·MAX_D 赤道 → 3·MAX_D 南极(-90°) → 4·MAX_D 赤道。
     * <p>1 格 = 1 米不变（每 MAX_D 格走 90 度）。**全程连续且周期** ⇒ sin(lat) 连续 ⇒ f 不再跳号。
     */
    /**
     * ★ §527 新增：**z 的周期回卷 —— 单一来源**。
     *
     * <p>为什么要有它：契约下气候在 Z 上以 {@code zCycle} 周期重复，所以「把 z 弄回一个周期内」
     * 只能【回卷】，不能【夹逼】。历史上有两处用了 {@code clamp(z, 0, MAX_D)}，
     * 那只保留北半球：南半球点（z 约 3*MAX_D）会被夹到 **MAX_D = 北极**，
     * 而不是回到它自己的纬度。见 {@code PrecipField.upwindSea} / {@code moistureAdvected}。
     *
     * <p>整数版与 {@link #latOf} 内部【逐字一致】；连续版供沿风逆推这类浮点游走使用。
     */
    public static int wrapZ(int z, int zCycle) {
        return ((z % zCycle) + zCycle) % zCycle;
    }

    /** 连续 z 的周期回卷（与 {@link #wrapZ(int,int)} 同口径）。 */
    public static double wrapZ(double z, int zCycle) {
        double w = z % zCycle;
        return w < 0.0 ? w + zCycle : w;
    }

    public static double latOf(int z, int zCycle) {
        int quarter = zCycle / 4;
        int u = wrapZ(z, zCycle);
        double q = (double) u / quarter;
        double t;
        if (q < 1.0) t = q;
        else if (q < 3.0) t = 2.0 - q;
        else t = q - 4.0;
        return t * Math.PI / 2.0;
    }

    /** bandD = |纬度| / 90°：0 在**两条赤道**（z=0 与 2·MAX_D）、1 在**两个极点**。 */
    public static double bandD(int z, int zCycle) {
        return Math.abs(latOf(z, zCycle)) / (Math.PI / 2.0);
    }

    /** 半球符号（D1 后由纬度决定；两条赤道上取 +1）。 */
    public static double hemisphereSign(int z, int zCycle) {
        return latOf(z, zCycle) >= 0 ? 1.0 : -1.0;
    }

    /**
     * beta(phi) = 2*Omega*cos(phi)/R。
     * <p>★ D1：整条子午圈 = 4·MAX_D = 2πR ⇒ **R = zCycle/(2π)** ⇒ 系数是 {@code 2π/zCycle}。
     * <p>⚠ 改 Z_CYCLE 时必须同时改这一行；漏改会把 beta 砍半，直接歪掉西边界流。
     * **绝不要再用常数 3.24e-10（那是 45 度的值）。**
     */
    public static double betaForLatitude(double latRad, int zCycle) {
        return 2.0 * OMEGA * (2.0 * Math.PI) / zCycle * Math.cos(latRad);
    }

    /**
     * 纬度（度）→ z（block）。**探针不许再手写 {@code lat/90 * Z_CYCLE/2}**（D1 后会静默错位）。
     * <p>北纬走第一支 [0, MAX_D]，南纬走第三支 [2·MAX_D, 3·MAX_D]。
     */
    public static int zOfLat(double latDeg) {
        double b = Math.abs(latDeg) / 90.0 * MAX_D;
        return latDeg >= 0 ? (int) Math.round(b) : (int) Math.round(2.0 * MAX_D + b);
    }

    /** 科氏参数 f = 2*Omega*sin(phi)。 */
    public static double coriolis(double latRad) {
        return 2.0 * OMEGA * Math.sin(latRad);
    }

    // ==================================================================================
    // ★★ D1（极点分离）—— **已于 2026-09-18 落地**；下面只剩命名别名与自检
    //
    // 用户裁决（2026-09-18）：「分离，并且继续保持行星尺度」。
    //
    // 几何：球面上一条子午圈本身就**是一个圆**，周长 40,000 km（北极→赤道→南极→赤道→北极）。
    //   所以要让 z 忠实等于一条子午圈，周期必须是 4 * MAX_D。
    //   现行的 2 * MAX_D 只够走「赤道→北极」+「南极→赤道」各 10,000 km，
    //   中间那 20,000 km 的「北极→南极」被压成了 0 ⇒ 极点在 z = MAX_D 处**焊死**，
    //   于是 sin(纬度) 在那里从 +1 硬跳到 -1 ⇒ 科氏参数 f 从 +2Ω 瞬间翻到 -2Ω
    //   （OceanField 逐行取 f 做环流，那里就是非物理的）。
    //
    // 为什么「相位平移」不算：把不连续点从 z=MAX_D 搬到 z=0 只是换位置。
    //   圆柱的 z 是一个圆，纬度要覆盖 -90…+90 ⇒ **必然有一对纬度被等同**。
    //   要让它落在「两条赤道」上，就需要 40,000 km。
    //   ⇒ **只有 4 * MAX_D 能让纬度全程连续且周期，从而让 sin(lat) 连续。**
    //
    // 落地清单（D1 第二步）：
    //   1) Z_CYCLE = 4 * MAX_D
    //   2) latOf -> latOfD1，bandD -> bandDD1，hemisphereSign 退役
    //   3) betaForLatitude 的 (pi / zCycle) -> (2 * pi / zCycle)   ← 两行必须一起改
    //   4) 审计所有把 Z_CYCLE / 2 当「极点」用的地方（会静默错位）
    //   R_EFF 与全部 MAX_D 派生式**一个字都不用改**（这正是先解耦 MAX_D 的目的）。
    // ==================================================================================

    /** D1 的周期：一条完整子午圈 = 4 ×（极到赤道）。 */
    public static final int Z_CYCLE_D1 = Z_CYCLE;   // 已落地；保留为别名

    /**
     * D1 的纬度（弧度）：沿子午圈**匀速**行走的线性三角波。
     * <p>z=0 赤道 → MAX_D 北极(+90°) → 2·MAX_D 赤道 → 3·MAX_D 南极(-90°) → 4·MAX_D 赤道。
     * <p>1 格 = 1 米不变（每 MAX_D 格走 90 度）。
     */
    public static double latOfD1(int z, int zCycle) { return latOf(z, zCycle); }

    /** D1 的 bandD = |纬度| / 90°：0 在两条赤道、1 在两个极点。 */
    public static double bandDD1(int z, int zCycle) { return bandD(z, zCycle); }

    /** D1 的科氏参数 f = 2Ω·sin(纬度) —— 公式不变，但纬度连续 ⇒ f 连续。 */
    public static double coriolisD1(int z, int zCycle) { return coriolis(latOf(z, zCycle)); }

    /**
     * D1 的 beta：整条子午圈 = 2πR ⇒ R = zCycle / (2π)，故 beta = 2Ω·cos(φ)/R。
     * <p>⚠ 现行版本是 {@code 2Ω·π/zCycle}（因为现行 R = zCycle/π）。
     * **这两行必须与 Z_CYCLE 一起改；改错直接歪掉西边界流。**
     */
    public static double betaForLatitudeD1(double latRad, int zCycle) { return betaForLatitude(latRad, zCycle); }

    /** D1 自检：逐条打印结论。**任何一条 FAIL 都不许落地。** */
    public static String d1SelfCheck() {
        java.util.Locale L = java.util.Locale.ROOT;
        StringBuilder b = new StringBuilder();
        int zc = Z_CYCLE_D1;
        b.append(String.format(L, "  Z_CYCLE_D1 = %d  ( = 4 x MAX_D )%n", zc));
        double rD1 = zc / (2.0 * Math.PI);
        b.append(String.format(L, "  R_EFF(现行) = %.3f km    R = Z_CYCLE_D1/(2*pi) = %.3f km    差 = %.3f km%n",
            R_EFF / 1000.0, rD1 / 1000.0, Math.abs(R_EFF - rD1) / 1000.0));
        boolean ok = true;
        int[] zs = {0, MAX_D, 2 * MAX_D, 3 * MAX_D, 4 * MAX_D};
        int[] ex = {0, 90, 0, -90, 0};
        for (int i = 0; i < zs.length; i++) {
            double got = Math.toDegrees(latOfD1(zs[i], zc));
            boolean e = Math.abs(got - ex[i]) < 1e-9;
            ok = ok && e;
            b.append(String.format(L, "  lat(z=%9d) = %+9.5f deg    期望 %+4d    %s%n", zs[i], got, ex[i], e ? "OK" : "**FAIL**"));
        }
        double mx = 0; int at = 0;
        for (int z = -zc; z <= 2 * zc; z += 997) {
            double d = Math.abs(Math.sin(latOfD1(z + 1, zc)) - Math.sin(latOfD1(z, zc)));
            if (d > mx) { mx = d; at = z; }
        }
        b.append(String.format(L, "  D1   |delta sin(lat)| 最大单格步进 = %.8f (z=%d)   %s%n", mx, at, mx < 1e-3 ? "连续 OK" : "**有跳变 FAIL**"));
        ok = ok && mx < 1e-3;
        double jump = Math.abs(Math.sin(latOf(MAX_D - 1)) - Math.sin(latOf(MAX_D + 1)));
        b.append(String.format(L, "  D1 落地后 |delta sin(lat)| 跨 z=MAX_D(+-1 格) = %.6f   (应 ~0)%n", jump));
        ok = ok && jump < 1e-3;
        b.append(String.format(L, "  总判定 = %s%n", ok ? "PASS" : "**FAIL**"));
        return b.toString();
    }

    // ---- 默认契约的便捷重载 ----
    public static double bandD(int z) { return bandD(z, Z_CYCLE); }
    public static double latOf(int z) { return latOf(z, Z_CYCLE); }
    public static double betaForLatitude(double latRad) { return betaForLatitude(latRad, Z_CYCLE); }
}
