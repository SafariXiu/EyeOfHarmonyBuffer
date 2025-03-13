package com.EyeOfHarmonyBuffer.Recipe;

import bartworks.system.material.WerkstoffLoader;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTUtility;
import gtnhlanth.common.register.WerkstoffMaterialPool;

import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.CoreDrill;
import static com.EyeOfHarmonyBuffer.utils.Utils.setStackSize;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

public class CoreDrillRecipe implements IRecipePool {

    @Override
    public void loadRecipes() {
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTUtility.getIntegratedCircuit(1),
                GTUtility.copyAmount(0,GTOreDictUnificator.get(OrePrefixes.dust,Materials.RawRubber,1)),
                GTUtility.copyAmount(0,GTOreDictUnificator.get(OrePrefixes.dust,Materials.Sulfur,1))
            )
            .fluidOutputs(
                Materials.Silicone.getMolten(Integer.MAX_VALUE),
                Materials.StyreneButadieneRubber.getMolten(Integer.MAX_VALUE),
                Materials.Rubber.getMolten(Integer.MAX_VALUE),
                WerkstoffMaterialPool.PTMEGElastomer.getMolten(Integer.MAX_VALUE)
            )
            .eut(0)
            .duration(5 * SECONDS)
            .addTo(CoreDrill);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTUtility.getIntegratedCircuit(2),
                GTUtility.copyAmount(0, WerkstoffLoader.PTMetallicPowder.get(OrePrefixes.dust, 1))
            )
            .itemOutputs(
                setStackSize(GTOreDictUnificator.get(OrePrefixes.dust,Materials.Platinum,1),Integer.MAX_VALUE),
                setStackSize(GTOreDictUnificator.get(OrePrefixes.dust,Materials.Palladium,1),Integer.MAX_VALUE),
                setStackSize(GTOreDictUnificator.get(OrePrefixes.dust,Materials.Iridium,1),Integer.MAX_VALUE),
                setStackSize(GTOreDictUnificator.get(OrePrefixes.dust,Materials.Osmium,1),Integer.MAX_VALUE),
                WerkstoffLoader.Rhodium.get(OrePrefixes.dust, Integer.MAX_VALUE),
                WerkstoffLoader.Ruthenium.get(OrePrefixes.dust,Integer.MAX_VALUE)
            )
            .eut(0)
            .duration(5 * SECONDS)
            .addTo(CoreDrill);
    }
}
