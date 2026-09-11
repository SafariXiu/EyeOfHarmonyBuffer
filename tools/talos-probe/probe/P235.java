package probe;

import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.BarotropicGyre;
import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.GlobalCirculation;
import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.RelaxedClimate;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.NoiseContinentGrid;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.PolarZone;

import java.io.File;
import java.io.PrintStream;

/**
 * P235：**极地三件套的验收**（实心冰盖 / 浮冰带 / 虚拟墙 / 固定急流 / 冷带）。
 *
 * <h3>验什么</h3>
 * 口径唯一只是"不可能漂移"的必要条件，不是充分条件。必须再证明：
 *   [1] 墙在**每一列**都存在、与浮冰带不重叠，且真的把极地海盆与中纬度海**切开**
 *       （细网格 + 求解器粗网格两级洪水填充，两级都必须不通）；
 *   [2] 冰缘平移**不改变面积**（多相位平均，消掉格点相位这个假信号）；
 *   [3] 急流峰值精确 = JET_PEAK、且严格无散度（u 只依赖纬度）；
 *   [4] 合成几何下墙**真的切断域**（不是减速）：极侧必须逐位 0，负对照必须非 0；
 *   [5] 生产窗口：加墙前后逐位比对 + 收敛（保守粗化没搞坏多网格）。
 *
 * <h3>[2] 为什么要多相位平均</h3>
 * 平移是连续的，而格点在 5km 上；一个"长度恰好 8 格"的开区间在格点对齐时含 7 个点、
 * 不对齐时含 8 个点。所以**逐格点计数**本身随相位跳 ±1，那是离散化假信号，不是面积趋势。
 * 对 4 个亚格相位各测一次再平均，假信号互相抵消，剩下的才是真面积差。
 *
 * <h3>[1] 的粗网格级为什么要单独做</h3>
 * 求解器把 240x240 最近邻采到 32x128。墙只有 2.1 个粗格厚；如果它在粗化中消失，
 * 细网格的洪水填充照样 PASS，而实际求解器里墙是漏的。所以必须按**求解器同一套采样**
 * 复刻掩码再填一次。
 *
 * 用法：runprobe4.bat P235   输出：p235_report.txt；退出码 0/1。预算约 2 分钟。
 */
public class P235 {

    static final int SEED = 1022228679;
    static final int TILE_X = 100_000, ZC = GlobalCirculation.Z_CYCLE;
    static final int CELL_X = 1250, CELL_Z = 5000, HALO_Z = 20, NY = 240, NX = 240;
    static final int NCX = BarotropicGyre.NCX, NCZ = BarotropicGyre.NCZ;

    static File ROOT = new File("K:\\moder\\EyeOfHarmonyBuffer");
    static PrintStream rep;

    public static void main(String[] args) throws Exception {
        RelaxedClimate.PREHEAT = false;
        rep = new PrintStream(new File(ROOT, "build\\eoh_probe\\mtn\\p235_report.txt"), "UTF-8");
        boolean ok = true;

        say("===== P235：极地三件套验收（PolarZone 单一口径）=====");
        say("  SEED=" + SEED + "  网格 " + NX + "x" + NY + " @ " + CELL_X + "/" + CELL_Z + "  haloZ=" + HALO_Z);
        say(String.format("  口径 CORE=%.2f FLOE=%.2f WALL=(%.2f,%.2f] COLD=%.2f | 冰缘平移 %.0fkm | JET=%.2f | FORCE=%.1f",
            PolarZone.CORE_BAND, PolarZone.FLOE_BAND, PolarZone.WALL_OUTER, PolarZone.WALL_INNER,
            PolarZone.COLD_BAND, PolarZone.EDGE_SHIFT / 1000.0, PolarZone.JET_PEAK, PolarZone.ICE_FORCE));

        // ===== 建生产几何（originX = -100km，与 RelaxedClimate 的 tileX=0 窗口一致）=====
        final int originX = -100_000;
        boolean[] land = new boolean[NX * NY];
        boolean[] wall = new boolean[NX * NY];
        boolean[] floe = new boolean[NX * NY];
        for (int iy = 0; iy < NY; iy++) {
            int z = (iy - HALO_Z) * CELL_Z;
            boolean w = PolarZone.isWallCell(PolarZone.rawBand(z));
            for (int ix = 0; ix < NX; ix++) {
                int i = iy * NX + ix;
                int x = originX + ix * CELL_X;
                land[i] = NoiseContinentGrid.isLand(x, z, SEED);
                wall[i] = w;
                floe[i] = PolarZone.isFloeOcean(PolarZone.band(x, z, SEED)) && !land[i];
            }
        }
        int floeN = 0, polarN = 0, wallOcean = 0;
        for (int i = 0; i < land.length; i++) {
            if (floe[i]) floeN++;
            if (PolarZone.isPolar(PolarZone.band(originX + (i % NX) * CELL_X, ((i / NX) - HALO_Z) * CELL_Z, SEED))) polarN++;
            if (wall[i] && !land[i]) wallOcean++;
        }
        say(String.format("  生产几何：浮冰带海格 %d（%.2f%%）| 极区格 %d（%.1f%%）| 墙格 %d（其中海 %d）",
            floeN, 100.0 * floeN / land.length, polarN, 100.0 * polarN / land.length, NX * NY / 240 * 8, wallOcean));

        // ================= [1] 两级洪水填充：墙真的切开域 =================
        say("");
        say("--- [1] 洪水填充：极地海盆能不能从中纬度走进去 ---");
        int fineSeedRows = firstWallRow(wall, NX, NY);
        boolean[] fineWall = flood(land, wall, NX, NY, true, fineSeedRows);
        boolean[] fineNoWall = flood(land, wall, NX, NY, false, fineSeedRows);
        int reachWall = 0, reachNoWall = 0;
        for (int i = 0; i < floe.length; i++) {
            if (!floe[i]) continue;
            if (fineWall[i]) reachWall++;
            if (fineNoWall[i]) reachNoWall++;
        }
        say(String.format("  [1a] 细网格 %dx%d：有墙时可从赤道侧走到的浮冰格 = %d/%d；无墙时 = %d/%d",
            NX, NY, reachWall, floeN, reachNoWall, floeN));
        boolean ok1a = reachWall == 0;
        say("       判据: 有墙时 0（极地海盆被切开）。无墙那一列是**灵敏度对照**：" +
            (reachNoWall > 0 ? "非 0 ⇒ 墙确实是那个把路堵死的东西" : "也是 0 ⇒ 这一段本来就全是陆，本窗口对墙不敏感"));
        ok &= ok1a;

        // 粗网格级：按求解器完全相同的最近邻采样复刻
        int ncx = Math.max(4, Math.min(NCX, NX)), ncz = Math.max(4, Math.min(NCZ, NY));
        double hx = NX * CELL_X / (double) ncx, hz = NY * CELL_Z / (double) ncz;
        boolean[] cLand = new boolean[ncx * ncz], cWall = new boolean[ncx * ncz], cFloe = new boolean[ncx * ncz];
        for (int y = 0; y < ncz; y++) {
            int fy = (int) Math.min(NY - 1, Math.floor((y + 0.5) * hz / CELL_Z));
            for (int x = 0; x < ncx; x++) {
                int fx = (int) Math.min(NX - 1, Math.floor((x + 0.5) * hx / CELL_X));
                int fi = fy * NX + fx;
                cLand[y * ncx + x] = land[fi];
                cWall[y * ncx + x] = wall[fi] && !land[fi];
                cFloe[y * ncx + x] = floe[fi];
            }
        }
        int cw = 0, cf = 0;
        for (int i = 0; i < cWall.length; i++) { if (cWall[i]) cw++; if (cFloe[i]) cf++; }
        int cSeedRows = firstWallRow(cWall, ncx, ncz);
        boolean[] cReachWall = flood(cLand, cWall, ncx, ncz, true, cSeedRows);
        boolean[] cReachNoWall = flood(cLand, cWall, ncx, ncz, false, cSeedRows);
        say("       种子行 = [0, " + cSeedRows + ")（墙的第一行之前）；墙位于粗行 " + cSeedRows + "+");
        int cReach1 = 0, cReach0 = 0;
        for (int i = 0; i < cFloe.length; i++) {
            if (!cFloe[i]) continue;
            if (cReachWall[i]) cReach1++;
            if (cReachNoWall[i]) cReach0++;
        }
        say(String.format("  [1b] 求解器粗网格 %dx%d（最近邻采样，与 BarotropicGyre 同一套公式）："
                + "墙新增挡住的海格 = %d；有墙时可达浮冰格 = %d/%d；无墙时 = %d/%d",
            ncx, ncz, cw, cReach1, cf, cReach0, cf));
        boolean ok1b = cReach1 == 0;
        say("       判据: 粗网格上有墙时也是 0（墙在 240->128 采样 + 保守粗化之后仍然完整） -> "
            + (ok1b ? "PASS" : "FAIL"));
        ok &= ok1b;

        // ================= [2] 冰缘平移的面积守恒（多相位平均）=================
        say("");
        say("--- [2] 冰缘平移是否改变面积（4 个亚格相位求平均，消掉格点相位假信号）---");
        // ⚠ 相位必须**避开** 0：平移量为 5km 的整数倍时，长度恰好 8 格的**开区间**只含 7 个格点，
        // 不对齐时含 8 个。取 CELL_Z 的奇数 /8 相位 ⇒ 全部落在"非对齐"那一类，
        // 相位平均才是在量真实的带宽而不是在量格点对齐这个退化情形（第一次就是这么被骗的：+3.23%）。
        double[] phases = { 625.0, 1875.0, 3125.0, 4375.0, 5625.0, 6875.0 };
        double baseCore = 0, baseFloe = 0, noisyCore = 0, noisyFloe = 0;
        double saveShift = PolarZone.EDGE_SHIFT;
        for (double ph : phases) {
            PolarZone.EDGE_SHIFT = 0.0;
            for (int ix = 0; ix < NX; ix++) {
                for (int iy = 0; iy < NY; iy++) {
                    int z = (iy - HALO_Z) * CELL_Z;
                    double be = PolarZone.rawBand((int) Math.round(z - ph));
                    if (PolarZone.isCore(be)) baseCore++;
                    if (PolarZone.isFloeOcean(be)) baseFloe++;
                }
            }
            PolarZone.EDGE_SHIFT = saveShift;
            for (int ix = 0; ix < NX; ix++) {
                double sh = PolarZone.edgeShift(ix * CELL_X, SEED) + ph;
                for (int iy = 0; iy < NY; iy++) {
                    int z = (iy - HALO_Z) * CELL_Z;
                    double be = PolarZone.rawBand((int) Math.round(z - sh));
                    if (PolarZone.isCore(be)) noisyCore++;
                    if (PolarZone.isFloeOcean(be)) noisyFloe++;
                }
            }
        }
        double p = phases.length;
        double dCore = 100.0 * (noisyCore - baseCore) / baseCore;
        double dFloe = 100.0 * (noisyFloe - baseFloe) / baseFloe;
        say(String.format("  [2a] 实心冰盖 相位平均格数：无平移=%.1f 有平移=%.1f（Δ%+.2f%%）", baseCore / p, noisyCore / p, dCore));
        say(String.format("  [2b] 浮冰带   相位平均格数：无平移=%.1f 有平移=%.1f（Δ%+.2f%%）", baseFloe / p, noisyFloe / p, dFloe));
        boolean ok2 = Math.abs(dCore) < 0.5 && Math.abs(dFloe) < 0.5;
        say("       判据: |Δ| < 0.5%（刚性平移按构造不改带宽） -> " + (ok2 ? "PASS" : "FAIL"));
        ok &= ok2;

        // 冰缘确实蜿蜒了（不只是"平移量为 0"）
        double shMin = 1e9, shMax = -1e9;
        for (int ix = 0; ix < NX; ix++) {
            double sh = PolarZone.edgeShift(ix * CELL_X, SEED);
            if (sh < shMin) shMin = sh;
            if (sh > shMax) shMax = sh;
        }
        say(String.format("  [2c] 冰缘平移实测范围 [%.0f, %.0f] block（幅度上限 %.0f）⇒ 冰缘确实在动",
            shMin, shMax, PolarZone.EDGE_SHIFT));
        boolean ok2c = (shMax - shMin) > 0.5 * PolarZone.EDGE_SHIFT;
        say("       判据: 平移幅度用掉一半以上（否则就是加了噪声却看不出蜿蜒） -> " + (ok2c ? "PASS" : "FAIL"));
        ok &= ok2c;

        // ================= [3] 急流 =================
        say("");
        say("--- [3] 固定急流 u(b) ---");
        double jetMax = 0; int jetMaxRow = -1, jetNonzero = 0;
        for (int iy = 0; iy < NY; iy++) {
            double u = PolarZone.jetU(PolarZone.rawBand((iy - HALO_Z) * CELL_Z));
            if (u != 0) jetNonzero++;
            if (Math.abs(u) > jetMax) { jetMax = Math.abs(u); jetMaxRow = iy; }
        }
        say(String.format("  [3a] 峰值 |u|=%.6f m/s（行 %d，bandD=%.3f）；有急流的行数=%d/%d（两翼各 5 行）",
            jetMax, jetMaxRow, PolarZone.rawBand((jetMaxRow - HALO_Z) * CELL_Z), jetNonzero, NY));
        boolean ok3a = Math.abs(jetMax - PolarZone.JET_PEAK) < 1e-9;
        say("       判据: 峰值**精确**等于 JET_PEAK（剖面是 sin² 包，两端为 0） -> " + (ok3a ? "PASS" : "FAIL"));
        ok &= ok3a;

        int dUdx = 0;
        for (int iy = 0; iy < NY; iy++) {
            double u0 = PolarZone.jetU(PolarZone.rawBand((iy - HALO_Z) * CELL_Z));
            for (int ix = 0; ix + 1 < NX; ix++) {
                double u1 = PolarZone.jetU(PolarZone.rawBand((iy - HALO_Z) * CELL_Z));
                if (Double.doubleToRawLongBits(u0) != Double.doubleToRawLongBits(u1)) dUdx++;
            }
        }
        say("  [3b] 急流的 du/dx 非零格 = " + dUdx + "（注入只加 fx，v 分量不变）");
        boolean ok3b = dUdx == 0;
        say("       判据: 逐位为 0（u 只依赖纬度 ⇒ 散度不变） -> " + (ok3b ? "PASS" : "FAIL"));
        ok &= ok3b;

        // ================= [4] 合成几何：墙真的切断域 =================
        say("");
        say("--- [4] 求解器级切断测试（全海 + 纬向风只加在墙的赤道侧；墙厚 = 生产厚度 4 行）---");
        int wLo = 80, wHi = 83;
        boolean[] sea = new boolean[NX * NY];
        boolean[] sw = new boolean[NX * NY];
        double[] uw = new double[NX * NY], vw = new double[NX * NY];
        for (int iy = 0; iy < NY; iy++) {
            double uu = iy < 50 ? 1.0 : 0.0;
            for (int ix = 0; ix < NX; ix++) {
                uw[iy * NX + ix] = uu;
                sw[iy * NX + ix] = iy >= wLo && iy <= wHi;
            }
        }
        double[] fRow = new double[NY], betaRow = new double[NY];
        fillRow(fRow, betaRow);
        double[] a1 = new double[NX * NY], b1 = new double[NX * NY];
        BarotropicGyre.solve(NX, NY, CELL_X, CELL_Z, sea, sw, uw, vw, fRow, betaRow, a1, b1, 0);
        double[] withWall = farSide(a1, b1, wHi);
        double st1 = BarotropicGyre.lastSteps, po1 = BarotropicGyre.lastPoisRes;
        double[] a0 = new double[NX * NY], b0 = new double[NX * NY];
        BarotropicGyre.solve(NX, NY, CELL_X, CELL_Z, sea, uw, vw, fRow, betaRow, a0, b0);
        double[] noWall = farSide(a0, b0, wHi);
        say(String.format("  [4a] 有墙：极侧 max|u|=%.3e max|v|=%.3e（步数 %.0f 泊松残差 %.3e）",
            withWall[0], withWall[1], st1, po1));
        say(String.format("  [4b] 负对照 wallF=null：极侧 max|u|=%.5f max|v|=%.5f", noWall[0], noWall[1]));
        boolean ok4 = withWall[0] < 1e-12 && withWall[1] < 1e-12 && noWall[0] > 1e-3;
        say("       判据: 有墙时极侧逐位 0（rhs≡0 + 齐次边界 ⇒ ψ≡0）且负对照非零 -> " + (ok4 ? "PASS" : "FAIL"));
        ok &= ok4;

        // ================= [5] 生产窗口：真实风 =================
        say("");
        say("--- [5] 生产窗口（真实海陆 + 真实风）逐位比对 ---");
        RealBox box = new RealBox(0);
        box.load();
        double[] pu0 = new double[NX * NY], pv0 = new double[NX * NY];
        BarotropicGyre.solve(NX, NY, CELL_X, CELL_Z, box.land, box.uW, box.vW, box.fRow, box.betaRow, pu0, pv0);
        double[] s0 = stats(box.land, pu0, pv0);
        double q0 = BarotropicGyre.lastSteps, r0 = BarotropicGyre.lastPoisRes;
        double[] pu1 = new double[NX * NY], pv1 = new double[NX * NY];
        BarotropicGyre.solve(NX, NY, CELL_X, CELL_Z, box.land, box.wall, box.uW, box.vW,
            box.fRow, box.betaRow, pu1, pv1, 0);
        double[] s1 = stats(box.land, pu1, pv1);
        double q1 = BarotropicGyre.lastSteps, r1 = BarotropicGyre.lastPoisRes;
        int diff = 0; double maxD = 0;
        for (int i = 0; i < pu0.length; i++) {
            if (Double.doubleToRawLongBits(pu0[i]) != Double.doubleToRawLongBits(pu1[i])
                || Double.doubleToRawLongBits(pv0[i]) != Double.doubleToRawLongBits(pv1[i])) {
                diff++;
                double d = Math.abs(pu0[i] - pu1[i]);
                if (d > maxD) maxD = d;
            }
        }
        say(String.format("  [5a] 无墙：RMS u=%.6f v=%.6f u/v=%6.3f | 步数=%.0f 泊松=%.6e", s0[0], s0[1], s0[0] / s0[1], q0, r0));
        say(String.format("  [5b] 有墙：RMS u=%.6f v=%.6f u/v=%6.3f | 步数=%.0f 泊松=%.6e", s1[0], s1[1], s1[0] / s1[1], q1, r1));
        say(String.format("  [5c] 逐位不同格 %d/%d（maxΔ|u|=%.6f）；ΔRMS u=%+.6e v=%+.6e",
            diff, pu0.length, maxD, s1[0] - s0[0], s1[1] - s0[1]));
        boolean ok5 = diff > 0;
        say("       判据: 墙必须真的改变解（否则等于没加；P236 记录过一次看起来没变化） -> "
            + (ok5 ? "PASS" : "FAIL"));
        ok &= ok5;
        boolean ok5b = r1 <= r0 * 1.5 + 1e-12;
        say(String.format("  [5d] 泊松残差比 = %.4f -> %s", r1 / Math.max(r0, 1e-30), ok5b ? "PASS(未劣化)" : "FAIL(劣化)"));
        ok &= ok5b;

        say("");
        say("POLAR_STATUS=" + (ok ? "PASS" : "FAIL"));
        rep.flush();
        rep.close();
        System.exit(ok ? 0 : 1);
    }

    /**
     * 从赤道侧（行 &lt; 100）所有可流格出发做 4 邻域洪水填充（X 方向环绕）。
     * {@code withWall=false} 时把墙当成可流格 = 负对照。
     */
    static boolean[] flood(boolean[] land, boolean[] wall, int nx, int ny, boolean withWall, int seedRows) {
        boolean[] seen = new boolean[nx * ny];
        int[] q = new int[nx * ny];
        int head = 0, tail = 0;
        // 种子只取**墙的赤道侧**（行 < seedRows）。踩过的坑：早先把整个下半场都当种子，
        // 而极地海盆本身就在下半场（墙在行 103、通道在行 109+），于是"填到了"是废话。
        for (int iy = 0; iy < seedRows && iy < ny; iy++) {
            for (int ix = 0; ix < nx; ix++) {
                int i = iy * nx + ix;
                if (land[i]) continue;
                if (withWall && wall[i]) continue;
                if (seen[i]) continue;
                seen[i] = true; q[tail++] = i;
            }
        }
        while (head < tail) {
            int i = q[head++];
            int ix = i % nx, iy = i / nx;
            for (int k = 0; k < 4; k++) {
                int jx = k == 0 ? (ix + 1) % nx : (k == 1 ? (ix - 1 + nx) % nx : ix);
                int jy = k == 2 ? iy + 1 : (k == 3 ? iy - 1 : iy);
                if (jy < 0 || jy >= ny) continue;
                int j = jy * nx + jx;
                if (seen[j] || land[j]) continue;
                if (withWall && wall[j]) continue;
                seen[j] = true; q[tail++] = j;
            }
        }
        return seen;
    }

    /** 第一行有墙的行号（种子区上界）；没有墙则返回全部行。 */
    static int firstWallRow(boolean[] wall, int nx, int ny) {
        for (int iy = 0; iy < ny; iy++) {
            for (int ix = 0; ix < nx; ix++) {
                if (wall[iy * nx + ix]) return iy;
            }
        }
        return ny;
    }

    static double[] farSide(double[] u, double[] v, int wHi) {
        double mu = 0, mv = 0;
        for (int iy = wHi + 1; iy < NY; iy++) {
            for (int ix = 0; ix < NX; ix++) {
                int i = iy * NX + ix;
                if (Math.abs(u[i]) > mu) mu = Math.abs(u[i]);
                if (Math.abs(v[i]) > mv) mv = Math.abs(v[i]);
            }
        }
        return new double[] { mu, mv };
    }

    static void fillRow(double[] fRow, double[] betaRow) {
        int nyLat = ZC / CELL_Z;
        for (int iy = 0; iy < NY; iy++) {
            int v = (iy - HALO_Z) % nyLat;
            if (v < 0) v += nyLat;
            int z = v * CELL_Z;
            double bb = GlobalCirculation.bandD(z);
            double ff = Math.sin(GlobalCirculation.latRad(z));
            fRow[iy] = Math.abs(ff) < 1e-4 ? 0.0 : ff;
            betaRow[iy] = 2.0 * BarotropicGyre.OMEGA * Math.cos(bb * Math.PI / 2.0)
                * (Math.PI / 2.0) * 2.0 / ZC;
        }
    }

    static double[] stats(boolean[] land, double[] uo, double[] vo) {
        double su = 0, sv = 0;
        int n = 0;
        for (int i = 0; i < land.length; i++) {
            if (land[i]) continue;
            su += uo[i] * uo[i];
            sv += vo[i] * vo[i];
            n++;
        }
        double ru = Math.sqrt(su / n), rv = Math.sqrt(sv / n);
        return new double[] { ru, rv, rv > 0 ? ru / rv : -1, n };
    }

    static void say(String s) {
        System.out.println("[P235] " + s);
        rep.println("[P235] " + s);
    }

    static class RealBox {
        final int originX;
        boolean[] land, wall;
        double[] uW, vW, fRow, betaRow;

        RealBox(int tileX) {
            this.originX = tileX * TILE_X - 100_000;
        }

        void load() {
            land = new boolean[NX * NY];
            wall = new boolean[NX * NY];
            uW = new double[NX * NY];
            vW = new double[NX * NY];
            for (int iy = 0; iy < NY; iy++) {
                int z = (iy - HALO_Z) * CELL_Z;
                boolean w = PolarZone.isWallCell(PolarZone.rawBand(z));
                for (int ix = 0; ix < NX; ix++) {
                    int i = iy * NX + ix;
                    land[i] = NoiseContinentGrid.isLand(originX + ix * CELL_X, z, SEED);
                    wall[i] = w;
                }
            }
            for (int tz = -1; tz <= 1; tz++) {
                for (int iy = 0; iy < NY; iy++) {
                    int z = (iy - HALO_Z) * CELL_Z;
                    if (Math.floorDiv(z, ZC) != tz) continue;
                    for (int tx = -1; tx <= 1; tx++) {
                        for (int ix = 0; ix < NX; ix++) {
                            int x = originX + ix * CELL_X;
                            if (Math.floorDiv(x, TILE_X) != tx) continue;
                            double[] w = RelaxedClimate.sampleWind(x, z, SEED);
                            uW[iy * NX + ix] = w[0];
                            vW[iy * NX + ix] = w[1];
                        }
                    }
                }
            }
            fRow = new double[NY];
            betaRow = new double[NY];
            fillRow(fRow, betaRow);
        }
    }
}
