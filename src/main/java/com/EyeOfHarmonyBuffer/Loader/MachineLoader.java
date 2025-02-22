package com.EyeOfHarmonyBuffer.Loader;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.common.Machine.EOHB_SolarEnergyArray;
import com.EyeOfHarmonyBuffer.common.Machine.EOHB_VendingMachines;
import com.EyeOfHarmonyBuffer.common.Machine.EOHB_WindTurbine;
import com.EyeOfHarmonyBuffer.utils.TextLocalization;
import net.minecraft.item.ItemStack;

public class MachineLoader {
    public static ItemStack VendingMachines;
    public static ItemStack WindTurbine;
    public static ItemStack SolarEnergyArrays;

    public static void loadMachines(){
        VendingMachines = new EOHB_VendingMachines(
            20001,
            "NameVendingMachines",
            TextLocalization.NameVendingMachines
        ).getStackForm(1);
        GTCMItemList.VendingMachines.set(VendingMachines);

        WindTurbine = new EOHB_WindTurbine(
            20002,
            "NameWindTurbine",
            TextLocalization.NameWindTurbine
        ).getStackForm(1);
        GTCMItemList.WindTurbines.set(WindTurbine);

        /*SolarEnergyArrays = new EOHB_SolarEnergyArray(
            20002,
            "NameSolarEnergyArray",
            TextLocalization.NameSolarEnergyArray
        ).getStackForm(1);
        GTCMItemList.SolarEnergyArray.set(SolarEnergyArrays);*/
    }
}
