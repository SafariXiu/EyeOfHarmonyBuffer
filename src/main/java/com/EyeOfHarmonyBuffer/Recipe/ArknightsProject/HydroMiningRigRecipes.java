package com.EyeOfHarmonyBuffer.Recipe.ArknightsProject;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.Materials;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.HydroMiningRig;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

public class HydroMiningRigRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {

        ItemStack YuanShiKuang = GTCMItemList.YuanShiMainBlock.get(1);
        YuanShiKuang.setStackDisplayName(StatCollector.translateToLocal("eohb_recipe.PowerMiner.YuanShi_source"));

        GTValues.RA.stdBuilder()
            .itemInputs(
                YuanShiKuang
            )
            .fluidInputs(
                Materials.Water.getFluid(6000)
            )
            .itemOutputs(
                GTCMItemList.YuanShiKuang.get(320)
            )
            .eut(0)
            .duration(10 * SECONDS)
            .addTo(HydroMiningRig);

        ItemStack LanTieKuang = GTCMItemList.LanTieMainBlock.get(1);
        LanTieKuang.setStackDisplayName(StatCollector.translateToLocal("eohb_recipe.PowerMiner.LanTie_source"));

        GTValues.RA.stdBuilder()
            .itemInputs(
                LanTieKuang
            )
            .fluidInputs(
                Materials.Water.getFluid(6000)
            )
            .itemOutputs(
                GTCMItemList.LanTieKuang.get(320)
            )
            .eut(0)
            .duration(10 * SECONDS)
            .addTo(HydroMiningRig);

        ItemStack ZiJingKuang = GTCMItemList.ZiJingMainBlock.get(1);
        ZiJingKuang.setStackDisplayName(StatCollector.translateToLocal("eohb_recipe.PowerMiner.ZiJing_source"));

        GTValues.RA.stdBuilder()
            .itemInputs(
                ZiJingKuang
            )
            .fluidInputs(
                Materials.Water.getFluid(6000)
            )
            .itemOutputs(
                GTCMItemList.ZiJingKuang.get(320)
            )
            .eut(0)
            .duration(10 * SECONDS)
            .addTo(HydroMiningRig);

        ItemStack ChiTongKuang = GTCMItemList.ChiTongMainBlock.get(1);
        ChiTongKuang.setStackDisplayName(StatCollector.translateToLocal("eohb_recipe.PowerMiner.ChiTong_source"));

        GTValues.RA.stdBuilder()
            .itemInputs(
                ChiTongKuang
            )
            .fluidInputs(
                Materials.Water.getFluid(12000)
            )
            .itemOutputs(
                GTCMItemList.ChiTongKuang.get(160)
            )
            .eut(0)
            .duration(10 * SECONDS)
            .addTo(HydroMiningRig);
    }
}
