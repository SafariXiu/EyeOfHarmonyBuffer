package probe;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.GyreRow;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Locale;

/** P291：目标① 的海盆几何（新地形，直接扫描 x 找陆海交替）。 */
public class P291 {

    static final int SEED = 1022228679;
    /** ⚠ 口径修正（§559，模板 P442:47）：收 {@code long seed} 的入口传本值；收 {@code int worldSeedInt} 的入口仍传裸 {@code SEED}。本次未改任何判据/阈值/输出行。 */
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final int ZC = WorldContract.Z_CYCLE;
    static final int DX = 25_000, XMIN = -12_000_000, XMAX = 12_000_000;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p291_report.txt"), "UTF-8");
        int cell = PlateField.PLATE_CELL;
        GyreRow.Params pp = new GyreRow.Params();
        say("P291：海盆几何（新地形；直接沿 x 扫描陆海交替）");
        say(String.format(LF, "  x = %d .. %d km，dx=%d km（%d 点/纬线）；PLATE_CELL=%d km",
            XMIN/1000, XMAX/1000, DX/1000, (XMAX-XMIN)/DX + 1, cell/1000));
        say("");
        say(String.format(LF, "  %-8s %8s %8s %10s %10s %10s %10s %10s %10s %10s",
            "纬度", "海盆数", "活跃数", "p10 km", "p25 km", "p50 km", "p75 km", "p90 km", "max km", "海洋%"));
        double[] all = new double[4000], act = new double[4000];
        int na = 0, nact = 0;
        for (int li = 0; li < 34; li++) {
            int latSign = li < 17 ? 1 : -1;
            int latDeg = 5 + (li % 17) * 5;
            int z = (int) (latSign * latDeg / 90.0 * (ZC / 2));
            double lat = WorldContract.latOf(z, ZC);
            double thr = 2.0 * Math.PI * pp.deltaAt(z);
            double[] w = new double[64];
            int nw = 0, nOcean = 0, nTot = 0, nActive = 0;
            int run = 0;
            for (int x = XMIN; x <= XMAX; x += DX) {
                boolean land = PlateField.isLandWithCell(x, z, SD, cell);
                nTot++;
                if (land) {
                    if (run > 0 && nw < w.length) { w[nw++] = run * (double) DX; }
                    run = 0;
                } else { run++; nOcean++; }
            }
            if (run > 0 && nw < w.length) w[nw++] = run * (double) DX;
            if (nw == 0) continue;
            double[] ws = Arrays.copyOf(w, nw);
            Arrays.sort(ws);
            int nA = 0;
            for (double v : ws) { if (na < all.length) all[na++] = v; if (v >= thr) { nActive++; if (nact < act.length) act[nact++] = v; } }
            say(String.format(LF, "  %-8.0f %8d %8d %10.0f %10.0f %10.0f %10.0f %10.0f %10.0f %9.1f%%",
                Math.toDegrees(lat), nw, nActive, ws[(int)(0.10*nw)], ws[(int)(0.25*nw)], ws[nw/2],
                ws[(int)(0.75*nw)], ws[Math.min(nw-1,(int)(0.90*nw))], ws[nw-1], 100.0*nOcean/nTot));
        }
        say("");
        say(String.format(LF, "全部海盆 %d 个：p10 %.0f / p25 %.0f / **p50 %.0f** / p75 %.0f / p90 %.0f / max %.0f km",
            na, all[(int)(0.10*na)], all[(int)(0.25*na)], all[na/2], all[(int)(0.75*na)], all[(int)(0.90*na)], all[na-1]));
        if (nact > 0) {
            Arrays.sort(act, 0, nact);
            say(String.format(LF, "活跃海盆（宽 >= 2*pi*delta_M）%d 个：p10 %.0f / p25 %.0f / **p50 %.0f** / p75 %.0f / p90 %.0f / max %.0f km",
                nact, act[(int)(0.10*nact)], act[(int)(0.25*nact)], act[nact/2], act[(int)(0.75*nact)], act[(int)(0.90*nact)], act[nact-1]));
            say(String.format(LF, "  活跃占比 %.0f%%", 100.0*nact/na));
        }
        say("  参考（§71 旧读数）：全部 p10 4,730 / p50 7,650 / max 18,060 km");
        rep.close();
    }

    static void say(String s) { System.out.println("[P291] " + s); rep.println("[P291] " + s); }
}
