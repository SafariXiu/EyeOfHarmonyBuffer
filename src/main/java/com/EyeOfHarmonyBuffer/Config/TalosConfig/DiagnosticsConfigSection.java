package com.EyeOfHarmonyBuffer.Config.TalosConfig;

import net.minecraftforge.common.config.Configuration;

public final class DiagnosticsConfigSection {

    private static final String CATEGORY = "fieldmanager.diagnostics";

    public static boolean diagnosticsSampleUsePlayer = false;
    public static boolean diagnosticsSampleUseSpawn = true;
    public static int diagnosticsSampleX = 0;
    public static int diagnosticsSampleZ = 0;

    public DiagnosticsConfigSection() {}

    public static void load(Configuration config) {
        diagnosticsSampleUsePlayer = config
            .get(CATEGORY, "usePlayerPosition", diagnosticsSampleUsePlayer,
                "诊断采样是否使用在线玩家位置（true=使用玩家坐标，false=根据 useSpawn/sampleX/Z）。")
            .getBoolean(diagnosticsSampleUsePlayer);

        diagnosticsSampleUseSpawn = config
            .get(CATEGORY, "useSpawn", diagnosticsSampleUseSpawn,
                "诊断采样是否使用世界重生点（true=使用 spawn，false=使用 sampleX/Z）。")
            .getBoolean(diagnosticsSampleUseSpawn);

        diagnosticsSampleX = config
            .get(CATEGORY, "sampleX", diagnosticsSampleX,
                "diagnosticsUseSpawn=false 时的采样 X 坐标。")
            .getInt(diagnosticsSampleX);

        diagnosticsSampleZ = config
            .get(CATEGORY, "sampleZ", diagnosticsSampleZ,
                "diagnosticsUseSpawn=false 时的采样 Z 坐标。")
            .getInt(diagnosticsSampleZ);
    }
}
