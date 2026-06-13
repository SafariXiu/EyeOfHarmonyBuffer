package com.EyeOfHarmonyBuffer.Recipe.ArknightsProject;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.common.material.EOHBMaterialPool;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.Materials;

import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.ForgeOfTheSky;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

public class ForgeOfTheSkyRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.WenDingTanKuai.get(2)
            )
            .fluidInputs(
                Materials.Water.getFluid(1000)
            )
            .itemOutputs(
                GTCMItemList.XiRang.get(1)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(ForgeOfTheSky);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.XiRang.get(10)
            )
            .fluidInputs(
                EOHBMaterialPool.XirconEffluent.getFluidOrGas(5000)
            )
            .itemOutputs(
                GTCMItemList.ZhongXiRang.get(1)
            )
            .eut(250000)
            .duration(10 * SECONDS)
            .addTo(ForgeOfTheSky);
    }
}
