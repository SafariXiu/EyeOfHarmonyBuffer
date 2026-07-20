package com.EyeOfHarmonyBuffer.Recipe.ArknightsProject;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.common.material.EOHBMaterialPool;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.GTValues;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.GasExtractor;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

public class GasExtractorRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {

        ItemStack DuoQi = GTCMItemList.DuoQiMainBlock.get(1);
        DuoQi.setStackDisplayName(StatCollector.translateToLocal("eohb_recipe.PowerMiner.DuoQi_source"));

        GTValues.RA.stdBuilder()
            .itemInputs(
                DuoQi
            )
            .fluidOutputs(
                EOHBMaterialPool.Inergen.getFluidOrGas(6000)
            )
            .eut(0)
            .duration(10 * SECONDS)
            .addTo(GasExtractor);

        ItemStack XiRangQi = GTCMItemList.XiRangQiMainBlock.get(1);
        XiRangQi.setStackDisplayName(StatCollector.translateToLocal("eohb_recipe.PowerMiner.XiRangQi_source"));

        GTValues.RA.stdBuilder()
            .itemInputs(
                DuoQi
            )
            .fluidOutputs(
                EOHBMaterialPool.Inergen.getFluidOrGas(6000)
            )
            .eut(0)
            .duration(10 * SECONDS)
            .addTo(GasExtractor);
    }
}
