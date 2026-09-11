package probe;

import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.TalosContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * P215：几何契约（{@link TalosContract}）的**主入口**。
 *
 * 用法：runprobe4.bat P215
 * 输出：p215_report.txt（UTF-8）+ 控制台；末行 CONTRACT_STATUS=PASS/FAIL；退出码 0/1。
 *
 * <h3>判据</h3>
 * 失败集合必须**恰好**等于 {@link #DECLARED_DEFECTS}。清单是"逐条登记"而不是"允许失败"：
 * 多一条失败 = 报错；清单里有一条其实不失败 = **登记过期**，同样报错
 * （避免"修好了但清单没删"这种掩体）。
 *
 * <h3>为什么现在没有"反向哨兵"了（2026-09 变更，必须写清楚）</h3>
 * 本探针原先有两段注入式哨兵：进程内把 {@code BarotropicGyre.WRAP_Y} 置 false，
 * 验证 T4a/T4b 会转绿（证明它们真的在读状态、不是恒假的装饰品）。
 * 那次定案**把开关本身删掉了** ⇒ 已经没有可以注入的状态，哨兵随之退役。
 *
 * 哨兵证明过的东西已经兑现（决策落地了），而新的 T4a 判据升级成了**结构断言**
 * （"这个字段根本不许存在"，反射查字段），与 T6b 守 {@code STEP_CAP} 同一套做法 ——
 * 它的反向自测是"临时把字段加回去"，属于代码改动、无法在进程内注入。
 * 这条取舍是自觉的：**能注入的哨兵随开关一起消失，换来的是生产代码里不再有第二个可能值。**
 */
public class P215 {

    static final int SEED = TalosContract.DEFAULT_SEED;

    /**
     * **已声明的真实缺陷**清单（允许且必须存在的失败行）。修好一条就从这里删一条。
     * 当前为**空集** —— 2026-09 定案后 T4a/T4b 已转绿，两条登记同时删除。
     * 空集意味着：**任何一条失败都是新缺陷**，没有任何豁免。
     */
    static final Set<String> DECLARED_DEFECTS = new LinkedHashSet<String>();

    static File ROOT = new File("K:\\moder\\EyeOfHarmonyBuffer");
    static PrintStream rep;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build\\eoh_probe\\mtn\\p215_report.txt"), "UTF-8");
        boolean ok = false;
        try {
            TalosContract.Result r = TalosContract.selfTest(SEED);
            say(r.format());
            Set<String> failed = failSet(r);
            ok = failed.equals(DECLARED_DEFECTS);
            say("[P215] FAILED_ROWS=" + (r.failedIds().isEmpty() ? "(none)" : r.failedIds()));
            say("[P215] DECLARED_DEFECTS=" + DECLARED_DEFECTS
                + "  （空集 = 任何失败都是新缺陷，没有豁免）");
            say("[P215] EXPECT_MATCH=" + ok);
            if (!ok) {
                Set<String> undeclared = new LinkedHashSet<String>(failed);
                undeclared.removeAll(DECLARED_DEFECTS);
                Set<String> stale = new LinkedHashSet<String>(DECLARED_DEFECTS);
                stale.removeAll(failed);
                say("[P215] 未登记的失败: " + (undeclared.isEmpty() ? "(none)" : undeclared));
                say("[P215] 登记过期（已不失败）: " + (stale.isEmpty() ? "(none)" : stale));
            }
        } finally {
            say("");
            say("[P215] 说明：本探针不再做注入式哨兵 —— 它能注入的那个开关（BarotropicGyre.WRAP_Y）"
                + "已在 2026-09 被删除，T4a 随之升级为\"该字段不得存在\"的结构断言（反射）。");
            say("[P215] CONTRACT_STATUS=" + (ok ? "PASS" : "FAIL"));
            rep.flush();
            rep.close();
        }
        System.exit(ok ? 0 : 1);
    }

    /** 失败行 id 集合。失败为空时 {@code failedIds()} 返回空串，不能直接 split（会得到 {""}）。 */
    static Set<String> failSet(TalosContract.Result r) {
        Set<String> s = new LinkedHashSet<String>();
        String ids = r.failedIds();
        if (ids != null && ids.length() > 0) {
            s.addAll(Arrays.asList(ids.split(",")));
        }
        return s;
    }

    static void say(String s) {
        System.out.println(s);
        rep.println(s);
    }
}
