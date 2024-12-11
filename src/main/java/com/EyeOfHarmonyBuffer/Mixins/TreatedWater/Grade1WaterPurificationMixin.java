package com.EyeOfHarmonyBuffer.Mixins.TreatedWater;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import gregtech.common.tileentities.machines.multi.purification.MTEPurificationUnitBase;
import gregtech.common.tileentities.machines.multi.purification.MTEPurificationUnitClarifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MTEPurificationUnitClarifier.class, remap = false)
public abstract class Grade1WaterPurificationMixin extends MTEPurificationUnitBase<MTEPurificationUnitClarifier>
    implements ISurvivalConstructable {
    protected Grade1WaterPurificationMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Inject(method = "depleteRecipeInputs", at = @At("HEAD"), cancellable = true)
    public void modifyDepleteRecipeInputs(CallbackInfo ci) {
        if(MainConfig.Grade1WaterPurificationEnabled){
            ci.cancel();
        }
    }
}
