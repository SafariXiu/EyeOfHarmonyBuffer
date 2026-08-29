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
        if (MainConfig.IndustrialDehydratorEnable) {
            ProcessingLogic customLogic = new IndustrialDehydratorLogic(this);

            cir.setReturnValue(customLogic);
            cir.cancel();
        }
    }

    private static final class IndustrialDehydratorLogic extends ProcessingLogic {

        private final IndustrialDehydratorMixin outer;

        IndustrialDehydratorLogic(IndustrialDehydratorMixin outer) {
            this.outer = outer;
        }

        @NotNull
        @Override
        protected CheckRecipeResult validateRecipe(@NotNull GTRecipe recipe) {
            return recipe.mSpecialValue <= outer.getCoilLevel()
                .getHeat()
                    ? CheckRecipeResultRegistry.SUCCESSFUL
                    : CheckRecipeResultRegistry.insufficientHeat(recipe.mSpecialValue);
        }

        @NotNull
        @Override
        protected OverclockCalculator createOverclockCalculator(@NotNull GTRecipe recipe) {
            return OverclockCalculator.ofNoOverclock(recipe)
                .setEUtDiscount(0.0);
        }

        @NotNull
        @Override
        protected ParallelHelper createParallelHelper(@NotNull GTRecipe recipe) {
            return new ParallelHelper()
                .setRecipe(recipe)
                .setItemInputs(inputItems)
                .setFluidInputs(inputFluids)
                .setAvailableEUt(Integer.MAX_VALUE)
                .setMachine(machine, protectItems, protectFluids)
                .setMaxParallel(Integer.MAX_VALUE)
                .setEUtModifier(0.0)
                .enableBatchMode(batchSize)
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
}
