package com.EyeOfHarmonyBuffer.Recipe;

import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.*;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTUtility;

import static com.EyeOfHarmonyBuffer.common.material.EOHBMaterialPool.EOHBCatalyst;
import static gregtech.api.enums.Mods.EternalSingularity;
import static gregtech.api.util.GTModHandler.getModItem;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;
import static gregtech.api.util.GTRecipeConstants.COIL_HEAT;
import static tectech.recipe.TecTechRecipeMaps.godforgeMoltenRecipes;

public class MaterialsRecipes implements IRecipePool{

    @Override
    public void loadRecipes() {

        GTValues.RA.stdBuilder()
            .itemInputsUnsafe(
                GTUtility.copyAmountUnsafe(1280000, GTOreDictUnificator.get(OrePrefixes.ingot, MaterialsUEVplus.WhiteDwarfMatter,1)),
                GTUtility.copyAmountUnsafe(1280000, GTOreDictUnificator.get(OrePrefixes.ingot, MaterialsUEVplus.BlackDwarfMatter,1)),
                GTUtility.copyAmountUnsafe(1280000, getModItem(EternalSingularity.ID, "eternal_singularity",1)),
                EOHBCatalyst.get(OrePrefixes.dust, 1)
            )
            .fluidInputs(
                MaterialsUEVplus.SpaceTime.getMolten(1440000)
            )
            .itemOutputs(
                EOHBCatalyst.get(OrePrefixes.ingot, 1)
            )
            .eut(TierEU.RECIPE_UXV)
            .duration(300 * SECONDS)
            .metadata(COIL_HEAT, 49999)
            .addTo(godforgeMoltenRecipes);
    }
}
