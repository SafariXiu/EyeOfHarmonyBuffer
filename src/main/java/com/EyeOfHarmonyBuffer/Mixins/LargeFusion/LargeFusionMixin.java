package com.EyeOfHarmonyBuffer.Mixins.LargeFusion;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.EyeOfHarmonyBuffer.Config.MainConfig;

import goodgenerator.blocks.tileEntity.base.MTELargeFusionComputer;
import gregtech.api.enums.GTValues;

@Mixin(value = MTELargeFusionComputer.class, remap = false)
public class LargeFusionMixin {

    /**
     * 覆盖 getSingleHatchPower 方法
     * 去掉 extraPara(100) 和 / 32，保持功率计算为 电压 * 安培数。
     *
     * @author EyeOfHarmonyBuffer
     * @reason Simplify the power calculation by removing extraPara(100) and /32 to match current design.
     */
    @Overwrite
    protected long getSingleHatchPower() {
        MTELargeFusionComputer fusionComputer = (MTELargeFusionComputer) (Object) this;

        return GTValues.V[fusionComputer.tier()] * fusionComputer.getMaxPara() * 32;
    }

    @Inject(method = "maxEUStore", at = @At("RETURN"), cancellable = true)
    private void modifyMaxEUStore(CallbackInfoReturnable<Long> cir) {
        if (MainConfig.LargeFusionMixin) {
            long maxEnergyStore = 10000000000000L;
            cir.setReturnValue(maxEnergyStore);
        }
    }
}
