package com.EyeOfHarmonyBuffer.Recipe;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.*;

import static gregtech.api.recipe.RecipeMaps.fluidSolidifierRecipes;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

public class ItemRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {

        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.Shape_Mold_Ingot.get(0)
            )
            .fluidInputs(
                Materials.Water.getFluid(1000)
            )
            .itemOutputs(
                GTCMItemList.Shit.get(1)
            )
            .eut(TierEU.RECIPE_HV)
            .duration(5 * SECONDS)
            .addTo(fluidSolidifierRecipes);
    }
}
