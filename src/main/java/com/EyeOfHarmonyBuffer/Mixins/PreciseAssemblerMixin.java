package com.EyeOfHarmonyBuffer.Mixins;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.EyeOfHarmonyBuffer.utils.CustomProcessingLogic;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import goodgenerator.blocks.tileEntity.MTEPreciseAssembler;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEExtendedPowerMultiBlockBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MTEPreciseAssembler.class, remap = false)
public abstract class PreciseAssemblerMixin extends MTEExtendedPowerMultiBlockBase<MTEPreciseAssembler>
    implements IConstructable, ISurvivalConstructable {

    protected PreciseAssemblerMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Shadow
    protected int mode;

    @Shadow
    protected int casingTier;

    @Inject(method = "createProcessingLogic",at = @At("HEAD"),cancellable = true)
    public void createProcessingLogic(CallbackInfoReturnable<ProcessingLogic> cir) {
        if (MainConfig.PreciseAssemblerEnable) {
            CustomProcessingLogic customLogic = new CustomProcessingLogic();
            cir.setReturnValue(customLogic);
            cir.cancel();
        }
    }
}
