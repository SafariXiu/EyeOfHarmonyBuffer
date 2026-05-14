package com.EyeOfHarmonyBuffer.Recipe.ArknightsProject;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.GTValues;
import gregtech.api.util.GTUtility;

import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.ElectricTypeOneMiningMachine;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

public class ElectricTypeOneMiningMachineRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTUtility.getIntegratedCircuit(1)
            )
            .itemOutputs(
                GTCMItemList.YuanShiKuang.get(10)
            )
            .eut(5000)
            .duration(10 * SECONDS)
            .addTo(ElectricTypeOneMiningMachine);
    }
}
