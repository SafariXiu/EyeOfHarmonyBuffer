package com.EyeOfHarmonyBuffer.Recipe;

import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.TierEU;
import gregtech.api.util.GTUtility;
import net.minecraftforge.fluids.FluidRegistry;

import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.CoreDrill;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

public class CoreDrillRecipe implements IRecipePool {

    @Override
    public void loadRecipes() {
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTUtility.getIntegratedCircuit(1)
            )
            .fluidInputs(
                FluidRegistry.getFluidStack("creosote", 500)
            )
            .fluidOutputs(
                FluidRegistry.getFluidStack("for.honey", 100)
            )
            .eut(0)
            .duration(5 * SECONDS)
            .addTo(CoreDrill);
    }
}
