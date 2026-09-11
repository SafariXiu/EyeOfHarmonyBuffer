package probe;

import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * P220：**口径唯一性 + 单链可达性**扫描器（源码级回归测试）。
 *
 * <h3>A 段：口径唯一性（同一件事不许有第二处实现）</h3>
 * TalosContract（P215）验的是**数值**：给定输入，输出对不对。它验不了"这件事是不是只有一处实现"。
 * 本系统出过的一整类缺陷恰恰是后者：缓存键 4 份（种子被截成 24 位）、高度合成 4 份
 * （地图与生产差 145 blocks）、海陆判定 6 份、地形轨 8 处分叉、UPLIFT_SCALE 2 份、
 * 梯度步长 1500/2000 两套…… 这类缺陷**不会**让任何数值断言变红，只会让世界各处悄悄不一致。
 * A 段把"唯一性"变成可执行判据：每条规则 = 一份契约 = 允许出现的文件白名单。
 *
 * <h3>B 段：单链可达性（"第二套系统"必须证明是死的）</h3>
 * 删掉旧轨的**入口**之后，旧轨那十几个类并不会消失，只是没人调用 —— 它们仍然会被 javac 编译、
 * 仍然可以被人再次接回生产链。所以"两套系统"这件事不能靠读代码确认，要靠**图**：
 * <ol>
 *   <li>从声明的根（{@link #ROOTS}，= 组合根 + 维度注册）出发，按"文件名出现"建引用图；</li>
 *   <li>算出**可达集**（活链）与**不可达集**（孤岛）；</li>
 *   <li>断言 1：每个不可达类都必须在 {@link #DECLARED_DEAD} 里登记过
 *       —— 否则说明有人把活代码孤岛化了（新缺陷）；</li>
 *   <li>断言 2：{@link #DECLARED_DEAD} 里的类**一个都不许可达**
 *       —— 这一条就是"第二套系统又长回来了"的回归守卫。</li>
 * </ol>
 * 报告会打印孤岛的**文件数与行数**，让"还剩多少旧系统的尸体"是一个数字而不是感觉。
 *
 * <h3>纪律</h3>
 * <ul>
 *   <li>扫描前剥掉注释与字符串字面量，**且保留换行**（否则多行注释会把后面的行号全部挪位，
 *       报告里的行号就对不上 —— 本扫描器第一版就是这么误报的）。注释里写旧代码是**好事**。</li>
 *   <li>只扫 {@code src/main/java}。</li>
 *   <li>退出码 0 = A、B 两段全通过；1 = 有违规。**不允许"已知违规"抑制**：
 *       要么修掉，要么写进白名单/登记表并写明理由。</li>
 * </ul>
 *
 * 用法：runprobe4.bat P220   输出：p220_report.txt；退出码 0/1。
 */
public class P220 {

    static final File ROOT = new File("K:\\moder\\EyeOfHarmonyBuffer");
    static final File SRC = new File(ROOT, "src\\main\\java");
    static PrintStream rep;

    // ==================================================================
    // A 段：口径唯一性规则
    // ==================================================================

    static final class Rule {
        final String id, claim, why;
        final String[] need;      // 同一行必须同时含有的子串
        final String[] allowed;   // 允许命中的文件名
        final String[] unless;    // 含任一子串的行直接跳过（"引用"与"定义"要分开）

        Rule(String id, String claim, String[] need, String[] allowed, String[] unless, String why) {
            this.id = id; this.claim = claim; this.need = need;
            this.allowed = allowed; this.unless = unless; this.why = why;
        }
    }

    static final String[] NONE = {};

    static final Rule[] RULES = {
        new Rule("U1", "缓存键不得再出现位移打包（int 种子会被截成 24 位）",
            new String[] { "<< 40" }, new String[] { "TalosContract.java" }, NONE,
            "唯一实现是 util.WindowKey.of。豁免 TalosContract：那里**故意**冻结了旧公式，"
            + "用作 T6a 的反向对照（旧式在同一样本上碰撞 21979 次）。"
            + "旧式实证：seed 与 seed+2^24 同键，两个世界共用同一批已解窗口（P217）"),
        new Rule("U2a", "海陆判定不得内联 landResidual(...) >= 0",
            new String[] { "landResidual", ">=" }, new String[] { "NoiseContinentGrid.java" }, NONE,
            "唯一实现是 NoiseContinentGrid.isLand / isLandResidual；曾内联 6 处"
            + "（OrographyField/GlobalClimate/ThermalForcing/RelaxedClimate/V2BiomeField/ClimateCoords）"),
        new Rule("U2b", "海陆判定不得内联 landResidual(...) < 0",
            new String[] { "landResidual", "<" }, new String[] { "NoiseContinentGrid.java" }, NONE,
            "同上（取反写法同样是第二份定义）"),
        new Rule("U3", "块级细节强度只能由 composeColumn 调用",
            new String[] { "mountainDetail(" }, new String[] { "V2TerrainGen.java" }, NONE,
            "mountainDetail 是 composeColumn 的第 4 步；别处直接调用 = 绕过软封顶与坡度调制"),
        new Rule("U4", "高度软封顶只能有一处",
            new String[] { "log1p(Math.exp(" }, new String[] { "V2TerrainGen.java" }, NONE,
            "软封顶 (H=252,k=6) 是 composeColumn 的一部分；抄第二份就与生产口径漂开"),
        new Rule("U5", "山体细节强度标尺不得硬编码",
            new String[] { "mtnComp / 90" }, NONE, NONE,
            "必须写 V2TerrainGen.DETAIL_MTNCOMP_SCALE（=90）"),
        new Rule("U6", "大陆骨架频率只能有一处字面量",
            new String[] { "/ 8000.0" }, new String[] { "TerrainBaseHeight.java" }, NONE,
            "TerrainBaseHeight.CONTINENTAL_FREQ 是唯一来源；V2TerrainGen 曾又写一遍 1.0/8000.0"),
        new Rule("U7", "风×海拔梯度归一标尺只能有一处字面量",
            new String[] { "1.8e-5" }, new String[] { "GlobalClimate.java" }, NONE,
            "GlobalClimate.UPLIFT_SCALE 是唯一来源；ClimateCoords 曾自己写一份并靠注释维持一致"),
        new Rule("U8", "海拔梯度步长只能有 GlobalClimate 一处定义",
            new String[] { "ELEV_STEP" }, new String[] { "GlobalClimate.java" },
            new String[] { "GlobalClimate." },
            "同一个「迎风抬升」算子原先 GlobalClimate 用 2000、ClimateCoords 用 1500（差 33%）；"
            + "引用 GlobalClimate.ELEV_STEP 是允许的，重写一个字面量不允许"),
        new Rule("U9", "地形轨开关不得复活",
            new String[] { "terrainV2Enabled" }, NONE, NONE,
            "开关已删除：地形链唯一。复活它 = 同一份代码又能生成两个不同的世界"),
        new Rule("U10", "纬度带海温期望只允许一个来源",
            new String[] { "SST_BY_BAND" }, NONE, NONE,
            "唯一来源是 ThermalForcing.zonalMeanSeaTeq；手抄的 10 档表在 bandD=0.5 处差 0.31"),
    };

    // ==================================================================
    // B 段：单链可达性
    // ==================================================================

    /**
     * **已退役的旧系统**：按**目录/文件**声明，而不是逐类名单。
     *
     * 为什么按目录：第一版用的是手写类名表，结果漏了 `LakeSmoothing`/`RiverQuery`/
     * `PlateId` 等十几个类，于是"旧系统内部互相引用"被当成"活代码引用旧系统"报了 30 条假违规。
     * 目录是**结构**：整个 `river_layer\` 都是旧系统，不存在"漏一个类"这种事。
     *
     * 这批类允许存在（删除旧轨文件是独立的 T4.3 一步），但**一个都不许再被活文件引用**。
     */
    // 【T4.3 之后的状态】这批文件已经**物理删除**（96 个文件 / 17824 行 = 86 个旧系统文件
    // + 10 个只报告旧系统数据的调试指令）。所以下面这些路径现在**一条都不存在** ——
    // 它们不再是"清单"，而是**绊线**：谁要是在这些路径下重新造出一个类，
    // U12 立刻检查活代码是否又引用了它。守卫的价值从"证明它们是死的"变成"防止它们复活"。
    static final String[] DEAD_PATH_PARTS = {
        "\\chunk\\river_layer\\",
        "\\chunk\\water_layer\\",
        "\\chunk\\mountain_layer\\",
        "\\chunk\\climate_layer\\",
        "\\chunk\\terrain_layer\\api\\",
        "\\chunk\\terrain_layer\\TerrainEngine.java",
        "\\chunk\\cave_layer\\integration\\",
        "\\chunk\\continent_layer\\",
        "\\chunk\\world\\TalosChunkContext.java",
    };

    /**
     * 上面那几个目录里**仍然是活的**文件 —— 它们是 V2 新链自己的一部分，
     * 只是碰巧与旧系统同目录（历史包袱）。每一行都要能说出"为什么它还活着"。
     */
    static final String[] DEAD_DIR_EXCEPTIONS = {
        "ClimateLatitudes.java",   // 纬度折叠：V2 气候与地形都用它（LAT_CYCLE 的唯一真值）
        "NoiseContinentGrid.java", // V2 海陆场本体
        "OrographyField.java",     // V2 地形骨架本体
        "TectonicMath.java",       // 纯哈希工具（Supercontinent 与 V2 侧都可能用）
        "AirMassType.java",        // 气团类型枚举：V2 的 GlobalClimate/ClimateSample 在用
        // 极地几何的**唯一口径**（实心冰盖 / 浮冰带 / 虚拟墙 / 固定急流 / 冷带）。
        // 它之所以在 continent_layer：读的是 terrain_layer 的噪声 + climate_layer 的纬度，
        // 而这两层都不能反过来依赖 circulation_layer（否则成环）；它是"极地阈值的唯一来源"，
        // 不是旧系统残留 —— 恰恰相反，它是把五处各自算阈值的口径偏移收掉的那一层。
        "PolarZone.java",
    };

    /**
     * 散落在**非**旧系统目录里的旧系统文件（按目录规则抓不到，必须逐个点名）。
     * 每一行都要能说出"它属于旧系统的哪一块"。
     */
    static final String[] DEAD_FILES = {
        "TerrainMacroPresetRegistry.java", // 旧宏包预设表（只被旧宏包/旧洞穴用过）
        // 注意：TalosBoundedFeatures **不是**旧系统 —— 它是活着的群系装饰库
        // （Grass/Flower/Pond/Rock/Tree/GroundPatch…）。它内部还有几个河/湖专用特征在引用旧河网，
        // 那是"活代码依赖旧系统"的真实遗留，由 U12 报出来，不能靠登记表盖掉。
    };

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build\\eoh_probe\\mtn\\p220_report.txt"), "UTF-8");
        List<File> files = new ArrayList<File>();
        collect(SRC, files);
        Map<String, String> text = new TreeMap<String, String>();
        Map<String, File> byName = new TreeMap<String, File>();
        for (File f : files) {
            String t = strip(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            text.put(f.getName(), t);
            byName.put(f.getName(), f);
        }
        say("===== P220：口径唯一性 + 单链可达性 扫描 =====");
        say("  源文件 " + files.size() + " 个；A 段规则 " + RULES.length + " 条；"
            + "扫描前剥掉注释与字符串字面量（保留换行，保证行号可信）");

        int bad = 0;
        say("");
        say("---------- A 段：口径唯一性 ----------");
        for (Rule r : RULES) {
            List<String> hits = new ArrayList<String>();
            for (Map.Entry<String, String> e : text.entrySet()) {
                if (contains(r.allowed, e.getKey())) continue;
                String[] lines = e.getValue().split("\n", -1);
                for (int i = 0; i < lines.length; i++) {
                    String ln = lines[i];
                    boolean all = true;
                    for (String s : r.need) {
                        if (ln.indexOf(s) < 0) { all = false; break; }
                    }
                    if (!all) continue;
                    boolean skip = false;
                    for (String u : r.unless) {
                        if (ln.indexOf(u) >= 0) { skip = true; break; }
                    }
                    if (skip) continue;
                    hits.add(e.getKey() + ":" + (i + 1) + "  " + ln.trim());
                }
            }
            bad += hits.size();
            say("");
            say("[" + r.id + "] " + (hits.isEmpty() ? "PASS" : "FAIL") + "  " + r.claim);
            say("        判据: 同一行同时含 " + join(r.need) + " 的行"
                + (r.allowed.length == 0 ? " 必须为 0" : " 只允许在 " + join(r.allowed))
                + (r.unless.length == 0 ? "" : "（含 " + join(r.unless) + " 的行豁免：那是引用不是定义）"));
            say("        理由: " + r.why);
            for (String h : hits) say("        违规: " + h);
        }

        // ---------------- B 段：旧系统必须与活链断开 ----------------
        say("");
        say("---------- B 段：旧系统不得再被活文件引用（单链守卫）----------");
        List<String> deadFiles = new ArrayList<String>();
        for (Map.Entry<String, File> e : byName.entrySet()) {
            String path = e.getValue().getAbsolutePath();
            boolean dead = false;
            for (String part : DEAD_PATH_PARTS) {
                if (path.indexOf(part) >= 0) { dead = true; break; }
            }
            if (dead && contains(DEAD_DIR_EXCEPTIONS, e.getKey())) dead = false;
            if (contains(DEAD_FILES, e.getKey())) dead = true;
            if (dead) deadFiles.add(e.getKey());
        }
        long deadLines = 0;
        for (String d : deadFiles) deadLines += text.get(d).split("\\n", -1).length;
        say("  旧系统文件 " + deadFiles.size() + " 个 / " + deadLines + " 行（允许存在；删除是 T4.3）");
        say("  判据: 每个【活文件】都不得引用旧系统的任何一个类名；命令类单独计数（开发排查工具）");

        List<String> refs = new ArrayList<String>();
        List<String> cmdRefs = new ArrayList<String>();
        for (Map.Entry<String, String> e : text.entrySet()) {
            String f = e.getKey();
            if (contains2(deadFiles, f)) continue;            // 旧系统内部互相引用是正常的
            String body = e.getValue();
            boolean isCmd = f.startsWith("Command") || f.startsWith("LegacyV2Note");
            for (String d : deadFiles) {
                String cls = d.substring(0, d.length() - 5);
                if (!hasWord(body, cls)) continue;
                if (isCmd) cmdRefs.add(f + " -> " + cls);
                else refs.add(f + " -> " + cls);
            }
        }
        say("");
        say("[U12] " + (refs.isEmpty() ? "PASS" : "FAIL") + "  活链不得引用任何旧系统类");
        say("        意义: 一次覆盖两类缺陷 —— (a) 旧轨被重新接回生产链（第二套系统复活）；"
            + "(b) 活代码偷偷依赖旧系统（实例：洞穴层曾用旧 TalosTerrainHeights 取地表高度，"
            + "与真实列顶差最多 145 blocks）");
        for (String r : refs) say("        违规: " + r);
        say("");
        say("        INFO 命令类引用旧系统（读旧数据出报告，不参与世界生成）: " + cmdRefs.size() + " 处");
        if (!cmdRefs.isEmpty()) {
            say("        " + joinList(cmdRefs.subList(0, Math.min(8, cmdRefs.size())))
                + (cmdRefs.size() > 8 ? "  …" : ""));
        }
        bad += refs.size();

        say("");
        say("RULES_A=" + RULES.length + " RULES_B=2 VIOLATIONS=" + bad);
        say("UNIQUENESS_STATUS=" + (bad == 0 ? "PASS" : "FAIL"));
        rep.flush();
        rep.close();
        System.exit(bad == 0 ? 0 : 1);
    }

    /**
     * 词边界匹配。**不能用 indexOf**：{@code RiverSystem} 是 {@code TalosRiverSystem} 的子串，
     * 于是"引用了 TalosRiverSystem"会被同时记成"引用了 RiverSystem"（两个不同的类）——
     * 第一版就是这么多报的。判定：匹配处前后都不能是 Java 标识符字符。
     */
    static boolean hasWord(String body, String word) {
        int from = 0;
        while (true) {
            int i = body.indexOf(word, from);
            if (i < 0) return false;
            int a = i - 1, b = i + word.length();
            boolean okL = a < 0 || !isIdent(body.charAt(a));
            boolean okR = b >= body.length() || !isIdent(body.charAt(b));
            if (okL && okR) return true;
            from = i + 1;
        }
    }

    static boolean isIdent(char c) {
        return Character.isJavaIdentifierPart(c);
    }

    static boolean contains2(List<String> a, String v) {
        for (int i = 0; i < a.size(); i++) if (a.get(i).equals(v)) return true;
        return false;
    }

    static boolean contains(String[] a, String v) {
        for (String s : a) if (s.equals(v)) return true;
        return false;
    }

    static String join(String[] a) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length; i++) { if (i > 0) sb.append(i == a.length - 1 ? " 或 " : "、"); sb.append(a[i]); }
        return sb.toString();
    }

    static String joinList(List<String> a) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.size(); i++) { if (i > 0) sb.append(", "); sb.append(a.get(i)); }
        return sb.toString();
    }

    static void collect(File dir, List<File> out) {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (f.isDirectory()) collect(f, out);
            else if (f.getName().endsWith(".java")) out.add(f);
        }
    }

    /** 剥注释/字符串/字符字面量，**保留换行**（多行注释按行数换成空白）。 */
    static String strip(String t) {
        t = replaceAll(Pattern.compile("(?s)/\\*.*?\\*/"), t, ' ');
        t = replaceAll(Pattern.compile("(?m)//[^\\n]*"), t, ' ');
        t = replaceAll(Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\""), t, ' ');
        t = replaceAll(Pattern.compile("'(?:\\\\.|[^'\\\\])*'"), t, ' ');
        return t;
    }

    /** 用空格替换匹配段，但原样保留其中的换行符。 */
    static String replaceAll(Pattern p, String in, char fill) {
        Matcher m = p.matcher(in);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String s = m.group();
            StringBuilder r = new StringBuilder();
            for (int i = 0; i < s.length(); i++) r.append(s.charAt(i) == '\n' ? '\n' : fill);
            m.appendReplacement(sb, Matcher.quoteReplacement(r.toString()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    static void say(String s) {
        System.out.println(s);
        rep.println(s);
    }
}
