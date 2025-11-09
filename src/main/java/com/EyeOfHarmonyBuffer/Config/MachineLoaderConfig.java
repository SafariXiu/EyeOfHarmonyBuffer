package com.EyeOfHarmonyBuffer.Config;

import com.EyeOfHarmonyBuffer.utils.WriteOnceOnly;
import net.minecraftforge.common.config.Configuration;

import java.io.File;

public class MachineLoaderConfig {

    public static boolean VendingMachines = false;

    public static final WriteOnceOnly SubstanceReshapingDevice = new WriteOnceOnly();

    private static Configuration config;

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

    public static void loadConfig() {

        VendingMachines = config
            .get("特殊机器", "大型贸易机", VendingMachines,
                "一个提供了IC贸易箱功能的大型机器,拥有int并行以及不耗电等特性,工作原理与IC贸易箱相同，但是是无限提供标记物,标记物放置在方块控制器中")
            .getBoolean(VendingMachines);

        boolean configValue = config
            .get("特殊机器", "物质重塑仪", false,
                "开启后物质重塑仪造价变为EV，取消所有限制，多方块造价下降(不支持热重载！)")
            .getBoolean(false);

        SubstanceReshapingDevice.set(configValue);

        if (config.hasChanged()) {
            config.save();
        }
    }
}
