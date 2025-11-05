package com.EyeOfHarmonyBuffer.Recipe;

import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import com.dreammaster.recipes.CustomItem;
import gregtech.api.enums.*;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTUtility;
import tectech.thing.CustomItemList;

import static com.EyeOfHarmonyBuffer.common.material.EOHBMaterialPool.EOHBCatalyst;
import static gregtech.api.enums.MetaTileEntityIDs.EyeofHarmony;
import static gregtech.api.enums.Mods.EternalSingularity;
import static gregtech.api.util.GTModHandler.getModItem;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;
import static gregtech.api.util.GTRecipeConstants.COIL_HEAT;
import static gtPlusPlus.api.recipe.GTPPRecipeMaps.mixerNonCellRecipes;
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

        GTValues.RA.stdBuilder()
            .itemInputsUnsafe(
                GTUtility.copyAmount(0, CustomItemList.Machine_Multi_EyeOfHarmony.get(1)),
                GTUtility.copyAmountUnsafe(Integer.MAX_VALUE,MaterialsUEVplus.MagMatter.getDust(1)),
                GTUtility.copyAmountUnsafe(Integer.MAX_VALUE,GTOreDictUnificator.get(OrePrefixes.dust, Materials.Bedrockium, 1)),
                GTUtility.copyAmountUnsafe(Integer.MAX_VALUE,MaterialsUEVplus.Eternity.getDust(1)),
                GTUtility.copyAmountUnsafe(Integer.MAX_VALUE,Materials.Infinity.getDust(1))

            )
            .fluidInputs(
                MaterialsUEVplus.Antimatter.getFluid(1919810),
                MaterialsUEVplus.PhononMedium.getFluid(1145140)
            )
            .itemOutputs(
                EOHBCatalyst.get(OrePrefixes.dust, 1)
            )
            .eut(TierEU.RECIPE_UXV)
            .duration(3000 * SECONDS)
            .addTo(mixerNonCellRecipes);
    }
}
