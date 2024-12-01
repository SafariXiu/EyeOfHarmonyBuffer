package com.EyeOfHarmonyBuffer.Mixins;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import gtnhlanth.common.item.MaskList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MaskList.class, remap = false)
public class MaskListMixin {

    @Inject(method = "getDamage",at = @At("RETURN"),cancellable = true)
    public void getDamage(CallbackInfoReturnable<Integer> cir) {
        if(MainConfig.MaskInfiniteDurability){
            cir.setReturnValue(0);
        }
    }
}
