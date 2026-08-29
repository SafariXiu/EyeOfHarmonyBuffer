package com.EyeOfHarmonyBuffer.Config.TalosConfig;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import org.apache.logging.log4j.LogManager;

public final class MacroSelectorHeightConfigSection {

    private static final String CATEGORY = "fieldmanager.macroselector.height";

    public static boolean selectorHeightNoiseEnabled = true;
    public static long selectorHeightNoiseSalt = 0x71A7105DL;
    public static double selectorHeightNoiseFrequency = 0.0009d;
    public static int selectorHeightNoiseOctaves = 3;
    public static double selectorHeightNoiseLacunarity = 2.0d;
    public static double selectorHeightNoiseGain = 0.45d;
    public static double selectorMacroHeightNoiseStrength = 0.65d;
    public static double selectorMicroHeightNoiseStrength = 0.35d;
    public static boolean selectorContinuousHeightField = true;
    public static int selectorHeightControlResolution = 3;

    public MacroSelectorHeightConfigSection() {}

    public static void load(Configuration config) {
        LogManager.getLogger("EyeOfHarmony").info("[CFG] Enter MacroSelectorHeightConfigSection.load");

        selectorHeightNoiseEnabled = config
            .get(CATEGORY, "enabled", selectorHeightNoiseEnabled,
                "是否启用宏群系高度噪声（方案 B）。")
            .getBoolean(selectorHeightNoiseEnabled);

        selectorHeightNoiseSalt = getLongProperty(config, "salt", selectorHeightNoiseSalt,
            "高度噪声 salt（与 patch/rare 区分）。");

        selectorHeightNoiseFrequency = config
            .get(CATEGORY, "frequency", selectorHeightNoiseFrequency,
                "高度噪声频率。")
            .getDouble(selectorHeightNoiseFrequency);

        selectorHeightNoiseOctaves = Math.max(1,
            config.get(CATEGORY, "octaves", selectorHeightNoiseOctaves,
                    "高度噪声叠加层数（>=1）。")
                .getInt(selectorHeightNoiseOctaves)
        );

        selectorHeightNoiseLacunarity = config
            .get(CATEGORY, "lacunarity", selectorHeightNoiseLacunarity,
                "高度噪声频率倍增系数。")
            .getDouble(selectorHeightNoiseLacunarity);

        selectorHeightNoiseGain = config
            .get(CATEGORY, "gain", selectorHeightNoiseGain,
                "高度噪声振幅衰减系数。")
            .getDouble(selectorHeightNoiseGain);

        selectorMacroHeightNoiseStrength = config
            .get(CATEGORY, "macroNoiseStrength", selectorMacroHeightNoiseStrength,
                "噪声对 baseHeight 的影响权重（宏尺度）。")
            .getDouble(selectorMacroHeightNoiseStrength);

        selectorMicroHeightNoiseStrength = config
            .get(CATEGORY, "microNoiseStrength", selectorMicroHeightNoiseStrength,
                "噪声对 microVariance 的影响权重。")
            .getDouble(selectorMicroHeightNoiseStrength);

        selectorContinuousHeightField = config
            .get(CATEGORY, "continuousHeightField", selectorContinuousHeightField,
                "是否启用连续高度控制网（true=使用控制网插值，false=使用站点常量）。")
            .getBoolean(selectorContinuousHeightField);

        selectorHeightControlResolution = Math.max(1,
            config.get(CATEGORY, "heightControlResolution", selectorHeightControlResolution,
                    "控制网分辨率（>=1，表示 N×N 采样点；建议 2 或 3）。")
                .getInt(selectorHeightControlResolution)
        );

        LogManager.getLogger("EyeOfHarmony").info("[CFG] Height category keys now: {}",
            config.getCategory(CATEGORY).getValues().keySet());
    }

    private static long getLongProperty(Configuration config, String key,
                                        long defaultValue, String comment) {
        Property property = config.get(CATEGORY, key, Long.toString(defaultValue), comment);
        String raw = property.getString();

        if (raw == null || raw.trim().isEmpty()) {
            property.set(Long.toString(defaultValue));
            return defaultValue;
        }

        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            property.set(Long.toString(defaultValue));
            return defaultValue;
        }
    }
}
