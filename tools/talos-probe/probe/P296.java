package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanWiring;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/** P296：B2 落地复测 —— (A) 本世界陆地占比/kappa 随纬度（子代理怀疑量）；(B) 降水三条带。 */
public class P296 {

    static final int SEED = 1022228679;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    public static void main(String[] args) throws Exception {

        // ── 第三步接线（§162）：验收必须在**生产口径**下跑 ──
        // 生产由 WorldChunkManagerTalos2 -> OceanWiring.onWorld 装 SST'；这里调**同一个入口**，
        // 保证「验收测的」与「生产跑的」是同一段代码（否则就是口径偏移）。
        // 预热是后台线程，本探针不等待 —— 它自己在采样时按行懒解，代价一样。
        OceanWiring.onWorld(SEED);
        System.out.println("[P296] 接线口径：installedSeed=" + com.EyeOfHarmonyBuffer.sim.ocean.OceanField.installedSeed()
            + "  ENABLED=" + com.EyeOfHarmonyBuffer.sim.ocean.OceanField.ENABLED);
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p296_report.txt"), "UTF-8");
        // ★ M7：仪器身份必须自证。§567 已删除旧地形开关 TALOS_TERRAIN，
        //   身份位移到 PlateField.WORLD_IS_TALOS（度量溯源读它；见 PlateField 的 javadoc）。
        say("P296：仪器自证 PlateField.WORLD_IS_TALOS=" + PlateField.WORLD_IS_TALOS
            + "  sd=seedOf(" + SEED + ")=" + SimTerrain.seedOf(SEED)
            + "  PLATE_CELL=" + PlateField.PLATE_CELL);
        int cell = PlateField.PLATE_CELL;
        long sd = SimTerrain.seedOf(SEED);
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);

        say("P296：B2 落地复测 + 本世界陆地占比/kappa 的纬度剖面");
        say("");
        say("A. 本世界的陆地占比与 kappa 均值随纬度（每纬线 " + Zonal.NX + " 个 x 采样，x 跨 "
            + (int) (Zonal.XSPAN / 1000) + " km = 地球纬圈周长）");
        say(String.format(LF, "  %-8s %10s %10s %12s", "纬度", "陆地占比", "kappa均值", "对照：地球"));
        double sumLand = 0, sumKap = 0; int nLat = 0;
        long tSecA = System.nanoTime();   // §333 计时：找出 P296 的瓶颈
        for (int latDeg = 5; latDeg <= 85; latDeg += 5) {
            int z = (int) ((double) WorldContract.zOfLat(latDeg));
            int nLand = 0, n = 0; double ks = 0;
            for (int i = 0; i < Zonal.NX; i++) {
                int x = (int) Math.round((i + 0.5) * Zonal.XSPAN / Zonal.NX);
                if (PlateField.isLandWithCell(x, z, sd, cell)) nLand++;
                ks += Atmosphere.kappaAt(x, z, sd, cell);
                n++;
            }
            double lf = nLand * 100.0 / n, km = ks / n;
            sumLand += lf; sumKap += km; nLat++;
            say(String.format(LF, "  %-8d %9.1f%% %10.3f", latDeg, lf, km));
        }
        say(String.format(LF, "  ⇒ 全球平均：陆地占比 %.1f%%   kappa 均值 %.3f", sumLand / nLat, sumKap / nLat));
        say(String.format(LF, "  对照：PlateField 的 KAPPA_MEAN = %.3f（cell 项的「零纬向平均」参考值）", Atmosphere.KAPPA_MEAN));
        say(String.format(LF, "  ⏱ A 段耗时 = %.1f s   （%d 次 kappaAt + %d 次 isLand）",
            (System.nanoTime() - tSecA) / 1e9, Zonal.NX * 17, Zonal.NX * 17));
        say("");

        say("B. 纬向平均降水（mm/day），夏至 / 冬至");
        say(String.format(LF, "  %-8s %12s %12s %10s", "纬度", "夏至", "冬至", "陆地占比"));
        // ★ 口径唯一化（M13）：降水剖面走 Zonal.profile —— 40,000 km = 地球纬圈周长。
        int NX = Zonal.NX, NZ = Zonal.NZ;
        long tSecB = System.nanoTime();
        double[] pS = Zonal.profile(sd, thS), pW = Zonal.profile(sd, thW);
        int[] lc = new int[NZ];
        for (int r = 0; r < NZ; r++) {
            int z = (int) ((r + 0.5) / NZ * WorldContract.Z_CYCLE);
            int nl = 0;
            for (int c = 0; c < NX; c++) {
                int x = (int) Math.round((c + 0.5) * Zonal.XSPAN / NX);
                if (PlateField.isLandWithCell(x, z, sd, cell)) nl++;
            }
            lc[r] = nl * 100 / NX;
        }
        for (int r = 0; r < NZ; r += 2) {
            int z = (int) ((r + 0.5) / NZ * WorldContract.Z_CYCLE);
            double lat = Math.toDegrees(WorldContract.latOf(z, WorldContract.Z_CYCLE));
            say(String.format(LF, "  %-8.1f %12.2f %12.2f %9d%%", lat, pS[r], pW[r], lc[r]));
        }
        say("");
        say(String.format(LF, "  赤道带(2.5~12.5) 夏 %.2f / 冬 %.2f   副热带(27.5~37.5) 夏 %.2f / 冬 %.2f",
            band(pS, 2.5, 12.5, NZ), band(pW, 2.5, 12.5, NZ), band(pS, 27.5, 37.5, NZ), band(pW, 27.5, 37.5, NZ)));
        say(String.format(LF, "  中纬带(47.5~62.5) 夏 %.2f / 冬 %.2f   中纬/赤道(夏) = %.3f = 1/%.2f",
            band(pS, 47.5, 62.5, NZ), band(pW, 47.5, 62.5, NZ),
            band(pS, 47.5, 62.5, NZ) / band(pS, 2.5, 12.5, NZ), band(pS, 2.5, 12.5, NZ) / band(pS, 47.5, 62.5, NZ)));
        say("");
        // ★★★ B2 季节判据（§95 改口径）★★★
        //   ❌ 旧的「定纬度带(47.5~62.5)平均 冬>夏」已废：它与「带位置随季节移动」互斥，
        //      且 EDDY_MIX 是纯乘子、该比值对它严格不变；真实地球 50N 的 DJF/JJA 也只有 1.06。
        //   ✅ 新口径 = 峰值 + 随带迁移的相对口径。
        say("   ★★★ B2 季节判据（§95 新口径）★★★");
        // ⚠ 必须**分区**取峰值：全球峰值是赤道带（ITCZ），而 B2 判的是**中纬带**。
        double[] pkS = peakIn(pS, NZ, 35.0, 70.0), pkW = peakIn(pW, NZ, 35.0, 70.0);
        double[] itS = peakIn(pS, NZ, 0.0, 25.0), itW = peakIn(pW, NZ, 0.0, 25.0);
        say(String.format(LF, "   B2.a 中纬带(35~70 度)峰值 冬 > 夏： 夏 %.2f @%.1f   冬 %.2f @%.1f   ⇒ %s",
            pkS[0], pkS[1], pkW[0], pkW[1], pkW[0] > pkS[0] ? "达标 ✓" : "**未达标**"));
        say(String.format(LF, "   B2.b 中纬带峰值随季节向赤道移动： %.1f -> %.1f = %.1f 度   ⇒ %s",
            pkS[1], pkW[1], pkS[1] - pkW[1], (pkS[1] - pkW[1]) >= 10.0 ? "达标 ✓ (>=10 度)" : "**不足 10 度**"));
        say(String.format(LF, "  GATE_B2A_PEAK=%s", pkW[0] > pkS[0] ? "PASS" : "FAIL"));
        say(String.format(LF, "  GATE_B2B_SHIFT=%s", (pkS[1] - pkW[1]) >= 10.0 ? "PASS" : "FAIL"));
        say(String.format(LF, "   （诊断）赤道带(0~25 度)峰值： 夏 %.2f @%.1f   冬 %.2f @%.1f",
            itS[0], itS[1], itW[0], itW[1]));
        // ⚠⚠ 2026-09-13 重锚（用户裁决「重锚 按照新的重新测试找到正确值」）：
        // 设计冻结 :5151 写着「EDDY_MIX（锚 = **45~55 度**纬向平均 2.0~2.6 mm/day）」，
        // 而 B2.c 一直测的是 **47.5~62.5 度** —— **判据用了锚以外的纬度带**。
        // ⇒ 这是口径错配（判据 vs 锚），不是模型错。现在判据改用**锚自己的带 45~55 度**，
        //   并把 47.5~62.5 降为诊断（保留历史可读性）。
        // ⚠⚠ 2026-09-13 用户裁决：**B2.c 从「验收项」降为「标定检查」**。
        // 理由：EDDY_MIX 就是被调到让这个数落进锚带的（P457 扫描）⇒ 被拟合的目标不能再当独立验收。
        // 判据锚换成真观测：GPCP v2.2 LTM(1991-2020) 45~55N JJA = 2.565 mm/day（等纬距口径）。
        // 数据：https://downloads.psl.noaa.gov/Datasets/gpcp/precip.mon.ltm.1991-2020.nc
        double b4555 = band(pS, 45.0, 55.0, NZ);
        // ★ §538 修正（P2-19）：原写「[标定检查·非独立] EDDY_MIX 标定目标 45~55 夏 = …」。
        //   而 EDDY_MIX 自 §429 起已是【推导值】：PrecipField.java:169 = 1/EADY_COEF = 3.2258
        //   （:165-167 说明 (L_mix/L_d)^2 = 1/EADY_COEF，**完全由模型自己的 EADY_COEF 导出，零自由度**）。
        //   ⇒ 它【不再是「标定乘子」】⇒ 本行只是【数值对照】，不构成「标定检查」。
        //   CALIBERS.md §8 第 12 条自己就写着「该检查已失效…持续误导」——本条即其修正。
        say(String.format(LF, "   [数值对照·非判据] 45~55 夏 = %.2f，GPCP 观测 2.565（%+.1f%%）"
            + "   ⚠ EDDY_MIX 已是推导值 1/EADY_COEF=3.2258（零自由度），本行不构成标定检查",
            b4555, 100 * (b4555 / 2.565 - 1)));
        say("   ---- 以下是**独立**验收：同一份 GPCP 里没有被 EDDY_MIX 拟合过的量 ----");
        say(String.format(LF, "   [独立·GPCP] 中纬 47.5~62.5  夏 %.2f / 2.534（%+.1f%%）   冬 %.2f / 2.432（%+.1f%%）",
            band(pS, 47.5, 62.5, NZ), 100 * (band(pS, 47.5, 62.5, NZ) / 2.534 - 1),
            band(pW, 47.5, 62.5, NZ), 100 * (band(pW, 47.5, 62.5, NZ) / 2.432 - 1)));
        say(String.format(LF, "   [独立·GPCP] 赤道 2.5~12.5  夏 %.2f / 6.585（%+.1f%%）   冬 %.2f / 3.580（%+.1f%%）",
            band(pS, 2.5, 12.5, NZ), 100 * (band(pS, 2.5, 12.5, NZ) / 6.585 - 1),
            band(pW, 2.5, 12.5, NZ), 100 * (band(pW, 2.5, 12.5, NZ) / 3.580 - 1)));
        say(String.format(LF, "   [独立·GPCP] 副热带 27.5~37.5  夏 %.2f / 2.301（%+.1f%%）  冬 %.2f / 2.391（%+.1f%%）",
            band(pS, 27.5, 37.5, NZ), 100 * (band(pS, 27.5, 37.5, NZ) / 2.301 - 1),
            band(pW, 27.5, 37.5, NZ), 100 * (band(pW, 27.5, 37.5, NZ) / 2.391 - 1)));
        say(String.format(LF, "   （诊断）旧带 47.5~62.5 夏 = %.2f", band(pS, 47.5, 62.5, NZ)));
        say(String.format(LF, "  ⏱ B 段耗时 = %.1f s   （%d 次 mmPerDay = %.3f ms/次）",
            (System.nanoTime() - tSecB) / 1e9, 2 * NX * NZ,
            (System.nanoTime() - tSecB) / 1e6 / (2.0 * NX * NZ)));
        rep.close();
    }

    /** 在 [lo,hi) 度内返回 {峰值, 峰值所在纬度}。**必须分区**：全球峰值是赤道带。 */
    static double[] peakIn(double[] p, int NZ, double lo, double hi) {
        double best = -1; double bestLat = 0;
        for (int r = 0; r < NZ; r++) {
            int z = (int) ((r + 0.5) / NZ * WorldContract.Z_CYCLE);
            double lat = Math.toDegrees(WorldContract.latOf(z, WorldContract.Z_CYCLE));
            if (lat < lo || lat >= hi) continue;
            if (p[r] > best) { best = p[r]; bestLat = lat; }
        }
        return new double[]{best < 0 ? 0 : best, bestLat};
    }

    /**
     * ★ M13：口径唯一化 —— 本函数**不再自己算**，直接委托给 {@link Zonal}
     * （40,000 km = 地球纬圈周长；见设计冻结 §327/§328）。
     * NZ 参数保留只为不改动 14 个调用点；真口径只在 Zonal 里定义一份。
     */
    static double band(double[] p, double lo, double hi, int NZ) {
        return Zonal.band(p, lo, hi);
    }

    static void say(String s) { System.out.println("[P296] " + s); rep.println("[P296] " + s); }
}
