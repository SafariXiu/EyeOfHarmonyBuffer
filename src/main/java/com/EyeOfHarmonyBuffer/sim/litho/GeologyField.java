package com.EyeOfHarmonyBuffer.sim.litho;

/**
 * ★ 2026-10-17（地质子系统，第一步）：**地质省**的粗层划分。
 *
 * <p>本类只做一件事：把一个世界坐标映射到 {@link Province}。它<strong>不改变任何现有行为</strong>——
 * 目前<strong>没有任何生产者调用</strong>它（{@link #GEOLOGY_ON} 默认 false），
 * 将来由「消费者 A（土壤质地）」按 km 级取用。
 *
 * <p><b>划分依据</b>（全部来自实测，探针 NPROVINCE，2026-10-17，1271 个陆地点）：
 * <pre>
 *   elev 百分位：5%=27.1  25%=58.2  50%=98.9  75%=184.4  95%=1006.5  max=3998.2 m
 *   |coastDist|  ：5%=1.4  25%=6.9  50%=15.2  75%=30.9  95%=90 km
 *   0~200 m 占 77.4%；200~600 占 14.0%；600~1200 占 4.8%；>1200 占 3.8%
 *   landFraction 全域只在 0.383~0.648 ⟹ <b>无法当判别量，已弃用</b>
 * </pre>
 *
 * <p>⚠ <b>阈值是【实测标定】，不是文献值</b>：200/600/1200 m 与 15 km 取自上面的百分位，
 * 只在「能区分开、且数量级合理」这一点上有依据；若将来换世界或换种子，需要重新出数。
 *
 * <p>⚠ <b>已知局限</b>：本世界陆地极度偏斜（77% 在 0~200 m），所以低平原会占绝对多数；
 * 若要更均衡的划分，应在低平原内部再细分（未做）。
 */
public final class GeologyField {

    private GeologyField() {}

    /** ★ 总开关。默认 <b>false</b> ⟹ 本类目前不影响任何生产读数。 */
    public static boolean GEOLOGY_ON = false;

    /** 粗层地质省（顺序即由低到高）。 */
    public enum Province {
        /** 滨海低地：高程 < 200 m 且离岸 < 15 km。 */
        COASTAL_LOWLAND,
        /** 内陆低平原：高程 < 200 m 且离岸 >= 15 km（本世界约 77% 的陆地主体）。 */
        INLAND_PLAIN,
        /** 丘陵/台地：200 ~ 600 m。 */
        HILL,
        /** 山地：600 ~ 1200 m。 */
        MOUNTAIN,
        /** 高山：>= 1200 m（约 3.8%）。 */
        HIGHLAND
    }

    /** 划界阈值（实测标定，见类注释）。 */
    public static final double E_HILL_M   = 200.0;
    public static final double E_MOUNT_M  = 600.0;
    public static final double E_HIGH_M   = 1200.0;
    public static final double COAST_M    = 15_000.0;
    private static final int    COAST_SEARCH = 6_000_000;

    // ===== 性能统计（只读，供"改动前后耗时对比"用；不影响判定）=====
    public static long PROVINCE_CALLS = 0;
    public static long PROVINCE_NANOS = 0;
    public static void resetPerf() { PROVINCE_CALLS = 0; PROVINCE_NANOS = 0; }

    /** 纯函数：世界坐标 -> 地质省。吃 seed，可复现。 */
    public static Province provinceAt(int x, int z, long seed, int cell) {
        long t0 = System.nanoTime();
        try {
        double e = PlateField.elevationWithCell(x, z, seed, cell);
        if (e >= E_HIGH_M)  return Province.HIGHLAND;
        if (e >= E_MOUNT_M) return Province.MOUNTAIN;
        if (e >= E_HILL_M)  return Province.HILL;
        double cd = Math.abs(PlateField.coastDistanceNew(x, z, seed, cell, COAST_SEARCH));
        return cd < COAST_M ? Province.COASTAL_LOWLAND : Province.INLAND_PLAIN;
        } finally { PROVINCE_CALLS++; PROVINCE_NANOS += (System.nanoTime() - t0); }
    }

    /** 配置指纹：阈值或开关一改，瓦片缓存必须失效。 */
    public static long configStamp() {
        long h = 0x9E3779B97F4A7C15L;
        h = h * 31 + (GEOLOGY_ON ? 1 : 0);
        h = h * 31 + Double.doubleToLongBits(E_HILL_M);
        h = h * 31 + Double.doubleToLongBits(E_MOUNT_M);
        h = h * 31 + Double.doubleToLongBits(E_HIGH_M);
        h = h * 31 + Double.doubleToLongBits(COAST_M);
        return h;
    }

    // ==================== 消费者 A：质地类（2026-10-17） ====================

    /**
     * FAO 官方**只有 3 类**质地（SMW/HWSD）。
     *
     * <p>⚠ 取证硬更正（build/h_lit/TEXTURE_COAST_ANCHOR_FORENSICS.md §9.2）：
     * 「FAO 七类质地」<b>不存在</b>。7 类里含 medium-fine / very-fine 的那份出自 SOTWIS 转引 CEC 1985，
     * 不是 FAO 原创；organic 也不是质地类（FAO GSD 2006 p.43 明说有机层无法定质地类）。
     * ⟹ 本类只用 <b>3 类</b>。
     */
    public enum TextureClass {
        /** coarse：clay<18% 且 sand>65%（HWSD 2009 p.7）。 */
        COARSE,
        /** medium：clay<35% 且 sand<65%。 */
        MEDIUM,
        /** fine：clay>35%。 */
        FINE
    }

    /**
     * 每类的 θcap / θpwp / θres（体积含水率）。
     *
     * <p>数值逐字取自 IFS Doc Cy47r3 Part IV §8.2.3 <b>Table 8.9</b>（印刷页 153；
     * Cy38r1 Table 8.8 印刷页 128 同值）：
     * <pre>
     *   Coarse 0.244 / 0.059 / 0.025
     *   Medium 0.347 / 0.151 / 0.010
     *   Fine   0.448 / 0.279 / 0.010
     * </pre>
     * <p>⚠ 这三行是 Table 8.9 里与 FAO 三类同名的行；<b>不是</b>我按比例编的。
     */
    public static double thetaCap(TextureClass t) {
        return t == TextureClass.COARSE ? 0.244 : (t == TextureClass.MEDIUM ? 0.347 : 0.448);
    }
    public static double thetaPwp(TextureClass t) {
        return t == TextureClass.COARSE ? 0.059 : (t == TextureClass.MEDIUM ? 0.151 : 0.279);
    }
    public static double thetaRes(TextureClass t) {
        return t == TextureClass.COARSE ? 0.025 : 0.010;
    }

    /**
     * ★★ <b>标定映射（不是文献结论）</b>：省 → 质地类。
     *
     * <p>依据只有一条是文献支持的方向，且取证判为<b>弱先验</b>：
     * 「滨海砂」（WRB p.150 说滨海砂只是小部分、最大砂体在大陆内部 ⟹ 方向可能反）。
     * ⟹ 本映射<b>明确是标定</b>，且<b>可被其他场覆盖</b>（调用方应先问别的场）。
     * <pre>
     *   COASTAL_LOWLAND -> COARSE   （弱先验：滨海砂；可被覆盖）
     *   INLAND_PLAIN    -> MEDIUM   （主体）
     *   HILL            -> MEDIUM
     *   MOUNTAIN        -> MEDIUM
     *   HIGHLAND        -> COARSE   （标定：高山多为薄土/岩屑；无文献）
     * </pre>
     */
    public static TextureClass textureClass(Province p) {
        if (p == Province.COASTAL_LOWLAND) return TextureClass.COARSE;
        if (p == Province.HIGHLAND)       return TextureClass.COARSE;
        return TextureClass.MEDIUM;
    }

    /** 便捷：坐标 -> 质地类。 */
    public static TextureClass textureClassAt(int x, int z, long seed, int cell) {
        return textureClass(provinceAt(x, z, seed, cell));
    }
}