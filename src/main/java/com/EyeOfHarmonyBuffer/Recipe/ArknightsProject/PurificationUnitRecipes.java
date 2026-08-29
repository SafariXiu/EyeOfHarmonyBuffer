package com.EyeOfHarmonyBuffer.Recipe.ArknightsProject;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.common.material.EOHBMaterialPool;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.Gas.GasEnvRecipeFlags;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;

import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.PurificationUnit;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.EOHB_Environment_NONE;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.EOHB_Environment_STABLE;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

public class PurificationUnitRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.InertXirconEffluent.getFluidOrGas(4000)
            )
            .fluidOutputs(
                EOHBMaterialPool.XirconEffluent.getFluidOrGas(1000),
                Materials.Water.getFluid(1000)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .specialValue(GasEnvRecipeFlags.NONE)
            .setNEIDesc(EOHB_Environment_NONE)
            .addTo(PurificationUnit);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.CupriumSolution.getFluidOrGas(4000)
            )
            .fluidOutputs(
                EOHBMaterialPool.HetoniteSolution.getFluidOrGas(1000),
                EOHBMaterialPool.PrecipitationAcid.getFluidOrGas(1000)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .specialValue(GasEnvRecipeFlags.NONE)
            .setNEIDesc(EOHB_Environment_NONE)
            .addTo(PurificationUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                EOHBMaterialPool.ArsenicImpact.get(OrePrefixes.dust, 32)
            )
            .itemOutputs(
                GTCMItemList.MiGuardFrostShard.get(1)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .specialValue(GasEnvRecipeFlags.NONE)
            .setNEIDesc(EOHB_Environment_NONE)
            .addTo(PurificationUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.FenLiXin.get(2)
            )
            .fluidInputs(
                EOHBMaterialPool.Xiragen.getFluidOrGas(2000)
            )
            .fluidOutputs(
                EOHBMaterialPool.HeavyXiragen.getFluidOrGas(1000)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .specialValue(GasEnvRecipeFlags.NONE)
            .setNEIDesc(EOHB_Environment_NONE)
            .addTo(PurificationUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.FenLiXin.get(1)
            )
            .fluidInputs(
                EOHBMaterialPool.Xiragen.getFluidOrGas(2000)
            )
            .fluidOutputs(
                EOHBMaterialPool.HeavyXiragen.getFluidOrGas(1000)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .specialValue(GasEnvRecipeFlags.STABLE)
            .setNEIDesc(EOHB_Environment_STABLE)
            .addTo(PurificationUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.FenLiXin.get(2)
            )
            .fluidInputs(
                EOHBMaterialPool.CupriumGas.getFluidOrGas(2000)
            )
            .fluidOutputs(
                EOHBMaterialPool.HetoniteGas.getFluidOrGas(1000)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .specialValue(GasEnvRecipeFlags.NONE)
            .setNEIDesc(EOHB_Environment_NONE)
            .addTo(PurificationUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.FenLiXin.get(1)
            )
            .fluidInputs(
                EOHBMaterialPool.CupriumGas.getFluidOrGas(2000)
            )
            .fluidOutputs(
                EOHBMaterialPool.HetoniteGas.getFluidOrGas(1000)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .specialValue(GasEnvRecipeFlags.STABLE)
            .setNEIDesc(EOHB_Environment_STABLE)
            .addTo(PurificationUnit);
    }
}
