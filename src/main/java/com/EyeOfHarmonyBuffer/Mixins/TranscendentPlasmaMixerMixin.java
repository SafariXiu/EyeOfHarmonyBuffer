package com.EyeOfHarmonyBuffer.Mixins;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.OverclockCalculator;
import gregtech.common.tileentities.machines.multi.MTETranscendentPlasmaMixer;
import net.minecraft.nbt.NBTTagCompound;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nonnull;
import java.math.BigInteger;

@Mixin(value = MTETranscendentPlasmaMixer.class,remap = false)
public abstract class TranscendentPlasmaMixerMixin extends MTEEnhancedMultiBlockBase<MTETranscendentPlasmaMixer>
    implements ISurvivalConstructable {

    protected TranscendentPlasmaMixerMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    private static final int FIXED_PARALLEL = 200_000;

    @Shadow
    private int multiplier;

    @Shadow
    BigInteger finalConsumption;

    @Inject(method = "createProcessingLogic", at = @At("RETURN"), cancellable = true)
    private void modifyProcessingLogic(CallbackInfoReturnable<ProcessingLogic> cir) {
        if(MainConfig.TranscendentPlasmaMixerEnable){
            ProcessingLogic originalLogic = cir.getReturnValue();

            ProcessingLogic newLogic = new ProcessingLogic() {
                @NotNull
                @Override
                protected CheckRecipeResult validateRecipe(@Nonnull GTRecipe recipe) {
                    return CheckRecipeResultRegistry.SUCCESSFUL;
                }

                @NotNull
                @Override
                protected CheckRecipeResult onRecipeStart(@Nonnull GTRecipe recipe) {
                    finalConsumption = BigInteger.ZERO;
                    setCalculatedEut(0);
                    return CheckRecipeResultRegistry.SUCCESSFUL;
                }

                @Nonnull
                @Override
                protected OverclockCalculator createOverclockCalculator(@Nonnull GTRecipe recipe) {
                    return OverclockCalculator.ofNoOverclock(recipe);
                }
            }.setMaxParallelSupplier(() -> FIXED_PARALLEL);

            cir.setReturnValue(newLogic);
        }
    }

    @Inject(method = "loadNBTData", at = @At("HEAD"))
    private void onLoadNBT(NBTTagCompound aNBT, CallbackInfo ci) {
        if(MainConfig.TranscendentPlasmaMixerEnable){
            multiplier = FIXED_PARALLEL;
        }
    }

    @Inject(method = "saveNBTData", at = @At("HEAD"))
    private void onSaveNBT(NBTTagCompound aNBT, CallbackInfo ci) {
        if(MainConfig.TranscendentPlasmaMixerEnable){
            multiplier = FIXED_PARALLEL;
        }
    }
}
