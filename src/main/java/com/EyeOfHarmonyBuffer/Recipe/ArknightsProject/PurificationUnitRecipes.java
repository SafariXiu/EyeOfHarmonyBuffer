package com.EyeOfHarmonyBuffer.Recipe.ArknightsProject;

import com.EyeOfHarmonyBuffer.common.material.EOHBMaterialPool;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.Materials;

import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.PurificationUnit;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

public class PurificationUnitRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.InertXirconEffluent.getFluidOrGas(4000)
            )
            .fluidOutputs(
                EOHBMaterialPool.XirconEffluent.getFluidOrGas(1000),
                Materials.Water.getFluid(1000)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(PurificationUnit);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.CupriumSolution.getFluidOrGas(4000)
            )
            .fluidOutputs(
                EOHBMaterialPool.HetoniteSolution.getFluidOrGas(1000),
                EOHBMaterialPool.PrecipitationAcid.getFluidOrGas(1000)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(PurificationUnit);
    }
}
