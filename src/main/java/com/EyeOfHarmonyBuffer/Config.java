package com.EyeOfHarmonyBuffer;

import java.io.File;
import java.math.BigInteger;

import net.minecraftforge.common.config.Configuration;

public class Config {

    public static double discount = 0.0;
    public static String constantOutputEUConfig = "3812901725648391027364519283746501928374652019384756209183475620193847562019384756201938475620193847562";

    public static boolean FluidOutPut = true;

    public static boolean GasInPut = true;

    public static boolean EOHinputBusMe = true;

    public static int EOHtime = 256;

    public static double RecipeChance = 1;
    public static double RecipeYield = 1;
    public static boolean EOHLV = true;
    public static int Fluid = 2000000000;

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

        discount = config.get(
            "超维度等离子锻炉",
            "催化剂减免",
            discount,
            "超维度锻炉催化剂减免，数值为0.0-1.0,1.0为没有任何减免(注:减免不代表你的机器不需要催化剂!他的作用仅仅是不让催化剂消耗，"
                + "例如填0.0也就是完全不消耗催化剂，但是如果你的DTPF检测不到配方需要的足够催化剂，机器仍然不会工作，你需要在输入仓中放有1份材料配方所需要的催化剂机器才会开始工作，尽管他们并不会被消耗哪怕1点)")
            .getDouble(discount);
        constantOutputEUConfig = config
            .get("鸿蒙之眼功能", "鸿蒙之眼发电量", constantOutputEUConfig, "鸿蒙之眼发电量修改，每次运行会产出一个固定的值的电量，参数为BigInteger，尽情填写你想要的数值吧XD")
            .getString();
        FluidOutPut = config.get("鸿蒙之眼流体产出", "鸿蒙之眼流体产出", FluidOutPut, "鸿蒙之眼额外流体产出，每次运行产出额外流体，默认开启")
            .getBoolean(FluidOutPut);
        Fluid = config.get("鸿蒙之眼流体产出", " 鸿蒙之眼流体产出数量", Fluid, "鸿蒙之眼额外流体产出数量设定，范围为int(时空产出也受这个参数的控制)")
            .getInt();
        GasInPut = config.get("鸿蒙之眼功能", "鸿蒙之眼流体输入", GasInPut, "鸿蒙之眼配方流体输入控制，控制是否需要输入流体才会工作，请与成功率控制与产出控制搭配使用!默认开启")
            .getBoolean(GasInPut);
        EOHtime = config.get("鸿蒙之眼功能", "鸿蒙之眼运行时间控制", EOHtime, "控制鸿蒙之眼运行时间为一个固定值，单位为tick，不建议修改，小于128tick会导致游戏延迟暴增!")
            .getInt(EOHtime);
        RecipeChance = config
            .get("鸿蒙之眼功能", "鸿蒙之眼成功率", RecipeChance, "鸿蒙之眼运行成功率设置，默认为1，即为每次运行百分百产出，星阵并行模式下则为所有成功率都为设置的参数")
            .getDouble(RecipeChance);
        RecipeYield = config
            .get("鸿蒙之眼功能", "鸿蒙之眼产出率", RecipeYield, "鸿蒙之眼产出率设置，默认为1，即为配方中所有物品全部产出完整的1份,不建议大于100，会导致机器卡死并且造成世界卡顿")
            .getDouble(RecipeYield);
        EOHLV = config.get("鸿蒙之眼", "鸿蒙之眼配方运行", EOHLV, "鸿蒙之眼配方运行等级修改，无视压缩场等级运行配方!默认开启")
            .getBoolean(EOHLV);
        EOHinputBusMe = config.get("鸿蒙之眼", "鸿蒙之眼ME输入总线", EOHinputBusMe, "启用鸿蒙之眼ME输入总线")
            .getBoolean(EOHinputBusMe);

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
