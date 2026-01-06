package com.EyeOfHarmonyBuffer.Config;

import net.minecraftforge.common.config.Configuration;

import java.io.File;

public final class FieldManagerCacheConfig {

    private static Configuration config;

    public static boolean enabled = true;
    public static int maxEntries = 512;
    public static boolean diagnosticsEnabled = true;

    private static final String CATEGORY = "fieldManager.macroCache";

    private FieldManagerCacheConfig() {}

    public static void init(File configFile) {
        if (config == null) {
            config = new Configuration(configFile);
            loadConfig();
        }
    }

    public static void reloadConfig() {
        if (config != null) {
            config.load();
            loadConfig();
        }
    }

    private static void loadConfig() {

        enabled = config.get(
                CATEGORY,
                "enabled",
                enabled,
                "是否启用宏格缓存（true=开启，false=关闭）。"
            )
            .getBoolean(enabled);

        maxEntries = config.get(
                CATEGORY,
                "maxEntries",
                maxEntries,
                "缓存最大条目数（>=32）。"
            )
            .getInt(maxEntries);
        if (maxEntries < 32) {
            maxEntries = 32;
        }

        diagnosticsEnabled = config.get(
                CATEGORY,
                "diagnosticsEnabled",
                diagnosticsEnabled,
                "是否启用缓存诊断统计（决定是否创建 FieldDiagnostics / MacroCacheProbe）。"
            )
            .getBoolean(diagnosticsEnabled);

        if (config.hasChanged()) {
            config.save();
        }
    }
}
