package com.EyeOfHarmonyBuffer.Recipe.ArknightsProject;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.common.material.EOHBMaterialPool;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.Materials;
import gregtech.api.util.GTUtility;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.FluidPumpMK1;
import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.FluidPumpMK2;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

public class FluidPumpRecipe implements IRecipePool {

    @Override
    public void loadRecipes() {

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTUtility.getIntegratedCircuit(1)
            )
            .fluidOutputs(
                Materials.Water.getFluid(10000)
            )
            .eut(10000)
            .duration(10 * SECONDS)
            .addTo(FluidPumpMK1);

        ItemStack waterDisplay = GTCMItemList.QingShui.get(1).copy();
        waterDisplay.setStackDisplayName(StatCollector.translateToLocal("eohb_recipe.fluidpump.water_source"));

        GTValues.RA.stdBuilder()
            .itemInputs(
                waterDisplay
            )
            .fluidOutputs(
                Materials.Water.getFluid(10000)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(FluidPumpMK2);

        ItemStack acidDisplay = GTCMItemList.ChenJiSuan.get(1).copy();
        acidDisplay.setStackDisplayName(StatCollector.translateToLocal("eohb_recipe.fluidpump.precipitation_acid"));

        GTValues.RA.stdBuilder()
            .itemInputs(
                acidDisplay
            )
            .fluidOutputs(
                EOHBMaterialPool.PrecipitationAcid.getFluidOrGas(5000)
            )
            .eut(50000)
            .duration(20 * SECONDS)
            .addTo(FluidPumpMK2);

        ItemStack lava = new ItemStack(Items.lava_bucket).copy();
        lava.setStackDisplayName(StatCollector.translateToLocal("eohb_recipe.fluidpump.lava_source"));

        GTValues.RA.stdBuilder()
            .itemInputs(
                lava
            )
            .fluidOutputs(
                Materials.Lava.getFluid(10000)
            )
            .eut(40000)
            .duration(10 * SECONDS)
            .addTo(FluidPumpMK2);
    }
}
