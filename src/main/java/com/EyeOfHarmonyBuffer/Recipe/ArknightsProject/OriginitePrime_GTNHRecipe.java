package com.EyeOfHarmonyBuffer.Recipe.ArknightsProject;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.Materials;
import gregtech.api.enums.TierEU;
import gtPlusPlus.core.fluids.GTPPFluids;
import net.minecraftforge.fluids.FluidStack;

import static com.EyeOfHarmonyBuffer.common.material.EOHBMaterialPool.OriginiumWasteLiquid;
import static gregtech.api.recipe.RecipeMaps.*;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

public class OriginitePrime_GTNHRecipe implements IRecipePool {

    @Override
    public void loadRecipes() {

        //粉碎
        GTValues.RA.stdBuilder()
            .itemInputs(
            GTCMItemList.YuanShiKuang.get(1)
            )
            .itemOutputs(
                GTCMItemList.YuanShiFenMo.get(1)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(5 * SECONDS)
            .addTo(maceratorRecipes);

        //筛选
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.YuanShiFenMo.get(32)
            )
            .itemOutputs(
                GTCMItemList.HanZaYuanShiFenMo.get(24),
                GTCMItemList.DiChunYuanShiFenMo.get(8)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(20 * SECONDS)
            .addTo(sifterRecipes);

        //化学反应釜
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.HanZaYuanShiFenMo.get(24),
                Materials.Tellurium.getDust(8)
            )
            .fluidInputs(
                new FluidStack(GTPPFluids.Cryotheum, 8000)
            )
            .itemOutputs(
                GTCMItemList.ChunJingYuanShiFenMo.get(16)
            )
            .fluidOutputs(
                OriginiumWasteLiquid.getFluidOrGas(8000)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(20 * SECONDS)
            .addTo(multiblockChemicalReactorRecipes);
    }
}
