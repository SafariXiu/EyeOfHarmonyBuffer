package com.EyeOfHarmonyBuffer.Mixins;

import bartworks.common.tileentities.multis.mega.MTEMegaBlastFurnace;
import bartworks.common.tileentities.multis.mega.MegaMultiBlockBase;
import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.OverclockCalculator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nonnull;

@Mixin(value = MTEMegaBlastFurnace.class, remap = false)
public abstract class MegaBlastFurnaceMixin extends MegaMultiBlockBase<MTEMegaBlastFurnace> implements ISurvivalConstructable {

    protected MegaBlastFurnaceMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Shadow
    private int mHeatingCapacity;

    @Inject(method = "createProcessingLogic",at = @At("HEAD"),cancellable = true)
    private void createProcessingLogic(CallbackInfoReturnable<ProcessingLogic> cir) {
        if(MainConfig.MegaBlastFurnaceEnable){
            ProcessingLogic customLogic = new ProcessingLogic(){

                @Nonnull
                @Override
                protected OverclockCalculator createOverclockCalculator(@Nonnull GTRecipe recipe) {
                    return super.createOverclockCalculator(recipe).setRecipeHeat(recipe.mSpecialValue)
                        .setMachineHeat(mHeatingCapacity)
                        .setHeatOC(true)
                        .enablePerfectOC()
                        .setHeatDiscount(true);
                }

                @Override
                protected @Nonnull CheckRecipeResult validateRecipe(@Nonnull GTRecipe recipe) {
                    return recipe.mSpecialValue <= mHeatingCapacity
                        ? CheckRecipeResultRegistry.SUCCESSFUL
                        : CheckRecipeResultRegistry.insufficientHeat(recipe.mSpecialValue);
                }
            };

            customLogic
                .setMaxParallelSupplier(() -> Integer.MAX_VALUE);

            cir.setReturnValue(customLogic);
            cir.cancel();
        }
    }
}
