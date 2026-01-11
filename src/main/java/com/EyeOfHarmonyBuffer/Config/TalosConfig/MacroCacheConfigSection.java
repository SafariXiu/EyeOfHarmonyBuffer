package com.EyeOfHarmonyBuffer.Config.TalosConfig;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

public final class MacroCacheConfigSection {

    private static final String CATEGORY = "fieldmanager.macroCache";
    private static final int MIN_MAX_ENTRIES = 32;

    public static boolean macroCacheEnabled = true;
    public static int macroCacheMaxEntries = 512;
    public static boolean macroCacheDiagnostics = true;

    public MacroCacheConfigSection() {}

    public static void load(Configuration config) {
        macroCacheEnabled = config
            .get(CATEGORY, "enabled", macroCacheEnabled,
                "是否启用宏格缓存（true=开启，false=关闭）。")
            .getBoolean(macroCacheEnabled);

        Property maxEntriesProp = config
            .get(CATEGORY, "maxEntries", macroCacheMaxEntries,
                "缓存最大条目数（>=32）。");
        macroCacheMaxEntries = maxEntriesProp.getInt(macroCacheMaxEntries);
        if (macroCacheMaxEntries < MIN_MAX_ENTRIES) {
            macroCacheMaxEntries = MIN_MAX_ENTRIES;
            maxEntriesProp.set(MIN_MAX_ENTRIES);
        }

        macroCacheDiagnostics = config
            .get(CATEGORY, "diagnosticsEnabled", macroCacheDiagnostics,
                "是否启用缓存诊断统计。")
            .getBoolean(macroCacheDiagnostics);
    }
}
