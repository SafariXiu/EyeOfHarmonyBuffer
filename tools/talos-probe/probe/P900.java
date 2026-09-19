package probe;

import java.io.*; import java.lang.reflect.*; import java.util.*;

/**
 * P900 -- 配置 dump（测量溯源 D3 的采集器）。**不是判据门，不打印 GATE_ 行。**
 *
 * <p>WHY THIS EXISTS: PowerShell cannot read a Java static. The 50+ static booleans in
 * sim/** decide what a probe measures, yet none of them entered the measurement identity
 * except the world-identity bit (TALOS_TERRAIN until section 567, now PlateField.WORLD_IS_TALOS)
 * -- so two runs with different switch states were filed under the
 * SAME output directory and one silently deleted the other (rerun_acceptance.ps1:148).
 * The only reliable way to capture their EFFECTIVE values is to let the JVM say them.
 *
 * <p>CLASS LIST IS NOT HARDCODED HERE. It is read from
 * build/eoh_probe/mtn/cfg_classes.txt, which fingerprint_config.ps1 regenerates from the
 * source tree on every run (grep: public static boolean). A hand-maintained list in Java
 * would rot the first time someone adds a switch -- the exact failure mode this whole
 * subsystem exists to prevent.
 *
 * <p>OUTPUT: plain ASCII KEY=VALUE lines between CFG_BEGIN / CFG_END, sorted, one per
 * switch: CFG_&lt;fqcn&gt;.&lt;field&gt;=true|false. Machine-readable, no locale, no Chinese.
 */
public class P900 {
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");

    public static void main(String[] args) throws Exception {
        File listFile = new File(ROOT, "build/eoh_probe/mtn/cfg_classes.txt");
        System.out.println("CFG_BEGIN");
        if (!listFile.isFile()) {
            System.out.println("CFG_ERROR=CLASSES_FILE_MISSING");
            System.out.println("CFG_HINT=run tools/talos-probe/fingerprint_config.ps1 first");
            System.out.println("CFG_END");
            System.out.println("JAVA_EXIT=1");
            return;
        }
        List<String> classes = new ArrayList<String>();
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(listFile), "UTF-8"));
        String ln;
        while ((ln = br.readLine()) != null) {
            String t = ln.trim();
            if (t.length() == 0 || t.startsWith("#")) continue;
            classes.add(t);
        }
        br.close();

        TreeMap<String, String> vals = new TreeMap<String, String>();
        int loadFail = 0;
        for (String cn : classes) {
            try {
                Class<?> c = Class.forName(cn);
                for (Field f : c.getDeclaredFields()) {
                    int m = f.getModifiers();
                    if (Modifier.isStatic(m) && Modifier.isPublic(m) && f.getType() == boolean.class) {
                        vals.put(cn + "." + f.getName(), String.valueOf(f.getBoolean(null)));
                    }
                }
            } catch (Throwable t) {
                loadFail++;
                System.out.println("CFG_CLASS_LOAD_FAIL=" + cn + " : " + t.getClass().getSimpleName());
            }
        }
        for (Map.Entry<String, String> e : vals.entrySet()) {
            System.out.println("CFG_" + e.getKey() + "=" + e.getValue());
        }
        System.out.println("CFG_COUNT=" + vals.size());
        System.out.println("CFG_CLASSES=" + classes.size());
        System.out.println("CFG_LOAD_FAIL=" + loadFail);
        System.out.println("CFG_END");
        System.out.println("JAVA_EXIT=0");
    }
}