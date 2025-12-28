package com.EyeOfHarmonyBuffer.space.talos;

import com.EyeOfHarmonyBuffer.space.talos.biome.MacroBiomeField;

import java.util.Locale;
import java.util.Objects;

public final class Talos2NoiseConfig {

    private static volatile String activeMacroPreset = "base";

    private Talos2NoiseConfig() {}

    public static MacroBiomeField.MacroBiomeConfig currentMacroConfig() {
        return resolveMacroPreset(activeMacroPreset);
    }

    public static MacroBiomeField.MacroBiomeConfig resolveMacroPreset(String preset) {
        final String key = (preset == null || preset.isEmpty())
            ? activeMacroPreset
            : preset.toLowerCase(Locale.ROOT);
        return MacroBiomeField.MacroBiomeConfig.preset(key);
    }

    public static String getActiveMacroPreset() {
        return activeMacroPreset;
    }

    public static void setActiveMacroPreset(String preset) {
        if (preset == null || preset.isEmpty()) return;
        activeMacroPreset = preset.toLowerCase(Locale.ROOT);
    }

    public static void withMacroPreset(String preset, Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable");
        String previous = activeMacroPreset;
        if (preset != null && !preset.isEmpty()) {
            activeMacroPreset = preset.toLowerCase(Locale.ROOT);
        }
        try {
            runnable.run();
        } finally {
            activeMacroPreset = previous;
        }
    }
}
