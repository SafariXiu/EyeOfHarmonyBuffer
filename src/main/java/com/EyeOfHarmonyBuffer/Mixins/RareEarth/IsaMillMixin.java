package com.EyeOfHarmonyBuffer.Mixins.RareEarth;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.OverclockCalculator;
import gregtech.api.util.ParallelHelper;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.GTPPMultiBlockBase;
import gtPlusPlus.xmod.gregtech.common.tileentities.machines.multi.processing.MTEIsaMill;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nonnull;

@Mixin(value = MTEIsaMill.class, remap = false)
public abstract class IsaMillMixin extends GTPPMultiBlockBase<MTEIsaMill> implements ISurvivalConstructable {

    public IsaMillMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Shadow
    protected abstract void damageMillingBall(ItemStack aStack);

    @Shadow
    protected abstract ItemStack findMillingBall(ItemStack[] aItemInputs);

    @Inject(
        method = "createProcessingLogic",
        at = @At("HEAD"),
        cancellable = true
    )
    private void injectCreateProcessingLogic(CallbackInfoReturnable<ProcessingLogic> cir) {
        if(MainConfig.IsaMillEnable){
            ProcessingLogic customProcessingLogic = new ProcessingLogic() {

                @org.jetbrains.annotations.NotNull
                @Override
                protected CheckRecipeResult validateRecipe(@org.jetbrains.annotations.NotNull GTRecipe recipe) {
                    return CheckRecipeResultRegistry.SUCCESSFUL;
                }

                @org.jetbrains.annotations.NotNull
                @Override
                protected OverclockCalculator createOverclockCalculator(@org.jetbrains.annotations.NotNull GTRecipe recipe) {
                    return new OverclockCalculator()
                        //.setSpeedBoost(100.0) // 速度提升 100 倍
                        .setParallel(Integer.MAX_VALUE) // 最大并行数
                        .setEUt(0); // 不耗电
                }

                @org.jetbrains.annotations.NotNull
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

            cir.setReturnValue(customProcessingLogic);
            cir.cancel();
        }
    }
}
