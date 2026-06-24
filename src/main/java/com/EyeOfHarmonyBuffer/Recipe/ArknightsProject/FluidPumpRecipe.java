package com.EyeOfHarmonyBuffer.Recipe.ArknightsProject;

import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.Materials;
import gregtech.api.util.GTUtility;

import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.FluidPump;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

public class FluidPumpRecipe implements IRecipePool {

    @Override
    public void loadRecipes() {

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTUtility.getIntegratedCircuit(1)
            )
            .fluidOutputs(
                Materials.Water.getFluid(10000)
            )
            .eut(10000)
            .duration(10 * SECONDS)
            .addTo(FluidPump);
    }
}
