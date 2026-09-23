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
        // ★★★ §585（P2-15 判据来源修正）：拆 NH/SH + 来源改「GPCP 月平均纬向降水峰纬度表」。
        //   ❌ 原版只扫 35~70（**北半球**）⇒ 南半球中纬【从来没被扫过】——调研 §9 第 6 条。
        //   ✅ GPCP v2.3 LTM(1991-2020) 实测锚（本轮独立复算）：
        //        NH 35~70N：DJF 38.75° → JJA 51.25° = 12.5°
        //        SH 35~70S：DJF -41.25° → JJA -56.25° = 15.0°
        //   ⚠ 阈值 >= 10.0 **不改**（它是地板；来源改为 GPCP 表，不再声称出自某篇论文）。
        double[] pkS = peakIn(pS, NZ, 35.0, 70.0), pkW = peakIn(pW, NZ, 35.0, 70.0);
        double[] pkSsh = peakIn(pS, NZ, -70.0, -35.0), pkWsh = peakIn(pW, NZ, -70.0, -35.0);
        double[] itS = peakIn(pS, NZ, 0.0, 25.0), itW = peakIn(pW, NZ, 0.0, 25.0);
        // SH 的「向赤道移动」= |DJF 纬度 - JJA 纬度|（南半球夏 = DJF = thW）
        double shShift = Math.abs(pkWsh[1] - pkSsh[1]);
        // ★★★ §595（用户裁决「换成带平均」）：B2.a 的统计量由【band 内 argmax 值】换成【band 平均】。
        //   为什么换：peakIn 取的是同纬度两个 z 切片的 max，且被 7.2 度格子量化
        //   —— 与 §586 废掉 GATE_B2B_SHIFT 同族（那里是位置，这里是数值）。
        //   ⚠ 为什么【带仍是 35~70】：这一条由【观测】定，不是由方便定。
        //   本地 gpcp_ltm.nc 复算（refs/b2a_band_gpcp.py；2.5 度网格、[lo,hi) 半开、等纬距）：
        //     35-70N    JJA 2.329  DJF 2.488  (DJF-JJA = +0.159)  => 冬 > 夏  ✓
        //     47.5-62.5N JJA 2.534 DJF 2.432  (DJF-JJA = -0.101)  => 夏 > 冬  ✗
        //     35-50N    JJA 2.403  DJF 3.005  (DJF-JJA = +0.602)  => 冬 > 夏（最强）
        //   ⇒ 「冬 > 夏」这条物理的家就在 35~70。若把它放在 47.5~62.5（那里观测是夏更湿），
        //     模型会因为「冬>夏」而 PASS，而观测锚是反的 —— 那就是门与仪器校验矛盾。
        //   ⇒ 带不动，只换统计量（隔离单一改动）。
        double b2aS = band(pS, 35.0, 70.0, NZ), b2aW = band(pW, 35.0, 70.0, NZ);
        say(String.format(LF, "   B2.a 中纬带(35~70 度)【band 平均】冬 > 夏： 夏 %.3f   冬 %.3f   （GPCP 2.329 / 2.488）⇒ %s",
            b2aS, b2aW, b2aW > b2aS ? "达标 ✓" : "**未达标**"));
        say(String.format(LF, "   （诊断·旧口径）band 内 argmax： 夏 %.2f @%.1f   冬 %.2f @%.1f",
            pkS[0], pkS[1], pkW[0], pkW[1]));
        // ★ §595 顺带诊断（**不设门**）：南半球同带。观测同样支持「冬>夏」
        //   （本地复算 35-70S：JJA 2.674 > DJF 2.432）。P296 的 B2.a 历来只覆盖北半球 ⇒
        //   此处只打印，是否升级为门【待裁决】。
        double b2aShW = band(pS, -70.0, -35.0, NZ), b2aShS = band(pW, -70.0, -35.0, NZ);
        say(String.format(LF, "   [SH 诊断·无门] 35~70S band 平均 冬(JJA) %.3f / 夏(DJF) %.3f（GPCP 2.674 / 2.432）",
            b2aShW, b2aShS));
        // ★★★ §586（仪器判决）：B2.b 的 argmax 版【已降级为诊断】—— 它测的是仪器的格子，不是物理。
        //   三条独立理由（任一即足以废掉这个门）：
        //   ① 量化：Zonal.profile 只有 NZ=50 行覆盖 Z_CYCLE=4e7 ⇒ 行心 3.6/10.8/.../90.0，间距恰 7.2 度。
        //      实测四个峰位 46.8/39.6/10.8/3.6 逐位等于行心 ⇒「7.2 度」= 恰好一格。
        //      阈值 >=10 在本仪器上构造不可达（一格 7.2、两格 14.4）⇒ 旧门只可能靠两格跳变偶然通过。
        //   ② 峰位跨切片：三角波令每个纬度在一个周期里出现 2 次（r6 与 r18 都是 +46.8），
        //      而 peakIn 取的是这 2 条的【max】。P296 自己的剖面就已显示 JJA 取 r18=3.83、DJF 取 r19=3.97
        //      —— 两个季节用的是【不同世界切片】的最大值，7.2 度的"迁移"里混着切片差。
        //   ③ 锚侧同样病态：GPCP 的 NH-JJA argmax 落在 41.25~58.75 一条 2.42~2.66（±5%）的平台上，
        //      那个 51.25 是平台上的噪声极大值，不是物理峰。⇒ 两侧的 argmax 都不该做判据。
        say(String.format(LF, "   （诊断·已降级）B2.b argmax 迁移： NH %.1f->%.1f = %.1f 度   SH %.1f->%.1f = %.1f 度",
            pkS[1], pkW[1], pkS[1] - pkW[1], pkSsh[1], pkWsh[1], shShift));
        // §595：本门改用【band 平均】（b2aS/b2aW 在上面的 B2.a 段声明）。
        say(String.format(LF, "  GATE_B2A_PEAK=%s", b2aW > b2aS ? "PASS" : "FAIL"));
        // ---- §586 替代判据：向极 / 向赤道【子带比值】----
        //   物理：每个半球的【夏季】风暴轴降水峰向极移 ⇒ R_夏 > R_冬，其中 R = P(50~70)/P(35~50)。
        //   为何对分辨率稳健：R 连续依赖于带内全部 4~6 行的数值，argmax 只能整格跳；
        //   且 R 用 band() 把每个纬度的 2 个切片平均掉，不挑 max。
        //   锚：本地 gpcp_ltm.nc 独立复算（refs/split_gpcp.py；2.5 度网格、[lo,hi) 半开、等纬距、JJA=6/7/8 月、DJF=12/1/2 月）
        //     NH   R_JJA=0.9456  R_DJF=0.6987（差 +0.247）
        //     SH   R_DJF=0.9264  R_JJA=0.7164（差 +0.210）
        //   ⚠ 判据只用【符号】（夏 > 冬）。两侧差值都在 0.2 以上 ⇒ 符号不是临界量；
        //     本门【不】对绝对值设阈值（不捏造门槛）。
        double rNhS = band(pS, 50.0, 70.0, NZ) / band(pS, 35.0, 50.0, NZ);
        double rNhW = band(pW, 50.0, 70.0, NZ) / band(pW, 35.0, 50.0, NZ);
        double rShW = band(pW, -70.0, -50.0, NZ) / band(pW, -50.0, -35.0, NZ);
        double rShS = band(pS, -70.0, -50.0, NZ) / band(pS, -50.0, -35.0, NZ);
        say(String.format(LF, "   B2.b NH 子带比值 R=P(50~70)/P(35~50)  夏 %.4f / 冬 %.4f（GPCP 0.9456 / 0.6987）⇒ %s",
            rNhS, rNhW, rNhS > rNhW ? "夏>冬 ✓" : "**夏<=冬**"));
        say(String.format(LF, "  GATE_B2B_SPLIT=%s", rNhS > rNhW ? "PASS" : "FAIL"));
        // §694【判据修正】原式是 rShW > rShS（冬>夏），与本段 :149 的物理陈述
        //   「R_夏 > R_冬」以及 :154 的锚相反；而 :163 的 NH 门写的是 rNhS > rNhW（夏>冬）。
        //   证据全在代码自身，不需外部文献。同时修正打印槽位（原来把冬打进「夏」）。
        say(String.format(LF, "   B2.b-SH 子带比值 R=P(50~70S)/P(35~50S)  夏(DJF) %.4f / 冬(JJA) %.4f（GPCP 0.9264 / 0.7164）⇒ %s",
            rShS, rShW, rShS > rShW ? "夏>冬 ✓" : "**夏<=冬**"));
        say(String.format(LF, "  GATE_B2B_SPLIT_SH=%s", rShS > rShW ? "PASS" : "FAIL"));
        say(String.format(LF, "  OLD_B2B_SPLIT_SH_DIAG=%s   （§694 修正前的不等式 rShW>rShS，即冬>夏；与物理相反，仅存档）",
            rShW > rShS ? "PASS" : "FAIL"));
        // ---- §586 极地诊断（**无门**）：P296 的 35~70 带【不含】70 度以上，而那里有未入门的大偏差 ----
        double polNS = band(pS, 70.0, 90.001, NZ), polNW = band(pW, 70.0, 90.001, NZ);
        double polSS = band(pS, -90.001, -70.0, NZ), polSW = band(pW, -90.001, -70.0, NZ);
        say(String.format(LF, "   [极地诊断·无门] 70~90 带  NH 夏 %.3f / 冬 %.3f（GPCP 0.908 / 0.604 ⇒ %+.0f%% / %+.0f%%）",
            polNS, polNW, 100 * (polNS / 0.908 - 1), 100 * (polNW / 0.604 - 1)));
        say(String.format(LF, "   [极地诊断·无门] 70~90 带  SH 夏(DJF) %.3f / 冬(JJA) %.3f（GPCP 0.386 / 0.670 ⇒ %+.0f%% / %+.0f%%）",
            polSW, polSS, 100 * (polSW / 0.386 - 1), 100 * (polSS / 0.670 - 1)));
        say(String.format(LF, "   [判据来源] GPCP v2.3 LTM(1991-2020) 本地 gpcp_ltm.nc 独立复算：refs/split_gpcp.py + refs/polar_and_centroid_gpcp.py"));
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
