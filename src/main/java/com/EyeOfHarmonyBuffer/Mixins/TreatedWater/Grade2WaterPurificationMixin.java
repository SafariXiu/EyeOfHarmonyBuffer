package com.EyeOfHarmonyBuffer.Mixins.TreatedWater;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.common.tileentities.machines.multi.purification.MTEPurificationUnitBase;
import gregtech.common.tileentities.machines.multi.purification.MTEPurificationUnitOzonation;
import journeymap.shadow.org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MTEPurificationUnitOzonation.class, remap = false)
public abstract class Grade2WaterPurificationMixin extends MTEPurificationUnitBase<MTEPurificationUnitOzonation>
    implements ISurvivalConstructable {
    protected Grade2WaterPurificationMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Inject(method = "checkProcessing", at = @At("RETURN"), cancellable = true)
    private void injectCheckProcessing(CallbackInfoReturnable<CheckRecipeResult> cir) {
        if (MainConfig.Grade2WaterPurificationEnabled) {
            CheckRecipeResult originalResult = cir.getReturnValue();

            if (originalResult.wasSuccessful()) {
                cir.setReturnValue(CheckRecipeResultRegistry.SUCCESSFUL);
            } else {
                cir.setReturnValue(originalResult);
            }
        }
    }
}
