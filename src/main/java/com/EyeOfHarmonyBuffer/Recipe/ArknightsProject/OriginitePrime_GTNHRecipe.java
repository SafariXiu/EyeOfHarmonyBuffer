package com.EyeOfHarmonyBuffer.Recipe.ArknightsProject;

import bartworks.system.material.WerkstoffLoader;
import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import goodgenerator.items.GGMaterial;
import gregtech.api.enums.*;
import gtPlusPlus.core.fluids.GTPPFluids;
import net.minecraftforge.fluids.FluidStack;

import static com.EyeOfHarmonyBuffer.common.material.EOHBMaterialPool.OriginiumWasteLiquid;
import static gregtech.api.enums.Mods.EtFuturumRequiem;
import static gregtech.api.recipe.RecipeMaps.*;
import static gregtech.api.util.GTModHandler.getModItem;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;
import static gregtech.api.util.GTRecipeConstants.COIL_HEAT;

public class OriginitePrime_GTNHRecipe implements IRecipePool {

    @Override
    public void loadRecipes() {

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

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.HanZaYuanShiFenMo.get(24),
                Materials.Tellurium.getDust(8)
            )
            .fluidInputs(
                WerkstoffLoader.AquaRegia.getFluidOrGas(8000)
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

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ChunJingYuanShiFenMo.get(8),
                ItemList.Intensely_Bonded_Netherite_Nanoparticles.get(4),
                ItemList.Circuit_Silicon_Ingot.get(1)
            )
            .fluidInputs(
                new FluidStack(GTPPFluids.Cryotheum, 8000)
            )
            .itemOutputs(
                GTCMItemList.YuanShiJingHe.get(1)
            )
            .eut(TierEU.RECIPE_LuV)
            .duration(200 * SECONDS)
            .addTo(mixerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.YuanShiJingHe.get(1),
                getModItem(EtFuturumRequiem.ID, "netherite_scrap", 8)
            )
            .fluidInputs(
                Materials.Sunnarium.getMolten(32000)
            )
            .itemOutputs(
                GTCMItemList.UnactivatedYuanShi.get(1)
            )
            .eut(TierEU.RECIPE_LuV)
            .duration(200 * SECONDS)
            .addTo(autoclaveRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.UnactivatedYuanShi.get(1),
                WerkstoffLoader.CubicZirconia.get(OrePrefixes.dust,32)
            )
            .fluidInputs(
                GGMaterial.antimonyPentafluoride.getFluidOrGas(16000)
            )
            .itemOutputs(
                GTCMItemList.YuanShi.get(1)
            )
            .eut(TierEU.RECIPE_LuV)
            .duration(50 * SECONDS)
            .metadata(COIL_HEAT, 4500)
            .addTo(blastFurnaceRecipes);

    }
}
