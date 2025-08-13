package com.EyeOfHarmonyBuffer.Recipe;

import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.*;
import gregtech.api.util.GTOreDictUnificator;
import gtnhintergalactic.recipe.IGRecipeMaps;
import net.minecraft.item.ItemStack;
import tectech.thing.casing.TTCasingsContainer;

import static gregtech.api.enums.Mods.AppliedEnergistics2;
import static gregtech.api.enums.Mods.GTNHIntergalactic;
import static gregtech.api.util.GTModHandler.getModItem;
import static gregtech.api.util.GTRecipeBuilder.MINUTES;

public class SpaceAssemblerRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {
        if (GTNHIntergalactic.isModLoaded()){

            if (AppliedEnergistics2.isModLoaded()){
                GTValues.RA.stdBuilder()
                    .itemInputs(
                        getModItem(AppliedEnergistics2.ID, "item.ItemExtremeStorageCell.Singularity", 1),
                        GTOreDictUnificator.get(OrePrefixes.plateDense, MaterialsUEVplus.TranscendentMetal, 64L),
                        ItemList.Field_Generator_UXV.get(1L),
                        ItemList.ZPM6.get(1L),
                        new ItemStack(TTCasingsContainer.SpacetimeCompressionFieldGenerators, 4, 8),
                        GTOreDictUnificator.get(OrePrefixes.circuit, Materials.UXV, 4),
                        MaterialsUEVplus.Eternity.getNanite(4))
                    .fluidInputs(MaterialsUEVplus.Eternity.getMolten(36864))
                    .itemOutputs(
                        getModItem(AppliedEnergistics2.ID, "item.ItemExtremeStorageCell.Universe", 1))
                    .specialValue(3)
                    .nbtSensitive()
                    .duration(1 * MINUTES)
                    .eut(TierEU.RECIPE_UXV)
                    .addTo(IGRecipeMaps.spaceAssemblerRecipes);
            }
        }
    }
}
