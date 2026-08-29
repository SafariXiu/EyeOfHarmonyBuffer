package com.EyeOfHarmonyBuffer.Config.TalosConfig;

import net.minecraftforge.common.config.Configuration;

public final class MacroSelectorTransitionConfigSection {

    private static final String CATEGORY = "fieldmanager.macroselector.transition";

    public static boolean selectorTransitionEnabled = true;
    public static double selectorTransitionDefaultCoastWidth = 32.0d;
    public static String[] selectorTransitionRules = new String[] {
        "8|32|allowVariants=talos:mountains,talos:plateau,talos:alpine"
    };

    public MacroSelectorTransitionConfigSection() {}

    public static void load(Configuration config) {
        selectorTransitionEnabled = config
            .get(CATEGORY, "enabled", selectorTransitionEnabled,
                "是否启用海岸 Transition 覆写规则。")
            .getBoolean(selectorTransitionEnabled);

        selectorTransitionDefaultCoastWidth = config
            .get(CATEGORY, "defaultCoastWidth", selectorTransitionDefaultCoastWidth,
                "Transition 默认海岸判定宽度（方块）。")
            .getDouble(selectorTransitionDefaultCoastWidth);

        selectorTransitionRules = config
            .get(CATEGORY, "rules", selectorTransitionRules,
                "自定义 Transition 规则，每行格式：<macroId>|<width>|allowVariants=a,b,c")
            .getStringList();
    }
}
