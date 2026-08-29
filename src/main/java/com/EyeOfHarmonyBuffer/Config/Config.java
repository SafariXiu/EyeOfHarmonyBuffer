package com.EyeOfHarmonyBuffer.Config;

import java.io.File;
import java.math.BigInteger;

public final class Config {

    private static boolean initialized;

    private Config() {}

    public static synchronized void init(
        File mainConfigFile,
        File itemsConfigFile,
        File fluidsConfigFile,
        File machineLoaderConfigFile,
        File fieldManagerConfigFile
    ) {
        if (initialized) {
            return;
        }

        MainConfig.init(mainConfigFile);
        ItemConfig.init(itemsConfigFile);
        FluidConfig.init(fluidsConfigFile);
        MachineLoaderConfig.init(machineLoaderConfigFile);
        FieldManagerConfigSpec.init(fieldManagerConfigFile);

        initialized = true;
    }

    public static synchronized void reloadConfig() {
        ensureInitialized();

        MainConfig.reloadConfig();
        ItemConfig.reloadConfig();
        FluidConfig.reloadConfig();
        MachineLoaderConfig.reloadConfig();
        FieldManagerConfigSpec.reloadConfig();
    }

    private static void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException("Config.init() has not been called yet.");
        }
    }

    public static BigInteger getConstantOutputEU() {
        try {
            return new BigInteger(MainConfig.constantOutputEUConfig);
        } catch (NumberFormatException e) {
            System.err.println("无效的数值: " + MainConfig.constantOutputEUConfig);
            return BigInteger.ZERO;
        }
    }
}
