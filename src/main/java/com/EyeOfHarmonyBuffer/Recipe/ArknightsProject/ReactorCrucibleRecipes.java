package com.EyeOfHarmonyBuffer.Recipe.ArknightsProject;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.common.material.EOHBMaterialPool;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.Materials;

import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.ReactorCrucible;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

public class ReactorCrucibleRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.YaZhenFenMo.get(1)
            )
            .fluidInputs(
                Materials.Water.getFluid(1000)
            )
            .fluidOutputs(
                EOHBMaterialPool.YaZhenSolution.getFluidOrGas(1000)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(ReactorCrucible);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.JinCaoFenMo.get(1)
            )
            .fluidInputs(
                Materials.Water.getFluid(1000)
            )
            .fluidOutputs(
                EOHBMaterialPool.JinCaoSolution.getFluidOrGas(1000)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(ReactorCrucible);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.XiRang.get(1)
            )
            .fluidInputs(
                Materials.Water.getFluid(1000)
            )
            .fluidOutputs(
                EOHBMaterialPool.LiquidXiranite.getFluidOrGas(1000)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(ReactorCrucible);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ZhongXiRang.get(1)
            )
            .fluidInputs(
                EOHBMaterialPool.PrecipitationAcid.getFluidOrGas(1000)
            )
            .fluidOutputs(
                EOHBMaterialPool.LiquidHeavyXiranite.getFluidOrGas(1000)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(ReactorCrucible);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ChiTongFenMo.get(1)
            )
            .fluidInputs(
                EOHBMaterialPool.PrecipitationAcid.getFluidOrGas(1000)
            )
            .fluidOutputs(
                EOHBMaterialPool.CupriumSolution.getFluidOrGas(1000)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(ReactorCrucible);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.LiquidXiranite.getFluidOrGas(1000),
                EOHBMaterialPool.Sewage.getFluidOrGas(1000)
            )
            .fluidOutputs(
                EOHBMaterialPool.XirconEffluent.getFluidOrGas(1000),
                EOHBMaterialPool.InertXirconEffluent.getFluidOrGas(1000)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(ReactorCrucible);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.LanTieFenMo.get(1)
            )
            .fluidInputs(
                EOHBMaterialPool.XirconEffluent.getFluidOrGas(2000)
            )
            .itemOutputs(
                GTCMItemList.RangJing.get(1)
            )
            .fluidOutputs(
                EOHBMaterialPool.Sewage.getFluidOrGas(1000)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(ReactorCrucible);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.LanTieFenMo.get(1)
            )
            .fluidInputs(
                EOHBMaterialPool.HetoniteSolution.getFluidOrGas(2000)
            )
            .itemOutputs(
                GTCMItemList.HeTongKuai.get(1)
            )
            .fluidOutputs(
                EOHBMaterialPool.Sewage.getFluidOrGas(1000)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(ReactorCrucible);
    }
}
