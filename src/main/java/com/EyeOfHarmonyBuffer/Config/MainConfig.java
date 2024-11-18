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
    public static boolean EOHAstralArrayAmount = true;
    public static boolean EOHZeroPowerStart = true;
    public static boolean EOHSuccessRateControls = true;
    public static boolean EOHOutputRateControl = true;
    public static boolean EOHWorkTime = true;
    public static boolean EOHOpenEuOutPut = true;
    public static boolean DTPFOpen = true;
    public static boolean FOGUpDate = true;
    public static boolean BioVatTrue = true;
    public static boolean DisTankTrue = true;
    public static boolean DigesterMixin = true;
    public static boolean LargeFusionMixin = true;
    public static boolean LargeFusionParaMixin = true;
    public static int LargeFusionPara = 256;
    public static boolean UUMixin = true;
    public static boolean BioLabMixin = true;
    public static boolean SpaceElevatorMiningPlasma = true;
    public static boolean SpaceElevatorMiningParallelsTrue = true;
    public static int SpaceElevatorMiningParallels = 10000;
    public static int SpaceElevatorMiningTicks = 128;
    public static boolean SpaceElevatorMiningTicksTrue = true;
    public static boolean SpaceElevatorAssemblerParallelsTrue = true;
    public static int SpaceElevatorAssemblerParallels = 1024;
    public static boolean SpaceElevatorAssemblerOverClock = true;

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
            .get("鸿蒙之眼发电", "鸿蒙之眼发电量设置", constantOutputEUConfig, "鸿蒙之眼发电量修改，每次运行会产出一个固定的值的电量，参数为BigInteger")
            .getString();

        GasInPut = config.get("鸿蒙之眼功能", "鸿蒙之眼流体输入", GasInPut, "鸿蒙之眼配方流体输入控制，控制是否需要输入流体才会工作，开启后鸿蒙不需要流体输入即可工作")
            .getBoolean(GasInPut);

        EOHtime = config.get("鸿蒙之眼运行时间", "鸿蒙之眼运行时间设置", EOHtime, "控制鸿蒙之眼运行时间为一个固定值，单位为tick")
            .getInt(EOHtime);

        RecipeChance = config.get("鸿蒙之眼成功率", "鸿蒙之眼成功率设置", RecipeChance, "鸿蒙之眼运行成功率大小设置")
            .getDouble(RecipeChance);

        EOHSuccessRateControls = config.get("鸿蒙之眼成功率", "鸿蒙之眼成功率控制", EOHSuccessRateControls, "鸿蒙之眼锁定成功率是否开启")
            .getBoolean(EOHSuccessRateControls);

        RecipeYield = config.get("鸿蒙之眼产出率", "鸿蒙之眼产出率设置", RecipeYield, "鸿蒙之眼产出率大小设置")
            .getDouble(RecipeYield);

        EOHOutputRateControl = config.get("鸿蒙之眼产出率", "鸿蒙之眼产出率控制", EOHOutputRateControl, "鸿蒙之眼锁定产出率是否开启")
            .getBoolean(EOHOutputRateControl);

        EOHLV = config.get("鸿蒙之眼功能", "鸿蒙之眼配方运行", EOHLV, "鸿蒙之眼配方运行等级修改,开启后1级外壳就可以运行任何级别的配方了")
            .getBoolean(EOHLV);

        EOHinputBusMe = config.get("鸿蒙之眼功能", "鸿蒙之眼ME输入总线", EOHinputBusMe, "启用鸿蒙之眼ME输入总线")
            .getBoolean(EOHinputBusMe);

        enableFluidOutPut = config.get("鸿蒙之眼功能", "鸿蒙之眼额外流体产出", enableFluidOutPut, "鸿蒙之眼额外流体产出是否启用")
            .getBoolean(enableFluidOutPut);

        EOHItemInPut = config.get("鸿蒙之眼功能", "额外产出", EOHItemInPut, "鸿蒙之眼额外物品产出是否启用")
            .getBoolean(EOHItemInPut);

        EOHAstralArrayAmount = config.get("鸿蒙之眼功能", "鸿蒙之眼星阵上限", EOHAstralArrayAmount, "鸿蒙之眼星阵上限数量修改，最高上限支持到100万")
            .getBoolean(EOHAstralArrayAmount);

        EOHZeroPowerStart = config.get("鸿蒙之眼功能", "鸿蒙之眼0电启动", EOHZeroPowerStart, "鸿蒙之眼0电量启动，现在它不耗电了!")
            .getBoolean(EOHZeroPowerStart);

        EOHWorkTime = config.get("鸿蒙之眼运行时间", "鸿蒙之眼运行时间控制", EOHWorkTime, "是否启用控制鸿蒙之眼运行时间为一个固定值")
            .getBoolean(EOHWorkTime);

        EOHOpenEuOutPut = config.get("鸿蒙之眼发电", "鸿蒙之眼固定发电量设置", EOHOpenEuOutPut, "是否开启鸿蒙之眼额外EU产出")
            .getBoolean(EOHOpenEuOutPut);

        DTPFOpen = config.get("超维度等离子锻炉", "催化剂减免是否启用", DTPFOpen, "超维度锻炉锁定催化剂减免是否启用")
            .getBoolean(DTPFOpen);

        FOGUpDate = config.get("诸神之锻炉", "诸神之锻炉升级模块", FOGUpDate, "诸神之锻炉升级模块随便点，无视材料，分支，引力子碎片")
            .getBoolean(FOGUpDate);

        BioVatTrue = config.get("其他机器", "细菌培养缸", BioVatTrue, "开启后细菌培养缸持续最大输出不需要保持半满,仅支持传统输出仓，不支持ME输出仓")
            .getBoolean(BioVatTrue);

        DisTankTrue = config.get("其他机器", "溶解罐", DisTankTrue, "开启后溶解罐不需要等比例流体即可工作")
            .getBoolean(DisTankTrue);

        DigesterMixin = config.get("其他机器", "煮解池", DigesterMixin, "开启后煮解池通过线圈等级获得BUFF，提高处理速度与减少时间，并且 不增加额外电力消耗")
            .getBoolean(DigesterMixin);

        LargeFusionMixin = config
            .get("压缩聚变", "压缩聚变能源仓buff", LargeFusionMixin, "开启后提高每个能源仓提供的功率上限，并且锁定最大能量存储为10000000000000EU")
            .getBoolean(LargeFusionMixin);

        LargeFusionParaMixin = config.get("压缩聚变", "开启压缩聚变并行基础值修改", LargeFusionMixin, "开启后支持修改压缩聚变并行基础值,支持1-5级压缩聚变")
            .getBoolean(LargeFusionParaMixin);

        LargeFusionPara = config.get("压缩聚变", "压缩聚变并行基础值修改", LargeFusionPara, "修改压缩聚变并行基础值,原版机器默认64")
            .getInt(LargeFusionPara);

        UUMixin = config.get("其他机器", "大UU", UUMixin, "开启后大UU启用不耗电+int并行")
            .getBoolean(UUMixin);

        BioLabMixin = config.get("其他机器", "生物实验室", BioLabMixin, "开启后所有抽卡成功率为百分百")
            .getBoolean(BioLabMixin);

        SpaceElevatorMiningPlasma = config.get("太空电梯-采矿模块", "采矿模块等离子体", SpaceElevatorMiningPlasma, "开启后采矿模块不再消耗等离子")
            .getBoolean(SpaceElevatorMiningPlasma);

        SpaceElevatorMiningParallels = config.get("太空电梯-采矿模块", "采矿模块并行", SpaceElevatorMiningParallels, "设置采矿模块并行数量")
            .getInt(SpaceElevatorMiningParallels);

        SpaceElevatorMiningParallelsTrue = config
            .get("太空电梯-采矿模块", "采矿模块并行修改", SpaceElevatorMiningParallelsTrue, "开启后可自定义采矿模块并行数量")
            .getBoolean(SpaceElevatorMiningParallelsTrue);

        SpaceElevatorMiningTicks = config.get("太空电梯-采矿模块", "采矿模块运行时间", SpaceElevatorMiningTicks, "设置采矿模块工作时间")
            .getInt(SpaceElevatorMiningTicks);

        SpaceElevatorMiningTicksTrue = config
            .get("太空电梯-采矿模块", "采矿模块运行时间修改", SpaceElevatorMiningTicksTrue, "开启后可自定义采矿模块工作时间")
            .getBoolean(SpaceElevatorMiningTicksTrue);

        SpaceElevatorAssemblerParallelsTrue = config
            .get("太空电梯-组装机模块", "组装机模块并行修改", SpaceElevatorAssemblerParallelsTrue, "开启后可自定义组装机模块并行数量")
            .getBoolean(SpaceElevatorAssemblerParallelsTrue);

        SpaceElevatorAssemblerParallels = config
            .get("太空电梯-组装机模块", "组装机模块并行数", SpaceElevatorAssemblerParallels, "设置组装机模块并行数量")
            .getInt(SpaceElevatorAssemblerParallels);

        SpaceElevatorAssemblerOverClock = config
            .get("太空电梯-组装机模块", "组装机模块超频", SpaceElevatorAssemblerOverClock, "开启组装机模块超频")
            .getBoolean(SpaceElevatorAssemblerOverClock);

        if (config.hasChanged()) {
            config.save();
        }
    }

    public static boolean isDevelopmentEnvironment() {
        String currentDir = System.getProperty("user.dir");
        return currentDir.contains("run");
    }
}
