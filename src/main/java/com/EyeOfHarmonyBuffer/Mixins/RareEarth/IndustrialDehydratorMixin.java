package com.EyeOfHarmonyBuffer.Mixins.RareEarth;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import gregtech.api.enums.HeatingCoilLevel;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.OverclockCalculator;
import gregtech.api.util.ParallelHelper;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.GTPPMultiBlockBase;
import gtPlusPlus.xmod.gregtech.common.tileentities.machines.multi.processing.MTEIndustrialDehydrator;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nonnull;

@Mixin(value = MTEIndustrialDehydrator.class, remap = false)
public abstract class IndustrialDehydratorMixin extends GTPPMultiBlockBase<MTEIndustrialDehydrator>
    implements ISurvivalConstructable {

    public IndustrialDehydratorMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Shadow
    public abstract HeatingCoilLevel getCoilLevel();

    @Inject(
        method = "createProcessingLogic",
        at = @At("HEAD"),
        cancellable = true
    )
    private void injectCreateProcessingLogic(CallbackInfoReturnable<ProcessingLogic> cir) {
        if(MainConfig.IndustrialDehydratorEnable){
            ProcessingLogic customLogic = new ProcessingLogic() {
                @NotNull
                @Override
                protected CheckRecipeResult validateRecipe(@NotNull GTRecipe recipe) {
                    return recipe.mSpecialValue <= getCoilLevel().getHeat()
                        ? CheckRecipeResultRegistry.SUCCESSFUL
                        : CheckRecipeResultRegistry.insufficientHeat(recipe.mSpecialValue);
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
            };

            cir.setReturnValue(customLogic);
            cir.cancel();
        }
    }
}
