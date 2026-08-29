package com.EyeOfHarmonyBuffer.Recipe.ArknightsProject;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.common.material.EOHBMaterialPool;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.GTValues;

import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.Solid_GasTransmutingUnit;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

public class Solid_GasTransmutingUnitRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.XiRang.get(1)
            )
            .fluidOutputs(
                EOHBMaterialPool.Xiragen.getFluidOrGas(1000)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(Solid_GasTransmutingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ZhongXiRang.get(2)
            )
            .fluidOutputs(
                EOHBMaterialPool.HeavyXiragen.getFluidOrGas(5000)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(Solid_GasTransmutingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ChiTongKuai.get(2)
            )
            .fluidOutputs(
                EOHBMaterialPool.CupriumGas.getFluidOrGas(1000)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(Solid_GasTransmutingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.HeTongKuai.get(1)
            )
            .fluidOutputs(
                EOHBMaterialPool.HetoniteGas.getFluidOrGas(2000)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(Solid_GasTransmutingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ZhuoTongKuai.get(1)
            )
            .fluidOutputs(
                EOHBMaterialPool.PyrroliteGas.getFluidOrGas(1000)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(Solid_GasTransmutingUnit);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.Xiragen.getFluidOrGas(1000)
            )
            .itemOutputs(
                GTCMItemList.XiRang.get(1)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(Solid_GasTransmutingUnit);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.HeavyXiragen.getFluidOrGas(5000)
            )
            .itemOutputs(
                GTCMItemList.ZhongXiRang.get(2)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(Solid_GasTransmutingUnit);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.CupriumGas.getFluidOrGas(1000)
            )
            .itemOutputs(
                GTCMItemList.ChiTongKuai.get(2)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(Solid_GasTransmutingUnit);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.HetoniteGas.getFluidOrGas(2000)
            )
            .itemOutputs(
                GTCMItemList.HeTongKuai.get(1)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(Solid_GasTransmutingUnit);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.PyrroliteGas.getFluidOrGas(1000)
            )
            .itemOutputs(
                GTCMItemList.ZhuoTongKuai.get(1)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(Solid_GasTransmutingUnit);
    }
}
