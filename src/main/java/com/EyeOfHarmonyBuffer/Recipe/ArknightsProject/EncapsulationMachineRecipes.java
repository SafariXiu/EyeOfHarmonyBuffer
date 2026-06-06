package com.EyeOfHarmonyBuffer.Recipe.ArknightsProject;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.GTValues;

import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.EncapsulationMachine;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

public class EncapsulationMachineRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ZiJingLingJian.get(5),
                GTCMItemList.YuanShiFenMo.get(10)
            )
            .itemOutputs(
                GTCMItemList.DiRongLiangDianChi.get(1)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(EncapsulationMachine);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ZiJingLingJian.get(5),
                GTCMItemList.TongHuaGuanMuFenMo.get(1)
            )
            .itemOutputs(
                GTCMItemList.GongYeBaoZhaWu.get(1)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(EncapsulationMachine);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.TieZhiLingJian.get(10),
                GTCMItemList.YuanShiFenMo.get(15)
            )
            .itemOutputs(
                GTCMItemList.ZhongRongLiangDianChi.get(1)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(EncapsulationMachine);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.GangZhiLingJian.get(10),
                GTCMItemList.ZhiMiYuanShiFenMo.get(15)
            )
            .itemOutputs(
                GTCMItemList.GaoRongLiangDianChi.get(1)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(EncapsulationMachine);
    }
}
