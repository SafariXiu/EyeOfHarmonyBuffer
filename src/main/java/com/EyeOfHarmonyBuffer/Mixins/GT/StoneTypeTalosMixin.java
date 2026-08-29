package com.EyeOfHarmonyBuffer.Mixins.GT;

import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.EyeOfHarmonyBuffer.space.talos.WorldProviderTalos2;
import gregtech.api.enums.StoneType;

/**
 * 深板岩/凝灰岩的 StoneType 默认只允许在主世界生成，
 * 这里让它们在塔罗斯维度也能被 StoneType.findStoneType 识别，
 * 这样 GT 材料矿脉在深板岩/凝灰岩中也能生成对应变体矿石。
 */
@Mixin(value = StoneType.class, remap = false)
public class StoneTypeTalosMixin {

    @Inject(method = "canGenerateInWorld", at = @At("HEAD"), cancellable = true, remap = false)
    private void eohb$allowDeepslateAndTuffInTalos(World world, CallbackInfoReturnable<Boolean> cir) {
        if (!(world.provider instanceof WorldProviderTalos2)) return;

        StoneType self = StoneType.class.cast(this);
        if (self == StoneType.Deepslate || self == StoneType.Tuff) {
            cir.setReturnValue(true);
        }
    }
}
