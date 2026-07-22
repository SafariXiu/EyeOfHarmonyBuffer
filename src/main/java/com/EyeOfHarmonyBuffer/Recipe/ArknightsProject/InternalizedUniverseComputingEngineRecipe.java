package com.EyeOfHarmonyBuffer.Recipe.ArknightsProject;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.GTValues;

import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.InternalizedUniverseComputingEngine;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

public class InternalizedUniverseComputingEngineRecipe implements IRecipePool {

    @Override
    public void loadRecipes() {

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.YuanShi.get(1)
            )
            .eut(10000)
            .duration(60 * SECONDS)
            .addTo(InternalizedUniverseComputingEngine);
    }
}
