package probe;

import com.EyeOfHarmonyBuffer.sim.export.MapWriter;

import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * P475：**MapWriter 的仪表（D32 的验证）**。
 *
 * <p>为什么要单独一支：MapWriter 已经有 5 支探针在用（P258/P259/P260/P261/P267），
 * 但它们都是「顺便写图」，**没有任何一支断言过写出来的东西的结构**。
 * D32 的三条（stride<=0 死循环 / manifest 双表头 / PrintStream 不查错）正是从这个缝里漏过去的。
 *
 * <p>本探针只做**结构断言**，不做物理计算，所以秒级完成、可以进回归集。
 */
public class P475 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static int pass = 0, fail = 0;

    static void say(String s) { rep.println("[P475] " + s); rep.flush(); System.out.println("[P475] " + s); System.out.flush(); }

    static void check(String what, boolean ok, String detail) {
        if (ok) { pass++; say(String.format(LF, "   [OK]   %s   %s", what, detail)); }
        else { fail++; say(String.format(LF, "   [FAIL] %s   %s", what, detail)); }
    }

    static List<String> lines(File f) throws Exception {
        return Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
    }

    /**
     * 表头行 = 去掉第一个 '#' 之后以 TAB 开头。
     *
     * <p>⚠ E38：这个判据第一版报了 [FAIL] —— 因为**产品代码当时有两种表头形状**：
     * 调用方的 header 印成 "#\tfile\t..."，而内置回退印成 "# file\t..."（井号后是空格）。
     * 我**没有**去放宽判据，而是把产品统一成 TAB 一种形状 —— 判据保持严格。
     * （放宽判据去迁就产品，正是本项目一直在防的那种「让仪器闭嘴」。）
     */
    static int headerLines(List<String> ls) {
        int n = 0;
        for (String s : ls) {
            if (s.length() > 1 && s.charAt(0) == '#' && s.charAt(1) == '\t') n++;
        }
        return n;
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p475_report.txt"), "UTF-8");
        File out = new File(ROOT, "build/eoh_probe/mtn/p475");
        out.mkdirs();
        say("P475：MapWriter 结构断言（D32 的验证仪器）");
        say(String.format(LF, "  输出目录 %s", out.getAbsolutePath()));
        say("");

        // ---- A. manifest：表头只能有一行 ----
        say("A. writeManifest 的表头行数（D32(b)：原来会印两行）");
        String[] hdr = {"file", "field", "unit", "x0", "z0", "dx", "dz", "grid", "range", "colormap"};
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"a.png", "温度", "K", "0", "0", "20000", "200000", "551x101", "230..315", "thermal"});
        rows.add(new String[]{"b.tsv", "降水", "mm/day", "0", "0", "20000", "200000", "551x101", "见文件", "数值"});

        File m1 = new File(out, "manifest_hdr.tsv");
        MapWriter.writeManifest(m1, "P475 测试清单（给了 header）", hdr, rows);
        List<String> l1 = lines(m1);
        int h1 = headerLines(l1);
        check("给了 header 时表头行数 == 1", h1 == 1, "实测 " + h1 + " 行；总行数 " + l1.size());
        check("表头内容 = 调用方给的", l1.size() > 1 && l1.get(1).equals("#\t" + String.join("\t", hdr)),
            l1.size() > 1 ? l1.get(1) : "<空>");
        check("数据行数 == rows.size()", l1.size() == 2 + rows.size(), "实测 " + (l1.size() - 2) + " 行");

        File m2 = new File(out, "manifest_nohdr.tsv");
        MapWriter.writeManifest(m2, "P475 测试清单（没给 header）", new String[0], rows);
        List<String> l2 = lines(m2);
        int h2 = headerLines(l2);
        check("没给 header 时回退到内置 schema，且只有 1 行", h2 == 1, "实测 " + h2 + " 行；" + (l2.size() > 1 ? l2.get(1) : "<空>"));

        // ---- B. tsv ----
        say("");
        say("B. writeTsv 的行数与自述");
        int w = 5, h = 4;
        double[] v = new double[w * h];
        for (int i = 0; i < v.length; i++) v[i] = i * 1.5 - 3;
        File t1 = new File(out, "t_dx.tsv");
        MapWriter.writeTsv(t1, w, h, v, 100, 200, 20_000, 200_000, "测试场 [K]");
        List<String> lt = lines(t1);
        check("tsv 行数 == h + 2", lt.size() == h + 2, "实测 " + lt.size());
        check("tsv 自述含 dx/dz 各自的值", lt.size() > 1 && lt.get(1).contains("step 20000") && lt.get(1).contains("step 200000"),
            lt.size() > 1 ? lt.get(1) : "<空>");
        boolean dxOk = false;
        try { MapWriter.writeTsv(new File(out, "bad.tsv"), w, h, v, 0, 0, 0, 100, "x"); }
        catch (IllegalArgumentException e) { dxOk = true; }
        check("dx<=0 必须抛 IllegalArgumentException", dxOk, dxOk ? "已抛" : "**没抛**");

        // ---- C. png ----
        say("");
        say("C. writePng / writePngWithVectors");
        double[] uxx = new double[w * h], vzz = new double[w * h];
        Arrays.fill(uxx, 5.0); Arrays.fill(vzz, -3.0);
        File p1 = new File(out, "p_plain.png");
        MapWriter.writePng(p1, w, h, v, -5, 10, MapWriter.THERMAL);
        check("writePng 产出非空文件", p1.exists() && p1.length() > 0, p1.length() + " 字节");
        File p2 = new File(out, "p_vec.png");
        MapWriter.writePngWithVectors(p2, w, h, v, -5, 10, MapWriter.THERMAL, uxx, vzz, 2, 0.9, 0.09);
        check("writePngWithVectors 产出非空文件", p2.exists() && p2.length() > 0, p2.length() + " 字节");
        boolean stOk = false;
        try { MapWriter.writePngWithVectors(new File(out, "bad.png"), w, h, v, -5, 10, MapWriter.THERMAL, uxx, vzz, 0, 0.9, 0.9); }
        catch (IllegalArgumentException e) { stOk = true; }
        check("stride<=0 必须抛 IllegalArgumentException（D32(a)：原来会死循环）", stOk, stOk ? "已抛" : "**没抛**");

        // ---- D. 色标边界 ----
        say("");
        say("D. 色标端点（与 D22 各向异性无关，纯值域检查）");
        check("THERMAL(0) 是蓝 (0x0000FF)", MapWriter.THERMAL.rgb(0.0) == 0x0000FF, Integer.toHexString(MapWriter.THERMAL.rgb(0.0)));
        check("THERMAL(1) 是红 (0xFF0000)", MapWriter.THERMAL.rgb(1.0) == 0xFF0000, Integer.toHexString(MapWriter.THERMAL.rgb(1.0)));
        check("DIVERGING(0.5) 是白 (0xFFFFFF)", MapWriter.DIVERGING.rgb(0.5) == 0xFFFFFF, Integer.toHexString(MapWriter.DIVERGING.rgb(0.5)));
        check("clamp01 越界被夹住", MapWriter.clamp01(-1) == 0.0 && MapWriter.clamp01(2) == 1.0, "ok");

        say("");
        say(String.format(LF, "   ⇒ 断言通过 %d，失败 %d", pass, fail));
        say("⚠ 记账：本探针只写自己的测试文件到 build/eoh_probe/mtn/p475/，不碰生产路径。");
        rep.flush();
        System.out.println("JAVA_EXIT=" + (fail == 0 ? 0 : 3));
    }
}
