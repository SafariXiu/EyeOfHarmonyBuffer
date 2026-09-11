package probe;

import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.BarotropicGyre;
import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.GlobalCirculation;
import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.RelaxedClimate;

import java.io.File;
import java.io.PrintStream;

/**
 * P200：【只读】受控实验 —— 把「X 周期」这一个变量单独拎出来。
 * P199 的拓扑结果已经部分否定了原假设（98.8% 的海面积都是 X 环绕型，但 z=850k 那个盆仍然出了
 * 教科书式西边界流）。所以这里做三个理想化海盆，只改几何、风场与 β 完全相同：
 *   Case 1 闭合海盆：海 = 行[40,200) x 列[40,200)，四周都是墙
 *   Case 2 X 环形通道：海 = 行[40,200) x 全部列（东西墙消失 → 就是 P199 里 98.8% 的那种）
 *   Case 3 环形通道 + 一道南北向的墙：海 = 行[40,200) x 全部列，但列[140,152) 是陆
 * 判据（先声明）：看中纬度那一行的 v(x) 剖面
 *   Case 1 预期：西墙附近 |v| 峰值远大于东墙（西向强化）
 *   Case 2 预期：v 近似恒 0（只有纬向流）
 *   Case 3 预期：v 在墙的西侧重新出现峰值（说明"墙"才是必要条件，"环绕"本身不是）
 */
public class P200 {

    static final int NX = 240, NY = 240;
    static final int CELL_X = 1250, CELL_Z = 5000;
    static final int ZC = GlobalCirculation.Z_CYCLE;

    static File ROOT = new File("K:\\moder\\EyeOfHarmonyBuffer");
    static PrintStream rep;

    public static void main(String[] args) throws Exception {
        RelaxedClimate.PREHEAT = false;
        rep = new PrintStream(new File(ROOT, "build\\eoh_probe\\mtn\\p200_report.txt"), "UTF-8");
        say("理想化受控实验（风场/β/网格完全相同，只改海陆几何）");
        say("  A_H=" + BarotropicGyre.A_H + " MACRO=" + BarotropicGyre.MACRO
            + " V_CYCLES=" + BarotropicGyre.V_CYCLES + " FINAL_CYCLES=" + BarotropicGyre.FINAL_CYCLES
            + " NCX=" + BarotropicGyre.NCX + " NCZ=" + BarotropicGyre.NCZ);

        caseRun(1, seaClosed());
        caseRun(2, seaChannel());
        caseRun(3, seaChannelWithWall());
        rep.close();
    }

    static void say(String s) { System.out.println("[P200] " + s); rep.println("[P200] " + s); }

    // ---------------- 三种几何 ----------------

    static boolean[] seaClosed() {
        boolean[] land = new boolean[NX * NY];
        for (int y = 0; y < NY; y++) {
            for (int x = 0; x < NX; x++) {
                boolean sea = y >= 40 && y < 200 && x >= 40 && x < 200;
                land[y * NX + x] = !sea;
            }
        }
        return land;
    }

    static boolean[] seaChannel() {
        boolean[] land = new boolean[NX * NY];
        for (int y = 0; y < NY; y++) {
            for (int x = 0; x < NX; x++) {
                boolean sea = y >= 40 && y < 200;
                land[y * NX + x] = !sea;
            }
        }
        return land;
    }

    static boolean[] seaChannelWithWall() {
        boolean[] land = seaChannel();
        for (int y = 40; y < 200; y++) {
            for (int x = 140; x < 152; x++) land[y * NX + x] = true;
        }
        return land;
    }

    // ---------------- 风场（与生产同式，解析给出） ----------------

    static void wind(double[] u, double[] v) {
        for (int y = 0; y < NY; y++) {
            int z = y * CELL_Z;
            double b = GlobalCirculation.bandD(z);          // 0=赤道 1=极地
            double uu = Math.cos(Math.PI * b);              // 赤道东风 -> 中纬西风 -> 极地东风
            for (int x = 0; x < NX; x++) {
                u[y * NX + x] = uu;
                v[y * NX + x] = 0.0;
            }
        }
    }

    static void rows(double[] fRow, double[] betaRow) {
        double om = BarotropicGyre.OMEGA;
        for (int y = 0; y < NY; y++) {
            int z = y * CELL_Z;
            double b = GlobalCirculation.bandD(z);
            double f = Math.sin(GlobalCirculation.latRad(z));
            fRow[y] = Math.abs(f) < 1.0e-4 ? 0.0 : f;
            betaRow[y] = 2.0 * om * Math.cos(b * Math.PI / 2.0) * (Math.PI / 2.0) * 2.0 / ZC;
        }
    }

    static void caseRun(int id, boolean[] land) {
        String[] names = {"", "Case1 闭合海盆", "Case2 X 环形通道", "Case3 环形通道+一道墙"};
        say("");
        say("===== " + names[id] + " =====");
        double[] u = new double[NX * NY], v = new double[NX * NY];
        wind(u, v);
        double[] fRow = new double[NY], betaRow = new double[NY];
        rows(fRow, betaRow);
        double[] uo = new double[NX * NY], vo = new double[NX * NY];
        long t0 = System.nanoTime();
        BarotropicGyre.solve(NX, NY, CELL_X, CELL_Z, land, u, v, fRow, betaRow, uo, vo);
        double ms = (System.nanoTime() - t0) / 1e6;
        say(String.format("  solve 用时 %.0f ms；steps=%d 泊松残差=%.2e maxZeta=%.3e",
            ms, BarotropicGyre.lastSteps, BarotropicGyre.lastPoisRes, BarotropicGyre.lastMaxZeta));

        double su = 0, sv = 0;
        int n = 0;
        for (int i = 0; i < NX * NY; i++) {
            if (land[i]) continue;
            su += uo[i] * uo[i];
            sv += vo[i] * vo[i];
            n++;
        }
        double ru = Math.sqrt(su / n), rv = Math.sqrt(sv / n);
        say(String.format("  海格 %d：RMS u=%.5f  RMS v=%.5f  u/v=%.2f  最大|u|=%.4f 最大|v|=%.4f",
            n, ru, rv, rv > 0 ? ru / rv : Double.POSITIVE_INFINITY, BarotropicGyre.lastMaxU, BarotropicGyre.lastMaxV));

        int iy = 120;   // 通道中部
        say("  行 y=120 的 v(x)（每 6 格打印一次，单位 1e-3 m/s；x 单位 km）：");
        StringBuilder sb = new StringBuilder("    x:");
        for (int x = 0; x < NX; x += 6) sb.append(String.format("%6.0f", x * CELL_X / 1000.0));
        say(sb.toString());
        sb = new StringBuilder("    v:");
        for (int x = 0; x < NX; x += 6) sb.append(String.format("%6.1f", vo[iy * NX + x] * 1000.0));
        say(sb.toString());
        sb = new StringBuilder("    u:");
        for (int x = 0; x < NX; x += 6) sb.append(String.format("%6.1f", uo[iy * NX + x] * 1000.0));
        say(sb.toString());

        // 西/东边窗 |v|（取该行最西/最东各 20km 内的海格）
        edgeStat(id, land, uo, vo, iy);
    }

    static void edgeStat(int id, boolean[] land, double[] uo, double[] vo, int iy) {
        int W = 16;   // 20km
        // 找出该行最西/最东的海格
        int first = -1, last = -1;
        for (int x = 0; x < NX; x++) if (!land[iy * NX + x]) { first = x; break; }
        for (int x = NX - 1; x >= 0; x--) if (!land[iy * NX + x]) { last = x; break; }
        if (first < 0) { say("  该行没有海格"); return; }
        double aw = 0, ae = 0;
        int cw = 0, ce = 0;
        for (int x = first; x < Math.min(NX, first + W); x++) if (!land[iy * NX + x]) { aw += Math.abs(vo[iy * NX + x]); cw++; }
        for (int x = Math.max(0, last - W + 1); x <= last; x++) if (!land[iy * NX + x]) { ae += Math.abs(vo[iy * NX + x]); ce++; }
        double mw = cw > 0 ? aw / cw : 0, me = ce > 0 ? ae / ce : 0;
        say(String.format("  西缘 x=%.0fkm |v|均值=%.5f ；东缘 x=%.0fkm |v|均值=%.5f ；西/东 = %s",
            first * CELL_X / 1000.0, mw, last * CELL_X / 1000.0, me,
            me > 1e-9 ? String.format("%.2f", mw / me) : "n/a"));
    }
}
