package com.EyeOfHarmonyBuffer.Config;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class MainConfig {

    public static double discount = 0.0;
    public static String constantOutputEUConfig = "3812901725648391027364519283746501928374652019384756209183475620193847562019384756201938475620193847562";
    public static boolean GasInPut = true;
    public static boolean EOHinputBusMe = true;
    public static boolean enableFluidOutPut = true;
    public static boolean EOHItemInPut = true;
    public static int EOHtime = 128;
    public static double RecipeChance = 1;
    public static double RecipeYield = 1;
    public static boolean EOHLV = true;

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
        discount = config.get("超维度等离子锻炉", "催化剂减免", discount, "超维度锻炉催化剂减免，数值为0.0-1.0,1.0为没有任何减免")
            .getDouble(discount);

        constantOutputEUConfig = config
            .get("鸿蒙之眼功能", "鸿蒙之眼发电量", constantOutputEUConfig, "鸿蒙之眼发电量修改，每次运行会产出一个固定的值的电量，参数为BigInteger")
            .getString();

        GasInPut = config.get("鸿蒙之眼功能", "鸿蒙之眼流体输入", GasInPut, "鸿蒙之眼配方流体输入控制，控制是否需要输入流体才会工作")
            .getBoolean(GasInPut);

        EOHtime = config.get("鸿蒙之眼功能", "鸿蒙之眼运行时间控制", EOHtime, "控制鸿蒙之眼运行时间为一个固定值，单位为tick")
            .getInt(EOHtime);

        RecipeChance = config.get("鸿蒙之眼功能", "鸿蒙之眼成功率", RecipeChance, "鸿蒙之眼运行成功率设置")
            .getDouble(RecipeChance);

        RecipeYield = config.get("鸿蒙之眼功能", "鸿蒙之眼产出率", RecipeYield, "鸿蒙之眼产出率设置")
            .getDouble(RecipeYield);

        EOHLV = config.get("鸿蒙之眼功能", "鸿蒙之眼配方运行", EOHLV, "鸿蒙之眼配方运行等级修改")
            .getBoolean(EOHLV);

        EOHinputBusMe = config.get("鸿蒙之眼功能", "鸿蒙之眼ME输入总线", EOHinputBusMe, "启用鸿蒙之眼ME输入总线")
            .getBoolean(EOHinputBusMe);

        enableFluidOutPut = config.get("鸿蒙之眼功能", "鸿蒙之眼额外流体产出", enableFluidOutPut, "鸿蒙之眼额外流体产出是否启用")
            .getBoolean(enableFluidOutPut);

        EOHItemInPut = config.get("鸿蒙之眼功能", "额外产出", EOHItemInPut, "鸿蒙之眼额外物品产出是否启用")
            .getBoolean(EOHItemInPut);

        if (config.hasChanged()) {
            config.save();
        }
    }

    public static boolean isDevelopmentEnvironment() {
        String currentDir = System.getProperty("user.dir");
        return currentDir.contains("run");
    }
}