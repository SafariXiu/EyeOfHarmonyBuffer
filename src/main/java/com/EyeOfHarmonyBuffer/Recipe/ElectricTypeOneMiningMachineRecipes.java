package com.EyeOfHarmonyBuffer.Recipe;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.GTValues;

import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.ElectricTypeOneMiningMachine;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

public class ElectricTypeOneMiningMachineRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {

        GTValues.RA.stdBuilder()
            .itemInputs()
            .itemOutputs(
                GTCMItemList.YuanShiKuang.get(10)
            )
            .eut(0)
            .duration(10 * SECONDS)
            .addTo(ElectricTypeOneMiningMachine);
    }
}
