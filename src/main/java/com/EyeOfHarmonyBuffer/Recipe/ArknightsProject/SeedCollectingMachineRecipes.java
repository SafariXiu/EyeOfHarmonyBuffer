package com.EyeOfHarmonyBuffer.Recipe.ArknightsProject;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.GTValues;

import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.SeedCollectingMachine;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

public class SeedCollectingMachineRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ShaYe.get(1)
            )
            .itemOutputs(
                GTCMItemList.ShaYeZhongZi.get(2)
            )
            .eut(2000)
            .duration(5 * SECONDS)
            .addTo(SeedCollectingMachine);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.YaZhen.get(1)
            )
            .itemOutputs(
                GTCMItemList.YaZhenZhongZi.get(2)
            )
            .eut(2000)
            .duration(5 * SECONDS)
            .addTo(SeedCollectingMachine);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.QiaoHua.get(1)
            )
            .itemOutputs(
                GTCMItemList.QiaoHuaZhongZi.get(2)
            )
            .eut(2000)
            .duration(5 * SECONDS)
            .addTo(SeedCollectingMachine);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.TongHuaGuanMu.get(1)
            )
            .itemOutputs(
                GTCMItemList.TongHuaShuZhong.get(2)
            )
            .eut(2000)
            .duration(5 * SECONDS)
            .addTo(SeedCollectingMachine);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.GanShi.get(1)
            )
            .itemOutputs(
                GTCMItemList.GanShiZhongZi.get(2)
            )
            .eut(2000)
            .duration(5 * SECONDS)
            .addTo(SeedCollectingMachine);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.JinCao.get(1)
            )
            .itemOutputs(
                GTCMItemList.JinCaoZhongZi.get(2)
            )
            .eut(2000)
            .duration(5 * SECONDS)
            .addTo(SeedCollectingMachine);
    }
}
