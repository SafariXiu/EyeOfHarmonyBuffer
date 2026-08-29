package com.EyeOfHarmonyBuffer.Recipe.ArknightsProject;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.common.material.EOHBMaterialPool;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.GTValues;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.GasDiffuser;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

public class GasDiffuserRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {

        ItemStack ShuiZhengQi = GTCMItemList.ShuiZhengQi.get(1);
        ShuiZhengQi.setStackDisplayName(StatCollector.translateToLocal("eohb_recipe.GasDiffuser.ShuiZhengQi_atmosphere"));

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.Aquagen.getFluidOrGas(6000)
            )
            .itemOutputs(
                ShuiZhengQi
            )
            .eut(0)
            .duration(60 * SECONDS)
            .addTo(GasDiffuser);

        ItemStack DuoQi = GTCMItemList.DuoQi.get(1);
        DuoQi.setStackDisplayName(StatCollector.translateToLocal("eohb_recipe.GasDiffuser.DuoQi_atmosphere"));

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.Inergen.getFluidOrGas(6000)
            )
            .itemOutputs(
                DuoQi
            )
            .eut(0)
            .duration(60 * SECONDS)
            .addTo(GasDiffuser);

        ItemStack XiRangQi = GTCMItemList.XiRangQi.get(1);
        XiRangQi.setStackDisplayName(StatCollector.translateToLocal("eohb_recipe.GasDiffuser.XiRangQi_atmosphere"));

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.Xiragen.getFluidOrGas(6000)
            )
            .itemOutputs(
                XiRangQi
            )
            .eut(0)
            .duration(60 * SECONDS)
            .addTo(GasDiffuser);

        ItemStack SuanQi = GTCMItemList.SuanQi.get(1);
        SuanQi.setStackDisplayName(StatCollector.translateToLocal("eohb_recipe.GasDiffuser.SuanQi_atmosphere"));

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.Acridgen.getFluidOrGas(6000)
            )
            .itemOutputs(
                SuanQi
            )
            .eut(0)
            .duration(60 * SECONDS)
            .addTo(GasDiffuser);
    }
}
