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
    public static final String add_LaserSourceHatch;
    public static final String add_LaserTargetHatch;
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
    public static final String EOHB_Waila_EUPreCycle;
    public static final String EOHB_Waila_CurrentRunTime;
    public static final String EOHB_Waila_TotalLaserAmps;
    public static final String EOHB_Waila_MaxLaserTier;
    public static final String EOHB_Waila_OrundumCost;
    public static final String EOHB_Waila_UpgradeBlock;
    public static final String EOHB_Waila_UpgradeLevel;
    public static final String EOHB_Waila_None;
    public static final String EOHB_Waila_OrundumFarallel;
    public static final String EOHB_MachineType_1;
    public static final String EOHB_MachineType_2;
    public static final String EOHB_MachineType_3;
    public static final String EOHB_MachineType_4;
    public static final String EOHB_MachineType_5;
    public static final String EOHB_MachineType_6;
    public static final String EOHB_MachineType_7;
    public static final String EOHB_MachineType_8;
    public static final String EOHB_MachineType_9;
    public static final String EOHB_MachineType_10;
    public static final String EOHB_MachineType_11;
    public static final String EOHB_MachineType_12;
    public static final String EOHB_Environment_STABLE;
    public static final String EOHB_Environment_HUMID;
    public static final String EOHB_Environment_ACRID;
    public static final String EOHB_Environment_XRANITE;
    public static final String EOHB_Environment_NONE;
    public static final String EOHB_Environment_ANY;
    public static final String EOHB_OutPutEnergy;

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

    public static final String NameFluidPumpMK1;
    public static final String Tooltip_FluidPumpMK1_MachineType;
    public static final String Tooltip_FluidPumpMK1_Controller;
    public static final String Tooltip_FluidPumpMK1_00;
    public static final String Tooltip_FluidPumpMK1_01;
    public static final String Tooltip_FluidPumpMK1_02;

    public static final String NameFluidPumpMK2;
    public static final String Tooltip_FluidPumpMK2_MachineType;
    public static final String Tooltip_FluidPumpMK2_Controller;
    public static final String Tooltip_FluidPumpMK2_00;
    public static final String Tooltip_FluidPumpMK2_01;
    public static final String Tooltip_FluidPumpMK2_02;
    public static final String Tooltip_FluidPumpMK2_03;
    public static final String Tooltip_FluidPumpMK2_04;

    public static final String NameElectricTypeTwoMiningMachine;
    public static final String Tooltip_ElectricTypeTwoMiningMachine_MachineType;
    public static final String Tooltip_ElectricTypeTwoMiningMachine_Controller;
    public static final String Tooltip_ElectricTypeTwoMiningMachine_00;
    public static final String Tooltip_ElectricTypeTwoMiningMachine_01;
    public static final String Tooltip_ElectricTypeTwoMiningMachine_02;
    public static final String Tooltip_ElectricTypeTwoMiningMachine_03;

    public static final String NameHighDensityEnergyFluidGenerator;
    public static final String Tooltip_HighDensityEnergyFluidGenerator_MachineType;
    public static final String Tooltip_HighDensityEnergyFluidGenerator_Controller;
    public static final String Tooltip_HighDensityEnergyFluidGenerator_00;
    public static final String Tooltip_HighDensityEnergyFluidGenerator_01;
    public static final String Tooltip_HighDensityEnergyFluidGenerator_02;
    public static final String Tooltip_HighDensityEnergyFluidGenerator_03;
    public static final String Tooltip_HighDensityEnergyFluidGenerator_04;
    public static final String Tooltip_HighDensityEnergyFluidGenerator_05;
    public static final String Tooltip_HighDensityEnergyFluidGenerator_06;
    public static final String Tooltip_HighDensityEnergyFluidGenerator_07;

    public static final String NameIsotopeInfusionReactor;
    public static final String Tooltip_IsotopeInfusionReactor_MachineType;
    public static final String Tooltip_IsotopeInfusionReactor_Controller;
    public static final String Tooltip_IsotopeInfusionReactor_00;
    public static final String Tooltip_IsotopeInfusionReactor_01;
    public static final String Tooltip_IsotopeInfusionReactor_02;
    public static final String Tooltip_IsotopeInfusionReactor_03;
    public static final String Tooltip_IsotopeInfusionReactor_04;
    public static final String Tooltip_IsotopeInfusionReactor_05;
    public static final String Tooltip_IsotopeInfusionReactor_06;
    public static final String Tooltip_IsotopeInfusionReactor_07;
    public static final String Tooltip_IsotopeInfusionReactor_08;
    public static final String Tooltip_IsotopeInfusionReactor_09;
    public static final String Tooltip_IsotopeInfusionReactor_10;
    public static final String Tooltip_IsotopeInfusionReactor_11;
    public static final String Tooltip_IsotopeInfusionReactor_12;
    public static final String Tooltip_IsotopeInfusionReactor_13;

    public static final String NameGasDiffuser;
    public static final String Tooltip_GasDiffuser_MachineType;
    public static final String Tooltip_GasDiffuser_Controller;
    public static final String Tooltip_GasDiffuser_00;
    public static final String Tooltip_GasDiffuser_01;
    public static final String Tooltip_GasDiffuser_02;

    public static final String NameFluid_GasTransmutingUnit;
    public static final String Tooltip_Fluid_GasTransmutingUnit_MachineType;
    public static final String Tooltip_Fluid_GasTransmutingUnit_Controller;
    public static final String Tooltip_Fluid_GasTransmutingUnit_00;
    public static final String Tooltip_Fluid_GasTransmutingUnit_01;
    public static final String Tooltip_Fluid_GasTransmutingUnit_02;
    public static final String Tooltip_Fluid_GasTransmutingUnit_03;

    public static final String NameSolid_GasTransmutingUnit;
    public static final String Tooltip_Solid_GasTransmutingUnit_MachineType;
    public static final String Tooltip_Solid_GasTransmutingUnit_Controller;
    public static final String Tooltip_Solid_GasTransmutingUnit_00;
    public static final String Tooltip_Solid_GasTransmutingUnit_01;
    public static final String Tooltip_Solid_GasTransmutingUnit_02;
    public static final String Tooltip_Solid_GasTransmutingUnit_03;
    public static final String Tooltip_Solid_GasTransmutingUnit_04;

    public static final String NameGasReactorGlobe;
    public static final String Tooltip_GasReactorGlobe_MachineType;
    public static final String Tooltip_GasReactorGlobe_Controller;
    public static final String Tooltip_GasReactorGlobe_00;
    public static final String Tooltip_GasReactorGlobe_01;
    public static final String Tooltip_GasReactorGlobe_02;

    public static final String NameHydroMiningRig;
    public static final String Tooltip_HydroMiningRig_MachineType;
    public static final String Tooltip_HydroMiningRig_Controller;
    public static final String Tooltip_HydroMiningRig_00;
    public static final String Tooltip_HydroMiningRig_01;
    public static final String Tooltip_HydroMiningRig_02;
    public static final String Tooltip_HydroMiningRig_03;
    public static final String Tooltip_HydroMiningRig_04;

    public static final String NameGasExtractor;
    public static final String Tooltip_GasExtractor_MachineType;
    public static final String Tooltip_GasExtractor_Controller;
    public static final String Tooltip_GasExtractor_00;
    public static final String Tooltip_GasExtractor_01;
    public static final String Tooltip_GasExtractor_02;
    public static final String Tooltip_GasExtractor_03;
    public static final String Tooltip_GasExtractor_04;
    public static final String Tooltip_GasExtractor_05;

    public static final String NameSeparatingUnit;
    public static final String Tooltip_SeparatingUnit_MachineType;
    public static final String Tooltip_SeparatingUnit_Controller;
    public static final String Tooltip_SeparatingUnit_00;
    public static final String Tooltip_SeparatingUnit_01;
    public static final String Tooltip_SeparatingUnit_02;

    public static final String NameGearingUnit;
    public static final String Tooltip_GearingUnit_MachineType;
    public static final String Tooltip_GearingUnit_Controller;
    public static final String Tooltip_GearingUnit_00;
    public static final String Tooltip_GearingUnit_01;

    public static final String NameLargeForce_ContainedProliferationMine;
    public static final String Tooltip_LargeForce_ContainedProliferationMine_MachineType;
    public static final String Tooltip_LargeForce_ContainedProliferationMine_Controller;
    public static final String Tooltip_LargeForce_ContainedProliferationMine_00;
    public static final String Tooltip_LargeForce_ContainedProliferationMine_01;
    public static final String Tooltip_LargeForce_ContainedProliferationMine_02;
    public static final String Tooltip_LargeForce_ContainedProliferationMine_03;
    public static final String Tooltip_LargeForce_ContainedProliferationMine_04;
    public static final String Tooltip_LargeForce_ContainedProliferationMine_05;
    public static final String Tooltip_LargeForce_ContainedProliferationMine_06;
    public static final String Tooltip_LargeForce_ContainedProliferationMine_07;

    public static final String NameInternalizedUniverseComputingEngine;
    public static final String Tooltip_InternalizedUniverseComputingEngine_MachineType;
    public static final String Tooltip_InternalizedUniverseComputingEngine_Controller;
    public static final String Tooltip_InternalizedUniverseComputingEngine_00;
    public static final String Tooltip_InternalizedUniverseComputingEngine_01;
    public static final String Tooltip_InternalizedUniverseComputingEngine_02;
    public static final String Tooltip_InternalizedUniverseComputingEngine_03;
    public static final String Tooltip_InternalizedUniverseComputingEngine_04;
    public static final String Tooltip_InternalizedUniverseComputingEngine_05;

    public static final String NameXiraniteSolarPowerGenerator;
    public static final String Tooltip_XiraniteSolarPowerGenerator_MachineType;
    public static final String Tooltip_XiraniteSolarPowerGenerator_Controller;
    public static final String Tooltip_XiraniteSolarPowerGenerator_00;
    public static final String Tooltip_XiraniteSolarPowerGenerator_01;
    public static final String Tooltip_XiraniteSolarPowerGenerator_02;
    public static final String Tooltip_XiraniteSolarPowerGenerator_03;
    public static final String Tooltip_XiraniteSolarPowerGenerator_04;
    public static final String Tooltip_XiraniteSolarPowerGenerator_05;
    public static final String Tooltip_XiraniteSolarPowerGenerator_06;

    public static final String NameXirangAssembler;
    public static final String Tooltip_XirangAssembler_MachineType;
    public static final String Tooltip_XirangAssembler_Controller;
    public static final String Tooltip_XirangAssembler_00;
    public static final String Tooltip_XirangAssembler_01;
    public static final String Tooltip_XirangAssembler_02;
    public static final String Tooltip_XirangAssembler_03;
    public static final String Tooltip_XirangAssembler_04;

    public static final String NameProtocolCore;
    public static final String Tooltip_ProtocolCore_MachineType;
    public static final String Tooltip_ProtocolCore_Controller;
    public static final String Tooltip_ProtocolCore_00;
    public static final String Tooltip_ProtocolCore_01;
    public static final String Tooltip_ProtocolCore_02;
    public static final String Tooltip_ProtocolCore_03;
    public static final String Tooltip_ProtocolCore_04;
    public static final String Tooltip_ProtocolCore_05;
    public static final String Tooltip_ProtocolCore_06;
    public static final String Tooltip_ProtocolCore_07;

    public static final String NameRelayTower;
    public static final String Tooltip_RelayTower_MachineType;
    public static final String Tooltip_RelayTower_Controller;
    public static final String Tooltip_RelayTower_00;
    public static final String Tooltip_RelayTower_01;
    public static final String Tooltip_RelayTower_02;
    public static final String Tooltip_RelayTower_03;
    public static final String Tooltip_RelayTower_04;
    public static final String Tooltip_RelayTower_05;
    public static final String Tooltip_RelayTower_06;

    public static final String NameElectricPylon;
    public static final String Tooltip_ElectricPylon_MachineType;
    public static final String Tooltip_ElectricPylon_Controller;
    public static final String Tooltip_ElectricPylon_00;
    public static final String Tooltip_ElectricPylon_01;
    public static final String Tooltip_ElectricPylon_02;
    public static final String Tooltip_ElectricPylon_03;
    public static final String Tooltip_ElectricPylon_04;
    public static final String Tooltip_ElectricPylon_05;
    public static final String Tooltip_ElectricPylon_06;

    //戴森球
    public static final String NameDysonCore;
    public static final String Tooltip_DysonCore_MachineType;
    public static final String Tooltip_DysonCore_00;
    public static final String Tooltip_DysonCore_01;
    public static final String Tooltip_DysonCore_02;
    public static final String Tooltip_DysonCore_03;

    public static final String NameDysonManufacturingModule;
    public static final String Tooltip_DysonManufacturingModule_MachineType;
    public static final String Tooltip_DysonManufacturingModule_00;
    public static final String Tooltip_DysonManufacturingModule_01;
    public static final String Tooltip_DysonManufacturingModule_02;
    public static final String Tooltip_DysonManufacturingModule_03;

    public static final String NameDysonLaunchModule;
    public static final String Tooltip_DysonLaunchModule_MachineType;
    public static final String Tooltip_DysonLaunchModule_00;
    public static final String Tooltip_DysonLaunchModule_01;
    public static final String Tooltip_DysonLaunchModule_02;
    public static final String Tooltip_DysonLaunchModule_03;

    public static final String NameDysonMassLaunchModule;
    public static final String Tooltip_DysonMassLaunchModule_MachineType;
    public static final String Tooltip_DysonMassLaunchModule_00;
    public static final String Tooltip_DysonMassLaunchModule_01;
    public static final String Tooltip_DysonMassLaunchModule_02;
    public static final String Tooltip_DysonMassLaunchModule_03;

    public static final String NameDysonReceiverModule;
    public static final String Tooltip_DysonReceiverModule_MachineType;
    public static final String Tooltip_DysonReceiverModule_00;
    public static final String Tooltip_DysonReceiverModule_01;
    public static final String Tooltip_DysonReceiverModule_02;

    // 戴森球信息面板 / GUI / 指令
    public static final String Dyson_Info_ModuleConnected;
    public static final String Dyson_Info_ModuleDisconnected;
    public static final String Dyson_Info_ComputeRequirement;
    public static final String Dyson_Info_CloudComponentStock;
    public static final String Dyson_Info_FrameComponentStock;
    public static final String Dyson_Info_ConnectedModules;
    public static final String Dyson_Info_ActiveSlots;
    public static final String Dyson_Info_TeamPaste;
    public static final String Dyson_Info_PersonalComponents;
    public static final String Dyson_Info_DuplicateCore;
    public static final String Dyson_Info_ComputeSatisfied;
    public static final String Dyson_Info_ComputeInsufficient;
    public static final String Dyson_Info_LaunchPriority;
    public static final String Dyson_Info_LaunchBatch;
    public static final String Dyson_Info_MassMode;
    public static final String Dyson_Info_Split;
    public static final String Dyson_Info_StrangeMatter;
    public static final String Dyson_Stat_Cloud;
    public static final String Dyson_Stat_Frame;
    public static final String Dyson_Stat_Paste;
    public static final String Dyson_Stat_Components;
    public static final String Dyson_Stat_StrangeMatter;
    public static final String Dyson_Stat_Stage;
    public static final String Dyson_Stat_Leader;
    public static final String Dyson_Gui_PriorityCloud;
    public static final String Dyson_Gui_PriorityFrame;
    public static final String Dyson_Gui_PriorityTooltip;
    public static final String Dyson_Gui_SplitTitle;
    public static final String Dyson_Gui_SplitEUText;
    public static final String Dyson_Gui_SplitTooltip;
    public static final String Dyson_Gui_EjectMatter;
    public static final String Dyson_Text_Or;
    public static final String Dyson_Cmd_NoPermission;
    public static final String Dyson_Cmd_NotLoaded;
    public static final String Dyson_Cmd_Usage;
    public static final String Dyson_Cmd_UsageStage;
    public static final String Dyson_Cmd_UsageCloud;
    public static final String Dyson_Cmd_UsageFrame;
    public static final String Dyson_Cmd_UsagePaste;
    public static final String Dyson_Cmd_StageRange;
    public static final String Dyson_Cmd_Number;
    public static final String Dyson_Cmd_Reset;
    public static final String Dyson_Cmd_AlreadyComplete;
    public static final String Dyson_Cmd_Completed;
    public static final String Dyson_Cmd_Updated;
    public static final String Dyson_Broadcast_00;
    public static final String Dyson_Broadcast_01;
    public static final String Dyson_Broadcast_UnknownTeam;
    public static final String EOHB_Recipe_DysonManufacturing;

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
    public static final String EOHB_TangZhi;
    public static final String EOHB_TongJiRongYe;
    public static final String EOHB_TongJiJuHeWu;
    public static final String EOHB_FuHeTangJiang;
    public static final String EOHB_FaJiaoTangJiang;
    public static final String EOHB_TangRongYe;
    public static final String EOHB_JuZhiRongJiang;
    public static final String EOHB_FuHeJuZhiJiang;
    public static final String EOHB_YiTieJiang;
    public static final String EOHB_FuHeYiTieJiang;
    public static final String EOHB_BoYiFen;
    public static final String EOHB_DianJiJiang;
    public static final String EOHB_JingZhiDianJiJiang;
    public static final String EOHB_JingTiJiang;
    public static final String EOHB_ShiKeJingTiJiang;
    public static final String EOHB_DuBoJingTiJiang;
    public static final String EOHB_ChiHeJinPeiLiao;
    public static final String EOHB_ChiHeJinJiang;
    public static final String EOHB_JingLianChiHeJinJiang;
    public static final String EOHB_WenDingGaoNengQiTi;
    public static final String EOHB_GaoNengYeTi;
    public static final String EOHB_NingJiaoQianTi;
    public static final String EOHB_JiaoLianNingJiao;
    public static final String EOHB_NiuZhuanChunJiang;
    public static final String EOHB_ChunHuaNiuZhuanChunJiang;
    public static final String EOHB_ChuanTongRongJi;
    public static final String EOHB_GaiXingRongJi;
    public static final String EOHB_NaiSuanJianRongJi;
    public static final String EOHB_RuHuaJi;
    public static final String EOHB_JingZhiQieXiaoYe;
    public static final String EOHB_XiFuJiang;
    public static final String EOHB_NingJieJiang;
    public static final String EOHB_HuanTingDanTi;
    public static final String EOHB_HuanTingJuHeWu;
    public static final String EOHB_GangYuFen;
    public static final String EOHB_MengKuangJingFen;
    public static final String EOHB_YanMoJiang;
    public static final String EOHB_JingZhiYanMoJiang;
    public static final String EOHB_KuangWuJiang;
    public static final String EOHB_JingZhiKuangWuJiang;
    public static final String EOHB_FangSiYe;
    public static final String EOHB_JingZhiFangSiYe;
    public static final String EOHB_NongSuoFangSiYe;
    public static final String EOHB_CuZhiQieXiaoYe;
    public static final String EOHB_GaoJieJingLianYe;
    public static final String EOHB_LiquidXiranite;
    public static final String EOHB_LiquidHeavyXiranite;
    public static final String EOHB_LiquefiedOrundum;
    public static final String EOHB_CrudeLiquefiedOrundum;
    public static final String EOHB_OriginiumWasteLiquid;
    public static final String EOHB_HighEnergyOrundumSolvent;
    public static final String EOHB_UnstableOrundumSolvent;
    public static final String EOHB_ContaminatedOrundumSlurry;
    public static final String EOHB_StabilizedHigh_EnergyOrundumSolvent;
    public static final String EOHB_AnomalousEnergyCondensate;
    public static final String EOHB_AdvancedOrundumFuelPrecursor;
    public static final String EOHB_Acridgen;
    public static final String EOHB_Aquagen;
    public static final String EOHB_Inergen;
    public static final String EOHB_Xiragen;
    public static final String EOHB_HeavyXiragen;
    public static final String EOHB_CupriumGas;
    public static final String EOHB_HetoniteGas;
    public static final String EOHB_PyrroliteGas;
    public static final String EOHB_HighEnergyGas;

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
    public static final String EOHB_KuangMaiCaiJiZhe_Tooltip_00;
    public static final String EOHB_EnergyConnector_Tooltip_00;
    //粥本家批量物品 Tooltip（双语文案见 en_US.lang / zh_CN.lang）
    public static final String[] EOHB_ArknightsItem_LongGu;
    public static final String[] EOHB_ArknightsItem_Tan;
    public static final String[] EOHB_ArknightsItem_TanSu;
    public static final String[] EOHB_ArknightsItem_TanSuZu;
    public static final String[] EOHB_ArknightsItem_JiChuJiaGuJianCai;
    public static final String[] EOHB_ArknightsItem_JinJieJiaGuJianCai;
    public static final String[] EOHB_ArknightsItem_GaoJiJiaGuJianCai;
    public static final String[] EOHB_ArknightsItem_YuanShiSuiPian;
    public static final String[] EOHB_ArknightsItem_ChiJin;
    public static final String[] EOHB_ArknightsItem_JiQiaoGaiYao_Juan1;
    public static final String[] EOHB_ArknightsItem_JiQiaoGaiYao_Juan2;
    public static final String[] EOHB_ArknightsItem_JiQiaoGaiYao_Juan3;
    public static final String[] EOHB_ArknightsItem_NiuZhuanChun;
    public static final String[] EOHB_ArknightsItem_BaiMaChun;
    public static final String[] EOHB_ArknightsItem_ShuangJiNaMiPian;
    public static final String[] EOHB_ArknightsItem_PoSunZhuangZhi;
    public static final String[] EOHB_ArknightsItem_ZhuangZhi;
    public static final String[] EOHB_ArknightsItem_QuanXinZhuangZhi;
    public static final String[] EOHB_ArknightsItem_GaiLiangZhuangZhi;
    public static final String[] EOHB_ArknightsItem_HuaHeQieXiaoYe;
    public static final String[] EOHB_ArknightsItem_DianJiDanYuan;
    public static final String[] EOHB_ArknightsItem_JuNengDongLiDanYuan;
    public static final String[] EOHB_ArknightsItem_D32Gang;
    public static final String[] EOHB_ArknightsItem_ZhongXiangWeiDuiYingTi;
    public static final String[] EOHB_ArknightsItem_YuanYan;
    public static final String[] EOHB_ArknightsItem_GuYuanYan;
    public static final String[] EOHB_ArknightsItem_GuYuanYanZu;
    public static final String[] EOHB_ArknightsItem_TiChunYuanYan;
    public static final String[] EOHB_ArknightsItem_HuanTingJuZhi;
    public static final String[] EOHB_ArknightsItem_HuanTingYuZhiTi;
    public static final String[] EOHB_ArknightsItem_ChiHeJin;
    public static final String[] EOHB_ArknightsItem_ChiHeJinKuai;
    public static final String[] EOHB_ArknightsItem_YiTieSuiPian;
    public static final String[] EOHB_ArknightsItem_YiTie;
    public static final String[] EOHB_ArknightsItem_YiTieZu;
    public static final String[] EOHB_ArknightsItem_YiTieKuai;
    public static final String[] EOHB_ArknightsItem_LeiNingJieHe;
    public static final String[] EOHB_ArknightsItem_ShuangTong;
    public static final String[] EOHB_ArknightsItem_TongNingJi;
    public static final String[] EOHB_ArknightsItem_TongNingJiZu;
    public static final String[] EOHB_ArknightsItem_TongZhenLie;
    public static final String[] EOHB_ArknightsItem_QingMengKuang;
    public static final String[] EOHB_ArknightsItem_SanShuiMengKuang;
    public static final String[] EOHB_ArknightsItem_JingTiYuanJian;
    public static final String[] EOHB_ArknightsItem_JingTiDianLu;
    public static final String[] EOHB_ArknightsItem_JingTiDianZiDanYuan;
    public static final String[] EOHB_ArknightsItem_YanMoShi;
    public static final String[] EOHB_ArknightsItem_WuShuiYanMoShi;
    public static final String[] EOHB_ArknightsItem_NingJiao;
    public static final String[] EOHB_ArknightsItem_JuHeNingJiao;
    public static final String[] EOHB_ArknightsItem_QieXiaoYuanYe;
    public static final String[] EOHB_ArknightsItem_JuHeJi;
    public static final String[] EOHB_ArknightsItem_ShouXingQuGuangTi;
    public static final String[] EOHB_ArknightsItem_RMA70_12;
    public static final String[] EOHB_ArknightsItem_RMA70_24;
    public static final String[] EOHB_ArknightsItem_JingLianRongJi;
    public static final String[] EOHB_ArknightsItem_ZhiYuanLiao;
    public static final String[] EOHB_ArknightsItem_JuSuanZhi;
    public static final String[] EOHB_ArknightsItem_JuSuanZhiZu;
    public static final String[] EOHB_ArknightsItem_JuSuanZhiKuai;
    public static final String[] EOHB_ArknightsItem_ShaoJieHeNingJing;
    public static final String[] EOHB_ArknightsItem_BanZiRanRongJi;
    public static final String[] EOHB_ArknightsItem_DaiTang;
    public static final String[] EOHB_ArknightsItem_Tang;
    public static final String[] EOHB_ArknightsItem_TangZu;
    public static final String[] EOHB_ArknightsItem_TangJuKuai;
    public static final String[] EOHB_ArknightsItem_HeSuXianWei;
    public static final String[] EOHB_ArknightsItem_GuHuaXianWeiBan;
    public static final String[] EOHB_ArknightsItem_YeHuaGaoNengQiTi;
    public static final String[] EOHB_ArknightsItem_YeHuaMiXiJuTi;
    public static final String[] EOHB_ArknightsItem_ZhuanZhiYanZu;
    public static final String[] EOHB_ArknightsItem_ZhuanZhiYanJuKuai;
    public static final String[] EOHB_ArknightsItem_QiYiWuZhi;


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
    public static final String EOHB_Recipe_FluidPumpMK1;
    public static final String EOHB_Recipe_FluidPumpMK2;
    public static final String EOHB_Recipe_ElectricTypeTwoMiningMachine;
    public static final String EOHB_Recipe_HighDensityEnergyFluidGenerator;
    public static final String EOHB_Recipe_IsotopeInfusionReactor;
    public static final String EOHB_Recipe_GasDiffuser;
    public static final String EOHB_Recipe_Fluid_GasTransmutingUnit;
    public static final String EOHB_Recipe_Solid_GasTransmutingUnit;
    public static final String EOHB_Recipe_GasReactorGlobe;
    public static final String EOHB_Recipe_HydroMiningRig;
    public static final String EOHB_Recipe_GasExtractor;
    public static final String EOHB_Recipe_SeparatingUnit;
    public static final String EOHB_Recipe_GearingUnit;
    public static final String EOHB_Recipe_LargeForce_ContainedProliferationMine;
    public static final String EOHB_Recipe_InternalizedUniverseComputingEngine;
    public static final String EOHB_Recipe_XirangAssembler;

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
        add_LaserSourceHatch = TextHandler.texter("Laser Source Hatch","add_LaserSourceHatch");
        add_LaserTargetHatch = TextHandler.texter("Laser Target Hatch","add_LaserTargetHatch");
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
        EOHB_Waila_EUPreCycle = TextHandler.texter("EU per Cycle", "EOHB_Waila_EUPreCycle");
        EOHB_Waila_CurrentRunTime = TextHandler.texter("Current Run Time", "EOHB_Waila_CurrentRunTime");
        EOHB_Waila_TotalLaserAmps = TextHandler.texter("Total Laser Amps", "EOHB_Waila_TotalLaserAmps");
        EOHB_Waila_MaxLaserTier = TextHandler.texter("Max Laser Tier", "EOHB_Waila_MaxLaserTier");
        EOHB_Waila_OrundumCost = TextHandler.texter("Current Orundum Cost","EOHB_Waila_OrundumCost");
        EOHB_Waila_UpgradeBlock = TextHandler.texter("Upgrade Block","EOHB_Waila_UpgradeBlock");
        EOHB_Waila_UpgradeLevel = TextHandler.texter("Upgrade Block Level","EOHB_Waila_UpgradeLevel");
        EOHB_Waila_None = TextHandler.texter("None","EOHB_Waila_None");
        EOHB_Waila_OrundumFarallel = TextHandler.texter("Current effective parallel runs","EOHB_Waila_OrundumFarallel");
        EOHB_MachineType_1 = TextHandler.texter("Hint Block #1","EOHB_MachineType_1");
        EOHB_MachineType_2 = TextHandler.texter("Hint Block #2","EOHB_MachineType_2");
        EOHB_MachineType_3 = TextHandler.texter("Hint Block #3","EOHB_MachineType_3");
        EOHB_MachineType_4 = TextHandler.texter("Hint Block #4","EOHB_MachineType_4");
        EOHB_MachineType_5 = TextHandler.texter("Hint Block #5","EOHB_MachineType_5");
        EOHB_MachineType_6 = TextHandler.texter("Hint Block #6","EOHB_MachineType_6");
        EOHB_MachineType_7 = TextHandler.texter("Hint Block #7","EOHB_MachineType_7");
        EOHB_MachineType_8 = TextHandler.texter("Hint Block #8","EOHB_MachineType_8");
        EOHB_MachineType_9 = TextHandler.texter("Hint Block #9","EOHB_MachineType_9");
        EOHB_MachineType_10 = TextHandler.texter("Hint Block #10","EOHB_MachineType_10");
        EOHB_MachineType_11 = TextHandler.texter("Hint Block #11","EOHB_MachineType_11");
        EOHB_MachineType_12 = TextHandler.texter("Hint Block #12","EOHB_MachineType_12");
        EOHB_Environment_STABLE = TextHandler.texter("Required Gas Environment: Stable","EOHB_Environment_STABLE");
        EOHB_Environment_HUMID = TextHandler.texter("Required Gas Environment: Humid","EOHB_Environment_HUMID");
        EOHB_Environment_ACRID = TextHandler.texter("Required Gas Environment: Acrid","EOHB_Environment_ACRID");
        EOHB_Environment_XRANITE = TextHandler.texter("Required Gas Environment: Xranite","EOHB_Environment_XRANITE");
        EOHB_Environment_NONE = TextHandler.texter("Required Gas Environment: None","EOHB_Environment_NONE");
        EOHB_Environment_ANY = TextHandler.texter("Required Gas Environment: Any","EOHB_Environment_ANY");
        EOHB_OutPutEnergy = TextHandler.texter("Current Power Generation Capacity","EOHB_OutPutEnergy");

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
        Tooltip_ReactorCrucible_00 = TextHandler.texter("A specialized device used for solid-liquid chemical reactions.","Tooltip_ReactorCrucible_00");
        Tooltip_ReactorCrucible_01 = TextHandler.texter("Terminal Industries ultimately decided to order a batch of special steel from the southern region of Talos II, produced by the Syndicate.","Tooltip_ReactorCrucible_01");
        Tooltip_ReactorCrucible_02 = TextHandler.texter("This was done to counter the corrosive failures occurring within their reaction equipment.","Tooltip_ReactorCrucible_02");

        //扩容反应池
        NameExpandedCrucible = TextHandler.texter("Expanded Crucible","NameExpandedCrucible");
        Tooltip_ExpandedCrucible_MachineType = TextHandler.texter("Reactor Crucible","Tooltip_PurificationUnit_MachineType");
        Tooltip_ExpandedCrucible_Controller = TextHandler.texter("The controller block of the Expanded Crucible","Tooltip_ExpandedCrucible_Controller");
        Tooltip_ExpandedCrucible_00 = TextHandler.texter("A high-performance device for conducting solid-liquid chemical reactions, equipped with more ports to handle multiple reactions simultaneously.","Tooltip_ExpandedCrucible_00");
        Tooltip_ExpandedCrucible_01 = TextHandler.texter("Higher capacity, greater safety.","Tooltip_ExpandedCrucible_01");
        Tooltip_ExpandedCrucible_02 = TextHandler.texter("This machine supports up to four recipes running in parallel!","Tooltip_ExpandedCrucible_02");

        //水泵MK1
        NameFluidPumpMK1 = TextHandler.texter("Fluid Pump MK1","NameFluidPumpMK1");
        Tooltip_FluidPumpMK1_MachineType = TextHandler.texter("Fluid Pump","Tooltip_FluidPumpMK1_MachineType");
        Tooltip_FluidPumpMK1_Controller = TextHandler.texter("ontroller block of the Fluid Pump MK1","Tooltip_FluidPumpMK1_Controller");
        Tooltip_FluidPumpMK1_00 = TextHandler.texter("A basic device used for extracting liquids.","Tooltip_FluidPumpMK1_00");
        Tooltip_FluidPumpMK1_01 = TextHandler.texter("The commerce division procured basic pump components, and the manufacturing base added a power-supply module—thus, a pressurized device capable of integration within industrial systems was born.","Tooltip_FluidPumpMK1_01");
        Tooltip_FluidPumpMK1_02 = TextHandler.texter("The area beneath the pipeline structure at the rear of the machine must be a liquid source for it to operate!","Tooltip_FluidPumpMK1_02");

        //水泵MK2
        NameFluidPumpMK2 = TextHandler.texter("Fluid Pump MK2","NameFluidPumpMK2");
        Tooltip_FluidPumpMK2_MachineType = TextHandler.texter("Fluid Pump","Tooltip_FluidPumpMK2_MachineType");
        Tooltip_FluidPumpMK2_Controller = TextHandler.texter("ontroller block of the Fluid Pump MK2","Tooltip_FluidPumpMK2_Controller");
        Tooltip_FluidPumpMK2_00 = TextHandler.texter("A powered device used for extracting liquids, capable of handling corrosive fluids.","Tooltip_FluidPumpMK2_00");
        Tooltip_FluidPumpMK2_01 = TextHandler.texter("According to rather unreliable reports, engineers involved in developing the Type‑II acid‑resistant pump once whispered concerns about this product in a corner of the O.M.V.DiJiang’s cafeteria.","Tooltip_FluidPumpMK2_01");
        Tooltip_FluidPumpMK2_02 = TextHandler.texter("Admittedly, Precipitation Acid has been proven to be a sufficiently safe material, yet they still worried that it might react uncontrollably with other Angel‑related substances.","Tooltip_FluidPumpMK2_02");
        Tooltip_FluidPumpMK2_03 = TextHandler.texter("Therefore, an emergency‑lock module was added to this model.","Tooltip_FluidPumpMK2_03");
        Tooltip_FluidPumpMK2_04 = TextHandler.texter("The area beneath the pipeline structure at the rear of the machine must be a liquid source for it to operate!","Tooltip_FluidPumpMK2_04");

        //电力二型矿机
        NameElectricTypeTwoMiningMachine = TextHandler.texter("Electric Type‑II Mining Machine","NameElectricTypeTwoMiningMachine");
        Tooltip_ElectricTypeTwoMiningMachine_MachineType = TextHandler.texter("Electric Mining Machine","Tooltip_ElectricTypeTwoMiningMachine_MachineType");
        Tooltip_ElectricTypeTwoMiningMachine_Controller = TextHandler.texter("Controller block of the Electric Type‑II Mining Machine","Tooltip_ElectricTypeTwoMiningMachine_Controller");
        Tooltip_ElectricTypeTwoMiningMachine_00 = TextHandler.texter("An advanced version of the Electric Type‑I Mining Machine, featuring sharper drill heads and the ability to extract multiple kinds of ores.","Tooltip_ElectricTypeTwoMiningMachine_00");
        Tooltip_ElectricTypeTwoMiningMachine_01 = TextHandler.texter("Place the structural core near the primary ore‑vein blocks unique to Planet Talos II to begin operation!","Tooltip_ElectricTypeTwoMiningMachine_01");
        Tooltip_ElectricTypeTwoMiningMachine_02 = TextHandler.texter("At least one corresponding primary ore‑vein block must exist within the detection range, otherwise the device cannot start.","Tooltip_ElectricTypeTwoMiningMachine_02");
        Tooltip_ElectricTypeTwoMiningMachine_03 = TextHandler.texter("Detection range: from the main block of the machine, extends 1 block backward and 2 blocks downward, covering a 7×7×3 cuboid area.","Tooltip_ElectricTypeTwoMiningMachine_03");

        //高密度能量液体发生器
        NameHighDensityEnergyFluidGenerator = TextHandler.texter("High-Density Energy Fluid Generator","NameHighDensityEnergyFluidGenerator");
        Tooltip_HighDensityEnergyFluidGenerator_MachineType = TextHandler.texter("High-Density Energy Fluid Generator","Tooltip_HighDensityEnergyFluidGenerator_MachineType");
        Tooltip_HighDensityEnergyFluidGenerator_Controller = TextHandler.texter("Controller block of the High-Density Energy Fluid Generator","Tooltip_HighDensityEnergyFluidGenerator_Controller");
        Tooltip_HighDensityEnergyFluidGenerator_00 = TextHandler.texter("The High-Density Energy Fluid Generator is an industrial-grade device dedicated to the production of high-energy fluid.","Tooltip_HighDensityEnergyFluidGenerator_00");
        Tooltip_HighDensityEnergyFluidGenerator_01 = TextHandler.texter("It is dedicated to producing high-energy fluid that can be used directly.","Tooltip_HighDensityEnergyFluidGenerator_01");
        Tooltip_HighDensityEnergyFluidGenerator_02 = TextHandler.texter("During operation, it first consumes Orundum energy to keep the pumps and control systems stable.","Tooltip_HighDensityEnergyFluidGenerator_02");
        Tooltip_HighDensityEnergyFluidGenerator_03 = TextHandler.texter("At the same time, EU power is continuously supplied and injected into a fully sealed reaction chamber.","Tooltip_HighDensityEnergyFluidGenerator_03");
        Tooltip_HighDensityEnergyFluidGenerator_04 = TextHandler.texter("Inside the chamber, the fluid comes into full contact with the injected energy.","Tooltip_HighDensityEnergyFluidGenerator_04");
        Tooltip_HighDensityEnergyFluidGenerator_05 = TextHandler.texter("It then passes through sequential stages of pressurization, heating, and continuous stirring.","Tooltip_HighDensityEnergyFluidGenerator_05");
        Tooltip_HighDensityEnergyFluidGenerator_06 = TextHandler.texter("This gradually raises the fluid’s energy level and converts it into a stable high-energy fluid.","Tooltip_HighDensityEnergyFluidGenerator_06");
        Tooltip_HighDensityEnergyFluidGenerator_07 = TextHandler.texter("Each recipe run additionally consumes 100000 EU from the Wireless Energy network","Tooltip_HighDensityEnergyFluidGenerator_07");

        //同位素注入反应器
        NameIsotopeInfusionReactor = TextHandler.texter("Isotope Infusion Reactor", "NameIsotopeInfusionReactor");
        Tooltip_IsotopeInfusionReactor_MachineType = TextHandler.texter("Isotope Infusion Reactor", "Tooltip_IsotopeInfusionReactor_MachineType");
        Tooltip_IsotopeInfusionReactor_Controller = TextHandler.texter("Controller block of the Isotope Infusion Reactor", "Tooltip_IsotopeInfusionReactor_Controller");
        Tooltip_IsotopeInfusionReactor_00 = TextHandler.texter("Insert radioactive blocks into the left radiation chamber to initialize the isotope infusion process.", "Tooltip_IsotopeInfusionReactor_00");
        Tooltip_IsotopeInfusionReactor_01 = TextHandler.texter("The material is transferred through pipelines into the core cavity and heated to bombardment temperature.", "Tooltip_IsotopeInfusionReactor_01");
        Tooltip_IsotopeInfusionReactor_02 = TextHandler.texter("Under high pressure, it undergoes neutron-flux treatment and finally outputs high-density liquid or solid materials from the top outlet!", "Tooltip_IsotopeInfusionReactor_02");
        Tooltip_IsotopeInfusionReactor_03 = TextHandler.texter("Typical isotopes include Plutonium‑238, Uranium‑233, Radium, and Technetium.", "Tooltip_IsotopeInfusionReactor_03");
        Tooltip_IsotopeInfusionReactor_04 = TextHandler.texter("At upgrade level 0, the maximum parallel recipe count is 4.", "Tooltip_IsotopeInfusionReactor_04");
        Tooltip_IsotopeInfusionReactor_05 = TextHandler.texter("When level n ≥ 1, the parallel limit becomes 4^(n+1).", "Tooltip_IsotopeInfusionReactor_05");
        Tooltip_IsotopeInfusionReactor_06 = TextHandler.texter("Base processing time (no upgrade): 800 ticks.", "Tooltip_IsotopeInfusionReactor_06");
        Tooltip_IsotopeInfusionReactor_07 = TextHandler.texter("At levels 1–4, processing time shortens roughly geometrically—ensure a sufficiently strong power grid.", "Tooltip_IsotopeInfusionReactor_07");
        Tooltip_IsotopeInfusionReactor_08 = TextHandler.texter("At upgrade level ≥ 5, wireless mode processing time is locked at 20 ticks and will not decrease further.", "Tooltip_IsotopeInfusionReactor_08");
        Tooltip_IsotopeInfusionReactor_09 = TextHandler.texter("According to an internal lab memo, below this threshold it is usually the server, not the reactor, that melts first.", "Tooltip_IsotopeInfusionReactor_09");
        Tooltip_IsotopeInfusionReactor_10 = TextHandler.texter("Engineers once debated enabling multi‑isotope mixing for higher efficiency.", "Tooltip_IsotopeInfusionReactor_10");
        Tooltip_IsotopeInfusionReactor_11 = TextHandler.texter("The plan was abandoned after a joint statement from the “Demolition Team (refusing to sign)” and the “Janitorial Staff (refusing to work overtime)”—the feature remains sealed beneath a coffee stain on the blueprint.", "Tooltip_IsotopeInfusionReactor_11");
        Tooltip_IsotopeInfusionReactor_12 = TextHandler.texter("Friendly reminder: do not drink the reactor’s output or barbecue on top of it.", "Tooltip_IsotopeInfusionReactor_12");
        Tooltip_IsotopeInfusionReactor_13 = TextHandler.texter("If you hear the material whispering its own name, shut down the power immediately and contact your nearest Radiation Safety Office.", "Tooltip_IsotopeInfusionReactor_13");

        //气体散布机
        NameGasDiffuser = TextHandler.texter("Gas Diffuser","NameGasDiffuser");
        Tooltip_GasDiffuser_MachineType = TextHandler.texter("Gas Diffuser","Tooltip_GasDiffuser_MachineType");
        Tooltip_GasDiffuser_Controller = TextHandler.texter("Controller block of the Gas Diffuser","Tooltip_GasDiffuser_Controller");
        Tooltip_GasDiffuser_00 = TextHandler.texter("It is a device that can disperse continuously‑input gas around itself, thereby changing the surrounding environmental state.","Tooltip_GasDiffuser_00");
        Tooltip_GasDiffuser_01 = TextHandler.texter("It only changes the surroundings during its effective operation and will not cause any harm to the actual environment.","Tooltip_GasDiffuser_01");
        Tooltip_GasDiffuser_02 = TextHandler.texter("\"This thing is not for you to use as an air purifier, and please don’t pour perfume into it next time, okay?\"","Tooltip_GasDiffuser_02");

        //液气转化机
        NameFluid_GasTransmutingUnit = TextHandler.texter("Fluid-Gas Transmuting Unit","NameFluid_GasTransmutingUnit");
        Tooltip_Fluid_GasTransmutingUnit_MachineType = TextHandler.texter("Fluid-Gas Transmuting Unit","Tooltip_Fluid_GasTransmutingUnit_MachineType");
        Tooltip_Fluid_GasTransmutingUnit_Controller = TextHandler.texter("Controller block of the Fluid-Gas Transmuting Unit","Tooltip_Fluid_GasTransmutingUnit_Controller");
        Tooltip_Fluid_GasTransmutingUnit_00 = TextHandler.texter("When activated by a steady input of Liquid Xiranite, this facility can transmute fluid materials into gas, and vice versa.","Tooltip_Fluid_GasTransmutingUnit_00");
        Tooltip_Fluid_GasTransmutingUnit_01 = TextHandler.texter("A special facility that can transmute materials into another state.","Tooltip_Fluid_GasTransmutingUnit_01");
        Tooltip_Fluid_GasTransmutingUnit_02 = TextHandler.texter("The process may sound simple, but every single component of the facility, down to the most basic thermometer, is the pinnacle of modern Originium Arts and technology.","Tooltip_Fluid_GasTransmutingUnit_02");
        Tooltip_Fluid_GasTransmutingUnit_03 = TextHandler.texter("The Liquid Xiranite input hatch is located in a dedicated input slot at the center of the machine.","Tooltip_Fluid_GasTransmutingUnit_03");

        //固气转化机
        NameSolid_GasTransmutingUnit = TextHandler.texter("Solid-Gas Transmuting Unit","NameSolid_GasTransmutingUnit");
        Tooltip_Solid_GasTransmutingUnit_MachineType = TextHandler.texter("Solid-Gas Transmuting Unit","Tooltip_Solid_GasTransmutingUnit_MachineType");
        Tooltip_Solid_GasTransmutingUnit_Controller = TextHandler.texter("Controller block of the Solid-Gas Transmuting Unit","Tooltip_Solid_GasTransmutingUnit_Controller");
        Tooltip_Solid_GasTransmutingUnit_00 = TextHandler.texter("When activated by a steady input of Xiragen, this facility can transmute solid materials into gas, and vice versa.","Tooltip_Solid_GasTransmutingUnit_00");
        Tooltip_Solid_GasTransmutingUnit_01 = TextHandler.texter("A special facility that can transmute materials into another state. During the design phase, the engineers accessed every","Tooltip_Solid_GasTransmutingUnit_01");
        Tooltip_Solid_GasTransmutingUnit_02 = TextHandler.texter("entry on Originium Arts and technology in the SOW database. Successful recovery of data lost during the Aggeloi War","Tooltip_Solid_GasTransmutingUnit_02");
        Tooltip_Solid_GasTransmutingUnit_03 = TextHandler.texter("provided crucial inspiration for the scientists today.","Tooltip_Solid_GasTransmutingUnit_03");
        Tooltip_Solid_GasTransmutingUnit_04 = TextHandler.texter("The Liquid Xiranite input hatch is located in a dedicated input slot at the center of the machine.","Tooltip_Solid_GasTransmutingUnit_04");

        //气体反应炉
        NameGasReactorGlobe = TextHandler.texter("Gas Reactor Globe","NameGasReactorGlobe");
        Tooltip_GasReactorGlobe_MachineType = TextHandler.texter("Gas Reactor Globe","Tooltip_GasReactorGlobe_MachineType");
        Tooltip_GasReactorGlobe_Controller = TextHandler.texter("Controller block of the Gas Reactor Globe","Tooltip_GasReactorGlobe_Controller");
        Tooltip_GasReactorGlobe_00 = TextHandler.texter("A facility for performing gas-phase reactions.","Tooltip_GasReactorGlobe_00");
        Tooltip_GasReactorGlobe_01 = TextHandler.texter("\"The facility must be completely hermetic and include thermostats, humidity controllers, and separator modules.","Tooltip_GasReactorGlobe_01");
        Tooltip_GasReactorGlobe_02 = TextHandler.texter("\"You think I'm complaining about design challenges? No. In fact, I'm planning to fit a self-destruct module into this contraption.\"","Tooltip_GasReactorGlobe_02");

        //水驱矿机
        NameHydroMiningRig = TextHandler.texter("Hydro Mining Rig","NameHydroMiningRig");
        Tooltip_HydroMiningRig_MachineType = TextHandler.texter("Hydro Mining Rig","Tooltip_HydroMiningRig_MachineType");
        Tooltip_HydroMiningRig_Controller = TextHandler.texter("Controller block of the Hydro Mining Rig","Tooltip_HydroMiningRig_Controller");
        Tooltip_HydroMiningRig_00 = TextHandler.texter("A mining rig for digging up various types of ore such as Cuprium Ore. This rig does not need power as it can be driven with Clean Water.","Tooltip_HydroMiningRig_00");
        Tooltip_HydroMiningRig_01 = TextHandler.texter("The Endfield Hydro Mining Rig features a special coating to minimize the riskof rusting in humid/wet environments.","Tooltip_HydroMiningRig_01");
        Tooltip_HydroMiningRig_02 = TextHandler.texter("Place the structural core near the primary ore‑vein blocks unique to Planet Talos II to begin operation!","Tooltip_HydroMiningRig_02");
        Tooltip_HydroMiningRig_03 = TextHandler.texter("At least one corresponding primary ore‑vein block must exist within the detection range, otherwise the device cannot start.","Tooltip_HydroMiningRig_03");
        Tooltip_HydroMiningRig_04 = TextHandler.texter("Detection range: from the main block of the machine, extends 1 block backward and 2 blocks downward, covering a 7×7×3 cuboid area.","Tooltip_HydroMiningRig_04");

        //气体收集泵
        NameGasExtractor = TextHandler.texter("Gas Extractor","NameGasExtractor");
        Tooltip_GasExtractor_MachineType = TextHandler.texter("Gas Extractor","Tooltip_GasExtractor_MachineType");
        Tooltip_GasExtractor_Controller = TextHandler.texter("Controller block of the Gas Extractor","Tooltip_GasExtractor_Controller");
        Tooltip_GasExtractor_00 = TextHandler.texter("It does not require power to work.","Tooltip_GasExtractor_00");
        Tooltip_GasExtractor_01 = TextHandler.texter("A facility that can extract Inergen and other gases.","Tooltip_GasExtractor_01");
        Tooltip_GasExtractor_02 = TextHandler.texter("Those companies would never realize why Endfield Industries bought up all those vacuum cleaner patents.","Tooltip_GasExtractor_02");
        Tooltip_GasExtractor_03 = TextHandler.texter("Place the structural core near the primary ore‑vein blocks unique to Planet Talos II to begin operation!","Tooltip_GasExtractor_03");
        Tooltip_GasExtractor_04 = TextHandler.texter("At least one corresponding primary ore‑vein block must exist within the detection range, otherwise the device cannot start.","Tooltip_GasExtractor_04");
        Tooltip_GasExtractor_05 = TextHandler.texter("Detection range: from the main block of the machine, extends 1 block backward and 2 blocks downward, covering a 7×7×3 cuboid area.","Tooltip_GasExtractor_05");

        //拆解机
        NameSeparatingUnit = TextHandler.texter("Separating Unit","NameSeparatingUnit");
        Tooltip_SeparatingUnit_MachineType = TextHandler.texter("Separating Unit","Tooltip_SeparatingUnit_MachineType");
        Tooltip_SeparatingUnit_Controller = TextHandler.texter("Controller block of the Separating Unit","Tooltip_SeparatingUnit_Controller");
        Tooltip_SeparatingUnit_00 = TextHandler.texter("A facility that physically disassembles various items.","Tooltip_SeparatingUnit_00");
        Tooltip_SeparatingUnit_01 = TextHandler.texter("Eighty percent of those who first heard the story of the separating unit simply refused to believe this fact: ","Tooltip_SeparatingUnit_01");
        Tooltip_SeparatingUnit_02 = TextHandler.texter("The design of this facility was inspired by large UWST disassembling stations for sawing and cutting apart Aggeloi carcasses.","Tooltip_SeparatingUnit_02");

        //装备原件机
        NameGearingUnit = TextHandler.texter("Gearing Unit","NameGearingUnit");
        Tooltip_GearingUnit_MachineType = TextHandler.texter("Gearing Unit","Tooltip_GearingUnit_MachineType");
        Tooltip_GearingUnit_Controller = TextHandler.texter("Controller block of the Gearing Unit","Tooltip_GearingUnit_Controller");
        Tooltip_GearingUnit_00 = TextHandler.texter("A facility that laminates different materials together to create gear components.","Tooltip_GearingUnit_00");
        Tooltip_GearingUnit_01 = TextHandler.texter("A top-tier piece of equipment must undergo multiple processing steps that include textile weaving and compression lamination of various fabric and lining.","Tooltip_GearingUnit_01");

        //AG-07 大型力场约束式增殖矿场
        NameLargeForce_ContainedProliferationMine = TextHandler.texter("","NameLargeForce_ContainedProliferationMine");
        Tooltip_LargeForce_ContainedProliferationMine_MachineType = TextHandler.texter("","Tooltip_LargeForce_ContainedProliferationMine_MachineType");
        Tooltip_LargeForce_ContainedProliferationMine_Controller = TextHandler.texter("","Tooltip_LargeForce_ContainedProliferationMine_Controller");
        Tooltip_LargeForce_ContainedProliferationMine_00 = TextHandler.texter("","Tooltip_LargeForce_ContainedProliferationMine_00");
        Tooltip_LargeForce_ContainedProliferationMine_01 = TextHandler.texter("","Tooltip_LargeForce_ContainedProliferationMine_01");
        Tooltip_LargeForce_ContainedProliferationMine_02 = TextHandler.texter("","Tooltip_LargeForce_ContainedProliferationMine_02");
        Tooltip_LargeForce_ContainedProliferationMine_03 = TextHandler.texter("","Tooltip_LargeForce_ContainedProliferationMine_03");
        Tooltip_LargeForce_ContainedProliferationMine_04 = TextHandler.texter("","Tooltip_LargeForce_ContainedProliferationMine_04");
        Tooltip_LargeForce_ContainedProliferationMine_05 = TextHandler.texter("","Tooltip_LargeForce_ContainedProliferationMine_05");
        Tooltip_LargeForce_ContainedProliferationMine_06 = TextHandler.texter("","Tooltip_LargeForce_ContainedProliferationMine_06");
        Tooltip_LargeForce_ContainedProliferationMine_07 = TextHandler.texter("","Tooltip_LargeForce_ContainedProliferationMine_07");

        //内化宇宙演算机
        NameInternalizedUniverseComputingEngine = TextHandler.texter("Internalized Universe Computing Engine","NameInternalizedUniverseComputingEngine");
        Tooltip_InternalizedUniverseComputingEngine_MachineType = TextHandler.texter("Orundum Computer","Tooltip_InternalizedUniverseComputingEngine_MachineType");
        Tooltip_InternalizedUniverseComputingEngine_Controller = TextHandler.texter("Controller block of the Internalized Universe Computing Engine","Tooltip_InternalizedUniverseComputingEngine_Controller");
        Tooltip_InternalizedUniverseComputingEngine_00 = TextHandler.texter("A machine that provides basic Orundum-internalized universe computing power, by retrieving information from the internalized universe within Orundum to generate computation.","Tooltip_InternalizedUniverseComputingEngine_00");
        Tooltip_InternalizedUniverseComputingEngine_01 = TextHandler.texter("Simple, isn't it? Just supply power and feed it some Orundum, and you'll get 200 points of computing power from the internalized universe.","Tooltip_InternalizedUniverseComputingEngine_01");
        Tooltip_InternalizedUniverseComputingEngine_02 = TextHandler.texter("In theory, the computing engine extracts \"universal fundamental information\" from Orundum, but what actually comes out is always baffling.","Tooltip_InternalizedUniverseComputingEngine_02");
        Tooltip_InternalizedUniverseComputingEngine_03 = TextHandler.texter("One piece of Orundum output nothing but a shopping list: \"three pounds of apples, two pounds of pork, and a dozen eggs.\"","Tooltip_InternalizedUniverseComputingEngine_03");
        Tooltip_InternalizedUniverseComputingEngine_04 = TextHandler.texter("Another piece was even more absurd — the extraction turned out to be the complete genome sequence of the neighbor's dog.","Tooltip_InternalizedUniverseComputingEngine_04");
        Tooltip_InternalizedUniverseComputingEngine_05 = TextHandler.texter("In the end, everyone concluded that Orundum doesn't store cosmic truths — it stores all the mundane everyday life of this world.","Tooltip_InternalizedUniverseComputingEngine_05");

        //息壤太阳能
        NameXiraniteSolarPowerGenerator = TextHandler.texter("Xiranite Solar Power Generator","NameXiraniteSolarPowerGenerator");
        Tooltip_XiraniteSolarPowerGenerator_MachineType = TextHandler.texter("Xiranite Solar Power Generator","Tooltip_XiraniteSolarPowerGenerator_MachineType");
        Tooltip_XiraniteSolarPowerGenerator_Controller = TextHandler.texter("Controller block of the Xiranite Solar Power Generator","Tooltip_XiraniteSolarPowerGenerator_Controller");
        Tooltip_XiraniteSolarPowerGenerator_00 = TextHandler.texter("By adding Xiranite from Wuling to the machine, engineers finally solved the problem of excessive energy loss in solar power generation.","Tooltip_XiraniteSolarPowerGenerator_00");
        Tooltip_XiraniteSolarPowerGenerator_01 = TextHandler.texter("Standard output is 1M per 100 ticks. If provided with a Xiranite gas atmosphere, output increases to 1.3x.","Tooltip_XiraniteSolarPowerGenerator_01");
        Tooltip_XiraniteSolarPowerGenerator_02 = TextHandler.texter("Wuling Clean Energy! Wishing you a clear blue sky!","Tooltip_XiraniteSolarPowerGenerator_02");
        Tooltip_XiraniteSolarPowerGenerator_03 = TextHandler.texter("The Logistics Department moved a decommissioned Xiranite Solar Power Generator to the employee garden to power it, citing the call for green environmental protection — and also to save on electricity bills.","Tooltip_XiraniteSolarPowerGenerator_03");
        Tooltip_XiraniteSolarPowerGenerator_04 = TextHandler.texter("As it turned out, the tomatoes in the garden tripled in size to the size of watermelons in just three days. The canteen auntie made a pot of tomato egg drop soup, and somehow managed to serve up the equivalent of ten pots.","Tooltip_XiraniteSolarPowerGenerator_04");
        Tooltip_XiraniteSolarPowerGenerator_05 = TextHandler.texter("An Agricultural Master came to inspect and suggested that residual Xiranite might have affected plant growth. But the Logistics Department didn't care — they immediately switched to growing cash crops instead.","Tooltip_XiraniteSolarPowerGenerator_05");
        Tooltip_XiraniteSolarPowerGenerator_06 = TextHandler.texter("Now that garden is sealed off, with a sign at the entrance reading \"Research Experimental Zone.\" Inside, they're growing the most expensive spices on the market.","Tooltip_XiraniteSolarPowerGenerator_06");

        //协议核心
        NameProtocolCore = TextHandler.texter("Protocol Core","NameProtocolCore");
        Tooltip_ProtocolCore_MachineType = TextHandler.texter("Protocol Core","Tooltip_ProtocolCore_MachineType");
        Tooltip_ProtocolCore_Controller = TextHandler.texter("Controller block of the Protocol Core","Tooltip_ProtocolCore_Controller");
        Tooltip_ProtocolCore_00 = TextHandler.texter("The supreme achievement of Endfield Industries, which creates a vast integrated industrial space through the massive Protocol Originium within its structure, and can supply power to integrated industrial machines within its range.","Tooltip_ProtocolCore_00");
        Tooltip_ProtocolCore_01 = TextHandler.texter("The effective range of the Protocol Core is centered on the current chunk of the main block, extending to a square range of 51×51 chunks.","Tooltip_ProtocolCore_01");
        Tooltip_ProtocolCore_02 = TextHandler.texter("After joining a player's Originium energy network, you automatically gain the right to use machines within their Protocol Core.","Tooltip_ProtocolCore_02");
        Tooltip_ProtocolCore_03 = TextHandler.texter("Using machines in a Protocol Core without permission will fail inspection and the machine will be unable to operate.","Tooltip_ProtocolCore_03");
        Tooltip_ProtocolCore_04 = TextHandler.texter("Warning! Do not frequently turn the Protocol Core on and off! Doing so may cause server lag!","Tooltip_ProtocolCore_04");
        Tooltip_ProtocolCore_05 = TextHandler.texter("The Protocol Core logic has been specially optimized — after the first startup, no further scanning operations will be performed.","Tooltip_ProtocolCore_05");

        //中继器
        NameRelayTower = TextHandler.texter("Relay Tower","NameRelayTower");
        Tooltip_ProtocolCore_06 = TextHandler.texter("If this machine only acts as a relay node and does not supply any field to machines, its chunk does not need to be loaded and the link will stay active.","Tooltip_ProtocolCore_06");
        Tooltip_ProtocolCore_07 = TextHandler.texter("If this machine needs to provide a field to machines, its chunk must remain loaded (e.g. via a chunkloader); otherwise the field will disappear.","Tooltip_ProtocolCore_07");

        Tooltip_RelayTower_MachineType = TextHandler.texter("Relay Tower","Tooltip_RelayTower_MachineType");
        Tooltip_RelayTower_Controller = TextHandler.texter("Controller block of the Relay Tower","Tooltip_RelayTower_Controller");
        Tooltip_RelayTower_00 = TextHandler.texter("A relay device capable of wired connections within 500 blocks (absolute distance) to complete power distribution.","Tooltip_RelayTower_00");
        Tooltip_RelayTower_01 = TextHandler.texter("Can wirelessly supply power to Endfield devices within a small range.","Tooltip_RelayTower_01");
        Tooltip_RelayTower_02 = TextHandler.texter("The Relay Tower is one of the most expensive devices in the integrated industrial system, with an overall design philosophy closer to that of the Protocol Core.","Tooltip_RelayTower_02");
        Tooltip_RelayTower_03 = TextHandler.texter("Use an Energy Connector and right-click the controller block to start linking, then left-click another Relay Tower / Power Post to complete the link.","Tooltip_RelayTower_03");
        Tooltip_RelayTower_04 = TextHandler.texter("Linking from a Protocol Core / Secondary Core also requires the use of an Energy Connector.","Tooltip_RelayTower_04");
        Tooltip_RelayTower_05 = TextHandler.texter("If this machine only acts as a relay node and does not supply any field to machines, its chunk does not need to be loaded and the link will stay active.","Tooltip_RelayTower_05");
        Tooltip_RelayTower_06 = TextHandler.texter("If this machine needs to provide a field to machines, its chunk must remain loaded (e.g. via a chunkloader); otherwise the field will disappear.","Tooltip_RelayTower_06");

        //供电桩
        NameElectricPylon = TextHandler.texter("Electric Pylon","NameElectricPylon");
        Tooltip_ElectricPylon_MachineType = TextHandler.texter("Electric Pylon","Tooltip_ElectricPylon_MachineType");
        Tooltip_ElectricPylon_Controller = TextHandler.texter("Controller block of the Electric Pylon","Tooltip_ElectricPylon_Controller");
        Tooltip_ElectricPylon_00 = TextHandler.texter("When powered, it can wirelessly supply power to Endfield devices within a relatively large range.","Tooltip_ElectricPylon_00");
        Tooltip_ElectricPylon_01 = TextHandler.texter("Capable of short-distance power transmission within 100 blocks (absolute distance).","Tooltip_ElectricPylon_01");
        Tooltip_ElectricPylon_02 = TextHandler.texter("\"Wireless power supply\" is Endfield Industries' greatest technological breakthrough. This technology is an extension of Protocol technology and serves as the operational foundation of the entire integrated industrial system.","Tooltip_ElectricPylon_02");
        Tooltip_ElectricPylon_03 = TextHandler.texter("Use an Energy Connector and right-click the controller block to start linking, then left-click another Relay Tower / Power Post to complete the link.","Tooltip_ElectricPylon_03");
        Tooltip_ElectricPylon_04 = TextHandler.texter("Linking from a Protocol Core / Secondary Core also requires the use of an Energy Connector.","Tooltip_ElectricPylon_04");
        Tooltip_ElectricPylon_05 = TextHandler.texter("If this machine only acts as a relay node and does not supply any field to machines, its chunk does not need to be loaded and the link will stay active.","Tooltip_ElectricPylon_05");
        Tooltip_ElectricPylon_06 = TextHandler.texter("If this machine needs to provide a field to machines, its chunk must remain loaded (e.g. via a chunkloader); otherwise the field will disappear.","Tooltip_ElectricPylon_06");

        //息壤装配器
        NameXirangAssembler = TextHandler.texter("Xirang Assembler","NameXirangAssembler");
        Tooltip_XirangAssembler_MachineType = TextHandler.texter("Xirang Assembler","Tooltip_XirangAssembler_MachineType");
        Tooltip_XirangAssembler_Controller = TextHandler.texter("Controller block of the Xirang Assembler","Tooltip_XirangAssembler_Controller");
        Tooltip_XirangAssembler_00 = TextHandler.texter("A high-precision assembly machine that performs fine assembling inside an active Xirang gas environment--the \"final pair of hands\" of the production line.","Tooltip_XirangAssembler_00");
        Tooltip_XirangAssembler_01 = TextHandler.texter("Only operates in an active Xirang gas atmosphere; the living soil gas acts as the precision assembly medium, and allegedly doubles as \"seasoning\" for the parts.","Tooltip_XirangAssembler_01");
        Tooltip_XirangAssembler_02 = TextHandler.texter("R&D first tried ordinary air and the yield rate stayed stuck at 40%%. A Wuling master craftsman happened to pass by, took one sniff of the workshop, and said: \"The parts need to steep in Xirang gas first.\"","Tooltip_XirangAssembler_02");
        Tooltip_XirangAssembler_03 = TextHandler.texter("After switching to Xirang gas, the yield rate shot up to 99.8%%. Now the entire workshop smells faintly of fresh soil, and the workers swear the finished parts really do come out... smoother.","Tooltip_XirangAssembler_03");
        Tooltip_XirangAssembler_04 = TextHandler.texter("The canteen auntie later moved her rice cooker into the workshop, insisting that rice steamed in this atmosphere tastes extra fragrant. Wuling production line--every inch of soil counts.","Tooltip_XirangAssembler_04");

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
        EOHB_KuangMaiCaiJiZhe_Tooltip_00 = TextHandler.texter("Right-click on the Prime Main Vein Block to collect the corresponding vein block.","EOHB_KuangMaiCaiJiZhe_Tooltip_00");
        EOHB_EnergyConnector_Tooltip_00 = TextHandler.texter("Right-click the machine main block to start linking, then left-click the target machine main block to complete the link.","EOHB_EnergyConnector_Tooltip_00");
        EOHB_ArknightsItem_LongGu = new String[] {
            TextHandler.texter("A delicate artificial component used as the core material of Base construction.", "EOHB_ArknightsItem_LongGu_00"),
            TextHandler.texter("The foundation of the entire construction operation at", "EOHB_ArknightsItem_LongGu_01"),
            TextHandler.texter("Rhodes Island. Although this giant, complicated component", "EOHB_ArknightsItem_LongGu_02"),
            TextHandler.texter("must be custom made or ordered, for some reason, Rhodes", "EOHB_ArknightsItem_LongGu_03"),
            TextHandler.texter("Island often uses it to build special facilities.", "EOHB_ArknightsItem_LongGu_04")
        };
        EOHB_ArknightsItem_Tan = new String[] {
            TextHandler.texter("A carbon stick used for the facility development of Rhodes Island.", "EOHB_ArknightsItem_Tan_00"),
            TextHandler.texter("One of the necessary industrial materials, it", "EOHB_ArknightsItem_Tan_01"),
            TextHandler.texter("can be used to produce a variety of polymers.", "EOHB_ArknightsItem_Tan_02")
        };
        EOHB_ArknightsItem_TanSu = new String[] {
            TextHandler.texter("A carbon brick used for the facility development of Rhodes Island.", "EOHB_ArknightsItem_TanSu_00"),
            TextHandler.texter("Light and highly pure, it possesses excellent workability,", "EOHB_ArknightsItem_TanSu_01"),
            TextHandler.texter("and is instrumental to the success of our operations.", "EOHB_ArknightsItem_TanSu_02")
        };
        EOHB_ArknightsItem_TanSuZu = new String[] {
            TextHandler.texter("A pack of carbon bricks used for the facility development of Rhodes Island.", "EOHB_ArknightsItem_TanSuZu_00"),
            TextHandler.texter("A large number of carbon bricks. Every one of them has a burning soul for modern industry.", "EOHB_ArknightsItem_TanSuZu_01")
        };
        EOHB_ArknightsItem_JiChuJiaGuJianCai = new String[] {
            TextHandler.texter("A basic construction material used for the facility development of Rhodes Island.", "EOHB_ArknightsItem_JiChuJiaGuJianCai_00"),
            TextHandler.texter("Room modification causes irreversible damage to load bearing parts if not handled properly. Please", "EOHB_ArknightsItem_JiChuJiaGuJianCai_01"),
            TextHandler.texter("use building materials before commencing modification", "EOHB_ArknightsItem_JiChuJiaGuJianCai_02"),
            TextHandler.texter("in order to guarantee the integrity of the room.", "EOHB_ArknightsItem_JiChuJiaGuJianCai_03")
        };
        EOHB_ArknightsItem_JinJieJiaGuJianCai = new String[] {
            TextHandler.texter("A strengthened construction material used for the facility development of Rhodes Island.", "EOHB_ArknightsItem_JinJieJiaGuJianCai_00"),
            TextHandler.texter("Before conducting modifications to rooms with poor", "EOHB_ArknightsItem_JinJieJiaGuJianCai_01"),
            TextHandler.texter("structural integrity, please use this building", "EOHB_ArknightsItem_JinJieJiaGuJianCai_02"),
            TextHandler.texter("material for maintenance to ensure the safety of", "EOHB_ArknightsItem_JinJieJiaGuJianCai_03"),
            TextHandler.texter("the work site. Only then will you be ready to begin.", "EOHB_ArknightsItem_JinJieJiaGuJianCai_04")
        };
        EOHB_ArknightsItem_GaoJiJiaGuJianCai = new String[] {
            TextHandler.texter("An advanced construction material used for the facility development of Rhodes Island.", "EOHB_ArknightsItem_GaoJiJiaGuJianCai_00"),
            TextHandler.texter("Please do not waste time with stress tests on rooms", "EOHB_ArknightsItem_GaoJiJiaGuJianCai_01"),
            TextHandler.texter("strengthened by this material! None of the currently employed", "EOHB_ArknightsItem_GaoJiJiaGuJianCai_02"),
            TextHandler.texter("demolition methods can damage the structural integrity of", "EOHB_ArknightsItem_GaoJiJiaGuJianCai_03"),
            TextHandler.texter("these rooms, but they may be dangerous to the experimenters.", "EOHB_ArknightsItem_GaoJiJiaGuJianCai_04")
        };
        EOHB_ArknightsItem_YuanShiSuiPian = new String[] {
            TextHandler.texter("An Originium shard collected from polluted regions. Can be used to produce Orundum.", "EOHB_ArknightsItem_YuanShiSuiPian_00"),
            TextHandler.texter("An Originium shard collected from heavily contaminated", "EOHB_ArknightsItem_YuanShiSuiPian_01"),
            TextHandler.texter("regions. Anyone who touches it has a high chance to get", "EOHB_ArknightsItem_YuanShiSuiPian_02"),
            TextHandler.texter("infected and even mutate. Therefore, it is a hazardous", "EOHB_ArknightsItem_YuanShiSuiPian_03"),
            TextHandler.texter("material as indicated in the Columbia CDC toxicity database.", "EOHB_ArknightsItem_YuanShiSuiPian_04")
        };
        EOHB_ArknightsItem_ChiJin = new String[] {
            TextHandler.texter("A refined gold bar. Can be exchanged for a lot of LMD.", "EOHB_ArknightsItem_ChiJin_00"),
            TextHandler.texter("How much ore does it take to", "EOHB_ArknightsItem_ChiJin_01"),
            TextHandler.texter("make such valuable gold bars?", "EOHB_ArknightsItem_ChiJin_02"),
            TextHandler.texter("It doesn't matter. What matters is that they're yours.", "EOHB_ArknightsItem_ChiJin_03")
        };
        EOHB_ArknightsItem_JiQiaoGaiYao_Juan1 = new String[] {
            TextHandler.texter("A book that records basic tactics. Can be used to upgrade Operator skills.", "EOHB_ArknightsItem_JiQiaoGaiYao_Juan1_00"),
            TextHandler.texter("It records entry-level skills needed by Operators. Even", "EOHB_ArknightsItem_JiQiaoGaiYao_Juan1_01"),
            TextHandler.texter("a dummy could master the techniques after a bit of study.", "EOHB_ArknightsItem_JiQiaoGaiYao_Juan1_02")
        };
        EOHB_ArknightsItem_JiQiaoGaiYao_Juan2 = new String[] {
            TextHandler.texter("A book that records intermediate tactics. Can be used to upgrade Operator skills.", "EOHB_ArknightsItem_JiQiaoGaiYao_Juan2_00"),
            TextHandler.texter("It records junior skills needed by Operators.", "EOHB_ArknightsItem_JiQiaoGaiYao_Juan2_01"),
            TextHandler.texter("It takes some time and effort to master them.", "EOHB_ArknightsItem_JiQiaoGaiYao_Juan2_02")
        };
        EOHB_ArknightsItem_JiQiaoGaiYao_Juan3 = new String[] {
            TextHandler.texter("A book that records advanced tactics. Can be used to upgrade Operator skills.", "EOHB_ArknightsItem_JiQiaoGaiYao_Juan3_00"),
            TextHandler.texter("It records high level skills needed by Operators. It takes", "EOHB_ArknightsItem_JiQiaoGaiYao_Juan3_01"),
            TextHandler.texter("a depth of experience in relevant fields to understand them.", "EOHB_ArknightsItem_JiQiaoGaiYao_Juan3_02")
        };
        EOHB_ArknightsItem_NiuZhuanChun = new String[] {
            TextHandler.texter("A flaky organic compound. Can be used for a variety", "EOHB_ArknightsItem_NiuZhuanChun_00"),
            TextHandler.texter("of upgrades and for production in the Factory.", "EOHB_ArknightsItem_NiuZhuanChun_01"),
            TextHandler.texter("An outstanding chemical mediator. When in use, it often changes", "EOHB_ArknightsItem_NiuZhuanChun_02"),
            TextHandler.texter("between two forms to store and release other materials. When", "EOHB_ArknightsItem_NiuZhuanChun_03"),
            TextHandler.texter("it becomes liquid, some of its properties are the same as those", "EOHB_ArknightsItem_NiuZhuanChun_04"),
            TextHandler.texter("of alcohol, which often leaves our Engineer Operators tipsy.", "EOHB_ArknightsItem_NiuZhuanChun_05")
        };
        EOHB_ArknightsItem_BaiMaChun = new String[] {
            TextHandler.texter("A flaky organic compound. Can be used for a variety", "EOHB_ArknightsItem_BaiMaChun_00"),
            TextHandler.texter("of upgrades and the synthesis of Bipolar Nanoflake.", "EOHB_ArknightsItem_BaiMaChun_01"),
            TextHandler.texter("A product made from Loxic Kohl after fine processing. It was named after the company that discovered its", "EOHB_ArknightsItem_BaiMaChun_02"),
            TextHandler.texter("production method. Experiments show that it tends to", "EOHB_ArknightsItem_BaiMaChun_03"),
            TextHandler.texter("transform to a higher structure in abnormal environments.", "EOHB_ArknightsItem_BaiMaChun_04")
        };
        EOHB_ArknightsItem_ShuangJiNaMiPian = new String[] {
            TextHandler.texter("The brainchild of modern science and industry. Can be used for high level upgrades.", "EOHB_ArknightsItem_ShuangJiNaMiPian_00"),
            TextHandler.texter("A device that is sensitive to Originium within a", "EOHB_ArknightsItem_ShuangJiNaMiPian_01"),
            TextHandler.texter("certain range. It can significantly improve the", "EOHB_ArknightsItem_ShuangJiNaMiPian_02"),
            TextHandler.texter("sensitivity of nearby weapons and equipment to", "EOHB_ArknightsItem_ShuangJiNaMiPian_03"),
            TextHandler.texter("Originium, making them near vessels for Originium Arts.", "EOHB_ArknightsItem_ShuangJiNaMiPian_04")
        };
        EOHB_ArknightsItem_PoSunZhuangZhi = new String[] {
            TextHandler.texter("A damaged mechanical device captured from Reunion. Can be used", "EOHB_ArknightsItem_PoSunZhuangZhi_00"),
            TextHandler.texter("for a variety of upgrades and for production in the Factory.", "EOHB_ArknightsItem_PoSunZhuangZhi_01"),
            TextHandler.texter("A broken device that was embedded in", "EOHB_ArknightsItem_PoSunZhuangZhi_02"),
            TextHandler.texter("the enemy's weapons and armor. It got", "EOHB_ArknightsItem_PoSunZhuangZhi_03"),
            TextHandler.texter("damaged during a fierce fight, but the", "EOHB_ArknightsItem_PoSunZhuangZhi_04"),
            TextHandler.texter("components inside are still valuable.", "EOHB_ArknightsItem_PoSunZhuangZhi_05")
        };
        EOHB_ArknightsItem_ZhuangZhi = new String[] {
            TextHandler.texter("An ordinary mechanical device captured from Reunion. Can be used", "EOHB_ArknightsItem_ZhuangZhi_00"),
            TextHandler.texter("for a variety of upgrades and for production in the Factory.", "EOHB_ArknightsItem_ZhuangZhi_01"),
            TextHandler.texter("A set of devices that are relatively complete", "EOHB_ArknightsItem_ZhuangZhi_02"),
            TextHandler.texter("with numerous valuable components. To make", "EOHB_ArknightsItem_ZhuangZhi_03"),
            TextHandler.texter("it portable and practical, almost every bit of space around the motherboard is in use.", "EOHB_ArknightsItem_ZhuangZhi_04")
        };
        EOHB_ArknightsItem_QuanXinZhuangZhi = new String[] {
            TextHandler.texter("A brand new mechanical device captured from Reunion. Can be used", "EOHB_ArknightsItem_QuanXinZhuangZhi_00"),
            TextHandler.texter("for a variety of upgrades and for production in the Factory.", "EOHB_ArknightsItem_QuanXinZhuangZhi_01"),
            TextHandler.texter("A set of brand new devices. By reproducing the", "EOHB_ArknightsItem_QuanXinZhuangZhi_02"),
            TextHandler.texter("structure of devices of the same type, the lack of space", "EOHB_ArknightsItem_QuanXinZhuangZhi_03"),
            TextHandler.texter("around the motherboard has been solved. The energy consumption, of course, has also become higher.", "EOHB_ArknightsItem_QuanXinZhuangZhi_04")
        };
        EOHB_ArknightsItem_GaiLiangZhuangZhi = new String[] {
            TextHandler.texter("An advanced mechanical device captured from Reunion. Can be used", "EOHB_ArknightsItem_GaiLiangZhuangZhi_00"),
            TextHandler.texter("for a variety of upgrades and the synthesis of Bipolar Nanoflake.", "EOHB_ArknightsItem_GaiLiangZhuangZhi_01"),
            TextHandler.texter("After undergoing a large number of private modifications,", "EOHB_ArknightsItem_GaiLiangZhuangZhi_02"),
            TextHandler.texter("these devices have greatly expanded capacity, which improves", "EOHB_ArknightsItem_GaiLiangZhuangZhi_03"),
            TextHandler.texter("performance at the cost of stability. From this, one can feel the dedication and perseverance from the makers.", "EOHB_ArknightsItem_GaiLiangZhuangZhi_04")
        };
        EOHB_ArknightsItem_HuaHeQieXiaoYe = new String[] {
            TextHandler.texter("An industrial agent necessary for metal processing. Can be used for a variety of upgrades.", "EOHB_ArknightsItem_HuaHeQieXiaoYe_00"),
            TextHandler.texter("Provides lubrication and heat resistance during the refining process, which can", "EOHB_ArknightsItem_HuaHeQieXiaoYe_01"),
            TextHandler.texter("further increase the rate at which metal products meet final quality specifications.", "EOHB_ArknightsItem_HuaHeQieXiaoYe_02")
        };
        EOHB_ArknightsItem_DianJiDanYuan = new String[] {
            TextHandler.texter("An industrial material with high electrical stability and corrosion resistance. Used in a", "EOHB_ArknightsItem_DianJiDanYuan_00"),
            TextHandler.texter("variety of strengthening situations, and is often a material for Factory Station synthesis.", "EOHB_ArknightsItem_DianJiDanYuan_01"),
            TextHandler.texter("Electrodes made of special materials.", "EOHB_ArknightsItem_DianJiDanYuan_02"),
            TextHandler.texter("They do not participate in electrolytic", "EOHB_ArknightsItem_DianJiDanYuan_03"),
            TextHandler.texter("reactions and hold great value in smelting and purification processes.", "EOHB_ArknightsItem_DianJiDanYuan_04")
        };
        EOHB_ArknightsItem_JuNengDongLiDanYuan = new String[] {
            TextHandler.texter("A power output device with specialized design and material", "EOHB_ArknightsItem_JuNengDongLiDanYuan_00"),
            TextHandler.texter("improvements. Can be used in a variety of strengthening situations.", "EOHB_ArknightsItem_JuNengDongLiDanYuan_01"),
            TextHandler.texter("A comprehensive improvement over previously", "EOHB_ArknightsItem_JuNengDongLiDanYuan_02"),
            TextHandler.texter("confiscated devices. The new hydraulic unit greatly", "EOHB_ArknightsItem_JuNengDongLiDanYuan_03"),
            TextHandler.texter("reduces the failure rate while maintaining performance, significantly improving productivity.", "EOHB_ArknightsItem_JuNengDongLiDanYuan_04")
        };
        EOHB_ArknightsItem_D32Gang = new String[] {
            TextHandler.texter("An artificial metal that doesn't exist in", "EOHB_ArknightsItem_D32Gang_00"),
            TextHandler.texter("nature. Can be used for high level upgrades.", "EOHB_ArknightsItem_D32Gang_01"),
            TextHandler.texter("This material can deliver Originium Arts smoothly and its amazing hardness makes", "EOHB_ArknightsItem_D32Gang_02"),
            TextHandler.texter("it impossible to penetrate. It will set a new standard for weapon materials.", "EOHB_ArknightsItem_D32Gang_03")
        };
        EOHB_ArknightsItem_ZhongXiangWeiDuiYingTi = new String[] {
            TextHandler.texter("A product of cutting-edge research on luminescent materials. Can be used for high level upgrades.", "EOHB_ArknightsItem_ZhongXiangWeiDuiYingTi_00"),
            TextHandler.texter("A new material still in the experimental stage. Not", "EOHB_ArknightsItem_ZhongXiangWeiDuiYingTi_01"),
            TextHandler.texter("only can it output large amounts of energy, but it can", "EOHB_ArknightsItem_ZhongXiangWeiDuiYingTi_02"),
            TextHandler.texter("also adapt to Originium Arts. This may indicate the", "EOHB_ArknightsItem_ZhongXiangWeiDuiYingTi_03"),
            TextHandler.texter("future development of this optically protective material.", "EOHB_ArknightsItem_ZhongXiangWeiDuiYingTi_04")
        };
        EOHB_ArknightsItem_YuanYan = new String[] {
            TextHandler.texter("A rock mined from the ground. Can be used for a", "EOHB_ArknightsItem_YuanYan_00"),
            TextHandler.texter("variety of upgrades and for production in the Factory.", "EOHB_ArknightsItem_YuanYan_01"),
            TextHandler.texter("Rich in organics, it is commonly seen in regions where Originium has", "EOHB_ArknightsItem_YuanYan_02"),
            TextHandler.texter("completely volatilized. Compared to Originium, it is easier to exploit.", "EOHB_ArknightsItem_YuanYan_03")
        };
        EOHB_ArknightsItem_GuYuanYan = new String[] {
            TextHandler.texter("A rock cube mined from the ground. Can be used for a", "EOHB_ArknightsItem_GuYuanYan_00"),
            TextHandler.texter("variety of upgrades and for production in the Factory.", "EOHB_ArknightsItem_GuYuanYan_01"),
            TextHandler.texter("With numerous micropores, it is often used in the interlayer of", "EOHB_ArknightsItem_GuYuanYan_02"),
            TextHandler.texter("armors as an adsorbent of the breakdown product of Originium gas.", "EOHB_ArknightsItem_GuYuanYan_03")
        };
        EOHB_ArknightsItem_GuYuanYanZu = new String[] {
            TextHandler.texter("A conglomerate rock mined from the ground. Can be used for", "EOHB_ArknightsItem_GuYuanYanZu_00"),
            TextHandler.texter("a variety of upgrades and for production in the Factory.", "EOHB_ArknightsItem_GuYuanYanZu_01"),
            TextHandler.texter("Compressed from Orirock Cubes, it can be formed naturally.", "EOHB_ArknightsItem_GuYuanYanZu_02"),
            TextHandler.texter("It is a fragile material, but industrial technology has", "EOHB_ArknightsItem_GuYuanYanZu_03"),
            TextHandler.texter("made it possible to produce complete Orirock Clusters now.", "EOHB_ArknightsItem_GuYuanYanZu_04"),
            TextHandler.texter("People often mistake it for an ordinary cluster of rocks.", "EOHB_ArknightsItem_GuYuanYanZu_05")
        };
        EOHB_ArknightsItem_TiChunYuanYan = new String[] {
            TextHandler.texter("A refined matter produced with Orirock Cluster. Can be used for a", "EOHB_ArknightsItem_TiChunYuanYan_00"),
            TextHandler.texter("variety of upgrades and the synthesis of Polymerization Preparation.", "EOHB_ArknightsItem_TiChunYuanYan_01"),
            TextHandler.texter("This Orirock looks different from the raw material after the", "EOHB_ArknightsItem_TiChunYuanYan_02"),
            TextHandler.texter("refinement process. It costs much more than other processing", "EOHB_ArknightsItem_TiChunYuanYan_03"),
            TextHandler.texter("methods. Anyone who has seen the smooth cutting surface will be", "EOHB_ArknightsItem_TiChunYuanYan_04"),
            TextHandler.texter("amazed. That must be the charm of combining nature with industry.", "EOHB_ArknightsItem_TiChunYuanYan_05")
        };
        EOHB_ArknightsItem_HuanTingJuZhi = new String[] {
            TextHandler.texter("A superlatively heat- and chemical-resistant industrial", "EOHB_ArknightsItem_HuanTingJuZhi_00"),
            TextHandler.texter("material. Used in a variety of strengthening projects.", "EOHB_ArknightsItem_HuanTingJuZhi_01"),
            TextHandler.texter("A novel transparent material born from the laboratory.", "EOHB_ArknightsItem_HuanTingJuZhi_02"),
            TextHandler.texter("Its high luminous transmittance and phenomenal", "EOHB_ArknightsItem_HuanTingJuZhi_03"),
            TextHandler.texter("durability in all aspects promise to make it an excellent substitute for many conventional materials.", "EOHB_ArknightsItem_HuanTingJuZhi_04")
        };
        EOHB_ArknightsItem_HuanTingYuZhiTi = new String[] {
            TextHandler.texter("A prefabricated industrial intermediate composed of multiple", "EOHB_ArknightsItem_HuanTingYuZhiTi_00"),
            TextHandler.texter("first-rate materials. Used in a variety of strengthening projects.", "EOHB_ArknightsItem_HuanTingYuZhiTi_01"),
            TextHandler.texter("An industrial product arrived at through the introduction of several", "EOHB_ArknightsItem_HuanTingYuZhiTi_02"),
            TextHandler.texter("excellent materials into the synthesis process, preserving luminous", "EOHB_ArknightsItem_HuanTingYuZhiTi_03"),
            TextHandler.texter("transmittance while vastly increasing strength and shock resistance. Prospects are high for its wide adoption in the field of defense.", "EOHB_ArknightsItem_HuanTingYuZhiTi_04")
        };
        EOHB_ArknightsItem_ChiHeJin = new String[] {
            TextHandler.texter("A special alloy with a high melting point, commonly used in the electronics industry.", "EOHB_ArknightsItem_ChiHeJin_00"),
            TextHandler.texter("Can be used for a variety of upgrades and for synthesis projects in Factories.", "EOHB_ArknightsItem_ChiHeJin_01"),
            TextHandler.texter("An alloy smelted from several rare Terran metals.", "EOHB_ArknightsItem_ChiHeJin_02"),
            TextHandler.texter("Used to manufacture rare electronic components and", "EOHB_ArknightsItem_ChiHeJin_03"),
            TextHandler.texter("circuit boards, it has become an indispensable material in the cutting-edge electronics industry.", "EOHB_ArknightsItem_ChiHeJin_04")
        };
        EOHB_ArknightsItem_ChiHeJinKuai = new String[] {
            TextHandler.texter("A rarely-produced alloy with a high melting point, commonly used in", "EOHB_ArknightsItem_ChiHeJinKuai_00"),
            TextHandler.texter("the electronics industry. Can be used for a variety of upgrades.", "EOHB_ArknightsItem_ChiHeJinKuai_01"),
            TextHandler.texter("A product derived from further processing of incandescent alloy. After", "EOHB_ArknightsItem_ChiHeJinKuai_02"),
            TextHandler.texter("complicated industrial processing, the stability of its solid-liquid hybridization", "EOHB_ArknightsItem_ChiHeJinKuai_03"),
            TextHandler.texter("state at certain temperatures has been preserved. As a result, it has an", "EOHB_ArknightsItem_ChiHeJinKuai_04"),
            TextHandler.texter("irreplaceable role in product development in the cutting-edge electronics industry.", "EOHB_ArknightsItem_ChiHeJinKuai_05")
        };
        EOHB_ArknightsItem_YiTieSuiPian = new String[] {
            TextHandler.texter("A common industrial material. Can be used for a", "EOHB_ArknightsItem_YiTieSuiPian_00"),
            TextHandler.texter("variety of upgrades and for production in the Factory.", "EOHB_ArknightsItem_YiTieSuiPian_01"),
            TextHandler.texter("A byproduct of massive metal processing.", "EOHB_ArknightsItem_YiTieSuiPian_02"),
            TextHandler.texter("Its high plasticity and oxidation", "EOHB_ArknightsItem_YiTieSuiPian_03"),
            TextHandler.texter("resistance make it a common material of smelting and phased processing.", "EOHB_ArknightsItem_YiTieSuiPian_04")
        };
        EOHB_ArknightsItem_YiTie = new String[] {
            TextHandler.texter("A rare industrial material. Can be used for a variety", "EOHB_ArknightsItem_YiTie_00"),
            TextHandler.texter("of upgrades and for production in the Factory.", "EOHB_ArknightsItem_YiTie_01"),
            TextHandler.texter("In a small number of inerting processes, Oriron Shards may have phase changed and polymerized", "EOHB_ArknightsItem_YiTie_02"),
            TextHandler.texter("into Oriron. It is generally thought that Oriron", "EOHB_ArknightsItem_YiTie_03"),
            TextHandler.texter("is relatively stable when it reaches this level.", "EOHB_ArknightsItem_YiTie_04")
        };
        EOHB_ArknightsItem_YiTieZu = new String[] {
            TextHandler.texter("A precious industrial material. Can be used for a", "EOHB_ArknightsItem_YiTieZu_00"),
            TextHandler.texter("variety of upgrades and for production in the Factory.", "EOHB_ArknightsItem_YiTieZu_01"),
            TextHandler.texter("During the processing, influenced by very few unartificial factors, multiple Orirons may", "EOHB_ArknightsItem_YiTieZu_02"),
            TextHandler.texter("merge into this form by chance. Its hardness has decreased but its purity has increased.", "EOHB_ArknightsItem_YiTieZu_03")
        };
        EOHB_ArknightsItem_YiTieKuai = new String[] {
            TextHandler.texter("An expensive industrial material. Can be used for a variety", "EOHB_ArknightsItem_YiTieKuai_00"),
            TextHandler.texter("of upgrades and the synthesis of Polymerization Preparation.", "EOHB_ArknightsItem_YiTieKuai_01"),
            TextHandler.texter("An Oriron block fused with multiple Oriron Clusters under extremely harsh conditions. As the", "EOHB_ArknightsItem_YiTieKuai_02"),
            TextHandler.texter("most stable and rarest form of Oriron, it can be", "EOHB_ArknightsItem_YiTieKuai_03"),
            TextHandler.texter("used in all manners of industrial production.", "EOHB_ArknightsItem_YiTieKuai_04")
        };
        EOHB_ArknightsItem_LeiNingJieHe = new String[] {
            TextHandler.texter("A lab-made synthetic particle, boasting powerful adsorption", "EOHB_ArknightsItem_LeiNingJieHe_00"),
            TextHandler.texter("properties. Can be used in a variety of strengthening projects.", "EOHB_ArknightsItem_LeiNingJieHe_01"),
            TextHandler.texter("A particle that can adsorb many different materials.", "EOHB_ArknightsItem_LeiNingJieHe_02"),
            TextHandler.texter("At first, it was only used in cheap adsorbents,", "EOHB_ArknightsItem_LeiNingJieHe_03"),
            TextHandler.texter("but has since become a cutting-edge material thanks", "EOHB_ArknightsItem_LeiNingJieHe_04"),
            TextHandler.texter("to its strong universal coagulative properties.", "EOHB_ArknightsItem_LeiNingJieHe_05")
        };
        EOHB_ArknightsItem_ShuangTong = new String[] {
            TextHandler.texter("A very small amount of industrial organic compound. Can be used", "EOHB_ArknightsItem_ShuangTong_00"),
            TextHandler.texter("for a variety of upgrades and for production in the Factory.", "EOHB_ArknightsItem_ShuangTong_01"),
            TextHandler.texter("A very small amount of Diketon. After", "EOHB_ArknightsItem_ShuangTong_02"),
            TextHandler.texter("reprocessing, Engineer Operators use", "EOHB_ArknightsItem_ShuangTong_03"),
            TextHandler.texter("solidification during chemical combination", "EOHB_ArknightsItem_ShuangTong_04"),
            TextHandler.texter("to bond other stable structures.", "EOHB_ArknightsItem_ShuangTong_05")
        };
        EOHB_ArknightsItem_TongNingJi = new String[] {
            TextHandler.texter("A small amount of industrial organic compound. Can be used", "EOHB_ArknightsItem_TongNingJi_00"),
            TextHandler.texter("for a variety of upgrades and for production in the Factory.", "EOHB_ArknightsItem_TongNingJi_01"),
            TextHandler.texter("A small amount of Polyketon preparation produced through a", "EOHB_ArknightsItem_TongNingJi_02"),
            TextHandler.texter("special process. Its closed molecular structure has changed", "EOHB_ArknightsItem_TongNingJi_03"),
            TextHandler.texter("much complicated processing into simple chemical reactions.", "EOHB_ArknightsItem_TongNingJi_04"),
            TextHandler.texter("Of course, certain technical support is still needed.", "EOHB_ArknightsItem_TongNingJi_05")
        };
        EOHB_ArknightsItem_TongNingJiZu = new String[] {
            TextHandler.texter("A modest amount of industrial organic compound. Can be used", "EOHB_ArknightsItem_TongNingJiZu_00"),
            TextHandler.texter("for a variety of upgrades and for production in the Factory.", "EOHB_ArknightsItem_TongNingJiZu_01"),
            TextHandler.texter("A modest amount of Aketon preparation produced through", "EOHB_ArknightsItem_TongNingJiZu_02"),
            TextHandler.texter("further dehydrocarbon processing. The preparation reacts", "EOHB_ArknightsItem_TongNingJiZu_03"),
            TextHandler.texter("with the non-oxygen molecules in the air, so Engineer", "EOHB_ArknightsItem_TongNingJiZu_04"),
            TextHandler.texter("Operators must be careful during processing to avoid waste.", "EOHB_ArknightsItem_TongNingJiZu_05")
        };
        EOHB_ArknightsItem_TongZhenLie = new String[] {
            TextHandler.texter("A large amount of industrial organic compound. Can be used for a", "EOHB_ArknightsItem_TongZhenLie_00"),
            TextHandler.texter("variety of upgrades and the synthesis of Polymerization Preparation.", "EOHB_ArknightsItem_TongZhenLie_01"),
            TextHandler.texter("A large amount of unstable Keton preparation. As one of the advanced industrial materials,", "EOHB_ArknightsItem_TongZhenLie_02"),
            TextHandler.texter("it is closely monitored by our Engineer Operators. Please be careful when handling.", "EOHB_ArknightsItem_TongZhenLie_03")
        };
        EOHB_ArknightsItem_QingMengKuang = new String[] {
            TextHandler.texter("A metal ore used for metallurgy. Can be used for a", "EOHB_ArknightsItem_QingMengKuang_00"),
            TextHandler.texter("variety of upgrades and for production in the Factory.", "EOHB_ArknightsItem_QingMengKuang_01"),
            TextHandler.texter("A metal that is used to produce a catalyst", "EOHB_ArknightsItem_QingMengKuang_02"),
            TextHandler.texter("widely used in industries. As the reprocessing", "EOHB_ArknightsItem_QingMengKuang_03"),
            TextHandler.texter("is complicated, accidents caused by nonstandard techniques do happen from time to time.", "EOHB_ArknightsItem_QingMengKuang_04")
        };
        EOHB_ArknightsItem_SanShuiMengKuang = new String[] {
            TextHandler.texter("A metal ore used for metallurgy. Can be used for a", "EOHB_ArknightsItem_SanShuiMengKuang_00"),
            TextHandler.texter("variety of upgrades and the synthesis of D32 Steel.", "EOHB_ArknightsItem_SanShuiMengKuang_01"),
            TextHandler.texter("A precious metal which few companies are willing to use to produce industrial catalysts. The catalyst made from it, however, has an extremely", "EOHB_ArknightsItem_SanShuiMengKuang_02"),
            TextHandler.texter("long service life. It can be used repeatedly and even stripped and recycled,", "EOHB_ArknightsItem_SanShuiMengKuang_03"),
            TextHandler.texter("but the complicated processes have forced many companies to give up.", "EOHB_ArknightsItem_SanShuiMengKuang_04")
        };
        EOHB_ArknightsItem_JingTiYuanJian = new String[] {
            TextHandler.texter("An important Originium industry material. Can be used for a variety of upgrades.", "EOHB_ArknightsItem_JingTiYuanJian_00"),
            TextHandler.texter("Can be manufactured and assembled into more advanced Originium electric components.", "EOHB_ArknightsItem_JingTiYuanJian_01"),
            TextHandler.texter("A raw industrial material made with the outer shell of an Originium crystal.", "EOHB_ArknightsItem_JingTiYuanJian_02"),
            TextHandler.texter("A fundamental product in the modern Originium electronics industry.", "EOHB_ArknightsItem_JingTiYuanJian_03")
        };
        EOHB_ArknightsItem_JingTiDianLu = new String[] {
            TextHandler.texter("An important Originium industry material. Can be used for a variety of", "EOHB_ArknightsItem_JingTiDianLu_00"),
            TextHandler.texter("upgrades. A fundamental part of manufacturing Originium crystal assemblies.", "EOHB_ArknightsItem_JingTiDianLu_01"),
            TextHandler.texter("A core product in the modern Originium electronics industry. Found inside electronic goods far and wide throughout", "EOHB_ArknightsItem_JingTiDianLu_02"),
            TextHandler.texter("Terra's nations. The mass application of crystalline circuitry", "EOHB_ArknightsItem_JingTiDianLu_03"),
            TextHandler.texter("is one more reminder of Terra's industrial modernization.", "EOHB_ArknightsItem_JingTiDianLu_04")
        };
        EOHB_ArknightsItem_JingTiDianZiDanYuan = new String[] {
            TextHandler.texter("An expensive Originium industry product. Can be used for major upgrades.", "EOHB_ArknightsItem_JingTiDianZiDanYuan_00"),
            TextHandler.texter("A crystallization of Terra's Originium technology. A symbol of Terra's industrial modernization. From", "EOHB_ArknightsItem_JingTiDianZiDanYuan_01"),
            TextHandler.texter("Originium Casting units to intercity network servers,", "EOHB_ArknightsItem_JingTiDianZiDanYuan_02"),
            TextHandler.texter("their making all starts with inventions like these.", "EOHB_ArknightsItem_JingTiDianZiDanYuan_03")
        };
        EOHB_ArknightsItem_YanMoShi = new String[] {
            TextHandler.texter("A grindstone used to process weapon parts. Can be used for", "EOHB_ArknightsItem_YanMoShi_00"),
            TextHandler.texter("a variety of upgrades and for production in the Factory.", "EOHB_ArknightsItem_YanMoShi_01"),
            TextHandler.texter("An important tool in component", "EOHB_ArknightsItem_YanMoShi_02"),
            TextHandler.texter("processing. It has stable", "EOHB_ArknightsItem_YanMoShi_03"),
            TextHandler.texter("properties, and will never explode, powderize, or crack.", "EOHB_ArknightsItem_YanMoShi_04")
        };
        EOHB_ArknightsItem_WuShuiYanMoShi = new String[] {
            TextHandler.texter("An advanced grindstone used in the finish machining of weapon parts.", "EOHB_ArknightsItem_WuShuiYanMoShi_00"),
            TextHandler.texter("Can be used for a variety of upgrades", "EOHB_ArknightsItem_WuShuiYanMoShi_01"),
            TextHandler.texter("and the synthesis of D32 Steel.", "EOHB_ArknightsItem_WuShuiYanMoShi_02"),
            TextHandler.texter("Compared to normal Grindstone, this tool has an even", "EOHB_ArknightsItem_WuShuiYanMoShi_03"),
            TextHandler.texter("more stable structure. It is highly non-reactive.", "EOHB_ArknightsItem_WuShuiYanMoShi_04")
        };
        EOHB_ArknightsItem_NingJiao = new String[] {
            TextHandler.texter("A high-strength plastic material suitable. Can be used for a", "EOHB_ArknightsItem_NingJiao_00"),
            TextHandler.texter("variety of upgrades and for synthesis projects in Factories.", "EOHB_ArknightsItem_NingJiao_01"),
            TextHandler.texter("An artificial material serendipitously born in the", "EOHB_ArknightsItem_NingJiao_02"),
            TextHandler.texter("laboratory. It has excellent resistance to extreme", "EOHB_ArknightsItem_NingJiao_03"),
            TextHandler.texter("temperatures, is lightweight, and is easy to process, making it ideal for use in high-tech projects.", "EOHB_ArknightsItem_NingJiao_04")
        };
        EOHB_ArknightsItem_JuHeNingJiao = new String[] {
            TextHandler.texter("An extremely high-strength plastic material. Can be used for a variety of upgrades.", "EOHB_ArknightsItem_JuHeNingJiao_00"),
            TextHandler.texter("An artificial material created from a large number of", "EOHB_ArknightsItem_JuHeNingJiao_01"),
            TextHandler.texter("experiments using coagulating gel as a base material. It", "EOHB_ArknightsItem_JuHeNingJiao_02"),
            TextHandler.texter("can maintain its stability even in high-pressure", "EOHB_ArknightsItem_JuHeNingJiao_03"),
            TextHandler.texter("environments, playing a key role in some high-tech projects.", "EOHB_ArknightsItem_JuHeNingJiao_04")
        };
        EOHB_ArknightsItem_QieXiaoYuanYe = new String[] {
            TextHandler.texter("A stock solution prepared from a variety of active solvents. Can be used for high level upgrades.", "EOHB_ArknightsItem_QieXiaoYuanYe_00"),
            TextHandler.texter("A biologically stable stock solution. Precautions should", "EOHB_ArknightsItem_QieXiaoYuanYe_01"),
            TextHandler.texter("be taken to avoid cross-contamination during storage.", "EOHB_ArknightsItem_QieXiaoYuanYe_02")
        };
        EOHB_ArknightsItem_JuHeJi = new String[] {
            TextHandler.texter("A complicated liquid industrial product. Can be used for high level upgrades.", "EOHB_ArknightsItem_JuHeJi_00"),
            TextHandler.texter("A material commonly used as an isolation coating for delicate equipment. Its", "EOHB_ArknightsItem_JuHeJi_01"),
            TextHandler.texter("bonding effect is strong enough to interrupt the volatilization of Originium.", "EOHB_ArknightsItem_JuHeJi_02")
        };
        EOHB_ArknightsItem_ShouXingQuGuangTi = new String[] {
            TextHandler.texter("A specialized material that displays useful optical qualities", "EOHB_ArknightsItem_ShouXingQuGuangTi_00"),
            TextHandler.texter("and other physical traits. Can be used for high level upgrades.", "EOHB_ArknightsItem_ShouXingQuGuangTi_01"),
            TextHandler.texter("An optical material that boasts both physical", "EOHB_ArknightsItem_ShouXingQuGuangTi_02"),
            TextHandler.texter("durability and astonishing Originium Arts resistance,", "EOHB_ArknightsItem_ShouXingQuGuangTi_03"),
            TextHandler.texter("currently used in studies regarding optical protection. Other uses are still being tested.", "EOHB_ArknightsItem_ShouXingQuGuangTi_04")
        };
        EOHB_ArknightsItem_RMA70_12 = new String[] {
            TextHandler.texter("A sensitive mineral with high conductivity. Can be used for", "EOHB_ArknightsItem_RMA70_12_00"),
            TextHandler.texter("a variety of upgrades and for production in the Factory.", "EOHB_ArknightsItem_RMA70_12_01"),
            TextHandler.texter("A mineral whose natural form is a", "EOHB_ArknightsItem_RMA70_12_02"),
            TextHandler.texter("complicated polyhedron. Its value", "EOHB_ArknightsItem_RMA70_12_03"),
            TextHandler.texter("for Originium Arts was known long before its industrial value.", "EOHB_ArknightsItem_RMA70_12_04")
        };
        EOHB_ArknightsItem_RMA70_24 = new String[] {
            TextHandler.texter("A highly sensitive mineral with", "EOHB_ArknightsItem_RMA70_24_00"),
            TextHandler.texter("outstanding conductivity. Can be", "EOHB_ArknightsItem_RMA70_24_01"),
            TextHandler.texter("used for a variety of upgrades and the synthesis of D32 Steel.", "EOHB_ArknightsItem_RMA70_24_02"),
            TextHandler.texter("A mineral whose natural form is a", "EOHB_ArknightsItem_RMA70_24_03"),
            TextHandler.texter("complicated polyhedron. Found in 1024, it", "EOHB_ArknightsItem_RMA70_24_04"),
            TextHandler.texter("shows great industrial value that other", "EOHB_ArknightsItem_RMA70_24_05"),
            TextHandler.texter("minerals used for Originium Arts lack.", "EOHB_ArknightsItem_RMA70_24_06")
        };
        EOHB_ArknightsItem_JingLianRongJi = new String[] {
            TextHandler.texter("A special coating made from a specific formulation process. Can be used for high level upgrades.", "EOHB_ArknightsItem_JingLianRongJi_00"),
            TextHandler.texter("A coating held together by high-molecule polymers. In addition to its baseline protective capabilities,", "EOHB_ArknightsItem_JingLianRongJi_01"),
            TextHandler.texter("it has also gained other special characteristics, of which heat resistance is but one of many.", "EOHB_ArknightsItem_JingLianRongJi_02")
        };
        EOHB_ArknightsItem_ZhiYuanLiao = new String[] {
            TextHandler.texter("Ester used in industrial production. Can be used for a", "EOHB_ArknightsItem_ZhiYuanLiao_00"),
            TextHandler.texter("variety of upgrades and for production in the Factory.", "EOHB_ArknightsItem_ZhiYuanLiao_01"),
            TextHandler.texter("One of the most important materials in modern industry. Its appearance has lead to the development", "EOHB_ArknightsItem_ZhiYuanLiao_02"),
            TextHandler.texter("of many modern products. This one, of course, is", "EOHB_ArknightsItem_ZhiYuanLiao_03"),
            TextHandler.texter("only a raw material that has practical application.", "EOHB_ArknightsItem_ZhiYuanLiao_04")
        };
        EOHB_ArknightsItem_JuSuanZhi = new String[] {
            TextHandler.texter("A small amount of polyester used in industrial production. Can be", "EOHB_ArknightsItem_JuSuanZhi_00"),
            TextHandler.texter("used for a variety of upgrades and for production in the Factory.", "EOHB_ArknightsItem_JuSuanZhi_01"),
            TextHandler.texter("Although its is lacking in potency, it can still be used to produce some basic", "EOHB_ArknightsItem_JuSuanZhi_02"),
            TextHandler.texter("materials we need. It is also a common", "EOHB_ArknightsItem_JuSuanZhi_03"),
            TextHandler.texter("ingredient in some sustained-release drugs.", "EOHB_ArknightsItem_JuSuanZhi_04")
        };
        EOHB_ArknightsItem_JuSuanZhiZu = new String[] {
            TextHandler.texter("A pack of polyester used in industrial production. Can be used", "EOHB_ArknightsItem_JuSuanZhiZu_00"),
            TextHandler.texter("for a variety of upgrades and for production in the Factory.", "EOHB_ArknightsItem_JuSuanZhiZu_01"),
            TextHandler.texter("After a series of processes, it meets the standards and general needs of most", "EOHB_ArknightsItem_JuSuanZhiZu_02"),
            TextHandler.texter("products on the market. It can also be", "EOHB_ArknightsItem_JuSuanZhiZu_03"),
            TextHandler.texter("used to process some special materials.", "EOHB_ArknightsItem_JuSuanZhiZu_04")
        };
        EOHB_ArknightsItem_JuSuanZhiKuai = new String[] {
            TextHandler.texter("A lump of polyester used in industrial production. Can be used", "EOHB_ArknightsItem_JuSuanZhiKuai_00"),
            TextHandler.texter("for a variety of upgrades and for production in the Factory.", "EOHB_ArknightsItem_JuSuanZhiKuai_01"),
            TextHandler.texter("A refined material. As a product, it is mostly sold to organizations and research institutions that", "EOHB_ArknightsItem_JuSuanZhiKuai_02"),
            TextHandler.texter("have extremely high raw material needs. It may become", "EOHB_ArknightsItem_JuSuanZhiKuai_03"),
            TextHandler.texter("the basis of the next generation of materials.", "EOHB_ArknightsItem_JuSuanZhiKuai_04")
        };
        EOHB_ArknightsItem_ShaoJieHeNingJing = new String[] {
            TextHandler.texter("A product of modern technology that requires the ultimate in industrial craft. Used in advanced strengthening projects.", "EOHB_ArknightsItem_ShaoJieHeNingJing_00"),
            TextHandler.texter("A material that possesses a molecular recognitive capacity under specific high-temperature conditions. Can selectively adsorb Originium when used as a", "EOHB_ArknightsItem_ShaoJieHeNingJing_01"),
            TextHandler.texter("reagent, allowing for precise handling of Originium materials, lower Originium", "EOHB_ArknightsItem_ShaoJieHeNingJing_02"),
            TextHandler.texter("device energy consumption, and the opening of further new possibilities.", "EOHB_ArknightsItem_ShaoJieHeNingJing_03")
        };
        EOHB_ArknightsItem_BanZiRanRongJi = new String[] {
            TextHandler.texter("An active solvent with excellent physical characteristics, and good", "EOHB_ArknightsItem_BanZiRanRongJi_00"),
            TextHandler.texter("resistance to acids and bases. Can be used in a variety of upgrades.", "EOHB_ArknightsItem_BanZiRanRongJi_01"),
            TextHandler.texter("The product of a traditional solvent undergoing a modernization", "EOHB_ArknightsItem_BanZiRanRongJi_02"),
            TextHandler.texter("process, it demonstrates remarkably enhanced properties.", "EOHB_ArknightsItem_BanZiRanRongJi_03")
        };
        EOHB_ArknightsItem_DaiTang = new String[] {
            TextHandler.texter("Cheap sugar substitute produced by assembly line. Can be used", "EOHB_ArknightsItem_DaiTang_00"),
            TextHandler.texter("for a variety of upgrades and for production in the Factory.", "EOHB_ArknightsItem_DaiTang_01"),
            TextHandler.texter("It's slightly sweet and possibly edible. It is", "EOHB_ArknightsItem_DaiTang_02"),
            TextHandler.texter("also commonly used in chemical manufacturing.", "EOHB_ArknightsItem_DaiTang_03")
        };
        EOHB_ArknightsItem_Tang = new String[] {
            TextHandler.texter("A small amount of sugar produced by assembly line. Can be used", "EOHB_ArknightsItem_Tang_00"),
            TextHandler.texter("for a variety of upgrades and for production in the Factory.", "EOHB_ArknightsItem_Tang_01"),
            TextHandler.texter("A more expensive sugar made from natural", "EOHB_ArknightsItem_Tang_02"),
            TextHandler.texter("materials. Ahh, this taste... will", "EOHB_ArknightsItem_Tang_03"),
            TextHandler.texter("surely put one in a great mood. However, it's not for casual snacking.", "EOHB_ArknightsItem_Tang_04")
        };
        EOHB_ArknightsItem_TangZu = new String[] {
            TextHandler.texter("A decent amount of sugar produced by assembly line. Can be used", "EOHB_ArknightsItem_TangZu_00"),
            TextHandler.texter("for a variety of upgrades and for production in the Factory.", "EOHB_ArknightsItem_TangZu_01"),
            TextHandler.texter("A calorie-rich sugar pack that's highly sought", "EOHB_ArknightsItem_TangZu_02"),
            TextHandler.texter("after. Its weight never seems to meet the standards", "EOHB_ArknightsItem_TangZu_03"),
            TextHandler.texter("set for chemical manufacturing. The production line employees may have something to do with it.", "EOHB_ArknightsItem_TangZu_04")
        };
        EOHB_ArknightsItem_TangJuKuai = new String[] {
            TextHandler.texter("A large amount of sugar produced by assembly line. Can be used", "EOHB_ArknightsItem_TangJuKuai_00"),
            TextHandler.texter("for a variety of upgrades and for production in the Factory.", "EOHB_ArknightsItem_TangJuKuai_01"),
            TextHandler.texter("A nice, finely processed sugar lump", "EOHB_ArknightsItem_TangJuKuai_02"),
            TextHandler.texter("commonly used for potion production. Do", "EOHB_ArknightsItem_TangJuKuai_03"),
            TextHandler.texter("not taste test it! This is not food and is not to be sold as food! EVER!", "EOHB_ArknightsItem_TangJuKuai_04")
        };
        EOHB_ArknightsItem_HeSuXianWei = new String[] {
            TextHandler.texter("A specialized fiber bundle, possessing high tensile strength and", "EOHB_ArknightsItem_HeSuXianWei_00"),
            TextHandler.texter("elastic modulus. Used in a variety of strengthening projects.", "EOHB_ArknightsItem_HeSuXianWei_01"),
            TextHandler.texter("A derived product in Originium engineering, widely used in industrial fields due to its exceptional", "EOHB_ArknightsItem_HeSuXianWei_02"),
            TextHandler.texter("functionality. In recent years, its unique appearance has", "EOHB_ArknightsItem_HeSuXianWei_03"),
            TextHandler.texter("also drawn keen interest from those working in design.", "EOHB_ArknightsItem_HeSuXianWei_04")
        };
        EOHB_ArknightsItem_GuHuaXianWeiBan = new String[] {
            TextHandler.texter("A specialized fiber board, solidified in a mold. Used in a variety of strengthening projects.", "EOHB_ArknightsItem_GuHuaXianWeiBan_00"),
            TextHandler.texter("A compound fiber board crafted through bespoke manufacturing. Exhibits outstanding functionality, both physically", "EOHB_ArknightsItem_GuHuaXianWeiBan_01"),
            TextHandler.texter("and chemically, coupled with a rarely-seen ease of processing.", "EOHB_ArknightsItem_GuHuaXianWeiBan_02"),
            TextHandler.texter("An irreplaceable part of many an industrial workflow.", "EOHB_ArknightsItem_GuHuaXianWeiBan_03")
        };
        EOHB_ArknightsItem_YeHuaGaoNengQiTi = new String[] {
            TextHandler.texter("Liquefied gas stored in specially made metal containers. Can be used in a variety", "EOHB_ArknightsItem_YeHuaGaoNengQiTi_00"),
            TextHandler.texter("of strengthening situations, and is often a material for Factory Station synthesis.", "EOHB_ArknightsItem_YeHuaGaoNengQiTi_01"),
            TextHandler.texter("A highly active, highly flammable gas product. It burns at extremely high temperatures and can", "EOHB_ArknightsItem_YeHuaGaoNengQiTi_02"),
            TextHandler.texter("melt most metal materials, making it widely used", "EOHB_ArknightsItem_YeHuaGaoNengQiTi_03"),
            TextHandler.texter("in industrial welding and catalytic reactions.", "EOHB_ArknightsItem_YeHuaGaoNengQiTi_04")
        };
        EOHB_ArknightsItem_YeHuaMiXiJuTi = new String[] {
            TextHandler.texter("An organic compound obtained through a complex synthesis", "EOHB_ArknightsItem_YeHuaMiXiJuTi_00"),
            TextHandler.texter("process. Can be used in a variety of strengthening situations.", "EOHB_ArknightsItem_YeHuaMiXiJuTi_01"),
            TextHandler.texter("An ether preparation obtained by passivating ketone", "EOHB_ArknightsItem_YeHuaMiXiJuTi_02"),
            TextHandler.texter("preparations. It is relatively stable and does", "EOHB_ArknightsItem_YeHuaMiXiJuTi_03"),
            TextHandler.texter("not react with active metal materials, making it widely used in various industrial processes.", "EOHB_ArknightsItem_YeHuaMiXiJuTi_04")
        };
        EOHB_ArknightsItem_ZhuanZhiYanZu = new String[] {
            TextHandler.texter("A compound crystallization that has undergone low-tolerance refinement. Can be used in a variety of strengthening projects.", "EOHB_ArknightsItem_ZhuanZhiYanZu_00"),
            TextHandler.texter("The surface of this material has mutable characteristics, realizing results that were originally", "EOHB_ArknightsItem_ZhuanZhiYanZu_01"),
            TextHandler.texter("nearly impossible. CAUTION: industrial salt is not to be eaten under any circumstances.", "EOHB_ArknightsItem_ZhuanZhiYanZu_02")
        };
        EOHB_ArknightsItem_ZhuanZhiYanJuKuai = new String[] {
            TextHandler.texter("A compound crystal mass that has undergone high-tolerance refinement. Used in a variety", "EOHB_ArknightsItem_ZhuanZhiYanJuKuai_00"),
            TextHandler.texter("of strengthening projects, and is a common material in synthetic items at factories.", "EOHB_ArknightsItem_ZhuanZhiYanJuKuai_01"),
            TextHandler.texter("A stable crystalline body, manufactured as a crystal blend within saturated", "EOHB_ArknightsItem_ZhuanZhiYanJuKuai_02"),
            TextHandler.texter("solutions. Can provide materials with a diverse array of capabilities.", "EOHB_ArknightsItem_ZhuanZhiYanJuKuai_03")
        };
        EOHB_ArknightsItem_QiYiWuZhi = new String[] {
            TextHandler.texter("Strange matter captured from the star, completely different in nature from matter in the material world.", "EOHB_ArknightsItem_QiYiWuZhi_00"),
            TextHandler.texter("Seems it can be used to improve machine performance.", "EOHB_ArknightsItem_QiYiWuZhi_01")
        };


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
        EOHB_TangZhi = TextHandler.texter("Sugar Syrup","EOHB_TangZhi");
        EOHB_TongJiRongYe = TextHandler.texter("Ketone Solution","EOHB_TongJiRongYe");
        EOHB_TongJiJuHeWu = TextHandler.texter("Ketone Polymer","EOHB_TongJiJuHeWu");
        EOHB_FuHeTangJiang = TextHandler.texter("Compound Sugar Syrup","EOHB_FuHeTangJiang");
        EOHB_FaJiaoTangJiang = TextHandler.texter("Fermented Sugar Syrup","EOHB_FaJiaoTangJiang");
        EOHB_TangRongYe = TextHandler.texter("Sugar Solution","EOHB_TangRongYe");
        EOHB_JuZhiRongJiang = TextHandler.texter("Polyester Melt","EOHB_JuZhiRongJiang");
        EOHB_FuHeJuZhiJiang = TextHandler.texter("Compound Polyester Slurry","EOHB_FuHeJuZhiJiang");
        EOHB_YiTieJiang = TextHandler.texter("Oriron Slurry","EOHB_YiTieJiang");
        EOHB_FuHeYiTieJiang = TextHandler.texter("Compound Oriron Slurry","EOHB_FuHeYiTieJiang");
        EOHB_BoYiFen = TextHandler.texter("Platinum-Iridium Dust","EOHB_BoYiFen");
        EOHB_DianJiJiang = TextHandler.texter("Electrode Slurry","EOHB_DianJiJiang");
        EOHB_JingZhiDianJiJiang = TextHandler.texter("Refined Electrode Slurry","EOHB_JingZhiDianJiJiang");
        EOHB_JingTiJiang = TextHandler.texter("Crystal Slurry","EOHB_JingTiJiang");
        EOHB_ShiKeJingTiJiang = TextHandler.texter("Etched Crystal Slurry","EOHB_ShiKeJingTiJiang");
        EOHB_DuBoJingTiJiang = TextHandler.texter("Platinum-Plated Crystal Slurry","EOHB_DuBoJingTiJiang");
        EOHB_ChiHeJinPeiLiao = TextHandler.texter("Blazing Alloy Feedstock","EOHB_ChiHeJinPeiLiao");
        EOHB_ChiHeJinJiang = TextHandler.texter("Blazing Alloy Slurry","EOHB_ChiHeJinJiang");
        EOHB_JingLianChiHeJinJiang = TextHandler.texter("Refined Blazing Alloy Slurry","EOHB_JingLianChiHeJinJiang");
        EOHB_WenDingGaoNengQiTi = TextHandler.texter("Stabilized High-Energy Gas","EOHB_WenDingGaoNengQiTi");
        EOHB_GaoNengYeTi = TextHandler.texter("High-Energy Liquid","EOHB_GaoNengYeTi");
        EOHB_NingJiaoQianTi = TextHandler.texter("Gel Precursor","EOHB_NingJiaoQianTi");
        EOHB_JiaoLianNingJiao = TextHandler.texter("Crosslinked Gel","EOHB_JiaoLianNingJiao");
        EOHB_NiuZhuanChunJiang = TextHandler.texter("Twisted Alcohol Slurry","EOHB_NiuZhuanChunJiang");
        EOHB_ChunHuaNiuZhuanChunJiang = TextHandler.texter("Purified Twisted Alcohol Slurry","EOHB_ChunHuaNiuZhuanChunJiang");
        EOHB_ChuanTongRongJi = TextHandler.texter("Traditional Solvent","EOHB_ChuanTongRongJi");
        EOHB_GaiXingRongJi = TextHandler.texter("Modified Solvent","EOHB_GaiXingRongJi");
        EOHB_NaiSuanJianRongJi = TextHandler.texter("Acid-Alkali Resistant Solvent","EOHB_NaiSuanJianRongJi");
        EOHB_RuHuaJi = TextHandler.texter("Emulsifier","EOHB_RuHuaJi");
        EOHB_JingZhiQieXiaoYe = TextHandler.texter("Refined Cutting Fluid","EOHB_JingZhiQieXiaoYe");
        EOHB_XiFuJiang = TextHandler.texter("Adsorption Slurry","EOHB_XiFuJiang");
        EOHB_NingJieJiang = TextHandler.texter("Condensation Slurry","EOHB_NingJieJiang");
        EOHB_HuanTingDanTi = TextHandler.texter("Cyclic Hydrocarbon Monomer","EOHB_HuanTingDanTi");
        EOHB_HuanTingJuHeWu = TextHandler.texter("Cyclic Hydrocarbon Polymer","EOHB_HuanTingJuHeWu");
        EOHB_GangYuFen = TextHandler.texter("Corundum Dust","EOHB_GangYuFen");
        EOHB_MengKuangJingFen = TextHandler.texter("Refined Manganese Dust","EOHB_MengKuangJingFen");
        EOHB_YanMoJiang = TextHandler.texter("Grinding Slurry","EOHB_YanMoJiang");
        EOHB_JingZhiYanMoJiang = TextHandler.texter("Refined Grinding Slurry","EOHB_JingZhiYanMoJiang");
        EOHB_KuangWuJiang = TextHandler.texter("Mineral Slurry","EOHB_KuangWuJiang");
        EOHB_JingZhiKuangWuJiang = TextHandler.texter("Refined Mineral Slurry","EOHB_JingZhiKuangWuJiang");
        EOHB_FangSiYe = TextHandler.texter("Spinning Solution","EOHB_FangSiYe");
        EOHB_JingZhiFangSiYe = TextHandler.texter("Refined Spinning Solution","EOHB_JingZhiFangSiYe");
        EOHB_NongSuoFangSiYe = TextHandler.texter("Concentrated Spinning Solution","EOHB_NongSuoFangSiYe");
        EOHB_CuZhiQieXiaoYe = TextHandler.texter("Raw Cutting Fluid","EOHB_CuZhiQieXiaoYe");
        EOHB_GaoJieJingLianYe = TextHandler.texter("High-Grade Refining Fluid","EOHB_GaoJieJingLianYe");
        EOHB_LiquidXiranite = TextHandler.texter("Liquid Xiranite","EOHB_LiquidXiranite");
        EOHB_LiquidHeavyXiranite = TextHandler.texter("Liquid Heavy Xiranite","EOHB_LiquidHeavyXiranite");
        EOHB_LiquefiedOrundum = TextHandler.texter("Liquefied Orundum","EOHB_LiquefiedOrundum");
        EOHB_CrudeLiquefiedOrundum = TextHandler.texter("Crude Liquefied Orundum","EOHB_CrudeLiquefiedOrundum");
        EOHB_HighEnergyOrundumSolvent = TextHandler.texter("High-Energy Orundum Solvent","EOHB_HighEnergyOrundumSolvent");
        EOHB_UnstableOrundumSolvent = TextHandler.texter("Unstable Orundum Solvent","EOHB_UnstableOrundumSolvent");
        EOHB_ContaminatedOrundumSlurry = TextHandler.texter("Contaminated Orundum Slurry","EOHB_ContaminatedOrundumSlurry");
        EOHB_StabilizedHigh_EnergyOrundumSolvent = TextHandler.texter("Stabilized High-Energy Orundum Solvent","EOHB_StabilizedHigh_EnergyOrundumSolvent");
        EOHB_AnomalousEnergyCondensate = TextHandler.texter("Anomalous Energy Condensate","EOHB_AnomalousEnergyCondensate");
        EOHB_AdvancedOrundumFuelPrecursor = TextHandler.texter("Advanced Orundum Fuel Precursor","EOHB_AdvancedOrundumFuelPrecursor");
        EOHB_Acridgen = TextHandler.texter("Acridgen","EOHB_Acridgen");
        EOHB_Aquagen = TextHandler.texter("Aquagen","EOHB_Aquagen");
        EOHB_Inergen = TextHandler.texter("Inergen","EOHB_Inergen");
        EOHB_Xiragen = TextHandler.texter("Xiragen","EOHB_Xiragen");
        EOHB_HeavyXiragen = TextHandler.texter("Heavy Xiragen","EOHB_HeavyXiragen");
        EOHB_CupriumGas = TextHandler.texter("Cuprium Gas","EOHB_CupriumGas");
        EOHB_HetoniteGas = TextHandler.texter("Hetonite Gas","EOHB_HetoniteGas");
        EOHB_PyrroliteGas = TextHandler.texter("Pyrrolite Gas","EOHB_PyrroliteGas");
        EOHB_HighEnergyGas = TextHandler.texter("High-Energy Gas","EOHB_HighEnergyGas");

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
        EOHB_Recipe_FluidPumpMK1 = TextHandler.texter("Fluid Pump MK1","EOHB_Recipe_FluidPumpMK1");
        EOHB_Recipe_FluidPumpMK2 = TextHandler.texter("Fluid Pump MK2","EOHB_Recipe_FluidPumpMK2");
        EOHB_Recipe_ElectricTypeTwoMiningMachine = TextHandler.texter("Electric Type Two Mining Machine","EOHB_Recipe_ElectricTypeTwoMiningMachine");
        EOHB_Recipe_HighDensityEnergyFluidGenerator = TextHandler.texter("High-Density Energy Fluid Generator","EOHB_Recipe_HighDensityEnergyFluidGenerator");
        EOHB_Recipe_IsotopeInfusionReactor = TextHandler.texter("Isotope Infusion Reactor","EOHB_Recipe_IsotopeInfusionReactor");
        EOHB_Recipe_GasDiffuser = TextHandler.texter("Gas Diffuser","EOHB_Recipe_GasDiffuser");
        EOHB_Recipe_Fluid_GasTransmutingUnit = TextHandler.texter("Fluid-Gas Transmuting Unit","EOHB_Recipe_Fluid_GasTransmutingUnit");
        EOHB_Recipe_Solid_GasTransmutingUnit = TextHandler.texter("Solid-Gas Transmuting Unit","EOHB_Recipe_Solid_GasTransmutingUnit");
        EOHB_Recipe_GasReactorGlobe = TextHandler.texter("Gas Reactor Globe","EOHB_Recipe_GasReactorGlobe");
        EOHB_Recipe_HydroMiningRig = TextHandler.texter("Hydro Mining Rig","EOHB_Recipe_HydroMiningRig");
        EOHB_Recipe_GasExtractor = TextHandler.texter("Gas Extractor","EOHB_Recipe_GasExtractor");
        EOHB_Recipe_SeparatingUnit = TextHandler.texter("Separating Unit","EOHB_Recipe_SeparatingUnit");
        EOHB_Recipe_GearingUnit = TextHandler.texter("Gearing Unit","EOHB_Recipe_GearingUnit");
        EOHB_Recipe_LargeForce_ContainedProliferationMine = TextHandler.texter("AG-07 Large Force-Contained Proliferation Mine","EOHB_Recipe_LargeForce_ContainedProliferationMine");
        EOHB_Recipe_InternalizedUniverseComputingEngine = TextHandler.texter("Internalized Universe Computing Engine","EOHB_Recipe_InternalizedUniverseComputingEngine");
        EOHB_Recipe_XirangAssembler = TextHandler.texter("Xirang Assembler","EOHB_Recipe_XirangAssembler");

        //戴森球
        NameDysonCore = TextHandler.texter("Dyson Core","NameDysonCore");
        Tooltip_DysonCore_MachineType = TextHandler.texter("Dyson Core","Tooltip_DysonCore_MachineType");
        Tooltip_DysonCore_00 = TextHandler.texter("One core per player, a modular megastructure hub.","Tooltip_DysonCore_00");
        Tooltip_DysonCore_01 = TextHandler.texter("The core and modules must work inside the Orundum power field.","Tooltip_DysonCore_01");
        Tooltip_DysonCore_02 = TextHandler.texter("Up to 32 module slots (unlocked by team paste count).","Tooltip_DysonCore_02");
        Tooltip_DysonCore_03 = TextHandler.texter("Consumes 1,000,000 computation.","Tooltip_DysonCore_03");

        NameDysonManufacturingModule = TextHandler.texter("Dyson Manufacturing Module","NameDysonManufacturingModule");
        Tooltip_DysonManufacturingModule_MachineType = TextHandler.texter("Dyson Manufacturing Module","Tooltip_DysonManufacturingModule_MachineType");
        Tooltip_DysonManufacturingModule_00 = TextHandler.texter("Manufactures Dyson Cloud and Frame components.","Tooltip_DysonManufacturingModule_00");
        Tooltip_DysonManufacturingModule_01 = TextHandler.texter("Base round time 30s, base parallel 64.","Tooltip_DysonManufacturingModule_01");
        Tooltip_DysonManufacturingModule_02 = TextHandler.texter("Costs go to the team Orundum ledger (cloud 1B/t, frame 5B/t).","Tooltip_DysonManufacturingModule_02");
        Tooltip_DysonManufacturingModule_03 = TextHandler.texter("Consumes 10,000 computation.","Tooltip_DysonManufacturingModule_03");

        NameDysonLaunchModule = TextHandler.texter("Dyson Launch Module","NameDysonLaunchModule");
        Tooltip_DysonLaunchModule_MachineType = TextHandler.texter("Dyson Launch Module","Tooltip_DysonLaunchModule_MachineType");
        Tooltip_DysonLaunchModule_00 = TextHandler.texter("Launches the owner's components into the team counters (1 component = 1 count).","Tooltip_DysonLaunchModule_00");
        Tooltip_DysonLaunchModule_01 = TextHandler.texter("Base round time 10s, up to 16 components per round.","Tooltip_DysonLaunchModule_01");
        Tooltip_DysonLaunchModule_02 = TextHandler.texter("Each component costs 10,000 Orundum.","Tooltip_DysonLaunchModule_02");
        Tooltip_DysonLaunchModule_03 = TextHandler.texter("Consumes 100,000 computation.","Tooltip_DysonLaunchModule_03");

        NameDysonMassLaunchModule = TextHandler.texter("Dyson Mass Launch Module","NameDysonMassLaunchModule");
        Tooltip_DysonMassLaunchModule_MachineType = TextHandler.texter("Dyson Mass Launch Module","Tooltip_DysonMassLaunchModule_MachineType");
        Tooltip_DysonMassLaunchModule_00 = TextHandler.texter("One 60s round launches all of the owner's cloud/frame components at once.","Tooltip_DysonMassLaunchModule_00");
        Tooltip_DysonMassLaunchModule_01 = TextHandler.texter("Requires the team to unlock the Mass Launch major node.","Tooltip_DysonMassLaunchModule_01");
        Tooltip_DysonMassLaunchModule_02 = TextHandler.texter("Each component costs 10,000 Orundum.","Tooltip_DysonMassLaunchModule_02");
        Tooltip_DysonMassLaunchModule_03 = TextHandler.texter("Consumes 100,000 computation.","Tooltip_DysonMassLaunchModule_03");

        NameDysonReceiverModule = TextHandler.texter("Dyson Receiver Module","NameDysonReceiverModule");
        Tooltip_DysonReceiverModule_MachineType = TextHandler.texter("Dyson Receiver Module","Tooltip_DysonReceiverModule_MachineType");
        Tooltip_DysonReceiverModule_00 = TextHandler.texter("Generates power from the team's cloud + paste count (settles once per second).","Tooltip_DysonReceiverModule_00");
        Tooltip_DysonReceiverModule_01 = TextHandler.texter("Outputs 10^200 EU/t after completion.","Tooltip_DysonReceiverModule_01");
        Tooltip_DysonReceiverModule_02 = TextHandler.texter("One per team (can be placed on any member's core).","Tooltip_DysonReceiverModule_02");

        Dyson_Info_ModuleConnected = TextHandler.texter("Module status: Connected","Dyson_Info_ModuleConnected");
        Dyson_Info_ModuleDisconnected = TextHandler.texter("Module status: Disconnected (core offline)","Dyson_Info_ModuleDisconnected");
        Dyson_Info_ComputeRequirement = TextHandler.texter("Computation requirement: ","Dyson_Info_ComputeRequirement");
        Dyson_Info_CloudComponentStock = TextHandler.texter("Personal cloud component stock: ","Dyson_Info_CloudComponentStock");
        Dyson_Info_FrameComponentStock = TextHandler.texter("Frame component stock: ","Dyson_Info_FrameComponentStock");
        Dyson_Info_ConnectedModules = TextHandler.texter("Connected modules: ","Dyson_Info_ConnectedModules");
        Dyson_Info_ActiveSlots = TextHandler.texter("Active slots: ","Dyson_Info_ActiveSlots");
        Dyson_Info_TeamPaste = TextHandler.texter("Team paste: ","Dyson_Info_TeamPaste");
        Dyson_Info_PersonalComponents = TextHandler.texter("Personal components: cloud ","Dyson_Info_PersonalComponents");
        Dyson_Info_DuplicateCore = TextHandler.texter("This player already has a core. This machine is shut down.","Dyson_Info_DuplicateCore");
        Dyson_Info_ComputeSatisfied = TextHandler.texter("Core computation: Satisfied","Dyson_Info_ComputeSatisfied");
        Dyson_Info_ComputeInsufficient = TextHandler.texter("Core computation: Insufficient (needs 1,000,000)","Dyson_Info_ComputeInsufficient");
        Dyson_Info_LaunchPriority = TextHandler.texter("Launch priority: ","Dyson_Info_LaunchPriority");
        Dyson_Info_LaunchBatch = TextHandler.texter("Batch per round: ","Dyson_Info_LaunchBatch");
        Dyson_Info_MassMode = TextHandler.texter("Launch mode: Mass (one 60s round clears all personal components)","Dyson_Info_MassMode");
        Dyson_Info_Split = TextHandler.texter("Output split: Orundum ","Dyson_Info_Split");
        Dyson_Info_StrangeMatter = TextHandler.texter("Strange Matter: ","Dyson_Info_StrangeMatter");
        Dyson_Stat_Cloud = TextHandler.texter("Cloud","Dyson_Stat_Cloud");
        Dyson_Stat_Frame = TextHandler.texter("Frame","Dyson_Stat_Frame");
        Dyson_Stat_Paste = TextHandler.texter("Paste","Dyson_Stat_Paste");
        Dyson_Stat_Components = TextHandler.texter("Personal components","Dyson_Stat_Components");
        Dyson_Stat_StrangeMatter = TextHandler.texter("Strange Matter","Dyson_Stat_StrangeMatter");
        Dyson_Stat_Stage = TextHandler.texter("Stage","Dyson_Stat_Stage");
        Dyson_Stat_Leader = TextHandler.texter("Leader","Dyson_Stat_Leader");
        Dyson_Gui_PriorityCloud = TextHandler.texter("Cloud","Dyson_Gui_PriorityCloud");
        Dyson_Gui_PriorityFrame = TextHandler.texter("Frame","Dyson_Gui_PriorityFrame");
        Dyson_Gui_PriorityTooltip = TextHandler.texter("Launch priority: cloud first / frame first","Dyson_Gui_PriorityTooltip");
        Dyson_Gui_SplitTitle = TextHandler.texter("Orundum output share (0-100)","Dyson_Gui_SplitTitle");
        Dyson_Gui_SplitEUText = TextHandler.texter("EU: ","Dyson_Gui_SplitEUText");
        Dyson_Gui_SplitTooltip = TextHandler.texter("Configure Orundum / EU output split","Dyson_Gui_SplitTooltip");
        Dyson_Gui_EjectMatter = TextHandler.texter("Eject Strange Matter as items","Dyson_Gui_EjectMatter");
        Dyson_Text_Or = TextHandler.texter(" or ","Dyson_Text_Or");
        Dyson_Cmd_NoPermission = TextHandler.texter("You do not have permission to use this command.","Dyson_Cmd_NoPermission");
        Dyson_Cmd_NotLoaded = TextHandler.texter("Dyson sphere save has not been loaded yet.","Dyson_Cmd_NotLoaded");
        Dyson_Cmd_Usage = TextHandler.texter("Usage: /dyson stage <1-5> | /dyson cloud <count> | /dyson frame <count> | /dyson paste <count> | /dyson complete | /dyson reset","Dyson_Cmd_Usage");
        Dyson_Cmd_UsageStage = TextHandler.texter("Usage: /dyson stage <1-5>","Dyson_Cmd_UsageStage");
        Dyson_Cmd_UsageCloud = TextHandler.texter("Usage: /dyson cloud <count>","Dyson_Cmd_UsageCloud");
        Dyson_Cmd_UsageFrame = TextHandler.texter("Usage: /dyson frame <count>","Dyson_Cmd_UsageFrame");
        Dyson_Cmd_UsagePaste = TextHandler.texter("Usage: /dyson paste <count>","Dyson_Cmd_UsagePaste");
        Dyson_Cmd_StageRange = TextHandler.texter("Stage must be an integer from 1 to 5.","Dyson_Cmd_StageRange");
        Dyson_Cmd_Number = TextHandler.texter("Count must be an integer.","Dyson_Cmd_Number");
        Dyson_Cmd_Reset = TextHandler.texter("Dyson sphere state reset (all teams and completion cleared).","Dyson_Cmd_Reset");
        Dyson_Cmd_AlreadyComplete = TextHandler.texter("Dyson sphere is already complete (permanently locked). Use /dyson reset for testing.","Dyson_Cmd_AlreadyComplete");
        Dyson_Cmd_Completed = TextHandler.texter("Completion triggered: your team becomes the owner, losers are cleared, and the server is permanently locked.","Dyson_Cmd_Completed");
        Dyson_Cmd_Updated = TextHandler.texter("Dyson sphere state updated (leader): ","Dyson_Cmd_Updated");
        Dyson_Broadcast_00 = TextHandler.texter("[Dyson Sphere] ","Dyson_Broadcast_00");
        Dyson_Broadcast_01 = TextHandler.texter("'s team has completed the Talos Dyson Sphere! The star now permanently belongs to them.","Dyson_Broadcast_01");
        Dyson_Broadcast_UnknownTeam = TextHandler.texter("Unknown Team","Dyson_Broadcast_UnknownTeam");
        EOHB_Recipe_DysonManufacturing = TextHandler.texter("Dyson Manufacturing","EOHB_Recipe_DysonManufacturing");
    }
}
