package com.EyeOfHarmonyBuffer.Config;

import net.minecraftforge.common.config.Configuration;

import java.io.File;

public class MachineLoaderConfig {

    public static boolean VendingMachines = false;

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

    public static void loadConfig(){

        VendingMachines = config
            .get("特殊机器","大型贸易机",VendingMachines,"一个提供了IC贸易箱功能的大型机器,拥有int并行以及不耗电等特性,工作原理与IC贸易箱相同，但是是无限提供标记物,标记物放置在方块控制器中")
            .getBoolean(VendingMachines);

        if (config.hasChanged()) {
            config.save();
        }
    }
}
