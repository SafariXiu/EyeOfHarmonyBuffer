package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanField;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanWiring;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.OrographyField;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.ClimateCoords;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.V2BiomeSelect;

import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Locale;

/**
 * P482：D8-b 的 2x2 定量裁决（用户裁决 4-C「重新量四类再决定」）
 *      + 「airT / airMass 在**生产路径上**到底有没有消费者」的**行为学**证明。
 *
 * <p>三段真问题：
 * <ol>
 *   <li>airT 的分子用 tSfc（含海拔直减）还是 tSea（海平面等价值）—— 四类占比各是多少？</li>
 *   <li>这个选择**现在**会改变任何下游结果吗？（不是读源码猜，而是**逐位比较**下游权重）</li>
 *   <li>如果不会，那 D8 说的「群系硬边」还成立吗？气团加项 AIR_DT/AIR_DQ 去哪了？</li>
 * </ol>
 *
 * <p>网格：用于主普查的是 **P458 的同一张 480 点网格**（x/z 逐点同式），所以与设计冻结
 * §128 里 21.0 / 1.3 / 35.4 / 42.3 那份基线**可以直接对账**。
 */
public class P482 {

    static final int SEED = 1022228679;
    static final long SD = SimTerrain.seedOf(SEED);
    static final int CELL = PlateField.PLATE_CELL;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static final int NXB = 24, NZB = 20, NP = NXB * NZB;
    static PrintStream rep;

    static void say(String s) { rep.println("[P482] " + s); rep.flush(); System.out.println("[P482] " + s); System.out.flush(); }

    /** 索引口径逐字抄源码：airMass = mar >= 0.5 ? (airT >= 0 ? 0 : 2) : (airT >= 0 ? 1 : 3)。 */
    static final String[] AM = {"0 mT 海洋性+暖", "1 cP 大陆性+暖", "2 mP 海洋性+冷", "3 cT 大陆性+冷"};

    static int gx(int ix) { return (int) ((ix + 0.5) / NXB * 24_000_000.0) - 12_000_000; }
    static int gz(int iz) { return (int) ((iz + 0.5) / NZB * 20_000_000.0); }

    static double pct(int a, int n) { return 100.0 * a / Math.max(1, n); }

    static double pctile(double[] v, double p) {
        double[] c = v.clone(); Arrays.sort(c);
        int i = (int) Math.round(p * (c.length - 1));
        return c[Math.max(0, Math.min(c.length - 1, i))];
    }

    // ==================== 普查 ====================
    static int[] lastAM;  static double[] lastAT;  static int lastN;
    static int maskMis = -1, maskLandP = -1, maskLandO = -1;

    static void census(String tag, int stepX, int stepZ) {
        int[] am = new int[4];
        int nx = 0, nz = 0;
        for (int i = 0; i < NXB; i += stepX) nx++;
        for (int j = 0; j < NZB; j += stepZ) nz++;
        int n = nx * nz;
        double[] at = new double[n];
        double[] mr = new double[n];
        int nAirPos = 0, nClamp = 0, nMarEdge = 0, nLandP = 0, nLandO = 0, mism = 0;
        int k = 0;
        long t0 = System.currentTimeMillis();
        for (int iz = 0; iz < NZB; iz += stepZ) {
            int z = gz(iz);
            for (int ix = 0; ix < NXB; ix += stepX) {
                int x = gx(ix);
                ClimateCoords.Coords c = ClimateCoords.sample(x, z, SEED, null);
                if (c.airMass >= 0 && c.airMass < 4) am[c.airMass]++;
                at[k] = c.airT; mr[k] = c.mar;
                if (c.airT >= 0.0) nAirPos++;
                if (Math.abs(c.airT) >= 0.999999) nClamp++;
                if (c.mar <= 0.0 || c.mar >= 1.0) nMarEdge++;
                boolean lp = PlateField.isLandWithCell(x, z, SD, CELL);
                boolean lo = OrographyField.sample(x, z, SEED).isLand;
                if (lp) nLandP++;
                if (lo) nLandO++;
                if (lp != lo) mism++;
                k++;
            }
            say(String.format(LF, "    进度 %-22s %3d / %3d   %5.1f s", tag, k, n, (System.currentTimeMillis() - t0) / 1000.0));
        }
        say(String.format(LF, "  == %s ==", tag));
        say(String.format(LF, "     四类  mT %4d (%.1f%%)   cT %4d (%.1f%%)   mP %4d (%.1f%%)   cP %4d (%.1f%%)",
            am[0], pct(am[0], n), am[1], pct(am[1], n), am[2], pct(am[2], n), am[3], pct(am[3], n)));
        say(String.format(LF, "     airT  p05 %+.4f  p25 %+.4f  p50 %+.4f  p75 %+.4f  p95 %+.4f   >=0 占 %.1f%%",
            pctile(at, 0.05), pctile(at, 0.25), pctile(at, 0.50), pctile(at, 0.75), pctile(at, 0.95), pct(nAirPos, n)));
        say(String.format(LF, "     mar   p50 %.4f   |airT|>=1（被夹）的点 %d   mar 触边界的点 %d", pctile(mr, 0.50), nClamp, nMarEdge));
        say(String.format(LF, "     陆地掩膜：PlateField %d (%.1f%%)   OrographyField %d (%.1f%%)   **不一致 %d (%.1f%%)**",
            nLandP, pct(nLandP, n), nLandO, pct(nLandO, n), mism, pct(mism, n)));
        say(String.format(LF, "     （%d 点，x 步长 %d，z 步长 %d，用时 %.1f s）", n, stepX, stepZ, (System.currentTimeMillis() - t0) / 1000.0));
        lastAM = am; lastAT = at; lastN = n;
        maskMis = mism; maskLandP = nLandP; maskLandO = nLandO;
    }

    /** 两次同网格普查的逐点类别差异率。 */
    static void classDiff(String a, int[] A, double[] ATa, String b, int[] B, double[] ATb) {
        int diff = 0;
        double maxd = 0;
        for (int i = 0; i < ATa.length; i++) {
            int ca = warm(ATa[i]), cb = warm(ATb[i]);
            if (ca != cb) diff++;
            double d = Math.abs(ATa[i] - ATb[i]);
            if (d > maxd) maxd = d;
        }
        say(String.format(LF, "     %s vs %s：逐点类别不一致 %d / %d (%.1f%%)   airT 最大差 %.6f",
            a, b, diff, ATa.length, pct(diff, ATa.length), maxd));
    }

    /** 只看 airT 的符号（暖/冷那一刀）—— 跨配置比较时 mar 那一刀与本次开关无关。 */
    static int warm(double airT) { return airT >= 0.0 ? 0 : 1; }

    // ==================== 下游权重 ====================
    static double[] weights(int x, int z) {
        OrographyField.OroSample oro = OrographyField.sample(x, z, SEED);
        double[] w = new double[V2BiomeSelect.KINDS];
        V2BiomeSelect.accumulateWeights(x, z, SEED, oro, true, w);
        return w;
    }

    static double maxDiff(double[] a, double[] b) {
        double m = 0;
        for (int i = 0; i < a.length; i++) m = Math.max(m, Math.abs(a[i] - b[i]));
        return m;
    }

    static int argmax(double[] a) {
        int bi = 0;
        for (int i = 1; i < a.length; i++) if (a[i] > a[bi]) bi = i;
        return bi;
    }

    /** Tier-1 帐隶属度（逐字抄 V2BiomeSelect 114~143 行的数学，只留 Tier-1，用于 D4 代理量化）。 */
    static double[] tier1(double temp, double moist) {
        double lo = V2BiomeSelect.TEMP_CENTER[0], hi = V2BiomeSelect.TEMP_CENTER[V2BiomeSelect.TEMP_CENTER.length - 1];
        if (temp < lo) temp = lo;
        if (temp > hi) temp = hi;
        double mlo = V2BiomeSelect.MOIST_CENTER[0], mhi = V2BiomeSelect.MOIST_CENTER[V2BiomeSelect.MOIST_CENTER.length - 1];
        if (moist < mlo) moist = mlo;
        if (moist > mhi) moist = mhi;
        double[] w = new double[V2BiomeSelect.TEMP_CENTER.length * V2BiomeSelect.MOIST_CENTER.length];
        double sum = 0;
        for (int i = 0; i < V2BiomeSelect.TEMP_CENTER.length; i++) {
            double wt = tent(temp, V2BiomeSelect.TEMP_CENTER[i], V2BiomeSelect.TEMP_SPAN);
            if (wt <= 0.0) continue;
            for (int j = 0; j < V2BiomeSelect.MOIST_CENTER.length; j++) {
                double wm = tent(moist, V2BiomeSelect.MOIST_CENTER[j], V2BiomeSelect.MOIST_SPAN);
                if (wm <= 0.0) continue;
                w[i * V2BiomeSelect.MOIST_CENTER.length + j] = wt * wm;
                sum += wt * wm;
            }
        }
        if (sum > 1e-9) for (int q = 0; q < w.length; q++) w[q] /= sum;
        return w;
    }

    static double tent(double v, double c, double span) {
        double d = Math.abs(v - c);
        return d >= span ? 0.0 : 1.0 - d / span;
    }

    static double clamp(double v, double lo, double hi) { return v < lo ? lo : (v > hi ? hi : v); }

    // ==================== main ====================
    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p482_report.txt"), "UTF-8");
        say("P482：D8-b 的 2x2 裁决 + airT/airMass 消费者的行为学证明");
        say(String.format(LF, "  SEED=%d  SimClimate.ENABLED=%s  ClimateCoords.ENABLE_AIRMASS=%s  SimClimate.AIRT_SEALEVEL=%s",
            SEED, SimClimate.ENABLED, ClimateCoords.ENABLE_AIRMASS, SimClimate.AIRT_SEALEVEL));
        say(String.format(LF, "  OceanField.ENABLED=%s  SST_PROVIDER=%s", OceanField.ENABLED,
            Atmosphere.SST_PROVIDER == null ? "null（SST 关）" : "已装（SST 开）"));
        say(String.format(LF, "  气团加项（旧路径 ClimateCoords 用）：AIR_DT=%s", Arrays.toString(ClimateCoords.AIR_DT)));
        say(String.format(LF, "                                    AIR_DQ=%s", Arrays.toString(ClimateCoords.AIR_DQ)));
        say(String.format(LF, "  AIRT_SCALE=%.1f K   GAMMA=%.6f K/m   PLATE_CELL=%d km",
            SimClimate.AIRT_SCALE, Atmosphere.GAMMA, CELL / 1000));
        say("");

        // ---------- A. 开关进指纹吗 ----------
        say("A. AIRT_SEALEVEL 是否进 configStamp()（D58 纪律）");
        long s0 = SimClimate.configStamp();
        SimClimate.AIRT_SEALEVEL = true;
        long s1 = SimClimate.configStamp();
        SimClimate.AIRT_SEALEVEL = false;
        long s2 = SimClimate.configStamp();
        say(String.format(LF, "   false -> %016X   true -> %016X   复原 false -> %016X", s0, s1, s2));
        say(String.format(LF, "   ⇒ 翻转确实改变指纹：%s ；复原后回到原值：%s", s0 != s1, s0 == s2));
        say("");

        // ---------- 装线 ----------
        OceanWiring.onWorld(SEED);
        say(String.format(LF, "B. 装线：SST_PROVIDER=%s  installedSeed=%d",
            Atmosphere.SST_PROVIDER == null ? "**null**" : "已装", OceanField.installedSeed()));
        say("");

        // ---------- B. 解析推导 vs 真开关（8 个陆点） ----------
        say("C. 「解析推导」与「真翻转开关」的交叉校验（8 个陆点）");
        say("   推导：num2 = num1 + GAMMA*max(0,elev)*kap，其中 kap = 1 - mar（mar 是 tile 双线性插值出来的同一个量）");
        int[] bx = new int[8], bz = new int[8];
        int nb = 0;
        for (int iz = 0; iz < NZB && nb < 8; iz++) {
            for (int ix = 0; ix < NXB && nb < 8; ix++) {
                int x = gx(ix), z = gz(iz);
                if (OrographyField.sample(x, z, SEED).isLand && PlateField.isLandWithCell(x, z, SD, CELL)) {
                    bx[nb] = x; bz[nb] = z; nb++;
                }
            }
        }
        int bad = 0, skipped = 0;
        double worstd = 0;
        for (int i = 0; i < nb; i++) {
            SimClimate.AIRT_SEALEVEL = false;
            ClimateCoords.Coords c1 = ClimateCoords.sample(bx[i], bz[i], SEED, null);
            SimClimate.AIRT_SEALEVEL = true;
            ClimateCoords.Coords c2 = ClimateCoords.sample(bx[i], bz[i], SEED, null);
            double elev = PlateField.elevationWithCell(bx[i], bz[i], SD, CELL);
            double kap = 1.0 - c1.mar;
            double num1 = c1.airT * SimClimate.AIRT_SCALE;
            double num2 = num1 + Atmosphere.GAMMA * Math.max(0.0, elev) * kap;
            double der = clamp(num2 / SimClimate.AIRT_SCALE, -1.0, 1.0);
            double d = Math.abs(der - c2.airT);
            boolean clamped = Math.abs(c1.airT) >= 0.999999;
            if (clamped) skipped++; else if (d > 1e-12) bad++;
            worstd = Math.max(worstd, d);
            if (i < 4) {
                say(String.format(LF, "     x=%9d z=%9d elev=%7.1f kap=%.4f  airT(tSfc)=%+.6f airT(tSea)=%+.6f 推导=%+.6f 差=%.3e%s",
                    bx[i], bz[i], elev, kap, c1.airT, c2.airT, der, d, clamped ? "  [被夹,跳过]" : ""));
            }
        }
        say(String.format(LF, "   ⇒ 8 点里被夹 %d 个；未夹的里面推导与真值不一致 %d 个；最大差 %.3e", skipped, bad, worstd));
        SimClimate.AIRT_SEALEVEL = false;
        say("");

        // ---------- D. 下游惰性证明 ----------
        say("D. airMass / airT / 气团加项 在**生产路径上**有没有消费者（逐位比较下游权重）");
        int nM = Math.min(24, nb);
        double[][] wBase = new double[nM][];
        for (int i = 0; i < nM; i++) wBase[i] = weights(bx[i], bz[i]);
        say(String.format(LF, "   取 %d 个陆点（OrographyField 与 PlateField 都判为陆），基线 = 生产口径", nM));

        ClimateCoords.ENABLE_AIRMASS = false;
        double m1 = 0; int k1 = 0;
        for (int i = 0; i < nM; i++) { double[] w = weights(bx[i], bz[i]); m1 = Math.max(m1, maxDiff(wBase[i], w)); if (argmax(w) != argmax(wBase[i])) k1++; }
        ClimateCoords.ENABLE_AIRMASS = true;
        say(String.format(LF, "   D1 新路径 + ENABLE_AIRMASS true->false：权重最大差 %.3e  argmax 改变 %d 个", m1, k1));

        SimClimate.AIRT_SEALEVEL = true;
        double m2 = 0; int k2 = 0;
        for (int i = 0; i < nM; i++) { double[] w = weights(bx[i], bz[i]); m2 = Math.max(m2, maxDiff(wBase[i], w)); if (argmax(w) != argmax(wBase[i])) k2++; }
        say(String.format(LF, "   D2 新路径 + AIRT_SEALEVEL false->true：权重最大差 %.3e  argmax 改变 %d 个", m2, k2));
        SimClimate.AIRT_SEALEVEL = false;

        SimClimate.ENABLED = false;
        ClimateCoords.ENABLE_AIRMASS = true;
        double[][] wOldOn = new double[nM][];
        for (int i = 0; i < nM; i++) wOldOn[i] = weights(bx[i], bz[i]);
        ClimateCoords.ENABLE_AIRMASS = false;
        double m3 = 0; int k3 = 0;
        for (int i = 0; i < nM; i++) { double[] w = weights(bx[i], bz[i]); m3 = Math.max(m3, maxDiff(wOldOn[i], w)); if (argmax(w) != argmax(wOldOn[i])) k3++; }
        ClimateCoords.ENABLE_AIRMASS = true;
        double m4 = 0; int k4 = 0;
        for (int i = 0; i < nM; i++) { double[] w = weights(bx[i], bz[i]); m4 = Math.max(m4, maxDiff(wBase[i], w)); if (argmax(w) != argmax(wBase[i])) k4++; }
        SimClimate.ENABLED = true;
        say(String.format(LF, "   D3 旧路径 + ENABLE_AIRMASS true->false（**阳性对照**）：权重最大差 %.3e  argmax 改变 %d 个", m3, k3));
        say(String.format(LF, "   D4 旧路径(开关开) vs 新路径基线：权重最大差 %.3e  argmax 改变 %d 个（旧->新 的整段漂移）", m4, k4));
        say("");

        // ---------- E. D66 代理量化 ----------
        say("E. 代理量化：若在**新路径**上恢复气团加项 AIR_DT/AIR_DQ，Tier-1 帐权重会变的点占多少");
        int nChg = 0, nTotal = 0; double sumL1 = 0, mxL1 = 0;
        for (int i = 0; i < nM; i++) {
            ClimateCoords.Coords c = ClimateCoords.sample(bx[i], bz[i], SEED, null);
            int a = (c.airMass >= 0 && c.airMass < 4) ? c.airMass : 0;
            double[] w0 = tier1(c.temp, c.moist);
            double[] w1 = tier1(c.temp + ClimateCoords.AIR_DT[a], c.moist + ClimateCoords.AIR_DQ[a]);
            double l1 = 0;
            for (int q = 0; q < w0.length; q++) l1 += Math.abs(w0[q] - w1[q]);
            sumL1 += l1; mxL1 = Math.max(mxL1, l1);
            if (l1 > 1e-9) nChg++;
            nTotal++;
            if (i < 4) say(String.format(LF, "     airMass=%d temp=%.4f moist=%.4f dT=%+.3f dQ=%+.3f -> Tier-1 L1 = %.4f",
                a, c.temp, c.moist, ClimateCoords.AIR_DT[a], ClimateCoords.AIR_DQ[a], l1));
        }
        say(String.format(LF, "   ⇒ 会变的点 %d / %d (%.1f%%)   平均 L1 %.4f   最大 L1 %.4f", nChg, nTotal, pct(nChg, nTotal), sumL1 / Math.max(1, nTotal), mxL1));
        say("   （⚠ 这是**代理**：只算 Tier-1 帐权重向量，不是真的重跑群系 Kind）");
        say("");

        // ---------- F. 大网格 2x2 普查 ----------
        say("F. 2x2 普查（主网格 = P458 的同一张 480 点网格）");
        SimClimate.AIRT_SEALEVEL = true;
        census("SST 开 / tSea", 1, 1);
        int[] amSea = lastAM; double[] atSea = lastAT; int nSea = lastN;
        SimClimate.AIRT_SEALEVEL = false;
        census("SST 开 / tSfc", 1, 1);
        int[] amSfc = lastAM; double[] atSfc = lastAT;
        classDiff("tSea", amSea, atSea, "tSfc", amSfc, atSfc);
        say("");
        say("   --- 关掉 SST（回到 T_zm 口径；用步长 4 的粗网格，因为无 SST 时 airT 结构上不含空间信号）---");
        OceanWiring.off();
        SimClimate.AIRT_SEALEVEL = false;
        census("SST 关 / tSfc", 4, 4);
        int[] amOffSfc = lastAM; double[] atOffSfc = lastAT;
        SimClimate.AIRT_SEALEVEL = true;
        census("SST 关 / tSea", 4, 4);
        int[] amOffSea = lastAM; double[] atOffSea = lastAT;
        classDiff("tSfc", amOffSfc, atOffSfc, "tSea", amOffSea, atOffSea);
        say("");
        SimClimate.AIRT_SEALEVEL = false;
        OceanWiring.onWorld(SEED);
        say(String.format(LF, "G. 现场复原：AIRT_SEALEVEL=%s  ENABLE_AIRMASS=%s  SimClimate.ENABLED=%s  SST_PROVIDER=%s",
            SimClimate.AIRT_SEALEVEL, ClimateCoords.ENABLE_AIRMASS, SimClimate.ENABLED,
            Atmosphere.SST_PROVIDER == null ? "null" : "已装"));
        say(String.format(LF, "   reentryBlocked = %d（必须 0）", OceanField.reentryBlocked));
        say("⚠ 记账：本探针只测量与开关接线，未改任何物理参数。");
        rep.flush();
        rep.close();
        System.out.println("JAVA_EXIT=0");
    }
}
