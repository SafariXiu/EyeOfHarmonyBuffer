package com.EyeOfHarmonyBuffer.Recipe.ArknightsProject;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.GTValues;

import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.OrundumDynamo;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.EOHB_Recipe_OrundumDynamo_Tooltip_00;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

public class OrundumDynamoRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.YuanShi.get(1)
            )
            .itemOutputs(
                GTCMItemList.HeChengYu.get(180)
            )
            .eut(0)
            .duration(5 * SECONDS)
            .setNEIDesc(EOHB_Recipe_OrundumDynamo_Tooltip_00 + "10000")
            .addTo(OrundumDynamo);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.DiRongGuDiDianChi.get(1)
            )
            .itemOutputs(
                GTCMItemList.PoSuiYuanShi.get(25)
            )
            .eut(0)
            .duration(5 * SECONDS)
            .setNEIDesc(EOHB_Recipe_OrundumDynamo_Tooltip_00 + "20000")
            .addTo(OrundumDynamo);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ZhongRongGuDiDianChi.get(1)
            )
            .itemOutputs(
                GTCMItemList.PoSuiYuanShi.get(50)
            )
            .eut(0)
            .duration(5 * SECONDS)
            .setNEIDesc(EOHB_Recipe_OrundumDynamo_Tooltip_00 + "50000")
            .addTo(OrundumDynamo);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.GaoRongGuDiDianChi.get(1)
            )
            .itemOutputs(
                GTCMItemList.PoSuiYuanShi.get(200)
            )
            .eut(0)
            .duration(5 * SECONDS)
            .setNEIDesc(EOHB_Recipe_OrundumDynamo_Tooltip_00 + "100000")
            .addTo(OrundumDynamo);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.DiRongXiRangDianChi.get(1)
            )
            .itemOutputs(
                GTCMItemList.XiRang.get(1)
            )
            .eut(0)
            .duration(5 * SECONDS)
            .setNEIDesc(EOHB_Recipe_OrundumDynamo_Tooltip_00 + "200000")
            .addTo(OrundumDynamo);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ZhongRongWuLingDianChi.get(1)
            )
            .itemOutputs(
                GTCMItemList.ZhongXiRang.get(1)
            )
            .eut(0)
            .duration(5 * SECONDS)
            .setNEIDesc(EOHB_Recipe_OrundumDynamo_Tooltip_00 + "500000")
            .addTo(OrundumDynamo);
    }
}
