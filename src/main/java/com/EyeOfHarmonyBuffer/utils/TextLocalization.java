package com.EyeOfHarmonyBuffer.utils;

import net.minecraft.profiler.PlayerUsageSnooper;
import net.minecraft.util.EnumChatFormatting;

public class TextLocalization {
    public static final String ModName = "Eye Of Harmony Buffer";
    public static final String Waila_WirelessMode;
    public static final String Waila_CurrentEuCost;
    public static final String WirelessNetwork;
    public static final String StructureTooComplex;
    public static final String BLUE_PRINT_INFO;
    public static final String Tooltip_Details;
    public static final String Disable_loading;
    public static final String add_InputBus;
    public static final String add_OutputBus;
    public static final String add_inputHatch;
    public static final String add_outputHatch;
    public static final String add_DynamoHatch;
    public static final String add_MaintenanceHatch;
    public static final String EOHB_Text_SeparatingLine;
    public static final String EOHB_Starry_Miracle_Project;
    public static final String EOHB_Legendary_Machine_Project;
    public static final String EOHB_Arknights_Project;
    public static final String EOHB_Arknights_Project_Energy;
    public static final String EOHB_Arknights_Project_UpgradeCard;
    public static final String EOHB_Client_PlayerJoin00;
    public static final String EOHB_Client_PlayerJoin01;
    public static final String EOHB_Client_PlayerJoin01_1;
    public static final String EOHB_Client_PlayerJoin01_2;
    public static final String EOHB_Client_PlayerJoin02;
    public static final String EOHB_Client_PlayerJoin03;
    public static final String EOHB_Client_PlayerJoin04;
    public static final String EOHB_Client_PlayerJoin05;
    public static final String EOHB_Client_PlayerJoin06;
    public static final String EOHB_Client_PlayerJoin07;
    public static final String EOHB_Client_PlayerJoin07_1;
    public static final String EOHB_Client_PlayerJoin07_2;
    public static final String EOHB_Config_Not_Exist;
    public static final String EOHB_Open_Config;
    public static final String EOHB_Opened;
    public static final String EOHB_Open_Failed;
    public static final String EOHB_WirelessMode_On;
    public static final String EOHB_WirelessMode_Off;
    public static final String EOHB_FuelRob_NO_Outgrowth;

    //机器
    public static final String NameVendingMachines;
    public static final String Tooltip_VendingMachines_MachineType;
    public static final String Tooltip_VendingMachines_Controller;
    public static final String Tooltip_VendingMachines_00;
    public static final String Tooltip_VendingMachines_01;
    public static final String Tooltip_VendingMachines_02;
    public static final String Tooltip_VendingMachines_03;

    public static final String NameWindTurbine;
    public static final String Tooltip_WindTurbine_MachineType;
    public static final String Tooltip_WindTurbine_Controller;
    public static final String Tooltip_WindTurbine_00;
    public static final String Tooltip_WindTurbine_01;
    public static final String Tooltip_WindTurbine_02;
    public static final String Tooltip_WindTurbine_03;
    public static final String Tooltip_WindTurbine_04;
    public static final String StructureInfo_WindTurbine_00;
    public static final String StructureInfo_WindTurbine_01;
    public static final String StructureInfo_WindTurbine_02;
    public static final String StructureInfo_WindTurbine_03;
    public static final String StructureInfo_WindTurbine_04;
    public static final String StructureInfo_WindTurbine_05;
    public static final String StructureInfo_WindTurbine_06;
    public static final String StructureInfo_WindTurbine_07;
    public static final String StructureInfo_WindTurbine_08;
    public static final String StructureInfo_WindTurbine_09;

    public static final String NameSolarEnergyArray;
    public static final String Tooltip_SolarEnergyArray_MachineType;
    public static final String Tooltip_SolarEnergyArray_Controller;
    public static final String Tooltip_SolarEnergyArray_00;
    public static final String Tooltip_SolarEnergyArray_01;
    public static final String Tooltip_SolarEnergyArray_02;
    public static final String Tooltip_SolarEnergyArray_03;
    public static final String Tooltip_SolarEnergyArray_04;
    public static final String Tooltip_SolarEnergyArray_05;

    public static final String NameSubstanceReshapingDevice;
    public static final String Tooltip_SubstanceReshapingDevice_MachineType;
    public static final String Tooltip_SubstanceReshapingDevice_Controller;
    public static final String Tooltip_SubstanceReshapingDevice_00;
    public static final String Tooltip_SubstanceReshapingDevice_01;
    public static final String Tooltip_SubstanceReshapingDevice_02;
    public static final String Tooltip_SubstanceReshapingDevice_03;
    public static final String Tooltip_SubstanceReshapingDevice_04;
    public static final String Tooltip_SubstanceReshapingDevice_05;
    public static final String Tooltip_SubstanceReshapingDevice_06;
    public static final String Tooltip_SubstanceReshapingDevice_07;
    public static final String Tooltip_SubstanceReshapingDevice_08;
    public static final String Tooltip_SubstanceReshapingDevice_09;
    public static final String Tooltip_SubstanceReshapingDevice_10;
    public static final String SubstanceReshapingDeviceRecipes;

    public static final String NameBlueDogMachine;
    public static final String Tooltip_BlueDogMachine_MachineType;
    public static final String Tooltip_BlueDogMachine_Controller;
    public static final String Tooltip_BlueDogMachine_00;
    public static final String Tooltip_BlueDogMachine_01;
    public static final String Tooltip_BlueDogMachine_02;
    public static final String Tooltip_BlueDogMachine_03;
    public static final String Tooltip_BlueDogMachine_04;
    public static final String Tooltip_BlueDogMachine_05;
    public static final String Tooltip_BlueDogMachine_06;

    public static final String NameMonkeyShit;
    public static final String Tooltip_MonkeyShit_MachineType;
    public static final String Tooltip_MonkeyShit_Controller;
    public static final String Tooltip_MonkeyShit_00;
    public static final String Tooltip_MonkeyShit_01;
    public static final String Tooltip_MonkeyShit_02;
    public static final String Tooltip_MonkeyShit_03;
    public static final String Tooltip_MonkeyShit_04;
    public static final String Tooltip_MonkeyShit_05;

    public static final String NameOrundumDynamo;
    public static final String Tooltip_OrundumDynamo_MachineType;
    public static final String Tooltip_OrundumDynamo_Controller;
    public static final String Tooltip_OrundumDynamo_00;
    public static final String Tooltip_OrundumDynamo_01;
    public static final String Tooltip_OrundumDynamo_02;
    public static final String Tooltip_OrundumDynamo_03;
    public static final String Tooltip_OrundumDynamo_04;
    public static final String Tooltip_OrundumDynamo_05;

    public static final String NameElectricTypeOneMiningMachine;
    public static final String Tooltip_ElectricTypeOneMiningMachine_MachineType;
    public static final String Tooltip_ElectricTypeOneMiningMachine_Controller;
    public static final String Tooltip_ElectricTypeOneMiningMachine_00;
    public static final String Tooltip_ElectricTypeOneMiningMachine_01;
    public static final String Tooltip_ElectricTypeOneMiningMachine_02;
    public static final String Tooltip_ElectricTypeOneMiningMachine_03;
    public static final String Tooltip_ElectricTypeOneMiningMachine_04;
    public static final String Tooltip_ElectricTypeOneMiningMachine_05;

    public static final String NamePlanter;
    public static final String Tooltip_Planter_MachineType;
    public static final String Tooltip_Planter_Controller;
    public static final String Tooltip_Planter_00;
    public static final String Tooltip_Planter_01;
    public static final String Tooltip_Planter_02;
    public static final String Tooltip_Planter_03;
    public static final String Tooltip_Planter_04;

    public static final String NameSeedCollectingMachine;
    public static final String Tooltip_SeedCollectingMachine_MachineType;
    public static final String Tooltip_SeedCollectingMachine_Controller;
    public static final String Tooltip_SeedCollectingMachine_00;
    public static final String Tooltip_SeedCollectingMachine_01;
    public static final String Tooltip_SeedCollectingMachine_02;
    public static final String Tooltip_SeedCollectingMachine_03;
    public static final String Tooltip_SeedCollectingMachine_04;

    public static final String NameRefiningFurnace;
    public static final String Tooltip_RefiningFurnace_MachineType;
    public static final String Tooltip_RefiningFurnace_Controller;
    public static final String Tooltip_RefiningFurnace_00;
    public static final String Tooltip_RefiningFurnace_01;
    public static final String Tooltip_RefiningFurnace_02;
    public static final String Tooltip_RefiningFurnace_03;
    public static final String Tooltip_RefiningFurnace_04;
    public static final String Tooltip_RefiningFurnace_05;

    public static final String NamePulverizer;
    public static final String Tooltip_Pulverizer_MachineType;
    public static final String Tooltip_Pulverizer_Controller;
    public static final String Tooltip_Pulverizer_00;
    public static final String Tooltip_Pulverizer_01;
    public static final String Tooltip_Pulverizer_02;

    public static final String NameAccessoriesMachine;
    public static final String Tooltip_AccessoriesMachine_MachineType;
    public static final String Tooltip_AccessoriesMachine_Controller;
    public static final String Tooltip_AccessoriesMachine_00;
    public static final String Tooltip_AccessoriesMachine_01;

    public static final String NameShapingMachine;
    public static final String Tooltip_ShapingMachine_MachineType;
    public static final String Tooltip_ShapingMachine_Controller;
    public static final String Tooltip_ShapingMachine_00;
    public static final String Tooltip_ShapingMachine_01;
    public static final String Tooltip_ShapingMachine_02;

    public static final String NameGrinder;
    public static final String Tooltip_Grinder_MachineType;
    public static final String Tooltip_Grinder_Controller;
    public static final String Tooltip_Grinder_00;
    public static final String Tooltip_Grinder_01;

    public static final String NameEncapsulationMachine;
    public static final String Tooltip_EncapsulationMachine_MachineType;
    public static final String Tooltip_EncapsulationMachine_Controller;
    public static final String Tooltip_EncapsulationMachine_00;
    public static final String Tooltip_EncapsulationMachine_01;
    public static final String Tooltip_EncapsulationMachine_02;

    public static final String NameFillingUnit;
    public static final String Tooltip_FillingUnit_MachineType;
    public static final String Tooltip_FillingUnit_Controller;
    public static final String Tooltip_FillingUnit_00;
    public static final String Tooltip_FillingUnit_01;

    public static final String NameForgeOfTheSky;
    public static final String Tooltip_ForgeOfTheSky_MachineType;
    public static final String Tooltip_ForgeOfTheSky_Controller;
    public static final String Tooltip_ForgeOfTheSky_00;
    public static final String Tooltip_ForgeOfTheSky_01;

    public static final String NamePurificationUnit;
    public static final String Tooltip_PurificationUnit_MachineType;
    public static final String Tooltip_PurificationUnit_Controller;
    public static final String Tooltip_PurificationUnit_00;
    public static final String Tooltip_PurificationUnit_01;
    public static final String Tooltip_PurificationUnit_02;
    public static final String Tooltip_PurificationUnit_03;
    public static final String Tooltip_PurificationUnit_04;

    public static final String NameReactorCrucible;
    public static final String Tooltip_ReactorCrucible_MachineType;
    public static final String Tooltip_ReactorCrucible_Controller;
    public static final String Tooltip_ReactorCrucible_00;
    public static final String Tooltip_ReactorCrucible_01;
    public static final String Tooltip_ReactorCrucible_02;

    public static final String NameExpandedCrucible;
    public static final String Tooltip_ExpandedCrucible_MachineType;
    public static final String Tooltip_ExpandedCrucible_Controller;
    public static final String Tooltip_ExpandedCrucible_00;
    public static final String Tooltip_ExpandedCrucible_01;
    public static final String Tooltip_ExpandedCrucible_02;

    public static final String NameFluidPump;
    public static final String Tooltip_FluidPump_MachineType;
    public static final String Tooltip_FluidPump_Controller;
    public static final String Tooltip_FluidPump_00;
    public static final String Tooltip_FluidPump_01;

    //MOD
    public static final String EOHB_Harmony;
    public static final String EOHB_Harmony_Tooltip;
    public static final String EOHB_ArsenicImpact;
    public static final String EOHB_ArsenicImpact_Tooltip;
    public static final String EOHB_Hoyomixium;
    public static final String EOHB_Hoyomixium_Tooltip;
    public static final String EOHB_PrecipitationAcid;
    public static final String EOHB_CupriumSolution;
    public static final String EOHB_InertXirconEffluent;
    public static final String EOHB_HetoniteSolution;
    public static final String EOHB_JinCaoSolution;
    public static final String EOHB_XirconEffluent;
    public static final String EOHB_Sewage;
    public static final String EOHB_YaZhenSolution;
    public static final String EOHB_LiquidXiranite;
    public static final String EOHB_LiquidHeavyXiranite;
    public static final String EOHB_LiquefiedOrundum;
    public static final String EOHB_CrudeLiquefiedOrundum;
    public static final String EOHB_HighEnergyOrundumSolvent;
    public static final String EOHB_UnstableOrundumSolvent;

    //流体
    public static final String EOHB_OriginiumWasteLiquid;

    //物品
    public static final String EOHB_ChengDuHeart_Tooltip_00;
    public static final String EOHB_ChengDuHeart_Tooltip_01;
    public static final String EOHB_Monkey_Tooltip_00;
    public static final String EOHB_Monkey_Tooltip_01;
    public static final String EOHB_Shit_Tooltip_00;
    public static final String EOHB_YuanShi_Tooltip_00;
    public static final String EOHB_YuanShi_Tooltip_01;
    public static final String EOHB_YuanShi_Tooltip_02;
    public static final String EOHB_HeChengYu_Tooltip_00;
    public static final String EOHB_HeChengYu_Tooltip_01;
    public static final String EOHB_PoSuiYuanShi_Tooltip_00;
    public static final String EOHB_PoSuiYuanShi_Tooltip_01;
    public static final String EOHB_UpgradeChipMK1_Tooltip_00;
    public static final String EOHB_UpgradeChipMK2_Tooltip_00;
    public static final String EOHB_UpgradeChipMK3_Tooltip_00;
    public static final String EOHB_YaZhenZhenJi_00;
    public static final String EOHB_YaZhenZhenJi_01;
    public static final String EOHB_JinCaoRuanYin_00;
    public static final String EOHB_JinCaoRuanYin_01;
    public static final String EOHB_ForgeOfTheSkyCore_Tooltip_00;
    public static final String EOHB_ForgeOfTheSkyCore_Tooltip_01;

    //配方池
    public static final String EOHB_Recipe_SubstanceReshapingDevice;
    public static final String EOHB_Recipe_BlueDogFountain;
    public static final String EOHB_Recipe_BlueDogFountainMAX;
    public static final String EOHB_Recipe_MonkeyShit;
    public static final String EOHB_Recipe_OrundumDynamo;
    public static final String EOHB_Recipe_OrundumDynamo_Tooltip_00;
    public static final String EOHB_Recipe_ElectricTypeOneMiningMachine;
    public static final String EOHB_Recipe_Planter;
    public static final String EOHB_Recipe_SeedCollectingMachine;
    public static final String EOHB_Recipe_RefiningFurnace;
    public static final String EOHB_Recipe_Pulverizer;
    public static final String EOHB_Recipe_AccessoriesMachine;
    public static final String EOHB_Recipe_ShapingMachine;
    public static final String EOHB_Recipe_Grinder;
    public static final String EOHB_Recipe_EncapsulationMachine;
    public static final String EOHB_Recipe_FillingUnit;
    public static final String EOHB_Recipe_ForgeOfTheSky;
    public static final String EOHB_Recipe_PurificationUnit;
    public static final String EOHB_Recipe_ReactorCrucible;
    public static final String EOHB_Recipe_FluidPump;

    public TextLocalization() {
    }

    static{
        //默认
        Waila_WirelessMode = TextHandler.texter("Wireless Mode", "Waila.General.WirelessMode");
        Waila_CurrentEuCost = TextHandler.texter("Current EU Cost", "Waila.General.CurrentEuCost");
        WirelessNetwork = TextHandler.texter("Directly get EU from the Wireless EU Net.", "WirelessNetwork");
        StructureTooComplex = TextHandler.texter("The structure is too complex!", "StructureTooComplex");
        BLUE_PRINT_INFO = TextHandler.texter(
            "Follow the" + EnumChatFormatting.BLUE
                + " Structure"
                + EnumChatFormatting.DARK_BLUE
                + "Lib"
                + EnumChatFormatting.GRAY
                + " hologram projector to build the main structure.",
            "BLUE_PRINT_INFO");
        Tooltip_Details = TextHandler.texter(EnumChatFormatting.LIGHT_PURPLE + "Details: ", "Tooltip_Details");
        Disable_loading = TextHandler.texter("§4§l§oMachine is prohibited from loading!Please go to the configuration file MachineLoaderConfig to enable the machine!", "Disable_loading");
        add_InputBus = TextHandler.texter("Any casing","add_InputBus");
        add_OutputBus = TextHandler.texter("Any casing","add_OutputBus");
        add_inputHatch = TextHandler.texter("Any casing","add_inputHatch");
        add_outputHatch = TextHandler.texter("Any casing","add_outputHatch");
        add_DynamoHatch = TextHandler.texter("Any casing","add_DynamoHatch");
        add_MaintenanceHatch = TextHandler.texter("Any casing","add_MaintenanceHatch");
        EOHB_Text_SeparatingLine = TextHandler.texter("-----------------------------------------","EOHB_Text_SeparatingLine");
        EOHB_Starry_Miracle_Project = TextHandler.texter("EOHB — Starry Miracle Project","EOHB_Starry_Miracle_Project");
        EOHB_Arknights_Project= TextHandler.texter("EOHB — Arknights Project","EOHB_Arknights_Project");
        EOHB_Arknights_Project_Energy = TextHandler.texter("This machine runs on Orundum energy!","EOHB_Arknights_Project_Energy");
        EOHB_Legendary_Machine_Project = TextHandler.texter("EOHB-Legendary Machine Project","EOHB_Legendary_Machine_Project");
        EOHB_Arknights_Project_UpgradeCard = TextHandler.texter("This machine can be upgraded by upgrading the chips!","EOHB_Arknights_Project_UpgradeCard");
        EOHB_WirelessMode_On = TextHandler.texter("Wireless mode enabled.","EOHB_WirelessMode_On");
        EOHB_WirelessMode_Off = TextHandler.texter("Wireless mode disabled.","EOHB_WirelessMode_Off");
        EOHB_FuelRob_NO_Outgrowth = TextHandler.texter("This fuel rod does not produce anything! It disappears upon burning.", "EOHB_FuelRob_NO_Outgrowth");

        //载入提示性文本
        EOHB_Client_PlayerJoin00 = TextHandler.texter("Welcome to use EyeOfHarmonyBuffer!","EOHB_Client_PlayerJoin00");
        EOHB_Client_PlayerJoin01 = TextHandler.texter("Remember to check your config file to see which features you’ve actually turned on.","EOHB_Client_PlayerJoin01");
        EOHB_Client_PlayerJoin01_1 = TextHandler.texter("right now, everything’s snoozing by default!","EOHB_Client_PlayerJoin01_1");
        EOHB_Client_PlayerJoin01_2 = TextHandler.texter("Type /EOHBconfiglist to show the four config file links below once more!","EOHB_Client_PlayerJoin01_2");
        EOHB_Client_PlayerJoin02 = TextHandler.texter("Main Configuration File","EOHB_Client_PlayerJoin02");
        EOHB_Client_PlayerJoin03 = TextHandler.texter("EOH Extra Item Output Config File","EOHB_Client_PlayerJoin03");
        EOHB_Client_PlayerJoin04 = TextHandler.texter("EOH Extra Fluid Output Config File","EOHB_Client_PlayerJoin04");
        EOHB_Client_PlayerJoin05 = TextHandler.texter("Machine Loading Config File","EOHB_Client_PlayerJoin05");
        EOHB_Client_PlayerJoin06 = TextHandler.texter("Open configuration folder","EOHB_Client_PlayerJoin06");
        EOHB_Client_PlayerJoin07 = TextHandler.texter("Done with your configs? Save them and hit /eoh_reloadconfig in-game for a hot reload!","EOHB_Client_PlayerJoin07");
        EOHB_Client_PlayerJoin07_1 = TextHandler.texter("Just a heads-up: recipe tweaks and machine loads need a full restart to kick in.","EOHB_Client_PlayerJoin07_1");
        EOHB_Client_PlayerJoin07_2 = TextHandler.texter("Have fun tweaking and playing!","EOHB_Client_PlayerJoin07_2");
        EOHB_Config_Not_Exist = TextHandler.texter("Configuration directory does not exist!","EOHB_Config_Not_Exist");
        EOHB_Open_Config = TextHandler.texter("Configuration folder opened.","EOHB_Open_Config");
        EOHB_Opened = TextHandler.texter("Opened:","EOHB_Opened");
        EOHB_Open_Failed = TextHandler.texter("Failed to open configuration:","EOHB_Open_Failed");

        //大型贸易机
        NameVendingMachines = TextHandler.texter("Vending Machines", "NameVendingMachines");
        Tooltip_VendingMachines_MachineType = TextHandler.texter("Vending Machines", "Tooltip_VendingMachines_MachineType");
        Tooltip_VendingMachines_Controller = TextHandler.texter("Controller block for the Vending Machines", "Tooltip_VendingMachines_Controller");
        Tooltip_VendingMachines_00 = TextHandler.texter("A large trade machine, Vending Machines, Place the marked item into the machine's main block slot to set it as the machine's output item.", "Tooltip_VendingMachines_00");
        Tooltip_VendingMachines_01 = TextHandler.texter("When powered by a wireless EU Net, it supports parallel multi-recipe processing with a fixed working time of 6.4 seconds.", "Tooltip_VendingMachines_01");
        Tooltip_VendingMachines_02 = TextHandler.texter("Put the desired output item into the machine's main block. Any input item will produce the desired output item!", "Tooltip_VendingMachines_02");
        Tooltip_VendingMachines_03 = TextHandler.texter("The machine has a default of int parallel processing, a fixed working time of 6.4 seconds, and consumes no power.", "Tooltip_VendingMachines_03");

        //大型风力发电机
        NameWindTurbine = TextHandler.texter("Large Wind Turbine", "NameWindTurbine");
        Tooltip_WindTurbine_MachineType = TextHandler.texter("Wind Turbine", "Tooltip_WindTurbine_MachineType");
        Tooltip_WindTurbine_Controller = TextHandler.texter("Controller block for the Large Wind Turbine", "Tooltip_WindTurbine_Controller");
        Tooltip_WindTurbine_00 = TextHandler.texter("A large wind turbine that starts operating once the rotor is placed into the controller.", "Tooltip_WindTurbine_00");
        Tooltip_WindTurbine_01 = TextHandler.texter("The higher the grade of the rotor, the more power it generates.", "Tooltip_WindTurbine_01");
        Tooltip_WindTurbine_02 = TextHandler.texter("The power generation formula is: UHV * Wind Factor * Rotor Level.\n" +
            "The Wind Factor refreshes every 30 seconds and ranges from 0.5 to 1.5.", "Tooltip_WindTurbine_02");
        Tooltip_WindTurbine_03 = TextHandler.texter("Use wire cutters to enable or disable wireless mode!In wireless mode, the structure no longer requires a power module.","Tooltip_WindTurbine_03");
        Tooltip_WindTurbine_04 = TextHandler.texter("Clean energy! May your skies remain pollution-free!","Tooltip_WindTurbine_04");
        StructureInfo_WindTurbine_00 = TextHandler.texter("The rotor material is classified as follows:", "StructureInfo_WindTurbine_00");
        StructureInfo_WindTurbine_01 = TextHandler.texter("Wood-Level 1-1x", "StructureInfo_WindTurbine_01");
        StructureInfo_WindTurbine_02 = TextHandler.texter("Iron-Level 2-4x", "StructureInfo_WindTurbine_02");
        StructureInfo_WindTurbine_03 = TextHandler.texter("Steel-Level 2-8x", "StructureInfo_WindTurbine_03");
        StructureInfo_WindTurbine_04 = TextHandler.texter("Carbon-Level 4-16x", "StructureInfo_WindTurbine_04");
        StructureInfo_WindTurbine_05 = TextHandler.texter("EnergyTic Alloy-Level 5-32x", "StructureInfo_WindTurbine_05");
        StructureInfo_WindTurbine_06 = TextHandler.texter("Tungsten Steel-Level 6-64x", "StructureInfo_WindTurbine_06");
        StructureInfo_WindTurbine_07 = TextHandler.texter("Vibrant-Level 7-128x", "StructureInfo_WindTurbine_07");
        StructureInfo_WindTurbine_08 = TextHandler.texter("Iridium-Level 8-256x", "StructureInfo_WindTurbine_08");
        StructureInfo_WindTurbine_09 = TextHandler.texter("They are terrifyingly powerful!", "StructureInfo_WindTurbine_09");

        //太阳能阵列
        NameSolarEnergyArray = TextHandler.texter("Solar Energy Array", "NameSolarEnergyArray");
        Tooltip_SolarEnergyArray_MachineType = TextHandler.texter("Solar Energy Array", "Tooltip_SolarEnergyArray_MachineType");
        Tooltip_SolarEnergyArray_Controller = TextHandler.texter("Controller block for the Solar Energy Array", "Tooltip_SolarEnergyArray_Controller");
        Tooltip_SolarEnergyArray_00 = TextHandler.texter("A large solar panel, better than the smaller one!", "Tooltip_SolarEnergyArray_00");
        Tooltip_SolarEnergyArray_01 = TextHandler.texter("It works at all times, don't ask why! This is a highly advanced machine!", "Tooltip_SolarEnergyArray_01");
        Tooltip_SolarEnergyArray_02 = TextHandler.texter("The power generation formula is MAX * MAX.", "Tooltip_SolarEnergyArray_02");
        Tooltip_SolarEnergyArray_03 = TextHandler.texter("Clean energy! May your skies remain pollution-free!","Tooltip_SolarEnergyArray_03");
        Tooltip_SolarEnergyArray_04 = TextHandler.texter("Place the Astral Array Fabricator in the Controller Block to activate Wireless Mode.", "Tooltip_SolarEnergyArray_04");
        Tooltip_SolarEnergyArray_05 = TextHandler.texter("After activating Wireless Mode, Dynamo Hatches are no longer needed.", "Tooltip_SolarEnergyArray_05");

        //物质重塑仪
        NameSubstanceReshapingDevice = TextHandler.texter("Substance Reshaping Device", "NameSubstanceReshapingDevice");
        Tooltip_SubstanceReshapingDevice_MachineType = TextHandler.texter("Substance Reshaping Device", "Tooltip_SubstanceReshapingDevice_MachineType");
        Tooltip_SubstanceReshapingDevice_Controller = TextHandler.texter("Controller block for the Core Drill", "Tooltip_SubstanceReshapingDevice_Controller");
        Tooltip_SubstanceReshapingDevice_00 = TextHandler.texter("Void begets all things, and chaos forges gods. All matter can be reshaped, but great power cannot be defied.", "Tooltip_SubstanceReshapingDevice_00");
        Tooltip_SubstanceReshapingDevice_01 = TextHandler.texter("The laws of the universe are both a cage and a shackle. All things follow the path of entropy—life will inevitably decay, stars will burn out, and dust will return to dust. But we refuse to accept this fate.", "Tooltip_SubstanceReshapingDevice_01");
        Tooltip_SubstanceReshapingDevice_02 = TextHandler.texter("They are nameless entities, echoes from beyond reality, remnants we brought back from the unknown when we tampered with the laws of the universe","Tooltip_SubstanceReshapingDevice_02");
        Tooltip_SubstanceReshapingDevice_03 = TextHandler.texter("We are the creators of stars, the manipulators of reality, and the ultimate masters of gravity!                                                          ","Tooltip_SubstanceReshapingDevice_03");
        Tooltip_SubstanceReshapingDevice_04 = TextHandler.texter("Some particles exhibit arrangements that defy known physical rules, some substances exist in ways that violate the laws of thermodynamics.","Tooltip_SubstanceReshapingDevice_04");
        Tooltip_SubstanceReshapingDevice_05 = TextHandler.texter("and some creations… vanish the moment we attempt to document them, as if they never existed at all.","Tooltip_SubstanceReshapingDevice_05");
        Tooltip_SubstanceReshapingDevice_06 = TextHandler.texter("The machine supports cross-recipe parallel processing!","Tooltip_SubstanceReshapingDevice_06");
        Tooltip_SubstanceReshapingDevice_07 = TextHandler.texter("Supports up to 10 recipes running simultaneously! The optimal ratio for each recipe is 64*10!","Tooltip_SubstanceReshapingDevice_07");
        Tooltip_SubstanceReshapingDevice_08 = TextHandler.texter("If any recipe exceeds 64 units in operation, it will cause a decrease in total parallel efficiency!","Tooltip_SubstanceReshapingDevice_08");
        Tooltip_SubstanceReshapingDevice_09 = TextHandler.texter("But remember this—creators are also destroyers.","Tooltip_SubstanceReshapingDevice_09");
        Tooltip_SubstanceReshapingDevice_10 = TextHandler.texter("\"When the gods fall silent, mortals may seize the mantle of power. But remember, the mantle was never truly ours to claim.\"","Tooltip_SubstanceReshapingDevice_10");
        SubstanceReshapingDeviceRecipes = TextHandler.texter("Singularity Stabilization Ring Casings Live:  %s","SubstanceReshapingDeviceRecipes");

        //蓝狗喷泉
        NameBlueDogMachine = TextHandler.texter("Blue Dog Fountain","NameBlueDogMachine");
        Tooltip_BlueDogMachine_MachineType = TextHandler.texter("Blue Dog Fountain","Tooltip_BlueDogMachine_MachineType");
        Tooltip_BlueDogMachine_Controller = TextHandler.texter("Controller Block of Blue Dog Fountain","Tooltip_BlueDogMachine_Controller");
        Tooltip_BlueDogMachine_00 = TextHandler.texter("In honor of BlueDog — the legendary creator of unofficial mods!","Tooltip_BlueDogMachine_00");
        Tooltip_BlueDogMachine_01 = TextHandler.texter("A legendary device that turns regular water into ultra-pure water — with nothing else!\n" +
            "Rumor has it the idea came from a team outing…","Tooltip_BlueDogMachine_01");
        Tooltip_BlueDogMachine_02 = TextHandler.texter("They say if you place some curious items inside the machine’s controller, you might witness surprising results!","Tooltip_BlueDogMachine_02");
        Tooltip_BlueDogMachine_03 = TextHandler.texter("Oh, and you’d better not look too closely at where the water comes out… It’s not exactly elegant.","Tooltip_BlueDogMachine_03");
        Tooltip_BlueDogMachine_04 = TextHandler.texter("“He doesn’t look very comfortable, does he?”","Tooltip_BlueDogMachine_04");
        Tooltip_BlueDogMachine_05 = TextHandler.texter("“Not really. He might actually be enjoying it…”","Tooltip_BlueDogMachine_05");
        Tooltip_BlueDogMachine_06 = TextHandler.texter("Enjoy yourself — you’re definitely going to like him!","Tooltip_BlueDogMachine_06");

        //爱搬屎的猴子
        NameMonkeyShit = TextHandler.texter("Monkey Shit","NameMonkeyShit");
        Tooltip_MonkeyShit_MachineType = TextHandler.texter("Monkey Shit","Tooltip_MonkeyShit_MachineType");
        Tooltip_MonkeyShit_Controller = TextHandler.texter("Controller Block of Monkey Shit","Tooltip_MonkeyShit_Controller");
        Tooltip_MonkeyShit_00 = TextHandler.texter("The Relentless Poop Mover!","Tooltip_MonkeyShit_00");
        Tooltip_MonkeyShit_01 = TextHandler.texter("Seriously?! Where on earth did he get all this crap!?","Tooltip_MonkeyShit_01");
        Tooltip_MonkeyShit_02 = TextHandler.texter("At times, he walks near the gods... yet strays far from mankind.","Tooltip_MonkeyShit_02");
        Tooltip_MonkeyShit_03 = TextHandler.texter("“Please, post something else, I’m begging you!”","Tooltip_MonkeyShit_03");
        Tooltip_MonkeyShit_04 = TextHandler.texter("F*** it, you might as well post crap instead of this nonsense!","Tooltip_MonkeyShit_04");
        Tooltip_MonkeyShit_05 = TextHandler.texter("Just pour some smelly liquid into the machine — who knows what kind of surprise you’ll get!","Tooltip_MonkeyShit_05");

        //源石发电机
        NameOrundumDynamo = TextHandler.texter("Orundum Dynamo","NameOrundumDynamo");
        Tooltip_OrundumDynamo_MachineType = TextHandler.texter("Orundum Dynamo","Tooltip_OrundumDynamo_MachineType");
        Tooltip_OrundumDynamo_Controller = TextHandler.texter("Controller Block of Orundum Dynamo","Tooltip_OrundumDynamo_Controller");
        Tooltip_OrundumDynamo_00 = TextHandler.texter("A wondrous device brought from another continent — it runs on pure Originite Prime power!","Tooltip_OrundumDynamo_00");
        Tooltip_OrundumDynamo_01 = TextHandler.texter("It creates an entirely new source of energy!An all-new internal Originium network system!","Tooltip_OrundumDynamo_01");
        Tooltip_OrundumDynamo_02 = TextHandler.texter("Upgrade the coils and glass to achieve greater efficiency and improved parallel performance.","Tooltip_OrundumDynamo_02");
        Tooltip_OrundumDynamo_03 = TextHandler.texter("Every unit of Originite Prime converts into Orundum once it’s used up.","Tooltip_OrundumDynamo_03");
        Tooltip_OrundumDynamo_04 = TextHandler.texter("Completely free from Originium dust contamination!","Tooltip_OrundumDynamo_04");
        Tooltip_OrundumDynamo_05 = TextHandler.texter("The very dawn of the Originium industry—good luck out there, Administrator!","Tooltip_OrundumDynamo_05");

        //电力一形矿机
        NameElectricTypeOneMiningMachine = TextHandler.texter("Electric Type-I Mining Machine","NameElectricTypeOneMiningMachine");
        Tooltip_ElectricTypeOneMiningMachine_MachineType = TextHandler.texter("Power Miner","Tooltip_ElectricTypeOneMiningMachine_MachineType");
        Tooltip_ElectricTypeOneMiningMachine_Controller = TextHandler.texter("Controller Block of Electric Type-I Mining Machine","Tooltip_ElectricTypeOneMiningMachine_Controller");
        Tooltip_ElectricTypeOneMiningMachine_00 = TextHandler.texter("The Electric Type-I Mining Machine is the first piece of equipment you gain access to for fully automated mining of Orundum ore veins.","Tooltip_ElectricTypeOneMiningMachine_00");
        Tooltip_ElectricTypeOneMiningMachine_01 = TextHandler.texter("Just set the structure on top of the Orundum ore veins unique to Planet Talos and it will start operating!","Tooltip_ElectricTypeOneMiningMachine_01");
        Tooltip_ElectricTypeOneMiningMachine_02 = TextHandler.texter("The machine requires at least one Originite Prime Main Vein Block within its detection range to operate.","Tooltip_ElectricTypeOneMiningMachine_02");
        Tooltip_ElectricTypeOneMiningMachine_03 = TextHandler.texter("The detection range is a 7×7×3 cubic area that starts one block behind and two blocks below the machine’s main block.","Tooltip_ElectricTypeOneMiningMachine_03");
        Tooltip_ElectricTypeOneMiningMachine_04 = TextHandler.texter("By default, the machine operates with a single parallel line, yielding 10 Orundum ore every 10 seconds.","Tooltip_ElectricTypeOneMiningMachine_04");
        Tooltip_ElectricTypeOneMiningMachine_05 = TextHandler.texter("","Tooltip_ElectricTypeOneMiningMachine_05");

        //种植机
        NamePlanter = TextHandler.texter("Planter","NamePlanter");
        Tooltip_Planter_MachineType = TextHandler.texter("Planter","Tooltip_Planter_MachineType");
        Tooltip_Planter_Controller = TextHandler.texter("Controller Block of Planter","Tooltip_Planter_Controller");
        Tooltip_Planter_00 = TextHandler.texter("A silo-shaped device capable of cultivating various common plants. Developed in-house by Endfield, it offers real-time monitoring and adjustment of internal lighting, temperature, and plant hormone conditions — its greatest technical advantage.","Tooltip_Planter_00");
        Tooltip_Planter_01 = TextHandler.texter("Comes with 1 parallel slot by default; upgrading the glass casing allows for additional parallel operations.","Tooltip_Planter_01");
        Tooltip_Planter_02 = TextHandler.texter("Operates at 100% work time by default; upgrade the coil to reduce work time.","Tooltip_Planter_02");
        Tooltip_Planter_03 = TextHandler.texter("Can grow the majority of native crops found on Talos-2!","Tooltip_Planter_03");
        Tooltip_Planter_04 = TextHandler.texter("Use together with the Seed Collecting device!","Tooltip_Planter_04");

        //采种机
        NameSeedCollectingMachine = TextHandler.texter("Seed Collecting Machine","NameSeedCollectingMachine");
        Tooltip_SeedCollectingMachine_MachineType = TextHandler.texter("Seed Collecting Machine","Tooltip_SeedCollectingMachine_MachineType");
        Tooltip_SeedCollectingMachine_Controller = TextHandler.texter("Controller Block of Seed Collecting Machine","Tooltip_SeedCollectingMachine_Controller");
        Tooltip_SeedCollectingMachine_00 = TextHandler.texter("A device designed to collect ordinary plant seeds. On Talos-2, a bountiful harvest is always a cause for great joy.","Tooltip_SeedCollectingMachine_00");
        Tooltip_SeedCollectingMachine_01 = TextHandler.texter("Comes with 1 parallel slot by default; upgrading the glass casing allows for additional parallel operations.","Tooltip_SeedCollectingMachine_01");
        Tooltip_SeedCollectingMachine_02 = TextHandler.texter("Operates at 100% work time by default; upgrade the coil to reduce work time.","Tooltip_SeedCollectingMachine_02");
        Tooltip_SeedCollectingMachine_03 = TextHandler.texter("Can gather the majority of native crops found on Talos-2!","Tooltip_SeedCollectingMachine_03");
        Tooltip_SeedCollectingMachine_04 = TextHandler.texter("Use together with the Planter!","Tooltip_SeedCollectingMachine_04");

        //精炼炉
        NameRefiningFurnace = TextHandler.texter("Refining Furnace","NameRefiningFurnace");
        Tooltip_RefiningFurnace_MachineType = TextHandler.texter("Refining Furnace","Tooltip_RefiningFurnace_MachineType");
        Tooltip_RefiningFurnace_Controller = TextHandler.texter("The controller block of the Refining Furnace","Tooltip_RefiningFurnace_Controller");
        Tooltip_RefiningFurnace_00 = TextHandler.texter("A specialized device that refines other materials at extremely high temperatures.","Tooltip_RefiningFurnace_00");
        Tooltip_RefiningFurnace_01 = TextHandler.texter("Stripping, reshaping... under intense heat, the form of the material changes again and again until it emerges completely renewed.","Tooltip_RefiningFurnace_01");
        Tooltip_RefiningFurnace_02 = TextHandler.texter("Starts with 1 parallel line by default and a work time of 200 ticks.","Tooltip_RefiningFurnace_02");
        Tooltip_RefiningFurnace_03 = TextHandler.texter("Upgrade the coils and glass structure to unlock even greater performance!","Tooltip_RefiningFurnace_03");
        Tooltip_RefiningFurnace_04 = TextHandler.texter("This is not an ordinary furnace! Do not try to stuff random raw materials into it!","Tooltip_RefiningFurnace_04");
        Tooltip_RefiningFurnace_05 = TextHandler.texter("I hope you’ve stored up enough Orundum energy!","Tooltip_RefiningFurnace_05");

        //粉碎机
        NamePulverizer = TextHandler.texter("Pulverizer","NamePulverizer");
        Tooltip_Pulverizer_MachineType = TextHandler.texter("Pulverizer","Tooltip_Pulverizer_MachineType");
        Tooltip_Pulverizer_Controller = TextHandler.texter("The controller block of the Pulverizer","Tooltip_Pulverizer_Controller");
        Tooltip_Pulverizer_00 = TextHandler.texter("A heavy-duty industrial device capable of pulverizing various materials.","Tooltip_Pulverizer_00");
        Tooltip_Pulverizer_01 = TextHandler.texter("Do you hear the sound of it chewing through those hard materials?","Tooltip_Pulverizer_01");
        Tooltip_Pulverizer_02 = TextHandler.texter("Machines on Planet Talos can only pulverize items from Planet Talos—makes perfect sense, doesn’t it?","Tooltip_Pulverizer_02");

        //配件机
        NameAccessoriesMachine = TextHandler.texter("Accessories Machine","NameAccessoriesMachine");
        Tooltip_AccessoriesMachine_MachineType = TextHandler.texter("Accessories Machine","Tooltip_AccessoriesMachine_MachineType");
        Tooltip_AccessoriesMachine_Controller = TextHandler.texter("The controller block of the Accessories Machine","Tooltip_AccessoriesMachine_Controller");
        Tooltip_AccessoriesMachine_00 = TextHandler.texter("A versatile processing platform capable of machining all kinds of parts.","Tooltip_AccessoriesMachine_00");
        Tooltip_AccessoriesMachine_01 = TextHandler.texter("Turning, milling, planing, grinding—precision machining all in one machine.","Tooltip_AccessoriesMachine_01");

        //塑形机
        NameShapingMachine = TextHandler.texter("Shaping Machine","NameShapingMachine");
        Tooltip_ShapingMachine_MachineType = TextHandler.texter("Shaping Machine","Tooltip_ShapingMachine_MachineType");
        Tooltip_ShapingMachine_Controller = TextHandler.texter("The controller block of the Shaping Machine","Tooltip_ShapingMachine_Controller");
        Tooltip_ShapingMachine_00 = TextHandler.texter("A specialized device capable of stamping and forming various containers.","Tooltip_ShapingMachine_00");
        Tooltip_ShapingMachine_01 = TextHandler.texter("After holding a product workshop in collaboration with the Alliance Industrial Union,","Tooltip_ShapingMachine_01");
        Tooltip_ShapingMachine_02 = TextHandler.texter("Terminal Land Industries finally overcame the key technical challenge of “mold replacement”.","Tooltip_ShapingMachine_02");

        //研磨机
        NameGrinder = TextHandler.texter("Grinder","NameGrinder");
        Tooltip_Grinder_MachineType = TextHandler.texter("Grinder","Tooltip_Grinder_MachineType");
        Tooltip_Grinder_Controller = TextHandler.texter("The controller block of the Grinder","Tooltip_Grinder_Controller");
        Tooltip_Grinder_00 = TextHandler.texter("A specialized device capable of finely grinding various powdered materials.","Tooltip_Grinder_00");
        Tooltip_Grinder_01 = TextHandler.texter("This machine traces its origins back to a traditional agricultural processing device from the Yan Kingdom.","Tooltip_Grinder_01");

        //封装机
        NameEncapsulationMachine = TextHandler.texter("Encapsulation Machine","NameEncapsulationMachine");
        Tooltip_EncapsulationMachine_MachineType = TextHandler.texter("Encapsulation Machine","Tooltip_EncapsulationMachine_MachineType");
        Tooltip_EncapsulationMachine_Controller = TextHandler.texter("The controller block of the Encapsulation Machine","Tooltip_EncapsulationMachine_Controller");
        Tooltip_EncapsulationMachine_00 = TextHandler.texter("A specialized device used for encapsulating certain energy components.","Tooltip_EncapsulationMachine_00");
        Tooltip_EncapsulationMachine_01 = TextHandler.texter("Across Talos II, many different forms and designs of batteries once emerged.","Tooltip_EncapsulationMachine_01");
        Tooltip_EncapsulationMachine_02 = TextHandler.texter("After humanity finally escaped the quagmire of war, the production standards for these products gradually became unified.","Tooltip_EncapsulationMachine_02");

        //灌装机
        NameFillingUnit = TextHandler.texter("Filling Unit","NameFillingUnit");
        Tooltip_FillingUnit_MachineType = TextHandler.texter("Filling Unit","Tooltip_FillingUnit_MachineType");
        Tooltip_FillingUnit_Controller = TextHandler.texter("The controller block of the Filling Unit","Tooltip_FillingUnit_Controller");
        Tooltip_FillingUnit_00 = TextHandler.texter("A specialized device capable of precisely filling raw materials into containers.","Tooltip_FillingUnit_00");
        Tooltip_FillingUnit_01 = TextHandler.texter("Its patented aseptic filling technology greatly reduces the risk of product contamination.","Tooltip_FillingUnit_01");

        //天有洪炉
        NameForgeOfTheSky = TextHandler.texter("Forge Of The Sky","NameForgeOfTheSky");
        Tooltip_ForgeOfTheSky_MachineType = TextHandler.texter("Forge Of The Sky","Tooltip_ForgeOfTheSky_MachineType");
        Tooltip_ForgeOfTheSky_Controller = TextHandler.texter("The controller block of the Forge Of The Sky","Tooltip_ForgeOfTheSky_Controller");
        Tooltip_ForgeOfTheSky_00 = TextHandler.texter("A specialized device used for synthesizing products related to Xiranite.","Tooltip_ForgeOfTheSky_00");
        Tooltip_ForgeOfTheSky_01 = TextHandler.texter("The Earth gives birth to metals. The Sky holds the Forge.","Tooltip_ForgeOfTheSky_01");

        //提纯机
        NamePurificationUnit = TextHandler.texter("Purification Unit","NamePurificationUnit");
        Tooltip_PurificationUnit_MachineType = TextHandler.texter("Purification Unit","Tooltip_PurificationUnit_MachineType");
        Tooltip_PurificationUnit_Controller = TextHandler.texter("The controller block of the Purification Unit","Tooltip_PurificationUnit_Controller");
        Tooltip_PurificationUnit_00 = TextHandler.texter("A precision device used to purify solutions to higher concentrations.","Tooltip_PurificationUnit_00");
        Tooltip_PurificationUnit_01 = TextHandler.texter("\"You know, my friend, our alchemy has such a glorious history. Look out into space...\"","Tooltip_PurificationUnit_01");
        Tooltip_PurificationUnit_02 = TextHandler.texter("\"How could our journey and achievements possibly end here?\"","Tooltip_PurificationUnit_02");
        Tooltip_PurificationUnit_03 = TextHandler.texter("\"I need a proper environment—an excellent, stable Originium environment.\"","Tooltip_PurificationUnit_03");
        Tooltip_PurificationUnit_04 = TextHandler.texter("\"That’s your reason for almost blowing a hole through the O.M.V. DiJiang?\"","Tooltip_PurificationUnit_04");

        //反应池
        NameReactorCrucible = TextHandler.texter("Reactor Crucible","NameReactorCrucible");
        Tooltip_ReactorCrucible_MachineType = TextHandler.texter("Reactor Crucible","Tooltip_ReactorCrucible_MachineType");
        Tooltip_ReactorCrucible_Controller = TextHandler.texter("The controller block of the Reactor Crucible","Tooltip_ReactorCrucible_Controller");
        Tooltip_ReactorCrucible_00 = TextHandler.texter("","Tooltip_ReactorCrucible_00");
        Tooltip_ReactorCrucible_01 = TextHandler.texter("","Tooltip_ReactorCrucible_01");
        Tooltip_ReactorCrucible_02 = TextHandler.texter("","Tooltip_ReactorCrucible_02");

        //扩容反应池
        NameExpandedCrucible = TextHandler.texter("Expanded Crucible","NameExpandedCrucible");
        Tooltip_ExpandedCrucible_MachineType = TextHandler.texter("Reactor Crucible","Tooltip_PurificationUnit_MachineType");
        Tooltip_ExpandedCrucible_Controller = TextHandler.texter("The controller block of the Expanded Crucible","Tooltip_ExpandedCrucible_Controller");
        Tooltip_ExpandedCrucible_00 = TextHandler.texter("A high-performance device for conducting solid-liquid chemical reactions, equipped with more ports to handle multiple reactions simultaneously.","Tooltip_ExpandedCrucible_00");
        Tooltip_ExpandedCrucible_01 = TextHandler.texter("Higher capacity, greater safety.","Tooltip_ExpandedCrucible_01");
        Tooltip_ExpandedCrucible_02 = TextHandler.texter("This machine supports up to four recipes running in parallel!","Tooltip_ExpandedCrucible_02");

        //水泵
        NameFluidPump = TextHandler.texter("","NameFluidPump");
        Tooltip_FluidPump_MachineType = TextHandler.texter("","Tooltip_FluidPump_MachineType");
        Tooltip_FluidPump_Controller = TextHandler.texter("","Tooltip_FluidPump_Controller");
        Tooltip_FluidPump_00 = TextHandler.texter("","Tooltip_FluidPump_00");
        Tooltip_FluidPump_01 = TextHandler.texter("","Tooltip_FluidPump_01");

        //物品
        EOHB_Harmony = TextHandler.texter("Harmony","EOHB_Harmony");
        EOHB_Harmony_Tooltip = TextHandler.texter("Harmony","EOHB_Harmony");
        EOHB_ChengDuHeart_Tooltip_00 = TextHandler.texter("A... thing? Or maybe a gem? Looks pretty valuable","EOHB_ChengDuHeart_Tooltip_00");
        EOHB_ChengDuHeart_Tooltip_01 = TextHandler.texter("and judging by where it came from… yeah, let’s not think too hard about that.","EOHB_ChengDuHeart_Tooltip_01");
        EOHB_Monkey_Tooltip_00 = TextHandler.texter("Legendary Poop Handler of the Squad!","EOHB_Monkey_Tooltip_00");
        EOHB_Monkey_Tooltip_01 = TextHandler.texter("Monkeys and poop really are a pair of star-crossed lovers...","EOHB_Monkey_Tooltip_01");
        EOHB_Shit_Tooltip_00 = TextHandler.texter("As you can see... this is a pile of... poop.","EOHB_Shit_Tooltip_00");
        EOHB_YuanShi_Tooltip_00 = TextHandler.texter("Widely used in industry, this Originium crystal is difficult to extract.","EOHB_YuanShi_Tooltip_00");
        EOHB_YuanShi_Tooltip_01 = TextHandler.texter("Its production requires very fine processing, but it remains the major energy source of the world and the foundation of Originium Arts.","EOHB_YuanShi_Tooltip_01");
        EOHB_YuanShi_Tooltip_02 = TextHandler.texter("Even if widespread rumors claim \"Originite Prime spreads an incurable disease,\" few can resist its temptation.","EOHB_YuanShi_Tooltip_02");
        EOHB_HeChengYu_Tooltip_00 = TextHandler.texter("Made from Originite Prime, it also contains other minerals.","EOHB_HeChengYu_Tooltip_00");
        EOHB_HeChengYu_Tooltip_01 = TextHandler.texter("In the past, it was used only as a conductive element, but it has since become a store of credit value.","EOHB_HeChengYu_Tooltip_01");
        EOHB_PoSuiYuanShi_Tooltip_00 = TextHandler.texter("A shattered Originite Prime that has lost its energy","EOHB_PoSuiYuanShi_Tooltip_00");
        EOHB_PoSuiYuanShi_Tooltip_01 = TextHandler.texter("there might be a way to restore its potency.","EOHB_PoSuiYuanShi_Tooltip_01");
        EOHB_UpgradeChipMK1_Tooltip_00 = TextHandler.texter("When installed, single‑recipe parallelism increases to 16, single operation time decreases to 100 ticks.","EOHB_UpgradeChipMK1_Tooltip_00");
        EOHB_UpgradeChipMK2_Tooltip_00 = TextHandler.texter("When installed, single‑recipe parallelism increases to 64, single operation time decreases to 50 ticks.","EOHB_UpgradeChipMK2_Tooltip_00");
        EOHB_UpgradeChipMK3_Tooltip_00 = TextHandler.texter("When installed, single‑recipe parallelism increases to 512, single operation time decreases to 20 ticks.","EOHB_UpgradeChipMK3_Tooltip_00");
        EOHB_ArsenicImpact = TextHandler.texter("Arsenic Impact","EOHB_ArsenicImpact");
        EOHB_ArsenicImpact_Tooltip = TextHandler.texter("Omg","EOHB_ArsenicImpact_Tooltip");
        EOHB_Hoyomixium = TextHandler.texter("Magnesium–Hafnium–Europium Alloy","EOHB_Hoyomixium");
        EOHB_Hoyomixium_Tooltip = TextHandler.texter("TECH OTAKUS SAVE THE WORLD","EOHB_Hoyomixium_Tooltip");
        EOHB_YaZhenZhenJi_00 = TextHandler.texter("Automatically used as long as it is in your inventory.","EOHB_YaZhenZhenJi_00");
        EOHB_YaZhenZhenJi_01 = TextHandler.texter("When your health drops below 6 points, it will automatically consume one Bud Needle Injection and start regenerating your HP.","EOHB_YaZhenZhenJi_01");
        EOHB_JinCaoRuanYin_00 = TextHandler.texter("Automatically used as long as it is in your inventory.","EOHB_JinCaoRuanYin_00");
        EOHB_JinCaoRuanYin_01 = TextHandler.texter("When your health drops below 6 points, it automatically consumes one Embroidered Soft Drink and instantly restores a certain amount of HP.","EOHB_JinCaoRuanYin_01");
        EOHB_ForgeOfTheSkyCore_Tooltip_00 = TextHandler.texter("Core component of the multiblock structure Forge of the Sky.","EOHB_ForgeOfTheSkyCore_Tooltip_00");
        EOHB_ForgeOfTheSkyCore_Tooltip_01 = TextHandler.texter("Forge of the Sky, the Earth gives birth to metals.","EOHB_ForgeOfTheSkyCore_Tooltip_01");

        //流体
        EOHB_OriginiumWasteLiquid = TextHandler.texter("Originium Waste Liquid","EOHB_OriginiumWasteLiquid");
        EOHB_PrecipitationAcid = TextHandler.texter("Precipitation Acid","EOHB_PrecipitationAcid");
        EOHB_CupriumSolution = TextHandler.texter("Cuprium Solution","EOHB_CupriumSolution");
        EOHB_InertXirconEffluent = TextHandler.texter("Inert Xircon Effluent","EOHB_InertXirconEffluent");
        EOHB_HetoniteSolution = TextHandler.texter("Hetonite Solution","EOHB_HetoniteSolution");
        EOHB_JinCaoSolution = TextHandler.texter("JinCao Solution","EOHB_JinCaoSolution");
        EOHB_XirconEffluent = TextHandler.texter("Xircon Effluent","EOHB_XirconEffluent");
        EOHB_Sewage = TextHandler.texter("Sewage","EOHB_Sewage");
        EOHB_YaZhenSolution = TextHandler.texter("YaZhen Solution","EOHB_YaZhenSolution");
        EOHB_LiquidXiranite = TextHandler.texter("Liquid Xiranite","EOHB_LiquidXiranite");
        EOHB_LiquidHeavyXiranite = TextHandler.texter("Liquid Heavy Xiranite","EOHB_LiquidHeavyXiranite");
        EOHB_LiquefiedOrundum = TextHandler.texter("Liquefied Orundum","EOHB_LiquefiedOrundum");
        EOHB_CrudeLiquefiedOrundum = TextHandler.texter("Crude Liquefied Orundum","EOHB_CrudeLiquefiedOrundum");
        EOHB_HighEnergyOrundumSolvent = TextHandler.texter("High-Energy Orundum Solvent","EOHB_HighEnergyOrundumSolvent");
        EOHB_UnstableOrundumSolvent = TextHandler.texter("Unstable Orundum Solvent","EOHB_UnstableOrundumSolvent");

        //配方池
        EOHB_Recipe_SubstanceReshapingDevice = TextHandler.texter("Substance Reshaping Device","EOHB_Recipe_SubstanceReshapingDevice");
        EOHB_Recipe_BlueDogFountain = TextHandler.texter("Blue Dog Fountain","EOHB_Recipe_BlueDogFountain");
        EOHB_Recipe_BlueDogFountainMAX = TextHandler.texter("Blue Dog Fountain MAX","EOHB_Recipe_BlueDogFountainMAX");
        EOHB_Recipe_MonkeyShit = TextHandler.texter("The Holy Royal High Priest of Poop Transportation","EOHB_Recipe_MonkeyShit");
        EOHB_Recipe_OrundumDynamo = TextHandler.texter("Orundum Dynamo","EOHB_Recipe_OrundumDynamo");
        EOHB_Recipe_OrundumDynamo_Tooltip_00 = TextHandler.texter("Orundum OutPut：","EOHB_Recipe_OrundumDynamo_Tooltip_00");
        EOHB_Recipe_ElectricTypeOneMiningMachine = TextHandler.texter("Electric Type-I Mining Machine","EOHB_Recipe_ElectricTypeOneMiningMachine");
        EOHB_Recipe_Planter = TextHandler.texter("Planter","EOHB_Recipe_Planter");
        EOHB_Recipe_SeedCollectingMachine = TextHandler.texter("Seed Collecting Machine","EOHB_Recipe_SeedCollectingMachine");
        EOHB_Recipe_RefiningFurnace = TextHandler.texter("Refining Furnace","EOHB_Recipe_RefiningFurnace");
        EOHB_Recipe_Pulverizer = TextHandler.texter("Pulverizer","EOHB_Recipe_Pulverizer");
        EOHB_Recipe_AccessoriesMachine = TextHandler.texter("Accessories Machine","EOHB_Recipe_AccessoriesMachine");
        EOHB_Recipe_ShapingMachine = TextHandler.texter("Shaping Machine","EOHB_Recipe_ShapingMachine");
        EOHB_Recipe_Grinder = TextHandler.texter("Grinder","EOHB_Recipe_Grinder");
        EOHB_Recipe_EncapsulationMachine = TextHandler.texter("Encapsulation Machine","EOHB_Recipe_EncapsulationMachine");
        EOHB_Recipe_FillingUnit = TextHandler.texter("Filling Unit","EOHB_Recipe_FillingUnit");
        EOHB_Recipe_ForgeOfTheSky = TextHandler.texter("Forge Of The Sky","EOHB_Recipe_ForgeOfTheSky");
        EOHB_Recipe_PurificationUnit = TextHandler.texter("Purification Unit","EOHB_Recipe_PurificationUnit");
        EOHB_Recipe_ReactorCrucible = TextHandler.texter("Reactor Crucible","EOHB_Recipe_ReactorCrucible");
        EOHB_Recipe_FluidPump = TextHandler.texter("Fluid Pump","EOHB_Recipe_FluidPump");
    }
}
