package com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer;

import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.ClimateLatitudes;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.OrographyField;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.PolarZone;
import com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer.PeriodicNoise;
import com.EyeOfHarmonyBuffer.space.talos.chunk.util.WindowKey;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.LandformField;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.MountainLayerV2;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.V2TerrainGen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 世界生成的**几何契约**：唯一真值来源 + 可执行自检。
 *
 * <h3>为什么要有这个类</h3>
 * 本系统原本把"世界的几何拓扑"这件事**分散写在多处的注释里**：X 到底有没有周期、Z 的 1M 循环
 * 到底管什么、各层数组越界时该环绕还是该 clamp —— 每一层各自表述，**没有任何机制能发现它们互相矛盾**。
 * 已经发生过的实证：{@link BarotropicGyre} 用 {@code (y±1+ncz)%ncz} 把 Z 接回环，
 * 而 {@link RelaxedClimate.ClimateGridData} 专门做了"Z 不环绕"的真实 halo（越界 clamp）——
 * 两层对同一拓扑有两套意见，藏了两轮才被探针发现。
 *
 * <h3>契约（数值全部是现有常量的**别名**，本类不新增任何数值）</h3>
 * <ul>
 *   <li>{@link #LAT_CYCLE} —— 纬度循环长度，沿 Z 每 1M 重复一次**气候**。</li>
 *   <li>{@link #X_HAS_PERIOD} = false —— **X 方向无周期**：世界沿 X 无限，越界不回头。</li>
 *   <li>{@link #Z_PERIOD_IS_CLIMATE_ONLY} = true —— **Z 的周期只控制气候**：地形 / 海陆 / 洋流
 *       在 Z 上必须逐块不同，绝不每 1M 复读。</li>
 * </ul>
 *
 * <h3>反例（契约要照出来的那类缺陷）</h3>
 * <b>Z 环绕</b>：{@code BarotropicGyre} 的 Y（=Z 轴）模板用 {@code (y±1+ncz)%ncz}，等于宣称
 * "Z 是环"。这与"Z 只有气候周期"直接冲突，也与 {@code ClimateGridData.idx()} 的 clamp 冲突。
 * 自检的 T4 行就是为此设的 —— 它**当前会失败**，这正是设计意图（先让断言把缺陷照出来）。
 *
 * <h3>第二条轴：跨线程共享的可变状态</h3>
 * T1~T5 断言的是**几何**；T6 断言的是**机制**：
 * <ul>
 *   <li>T6a —— 缓存键必须单射。旧公式把 int 种子截成 24 位、瓦片掩成 20 位，同源拷贝曾有 4 份
 *       （换种子地形不变）。唯一实现现在是 {@link WindowKey#of}。</li>
 *   <li>T6b —— {@link BarotropicGyre} 的步数上限必须是 {@code solve()} 的参数，不得是静态字段。
 *       它曾经由 RelaxedClimate 赋值→调用→清零，而 preheat 线程会在同一时间窗里调用 solve()
 *       ⇒ 同一个 (种子,窗口) 的解取决于**另一个线程当时停在第几轮外层**（非确定性世界内容）。
 *       本行用反射断言字段不存在——这条纪律靠"结构上做不到"守住，不靠注释。</li>
 * </ul>
 *
 * <h3>一个必须区分的细节（否则契约自己会变成新的矛盾源）</h3>
 * 世界 X **无周期**，但气候窗口数组内部的 X **仍然按窗口宽度环绕**：那是 {@code HALO_X} 的
 * padding 设计（窗口两侧各解 100km 的 halo，越界取到对面 halo 即可），**不是**世界周期。
 * T4c/T4d 把这两件事分开断言。
 *
 * <h3>纪律</h3>
 * <ul>
 *   <li>本类**不参与任何生产计算路径**，只被探针（{@code probe.P215} / {@code probe.P216}）调用。</li>
 *   <li>每条断言都**实测**（调生产函数/读生产字段），不读注释、不读复制来的常量。</li>
 *   <li>**不含任何"已知失败"抑制**：{@link #FAILURE_NOTES} 只在对应行**已经失败**时解释原因，
 *       它不改变任何 pass/fail 判据；缺陷修好后该注释自动消失（见 {@link Result#format()}）。</li>
 * </ul>
 */
public final class TalosContract {

    private TalosContract() {
    }

    // ==================================================================
    // 1.1 契约声明（唯一真值来源；全部是现有常量的别名，不新增数值）
    // ==================================================================

    /** 纬度循环长度（blocks）。别名，真值在 {@link ClimateLatitudes#LAT_CYCLE}。 */
    public static final int LAT_CYCLE = ClimateLatitudes.LAT_CYCLE;

    /** **X 方向无周期**（C1 世界契约）。 */
    public static final boolean X_HAS_PERIOD = false;

    /** **Z 的周期只控制气候**：地形/海陆/洋流在 Z 上不重复。 */
    public static final boolean Z_PERIOD_IS_CLIMATE_ONLY = true;

    /** 探针约定种子（与 P168/P169/P176/P195/P200/P209/P214 同一个，便于交叉对照）。 */
    public static final int DEFAULT_SEED = 1022228679;

    // ==================================================================
    // 采样参数（**不是世界常量**，只是自检的取样点；全部由现有常量派生）
    // ==================================================================

    /**
     * T1 的 x 采样列。4 个点全部落在 {@link LandformField} 的**同一个**瓦片内
     * （x &lt; TILE_X = 100_000），于是每个 pass 只需解 1 个 LandformField 瓦片。
     */
    private static final int[] T1_XS = {
        49 * LandformField.CELL,   // 12250
        117 * LandformField.CELL,  // 29250
        185 * LandformField.CELL,  // 46250
        253 * LandformField.CELL,  // 63250
    };

    /** T1 的 z 偏移（相对瓦片起点）。3 个偏移都 &lt; TILE_Z，故仍在该瓦片内。 */
    private static final int[] T1_ZOFF = {
        0,
        28 * LandformField.CELL,   // 7000
        56 * LandformField.CELL,   // 14000
    };

    /** T1 的 z 瓦片起点：取第 5 个瓦片（z ∈ [250k, 300k)），与 P168/P169 的采样纬度一致。 */
    private static final int T1_Z0 = 5 * LandformField.TILE_Z;

    /** T1 采样点数 = 4 x 列 × 3 z 行 = 12。 */
    private static final int T1_N = T1_XS.length * T1_ZOFF.length;

    /** T1 对照行用的波长（**任意**波长都成立：折叠对任何格数都是精确的）——派生自 CELL，避免手抄频率。 */
    private static final double T1B_FREQ = 1.0 / (4.0 * LandformField.CELL);

    /** T1 对照行的噪声种子（取样参数，与生产种子无关）。 */
    private static final long T1B_SEED = 101L;

    /** T1 对照行的判据：折叠口径下的差异必须小到"数值上不可分辨"（blocks）。 */
    private static final double T1B_MAX_FOLD_DELTA = 1e-9;

    /** T2 的纬度周期编号（含负周期）。 */
    private static final int[] T2_CYCLES = { -3, -1, 0, 1, 2, 5 };

    /** T2 在周期内的偏移（全部由 LAT_CYCLE 派生）。 */
    private static final int[] T2_OFFS = {
        0,
        1,
        LAT_CYCLE / 4,
        LAT_CYCLE / 2 - 1,
        LAT_CYCLE / 2,
        3 * LAT_CYCLE / 4,
        LAT_CYCLE - 1,
    };

    /** T3 的检查波长：1/(k·CELL)，k 取 4~400（覆盖从细节到大陆尺度）。 */
    private static final int[] T3_KS = { 4, 8, 16, 40, 100, 400 };

    /** ClimateGridData 探针实例的列数（只为调 idx()，nx 与 Z 规则无关）。 */
    private static final int PROBE_GRID_NX = 4;

    /** 失败行的解释（**只解释、不抑制**；只在对应行实际失败时打印）。 */
    private static final Map<String, String> FAILURE_NOTES;

    static {
        Map<String, String> m = new LinkedHashMap<String, String>();
        // 【已清空】原本登记着 T4a/T4b 两条（WRAP_Y 把 Z 接回环）。2026-09 定案：
        // 开关已删除、yIdx 改为 clamp，两条断言随之转绿 ⇒ 登记表必须清空
        // （留着就是"修好了没删"的过期登记，P215 会为此报错）。
        FAILURE_NOTES = Collections.unmodifiableMap(m);
    }

    // ==================================================================
    // 1.2 结果对象（返回，不抛异常 —— 让探针能打印整张表）
    // ==================================================================

    /** 一行断言：通过/失败 + 期望 + 实测值。 */
    public static final class Row {
        public final String id;
        public final String claim;
        public final boolean pass;
        public final String expected;
        public final String observed;
        /** true = 对照行（"必须为 0 / 必须相同"的反向哨兵，README §3）。 */
        public final boolean control;

        /** true = 信息行：**不计入通过/失败**（只报告事实，供人决策后再立法）。 */
        public final boolean info;

        Row(String id, String claim, boolean pass, String expected, String observed,
            boolean control, boolean info) {
            this.id = id;
            this.claim = claim;
            this.pass = pass;
            this.expected = expected;
            this.observed = observed;
            this.control = control;
            this.info = info;
        }

        /** 单行文本（id 列对齐；中文 claim 不补空格，因为 CJK 是双宽字符）。 */
        public String line() {
            String kind = info ? "INFO" : (control ? "CTRL" : "");
            return String.format("%-5s %-5s %-4s %s%n          期望: %s | 实测: %s%n",
                id, kind, info ? "-" : (pass ? "PASS" : "FAIL"), claim, expected, observed);
        }
    }

    /** 一次自检的完整结果。 */
    public static final class Result {
        public final int seed;
        public final List<Row> rows;

        Result(int seed, List<Row> rows) {
            this.seed = seed;
            this.rows = Collections.unmodifiableList(rows);
        }

        public int failCount() {
            int n = 0;
            for (int i = 0; i < rows.size(); i++) {
                Row r = rows.get(i);
                if (!r.info && !r.pass) n++;
            }
            return n;
        }

        /** 参与判定的断言条数（不含信息行）。 */
        public int assertCount() {
            int n = 0;
            for (int i = 0; i < rows.size(); i++) {
                if (!rows.get(i).info) n++;
            }
            return n;
        }

        public int infoCount() {
            return rows.size() - assertCount();
        }

        public int controlCount() {
            int n = 0;
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).control) n++;
            }
            return n;
        }

        /** 失败行里属于对照行的条数（>0 表示**仪器**坏了，而不是被测对象坏了）。 */
        public int controlFailCount() {
            int n = 0;
            for (int i = 0; i < rows.size(); i++) {
                if (!rows.get(i).info && rows.get(i).control && !rows.get(i).pass) n++;
            }
            return n;
        }

        public List<Row> failures() {
            List<Row> out = new ArrayList<Row>();
            for (int i = 0; i < rows.size(); i++) {
                if (!rows.get(i).info && !rows.get(i).pass) out.add(rows.get(i));
            }
            return out;
        }

        /** 失败行的 id 集合，逗号分隔（按表内顺序）。 */
        public String failedIds() {
            StringBuilder sb = new StringBuilder();
            List<Row> f = failures();
            for (int i = 0; i < f.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(f.get(i).id);
            }
            return sb.toString();
        }

        public boolean ok() {
            return failCount() == 0;
        }

        /** 打印整张表（探针的固定输出格式）。 */
        public String format() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== TalosContract 几何契约自检  seed=").append(seed).append(" ===\n");
            sb.append("契约: LAT_CYCLE=").append(LAT_CYCLE)
              .append("（沿 Z 每 ").append(LAT_CYCLE / 1000).append(" km 重复【气候】）")
              .append("  X_HAS_PERIOD=").append(X_HAS_PERIOD)
              .append("  Z_PERIOD_IS_CLIMATE_ONLY=").append(Z_PERIOD_IS_CLIMATE_ONLY).append('\n');
            for (int i = 0; i < rows.size(); i++) {
                sb.append(rows.get(i).line());
            }
            sb.append("ROWS=").append(assertCount())
              .append(" PASS=").append(assertCount() - failCount())
              .append(" FAIL=").append(failCount())
              .append(" CONTROL_ROWS=").append(controlCount())
              .append(" CONTROL_FAIL=").append(controlFailCount())
              .append(" INFO_ROWS=").append(infoCount()).append('\n');
            sb.append("CONTRACT_STATUS=").append(ok() ? "PASS" : "FAIL").append('\n');
            sb.append("FAILED_ROWS=").append(failedIds()).append('\n');
            // 只对**实际失败**的行打印解释 ⇒ 缺陷修好后注释自动消失，不会漂。
            for (int i = 0; i < rows.size(); i++) {
                Row r = rows.get(i);
                if (r.pass || r.info) continue;
                String note = FAILURE_NOTES.get(r.id);
                if (note != null) sb.append("NOTE[").append(r.id).append("] ").append(note).append('\n');
            }
            sb.append("POLICY=no-suppression（本类不含任何\"已知失败\"抑制；NOTE 只解释，不改判据）\n");
            return sb.toString();
        }
    }

    // ==================================================================
    // 1.2 自检主体
    // ==================================================================

    /** 跑全部断言。**只返回结果，不抛异常**（内部异常会被捕获成一条失败行）。 */
    public static Result selfTest(int seed) {
        List<Row> rows = new ArrayList<Row>();
        try {
            t1_terrainZ(seed, rows);
        } catch (Throwable t) {
            rows.add(new Row("T1*", "T1 取样异常", false, "正常返回", String.valueOf(t), false, false));
        }
        t2_latitudeZ(rows);
        t3_periodicNoise(rows);
        t4_zTopology(rows);
        t5_derived(rows);
        t6_keyAndCap(rows);
        t7_noPrescribedCurrent(rows);
        rows.add(infoRow());
        return new Result(seed, rows);
    }

    /** 命令行入口：打印整张表。非零退出码表示有断言失败。 */
    public static void main(String[] args) {
        int seed = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_SEED;
        Result r = selfTest(seed);
        System.out.println(r.format());
        System.out.println("JAVA_EXIT=" + (r.ok() ? 0 : 1));
        System.exit(r.ok() ? 0 : 1);
    }

    // ---------------- T1：地形在 z 与 z+LAT_CYCLE 必须不同 ----------------

    /**
     * 两趟**有序扫描**（先全扫 z，再全扫 z+LAT_CYCLE），不逐对交替 —— 后者会在瓦片间跳跃，
     * 让工作集超过缓存上限（README §2 的 P209 事故：6000 次采样触发 7165 次求解）。
     *
     * 本 pass 的 12 个点全部落在同一个 LandformField 瓦片内 ⇒ 工作集 = 2 个瓦片
     * （z 与 z+1M 各一个），远小于 CACHE_LIMIT=20。
     *
     * **实测（P216）**：把 {@code PeriodicNoise.INFINITE_Z} 置 false 也**不能**让本行变红 ——
     * 因为地形在 Z 上的变化有第二条**与周期无关**的来源：大陆场走 {@code PeriodicNoise.value2XZ}
     * （该原语根本没有折叠模式）、山层走 {@code SimplexNoise2D}（无周期概念）。
     * 也就是说"Z 不重复地形"目前由两套独立机制同时保证（过定）。
     * ⇒ 本行的可测量性由 T1b（折叠口径下必须相同）与 T1c（同点必须相同）两条对照保证。
     */
    private static void t1_terrainZ(int seed, List<Row> rows) {
        long[] bitsA = new long[T1_N];
        long[] bitsB = new long[T1_N];
        double[] valsA = terrainPass(seed, T1_Z0, bitsA);
        double[] valsB = terrainPass(seed, T1_Z0 + LAT_CYCLE, bitsB);

        int differ = 0;
        double maxAbs = 0.0;
        for (int i = 0; i < T1_N; i++) {
            if (bitsA[i] != bitsB[i]) differ++;
            double d = Math.abs(valsB[i] - valsA[i]);
            if (d > maxAbs) maxAbs = d;
        }
        rows.add(new Row("T1a",
            "地形在 (x,z) 与 (x, z+LAT_CYCLE) 必须【不同】（Z 不重复地形）",
            differ >= 1,
            "differCount >= 1（折叠态下必然 = 0）",
            "differ=" + differ + "/" + T1_N + " max|Δbase|=" + fmt(maxAbs) + " blocks"
                + " （V2TerrainGen.baseAndPlain 实采，两趟有序扫描）",
            false, false));

        // 对照：同样的点，改用【折叠】格数（nz = +round(PERIOD_Z/λ)）→ 必须数值上不可分辨。
        // 这一行证明探针"看得见折叠"，否则 T1a 的"不同"可能只是随便什么都在变。
        int identical = 0;
        double maxFold = 0.0;
        int nx = PeriodicNoise.cellsXFromFreq(T1B_FREQ);
        int nzFold = Math.abs(PeriodicNoise.cellsZFromFreq(T1B_FREQ));
        for (int i = 0; i < T1_XS.length; i++) {
            for (int j = 0; j < T1_ZOFF.length; j++) {
                int x = T1_XS[i];
                int z = T1_Z0 + T1_ZOFF[j];
                double a = PeriodicNoise.gradientFbm2(T1B_SEED, x, z, nx, nzFold, 3);
                double b = PeriodicNoise.gradientFbm2(T1B_SEED, x, z + LAT_CYCLE, nx, nzFold, 3);
                if (Double.doubleToRawLongBits(a) == Double.doubleToRawLongBits(b)) identical++;
                double d = Math.abs(b - a);
                if (d > maxFold) maxFold = d;
            }
        }
        rows.add(new Row("T1b",
            "对照：折叠口径（nz=+round(PERIOD_Z/λ)）下 z 与 z+1M 的噪声必须不可分辨",
            maxFold <= T1B_MAX_FOLD_DELTA,
            "max|Δ| <= " + T1B_MAX_FOLD_DELTA,
            "identical=" + identical + "/" + T1_N + " max|Δ|=" + maxFold
                + " （nx=" + nx + " nzFold=" + nzFold + "）",
            true, false));

        // 对照：同一个 z 再扫一遍必须**逐位相同** ⇒ 证明 T1a 的"不同"来自 Z 位置本身，
        // 而不是采样路径的不确定性（ThreadLocal scratch / 缓存命中顺序 / 浮点累积）。
        long[] bitsC = new long[T1_N];
        terrainPass(seed, T1_Z0, bitsC);
        int samePointDiffer = 0;
        for (int i = 0; i < T1_N; i++) {
            if (bitsA[i] != bitsC[i]) samePointDiffer++;
        }
        rows.add(new Row("T1c",
            "对照：同一个 z 重采样必须逐位相同（否则 T1a 的\"不同\"无意义）",
            samePointDiffer == 0,
            "differCount = 0",
            "differ=" + samePointDiffer + "/" + T1_N + "（z=" + T1_Z0 + " 的第二趟扫描）",
            true, false));
    }

    private static double[] terrainPass(int seed, int zBase, long[] bits) {
        double[] bp = new double[2];
        double[] vals = new double[T1_N];
        int k = 0;
        for (int i = 0; i < T1_XS.length; i++) {
            int x = T1_XS[i];
            for (int j = 0; j < T1_ZOFF.length; j++) {
                int z = zBase + T1_ZOFF[j];
                OrographyField.OroSample o = OrographyField.sample(x, z, seed);
                V2TerrainGen.baseAndPlain(x, z, seed, LandformField.SEA_LEVEL, o, 0.5, 0.5, bp);
                vals[k] = bp[0];                       // out[0] = base（V2TerrainGen 的注释口径）
                bits[k] = Double.doubleToRawLongBits(bp[0]);
                k++;
            }
        }
        return vals;
    }

    // ---------------- T2：纬度在 z 与 z+LAT_CYCLE 必须相同 ----------------

    private static void t2_latitudeZ(List<Row> rows) {
        int pairs = 0, mismatch = 0, halfDiffer = 0, sumOk = 0;
        String firstBad = "";
        for (int ci = 0; ci < T2_CYCLES.length; ci++) {
            for (int oi = 0; oi < T2_OFFS.length; oi++) {
                int z = T2_CYCLES[ci] * LAT_CYCLE + T2_OFFS[oi];
                int z2 = z + LAT_CYCLE;
                pairs++;
                boolean same =
                    ClimateLatitudes.getBelt(z) == ClimateLatitudes.getBelt(z2)
                    && ClimateLatitudes.getDistanceToCenter(z) == ClimateLatitudes.getDistanceToCenter(z2)
                    && ClimateLatitudes.hemisphereSign(z) == ClimateLatitudes.hemisphereSign(z2)
                    && ClimateLatitudes.foldZToCycle(z) == ClimateLatitudes.foldZToCycle(z2)
                    && Double.doubleToRawLongBits(ClimateLatitudes.computeBeltT(z))
                       == Double.doubleToRawLongBits(ClimateLatitudes.computeBeltT(z2));
                if (!same) {
                    mismatch++;
                    if (firstBad.length() == 0) {
                        firstBad = " z=" + z + "(" + ClimateLatitudes.getBelt(z) + "/d="
                            + ClimateLatitudes.getDistanceToCenter(z) + ") vs z+1M("
                            + ClimateLatitudes.getBelt(z2) + "/d="
                            + ClimateLatitudes.getDistanceToCenter(z2) + ")";
                    }
                }
                int d = ClimateLatitudes.getDistanceToCenter(z);
                int dHalf = ClimateLatitudes.getDistanceToCenter(z + LAT_CYCLE / 2);
                if (d != dHalf) halfDiffer++;
                if (d + dHalf == ClimateLatitudes.MAX_D) sumOk++;
            }
        }
        rows.add(new Row("T2a",
            "纬度在 z 与 z+LAT_CYCLE 必须【相同】（belt/d/beltT/半球符号/折叠全部逐位相等）",
            mismatch == 0,
            "mismatch = 0",
            "pairs=" + pairs + " mismatch=" + mismatch + firstBad,
            false, false));
        rows.add(new Row("T2b",
            "对照：z 与 z+LAT_CYCLE/2 的 d 不能恒等（否则 T2a 是恒真）",
            halfDiffer >= 1,
            "differCount >= 1",
            "differ=" + halfDiffer + "/" + pairs + "（另一半 d 必然相等：d(z)+d(z+0.5M)=MAX_D）",
            true, false));
        rows.add(new Row("T2c",
            "纬度折叠自洽：d(z) + d(z+LAT_CYCLE/2) == MAX_D（折叠必须对称）",
            sumOk == pairs,
            "sumOk = pairs = " + pairs,
            "sumOk=" + sumOk + "/" + pairs + " MAX_D=" + ClimateLatitudes.MAX_D,
            false, false));
    }

    // ---------------- T3：PeriodicNoise 两轴都不折叠 ----------------

    private static void t3_periodicNoise(List<Row> rows) {
        boolean flags = PeriodicNoise.INFINITE_X && PeriodicNoise.INFINITE_Z;
        rows.add(new Row("T3a",
            "PeriodicNoise 的两轴开关必须都为 true（两轴都不折叠）",
            flags,
            "INFINITE_X=true 且 INFINITE_Z=true",
            "INFINITE_X=" + PeriodicNoise.INFINITE_X + " INFINITE_Z=" + PeriodicNoise.INFINITE_Z,
            false, false));

        int bad = 0;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < T3_KS.length; i++) {
            double f = 1.0 / (T3_KS[i] * (double) LandformField.CELL);
            int cx = PeriodicNoise.cellsX(f), cz = PeriodicNoise.cellsZ(f);
            int cx2 = PeriodicNoise.cellsXFromFreq(f), cz2 = PeriodicNoise.cellsZFromFreq(f);
            if (cx > 0 || cz > 0 || cx2 > 0 || cz2 > 0) bad++;
            if (i > 0) sb.append(", ");
            sb.append("λ=").append(T3_KS[i] * LandformField.CELL).append("m→Z=").append(cz2);
        }
        rows.add(new Row("T3b",
            "6 个波长的格数必须全部 <= 0（<=0 = 该轴不折叠）",
            bad == 0,
            "越界(>0)数 = 0",
            "bad=" + bad + "/" + T3_KS.length + " [" + sb + "]",
            false, false));
    }

    // ---------------- T4：所有层的 Z 处理必须一致地【不环绕】 ----------------

    private static void t4_zTopology(List<Row> rows) {
        // T4a：Z 周期开关**不得复活**。原先这里断言的是 WRAP_Y 字段的值（当前为 true ⇒ 红）；
        // 定案之后判据升级为"这个字段根本不许存在" —— 与 T6b 守 BarotropicGyre.STEP_CAP 同一套做法。
        boolean noWrapSwitch = true;
        String wrapDetail;
        try {
            BarotropicGyre.class.getDeclaredField("WRAP_Y");
            noWrapSwitch = false;
            wrapDetail = "WRAP_Y 字段仍在（Z 周期开关复活）";
        } catch (NoSuchFieldException expected) {
            wrapDetail = "WRAP_Y 字段不存在";
        }
        rows.add(new Row("T4a",
            "BarotropicGyre 不得再有 Z 周期开关（Z 不环绕已定案为唯一实现）",
            noWrapSwitch,
            "无 WRAP_Y 字段（原先默认 true，把 Z 接回环）",
            wrapDetail + "（定案实测：u/v 3.98→2.05，见 BarotropicGyre.yIdx 注释）",
            false, false));

        int nyA = BarotropicGyre.NCZ;
        int nyB = GlobalCirculation.Z_CYCLE / RelaxedClimate.CELL_Z;
        StringBuilder sb = new StringBuilder();
        int bad = 0;
        int[] nys = { nyA, nyB };
        for (int i = 0; i < nys.length; i++) {
            int ny = nys[i];
            int lo = BarotropicGyre.yIdx(-1, ny);
            int hi = BarotropicGyre.yIdx(ny, ny);
            int inner = BarotropicGyre.yIdx(ny - 1, ny);
            boolean ok = lo == 0 && hi == ny - 1 && inner == ny - 1;
            if (!ok) bad++;
            if (i > 0) sb.append("; ");
            sb.append("ny=").append(ny).append(": yIdx(-1)=").append(lo).append("(要 0) yIdx(ny)=")
              .append(hi).append("(要 ").append(ny - 1).append(") yIdx(ny-1)=").append(inner);
        }
        rows.add(new Row("T4b",
            "BarotropicGyre.yIdx 越界必须 clamp 到 [0, ny-1]（不得环绕）",
            bad == 0,
            "两个 ny 都满足 yIdx(-1)=0 且 yIdx(ny)=ny-1",
            sb.toString(),
            false, false));

        // ClimateGridData：构造一个**极小**的探针实例（nx=4）只为调 idx()；
        // 构造器会覆盖诊断量 LAST_WINDOW_BYTES/N，所以存取还原，避免污染别处的诊断数字。
        long savedBytes = RelaxedClimate.LAST_WINDOW_BYTES;
        int savedN = RelaxedClimate.LAST_WINDOW_N;
        try {
            RelaxedClimate.ClimateGridData g =
                new RelaxedClimate.ClimateGridData(0, 0, 0, PROBE_GRID_NX);
            int ny = g.ny;

            boolean zClamp = g.idx(0, -1) == g.idx(0, 0)
                && g.idx(0, ny) == g.idx(0, ny - 1)
                && g.idx(0, -1000) == g.idx(0, 0)
                && g.idx(0, 10000) == g.idx(0, ny - 1);
            rows.add(new Row("T4c",
                "ClimateGridData.idx 的 Z 越界必须 clamp 到 halo 两端（不得环绕）",
                zClamp,
                "idx(ix,-1)==idx(ix,0) 且 idx(ix,ny)==idx(ix,ny-1)",
                "nx=" + g.nx + " ny=" + ny
                    + " idx(0,-1)=" + g.idx(0, -1) + " idx(0,0)=" + g.idx(0, 0)
                    + " idx(0,ny)=" + g.idx(0, ny) + " idx(0,ny-1)=" + g.idx(0, ny - 1),
                false, false));

            boolean xWrap = g.idx(g.nx, 0) == g.idx(0, 0) && g.idx(-1, 0) == g.idx(g.nx - 1, 0);
            rows.add(new Row("T4d",
                "同处 X 仍按【窗口宽度】环绕（这是 HALO 的 padding，不是世界周期）",
                xWrap,
                "idx(nx,iy)==idx(0,iy) 且 idx(-1,iy)==idx(nx-1,iy)",
                "idx(nx,0)=" + g.idx(g.nx, 0) + " idx(0,0)=" + g.idx(0, 0)
                    + " idx(-1,0)=" + g.idx(-1, 0) + " idx(nx-1,0)=" + g.idx(g.nx - 1, 0),
                false, false));
        } finally {
            RelaxedClimate.LAST_WINDOW_BYTES = savedBytes;
            RelaxedClimate.LAST_WINDOW_N = savedN;
        }
    }

    // ---------------- T5：派生一致性 ----------------

    private static void t5_derived(List<Row> rows) {
        boolean a = LandformField.TILE_X % LandformField.CELL == 0
            && LandformField.NX == LandformField.TILE_X / LandformField.CELL
            && LandformField.NZ == LandformField.TILE_Z / LandformField.CELL
            && LandformField.SX == LandformField.NX + 2
            && LandformField.SZ == LandformField.NZ + 2;
        rows.add(new Row("T5a",
            "LandformField：TILE_X%CELL==0，NX/NZ/SX/SZ 必须由 TILE_* 派生",
            a,
            "TILE_X%CELL=0，NX=TILE_X/CELL，NZ=TILE_Z/CELL，SX=NX+2，SZ=NZ+2",
            "TILE_X=" + LandformField.TILE_X + " CELL=" + LandformField.CELL
                + " NX=" + LandformField.NX + " NZ=" + LandformField.NZ
                + " SX=" + LandformField.SX + " SZ=" + LandformField.SZ,
            false, false));

        boolean b = MountainLayerV2.BELT_CELL_X % MountainLayerV2.CELL == 0
            && MountainLayerV2.NX == MountainLayerV2.BELT_CELL_X / MountainLayerV2.CELL;
        rows.add(new Row("T5b",
            "MountainLayerV2：BELT_CELL_X%CELL==0，NX 必须由 BELT_CELL_X 派生",
            b,
            "BELT_CELL_X%CELL=0，NX=BELT_CELL_X/CELL",
            "BELT_CELL_X=" + MountainLayerV2.BELT_CELL_X + " CELL=" + MountainLayerV2.CELL
                + " NX=" + MountainLayerV2.NX,
            false, false));

        // 已声明的例外（MountainLayerV2 的 NZ 注释 + 探针 P170 的实测证据）：
        // NZ 不是从 BELT_CELL_Z 派生的，而是 carve（Priority-Flood + D8 汇水面积）的**计算域**高度，
        // 域的大小会改变 acc → 下切量 → 山高。所以断言"真实派生式 + 例外处于激活状态"：
        // 谁把 CARVE_DOMAIN_Z 改成 BELT_CELL_Z，本行立刻失败，强制同步注释与证据。
        boolean c = MountainLayerV2.NZ == MountainLayerV2.CARVE_DOMAIN_Z / MountainLayerV2.CELL
            && MountainLayerV2.CARVE_DOMAIN_Z != MountainLayerV2.BELT_CELL_Z;
        rows.add(new Row("T5c",
            "MountainLayerV2.NZ = CARVE_DOMAIN_Z/CELL（**已声明例外**，非 BELT_CELL_Z/CELL）",
            c,
            "NZ==CARVE_DOMAIN_Z/CELL 且 CARVE_DOMAIN_Z!=BELT_CELL_Z",
            "NZ=" + MountainLayerV2.NZ + " CARVE_DOMAIN_Z/CELL="
                + (MountainLayerV2.CARVE_DOMAIN_Z / MountainLayerV2.CELL)
                + " BELT_CELL_Z/CELL=" + (MountainLayerV2.BELT_CELL_Z / MountainLayerV2.CELL),
            false, false));

        long savedBytes = RelaxedClimate.LAST_WINDOW_BYTES;
        int savedN = RelaxedClimate.LAST_WINDOW_N;
        try {
            RelaxedClimate.ClimateGridData g =
                new RelaxedClimate.ClimateGridData(0, 0, 0, PROBE_GRID_NX);
            boolean d = RelaxedClimate.TILE_X % RelaxedClimate.CELL_X == 0
                && (RelaxedClimate.TILE_X + 2 * RelaxedClimate.HALO_X) % RelaxedClimate.CELL_X == 0
                && GlobalCirculation.Z_CYCLE % RelaxedClimate.CELL_Z == 0
                && g.nyLat == GlobalCirculation.Z_CYCLE / RelaxedClimate.CELL_Z
                && g.ny == g.nyLat + 2 * g.haloZ;
            rows.add(new Row("T5d",
                "RelaxedClimate：窗口几何必须整除（TILE_X/CELL_X、Z_CYCLE/CELL_Z），ny=nyLat+2·haloZ",
                d,
                "三项整除 + nyLat=Z_CYCLE/CELL_Z + ny=nyLat+2·haloZ",
                "TILE_X=" + RelaxedClimate.TILE_X + " CELL_X=" + RelaxedClimate.CELL_X
                    + " (+2·HALO_X=" + (RelaxedClimate.TILE_X + 2 * RelaxedClimate.HALO_X) + ")"
                    + " Z_CYCLE=" + GlobalCirculation.Z_CYCLE + " CELL_Z=" + RelaxedClimate.CELL_Z
                    + " nyLat=" + g.nyLat + " haloZ=" + g.haloZ + " ny=" + g.ny,
                false, false));
        } finally {
            RelaxedClimate.LAST_WINDOW_BYTES = savedBytes;
            RelaxedClimate.LAST_WINDOW_N = savedN;
        }

        boolean e = GlobalCirculation.Z_CYCLE == LAT_CYCLE;
        rows.add(new Row("T5e",
            "GlobalCirculation.Z_CYCLE 必须等于契约的 LAT_CYCLE（同一事实不许两处独立取值）",
            e,
            "Z_CYCLE == LAT_CYCLE",
            "Z_CYCLE=" + GlobalCirculation.Z_CYCLE + " LAT_CYCLE=" + LAT_CYCLE,
            false, false));

        int periodZ = (int) PeriodicNoise.PERIOD_Z;
        boolean f = periodZ == LAT_CYCLE;
        rows.add(new Row("T5f",
            "PeriodicNoise.PERIOD_Z 与 LAT_CYCLE 是同一事实的两处独立硬编码，必须相等",
            f,
            "(int)PERIOD_Z == LAT_CYCLE",
            "PERIOD_Z=" + periodZ + " LAT_CYCLE=" + LAT_CYCLE + "（PeriodicNoise 未从 ClimateLatitudes 派生）",
            false, false));
    }

    // ==================================================================
    // T6：缓存键与求解器上限的【结构性】约束
    // ==================================================================

    /**
     * T6 的种子取样。{@code 1} 与 {@code 1 + 2^24} 只差 2^24 —— 旧公式
     * {@code ((long) seed << 40)} 里 int 只有低 24 位能留在 long 中，
     * 所以这一对**必然同键**（"换了种子地形没换"）。这一对是 T6a 反向对照的支点，不是随手挑的。
     */
    private static final int[] T6_SEEDS = {
        0, 1, 1 + (1 << 24), -1, DEFAULT_SEED, Integer.MIN_VALUE, Integer.MAX_VALUE,
    };

    /** T6 的稠密瓦片网格半径（真实定义域远小于此：|x| ≤ 30M 除以最小瓦片 5000 ⇒ |tile| ≤ 6000）。 */
    private static final int T6_DENSE = 40;

    /**
     * T6 的别名取样点。旧公式用 {@code & 0xFFFFF} 掩码瓦片索引，所以
     * {@code -2^20 / 0 / +2^20} 在旧公式下**必然同键**（掩码把高位抹掉），
     * 与 T6_SEEDS 里那一对合起来覆盖"种子被截"和"瓦片被掩"两类信息丢失。
     */
    private static final int[] T6_ALIAS = { -(1 << 20), -1, 0, 1, (1 << 20) };

    /** 旧公式的**对照实现**。只许 T6a 调用；生产代码禁止调用（唯一实现是 {@link WindowKey#of}）。 */
    private static long legacyKey(int seed, int a, int b) {
        return ((long) seed << 40) ^ ((long) (a & 0xFFFFF) << 20) ^ (b & 0xFFFFFL);
    }

    private static void t6_keyAndCap(List<Row> rows) {
        // ---- T6a：三个 int 的全部 32 位都必须参与键 ----
        // 瓦片取值必须**去重**：T6_ALIAS 里的 0/±1 与稠密网格重叠，若直接拼数组，
        // 同一个 (seed,a,b) 会被插两次并被计成"碰撞"（第一次跑就是这样误报 3549 的）。
        TreeSet<Integer> uniq = new TreeSet<Integer>();
        for (int i = -T6_DENSE; i <= T6_DENSE; i++) uniq.add(i);
        for (int i = 0; i < T6_ALIAS.length; i++) uniq.add(T6_ALIAS[i]);
        int[] tiles = new int[uniq.size()];
        int ti = 0;
        for (Integer v : uniq) tiles[ti++] = v;

        HashSet<Long> now = new HashSet<Long>();
        HashSet<Long> old = new HashSet<Long>();
        int total = 0, nowDup = 0, oldDup = 0;
        for (int si = 0; si < T6_SEEDS.length; si++) {
            for (int ai = 0; ai < tiles.length; ai++) {
                for (int bi = 0; bi < tiles.length; bi++) {
                    total++;
                    if (!now.add(WindowKey.of(T6_SEEDS[si], tiles[ai], tiles[bi]))) nowDup++;
                    if (!old.add(legacyKey(T6_SEEDS[si], tiles[ai], tiles[bi]))) oldDup++;
                }
            }
        }
        rows.add(new Row("T6a",
            "缓存键 (种子,瓦片A,瓦片B)→long 必须【单射】（三个 int 的全部 32 位都参与）",
            nowDup == 0 && oldDup > 0,
            "WindowKey.of 碰撞=0 且 旧公式碰撞>0（后者是反向对照：证明这一行能红）",
            "样本 " + total + " 个键（" + T6_SEEDS.length + " 种子 × "
                + tiles.length + "×" + tiles.length + " 瓦片）：WindowKey.of 碰撞=" + nowDup
                + "；旧公式 ((long)seed<<40)^((a&0xFFFFF)<<20)^(b&0xFFFFF) 碰撞=" + oldDup
                + "（旧式把 int 种子截成 24 位、瓦片掩成 20 位）",
            false, false));

        // ---- T6b：步数上限必须是显式参数，不得是跨线程共享的可变静态 ----
        boolean fieldGone = true;
        String fieldDetail;
        try {
            BarotropicGyre.class.getDeclaredField("STEP_CAP");
            fieldGone = false;
            fieldDetail = "STEP_CAP 字段仍在（缺陷复发）";
        } catch (NoSuchFieldException expected) {
            fieldDetail = "STEP_CAP 字段不存在";
        }
        int solveCount = 0;
        boolean capParam = false;
        java.lang.reflect.Method[] ms = BarotropicGyre.class.getDeclaredMethods();
        for (int i = 0; i < ms.length; i++) {
            if (!ms[i].getName().equals("solve")
                || !java.lang.reflect.Modifier.isStatic(ms[i].getModifiers())) {
                continue;
            }
            solveCount++;
            Class<?>[] p = ms[i].getParameterTypes();
            if (p.length == 12 && p[11] == int.class
                && java.lang.reflect.Modifier.isPublic(ms[i].getModifiers())) {
                capParam = true;
            }
        }
        rows.add(new Row("T6b",
            "BarotropicGyre：步数上限必须是 solve() 的显式参数，不得是跨线程共享的可变静态",
            fieldGone && capParam,
            "无 STEP_CAP 字段 且 存在 public static solve(..., int stepCap)",
            fieldDetail + "；public static solve 重载数=" + solveCount
                + "（含 12 参带 stepCap=" + capParam + "）"
                + "（旧写法由 RelaxedClimate 赋值→调用→清零，preheat 线程会串改前台的值）",
            false, false));
    }

    // ---------------- T7：极地不得再有规定洋流 ----------------

    /**
     * T7：极地系统不得再暴露任何"规定洋流"的 API。
     *
     * 2026-09 最终定案：**极地对洋流的影响 = 0**。环极急流与沿岸环流都已删除，
     * 极地洋流必须**完全由 {@code BarotropicGyre} 解出**。删除理由：那两条流是"画"上去的，
     * 而且实测墙内求解器只能给出 0.0006 m/s（δ_M ≈ 64km ≈ 海盆宽度 ⇒ 整盆都在摩擦层里），
     * 规定流实际上是在**掩盖**这个事实，而不是解决它。
     *
     * 作法与 T4a / T6b 同一套：不信"注释说删了"，而是**反射查成员**。
     * 配一条对照行：同一个扫描必须看得见确实还在的成员（{@code isWallCell}），否则 T7 是空断言。
     */
    private static void t7_noPrescribedCurrent(List<Row> rows) {
        java.lang.reflect.Method[] ms = PolarZone.class.getDeclaredMethods();
        java.lang.reflect.Field[] fs = PolarZone.class.getDeclaredFields();
        StringBuilder pub = new StringBuilder();
        int scanned = 0, hit = 0;
        StringBuilder hitDetail = new StringBuilder();
        for (java.lang.reflect.Method m : ms) {
            scanned++;
            if (java.lang.reflect.Modifier.isPublic(m.getModifiers())
                && java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                pub.append(m.getName()).append(' ');
            }
            String nm = m.getName().toLowerCase();
            if (nm.contains("jet") || nm.contains("coastcurrent")) {
                hit++;
                hitDetail.append("方法 ").append(m.getName()).append(' ');
            }
        }
        for (java.lang.reflect.Field f : fs) {
            scanned++;
            String nm = f.getName().toLowerCase();
            if (nm.startsWith("jet") || nm.startsWith("coast_current")) {
                hit++;
                hitDetail.append("字段 ").append(f.getName()).append(' ');
            }
        }
        rows.add(new Row("T7",
            "极地系统不得再暴露规定洋流（环极急流 / 沿岸环流已删除；极地洋流必须完全由求解器算出）",
            hit == 0,
            "0 个 jet / coastCurrent 成员",
            hit == 0 ? ("扫描 " + scanned + " 个成员，0 命中") : ("命中 " + hit + " 个：" + hitDetail),
            false, false));

        boolean sawWall = false;
        for (java.lang.reflect.Method m : ms) {
            if (m.getName().equals("isWallCell")) {
                sawWall = true;
            }
        }
        rows.add(new Row("T7*",
            "T7 对照：同一个扫描必须看得见仍然存在的成员（否则 T7 是空断言）",
            sawWall, "找到 isWallCell", sawWall ? "找到" : "**没找到 ⇒ T7 无效**",
            true, false));
        rows.add(new Row("I2", "PolarZone 的公开静态方法面（信息行：便于发现新冒出来的旋钮）",
            true, "-", pub.length() > 0 ? pub.toString().trim() : "(none)", false, true));
    }

    /**
     * 信息行：**不计入通过/失败**（用户口径：先看清，不先立法）。
     *
     * {@code PeriodicNoise.PERIOD_X} 与 {@code RelaxedClimate.TILE_X} / {@code LandformField.TILE_X}
     * 数值相同，是同一数值的第三处独立表达。但 INFINITE_X=true 之后它实际是"波长量化基"
     * （有效波长 = PERIOD_X/n），不再是世界周期 ⇒ 写成断言等于宣称一个可能已不成立的语义。
     */
    private static Row infoRow() {
        return new Row("I1", "PERIOD_X 与 TILE_X 是同一数值的第三处独立表达（信息行：先看清，不先立法）", true, "-",
            "PERIOD_X=" + (long) PeriodicNoise.PERIOD_X
                + " == RelaxedClimate.TILE_X=" + RelaxedClimate.TILE_X
                + " == LandformField.TILE_X=" + LandformField.TILE_X
                + "（INFINITE_X=" + PeriodicNoise.INFINITE_X + " ⇒ 它是波长量化基，非世界周期）", false, true);
    }

    private static String fmt(double v) {
        return String.format("%.6f", v);
    }
}
