package com.EyeOfHarmonyBuffer.Loader;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.common.Machine.*;
import com.EyeOfHarmonyBuffer.utils.TextLocalization;
import net.minecraft.item.ItemStack;

public class MachineLoader {
    public static ItemStack VendingMachines;
    public static ItemStack WindTurbine;
    public static ItemStack SolarEnergyArrays;
    public static ItemStack SubstanceReshapingDevices;
    public static ItemStack BlueDogMachine;
    public static ItemStack MonkeyShit;

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
    }
}
