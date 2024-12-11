package com.EyeOfHarmonyBuffer.Mixins.TreatedWater;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import gregtech.common.tileentities.machines.multi.purification.MTEPurificationUnitBase;
import gregtech.common.tileentities.machines.multi.purification.MTEPurificationUnitFlocculation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MTEPurificationUnitFlocculation.class, remap = false)
public abstract class Grade3WaterPurificationMixin extends MTEPurificationUnitBase<MTEPurificationUnitFlocculation>
    implements ISurvivalConstructable {
    protected Grade3WaterPurificationMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Inject(
        method = "calculateFinalSuccessChance",
        at = @At("HEAD"),
        cancellable = true
    )
    private void modifyFinalSuccessChance(CallbackInfoReturnable<Float> cir) {
        if (MainConfig.Grade3WaterPurificationEnabled) {
            cir.setReturnValue(100.0f);
        }
    }
}
