package com.EyeOfHarmonyBuffer.Recipe.ArknightsProject;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.GTValues;

import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.Pulverizer;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

public class PulverizerRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ShaYe.get(1)
            )
            .itemOutputs(
                GTCMItemList.ShaYeFenMo.get(1)
            )
            .eut(5000)
            .duration(10 * SECONDS)
            .addTo(Pulverizer);
    }
}
