package com.EyeOfHarmonyBuffer.Config;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.minecraftforge.common.config.Configuration;

import com.EyeOfHarmonyBuffer.utils.FluidInfo;
import com.EyeOfHarmonyBuffer.utils.UnitParser;

public class FluidConfig {

    public static List<FluidInfo> outputFluids = new ArrayList<>();
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
        String[] fluidsConfig = config.get(
            "鸿蒙之眼功能",
            "流体列表",
            new String[] { "rawstarmatter:2G", "spatialfluid:2G", "molten.spacetime:2G" },
            "流体列表，每个格式条目为 fluidname:amount 或者 modid:fluidName:amount 来指定支持long级别的物品输出，大于int数量的流体请使用字符来表示，例如100T，100G等方式"
                + "目前支持K(千)，M(百万，B、G(10亿),T(万亿),P(千万亿),E(百亿亿))")
            .getStringList();

        outputFluids.clear();

        for (String fluidConfig : fluidsConfig) {
            fluidConfig = fluidConfig.trim();
            String[] parts = fluidConfig.split(":");
            if (parts.length == 2) {
                String fluidName = parts[0];
                long amount = UnitParser.parseQuantityWithUnits(parts[1]);
                outputFluids.add(new FluidInfo(fluidName, amount));
            }
        }

        if (config.hasChanged()) {
            config.save();
        }
    }
}
