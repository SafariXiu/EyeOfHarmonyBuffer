package com.EyeOfHarmonyBuffer.Recipe.ArknightsProject;

import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.DysonManufacturing;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;

import gregtech.api.enums.GTValues;

/** 戴森组件制造配方（占位材料与数值，后续替换）。 */
public class DysonManufacturingRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {

        GTValues.RA.stdBuilder()
            .itemInputs(GTCMItemList.YuanShi.get(64))
            .itemOutputs(GTCMItemList.DysonCloudComponent.get(1))
            .eut(1000)
            .duration(60 * SECONDS)
            .addTo(DysonManufacturing);

        GTValues.RA.stdBuilder()
            .itemInputs(GTCMItemList.XiRang.get(64))
            .itemOutputs(GTCMItemList.DysonFrameComponent.get(1))
            .eut(2000)
            .duration(60 * SECONDS)
            .addTo(DysonManufacturing);
    }
}
