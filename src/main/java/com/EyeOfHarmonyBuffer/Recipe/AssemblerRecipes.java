package com.EyeOfHarmonyBuffer.Recipe;

import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.*;
import gregtech.api.util.GTOreDictUnificator;

import static gregtech.api.enums.Mods.AppliedEnergistics2;
import static gregtech.api.enums.Mods.NewHorizonsCoreMod;
import static gregtech.api.recipe.RecipeMaps.assemblerRecipes;
import static gregtech.api.util.GTModHandler.getModItem;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

public final class AssemblerRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {

        GTValues.RA.stdBuilder()
            .itemInputs(
                getModItem(NewHorizonsCoreMod.ID,"item.EngineeringProcessorSpatialPulsatingCore",1,0),
                GTOreDictUnificator.get(OrePrefixes.plate, Materials.Glowstone,4),
                GTOreDictUnificator.get(OrePrefixes.pearl, Materials.Fluix,4)
            )
            .itemOutputs(
                getModItem(AppliedEnergistics2.ID,"item.ItemMultiMaterial",1,32)
            )
            .eut(TierEU.RECIPE_EV)
            .duration(20 * SECONDS)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                getModItem(NewHorizonsCoreMod.ID,"item.EngineeringProcessorSpatialPulsatingCore",1,0),
                GTOreDictUnificator.get(OrePrefixes.plate, Materials.EnderPearl,4),
                getModItem(AppliedEnergistics2.ID,"item.ItemMultiMaterial",4,32)
            )
            .itemOutputs(
                getModItem(AppliedEnergistics2.ID,"item.ItemMultiMaterial",1,33)
            )
            .eut(TierEU.RECIPE_EV)
            .duration(20 * SECONDS)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                getModItem(NewHorizonsCoreMod.ID,"item.EngineeringProcessorSpatialPulsatingCore",1,0),
                GTOreDictUnificator.get(OrePrefixes.plate, Materials.EnderEye,4),
                getModItem(AppliedEnergistics2.ID,"item.ItemMultiMaterial",4,33)
            )
            .itemOutputs(
                getModItem(AppliedEnergistics2.ID,"item.ItemMultiMaterial",1,34)
            )
            .eut(TierEU.RECIPE_EV)
            .duration(20 * SECONDS)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTOreDictUnificator.get(OrePrefixes.gem, Materials.EnderPearl,1),
                GTOreDictUnificator.get(OrePrefixes.plate, Materials.EnderEye,4),
                GTOreDictUnificator.get(OrePrefixes.gem, Materials.Fluix,4)
            )
            .itemOutputs(
                GTOreDictUnificator.get(OrePrefixes.pearl, Materials.Fluix,4)
            )
            .eut(TierEU.RECIPE_EV)
            .duration(20 * SECONDS)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTOreDictUnificator.get(OrePrefixes.gem, Materials.EnderPearl,1),
                GTOreDictUnificator.get(OrePrefixes.plate, Materials.EnderEye,4),
                getModItem(AppliedEnergistics2.ID,"item.ItemMultiMaterial",4,12)
            )
            .itemOutputs(
                GTOreDictUnificator.get(OrePrefixes.pearl, Materials.Fluix,4)
            )
            .eut(TierEU.RECIPE_EV)
            .duration(20 * SECONDS)
            .addTo(assemblerRecipes);

    }
}
