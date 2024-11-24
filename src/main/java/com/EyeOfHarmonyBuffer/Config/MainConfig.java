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
    public static boolean EOHZeroPowerStart = true;
    public static boolean EOHSuccessRateControls = true;
    public static boolean EOHOutputRateControl = true;
    public static boolean EOHWorkTime = true;
    public static boolean EOHOpenEuOutPut = true;

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
        constantOutputEUConfig = config
            .get("鸿蒙之眼发电", "鸿蒙之眼发电量设置", constantOutputEUConfig, "鸿蒙之眼发电量修改，每次运行会产出一个固定的值的电量，参数为BigInteger")
            .getString();

        GasInPut = config
            .get("鸿蒙之眼功能", "鸿蒙之眼流体输入", GasInPut, "鸿蒙之眼配方流体输入控制，控制是否需要输入流体才会工作，开启后鸿蒙不需要流体输入即可工作")
            .getBoolean(GasInPut);

        EOHtime = config
            .get("鸿蒙之眼运行时间", "鸿蒙之眼运行时间设置", EOHtime, "控制鸿蒙之眼运行时间为一个固定值，单位为tick")
            .getInt(EOHtime);

        RecipeChance = config
            .get("鸿蒙之眼成功率", "鸿蒙之眼成功率设置", RecipeChance, "鸿蒙之眼运行成功率大小设置")
            .getDouble(RecipeChance);

        EOHSuccessRateControls = config
            .get("鸿蒙之眼成功率", "鸿蒙之眼成功率控制", EOHSuccessRateControls, "鸿蒙之眼锁定成功率是否开启")
            .getBoolean(EOHSuccessRateControls);

        RecipeYield = config
            .get("鸿蒙之眼产出率", "鸿蒙之眼产出率设置", RecipeYield, "鸿蒙之眼产出率大小设置")
            .getDouble(RecipeYield);

        EOHOutputRateControl = config
            .get("鸿蒙之眼产出率", "鸿蒙之眼产出率控制", EOHOutputRateControl, "鸿蒙之眼锁定产出率是否开启")
            .getBoolean(EOHOutputRateControl);

        EOHLV = config
            .get("鸿蒙之眼功能", "鸿蒙之眼配方运行", EOHLV, "鸿蒙之眼配方运行等级修改,开启后1级外壳就可以运行任何级别的配方了")
            .getBoolean(EOHLV);

        EOHinputBusMe = config
            .get("鸿蒙之眼功能", "鸿蒙之眼ME输入总线", EOHinputBusMe, "启用鸿蒙之眼ME输入总线")
            .getBoolean(EOHinputBusMe);

        enableFluidOutPut = config
            .get("鸿蒙之眼功能", "鸿蒙之眼额外流体产出", enableFluidOutPut, "鸿蒙之眼额外流体产出是否启用")
            .getBoolean(enableFluidOutPut);

        EOHItemInPut = config
            .get("鸿蒙之眼功能", "额外产出", EOHItemInPut, "鸿蒙之眼额外物品产出是否启用")
            .getBoolean(EOHItemInPut);

        EOHZeroPowerStart = config
            .get("鸿蒙之眼功能", "鸿蒙之眼0电启动", EOHZeroPowerStart, "鸿蒙之眼0电量启动，现在它不耗电了!")
            .getBoolean(EOHZeroPowerStart);

        EOHWorkTime = config
            .get("鸿蒙之眼运行时间", "鸿蒙之眼运行时间控制", EOHWorkTime, "是否启用控制鸿蒙之眼运行时间为一个固定值")
            .getBoolean(EOHWorkTime);

        EOHOpenEuOutPut = config
            .get("鸿蒙之眼发电", "鸿蒙之眼固定发电量设置", EOHOpenEuOutPut, "是否开启鸿蒙之眼额外EU产出")
            .getBoolean(EOHOpenEuOutPut);

        if (config.hasChanged()) {
            config.save();
        }
    }

    public static boolean isDevelopmentEnvironment() {
        String currentDir = System.getProperty("user.dir");
        return currentDir.contains("run");
    }
}
