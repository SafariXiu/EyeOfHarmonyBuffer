package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.export.MapWriter;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * P261：**目标 4 的完整交付** —— 一次导出全套图 + 数值 + 机器可读清单 + 人读 schema。
 *
 * <p>验收条款是「图 + 数值，schema 明确」。散落的 PNG 不算交付，所以这里：
 * <ol>
 *   <li>把全部场一次导出到统一目录 export/；</li>
 *   <li>每个文件登记元数据（场名/单位/范围/网格/色标）写入 manifest.tsv；</li>
 *   <li>同时写一份 schema.md 说明约定（行列方向、坐标系、单位、如何复算、已知限制）。</li>
 * </ol>
 */
public class P261 {

    static final int SEED = 1022228679;
    /** ⚠ 口径修正（§559，模板 P442:47）：收 {@code long seed} 的入口传本值；收 {@code int worldSeedInt} 的入口仍传裸 {@code SEED}。本次未改任何判据/阈值/输出行。 */
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final int ZC = WorldContract.Z_CYCLE;
    static final int X0 = 0, Z0 = 0, DX = 20_000, DZ = 200_000;   // z 覆盖一整个气候周期 0~20M
    static final int NX = 551, NZ = 101;
    static final int GRAD = 10_000;
    static final int[] CELLS = {600_000, 2_400_000};
    static final int[] TAG = {600, 2400};

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static final File OUT = new File(ROOT, "build/eoh_probe/mtn/export");
    static PrintStream rep;
    static List<String[]> manifest = new ArrayList<String[]>();

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p261_report.txt"), "UTF-8");
        say("P261：目标 4 完整交付（图 + 数值 + manifest + schema）");
        say(String.format(LF, "  输出目录 %s", OUT.getAbsolutePath()));
        say(String.format(LF, "  x=[%d, %d] dx=%dkm   z=[%d, %d] dz=%dkm   网格 %dx%d",
            X0, X0 + (NX - 1) * DX, DX / 1000, Z0, Z0 + (NZ - 1) * DZ, DZ / 1000, NX, NZ));
        say("");
        say(String.format(LF, "   %-8s %-16s %10s %10s %10s %9s", "PLATE", "场", "min", "max", "mean", "耗时ms"));
        double thS = Atmosphere.theta(0.0);
        double thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);

        for (int ci = 0; ci < CELLS.length; ci++) {
            int cell = CELLS[ci];
            String tag = "c" + TAG[ci];
            double[] land = new double[NX * NZ], elev = new double[NX * NZ];
            double[] tS = new double[NX * NZ], tW = new double[NX * NZ], dT = new double[NX * NZ];
            double[] pS = new double[NX * NZ];
            double[] spS = new double[NX * NZ], uS = new double[NX * NZ], vS = new double[NX * NZ];
            double[] spW = new double[NX * NZ], uW = new double[NX * NZ], vW = new double[NX * NZ];
            double[] prS = new double[NX * NZ], prW = new double[NX * NZ];
            long t0 = System.nanoTime();
            for (int r = 0; r < NZ; r++) {
                int z = Z0 + r * DZ;
                for (int c = 0; c < NX; c++) {
                    int x = X0 + c * DX;
                    int i = r * NX + c;
                    land[i] = PlateField.isLandWithCell(x, z, SD, cell) ? 1.0 : 0.0;
                    elev[i] = PlateField.elevationWithCell(x, z, SD, cell);
                    tS[i] = Atmosphere.surfaceTemp(x, z, SD, cell, thS);
                    tW[i] = Atmosphere.surfaceTemp(x, z, SD, cell, thW);
                    dT[i] = tS[i] - tW[i];
                    pS[i] = Atmosphere.pressureAnomaly(x, z, SD, cell, thS);
                    double[] a = Atmosphere.windAt(x, z, SD, cell, thS, GRAD);
                    uS[i] = a[0]; vS[i] = a[1]; spS[i] = Math.hypot(a[0], a[1]);
                    double[] b = Atmosphere.windAt(x, z, SD, cell, thW, GRAD);
                    uW[i] = b[0]; vW[i] = b[1]; spW[i] = Math.hypot(b[0], b[1]);
                    prS[i] = PrecipField.mmPerDay(x, z, SD, cell, thS, GRAD);
                    prW[i] = PrecipField.mmPerDay(x, z, SD, cell, thW, GRAD);
                }
            }
            double ms = (System.nanoTime() - t0) / 1e6;

            reg(tag, "land", "土地掩膜（1=陆）", "", land, 0, 1, MapWriter.THERMAL);
            reg(tag, "elev", "地形海拔", "m", elev, -5000, 3200, MapWriter.DIVERGING);
            reg(tag, "temp_summer", "夏至地表气温", "K", tS, 230, 315, MapWriter.THERMAL);
            reg(tag, "temp_winter", "冬至地表气温", "K", tW, 230, 315, MapWriter.THERMAL);
            reg(tag, "dt_seasonal", "季节温差（夏-冬）", "K", dT, -40, 40, MapWriter.DIVERGING);
            reg(tag, "p_summer", "夏至地面气压距平", "Pa", pS, -900, 900, MapWriter.DIVERGING);
            writePngVec(tag, "wind_summer", "夏至地面风速（叠加风矢量）", "m/s", spS, uS, vS, 0, 20);
            writePngVec(tag, "wind_winter", "冬至地面风速（叠加风矢量）", "m/s", spW, uW, vW, 0, 20);
            reg(tag, "precip_summer", "夏至降水率", "mm/day", prS, 0, 12, MapWriter.THERMAL);
            reg(tag, "precip_winter", "冬至降水率", "mm/day", prW, 0, 12, MapWriter.THERMAL);

            if (ci == 0) {
                tsv(tag, "land", "土地掩膜（1=陆）", "", land);
                tsv(tag, "elev", "地形海拔", "m", elev);
                tsv(tag, "temp_summer", "夏至地表气温", "K", tS);
                tsv(tag, "wind_summer_u", "夏至风 u（东为正）", "m/s", uS);
                tsv(tag, "wind_summer_v", "夏至风 v（北为正）", "m/s", vS);
                tsv(tag, "precip_summer", "夏至降水率", "mm/day", prS);
            }

            say(String.format(LF, "   %-8d %-16s %10.1f %10.1f %10.1f %9.0f", cell, "全套 8 图", min(elev), max(elev), mean(elev), ms));
            say(String.format(LF, "   %-8d %-16s %10.2f %10.2f %10.2f %9s", cell, "  风速 m/s", min(spS), max(spS), mean(spS), ""));
            say(String.format(LF, "   %-8d %-16s %10.1f %10.1f %10.1f %9s", cell, "  陆地 %", mean(land) * 100, mean(land) * 100, mean(land) * 100, ""));
        }

        MapWriter.writeManifest(new File(OUT, "manifest.tsv"),
            "Talos 世界模拟器 导出清单（由 P261 生成；SEED=" + SEED + "）",
            new String[]{"file", "field", "unit", "x0", "z0", "dx", "dz", "grid", "range", "colormap"},
            manifest);
        writeSchema();
        say("");
        say(String.format(LF, "  manifest.tsv 登记 %d 个文件", manifest.size()));
        say("  schema.md 已写出");
        rep.close();
    }

    static void reg(String tag, String name, String field, String unit, double[] v, double lo, double hi,
                    MapWriter.ColorMap cm) throws Exception {
        File f = new File(OUT, "p261_" + tag + "_" + name + ".png");
        MapWriter.writePng(f, NX, NZ, v, lo, hi, cm);
        manifest.add(new String[]{"p261_" + tag + "_" + name + ".png", field, unit, "" + X0, "" + Z0,
            "" + DX, "" + DZ, NX + "x" + NZ, fmt(lo) + ".." + fmt(hi),
            cm == MapWriter.DIVERGING ? "diverging" : "thermal"});
    }

    static void writePngVec(String tag, String name, String field, String unit, double[] sp,
                            double[] u, double[] v, double lo, double hi) throws Exception {
        File f = new File(OUT, "p261_" + tag + "_" + name + ".png");
        // 审计 D22：网格是**各向异性**的（DX=20 km、DZ=200 km）。旧写法只给一个各向同性的 0.9
        // ⇒ (0,10) 的箭头被画成 1980 km 长、**方向失真 10 倍**。
        // 正确：先定「每 (m/s) 折算多少米」的世界基准 T = 0.9*DX，再分别除以 DX / DZ。
        double T = 0.9 * DX;
        MapWriter.writePngWithVectors(f, NX, NZ, sp, lo, hi, MapWriter.THERMAL, u, v, 10,
            T / DX, T / DZ);
        manifest.add(new String[]{"p261_" + tag + "_" + name + ".png", field, unit, "" + X0, "" + Z0,
            "" + DX, "" + DZ, NX + "x" + NZ, fmt(lo) + ".." + fmt(hi), "thermal+vectors"});
    }

    static void tsv(String tag, String name, String field, String unit, double[] v) throws Exception {
        // 审计 D21：旧签名只有一个 step，传 DX 会让自述的 dz 错 10 倍。
        MapWriter.writeTsv(new File(OUT, "p261_" + tag + "_" + name + ".tsv"), NX, NZ, v, X0, Z0, DX, DZ,
            field + (unit.isEmpty() ? "" : " [" + unit + "]"));
        manifest.add(new String[]{"p261_" + tag + "_" + name + ".tsv", field, unit, "" + X0, "" + Z0,
            "" + DX, "" + DZ, NX + "x" + NZ, "见文件", "数值"});
    }

    static String fmt(double d) { return String.format(LF, "%.4g", d); }

    static void writeSchema() throws Exception {
        PrintStream p = new PrintStream(new File(OUT, "schema.md"), "UTF-8");
        p.println("# 导出 schema（由 P261 自动生成，与数据同步）");
        p.println();
        p.println("- 坐标系：x = 东西（东为正，**无限**）；z = 南北（北为正，**无限**）。单位 block（=1 m）。");
        p.println("- 气候在 z 上以 Z_CYCLE = " + ZC + " 重复；z=0 是赤道，z=" + (ZC / 2) + " 是极点；地形不重复。");
        p.println("- 本次导出范围：x = [" + X0 + ", " + (X0 + (NX - 1) * DX) + "]，z = [" + Z0 + ", " + (Z0 + (NZ - 1) * DZ) + "]");
        p.println("- 网格：" + NX + " x " + NZ + "，dx = " + (DX / 1000) + " km，dz = " + (DZ / 1000) + " km");
        p.println();
        p.println("## 文件约定");
        p.println();
        p.println("| 后缀 | 内容 | 行列方向 |");
        p.println("|---|---|---|");
        p.println("| .png | 图 | **第 0 行画在底部**（z 向上），第 0 列在左边（x 向右） |");
        p.println("| .tsv | 数值 | 每行一个 z（从 " + Z0 + " 起，步长 " + DZ + "）；每列一个 x（从 " + X0 + " 起，步长 " + DX + "） |");
        p.println();
        p.println("数值文件第一行是井号开头的场名与单位；PNG 的色标范围见 manifest.tsv 的 range 列。");
        p.println();
        p.println("## 复算方式");
        p.println();
        p.println("全部场都是纯函数，种子 SEED = " + SEED + "：");
        p.println();
        p.println("    terrain : PlateField.elevationWithCell(x, z, SEED, cell)");
        p.println("    land    : PlateField.isLandWithCell(x, z, SEED, cell)");
        p.println("    T_sfc   : Atmosphere.surfaceTemp(x, z, SEED, cell, Theta)");
        p.println("    p_anom  : Atmosphere.pressureAnomaly(x, z, SEED, cell, Theta)");
        p.println("    wind    : Atmosphere.windAt(x, z, SEED, cell, Theta, " + GRAD + ")");
        p.println();
        p.println("cell = PLATE_CELL（本次导出 600000 与 2400000 两套，文件名里的 c600 / c2400 即此）。");
        p.println("Theta = 0 是**北半球夏至**，Theta = pi 是冬至（用 Atmosphere.theta(day) 换算）。");
        p.println();
        p.println("## 已知限制（诚实清单）");
        p.println();
        p.println("1. 风场在海岸线附近有**虚假强风**：p' 的海陆跳变尚未连续化（设计冻结 §27.4），");
        p.println("   沿岸格点风速会撞到 25 m/s 上限。看风图时请以离岸 100 km 以外为准。");
        p.println("2. SST 目前只有纬度依赖，**在 x 方向没有结构**（洋流输运尚未接进大气，§26.4）。");
        p.println("3. 极点附近（|lat| > 85 度）beta -> 0，洋流模块在那里是奇异的（§25.7）。");
        p.println("4. 降水用的是 w_zm(phi-delta_ITCZ) + 局地辐合；**中纬雨带的绝对量偏弱 3~5 倍**");
        p.println("   （w_zm 是瞬变斜压涡动水汽通量辐合的替身，见设计冻结 §37.3）。");
        p.println("5. 本导出**未接入海温异常提供者**（§36 实测它对沿岸风贡献 <2%，可忽略），");
        p.println("   所以 SST 只有纬度结构 + 季节循环。");
        p.close();
    }

    static double min(double[] v) { double m = v[0]; for (double x : v) if (x < m) m = x; return m; }
    static double max(double[] v) { double m = v[0]; for (double x : v) if (x > m) m = x; return m; }
    static double mean(double[] v) { double s = 0; for (double x : v) s += x; return s / v.length; }

    static void say(String s) { System.out.println("[P261] " + s); rep.println("[P261] " + s); }
}
