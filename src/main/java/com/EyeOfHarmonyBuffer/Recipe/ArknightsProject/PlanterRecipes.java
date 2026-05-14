package com.EyeOfHarmonyBuffer.Recipe.ArknightsProject;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.GTValues;

import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.Planter;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

public class PlanterRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ShaYeZhongZi.get(1)
            )
            .itemOutputs(
                GTCMItemList.ShaYe.get(2)
            )
            .eut(2000)
            .duration(5 * SECONDS)
            .addTo(Planter);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.YaZhenZhongZi.get(1)
            )
            .itemOutputs(
                GTCMItemList.YaZhen.get(2)
            )
            .eut(2000)
            .duration(5 * SECONDS)
            .addTo(Planter);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.QiaoHuaZhongZi.get(1)
            )
            .itemOutputs(
                GTCMItemList.QiaoHua.get(2)
            )
            .eut(2000)
            .duration(5 * SECONDS)
            .addTo(Planter);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.TongHuaShuZhong.get(1)
            )
            .itemOutputs(
                GTCMItemList.TongHuaGuanMu.get(2)
            )
            .eut(2000)
            .duration(5 * SECONDS)
            .addTo(Planter);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.GanShiZhongZi.get(1)
            )
            .itemOutputs(
                GTCMItemList.GanShi.get(2)
            )
            .eut(2000)
            .duration(5 * SECONDS)
            .addTo(Planter);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.JinCaoZhongZi.get(1)
            )
            .itemOutputs(
                GTCMItemList.JinCao.get(2)
            )
            .eut(2000)
            .duration(5 * SECONDS)
            .addTo(Planter);
    }
}
