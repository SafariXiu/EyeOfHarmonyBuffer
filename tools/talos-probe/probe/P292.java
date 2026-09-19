package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.CoastalLayer;
import com.EyeOfHarmonyBuffer.sim.ocean.GyreRow;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanWiring;

import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Locale;

/** P292：**A3 的用户裁决版读数**（新闭合 hcLocal + 局地海岸切向 coastTangent）。 */
public class P292 {

    static final int SEED = 1022228679;
    /**
     * ⚠⚠ 2026-09-19 修正（C5/D1，**E5a 类口径错误**）：{@code PlateField}/{@code Atmosphere} 要的是
     * <b>派生后</b>的长种子，不是裸世界种子。
     *
     * <p>生产路径：{@code OceanField:205 long seed = SimTerrain.seedOf(worldSeedInt)}，随后
     * {@code GyreRow.solve}(:369) / {@code CoastalLayer.coastTangent}(:385) /
     * {@code Atmosphere.windStress}(:390) <b>全部</b>用它。本探针原来把裸世界种子 {@code SEED}
     * 直接传给这三处 ⇒ 陆地掩膜、κ 场、海岸切向全不同 ⇒ **量的是另一个世界**。
     * 同型错误本仓已有两次先例并已修：E5a（冻结 :6208）；P539（冻结 :22955
     * 「用裸种子 ⇒ 量的不是同一个世界」、:22962「已改为 SimTerrain.seedOf(...) 派生」）。
     *
     * <p>正确性的独立判据：P294 A 段（:53-61）断言
     * {@code V2TerrainGen.composeColumn(..., SEED, ...)} 必须与
     * {@code PlateField.isLandWithCell(x, z, SimTerrain.seedOf(SEED), ...)} 逐位一致
     * ⇒ PlateField 收的是派生种子。
     *
     * <p>{@code OceanWiring.onWorld(SEED)} 仍传<b>世界种子</b>：那个入口按定义收 int 世界种子（生产同样如此）。
     * <p>本次改动**不动任何判据、域、阈值**（仍是 |lat| <= 35 的 24 格、仍 >= 80%）。
     */
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final int ZC = WorldContract.Z_CYCLE;
    static final double TAU0 = 0.0685, H_T = 4000.0, A_H = 1.9e4, H = 5000.0, W = 100_000.0;
    static final int GRAD = 500_000;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    static final int[] LATS = {10, 20, 30, 45, -10, -20, -30, -45};
    /** ★ §95 死区（mm/s）：|东带| 小于它视为「骑零」，不计入方向判据；**原始读数照报**。 */
    static final double A3_DEAD = 0.5;
    static int[] nAlive = new int[LATS.length], nEqAlive = new int[LATS.length];
    static final int NQ = 6;
    static int[][] rX = new int[LATS.length][NQ];
    static int[][] rZ = new int[LATS.length][NQ];
    static boolean[][] rOk = new boolean[LATS.length][NQ];

    public static void main(String[] args) throws Exception {

        // ── 第三步接线（§162）：验收必须在**生产口径**下跑 ──
        // 生产由 WorldChunkManagerTalos2 -> OceanWiring.onWorld 装 SST'；这里调**同一个入口**，
        // 保证「验收测的」与「生产跑的」是同一段代码（否则就是口径偏移）。
        // 预热是后台线程，本探针不等待 —— 它自己在采样时按行懒解，代价一样。
        OceanWiring.onWorld(SEED);
        System.out.println("[P292] 接线口径：installedSeed=" + com.EyeOfHarmonyBuffer.sim.ocean.OceanField.installedSeed()
            + "  ENABLED=" + com.EyeOfHarmonyBuffer.sim.ocean.OceanField.ENABLED);
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p292_report.txt"), "UTF-8");
        int cell = PlateField.PLATE_CELL;
        GyreRow.BandedWind band = new GyreRow.BandedWind(TAU0, ZC);
        say("P292：A3 裁决版读数（新闭合 hcLocal + coastTangent）");
        say(String.format(LF, "  T_up=%.0f 天  L_x=%.0f km  W=%.0f km  G'=%.3f  H1=%.0f  H=%.0f",
            CoastalLayer.UPWELL_SEASON_DAYS, CoastalLayer.UPWELL_WIDTH/1000, W/1000,
            CoastalLayer.G_PRIME, CoastalLayer.H_THERMOCLINE, CoastalLayer.H_TOTAL));

        double f30 = WorldContract.coriolis(Math.toRadians(30.0));
        double hc30 = CoastalLayer.hcLocal(0.06, f30);
        double hcRaw = CoastalLayer.hcLocalRaw(0.06, f30);
        say("");
        say("A. 尺度核对（30 度、tau_s = 0.06 Pa）");
        say(String.format(LF, "  h_c(有界化) = %.1f m   h_c(raw) = %.1f m   R_d = %.1f km",
            hc30, hcRaw, CoastalLayer.rossbyRadius(f30)/1000));
        say(String.format(LF, "  v_max = %.3f m/s   T_E = %.2f Sv   全深均 @100km = %.2f mm/s",
            CoastalLayer.jetPeak(hc30, f30), CoastalLayer.transportSv(hc30, f30),
            CoastalLayer.eastBandContribution(hc30, f30, W)*1000));
        say("  参考：真实沿岸上升流抬升 50~150 m；东边界流表层 0.2~0.5 m/s；沿岸急流 1~2 Sv");
        say("");

        int nOk = 0;
        for (int li = 0; li < LATS.length; li++) {
            int z = (int) ((double) LATS[li] / 90.0 * (ZC / 2));
            for (int q = 0; q < NQ; q++) {
                GyreRow.Params p = new GyreRow.Params();
                p.h = H; p.rhoH = 1025.0 * H_T; p.aH = A_H; p.zCycle = ZC;
                GyreRow.Row row = GyreRow.solve((q * 3_100_000 + li * 811_000) % 9_000_000, z, SD, cell, band, p);
                if (!row.valid) continue;
                if (row.eastX >= p.maxRow - 20_000 || row.westX <= -p.maxRow + 20_000) continue;
                if ((row.eastX - row.westX) < 2 * Math.PI * p.deltaAt(z)) continue;
                rX[li][q] = row.eastX; rZ[li][q] = z; rOk[li][q] = true; nOk++;
            }
        }
        say(String.format(LF, "  合格行 %d / %d", nOk, LATS.length * NQ));
        say("");

        double[] all = new double[400];
        int naAll = 0;
        int[] nS = new int[LATS.length], nEq = new int[LATS.length];
        say("B. 东带 @100 km 全深均（新闭合），按季节 x 纬度（每纬度取合格行的中位）");
        StringBuilder hd = new StringBuilder();
        for (int l : LATS) hd.append(String.format(LF, " %9d", l));
        say("  " + String.format(LF, "%-8s", "季节") + hd);
        for (int si = 0; si < 4; si++) {
            double th = Atmosphere.theta(si * WorldContract.DAYS_PER_YEAR / 4.0);
            StringBuilder sb = new StringBuilder();
            for (int li = 0; li < LATS.length; li++) {
                double lat = WorldContract.latOf(rZ[li][0] == 0 && !rOk[li][0] ? (int)((double)LATS[li]/90.0*(ZC/2)) : rZ[li][0], ZC);
                if (rZ[li][0] == 0) lat = Math.toRadians(LATS[li]);
                double f = WorldContract.coriolis(lat);
                double[] v = new double[NQ];
                int n = 0;
                for (int q = 0; q < NQ; q++) {
                    if (!rOk[li][q]) continue;
                    int z = rZ[li][q];
                    double[] t = CoastalLayer.coastTangent(rX[li][q], z, SD, cell);
                    double[] ts = Atmosphere.windStress(rX[li][q] - 50_000, z, SD, cell, th, GRAD);
                    double tauS = ts[0]*t[0] + ts[1]*t[1];
                    double hc = CoastalLayer.hcLocal(tauS, f);
                    v[n++] = CoastalLayer.eastBandContribution(hc, f, W) * 1000;
                }
                if (n == 0) { sb.append(String.format(LF, " %9s", "-")); continue; }
                double[] vv = Arrays.copyOf(v, n); Arrays.sort(vv);
                double m = vv[n/2];
                sb.append(String.format(LF, " %9.1f", m));
                all[naAll++] = m; nS[li]++;
                boolean eq = LATS[li] >= 0 ? m < 0 : m > 0;
                if (eq) nEq[li]++;
                // ★ §95 死区：|v| < A3_DEAD 的格「骑零」——0.0002 m/s 的流向没有意义。
                //   但**原始读数必须照报**，死区只作为附注，且必须报出被排除的格数。
                if (Math.abs(m) >= A3_DEAD) { nAlive[li]++; if (eq) nEqAlive[li]++; }
            }
            say(String.format(LF, "  %-8s%s", "day=" + (int)(si * WorldContract.DAYS_PER_YEAR / 4.0), sb.toString()));
        }
        say("");
        say("C. 方向正确率拆解（4 季 x 各纬度；北半球向赤道 = 负、南半球向赤道 = 正）");
        int tot = 0, totEq = 0, sub = 0, subEq = 0, sub45 = 0, sub45Eq = 0, sub10 = 0, sub10Eq = 0;
        // 审计 D53：判据域 = |lat| <= 35。±45 的「沿岸风必须向赤道」观测上不成立。
        //
        // ⚠ 2026-09-14 独立复算（`refs\verify_a3_obs.py` + `verify_a3_obs2.py`）：
        //   原文写「ERA5 **2015** 逐月 —— 45N 有 **4/12** 个月向极（Jan/Feb/Oct/Dec）」。
        //   复算暴露两处小错，**但结论不变、而且更强**：
        //     (a) **年份错了**：DODS 切片 `time[900:1:911]` 的真实窗口是
        //         **2014-01..2014-12**（时间轴 = MATLAB datenum；`time[0]` = 1939-01，
        //         `time[900]` = 735600 = 2014-01-01），不是 2015。
        //     (b) **月份数少了 1**：2014 年 45N 实际是 **5/12** 向极（Jan/Feb/**Mar**/Oct/Dec）。
        //   更重要的是把「单年轶事」升级成「多年稳健」——扫 7 年（1995/2000/2005/2010/2014/2018/2022，
        //   共 84 个月）：
        //     NE Pacific 45N  向极 **28/84 = 33.3%**，每年 2~5 个月，**7/7 年都有向极月**；
        //     Chile 45S       向极 **84/84 = 100%**（判据在 45S 恰好成立，属巧合）；
        //     NE Pacific 30N  向极 **0/84 = 0%**，**7/7 年全年向赤道** ← 对照组。
        //   ⇒ **判据域 |lat| <= 35 的取舍是对的**：30N 完美满足「必须向赤道」，
        //     而 45N 有三分之一的月份根本不满足 ⇒ 把 45 度算进分母等于「因为模型对了而扣分」。
        //   本探针的子统计（|lat| 20~30、|lat| 10）本来就排除 45 度，只有合计数没跟上。
        int totC = 0, totEqC = 0, aliveC = 0, eqAliveC = 0;
        for (int li = 0; li < LATS.length; li++) {
            say(String.format(LF, "  lat %+4d : %2d/%2d = %3.0f%%", LATS[li], nEq[li], nS[li], nS[li] > 0 ? nEq[li]*100.0/nS[li] : 0));
            tot += nS[li]; totEq += nEq[li];
            int a = Math.abs(LATS[li]);
            if (a == 20 || a == 30) { sub += nS[li]; subEq += nEq[li]; }
            if (a == 45) { sub45 += nS[li]; sub45Eq += nEq[li]; }
            if (a == 10) { sub10 += nS[li]; sub10Eq += nEq[li]; }
            if (a <= 35) {                       // 审计 D53 判据域
                totC += nS[li]; totEqC += nEq[li];
                aliveC += nAlive[li]; eqAliveC += nEqAlive[li];
            }
        }
        int totAlive = 0, totEqAlive = 0;
        for (int li = 0; li < LATS.length; li++) { totAlive += nAlive[li]; totEqAlive += nEqAlive[li]; }
        say(String.format(LF, "  **合计（|lat| <= 35，判据域 = D53） %d/%d = %.0f%%**（判据 >=80%%）   死区版 %d/%d = %.0f%%",
            totEqC, totC, totEqC*100.0/Math.max(1,totC), eqAliveC, aliveC, eqAliveC*100.0/Math.max(1,aliveC)));
        say(String.format(LF, "  GATE_A3_DIRECTION=%s", totEqC*100.0/Math.max(1,totC) >= 80.0 ? "PASS" : "FAIL"));
        say(String.format(LF, "  （诊断）含 ±45 度的旧合计 %d/%d = %.0f%% —— **已废**（D53：那条判据在 45 度观测上不成立）",
            totEq, tot, totEq*100.0/Math.max(1,tot)));
        say(String.format(LF, "  **合计（死区 |v| < %.1f mm/s 不计） %d/%d = %.0f%%**   被排除 %d 格 = %.0f%%",
            A3_DEAD, totEqAlive, totAlive, totEqAlive*100.0/Math.max(1,totAlive), tot - totAlive, (tot - totAlive)*100.0/Math.max(1,tot)));
        say("  ★ 报告纪律：**两个数都要报**，不许只报死区后的。死区只处理「流向无意义」的骑零格。");
        say(String.format(LF, "  其中 |lat| 20~30 度：**%d/%d = %.0f%%**   ← 海洋侧能管的部分", subEq, sub, subEq*100.0/Math.max(1,sub)));
        say(String.format(LF, "       |lat| 10 度   ：%d/%d = %.0f%%   ← **挂起**（大气侧：模型给 18.6 m/s 向极风）", sub10Eq, sub10, sub10Eq*100.0/Math.max(1,sub10)));
        say(String.format(LF, "       |lat| 45 度   ：%d/%d = %.0f%%   ← **不计入**（真实东北太平洋冬季南风是对的）", sub45Eq, sub45, sub45Eq*100.0/Math.max(1,sub45)));
        say("");
        double[] aa = Arrays.copyOf(all, naAll);
        Arrays.sort(aa);
        say(String.format(LF, "D. 量级分布（新锚）：n=%d  p10 %.1f  p25 %.1f  **p50 %.1f**  p75 %.1f  p90 %.1f  max %.1f mm/s",
            naAll, aa[(int)(0.10*naAll)], aa[(int)(0.25*naAll)], aa[naAll/2], aa[(int)(0.75*naAll)], aa[(int)(0.90*naAll)], aa[naAll-1]));
        say("  参考（§79 子代理读数）：p50 = 5.5、p90 = 34 mm/s；旧锚 20 mm/s 达标率 44%（错的闭合）");
        rep.close();
    }

    static void say(String s) { System.out.println("[P292] " + s); rep.println("[P292] " + s); }
}
