package com.EyeOfHarmonyBuffer.Recipe.ArknightsProject;

import com.EyeOfHarmonyBuffer.common.material.EOHBMaterialPool;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.GTValues;

import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.HighDensityEnergyFluidGenerator;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

public class HighDensityEnergyFluidGeneratorRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.UnstableOrundumSolvent.getFluidOrGas(100)
            )
            .fluidOutputs(
                EOHBMaterialPool.StabilizedHigh_EnergyOrundumSolvent.getFluidOrGas(90),
                EOHBMaterialPool.AnomalousEnergyCondensate.getFluidOrGas(10)
            )
            .fluidOutputChances(
                10000,
                50
            )
            .eut(500000)
            .duration(50 * SECONDS)
            .addTo(HighDensityEnergyFluidGenerator);
    }
}
