package com.EyeOfHarmonyBuffer.Config;

import com.EyeOfHarmonyBuffer.Config.TalosConfig.*;
import net.minecraftforge.common.config.Configuration;

import java.io.File;

public final class FieldManagerConfigSpec {
    private static Configuration config;
    private static File configFile;

    public static void init(File file) {
        if (config == null) {
            configFile = file;
            config = new Configuration(file);
            loadConfig();
        }
    }

    public static void reloadConfig() {
        ensureInitialized();
        loadConfig();
    }

    private static void loadConfig() {
        config.load();

        MacroCacheConfigSection.load(config);
        MacroSelectorConfigSection.load(config);
        MacroSelectorHeightConfigSection.load(config);
        MacroSelectorTransitionConfigSection.load(config);
        DiagnosticsConfigSection.load(config);
        TerrainConfigSection.load(config);
        ClimateConfigSection.load(config);
        HydroConfigSection.load(config);
        MacroSelectorContinuityConfigSection.load(config);

        if (config.hasChanged()) {
            config.save();
        }
    }

    private static void ensureInitialized() {
        if (config == null) throw new IllegalStateException("Not initialized");
    }
}
