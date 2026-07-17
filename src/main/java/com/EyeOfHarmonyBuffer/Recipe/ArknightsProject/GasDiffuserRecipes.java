package com.EyeOfHarmonyBuffer.Recipe.ArknightsProject;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.GTValues;

import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.GasDiffuser;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

public class GasDiffuserRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.DuoQi.get(1)
            )
            .eut(50000)
            .duration(60 * SECONDS)
            .addTo(GasDiffuser);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.SuanQi.get(1)
            )
            .eut(50000)
            .duration(60 * SECONDS)
            .addTo(GasDiffuser);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ShuiZhengQi.get(1)
            )
            .eut(50000)
            .duration(60 * SECONDS)
            .addTo(GasDiffuser);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.XiRangQi.get(1)
            )
            .eut(50000)
            .duration(60 * SECONDS)
            .addTo(GasDiffuser);
    }
}
