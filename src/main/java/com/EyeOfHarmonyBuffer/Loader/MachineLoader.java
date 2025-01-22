package com.EyeOfHarmonyBuffer.Loader;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.common.Machine.EOHB_VendingMachines;
import net.minecraft.item.ItemStack;

public class MachineLoader {
    public static ItemStack VendingMachines;

    public static void loadMachines(){
        VendingMachines = new EOHB_VendingMachines(
            200001,
            "EOHB_VendingMachine",
            "大型贸易机"
        ).getStackForm(1);
        GTCMItemList.VendingMachines.set(VendingMachines);
    }
}
