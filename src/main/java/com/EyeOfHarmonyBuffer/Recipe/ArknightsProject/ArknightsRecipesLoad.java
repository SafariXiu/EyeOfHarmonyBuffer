package com.EyeOfHarmonyBuffer.Recipe.ArknightsProject;

import bartworks.system.material.WerkstoffLoader;
import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.common.material.EOHBMaterialPool;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.TierEU;
import gregtech.api.util.GTUtility;

import static gregtech.api.recipe.RecipeMaps.assemblerRecipes;
import static gregtech.api.recipe.RecipeMaps.mixerRecipes;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

public class ArknightsRecipesLoad implements IRecipePool {

    @Override
    public void loadRecipes() {

        GTValues.RA.stdBuilder()
            .itemInputs(
                Materials.Magnesium.getDust(32),
                WerkstoffLoader.TantalumCarbideHafniumCarbideMixture.get(OrePrefixes.dust, 16),
                Materials.Europium.getDust(16)
            )
            .itemOutputs(
                EOHBMaterialPool.Hoyomixium.get(OrePrefixes.dust, 64)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(325 * SECONDS)
            .addTo(mixerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTUtility.getIntegratedCircuit(2),
                GTCMItemList.YuanShiFuelRod1.get(2),
                EOHBMaterialPool.ArsenicImpact.get(OrePrefixes.stick, 4)
            )
            .itemOutputs(
                GTCMItemList.YuanShiFuelRod2.get(1)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(10 * SECONDS)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTUtility.getIntegratedCircuit(5),
                GTCMItemList.YuanShiFuelRod2.get(2),
                EOHBMaterialPool.ArsenicImpact.get(OrePrefixes.stick, 4)
            )
            .itemOutputs(
                GTCMItemList.YuanShiFuelRod4.get(1)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(10 * SECONDS)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTUtility.getIntegratedCircuit(4),
                GTCMItemList.YuanShiFuelRod1.get(4),
                EOHBMaterialPool.ArsenicImpact.get(OrePrefixes.stickLong, 6)
            )
            .itemOutputs(
                GTCMItemList.YuanShiFuelRod4.get(1)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(10 * SECONDS)
            .addTo(assemblerRecipes);
    }
}
