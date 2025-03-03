package com.EyeOfHarmonyBuffer.Config;

import java.io.File;
import java.math.BigInteger;

public class Config {

    public static void init(File mainConfigFile, File itemsConfigFile, File fluidsConfigFile,File MachineLoaderConfigFile) {
        MainConfig.init(mainConfigFile);
        ItemConfig.init(itemsConfigFile);
        FluidConfig.init(fluidsConfigFile);
        MachineLoaderConfig.init(MachineLoaderConfigFile);
    }

    public static void reloadConfig() {
        MainConfig.reloadConfig();
        ItemConfig.reloadConfig();
        FluidConfig.reloadConfig();
        MachineLoaderConfig.reloadConfig();
    }

    public static BigInteger getConstantOutputEU() {
        try {
            return new BigInteger(MainConfig.constantOutputEUConfig);
        } catch (NumberFormatException e) {
            System.err.println("无效的数值:" + MainConfig.constantOutputEUConfig);
            return BigInteger.ZERO;
        }
    }
}
