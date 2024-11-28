package com.EyeOfHarmonyBuffer.Mixins;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import gregtech.api.logic.ProcessingLogic;
import gregtech.common.tileentities.machines.multi.MTEIndustrialLaserEngraver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MTEIndustrialLaserEngraver.class,remap = false)
public class IndustrialLaserEngraverMixin {

    @Shadow
    private int laserAmps;

    @Inject(method = "createProcessingLogic", at = @At("RETURN"), cancellable = true)
    private void injectProcessingLogic(CallbackInfoReturnable<ProcessingLogic> cir) {
        if(MainConfig.IndustrialLaserEngraverOverclockEnabled){
            ProcessingLogic logic = cir.getReturnValue();
            logic.setSpeedBonus(0F / 100F)
                .setEuModifier(0.0F);
            cir.setReturnValue(logic);
        }
    }

    /**
     * @author 狠狠的覆盖返回值
     * @reason 我们讨厌愚蠢的低并行!
     */
    @Overwrite
    private int getMaxParallelRecipes() {
        if(MainConfig.IndustrialLaserEngraverParallelEnabled){
            return Integer.MAX_VALUE;
        }
        return laserAmps;
    }
}
