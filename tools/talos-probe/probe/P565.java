package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

/**
 * P565 -- A1 的实测依据：对【纬向平均】而言，NX=500 是不是过采样？
 *
 * 主张（§367）：Zonal.profile 要的是对 x 的平均；P491 实测本世界降水场在 x 上的
 * 有效独立样本数只有约 19 个 => NX=500 对纬向平均而言约 26 倍过采样。
 *
 * 本探针在【一条纬度行】上，用 NX = 500 / 256 / 128 / 96 / 72 / 48 / 24 / 12 各求一次纬向平均，
 * 与 NX=500 的基准比较。若 NX=72 已与 NX=500 在判据容差内一致 => A1 成立且有据。
 *
 * 成本：一条行的互不相同瓦片约 400 个，冷解 ~2 s/个 => ~13 min。
 */
public class P565 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P565] " + s); System.out.println("[P565] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p565_report.txt"), "UTF-8");
        say("P565: 纬向平均对 NX 的敏感性（A1 的实测依据）");
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        OceanField.install(SEED);
        double th = Atmosphere.theta(0.0);
        int[] nxs = {500, 256, 128, 96, 72, 48, 24, 12};
        int[] lats = {45, 25};
        say("  seed=" + sd + "  PLATE_CELL=" + cell + "  GRAD_STEP=" + GRAD);

        for (int latDeg : lats) {
            int z = WorldContract.zOfLat(latDeg);
            double lat = WorldContract.latOf(z);
            double base = -1;
            say("");
            say("  纬度 " + latDeg + "N  (z=" + z + ")");
            say("    NX     纬向平均 P(mm/day)     与 NX=500 之差        差%      互不相同瓦片数(估)");
            for (int nx : nxs) {
                double sum = 0;
                java.util.HashSet<Long> tiles = new java.util.HashSet<Long>();
                for (int c = 0; c < nx; c++) {
                    int x = (int) Math.round((c + 0.5) * CIRC / nx);
                    sum += PrecipField.mmPerDay(x, z, sd, cell, th, GRAD);
                    tiles.add(((long) (x / SimClimate.TILE_X) << 32) | ((z / SimClimate.TILE_Z) & 0xFFFFFFFFL));
                }
                double mean = sum / nx;
                if (nx == 500) base = mean;
                say(String.format(LF, "    %4d   %18.6f   %18.6f   %9.4f%%   %d",
                    nx, mean, base < 0 ? 0.0 : mean - base, base < 0 ? 0.0 : 100.0 * (mean - base) / base, tiles.size()));
            }
        }
        say("");
        say("  判据：若 NX=72 与 NX=500 的差 < 2%（验收判据的量级）=> A1 成立（纬向平均不需要 NX=500）");
        say("        若差随 NX 单调缩小且 NX=256 仍在 1% 以上 => 需要 NX>=256，A1 应改口径为 NX=256");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
