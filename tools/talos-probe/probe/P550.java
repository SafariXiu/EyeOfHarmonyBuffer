package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

/**
 * P550 —— **A1 第一步：标定 PLATEAU_AMP**（高原「把热量加到中层大气」的显式参数化）。
 *
 * <p>为什么是它：模型没有任何别的机制能把「亚洲季风区」与「同纬度的撒哈拉」分开 ——
 * {@code W_ZM(shifted)} 两者完全相同（都是纬度减 10 度）、{@code kappa} 0.832 vs 0.795 几乎一样、
 * {@code cellPressure ∝ (KAPPA_MEAN - kappa)} 也一样。唯一的经向差异是**地形**：
 * 青藏 5000 m，撒哈拉 300 m ⇒ {@code PLATEAU_AMP > 1} 只强化亚洲。
 *
 * <p>接线已核实（{@code Atmosphere:667-672}）：{@code PLATEAU_AMP=1.0} 时
 * {@code GAMMA*h} 与 {@code tSfc} 里的 {@code -GAMMA*elev*k} 精确抵消 ⇒ 零影响。
 *
 * <p>本探针**不改任何 src**，只在扫描里改 {@code Atmosphere.PLATEAU_AMP}（public static，已在 configStamp 里）。
 * 分解复用 {@link P546#decompose}（同包）⇒ 口径与 P546/P548/P549 逐位一致。
 */
public class P550 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static final File TSV = new File(ROOT, "build/eoh_probe/refs/earth_monsoon_boxes.tsv");
    static PrintStream rep;
    static void say(String s) { rep.println("[P550] " + s); System.out.println("[P550] " + s); }

    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static final int SEED = 1022228679;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p550_report.txt"), "UTF-8");
        say("P550：A1 第一步 —— 标定 PLATEAU_AMP（高原显式参数化）");
        say("  契约自证：Z_CYCLE=" + WorldContract.Z_CYCLE + "  gain=" + PrecipField.EDDY_PHYS_GAIN
            + "  RH_SEA=" + PrecipField.RH_SEA + "  初始 PLATEAU_AMP=" + Atmosphere.PLATEAU_AMP);
        P546.loadMask();
        PlateField.MASK = new PlateField.LandMask() {
            public boolean isLand(int x, int z) { return P546.earthIsLand(x, z); }
        };
        // 地球参照（脚本落盘，M14）
        double[] eJ = new double[8];
        String[] bnE = new String[8];
        int nb = 0;
        for (String ln : Files.readAllLines(TSV.toPath(), StandardCharsets.UTF_8)) {
            if (ln.startsWith("#") || ln.trim().isEmpty()) continue;
            String[] q = ln.split("\t");
            if (nb >= 8) break;
            bnE[nb] = q[0]; eJ[nb] = Double.parseDouble(q[5]); nb++;
        }
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0);

        Object[][] boxes = {
            {70.0, 120.0, 15.0, 35.0, "亚洲季风区 70-120E"},
            {0.0, 30.0, 20.0, 35.0, "撒哈拉 0-30E"},
            {250.0, 285.0, 25.0, 35.0, "美国南部 110-75W"},
            {150.0, 210.0, 25.0, 35.0, "北太平洋 150E-150W"},
            {300.0, 350.0, 25.0, 35.0, "北大西洋 60-10W"},
        };
        double[] amps = {1.00, 1.05, 1.10, 1.15, 1.25};
        double[][] res = new double[amps.length][boxes.length];
        double[][] r1 = new double[amps.length][boxes.length];

        for (int ai = 0; ai < amps.length; ai++) {
            Atmosphere.PLATEAU_AMP = amps[ai];
            for (int bi = 0; bi < boxes.length; bi++) {
                double lo = (double) boxes[bi][0], hi = (double) boxes[bi][1];
                double la = (double) boxes[bi][2], hb = (double) boxes[bi][3];
                double s1 = 0, sf = 0; long n = 0;
                for (double latd = la + 2.5; latd <= hb; latd += 2.5) {
                    int z = (int) Math.round(latd / 90.0 * WorldContract.MAX_D);
                    for (int c = 0; c < 72; c++) {
                        double lon = (c + 0.5) * 360.0 / 72;
                        if (lon < lo || lon > hi) continue;
                        int x = (int) Math.round((c + 0.5) * CIRC / 72);
                        double[] r = P546.decompose(x, z, sd, cell, thS, null);
                        s1 += r[0]; sf += r[4]; n++;
                    }
                }
                res[ai][bi] = sf / n; r1[ai][bi] = s1 / n;
            }
        }
        Atmosphere.PLATEAU_AMP = 1.0;

        say("");
        say("=== JJA 各框降水 vs PLATEAU_AMP（mm/day；括号是 term1 单独）===");
        say("  框                       地球     " + String.join("      ", java.util.Arrays.stream(amps).mapToObj(a -> String.format(LF, "amp=%.2f", a)).toArray(String[]::new)));
        for (int bi = 0; bi < boxes.length; bi++) {
            StringBuilder sb = new StringBuilder(String.format(LF, "  %-22s %6.3f   ", boxes[bi][4], eJ[bi]));
            for (int ai = 0; ai < amps.length; ai++)
                sb.append(String.format(LF, "%6.3f(%.3f)  ", res[ai][bi], r1[ai][bi]));
            say(sb.toString());
        }
        say("");
        say("=== 相对地球的比值 ===");
        for (int bi = 0; bi < boxes.length; bi++) {
            StringBuilder sb = new StringBuilder(String.format(LF, "  %-22s          ", boxes[bi][4]));
            for (int ai = 0; ai < amps.length; ai++)
                sb.append(String.format(LF, "%6.2fx        ", eJ[bi] > 0 ? res[ai][bi] / eJ[bi] : -1));
            say(sb.toString());
        }
        say("");
        say("  判据：亚洲应**上升**（现 0.37x），撒哈拉应**基本不动**（现 38.2x，地形只有 300 m）。");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
