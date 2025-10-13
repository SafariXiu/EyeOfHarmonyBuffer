package com.EyeOfHarmonyBuffer.Mixins.IndustrialLaserEngraver;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.common.tileentities.machines.multi.MTEIndustrialLaserEngraver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MTEIndustrialLaserEngraver.class,remap = false)
public class IndustrialLaserEngraverParallelMixin {

    @Shadow
    private int laserAmps;

    @Inject(method = "getMaxParallelRecipes", at = @At("RETURN"), cancellable = true)
    private void injectGetMaxParallelRecipes(CallbackInfoReturnable<Integer> cir) {
        if (MainConfig.IndustrialLaserEngraverParallelEnabled) {
            cir.setReturnValue(Integer.MAX_VALUE);
        }
    }

    @Inject(method = "addLaserSource", at = @At("TAIL"))
    private void modifyLaserAmps(IGregTechTileEntity aTileEntity, int aBaseCasingIndex, CallbackInfoReturnable<Boolean> cir) {
        if (MainConfig.IndustrialLaserEngraverParallelEnabled) {
            laserAmps = Integer.MAX_VALUE;
        }
    }
}
