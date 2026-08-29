package com.EyeOfHarmonyBuffer.Recipe.ArknightsProject;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.GTValues;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.ElectricTypeTwoMiningMachine;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

public class ElectricTypeTwoMiningMachineRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {

        ItemStack YuanShiKuang = GTCMItemList.YuanShiMainBlock.get(1);
        YuanShiKuang.setStackDisplayName(StatCollector.translateToLocal("eohb_recipe.PowerMiner.YuanShi_source"));

        GTValues.RA.stdBuilder()
            .itemInputs(
                YuanShiKuang
            )
            .itemOutputs(
                GTCMItemList.YuanShiKuang.get(160)
            )
            .eut(10000)
            .duration(10 * SECONDS)
            .addTo(ElectricTypeTwoMiningMachine);

        ItemStack ZiJingKuang = GTCMItemList.ZiJingMainBlock.get(1);
        ZiJingKuang.setStackDisplayName(StatCollector.translateToLocal("eohb_recipe.PowerMiner.ZiJing_source"));

        GTValues.RA.stdBuilder()
            .itemInputs(
                ZiJingKuang
            )
            .itemOutputs(
                GTCMItemList.ZiJingKuang.get(160)
            )
            .eut(10000)
            .duration(10 * SECONDS)
            .addTo(ElectricTypeTwoMiningMachine);

        ItemStack LanTieKuang = GTCMItemList.LanTieMainBlock.get(1);
        LanTieKuang.setStackDisplayName(StatCollector.translateToLocal("eohb_recipe.PowerMiner.LanTie_source"));

        GTValues.RA.stdBuilder()
            .itemInputs(
                LanTieKuang
            )
            .itemOutputs(
                GTCMItemList.LanTieKuang.get(160)
            )
            .eut(40000)
            .duration(10 * SECONDS)
            .addTo(ElectricTypeTwoMiningMachine);
    }
}
