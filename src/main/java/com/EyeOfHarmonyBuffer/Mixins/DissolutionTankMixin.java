package com.EyeOfHarmonyBuffer.Mixins;

import java.util.List;

import com.EyeOfHarmonyBuffer.utils.CustomProcessingLogic;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import gregtech.api.interfaces.ISecondaryDescribable;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.EyeOfHarmonyBuffer.Config.MainConfig;

import gregtech.api.util.GTRecipe;
import gtnhlanth.common.tileentity.MTEDissolutionTank;

@Mixin(value = MTEDissolutionTank.class, remap = false)
public abstract class DissolutionTankMixin extends MTEEnhancedMultiBlockBase<MTEDissolutionTank>
    implements ISurvivalConstructable, ISecondaryDescribable {

    protected DissolutionTankMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Inject(method = "checkRatio", at = @At("HEAD"), cancellable = true)
    private void injectCheckRatio(GTRecipe tRecipe, List<FluidStack> tFluidInputs,
        CallbackInfoReturnable<Boolean> cir) {
        if (MainConfig.DisTankTrue) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "createProcessingLogic",at = @At("HEAD"),cancellable = true)
    private void injectCreateProcessingLogic(CallbackInfoReturnable<ProcessingLogic> cir){
        if(MainConfig.DisTankOverClockEnable){
            CustomProcessingLogic customProcessingLogic = new CustomProcessingLogic();
            cir.setReturnValue(customProcessingLogic);
            cir.cancel();
        }
    }
}
