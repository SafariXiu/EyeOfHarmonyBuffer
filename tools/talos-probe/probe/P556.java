package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;

import java.io.*;
import java.util.Locale;

/**
 * P556 -- §362 刀 1/2/3 的【逐位自证】+ 收益复测。
 *
 * 自证的逻辑：memo 缓存的是 (x,z,seed,cell) 的确定性纯函数，
 * 所以「算两次」== 「算一次再查表」⇒ 必须【逐位相同】。
 * 本探针用 Double.doubleToLongBits 逐点断言，不靠推理。
 *
 * 对照方式：memo 关 = 旧行为（kappaMemo 在无 memo 时等价回落，Atmosphere:284），
 *          memo 开 = 新行为。两者必须给出同一个 bit 串。
 */
public class P556 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P556] " + s); System.out.println("[P556] " + s); }
    static final int SEED = 1022228679;

    static long bits(double d) { return Double.doubleToLongBits(d); }
    static long mism = 0, tested = 0;
    static void cmp(String what, double a, double b) {
        tested++;
        if (bits(a) != bits(b)) { mism++; if (mism <= 5) say("  !! MISMATCH " + what + "  " + a + " vs " + b); }
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p556_report.txt"), "UTF-8");
        say("P556: §362 刀 1/2/3 逐位自证 + 收益复测");
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double th = Atmosphere.theta(0.0);
        OceanField.install(SEED);
        say("  接线: installedSeed=" + OceanField.installedSeed());

        // ================= A) 函数级逐位自证 =================
        say("");
        say("A) 函数级逐位自证（memo 关 vs memo 开，Double.doubleToLongBits）");
        int N = 300;
        long hits0 = Atmosphere.memoHits, miss0 = Atmosphere.memoMisses;
        for (int i = 0; i < N; i++) {
            int x = 100_000 + i * 137_000;
            int z = 50_000 + i * 91_000;
            // --- memo 关 ---
            double kapOff = Atmosphere.kappaAt(x, z, sd, cell);
            double wOff0  = Atmosphere.windAt(x, z, sd, cell, th, SimClimate.GRAD_STEP)[0];
            double wOff1  = Atmosphere.windAt(x, z, sd, cell, th, SimClimate.GRAD_STEP)[1];
            double stOff  = Atmosphere.surfaceTemp(x, z, sd, cell, th);
            double paOff  = Atmosphere.pressureAnomaly(x, z, sd, cell, th);
            double pmOff  = PrecipField.mmPerDay(x, z, sd, cell, th, SimClimate.GRAD_STEP);
            // --- memo 开 ---
            boolean mc = Atmosphere.beginMemo();
            double kapOnA = Atmosphere.kappaMemo(x, z, sd, cell);   // 第一次 = miss
            double kapOnB = Atmosphere.kappaMemo(x, z, sd, cell);   // 第二次 = hit（走缓存路径）
            double wOn0   = Atmosphere.windAt(x, z, sd, cell, th, SimClimate.GRAD_STEP)[0];
            double wOn1   = Atmosphere.windAt(x, z, sd, cell, th, SimClimate.GRAD_STEP)[1];
            double stOn   = Atmosphere.surfaceTemp(x, z, sd, cell, th);
            double paOn   = Atmosphere.pressureAnomaly(x, z, sd, cell, th);
            double pmOn   = PrecipField.mmPerDay(x, z, sd, cell, th, SimClimate.GRAD_STEP);
            Atmosphere.endMemo(mc);
            cmp("kappaAt vs kappaMemo(miss)", kapOff, kapOnA);
            cmp("kappaAt vs kappaMemo(hit)",  kapOff, kapOnB);
            cmp("windAt[0]", wOff0, wOn0);
            cmp("windAt[1]", wOff1, wOn1);
            cmp("surfaceTemp", stOff, stOn);
            cmp("pressureAnomaly", paOff, paOn);
            cmp("mmPerDay", pmOff, pmOn);
        }
        say(String.format(LF, "   比较 %d 次，逐位不符 %d 次   =>  %s", tested, mism, mism == 0 ? "自证通过" : "自证失败"));
        say(String.format(LF, "   memo 计数: hits=%d misses=%d", Atmosphere.memoHits - hits0, Atmosphere.memoMisses - miss0));

        // ================= B) 收益复测 =================
        say("");
        say("B) 冷瓦片收益复测（与 P555 的 13.63 ms/节点 直接可比）");
        SimClimate.clearCache(); SimClimate.resetStats();
        int NT = 8;
        for (int i = 0; i < NT; i++) {
            int x = i * SimClimate.TILE_X + SimClimate.TILE_X / 2;
            int z = i * 500_000 + SimClimate.TILE_Z / 2;
            SimClimate.sample(x, z, SEED, null);
        }
        long sc = SimClimate.SOLVE_COUNT.get(), sn = SimClimate.SOLVE_NANOS.get(), nd = SimClimate.NODE_COUNT.get();
        say(String.format(LF, "   solves=%d  nodes=%d  =>  %.1f ms/瓦片   %.2f ms/节点", sc, nd, sn / 1.0e6 / sc, sn / 1.0e6 / nd));
        say(String.format(LF, "   P555 基线: 1144.9 ms/瓦片   13.63 ms/节点   =>  加速 %.2fx",
            1144.9 / (sn / 1.0e6 / sc), 13.63 / (sn / 1.0e6 / nd)));
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
