package com.EyeOfHarmonyBuffer.Recipe.ArknightsProject;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.common.material.EOHBMaterialPool;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.OrePrefixes;

import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.ShapingMachine;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

public class ShapingMachineRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.LanTieKuai.get(2)
            )
            .itemOutputs(
                GTCMItemList.LanTiePing.get(1)
            )
            .eut(10000)
            .duration(10 * SECONDS)
            .addTo(ShapingMachine);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.GaoJingXianWei.get(2)
            )
            .itemOutputs(
                GTCMItemList.GaoJingZhiPing.get(1)
            )
            .eut(10000)
            .duration(10 * SECONDS)
            .addTo(ShapingMachine);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ZiJingXianWei.get(2)
            )
            .itemOutputs(
                GTCMItemList.ZiJingZhiPing.get(1)
            )
            .eut(10000)
            .duration(10 * SECONDS)
            .addTo(ShapingMachine);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.GangKuai.get(2)
            )
            .itemOutputs(
                GTCMItemList.GangZhiPing.get(1)
            )
            .eut(10000)
            .duration(10 * SECONDS)
            .addTo(ShapingMachine);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ChiTongKuai.get(2)
            )
            .itemOutputs(
                GTCMItemList.ChiTongPing.get(1)
            )
            .eut(10000)
            .duration(10 * SECONDS)
            .addTo(ShapingMachine);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.HeTongKuai.get(2)
            )
            .itemOutputs(
                GTCMItemList.HeTongPing.get(1)
            )
            .eut(10000)
            .duration(10 * SECONDS)
            .addTo(ShapingMachine);

        GTValues.RA.stdBuilder()
            .itemInputs(
                EOHBMaterialPool.ArsenicImpact.get(OrePrefixes.ingot, 2)
            )
            .itemOutputs(
                GTCMItemList.FuelRod_empty1.get(1)
            )
            .eut(10000)
            .duration(10 * SECONDS)
            .addTo(ShapingMachine);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ChiTongKuai.get(2)
            )
            .fluidInputs(
                EOHBMaterialPool.Inergen.getFluidOrGas(1000)
            )
            .itemOutputs(
                GTCMItemList.ChiTongNaiYaPing.get(1)
            )
            .eut(10000)
            .duration(10 * SECONDS)
            .addTo(ShapingMachine);
    }
}
