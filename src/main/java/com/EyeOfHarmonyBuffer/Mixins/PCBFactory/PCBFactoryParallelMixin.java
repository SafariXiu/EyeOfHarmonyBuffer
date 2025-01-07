package com.EyeOfHarmonyBuffer.Mixins.PCBFactory;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.EyeOfHarmonyBuffer.Mixins.Accessor.PCBFactory.PCBFactoryParallelAccessor;
import com.EyeOfHarmonyBuffer.Mixins.Accessor.PCBFactory.PCBFactoryParallelThisAccessor;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.util.GTRecipe;
import gregtech.common.tileentities.machines.multi.MTEPCBFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import javax.annotation.Nonnull;

@Mixin(targets = "gregtech.common.tileentities.machines.multi.MTEPCBFactory$1",remap = false)
public abstract class PCBFactoryParallelMixin extends ProcessingLogic {

    @Inject(method = "validateRecipe", at = @At("RETURN"), cancellable = true)
    private void modifyMaxParallel(@Nonnull GTRecipe recipe, CallbackInfoReturnable<CheckRecipeResult> cir) {
        if(MainConfig.PCBFactoryParallelEnable){
            this.maxParallel = Integer.MAX_VALUE;

            PCBFactoryParallelThisAccessor accessor = (PCBFactoryParallelThisAccessor) this;
            MTEPCBFactory outerInstance = accessor.getOuterInstance();
            ((PCBFactoryParallelAccessor) outerInstance).setMaxParallel(this.maxParallel);
        }
    }
}
