package com.EyeOfHarmonyBuffer.Recipe;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.GTValues;
import gregtech.api.recipe.RecipeMetadataKey;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import static com.EyeOfHarmonyBuffer.Recipe.RecipeKey.GTExtendedRecipeKeys.NEI_OUTPUT_MAX;
import static com.EyeOfHarmonyBuffer.Recipe.RecipeKey.GTExtendedRecipeKeys.NEI_OUTPUT_MIN;
import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.OrundumDynamo;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.EOHB_Recipe_OrundumDynamo_Tooltip_00;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;
import static net.minecraft.world.EnumSkyBlock.Block;

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
            .setNEIDesc(EOHB_Recipe_OrundumDynamo_Tooltip_00 + "100000")
            .addTo(OrundumDynamo);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.DiRongLiangDianChi.get(1)
            )
            .itemOutputs(
                GTCMItemList.PoSuiYuanShi.get(25)
            )
            .eut(0)
            .duration(5 * SECONDS)
            .setNEIDesc(EOHB_Recipe_OrundumDynamo_Tooltip_00 + "500000")
            .addTo(OrundumDynamo);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ZhongRongLiangDianChi.get(1)
            )
            .itemOutputs(
                GTCMItemList.PoSuiYuanShi.get(50)
            )
            .eut(0)
            .duration(5 * SECONDS)
            .setNEIDesc(EOHB_Recipe_OrundumDynamo_Tooltip_00 + "1000000")
            .addTo(OrundumDynamo);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.GaoRongLiangDianChi.get(1)
            )
            .itemOutputs(
                GTCMItemList.PoSuiYuanShi.get(200)
            )
            .eut(0)
            .duration(5 * SECONDS)
            .setNEIDesc(EOHB_Recipe_OrundumDynamo_Tooltip_00 + "5000000")
            .addTo(OrundumDynamo);

        /*GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.HeChengYu.get(2)
            )
            .itemOutputs(
                new ItemStack(Blocks.stone,64)
            )
            .eut(0)
            .duration(5 * SECONDS)
            .metadata(NEI_OUTPUT_MIN, 32)
            .metadata(NEI_OUTPUT_MAX, 64)
            .setNEIDesc(EOHB_Recipe_OrundumDynamo_Tooltip_00 + "1000")
            .addTo(OrundumDynamo);*/
    }
}
