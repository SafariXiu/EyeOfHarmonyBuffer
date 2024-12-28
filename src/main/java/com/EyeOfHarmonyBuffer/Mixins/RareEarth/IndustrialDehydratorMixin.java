package com.EyeOfHarmonyBuffer.Mixins.RareEarth;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import gregtech.api.enums.HeatingCoilLevel;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.OverclockCalculator;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.GTPPMultiBlockBase;
import gtPlusPlus.xmod.gregtech.common.tileentities.machines.multi.processing.MTEIndustrialDehydrator;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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
                    return super.createOverclockCalculator(recipe)
                        .setHeatOC(true)
                        .setHeatDiscount(true)
                        .setRecipeHeat(recipe.mSpecialValue)
                        .setMachineHeat((int) getCoilLevel().getHeat());
                }
            };

            customLogic.setSpeedBonus(1F / 0.02F)
                .setEuModifier(0.0F)
                .setMaxParallelSupplier(() -> 2000000);

            cir.setReturnValue(customLogic);
        }
    }
}
