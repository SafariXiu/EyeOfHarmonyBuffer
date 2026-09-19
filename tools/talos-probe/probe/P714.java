package probe;

import java.io.*; import java.util.ArrayList; import java.util.List; import java.util.Locale;

// P714 -- §547 (P1-6 前置)：把「旧口径」与「Boxes 格心口径」的点集直接对比，
//   先量化差异，再决定是否铺开（P1-6 是唯一会改读数的清理项）。
public class P714 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s){ rep.println("[P714] "+s); System.out.println("[P714] "+s); rep.flush(); }

    /** 旧口径：latd = lo+half; latd <= hi; latd += d  */
    static List<Double> oldRule(double lo, double hi, double d, double half){
        List<Double> r = new ArrayList<>();
        for (double v = lo + half; v <= hi + 1e-9; v += d) r.add(v);
        return r;
    }
    /** Boxes 格心口径：v = lo + (i+0.5)*d, i = 0..n-1, n = floor((hi-lo)/d) */
    static List<Double> newRule(double lo, double hi, double d){
        List<Double> r = new ArrayList<>();
        int n = (int) Math.floor((hi - lo) / d);
        for (int i = 0; i < n; i++) r.add(lo + (i + 0.5) * d);
        return r;
    }
    static String brief(List<Double> v){
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < v.size(); i++) { if (i > 0) b.append(","); b.append(String.format(LF,"%.1f", v.get(i))); }
        return b.toString();
    }

    public static void main(String[] a) throws Exception {
        rep = new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p714_report.txt"),"UTF-8");
        say("P714: §547 P1-6 前置 —— 旧口径 vs Boxes 格心口径，点集差异量化");
        say("");
        // 项目里实际用过的组合：{盒, 步长, 旧口径的 half}
        double[][] boxes = {{70,120},{0,30}};
        String[] bn = {"lon 70-120 (ASIA)", "lon 0-30 (SAHARA)"};
        double[][][] steps = {{{15,35,2.5,2.5},{20,35,2.5,2.5}},
                              {{15,35,5.0,2.5},{20,35,5.0,2.5}}};
        String[] sn = {"lat step 2.5 (P699/P700)", "lat step 5.0 (P708/710/711/712/713)"};
        say("  经度侧（half=5.0, d=10.0）：");
        for (int i = 0; i < boxes.length; i++) {
            List<Double> o = oldRule(boxes[i][0], boxes[i][1], 10.0, 5.0);
            List<Double> n = newRule(boxes[i][0], boxes[i][1], 10.0);
            say(String.format(LF, "    %-20s 旧=%d 点 [%s]", bn[i], o.size(), brief(o)));
            say(String.format(LF, "    %-20s 新=%d 点 [%s]   %s", "", n.size(), brief(n),
                o.equals(n) ? "一致" : "【不同】"));
        }
        say("");
        say("  纬度侧：");
        for (int s = 0; s < steps.length; s++) {
            say("    --- " + sn[s]);
            for (int b = 0; b < 2; b++) {
                double lo = steps[s][b][0], hi = steps[s][b][1], d = steps[s][b][2], h = steps[s][b][3];
                List<Double> o = oldRule(lo, hi, d, h);
                List<Double> n = newRule(lo, hi, d);
                say(String.format(LF, "      %-8s 旧=%d 点 [%s]", b==0?"ASIA":"SAHARA", o.size(), brief(o)));
                say(String.format(LF, "      %-8s 新=%d 点 [%s]   %s", "", n.size(), brief(n),
                    o.equals(n) ? "一致" : "【不同】"));
            }
        }
        say("");
        say("  判读：旧口径的末点若【等于盒顶边】(hi)，其格心代表 [hi-d/2, hi+d/2] ⇒ 越界 d/2；");
        say("        格心口径的末点为 hi-d/2 ⇒ 不越界。差异只在 (hi-lo)/d 非整数时出现。");
        say("DONE");
        System.out.println("JAVA_EXIT=0");
    }
}
