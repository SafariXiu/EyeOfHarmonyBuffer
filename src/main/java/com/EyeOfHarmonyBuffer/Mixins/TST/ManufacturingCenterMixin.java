package com.EyeOfHarmonyBuffer.Mixins.TST;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.Nxer.TwistSpaceTechnology.common.machine.TST_ManufacturingCenter;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;
import gregtech.api.util.OverclockCalculator;
import gregtech.api.util.ParallelHelper;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.GTPPMultiBlockBase;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nonnull;

@Mixin(value = TST_ManufacturingCenter.class, remap = false)
public abstract class ManufacturingCenterMixin extends GTPPMultiBlockBase<TST_ManufacturingCenter>
    implements ISurvivalConstructable {

    public ManufacturingCenterMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Inject(method = "createProcessingLogic",at = @At("HEAD"),cancellable = true)
    public void createProcessingLogic(CallbackInfoReturnable<ProcessingLogic> cir) {
        if(MainConfig.ManufacturingCenterEnable){
            ProcessingLogic customLogic = new ProcessingLogic() {

                @Override
                protected @NotNull CheckRecipeResult validateRecipe(@NotNull GTRecipe recipe){
                    return super.validateRecipe(recipe);
                }

                @Override
                protected double calculateDuration(@Nonnull GTRecipe recipe, @Nonnull ParallelHelper helper,
                                                   @Nonnull OverclockCalculator calculator) {
                    return 10;
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
            };
            cir.setReturnValue(customLogic);
            cir.cancel();
        }
    }

    @Inject(method = "setProcessingLogicPower",at = @At("HEAD"),cancellable = true)
    public void setProcessingLogicPower(ProcessingLogic logic, CallbackInfo ci) {
        if(MainConfig.ManufacturingCenterEnable){
            logic.setAvailableVoltage(GTUtility.roundUpVoltage(this.getMaxInputVoltage()));

            ci.cancel();
        }
    }
}
