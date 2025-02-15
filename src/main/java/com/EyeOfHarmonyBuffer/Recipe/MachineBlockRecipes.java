package com.EyeOfHarmonyBuffer.Recipe;

import com.EyeOfHarmonyBuffer.Config.MachineLoaderConfig;
import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.TierEU;
import gregtech.api.util.GTOreDictUnificator;

import static gregtech.api.enums.Mods.GTPlusPlus;
import static gregtech.api.enums.Mods.IndustrialCraft2;
import static gregtech.api.recipe.RecipeMaps.assemblerRecipes;
import static gregtech.api.util.GTModHandler.getModItem;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

public final class MachineBlockRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {
        if(MachineLoaderConfig.VendingMachines){
            GTValues.RA.stdBuilder()
                .itemInputs(
                    getModItem(IndustrialCraft2.ID,"blockPersonal",64,1),
                    GTOreDictUnificator.get(OrePrefixes.circuit, Materials.Data,16),
                    getModItem(GTPlusPlus.ID, "gtplusplus.blockcasings.3",16,2)
                )
                .itemOutputs(
                    GTCMItemList.VendingMachines.get(1)
                )
                .eut(TierEU.RECIPE_EV)
                .duration(20 * SECONDS)
                .addTo(assemblerRecipes);
        }
    }
}
