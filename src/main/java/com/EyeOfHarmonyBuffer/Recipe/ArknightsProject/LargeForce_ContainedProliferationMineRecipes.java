package com.EyeOfHarmonyBuffer.Recipe.ArknightsProject;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.GTValues;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.LargeForce_ContainedProliferationMine;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

public class LargeForce_ContainedProliferationMineRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {

        ItemStack YuanShiKuang = GTCMItemList.YuanShiMainBlock.get(1);
        YuanShiKuang.setStackDisplayName(StatCollector.translateToLocal("eohb_recipe.PowerMiner.YuanShi_source"));

        GTValues.RA.stdBuilder()
            .itemInputs(
                YuanShiKuang
            )
            .itemOutputs(
                GTCMItemList.YuanShiKuang.get(1_000_000)
            )
            .eut(100_000_000L)
            .duration(60 * SECONDS)
            .addTo(LargeForce_ContainedProliferationMine);

        ItemStack LanTieKuang = GTCMItemList.LanTieMainBlock.get(1);
        LanTieKuang.setStackDisplayName(StatCollector.translateToLocal("eohb_recipe.PowerMiner.LanTie_source"));

        GTValues.RA.stdBuilder()
            .itemInputs(
                LanTieKuang
            )
            .itemOutputs(
                GTCMItemList.LanTieKuang.get(1_000_000)
            )
            .eut(100_000_000L)
            .duration(60 * SECONDS)
            .addTo(LargeForce_ContainedProliferationMine);

        ItemStack ZiJingKuang = GTCMItemList.ZiJingMainBlock.get(1);
        ZiJingKuang.setStackDisplayName(StatCollector.translateToLocal("eohb_recipe.PowerMiner.ZiJing_source"));

        GTValues.RA.stdBuilder()
            .itemInputs(
                ZiJingKuang
            )
            .itemOutputs(
                GTCMItemList.ZiJingKuang.get(1_000_000)
            )
            .eut(100_000_000L)
            .duration(60 * SECONDS)
            .addTo(LargeForce_ContainedProliferationMine);

        ItemStack ChiTongKuang = GTCMItemList.ChiTongMainBlock.get(1);
        ChiTongKuang.setStackDisplayName(StatCollector.translateToLocal("eohb_recipe.PowerMiner.ChiTong_source"));

        GTValues.RA.stdBuilder()
            .itemInputs(
                ChiTongKuang
            )
            .itemOutputs(
                GTCMItemList.ChiTongKuang.get(1_000_000)
            )
            .eut(100_000_000L)
            .duration(60 * SECONDS)
            .addTo(LargeForce_ContainedProliferationMine);
    }
}
