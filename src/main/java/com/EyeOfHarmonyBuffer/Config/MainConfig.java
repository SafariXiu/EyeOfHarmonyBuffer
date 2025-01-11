package com.EyeOfHarmonyBuffer.Config;

import java.io.File;

import com.EyeOfHarmonyBuffer.Mixins.HIPCompressorMixin;
import net.minecraftforge.common.config.Configuration;

public class MainConfig {

    public static String constantOutputEUConfig = "3812901725648391027364519283746501928374652019384756209183475620193847562019384756201938475620193847562";
    public static boolean GasInPut = true;
    public static boolean EOHinputBusMe = true;
    public static boolean EOHinputHatchEnable= true;
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
    public static boolean NaquadahFuelRefineryMixinTrue = true;
    public static int NaquadahFuelRefineryMagnification = 10000;
    public static boolean NaquadahFuelOutPutMagnificationTrue = true;
    public static int NaquadahFuelOutPutMagnification = 10000;
    public static boolean BlackHoleCompressorStabilityLock = true;
    public static boolean BlackHoleCompressorParallelModificationEnabled = true;
    public static int BlackHoleCompressorParallelCountModification = 2000000000;
    public static boolean BlackHoleCompressorPowerConsumptionModificationEnabled = true;
    public static String BlackHoleCompressorPowerConsumptionModification = "0.0";
    public static boolean BlackHoleCompressorTimeConsumptionModificationEnabled = true;
    public static String BlackHoleCompressorTimeConsumptionModification = "0.0";
    public static boolean IndustrialLaserEngraverParallelEnabled = true;
    public static boolean IndustrialLaserEngraverOverclockEnabled = false;
    public static boolean MaskInfiniteDurability = true;
    public static boolean Grade8WaterPurificationEnabled = true;
    public static boolean Grade7WaterPurificationEnabled = true;
    public static boolean Grade6WaterPurificationEnabled = true;
    public static boolean Grade5WaterPurificationEnabled = true;
    public static boolean Grade4WaterPurificationEnabled = true;
    public static boolean Grade3WaterPurificationEnabled = true;
    public static boolean Grade2WaterPurificationEnabled = true;
    public static boolean Grade1WaterPurificationEnabled = true;
    public static boolean DTPFMK2Enable = true;
    public static boolean HIPCompressorEnable = true;
    public static boolean ExoticModuleEnable = true;
    public static int SpaceElevatorAssemblerParallel = 1024;
    public static boolean SpaceElevatorAssemblerParallelEnable = true;
    public static boolean SpaceElevatorMiningParallelsEnable = true;
    public static boolean SpaceElevatorMiningPlasmaEnable = true;
    public static int SpaceElevatorModuleMiningParallels = 128;
    public static int SpaceElevatorMiningTicks = 128;
    public static boolean Water = true;
    public static boolean SpaceElevatorMiningTicksTrue = true;
    public static boolean OutPutHatchMEEnable = true;
    public static boolean OutPutBusMEEnable = true;
    public static boolean TargetChamberEnable = true;
    public static boolean TargetChamberParallelEnable = true;
    public static int TargetChamberParallel = 256;
    public static boolean PlanetaryGasSiphonEnable = true;
    public static int PlanetaryGasSiphonParallel = 10;
    public static boolean IndustrialDehydratorEnable = true;
    public static boolean FrothFlotationCellEnable = true;
    public static boolean IsaMillEnable = true;
    public static boolean FOGGravitonShardEnable = true;
    public static boolean EOHGemEnable = true;
    public static String EOHGem = "2T";
    public static boolean PCBFactoryParallelEnable = true;
    public static boolean PCBFactoryCoolantEnable = true;
    public static boolean CircuitAssemblyLineEnable = true;
    public static boolean LightningSpireEnable = true;
    public static int LightningSpireTime = 256;

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
        TargetChamberEnable = config
            .get("靶室","靶室运行设定",TargetChamberEnable,"开启后删除光束流等机器需求，只保留物品输入检测，并且机器不再消耗电力，耗时为原来的二十分之一")
            .getBoolean(TargetChamberEnable);

        TargetChamberParallelEnable = config
            .get("靶室","靶室并行数量修改开启",TargetChamberParallelEnable,"开启靶室并行数量修改(靶室运行设定必须为True！)")
            .getBoolean(TargetChamberParallelEnable);

        TargetChamberParallel = config
            .get("靶室","靶室并行数量修改",TargetChamberParallel,"设置靶室并行数量，最大不超过100万，超过100万取100万")
            .getInt(TargetChamberParallel);

        CircuitAssemblyLineEnable = config
            .get("其他机器","电路装配线",CircuitAssemblyLineEnable,"开启后电路装配线不再耗电并且大幅度提升并行")
            .getBoolean(CircuitAssemblyLineEnable);

        PCBFactoryParallelEnable = config
            .get("PCB工厂","PCB工厂并行修改开启",PCBFactoryParallelEnable,"开启后PCB工厂最大并行数量锁定为int")
            .getBoolean(PCBFactoryParallelEnable);

        PCBFactoryCoolantEnable = config
            .get("PCB工厂","PCB工厂不消耗冷却液开启",PCBFactoryCoolantEnable,"开启后PCB工厂不再消耗冷却液，并行数量不再依赖冷却液数量")
            .getBoolean(PCBFactoryCoolantEnable);

        IndustrialDehydratorEnable = config
            .get("稀土线","真空干燥炉",IndustrialDehydratorEnable,"开启后增强真空干燥炉到50倍速，不耗电，200万并行")
            .getBoolean(IndustrialDehydratorEnable);

        FrothFlotationCellEnable = config
            .get("稀土线","浮选机",FrothFlotationCellEnable,"开启后增强浮选机到50倍速，不耗电，200万并行")
            .getBoolean(FrothFlotationCellEnable);

        IsaMillEnable = config
            .get("稀土线","艾萨研磨机",IsaMillEnable,"开启后增强艾萨研磨机到50倍速，不耗电，200万并行，研磨球不会损耗")
            .getBoolean(IsaMillEnable);

        PlanetaryGasSiphonEnable = config
            .get("行星气体钻机","行星气体钻机产出倍率修改开启",PlanetaryGasSiphonEnable,"开启行星气体钻机产出修改")
            .getBoolean(PlanetaryGasSiphonEnable);

        PlanetaryGasSiphonParallel = config
            .get("行星气体钻机","行星气体钻机产出倍率",PlanetaryGasSiphonParallel,"设置行星气体钻机产出倍率")
            .getInt(PlanetaryGasSiphonParallel);

        OutPutBusMEEnable = config
            .get("ME总线","ME输出总线",OutPutBusMEEnable,"开启后为无限存储量（Long.MAX_VALUE）")
            .getBoolean(OutPutBusMEEnable);

        OutPutHatchMEEnable = config
            .get("ME总线","ME输出仓",OutPutHatchMEEnable,"开启后为无限存储量（Long.MAX_VALUE）")
            .getBoolean(OutPutHatchMEEnable);

        SpaceElevatorAssemblerParallel = config
            .get("太空电梯-组装机模块","组装机模块并行数量设置",SpaceElevatorAssemblerParallel,"设置组装机模块并行数量")
            .getInt(SpaceElevatorAssemblerParallel);

        SpaceElevatorAssemblerParallelEnable = config
            .get("太空电梯-组装机模块","组装机模块并行数量修改开启",SpaceElevatorAssemblerParallelEnable,"开启组装机并行数量修改")
            .getBoolean(SpaceElevatorAssemblerParallelEnable);

        SpaceElevatorMiningParallelsEnable = config
            .get("太空电梯-采矿模块","采矿模块并行数量修改开启",SpaceElevatorMiningParallelsEnable,"开启采矿模块并行数量修改")
            .getBoolean(SpaceElevatorMiningParallelsEnable);

        SpaceElevatorModuleMiningParallels = config
            .get("太空电梯-采矿模块","采矿模块并行数量设置",SpaceElevatorModuleMiningParallels,"设置采矿模块并行数量")
            .getInt(SpaceElevatorModuleMiningParallels);

        SpaceElevatorMiningPlasmaEnable = config
            .get("太空电梯-采矿模块","采矿模块等离子体不消耗",SpaceElevatorMiningPlasmaEnable,"开启后采矿模块等离子体不会消耗")
            .getBoolean(SpaceElevatorMiningPlasmaEnable);

        SpaceElevatorMiningTicks = config
            .get("太空电梯-采矿模块", "采矿模块运行时间", SpaceElevatorMiningTicks, "设置采矿模块工作时间")
            .getInt(SpaceElevatorMiningTicks);

        SpaceElevatorMiningTicksTrue = config
            .get("太空电梯-采矿模块", "采矿模块运行时间修改", SpaceElevatorMiningTicksTrue, "开启后可自定义采矿模块工作时间")
            .getBoolean(SpaceElevatorMiningTicksTrue);

        DTPFOpen = config
            .get("超维度等离子锻炉", "开启锁定催化剂减免", DTPFOpen, "开启后超维度等离子锻炉催化剂减免锁定为百分百，不再有减免，避免通厕所")
            .getBoolean(DTPFOpen);

        DTPFMK2Enable = config
            .get("TST","开启超维度等离子锻炉MK2锁定催化剂减免",DTPFMK2Enable,"开启后超维度等离子锻炉MK2催化剂减免锁定为百分百，不再有减免，避免通厕所")
            .getBoolean(DTPFMK2Enable);

        LightningSpireEnable = config
            .get("TST","闪电尖塔",LightningSpireEnable,"开启闪电尖塔mixin,开启后闪电尖塔不再消耗任何流体")
            .getBoolean(LightningSpireEnable);

        LightningSpireTime = config
            .get("TST","闪电尖塔工作时间设置",LightningSpireTime,"设置闪电尖塔一次工作的时间，默认256tick（与原版相同）,必须开启闪电尖塔mixin后才能生效！")
            .getInt(LightningSpireTime);

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

        EOHinputHatchEnable = config
            .get("鸿蒙之眼功能","鸿蒙之眼输入仓",EOHinputHatchEnable,"开启后鸿蒙之眼不强制需求2个输入仓，可以为0,1,2个")
            .getBoolean(EOHinputHatchEnable);

        enableFluidOutPut = config
            .get("鸿蒙之眼功能", "鸿蒙之眼额外流体产出", enableFluidOutPut, "鸿蒙之眼额外流体产出是否启用")
            .getBoolean(enableFluidOutPut);

        EOHItemInPut = config
            .get("鸿蒙之眼功能", "额外产出", EOHItemInPut, "鸿蒙之眼额外物品产出是否启用")
            .getBoolean(EOHItemInPut);

        EOHAstralArrayAmount = config
            .get("鸿蒙之眼功能", "鸿蒙之眼星阵上限", EOHAstralArrayAmount, "鸿蒙之眼星阵上限数量修改，最高上限支持到10万")
            .getBoolean(EOHAstralArrayAmount);

        EOHZeroPowerStart = config
            .get("鸿蒙之眼功能", "鸿蒙之眼0电启动", EOHZeroPowerStart, "鸿蒙之眼0电量启动，现在它不耗电了!")
            .getBoolean(EOHZeroPowerStart);

        EOHGemEnable = config
            .get("鸿蒙之眼宝石产出","开启鸿蒙之眼宝石产出",EOHGemEnable,"开启后鸿蒙之眼可以输出所有矿物词典带有Gem的物品")
            .getBoolean(EOHGemEnable);

        EOHGem = config
            .get("鸿蒙之眼宝石产出","鸿蒙之眼宝石产出数量设置",EOHGem,"支持long级别的物品输出，大于int数量的物品请使用字符来表示，例如100T，100G等方式" +
                "目前支持K(千)，M(百万，B、G(10亿),T(万亿),P(千万亿),E(百亿亿))")
            .getString();

        EOHWorkTime = config
            .get("鸿蒙之眼运行时间", "鸿蒙之眼运行时间控制", EOHWorkTime, "是否启用控制鸿蒙之眼运行时间为一个固定值")
            .getBoolean(EOHWorkTime);

        EOHOpenEuOutPut = config
            .get("鸿蒙之眼发电", "鸿蒙之眼固定发电量设置", EOHOpenEuOutPut, "是否开启鸿蒙之眼额外EU产出")
            .getBoolean(EOHOpenEuOutPut);

        FOGUpDate = config
            .get("诸神之锻炉", "诸神之锻炉升级模块", FOGUpDate, "诸神之锻炉升级模块随便点，无视材料，分支，引力子碎片")
            .getBoolean(FOGUpDate);

        FOGGravitonShardEnable = config
            .get("诸神之锻炉","诸神之锻炉引力子碎片输出",FOGGravitonShardEnable,"开启后诸神之锻炉引力子碎片输出不再减少机器内部引力子碎片数量")
            .getBoolean(FOGGravitonShardEnable);

        ExoticModuleEnable = config
            .get("诸神之锻炉","诸神之锻炉太阳聚变异化器模块",ExoticModuleEnable,"开启后太阳聚变异化器模块不需要任何输入就可以产生夸克胶子与磁流体物质")
            .getBoolean(ExoticModuleEnable);

        BioVatTrue = config
            .get("其他机器", "细菌培养缸", BioVatTrue, "开启后细菌培养缸持续最大输出不需要保持半满,仅支持传统输出仓，不支持ME输出仓")
            .getBoolean(BioVatTrue);

        DisTankTrue = config
            .get("其他机器", "溶解罐", DisTankTrue, "开启后溶解罐不需要等比例流体即可工作")
            .getBoolean(DisTankTrue);

        DigesterMixin = config
            .get("其他机器", "煮解池", DigesterMixin, "开启后煮解池通过线圈等级获得BUFF，提高处理速度与减少时间，并且 不增加额外电力消耗")
            .getBoolean(DigesterMixin);

        LargeFusionMixin = config
            .get("压缩聚变", "压缩聚变能源仓buff", LargeFusionMixin, "开启后提高每个能源仓提供的功率上限，并且锁定最大能量存储为10000000000000EU")
            .getBoolean(LargeFusionMixin);

        LargeFusionParaMixin = config
            .get("压缩聚变", "开启压缩聚变并行基础值修改", LargeFusionMixin, "开启后支持修改压缩聚变并行基础值,支持1-5级压缩聚变")
            .getBoolean(LargeFusionParaMixin);

        LargeFusionPara = config
            .get("压缩聚变", "压缩聚变并行基础值修改", LargeFusionPara, "修改压缩聚变并行基础值,原版机器默认64")
            .getInt(LargeFusionPara);

        UUMixin = config
            .get("其他机器", "大UU", UUMixin, "开启后大UU启用不耗电+int并行")
            .getBoolean(UUMixin);

        BioLabMixin = config
            .get("其他机器", "生物实验室", BioLabMixin, "开启后所有抽卡成功率为百分百")
            .getBoolean(BioLabMixin);

        HIPCompressorEnable = config
            .get("其他机器","HIP压缩机", HIPCompressorEnable,"开启后关闭HIP热量系统")
            .getBoolean(HIPCompressorEnable);

        NaquadahFuelRefineryMixinTrue = config
            .get("硅岩燃料精炼厂", "开启燃料产出修改", NaquadahFuelRefineryMixinTrue, "开启可以后自定义配方倍率，可以在NEI中查看")
            .getBoolean(NaquadahFuelRefineryMixinTrue);

        NaquadahFuelRefineryMagnification = config
            .get("硅岩燃料精炼厂", "燃料倍率修改", NaquadahFuelRefineryMagnification, "倍率直接反映在NEI中，减少请使用小数")
            .getInt(NaquadahFuelRefineryMagnification);

        NaquadahFuelOutPutMagnificationTrue = config
            .get("硅岩反应堆","开启修改枯竭燃料产出",NaquadahFuelOutPutMagnificationTrue,"开启后可以自定义枯竭燃料产出倍率")
            .getBoolean(NaquadahFuelOutPutMagnificationTrue);

        NaquadahFuelOutPutMagnification = config
            .get("硅岩反应堆","枯竭燃料产出倍率",NaquadahFuelOutPutMagnification,"设置枯竭燃料产出倍率")
            .getInt(NaquadahFuelOutPutMagnification);

        BlackHoleCompressorStabilityLock = config
            .get("黑洞压缩机","黑洞压缩机稳定性修改",BlackHoleCompressorStabilityLock,"锁定黑洞为稳定状态")
            .getBoolean(BlackHoleCompressorStabilityLock);

        BlackHoleCompressorParallelModificationEnabled = config
            .get("黑洞压缩机","黑洞压缩机并行度修改开启",BlackHoleCompressorParallelModificationEnabled,"开启后可以自定义黑洞压缩机并行数量")
            .getBoolean(BlackHoleCompressorParallelModificationEnabled);

        BlackHoleCompressorParallelCountModification = config
            .get("黑洞压缩机","黑洞压缩机并行度修改",BlackHoleCompressorParallelCountModification,"黑洞压缩机并行数量修改")
            .getInt(BlackHoleCompressorParallelCountModification);

        BlackHoleCompressorPowerConsumptionModificationEnabled = config
            .get("黑洞压缩机","黑洞压缩机耗电修改开启",BlackHoleCompressorPowerConsumptionModificationEnabled,"开启黑洞发电机耗电修改")
            .getBoolean(BlackHoleCompressorPowerConsumptionModificationEnabled);

        BlackHoleCompressorPowerConsumptionModification = config
            .get("黑洞压缩机","黑洞压缩机耗电系数修改",BlackHoleCompressorPowerConsumptionModification,"黑洞压缩机耗电系数修改,传入参数为float")
            .getString();

        BlackHoleCompressorTimeConsumptionModification = config
            .get("黑洞压缩机","黑洞压缩机耗时系数修改",BlackHoleCompressorTimeConsumptionModification,"黑洞压缩机耗时系数修改,传入参数为float")
            .getString();

        IndustrialLaserEngraverParallelEnabled = config
            .get("大激光蚀刻机","大激光蚀刻机并行修改",IndustrialLaserEngraverParallelEnabled,"开启后大激光蚀刻机并行锁定为int")
            .getBoolean(IndustrialLaserEngraverParallelEnabled);

        IndustrialLaserEngraverOverclockEnabled = config
            .get("大激光蚀刻机","大激光蚀刻机超频修改",IndustrialLaserEngraverOverclockEnabled,"开启后修改大激光蚀刻机超频机制")
            .getBoolean(IndustrialLaserEngraverOverclockEnabled);

        MaskInfiniteDurability = config
            .get("物品","掩膜板",MaskInfiniteDurability,"开启后全部对应物品变为无限耐久")
            .getBoolean(MaskInfiniteDurability);

        Water = config
            .get("净化水产线机器","提示",Water,"净化水机器修改仅改动了内部处理逻辑，机器多方块结构依然要保证正确！")
            .getBoolean(Water);

        Grade8WaterPurificationEnabled = config
            .get("净化水产线机器","8级水",Grade8WaterPurificationEnabled,"开启后8级水机器输入7级水即可工作并且百分百成功，不需要任何额外自动化")
            .getBoolean(Grade8WaterPurificationEnabled);

        Grade7WaterPurificationEnabled = config
            .get("净化水产线机器","7级水",Grade7WaterPurificationEnabled,"开启后7级水机器输入6级水即可工作并且百分百成功，不需要任何额外自动化")
            .getBoolean(Grade7WaterPurificationEnabled);

        Grade6WaterPurificationEnabled = config
            .get("净化水产线机器","6级水",Grade6WaterPurificationEnabled,"开启后6级水机器输入5级水即可工作并且百分百成功，不需要任何额外自动化")
            .getBoolean(Grade6WaterPurificationEnabled);

        Grade5WaterPurificationEnabled = config
            .get("净化水产线机器","5级水",Grade5WaterPurificationEnabled,"开启后5级水机器输入4级水即可工作并且百分百成功，不需要任何额外自动化")
            .getBoolean(Grade5WaterPurificationEnabled);

        Grade4WaterPurificationEnabled = config
            .get("净化水产线机器","4级水",Grade4WaterPurificationEnabled,"开启后4级水机器输入3级水即可工作并且百分百成功，不需要任何额外自动化")
            .getBoolean(Grade4WaterPurificationEnabled);

        Grade3WaterPurificationEnabled = config
            .get("净化水产线机器","3级水",Grade3WaterPurificationEnabled,"开启后3级水机器输入2级水即可工作并且百分百成功，不需要任何额外自动化")
            .getBoolean(Grade3WaterPurificationEnabled);

        Grade2WaterPurificationEnabled = config
            .get("净化水产线机器","2级水",Grade2WaterPurificationEnabled,"开启后2级水机器百分百成功并且输入臭氧过量也不会爆炸")
            .getBoolean(Grade2WaterPurificationEnabled);

        Grade1WaterPurificationEnabled = config
            .get("净化水产线机器","1级水",Grade1WaterPurificationEnabled,"开启后1级水机器的过滤器永不损坏")
            .getBoolean(Grade1WaterPurificationEnabled);

        if (config.hasChanged()) {
            config.save();
        }
    }

    public static boolean isDevelopmentEnvironment() {
        String currentDir = System.getProperty("user.dir");
        return currentDir.contains("run");
    }
}
