package probe;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.PrintStream;
import java.util.Locale;

/**
 * §535 ★★★★★★★ **盒定义的单一来源 + §476 海陆声明**。
 *
 * <p>为什么要有它（审计面 2 的两条产出）：
 * <ol>
 *   <li><b>§476 纪律</b>（设计冻结 :34197）：「所有基于『地球经纬度盒子』的判据都必须先声明
 *       该盒子在模型世界里是不是陆地」。实测 81 个盒探针里 <b>64 个完全未声明</b>。</li>
 *   <li><b>盒采样口径不统一</b>：同名盒子在不同探针里是不同点集 —— 纬度步长 2.5/5.0、
 *       经度 5.0/10.0、起点有/无 +half 偏移、上端 `&lt;=` 与 `&lt;` 三种包含性并存。</li>
 * </ol>
 *
 * <p><b>本类只提供【口径与声明】，不改变任何现有探针的采样</b> —— 那属 P1-6，要单独做并重测。
 *
 * <p>⚠ <b>格心口径（P1-6 的目标约定，本类已按它实现）</b>：
 * <pre>
 *   格心 lon = lonLo + (i + 0.5) * dLon,  i = 0..n-1,  且【lon &lt; lonHi】（上端开区间）
 *   格心 lat 同理。n = floor((hi - lo) / d)
 *   ⇒ 这样盒边两侧各留半格，【不会】出现旧式「lo+half 起、末点落在盒顶边」造成的整体北偏。
 * </pre>
 */
public final class Boxes {

    private Boxes() {}

    /** 世界周向跨度（m），= 40,000 km（与 {@link Zonal} 同一约定）。 */
    public static final double CIRC = 40_000_000.0;

    public static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }

    /** 格心采样：纬度格心数（上端开区间）。 */
    public static int nLat(double latLo, double latHi, double dLat) {
        return (int) Math.floor((latHi - latLo) / dLat);
    }

    /** 格心采样：经度格心数（上端开区间）。 */
    public static int nLon(double lonLo, double lonHi, double dLon) {
        return (int) Math.floor((lonHi - lonLo) / dLon);
    }

    /** 盒内的模型陆地占比（0..1），按【格心】口径采样。 */
    public static double landFrac(long seed, int cell,
                                  double lonLo, double lonHi, double latLo, double latHi,
                                  double dLon, double dLat) {
        int nl = nLon(lonLo, lonHi, dLon), nb = nLat(latLo, latHi, dLat);
        if (nl <= 0 || nb <= 0) return Double.NaN;
        int land = 0, tot = 0;
        for (int i = 0; i < nl; i++) {
            double lon = lonLo + (i + 0.5) * dLon;
            for (int j = 0; j < nb; j++) {
                double lat = latLo + (j + 0.5) * dLat;
                if (PlateField.isLandWithCell(xOfLon(lon), WorldContract.zOfLat(lat), seed, cell)) land++;
                tot++;
            }
        }
        return tot == 0 ? Double.NaN : (double) land / tot;
    }

    /**
     * 打印 §476 声明行。**所有基于盒子的判据都必须先调用它。**
     *
     * @param tag 盒子名（如 "ASIA 70-120E/15-35N"）
     */
    public static double declare(PrintStream rep, String tag, long seed, int cell,
                                 double lonLo, double lonHi, double latLo, double latHi,
                                 double dLon, double dLat) {
        double lf = landFrac(seed, cell, lonLo, lonHi, latLo, latHi, dLon, dLat);
        int tot = nLon(lonLo, lonHi, dLon) * nLat(latLo, latHi, dLat);
        rep.println(String.format(Locale.ROOT,
            "[BOX] §476 声明 %-26s 模型陆占比 = %.3f  (格心口径 %d 点, dLon=%.1f dLat=%.1f)",
            tag, lf, tot, dLon, dLat));
        rep.flush();
        return lf;
    }
}
