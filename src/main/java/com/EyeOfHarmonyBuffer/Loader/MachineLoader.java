package com.EyeOfHarmonyBuffer.Loader;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.common.Machine.*;
import com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.*;
import com.EyeOfHarmonyBuffer.utils.TextLocalization;
import net.minecraft.item.ItemStack;

public class MachineLoader {
    public static ItemStack VendingMachines;
    public static ItemStack WindTurbine;
    public static ItemStack SolarEnergyArrays;
    public static ItemStack SubstanceReshapingDevices;
    public static ItemStack BlueDogMachine;
    public static ItemStack MonkeyShit;
    public static ItemStack OrundumDynamo;
    public static ItemStack ElectricTypeOneMiningMachine;
    public static ItemStack Planter;
    public static ItemStack SeedCollectingMachine;
    public static ItemStack RefiningFurnace;
    public static ItemStack Pulverizer;
    public static ItemStack AccessoriesMachine;
    public static ItemStack ShapingMachine;
    public static ItemStack Grinder;
    public static ItemStack EncapsulationMachine;
    public static ItemStack FillingUnit;
    public static ItemStack ForgeOfTheSky;
    public static ItemStack PurificationUnit;
    public static ItemStack ReactorCrucible;
    public static ItemStack ExpandedCrucible;
    public static ItemStack FluidPumpMK1;
    public static ItemStack FluidPumpMK2;
    public static ItemStack ElectricTypeTwoMiningMachine;
    public static ItemStack HighDensityEnergyFluidGenerator;
    public static ItemStack IsotopeInfusionReactor;
    public static ItemStack GasDiffuser;
    public static ItemStack Fluid_GasTransmutingUnit;
    public static ItemStack Solid_GasTransmutingUnit;
    public static ItemStack GasReactorGlobe;
    public static ItemStack HydroMiningRig;
    public static ItemStack GasExtractor;
    public static ItemStack SeparatingUnit;
    public static ItemStack GearingUnit;
    public static ItemStack LargeForce_ContainedProliferationMine;
    public static ItemStack InternalizedUniverseComputingEngine;
    public static ItemStack XiraniteSolarPowerGenerator;
    public static ItemStack ProtocolCore;
    public static ItemStack RelayTower;
    public static ItemStack ElectricPylon;
    public static ItemStack XirangAssembler;

    private final static int MachineBlockID = 23000;

    public static void loadMachines(){
        VendingMachines = new EOHB_VendingMachines(
            MachineBlockID + 1,
            "NameVendingMachines",
            TextLocalization.NameVendingMachines
        ).getStackForm(1);
        GTCMItemList.VendingMachines.set(VendingMachines);

        WindTurbine = new EOHB_WindTurbine(
            MachineBlockID + 2,
            "NameWindTurbine",
            TextLocalization.NameWindTurbine
        ).getStackForm(1);
        GTCMItemList.WindTurbines.set(WindTurbine);

        SolarEnergyArrays = new EOHB_SolarEnergyArray(
            MachineBlockID + 3,
            "NameSolarEnergyArray",
            TextLocalization.NameSolarEnergyArray
        ).getStackForm(1);
        GTCMItemList.SolarEnergyArray.set(SolarEnergyArrays);

        SubstanceReshapingDevices = new EOHB_SubstanceReshapingDevice(
            MachineBlockID + 4,
            "NameCoreDrill",
            TextLocalization.NameSubstanceReshapingDevice
        ).getStackForm(1);
        GTCMItemList.SubstanceReshapingDevice.set(SubstanceReshapingDevices);

        BlueDogMachine = new EOHB_BlueDogMachine(
            MachineBlockID + 5,
            "NameBlueDogMachine",
            TextLocalization.NameBlueDogMachine
        ).getStackForm(1);
        GTCMItemList.BlueDogMachines.set(BlueDogMachine);

        MonkeyShit = new EOHB_MonkeyShit(
            MachineBlockID + 6,
            "NameShit",
            TextLocalization.NameMonkeyShit
        ).getStackForm(1);
        GTCMItemList.MonkeyShitS.set(MonkeyShit);

        OrundumDynamo = new EOHB_OrundumDynamo(
            MachineBlockID + 7,
            "NameOrundumDynamo",
            TextLocalization.NameOrundumDynamo
        ).getStackForm(1);
        GTCMItemList.OrundumDynamos.set(OrundumDynamo);

        ElectricTypeOneMiningMachine = new EOHB_ElectricTypeOneMiningMachine(
            MachineBlockID + 8,
            "NameElectricTypeOneMiningMachine",
            TextLocalization.NameElectricTypeOneMiningMachine
        ).getStackForm(1);
        GTCMItemList.ElectricTypeOneMiningMachines.set(ElectricTypeOneMiningMachine);

        Planter = new EOHB_Planter(
            MachineBlockID + 9,
            "NamePlanter",
            TextLocalization.NamePlanter
        ).getStackForm(1);
        GTCMItemList.Planters.set(Planter);

        SeedCollectingMachine = new EOHB_SeedCollectingMachine(
            MachineBlockID + 10,
            "NamePlanterSeedCollectingMachine",
            TextLocalization.NameSeedCollectingMachine
        ).getStackForm(1);
        GTCMItemList.SeedCollectingMachines.set(SeedCollectingMachine);

        RefiningFurnace = new EOHB_RefiningFurnace(
            MachineBlockID + 11,
            "NameRefiningFurnace",
            TextLocalization.NameRefiningFurnace
        ).getStackForm(1);
        GTCMItemList.RefiningFurnaces.set(RefiningFurnace);

        Pulverizer = new EOHB_Pulverizer(
            MachineBlockID + 12,
            "NamePulverizer",
            TextLocalization.NamePulverizer
        ).getStackForm(1);
        GTCMItemList.Pulverizers.set(Pulverizer);

        AccessoriesMachine = new EOHB_AccessoriesMachine(
            MachineBlockID + 13,
            "NameAccessoriesMachine",
            TextLocalization.NameAccessoriesMachine
        ).getStackForm(1);
        GTCMItemList.AccessoriesMachines.set(AccessoriesMachine);

        ShapingMachine = new EOHB_ShapingMachine(
            MachineBlockID + 14,
            "NameShapingMachine",
            TextLocalization.NameShapingMachine
        ).getStackForm(1);
        GTCMItemList.ShapingMachines.set(ShapingMachine);

        Grinder = new EOHB_Grinder(
            MachineBlockID + 15,
            "NameGrinder",
            TextLocalization.NameGrinder
        ).getStackForm(1);
        GTCMItemList.Grinders.set(Grinder);

        EncapsulationMachine = new EOHB_EncapsulationMachine(
            MachineBlockID + 16,
            "NameEncapsulationMachine",
            TextLocalization.NameEncapsulationMachine
        ).getStackForm(1);
        GTCMItemList.EncapsulationMachines.set(EncapsulationMachine);

        FillingUnit = new EOHB_FillingUnit(
            MachineBlockID + 17,
            "NameFillingUnit",
            TextLocalization.NameFillingUnit
        ).getStackForm(1);
        GTCMItemList.FillingUnits.set(FillingUnit);

        ForgeOfTheSky = new EOHB_ForgeOfTheSky(
            MachineBlockID + 18,
            "NameForgeOfTheSky",
            TextLocalization.NameForgeOfTheSky
        ).getStackForm(1);
        GTCMItemList.ForgeOfTheSkys.set(ForgeOfTheSky);

        PurificationUnit = new EOHB_PurificationUnit(
            MachineBlockID + 19,
            "NamePurificationUnit",
            TextLocalization.NamePurificationUnit
        ).getStackForm(1);
        GTCMItemList.PurificationUnits.set(PurificationUnit);

        ReactorCrucible = new EOHB_ReactorCrucible(
            MachineBlockID + 20,
            "NameReactorCrucible",
            TextLocalization.NameReactorCrucible
        ).getStackForm(1);
        GTCMItemList.ReactorCrucibles.set(ReactorCrucible);

        ExpandedCrucible = new EOHB_ExpandedCrucible(
            MachineBlockID + 21,
            "NameExpandedCrucible",
            TextLocalization.NameExpandedCrucible
        ).getStackForm(1);
        GTCMItemList.ExpandedCrucibles.set(ExpandedCrucible);

        FluidPumpMK1 = new EOHB_FluidPumpMK1(
            MachineBlockID + 22,
            "NameFluidPumpMK1",
            TextLocalization.NameFluidPumpMK1
        ).getStackForm(1);
        GTCMItemList.FluidPumpMK1.set(FluidPumpMK1);

        FluidPumpMK2 = new EOHB_FluidPumpMK2(
            MachineBlockID + 23,
            "NameFluidPumpMK2",
            TextLocalization.NameFluidPumpMK2
        ).getStackForm(1);
        GTCMItemList.FluidPumpMK2.set(FluidPumpMK2);

        ElectricTypeTwoMiningMachine = new EOHB_ElectricTypeTwoMiningMachine(
            MachineBlockID + 24,
            "NameElectricTypeTwoMiningMachine",
            TextLocalization.NameElectricTypeTwoMiningMachine
        ).getStackForm(1);
        GTCMItemList.ElectricTypeTwoMiningMachine.set(ElectricTypeTwoMiningMachine);

        HighDensityEnergyFluidGenerator = new EOHB_HighDensityEnergyFluidGenerator(
            MachineBlockID + 25,
            "NameHighDensityEnergyFluidGenerator",
            TextLocalization.NameHighDensityEnergyFluidGenerator
        ).getStackForm(1);
        GTCMItemList.HighDensityEnergyFluidGenerator.set(HighDensityEnergyFluidGenerator);

        IsotopeInfusionReactor = new EOHB_IsotopeInfusionReactor(
            MachineBlockID + 26,
            "NameIsotopeInfusionReactor",
            TextLocalization.NameIsotopeInfusionReactor
        ).getStackForm(1);
        GTCMItemList.IsotopeInfusionReactor.set(IsotopeInfusionReactor);

        GasDiffuser = new EOHB_GasDiffuser(
            MachineBlockID + 27,
            "NameGasDiffuser",
            TextLocalization.NameGasDiffuser
        ).getStackForm(1);
        GTCMItemList.GasDiffuser.set(GasDiffuser);

        Fluid_GasTransmutingUnit = new EOHB_Fluid_GasTransmutingUnit(
            MachineBlockID + 28,
            "NameFluid_GasTransmutingUnit",
            TextLocalization.NameFluid_GasTransmutingUnit
        ).getStackForm(1);
        GTCMItemList.Fluid_GasTransmutingUnit.set(Fluid_GasTransmutingUnit);

        Solid_GasTransmutingUnit = new EOHB_Solid_GasTransmutingUnit(
            MachineBlockID + 29,
            "NameSolid_GasTransmutingUnit",
            TextLocalization.NameSolid_GasTransmutingUnit
        ).getStackForm(1);
        GTCMItemList.Solid_GasTransmutingUnit.set(Solid_GasTransmutingUnit);

        GasReactorGlobe = new EOHB_GasReactorGlobe(
            MachineBlockID + 30,
            "NameGasReactorGlobe",
            TextLocalization.NameGasReactorGlobe
        ).getStackForm(1);
        GTCMItemList.GasReactorGlobe.set(GasReactorGlobe);

        HydroMiningRig = new EOHB_HydroMiningRig(
            MachineBlockID + 31,
            "NameHydroMiningRig",
            TextLocalization.NameHydroMiningRig
        ).getStackForm(1);
        GTCMItemList.HydroMiningRig.set(HydroMiningRig);

        GasExtractor = new EOHB_GasExtractor(
            MachineBlockID + 32,
            "NameGasExtractor",
            TextLocalization.NameGasExtractor
        ).getStackForm(1);
        GTCMItemList.GasExtractor.set(GasExtractor);

        SeparatingUnit = new EOHB_SeparatingUnit(
            MachineBlockID + 33,
            "NameSeparatingUnit",
            TextLocalization.NameSeparatingUnit
        ).getStackForm(1);
        GTCMItemList.SeparatingUnit.set(SeparatingUnit);

        GearingUnit = new EOHB_GearingUnit(
            MachineBlockID + 34,
            "NameGearingUnit",
            TextLocalization.NameGearingUnit
        ).getStackForm(1);
        GTCMItemList.GearingUnit.set(GearingUnit);

        LargeForce_ContainedProliferationMine = new EOHB_LargeForce_ContainedProliferationMine(
            MachineBlockID + 35,
            "NameLargeForce_ContainedProliferationMine",
            TextLocalization.NameLargeForce_ContainedProliferationMine
        ).getStackForm(1);
        GTCMItemList.LargeForce_ContainedProliferationMine.set(LargeForce_ContainedProliferationMine);

        InternalizedUniverseComputingEngine = new EOHB_InternalizedUniverseComputingEngine(
            MachineBlockID + 36,
            "NameInternalizedUniverseComputingEngine",
            TextLocalization.NameInternalizedUniverseComputingEngine
        ).getStackForm(1);
        GTCMItemList.InternalizedUniverseComputingEngine.set(InternalizedUniverseComputingEngine);

        XiraniteSolarPowerGenerator = new EOHB_XiraniteSolarPowerGenerator(
            MachineBlockID + 37,
            "NameXiraniteSolarPowerGenerator",
            TextLocalization.NameXiraniteSolarPowerGenerator
        ).getStackForm(1);
        GTCMItemList.XiraniteSolarPowerGenerator.set(XiraniteSolarPowerGenerator);

        ProtocolCore = new EOHB_ProtocolCore(
            MachineBlockID + 38,
            "NameProtocolCore",
            TextLocalization.NameProtocolCore
        ).getStackForm(1);
        GTCMItemList.ProtocolCore.set(ProtocolCore);

        RelayTower = new EOHB_RelayTower(
            MachineBlockID + 39,
            "NameRelayTower",
            TextLocalization.NameRelayTower
        ).getStackForm(1);
        GTCMItemList.RelayTower.set(RelayTower);

        ElectricPylon = new EOHB_ElectricPylon(
            MachineBlockID + 40,
            "NameElectricPylon",
            TextLocalization.NameElectricPylon
        ).getStackForm(1);
        GTCMItemList.ElectricPylon.set(ElectricPylon);

        XirangAssembler = new EOHB_XirangAssembler(
            MachineBlockID + 41,
            "NameXirangAssembler",
            TextLocalization.NameXirangAssembler
        ).getStackForm(1);
        GTCMItemList.XirangAssembler.set(XirangAssembler);
    }
}
