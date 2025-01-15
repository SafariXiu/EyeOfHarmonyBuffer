package com.EyeOfHarmonyBuffer.utils;

import gregtech.api.logic.ProcessingLogic;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.OverclockCalculator;
import gregtech.api.util.ParallelHelper;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;

public class CustomProcessingLogic extends ProcessingLogic {

    public CustomProcessingLogic() {
        super();
    }

    @NotNull
    @Override
    protected CheckRecipeResult validateRecipe(@NotNull GTRecipe recipe) {
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    @NotNull
    @Override
    protected OverclockCalculator createOverclockCalculator(@NotNull GTRecipe recipe) {
        return new OverclockCalculator()
            //.setSpeedBoost(100.0) // 速度提升 100 倍
            .setParallel(Integer.MAX_VALUE) // 最大并行数
            .setEUt(0); // 不耗电
    }

    @NotNull
    @Override
    protected ParallelHelper createParallelHelper(@NotNull GTRecipe recipe) {
        return new ParallelHelper()
            .setRecipe(recipe)
            .setItemInputs(inputItems)
            .setFluidInputs(inputFluids)
            .setAvailableEUt(Integer.MAX_VALUE) // 设置无限能量
            .setMachine(machine, protectItems, protectFluids)
            .setMaxParallel(Integer.MAX_VALUE) // 设置极大并行
            .setEUtModifier(0.0) // 不耗电
            .enableBatchMode(batchSize) // 启用批量模式
            .setConsumption(true)
            .setOutputCalculation(true);
    }

    @Override
    protected double calculateDuration(@Nonnull GTRecipe recipe, @Nonnull ParallelHelper helper,
                                       @Nonnull OverclockCalculator calculator) {
        return 10;
    }

    @Override
    public CheckRecipeResult process() {
        return super.process();
    }
}
