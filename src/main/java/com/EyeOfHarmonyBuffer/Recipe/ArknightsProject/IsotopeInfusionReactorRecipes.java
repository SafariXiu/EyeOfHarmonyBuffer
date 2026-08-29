package com.EyeOfHarmonyBuffer.Recipe.ArknightsProject;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.common.material.EOHBMaterialPool;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.GTValues;

import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.IsotopeInfusionReactor;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

public class IsotopeInfusionReactorRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.YuanShiTongWeiSu_Alpha.get(1)
            )
            .fluidInputs(
                EOHBMaterialPool.StabilizedHigh_EnergyOrundumSolvent.getFluidOrGas(5000)
            )
            .fluidOutputs(
                EOHBMaterialPool.AdvancedOrundumFuelPrecursor.getFluidOrGas(5000)
            )
            .eut(50000)
            .duration(40 * SECONDS)
            .addTo(IsotopeInfusionReactor);
    }
}
