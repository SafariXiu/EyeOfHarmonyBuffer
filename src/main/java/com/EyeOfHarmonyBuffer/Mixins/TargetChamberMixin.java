package com.EyeOfHarmonyBuffer.Mixins;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gtnhlanth.common.tileentity.MTETargetChamber;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = MTETargetChamber.class, remap = false)
public abstract class TargetChamberMixin extends MTEEnhancedMultiBlockBase<MTETargetChamber> implements ISurvivalConstructable {

    protected TargetChamberMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @ModifyVariable(method = "checkRecipe", at = @At("STORE"), name = "progressTime")
    private float modifyProgressTime(float original) {
        return 100f;
    }
}
