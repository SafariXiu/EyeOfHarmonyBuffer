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
    private static final double KIND_PK_TOP = 0.035;
    /** 山+峰 = dev 最小的陆地份额（峰先取走，其余为山）。 */
    private static final double KIND_MTN_TOP = 0.30;
    /** 高原 = 剩余（非山地）陆地里海拔最高者的份额 → 低地+丘陵 ≈ 1-0.30-0.17 ≈ 53%。 */
    private static final double KIND_PLATEAU_TARGET = 0.17;
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
    private static final double SHORE_START = 800.0;
    private static final double SHORE_FULL = 3500.0;
    // ---- 标定 ----
    private static final int CALIBRATE_STRIDE = 8000;

    /** 每种子标定结果缓存。 */
    private static final ConcurrentHashMap<Integer, Cutoffs> CUTOFF_CACHE =
        new ConcurrentHashMap<Integer, Cutoffs>();

    /** 分位阈值组（dev 升序 + 条件海拔，避免 0 值并列塌陷）。 */
    private static final class Cutoffs {
        final double devQ95;       // relief 归一化标尺（展示量用）
        final double devPeakQ;     // 峰：dev <= devPeakQ（且海拔达标）
        final double devMtnQ;      // 山：dev <= devMtnQ
        final double plateauElevQ; // 高原：非山地中 elevation >= plateauElevQ

        Cutoffs(double devQ95, double devPeakQ, double devMtnQ, double plateauElevQ) {
            this.devQ95 = devQ95;
            this.devPeakQ = devPeakQ;
            this.devMtnQ = devMtnQ;
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

        OroSample(boolean isLand, double elevation01, double relief01,
                  double beltMask01, int kind, double residual) {
            this.isLand = isLand;
            this.elevation01 = elevation01;
            this.relief01 = relief01;
            this.beltMask01 = beltMask01;
            this.kind = kind;
            this.residual = residual;
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

    /** 单点采样（世界 block 坐标，任意范围）。 */
    public static OroSample sample(int x, int z, int worldSeedInt) {
        double r = NoiseContinentGrid.landResidual(x, z, worldSeedInt);
        if (r < 0.0) {
            return new OroSample(false, 0.0, 0.0, 0.0, KIND_LOWLAND, 0.0);
        }
        Cutoffs c = cutoffsFor(worldSeedInt);
        double elevation = elevation01(r, worldSeedInt);
        double dev = ridgeDev(x, z, worldSeedInt);
        double relief = reliefFromDev(x, z, worldSeedInt, dev);   // 含贴岸淡化，与 relief01() 同口径

        int kind;
        if (dev <= c.devPeakQ && elevation >= PEAK_MIN_ELEV) {
            kind = KIND_PEAK;
        } else if (dev <= c.devMtnQ) {
            kind = KIND_MOUNTAIN;
        } else if (elevation >= c.plateauElevQ) {
            kind = KIND_PLATEAU;
        } else if (relief >= KIND_HILL_RELIEF || elevation >= KIND_HILL_ELEV) {
            kind = KIND_HILL;
        } else {
            kind = KIND_LOWLAND;
        }

        return new OroSample(true, elevation, relief, smoothstep(0.30, 0.65, relief), kind, r);
    }

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
        double devQ95 = cutoffsFor(worldSeedInt).devQ95;
        double micro = (devQ95 > 0.0) ? clamp01(1.0 - dev / devQ95) : 0.0;
        double d = NoiseContinentGrid.coastDistBlocks(x, z, worldSeedInt);   // 陆上 <0
        double shoreFade = clamp01((-d - SHORE_START) / (SHORE_FULL - SHORE_START));
        return micro * shoreFade;
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
        int nx = 400_000 / CALIBRATE_STRIDE;
        int nz = 200_000 / CALIBRATE_STRIDE;
        int max = nx * nz;
        double[] devs = new double[max];
        double[] elevs = new double[max];
        int m = 0;
        for (int z = 0; z < 200_000; z += CALIBRATE_STRIDE) {
            for (int x = 0; x < 400_000; x += CALIBRATE_STRIDE) {
                double r = NoiseContinentGrid.landResidual(x, z, worldSeedInt);
                if (r < 0.0) {
                    continue;
                }
                devs[m] = ridgeDev(x, z, worldSeedInt);
                elevs[m] = elevation01(r, worldSeedInt);
                m++;
            }
        }
        if (m == 0) {
            return new Cutoffs(1.0, 0.0, 0.0, 1.0);
        }
        double[] dev = Arrays.copyOf(devs, m);
        Arrays.sort(dev);
        double devQ95 = quantile(dev, 0.95);
        double devPeakQ = quantile(dev, KIND_PK_TOP);
        double devMtnQ = quantile(dev, KIND_MTN_TOP);

        // 高原阈值：非山地（dev > devMtnQ）中的海拔分位，使高原占全部陆地 KIND_PLATEAU_TARGET
        double remain = 1.0 - KIND_MTN_TOP;   // 非山地占陆地比（≈0.70）
        double need = KIND_PLATEAU_TARGET / remain;
        double[] es = new double[m];
        int e = 0;
        for (int i = 0; i < m; i++) {
            if (devs[i] > devMtnQ) {
                es[e++] = elevs[i];
            }
        }
        Arrays.sort(es, 0, e);   // quantile 需要升序！
        double plateauElevQ = (e > 0) ? quantile(Arrays.copyOf(es, e), 1.0 - need) : 1.0;
        return new Cutoffs(devQ95, devPeakQ, devMtnQ, plateauElevQ);
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
