package com.EyeOfHarmonyBuffer.Recipe.ArknightsProject;

import com.EyeOfHarmonyBuffer.common.material.EOHBMaterialPool;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.Materials;

import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.Fluid_GasTransmutingUnit;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

public class Fluid_GasTransmutingUnitRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {

        GTValues.RA.stdBuilder()
            .fluidInputs(
                Materials.Water.getFluid(1000)
            )
            .fluidOutputs(
                EOHBMaterialPool.Aquagen.getFluidOrGas(1000)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(Fluid_GasTransmutingUnit);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.PrecipitationAcid.getFluidOrGas(1000)
            )
            .fluidOutputs(
                EOHBMaterialPool.Acridgen.getFluidOrGas(1000)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(Fluid_GasTransmutingUnit);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.LiquidXiranite.getFluidOrGas(1000)
            )
            .fluidOutputs(
                EOHBMaterialPool.Xiragen.getFluidOrGas(1000)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(Fluid_GasTransmutingUnit);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.LiquidHeavyXiranite.getFluidOrGas(2000)
            )
            .fluidOutputs(
                EOHBMaterialPool.HeavyXiragen.getFluidOrGas(5000)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(Fluid_GasTransmutingUnit);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.CupriumSolution.getFluidOrGas(2000)
            )
            .fluidOutputs(
                EOHBMaterialPool.CupriumGas.getFluidOrGas(1000)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(Fluid_GasTransmutingUnit);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.HetoniteSolution.getFluidOrGas(2000)
            )
            .fluidOutputs(
                EOHBMaterialPool.HetoniteGas.getFluidOrGas(1000)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(Fluid_GasTransmutingUnit);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.Aquagen.getFluidOrGas(1000)
            )
            .fluidOutputs(
                Materials.Water.getFluid(1000)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(Fluid_GasTransmutingUnit);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.Acridgen.getFluidOrGas(1000)
            )
            .fluidOutputs(
                EOHBMaterialPool.PrecipitationAcid.getFluidOrGas(1000)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(Fluid_GasTransmutingUnit);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.Xiragen.getFluidOrGas(1000)
            )
            .fluidOutputs(
                EOHBMaterialPool.LiquidXiranite.getFluidOrGas(1000)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(Fluid_GasTransmutingUnit);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.HeavyXiragen.getFluidOrGas(5000)
            )
            .fluidOutputs(
                EOHBMaterialPool.LiquidHeavyXiranite.getFluidOrGas(2000)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(Fluid_GasTransmutingUnit);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.CupriumGas.getFluidOrGas(1000)
            )
            .fluidOutputs(
                EOHBMaterialPool.CupriumSolution.getFluidOrGas(2000)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(Fluid_GasTransmutingUnit);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.HetoniteGas.getFluidOrGas(1000)
            )
            .fluidOutputs(
                EOHBMaterialPool.HetoniteSolution.getFluidOrGas(2000)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(Fluid_GasTransmutingUnit);
    }
}
