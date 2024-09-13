package com.EyeOfHarmonyBuffer;

import java.io.File;
import java.math.BigInteger;

import net.minecraftforge.common.config.Configuration;

public class Config {

    public static double discount = 1.0;
    public static String constantOutputEUConfig = "476205893017462093817462093817462093817462093817462093817462";

    public static boolean FluidOutPut = false;

    public static boolean GasInPut = true;

    public static int EOHtime = 256;

    public static double RecipeChance = 1;
    public static double RecipeYield = 1;

    private static Configuration config;

    public static void init(File configFile) {
        if (config == null) {
            config = new Configuration(configFile);
            loadConfig();
        }
    }

    public static void loadConfig() {
        if (isDevelopmentEnvironment()) {
            System.out.println("检测到开发环境，跳过配置文件加载");
            return;
        }

        discount = config.get("超维度等离子锻炉", "催化剂减免", discount, "超维度锻炉催化剂减免，数值为0.0-1.0,1.0为没有任何减免")
            .getDouble(discount);
        constantOutputEUConfig = config
            .get("鸿蒙之眼", "鸿蒙之眼发电量", constantOutputEUConfig, "鸿蒙之眼发电量修改，每次运行会产出一个固定的值的电量，参数为BigInteger，尽情填写你想要的数值吧XD")
            .getString();
        FluidOutPut = config.get("鸿蒙之眼", "鸿蒙之眼流体产出", FluidOutPut, "鸿蒙之眼俄额外流体产出，每次运行产出大量额外流体，默认开启")
            .getBoolean(FluidOutPut);
        GasInPut = config.get("鸿蒙之眼", "鸿蒙之眼流体输入", GasInPut, "鸿蒙之眼配方流体输入控制，控制是否需要输入流体才会工作，请与成功率控制与产出控制搭配使用!默认开启")
            .getBoolean(GasInPut);
        EOHtime = config.get("鸿蒙之眼", "鸿蒙之眼运行时间控制", EOHtime, "控制鸿蒙之眼运行时间为一个固定值，不建议修改，小于128tick会导致游戏延迟暴增!")
            .getInt(EOHtime);
        RecipeChance = config.get("鸿蒙之眼", "鸿蒙之眼成功率", RecipeChance, "鸿蒙之眼运行成功率设置，默认为1，即为每次运行百分百产出")
            .getDouble(RecipeChance);
        RecipeYield = config.get("鸿蒙之眼", "鸿蒙之眼产出率", RecipeYield, "鸿蒙之眼产出率设置，默认为1，即为配方中所有物品全部产出完整的1份")
            .getDouble(RecipeYield);

        if (config.hasChanged()) {
            config.save();
        }
    }

    private static boolean isDevelopmentEnvironment() {
        String currentDir = System.getProperty("user.dir");
        System.out.println("当前工作目录:" + currentDir);

        return currentDir.contains("run");
    }

    public static BigInteger getConstantOutputEU() {
        return new BigInteger(constantOutputEUConfig);
    }

    public static void synchronizeConfiguration(File configFile) {
        init(configFile);
    }
}
