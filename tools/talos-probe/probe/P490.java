package probe;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P490：**本世界的纬向平均海陆到底对不对称？** —— 决定 ④(b)「给 T_zm 加奇次项」该不该做。
 *
 * <p>物理前提：{@code latOf} 是**帐篷函数** ⇒ |lat| 对 z = MAX_D 对称，即
 * {@code z} 与 {@code Z_CYCLE - z} 是**同一纬度**的南北两个位置。
 * 而 {@code PlateField} 在 z 上**非周期**（契约：跨极点后是新大陆、新海盆）。
 *
 * <p>所以问题变成一句可测的话：**在「同一纬度」的南北两个 z 上，
 * 纬向平均的陆地占比是否相等？** 若相等 ⇒ 本世界的（纬向平均）地理是南北对称的
 * ⇒ 纬向平均温度场**必须是 |lat| 的偶函数** ⇒ 加奇次项就是把**地球**的
 * 「南极大陆 vs 北冰洋」不对称**搬进一个没有这种不对称的世界**。
 *
 * <p>做法：对每个纬度 φ，取 z(+) 与其镜像 z(-) = Z_CYCLE - z(+)，
 * 在**同一批 x** 上数陆地占比，比较两者之差，并与二项分布的标准误比较。
 */
public class P490 {

    static final int SEED = 1022228679;
    static final long SD = SimTerrain.seedOf(SEED);
    static final int CELL = PlateField.PLATE_CELL;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    static void say(String s) { rep.println("[P490] " + s); rep.flush(); System.out.println("[P490] " + s); System.out.flush(); }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p490_report.txt"), "UTF-8");
        say("P490：纬向平均海陆的南北对称性检验（决定 ④(b) 该不该做）");
        say(String.format(LF, "  Z_CYCLE=%d  MAX_D=%d  PLATE_CELL=%d km", WorldContract.Z_CYCLE, WorldContract.MAX_D, CELL / 1000));
        say("");

        // ---- 0) 自检：z 与 Z_CYCLE - z 的 |lat| 必须相等 ----
        double maxd = 0;
        for (int z = 0; z <= WorldContract.Z_CYCLE; z += 250_000) {
            double a = Math.abs(Math.toDegrees(WorldContract.latOf(z)));
            double b = Math.abs(Math.toDegrees(WorldContract.latOf(WorldContract.Z_CYCLE - z)));
            maxd = Math.max(maxd, Math.abs(a - b));
        }
        say(String.format(LF, "0. 自检：|lat(z)| vs |lat(Z_CYCLE - z)| 的最大差 = %.3e 度（应 ~0）", maxd));
        say("");

        // ---- 1) 逐纬度比较南北陆地占比 ----
        final int NX = 200;            // x 采样数
        final int XSPAN = 20_000_000;  // 采样跨度 20,000 km
        say(String.format(LF, "1. 每个纬度取 %d 个 x（跨度 %d km），数陆地占比", NX, XSPAN / 1000));
        say(String.format(LF, "   %8s %12s %12s %10s %10s %8s", "lat", "z(北)", "z(南)", "北陆%", "南陆%", "差"));
        double sumAbs = 0, sumSe = 0; int nLat = 0, nSig = 0;
        for (int i = 1; i <= 30; i++) {
            double lat = i * 3.0;                                   // 3..90 度
            int zN = WorldContract.zOfLat(lat);
            int zS = WorldContract.Z_CYCLE - zN;
            if (zN <= 0 || zN >= WorldContract.MAX_D) continue;
            int nLandN = 0, nLandS = 0;
            for (int k = 0; k < NX; k++) {
                int x = (int) ((long) k * XSPAN / NX);
                if (PlateField.isLandWithCell(x, zN, SD, CELL)) nLandN++;
                if (PlateField.isLandWithCell(x, zS, SD, CELL)) nLandS++;
            }
            double pN = nLandN / (double) NX, pS = nLandS / (double) NX;
            double pBar = (pN + pS) / 2.0;
            double se = Math.sqrt(Math.max(1e-12, pBar * (1 - pBar) * 2.0 / NX));   // 两样本比例之差的标准误
            double d = pN - pS;
            sumAbs += Math.abs(d); sumSe += se; nLat++;
            boolean sig = Math.abs(d) > 2.0 * se;
            if (sig) nSig++;
            say(String.format(LF, "   %8.1f %12d %12d %9.1f%% %9.1f%% %+7.1f pt%s",
                lat, zN, zS, 100 * pN, 100 * pS, 100 * d, sig ? "  <== 超 2SE" : ""));
        }
        say("");
        say(String.format(LF, "   ⇒ %d 个纬度里，南北差超过 2 倍标准误的只有 **%d** 个", nLat, nSig));
        say(String.format(LF, "   ⇒ 平均 |南北差| = %.1f 个百分点；平均 2SE = %.1f 个百分点", 100 * sumAbs / nLat, 100 * sumSe / nLat));
        say("");
        say("   ★ 判读（跑之前写死）：");
        say("     · 若「超 2SE」的纬度 <= 10%（约 3 个）且平均差与 2SE 同量级 ⇒ **南北对称**");
        say("       ⇒ 纬向平均温度必须是 |lat| 的**偶函数** ⇒ **④(b) 的奇次项必须取消**（它会把地球的不对称搬进来）；");
        say("     · 若显著不对称 ⇒ 奇次项才有物理依据，④(b) 可以按原计划做。");
        say("");
        say("   ⚠ 口径：这是**纬向**（沿 x）平均的对称性。逐点的南北当然不同（PlateField 在 z 上非周期），");
        say("     但**纬向平均**才是 T_zm 这个量所描述的对象。");
        rep.flush();
        rep.close();
        System.out.println("JAVA_EXIT=0");
    }
}
