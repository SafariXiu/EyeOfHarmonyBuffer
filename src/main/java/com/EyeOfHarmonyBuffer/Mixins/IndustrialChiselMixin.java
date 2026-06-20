package com.EyeOfHarmonyBuffer.Mixins;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEExtendedPowerMultiBlockBase;
import gregtech.common.tileentities.machines.multi.MTEIndustrialChisel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MTEIndustrialChisel.class, remap = false)
public abstract class IndustrialChiselMixin extends MTEExtendedPowerMultiBlockBase<MTEIndustrialChisel> implements ISurvivalConstructable {
    public IndustrialChiselMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Inject(method = "createProcessingLogic", at = @At("RETURN"), cancellable = true)
    private void modifyProcessingLogic(CallbackInfoReturnable<ProcessingLogic> cir) {
        if(MainConfig.IndustrialChiselEnable){
            ProcessingLogic originalLogic = cir.getReturnValue();

            ProcessingLogic modifiedLogic = originalLogic
                .setSpeedBonus(1F / 100F)
                .setEuModifier(0F)
                .setMaxParallelSupplier(() -> Integer.MAX_VALUE);

            cir.setReturnValue(modifiedLogic);
        }
    }
}
