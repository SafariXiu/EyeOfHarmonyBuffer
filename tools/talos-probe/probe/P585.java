package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.StationaryWave;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

// P585 -- latOf 与 zOfLat 的往返恒等性 + divAt 与求解器网格是否同一行。
// P584（行号口径）与 P582（latOf/zOfLat 口径）给出相反符号 => 两个口径必有一个错。
public class P585 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P585] " + s); System.out.println("[P585] " + s); }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p585_report.txt"), "UTF-8");
        say("P585: latOf / zOfLat 往返恒等性");

        say("");
        say("  [A] latOf(zOfLat(lat)) == lat ?");
        int bad = 0;
        for (int lat = -90; lat <= 90; lat += 5) {
            int z = WorldContract.zOfLat(lat);
            // ★ §532 修复(P1-8 之一)：latOf 返回【弧度】，而 lat 是【度】——
            //   原版直接相减 ⇒ 假警报「不恒等 36/37」。往返其实是成立的。
            double back = Math.toDegrees(WorldContract.latOf(z));
            // ★ §532：容差 1e-6【太紧】—— zOfLat 返回 int，往返必然带 ~1e-6 度的量化误差。
            //   改为 1e-4（仍足以抓住任何真正的口径错，例如度/弧度会差到 57 倍）。
            boolean ok = Math.abs(back - lat) < 1e-4;
            if (!ok) bad++;
            if (!ok || lat % 30 == 0)
                say(String.format(LF, "    lat=%+4d  z=%9d  z/MAX_D=%.4f  latOf(z)=%+12.6f  %s",
                    lat, z, z / (double) WorldContract.MAX_D, back, ok ? "ok" : "<<< 不恒等"));
        }
        say(String.format(LF, "    => 不恒等的纬度点数 = %d/37", bad));

        say("");
        say("  [B] zOfLat(latOf(z)) == z ?（取 z 扫过整个周期）");
        say("      ★ §532 判读：契约的纬度是【三角波】(0->MAX_D 北->2MAX_D 赤->3MAX_D 南->4MAX_D)");
        say("        ⇒ zOfLat 是【多对一】的 —— 下降支的 z 不可能被还原（12.5M 与 7.5M 同为 +67.5°）。");
        say("        ⇒ 下降支的「不恒等」是【契约本身的性质】，不是缺陷。");
        int bad2 = 0;
        for (long z = 0; z < WorldContract.Z_CYCLE; z += WorldContract.Z_CYCLE / 16) {
            // ★ §532 修复(P1-8 之二)：zOfLat 收【度】，原版喂的是 latOf 的【弧度】。
            double lat = Math.toDegrees(WorldContract.latOf((int) z));
            int back = WorldContract.zOfLat(lat);
            boolean ok = back == z;
            if (!ok) bad2++;
            say(String.format(LF, "    z=%9d  latOf=%+9.4f  zOfLat=%9d  %s", z, lat, back, ok ? "ok" : "<<< 不恒等"));
        }

        say("");
        say("  [C] 求解器第 j 行的物理纬度：phiOf[j] 与 latOf(zOfLat(deg(phiOf[j])))");
        StationaryWave.ENABLED = true;
        StationaryWave.Q_SCALE = 1e-3;
        StationaryWave.invalidate();
        StationaryWave.ensureSolved(SimTerrain.seedOf(1022228679), PlateField.PLATE_CELL, Atmosphere.theta(0.0), 500_000);
        int rows = StationaryWave.gridRows(), nx = StationaryWave.NX;
        for (int j = 1; j < rows - 1; j += 6) {
            double phiDeg = -90.0 + 180.0 * j / (rows - 1);
            int z = WorldContract.zOfLat(phiDeg);
            // ★ §532 修复(P1-8 之三)：同 [A]，原版拿弧度与 phiDeg 比 ⇒ 8 行全打「不一致」。
            double back = Math.toDegrees(WorldContract.latOf(z));
            say(String.format(LF, "    j=%2d  phiOf=%+8.3f  z=%9d  latOf(z)=%+8.3f  f=%+.3e  %s",
                j, phiDeg, z, back, StationaryWave.fAtRow(j),
                Math.abs(back - phiDeg) < 1e-4 ? "ok" : "<<< 行号与物理纬度不一致"));
        }

        say("");
        say("  [D] 同一物理点：divAt(x,z)  vs  求解器网格值");
        double[] dv = StationaryWave.divGridCopy();
        double lx = 2 * Math.PI * (10_000_000.0 / (Math.PI / 2.0));
        int[][] pts = {{25, 95}, {25, 15}, {30, 100}, {20, 80}, {-36, 67}};
        for (int[] p : pts) {
            int z = WorldContract.zOfLat(p[0]);
            int x = (int) Math.round(p[1] / 360.0 * lx);
            double at = StationaryWave.divAt(x, z);
            int i = (int) Math.round((double) x / lx * nx);
            // ★ §532 修复(P1-8 之四)：latOf 已是【弧度】，原版又套一次 toRadians（E128 同型）
            //   ⇒ j 恒为 23~24（赤道），[D] 段 5 个点全落赤道、结论无效。
            int j = (int) Math.round((WorldContract.latOf(z) + Math.PI / 2) / Math.PI * (rows - 1));
            double grid = (j >= 0 && j < rows) ? dv[j * nx + (((i % nx) + nx) % nx)] : Double.NaN;
            say(String.format(LF, "    (%+4dN,%4dE) z=%9d  j=%2d i=%2d  divAt=%+.4e  网格=%+.4e  %s",
                p[0], p[1], z, j, i, at, grid, Math.abs(at - grid) < 1e-12 ? "一致" : "<<< 不一致"));
        }
        StationaryWave.ENABLED = false;
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
