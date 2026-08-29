package com.EyeOfHarmonyBuffer.Recipe.ArknightsProject;

import com.EyeOfHarmonyBuffer.common.material.EOHBMaterialPool;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.Gas.GasEnvRecipeFlags;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.GTValues;

import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.GasReactorGlobe;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.EOHB_Environment_ACRID;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

public class GasReactorGlobeRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.CupriumGas.getFluidOrGas(2000),
                EOHBMaterialPool.Xiragen.getFluidOrGas(1000)
            )
            .fluidOutputs(
                EOHBMaterialPool.PyrroliteGas.getFluidOrGas(1000)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .specialValue(GasEnvRecipeFlags.ACRID)
            .setNEIDesc(EOHB_Environment_ACRID)
            .addTo(GasReactorGlobe);
    }
}
