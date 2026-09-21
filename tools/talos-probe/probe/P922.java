package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P922 -- §591 判别实验：高纬那个振荡是【5 度周期的折角梳】还是【平滑大尺度】？
//   P921 用 2.5 度步长采样，而折角周期是 5 度 => 恰好半周期 => 【会混叠】。
//   本探针降到 0.25 度，并把 5 度节点的位置标出来。全部是标量函数，秒级。
public class P922 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    public static void main(String[] a) throws Exception {
        PrintStream rep = new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p922_report.txt"),"UTF-8");
        StringBuilder sb = new StringBuilder();
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR/2.0);
        sb.append("P922: eddyMfc 的细扫描（0.25 度），N 标记 = 5 度节点\n\n");
        sb.append("   lat N |  eddyMfc夏(1e-5)  eddyMfc冬(1e-5) | 柱水汽夏  柱水汽冬 | sigma夏    L_d(km)夏 | stormGate夏\n");
        for (double d = 55.0; d <= 80.0001; d += 0.25) {
            double lr = Math.toRadians(d);
            double mS = PrecipField.eddyMfc(lr, thS), mW = PrecipField.eddyMfc(lr, thW);
            double wS = PrecipField.columnWater(lr, thS), wW = PrecipField.columnWater(lr, thW);
            boolean node = Math.abs(d / 5.0 - Math.round(d / 5.0)) < 1e-9;
            sb.append(String.format(LF, "  %6.2f %s | %14.5f %14.5f | %9.4f %9.4f%n",
                d, node ? "N" : " ", mS * 1e5, mW * 1e5, wS, wW));
        }
        sb.append("\n  判读：\n");
        sb.append("   若只在 N 行出现尖峰、非 N 行接近 0  => 折角梳（§425/§432 的机制），修法 = CW_SMOOTH/EDDY_T_SMOOTH。\n");
        sb.append("   若非 N 行也有同量级值、且剖面随纬度平滑起伏  => 不是折角，另有来源（stormGate 或 sigma）。\n");
        sb.append("DONE\n");
        rep.print(sb); rep.flush(); System.out.print(sb);
        System.out.println("JAVA_EXIT=0");
    }
}
