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
    }
}
