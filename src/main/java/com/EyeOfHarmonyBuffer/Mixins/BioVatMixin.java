package com.EyeOfHarmonyBuffer.Mixins;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.ParallelHelper;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import bartworks.common.tileentities.multis.MTEBioVat;

import javax.annotation.Nonnull;

@Mixin(value = MTEBioVat.class, remap = false)
public abstract class BioVatMixin extends MTEEnhancedMultiBlockBase<MTEBioVat> implements ISurvivalConstructable {

    @Shadow
    private int mSievert;

    @Shadow
    protected abstract GTRecipe recipeWithMultiplier(GTRecipe recipe, FluidStack[] fluidInputs);

    protected BioVatMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Inject(method = "createProcessingLogic", at = @At("RETURN"), cancellable = true)
    private void modifyProcessingLogic(CallbackInfoReturnable<ProcessingLogic> cir) {
        if(MainConfig.BioVatRadiationEnabled){
            ProcessingLogic originalLogic = cir.getReturnValue();

            ProcessingLogic wrappedLogic = new ProcessingLogic() {
                @NotNull
                @Override
                protected CheckRecipeResult validateRecipe(@NotNull GTRecipe recipe) {
                    return CheckRecipeResultRegistry.SUCCESSFUL;
                }

                @NotNull
                @Override
                protected ParallelHelper createParallelHelper(@Nonnull GTRecipe recipe) {
                    return super.createParallelHelper(recipeWithMultiplier(recipe, inputFluids));
                }
            };

            cir.setReturnValue(wrappedLogic);
        }
    }

    @Inject(method = "recipeWithMultiplier", at = @At("HEAD"), cancellable = true)
    private void modifyRecipeWithMultiplier(GTRecipe recipe, FluidStack[] fluidInputs, CallbackInfoReturnable<GTRecipe> cir) {
        if (MainConfig.BioVatOutputRatioEnable){
            GTRecipe tRecipe = recipe.copy();

            int multiplier = MainConfig.BioVatOutputRatio;

            if (tRecipe.mFluidOutputs != null && tRecipe.mFluidOutputs.length > 0) {
                tRecipe.mFluidOutputs[0].amount *= multiplier;
            }

            cir.setReturnValue(tRecipe);
        }
    }

    @Inject(method = "onPostTick", at = @At("HEAD"))
    private void modifyOnPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick, CallbackInfo ci) {
        if(MainConfig.BioVatRadiationEnabled){
            this.mSievert = Integer.MAX_VALUE;
        }
    }
}
