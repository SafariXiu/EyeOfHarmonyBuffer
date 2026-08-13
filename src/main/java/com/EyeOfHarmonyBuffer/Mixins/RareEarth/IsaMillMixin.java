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

    @Inject(
        method = "createProcessingLogic",
        at = @At("HEAD"),
        cancellable = true
    )
    private void injectCreateProcessingLogic(CallbackInfoReturnable<ProcessingLogic> cir) {
        if (MainConfig.IsaMillEnable) {
            ProcessingLogic customProcessingLogic = new IsaMillLogic();

            cir.setReturnValue(customProcessingLogic);
            cir.cancel();
        }
    }

    private static final class IsaMillLogic extends ProcessingLogic {

        @NotNull
        @Override
        protected CheckRecipeResult validateRecipe(@NotNull GTRecipe recipe) {
            return CheckRecipeResultRegistry.SUCCESSFUL;
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
