package com.EyeOfHarmonyBuffer.Config;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class MainConfig {

    public static String constantOutputEUConfig = "3812901725648391027364519283746501928374652019384756209183475620193847562019384756201938475620193847562";
    public static boolean GasInPut = false;
    public static boolean EOHinputHatchEnable= false;
    public static boolean enableFluidOutPut = false;
    public static boolean EOHItemInPut = false;
    public static int EOHtime = 128;
    public static double RecipeChance = 1;
    public static double RecipeYield = 1;
    public static boolean EOHLV = false;
    public static boolean EOHAstralArrayAmount = false;
    public static boolean EOHZeroPowerStart = false;
    public static boolean EOHSuccessRateControls = false;
    public static boolean EOHOutputRateControl = false;
    public static boolean EOHWorkTime = false;
    public static boolean EOHOpenEuOutPut = false;
    public static boolean DTPFOpen = false;
    public static boolean FOGUpDate = false;
    public static boolean BioVatOutputRatioEnable = false;
    public static boolean DisTankTrue = false;
    public static boolean DisTankOverClockEnable = false;
    public static boolean DigesterMixin = false;
    public static boolean LargeFusionMixin = false;
    public static boolean LargeFusionParaMixin = false;
    public static int LargeFusionPara = 256;
    public static boolean UUMixin = false;
    public static boolean BioLabMixin = false;
    public static boolean NaquadahFuelRefineryMixinTrue = false;
    public static int NaquadahFuelRefineryMagnification = 10000;
    public static boolean NaquadahFuelOutPutMagnificationTrue = false;
    public static int NaquadahFuelOutPutMagnification = 10000;
    public static boolean BlackHoleCompressorStabilityLock = false;
    public static boolean BlackHoleCompressorParallelModificationEnabled = false;
    public static int BlackHoleCompressorParallelCountModification = 2000000000;
    public static boolean BlackHoleCompressorPowerConsumptionModificationEnabled = false;
    public static String BlackHoleCompressorPowerConsumptionModification = "0.0";
    public static boolean BlackHoleCompressorTimeConsumptionModificationEnabled = false;
    public static String BlackHoleCompressorTimeConsumptionModification = "0.0";
    public static boolean IndustrialLaserEngraverParallelEnabled = false;
    public static boolean IndustrialLaserEngraverOverclockEnabled = false;
    public static boolean MaskInfiniteDurability = false;
    public static boolean Grade8WaterPurificationEnabled = false;
    public static boolean Grade7WaterPurificationEnabled = false;
    public static boolean Grade6WaterPurificationEnabled = false;
    public static boolean Grade5WaterPurificationEnabled = false;
    public static boolean Grade4WaterPurificationEnabled = false;
    public static boolean Grade3WaterPurificationEnabled = false;
    public static boolean Grade2WaterPurificationEnabled = false;
    public static boolean Grade1WaterPurificationEnabled = false;
    public static boolean DTPFMK2Enable = false;
    public static boolean HIPCompressorEnable = false;
    public static boolean ExoticModuleEnable = false;
    public static int SpaceElevatorAssemblerParallel = 1024;
    public static boolean SpaceElevatorAssemblerParallelEnable = false;
    public static boolean SpaceElevatorMiningParallelsEnable = false;
    public static boolean SpaceElevatorMiningPlasmaEnable = false;
    public static int SpaceElevatorModuleMiningParallels = 128;
    public static int SpaceElevatorMiningTicks = 128;
    public static boolean Water = false;
    public static boolean SpaceElevatorMiningTicksTrue = false;
    public static boolean OutPutHatchMEEnable = false;
    public static boolean OutPutBusMEEnable = false;
    public static boolean TargetChamberEnable = false;
    public static boolean PlanetaryGasSiphonEnable = false;
    public static int PlanetaryGasSiphonParallel = 10;
    public static boolean IndustrialDehydratorEnable = false;
    public static boolean FrothFlotationCellEnable = false;
    public static boolean IsaMillEnable = false;
    public static boolean FOGGravitonShardEnable = false;
    public static boolean EOHGemEnable = false;
    public static String EOHGem = "2T";
    public static boolean PCBFactoryParallelEnable = false;
    public static boolean PCBFactoryCoolantEnable = false;
    public static boolean CircuitAssemblyLineEnable = false;
    public static boolean LightningSpireEnable = false;
    public static int LightningSpireTime = 256;
    public static boolean DEFusionCrafterEnable = false;
    public static boolean IndustrialChiselEnable = false;
    public static boolean BioVatRadiationEnabled = false;
    public static int BioVatOutputRatio = 1000000;
    public static boolean FuelRefineFactoryEnable = false;
    public static int LightningSpireMaxRods = 8192;
    public static boolean DTPFEnable = false;
    public static boolean AlloyBlastSmelterEnable = false;
    public static boolean ChemicalPlantEnable = false;
    public static boolean LargerTurbinePlasmaEnable = false;
    public static boolean PreciseAssemblerEnable = false;
    public static boolean CyclotronRecipeMixinEnable = false;
    public static boolean ExoticModuleOverClock = false;
    public static boolean MoltenModuleEnable = false;
    public static boolean PlasmaModuleEnable = false;
    public static boolean SmeltingModuleEnable = false;
    public static boolean PrimitiveBlastFurnaceEnable = false;
    public static boolean WoodenFusionReactorEnable = false;
    public static boolean MegaNineInOneEnable = false;
    public static boolean MegaIsaFactoryEnable = false;
    public static boolean DEFAULT_BATCH_MODE = false;
    public static boolean FusionComputerEnable = false;
    public static boolean ManufacturingCenterEnable = false;
    public static boolean ModulePumpEnable = false;
    public static int ModulePumpParallel = 1024;
    public static boolean ElementalDuplicatorEnable = false;
    public static boolean SpaceMiningRecipesEnable = false;
    public static boolean MegaBlastFurnaceEnable = false;
    public static boolean IndustrialCuttingMachineEnable = false;
    public static boolean StorageOutputHatchEnable = false;
    public static boolean StorageOutputBusEnable = false;
    public static boolean ResearchCompleterEnable = false;
    public static boolean GTPPMachineExoEnergyHatchFixEnable = false;
    public static boolean AllMachineUseExoEnergyHatchEnable = false;
    public static boolean ExtremeIndustrialGreenhouseOutPutEnable = false;
    public static int ExtremeIndustrialGreenhouseOutPut = 10;
    public static boolean MegaIndustrialApiaryOutPutEnable = false;
    public static int MegaIndustrialApiaryOutPut = 10;

    private static Configuration config;

    static {
        File configDir = new File("config/EyeOfHarmonyBuffer");
        if (!configDir.exists()) {
            configDir.mkdirs();
        }

        File mainConfigFile = new File(configDir, "main.cfg");

        if (config == null) {
            config = new Configuration(mainConfigFile);
            loadConfig();
        }
    }

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
            .get("其他机器","靶室",TargetChamberEnable,"开启后删除光束流等机器需求，只保留物品输入检测，最大并行数量调整为2050781，锁定运行时间为20tick")
            .getBoolean(TargetChamberEnable);

        ExtremeIndustrialGreenhouseOutPutEnable = config
            .get("极限工业温室","极限工业温室产物输出倍率修改",ExtremeIndustrialGreenhouseOutPutEnable,"开启后可以配置极限工业温室产物输出倍率")
            .getBoolean(ExtremeIndustrialGreenhouseOutPutEnable);

        ExtremeIndustrialGreenhouseOutPut = config
            .get("极限工业温室","极限工业温室产物输出倍率",ExtremeIndustrialGreenhouseOutPut,"设置极限工业温室产物输出倍率")
            .getInt(ExtremeIndustrialGreenhouseOutPut);

        MegaIndustrialApiaryOutPutEnable= config
            .get("巨型工业蜂箱","巨型工业蜂箱产物输出倍率修改",MegaIndustrialApiaryOutPutEnable,"开启后可以配置巨型工业蜂箱产物输出倍率")
            .getBoolean(MegaIndustrialApiaryOutPutEnable);

        MegaIndustrialApiaryOutPut= config
            .get("巨型工业蜂箱","巨型工业蜂箱产物输出倍率",MegaIndustrialApiaryOutPut,"设置巨型工业蜂箱产物输出倍率")
            .getInt(MegaIndustrialApiaryOutPut);

        StorageOutputHatchEnable = config
            .get("可编程舱室","存储输出仓",StorageOutputHatchEnable,"开启后可编程舱室存储输出仓锁定为为long存储")
            .getBoolean(StorageOutputHatchEnable);

        StorageOutputBusEnable = config
            .get("可编程舱室","存储输出总线",StorageOutputBusEnable,"开启后可编程舱室存储输出总线锁定为为long存储")
            .getBoolean(StorageOutputBusEnable);

        CyclotronRecipeMixinEnable = config
            .get("配方类","回旋加速器配方",CyclotronRecipeMixinEnable,"开启后原版回旋加速器配方全部变为百分百产出(不包括私货),任何配方类都mixin都不支持热重载，需要重启游戏后生效!")
            .getBoolean(CyclotronRecipeMixinEnable);

        SpaceMiningRecipesEnable = config
            .get("配方类","太空电梯-采矿模块",SpaceMiningRecipesEnable,"开启后太空采矿模块所有配方的杆与钻头全部删除,任何配方类都mixin都不支持热重载，需要重启游戏后生效!")
            .getBoolean(SpaceMiningRecipesEnable);

        GTPPMachineExoEnergyHatchFixEnable = config
            .get("其他机器","所有GTPP常规类机器", GTPPMachineExoEnergyHatchFixEnable,"开启后修复GTPP无法正常检查TecTech能源仓的问题")
            .getBoolean(GTPPMachineExoEnergyHatchFixEnable);

        AllMachineUseExoEnergyHatchEnable = config
            .get("其他机器","所有机器", AllMachineUseExoEnergyHatchEnable,"开启后强制允许所有机器安装TecTech能源仓")
            .getBoolean(AllMachineUseExoEnergyHatchEnable);

        FusionComputerEnable = config
            .get("其他机器","聚变反应堆MK1-MK5",FusionComputerEnable,"开启后聚变反应堆MK1-MK5锁定配方时间为10tick，大幅度提升每个能源仓可以提供的能量输入，机器运行不再消耗电力(机器中仍然需要拥有配方需要的启动电量！),拥有int并行")
            .getBoolean(FusionComputerEnable);

        ElementalDuplicatorEnable = config
            .get("其他机器","元素复制机",ElementalDuplicatorEnable,"开启后元素复制机不再消耗电力，所有工作都会在10tick内完成，并且拥有int并行")
            .getBoolean(ElementalDuplicatorEnable);

        MegaBlastFurnaceEnable = config
            .get("其他机器","巨型工业高炉",MegaBlastFurnaceEnable,"开启后巨型工业高炉支持无损超频,拥有int并行")
            .getBoolean(MegaBlastFurnaceEnable);

        IndustrialCuttingMachineEnable = config
            .get("其他机器","工业切割机",IndustrialDehydratorEnable,"开启后工业切割机不再消耗电力，所有工作都会在10tick内完成，并且拥有int并行")
            .getBoolean(IndustrialCuttingMachineEnable);

        ResearchCompleterEnable = config
            .get("其他机器","奥法阐释者",ResearchCompleterEnable,"开启后奥法阐释者不再消耗VIS")
            .getBoolean(ResearchCompleterEnable);

        WoodenFusionReactorEnable = config
            .get("123Technology","压缩原木聚变反应堆Mk 0",WoodenFusionReactorEnable,"开启后压缩原木聚变反应堆Mk 0所有工作都会在10tick内完成，并且拥有int并行,配方大幅度增强")
            .getBoolean(WoodenFusionReactorEnable);

        MegaNineInOneEnable = config
            .get("123Technology","巨型九合一",MegaNineInOneEnable,"开启后巨型九合一运行配方不再有任何限制，不再消耗电力，所有工作都会在10tick内完成，并且拥有int并行")
            .getBoolean(MegaNineInOneEnable);

        MegaIsaFactoryEnable = config
            .get("123Technology","艾萨集成工厂",MegaIsaFactoryEnable,"开启后艾萨集成工厂运行配方不再有任何限制，不再消耗电力，所有工作都会在10tick内完成，并且拥有int并行")
            .getBoolean(MegaIsaFactoryEnable);

        CircuitAssemblyLineEnable = config
            .get("其他机器","电路装配线",CircuitAssemblyLineEnable,"开启后电路装配线不再耗电并且大幅度提升并行")
            .getBoolean(CircuitAssemblyLineEnable);

        PrimitiveBlastFurnaceEnable = config
            .get("其他机器","土高炉",PrimitiveBlastFurnaceEnable,"开启后土高炉运行任何配方均只需要10tick")
            .getBoolean(PrimitiveBlastFurnaceEnable);

        DEFusionCrafterEnable = config
            .get("其他机器","龙研聚合装置", DEFusionCrafterEnable,"开启后龙之研究聚合装置不再消耗电力，所有工作都会在10tick内完成，并且拥有int并行")
            .getBoolean(DEFusionCrafterEnable);

        LargerTurbinePlasmaEnable = config
            .get("其他机器","特大等离子涡轮",LargerTurbinePlasmaEnable,"开启后特大等离子涡轮低热值等离子输出会更高")
            .getBoolean(LargerTurbinePlasmaEnable);

        PreciseAssemblerEnable = config
            .get("其他机器","精密组装机",PreciseAssemblerEnable,"开启后精密组装机工作不受到机械方块等级与电压限制,不再消耗电力，所有工作都会在10tick内完成，并且拥有int并行")
            .getBoolean(PreciseAssemblerEnable);

        BioVatRadiationEnabled = config
            .get("细菌培养缸","细菌培养缸机器辐射修改",BioVatRadiationEnabled,"开启后细菌培养缸运行不再需要辐射")
            .getBoolean(BioVatRadiationEnabled);

        BioVatOutputRatioEnable = config
            .get("细菌培养缸", "开细菌培养缸产出倍率修改", BioVatOutputRatioEnable, "开启后细菌培养缸输出倍率修改")
            .getBoolean(BioVatOutputRatioEnable);

        BioVatOutputRatio = config
            .get("细菌培养缸","细菌培养缸产出倍率设置",BioVatOutputRatio,"设置细菌培养缸产出倍率")
            .getInt(BioVatOutputRatio);

        IndustrialChiselEnable = config
            .get("其他机器","工业3D打印机",IndustrialChiselEnable,"开启后工业3D打印机设置为0耗电,速度+1000%,int并行")
            .getBoolean(IndustrialChiselEnable);

        AlloyBlastSmelterEnable = config
            .get("其他机器","合金冶炼炉机器运行修改",AlloyBlastSmelterEnable,"开启后(MAGA)合金冶炼炉不再消耗电力，所有工作都会在10tick内完成，并且拥有int并行")
            .getBoolean(AlloyBlastSmelterEnable);

        ChemicalPlantEnable = config
            .get("其他机器","化工厂",ChemicalPlantEnable,"开启后化工厂不再消耗催化剂")
            .getBoolean(ChemicalPlantEnable);

        IndustrialChiselEnable = config
            .get("其他机器","工业3D打印机",IndustrialChiselEnable,"开启后工业3D打印机设置为0耗电,速度+1000%,int并行")
            .getBoolean(IndustrialChiselEnable);

        PCBFactoryParallelEnable = config
            .get("PCB工厂","PCB工厂并行修改开启",PCBFactoryParallelEnable,"开启后PCB工厂最大并行数量锁定为int")
            .getBoolean(PCBFactoryParallelEnable);

        PCBFactoryCoolantEnable = config
            .get("PCB工厂","PCB工厂不消耗冷却液开启",PCBFactoryCoolantEnable,"开启后PCB工厂不再消耗冷却液，并行数量不再依赖冷却液数量")
            .getBoolean(PCBFactoryCoolantEnable);

        IndustrialDehydratorEnable = config
            .get("稀土线","真空干燥炉",IndustrialDehydratorEnable,"开启后真空干燥炉不再消耗电力，所有工作都会在10tick内完成，并且拥有int并行")
            .getBoolean(IndustrialDehydratorEnable);

        FrothFlotationCellEnable = config
            .get("稀土线","浮选机",FrothFlotationCellEnable,"开启后浮选机不再消耗电力，所有工作都会在10tick内完成，并且拥有int并行")
            .getBoolean(FrothFlotationCellEnable);

        IsaMillEnable = config
            .get("稀土线","艾萨研磨机",IsaMillEnable,"开启后艾萨研磨机不再消耗电力，所有工作都会在10tick内完成，并且拥有int并行，研磨球不会损耗")
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

        ModulePumpEnable = config
            .get("太空电梯-钻机模块","钻机模块最大并行数量修改",ModulePumpEnable,"开启钻机模块最大并行数量上限修改")
            .getBoolean(ModulePumpEnable);

        ModulePumpParallel = config
            .get("太空电梯-钻机模块","钻机模块最大并行数量设置",ModulePumpParallel,"设置钻机模块最大并行数量")
            .getInt(ModulePumpParallel);

        DTPFOpen = config
            .get("超维度等离子锻炉", "开启锁定催化剂减免", DTPFOpen, "开启后超维度等离子锻炉催化剂减免锁定为百分百，不再有减免，避免通厕所")
            .getBoolean(DTPFOpen);

        DTPFEnable = config
            .get("超维度等离子锻炉","超维度等离子锻炉机器运行修改",DTPFEnable,"开启后超维度等离子锻炉运行不再受限于线圈等级，不再消耗电力，所有工作都会在10tick内完成，并且拥有int并行")
            .getBoolean(DTPFEnable);

        DTPFMK2Enable = config
            .get("TST","开启超维度等离子锻炉MK2锁定催化剂减免",DTPFMK2Enable,"开启后超维度等离子锻炉MK2催化剂减免锁定为百分百，不再有减免，避免通厕所")
            .getBoolean(DTPFMK2Enable);

        LightningSpireEnable = config
            .get("TST","闪电尖塔",LightningSpireEnable,"开启闪电尖塔mixin,开启后闪电尖塔不再消耗任何流体")
            .getBoolean(LightningSpireEnable);

        LightningSpireTime = config
            .get("TST","闪电尖塔工作时间设置",LightningSpireTime,"设置闪电尖塔一次工作的时间，默认256tick（与原版相同）,必须开启闪电尖塔mixin后才能生效！")
            .getInt(LightningSpireTime);

        LightningSpireMaxRods = config
            .get("TST","闪电尖塔避雷针数量设置",LightningSpireMaxRods,"设置闪电尖塔内部可缓存避雷针数量,必须开启闪电尖塔mixin后才能生效！")
            .getInt(LightningSpireMaxRods);

        ManufacturingCenterEnable = config
            .get("TST","加工工厂",ManufacturingCenterEnable,"开启后加工工厂不再消耗电力，所有工作都会在10tick内完成，并且拥有int并行,工作不再受限于核心等级限制，支持UHV+电压配方")
            .getBoolean(ManufacturingCenterEnable);

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
            .get("诸神之锻炉","诸神之锻炉引力子碎片输出",FOGGravitonShardEnable,"开启后诸神之锻炉引力子碎片输出不再减少机器内部引力子碎片数量(默认关闭,机器内部存储大量引力子碎片可能导致机器停机等问题,后期请慎重开启)")
            .getBoolean(FOGGravitonShardEnable);

        ExoticModuleEnable = config
            .get("诸神之锻炉","诸神之锻炉太阳聚变异化器模块",ExoticModuleEnable,"开启后太阳聚变异化器模块不需要任何输入就可以产生夸克胶子与磁流体物质")
            .getBoolean(ExoticModuleEnable);

        ExoticModuleOverClock = config
            .get("诸神之锻炉","诸神之锻炉太阳聚变异化器模块超频修改",ExoticModuleOverClock,"开启后太阳聚变异化器模块不再受限于升级，不再消耗电力，所有工作都会在10tick内完成，并且拥有200000并行")
            .getBoolean(ExoticModuleOverClock);

        MoltenModuleEnable = config
            .get("诸神之锻炉","诸神之锻炉太阳射流融化核心模块超频修改",MoltenModuleEnable,"开启后太阳射流融化核心模块不再受限于升级，不再消耗电力，所有工作都会在10tick内完成，并且拥有int并行")
            .getBoolean(MoltenModuleEnable);

        PlasmaModuleEnable = config
            .get("诸神之锻炉","诸神之锻炉太阳热能等离子体制造机超频修改",PlasmaModuleEnable,"开启后太阳热能等离子体制造机不再受限于升级，不再消耗电力，所有工作都会在10tick内完成，并且拥有int并行")
            .getBoolean(PlasmaModuleEnable);

        SmeltingModuleEnable = config
            .get("诸神之锻炉","诸神之锻炉太阳烈焰能量锻炉超频修改",SmeltingModuleEnable,"开启后太阳烈焰能量锻炉不再受限于升级，不再消耗电力，所有工作都会在10tick内完成，并且拥有int并行")
            .getBoolean(SmeltingModuleEnable);

        DisTankTrue = config
            .get("其他机器", "溶解罐", DisTankTrue, "开启后溶解罐不需要等比例流体即可工作")
            .getBoolean(DisTankTrue);

        DisTankOverClockEnable = config
            .get("其他机器","溶解罐",DisTankOverClockEnable,"开启后溶解罐不再消耗电力，所有工作都会在10tick内完成，并且拥有int并行")
            .getBoolean(DisTankOverClockEnable);

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
            .get("其他机器", "生物实验室", BioLabMixin, "开启后生物实验室所有抽卡成功率为百分百")
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

        FuelRefineFactoryEnable = config
            .get("硅岩燃料精炼厂","硅岩燃料精炼厂机器运行修改",FuelRefineFactoryEnable,"开启后硅岩燃料精炼厂运行不再受限于线圈等级，不再消耗电力，所有工作都会在10tick内完成，并且拥有int并行")
            .getBoolean(FuelRefineFactoryEnable);

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
            .get("净化水产线机器","2级水",Grade2WaterPurificationEnabled,"开启后2级水机器百分百成功并且输入臭氧过量也不会爆炸(注：配方百分百成功是利用ASM字节码进行修改,如果您在游玩中使用热重载关闭了这个功能,对配方的修改也不会失效!必须重启游戏才可以")
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
