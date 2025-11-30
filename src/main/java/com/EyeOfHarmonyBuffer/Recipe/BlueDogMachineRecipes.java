package com.EyeOfHarmonyBuffer.Recipe;

import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.Materials;
import gregtech.api.enums.TierEU;

import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.BlueDogMachine;
import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.BlueDogMachineMax;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

public class BlueDogMachineRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {

        GTValues.RA.stdBuilder()
            .fluidInputs(
                Materials.Water.getFluid(1000)
            )
            .fluidOutputs(
                Materials.Grade1PurifiedWater.getFluid(Integer.MAX_VALUE),
                Materials.Grade2PurifiedWater.getFluid(Integer.MAX_VALUE),
                Materials.Grade3PurifiedWater.getFluid(Integer.MAX_VALUE),
                Materials.Grade4PurifiedWater.getFluid(Integer.MAX_VALUE),
                Materials.Grade5PurifiedWater.getFluid(Integer.MAX_VALUE),
                Materials.Grade6PurifiedWater.getFluid(Integer.MAX_VALUE),
                Materials.Grade7PurifiedWater.getFluid(Integer.MAX_VALUE),
                Materials.Grade8PurifiedWater.getFluid(Integer.MAX_VALUE),
                Materials.FlocculationWasteLiquid.getFluid(Integer.MAX_VALUE)
            )
            .eut(0)
            .duration(5 * SECONDS)
            .addTo(BlueDogMachineMax);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                Materials.Water.getFluid(1000)
            )
            .fluidOutputs(
                Materials.Grade1PurifiedWater.getFluid(900),
                Materials.Water.getFluid(1000)
            )
            .eut(TierEU.RECIPE_LuV)
            .duration(30 * SECONDS)
            .addTo(BlueDogMachine);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                Materials.Grade1PurifiedWater.getFluid(1000)
            )
            .fluidOutputs(
                Materials.Grade2PurifiedWater.getFluid(900),
                Materials.Water.getFluid(1000)
            )
            .eut(TierEU.RECIPE_ZPM)
            .duration(30 * SECONDS)
            .addTo(BlueDogMachine);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                Materials.Grade2PurifiedWater.getFluid(1000)
            )
            .fluidOutputs(
                Materials.Grade3PurifiedWater.getFluid(900),
                Materials.Water.getFluid(1000)
            )
            .eut(TierEU.RECIPE_UV)
            .duration(30 * SECONDS)
            .addTo(BlueDogMachine);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                Materials.Grade3PurifiedWater.getFluid(1000)
            )
            .fluidOutputs(
                Materials.Grade4PurifiedWater.getFluid(900),
                Materials.Water.getFluid(1000)
            )
            .eut(TierEU.RECIPE_UHV)
            .duration(30 * SECONDS)
            .addTo(BlueDogMachine);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                Materials.Grade4PurifiedWater.getFluid(1000)
            )
            .fluidOutputs(
                Materials.Grade5PurifiedWater.getFluid(900),
                Materials.Water.getFluid(1000)
            )
            .eut(TierEU.RECIPE_UEV)
            .duration(30 * SECONDS)
            .addTo(BlueDogMachine);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                Materials.Grade5PurifiedWater.getFluid(1000)
            )
            .fluidOutputs(
                Materials.Grade6PurifiedWater.getFluid(900),
                Materials.Water.getFluid(1000)
            )
            .eut(TierEU.RECIPE_UIV)
            .duration(30 * SECONDS)
            .addTo(BlueDogMachine);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                Materials.Grade6PurifiedWater.getFluid(1000)
            )
            .fluidOutputs(
                Materials.Grade7PurifiedWater.getFluid(900),
                Materials.Water.getFluid(1000)
            )
            .eut(TierEU.RECIPE_UMV)
            .duration(30 * SECONDS)
            .addTo(BlueDogMachine);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                Materials.Grade7PurifiedWater.getFluid(1000)
            )
            .fluidOutputs(
                Materials.Grade8PurifiedWater.getFluid(900),
                Materials.StableBaryonicMatter.getFluid(500),
                Materials.Water.getFluid(1000)
            )
            .eut(TierEU.RECIPE_UXV)
            .duration(30 * SECONDS)
            .addTo(BlueDogMachine);
    }
}
