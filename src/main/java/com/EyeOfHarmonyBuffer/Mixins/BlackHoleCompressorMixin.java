package com.EyeOfHarmonyBuffer.Mixins;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.EyeOfHarmonyBuffer.Mixins.Accessor.BlackHoleCompressorAccessor;
import gregtech.api.logic.ProcessingLogic;
import gregtech.common.tileentities.machines.multi.compressor.MTEBlackHoleCompressor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.EyeOfHarmonyBuffer.Config.MainConfig.BlackHoleCompressorPowerConsumptionModification;
import static com.EyeOfHarmonyBuffer.Config.MainConfig.BlackHoleCompressorTimeConsumptionModification;

@Mixin(value = MTEBlackHoleCompressor.class,remap = false)
public class BlackHoleCompressorMixin {

    @Inject(method = "onPostTick", at = @At("HEAD"))
    private void ensureStableBlackHole(CallbackInfo ci) {
        if(MainConfig.BlackHoleCompressorStabilityLock){
            ((BlackHoleCompressorAccessor) this).setBlackHoleStatus((byte) 2);
        }
    }

    @Inject(method = "getMaxParallelRecipes", at = @At("RETURN"), cancellable = true)
    private void overrideMaxParallelRecipes(CallbackInfoReturnable<Integer> cir) {
        if(MainConfig.BlackHoleCompressorParallelModificationEnabled){
            cir.setReturnValue(MainConfig.BlackHoleCompressorParallelCountModification);
        }
    }

    @Inject(method = "createProcessingLogic", at = @At("RETURN"))
    private void adjustEnergyAndSpeed(CallbackInfoReturnable<ProcessingLogic> cir) {
        String powerModifierStr = BlackHoleCompressorPowerConsumptionModification;
        String timeModifierStr = BlackHoleCompressorTimeConsumptionModification;

        float powerModifier = parseFloatConfig(powerModifierStr, 0.7F);
        float timeModifier = parseFloatConfig(timeModifierStr, 0.2F);

        ProcessingLogic logic = cir.getReturnValue();
        if(MainConfig.BlackHoleCompressorPowerConsumptionModificationEnabled){
            logic.setEuModifier(powerModifier);
        }
        if(MainConfig.BlackHoleCompressorTimeConsumptionModificationEnabled){
            logic.setSpeedBonus(timeModifier);
        }
    }

    private float parseFloatConfig(String value, float defaultValue) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            System.err.println("配置文件中数据异常：" + value + "，将使用默认值：" + defaultValue);
            return defaultValue;
        }
    }
}
