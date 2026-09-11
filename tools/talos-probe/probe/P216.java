package probe;

import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.TalosContract;
import com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer.PeriodicNoise;

import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * P216：契约断言的**负向自测**——临时制造"Z 折叠"矛盾态，确认相关断言真的会报失败。
 *
 * 用法：runprobe4.bat P216
 * 输出：p216_report.txt（UTF-8）+ 控制台；末行 NEGATIVE_SELFTEST=PASS/FAIL；退出码 0/1。
 *
 * 为什么必须**另起一个 JVM**（不能像 P215 那样在同一进程里翻标志）：
 * V2TerrainGen 的格数是 {@code static final int ... = PeriodicNoise.cellsZFromFreq(...)}，
 * **类初始化时就冻结**。本进程第一句就把 INFINITE_Z 置 false（早于任何 V2TerrainGen 的触碰），
 * 于是"折叠态"是世界初始化时的真实状态，而不是半途改出来的混合态。
 *
 * 判据（**先声明**）：
 *   段A 折叠态：失败集合必须**恰好**是 {T3a,T3b,T4a,T4b}
 *        —— T3a/T3b 是本条哨兵要照出来的**新失败**（开关与格数都不再"不折叠"）；
 *           T4a/T4b 是本来的真实缺陷（WRAP_Y），与本次注入无关。
 *   段B 进程内置回 INFINITE_Z=true：失败集合必须回到 {T4a,T4b}，且 cellsZFromFreq 恢复为负。
 *
 * ★ 本探针同时记录一条**实测反直觉事实**（这是它最有价值的产出）：
 *   把 INFINITE_Z 置 false **并不能**让 T1a（"地形在 z 与 z+1M 必须不同"）变红。
 *   原因：地形在 Z 上的变化有第二条**与周期无关**的来源 ——
 *     大陆场 NoiseContinentGrid 走 PeriodicNoise.value2XZ（该原语根本没有折叠模式），
 *     山层走 TerrainNoise/SimplexNoise2D（无周期概念）。
 *   所以 "Z 不重复地形" 目前由两套独立机制同时保证（过定）；T1a 的可测量性改由
 *   T1b（折叠口径下必须相同）+ T1c（同一点重采样必须相同）两条对照保证。
 *
 * 本探针**不改任何源码/生产默认值**：矛盾态只存在于本进程生命周期内（退出即消失）。
 */
public class P216 {

    static final int SEED = TalosContract.DEFAULT_SEED;
    static File ROOT = new File("K:\\moder\\EyeOfHarmonyBuffer");
    static PrintStream rep;

    public static void main(String[] args) throws Exception {
        // ===== 必须在任何 V2TerrainGen / LandformField / NoiseContinentGrid 触碰【之前】执行 =====
        PeriodicNoise.INFINITE_Z = false;

        rep = new PrintStream(new File(ROOT, "build\\eoh_probe\\mtn\\p216_report.txt"), "UTF-8");
        boolean ok = true;

        say("=== P216 负向自测：临时制造「Z 折叠」矛盾态（仅在【本进程】内）===");
        say("注入：PeriodicNoise.INFINITE_Z=" + PeriodicNoise.INFINITE_Z
            + "（生产默认 true；本进程第一句就改，早于 V2TerrainGen 的类初始化）");
        say("");

        // ---------------- 段 A：折叠态 ----------------
        say("=== 段 A：折叠态下的契约表 ===");
        TalosContract.Result a = TalosContract.selfTest(SEED);
        say(a.format());
        Set<String> aFailed = new LinkedHashSet<String>(Arrays.asList(a.failedIds().split(",")));
        Set<String> wantA = new LinkedHashSet<String>(Arrays.asList("T3a", "T3b", "T4a", "T4b"));
        boolean aOk = aFailed.equals(wantA);
        say("[P216] A_FAILED_ROWS=" + a.failedIds());
        say("[P216] A_EXPECT={T3a,T3b,T4a,T4b} A_EXPECT_MATCH=" + aOk);
        say("[P216] 结论：T3a/T3b 在折叠态下**确实会报失败** ⇒ 这两条不是恒真"
            + "（T4a/T4b 是本来的真实缺陷，与本次注入无关）。");
        say("");
        say("[P216] ★ 反直觉事实（实测，非推断）：T1a **没有**变红 ——");
        say("        " + row(a, "T1a"));
        say("        " + row(a, "T1b"));
        say("        " + row(a, "T1c"));
        say("[P216] 原因：地形在 Z 上的变化还有第二条与周期无关的来源 ——");
        say("        大陆场 NoiseContinentGrid 走 PeriodicNoise.value2XZ（该原语无折叠模式），");
        say("        山层走 TerrainNoise/SimplexNoise2D（无周期概念）。");
        say("[P216] ⇒ 「Z 不重复地形」目前由两套独立机制同时保证（过定）；"
            + "T1a 的可测量性由 T1b/T1c 两条对照保证，而不是靠注入折叠把它弄红。");
        ok &= aOk;

        // ---------------- 段 B：进程内恢复 ----------------
        say("");
        say("=== 段 B：进程内置回 INFINITE_Z=true ===");
        PeriodicNoise.INFINITE_Z = true;
        double probeFreq = 1.0 / (4.0 * 250);
        int cz = PeriodicNoise.cellsZFromFreq(probeFreq);
        say("[P216] 恢复后 cellsZFromFreq(1/1000)=" + cz + "（<0 表示噪声层已恢复不折叠）");
        TalosContract.Result b = TalosContract.selfTest(SEED);
        say(b.format());
        Set<String> bFailed = new LinkedHashSet<String>(Arrays.asList(b.failedIds().split(",")));
        Set<String> wantB = new LinkedHashSet<String>(Arrays.asList("T4a", "T4b"));
        boolean bOk = cz < 0 && bFailed.equals(wantB);
        say("[P216] B_FAILED_ROWS=" + b.failedIds());
        say("[P216] B_EXPECT={T4a,T4b}（T3a/T3b 必须随标志一起转绿） B_EXPECT_MATCH=" + bOk);
        say("[P216] 说明：格数走方法级读标志 ⇒ 立刻恢复；而 V2TerrainGen 的 static final 已冻结。");
        say("[P216] 本进程退出后一切恢复（未改源码、未改生产默认值）。");
        ok &= bOk;

        say("");
        say("[P216] NEGATIVE_SELFTEST=" + (ok ? "PASS" : "FAIL"));
        rep.flush();
        rep.close();
        System.exit(ok ? 0 : 1);
    }

    /** 取一条断言的单行摘要（id / pass / 实测值），用于把关键反直觉事实直接摊在报告里。 */
    static String row(TalosContract.Result r, String id) {
        for (int i = 0; i < r.rows.size(); i++) {
            TalosContract.Row x = r.rows.get(i);
            if (x.id.equals(id)) {
                return id + " pass=" + x.pass + " | 实测: " + x.observed;
            }
        }
        return id + " (not found)";
    }

    static void say(String s) {
        System.out.println(s);
        rep.println(s);
    }
}
