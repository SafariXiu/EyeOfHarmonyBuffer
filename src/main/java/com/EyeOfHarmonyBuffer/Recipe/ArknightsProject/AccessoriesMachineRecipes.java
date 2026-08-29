package com.EyeOfHarmonyBuffer.Recipe.ArknightsProject;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.GTValues;

import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.AccessoriesMachine;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

public class AccessoriesMachineRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.LanTieKuai.get(1)
            )
            .itemOutputs(
                GTCMItemList.TieZhiLingJian.get(1)
            )
            .eut(5000)
            .duration(10 * SECONDS)
            .addTo(AccessoriesMachine);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ZiJingXianWei.get(1)
            )
            .itemOutputs(
                GTCMItemList.ZiJingLingJian.get(1)
            )
            .eut(5000)
            .duration(10 * SECONDS)
            .addTo(AccessoriesMachine);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.GangKuai.get(1)
            )
            .itemOutputs(
                GTCMItemList.GangZhiLingJian.get(1)
            )
            .eut(5000)
            .duration(10 * SECONDS)
            .addTo(AccessoriesMachine);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.GaoJingXianWei.get(1)
            )
            .itemOutputs(
                GTCMItemList.GaoJingLingJian.get(1)
            )
            .eut(5000)
            .duration(10 * SECONDS)
            .addTo(AccessoriesMachine);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ChiTongKuai.get(1)
            )
            .itemOutputs(
                GTCMItemList.ChiTongLingJian.get(1)
            )
            .eut(5000)
            .duration(10 * SECONDS)
            .addTo(AccessoriesMachine);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.HeTongKuai.get(5)
            )
            .itemOutputs(
                GTCMItemList.HeTongLingJian.get(1)
            )
            .eut(5000)
            .duration(10 * SECONDS)
            .addTo(AccessoriesMachine);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ZhuoTongKuai.get(5)
            )
            .itemOutputs(
                GTCMItemList.ZhuoTongLingJian.get(1)
            )
            .eut(5000)
            .duration(10 * SECONDS)
            .addTo(AccessoriesMachine);
    }
}
