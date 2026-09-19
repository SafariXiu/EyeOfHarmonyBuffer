package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

/**
 * P551 —— **丙方案的可行性实测**：逆风轨迹会走到哪里？
 *
 * <p>设计意图（若可行则实现）：把 {@code q} 从「当地温度诊断」改成**逆风轨迹积分的水汽收支** ——
 * 沿风向往上游走 N 步，每一步按 {@code 海洋 = 蒸发源 / 陆地 = 无源} 与 {@code 降水消耗} 更新 q。
 *
 * <p><b>为什么它天然能分开亚洲与撒哈拉</b>：JJA 亚洲是西南季风（向岸，上风是温暖的印度洋）；
 * 撒哈拉是东北信风（**离岸**，上风就是撒哈拉自己 ⇒ 上游根本没有水）。
 *
 * <p><b>但这一切取决于模型自己的风场对不对</b> —— 所以本探针先只回答三个问题：
 * <ol>
 *   <li>从每个框逆风走 1500 km，**有多少比例的点会落到海上**？</li>
 *   <li>落到的那个海点的**温度**是多少（决定源比湿）？</li>
 *   <li>模型现在的 {@code q} 与「按轨迹算出来的 q」差多少？</li>
 * </ol>
 * 若撒哈拉「落到海上」的比例**显著低**于亚洲 ⇒ 方案可行 ⇒ 再实现。
 * 若两者比例相近 ⇒ 方案同样无效 ⇒ 应当停止并回报。
 */
public class P551 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P551] " + s); System.out.println("[P551] " + s); }

    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static final int SEED = 1022228679;
    static final int NSTEP = 10;
    static final double STEP = 150_000.0;      // 与 PrecipField.UPWIND_STEP 同量级

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p551_report.txt"), "UTF-8");
        say("P551：丙方案可行性 —— 逆风 1500 km 会走到哪里？（地球掩膜驱动）");
        say("  契约自证：Z_CYCLE=" + WorldContract.Z_CYCLE + "  RH_SEA=" + PrecipField.RH_SEA
            + "  UPWIND_STEP=" + PrecipField.UPWIND_STEP + "  NSTEP=" + NSTEP + "  步长=" + (int) (STEP / 1000) + " km");
        P546.loadMask();
        PlateField.MASK = new PlateField.LandMask() {
            public boolean isLand(int x, int z) { return P546.earthIsLand(x, z); }
        };
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

        say("");
        say("=== 逆风轨迹（JJA）===");
        say("  框                     起点陆占比  落到海上的比例  首达海平均步数(=距离/150km)  起点q    轨迹源q(RH_SEA*qSat(T_sea))  比");
        for (Object[] bx : boxes) {
            double lo = (double) bx[0], hi = (double) bx[1];
            double la = (double) bx[2], hb = (double) bx[3];
            long n = 0, nLand0 = 0, nReachSea = 0; double sumStep = 0;
            double sumQ = 0, sumQsrc = 0;
            for (double latd = la + 2.5; latd <= hb; latd += 2.5) {
                int z = WorldContract.zOfLat(latd);
                for (int c = 0; c < 72; c++) {
                    double lon = (c + 0.5) * 360.0 / 72;
                    if (lon < lo || lon > hi) continue;
                    int x = (int) Math.round((c + 0.5) * CIRC / 72);
                    boolean isLand = P546.earthIsLand(x, z);
                    if (isLand) nLand0++;
                    // 当前 q（与生产同口径）
                    double[] dg = new double[8];
                    P546.decompose(x, z, sd, cell, thS, dg);
                    sumQ += dg[1];
                    // 逆风轨迹
                    int cx = x, cz = z; int found = -1; double tSea = 0;
                    for (int s = 1; s <= NSTEP; s++) {
                        double[] w = Atmosphere.windAt(cx, cz, sd, cell, thS, GRAD);
                        double sp = Math.hypot(w[0], w[1]);
                        if (sp < 0.05) break;
                        int nx2 = cx - (int) Math.round(w[0] / sp * STEP);
                        int nz2 = cz - (int) Math.round(w[1] / sp * STEP);
                        nx2 = (int) (((long) nx2 % (long) CIRC + (long) CIRC) % (long) CIRC);
                        nz2 = (int) (((long) nz2 % WorldContract.Z_CYCLE + WorldContract.Z_CYCLE) % WorldContract.Z_CYCLE);
                        cx = nx2; cz = nz2;
                        if (!P546.earthIsLand(cx, cz)) { found = s; tSea = Atmosphere.surfaceTemp(cx, cz, sd, cell, thS); break; }
                    }
                    if (found > 0) { nReachSea++; sumStep += found; sumQsrc += PrecipField.RH_SEA * PrecipField.qSat(tSea); }
                    else { sumQsrc += PrecipField.RH_SEA * PrecipField.qSat(Atmosphere.surfaceTemp(x, z, sd, cell, thS)); }
                    n++;
                }
            }
            double landPct = 100.0 * nLand0 / n;
            double reachPct = 100.0 * nReachSea / n;
            double avgStep = nReachSea > 0 ? (double) sumStep / nReachSea : -1;
            double q = sumQ / n, qs = sumQsrc / n;
            say(String.format(LF, "  %-20s %8.1f%% %13.1f%% %22.1f %12.5f %20.5f %8.2f",
                bx[4], landPct, reachPct, avgStep, q, qs, qs > 1e-9 ? q / qs : -1));
        }
        say("");
        say("  判据：撒哈拉的「落到海上的比例」应显著低于亚洲 —— 那才说明「离岸风 ⇒ 上游无水」这件事");
        say("        在模型自己的风场里**已经成立**，丙方案才有意义。");
        say("  若两者比例相近 ⇒ 模型的风场没有这个经向差异 ⇒ 丙方案同样无效，必须先修风场。");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
