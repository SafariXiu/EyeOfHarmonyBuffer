package com.EyeOfHarmonyBuffer.Mixins.TreatedWater;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import gregtech.common.tileentities.machines.multi.purification.MTEPurificationUnitBase;
import gregtech.common.tileentities.machines.multi.purification.MTEPurificationUnitDegasser;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

@Mixin(value = MTEPurificationUnitDegasser.class, remap = false)
public abstract class Grade7WaterPurificationMixin extends MTEPurificationUnitBase<MTEPurificationUnitDegasser>
    implements ISurvivalConstructable {

    protected Grade7WaterPurificationMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Inject(
        method = "calculateFinalSuccessChance",
        at = @At("HEAD"),
        cancellable = true
    )
    private void alwaysSuccess(CallbackInfoReturnable<Float> cir) {
        if (MainConfig.Grade7WaterPurificationEnabled) {
            cir.setReturnValue(100.0f);
        }
    }
}
